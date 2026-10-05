"""Build verification receipts bind tested inputs to a retained packaged artifact."""
from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import hashlib
import importlib.metadata
import json
from pathlib import Path
import re
import shutil
import sys
import time
import uuid
import xml.etree.ElementTree as ET

VERIFICATION = {'maven': ['clean', 'verify'],
                'python': ['-B', '-m', 'unittest', 'discover', '-s', 'scripts', '-p', 'test_*.py']}


class BuildFailure(ValueError):
    def __init__(self, message, exit_code=1):
        super().__init__(message)
        self.exit_code = exit_code


def _sha256(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def _write_json(path, value):
    temporary = path.with_name(path.name+'.'+uuid.uuid4().hex+'.tmp')
    try:
        temporary.write_text(json.dumps(value, indent=2)+'\n', encoding='utf-8')
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def _git_revision(root):
    """Read local HEAD without launching an unsupervised child on a receipt hit."""
    try:
        git = root/'.git'
        if git.is_file():
            marker = git.read_text().strip()
            if not marker.startswith('gitdir: '):
                return None
            git = (root/marker[8:]).resolve()
        common = git
        if (git/'commondir').is_file():
            common = (git/(git/'commondir').read_text().strip()).resolve()
        value = (git/'HEAD').read_text().strip()
        for _ in range(8):
            if re.fullmatch(r'[0-9a-f]{40}|[0-9a-f]{64}', value):
                return value
            if not value.startswith('ref: refs/'):
                return None
            ref = value[5:]
            if '..' in Path(ref).parts:
                return None
            loose = next((directory/ref for directory in (git, common)
                          if (directory/ref).is_file()), None)
            if loose:
                value = loose.read_text().strip()
                continue
            for line in (common/'packed-refs').read_text().splitlines():
                fields = line.split()
                if len(fields) == 2 and fields[1] == ref:
                    value = fields[0]
                    break
            else:
                return None
        return None
    except (OSError, UnicodeError):
        return None


def _maven_distribution(root, env):
    wrapper=root/'.mvn/wrapper/maven-wrapper.properties'
    if not wrapper.is_file(): return None
    match=re.search(r'^distributionUrl\s*=\s*(https?://\S+)\s*$',wrapper.read_text(),re.M)
    if not match: return None
    url=match[1]
    mirror=env.get('MVNW_REPOURL')
    if mirror:
        pattern='/org/apache/maven/'
        url=mirror+pattern+(url.split(pattern,1)[1] if pattern in url else url)
    name=url.rsplit('/',1)[-1].rsplit('.',1)[0].removesuffix('-bin')
    code=0
    for char in url: code=(31*code+ord(char)) & 0xffffffff
    home=Path(env.get('MAVEN_USER_HOME') or root/'.cloudsim/maven').resolve()
    return home/'wrapper/dists'/name/f'{code:x}'


def _inputs(root, control, metadata, python):
    paths = {root/'pom.xml', root/'mvnw'}
    paths.update(root.glob('*.sh'))
    paths.update(path for path in (root/'packaging').rglob('*') if path.is_file())
    if (root/'mvnw.cmd').is_file(): paths.add(root/'mvnw.cmd')
    complete = True
    for directory in (root/'.mvn', root/'src'):
        entries = list(directory.rglob('*')) if directory.is_dir() else []
        complete = complete and any(entry.is_file() for entry in entries)
        complete = complete and not any(entry.is_symlink() and entry.is_dir() for entry in entries)
        paths.update(entry for entry in entries if entry.is_file() or entry.is_symlink())
    scripts = list((root/'scripts').glob('*.py'))
    complete = complete and bool(scripts)
    paths.update(scripts)
    requirements = sorted({*root.glob('requirements*.txt'), *(root/'scripts').glob('requirements*.txt')})
    paths.update(requirements)
    files = []
    for path in sorted(paths):
        control.check()
        if not path.is_file():
            complete = False
            files.append({'path': path.relative_to(root).as_posix(), 'sha256': None})
            continue
        files.append({'path': path.relative_to(root).as_posix(), 'sha256': _sha256(path)})
    packages = {}
    for path in requirements:
        for line in path.read_text(encoding='utf-8').splitlines():
            line = line.split('#', 1)[0].strip()
            if not line:
                continue
            match = re.fullmatch(r'([A-Za-z0-9_.-]+)==([^\s;]+)', line)
            if not match:
                complete = False
                continue
            name, _ = match.groups()
            try:
                packages[name] = importlib.metadata.version(name)
            except importlib.metadata.PackageNotFoundError:
                packages[name] = None
                complete = False
    inventory = sorted((distribution.metadata.get('Name', '').lower(), distribution.version)
                       for distribution in importlib.metadata.distributions())
    maven_home=Path(control.env.get('MAVEN_USER_HOME') or root/'.cloudsim/maven').resolve()
    settings = {Path.home()/'.m2/settings.xml', maven_home/'settings.xml'}
    settings.update(maven_home.glob('wrapper/dists/**/conf/settings.xml'))
    maven_settings = [{'path': str(path), 'sha256': _sha256(path) if path.is_file() else None}
                      for path in sorted(settings)]
    toolchain = getattr(control, 'toolchain', None)
    if not isinstance(toolchain, dict):
        toolchain = {}
    selected_jdk = {}
    for name in ('java', 'javac'):
        tool = toolchain.get(name)
        complete = complete and isinstance(tool, dict) and bool(tool.get('path')) and bool(tool.get('version'))
        if isinstance(tool, dict) and tool.get('path'):
            path = Path(tool['path']).resolve()
            digest = _sha256(path) if path.is_file() else None
            selected_jdk[name] = {'path': str(path), 'sha256': digest}
            complete = complete and bool(digest)
    java = selected_jdk.get('java')
    if java:
        home = Path(java['path']).parent.parent
        release = home/'release'
        digest = _sha256(release) if release.is_file() else None
        selected_jdk['release'] = {'path': str(release), 'sha256': digest}
        complete = complete and bool(digest)
        native = sorted(path for path in (home/'lib').rglob('*')
                        if path.suffix in ('.so', '.dylib', '.jnilib'))
        runtime = [home/'lib/modules', *native]
        selected_jdk['runtime_files'] = [
            {'path': str(path), 'sha256': _sha256(path) if path.is_file() else None}
            for path in runtime if path.exists()]
    complete = complete and bool(toolchain.get('release_sha256') or toolchain.get('release'))
    revision = _git_revision(root)
    complete = complete and bool(revision) and revision == metadata.get('source_revision')
    executable = str(Path(python).absolute())
    same_python = executable == str(Path(sys.executable).absolute())
    complete = complete and same_python
    inputs = {'files': files, 'git_revision': revision,
              'source_revision': metadata.get('source_revision'),
              'source_dirty': metadata.get('source_dirty'), 'java': metadata.get('java'),
              'toolchain': toolchain, 'selected_jdk': selected_jdk, 'maven_settings': maven_settings,
              'maven_distribution': str(_maven_distribution(root, control.env)),
              'python': {'executable': executable, 'version': sys.version if same_python else None,
                                              'prefix': sys.prefix if same_python else None,
                                              'base_prefix': sys.base_prefix if same_python else None,
                                              'launcher_version': sys.version,
                                              'implementation': sys.implementation.name,
                                              'packages': packages, 'installed_distributions': inventory}}
    # Round-trip detaches mutable toolchain dictionaries before the second snapshot.
    encoded = json.dumps(inputs, sort_keys=True, separators=(',', ':')).encode('utf-8')
    return hashlib.sha256(encoded).hexdigest(), json.loads(encoded), bool(complete)


@contextmanager
def build_lock(root, control):
    """Serialize cooperating verified builds, package-only builds, and target copies.

    Callers that touch target outside ensure_verified_build must hold this same
    lock. External Maven processes do not participate and must be managed by
    their owner; this lock never inspects or stops unrelated processes.
    """
    cache = Path(root).resolve()/'.cloudsim'
    cache.mkdir(parents=True, exist_ok=True)
    with (cache/'build-cache.lock').open('a+b') as lock:
        waiting = False
        while True:
            control.present('poll', control)
            control.check()
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except BlockingIOError:
                if not waiting:
                    control.render('build', {'detail': 'Waiting for another verified build to finish.'}, force=True)
                    waiting = True
                time.sleep(.1)
        try:
            control.check()
            yield
        finally:
            fcntl.flock(lock, fcntl.LOCK_UN)


def _cached_receipt(cache, fingerprint):
    try:
        receipt = json.loads((cache/'verified-build.json').read_text(encoding='utf-8'))
        if (receipt.get('schema') != 1 or receipt.get('tests') != 'PASS'
                or receipt.get('reusable') is not True
                or receipt.get('verification') != VERIFICATION
                or receipt.get('input_fingerprint') != fingerprint
                or not receipt.get('tested_at') or not isinstance(receipt.get('logs'), dict)):
            return None
        if datetime.fromisoformat(receipt['tested_at']).tzinfo is None:
            return None
        encoded = json.dumps(receipt['inputs'], sort_keys=True, separators=(',', ':')).encode('utf-8')
        if hashlib.sha256(encoded).hexdigest() != fingerprint:
            return None
        artifact = receipt['artifact']
        path = Path(artifact['path'])
        if not path.is_file() or path.is_symlink() or not path.resolve().is_relative_to((cache/'builds').resolve()):
            return None
        if _sha256(path) != artifact['sha256']:
            return None
        return receipt
    except (OSError, UnicodeError, ValueError, TypeError, KeyError, AttributeError):
        return None


def _packaged_jar(root):
    jars = list((root/'target').glob('lattora-*.jar'))
    if len(jars) != 1 or not jars[0].is_file() or jars[0].is_symlink():
        raise BuildFailure('Expected exactly one packaged target/lattora-*.jar')
    return jars[0]


def _java_reports(root):
    expected = []
    for path in (root/'src/test/java').rglob('*.java'):
        if not path.stem.endswith(('Test', 'IT')): continue
        source = path.read_text(encoding='utf-8')
        package = re.search(r'^package\s+([\w.]+)\s*;', source, re.M)
        expected.append((package[1]+'.' if package else '')+path.stem)
    reports = list((root/'target/surefire-reports').glob('TEST-*.xml')) + list((root/'target/failsafe-reports').glob('TEST-*.xml'))
    executed = set(); total = 0
    for path in reports:
        try:
            suite = ET.parse(path).getroot()
            counts = {name: int(suite.get(name, '0')) for name in ('tests', 'errors', 'failures', 'skipped')}
        except (OSError, ValueError, ET.ParseError) as error:
            raise BuildFailure('Invalid Java test report: '+str(path)) from error
        if counts['errors'] or counts['failures'] or counts['skipped'] or counts['tests'] <= 0:
            raise BuildFailure('Java verification report contains failed, skipped or missing tests: '+str(path))
        executed.add(suite.get('name')); total += counts['tests']
    missing = set(expected) - executed
    if missing: raise BuildFailure('Java verification did not execute required test suites: '+', '.join(sorted(missing)))
    return {'tests': total, 'suites': len(reports)}


def ensure_verified_build(root, control, metadata, outer, *, force=False, python=sys.executable):
    root, outer = Path(root).resolve(), Path(outer).resolve()
    cache = root/'.cloudsim'
    cache.mkdir(parents=True, exist_ok=True)
    metadata.update(status='build', tests='RUNNING')
    previous_env = control.env
    try:
        for name in ('MAVEN_ARGS', 'MAVEN_OPTS'):
            if control.env.get(name, '').strip():
                raise BuildFailure(name+' is set; clear it explicitly so full verified tests cannot be skipped or filtered.')
        control.env = {**previous_env, 'MAVEN_SKIP_RC': '1'}
        with build_lock(root, control):
            # Complete a first wrapper download before snapshotting the installed
            # Maven settings. This informational command never touches target.
            distribution = _maven_distribution(root, control.env)
            if distribution is not None and not (distribution/'bin/mvn').is_file():
                command = [str(root/'mvnw'), '-v']
                code = control.run(command, outer/'maven-version.log', 'build', cwd=root)
                if code: raise BuildFailure('Maven wrapper setup failed; see '+str(outer/'maven-version.log'), code)
            revision = _git_revision(root)
            if revision:
                supplied = metadata.get('source_revision')
                if supplied and supplied != revision:
                    raise BuildFailure('Source revision changed since provenance; retry before building.')
                metadata['source_revision'] = revision
            fingerprint, inputs, reusable = _inputs(root, control, metadata, python)
            receipt = _cached_receipt(cache, fingerprint) if reusable and not force else None
            if receipt:
                # Hash inputs again around reading the receipt and its artifact bytes.
                if _inputs(root, control, metadata, python)[0] != fingerprint:
                    raise BuildFailure('Build inputs changed while checking the verified receipt; retry.')
                _write_json(outer/'build-receipt.json', receipt)
                control.render('build', {'detail': 'Previous PASS reused from '+receipt['tested_at']+
                               '; this experiment will still validate independently.'}, force=True)
                metadata.update(tests='PASS', build='REUSED_VERIFIED', build_receipt=receipt,
                                java_verification=receipt.get('java_verification'))
                return Path(receipt['artifact']['path'])
            (cache/'verified-build.json').unlink(missing_ok=True)
            commands = [
                ([str(root/'mvnw'), '-B', '-Dmaven.repo.local='+str(cache/'maven/repository'), 'clean', 'verify'], 'build.log'),
                ([str(python), *VERIFICATION['python']], 'python-tests.log'),
            ]
            control.render('build', {'detail': 'Running Maven clean verify and the full Python test suite.'}, force=True)
            packaged = packaged_digest = None
            for command, name in commands:
                code = control.run(command, outer/name, 'build', cwd=root)
                if code:
                    raise BuildFailure(f'Build/check failed with exit {code}; see {outer/name}', code)
                if name == 'build.log':
                    metadata['java_verification'] = _java_reports(root)
                    packaged = _packaged_jar(root)
                    packaged_digest = _sha256(packaged)
            after, _, still_reusable = _inputs(root, control, metadata, python)
            if after != fingerprint:
                raise BuildFailure('Build inputs changed during full verification; no verified receipt was saved. Retry.')
            if _packaged_jar(root) != packaged or _sha256(packaged) != packaged_digest:
                raise BuildFailure('Packaged JAR changed after Maven verification; no receipt was saved. Retry.')
            # Each successful build owns a new path, including forced builds of identical inputs.
            artifact = cache/'builds'/fingerprint/uuid.uuid4().hex/'app.jar'
            artifact.parent.mkdir(parents=True)
            shutil.copyfile(packaged, artifact)
            artifact_digest = _sha256(artifact)
            if artifact_digest != packaged_digest or _sha256(packaged) != packaged_digest:
                artifact.unlink(missing_ok=True)
                raise BuildFailure('Packaged JAR changed while retaining the verified artifact; no receipt was saved. Retry.')
            if _inputs(root, control, metadata, python)[0] != fingerprint:
                artifact.unlink(missing_ok=True)
                raise BuildFailure('Build inputs changed while retaining the verified artifact; no receipt was saved. Retry.')
            receipt = {'schema': 1, 'tests': 'PASS', 'verification': VERIFICATION,
                       'input_fingerprint': fingerprint, 'inputs': inputs,
                       'toolchain': inputs['toolchain'], 'reusable': reusable and still_reusable,
                       'java_verification': metadata.get('java_verification'),
                       'tested_at': datetime.now(timezone.utc).isoformat(),
                       'artifact': {'path': str(artifact), 'sha256': artifact_digest, 'source': str(packaged)},
                       'logs': {name: str(outer/name) for _, name in commands}}
            if receipt['reusable']:
                _write_json(cache/'verified-build.json', receipt)
            _write_json(outer/'build-receipt.json', receipt)
            metadata.update(tests='PASS', build='VERIFIED', build_receipt=receipt)
            return artifact
    except Exception:
        metadata['tests'] = 'FAILED'
        raise
    finally:
        control.env = previous_env
