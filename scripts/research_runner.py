#!/usr/bin/env python3
"""Build, run the fixed research matrix, and independently validate its retained artifact."""
import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import unicodedata
from datetime import datetime, timezone
from contextlib import ExitStack

ROOT = Path(__file__).resolve().parents[1]
MIB = 1024**2


def sanitize(value):
    text = re.sub(r'\x1b(?:\][^\x07\x1b]*(?:\x07|\x1b\\)|\[[0-?]*[ -/]*[@-~])', '', str(value))
    return ''.join(' ' if unicodedata.category(char).startswith('C') else char for char in text)


def clip_cells(text, width):
    used = 0
    for index,char in enumerate(text):
        cells = 0 if unicodedata.category(char) in ('Mn','Me') else 2 if unicodedata.east_asian_width(char) in ('W','F') else 1
        if used+cells>width: return text[:index]
        used += cells
    return text


def usable_memory(proc=Path('/proc')):
    """Linux MemAvailable, constrained by every visible cgroup-v2 ancestor."""
    try:
        match = re.search(r'^MemAvailable:\s+(\d+) kB$', (proc/'meminfo').read_text(), re.M)
        if not match: raise ValueError('Linux available memory evidence unavailable')
        available = int(match[1])*1024
        memberships = (proc/'self/cgroup').read_text().splitlines()
        unified = [line[3:] for line in memberships if line.startswith('0::')]
        if len(unified)!=1: raise ValueError('Unsupported cgroup layout; cannot check memory safely')
        mounts = [line.split() for line in (proc/'self/mountinfo').read_text().splitlines()
                  if ' - cgroup2 ' in line]
        if len(mounts)!=1: raise ValueError('Unsupported cgroup mount; cannot check memory safely')
        def unescape(value):
            return re.sub(r'\\([0-7]{3})',lambda m: chr(int(m[1],8)),value)
        mount_root, mount = Path(unescape(mounts[0][3])), Path(unescape(mounts[0][4]))
        relative = Path(unified[0]).relative_to(mount_root)
        if '..' in relative.parts: raise ValueError('Unobservable cgroup ancestry')
        current = mount/relative
        while True:
            limit_file = current/'memory.max'
            # The actual hierarchy root has no memory.max/current by kernel design.
            if not (current==mount and mount_root==Path('/') and not limit_file.exists()):
                limit = limit_file.read_text().strip()
                usage = int((current/'memory.current').read_text().strip())
                if usage<0: raise ValueError('Invalid cgroup memory usage')
                if limit!='max': available = min(available,max(0,int(limit)-usage))
            if current==mount: break
            current = current.parent
        if mount_root!=Path('/'):
            raise ValueError('Hidden cgroup ancestors; cannot check memory safely')
        return available
    except (OSError,ValueError) as error:
        raise ValueError(f'Cannot verify Linux/cgroup memory: {error}') from error


def require_memory(heap_mib, proc=Path('/proc')):
    available = usable_memory(proc)
    required = max(3072,heap_mib+1024)*MIB
    if available<required:
        raise ValueError(f'Insufficient usable memory: {available//MIB} MiB available, {required//MIB} MiB required')
    return available


def check_java_options(env):
    for name in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS'):
        if env.get(name,'').strip():
            raise ValueError(f'{name} is set; clear it explicitly so the requested JVM limits remain effective')


def java_command(java, heap_mib, jar, outer, config=None):
    command = [str(java),'-Xms256m',f'-Xmx{heap_mib}m','-XX:+UseG1GC','-XX:+ExitOnOutOfMemoryError',
               '-Xlog:gc*:file=gc.log:time,uptime,level,tags:filecount=3,filesize=5M',
               '-jar',str(jar),'--profile','research','--output-dir',str(outer/'research')]
    if config is not None: command += ['--config',str(config)]
    return command


def read_progress(root):
    try:
        paths = list(root.glob('run-*/run.json'))
        if len(paths)!=1: raise ValueError('waiting for one manifest')
        data = json.loads(paths[0].read_text())
        attempted, done = data['attempted_cases'],data['successful_cases']
        if any(type(v)!=int or not 0<=v<=450 for v in (attempted,done)) or done>attempted:
            raise ValueError('invalid counters')
        detail = 'Cases finished; application analysis in progress' if done==450 else 'Waiting for next case'
        if done<attempted:
            active = data['cases'][attempted-1]
            detail = f"{active['scenario']} / {active['algorithm']} / replication {active['replication']} ({active['phase']})"
        if data.get('error'): detail = str(data['error'])
        return {'stage':'analysis' if done==450 and data['state']=='RUNNING' else 'research',
                'done':done,'detail':detail}
    except (OSError,ValueError,KeyError,TypeError,IndexError):
        return {'stage':'research','done':None,'detail':'Progress snapshot unavailable; child/logs remain authoritative'}


def completed_run(root, artifact_hash):
    paths = list(root.iterdir())
    if len(paths)!=1 or not paths[0].is_dir() or paths[0].is_symlink():
        raise ValueError('Expected exactly one new research run')
    path = paths[0]
    data = json.loads((path/'run.json').read_text())
    if data.get('profile')!='research' or data.get('state')!='COMPLETE' or data.get('error') is not None:
        raise ValueError('Research run is unsuccessful or incomplete')
    if any(type(data.get(k))!=int or data[k]!=450 for k in ('expected_cases','attempted_cases','successful_cases')):
        raise ValueError('Research run does not contain 450 successful cases')
    if any(type(data.get(k))!=int or data[k]!=0 for k in ('failed_cases','unattempted_cases')):
        raise ValueError('Research run contains failed or unattempted cases')
    if data.get('artifact_sha256')!=artifact_hash:
        raise ValueError('Research artifact SHA-256 differs from retained JAR')
    return path


def rss(pid):
    try:
        match = re.search(r'^VmRSS:\s+(\d+) kB$',Path(f'/proc/{pid}/status').read_text(),re.M)
        return f'{int(match[1])//1024} MiB' if match else 'unavailable'
    except OSError: return 'unavailable'


class Dashboard:
    def __init__(self, stream=None, plain=False, console=None, heap_mib=1024):
        self.stream = stream if stream is not None else sys.stdout
        self.tty = not plain and self.stream.isatty() and os.environ.get('TERM','dumb')!='dumb' and 'NO_COLOR' not in os.environ
        self.console = console
        self.started = time.monotonic()
        self.updated = 0
        self.drawn = False
        self.warning = None
        self.heap_mib = heap_mib

    def render(self,stage,progress=None,pid=None,force=False):
        now = time.monotonic()
        if not force and now-self.updated<1: return
        self.updated = now
        progress = progress or {}
        done = progress.get('done')
        detail = sanitize(progress.get('detail',''))
        elapsed = int(now-self.started)
        bar = ('['+'#'*(done*20//450)+'-'*(20-done*20//450)+f'] {done*100//450}% cases') if done is not None else 'Progress: stage in progress'
        lines = ['CLOUDSIM  /  RESEARCH',f'Stage: {stage}    Elapsed: {elapsed//60:02d}:{elapsed%60:02d}',
                 f'Cases: {done if done is not None else "-"}/450    Java RSS: {rss(pid) if pid else "-"}    Heap: {self.heap_mib} MiB',bar,detail]
        if self.console:
            self.console.write(' | '.join(lines)+'\n'); self.console.flush()
        try:
            if self.tty:
                width = max(1,shutil.get_terminal_size((80,24)).columns-1)
                if self.drawn: self.stream.write('\x1b[5A')
                else: self.stream.write('\x1b[?25l')
                for index,line in enumerate(lines):
                    self.stream.write('\x1b[2K'+ ('\x1b[36m' if index==0 else '')+clip_cells(line,width)+('\x1b[0m' if index==0 else '')+'\n')
                self.drawn = True
            else:
                self.stream.write(f'[{stage}] {elapsed}s | {lines[2]} | {detail}\n')
            self.stream.flush()
        except (OSError,ValueError) as error:
            self.warning = f'UI_WARNING: dashboard output failed: {sanitize(error)}'
            self.tty = False
            if self.console: self.console.write(self.warning+'\n'); self.console.flush()

    def close(self):
        if self.drawn:
            try: self.stream.write('\x1b[0m\x1b[?25h'); self.stream.flush()
            except (OSError,ValueError): pass


class Interrupted(Exception):
    def __init__(self,signum): self.signum = signum


class ProcessControl:
    def __init__(self,dashboard,root):
        self.dashboard,self.root = dashboard,root
        self.signum = None
        self.handlers = {}

    def install(self):
        for number in (signal.SIGINT,signal.SIGTERM):
            self.handlers[number] = signal.signal(number,self.on_signal)

    def on_signal(self,number,frame): self.signum = number

    def check(self):
        if self.signum is not None: raise Interrupted(self.signum)

    def restore(self):
        for number,handler in self.handlers.items(): signal.signal(number,handler)
        self.dashboard.close()

    def stop(self,child):
        try: os.killpg(child.pid,signal.SIGTERM)
        except ProcessLookupError: child.wait(); return
        deadline = time.monotonic()+3
        while time.monotonic()<deadline:
            child.poll()
            try: os.killpg(child.pid,0)
            except ProcessLookupError: child.wait(); return
            time.sleep(.05)
        try: os.killpg(child.pid,signal.SIGKILL)
        except ProcessLookupError: pass
        child.wait(timeout=3)

    def run(self,command,log,stage,cwd=None,timeout=12*60*60,stderr_log=None):
        self.check()
        with ExitStack() as files:
            output = files.enter_context(log.open('wb'))
            errors = files.enter_context(stderr_log.open('wb')) if stderr_log else subprocess.STDOUT
            child = subprocess.Popen(command,cwd=cwd or self.root,stdout=output,stderr=errors,start_new_session=True)
            started = time.monotonic()
            observed = 0
            try:
                while child.poll() is None:
                    self.check()
                    if timeout and time.monotonic()-started>timeout: raise ValueError(f'{stage} timed out')
                    if time.monotonic()-observed>=1:
                        observed = time.monotonic()
                        progress = read_progress(self.root/'research') if stage=='research' else {'detail':f'Full output: {log}'}
                        self.dashboard.render(progress.get('stage',stage),progress,child.pid if stage=='research' else None)
                    time.sleep(.1)
                self.check()
                return child.returncode if child.returncode>=0 else 128-child.returncode
            finally: self.stop(child)


def stamp(): return datetime.now(timezone.utc).isoformat()


def save_metadata(outer,metadata):
    temporary = outer/'runner.json.tmp'
    temporary.write_text(json.dumps(metadata,indent=2)+'\n')
    temporary.replace(outer/'runner.json')


def sha256(path):
    with path.open('rb') as source: return hashlib.file_digest(source,'sha256').hexdigest()


def heap_value(value):
    if not value.isascii() or not value.isdigit() or int(value)<512:
        raise argparse.ArgumentTypeError('heap must be an integer MiB value of at least 512')
    return int(value)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__,epilog='Requires a full JDK 21 (select with JAVA_HOME), Linux memory evidence, and Python 3.11+. Retained outputs are never deleted. Monitoring consumes some runtime resources.')
    parser.add_argument('--output-dir',type=Path,default=ROOT/'results',help='parent for a unique retained runner directory (default: project results)')
    parser.add_argument('--heap-mib',type=heap_value,default=1024,help='Java maximum heap in MiB (default: 1024, minimum: 512)')
    parser.add_argument('--config',type=Path,help='existing properties file; only master.seed and log.level are configurable')
    parser.add_argument('--plain','--no-tui',action='store_true',help='plain status lines')
    parser.add_argument('--skip-build',action='store_true',help='skip Maven clean verify and Python discovery; use the existing packaged JAR')
    parser.add_argument('--check',action='store_true',help='check JDK/environment/memory only; do not build or run research')
    args = parser.parse_args(argv)
    outer = None
    metadata = {'started_at':stamp(),'status':'preflight','exit_code':None,'profile':'research','heap_mib':args.heap_mib,
                'skip_build':args.skip_build,'validation':'NOT_RUN','arguments':list(argv if argv is not None else sys.argv[1:])}
    dashboard = Dashboard(plain=args.plain,heap_mib=args.heap_mib)
    control = ProcessControl(dashboard,ROOT)
    control.install()
    code = 1
    console = None
    try:
        check_java_options(os.environ)
        if args.config:
            args.config = args.config.resolve(strict=True)
            if not args.config.is_file(): raise ValueError('Config must be an existing file')
        java = Path(os.environ['JAVA_HOME'])/'bin/java' if os.environ.get('JAVA_HOME') else Path(shutil.which('java') or '/missing-java').resolve()
        java = java.resolve(strict=True)
        javac = java.parent/'javac'
        if not javac.is_file(): raise ValueError('Select a full JDK 21 with JAVA_HOME (javac missing)')
        # Temporary version logs are the only preflight artifacts; no results directory is created.
        with tempfile.TemporaryDirectory(prefix='research-preflight-') as temporary:
            for tool in (java,javac):
                log = Path(temporary)/tool.name
                result = control.run([str(tool),'-version'],log,'preflight',timeout=20)
                if result or not re.search(r'(?:version\s+"?|javac\s+)21(?:[.\s"+-]|$)',log.read_text(errors='replace')):
                    raise ValueError(f'{tool.name} must be version 21; select a full JDK 21 with JAVA_HOME')
        available = require_memory(args.heap_mib)
        if args.check:
            dashboard.render('preflight',{'detail':f'PASS: JDK 21; {available//MIB} MiB usable memory. No build/research/validation executed.'},force=True)
            return 0
        args.output_dir = args.output_dir.resolve()
        args.output_dir.mkdir(parents=True,exist_ok=True)
        outer = Path(tempfile.mkdtemp(prefix='research-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-',dir=args.output_dir))
        control.root = outer
        console = (outer/'console.log').open('a')
        dashboard.console = console
        metadata['status'] = 'provenance'; save_metadata(outer,metadata)
        for arguments,name in [(['status','--porcelain'],'source-status'),(['rev-parse','HEAD'],'source-revision')]:
            code = control.run(['git',*arguments],outer/f'{name}.log','preflight',cwd=ROOT,timeout=10,stderr_log=outer/f'{name}.stderr.log')
            if code: raise ValueError(f'Git provenance failed with exit {code}; see {outer}/{name}.stderr.log')
        status = (outer/'source-status.log').read_text(errors='replace')
        revision = (outer/'source-revision.log').read_text(errors='replace').strip()
        metadata.update(source_revision=revision,source_dirty=bool(status),source_status=status,java=str(java),
                        config=str(args.config) if args.config else None,output_directory=str(outer))
        save_metadata(outer,metadata)
        if args.skip_build:
            dashboard.render('build',{'detail':'SKIPPED: Maven clean verify and Python test discovery (--skip-build).'},force=True)
        else:
            for command,log in [([str(ROOT/'mvnw'),'-B','clean','verify'],'build.log'),
                                ([sys.executable,'-B','-m','unittest','discover','-s','scripts','-p','test_*.py'],'python-tests.log')]:
                metadata['status']='build'; save_metadata(outer,metadata)
                code = control.run(command,outer/log,'build',cwd=ROOT)
                if code: raise ValueError(f'Build/check failed with exit {code}; see {outer/log}')
        jars = list((ROOT/'target').glob('cloudsim-ho-research-v2-*.jar'))
        if len(jars)!=1: raise ValueError('Expected exactly one packaged target/cloudsim-ho-research-v2-*.jar')
        retained = outer/'research.jar'; shutil.copyfile(jars[0],retained)
        digest = sha256(retained)
        metadata.update(artifact_sha256=digest,artifact_source=str(jars[0]))
        command = java_command(java,args.heap_mib,retained,outer,args.config)
        metadata.update(java_command=command,status='research'); save_metadata(outer,metadata)
        require_memory(args.heap_mib); control.check()
        code = control.run(command,outer/'research.log','research',cwd=outer)
        if code: raise ValueError(f'Research child failed with exit {code}; see {outer/"research.log"}')
        run = completed_run(outer/'research',digest)
        if sha256(retained)!=digest: raise ValueError('Retained JAR changed after launch')
        artifact = json.loads((run/'run.json').read_text())
        metadata.update(status='validation',run_directory=str(run),artifact_revision=artifact.get('git_revision'),artifact_dirty=artifact.get('git_dirty'))
        save_metadata(outer,metadata)
        require_memory(512); control.check()
        code = control.run([sys.executable,'-B',str(ROOT/'scripts/statistics_validator.py'),str(run)],outer/'validation.log','validation',cwd=ROOT)
        if code: raise ValueError(f'Independent validation failed with exit {code}; see {outer/"validation.log"}')
        completed_run(outer/'research',digest)
        if sha256(retained)!=digest: raise ValueError('Retained JAR changed during validation')
        with (run/'analysis/pairwise_primary.csv').open(newline='') as claims:
            decisions = [row['decision'] for row in csv.DictReader(claims)]
        diagnostic = bool(status) or artifact.get('git_dirty') is not False or artifact.get('git_revision')!=revision or args.skip_build
        detail = f'VALIDATED: 450/450; {decisions.count("CLAIM")} CLAIM, {decisions.count("NO_CLAIM")} NO_CLAIM. '+('Source/artifact diagnostics.' if diagnostic else 'Clean-source run.')
        metadata.update(status='complete',validation='PASS',diagnostic=diagnostic,claims=decisions.count('CLAIM'),no_claim=decisions.count('NO_CLAIM'))
        dashboard.render('complete',{'done':450,'detail':detail+f' Retained: {outer}'},force=True)
        code = 0
    except Interrupted as error:
        code = 128+error.signum
        metadata.update(status='interrupted',error=f'Interrupted by signal {error.signum}')
        dashboard.render('interrupted',{'detail':metadata['error']+f'; retained: {outer}'},force=True)
    except (OSError,ValueError,KeyError,subprocess.SubprocessError) as error:
        code = code if code not in (0,1) else 1
        metadata.update(status='failed',error=sanitize(error))
        dashboard.render('failed',{'detail':metadata['error']+f'; retained: {outer}'},force=True)
    finally:
        control.restore()
        metadata.update(exit_code=code,finished_at=stamp(),ui_warning=dashboard.warning)
        if outer: save_metadata(outer,metadata)
        if console: console.close()
        if outer:
            try: print(f'Retained results: {sanitize(outer)}')
            except (OSError,ValueError): pass
    return code


if __name__=='__main__': sys.exit(main())
