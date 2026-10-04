#!/usr/bin/env python3
"""Bounded local static stress with separate, independently validated calibration."""
import argparse
from copy import deepcopy
from datetime import datetime, timezone
import json
import io
import math
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import time

import research_runner as shared

ROOT = Path(__file__).resolve().parents[1]
PRESETS = {'micro':(50,10), 'tiny':(100,20), 'small':(500,100), 'medium':(2000,400),
           'large':(10000,2000), 'xlarge':(20000,4000)}
FIELDS = ('vms','hosts','population','iterations','replications')
INT_MAX = (1<<31)-1
LONG_MAX = (1<<63)-1


def parse_duration(value):
    if value=='none': return None
    match=re.fullmatch(r'([0-9]+)([smh]?)',value)
    if not match or len(match[1])>19:
        raise argparse.ArgumentTypeError('time limit must be positive integer seconds or s/m/h, or none')
    seconds=int(match[1])*{'':1,'s':1,'m':60,'h':3600}[match[2]]
    if not 0<seconds<=INT_MAX:
        raise argparse.ArgumentTypeError('time limit must be positive and at most 2147483647 seconds')
    return float(seconds)


def positive(value):
    if not re.fullmatch(r'[0-9]+',value) or len(value)>10 or not 0<int(value)<=INT_MAX:
        raise argparse.ArgumentTypeError('must be a positive integer up to 2147483647')
    return int(value)


def seed_value(value):
    if not re.fullmatch(r'[+-]?[0-9]+',value) or len(value.lstrip('+-'))>19 or not -(1<<63)<=int(value)<=LONG_MAX:
        raise argparse.ArgumentTypeError('seed must be a signed 64-bit integer')
    return int(value)


def output_path(value):
    if not value.strip(): raise argparse.ArgumentTypeError('output directory is required')
    return Path(value)


def parse_args(argv=None):
    parser=argparse.ArgumentParser(description=__doc__,allow_abbrev=False,
        epilog='Requires full JDK 21, Linux/cgroup or macOS memory evidence and Python 3.11+. Deadline covers calibration, production and validation; excludes build/tests. Presets are unmeasured proposals, not completion guarantees.')
    parser.add_argument('--preset',choices=PRESETS,default='small',help='VMs/hosts: micro=50/10, tiny=100/20, small=500/100 (default), medium=2000/400, large=10000/2000, xlarge=20000/4000 (unverified opt-in); all use N30/T40/R5 unless explicitly overridden')
    for field,default in zip(FIELDS,(None,None,30,40,5)):
        parser.add_argument('--'+field,type=positive,help=f'override only this field; default: {default if default is not None else "selected size preset"}')
    parser.add_argument('--seed',type=seed_value,default=123456,help='signed 64-bit master seed (default: 123456)')
    parser.add_argument('--time-limit',type=parse_duration,default='2h',help='shared deadline: positive integer seconds or s/m/h suffix, or none (default: 2h)')
    parser.add_argument('--heap-mib',type=positive,default=1024,help='explicit maximum Java heap in MiB (default: 1024, minimum: 512); never grows automatically')
    parser.add_argument('--output-dir',type=output_path,default=ROOT/'results/stress',help='parent for unique retained output directories (default: results/stress)')
    parser.add_argument('--plain',action='store_true',help='plain progress lines instead of terminal dashboard')
    parser.add_argument('--skip-build',action='store_true',help='skip Maven clean verify and Python discovery; copy the existing packaged JAR')
    parser.add_argument('--force-build',action='store_true',help='force full build and verification instead of verified reuse')
    parser.add_argument('--workers',type=shared.worker_value,default='auto',help='independent case workers: auto or 1..32, bounded by CPU and shared heap')
    parser.add_argument('--dry-run',action='store_true',help='show effective config, counts and uncertain estimates; create no files or child processes')
    parser.add_argument('--interactive',action='store_true',help='terminal-only size and Default/Custom selector; confirm before build/run; incompatible with dry-run or matrix flags')
    arguments=list(argv if argv is not None else sys.argv[1:])
    flags=[arg.split('=',1)[0] for arg in arguments if arg.startswith('--')]
    if len(flags)!=len(set(flags)): parser.error('duplicate options are not supported')
    args=parser.parse_args(arguments)
    if args.skip_build and args.force_build: parser.error('--force-build and --skip-build are incompatible')
    if args.interactive and set(flags)&{'--preset','--dry-run',*('--'+field for field in FIELDS)}:
        parser.error('--interactive cannot be combined with --dry-run or preset/VM/host/population/iteration/replication flags')
    for field,default in zip(FIELDS,(*PRESETS[args.preset],30,40,5)):
        if getattr(args,field) is None: setattr(args,field,default)
    if args.population<2 or args.population%2: parser.error('population must be even and at least 2')
    if args.population+3*args.population*args.iterations>INT_MAX or 4*args.replications>INT_MAX:
        parser.error('resolved Java case/evaluation budget overflows 32-bit integer')
    if args.heap_mib<512: parser.error('heap must be at least 512 MiB')
    return args


def calibrations(config):
    result=[]
    for size in sorted({100,500,config.vms}):
        if size>config.vms: continue
        pilot=argparse.Namespace(**vars(config))
        pilot.vms=size
        pilot.hosts=(config.hosts*size+config.vms-1)//config.vms
        pilot.population,pilot.iterations,pilot.replications=10,10,1
        result.append(pilot)
    return result


def effective(config):
    return {field:getattr(config,field) for field in (*FIELDS,'seed','heap_mib','time_limit')}


def memory_preview(config):
    evidence={'sampled_at':shared.stamp(),'physical_memory_bytes':None,'usable_memory_bytes':None,
              'warning':None,'policy_note':None}
    for name,reader in [('physical_memory_bytes',shared.physical_memory),('usable_memory_bytes',shared.usable_memory)]:
        try: evidence[name]=reader()
        except (OSError,ValueError) as error: evidence[name+'_error']=shared.sanitize(error)
    if config.preset=='xlarge' or config.vms>=20000 or config.hosts>=4000:
        evidence['policy_note']='XLarge-scale is unverified: 8 GiB is a conservative warning threshold, not a measured minimum; >=8 GiB is not guaranteed safe.'
        evidence['conservative_physical_threshold_bytes']=8*1024**3
        if evidence['physical_memory_bytes'] is not None and evidence['physical_memory_bytes']<8*1024**3:
            evidence['warning']='WARNING: XLarge-scale on physical RAM below 8 GiB may cause severe memory pressure or OOM termination; recommend a smaller preset.'
    return evidence


def show_memory(evidence,output=None):
    output=output if output is not None else sys.stdout
    def gib(value): return f'{value/1024**3:.2f} GiB' if value is not None else 'unknown'
    source = 'macOS free/reclaimable pages' if sys.platform == 'darwin' else 'Linux/cgroup-aware'
    print(f'Memory: physical: {gib(evidence["physical_memory_bytes"])}; usable: {gib(evidence["usable_memory_bytes"])} ({source})',file=output)
    if evidence['warning']: print(evidence['warning'],file=output)
    if evidence['policy_note']: print(evidence['policy_note'],file=output)


def estimate(config,pilot_measurements):
    budget=config.population+3*config.population*config.iterations
    result={'expected_cases':4*config.replications,'optimizer_evaluations_per_case':budget,
            'expected_evaluations':config.replications*(2*budget+2),
            'approximate_vm_host_evaluation_work':2*config.replications*budget*config.vms*config.hosts,
            'population_gene_bytes_lower_bound':config.population*config.vms*8,
            'estimated_wall_seconds':None,'estimated_output_bytes':None,
            'observed_pilots':pilot_measurements,
            'uncertainty':'Population genes are a lower bound only; native objects/arrays and trace bytes are unknown before pilots. Scaling is approximate, not a feasibility/ETA/completion guarantee.'}
    if pilot_measurements:
        # ponytail: pilot ratios are approximate; use measured scale models if forecasts matter.
        pilot=pilot_measurements[-1]; pc=pilot['effective_config']
        pilot_budget=pc['population']+3*pc['population']*pc['iterations']
        evaluation_ratio=config.replications*budget/pilot_budget
        workload_ratio=config.vms*config.hosts/(pc['vms']*pc['hosts'])
        result['approximate_evaluation_ratio']=evaluation_ratio
        result['approximate_workload_ratio']=workload_ratio
        result['estimated_wall_seconds']=pilot['wall_seconds']*evaluation_ratio*workload_ratio
        result['estimated_output_bytes']=math.ceil(pilot['output_bytes']*evaluation_ratio*config.vms/pc['vms'])
    return result


def planned_work(config):
    # Previews and retained metadata must count the same mandatory calibration blocks.
    def block(config):
        totals=estimate(config,[])
        return {'effective_config':effective(config),
                **{field:totals[field] for field in ('expected_cases','expected_evaluations')}}
    pilots=[block(pilot) for pilot in calibrations(config)]
    production=block(config)
    return {'calibration':pilots,'production':production,
            'combined_totals':{field:sum(b[field] for b in [*pilots,production])
                               for field in ('expected_cases','expected_evaluations')}}


def show_planned_work(plan,output=None):
    output=output if output is not None else sys.stdout
    for label,block in [('Calibration',p) for p in plan['calibration']]+[('Production',plan['production'])]:
        c=block['effective_config']
        print(f'{label}: V{c["vms"]}/H{c["hosts"]}/N{c["population"]}/T{c["iterations"]}/R{c["replications"]}; '
              f'{block["expected_cases"]} cases / {block["expected_evaluations"]} evaluations',file=output)
    totals=plan['combined_totals']
    print(f'Combined totals: {totals["expected_cases"]} cases / {totals["expected_evaluations"]} evaluations',file=output)


def java_command(java,config,jar,output,phase,*,workers=1):
    command=shared.java_command(java,config.heap_mib,jar,output,workers=workers)
    command[command.index('--profile')+1]='stress'
    command[command.index('--output-dir')+1]=str(output)
    for field in FIELDS: command+=['--'+field,str(getattr(config,field))]
    return command+['--seed',str(config.seed),'--experiment-phase',phase]


def completed_run(parent,digest,config,phase):
    paths=list(parent.iterdir())
    if len(paths)!=1 or not paths[0].is_dir() or paths[0].is_symlink():
        raise ValueError('Expected exactly one new stress artifact')
    run=paths[0]; data=json.loads((run/'run.json').read_text())
    if (data.get('experiment_kind')!='static_stress' or data.get('stress_schema_version')!=1
            or 'schema_version' in data or data.get('profile')!='stress' or data.get('experiment_phase')!=phase
            or data.get('state')!='COMPLETE' or data.get('error') is not None or data.get('artifact_sha256')!=digest):
        raise ValueError('Stress artifact is incomplete, unsuccessful or mismatched')
    totals=estimate(config,[])
    for name,value in [('expected_cases',totals['expected_cases']),('attempted_cases',totals['expected_cases']),
                       ('successful_cases',totals['expected_cases']),('failed_cases',0),('unattempted_cases',0),
                       ('expected_evaluations',totals['expected_evaluations']),('completed_evaluations',totals['expected_evaluations'])]:
        if type(data.get(name)) is not int or data[name]!=value: raise ValueError('Incorrect stress completion counters')
    for key,field in [('vm.count','vms'),('host.count','hosts'),('population','population'),('iterations','iterations'),
                      ('replications','replications'),('master.seed','seed')]:
        if data['effective_config'].get(key)!=str(getattr(config,field)): raise ValueError('Resolved stress config differs from artifact')
    return run


def progress(parent,config,deadline,estimates):
    remaining_seconds=None if deadline is None else max(0,deadline-time.monotonic())
    remaining='none' if remaining_seconds is None else f'{remaining_seconds:.0f}s'
    timing={'deadline_remaining':remaining_seconds,'deadline_unlimited':deadline is None}
    extra=[f'V{config.vms}/H{config.hosts}/N{config.population}/T{config.iterations}/R{config.replications}; deadline remaining {remaining}']
    if estimates['observed_pilots']:
        pilot=estimates['observed_pilots'][-1]
        peak=pilot.get('sampled_peak_rss_bytes')
        extra.append(f'Pilot: {pilot["wall_seconds"]:.2f}s RSS {peak//shared.MIB if peak else "unknown"}MiB bytes {pilot["output_bytes"]}')
        extra.append(f'Approx work x{estimates["approximate_workload_ratio"]:.2g}/eval x{estimates["approximate_evaluation_ratio"]:.2g}, {estimates["estimated_wall_seconds"]:.0f}s (uncertain)')
    else: extra.append('Pilot estimates: unknown until measured; no completion guarantee')
    try:
        paths=list(parent.glob('stress-*/progress.json'))
        if len(paths)!=1: raise ValueError('progress unavailable')
        data=json.loads(paths[0].read_text()); done=data['successful_cases']; total=4*config.replications
        if type(done) is not int or not 0<=done<=total: raise ValueError('invalid progress')
        active=data.get('current_case')
        if active is not None and not isinstance(active,dict): raise ValueError('invalid active case')
        evaluations,expected=data['completed_evaluations'],data['expected_evaluations']
        if any(type(value) is not int or value<0 for value in (evaluations,expected)) or evaluations>expected:
            raise ValueError('invalid evaluation counters')
        detail=f'{active.get("algorithm","?")} replication {active.get("replication","?")} evaluation {active.get("evaluation","?")}; ' if active else ''
        if active and active.get('computing_phase'):
            detail=f'{active.get("algorithm","?")} replication {active.get("replication","?")}: {active["computing_phase"]}'
            if active.get('computing_evaluation') is not None:
                detail+=f' computing evaluation {active["computing_evaluation"]}, iteration {active.get("computing_iteration","?")} ({active.get("computing_stage","?")})'
            detail+='; published '
        detail+=f'evaluations {data["completed_evaluations"]}/{data["expected_evaluations"]}'
        return {'done':done,'total':total,'detail':detail,'extra_lines':extra,
                'evaluations':evaluations,'evaluations_total':expected,**timing,
                **{key:(active or {}).get(key) for key in ('algorithm','scenario','replication','phase','evaluation')}}
    except (OSError,ValueError,KeyError,TypeError):
        return {'total':4*config.replications,'detail':'Progress unavailable (logs retained)','extra_lines':extra,**timing}


def gc_measurements(directory):
    pauses=[]
    for log in directory.glob('gc.log*'):
        with log.open(errors='replace') as source:
            for line in source:
                match=re.search(r'\[gc\s*\].*Pause .* (\d+)M->(\d+)M\(\d+M\) ([\d.]+)ms',line)
                if match: pauses.append((int(match[1]),int(match[2]),float(match[3])))
    return {'gc_pause_count':len(pauses),'gc_pause_seconds':sum(p[2] for p in pauses)/1000,
            'max_gc_heap_before_mib':max((p[0] for p in pauses),default=None),
            'max_gc_heap_after_mib':max((p[1] for p in pauses),default=None)}


def select_interactive(arguments,read=input,output=None):
    output=output if output is not None else sys.stdout
    def ask(prompt):
        value=read(prompt).strip()
        if value.lower() in ('q','quit'): raise EOFError('Cancelled')
        return value
    def choice(prompt,allowed,default):
        while True:
            value=ask(prompt).lower() or default
            if value in allowed: return value
            print('Invalid choice; use a listed number or q to cancel.',file=output)
    def number(label,default,limit,even=False):
        while True:
            value=ask(f'{label} [{default}]: ') or str(default)
            try:
                result=positive(value)
                if result>limit or (even and (result<2 or result%2)):
                    raise argparse.ArgumentTypeError(f'maximum {limit}'+('; even and at least 2' if even else ''))
                return result
            except argparse.ArgumentTypeError as error: print(f'Invalid {label}: {error}',file=output)
    print('STATIC STRESS — choose size (VMs/hosts); q cancels at any prompt.',file=output)
    names=list(PRESETS)
    for index,name in enumerate(names,1):
        v,h=PRESETS[name]
        print(f'{index}. {name}: {v}/{h}'+(' (unverified opt-in)' if name=='xlarge' else ''),file=output)
    preset=names[int(choice('Size [3]: ',{str(i) for i in range(1,len(names)+1)},'3'))-1]
    print('1. Default: N30/T40/R5 optimizer effort.\n2. Custom: set candidates N, iterations T and paired replications R.',file=output)
    mode=choice('Effort [1]: ',{'1','2'},'1')
    resolved=[arg for arg in arguments if arg!='--interactive']+['--preset',preset]
    if mode=='2':
        n=number('Population N',30,INT_MAX//4,even=True)
        t=number('Iterations T',40,(INT_MAX-n)//(3*n))
        r=number('Replications R',5,INT_MAX//4)
        resolved+=['--population',str(n),'--iterations',str(t),'--replications',str(r)]
    args=parse_args(resolved)
    choices={'preset':preset,'mode':'default' if mode=='1' else 'custom',
             'population':args.population,'iterations':args.iterations,'replications':args.replications}
    print(f'Effective: V{args.vms}/H{args.hosts}/N{args.population}/T{args.iterations}/R{args.replications}; seed {args.seed}; heap {args.heap_mib} MiB',file=output)
    show_planned_work(planned_work(args),output)
    print(f'Deadline: {args.time_limit if args.time_limit is not None else "none"} seconds (calibration + production + validation); output: {shared.sanitize(args.output_dir.resolve())}',file=output)
    show_memory(memory_preview(args),output)
    if choice('Start build/calibration/run? [y/N]: ',{'y','yes','n','no'},'n') in ('n','no'):
        raise EOFError('Cancelled')
    return args,choices


def main(argv=None, *, dashboard=None, on_complete=None, invocation=None) -> int:
    arguments=list(argv if argv is not None else sys.argv[1:]); args=parse_args(arguments)
    menu_choices=None
    if args.interactive and dashboard is not None:
        control=shared.ProcessControl(dashboard,ROOT)
        metadata={'started_at':shared.stamp(),'status':'failed','profile':'stress','validation':'NOT_RUN',
                  'arguments':arguments,'error':'Legacy --interactive requires the standalone runner; configure embedded runs in the launcher.'}
        if invocation is not None: metadata['invocation']=deepcopy(invocation)
        try: control.render('failed',{'detail':metadata['error']},force=True)
        except shared.PresentationError as error: metadata['error']=shared.sanitize(error)
        return shared.finish(control,metadata,2,on_complete=on_complete)
    if args.interactive:
        if not sys.stdin.isatty() or not sys.stdout.isatty():
            print('ERROR: --interactive requires terminal stdin and stdout',file=sys.stderr)
            return 2
        try: args,menu_choices=select_interactive(arguments)
        except EOFError:
            print('Cancelled; no run started.'); return 0
        except KeyboardInterrupt:
            print('\nCancelled by Ctrl-C; no run started.'); return 130
    metadata={'started_at':shared.stamp(),'status':'preflight','exit_code':None,'profile':'stress',
              'arguments':arguments,'effective_config':effective(args),'validation':'NOT_RUN','tests':'NOT_RUN','calibration':[],
              'menu_choices':menu_choices,
              'memory_evidence':memory_preview(args),
              'preset_notice':'xlarge is an unverified opt-in size; no runtime or resource guarantee' if args.preset=='xlarge' else None,
              'time_limit_scope':'calibration + production + all validation; excludes build/tests',
              'skip_build':args.skip_build,'estimates':estimate(args,[]),'estimates_scope':'production',
              'planned_work':planned_work(args)}
    metadata['execution']=shared.execution_settings(args.workers,args.heap_mib)
    if invocation is not None: metadata['invocation']=deepcopy(invocation)
    supplied_dashboard=dashboard is not None
    dashboard=dashboard if supplied_dashboard else shared.Dashboard(plain=args.plain,heap_mib=args.heap_mib,title='STATIC STRESS',total=4*args.replications)
    control=shared.ProcessControl(dashboard,ROOT)
    if args.dry_run:
        code=0
        try:
            preview=io.StringIO()
            show_memory(metadata['memory_evidence'],preview)
            show_planned_work(metadata['planned_work'],preview)
            print(json.dumps(metadata,indent=2),file=preview)
            print('Java command template: '+shlex.join(java_command(Path('JAVA_HOME/bin/java'),args,Path('RETAINED/stress.jar'),Path('RETAINED/production/artifacts'),'stress')),file=preview)
            if supplied_dashboard: control.render('dry-run',{'detail':preview.getvalue()},force=True)
            else: print(preview.getvalue(),end='')
            metadata['status']='dry-run'
        except shared.PresentationError as error:
            code=1; metadata.update(status='failed',error=shared.sanitize(error))
        return shared.finish(control,metadata,code,on_complete=on_complete)
    control.install()
    outer=console=None; code=1; deadline=None
    try:
        args.output_dir=args.output_dir.resolve(); args.output_dir.mkdir(parents=True,exist_ok=True)
        outer=Path(tempfile.mkdtemp(prefix='stress-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-',dir=args.output_dir))
        control.root=outer; console=(outer/'console.log').open('a'); dashboard.console=console
        metadata['output_directory']=str(outer); shared.save_metadata(outer,metadata)
        if menu_choices is None:
            if supplied_dashboard:
                preview=io.StringIO(); show_memory(metadata['memory_evidence'],preview)
                control.render('preflight',{'detail':preview.getvalue()},force=True)
            else: show_memory(metadata['memory_evidence'])
        java,metadata['usable_memory_bytes']=shared.check_environment(control,args.heap_mib)
        metadata.update(output_directory=str(outer),java=str(java)); shared.save_metadata(outer,metadata)
        for command,name in [(['status','--porcelain'],'source-status'),(['rev-parse','HEAD'],'source-revision')]:
            code=control.run(['git',*command],outer/f'{name}.log','provenance',cwd=ROOT,timeout=10,stderr_log=outer/f'{name}.stderr.log')
            if code: raise ValueError(f'Git provenance failed; see {outer}/{name}.stderr.log')
        metadata.update(source_revision=(outer/'source-revision.log').read_text().strip(),
                        source_status=(outer/'source-status.log').read_text(),source_dirty=bool((outer/'source-status.log').read_text()))
        retained,artifact_source=shared.prepare_artifact(args,metadata,control,outer,'stress',root=ROOT)
        shared.save_metadata(outer,metadata)
        digest=shared.sha256(retained)
        config_lines=[f'Time limit: {args.time_limit if args.time_limit is not None else "none"} seconds; heap {args.heap_mib} MiB',
                      'Deadline: calibration + production + validation; excludes build/tests',
                      f'Production workers: {metadata["execution"]["workers"]}; shared heap {args.heap_mib} MiB. Calibration uses one worker.']
        if metadata['preset_notice']: config_lines.append(metadata['preset_notice'])
        control.render('configuration',{'detail':f'V{args.vms}/H{args.hosts}/N{args.population}/T{args.iterations}/R{args.replications}; seed {args.seed}',
                                         'extra_lines':config_lines},force=True)
        metadata.update(artifact_sha256=digest,artifact_source=str(artifact_source),deadline_started_at=shared.stamp())
        deadline=time.monotonic()+args.time_limit if args.time_limit is not None else None
        measurements=[]
        blocks=[(pilot,'stress_calibration',f'calibration-{pilot.vms}') for pilot in calibrations(args)]+[(args,'stress','production')]
        for config,phase,name in blocks:
            control.check(deadline); shared.require_memory(config.heap_mib)
            block=outer/name; block.mkdir(); parent=block/'artifacts'
            workers=1 if phase=='stress_calibration' else metadata['execution']['workers']
            command=java_command(java,config,retained,parent,phase,workers=workers)
            record={'phase':phase,'effective_config':effective(config),'workers':workers,'java_command':command,'validation':'NOT_RUN','directory':str(block)}
            if phase=='stress_calibration': metadata['calibration'].append(record)
            else: metadata['production']=record; metadata['java_command']=command
            metadata['status']=name; shared.save_metadata(outer,metadata)
            estimates=estimate(args,measurements)
            reader=lambda: progress(parent,config,deadline,estimates)
            control.render(name,reader(),force=True)
            code=control.run(command,block/'java.log',name,cwd=block,timeout=None,deadline=deadline,progress_reader=reader)
            record.update(control.last_measurement)
            shared.save_metadata(outer,metadata)
            if code: raise ValueError(f'{name} child failed with exit {code}; retained: {block}')
            run=completed_run(parent,digest,config,phase); record['run_directory']=str(run)
            if phase=='stress': metadata['run_directory']=str(run)
            if shared.sha256(retained)!=digest: raise ValueError('Retained JAR changed after launch')
            record['output_bytes']=sum(path.stat().st_size for path in block.rglob('*') if path.is_file())
            record['gc_log_files']=[str(path) for path in block.glob('gc.log*')]
            record.update(gc_measurements(block))
            record['gc_observation_scope']='Observed retained rolling GC logs (3 x 5MiB); counts/maxima may omit rotated history and are not whole-run guarantees'
            metadata['status']=name+'-validation'; shared.save_metadata(outer,metadata)
            shared.require_memory(512); control.check(deadline)
            code=control.run([sys.executable,'-B',str(ROOT/'scripts/stress_validator.py'),str(run)],block/'validation.log',name+'-validation',cwd=ROOT,timeout=None,deadline=deadline,progress_reader=reader)
            if code: raise ValueError(f'Independent {name} validation failed with exit {code}; see {block/"validation.log"}')
            control.check(deadline); completed_run(parent,digest,config,phase)
            if shared.sha256(retained)!=digest: raise ValueError('Retained JAR changed during validation')
            record['validation']='PASS'; record['validation_wall_seconds']=control.last_measurement['wall_seconds']
            artifact=json.loads((run/'run.json').read_text())
            record.update(artifact_revision=artifact.get('git_revision'),artifact_dirty=artifact.get('git_dirty'),max_heap_bytes=artifact.get('max_heap_bytes'))
            if phase=='stress_calibration': measurements.append(record); metadata['estimates']=estimate(args,measurements)
            shared.save_metadata(outer,metadata)
        control.check(deadline)
        diagnostic=metadata['source_dirty'] or args.skip_build or record['artifact_dirty'] is not False or record['artifact_revision']!=metadata['source_revision']
        metadata.update(status='complete',validation='PASS',diagnostic=diagnostic,
                        artifact_revision=record['artifact_revision'],artifact_dirty=record['artifact_dirty'])
        control.render('complete',{'done':4*args.replications,'detail':f'VALIDATED: {4*args.replications} paired static stress cases; descriptive results only. Retained: {outer}'},force=True)
        code=0
    except shared.Interrupted as error:
        code=128+error.signum; metadata.update(status='interrupted',error=f'Interrupted by signal {error.signum}')
    except shared.DeadlineExceeded as error:
        code=124; metadata.update(status='timeout',error=str(error))
    except (OSError,ValueError,KeyError,subprocess.SubprocessError) as error:
        code=getattr(error,'exit_code',0) or (code if code not in (0,1) else 1); metadata.update(status='failed',error=shared.sanitize(error))
    finally:
        if metadata['status'] in ('failed','timeout','interrupted'):
            try: control.render(metadata['status'],{'detail':metadata['error']+f'; retained: {outer}'},force=True)
            except shared.PresentationError: pass
        code=shared.finish(control,metadata,code,outer,console,on_complete)
        if outer and not supplied_dashboard:
            try: print(f'Retained results: {shared.sanitize(outer)}')
            except (OSError,ValueError): pass
    return code


if __name__=='__main__': sys.exit(main())
