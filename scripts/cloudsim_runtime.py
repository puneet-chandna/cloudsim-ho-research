"""Explicit project-local setup; no global installs or shell configuration changes."""
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import platform
import re
import shutil
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
    if not chosen:
        for candidate in sorted(Path('/usr/lib/jvm').glob('*21*/bin/javac')):
            try: home = (verify or probe_jdk)(candidate.parent.parent)
            except (OSError, ValueError, subprocess.SubprocessError): continue
            chosen = str(home); break
    if not chosen:
        java = shutil.which('java', path=env.get('PATH', os.defpath))
        if java:
            home = Path(java).resolve().parent.parent
            chosen = str(home)
    if chosen:
        home = Path(chosen).expanduser().resolve()
        child.update(JAVA_HOME=str(home), PATH=str(home/'bin')+os.pathsep+env.get('PATH', os.defpath))
    return child


def owned_child_identity(pid, parent):
    """Bind cleanup to a Linux process birth time, rather than a reusable PID."""
    try:
        fields = Path(f'/proc/{pid}/stat').read_text().rsplit(')',1)[1].split()
        if int(fields[1]) == parent and int(fields[2]) == pid:
            return {'pid':pid, 'parent':parent, 'start_time':fields[19]}
    except (OSError, ValueError, TypeError, IndexError): pass
    return None


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
        probe_jdk(roots[0])
        roots[0].rename(destination)
    return destination


def install_jdk(root=ROOT, report=print):
    target = root/'.cloudsim/jdk'
    if target.exists():
        probe_jdk(target); report('Local JDK 21 is already installed.'); return target
    if sys.platform != 'linux' or platform.machine() not in ('x86_64', 'amd64'):
        raise ValueError('Automatic JDK setup supports Linux x86-64. Select an installed JDK 21 on this platform.')
    target.parent.mkdir(parents=True, exist_ok=True)
    report('Downloading Eclipse Temurin JDK 21 (198 MiB).')
    with tempfile.TemporaryDirectory(prefix='jdk-download-', dir=target.parent) as temporary:
        archive = Path(temporary)/'jdk.tar.gz'
        request = urllib.request.Request(JDK_URL, headers={'User-Agent': 'CloudSim-Launcher'})
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
        extract_jdk(archive, JDK_SHA256, target)
    (target.parent/'jdk-install.json').write_text(json.dumps({'version':JDK_VERSION, 'url':JDK_URL,
        'sha256':JDK_SHA256, 'java_home':str(target)}, indent=2)+'\n')
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
