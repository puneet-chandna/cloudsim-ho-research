import hashlib
import json
import os
from pathlib import Path
import tarfile
import tempfile
import subprocess
import unittest
from unittest.mock import patch

import lattora_context
import lattora_install as install


class InstallTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.home = Path(self.temp.name)/'user space'; self.home.mkdir()
        self.env = {'HOME':str(self.home),'SHELL':'/bin/bash','PATH':'/usr/bin:/bin'}
        self.base = self.home/'.local/share/lattora'

    def bundle(self, version):
        root = Path(self.temp.name)/('bundle-'+version); root.mkdir(exist_ok=True)
        (root/'payload').write_text(version)
        manifest = {'schema':1,'product':'lattora','version':version,'platform':lattora_context.target_platform(),
                    'source_revision':'a'*40,'source_dirty':False,'verification':{'java':'PASS','python':'PASS'},
                    'files':{'payload':{'sha256':lattora_context.sha256(root/'payload'),'size':len(version)}}}
        (root/'distribution.json').write_text(json.dumps(manifest))
        return root

    def install(self, version):
        return install.install_staged(self.bundle(version), env=self.env, health=lambda root:None)

    def test_install_rerun_path_and_data_survive_rollback_and_uninstall(self):
        self.install('2.1.0')
        results = self.base/'results'; results.mkdir(); (results/'evidence').write_text('keep')
        config = self.home/'.config/lattora/settings.json'; config.parent.mkdir(parents=True); config.write_text('{}')
        self.install('2.1.0')
        self.assertEqual((self.home/('.bash_profile' if os.sys.platform=='darwin' else '.bashrc')).read_text().count('# >>> lattora PATH >>>'),1)
        self.install('2.1.1')
        install.rollback(env=self.env, health=lambda root:None)
        self.assertEqual((self.base/'current').resolve().name,'2.1.0')
        install.uninstall(env=self.env)
        self.assertTrue((results/'evidence').exists()); self.assertTrue(config.exists())
        self.assertFalse((self.home/'.local/bin/lattora').exists())

    def test_unrelated_existing_command_is_preserved(self):
        command = self.home/'.local/bin/lattora'; command.parent.mkdir(parents=True); command.write_text('someone else')
        with self.assertRaisesRegex(ValueError,'unrelated'):
            self.install('2.1.0')
        self.assertEqual(command.read_text(),'someone else')
        self.assertFalse((self.base/'current').exists())

    def test_failed_health_check_does_not_change_active_version(self):
        self.install('2.1.0')
        with self.assertRaisesRegex(ValueError,'broken'):
            install.install_staged(self.bundle('2.1.1'),env=self.env,health=lambda root:(_ for _ in ()).throw(ValueError('broken')))
        self.assertEqual((self.base/'current').resolve().name,'2.1.0')

    def test_active_session_blocks_uninstall(self):
        self.install('2.1.0')
        with install.session_lock(self.base/'versions/2.1.0', env=self.env):
            with self.assertRaisesRegex(ValueError,'running'):
                install.uninstall(env=self.env)
        self.assertTrue((self.base/'current').exists())

    def test_archive_rejects_traversal_and_escaping_symlinks(self):
        for name, link in (('../escape',None),('package/link','../../escape')):
            with self.subTest(name=name):
                archive = Path(self.temp.name)/'bad.tar.gz'
                with tarfile.open(archive,'w:gz') as stream:
                    item = tarfile.TarInfo(name)
                    if link: item.type=tarfile.SYMTYPE; item.linkname=link
                    stream.addfile(item)
                with self.assertRaises((ValueError,tarfile.FilterError)):
                    install.extract_bundle(archive,Path(self.temp.name)/'extracted')
        self.assertFalse((Path(self.temp.name)/'escape').exists())

    def test_release_discovery_ignores_legacy_and_prereleases(self):
        releases=[{'tag_name':'v1.0.3','assets':[]}, {'tag_name':'v2.1.1','prerelease':True,'assets':[{'name':'release.json'}]},
                  {'tag_name':'v2.1.0','assets':[{'name':'release.json'}]}]
        manifest={'schema':1,'product':'lattora','version':'2.1.0','assets':{lattora_context.target_platform():
                  {'name':'lattora-2.1.0-'+lattora_context.target_platform()+'.tar.gz','sha256':'a'*64,'size':100}}}
        def fetch(url): return manifest if url.endswith('/release.json') else releases
        release, asset = install.discover_release(fetch=fetch)
        self.assertEqual(release['version'],'2.1.0'); self.assertEqual(asset['sha256'],'a'*64)

    def test_download_checksum_failure_preserves_existing_install(self):
        self.install('2.1.0')
        with patch.object(install,'discover_release',return_value=({'version':'2.1.1'}, {'name':'asset','size':3,'sha256':'a'*64})), \
             patch.object(install,'download',side_effect=ValueError('checksum mismatch')):
            with self.assertRaisesRegex(ValueError,'checksum'):
                install.update(env=self.env)
        self.assertEqual((self.base/'current').resolve().name,'2.1.0')

    def test_path_block_executes_with_spaces_in_home(self):
        config = install.configure_path(self.env)
        result = subprocess.run(['/bin/bash','-c','source "$1"; printf "%s" "$PATH"','bash',str(config)],
                                env=self.env,capture_output=True,text=True)
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertTrue(result.stdout.startswith(str(self.home/'.local/bin')+':'))

    def test_activation_state_failure_restores_previous_version(self):
        self.install('2.1.0')
        with patch.object(install,'_atomic_json',side_effect=OSError('disk full')):
            with self.assertRaisesRegex(OSError,'disk full'):
                self.install('2.1.1')
        self.assertEqual((self.base/'current').resolve().name,'2.1.0')

    def test_latest_update_never_implicitly_downgrades(self):
        self.install('2.1.1')
        with patch.object(install,'discover_release',return_value=({'version':'2.1.0'},{})), patch.object(install,'download') as download:
            self.assertEqual(install.update(env=self.env),'2.1.1')
        download.assert_not_called()

    def test_killed_activation_keeps_recoverable_history(self):
        self.install('2.1.0')
        child=os.fork()
        if child == 0:
            with patch.object(install,'_atomic_json',side_effect=lambda *args:os._exit(137)):
                self.install('2.1.1')
            os._exit(0)
        _,status=os.waitpid(child,0)
        self.assertEqual(os.waitstatus_to_exitcode(status),137)
        self.assertEqual((self.base/'current').resolve().name,'2.1.0')

    def test_killed_after_pointer_commit_still_allows_rollback(self):
        self.install('2.1.0')
        child=os.fork()
        if child == 0:
            replace=os.replace
            def commit_then_die(source,destination):
                replace(source,destination)
                if Path(destination) == self.base/'current': os._exit(137)
            with patch.object(install.os,'replace',side_effect=commit_then_die):
                self.install('2.1.1')
            os._exit(0)
        _,status=os.waitpid(child,0)
        self.assertEqual(os.waitstatus_to_exitcode(status),137)
        self.assertEqual((self.base/'current').resolve().name,'2.1.1')
        self.assertEqual(install.rollback(env=self.env,health=lambda root:None),'2.1.0')

    def test_native_bash_acceptance_uses_the_config_installer_wrote(self):
        import accept_lattora
        for system in ('linux','darwin'):
            with self.subTest(platform=system),patch.object(install.os.sys,'platform',system):
                actual=install.configure_path(self.env)
                self.assertEqual(accept_lattora.bash_config(self.home),actual)
