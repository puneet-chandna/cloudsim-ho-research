"""Explicit project-local setup; no global installs or shell configuration changes."""
import hashlib
import ctypes
import errno
from functools import lru_cache
import importlib.metadata
import json
import os
from pathlib import Path
import platform
import re
import shutil
import signal
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
import venv

ROOT = Path(__file__).resolve().parents[1]
JDK_VERSION = '21.0.12.1+1'
JDK_URL = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz'
JDK_SHA256 = 'ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94'
MAC_JDK_SHA256 = {
    'aarch64': '3623232f33a9c3baadf304480b2535f9a3cba8a58d42ecbb438ba267315d9998',
}


def jdk_download():
    machine = platform.machine().lower()
    if sys.platform == 'linux' and machine in ('x86_64', 'amd64'):
        return JDK_URL, JDK_SHA256
    if sys.platform == 'linux' and machine in ('arm64', 'aarch64'):
        return JDK_URL.replace('x64_linux', 'aarch64_linux'), '23e37e026f12f3e706f18938ff611db3032d075b09d0879a25d06718c773e223'
    if sys.platform == 'darwin' and machine in ('arm64', 'aarch64'):
        arch = 'aarch64'
        return JDK_URL.replace('x64_linux', arch+'_mac'), MAC_JDK_SHA256[arch]
    raise ValueError('Automatic JDK setup supports Linux x86-64/ARM64 and macOS Apple Silicon. Select an installed JDK 21 on this platform.')


def mac_command(command):
    result = subprocess.run(command, capture_output=True, text=True, timeout=10,
                            env={**os.environ, 'LC_ALL':'C'})
    if result.returncode: raise ValueError(f'macOS probe failed: {command[0]}')
    return result.stdout.strip()


def mac_physical_memory():
    try:
        total = int(mac_command(['/usr/sbin/sysctl', '-n', 'hw.memsize']))
        if total <= 0: raise ValueError('invalid hw.memsize')
        return total
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        raise ValueError(f'Cannot verify macOS physical memory: {error}') from error


def mac_usable_memory():
    """Free plus reclaimable inactive/speculative pages; excludes swap/compressor.

    This is a launch-time estimate, not an OS reservation. require_memory keeps
    the same minimum and heap headroom used on Linux.
    """
    try:
        total = mac_physical_memory()
        text = mac_command(['/usr/bin/vm_stat'])
        match = re.search(r'page size of (\d+) bytes', text)
        if not match or int(match[1]) not in (4096, 16384): raise ValueError('invalid page size')
        counts = []
        for name in ('free', 'inactive', 'speculative'):
            values = re.findall(r'^Pages '+name+r':\s+(\d+)\.?\s*$', text, re.M)
            if len(values) != 1: raise ValueError(f'missing or invalid Pages {name}')
            counts.append(int(values[0]))
        available = sum(counts)*int(match[1])
        if available > total: raise ValueError('available pages exceed physical memory')
        return available
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        raise ValueError(f'Cannot verify macOS available memory: {error}') from error


# Native layouts from Apple's bsd/sys/proc_info.h (PROC_PIDTASKALLINFO = 2).
# libproc provides precise birth times; ps lstart only has one-second precision.
class _MacBsdInfo(ctypes.Structure):
    _fields_ = [(name, ctypes.c_uint32) for name in (
        'flags', 'status', 'xstatus', 'pid', 'ppid', 'uid', 'gid', 'ruid', 'rgid', 'svuid', 'svgid', 'reserved')]+[
        ('comm', ctypes.c_char*16), ('name', ctypes.c_char*32),
        ('nfiles', ctypes.c_uint32), ('pgid', ctypes.c_uint32), ('jobc', ctypes.c_uint32),
        ('tdev', ctypes.c_uint32), ('tpgid', ctypes.c_uint32), ('nice', ctypes.c_int32),
        ('start_sec', ctypes.c_uint64), ('start_usec', ctypes.c_uint64)]


class _MacTaskInfo(ctypes.Structure):
    _fields_ = [(name, ctypes.c_uint64) for name in (
        'virtual_size', 'resident_size', 'total_user', 'total_system', 'threads_user', 'threads_system')]+[
        ('counters', ctypes.c_int32*12)]


class _MacTaskAllInfo(ctypes.Structure):
    _fields_ = [('bsd', _MacBsdInfo), ('task', _MacTaskInfo)]


@lru_cache(maxsize=1)
def _mac_libproc():
    library = ctypes.CDLL('/usr/lib/libproc.dylib', use_errno=True)
    library.proc_pidinfo.argtypes = [ctypes.c_int, ctypes.c_int, ctypes.c_uint64, ctypes.c_void_p, ctypes.c_int]
    library.proc_pidinfo.restype = ctypes.c_int
    library.proc_listpids.argtypes = [ctypes.c_uint32, ctypes.c_uint32, ctypes.c_void_p, ctypes.c_int]
    library.proc_listpids.restype = ctypes.c_int
    return library


def mac_process_info(pid):
    try:
        info = _MacTaskAllInfo()
        if _mac_libproc().proc_pidinfo(pid, 2, 0, ctypes.byref(info), ctypes.sizeof(info)) != ctypes.sizeof(info):
            return None
        if info.bsd.pid != pid: return None
        return {'pid':pid, 'parent':info.bsd.ppid, 'group':info.bsd.pgid, 'session':os.getsid(pid),
                'start_time':[info.bsd.start_sec, info.bsd.start_usec], 'rss_bytes':info.task.resident_size}
    except (OSError, ValueError): return None


def mac_group_members(group):
    library = _mac_libproc()
    def query(buffer, size):
        ctypes.set_errno(0)
        count = library.proc_listpids(2, group, buffer, size)
        error = ctypes.get_errno()
        if count < 0 or (count == 0 and error):
            raise OSError(error or errno.EIO, 'Cannot enumerate macOS process group')
        return count
    # PROC_PGRP_ONLY = 2; grow if the group changed during the size probe.
    size = max(256, query(None, 0)+64)
    while size <= 1024*1024:
        buffer = (ctypes.c_int*(size//ctypes.sizeof(ctypes.c_int)))()
        count = query(buffer, ctypes.sizeof(buffer))
        if count == 0: return []
        if count < ctypes.sizeof(buffer): return [pid for pid in buffer[:count//4] if pid > 0]
        size *= 2
    raise OSError(errno.EOVERFLOW, 'macOS process group exceeds probe limit')


def mac_group_exited(group):
    """Confirm an empty/zombie-only group; unknown native state fails closed."""
    library = _mac_libproc()
    for pid in mac_group_members(group):
        info = _MacBsdInfo()
        ctypes.set_errno(0)
        # PROC_PIDTBSDINFO = 3, argument 1 includes zombies (SZOMB = 5).
        count = library.proc_pidinfo(pid, 3, 1, ctypes.byref(info), ctypes.sizeof(info))
        if count != ctypes.sizeof(info):
            error = ctypes.get_errno()
            if error == errno.ESRCH: continue  # Member exited after enumeration.
            raise OSError(error or errno.EIO, 'Cannot verify macOS process group member')
        if info.pid != pid: raise OSError(errno.EIO, 'macOS process identity mismatch')
        if info.pgid == group and info.status != 5: return False
    return True


def settings(root=ROOT):
    path = root/'.cloudsim/settings.json'
    if not path.exists(): return {}
    data = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(data, dict): raise ValueError(f'Invalid setup settings: {path}')
    return data


def save_java_home(home, root=ROOT):
    data = settings(root)
    data['java_home'] = str(Path(home).expanduser().resolve())
    path = root/'.cloudsim/settings.json'; path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(data, indent=2)+'\n', encoding='utf-8')
    temporary.replace(path)


def java_environment(env, root=ROOT, verify=None):
    child = dict(env)
    child.setdefault('MAVEN_USER_HOME', str(root/'.cloudsim/maven'))
    chosen = env.get('JAVA_HOME') or settings(root).get('java_home')
    if not chosen and (root/'.cloudsim/jdk/bin/javac').is_file():
        chosen = str(root/'.cloudsim/jdk')
    if not chosen and sys.platform == 'darwin':
        try:
            candidate = Path(mac_command(['/usr/libexec/java_home', '-v', '21']))
            chosen = str((verify or probe_jdk)(candidate))
        except (OSError, ValueError, subprocess.SubprocessError): pass
    if not chosen and sys.platform != 'darwin':
        for candidate in sorted(Path('/usr/lib/jvm').glob('*21*/bin/javac')):
            try: home = (verify or probe_jdk)(candidate.parent.parent)
            except (OSError, ValueError, subprocess.SubprocessError): continue
            chosen = str(home); break
    if not chosen:
        java = shutil.which('java', path=env.get('PATH', os.defpath))
        if java and not (sys.platform == 'darwin' and java == '/usr/bin/java'):
            home = Path(java).resolve().parent.parent
            chosen = str(home)
    if chosen:
        home = Path(chosen).expanduser().resolve()
        child.update(JAVA_HOME=str(home), PATH=str(home/'bin')+os.pathsep+env.get('PATH', os.defpath))
    return child


def owned_child_identity(pid, parent):
    """Bind cleanup to a process birth time, rather than a reusable PID."""
    if sys.platform == 'darwin':
        info = mac_process_info(pid)
        if info and info['parent']==parent and info['group']==pid and info['session']==pid:
            return {'pid':pid, 'parent':parent, 'start_time':info['start_time']}
        return None
    try:
        fields = Path(f'/proc/{pid}/stat').read_text().rsplit(')',1)[1].split()
        if int(fields[1]) == parent and int(fields[2]) == pid:
            return {'pid':pid, 'parent':parent, 'start_time':fields[19]}
    except (OSError, ValueError, TypeError, IndexError): pass
    return None


def stop_owned_child(identity):
    """Kill only the recorded session/group, even after its leader is reaped."""
    if not identity: return
    try:
        pid = identity['pid']
        if sys.platform == 'darwin':
            leader = mac_process_info(pid)
            if leader and leader['start_time'] != identity['start_time']: return
            for member in mac_group_members(pid):
                info = mac_process_info(member)
                if (info and info['group']==pid and info['session']==pid
                        and tuple(info['start_time']) >= tuple(identity['start_time'])):
                    os.killpg(pid, signal.SIGKILL); break
        else:
            leader = Path(f'/proc/{pid}/stat')
            if leader.exists():
                fields = leader.read_text().rsplit(')',1)[1].split()
                if fields[19] != identity['start_time']: return
            for path in Path('/proc').glob('[0-9]*/stat'):
                try: fields = path.read_text().rsplit(')',1)[1].split()
                except OSError: continue
                if int(fields[2])==pid and int(fields[3])==pid and int(fields[19])>=int(identity['start_time']):
                    os.killpg(pid, signal.SIGKILL); break
    except (OSError, ValueError, TypeError, IndexError, KeyError): pass


def probe_jdk(home):
    home = Path(home).expanduser().resolve()
    for name in ('java', 'javac'):
        tool = home/'bin'/name
        if not tool.is_file(): raise ValueError(f'Full JDK 21 required: {tool} is missing.')
        result = subprocess.run([str(tool), '-version'], capture_output=True, text=True, timeout=20,
                                env={k:v for k,v in os.environ.items() if k not in ('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')})
        version = result.stdout+result.stderr
        if result.returncode or not re.search(r'(?:version\s+"?|javac\s+)21(?:[.\s"+-]|$)', version):
            raise ValueError(f'{tool} must be version 21.')
    return home


def extract_jdk(archive, checksum, destination):
    destination = Path(destination)
    if destination.exists(): raise FileExistsError(f'Existing runtime preserved: {destination}')
    with Path(archive).open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    if digest != checksum: raise ValueError('JDK download checksum mismatch; nothing was installed.')
    if not hasattr(tarfile, 'data_filter'):
        raise ValueError('Secure archive extraction requires an updated Python 3.11+ installation.')
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='jdk-extract-', dir=destination.parent) as temporary:
        payload = Path(temporary)
        with tarfile.open(archive, 'r:gz') as source:
            source.extractall(payload, filter='data')
        roots = list(payload.iterdir())
        if len(roots) != 1 or not roots[0].is_dir(): raise ValueError('Unexpected JDK archive layout.')
        home = roots[0]/'Contents/Home' if (roots[0]/'Contents/Home').is_dir() else roots[0]
        probe_jdk(home)
        home.rename(destination)
    return destination


def install_jdk(root=ROOT, report=print):
    target = root/'.cloudsim/jdk'
    if target.exists():
        probe_jdk(target); report('Local JDK 21 is already installed.'); return target
    url, checksum = jdk_download()
    target.parent.mkdir(parents=True, exist_ok=True)
    report('Downloading Eclipse Temurin JDK 21 for '+platform.system()+' '+platform.machine()+'.')
    with tempfile.TemporaryDirectory(prefix='jdk-download-', dir=target.parent) as temporary:
        archive = Path(temporary)/'jdk.tar.gz'
        request = urllib.request.Request(url, headers={'User-Agent': 'CloudSim-Launcher'})
        with urllib.request.urlopen(request, timeout=30) as response, archive.open('wb') as output:
            received = 0; last = -1
            total = int(response.headers.get('Content-Length', 0))
            while chunk := response.read(1024*1024):
                received += len(chunk)
                if received > 512*1024**2: raise ValueError('JDK download exceeded the expected size limit.')
                output.write(chunk)
                percent = received*100//total if total else received//(10*1024**2)*5
                if percent//5 != last:
                    report(f'Downloading JDK: {received//1024**2} MiB'+(f' / {total//1024**2} MiB' if total else ''))
                    last = percent//5
        report('Verifying checksum and extracting JDK.')
        extract_jdk(archive, checksum, target)
    (target.parent/'jdk-install.json').write_text(json.dumps({'version':JDK_VERSION, 'url':url,
        'sha256':checksum, 'java_home':str(target)}, indent=2)+'\n')
    report('Local JDK 21 is ready. System Java settings were preserved.')
    return target


def install_ui(root=ROOT, report=print):
    python = root/'.cloudsim/venv/bin/python'
    requirements = root/'scripts/requirements-tui.txt'
    if python.exists():
        result = subprocess.run([str(python), '-c', 'import textual; print(textual.__version__)'],
                                capture_output=True, text=True)
        if result.returncode == 0 and result.stdout.strip() == requirements.read_text().strip().split('==')[1]:
            report('Terminal UI is already installed.'); return python
    report('Preparing the project-local terminal UI.')
    venv.EnvBuilder(with_pip=True).create(python.parent.parent)
    result = subprocess.run([str(python), '-m', 'pip', '--disable-pip-version-check', 'install',
                            '-r', str(requirements)], capture_output=True, text=True)
    log = root/'.cloudsim/ui-install.log'; log.write_text(result.stdout+result.stderr)
    if result.returncode: raise ValueError(f'Terminal UI installation failed. Check your network and retry --setup. Log: {log}')
    report('Terminal UI is ready.'); return python


def setup(report=print):
    try:
        install_ui(report=report)
        install_jdk(report=report)
        return 0
    except (OSError, ValueError, subprocess.SubprocessError, tarfile.TarError) as error:
        report(f'Setup failed: {error}. Retry ./cloudsim.sh --setup, or select an installed JDK 21 in Setup.')
        return 1


def ensure_ui():
    from lattora_context import get_context
    if get_context(ROOT).bundled: return True
    pinned = (ROOT/'scripts/requirements-tui.txt').read_text().strip().split('==')[1]
    try:
        if importlib.metadata.version('textual')==pinned: return True
    except importlib.metadata.PackageNotFoundError: pass
    print('CloudSim needs its terminal UI package. It will be installed only in .cloudsim/venv.')
    try: consent = input('Set up the terminal UI now? [Y/n] ').strip().lower()
    except (EOFError, KeyboardInterrupt): return False
    if consent not in ('', 'y', 'yes'): return False
    try: python = install_ui()
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        print(f'Setup failed: {error}', file=sys.stderr); return False
    os.execv(str(python), [str(python), '-B', str(ROOT/'scripts/cloudsim.py')])
    return False
