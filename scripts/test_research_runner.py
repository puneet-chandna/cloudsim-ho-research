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
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('research_runner', ROOT/'scripts/research_runner.py')
runner = importlib.util.module_from_spec(SPEC)
if SPEC.loader and SPEC.origin and Path(SPEC.origin).exists():
    SPEC.loader.exec_module(runner)


class RunnerChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='runner checks ')
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)

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
        stat = Path(f'/proc/{descendant}/stat')
        self.assertTrue(not stat.exists() or stat.read_text().split()[2]=='Z','live orphan remains')

    def fixture(self):
        root = self.base/'project space'; (root/'scripts').mkdir(parents=True)
        (root/'target').mkdir(); (root/'target/cloudsim-ho-research-v2-2.0.0.jar').write_bytes(b'controlled artifact')
        shutil.copyfile(ROOT/'scripts/research_runner.py',root/'scripts/research_runner.py')
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
            stat=Path(f'/proc/{descendant}/stat')
            self.assertTrue(not stat.exists() or stat.read_text().split()[2]=='Z','live orphan remains')

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
                    stat=Path(f'/proc/{descendant}/stat')
                    self.assertTrue(not stat.exists() or stat.read_text().split()[2]=='Z','live provenance orphan remains')
                    self.assertFalse((root/'java.started').exists()); self.assertFalse((root/'validator.started').exists())


if __name__ == '__main__': unittest.main()
