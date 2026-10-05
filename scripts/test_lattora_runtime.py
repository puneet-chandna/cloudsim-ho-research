import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import lattora_context
import research_runner as runner


class Dashboard:
    console = None
    def render(self, *args, **kwargs): pass
    def poll(self, *args): pass
    def set_log(self, *args): pass


class RuntimeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()/'bundle'; self.root.mkdir()
        self.env = patch.dict('os.environ', {'LATTORA_BUNDLED':'1'}); self.env.start(); self.addCleanup(self.env.stop)
        (self.root/'runtime/java/bin').mkdir(parents=True)
        java = self.root/'runtime/java/bin/java'
        java.write_text('#!/bin/sh\nprintf \'openjdk version "21.0.12.1"\\n\'\n'); java.chmod(0o755)
        (self.root/'engine').mkdir()
        self.jar = self.root/'engine/app.jar'
        self.write_jar('a'*40)
        self.manifest = {'schema':1,'product':'lattora','version':'2.1.0','platform':lattora_context.target_platform(),
                         'source_revision':'a'*40,'source_dirty':False,'verification':{'java':'PASS','python':'PASS'},
                         'files':{path.relative_to(self.root).as_posix():{'sha256':lattora_context.sha256(path),'size':path.stat().st_size}
                                  for path in (self.jar,java)}}
        (self.root/'distribution.json').write_text(json.dumps(self.manifest))

    def write_jar(self, revision):
        with zipfile.ZipFile(self.jar,'w') as archive:
            archive.writestr('META-INF/MANIFEST.MF',f'Implementation-Version: 2.1.0\nGit-Revision: {revision}\nGit-Dirty: false\n')

    def test_installed_preflight_uses_jre_without_javac_or_host_java(self):
        control = runner.ProcessControl(Dashboard(), self.root)
        with patch.object(runner,'ROOT',self.root), patch.object(runner,'require_memory',return_value=4*1024**3):
            java, memory = runner.check_environment(control,1024)
        self.assertEqual(java,self.root/'runtime/java/bin/java')
        self.assertFalse((java.parent/'javac').exists())
        self.assertEqual(memory,4*1024**3)

    def test_installed_provenance_and_artifact_do_not_build_or_call_git(self):
        outer = Path(self.temp.name).resolve()/'output'; outer.mkdir()
        control = runner.ProcessControl(Dashboard(),self.root)
        metadata = {}; args = type('Args',(),{'skip_build':False,'force_build':False})()
        with patch.object(control,'run',side_effect=AssertionError('No build or Git permitted')):
            runner.capture_provenance(control,metadata,outer,root=self.root)
            retained, source = runner.prepare_artifact(args,metadata,control,outer,'smoke',root=self.root)
        self.assertEqual(retained.read_bytes(),self.jar.read_bytes())
        self.assertEqual(metadata['tests'],'NOT_RUN')
        self.assertEqual(metadata['release_verification']['java'],'PASS')
        self.assertEqual(source,self.jar)

    def test_jar_revision_mismatch_blocks_installed_execution(self):
        self.write_jar('b'*40)
        self.manifest['files']['engine/app.jar'] = {'sha256':lattora_context.sha256(self.jar),'size':self.jar.stat().st_size}
        (self.root/'distribution.json').write_text(json.dumps(self.manifest))
        outer = Path(self.temp.name).resolve()/'output'; outer.mkdir()
        control = runner.ProcessControl(Dashboard(),self.root)
        args = type('Args',(),{'skip_build':False,'force_build':False})()
        with self.assertRaisesRegex(ValueError,'provenance'):
            runner.prepare_artifact(args,{},control,outer,'smoke',root=self.root)
