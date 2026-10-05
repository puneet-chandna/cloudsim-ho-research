"""Runtime selection and safe local setup boundaries; no downloads in unit tests."""
import hashlib
import io
import json
import os
from pathlib import Path
import tarfile
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch

import cloudsim_runtime as runtime


class RuntimeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()

    def jdk(self, path):
        (path/'bin').mkdir(parents=True)
        for name in ('java', 'javac'):
            (path/'bin'/name).touch()
        return path

    def test_explicit_jdk_and_child_environment_do_not_change_parent(self):
        selected = self.jdk(self.root/'chosen jdk')
        managed = self.jdk(self.root/'.cloudsim/jdk')
        original = {'JAVA_HOME': str(selected), 'PATH': '/usr/bin', 'OTHER': 'kept'}
        child = runtime.java_environment(original, self.root)
        self.assertEqual(child['JAVA_HOME'], str(selected))
        self.assertEqual(child['PATH'], str(selected/'bin')+os.pathsep+'/usr/bin')
        self.assertEqual(child['OTHER'], 'kept')
        self.assertEqual(original['PATH'], '/usr/bin')
        self.assertNotEqual(child['JAVA_HOME'], str(managed))
        self.assertEqual(child['MAVEN_USER_HOME'], str(self.root/'.cloudsim/maven'))

    def test_saved_and_managed_jdk_are_selected_without_shell_changes(self):
        managed = self.jdk(self.root/'.cloudsim/jdk')
        self.assertEqual(runtime.java_environment({'PATH': '/usr/bin'}, self.root)['JAVA_HOME'], str(managed))
        chosen = self.jdk(self.root/'saved jdk')
        runtime.save_java_home(chosen, self.root)
        self.assertEqual(runtime.java_environment({'PATH': '/usr/bin'}, self.root)['JAVA_HOME'], str(chosen))

    def test_installed_jdk21_is_discovered_when_path_has_full_jdk17(self):
        old = self.jdk(self.root/'jdk17')
        valid = self.jdk(self.root/'jdk21')
        with patch.object(runtime.sys, 'platform', 'linux'), \
             patch.object(runtime.shutil, 'which', return_value=str(old/'bin/java')), \
             patch.object(Path, 'glob', return_value=[valid/'bin/javac']), \
             patch.object(runtime, 'probe_jdk', return_value=valid):
            self.assertEqual(runtime.java_environment({'PATH':str(old/'bin')}, self.root)['JAVA_HOME'], str(valid))

    def test_macos_uses_java_home_instead_of_apple_java_stub(self):
        valid = self.jdk(self.root/'Temurin 21.jdk/Contents/Home')
        with patch.object(runtime.sys, 'platform', 'darwin'), \
             patch.object(runtime.subprocess, 'run', return_value=SimpleNamespace(returncode=0, stdout=str(valid)+'\n')), \
             patch.object(runtime, 'probe_jdk', return_value=valid), \
             patch.object(runtime.shutil, 'which', return_value='/usr/bin/java'):
            child = runtime.java_environment({'PATH':'/usr/bin'}, self.root)
        self.assertEqual(child['JAVA_HOME'], str(valid))

    def test_macos_explicit_selection_does_not_run_discovery(self):
        valid = self.jdk(self.root/'chosen')
        with patch.object(runtime.sys, 'platform', 'darwin'), patch.object(runtime.subprocess, 'run') as probe:
            self.assertEqual(runtime.java_environment({'JAVA_HOME':str(valid)}, self.root)['JAVA_HOME'], str(valid))
        probe.assert_not_called()

    def test_macos_jdk_download_is_pinned_for_apple_silicon(self):
        for machine, arch, checksum in (
            ('arm64','aarch64','3623232f33a9c3baadf304480b2535f9a3cba8a58d42ecbb438ba267315d9998'),
            ('aarch64','aarch64','3623232f33a9c3baadf304480b2535f9a3cba8a58d42ecbb438ba267315d9998')):
            with self.subTest(machine=machine), patch.object(runtime.sys,'platform','darwin'), \
                 patch.object(runtime.platform,'machine',return_value=machine):
                url, digest = runtime.jdk_download()
                self.assertIn(f'jdk_{arch}_mac_hotspot_', url)
                self.assertEqual(digest, checksum)
        with patch.object(runtime.sys,'platform','linux'), patch.object(runtime.platform,'machine',return_value='x86_64'):
            self.assertEqual(runtime.jdk_download(), (runtime.JDK_URL, runtime.JDK_SHA256))
        with patch.object(runtime.sys,'platform','darwin'), patch.object(runtime.platform,'machine',return_value='unsupported'):
            with self.assertRaisesRegex(ValueError, 'installed JDK'): runtime.jdk_download()

    def test_macos_archive_installs_contents_home_as_managed_jdk(self):
        archive = self.archive('jdk-21.jdk/Contents/Home/bin/java')
        digest = hashlib.sha256(archive.read_bytes()).hexdigest()
        target = self.root/'destination'
        with patch.object(runtime,'probe_jdk') as probe:
            runtime.extract_jdk(archive, digest, target)
        self.assertTrue((target/'bin/java').is_file())
        self.assertEqual(probe.call_args.args[0].parts[-2:], ('Contents','Home'))

    def test_macos_install_uses_selected_checksum_and_records_provenance(self):
        class Response(io.BytesIO):
            headers = {'Content-Length':'7'}
        with patch.object(runtime.sys,'platform','darwin'), patch.object(runtime.platform,'machine',return_value='arm64'), \
             patch.object(runtime.urllib.request,'urlopen',return_value=Response(b'archive')) as download, \
             patch.object(runtime,'extract_jdk') as extract:
            runtime.install_jdk(self.root, report=lambda message:None)
        url=download.call_args.args[0].full_url
        self.assertIn('jdk_aarch64_mac_hotspot_', url)
        self.assertEqual(extract.call_args.args[1],runtime.MAC_JDK_SHA256['aarch64'])
        metadata=json.loads((self.root/'.cloudsim/jdk-install.json').read_text())
        self.assertEqual(metadata['url'],url)
        self.assertEqual(metadata['sha256'],runtime.MAC_JDK_SHA256['aarch64'])

    def archive(self, name='jdk/bin/java', link=None):
        path = self.root/'download.tar.gz'
        with tarfile.open(path, 'w:gz') as archive:
            info = tarfile.TarInfo(name)
            if link:
                info.type = tarfile.SYMTYPE; info.linkname = link
                archive.addfile(info)
            else:
                data = b'java'; info.size = len(data)
                archive.addfile(info, io.BytesIO(data))
        return path

    def test_bad_checksum_cannot_extract_or_install(self):
        path = self.archive()
        with self.assertRaisesRegex(ValueError, 'checksum'):
            runtime.extract_jdk(path, '0'*64, self.root/'destination')
        self.assertFalse((self.root/'destination').exists())

    def test_archive_cannot_escape_destination(self):
        for name, link in (('../escaped', None), ('jdk/escape', '../../escaped')):
            with self.subTest(name=name):
                path = self.archive(name, link)
                digest = hashlib.sha256(path.read_bytes()).hexdigest()
                with self.assertRaises((ValueError, tarfile.TarError)):
                    runtime.extract_jdk(path, digest, self.root/'destination')
                self.assertFalse((self.root/'escaped').exists())

    def test_existing_runtime_is_never_replaced_by_extraction(self):
        target = self.jdk(self.root/'destination')
        (target/'user-file').write_text('preserve')
        path = self.archive()
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        with self.assertRaises(FileExistsError):
            runtime.extract_jdk(path, digest, target)
        self.assertEqual((target/'user-file').read_text(), 'preserve')
