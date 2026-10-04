"""Opt-in reproductions for TEST_FINDINGS.md; not a dataset acceptance gate.

Supply a valid Explore run. Each expected failure means a documented, coherent
metric forgery was accepted. Unexpected success means a rejection was added and
the finding/probe needs review. Missing fixtures and unexpected exceptions error.
"""
import argparse
import contextlib
import csv
import hashlib
import io
import json
from pathlib import Path
import shutil
import tempfile
import unittest

import statistics_validator as validator


class FrozenMetricGaps(unittest.TestCase):
    source = None

    @classmethod
    def setUpClass(cls):
        with contextlib.redirect_stdout(io.StringIO()):
            validator.validate(cls.source)
        manifest = json.loads((cls.source / 'run.json').read_text())
        if manifest['profile'] != 'explore':
            raise ValueError('The coherent-mutation reproductions require an Explore run')

    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='frozen-metric-gap-')
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name) / 'run'
        shutil.copytree(self.source, self.directory)
        raw_path = self.directory / 'raw/main_results.csv'
        summary_path = self.directory / 'analysis/scenario_summary.csv'
        with raw_path.open(newline='') as stream:
            reader = csv.DictReader(stream); fields = reader.fieldnames; raw = list(reader)
        with summary_path.open(newline='') as stream:
            reader = csv.DictReader(stream); summary_fields = reader.fieldnames; summary = list(reader)
        mutation = self._testMethodName
        if mutation == 'test_rejects_coherently_scaled_energy':
            for row in raw:
                row['energy_j'] = str(float(row['energy_j']) * 1.5)
                row['energy_kwh'] = str(float(row['energy_j']) / 3_600_000)
            for row in summary:
                for key in ('energy_mean_j', 'energy_sd_j'):
                    row[key] = str(float(row[key]) * 1.5)
        elif mutation == 'test_rejects_coherently_changed_horizon':
            for row in raw:
                row['horizon_s'] = str(float(row['horizon_s']) * 1.5)
        elif mutation == 'test_rejects_coherent_failure_and_sla_counts':
            for row in raw:
                row['completed_cloudlets'] = str(int(row['vm_count']) - 1)
                row['failed_cloudlets'] = '1'
                row['censored_cloudlets'] = '0'
                row['sla_violations'] = '1'
                row['sla_rate'] = str(1 / int(row['vm_count']))
            for row in summary:
                rates = [float(case['sla_rate']) for case in raw
                         if (case['scenario'], case['algorithm']) == (row['scenario'], row['algorithm'])]
                # The generated size is constant within each group: all forged rates equal.
                if len(set(rates)) != 1: raise AssertionError('Expected fixed VM count per summary group')
                row['sla_mean'] = str(rates[0]); row['sla_sd'] = '0.0'
        else:
            raise AssertionError('Unknown coherent mutation')
        for path, header, rows in ((raw_path, fields, raw), (summary_path, summary_fields, summary)):
            with path.open('w', newline='') as stream:
                writer = csv.DictWriter(stream, fieldnames=header, lineterminator='\n')
                writer.writeheader(); writer.writerows(rows)
        manifest_path = self.directory / 'run.json'
        manifest = json.loads(manifest_path.read_text())
        for name in manifest['files']:
            manifest['files'][name] = hashlib.sha256((self.directory / name).read_bytes()).hexdigest()
        manifest_path.write_text(json.dumps(manifest) + '\n')
        # Run outside the expected-failure methods so I/O/fixture/programming errors
        # remain errors. Only acceptance (no ValueError) reproduces a known gap.
        self.rejection = None
        try:
            with contextlib.redirect_stdout(io.StringIO()): validator.validate(self.directory)
        except ValueError as error:
            self.rejection = error

    @unittest.expectedFailure
    def test_rejects_coherently_scaled_energy(self):
        self.assertIsInstance(self.rejection, ValueError, 'FROZEN-METRICS-1: forged energy was accepted')

    @unittest.expectedFailure
    def test_rejects_coherently_changed_horizon(self):
        self.assertIsInstance(self.rejection, ValueError, 'FROZEN-METRICS-1: forged horizon was accepted')

    @unittest.expectedFailure
    def test_rejects_coherent_failure_and_sla_counts(self):
        self.assertIsInstance(self.rejection, ValueError, 'FROZEN-METRICS-1: forged completion/SLA was accepted')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path, help='Valid Explore run to copy; original is preserved')
    FrozenMetricGaps.source = parser.parse_args().directory.resolve()
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(FrozenMetricGaps)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    if result.wasSuccessful() and len(result.expectedFailures) == 3:
        print('REPRODUCED FROZEN-METRICS-1: 3 coherent metric forgeries accepted; this is not an acceptance PASS')
    raise SystemExit(0 if result.wasSuccessful() else 1)
