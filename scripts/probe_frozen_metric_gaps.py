"""Acceptance regressions for coherent frozen-metric corruption.

Supply a valid Explore run. The original must pass; all four coherently
rehashed metric forgeries must reject with their specific physical-model error.
Temporary copies preserve the original. These checks also run in Maven verify.
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


class FrozenMetricRegressionTests(unittest.TestCase):
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
        elif mutation in ('test_rejects_coherent_failure_and_sla_counts', 'test_rejects_coherent_sla_without_failure'):
            for row in raw:
                failed = int(mutation == 'test_rejects_coherent_failure_and_sla_counts')
                row['completed_cloudlets'] = str(int(row['vm_count']) - failed)
                row['failed_cloudlets'] = str(failed)
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
        # Fixture and unexpected programming/I/O errors remain test errors.
        self.rejection = None
        try:
            with contextlib.redirect_stdout(io.StringIO()): validator.validate(self.directory)
        except ValueError as error:
            self.rejection = error

    def test_rejects_coherently_scaled_energy(self):
        self.assertIsInstance(self.rejection, ValueError, 'FROZEN-METRICS-1: forged energy was accepted')
        self.assertIn('frozen energy', str(self.rejection))

    def test_rejects_coherently_changed_horizon(self):
        self.assertIsInstance(self.rejection, ValueError, 'FROZEN-METRICS-1: forged horizon was accepted')
        self.assertIn('frozen horizon', str(self.rejection))

    def test_rejects_coherent_failure_and_sla_counts(self):
        self.assertIsInstance(self.rejection, ValueError, 'FROZEN-METRICS-1: forged completion/SLA was accepted')
        self.assertIn('frozen completion/SLA', str(self.rejection))

    def test_rejects_coherent_sla_without_failure(self):
        self.assertIsInstance(self.rejection, ValueError, 'FROZEN-METRICS-1: forged SLA was accepted')
        self.assertIn('frozen completion/SLA', str(self.rejection))


def check_coherent_metrics(directory):
    FrozenMetricRegressionTests.source = Path(directory).resolve()
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(FrozenMetricRegressionTests)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    if not result.wasSuccessful():
        raise AssertionError('Coherent frozen-metric corruption regression failed')
    print('PASS: 4 rehashed coherent metric forgeries rejected with physical-model errors')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path, help='Valid Explore run to copy; original is preserved')
    args = parser.parse_args()
    try:
        check_coherent_metrics(args.directory)
    except AssertionError:
        raise SystemExit(1)
