"""User-scoped, serialized installation management. No experiment data is removed."""
from contextlib import contextmanager, ExitStack
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import tarfile
import tempfile
import time
import urllib.error
import urllib.request
import uuid

from lattora_context import REPOSITORY, get_context, load_json, target_platform, verify_distribution, version_tuple

RELEASE_URL = 'https://github.com/'+REPOSITORY+'/releases/download/'
API_URL = 'https://api.github.com/repos/'+REPOSITORY+'/releases'


def install_root(env=None):
    env = os.environ if env is None else env
    return Path(env.get('HOME') or Path.home())/'.local/share/lattora'


def command_path(env=None):
    env = os.environ if env is None else env
    return Path(env.get('HOME') or Path.home())/'.local/bin/lattora'


def shim(base):
    return '#!/bin/sh\n# Managed by Lattora installer\nexec '+shlex.quote(str(base/'current/bin/lattora'))+' "$@"\n'


def _atomic_json(path, data):
    temporary = path.with_name(path.name+'.'+uuid.uuid4().hex+'.tmp')
    try:
        temporary.write_text(json.dumps(data, indent=2)+'\n', encoding='utf-8')
        os.replace(temporary, path)
    finally: temporary.unlink(missing_ok=True)


def _check_layout(base):
    if base.is_symlink() or (base/'versions').is_symlink() or (base/'activations').is_symlink():
        raise ValueError('Managed installation directories must not be symlinks.')
    current = base/'current'
    if current.exists() or current.is_symlink():
        if not current.is_symlink() or not current.resolve().is_relative_to((base/'versions').resolve()):
            raise ValueError('Unrelated current installation path; preserved.')


@contextmanager
def installation_lock(env=None, *, shared=False):
    base = install_root(env)
    base.parent.mkdir(parents=True, exist_ok=True)
    path = base.parent/'.lattora-install.lock'
    if path.is_symlink(): raise ValueError('Installation lock must not be a symlink.')
    with path.open('a+b') as stream:
        deadline = time.monotonic()+120
        while True:
            try:
                fcntl.flock(stream, (fcntl.LOCK_SH if shared else fcntl.LOCK_EX)|fcntl.LOCK_NB)
                break
            except BlockingIOError:
                if time.monotonic() >= deadline: raise ValueError('Another installation operation is busy; retry later.')
                time.sleep(.1)
        try:
            _check_layout(base)
            yield base
        finally: fcntl.flock(stream, fcntl.LOCK_UN)


@contextmanager
def session_lock(root, *, env=None):
    root = Path(root).resolve(); base = install_root(env)
    if root.parent != (base/'versions').resolve():
        yield
        return
    with ExitStack() as stack:
        with installation_lock(env, shared=True):
            directory = base/'sessions'; directory.mkdir(parents=True, exist_ok=True)
            path = directory/(root.name+'.lock')
            if path.is_symlink(): raise ValueError('Session lock must not be a symlink.')
            stream = stack.enter_context(path.open('a+b'))
            fcntl.flock(stream, fcntl.LOCK_SH)
        yield


def health_check(root):
    env = dict(os.environ)
    env.pop('PYTHONHOME',None); env.pop('PYTHONPATH',None)
    result = subprocess.run([str(Path(root)/'bin/lattora'),'_health'], env=env,
                            capture_output=True, text=True, timeout=60)
    if result.returncode: raise ValueError('Installation health check failed: '+(result.stderr or result.stdout)[-4000:])


def _check_command(base, env):
    command = command_path(env)
    if command.exists() or command.is_symlink():
        if command.is_symlink() or not command.is_file() or command.read_text() != shim(base):
            raise ValueError('An unrelated lattora command already exists; it was preserved: '+str(command))
    return command


def configure_path(env=None):
    env = os.environ if env is None else env
    directory = command_path(env).parent
    if str(directory) in env.get('PATH','').split(os.pathsep): return None
    home = Path(env.get('HOME') or Path.home())
    shell = Path(env.get('SHELL','/bin/bash')).name
    if shell == 'fish':
        config = Path(env.get('XDG_CONFIG_HOME') or home/'.config')/'fish/conf.d/lattora-path.fish'
        block = '# >>> lattora PATH >>>\nfish_add_path '+shlex.quote(str(directory))+'\n# <<< lattora PATH <<<\n'
    else:
        if shell not in ('bash','zsh'): return None
        config = (Path(env.get('ZDOTDIR') or home)/'.zshrc') if shell == 'zsh' else home/('.bash_profile' if os.sys.platform == 'darwin' else '.bashrc')
        quoted = shlex.quote(str(directory))
        block = '# >>> lattora PATH >>>\ncase ":$PATH:" in\n  *:'+quoted+':*) ;;\n  *) export PATH='+quoted+':"$PATH" ;;\nesac\n# <<< lattora PATH <<<\n'
    if config.is_symlink(): raise ValueError('Shell configuration is a symlink; configure PATH manually: '+str(config))
    existing = config.read_text(encoding='utf-8') if config.exists() else ''
    if '# >>> lattora PATH >>>' in existing: return config
    config.parent.mkdir(parents=True, exist_ok=True)
    config.write_text(existing+('\n' if existing and not existing.endswith('\n') else '')+'\n'+block, encoding='utf-8')
    return config


def _active_state(base):
    current = base/'current'
    if not current.exists(): return {}
    target = Path(os.readlink(current))
    # Version and rollback history belong to one immutable activation record.
    if (target.is_absolute() or len(target.parts) != 3 or target.parts[0] != 'activations'
            or not re.fullmatch(r'[a-f0-9]{32}',target.parts[1]) or target.parts[2] != 'release'):
        raise ValueError('Missing managed activation metadata; reinstall from a trusted archive.')
    record = base/target.parent/'state.json'
    if record.is_symlink(): raise ValueError('Invalid activation state path')
    state = load_json(record)
    version_tuple(state.get('current'))
    if state.get('previous') is not None: version_tuple(state['previous'])
    if state.get('schema') != 1 or state['current'] != current.resolve().name:
        raise ValueError('Activation metadata does not match active version')
    return state


def _activate(base, version, previous, env):
    command = _check_command(base, env)
    directory = base/'activations'; directory.mkdir(parents=True,exist_ok=True)
    activation = directory/uuid.uuid4().hex; activation.mkdir(mode=0o700)
    _atomic_json(activation/'state.json', {'schema':1,'current':version,'previous':previous})
    (activation/'release').symlink_to('../../versions/'+version)
    current = base/'current'
    original = os.readlink(current) if current.is_symlink() else None
    link = base/('.current-'+uuid.uuid4().hex)
    link.symlink_to('activations/'+activation.name+'/release')
    temporary = command.with_name('.lattora-'+uuid.uuid4().hex)
    new_command = not command.exists()
    try:
        if new_command:
            command.parent.mkdir(parents=True,exist_ok=True)
            temporary.write_text(shim(base),encoding='utf-8'); temporary.chmod(0o755)
        # One pointer commits version and its already-complete rollback history.
        os.replace(link,current)
        if new_command:
            try: os.replace(temporary,command)
            except BaseException:
                if original is None: current.unlink(missing_ok=True)
                else:
                    link.symlink_to(original); os.replace(link,current)
                raise
    finally:
        link.unlink(missing_ok=True); temporary.unlink(missing_ok=True)


def _install_locked(staged, base, env, health, modify_path):
    manifest = verify_distribution(staged)
    version = manifest['version']
    _check_command(base, env)
    versions = base/'versions'; versions.mkdir(parents=True, exist_ok=True)
    destination = versions/version
    old = (base/'current').resolve().name if (base/'current').exists() else None
    state = _active_state(base)
    if destination.exists():
        if destination.is_symlink() or verify_distribution(destination) != manifest:
            raise ValueError('Existing version differs or is damaged; preserved: '+str(destination))
        health(destination)
    else:
        with tempfile.TemporaryDirectory(prefix='.staging-',dir=versions) as temporary:
            payload = Path(temporary)/'bundle'
            shutil.copytree(staged,payload,symlinks=True)
            verify_distribution(payload); health(payload)
            payload.rename(destination)
    # PATH preparation happens before activation so failure does not switch versions.
    if modify_path: configure_path(env)
    _activate(base,version,old if old != version else state.get('previous'),env)
    return destination


def install_staged(staged, *, env=None, health=health_check, modify_path=True):
    env = dict(os.environ) if env is None else dict(env)
    with installation_lock(env) as base:
        return _install_locked(Path(staged),base,env,health,modify_path)


def extract_bundle(archive, destination):
    destination = Path(destination); destination.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive,'r:gz') as stream:
        stream.extractall(destination, filter='data')
    roots = list(destination.iterdir())
    if len(roots) != 1 or not roots[0].is_dir() or roots[0].is_symlink():
        raise ValueError('Unexpected distribution archive layout')
    return roots[0]


def fetch_json(url):
    request = urllib.request.Request(url, headers={'Accept':'application/vnd.github+json','User-Agent':'lattora'})
    try:
        with urllib.request.urlopen(request,timeout=30) as response:
            data = response.read(4*1024**2+1)
        if len(data) > 4*1024**2: raise ValueError('Release metadata exceeded size limit')
        return json.loads(data)
    except (OSError, ValueError) as error:
        raise ValueError('Cannot fetch release metadata: '+str(error)) from error


def discover_release(version=None, *, fetch=None):
    fetch = fetch or fetch_json
    if version: version_tuple(version.removeprefix('v')); version = version.removeprefix('v')
    releases = [fetch(API_URL+'/tags/v'+version)] if version else fetch(API_URL+'?per_page=100')
    if not isinstance(releases, list): raise ValueError('Invalid release listing')
    candidates = []
    for release in releases:
        if not isinstance(release,dict) or release.get('draft') or release.get('prerelease'): continue
        tag = release.get('tag_name','')
        try: parsed = version_tuple(tag.removeprefix('v'))
        except ValueError: continue
        if not any(a.get('name') == 'release.json' for a in release.get('assets',[]) if isinstance(a,dict)): continue
        candidates.append((parsed,tag))
    platform = target_platform()
    for _, tag in sorted(candidates,reverse=True):
        manifest = fetch(RELEASE_URL+tag+'/release.json')
        if (not isinstance(manifest,dict) or manifest.get('schema') != 1 or manifest.get('product') != 'lattora'
                or manifest.get('version') != tag.removeprefix('v')):
            raise ValueError('Unsupported release manifest')
        asset = manifest.get('assets',{}).get(platform)
        if asset is None: continue
        expected = 'lattora-'+manifest['version']+'-'+platform+'.tar.gz'
        if (not isinstance(asset,dict) or asset.get('name') != expected
                or not re.fullmatch(r'[0-9a-f]{64}',str(asset.get('sha256')))
                or type(asset.get('size')) is not int or not 0 < asset['size'] <= 1024**3):
            raise ValueError('Invalid release asset')
        return manifest, asset
    raise ValueError('No stable Lattora release is available for '+platform)


def download(url, destination, asset):
    digest = hashlib.sha256(); received = 0
    try:
        with urllib.request.urlopen(url,timeout=30) as response, Path(destination).open('xb') as output:
            while chunk := response.read(1024**2):
                received += len(chunk)
                if received > asset['size']: raise ValueError('Release download exceeded its declared size')
                digest.update(chunk); output.write(chunk)
        if received != asset['size'] or digest.hexdigest() != asset['sha256']:
            raise ValueError('Release download checksum/size mismatch')
    except OSError as error: raise ValueError('Release download failed: '+str(error)) from error


def update(version=None, *, check=False, env=None):
    env = dict(os.environ) if env is None else dict(env)
    manifest, asset = discover_release(version)
    with installation_lock(env) as base:
        if not (base/'current').exists(): raise ValueError('No managed installation; use the installer first.')
        current = load_json((base/'current').resolve()/'distribution.json')['version']
        print(f'Installed: {current}; available: {manifest["version"]}')
        if check or current == manifest['version'] or (version is None and version_tuple(manifest['version']) < version_tuple(current)): return current
        with tempfile.TemporaryDirectory(prefix='.download-',dir=base) as temporary:
            archive = Path(temporary)/'bundle.tar.gz'
            download(RELEASE_URL+'v'+manifest['version']+'/'+asset['name'],archive,asset)
            staged = extract_bundle(archive,Path(temporary)/'extracted')
            distribution = verify_distribution(staged)
            if distribution['version'] != manifest['version']: raise ValueError('Downloaded distribution version mismatch')
            destination = _install_locked(staged,base,env,health_check,False)
            print('Installed Lattora '+destination.name+'. New commands use this version; running sessions keep theirs.')
            return destination.name


def rollback(*, env=None, health=health_check):
    env = dict(os.environ) if env is None else dict(env)
    with installation_lock(env) as base:
        state = _active_state(base)
        previous = state.get('previous')
        if not previous: raise ValueError('No previously active version is available.')
        version_tuple(previous)
        destination = base/'versions'/previous
        verify_distribution(destination); health(destination)
        _activate(base,previous,(base/'current').resolve().name,env)
        return previous


def uninstall(*, env=None):
    env = dict(os.environ) if env is None else dict(env)
    with installation_lock(env) as base, ExitStack() as stack:
        command = _check_command(base,env)
        for lock in (base/'sessions').glob('*.lock'):
            if lock.is_symlink(): raise ValueError('Invalid session lock')
            stream = stack.enter_context(lock.open('a+b'))
            try: fcntl.flock(stream,fcntl.LOCK_EX|fcntl.LOCK_NB)
            except BlockingIOError: raise ValueError('Lattora is running; close its sessions before uninstalling.')
        command.unlink(missing_ok=True)
        (base/'current').unlink(missing_ok=True)
        if (base/'activations').exists(): shutil.rmtree(base/'activations')
        if (base/'versions').exists(): shutil.rmtree(base/'versions')
        print('Lattora uninstalled. Experiments, configuration and PATH setup were retained.')
