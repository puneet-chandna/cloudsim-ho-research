import json
import sys
from pathlib import Path
import tempfile
from unittest.mock import patch, PropertyMock
import unittest

from cloudsim_tui import CloudSimApp
from cloudsim_results import build_result_summary


class InstalledUiTests(unittest.IsolatedAsyncioTestCase):
    async def test_installed_ui_uses_library_and_disables_source_controls(self):
        with tempfile.TemporaryDirectory() as temporary, patch.dict('os.environ',{'HOME':temporary,'LATTORA_BUNDLED':'1'}), \
             patch('lattora_context.ExecutionContext.version',new_callable=PropertyMock,return_value='2.1.0'), \
             patch('cloudsim_tui.CloudSimApp.check_readiness'), patch('cloudsim_tui.CloudSimApp.refresh_results'):
            app = CloudSimApp()
            async with app.run_test(size=(80,24)):
                self.assertEqual(app.TITLE,'Lattora')
                self.assertEqual(app.output_parent,Path(temporary)/('Library/Application Support/lattora/results' if sys.platform=='darwin' else '.local/share/lattora/results'))
                for selector in ('#build','#test','#force-build','#skip-build','#install-jdk','#select-jdk'):
                    self.assertFalse(app.query_one(selector).display,selector)
                self.assertIn('bundled',str(app.query_one('#setup-copy').render()).lower())

    def test_release_verification_is_distinct_from_local_tests(self):
        metadata={'status':'complete','exit_code':0,'validation':'PASS','profile':'smoke',
                  'tests':'NOT_RUN','build':'RELEASE_VERIFIED','release_verification':{'java':'PASS','python':'PASS'}}
        summary=build_result_summary(metadata)
        check=next(c for c in summary['checks'] if c['label']=='Release verification')
        self.assertEqual(check['status'],'RECORDED_PASS')
        self.assertIn('NOT_RUN',check['detail'])
