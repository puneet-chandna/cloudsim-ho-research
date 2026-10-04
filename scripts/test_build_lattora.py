from pathlib import Path
import tempfile
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
