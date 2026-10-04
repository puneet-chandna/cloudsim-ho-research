#!/usr/bin/env python3
"""Independent, streaming static stress-schema-1 verification (stdlib only).

Final feasibility/objective and static energy/SLA are independently derived.
Scalar traces establish budgets/order/eligible-best consistency, not optimizer
replay; coherently changed discarded-candidate scalars may remain undetectable.
Provenance metadata records artifact identity, not artifact authenticity.
Numerical tolerance: abs=1e-10, rel=2e-12, as in frozen schema-2 verification.
"""
import argparse
import csv
from datetime import datetime
import hashlib
import json
import math
from pathlib import Path
import re
import sys

from statistics_validator import JavaRandom, seed, require, close, finite

ALGORITHMS=['HO','GA','FirstFit','BestFit']
ID='stress_schema_version,run_id,phase,scenario,replication,algorithm,population,iterations'
CASE=ID+',scenario_seed,workload_seed,optimizer_seed,scenario_sha256,status,error_code,error_message,vm_count,host_count,evaluations,objective_w,energy_j,energy_kwh,sla_violations,sla_rate,completed_cloudlets,failed_cloudlets,censored_cloudlets,horizon_s,release_s,allocation_wall_ns'
PLACE=ID+',vm_id,host_id'
TRACE=ID+',evaluation,iteration,stage,candidate_index,feasible,fitness_w,accepted,best_fitness_w'
SUMMARY='stress_schema_version,run_id,phase,scenario,algorithm,n,objective_mean_w,energy_mean_j,sla_mean,allocation_mean_ns'
EVIDENCE_LIMIT='Scalar traces check order, budgets and eligible-best consistency; they cannot independently replay discarded candidates or guarantee detection of coherent non-best scalar tampering.'
ALLOCATION_TIMING='End-to-end search including synchronous buffered scalar trace writing; not pure CPU or directly comparable to frozen allocation timing.'


def digest(path):
    result=hashlib.sha256()
    with path.open('rb') as file:
        for block in iter(lambda:file.read(1024*1024),b''):result.update(block)
    return result.hexdigest()


def unique(pairs):
    result={}
    for key,value in pairs:
        require(key not in result,f'Duplicate JSON key: {key}')
        result[key]=value
    return result


def load(path):
    with path.open(encoding='utf-8') as file:
        result=json.load(file,object_pairs_hook=unique,parse_constant=lambda value:require(False,f'Nonfinite JSON: {value}'))
    require(isinstance(result,dict),f'Expected JSON object: {path}')
    return result


def rows(path,header):
    """Iterate one CSV row at a time, checking LF/final newline without a file copy."""
    with path.open(encoding='utf-8',newline='') as file:
        def lines():
            for line in file:
                require('\r' not in line and line.endswith('\n'),f'CSV must use LF and final newline: {path.name}')
                yield line
        try:
            reader=csv.DictReader(lines(),strict=True)
            require(reader.fieldnames==header.split(','),f'Wrong CSV schema: {path.name}')
            for row in reader:
                require(None not in row and None not in row.values(),f'Ragged CSV: {path.name}')
                yield row
        except csv.Error as error:raise ValueError(f'Invalid CSV: {path.name}') from error


def integer(value,label):
    require(isinstance(value,str) and re.fullmatch(r'-?(0|[1-9][0-9]*)',value) is not None,f'Invalid integer {label}')
    return int(value)


def generated(master,phase,name,replication,vm_count,host_count):
    """Dimensions are part of seed identity; draw order matches specified Java RNG."""
    ss,ws=seed(master,phase,name,replication,'scenario'),seed(master,phase,name,replication,'workload')
    hosts=[dict(id=i,pes=16,mipsPerPe=3000,ramMiB=32768,bwMbps=10000,storageMiB=1000000,idleW=[175,210,280][i%3],maxW=[250,300,400][i%3]) for i in range(host_count)]
    scenario,workload=JavaRandom(ss),JavaRandom(ws)
    vms=[];cloudlets=[];references=[]
    for i in range(vm_count):
        pes,mips,ram=1+scenario.integer(2),1000*(1+scenario.integer(3)),1024<<scenario.integer(3)
        duration=60+workload.integer(61)
        vms.append(dict(id=i,pes=pes,mipsPerPe=mips,ramMiB=ram,bwMbps=1000,storageMiB=10000))
        cloudlets.append(dict(id=i,vmId=i,pes=pes,lengthMi=mips*duration));references.append(float(duration))
    inputs=dict(seeds=dict(master=master,phase=phase,scenario=name,replication=replication,scenarioSeed=ss,workloadSeed=ws),hosts=hosts,vms=vms,cloudlets=cloudlets)
    lines=['kind,id,pes,mips_per_pe,ram_mib,bw_mbps,storage_mib,idle_w,max_w,vm_id,length_mi']
    for host in hosts:lines.append(','.join(map(str,['host',host['id'],16,3000,32768,10000,1000000,host['idleW'],host['maxW'],'',''])))
    for vm in vms:lines.append(','.join(map(str,['vm',vm['id'],vm['pes'],vm['mipsPerPe'],vm['ramMiB'],1000,10000,'','','',''])))
    for cloudlet in cloudlets:lines.append(','.join(map(str,['cloudlet',cloudlet['id'],cloudlet['pes'],'','','','','','',cloudlet['vmId'],cloudlet['lengthMi']])))
    keys=dict(cloudlet_scheduler='CloudletSchedulerSpaceShared',host_scheduler='VmSchedulerSpaceShared',min_event_interval_s='0.125',power_policy='used_on_until_horizon',power_curve='linear',release_s='0.0',utilization_model='constant_full',censor_s=str(10*max(references)))
    keys.update({f'reference.{i}':str(value) for i,value in enumerate(references)})
    canonical='\n'.join(lines+['key,value']+[f'{key},{keys[key]}' for key in sorted(keys)])+'\n'
    return dict(inputs=inputs,reference_seconds=references,censor_s=10*max(references),canonical_text=canonical,scenario_sha256=hashlib.sha256(canonical.encode()).hexdigest())


def placement_metrics(spec,placements):
    inputs=spec['inputs']; hosts=inputs['hosts'];vms=inputs['vms'];refs=spec['reference_seconds']
    used=[[0]*5 for _ in hosts]; work=[0.0]*len(hosts)
    require(len(placements)==len(vms),'Wrong final placement count')
    for vm,host_id,duration in zip(vms,placements,refs):
        require(0<=host_id<len(hosts),'Unknown final host')
        host=hosts[host_id]
        require(vm['mipsPerPe']<=host['mipsPerPe'],'Final per-PE MIPS infeasible')
        demand=[vm['pes'],vm['pes']*vm['mipsPerPe'],vm['ramMiB'],vm['bwMbps'],vm['storageMiB']]
        capacity=[host['pes'],host['pes']*host['mipsPerPe'],host['ramMiB'],host['bwMbps'],host['storageMiB']]
        for dimension in range(5):
            used[host_id][dimension]+=demand[dimension]
            require(used[host_id][dimension]<=capacity[dimension],'Final placement infeasible')
        work[host_id]+=demand[1]*duration
    horizon=max(refs);watts=energy=0.0
    for host,reserved,integral in zip(hosts,used,work):
        if reserved[1]:
            slope=host['maxW']-host['idleW']
            watts+=host['idleW']+slope*(reserved[1]/48000.0)
            energy+=host['idleW']*horizon+slope/48000.0*integral
    return watts,energy,horizon


def trace_order(algorithm,n,t):
    for member in range(n):yield 0,'INITIAL',member
    if algorithm=='GA':
        for offset in range(3*n*t):yield 1+offset//(n-1),'CHILD',1+offset%(n-1)
    else:
        for iteration in range(1,t+1):
            for member in range(n//2):
                yield iteration,'RIVER_MALE',member;yield iteration,'RIVER_FEMALE',member
            for member in range(n//2,n):
                yield iteration,'PREDATOR',member;yield iteration,'DEFENSE',member
            for member in range(n):yield iteration,'ESCAPE',member


def take(iterator,label):
    value=next(iterator,None)
    require(value is not None,f'Missing {label}')
    return value


def identity(row,expected):
    require([row[key] for key in ID.split(',')]==expected,'Wrong/duplicate/reordered case identity')


def validate(directory):
    """Raise ValueError unless the exact complete matrix and durable evidence validate."""
    try:_validate(Path(directory))
    except (OSError,KeyError,TypeError,OverflowError,UnicodeError,json.JSONDecodeError) as error:
        raise ValueError(f'Invalid or missing stress evidence: {error}') from error


def _validate(directory):
    m=load(directory/'run.json')
    require(m.get('experiment_kind')=='static_stress' and m.get('stress_schema_version')==1 and 'schema_version' not in m,'Requires isolated stress schema 1')
    require(m['state']=='COMPLETE' and m['profile']=='stress' and m['error'] is None,'Requires COMPLETE successful stress run')
    phase=m['experiment_phase'];require(phase in ('stress','stress_calibration'),'Invalid stress phase')
    run_id=m['run_id'];require(isinstance(run_id,str) and re.fullmatch(r'stress-[A-Za-z0-9-]+',run_id),'Invalid run identity')
    config=m['effective_config'];require(isinstance(config,dict),'Missing effective config')
    v,h,n,t,reps,master=[integer(config[key],key) for key in ['vm.count','host.count','population','iterations','replications','master.seed']]
    require(0<v<=2147483647 and 0<h<=2147483647 and 2<=n<=2147483647 and n%2==0 and 0<t<=2147483647 and 0<reps<=2147483647 and -(1<<63)<=master<1<<63,'Invalid stress dimensions/seed')
    budget=n+3*n*t;require(budget<=2147483647 and 4*reps<=2147483647,'Overflow stress budgets')
    name=f'Static-V{v}-H{h}'
    expected={'profile':'stress','experiment.kind':'static_stress','stress.schema.version':'1','experiment.phase':phase,'vm.count':str(v),'host.count':str(h),'population':str(n),'iterations':str(t),'replications':str(reps),'master.seed':str(master),'evaluation.budget':str(budget),'expected.cases':str(4*reps),'scenarios':name,'algorithms':','.join(ALGORITHMS)}
    require(config==expected and type(m['master_seed']) is int and m['master_seed']==master,'Wrong effective configuration')
    effective=''.join(f'{key}={expected[key]}\n' for key in sorted(expected))
    require((directory/'effective.properties').read_bytes()==effective.encode(),'Altered effective.properties')
    counters=dict(expected_cases=4*reps,attempted_cases=4*reps,successful_cases=4*reps,failed_cases=0,unattempted_cases=0,expected_evaluations=reps*(2*budget+2),completed_evaluations=reps*(2*budget+2))
    for key,value in counters.items():require(type(m[key]) is int and m[key]==value,f'Wrong {key}')
    progress=load(directory/'progress.json')
    progress_expected={key:m[key] for key in ['experiment_kind','stress_schema_version','run_id','state','error']+list(counters)};progress_expected['current_case']=None
    require(progress==progress_expected,'Wrong complete progress state')
    require(m['evidence_limit']==EVIDENCE_LIMIT and m['allocation_timing']==ALLOCATION_TIMING,'Missing evidence/timing limits')
    require(datetime.fromisoformat(m['finished_at'])>=datetime.fromisoformat(m['started_at']),'Invalid run times')
    require(m['cloudsim_version']=='8.5.7' and type(m['git_dirty']) is bool and type(m['max_heap_bytes']) is int and m['max_heap_bytes']>0,'Invalid provenance')
    for field in ['source_version','java_version','os','arch']:require(isinstance(m[field],str) and m[field],f'Missing provenance {field}')
    if m['artifact_sha256'] is None:
        require(m['source_version']=='development (unpackaged)' and m['git_revision']=='unknown' and m['git_dirty'],'Unlabeled unpackaged artifact')
    else:
        require(isinstance(m['artifact_sha256'],str) and re.fullmatch('[a-f0-9]{64}',m['artifact_sha256']) and isinstance(m['git_revision'],str) and re.fullmatch('[a-f0-9]{40}',m['git_revision']),'Invalid artifact provenance')
    files={'effective.properties','raw/cases.csv','raw/placements.csv','raw/evaluations.csv','analysis/summary.csv'}|{f'scenarios/replication-{r}.json' for r in range(reps)}
    require(isinstance(m['files'],dict) and set(m['files'])==files,'Wrong required files')
    for file in files:require(m['files'][file]==digest(directory/file),f'Hash mismatch: {file}')
    summaries={algorithm:[0,0.0,0.0,0.0,0.0] for algorithm in ALGORITHMS}
    case_rows=rows(directory/'raw/cases.csv',CASE);placement_rows=rows(directory/'raw/placements.csv',PLACE);trace_rows=rows(directory/'raw/evaluations.csv',TRACE)
    try:
        for replication in range(reps):
            spec=generated(master,phase,name,replication,v,h)
            require(load(directory/f'scenarios/replication-{replication}.json')==spec,'Seed/dimension/canonical specification mismatch')
            # A rejected constructive witness must not be silently reclassified as a successful run.
            placement_metrics(spec,[index%h for index in range(v)])
            for algorithm in ALGORITHMS:
                search=algorithm in ALGORITHMS[:2]
                expected_id=['1',run_id,phase,name,str(replication),algorithm,str(n) if search else '',str(t) if search else '']
                row=take(case_rows,'case');identity(row,expected_id)
                require(row['status']=='RUN_OK' and row['error_code']==row['error_message']=='','Invalid case status')
                seeds=spec['inputs']['seeds']
                require(row['scenario_seed']==str(seeds['scenarioSeed']) and row['workload_seed']==str(seeds['workloadSeed'])
                        and row['optimizer_seed']==(str(seed(master,phase,name,replication,'optimizer',algorithm)) if search else '')
                        and row['scenario_sha256']==spec['scenario_sha256'],'Seed/spec/hash mismatch')
                for field,value in [('vm_count',v),('host_count',h),('evaluations',budget if search else 1),('sla_violations',0),('completed_cloudlets',v),('failed_cloudlets',0),('censored_cloudlets',0)]:
                    require(integer(row[field],field)==value,f'Wrong {field}')
                plan=[]
                for vm in range(v):
                    placement=take(placement_rows,'placement');identity(placement,expected_id)
                    require(integer(placement['vm_id'],'vm_id')==vm,'Wrong/duplicate/reordered VM placement')
                    plan.append(integer(placement['host_id'],'host_id'))
                watts,energy,horizon=placement_metrics(spec,plan)
                for field,value in [('objective_w',watts),('energy_j',energy),('energy_kwh',energy/3600000),('horizon_s',horizon),('release_s',.125),('sla_rate',0)]:close(row[field],value,field)
                nanos=integer(row['allocation_wall_ns'],'allocation_wall_ns');require(0<=nanos<1<<63,'Invalid allocation time')
                if search:
                    best=None;incumbents=[None]*n
                    for evaluation,(iteration,stage,member) in enumerate(trace_order(algorithm,n,t),1):
                        trace=take(trace_rows,'evaluation');identity(trace,expected_id)
                        require((integer(trace['evaluation'],'evaluation'),integer(trace['iteration'],'iteration'),trace['stage'],integer(trace['candidate_index'],'candidate_index'))==(evaluation,iteration,stage,member),'Wrong scalar evaluation sequence/budget')
                        require(trace['feasible'] in ('true','false') and trace['accepted'] in ('true','false'),'Invalid scalar flags')
                        fitness=finite(trace['fitness_w']) if trace['fitness_w'] else None
                        require((fitness is not None)==(trace['feasible']=='true') and (fitness is None or fitness>0),'Invalid scalar fitness/feasibility')
                        accepted=trace['accepted']=='true'
                        if stage in ('INITIAL','CHILD'):require(accepted,'Population insertion must be accepted')
                        elif stage=='PREDATOR':require(not accepted,'Predator cannot be eligible')
                        else:require(accepted==(fitness is not None and (incumbents[member] is None or fitness<incumbents[member])),'Wrong slot acceptance')
                        if stage=='INITIAL' or accepted and algorithm=='HO':incumbents[member]=fitness
                        if accepted and fitness is not None and (best is None or fitness<best):best=fitness
                        if best is None:require(trace['best_fitness_w']=='','Unexpected eligible best')
                        else:close(trace['best_fitness_w'],best,'eligible-best chain')
                    require(best is not None,'Missing feasible eligible best');close(row['objective_w'],best,'Final eligible trace best')
                totals=summaries[algorithm];totals[0]+=1;totals[1]+=watts;totals[2]+=energy;totals[3]+=0;totals[4]+=nanos
        for iterator,label in [(case_rows,'cases'),(placement_rows,'placements'),(trace_rows,'evaluations')]:require(next(iterator,None) is None,f'Extra/duplicate {label}')
    finally:
        for iterator in [case_rows,placement_rows,trace_rows]:iterator.close()
    summary_rows=rows(directory/'analysis/summary.csv',SUMMARY)
    try:
        for algorithm in ALGORITHMS:
            row=take(summary_rows,'summary');totals=summaries[algorithm]
            require([row[key] for key in ['stress_schema_version','run_id','phase','scenario','algorithm','n']]==['1',run_id,phase,name,algorithm,str(reps)],'Wrong summary identity/count/order')
            for field,total in zip(['objective_mean_w','energy_mean_j','sla_mean','allocation_mean_ns'],totals[1:]):close(row[field],total/reps,field)
        require(next(summary_rows,None) is None,'Extra summary')
    finally:summary_rows.close()


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('run_directory',type=Path);args=parser.parse_args()
    try:validate(args.run_directory)
    except ValueError as error:print(f'INVALID: {error}',file=sys.stderr);return 1
    print('VALID static_stress stress_schema_version=1; '+EVIDENCE_LIMIT)
    return 0


if __name__=='__main__':sys.exit(main())
