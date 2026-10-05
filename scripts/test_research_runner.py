"""Fast runner contract checks; controlled children never execute the research matrix."""
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import signal
import shutil
import subprocess
import sys
import tempfile
import time
import unittest
from contextlib import redirect_stdout
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('research_runner', ROOT/'scripts/research_runner.py')
runner = importlib.util.module_from_spec(SPEC)
if SPEC.loader and SPEC.origin and Path(SPEC.origin).exists():
    SPEC.loader.exec_module(runner)


def process_alive(pid):
    """Independent POSIX check; a zombie has exited but awaits its owner's reap."""
    result = subprocess.run(['/bin/ps','-p',str(pid),'-o','stat='],capture_output=True,text=True,timeout=5)
    if result.returncode not in (0,1): raise RuntimeError(result.stderr)
    status = result.stdout.strip()
    return bool(status) and not status.startswith('Z')


def wait_for_exit(pid):
    deadline = time.monotonic()+2
    while process_alive(pid):
        if time.monotonic() >= deadline: return False
        time.sleep(.02)
    return True


class RecordingDashboard:
    def __init__(self):
        self.frames = []
        self.logs = []
        self.polls = 0
        self.closed = False

    def render(self,stage,progress=None,pid=None,force=False):
        self.frames.append((stage,progress or {}))

    def poll(self,control): self.polls += 1
    def set_log(self,path): self.logs.append(path)
    def close(self): self.closed = True


class RunnerChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='runner checks ')
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)

    def test_retained_campaign_copy_must_match_verified_receipt(self):
        from types import SimpleNamespace
        source = self.base/'cached.jar'; source.write_bytes(b'original verified artifact')
        receipt = {'artifact': {'sha256': hashlib.sha256(source.read_bytes()).hexdigest()}}
        outer = self.base/'campaign'; outer.mkdir()
        control = SimpleNamespace()
        args = SimpleNamespace(skip_build=False, force_build=False)
        metadata = {'build_receipt': receipt, 'tests': 'PASS'}
        original_copy = shutil.copyfile
        def corrupt_copy(selected, destination):
            selected.write_bytes(b'unverified replacement')
            return original_copy(selected, destination)
        with patch.object(runner.cloudsim_build, 'ensure_verified_build', return_value=source), \
             patch.object(runner.shutil, 'copyfile', side_effect=corrupt_copy), \
             self.assertRaisesRegex(ValueError, 'verified receipt'):
            runner.prepare_artifact(args, metadata, control, outer, 'smoke')

    def test_real_help_and_invalid_cli_do_not_start_children(self):
        for arguments, code in [(['--help'],0),(['--heap-mib','511'],2),
                                (['--heap-mib','1.5'],2),(['--profile','smoke'],2)]:
            result = subprocess.run([sys.executable,str(ROOT/'scripts/research_runner.py'),*arguments],
                                    cwd=self.base,capture_output=True,text=True)
            self.assertEqual(result.returncode,code,result.stderr)
            if arguments==['--help']:
                self.assertIn('master.seed',result.stdout)
                self.assertIn('log.level',result.stdout)
        self.assertEqual(list(self.base.iterdir()),[])

    def memory_files(self, limits):
        proc = self.base/'proc'; proc.mkdir()
        (proc/'self').mkdir()
        (proc/'meminfo').write_text('MemAvailable: 8388608 kB\n')
        mount = self.base/'cgroup'; mount.mkdir()
        leaf = mount/'parent'/'child'; leaf.mkdir(parents=True)
        (proc/'self/cgroup').write_text('0::/parent/child\n')
        escaped = str(mount).replace(' ','\\040')
        (proc/'self/mountinfo').write_text(f'1 0 0:1 / {escaped} rw - cgroup2 cgroup rw\n')
        for directory,limit,current in limits:
            path = mount/directory
            (path/'memory.max').write_text(str(limit))
            (path/'memory.current').write_text(str(current))
        return proc

    def test_cgroup_ancestor_headroom_and_max_sentinel(self):
        proc = self.memory_files([('parent','3221225472',1073741824),('parent/child','max',0)])
        self.assertEqual(runner.usable_memory(proc),2*1024**3)
        with self.assertRaisesRegex(ValueError,'memory'):
            runner.require_memory(1024,proc)

    def test_unreadable_cgroup_evidence_and_low_host_memory_fail_closed(self):
        proc = self.memory_files([('parent','max',0),('parent/child','max',0)])
        (proc/'meminfo').write_text('MemAvailable: 1048576 kB\n')
        with self.assertRaisesRegex(ValueError,'memory'):
            runner.require_memory(1024,proc)
        (self.base/'cgroup/parent/memory.max').unlink()
        with self.assertRaisesRegex(ValueError,'cgroup'):
            runner.usable_memory(proc)

    def test_hidden_java_options_are_rejected(self):
        for name in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']:
            with self.assertRaisesRegex(ValueError,name):
                runner.check_java_options({name:'-Xmx45g'})

    def test_java_command_is_research_only_and_quotes_are_literal(self):
        command = runner.java_command(Path('/jdk space/bin/java'),1024,self.base/'app.jar',self.base,self.base/'a config.properties')
        self.assertEqual(command[0],'/jdk space/bin/java')
        self.assertIn('-Xms256m',command); self.assertIn('-Xmx1024m',command)
        self.assertEqual(command[command.index('--profile')+1],'research')
        self.assertEqual(command[command.index('--config')+1],str(self.base/'a config.properties'))
        self.assertTrue(any('filecount=3,filesize=5M' in arg for arg in command))

    def test_frozen_profile_command_preserves_default_arguments(self):
        expected = ['/jdk/bin/java','-Xms256m','-Xmx1024m','-XX:+UseG1GC',
            '-XX:+ExitOnOutOfMemoryError',
            '-Xlog:gc*:file=gc.log:time,uptime,level,tags:filecount=3,filesize=5M',
            '-jar','/retained/app.jar','--profile','research','--output-dir','/retained/research']
        self.assertEqual(runner.java_command(Path('/jdk/bin/java'),1024,Path('/retained/app.jar'),Path('/retained')),expected)
        for profile in ('smoke','explore','research'):
            command=runner.java_command(Path('/jdk/bin/java'),1024,Path('/retained/app.jar'),Path('/retained'),profile=profile)
            self.assertEqual(command[8:],['--profile',profile,'--output-dir','/retained/'+profile])
        with self.assertRaises(ValueError):
            runner.java_command(Path('/jdk/bin/java'),1024,Path('/retained/app.jar'),self.base,profile='stress')

    def test_selected_frozen_profile_requires_its_exact_counters(self):
        for profile,total in [('smoke',4),('explore',40),('research',450)]:
            root=self.base/profile
            self.manifest(root,profile=profile,expected_cases=total,attempted_cases=total,successful_cases=total)
            self.assertEqual(runner.completed_run(root,'abc',profile=profile).name,'run-one')
            for changes in [dict(profile='wrong'),dict(expected_cases=total-1),
                            dict(attempted_cases=total-1),dict(successful_cases=total-1),
                            dict(expected_cases=True),dict(failed_cases=1),dict(unattempted_cases=1)]:
                with self.subTest(profile=profile,changes=changes):
                    values=dict(profile=profile,expected_cases=total,attempted_cases=total,successful_cases=total)
                    self.manifest(root,**{**values,**changes})
                    with self.assertRaises(ValueError): runner.completed_run(root,'abc',profile=profile)
        root=self.base/'mismatch'; self.manifest(root)
        with self.assertRaises(ValueError): runner.completed_run(root,'abc',profile='smoke')

    def test_progress_rejects_wrong_profile_and_bounds_by_selected_total(self):
        for profile,total in [('smoke',4),('explore',40),('research',450)]:
            root=self.base/profile
            self.manifest(root,profile=profile,state='RUNNING',attempted_cases=total,successful_cases=total)
            self.assertEqual(runner.read_progress(root,profile=profile)['stage'],'analysis')
            self.manifest(root,profile='wrong',attempted_cases=1,successful_cases=0)
            self.assertIn('unavailable',runner.read_progress(root,profile=profile)['detail'])
            self.manifest(root,profile=profile,attempted_cases=total+1,successful_cases=total+1)
            self.assertIn('unavailable',runner.read_progress(root,profile=profile)['detail'])

    def test_supervisor_polls_short_children_and_services_each_iteration(self):
        dashboard=RecordingDashboard(); control=runner.ProcessControl(dashboard,self.base)
        log=self.base/'short.log'
        self.assertEqual(control.run([sys.executable,'-c','pass'],log,'build'),0)
        self.assertGreaterEqual(dashboard.polls,2)
        self.assertEqual(dashboard.logs,[log])
        dashboard.polls=0
        self.assertEqual(control.run([sys.executable,'-c','import time; time.sleep(.35)'],log,'build'),0)
        self.assertGreaterEqual(dashboard.polls,4)

    def test_presentation_exception_stops_owned_child_before_propagating(self):
        marker=self.base/'child.pid'
        class Broken(RecordingDashboard):
            def poll(self,control):
                if marker.exists(): raise RuntimeError('presentation unavailable')
        dashboard=Broken(); control=runner.ProcessControl(dashboard,self.base)
        script=f'import os,pathlib,time; pathlib.Path({str(marker)!r}).write_text(str(os.getpid())); time.sleep(60)'
        with self.assertRaisesRegex(ValueError,'presentation unavailable'):
            control.run([sys.executable,'-c',script],self.base/'broken.log','build')
        pid=int(marker.read_text())
        self.assertTrue(wait_for_exit(pid),'owned child remains alive')

    def test_poll_can_cancel_before_launch(self):
        class Cancel(RecordingDashboard):
            def poll(self,control): control.on_signal(signal.SIGINT,None)
        marker=self.base/'launched'
        control=runner.ProcessControl(Cancel(),self.base)
        with self.assertRaises(runner.Interrupted):
            control.run([sys.executable,'-c',f'from pathlib import Path; Path({str(marker)!r}).touch()'],self.base/'cancel.log','build')
        self.assertFalse(marker.exists())

    def test_two_embedded_jobs_restore_signals_and_complete_copy(self):
        root,env=self.fixture(); capture=io.StringIO(); outcomes=[]
        previous={number:signal.getsignal(number) for number in (signal.SIGINT,signal.SIGTERM)}
        dashboard=RecordingDashboard()
        def completed(metadata):
            outcomes.append(dict(metadata)); metadata['status']='tampered'
        with patch.dict(os.environ,env,clear=True),patch.object(runner,'ROOT',root),patch.object(runner,'require_memory',return_value=4*1024**3),redirect_stdout(capture):
            for i in range(2):
                code=runner.main(['--skip-build','--output-dir',str(self.base/f'job-{i}')],
                                 dashboard=dashboard,on_complete=completed,invocation=['./cloudsim.sh','--profile','research'])
                self.assertEqual(code,0)
                self.assertTrue(dashboard.closed)
                self.assertEqual({n:signal.getsignal(n) for n in previous},previous)
                retained=json.loads((next((self.base/f'job-{i}').iterdir())/'runner.json').read_text())
                self.assertEqual(retained['status'],'complete')
                self.assertEqual(retained['invocation'],['./cloudsim.sh','--profile','research'])
        self.assertEqual(capture.getvalue(),'')
        self.assertEqual([d['exit_code'] for d in outcomes],[0,0])

    def test_completion_callback_failure_cannot_retain_success(self):
        root,env=self.fixture(); dashboard=RecordingDashboard()
        def failed(metadata): raise RuntimeError('completion unavailable')
        with patch.dict(os.environ,env,clear=True),patch.object(runner,'ROOT',root),patch.object(runner,'require_memory',return_value=4*1024**3),redirect_stdout(io.StringIO()):
            code=runner.main(['--skip-build','--output-dir',str(self.base/'callback')],dashboard=dashboard,on_complete=failed)
        self.assertEqual(code,1)
        retained=json.loads((next((self.base/'callback').iterdir())/'runner.json').read_text())
        self.assertEqual(retained['status'],'failed'); self.assertIn('completion unavailable',retained['error'])

    def test_completion_callback_cannot_mutate_nested_retained_status(self):
        control=runner.ProcessControl(RecordingDashboard(),self.base)
        metadata={'status':'complete','java_command':['java','-jar','app.jar']}
        def mutate(outcome): outcome['java_command'].clear()
        self.assertEqual(runner.finish(control,metadata,0,on_complete=mutate),0)
        self.assertEqual(metadata['java_command'],['java','-jar','app.jar'])

    def test_close_failure_restores_handlers_and_marks_failure(self):
        class Broken(RecordingDashboard):
            def close(self): raise RuntimeError('close unavailable')
        control=runner.ProcessControl(Broken(),self.base)
        previous={number:signal.getsignal(number) for number in (signal.SIGINT,signal.SIGTERM)}
        control.install(); metadata={'status':'complete'}
        self.assertEqual(runner.finish(control,metadata,0),1)
        self.assertEqual(metadata['status'],'failed')
        self.assertEqual({n:signal.getsignal(n) for n in previous},previous)

    def test_embedded_dashboard_retains_console_events(self):
        root,env=self.fixture(); dashboard=RecordingDashboard()
        with patch.dict(os.environ,env,clear=True),patch.object(runner,'ROOT',root),patch.object(runner,'require_memory',return_value=4*1024**3):
            self.assertEqual(runner.main(['--skip-build','--output-dir',str(self.base/'console')],dashboard=dashboard),0)
        log=(next((self.base/'console').iterdir())/'console.log').read_text()
        self.assertIn('SKIPPED',log); self.assertIn('VALIDATED',log)

    def test_embedded_smoke_explore_do_not_require_research_claims(self):
        root,env=self.fixture(); java=root/'jdk space/bin/java'
        source=java.read_text().replace("assert sys.argv[sys.argv.index('--profile')+1]=='research'", "profile=sys.argv[sys.argv.index('--profile')+1]; total={'smoke':4,'explore':40}[profile]")
        source=source.replace("profile='research',state='COMPLETE',expected_cases=450,attempted_cases=450,successful_cases=450", "profile=profile,state='COMPLETE',expected_cases=total,attempted_cases=total,successful_cases=total")
        source=source.replace("(run/'analysis/pairwise_primary.csv').write_text('decision\\nNO_CLAIM\\n')",'pass')
        java.write_text(source)
        with patch.dict(os.environ,env,clear=True),patch.object(runner,'ROOT',root),patch.object(runner,'require_memory',return_value=4*1024**3),redirect_stdout(io.StringIO()):
            for profile,total in [('smoke',4),('explore',40)]:
                dashboard=RecordingDashboard(); outcomes=[]
                code=runner.main(['--skip-build','--output-dir',str(self.base/profile)],profile=profile,dashboard=dashboard,on_complete=outcomes.append)
                self.assertEqual(code,0)
                self.assertEqual(outcomes[0]['profile'],profile)
                self.assertEqual(dashboard.frames[-1][1]['done'],total)
                self.assertNotIn('claims',outcomes[0])

    def manifest(self, root, **changes):
        run = root/'run-one'; run.mkdir(parents=True,exist_ok=True)
        data = dict(profile='research',state='COMPLETE',expected_cases=450,attempted_cases=450,
                    successful_cases=450,failed_cases=0,unattempted_cases=0,error=None,artifact_sha256='abc',
                    cases=[dict(status='RUN_OK',scenario='Small',algorithm='HO',replication=0,phase='main')]*450)
        data.update(changes); (run/'run.json').write_text(json.dumps(data))
        return run

    def test_only_one_successful_result_with_matching_artifact_is_accepted(self):
        root = self.base/'research'; self.manifest(root)
        self.assertEqual(runner.completed_run(root,'abc').name,'run-one')
        for changes in [dict(state='RUNNING'),dict(failed_cases=1),dict(profile='smoke'),
                        dict(artifact_sha256='wrong'),dict(error={'code':'FAILED'}),dict(successful_cases=449)]:
            with self.subTest(changes=changes):
                self.manifest(root,**changes)
                with self.assertRaises(ValueError): runner.completed_run(root,'abc')
        self.manifest(root); (root/'run-two').mkdir()
        with self.assertRaisesRegex(ValueError,'exactly one'): runner.completed_run(root,'abc')

    def test_transient_progress_and_analysis_never_imply_success(self):
        root = self.base/'research'; run = self.manifest(root,state='RUNNING')
        self.assertEqual(runner.read_progress(root)['stage'],'analysis')
        (run/'run.json').write_text('{')
        self.assertIn('unavailable',runner.read_progress(root)['detail'])
        self.manifest(root,state='RUNNING',successful_cases=0,attempted_cases=1)
        progress = runner.read_progress(root)
        self.assertEqual(progress['stage'],'research')
        self.assertIn('Small',progress['detail'])
        self.manifest(root,successful_cases='bad')
        self.assertIn('unavailable',runner.read_progress(root)['detail'])

    def test_plain_and_narrow_dashboard_sanitizes_controls(self):
        output = io.StringIO()
        dashboard = runner.Dashboard(output,plain=True)
        dashboard.render('research',{'done':2,'detail':'a\x1b]0;bad\x07\npath'},None,force=True)
        self.assertNotIn('\x1b',output.getvalue()); self.assertNotIn('\x07',output.getvalue())
        class Tty(io.StringIO):
            def isatty(self): return True
        output = Tty()
        with patch.dict(os.environ,{'TERM':'xterm'},clear=True),patch('shutil.get_terminal_size',return_value=os.terminal_size((18,20))):
            dashboard = runner.Dashboard(output)
            dashboard.render('research',{'done':2,'detail':'x'*70},None,force=True)
            dashboard.close()
        for line in output.getvalue().splitlines():
            self.assertLessEqual(len(runner.sanitize(line)),18)

    def test_dashboard_failure_is_disclosed_and_timeout_stops_child(self):
        class Broken(io.StringIO):
            def write(self,value): raise OSError('output unavailable')
        console = io.StringIO(); dashboard = runner.Dashboard(Broken(),plain=True,console=console)
        dashboard.render('research',force=True)
        self.assertIn('UI_WARNING',console.getvalue())
        control = runner.ProcessControl(runner.Dashboard(io.StringIO(),plain=True),self.base)
        with self.assertRaisesRegex(ValueError,'timed out'):
            control.run([sys.executable,'-c','import time; time.sleep(60)'],self.base/'timeout.log','build',timeout=.2)

    def test_narrow_dashboard_uses_terminal_cells_and_preserves_combining_marks(self):
        class Tty(io.StringIO):
            def isatty(self): return True
        for text,expected in [('界'*20,'界'*8),('e\u0301'*20,'e\u0301'*17),('界e\u0301'*10,'界e\u0301'*5+'界')]:
            with self.subTest(text=text):
                output=Tty()
                with patch.dict(os.environ,{'TERM':'xterm'},clear=True),patch('shutil.get_terminal_size',return_value=os.terminal_size((18,20))):
                    dashboard=runner.Dashboard(output)
                    dashboard.render('research',{'detail':text},force=True)
                    dashboard.close()
                self.assertEqual(runner.sanitize(output.getvalue().splitlines()[4]),expected)

    def test_manifest_reads_are_rate_limited_with_display(self):
        control = runner.ProcessControl(runner.Dashboard(io.StringIO(),plain=True),self.base)
        with patch.object(runner,'read_progress',return_value={'stage':'research','detail':'pending'}) as reads:
            control.run([sys.executable,'-c','import time; time.sleep(1.3)'],self.base/'poll.log','research')
        self.assertLessEqual(reads.call_count,2)

    def test_child_nonzero_is_preserved_and_signal_kills_owned_descendant(self):
        dashboard = runner.Dashboard(io.StringIO(),plain=True)
        control = runner.ProcessControl(dashboard,self.base)
        self.assertEqual(control.run([sys.executable,'-c','raise SystemExit(7)'],self.base/'failure.log','build'),7)
        marker = self.base/'descendant.pid'
        code = ('import subprocess,time,pathlib; '
                'p=subprocess.Popen(["sleep","60"]); '
                f'pathlib.Path({str(marker)!r}).write_text(str(p.pid)); time.sleep(60)')
        launch = ('import sys,signal; sys.path.insert(0,sys.argv[1]); import research_runner as r; '
                  'from pathlib import Path; c=r.ProcessControl(r.Dashboard(plain=True),Path(sys.argv[2])); '
                  'c.install();\ntry: c.run([sys.executable,"-c",sys.argv[3]],Path(sys.argv[2])/"signal.log","research")\n'
                  'except r.Interrupted as e: sys.exit(128+e.signum)\nfinally: c.restore()')
        parent = subprocess.Popen([sys.executable,'-B','-c',launch,str(ROOT/'scripts'),str(self.base),code],stdout=subprocess.DEVNULL)
        self.addCleanup(lambda: parent.kill() if parent.poll() is None else None)
        deadline = time.monotonic()+8
        while not marker.exists() and time.monotonic()<deadline: time.sleep(.02)
        self.assertTrue(marker.exists()); descendant = int(marker.read_text())
        parent.send_signal(signal.SIGTERM); self.assertEqual(parent.wait(timeout=8),143)
        self.assertTrue(wait_for_exit(descendant),'live orphan remains')

    def fixture(self):
        root = self.base/'project space'; (root/'scripts').mkdir(parents=True)
        (root/'target').mkdir(); (root/'target/lattora-2.0.0.jar').write_bytes(b'controlled artifact')
        source=(ROOT/'scripts/research_runner.py').read_text()
        # Controlled subprocess contracts must not depend on concurrent host memory use.
        source=source.replace("if __name__=='__main__':", "require_memory=lambda heap:4*1024**3\n\nif __name__=='__main__':")
        (root/'scripts/research_runner.py').write_text(source)
        shutil.copyfile(ROOT/'scripts/cloudsim_runtime.py',root/'scripts/cloudsim_runtime.py')
        shutil.copyfile(ROOT/'scripts/cloudsim_build.py',root/'scripts/cloudsim_build.py')
        shutil.copyfile(ROOT/'scripts/lattora_context.py',root/'scripts/lattora_context.py')
        shutil.copyfile(ROOT/'run-research.sh',root/'run-research.sh'); (root/'run-research.sh').chmod(0o755)
        jdk = root/'jdk space/bin'; jdk.mkdir(parents=True)
        prefix = '#!'+sys.executable+'\n'
        java = prefix+'''import hashlib,json,os,pathlib,sys
if '-version' in sys.argv: print('openjdk version "21.0.1"'); sys.exit(0)
root=pathlib.Path(os.environ['FIXTURE_ROOT']); (root/'java.started').touch()
if os.environ.get('MODE')=='java_fail': sys.exit(8)
assert sys.argv[sys.argv.index('--profile')+1]=='research'
output=pathlib.Path(sys.argv[sys.argv.index('--output-dir')+1]); run=output/'run-one'; run.mkdir(parents=True)
jar=pathlib.Path(sys.argv[sys.argv.index('-jar')+1])
data=dict(profile='research',state='COMPLETE',expected_cases=450,attempted_cases=450,successful_cases=450,failed_cases=0,unattempted_cases=0,error=None,git_revision='old-artifact',git_dirty=False,artifact_sha256=hashlib.sha256(jar.read_bytes()).hexdigest())
if os.environ.get('MODE')=='incomplete': data['state']='RUNNING'
(run/'run.json').write_text(json.dumps(data)); (run/'analysis').mkdir()
(run/'analysis/pairwise_primary.csv').write_text('decision\\nNO_CLAIM\\n')
'''
        (jdk/'java').write_text(java); (jdk/'javac').write_text(prefix+'print("javac 21.0.1")\n')
        (root/'mvnw').write_text(prefix+"import os,pathlib,sys\nroot=pathlib.Path(os.environ['FIXTURE_ROOT']); (root/'build.started').touch()\nsys.exit(7 if os.environ.get('MODE')=='build_fail' else 0)\n")
        (root/'scripts/test_gate.py').write_text("import os,pathlib,unittest\nclass Gate(unittest.TestCase):\n def test_gate(self): pathlib.Path(os.environ['FIXTURE_ROOT'],'tests.started').touch()\n")
        (root/'scripts/statistics_validator.py').write_text(prefix+"import os,pathlib,sys\nroot=pathlib.Path(os.environ['FIXTURE_ROOT']); (root/'validator.started').touch()\nsys.exit(9 if os.environ.get('MODE')=='validator_fail' else 0)\n")
        (jdk/'git').write_text(prefix+"import sys\nprint('current-checkout' if 'rev-parse' in sys.argv else '?? run-research.sh')\n")
        for executable in [jdk/'java',jdk/'javac',jdk/'git',root/'mvnw']: executable.chmod(0o755)
        env = dict(os.environ,JAVA_HOME=str(jdk.parent),FIXTURE_ROOT=str(root),PATH=str(jdk)+os.pathsep+os.environ['PATH'],PYTHONDONTWRITEBYTECODE='1')
        for key in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS'): env.pop(key,None)
        return root,env

    def test_cli_stage_failure_stops_downstream_and_retains_metadata(self):
        root,env = self.fixture()
        for mode,code,started in [('build_fail',7,{'build'}),('java_fail',8,{'build','tests','java'}),
                                  ('validator_fail',9,{'build','tests','java','validator'}),
                                  ('incomplete',1,{'build','tests','java'})]:
            with self.subTest(mode=mode):
                for marker in root.glob('*.started'): marker.unlink()
                env['MODE']=mode
                result = subprocess.run([str(root/'run-research.sh'),'--plain','--output-dir',str(self.base/mode)],
                                        cwd=self.base,env=env,capture_output=True,text=True,timeout=15)
                self.assertEqual(result.returncode,code,result.stdout+result.stderr)
                self.assertEqual({p.stem for p in root.glob('*.started')},started)
                retained = next((self.base/mode).iterdir())
                metadata = json.loads((retained/'runner.json').read_text())
                self.assertEqual(metadata['status'],'failed'); self.assertEqual(metadata['exit_code'],code)
                self.assertTrue((retained/'console.log').is_file())

    def test_success_skip_build_is_diagnostic_and_retains_artifact_provenance(self):
        root,env = self.fixture(); config = self.base/'a config.properties'; config.write_text('')
        result = subprocess.run([str(root/'run-research.sh'),'--skip-build','--plain','--config',str(config),
                                 '--output-dir',str(self.base/'output space')],cwd=self.base,env=env,capture_output=True,text=True,timeout=15)
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertIn('SKIPPED',result.stdout); self.assertIn('NO_CLAIM',result.stdout)
        self.assertFalse((root/'build.started').exists()); self.assertFalse((root/'tests.started').exists())
        retained = next((self.base/'output space').iterdir())
        metadata = json.loads((retained/'runner.json').read_text())
        self.assertTrue(metadata['diagnostic']); self.assertEqual(metadata['artifact_revision'],'old-artifact')
        self.assertEqual(metadata['source_revision'],'current-checkout')
        self.assertEqual(metadata['artifact_sha256'],hashlib.sha256((retained/'research.jar').read_bytes()).hexdigest())

    def test_cli_interrupt_retains_outcome_and_stops_owned_children(self):
        root,env = self.fixture()
        java = root/'jdk space/bin/java'
        source = java.read_text().replace("assert sys.argv[sys.argv.index('--profile')+1]=='research'",'''assert sys.argv[sys.argv.index('--profile')+1]=='research'
if os.environ.get('MODE')=='linger':
 import subprocess,time
 child=subprocess.Popen(['sleep','60'])
 (root/'descendant.pid').write_text(str(child.pid))
 time.sleep(60)''')
        java.write_text(source); env['MODE']='linger'
        for number in (signal.SIGINT,signal.SIGTERM):
            marker=root/'descendant.pid'; marker.unlink(missing_ok=True)
            output=self.base/f'interrupt-{number}'
            parent=subprocess.Popen([str(root/'run-research.sh'),'--skip-build','--plain','--output-dir',str(output)],
                                    cwd=self.base,env=env,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
            self.addCleanup(lambda p=parent: p.kill() if p.poll() is None else None)
            deadline=time.monotonic()+8
            while not marker.exists() and time.monotonic()<deadline: time.sleep(.02)
            self.assertTrue(marker.exists()); descendant=int(marker.read_text())
            parent.send_signal(number); _,error=parent.communicate(timeout=8)
            self.assertEqual(parent.returncode,128+number,error.decode())
            retained=next(output.iterdir()); metadata=json.loads((retained/'runner.json').read_text())
            self.assertEqual(metadata['status'],'interrupted'); self.assertEqual(metadata['exit_code'],128+number)
            self.assertIn('interrupted',(retained/'console.log').read_text())
            self.assertTrue(wait_for_exit(descendant),'live orphan remains')

    def test_git_stdout_provenance_excludes_stderr_and_retains_warnings(self):
        root,env=self.fixture(); git=root/'jdk space/bin/git'
        git.write_text(git.read_text()+"print('fsmonitor warning',file=sys.stderr)\n")
        result=subprocess.run([str(root/'run-research.sh'),'--skip-build','--plain','--output-dir',str(self.base/'git-warning')],
                              cwd=self.base,env=env,capture_output=True,text=True,timeout=15)
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        retained=next((self.base/'git-warning').iterdir()); metadata=json.loads((retained/'runner.json').read_text())
        self.assertEqual(metadata['source_revision'],'current-checkout')
        self.assertEqual(metadata['source_status'],'?? run-research.sh\n')
        self.assertTrue((retained/'source-status.stderr.log').is_file(),'Git warning output was not retained')
        self.assertIn('fsmonitor warning',(retained/'source-status.stderr.log').read_text())
        self.assertIn('fsmonitor warning',(retained/'source-revision.stderr.log').read_text())

    def test_git_provenance_interrupts_cleanup_descendants_and_retain_outcome(self):
        root,env=self.fixture(); git=root/'jdk space/bin/git'
        git.write_text(git.read_text()+'''import os,pathlib,subprocess,time
if os.environ.get('GIT_HANG_COMMAND') in sys.argv:
 child=subprocess.Popen(['sleep','60'])
 pathlib.Path(os.environ['FIXTURE_ROOT'],'git-descendant.pid').write_text(str(child.pid))
 time.sleep(60)
''')
        def kill_if_live(pid):
            try: os.kill(pid,signal.SIGKILL)
            except ProcessLookupError: pass
        for command in ('status','rev-parse'):
            for number in (signal.SIGINT,signal.SIGTERM):
                with self.subTest(command=command,signal=number):
                    env['GIT_HANG_COMMAND']=command
                    marker=root/'git-descendant.pid'; marker.unlink(missing_ok=True)
                    output=self.base/f'git-interrupt-{command}-{number}'
                    parent=subprocess.Popen([str(root/'run-research.sh'),'--skip-build','--plain','--output-dir',str(output)],
                                            cwd=self.base,env=env,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
                    self.addCleanup(lambda p=parent: p.kill() if p.poll() is None else None)
                    deadline=time.monotonic()+8
                    while not marker.exists() and time.monotonic()<deadline: time.sleep(.02)
                    self.assertTrue(marker.exists()); descendant=int(marker.read_text())
                    self.addCleanup(kill_if_live,descendant)
                    parent.send_signal(number); _,error=parent.communicate(timeout=15)
                    self.assertEqual(parent.returncode,128+number,error.decode())
                    retained=next(output.iterdir()); metadata=json.loads((retained/'runner.json').read_text())
                    self.assertEqual(metadata['status'],'interrupted'); self.assertEqual(metadata['exit_code'],128+number)
                    self.assertTrue(wait_for_exit(descendant),'live provenance orphan remains')
                    self.assertFalse((root/'java.started').exists()); self.assertFalse((root/'validator.started').exists())


if __name__ == '__main__': unittest.main()
