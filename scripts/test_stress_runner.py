"""Bounded runner contracts using real, controlled child processes."""
import importlib.util
from contextlib import redirect_stdout
import io
import json
import os
from pathlib import Path
import pty
import select
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

import research_runner as shared
import test_research_runner as research_tests

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('stress_runner', ROOT/'scripts/stress_runner.py')
runner = importlib.util.module_from_spec(SPEC)
if Path(SPEC.origin).exists(): SPEC.loader.exec_module(runner)


class StressRunnerChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='stress checks ')
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)

    def test_runner_exists(self):
        self.assertTrue(callable(getattr(runner,'main',None)), 'stress runner main is missing')

    def test_embedded_stress_routes_all_text_and_retains_invocation(self):
        root,env=self.fixture(); dashboard=research_tests.RecordingDashboard(); outcomes=[]; output=io.StringIO()
        previous={number:signal.getsignal(number) for number in (signal.SIGINT,signal.SIGTERM)}
        with patch.dict(os.environ,env,clear=True),patch.object(runner,'ROOT',root),patch.object(shared,'require_memory',return_value=4*1024**3),redirect_stdout(output):
            code=runner.main(['--skip-build','--vms','1','--hosts','1','--output-dir',str(self.base/'embedded')],
                dashboard=dashboard,on_complete=outcomes.append,invocation=['./cloudsim.sh','--profile','stress'])
        self.assertEqual(code,0); self.assertEqual(output.getvalue(),'')
        self.assertTrue(dashboard.closed); self.assertGreater(dashboard.polls,2)
        self.assertEqual({n:signal.getsignal(n) for n in previous},previous)
        self.assertEqual(outcomes[0]['invocation'],['./cloudsim.sh','--profile','stress'])
        self.assertEqual(outcomes[0]['status'],'complete')
        self.assertTrue(any('Memory:' in frame.get('detail','') for _,frame in dashboard.frames))

    def test_embedded_stress_dry_run_uses_presentation_without_children(self):
        dashboard=research_tests.RecordingDashboard(); output=io.StringIO(); outcomes=[]
        with redirect_stdout(output):
            code=runner.main(['--dry-run','--output-dir',str(self.base/'dry')],dashboard=dashboard,on_complete=outcomes.append)
        self.assertEqual(code,0); self.assertEqual(output.getvalue(),''); self.assertEqual(list(self.base.iterdir()),[])
        self.assertTrue(any('Combined totals:' in frame.get('detail','') for _,frame in dashboard.frames))
        self.assertTrue(dashboard.closed); self.assertEqual(outcomes[0]['exit_code'],0)

    def test_embedded_stress_presentation_failure_is_retained_as_failed(self):
        root,env=self.fixture()
        class Broken(research_tests.RecordingDashboard):
            def render(self,stage,progress=None,pid=None,force=False):
                if stage=='configuration': raise RuntimeError('screen unavailable')
                super().render(stage,progress,pid,force)
        dashboard=Broken(); outcomes=[]
        with patch.dict(os.environ,env,clear=True),patch.object(runner,'ROOT',root),patch.object(shared,'require_memory',return_value=4*1024**3),redirect_stdout(io.StringIO()):
            code=runner.main(['--skip-build','--vms','1','--hosts','1','--output-dir',str(self.base/'failed-ui')],dashboard=dashboard,on_complete=outcomes.append)
        self.assertEqual(code,1); self.assertTrue(dashboard.closed)
        self.assertEqual(outcomes[0]['status'],'failed'); self.assertIn('screen unavailable',outcomes[0]['error'])
        data=json.loads((next((self.base/'failed-ui').iterdir())/'runner.json').read_text())
        self.assertEqual(data['exit_code'],1); self.assertFalse((root/'java.started').exists())

    def test_embedded_stress_rejects_legacy_interactive_without_printing(self):
        dashboard=research_tests.RecordingDashboard(); output=io.StringIO(); outcomes=[]
        with redirect_stdout(output):
            code=runner.main(['--interactive'],dashboard=dashboard,on_complete=outcomes.append)
        self.assertEqual(code,2); self.assertEqual(output.getvalue(),'')
        self.assertEqual(outcomes[0]['status'],'failed'); self.assertTrue(dashboard.closed)

    @unittest.skipUnless(Path(SPEC.origin).exists(), 'runner implementation pending')
    def test_preset_fieldwise_overrides_and_exact_totals(self):
        config=runner.parse_args(['--preset','medium','--vms','500'])
        self.assertEqual([config.vms,config.hosts,config.population,config.iterations,config.replications,config.seed],
                         [500,400,30,40,5,123456])
        result=runner.estimate(config,[])
        self.assertEqual(result['expected_cases'],20)
        self.assertEqual(result['expected_evaluations'],36310)
        self.assertIsNone(result['estimated_wall_seconds'])
        self.assertEqual(result['population_gene_bytes_lower_bound'],120000)
        self.assertEqual(config.time_limit,7200)
        self.assertEqual([(p.vms,p.hosts,p.population,p.iterations,p.replications) for p in runner.calibrations(config)],
                         [(100,80,10,10,1),(500,400,10,10,1)])
        small=runner.parse_args([]); large=runner.parse_args(['--preset','large'])
        self.assertEqual([small.vms,small.hosts,small.population,small.iterations,small.replications],[500,100,30,40,5])
        self.assertEqual([large.vms,large.hosts,large.population,large.iterations,large.replications],[10000,2000,30,40,5])
        override=runner.parse_args(['--preset','medium','--vms','500','--population','50','--iterations','100','--replications','10'])
        self.assertEqual([override.vms,override.hosts,override.population,override.iterations,override.replications],[500,400,50,100,10])

    def test_six_size_only_tiers_have_common_effort_and_dry_run_totals(self):
        for name,vms,hosts in [('micro',50,10),('tiny',100,20),('small',500,100),('medium',2000,400),('large',10000,2000),('xlarge',20000,4000)]:
            with self.subTest(preset=name):
                config=runner.parse_args(['--preset',name])
                self.assertEqual([config.vms,config.hosts,config.population,config.iterations,config.replications,config.time_limit],
                                 [vms,hosts,30,40,5,7200])
                result=subprocess.run([str(ROOT/'run-stress.sh'),'--dry-run','--preset',name,
                                       '--output-dir',str(self.base/name)],capture_output=True,text=True)
                self.assertEqual(result.returncode,0,result.stderr)
                data=json.JSONDecoder().raw_decode(result.stdout[result.stdout.index('{'):])[0]
                self.assertEqual(data['estimates']['expected_cases'],20)
                self.assertEqual(data['estimates']['expected_evaluations'],36310)
                self.assertIn('estimates_scope',data)
                self.assertEqual(data['estimates_scope'],'production')
                self.assertEqual(data['effective_config']['vms'],vms)
                self.assertEqual(data['effective_config']['hosts'],hosts)
                plan=data['planned_work']
                pilots={'micro':[(50,10)],'tiny':[(100,20)],'small':[(100,20),(500,100)],
                        'medium':[(100,20),(500,100),(2000,400)],
                        'large':[(100,20),(500,100),(10000,2000)],
                        'xlarge':[(100,20),(500,100),(20000,4000)]}[name]
                self.assertEqual([(p['effective_config']['vms'],p['effective_config']['hosts'])
                                  for p in plan['calibration']],pilots)
                for pilot in plan['calibration']:
                    self.assertEqual([pilot['effective_config'][f] for f in ('population','iterations','replications')],[10,10,1])
                    self.assertEqual((pilot['expected_cases'],pilot['expected_evaluations']),(4,622))
                self.assertEqual((plan['production']['expected_cases'],plan['production']['expected_evaluations']),(20,36310))
                self.assertEqual(plan['combined_totals'],{
                    'expected_cases':20+4*len(pilots),'expected_evaluations':36310+622*len(pilots)})
                for v,h in pilots:
                    self.assertIn(f'Calibration: V{v}/H{h}/N10/T10/R1; 4 cases / 622 evaluations',result.stdout)
                self.assertIn('Production: ',result.stdout)
                self.assertIn('20 cases / 36310 evaluations',result.stdout)
                self.assertIn('Combined totals:',result.stdout)
                if name=='xlarge': self.assertIn('unverified',result.stdout.lower())
        self.assertEqual(list(self.base.iterdir()),[])

    def test_dry_run_custom_density_and_effort_include_all_planned_work(self):
        result=subprocess.run([str(ROOT/'run-stress.sh'),'--dry-run','--vms','750','--hosts','7',
                               '--population','2','--iterations','1','--replications','2'],capture_output=True,text=True)
        self.assertEqual(result.returncode,0,result.stderr)
        data=json.JSONDecoder().raw_decode(result.stdout[result.stdout.index('{'):])[0]
        self.assertIn('planned_work',data)
        plan=data['planned_work']
        self.assertEqual([(p['effective_config']['vms'],p['effective_config']['hosts'])
                          for p in plan['calibration']],[(100,1),(500,5),(750,7)])
        self.assertEqual((plan['production']['expected_cases'],plan['production']['expected_evaluations']),(8,36))
        self.assertEqual(plan['combined_totals'],{'expected_cases':20,'expected_evaluations':1902})
        self.assertIn('Calibration: V500/H5/N10/T10/R1; 4 cases / 622 evaluations',result.stdout)
        self.assertIn('Production: V750/H7/N2/T1/R2; 8 cases / 36 evaluations',result.stdout)

    @unittest.skipUnless(Path(SPEC.origin).exists(), 'runner implementation pending')
    def test_durations_and_overflow_cli_rejected_without_artifacts(self):
        for value,want in [('1',1),('30s',30),('2m',120),('4h',14400),('8h',28800),('none',None)]:
            self.assertEqual(runner.parse_duration(value),want)
        for arguments in [['--time-limit',v] for v in ['0','-1','1.5h','inf','9'*100]]+[
                ['--vms','2147483648'],['--seed','9223372036854775808'],['--population','3'],
                ['--iterations','2147483647'],['--replications','536870912'],['--heap-mib','9'*100],
                ['--config','x'],['--output-dir',''],['--preset','medium','--vms','1','--vms','2']]:
            with self.subTest(arguments=arguments):
                result=subprocess.run([sys.executable,'-B',str(ROOT/'scripts/stress_runner.py'),*arguments],
                                      cwd=self.base,capture_output=True,text=True)
                self.assertEqual(result.returncode,2,result.stdout+result.stderr)
        self.assertEqual(list(self.base.iterdir()),[])

    @unittest.skipUnless(Path(SPEC.origin).exists(), 'runner implementation pending')
    def test_dry_run_has_no_children_or_output_side_effects(self):
        env=dict(os.environ,JAVA_HOME='/missing jdk',JAVA_TOOL_OPTIONS='-Xmx999g')
        result=subprocess.run([str(ROOT/'run-stress.sh'),'--dry-run','--preset','medium','--vms','500',
                               '--output-dir',str(self.base/'output space')],env=env,cwd=self.base,capture_output=True,text=True)
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertIn('36310',result.stdout); self.assertIn('400',result.stdout)
        self.assertIn('unknown',result.stdout.lower()); self.assertIn('7200',result.stdout)
        self.assertEqual(list(self.base.iterdir()),[])

    def fixture(self):
        root,env=research_tests.RunnerChecks.fixture(self)
        shutil.copyfile(ROOT/'scripts/stress_runner.py',root/'scripts/stress_runner.py')
        shutil.copyfile(ROOT/'run-stress.sh',root/'run-stress.sh'); (root/'run-stress.sh').chmod(0o755)
        (root/'jdk space/bin/java').write_text('#!'+sys.executable+'\n'+'''import hashlib,json,os,pathlib,sys,time,subprocess,signal
if '-version' in sys.argv: print('openjdk version "21.0.1"'); sys.exit(0)
root=pathlib.Path(os.environ['FIXTURE_ROOT']); phase=sys.argv[sys.argv.index('--experiment-phase')+1]
assert sys.argv[sys.argv.index('--profile')+1]=='stress'
with (root/'invocations.jsonl').open('a') as out: out.write(json.dumps(sys.argv[1:])+'\\n')
if os.environ.get('MODE')=='linger':
 signal.signal(signal.SIGTERM,signal.SIG_IGN)
 child=subprocess.Popen([sys.executable,'-c','import signal,time; signal.signal(signal.SIGTERM,signal.SIG_IGN); time.sleep(60)'])
 (root/'descendant.pid').write_text(str(child.pid)); time.sleep(60)
time.sleep(float(os.environ.get('JAVA_DELAY','0')))
output=pathlib.Path(sys.argv[sys.argv.index('--output-dir')+1]); run=output/'stress-one'; run.mkdir(parents=True)
jar=pathlib.Path(sys.argv[sys.argv.index('-jar')+1]); n=int(sys.argv[sys.argv.index('--population')+1]); t=int(sys.argv[sys.argv.index('--iterations')+1]); r=int(sys.argv[sys.argv.index('--replications')+1])
config={k:str(sys.argv[sys.argv.index(flag)+1]) for k,flag in [('vm.count','--vms'),('host.count','--hosts'),('population','--population'),('iterations','--iterations'),('replications','--replications'),('master.seed','--seed')]}
data=dict(experiment_kind='static_stress',stress_schema_version=1,profile='stress',experiment_phase=phase,state='COMPLETE',expected_cases=4*r,attempted_cases=4*r,successful_cases=4*r,failed_cases=0,unattempted_cases=0,expected_evaluations=r*(2*(n+3*n*t)+2),completed_evaluations=r*(2*(n+3*n*t)+2),error=None,git_revision='old-artifact',git_dirty=False,artifact_sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),effective_config=config,current_case=None)
(run/'run.json').write_text(json.dumps(data)); (run/'progress.json').write_text(json.dumps(data))
print('completed '+phase)
''')
        (root/'scripts/stress_validator.py').write_text('''import os,pathlib,sys,time
root=pathlib.Path(os.environ['FIXTURE_ROOT'])
with (root/'validations.log').open('a') as out: out.write(sys.argv[1]+'\\n')
time.sleep(float(os.environ.get('VALIDATOR_DELAY','0')))
if '/production/' in sys.argv[1]: time.sleep(float(os.environ.get('VALIDATOR_PRODUCTION_DELAY','0')))
sys.exit(9 if os.environ.get('MODE')=='validator_fail' else 0)
''')
        return root,env

    @unittest.skipUnless(Path(SPEC.origin).exists(), 'runner implementation pending')
    def test_success_separate_validated_calibration_and_literal_space_paths(self):
        root,env=self.fixture(); output=self.base/'output space'
        args=['--vms','100','--hosts','20','--population','2','--iterations','1','--replications','2']
        result=subprocess.run([str(root/'run-stress.sh'),'--skip-build','--plain',*args,'--output-dir',str(output)],
                              env=env,cwd=self.base,capture_output=True,text=True,timeout=20)
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        retained=next(output.iterdir()); data=json.loads((retained/'runner.json').read_text())
        commands=[json.loads(line) for line in (root/'invocations.jsonl').read_text().splitlines()]
        self.assertEqual([c[c.index('--experiment-phase')+1] for c in commands],['stress_calibration','stress'])
        self.assertEqual(len((root/'validations.log').read_text().splitlines()),2)
        self.assertEqual(data['status'],'complete'); self.assertEqual(data['validation'],'PASS')
        self.assertEqual(data['arguments'][-1],str(output)); self.assertEqual(data['effective_config']['replications'],2)
        self.assertEqual(data['calibration'][0]['validation'],'PASS')
        self.assertIn('estimates_scope',data)
        self.assertEqual(data['estimates_scope'],'production')
        plan=data['planned_work']
        self.assertEqual(len(plan['calibration']),1)
        self.assertEqual((plan['calibration'][0]['expected_cases'],plan['calibration'][0]['expected_evaluations']),(4,622))
        self.assertEqual(plan['combined_totals'],{'expected_cases':12,'expected_evaluations':658})
        self.assertEqual([p['effective_config'] for p in plan['calibration']],
                         [p['effective_config'] for p in data['calibration']])
        self.assertEqual(plan['production']['effective_config'],data['production']['effective_config'])
        self.assertIn('VALIDATED',result.stdout); self.assertTrue(data['diagnostic'])
        self.assertIn('seed 123456',result.stdout)
        self.assertIn('excludes build/tests',result.stdout)
        self.assertEqual(data['artifact_sha256'],shared.sha256(retained/'stress.jar'))
        self.assertNotEqual(data['calibration'][0]['run_directory'],data['run_directory'])

    @unittest.skipUnless(Path(SPEC.origin).exists(), 'runner implementation pending')
    def test_pilot_validation_failure_stops_before_production(self):
        root,env=self.fixture(); env['MODE']='validator_fail'
        result=subprocess.run([str(root/'run-stress.sh'),'--skip-build','--plain','--vms','1','--hosts','1',
                               '--output-dir',str(self.base/'failure')],env=env,capture_output=True,text=True,timeout=15)
        self.assertEqual(result.returncode,9,result.stdout+result.stderr)
        self.assertEqual(len((root/'invocations.jsonl').read_text().splitlines()),1)
        data=json.loads((next((self.base/'failure').iterdir())/'runner.json').read_text())
        self.assertEqual(data['status'],'failed'); self.assertNotIn('VALIDATED',result.stdout)

    @unittest.skipUnless(Path(SPEC.origin).exists(), 'runner implementation pending')
    def test_total_deadline_includes_validation_and_never_reports_validated(self):
        root,env=self.fixture(); env.update(JAVA_DELAY='.3',VALIDATOR_DELAY='.9')
        result=subprocess.run([str(root/'run-stress.sh'),'--skip-build','--plain','--vms','1','--hosts','1',
                               '--time-limit','1s','--output-dir',str(self.base/'deadline')],env=env,capture_output=True,text=True,timeout=15)
        self.assertEqual(result.returncode,124,result.stdout+result.stderr)
        retained=next((self.base/'deadline').iterdir()); data=json.loads((retained/'runner.json').read_text())
        self.assertEqual(data['status'],'timeout'); self.assertEqual(data['exit_code'],124)
        self.assertEqual(data['validation'],'NOT_RUN'); self.assertNotIn('VALIDATED',result.stdout)
        self.assertEqual(json.loads((Path(data['calibration'][0]['run_directory'])/'run.json').read_text())['state'],'COMPLETE')

    @unittest.skipUnless(Path(SPEC.origin).exists(), 'runner implementation pending')
    def test_interrupt_and_term_kill_escalation_retain_incomplete_metadata(self):
        root,env=self.fixture(); env['MODE']='linger'
        for number in [signal.SIGINT,signal.SIGTERM]:
            marker=root/'descendant.pid'; marker.unlink(missing_ok=True)
            output=self.base/f'interrupt-{number}'
            parent=subprocess.Popen([str(root/'run-stress.sh'),'--skip-build','--plain','--vms','1','--hosts','1',
                                     '--output-dir',str(output)],env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
            self.addCleanup(lambda p=parent: p.kill() if p.poll() is None else None)
            until=time.monotonic()+8
            while not marker.exists() and time.monotonic()<until: time.sleep(.02)
            self.assertTrue(marker.exists()); descendant=int(marker.read_text()); parent.send_signal(number)
            stdout,stderr=parent.communicate(timeout=10)
            self.assertEqual(parent.returncode,128+number,stderr.decode())
            data=json.loads((next(output.iterdir())/'runner.json').read_text())
            self.assertEqual(data['status'],'interrupted'); self.assertEqual(data['exit_code'],128+number)
            self.assertNotIn(b'VALIDATED',stdout)
            stat=Path(f'/proc/{descendant}/stat')
            self.assertTrue(not stat.exists() or stat.read_text().split()[2]=='Z','live orphan remains')

    def test_shared_absolute_deadline_checks_after_short_child_exit(self):
        control=shared.ProcessControl(shared.Dashboard(io.StringIO(),plain=True),self.base)
        with self.assertRaisesRegex(ValueError,'deadline'):
            control.run([sys.executable,'-c','import time; time.sleep(.15)'],self.base/'deadline.log','validation',
                        deadline=time.monotonic()+.05)

    def test_gc_measurements_and_pilot_estimates_remain_labelled(self):
        log=self.base/'gc.log'
        log.write_text('[0.2s][info][gc          ] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 200M->30M(1024M) 4.500ms\n'
                       '[0.4s][info][gc] GC(1) Pause Young (Normal) (G1 Evacuation Pause) 250M->40M(1024M) 3.000ms\n')
        self.assertEqual(runner.gc_measurements(self.base),{'gc_pause_count':2,'gc_pause_seconds':.0075,
                                                         'max_gc_heap_before_mib':250,'max_gc_heap_after_mib':40})
        config=runner.parse_args(['--vms','200','--hosts','40','--population','10','--iterations','10','--replications','2'])
        estimate=runner.estimate(config,[{'effective_config':dict(vms=100,hosts=20,population=10,iterations=10),
                                         'wall_seconds':2,'output_bytes':1000}])
        self.assertEqual(estimate['approximate_evaluation_ratio'],2)
        self.assertEqual(estimate['approximate_workload_ratio'],4)
        self.assertEqual(estimate['estimated_wall_seconds'],16)
        self.assertIn('not a feasibility',estimate['uncertainty'])

    def test_normal_width_tui_keeps_active_case_config_and_deadline_visible(self):
        parent=self.base/'artifacts'; run=parent/'stress-one'; run.mkdir(parents=True)
        (run/'progress.json').write_text(json.dumps({'successful_cases':0,'completed_evaluations':123,
            'expected_evaluations':36310,'current_case':{'algorithm':'HO','replication':4,'evaluation':123}}))
        config=runner.parse_args(['--preset','medium'])
        class Tty(io.StringIO):
            def isatty(self): return True
        output=Tty()
        with patch.dict(os.environ,{'TERM':'xterm'},clear=True),patch('shutil.get_terminal_size',return_value=os.terminal_size((80,24))):
            dashboard=shared.Dashboard(output,title='STATIC STRESS',total=40)
            dashboard.render('production',runner.progress(parent,config,time.monotonic()+120,runner.estimate(config,[])),force=True)
            dashboard.close()
        visible=shared.sanitize(output.getvalue())
        for text in ('HO replication 4','evaluation 123','V2000/H400/N30/T40/R5','deadline remaining'):
            self.assertIn(text,visible)

    def test_live_computation_is_distinct_from_published_evidence(self):
        parent=self.base/'artifacts'; run=parent/'stress-one'; run.mkdir(parents=True)
        (run/'progress.json').write_text(json.dumps({'successful_cases':0,'completed_evaluations':0,
            'expected_evaluations':36310,'current_case':{'algorithm':'HO','replication':0,'evaluation':0,
            'computing_evaluation':123,'computing_iteration':2,'computing_stage':'UPDATE','computing_phase':'optimizing'}}))
        frame=runner.progress(parent,runner.parse_args([]),None,runner.estimate(runner.parse_args([]),[]))
        self.assertEqual(frame['evaluations'],0)
        self.assertEqual(frame['done'],0)
        self.assertIn('computing evaluation 123',frame['detail'])
        self.assertIn('published evaluations 0/36310',frame['detail'])

    def test_no_deadline_still_collects_observed_rss(self):
        control=shared.ProcessControl(shared.Dashboard(io.StringIO(),plain=True),self.base)
        code=control.run([sys.executable,'-c','import time; time.sleep(.2)'],self.base/'measure.log','pilot',
                         timeout=None,progress_reader=lambda:{'detail':'pilot'})
        self.assertEqual(code,0); self.assertGreater(control.last_measurement['sampled_peak_rss_bytes'],0)

    def test_production_validation_timeout_does_not_change_application_complete_state(self):
        root,env=self.fixture(); env['VALIDATOR_PRODUCTION_DELAY']='2'
        result=subprocess.run([str(root/'run-stress.sh'),'--skip-build','--plain','--vms','1','--hosts','1',
                               '--time-limit','1s','--output-dir',str(self.base/'production-timeout')],env=env,capture_output=True,text=True,timeout=15)
        self.assertEqual(result.returncode,124,result.stdout+result.stderr)
        data=json.loads((next((self.base/'production-timeout').iterdir())/'runner.json').read_text())
        self.assertEqual(data['calibration'][0]['validation'],'PASS'); self.assertEqual(data['validation'],'NOT_RUN')
        self.assertEqual(data['production']['validation'],'NOT_RUN'); self.assertNotIn('VALIDATED',result.stdout)
        self.assertEqual(json.loads((Path(data['run_directory'])/'run.json').read_text())['state'],'COMPLETE')

    def test_build_and_python_checks_run_before_calibration(self):
        root,env=self.fixture()
        result=subprocess.run([str(root/'run-stress.sh'),'--plain','--vms','1','--hosts','1',
                               '--output-dir',str(self.base/'build')],env=env,capture_output=True,text=True,timeout=20)
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertTrue((root/'build.started').exists()); self.assertTrue((root/'tests.started').exists())
        data=json.loads((next((self.base/'build').iterdir())/'runner.json').read_text())
        self.assertIn('deadline_started_at',data)

    def test_setup_failures_retain_logs_without_starting_experiments(self):
        root,env=self.fixture(); env['JAVA_TOOL_OPTIONS']='-Xmx99g'
        result=subprocess.run([str(root/'run-stress.sh'),'--skip-build','--plain','--output-dir',str(self.base/'options')],env=env,capture_output=True,text=True)
        self.assertEqual(result.returncode,1)
        retained=next((self.base/'options').iterdir())
        metadata=json.loads((retained/'runner.json').read_text())
        self.assertEqual(metadata['validation'],'NOT_RUN')
        self.assertIn('JAVA_TOOL_OPTIONS',metadata['error'])
        self.assertIn(metadata['error'],(retained/'console.log').read_text())
        with patch.object(shared,'require_memory',side_effect=ValueError('Insufficient usable memory')),patch.dict(os.environ,env):
            os.environ.pop('JAVA_TOOL_OPTIONS')
            with patch.object(runner,'ROOT',root):
                self.assertEqual(runner.main(['--skip-build','--plain','--output-dir',str(self.base/'memory')]),1)
        retained=next((self.base/'memory').iterdir())
        metadata=json.loads((retained/'runner.json').read_text())
        self.assertEqual(metadata['validation'],'NOT_RUN')
        self.assertIn('Insufficient usable memory',metadata['error'])
        self.assertIn(metadata['error'],(retained/'console.log').read_text())
        self.assertFalse((root/'build.started').exists())
        self.assertFalse((root/'java.started').exists())

    def test_research_wrapper_rejects_stress_overrides(self):
        result=subprocess.run([str(ROOT/'run-research.sh'),'--vms','100'],cwd=self.base,capture_output=True,text=True)
        self.assertEqual(result.returncode,2); self.assertEqual(list(self.base.iterdir()),[])

    def menu(self,values,arguments=None):
        inputs=iter(values); output=io.StringIO()
        result=runner.select_interactive(arguments or ['--interactive'],read=lambda prompt:next(inputs),output=output)
        return result,output.getvalue()

    def test_interactive_default_and_custom_share_config_validation(self):
        (default,choices),text=self.menu(['','','y'])
        self.assertEqual([default.vms,default.hosts,default.population,default.iterations,default.replications],[500,100,30,40,5])
        self.assertEqual(choices['mode'],'default'); self.assertEqual(choices['preset'],'small')
        (custom,choices),text=self.menu(['1','2','3','2','0','1','0','1','y'],
            ['--interactive','--seed','-7','--heap-mib','512','--time-limit','4h','--output-dir',str(self.base/'a space')])
        self.assertEqual([custom.vms,custom.hosts,custom.population,custom.iterations,custom.replications,custom.seed,custom.heap_mib,custom.time_limit],
                         [50,10,2,1,1,-7,512,14400])
        self.assertEqual(choices['mode'],'custom'); self.assertEqual(choices['population'],2)
        for part in ('V50/H10/N2/T1/R1','seed -7','heap 512 MiB','14400','a space'):
            self.assertIn(part,text)
        self.assertIn('Invalid',text)

    def test_interactive_complete_work_is_visible_before_confirmation(self):
        for values,expected in [(['','','n'],[
                'Calibration: V100/H20/N10/T10/R1; 4 cases / 622 evaluations',
                'Calibration: V500/H100/N10/T10/R1; 4 cases / 622 evaluations',
                'Production: V500/H100/N30/T40/R5; 20 cases / 36310 evaluations',
                'Combined totals: 28 cases / 37554 evaluations']),
                (['1','2','2','1','2','n'],[
                'Calibration: V50/H10/N10/T10/R1; 4 cases / 622 evaluations',
                'Production: V50/H10/N2/T1/R2; 8 cases / 36 evaluations',
                'Combined totals: 12 cases / 658 evaluations'])]:
            with self.subTest(values=values):
                output=io.StringIO(); inputs=iter(values); confirmation=[]
                def read(prompt):
                    if prompt.startswith('Start '): confirmation.append(output.getvalue())
                    return next(inputs)
                with self.assertRaises(EOFError):
                    runner.select_interactive(['--interactive'],read=read,output=output)
                for text in expected: self.assertIn(text,confirmation[0])
        self.assertEqual(list(self.base.iterdir()),[])

    def test_interactive_invalid_choices_and_cancel_confirmation(self):
        (args,choices),text=self.menu(['bogus','6','bogus','1','maybe','y'])
        self.assertEqual(args.vms,20000); self.assertEqual(choices['preset'],'xlarge')
        self.assertIn('unverified',text.lower()); self.assertIn('Invalid',text)
        for values in [['q'],['1','q'],['1','1','n'],['1','2','q']]:
            with self.subTest(values=values),self.assertRaises(EOFError): self.menu(values)

    def test_interactive_refuses_non_tty_and_conflicting_matrix_flags(self):
        for arguments in [['--interactive'],['--interactive','--dry-run']]+[
                ['--interactive',flag,value] for flag,value in [('--preset','small'),('--vms','10'),('--hosts','3'),
                    ('--population','2'),('--iterations','1'),('--replications','1')]]:
            with self.subTest(arguments=arguments):
                result=subprocess.run([str(ROOT/'run-stress.sh'),*arguments,'--output-dir',str(self.base/'refused')],capture_output=True,text=True)
                self.assertEqual(result.returncode,2,result.stdout+result.stderr)
                self.assertIn('interactive',result.stderr.lower())
        self.assertEqual(list(self.base.iterdir()),[])

    def test_interactive_pty_cancellation_and_interrupt_have_no_side_effects(self):
        for action,expected in [('q',0),('eof',0),('signal',130)]:
            with self.subTest(action=action):
                master,slave=pty.openpty()
                parent=subprocess.Popen([str(ROOT/'run-stress.sh'),'--interactive','--output-dir',str(self.base/action)],
                                        stdin=slave,stdout=slave,stderr=slave)
                os.close(slave); self.addCleanup(lambda p=parent:p.kill() if p.poll() is None else None)
                try:
                    captured=b''; until=time.monotonic()+5
                    while b'Size [' not in captured and time.monotonic()<until:
                        ready,_,_=select.select([master],[],[],.1)
                        if ready: captured+=os.read(master,65536)
                    self.assertIn(b'Size [',captured)
                    if action=='signal': parent.send_signal(signal.SIGINT)
                    else: os.write(master,b'q\n' if action=='q' else b'\x04')
                    self.assertEqual(parent.wait(timeout=5),expected)
                finally: os.close(master)
        self.assertEqual(list(self.base.iterdir()),[])

    def test_physical_memory_reader_and_unknown_evidence(self):
        proc=self.base/'proc'; proc.mkdir()
        (proc/'meminfo').write_text('MemTotal: 8388608 kB\nMemAvailable: 3145728 kB\n')
        self.assertEqual(shared.physical_memory(proc),8*1024**3)
        (proc/'meminfo').write_text('MemAvailable: 3145728 kB\n')
        with self.assertRaisesRegex(ValueError,'physical'): shared.physical_memory(proc)

    def test_xlarge_memory_warning_below_exact_threshold_and_custom_sizes(self):
        for physical,warning in [(7*1024**3,True),(8*1024**3,False)]:
            with self.subTest(physical=physical),patch.object(shared,'physical_memory',return_value=physical),patch.object(shared,'usable_memory',return_value=3*1024**3):
                evidence=runner.memory_preview(runner.parse_args(['--preset','xlarge']))
                self.assertEqual(bool(evidence['warning']),warning)
                self.assertEqual(evidence['usable_memory_bytes'],3*1024**3)
                self.assertIn('not a measured minimum',evidence['policy_note'])
                self.assertIn('not guaranteed safe',evidence['policy_note'])
                if warning:
                    for text in ('XLarge-scale','OOM','smaller preset'): self.assertIn(text,evidence['warning'])
        with patch.object(shared,'physical_memory',return_value=7*1024**3),patch.object(shared,'usable_memory',return_value=3*1024**3):
            self.assertIsNone(runner.memory_preview(runner.parse_args([]))['warning'])
            for flags in [['--vms','20000'],['--hosts','4000'],['--vms','20001']]:
                self.assertTrue(runner.memory_preview(runner.parse_args(flags))['warning'])
        with patch.object(shared,'physical_memory',side_effect=ValueError('unavailable')),patch.object(shared,'usable_memory',side_effect=ValueError('unavailable')):
            evidence=runner.memory_preview(runner.parse_args(['--preset','xlarge']))
            self.assertIsNone(evidence['physical_memory_bytes']); self.assertIsNone(evidence['usable_memory_bytes'])

    def test_xlarge_dry_run_warns_without_children_and_unknown_never_fails(self):
        output=self.base/'dry-memory'
        for physical in [7*1024**3,None]:
            capture=io.StringIO()
            with patch.object(shared,'physical_memory',return_value=physical),patch.object(shared,'usable_memory',side_effect=ValueError('unavailable')),redirect_stdout(capture):
                code=runner.main(['--preset','xlarge','--dry-run','--output-dir',str(output)])
            self.assertEqual(code,0); self.assertFalse(output.exists())
            self.assertIn('usable: unknown',capture.getvalue())
            if physical: self.assertLess(capture.getvalue().index('WARNING:'),capture.getvalue().index('"effective_config"'))
            else: self.assertIn('physical: unknown',capture.getvalue())

    def test_interactive_memory_warning_precedes_confirmation(self):
        output=io.StringIO(); values=iter(['6','1','y']); confirmation=[]
        def read(prompt):
            if prompt.startswith('Start '): confirmation.append(output.getvalue())
            return next(values)
        with patch.object(shared,'physical_memory',return_value=7*1024**3),patch.object(shared,'usable_memory',return_value=3*1024**3):
            runner.select_interactive(['--interactive'],read=read,output=output)
        self.assertIn('WARNING:',confirmation[0]); self.assertIn('physical: 7.00 GiB',confirmation[0])
        self.assertIn('usable: 3.00 GiB',confirmation[0])

    def test_startup_warning_is_retained_before_build_and_guard_stays_hard(self):
        root,env=self.fixture(); env['MODE']='build_fail'; output=self.base/'startup-memory'; capture=io.StringIO()
        with patch.dict(os.environ,env),patch.object(runner,'ROOT',root),patch.object(shared,'physical_memory',return_value=7*1024**3),patch.object(shared,'usable_memory',return_value=3*1024**3),redirect_stdout(capture):
            code=runner.main(['--preset','xlarge','--plain','--output-dir',str(output)])
        self.assertEqual(code,7); self.assertLess(capture.getvalue().index('WARNING:'),capture.getvalue().index('[preflight]'))
        data=json.loads((next(output.iterdir())/'runner.json').read_text())
        self.assertTrue(data['memory_evidence']['warning']); self.assertEqual(data['memory_evidence']['physical_memory_bytes'],7*1024**3)
        self.assertFalse((root/'invocations.jsonl').exists())
        with patch.object(shared,'usable_memory',return_value=2*1024**3),self.assertRaisesRegex(ValueError,'Insufficient'):
            shared.require_memory(1024)


if __name__=='__main__': unittest.main()
