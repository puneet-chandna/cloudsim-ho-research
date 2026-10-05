#!/usr/bin/env python3
"""Build, run the fixed research matrix, and independently validate its retained artifact."""
import argparse
import csv
from copy import deepcopy
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
import cloudsim_runtime
import cloudsim_build
import lattora_context
from datetime import datetime, timezone
from contextlib import ExitStack

ROOT = Path(__file__).resolve().parents[1]
MIB = 1024**2
FROZEN_PROFILES = {'smoke':4, 'explore':40, 'research':450}


def profile_total(profile):
    if profile not in FROZEN_PROFILES: raise ValueError('Profile must be smoke, explore or research')
    return FROZEN_PROFILES[profile]


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


def physical_memory(proc=None):
    """Physical RAM; separate from available-memory launch headroom."""
    if proc is None and sys.platform == 'darwin': return cloudsim_runtime.mac_physical_memory()
    proc = Path('/proc') if proc is None else proc
    try:
        match=re.search(r'^MemTotal:\s+(\d+) kB$',(proc/'meminfo').read_text(),re.M)
        if not match or int(match[1])<=0: raise ValueError('Missing or invalid MemTotal')
        return int(match[1])*1024
    except (OSError,ValueError) as error:
        raise ValueError(f'Cannot verify physical memory: {error}') from error


def usable_memory(proc=None):
    """Native macOS pages or Linux MemAvailable plus all cgroup-v2 ancestors."""
    if proc is None and sys.platform == 'darwin': return cloudsim_runtime.mac_usable_memory()
    proc = Path('/proc') if proc is None else proc
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


def require_memory(heap_mib, proc=None):
    available = usable_memory(proc)
    required = max(3072,heap_mib+1024)*MIB
    if available<required:
        raise ValueError(f'Insufficient usable memory: {available//MIB} MiB available, {required//MIB} MiB required')
    return available


def check_java_options(env):
    for name in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS'):
        if env.get(name,'').strip():
            raise ValueError(f'{name} is set; clear it explicitly so the requested JVM limits remain effective')


def java_command(java, heap_mib, jar, outer, config=None, *, profile='research', workers=1):
    profile_total(profile)
    command = [str(java),'-Xms256m',f'-Xmx{heap_mib}m','-XX:+UseG1GC','-XX:+ExitOnOutOfMemoryError',
               '-Xlog:gc*:file=gc.log:time,uptime,level,tags:filecount=3,filesize=5M',
               '-jar',str(jar),'--profile',profile,'--output-dir',str(outer/profile)]
    if config is not None: command += ['--config',str(config)]
    if workers > 1: command.insert(command.index('-jar'), f'-Dcloudsim.workers={workers}')
    return command


def maven_command(*arguments, root=None):
    root = ROOT if root is None else root
    return [str(root/'mvnw'), '-B', '-Dmaven.repo.local='+str(root/'.cloudsim/maven/repository'), *arguments]


def read_progress(root, *, profile='research'):
    total = profile_total(profile)
    try:
        paths = list(root.glob('run-*/run.json'))
        if len(paths)!=1: raise ValueError('waiting for one manifest')
        data = json.loads(paths[0].read_text())
        if data.get('profile')!=profile: raise ValueError('wrong profile')
        attempted, done = data['attempted_cases'],data['successful_cases']
        if any(type(v)!=int or not 0<=v<=total for v in (attempted,done)) or done>attempted:
            raise ValueError('invalid counters')
        detail = 'Cases finished; application analysis in progress' if done==total else 'Waiting for next case'
        active = {}
        if done<attempted:
            active = data['cases'][attempted-1]
            if not isinstance(active,dict): raise ValueError('invalid active case')
            detail = f"{active['scenario']} / {active['algorithm']} / replication {active['replication']} ({active['phase']})"
        if data.get('error'): detail = str(data['error'])
        return {'stage':'analysis' if done==total and data['state']=='RUNNING' else profile,
                'done':done,'total':total,'detail':detail,
                **{key:active.get(key) for key in ('algorithm','scenario','replication','phase')}}
    except (OSError,ValueError,KeyError,TypeError,IndexError):
        return {'stage':profile,'done':None,'total':total,'detail':'Progress snapshot unavailable; child/logs remain authoritative'}


def completed_run(root, artifact_hash, *, profile='research'):
    total = profile_total(profile)
    label = profile.capitalize()
    paths = list(root.iterdir())
    if len(paths)!=1 or not paths[0].is_dir() or paths[0].is_symlink():
        raise ValueError(f'Expected exactly one new {profile} run')
    path = paths[0]
    data = json.loads((path/'run.json').read_text())
    if data.get('profile')!=profile or data.get('state')!='COMPLETE' or data.get('error') is not None:
        raise ValueError(f'{label} run is unsuccessful or incomplete')
    if any(type(data.get(k))!=int or data[k]!=total for k in ('expected_cases','attempted_cases','successful_cases')):
        raise ValueError(f'{label} run does not contain {total} successful cases')
    if any(type(data.get(k))!=int or data[k]!=0 for k in ('failed_cases','unattempted_cases')):
        raise ValueError(f'{label} run contains failed or unattempted cases')
    if data.get('artifact_sha256')!=artifact_hash:
        raise ValueError(f'{label} artifact SHA-256 differs from retained JAR')
    return path


def rss(pid):
    if sys.platform == 'darwin':
        info = cloudsim_runtime.mac_process_info(pid)
        return f'{info["rss_bytes"]//MIB} MiB' if info else 'unavailable'
    try:
        match = re.search(r'^VmRSS:\s+(\d+) kB$',Path(f'/proc/{pid}/status').read_text(),re.M)
        return f'{int(match[1])//1024} MiB' if match else 'unavailable'
    except OSError: return 'unavailable'


def sampled_rss(pid):
    """Linux high-water RSS, or instantaneous native macOS RSS (sampled max)."""
    if sys.platform == 'darwin':
        info = cloudsim_runtime.mac_process_info(pid)
        return info['rss_bytes'] if info else None
    try:
        match = re.search(r'^VmHWM:\s+(\d+) kB$',Path(f'/proc/{pid}/status').read_text(),re.M)
        return int(match[1])*1024 if match else None
    except OSError: return None


class Dashboard:
    def __init__(self, stream=None, plain=False, console=None, heap_mib=1024, title='RESEARCH', total=450):
        self.stream = stream if stream is not None else sys.stdout
        self.tty = not plain and self.stream.isatty() and os.environ.get('TERM','dumb')!='dumb' and 'NO_COLOR' not in os.environ
        self.console = console
        self.started = time.monotonic()
        self.updated = 0
        self.drawn = False
        self.line_count = 0
        self.warning = None
        self.heap_mib = heap_mib
        self.title,self.total = title,total

    def poll(self,control) -> None: pass

    def set_log(self,path: Path | None) -> None: self.log = path

    def render(self,stage,progress=None,pid=None,force=False):
        now = time.monotonic()
        if not force and now-self.updated<1: return
        self.updated = now
        progress = progress or {}
        done = progress.get('done')
        detail = sanitize(progress.get('detail',''))
        elapsed = int(now-self.started)
        total = progress.get('total',self.total)
        bar = ('['+'#'*(done*20//total)+'-'*(20-done*20//total)+f'] {done*100//total}% cases') if done is not None else 'Progress: stage in progress'
        lines = [f'CLOUDSIM  /  {self.title}',f'Stage: {stage}    Elapsed: {elapsed//60:02d}:{elapsed%60:02d}',
                 f'Cases: {done if done is not None else "-"}/{total}    Java RSS: {rss(pid) if pid else "-"}    Heap: {self.heap_mib} MiB',bar,detail]
        lines += [sanitize(line) for line in progress.get('extra_lines',[])[:3]]
        if self.console:
            self.console.write(' | '.join(lines)+'\n'); self.console.flush()
        try:
            if self.tty:
                width = max(1,shutil.get_terminal_size((80,24)).columns-1)
                if self.drawn:
                    self.stream.write(f'\x1b[{self.line_count}A')
                    lines += ['']*max(0,self.line_count-len(lines))
                else: self.stream.write('\x1b[?25l')
                for index,line in enumerate(lines):
                    self.stream.write('\x1b[2K'+ ('\x1b[36m' if index==0 else '')+clip_cells(line,width)+('\x1b[0m' if index==0 else '')+'\n')
                self.drawn = True
                self.line_count = len(lines)
            else:
                self.stream.write(f'[{stage}] {elapsed}s | {lines[2]} | '+ ' | '.join(lines[4:])+'\n')
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


class DeadlineExceeded(ValueError):
    pass


class PresentationError(ValueError):
    pass


class ProcessControl:
    def __init__(self,dashboard,root):
        self.dashboard,self.root = dashboard,root
        self.env = dict(os.environ)
        self.signum = None
        self.handlers = {}
        self.warning = None

    def present(self,method,*args,**kwargs):
        try:
            console = getattr(self.dashboard,'console',None)
            if method=='render' and console and not isinstance(self.dashboard,Dashboard):
                console.write(f'[{sanitize(args[0])}] {sanitize(args[1] or {})}\n'); console.flush()
            return getattr(self.dashboard,method)(*args,**kwargs)
        except (Interrupted,DeadlineExceeded): raise
        except Exception as error:
            self.warning = f'UI_WARNING: presentation {method} failed: {sanitize(error)}'
            raise PresentationError(self.warning) from error

    def render(self,stage,progress=None,pid=None,force=False):
        return self.present('render',stage,progress,pid,force=force)

    def install(self):
        for number in (signal.SIGINT,signal.SIGTERM):
            self.handlers[number] = signal.signal(number,self.on_signal)

    def on_signal(self,number,frame): self.signum = number

    def check(self,deadline=None):
        if self.signum is not None: raise Interrupted(self.signum)
        if deadline is not None and time.monotonic()>=deadline:
            raise DeadlineExceeded('Shared experiment deadline exceeded')

    def restore(self):
        for number,handler in self.handlers.items(): signal.signal(number,handler)
        self.handlers.clear()
        self.present('close')

    def _signal_group(self,pid,number):
        try: os.killpg(pid,number)
        except ProcessLookupError: return False
        except PermissionError:
            # Darwin can report EPERM while a member exits, before its BSD
            # state becomes SZOMB. Confirm completion within a bounded grace
            # period; live members and unreadable probes still fail closed.
            if sys.platform == 'darwin':
                deadline = time.monotonic()+.2
                while True:
                    if cloudsim_runtime.mac_group_exited(pid): return False
                    if time.monotonic() >= deadline: break
                    time.sleep(.01)
            raise
        return True

    def stop(self,child):
        if not self._signal_group(child.pid,signal.SIGTERM): child.wait(); return
        deadline = time.monotonic()+3
        while time.monotonic()<deadline:
            try: self.present('poll',self)
            except Exception: pass  # Presentation must never prevent owned-group cleanup.
            child.poll()
            if not self._signal_group(child.pid,0): child.wait(); return
            time.sleep(.05)
        self._signal_group(child.pid,signal.SIGKILL)
        child.wait(timeout=3)

    def run(self,command,log,stage,cwd=None,timeout=12*60*60,stderr_log=None,deadline=None,progress_reader=None):
        self.present('set_log',log)
        self.present('poll',self)
        self.check(deadline)
        with ExitStack() as files:
            output = files.enter_context(log.open('wb'))
            errors = files.enter_context(stderr_log.open('wb')) if stderr_log else subprocess.STDOUT
            child = subprocess.Popen(command,cwd=cwd or self.root,stdout=output,stderr=errors,start_new_session=True,env=self.env)
            self.active_pid = child.pid
            started = time.monotonic()
            observed = 0
            peak = None
            try:
                while True:
                    self.present('poll',self)
                    self.check(deadline)
                    if child.poll() is not None: break
                    if timeout and time.monotonic()-started>timeout: raise ValueError(f'{stage} timed out')
                    if deadline is not None or progress_reader is not None:
                        sample = sampled_rss(child.pid)
                        if sample is not None: peak=max(peak or 0, sample)
                    if time.monotonic()-observed>=1:
                        observed = time.monotonic()
                        progress = progress_reader() if progress_reader else read_progress(self.root/'research') if stage=='research' else {'detail':f'Full output: {log}'}
                        self.render(progress.get('stage',stage),progress,child.pid if stage=='research' or progress_reader else None)
                    time.sleep(.1)
                self.check(deadline)
                self.last_exit_code = child.returncode if child.returncode>=0 else 128-child.returncode
                return self.last_exit_code
            finally:
                self.stop(child)
                self.active_pid = None
                self.last_measurement = {'wall_seconds':time.monotonic()-started,'sampled_peak_rss_bytes':peak}


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


def worker_value(value):
    if value == 'auto': return value
    if not re.fullmatch(r'[0-9]+', value) or not 1 <= int(value) <= 32:
        raise argparse.ArgumentTypeError('workers must be auto or an integer from 1 to 32')
    return int(value)


def available_cpus():
    try: return max(1, len(os.sched_getaffinity(0)))
    except (AttributeError, OSError): return max(1, os.cpu_count() or 1)


def execution_settings(requested, heap_mib):
    cpus = available_cpus()
    heap_bound = max(1, heap_mib // 512)
    workers = min(cpus, heap_bound, 32, 32 if requested == 'auto' else requested)
    return {'requested_workers': requested, 'workers': workers, 'available_cpus': cpus,
            'shared_heap_mib': heap_mib, 'heap_worker_bound': heap_bound,
            'policy': 'Independent cases share one bounded JVM heap; at most one worker per 512 MiB and available CPU. This is a concurrency bound, not a measured per-case memory guarantee.'}


def check_environment(control, heap_mib: int) -> tuple[Path, int]:
    check_java_options(control.env)
    context = lattora_context.get_context(ROOT)
    if context.bundled:
        context.manifest()
        java = context.java
        control.env.update(JAVA_HOME=str(java.parent.parent))
        with tempfile.TemporaryDirectory(prefix='lattora-preflight-') as temporary:
            log = Path(temporary)/'java'
            code = control.run([str(java), '-version'], log, 'preflight', timeout=20)
            if code or not re.search(r'version\s+"21(?:[.\s"+-]|$)', log.read_text(errors='replace')):
                raise ValueError('Bundled Java 21 is unavailable; reinstall Lattora.')
        return java, require_memory(heap_mib)
    def verify(home):
        tools = {}
        with tempfile.TemporaryDirectory(prefix='cloudsim-preflight-') as temporary:
            for name in ('java','javac'):
                tool = home/'bin'/name
                if not tool.is_file(): raise ValueError(f'{tool} is missing')
                log = Path(temporary)/name
                result = control.run([str(tool),'-version'],log,'preflight',timeout=20)
                if result or not re.search(r'(?:version\s+"?|javac\s+)21(?:[.\s"+-]|$)',log.read_text(errors='replace')):
                    raise ValueError(f'{name} must be version 21; select a full JDK 21 with JAVA_HOME')
                tools[name] = {'path': str(tool.resolve()), 'version': log.read_text(errors='replace').strip()}
        release = home/'release'
        tools['release_sha256'] = sha256(release) if release.is_file() else None
        modules = home/'lib/modules'
        tools['modules_sha256'] = sha256(modules) if modules.is_file() else None
        control.toolchain = tools
        return home
    control.env = cloudsim_runtime.java_environment(control.env, ROOT, verify=verify)
    java = Path(control.env['JAVA_HOME'])/'bin/java' if control.env.get('JAVA_HOME') else Path('/missing-java')
    try: java = java.resolve(strict=True)
    except OSError as error:
        raise ValueError(f'Java setup required: the selected Java is unavailable at {java}. '
                         'Open Setup to select or install a full JDK 21, then Check again.') from error
    javac = java.parent/'javac'
    if not javac.is_file():
        raise ValueError(f'Java setup required: {java} has no matching javac. '
                         'Use Setup in the app, or run ./cloudsim.sh --setup to install a local JDK 21. '
                         'Run ./cloudsim.sh --check to confirm setup; changing experiment settings cannot fix this.')
    verify(java.parent.parent)
    return java,require_memory(heap_mib)


def finish(control, metadata, code, outer=None, console=None, on_complete=None):
    """Restore presentation/signals and publish a detached copy of the retained outcome."""
    try: control.restore()
    except PresentationError as error:
        code = code or 1
        metadata.update(status='failed',error=sanitize(error))
    metadata.update(exit_code=code,finished_at=stamp(),
                    ui_warning=control.warning or getattr(control.dashboard,'warning',None))
    try:
        if outer: save_metadata(outer,metadata)
        if on_complete:
            try: on_complete(deepcopy(metadata))
            except Exception as error:
                code = code or 1
                metadata.update(status='failed',exit_code=code,error=f'Completion presentation failed: {sanitize(error)}')
                if outer: save_metadata(outer,metadata)
    finally:
        if console:
            console.close()
            if getattr(control.dashboard,'console',None) is console: control.dashboard.console = None
    return code


def main(argv=None, *, profile='research', dashboard=None, on_complete=None, invocation=None) -> int:
    total = profile_total(profile)
    parser = argparse.ArgumentParser(description=__doc__,epilog='Requires a full JDK 21 (select with JAVA_HOME), Linux or macOS memory evidence, and Python 3.11+. Retained outputs are never deleted. Monitoring consumes some runtime resources.')
    parser.add_argument('--output-dir',type=Path,default=ROOT/'results',help='parent for a unique retained runner directory (default: project results)')
    parser.add_argument('--heap-mib',type=heap_value,default=1024,help='Java maximum heap in MiB (default: 1024, minimum: 512)')
    parser.add_argument('--config',type=Path,help='existing properties file; only master.seed and log.level are configurable')
    parser.add_argument('--plain','--no-tui',action='store_true',help='plain status lines')
    parser.add_argument('--skip-build',action='store_true',help='skip Maven clean verify and Python discovery; use the existing packaged JAR')
    parser.add_argument('--force-build',action='store_true',help='run full verification even when a matching tested build exists')
    parser.add_argument('--workers',type=worker_value,default='auto',help='independent case workers: auto or 1..32, bounded by CPU and shared heap')
    parser.add_argument('--check',action='store_true',help='check JDK/environment/memory only; do not build or run research')
    args = parser.parse_args(argv)
    if args.skip_build and args.force_build: parser.error('--force-build and --skip-build are incompatible')
    outer = None
    metadata = {'started_at':stamp(),'status':'preflight','exit_code':None,'profile':profile,'heap_mib':args.heap_mib,
                'skip_build':args.skip_build,'validation':'NOT_RUN','tests':'NOT_RUN',
                'arguments':list(argv if argv is not None else sys.argv[1:])}
    metadata['execution'] = execution_settings(args.workers, args.heap_mib)
    if invocation is not None: metadata['invocation'] = deepcopy(invocation)
    supplied_dashboard = dashboard is not None
    dashboard = dashboard if supplied_dashboard else Dashboard(plain=args.plain,heap_mib=args.heap_mib,title=profile.upper(),total=total)
    control = ProcessControl(dashboard,ROOT)
    control.install()
    code = 1
    console = None
    try:
        args.output_dir = args.output_dir.resolve()
        args.output_dir.mkdir(parents=True,exist_ok=True)
        outer = Path(tempfile.mkdtemp(prefix=profile+'-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-',dir=args.output_dir))
        control.root = outer
        console = (outer/'console.log').open('a'); dashboard.console = console
        metadata['output_directory'] = str(outer)
        save_metadata(outer,metadata)
        if args.config:
            args.config = args.config.resolve(strict=True)
            if not args.config.is_file(): raise ValueError('Config must be an existing file')
        java,available = check_environment(control,args.heap_mib)
        if args.check:
            control.render('preflight',{'detail':f'PASS: JDK 21; {available//MIB} MiB usable memory. No build/research/validation executed.'},force=True)
            metadata.update(status='checked')
            code = 0
        else:
            code = run_profile(args,profile,total,java,metadata,control)
            outer = control.root if control.root!=ROOT else None
            console = getattr(dashboard,'console',None)
    except Interrupted as error:
        code = 128+error.signum
        metadata.update(status='interrupted',error=f'Interrupted by signal {error.signum}')
    except (OSError,ValueError,KeyError,subprocess.SubprocessError) as error:
        code = getattr(error,'exit_code',0) or getattr(control,'last_exit_code',0) or 1
        metadata.update(status='failed',error=sanitize(error))
    finally:
        outer = control.root if control.root!=ROOT else None
        console = getattr(dashboard,'console',None)
        if metadata['status'] in ('failed','interrupted'):
            try: control.render(metadata['status'],{'detail':metadata['error']+f'; retained: {outer}'},force=True)
            except PresentationError: pass
        code = finish(control,metadata,code,outer,console,on_complete)
        if outer and not supplied_dashboard:
            try: print(f'Retained results: {sanitize(outer)}')
            except (OSError,ValueError): pass
    return code


def prepare_artifact(args, metadata, control, outer, profile, *, root=None):
    root = ROOT if root is None else root
    retained = outer/f'{profile}.jar'
    context = lattora_context.get_context(root)
    if context.bundled:
        if args.skip_build or args.force_build:
            raise ValueError('Build controls require a source checkout; installed Lattora uses its verified release.')
        manifest = context.manifest()
        source = root/'engine/app.jar'
        provenance = lattora_context.jar_provenance(source)
        if (provenance.get('Implementation-Version') != manifest['version']
                or provenance.get('Git-Revision') != manifest['source_revision']
                or provenance.get('Git-Dirty') != str(manifest['source_dirty']).lower()):
            raise ValueError('Installed engine provenance does not match the distribution manifest.')
        metadata.update(context.release_metadata())
        shutil.copyfile(source, retained)
        if sha256(retained) != manifest['files']['engine/app.jar']['sha256']:
            raise ValueError('Retained engine integrity failed.')
        control.render('build', {'detail':'Verified release engine; local build/tests NOT RUN. Experiment validation runs freshly.'}, force=True)
        return retained, source
    if args.skip_build:
        control.render('build', {'detail': 'SKIPPED: diagnostic build skip; verification NOT RUN.'}, force=True)
        with cloudsim_build.build_lock(root, control):
            jars = list((root/'target').glob('cloudsim-ho-research-v2-*.jar'))
            if len(jars) != 1: raise ValueError('Expected exactly one packaged target JAR')
            source = jars[0]
            shutil.copyfile(source, retained)
        metadata['build'] = 'SKIPPED_DIAGNOSTIC'
    else:
        metadata['status'] = 'build'; save_metadata(outer, metadata)
        source = cloudsim_build.ensure_verified_build(root, control, metadata, outer, force=args.force_build)
        shutil.copyfile(source, retained)
        expected = metadata['build_receipt']['artifact']['sha256']
        if sha256(retained) != expected or sha256(source) != expected:
            metadata['tests'] = 'FAILED'
            raise ValueError('Campaign JAR does not match its verified receipt; no experiment was started.')
    return retained, source


def capture_provenance(control, metadata, outer, *, root=None):
    root = ROOT if root is None else root
    context = lattora_context.get_context(root)
    if context.bundled:
        metadata.update(context.release_metadata())
        return
    for arguments, name in [(['status','--porcelain'],'source-status'), (['rev-parse','HEAD'],'source-revision')]:
        code = control.run(['git', *arguments], outer/f'{name}.log', 'provenance', cwd=root,
                           timeout=10, stderr_log=outer/f'{name}.stderr.log')
        if code: raise ValueError(f'Git provenance failed with exit {code}; see {outer}/{name}.stderr.log')
    status = (outer/'source-status.log').read_text(errors='replace')
    metadata.update(source_status=status, source_dirty=bool(status),
                    source_revision=(outer/'source-revision.log').read_text().strip())


def run_profile(args,profile,total,java,metadata,control):
    dashboard = control.dashboard
    # Build receipts preserve full verification; experiments still validate independently.
    outer = control.root
    metadata['status'] = 'provenance'; save_metadata(outer,metadata)
    capture_provenance(control, metadata, outer)
    status = metadata['source_status']; revision = metadata['source_revision']
    metadata.update(java=str(java), config=str(args.config) if args.config else None, output_directory=str(outer))
    save_metadata(outer,metadata)
    retained, artifact_source = prepare_artifact(args, metadata, control, outer, profile)
    save_metadata(outer,metadata)
    digest = sha256(retained)
    metadata.update(artifact_sha256=digest,artifact_source=str(artifact_source))
    command = java_command(java,args.heap_mib,retained,outer,args.config,profile=profile,workers=metadata['execution']['workers'])
    metadata.update(java_command=command,status=profile); save_metadata(outer,metadata)
    require_memory(args.heap_mib); control.check()
    code = control.run(command,outer/f'{profile}.log',profile,cwd=outer,
                       progress_reader=lambda:read_progress(outer/profile,profile=profile))
    if code: raise ValueError(f'{profile.capitalize()} child failed with exit {code}; see {outer/f"{profile}.log"}')
    run = completed_run(outer/profile,digest,profile=profile)
    if sha256(retained)!=digest: raise ValueError('Retained JAR changed after launch')
    artifact = json.loads((run/'run.json').read_text())
    metadata.update(status='validation',run_directory=str(run),artifact_revision=artifact.get('git_revision'),artifact_dirty=artifact.get('git_dirty'))
    save_metadata(outer,metadata)
    require_memory(512); control.check()
    code = control.run([sys.executable,'-B',str(ROOT/'scripts/statistics_validator.py'),str(run)],outer/'validation.log','validation',cwd=ROOT)
    if code: raise ValueError(f'Independent validation failed with exit {code}; see {outer/"validation.log"}')
    completed_run(outer/profile,digest,profile=profile)
    if sha256(retained)!=digest: raise ValueError('Retained JAR changed during validation')
    diagnostic = bool(status) or artifact.get('git_dirty') is not False or artifact.get('git_revision')!=revision or args.skip_build
    detail = f'VALIDATED: {total}/{total}; '
    if profile=='research':
        with (run/'analysis/pairwise_primary.csv').open(newline='') as claims:
            decisions = [row['decision'] for row in csv.DictReader(claims)]
        metadata.update(claims=decisions.count('CLAIM'),no_claim=decisions.count('NO_CLAIM'))
        detail += f'{decisions.count("CLAIM")} CLAIM, {decisions.count("NO_CLAIM")} NO_CLAIM. '
    else: detail += f'{profile.capitalize()} frozen profile; no research claims. '
    detail += 'Source/artifact diagnostics.' if diagnostic else 'Clean-source run.'
    metadata.update(status='complete',validation='PASS',diagnostic=diagnostic)
    control.render('complete',{'done':total,'total':total,'detail':detail+f' Retained: {outer}'},force=True)
    return 0


if __name__=='__main__': sys.exit(main())
