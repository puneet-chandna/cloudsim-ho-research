import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import lattora_context as context


class ContextTests(unittest.TestCase):
    def test_source_and_installed_paths_are_separate(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = context.ExecutionContext(root, bundled=False, env={'HOME': str(root/'home')})
            self.assertEqual(source.results, root/'results')
            installed = context.ExecutionContext(root, bundled=True, env={'HOME':str(root/'home'), 'XDG_DATA_HOME':str(root/'data')})
            with patch.object(context.sys,'platform','linux'):
                self.assertEqual(installed.results, root/'data/lattora/results')
            self.assertEqual(installed.java, root/'runtime/java/bin/java')
            with patch.object(context.sys, 'platform', 'darwin'):
                mac = context.ExecutionContext(root, bundled=True, env={'HOME':str(root/'home')})
                self.assertEqual(mac.results, root/'home/Library/Application Support/lattora/results')

    def test_manifest_missing_and_component_tampering_fail_closed(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with self.assertRaisesRegex(ValueError, 'manifest'):
                context.verify_distribution(root, check_platform=False)
            (root/'engine').mkdir()
            (root/'engine/app.jar').write_bytes(b'jar')
            manifest = {'schema':1, 'product':'lattora', 'version':'2.1.0', 'platform':'linux-x86_64',
                        'source_revision':'a'*40, 'source_dirty':False,
                        'verification':{'java':'PASS','python':'PASS'},
                        'files':{'engine/app.jar':{'sha256':hashlib.sha256(b'jar').hexdigest(), 'size':3}}}
            (root/'distribution.json').write_text(json.dumps(manifest))
            self.assertEqual(context.verify_distribution(root, check_platform=False)['version'], '2.1.0')
            (root/'engine/app.jar').write_bytes(b'bad')
            with self.assertRaisesRegex(ValueError, 'integrity'):
                context.verify_distribution(root, check_platform=False)

    def test_inventory_cannot_omit_or_add_runtime_files(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); (root/'payload').write_bytes(b'original')
            manifest = {'schema':1,'product':'lattora','version':'2.1.0','source_revision':'a'*40,
                        'source_dirty':False,'verification':{'java':'PASS','python':'PASS'},
                        'files':{'payload':{'sha256':context.sha256(root/'payload'),'size':8}}}
            (root/'distribution.json').write_text(json.dumps(manifest))
            (root/'injected.py').write_text('unverified')
            with self.assertRaisesRegex(ValueError,'inventory'):
                context.verify_distribution(root,check_platform=False)

    def test_manifest_rejects_traversal_and_escaping_links(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            manifest = {'schema':1,'product':'lattora','version':'2.1.0','platform':'linux-arm64',
                        'source_revision':'a'*40,'source_dirty':False,
                        'verification':{'java':'PASS','python':'PASS'},
                        'files':{'../outside':{'sha256':'a'*64,'size':1}}}
            (root/'distribution.json').write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, 'path'):
                context.verify_distribution(root, check_platform=False)

    def test_platform_normalizes_arm_and_rejects_unsupported_targets(self):
        for name in ('arm64','aarch64'):
            with patch.object(context.sys,'platform','linux'), patch.object(context.platform,'machine',return_value=name):
                self.assertEqual(context.target_platform(), 'linux-arm64')
        with patch.object(context.sys,'platform','win32'):
            with self.assertRaisesRegex(ValueError, 'Unsupported'):
                context.target_platform()
