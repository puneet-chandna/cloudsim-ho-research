#!/usr/bin/env python3
"""Native archive acceptance. Reports bind evidence to the exact archive hash.

The driver uses CI Python only to supervise. Every app command, validator,
TUI and management fixture runs with the archive's Python/Java, a fresh HOME,
and a PATH containing no Python, Java, Git or Maven.
"""
import argparse
import asyncio
import json
import os
from pathlib import Path
import shutil
import shlex
import signal
import subprocess
import sys
import tarfile
import tempfile
import time
import zipfile

from lattora_context import sha256, target_platform, load_json, verify_distribution


def bash_config(home):
    return home/('.bash_profile' if sys.platform == 'darwin' else '.bashrc')


def require(value, message):
    if not value: raise ValueError(message)


def run(command, env, cwd, log, *, success=True, timeout=1200):
    with log.open('w') as output:
        process = subprocess.run([str(p) for p in command],env=env,cwd=cwd,
                                 stdout=output,stderr=subprocess.STDOUT,timeout=timeout)
    require((process.returncode == 0) == success,
            f'Unexpected exit {process.returncode}: {command}; see {log}')
    return process.returncode


def fixture_bundle(original, destination, version):
    """A diagnostic version fixture, never eligible for release publication."""
    shutil.copytree(original,destination)
    jar = destination/'engine/app.jar'; temporary = jar.with_suffix('.fixture')
    with zipfile.ZipFile(jar) as source, zipfile.ZipFile(temporary,'w') as output:
        for item in source.infolist():
            data = source.read(item.filename)
            if item.filename == 'META-INF/MANIFEST.MF':
                old = load_json(original/'distribution.json')['version'].encode()
                data = data.replace(b'Implementation-Version: '+old,b'Implementation-Version: '+version.encode())
            output.writestr(item,data)
    temporary.replace(jar)
    manifest = load_json(destination/'distribution.json')
    manifest.update(version=version,diagnostic=True)
    manifest['files']['engine/app.jar'] = {'sha256':sha256(jar),'size':jar.stat().st_size}
    (destination/'distribution.json').write_text(json.dumps(manifest))
    verify_distribution(destination)


def management(root, workspace):
    from unittest.mock import patch
    import lattora_install as manager
    import lattora
    base = manager.install_root(); original = root.name
    version = '2.1.1' if original != '2.1.1' else '2.1.2'
    fixture = workspace/'diagnostic update fixture'
    fixture_bundle(root,fixture,version)
    archive = workspace/'fixture.tar.gz'
    with tarfile.open(archive,'w:gz') as stream: stream.add(fixture,arcname='fixture')
    manifest = {'version':version}
    asset = {'name':'fixture.tar.gz','sha256':sha256(archive),'size':archive.stat().st_size}
    def local_download(url, destination, record): shutil.copyfile(archive,destination)
    with patch.object(manager,'discover_release',return_value=(manifest,asset)):
        with patch.object(manager,'download',side_effect=ValueError('interrupted fixture download')):
            require(lattora.main(['update']) == 1,'Interrupted update was accepted')
        require((base/'current').resolve() == root,'Interrupted download changed active version')
        with patch.object(manager,'download',side_effect=local_download):
            # The old session stays usable throughout activation and rollback.
            with manager.session_lock(root):
                require(lattora.main(['update','--check']) == 0,'Check update failed')
                require((base/'current').resolve() == root,'Check update changed active version')
                require(lattora.main(['update',version]) == 0,'Pinned update failed')
                require((base/'current').resolve().name == version,'Update did not activate')
                require(root.exists(),'Update removed running release')
                require(lattora.main(['rollback']) == 0,'Rollback failed')
                require((base/'current').resolve() == root,'Rollback did not restore release')
    # Two separate installers must serialize the same real payload safely.
    env = dict(os.environ)
    commands = [subprocess.Popen([str(fixture/'bin/lattora'),'_install','--no-modify-path'],
                                env=env,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE) for _ in range(2)]
    for process in commands:
        _, error = process.communicate(timeout=120)
        require(process.returncode == 0,'Concurrent install failed: '+error.decode())
    manager.rollback()
    require((base/'current').resolve() == root,'Concurrent rerun lost rollback history')
    damaged = workspace/'damaged fixture'; shutil.copytree(root,damaged)
    manifest_path = damaged/'distribution.json'
    manifest_path.write_text('{}')
    require(subprocess.run([str(damaged/'bin/lattora'),'run','--profile','smoke','--plain'],
                           env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode != 0,
            'Damaged manifest executed an experiment')
    shutil.copyfile(root/'distribution.json',manifest_path)
    with (damaged/'engine/app.jar').open('ab') as output: output.write(b'altered')
    require(subprocess.run([str(damaged/'bin/lattora'),'run','--profile','smoke','--plain'],
                           env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode != 0,
            'Altered engine executed an experiment')
    # Hash-valid JAR with conflicting provenance must also fail before execution.
    fixture_bundle(root,workspace/'provenance fixture','9.9.9')
    mismatch = workspace/'provenance fixture'
    manifest = load_json(mismatch/'distribution.json'); manifest['version'] = original
    (mismatch/'distribution.json').write_text(json.dumps(manifest))
    require(subprocess.run([str(mismatch/'bin/lattora'),'run','--profile','smoke','--plain'],
                           env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode != 0,
            'Conflicting engine provenance executed an experiment')


async def ui(root):
    from cloudsim_tui import CloudSimApp
    from textual.widgets import Input, Select, Button
    import urllib.request
    # Any in-process network attempt makes acceptance fail; workers have no
    # automatic network path, and PATH omits curl/wget too.
    def offline(*args, **kwargs): raise AssertionError('Unexpected network request')
    urllib.request.urlopen = offline
    app = CloudSimApp()
    async with app.run_test(size=(80,24)) as pilot:
        async def until(predicate, seconds=90):
            deadline = time.monotonic()+seconds
            while not predicate():
                require(time.monotonic() < deadline,'TUI operation timed out')
                await pilot.pause(.1)
        await until(lambda:not app.busy)
        require(app.ready,'Installed TUI readiness failed')
        await app.start_action()
        for width,height in ((120,34),(80,24),(60,18)):
            await pilot.resize_terminal(width,height); await pilot.pause(.1)
            app.action_tab('tools'); await pilot.pause(.1)
            app.action_tab('run'); await pilot.pause(.1)
        await until(lambda:not app.busy)
        require(app.outcome.get('exit_code') == 0 and app.outcome.get('validation') == 'PASS','Installed TUI Smoke failed')
        app.action_edit()
        app.query_one('#profile',Select).value='stress'; await pilot.pause(.1)
        for name,value in {'vms':'1000','hosts':'200','population':'30','iterations':'100','replications':'5','deadline':'5m'}.items():
            app.query_one('#'+name,Input).value=value
        await app.start_action()
        await until(lambda:app.child_pid is not None)
        identity = app.child_identity
        app.action_cancel(); await pilot.pause(.1)
        app.screen.query_one('#confirm',Button).press()
        await until(lambda:not app.busy)
        require(app.outcome.get('status') == 'interrupted' and app.outcome.get('exit_code') != 0,'Cancellation reported success')
        if identity:
            from cloudsim_runtime import owned_child_identity
            require(owned_child_identity(identity['pid'],identity['parent']) != identity,'Cancelled TUI left its owned child alive')
    print('PASS: installed TUI, real worker, resize, navigation and cancellation')


def accept(archive, report):
    archive = archive.resolve(); report = report.resolve(); report.parent.mkdir(parents=True,exist_ok=True)
    logs = report.parent/('acceptance-'+target_platform()); logs.mkdir(exist_ok=True)
    evidence = {'schema':1,'platform':target_platform(),'archive':archive.name,'sha256':sha256(archive),'status':'RUNNING','checks':[]}
    report.write_text(json.dumps(evidence,indent=2)+'\n')
    try:
        with tempfile.TemporaryDirectory(prefix='lattora native acceptance ') as temporary:
            work = Path(temporary); extracted=work/'extracted'; extracted.mkdir()
            with tarfile.open(archive,'r:gz') as stream: stream.extractall(extracted,filter='data')
            roots=list(extracted.iterdir()); require(len(roots)==1,'Unexpected archive layout')
            staged=roots[0]; manifest=verify_distribution(staged)
            evidence['version']=manifest['version']; evidence['source_revision']=manifest['source_revision']
            home=work/'fresh user home'; home.mkdir(); cwd=work/'arbitrary invocation'; cwd.mkdir()
            tools=work/'tools'; tools.mkdir()
            # The shell launcher needs dirname; no application runtimes are exposed.
            for name in ('dirname','uname','getconf','mktemp','rm','mkdir','wc','tr','tar','awk','gzip','sha256sum','shasum'):
                path=shutil.which(name)
                if path: (tools/name).symlink_to(path)
            env={'HOME':str(home),'PATH':str(tools),'SHELL':'/bin/bash','TERM':'xterm-256color',
                 'LANG':'en_US.UTF-8','PYTHONDONTWRITEBYTECODE':'1','NO_COLOR':'1','LATTORA_BUNDLED':'1'}
            for name in ('python','python3','java','javac','git','mvn'):
                require(shutil.which(name,path=env['PATH']) is None,'System tool available: '+name)
            from build_lattora import installer_script
            asset={'name':archive.name,'sha256':sha256(archive),'size':archive.stat().st_size}
            installer=work/'install.sh'; installer.write_text(installer_script(manifest['version'],{target_platform():asset}))
            # Route only the expected official URL to the exact accepted archive.
            curl=tools/'curl'
            expected='https://github.com/puneet-chandna/cloudsim-ho-research/releases/download/v'+manifest['version']+'/'+archive.name
            curl.write_text('#!/bin/sh\nfound=false\nfor arg; do [ "$arg" != '+shlex.quote(expected)+' ] || found=true; done\n'+
                            '[ "$found" = true ] || exit 99\nwhile [ "$#" -gt 0 ]; do\n'+
                            '  if [ "$1" = -o ]; then exec '+shlex.quote(shutil.which('cp'))+' '+shlex.quote(str(archive))+' "$2"; fi\n'+
                            '  shift\ndone\nexit 98\n'); curl.chmod(0o755)
            run(['/bin/bash',installer],env,cwd,logs/'install.log')
            # Experiments have no network tool on their PATH after bootstrap.
            curl.unlink()
            command=home/'.local/bin/lattora'; root=(home/'.local/share/lattora/current').resolve()
            python=root/'runtime/python/bin/python3'
            run([staged/'bin/lattora','_install'],env,cwd,logs/'rerun.log')
            require(bash_config(home).read_text().count('# >>> lattora PATH >>>')==1,'PATH was appended twice')
            evidence['checks'].append('checksum bootstrap, fresh installation, spaced paths, PATH setup and rerun')
            for arguments in (['--version'],['doctor'],['run','--help'],['completion','bash'],['completion','zsh'],['completion','fish']):
                run([command,*arguments],env,cwd,logs/(arguments[0].replace('-','')+'-'+arguments[-1]+'.log'))
            data=home/('Library/Application Support/lattora' if sys.platform=='darwin' else '.local/share/lattora')
            library=data/'results'
            (cwd/'seed overlay.properties').write_text('master.seed=123456\nlog.level=INFO\n')
            for profile in ('smoke','explore','research','stress'):
                arguments=['run','--profile',profile,'--plain','--workers','2']
                parent=library
                if profile=='smoke': arguments+=['--config','seed overlay.properties']
                if profile=='explore': arguments+=['--output-dir','explicit relative results']; parent=cwd/'explicit relative results'
                if profile=='stress': arguments+=['--vms','100','--hosts','20','--population','10','--iterations','4','--replications','1','--time-limit','5m']
                run([command,*arguments],env,cwd,logs/(profile+'.log'))
                outputs=list(parent.glob(profile+'-*')); require(len(outputs)==1,'Missing retained '+profile)
                metadata=load_json(outputs[0]/'runner.json')
                require(metadata['status']=='complete' and metadata['validation']=='PASS','Profile did not validate: '+profile)
                require(metadata['tests']=='NOT_RUN' and metadata['build']=='RELEASE_VERIFIED','Local tests were misrepresented')
                if profile=='stress': require(metadata['production']['sampled_peak_rss_bytes']>0,'No native RSS samples')
                run([command,'validate',outputs[0],'--plain'],env,cwd,logs/(profile+'-independent.log'))
                evidence['checks'].append(profile+' offline execution and independent validation')
            # Execute the same helper with bundled Python, importing installed modules.
            prefix=[python,'-B','-c','import runpy,sys; sys.path.insert(0,sys.argv[1]); sys.argv=sys.argv[2:]; runpy.run_path(sys.argv[0],run_name="__main__")',root/'scripts',Path(__file__).resolve()]
            run([*prefix,'--child','ui','--root',root],env,cwd,logs/'tui.log',timeout=300)
            evidence['checks'].append('installed TUI, resize, real worker, cancellation and cleanup')
            config=home/('Library/Application Support/lattora/settings.json' if sys.platform=='darwin' else '.config/lattora/settings.json')
            config.parent.mkdir(parents=True,exist_ok=True); config.write_text('{"theme":"paper"}\n')
            run([*prefix,'--child','management','--root',root,'--workspace',work],env,cwd,logs/'management.log',timeout=600)
            require(config.read_text()=='{"theme":"paper"}\n','Update changed settings')
            require(library.exists(),'Update removed results')
            run([command,'uninstall','--yes'],env,cwd,logs/'uninstall.log')
            require(config.exists() and library.exists() and not command.exists(),'Uninstall did not preserve user data')
            evidence['checks'].append('update fixtures, pinned install, rollback, concurrency, interruption, damaged manifest/JAR, provenance mismatch, data preservation')
        evidence['status']='PASS'
    except BaseException as error:
        evidence.update(status='FAIL',error=str(error)); raise
    finally: report.write_text(json.dumps(evidence,indent=2)+'\n')
    print('PASS: '+str(archive)+'; report: '+str(report))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive',type=Path); parser.add_argument('--report',type=Path)
    parser.add_argument('--child',choices=('ui','management')); parser.add_argument('--root',type=Path); parser.add_argument('--workspace',type=Path)
    args=parser.parse_args()
    if args.child=='ui': asyncio.run(ui(args.root))
    elif args.child=='management': management(args.root,args.workspace)
    elif args.archive and args.report: accept(args.archive,args.report)
    else: parser.error('--archive and --report are required')


if __name__=='__main__': main()
