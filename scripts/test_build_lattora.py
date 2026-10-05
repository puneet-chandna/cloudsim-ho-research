from pathlib import Path
import tempfile
import os
import shlex
import subprocess
import tarfile
import unittest
from unittest.mock import patch

import build_lattora as builder


class PackagingTests(unittest.TestCase):
    def test_runtime_lock_covers_exact_targets_and_pins(self):
        lock = builder.runtime_lock()
        self.assertEqual(set(lock['targets']),{'linux-x86_64','linux-arm64','macos-arm64'})
        for target, record in lock['targets'].items():
            for runtime in ('python','java'):
                self.assertRegex(record[runtime]['sha256'],r'^[a-f0-9]{64}$')
        self.assertEqual(lock['python_version'],'3.14.8')

    def test_package_requires_complete_verified_build_receipt(self):
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(ValueError,'verification'):
                builder.validate_receipt(Path(temporary),{'tests':'NOT_RUN'})

    def test_bootstrap_embeds_pinned_assets_and_rejects_unknown_platforms(self):
        assets={'linux-x86_64':{'name':'lattora-2.1.0-linux-x86_64.tar.gz','sha256':'a'*64,'size':123}}
        script=builder.installer_script('2.1.0',assets)
        self.assertIn('a'*64,script)
        self.assertIn('--no-modify-path',script)
        self.assertIn('Unsupported',script)
        self.assertNotIn('sudo',script)

    def test_inventory_rejects_external_symlinks(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary); (root/'escape').symlink_to('/etc/passwd')
            with self.assertRaisesRegex(ValueError,'escape'):
                builder.inventory(root)

    def test_release_assembly_rejects_reports_from_another_revision(self):
        from lattora_context import sha256
        import json
        with tempfile.TemporaryDirectory() as temporary:
            output=Path(temporary)
            for target in builder.runtime_lock()['targets']:
                archive=output/f'lattora-2.1.0-{target}.tar.gz'; archive.write_bytes(b'fixture')
                asset={'name':archive.name,'sha256':sha256(archive),'size':7,'diagnostic':False}
                (output/(target+'.json')).write_text(json.dumps(asset))
                report={'status':'PASS','platform':target,'version':'2.1.0','archive':archive.name,
                        'sha256':asset['sha256'],'source_revision':'b'*40}
                (output/('acceptance-'+target+'.json')).write_text(json.dumps(report))
            with patch.object(builder,'_git_revision',return_value='a'*40):
                with self.assertRaisesRegex(ValueError,'revision'):
                    builder.release_metadata(output)
            self.assertFalse((output/'release.json').exists())

    def test_assembly_rejects_engine_changed_during_copy(self):
        import json,shutil,zipfile
        from lattora_context import sha256
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary)/'source'; root.mkdir()
            (root/'scripts').mkdir(); (root/'packaging').mkdir()
            resource=root/'src/main/resources/protocol.properties'; resource.parent.mkdir(parents=True); resource.write_text('frozen')
            notices=resource.parent/'META-INF/third-party'; notices.mkdir(parents=True)
            (notices/'DEPENDENCIES.txt').write_text('notices'); (root/'LICENSE').write_text('license')
            (root/'scripts/requirements-tui.txt').write_text('textual==8.2.8')
            (root/'packaging/requirements-ui.lock').write_text('locked')
            (root/'pom.xml').write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><version>2.1.0</version></project>')
            jar=root/'tested.jar'
            with zipfile.ZipFile(jar,'w') as archive: archive.writestr('protocol.properties',b'frozen')
            receipt=root/'receipt.json'; receipt.write_text(json.dumps({'artifact':{'sha256':sha256(jar)},'inputs':{'source_dirty':False,'files':[]}}))
            def unpack(archive,destination,kind): destination.mkdir(parents=True)
            copy=shutil.copyfile
            def changed_copy(source,destination,**kwargs):
                copy(source,destination,**kwargs)
                if Path(source)==jar: Path(destination).write_bytes(b'untested engine')
            lock=builder.runtime_lock()
            with patch.object(builder,'ROOT',root),patch.object(builder,'runtime_lock',return_value=lock), \
                 patch.object(builder,'validate_receipt',return_value=(jar,'a'*40,False)), \
                 patch.object(builder,'cached_runtime',return_value=jar),patch.object(builder,'unpack_runtime',side_effect=unpack), \
                 patch.object(builder.subprocess,'run'),patch.object(builder.shutil,'copyfile',side_effect=changed_copy):
                with self.assertRaisesRegex(ValueError,'engine.*hash'):
                    builder.assemble('linux-x86_64',receipt,Path(temporary)/'output',Path(temporary)/'cache')


class BootstrapArgumentsTests(unittest.TestCase):
    def check_arguments(self, *, requested=False):
        from lattora_context import sha256, target_platform
        for no_path in (False, True):
            with self.subTest(requested=requested, no_path=no_path), tempfile.TemporaryDirectory(prefix='bootstrap arguments ') as temporary:
                root=Path(temporary).resolve(); home=root/'fresh home'; home.mkdir()
                tools=root/'tools'; tools.mkdir()
                target=target_platform(); name='lattora-2.1.0-'+target
                bundle=root/name; (bundle/'bin').mkdir(parents=True)
                launcher=bundle/'bin/lattora'
                launcher.write_text('#!/bin/sh\n[ "$1" = _install ] || exit 99\nshift\nprintf "%s\\n" "$#" "$@" > "$HOME/arguments"\n')
                launcher.chmod(0o755)
                archive=root/(name+'.tar.gz')
                with tarfile.open(archive,'w:gz') as stream: stream.add(bundle,arcname=name)
                forwarded=root/'forwarded.sh'
                forwarded.write_text('#!/bin/bash\nset -eu\nprintf "%s\\n" "$#" "$@" > "$HOME/arguments"\n')
                curl=tools/'curl'
                curl.write_text('#!/bin/sh\nfor arg; do\n'+
                    '  case "$arg" in */v2.1.1/install.sh) exec /bin/cat '+shlex.quote(str(forwarded))+' ;; esac\n'+
                    'done\nwhile [ "$#" -gt 0 ]; do\n'+
                    '  if [ "$1" = -o ]; then exec /bin/cp '+shlex.quote(str(archive))+' "$2"; fi\n'+
                    '  shift\ndone\nexit 98\n')
                curl.chmod(0o755)
                installer=root/'install.sh'
                installer.write_text(builder.installer_script('2.1.0',{target:{'name':archive.name,'sha256':sha256(archive),'size':archive.stat().st_size}}))
                flags=(['--version','2.1.1'] if requested else [])+(['--no-modify-path'] if no_path else [])
                shell=os.environ.get('LATTORA_TEST_BASH','/bin/bash')
                env={**os.environ,'HOME':str(home),'PATH':str(tools)+':/usr/bin:/bin:/usr/sbin:/sbin'}
                result=subprocess.run([shell,installer,*flags],env=env,capture_output=True,text=True)
                self.assertEqual(result.returncode,0,result.stderr)
                self.assertTrue((home/'arguments').exists(), 'Bootstrap returned success without invoking installation: '+result.stderr)
                self.assertEqual((home/'arguments').read_text(), '1\n--no-modify-path\n' if no_path else '0\n')

    def test_native_bash_bootstrap_forwards_zero_or_one_options(self):
        self.check_arguments()

    def test_native_bash_pinned_version_forwarding_preserves_options(self):
        self.check_arguments(requested=True)
