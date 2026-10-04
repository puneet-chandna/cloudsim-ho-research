"""Execution paths and integrity for source checkouts and immutable distributions."""
from dataclasses import dataclass, field
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = 'puneet-chandna/cloudsim-ho-research'
VERSION_PATTERN = r'(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)'


def version_tuple(value):
    if not isinstance(value, str) or not re.fullmatch(VERSION_PATTERN, value):
        raise ValueError('Invalid stable version: '+str(value))
    return tuple(int(part) for part in value.split('.'))


def target_platform():
    machine = platform.machine().lower()
    arch = {'amd64':'x86_64','x86_64':'x86_64','arm64':'arm64','aarch64':'arm64'}.get(machine)
    if sys.platform == 'linux' and arch in ('x86_64','arm64'):
        if platform.libc_ver()[0] == 'musl' or Path('/etc/alpine-release').exists():
            raise ValueError('Unsupported Linux libc: Lattora requires glibc.')
        return 'linux-'+arch
    if sys.platform == 'darwin' and arch == 'arm64': return 'macos-arm64'
    raise ValueError(f'Unsupported platform: {sys.platform}/{machine}. Use Linux x86-64/ARM64 or Apple Silicon macOS.')


def sha256(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def unique_json(pairs):
    result = {}
    for key, value in pairs:
        if key in result: raise ValueError('Duplicate manifest key: '+key)
        result[key] = value
    return result


def load_json(path):
    try:
        if Path(path).stat().st_size > 16*1024**2: raise ValueError('Oversized manifest')
        value = json.loads(Path(path).read_text(encoding='utf-8'), object_pairs_hook=unique_json)
        if not isinstance(value, dict): raise ValueError('Expected a manifest object')
        return value
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ValueError(f'Cannot read manifest {path}: {error}') from error


def verify_distribution(root, *, check_platform=True):
    root = Path(root).resolve()
    manifest = load_json(root/'distribution.json')
    if manifest.get('schema') != 1 or manifest.get('product') != 'lattora':
        raise ValueError('Unsupported Lattora distribution manifest')
    version_tuple(manifest.get('version'))
    if not re.fullmatch(r'[0-9a-f]{40}', str(manifest.get('source_revision'))):
        raise ValueError('Invalid distribution source revision')
    if type(manifest.get('source_dirty')) is not bool: raise ValueError('Missing source cleanliness')
    verification = manifest.get('verification', {})
    if verification.get('java') != 'PASS' or verification.get('python') != 'PASS':
        raise ValueError('Distribution has no passing release verification')
    if check_platform and manifest.get('platform') != target_platform():
        raise ValueError('Distribution platform does not match this machine')
    files = manifest.get('files')
    if not isinstance(files, dict) or not files: raise ValueError('Missing distribution file inventory')
    for name, record in files.items():
        path = Path(name)
        if path.is_absolute() or '..' in path.parts or not path.parts or name == 'distribution.json':
            raise ValueError('Invalid distribution path: '+name)
        item = root/path
        if not item.resolve().is_relative_to(root): raise ValueError('Distribution path escapes installation: '+name)
        if not isinstance(record, dict): raise ValueError('Invalid inventory record: '+name)
        if 'symlink' in record:
            if not item.is_symlink() or os.readlink(item) != record['symlink'] or not item.exists():
                raise ValueError('Distribution link integrity failed: '+name)
        elif (not item.is_file() or item.is_symlink() or item.stat().st_size != record.get('size')
              or sha256(item) != record.get('sha256')):
            raise ValueError('Distribution integrity failed: '+name)
    actual = {item.relative_to(root).as_posix() for item in root.rglob('*')
              if (item.is_file() or item.is_symlink()) and item != root/'distribution.json'}
    if actual != set(files): raise ValueError('Distribution file inventory is incomplete or has unexpected files')
    return manifest


def jar_provenance(jar):
    try:
        with zipfile.ZipFile(jar) as archive:
            text = archive.read('META-INF/MANIFEST.MF').decode('utf-8').replace('\r\n', '\n').replace('\n ', '')
    except (OSError, zipfile.BadZipFile, KeyError, UnicodeError) as error:
        raise ValueError('Cannot read engine provenance: '+str(error)) from error
    return dict(line.split(': ', 1) for line in text.splitlines() if ': ' in line)


@dataclass
class ExecutionContext:
    root: Path = ROOT
    bundled: bool = False
    env: dict = field(default_factory=lambda: dict(os.environ))

    @property
    def data(self):
        home = Path(self.env.get('HOME') or Path.home())
        if not self.bundled: return self.root/'.cloudsim'
        if sys.platform == 'darwin': return home/'Library/Application Support/lattora'
        return Path(self.env.get('XDG_DATA_HOME') or home/'.local/share')/'lattora'

    @property
    def config(self):
        if not self.bundled: return self.root/'.cloudsim/settings.json'
        if sys.platform == 'darwin': return self.data/'settings.json'
        home = Path(self.env.get('HOME') or Path.home())
        return Path(self.env.get('XDG_CONFIG_HOME') or home/'.config')/'lattora/settings.json'

    @property
    def cache(self):
        if not self.bundled: return self.root/'.cloudsim'
        home = Path(self.env.get('HOME') or Path.home())
        if sys.platform == 'darwin': return home/'Library/Caches/lattora'
        return Path(self.env.get('XDG_CACHE_HOME') or home/'.cache')/'lattora'

    @property
    def results(self): return self.data/'results' if self.bundled else self.root/'results'
    @property
    def java(self): return self.root/'runtime/java/bin/java'
    @property
    def protocol(self): return self.root/'src/main/resources/protocol.properties'
    @property
    def version(self):
        if self.bundled: return load_json(self.root/'distribution.json')['version']
        return ET.parse(self.root/'pom.xml').findtext('{http://maven.apache.org/POM/4.0.0}version')

    def manifest(self): return verify_distribution(self.root)

    def release_metadata(self):
        manifest = self.manifest()
        return {'source_revision':manifest['source_revision'], 'source_dirty':manifest['source_dirty'],
                'source_status':'Diagnostic local release; source verification is not publication acceptance' if manifest.get('diagnostic') else 'Release built from dirty source' if manifest['source_dirty'] else '',
                'distribution_diagnostic':bool(manifest.get('diagnostic')),
                'distribution_version':manifest['version'], 'distribution_platform':manifest['platform'],
                'distribution_manifest_sha256':sha256(self.root/'distribution.json'),
                'release_verification':manifest['verification'], 'tests':'NOT_RUN', 'build':'RELEASE_VERIFIED'}


def get_context(root=None):
    return ExecutionContext(Path(root or ROOT).resolve(), os.environ.get('LATTORA_BUNDLED') == '1')
