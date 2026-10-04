import io
import unittest
from contextlib import redirect_stdout
from unittest.mock import patch

import lattora


class CliTests(unittest.TestCase):
    def test_run_and_validate_forward_existing_parser_contract(self):
        with patch('lattora.cloudsim.main',return_value=0) as run:
            self.assertEqual(lattora.main(['run','--profile','smoke','--plain']),0)
            run.assert_called_once_with(['--profile','smoke','--plain'])
        with patch('lattora.cloudsim.main',return_value=0) as run:
            self.assertEqual(lattora.main(['validate','./results']),0)
            run.assert_called_once_with(['--validate','./results'])

    def test_version_is_derived_from_project(self):
        output = io.StringIO()
        with redirect_stdout(output): self.assertEqual(lattora.main(['--version']),0)
        self.assertEqual(output.getvalue().strip(),'Lattora 2.1.0')

    def test_completion_scripts_cover_management_and_profiles(self):
        for shell in ('bash','zsh','fish'):
            output = io.StringIO()
            with redirect_stdout(output): self.assertEqual(lattora.main(['completion',shell]),0)
            self.assertIn('update',output.getvalue()); self.assertIn('research',output.getvalue())

    def test_launch_does_not_check_network(self):
        with patch('lattora.cloudsim.main',return_value=0), patch('lattora.install.discover_release',side_effect=AssertionError('unexpected network')):
            self.assertEqual(lattora.main([]),0)

    def test_installed_validate_rejects_missing_metadata_and_altered_validator(self):
        import json
        from pathlib import Path
        import tempfile
        import lattora_context
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary); ctx=lattora_context.ExecutionContext(root,bundled=True,env={'HOME':temporary})
            with patch('lattora.context.get_context',return_value=ctx),patch('lattora.cloudsim.main',return_value=0) as validate:
                self.assertEqual(lattora.main(['validate','evidence']),1)
                validate.assert_not_called()
                validator=root/'validator.py'; validator.write_text('original')
                manifest={'schema':1,'product':'lattora','version':'2.1.0','platform':lattora_context.target_platform(),
                          'source_revision':'a'*40,'source_dirty':False,'verification':{'java':'PASS','python':'PASS'},
                          'files':{'validator.py':{'sha256':lattora_context.sha256(validator),'size':8}}}
                (root/'distribution.json').write_text(json.dumps(manifest))
                validator.write_text('modified')
                self.assertEqual(lattora.main(['validate','evidence']),1)
                validate.assert_not_called()
