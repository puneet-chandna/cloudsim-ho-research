#!/usr/bin/env python3
"""Assemble checksum-pinned standalone bundles from a fully verified source build."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
import zipfile

from cloudsim_build import VERIFICATION, _git_revision, _inputs
from lattora_context import ROOT, ExecutionContext, jar_provenance, load_json, sha256, version_tuple

LAUNCHER = '''#!/bin/sh
set -eu
release_root="$(CDPATH= cd -P -- "$(dirname -- "$0")/.." && pwd)"
unset PYTHONPATH PYTHONHOME
export PYTHONNOUSERSITE=1 PYTHONDONTWRITEBYTECODE=1 LATTORA_BUNDLED=1
exec "$release_root/runtime/python/bin/python3" -B "$release_root/scripts/lattora.py" "$@"
'''


def runtime_lock(): return load_json(ROOT/'packaging/runtimes.json')


def validate_receipt(root, receipt, *, allow_dirty=False):
    if receipt.get('tests') != 'PASS' or receipt.get('verification') != VERIFICATION:
        raise ValueError('Packaging requires full Java and Python verification, not a package-only build.')
    inputs = receipt.get('inputs',{})
    revision = _git_revision(root)
    if not revision or inputs.get('source_revision') != revision:
        raise ValueError('Verified source revision no longer matches checkout')
    if inputs.get('source_dirty') and not allow_dirty: raise ValueError('Release source is dirty; commit/review it first. --allow-dirty makes diagnostic local bundles only.')
    files = inputs.get('files')
    if not isinstance(files,list) or not files: raise ValueError('Missing verified source inventory')
    for record in files:
        name = record['path']; path = Path(name)
        if path.is_absolute() or '..' in path.parts: raise ValueError('Invalid receipt source path')
        item = root/path
        if not item.is_file() or sha256(item) != record.get('sha256'):
            raise ValueError('Verified source changed: '+name+'; run source verification again.')
    control = type('ReceiptCheck', (), {'env':dict(os.environ),'check':lambda self:None})()
    _, current_inputs, _ = _inputs(root,control,inputs,sys.executable)
    if current_inputs['files'] != files: raise ValueError('Verified source inventory changed; run source verification again.')
    artifact = receipt.get('artifact',{})
    jar = Path(artifact.get('path',''))
    if not jar.is_file() or sha256(jar) != artifact.get('sha256'): raise ValueError('Verified engine hash mismatch')
    provenance = jar_provenance(jar)
    version = ExecutionContext(root).version
    if provenance.get('Implementation-Version') != version or provenance.get('Git-Revision') != revision:
        raise ValueError('Verified engine provenance mismatch')
    if provenance.get('Git-Dirty') not in ('true','false'): raise ValueError('Missing engine source cleanliness')
    if provenance['Git-Dirty'] == 'true' and not allow_dirty: raise ValueError('Dirty engine cannot be published')
    return jar, revision, provenance['Git-Dirty'] == 'true'


def inventory(root):
    records = {}
    for path in sorted(root.rglob('*')):
        if not path.resolve().is_relative_to(root.resolve()): raise ValueError('Bundle link escapes root: '+str(path))
        name = path.relative_to(root).as_posix()
        if path.is_symlink(): records[name] = {'symlink':os.readlink(path)}
        elif path.is_file(): records[name] = {'sha256':sha256(path),'size':path.stat().st_size}
    return records


def cached_runtime(cache, target, kind, record):
    cache.mkdir(parents=True,exist_ok=True)
    archive = cache/(kind+'-'+target+'.tar.gz')
    if archive.exists():
        if sha256(archive) != record['sha256']: raise ValueError('Cached runtime checksum mismatch: '+str(archive))
        return archive
    temporary = archive.with_suffix('.download')
    try:
        with urllib.request.urlopen(record['url'],timeout=60) as response, temporary.open('wb') as output:
            received = 0
            while chunk := response.read(1024**2):
                received += len(chunk)
                if received > 512*1024**2: raise ValueError('Runtime download too large')
                output.write(chunk)
        if sha256(temporary) != record['sha256']: raise ValueError('Runtime download checksum mismatch')
        temporary.replace(archive)
        return archive
    finally: temporary.unlink(missing_ok=True)


def unpack_runtime(archive, destination, kind):
    with tempfile.TemporaryDirectory(dir=destination.parent,prefix='.extract-') as temporary:
        temporary = Path(temporary)
        with tarfile.open(archive,'r:gz') as stream: stream.extractall(temporary,filter='data')
        roots = list(temporary.iterdir())
        if len(roots) != 1 or not roots[0].is_dir(): raise ValueError('Unexpected runtime layout')
        source = roots[0]
        if kind == 'java' and (source/'Contents/Home').is_dir(): source = source/'Contents/Home'
        # Keep notices outside macOS Contents/Home when normalizing the runtime.
        if kind == 'java' and source != roots[0]:
            notices = destination.parent.parent/'licenses/temurin'; notices.mkdir(parents=True,exist_ok=True)
            for path in roots[0].rglob('*'):
                if path.is_file() and path.name.lower() in ('license','notice','assembly_exception'):
                    shutil.copyfile(path,notices/path.name)
        source.rename(destination)



def python_notices(archive, destination):
    destination = Path(destination); destination.mkdir(parents=True,exist_ok=True)
    with tarfile.open(archive,'r:*') as stream:
        for member in stream:
            if member.name != 'python/PYTHON.json' and not member.name.startswith('python/licenses/'):
                continue
            if member.isdir(): continue
            if not member.isfile() or member.size > 4*1024**2:
                raise ValueError('Invalid Python license archive member')
            name = Path(member.name).name
            if name in ('.','..'): raise ValueError('Invalid Python license name')
            (destination/name).write_bytes(stream.extractfile(member).read())
    if not (destination/'PYTHON.json').is_file() or not list(destination.glob('LICENSE*.txt')):
        raise ValueError('Python native licenses or component metadata missing')


def release_sources(output, version):
    records = load_json(ROOT/'packaging/sources.json')
    archive = Path(output)/f'lattora-{version}-sources.tar.gz'
    with tempfile.TemporaryDirectory(prefix='.sources-',dir=output) as temporary:
        bundle = Path(temporary)/f'lattora-{version}-sources'; bundle.mkdir()
        subprocess.run(['git','-C',str(ROOT),'archive','--format=tar','HEAD',
                        '-o',str(bundle/'lattora-source.tar')],check=True)
        shutil.copyfile(ROOT/'SOURCES.md',bundle/'SOURCES.md')
        vendor = bundle/'upstream'; vendor.mkdir()
        for name, record in records.items():
            if Path(name).name != name: raise ValueError('Invalid source archive name')
            source = cached_runtime(ROOT/'.cloudsim/lattora-sources','upstream',name,record)
            shutil.copyfile(source,vendor/name)
        manifest = {'version':version,'source_revision':_git_revision(ROOT),
                    'upstream':records,'files':inventory(bundle)}
        (bundle/'sources.json').write_text(json.dumps(manifest,indent=2)+'\n')
        with tarfile.open(archive,'w:gz') as stream: stream.add(bundle,arcname=bundle.name)
    return archive


def assemble(target, receipt_path, output, cache, *, allow_dirty=False, wheelhouse=None):
    root = ROOT; output = Path(output).resolve(); cache = Path(cache).resolve()
    lock = runtime_lock()
    if target not in lock['targets']: raise ValueError('Unsupported packaging target')
    receipt = load_json(receipt_path)
    jar, revision, dirty = validate_receipt(root,receipt,allow_dirty=allow_dirty)
    version = ExecutionContext(root).version; version_tuple(version)
    output.mkdir(parents=True,exist_ok=True)
    name = f'lattora-{version}-{target}'
    with tempfile.TemporaryDirectory(prefix='.bundle-',dir=output) as temporary:
        bundle = Path(temporary)/name; bundle.mkdir()
        (bundle/'runtime').mkdir(); (bundle/'engine').mkdir(); (bundle/'scripts').mkdir(); (bundle/'bin').mkdir()
        for kind in ('python','java'):
            archive = cached_runtime(cache,target,kind,lock['targets'][target][kind])
            unpack_runtime(archive,bundle/'runtime'/kind,kind)
        requirements = root/'packaging/requirements-ui.lock'
        packages = bundle/'runtime/python/lib/python3.14/site-packages'
        command = [sys.executable,'-m','pip','install','--disable-pip-version-check','--no-compile','--require-hashes',
                   '--only-binary=:all:','--target',str(packages),'-r',str(requirements)]
        if wheelhouse: command += ['--no-index','--find-links',str(Path(wheelhouse).resolve())]
        subprocess.run(command,check=True)
        verified_files = {record['path']:record['sha256'] for record in receipt['inputs']['files']}
        for source in (root/'scripts').glob('*.py'):
            if not source.name.startswith('test_') and source.name not in ('build_lattora.py','accept_lattora.py','probe_frozen_metric_gaps.py'):
                destination = bundle/'scripts'/source.name
                shutil.copyfile(source,destination)
                if sha256(destination) != verified_files.get(source.relative_to(root).as_posix()):
                    raise ValueError('Verified script changed during packaging: '+source.name)
        shutil.copyfile(root/'scripts/requirements-tui.txt',bundle/'scripts/requirements-tui.txt')
        resource = bundle/'src/main/resources/protocol.properties'; resource.parent.mkdir(parents=True)
        shutil.copyfile(root/'src/main/resources/protocol.properties',resource)
        with zipfile.ZipFile(jar) as archive:
            if archive.read('protocol.properties') != resource.read_bytes(): raise ValueError('Protocol resource does not match verified engine')
        shutil.copyfile(jar,bundle/'engine/app.jar')
        if sha256(bundle/'engine/app.jar') != receipt['artifact']['sha256']:
            raise ValueError('Verified engine copy hash mismatch; packaging stopped.')
        shutil.copyfile(root/'LICENSE',bundle/'LICENSE')
        shutil.copyfile(root/'SOURCES.md',bundle/'SOURCES.md')
        licenses = bundle/'licenses'; licenses.mkdir(exist_ok=True)
        notices = cached_runtime(cache,target,'python-licenses',lock['targets'][target]['python_licenses'])
        python_notices(notices,licenses/'python')
        shutil.copytree(root/'packaging/python-licenses',licenses/'python',dirs_exist_ok=True)
        shutil.copytree(root/'src/main/resources/META-INF/third-party',licenses/'engine')
        shutil.copyfile(requirements,licenses/'requirements-ui.lock')
        (bundle/'bin/lattora').write_text(LAUNCHER); (bundle/'bin/lattora').chmod(0o755)
        # Bytecode caches and host-interpreter entry points are not runtime inputs.
        for directory in list(bundle.rglob('__pycache__')): shutil.rmtree(directory)
        if (packages/'bin').exists(): shutil.rmtree(packages/'bin')
        # Bootstrap extraction accepts regular files/directories only. Normalize
        # runtime links before inventory so extraction needs no link trust rules.
        for path in list(bundle.rglob('*')):
            if path.is_symlink():
                source = path.resolve(); path.unlink()
                if source.is_dir(): shutil.copytree(source,path)
                else: shutil.copy2(source,path)
        validate_receipt(root,receipt,allow_dirty=allow_dirty)
        manifest = {'schema':1,'product':'lattora','version':version,'platform':target,
                    'source_revision':revision,'source_dirty':dirty,'diagnostic':bool(dirty or inputs_dirty(receipt)),
                    'runtimes':{'python':lock['python_version'],'java':lock['java_version'],'textual':'8.2.8'},
                    'verification':{'java':'PASS','python':'PASS','tested_at':receipt.get('tested_at'),
                                    'input_fingerprint':receipt.get('input_fingerprint'),'java_verification':receipt.get('java_verification')},
                    'files':inventory(bundle)}
        (bundle/'distribution.json').write_text(json.dumps(manifest,indent=2)+'\n')
        archive = output/(name+'.tar.gz')
        temporary_archive = output/(name+'.tar.gz.tmp')
        with tarfile.open(temporary_archive,'w:gz') as stream: stream.add(bundle,arcname=name)
        temporary_archive.replace(archive)
    asset = {'name':archive.name,'sha256':sha256(archive),'size':archive.stat().st_size,'diagnostic':manifest['diagnostic']}
    (output/(target+'.json')).write_text(json.dumps(asset,indent=2)+'\n')
    return archive


def inputs_dirty(receipt): return receipt.get('inputs',{}).get('source_dirty') is not False


def installer_script(version, assets):
    version_tuple(version)
    cases = []
    for target, asset in sorted(assets.items()):
        if target not in runtime_lock()['targets']: raise ValueError('Unsupported installer target')
        if asset['name'] != f'lattora-{version}-{target}.tar.gz': raise ValueError('Invalid asset name')
        cases.append(f"  {target}) asset='{asset['name']}'; checksum='{asset['sha256']}'; size='{asset['size']}' ;;")
    return (ROOT/'packaging/install.sh.in').read_text().replace('@VERSION@',version).replace('@ASSETS_CASE@','\n'.join(cases))


def release_metadata(output):
    output = Path(output); version = ExecutionContext(ROOT).version
    assets = {path.stem:load_json(path) for path in output.glob('*.json') if path.stem in runtime_lock()['targets']}
    if set(assets) != set(runtime_lock()['targets']): raise ValueError('All three native bundles are required for release assembly')
    reports = {}
    revision = _git_revision(ROOT)
    for target, asset in assets.items():
        report = load_json(output/('acceptance-'+target+'.json'))
        if (report.get('status') != 'PASS' or report.get('platform') != target
                or report.get('version') != version or report.get('archive') != asset['name']
                or report.get('sha256') != asset['sha256']):
            raise ValueError('Missing passing native acceptance for '+target)
        if not revision or report.get('source_revision') != revision:
            raise ValueError('Native acceptance source revision does not match release checkout: '+target)
        reports[target] = report
        if asset.get('diagnostic'): raise ValueError('Diagnostic local bundles cannot be published')
        archive = output/asset['name']
        if sha256(archive) != asset['sha256'] or archive.stat().st_size != asset['size']: raise ValueError('Release archive changed')
    (output/'release.json').write_text(json.dumps({'schema':1,'product':'lattora','version':version,'assets':assets,'acceptance':reports},indent=2)+'\n')
    installer = output/'install.sh'; installer.write_text(installer_script(version,assets)); installer.chmod(0o755)
    sources = release_sources(output,version)
    paths = [sources,*(output/a['name'] for a in assets.values()),*(output/('acceptance-'+t+'.json') for t in assets),output/'release.json',installer]
    (output/'SHA256SUMS').write_text(''.join(sha256(p)+'  '+p.name+'\n' for p in sorted(paths)))


def main():
    if sys.version_info[:3] != (3,14,8):
        raise SystemExit('Packaging requires Python 3.14.8; source development supports Python 3.11+.')
    parser = argparse.ArgumentParser(description=__doc__,allow_abbrev=False)
    parser.add_argument('--target',choices=tuple(runtime_lock()['targets']))
    parser.add_argument('--receipt',type=Path,default=ROOT/'.cloudsim/verified-build.json')
    parser.add_argument('--output-dir',type=Path,default=ROOT/'target/distribution')
    parser.add_argument('--runtime-cache',type=Path,default=ROOT/'.cloudsim/lattora-downloads')
    parser.add_argument('--wheelhouse',type=Path)
    parser.add_argument('--allow-dirty',action='store_true',help='diagnostic local bundles only; release assembly rejects these')
    parser.add_argument('--release-metadata',action='store_true')
    args = parser.parse_args()
    if args.release_metadata: release_metadata(args.output_dir)
    elif args.target: print(assemble(args.target,args.receipt,args.output_dir,args.runtime_cache,allow_dirty=args.allow_dirty,wheelhouse=args.wheelhouse))
    else: parser.error('Select --target or --release-metadata')


if __name__ == '__main__': main()
