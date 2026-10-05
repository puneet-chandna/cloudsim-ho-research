"""Independent, hand-checkable acceptance fixtures; no numerical dependencies."""
import unittest
import math
import contextlib
import csv
import hashlib
import io
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
sys.dont_write_bytecode = True
import statistics_validator as v


class Tape:
    def __init__(self, entries): self.entries = iter(entries)
    def boolean(self): return next(self.entries)
    def integer(self, bound): return next(self.entries)


class FrozenMetricsTest(unittest.TestCase):
    def test_independent_integral_accounts_for_multipe_work_idle_tails_and_unused_hosts(self):
        spec = {'inputs':{
            'hosts':[{'pes':4,'mipsPerPe':250,'idleW':100,'maxW':200},
                     {'pes':2,'mipsPerPe':1000,'idleW':200,'maxW':500},
                     {'pes':2,'mipsPerPe':1000,'idleW':999,'maxW':1999}],
            'vms':[{'pes':2,'mipsPerPe':100}, {'pes':1,'mipsPerPe':200}, {'pes':1,'mipsPerPe':500}]},
            'reference_seconds':[10,30,20]}
        # Host 0: 100*30 + .1*(200*10+200*30) = 3800 J.
        # Host 1: 200*30 + .15*(500*20) = 7500 J. Host 2 stays off.
        # Moving VM 1 to host 1 changes these to 3200 + 8400 J.
        for placement,energy in (([0,0,1],11300),([0,1,1],11600)):
            with self.subTest(placement=placement):
                self.assertEqual(v.frozen_metrics(spec,placement),(energy,30))


class StatisticsTest(unittest.TestCase):
    def test_ordered_binary64_mean_controls_strict_bootstrap_ties(self):
        self.assertEqual(v.mean([1e16,1,-1e16]),0.0)
    def test_java_random_known_sequence_and_signed_overflow_rejection(self):
        rng = v.JavaRandom(0)
        self.assertEqual([rng.integer(100) for _ in range(10)], [60,48,29,47,15,53,91,61,19,54])
        rng = v.JavaRandom(0)
        self.assertEqual([rng.boolean() for _ in range(10)], [True,True,False,True,True,False,True,False,True,True])
        rng.next = lambda bits, tape=iter([2147483647, 17]): next(tape)
        self.assertEqual(rng.integer(30), 17)

    def test_explicit_signs_and_undefined_resamples(self):
        signs = Tape([True]*3+[False]*3+[True,True,False]+[False,True,True])
        self.assertEqual(v.wild([-3,-2,-1], 0, signs, 4), .2)
        self.assertEqual(v.wild([-1,1], 0, Tape([True,False]*2), 2), 1)

    def test_bca_components_from_exact_resampling_table(self):
        b = v.bca([1,2,3], Tape([0,0,0,0,0,1,0,1,2,1,2,2,2,2,2]), 5)
        self.assertAlmostEqual(b['bias'], -.2533471031357997, places=12)
        self.assertEqual(b['acceleration'], 0)
        self.assertAlmostEqual(v.endpoint(b,.025),1.0090920315474858,places=12)
        self.assertEqual(v.quantile([1,2,4,8], .25),1.75)
        self.assertTrue(v.bca([0,0,0],v.JavaRandom(0),10)['reason'])
        self.assertEqual(v.wild([0,0,0],.01,v.JavaRandom(0),10),1)
        asymmetric = v.bca([0,0,1],Tape([0,0,0,2,2,2]),2)
        self.assertAlmostEqual(asymmetric['acceleration'],math.sqrt(6)/36,places=15)

    def test_holm_and_strict_margin(self):
        for actual,expected in zip(v.holm([.001,.002,.002]+[1]*15),[.018,.034,.034]):
            self.assertAlmostEqual(actual,expected,places=15)
        with self.assertRaises(ValueError): v.holm([.1])
        self.assertTrue(v.claim(.05,math.log(.95),.009,True))
        self.assertFalse(v.claim(.05,math.log(.95),.01,True))

    def test_holm_restores_original_order_clamps_and_rejects_nonprobabilities(self):
        # Sorted ranks are .001, .02, .04, then fifteen ones; factors 18,17,16.
        inputs = [.04,.001,.02] + [1.0]*15
        expected = [.64,.018,.34] + [1.0]*15
        for actual, wanted in zip(v.holm(inputs), expected):
            self.assertAlmostEqual(actual, wanted, places=15)
        for invalid in (-.001, 1.001, math.nan, math.inf, -math.inf):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                v.holm([invalid] + [.01]*17)

    def test_claim_neighboring_binary64_values_obey_all_three_thresholds(self):
        for energy, practical, ni in ((True,math.log(.95),.01), (False,-.01,math.log(1.05))):
            inside_ni = math.nextafter(ni, -math.inf)
            with self.subTest(energy=energy):
                self.assertTrue(v.claim(.05, practical, inside_ni, energy))
                self.assertFalse(v.claim(math.nextafter(.05, math.inf), practical, inside_ni, energy))
                self.assertFalse(v.claim(.05, math.nextafter(practical, math.inf), inside_ni, energy))
                self.assertFalse(v.claim(.05, practical, ni, energy))
                self.assertFalse(v.claim(.05, practical, math.nextafter(ni, math.inf), energy))

    def test_type_seven_quantile_endpoints_singleton_and_invalid_coordinates(self):
        for coordinate, expected in ((0,1),(.25,1.75),(.5,3),(.75,5),(1,8)):
            with self.subTest(coordinate=coordinate):
                self.assertEqual(v.quantile([1,2,4,8], coordinate), expected)
        self.assertEqual(v.quantile([7], .37), 7)
        for coordinate in (-.001, 1.001, math.nan, math.inf, -math.inf):
            with self.subTest(coordinate=coordinate), self.assertRaises(ValueError):
                v.quantile([1,2], coordinate)
        with self.assertRaises(ValueError): v.quantile([], .5)

    def test_bca_extreme_bias_and_underflow_report_specific_undefined_reasons(self):
        self.assertEqual(v.bca([1,2,3], Tape([0]*30), 10), {'reason':'BCA_BIAS_EXTREME'})
        self.assertEqual(v.bca([1,2,3], Tape([2]*30), 10), {'reason':'BCA_BIAS_EXTREME'})
        self.assertEqual(v.bca([1e-110,2e-110,3e-110], v.JavaRandom(1), 100),
                         {'reason':'BCA_JACKKNIFE_UNDEFINED'})
        self.assertIsNone(v.endpoint({'reason':'','sorted':[1,2,3],'bias':0,'acceleration':1}, .975))


def check_corruptions(source):
    """Every semantic mutation is rehashed: checks cannot pass on hashes alone."""
    variants = ['state','status','missing_row','duplicate_row','reorder','seed','conversion','sla',
                'placement','trace','canonical','analysis_value','analysis_order','analysis_missing','effective','alpha']
    for variant in variants:
        with tempfile.TemporaryDirectory(prefix='statistics-corrupt-') as tmp:
            directory = Path(tmp)/'run'
            shutil.copytree(source,directory)
            manifest = json.loads((directory/'run.json').read_text())
            if variant=='state': manifest['state']='RUNNING'
            elif variant=='status': manifest['cases'][0]['status']='RUN_ERROR'
            elif variant=='canonical': manifest['specifications'][0]['canonical_text']+='corrupt\n'
            elif variant=='effective': (directory/'effective.properties').unlink()
            elif variant=='alpha':
                manifest['effective_config']['analysis.alpha']='0.50'
                path=directory/'effective.properties'
                path.write_text(path.read_text().replace('analysis.alpha=0.05\n','analysis.alpha=0.50\n'))
            elif variant=='analysis_missing': (directory/'analysis/report.md').unlink()
            else:
                name = 'raw/placements.csv' if variant=='placement' else 'raw/optimizer_trace.csv' if variant=='trace' else 'analysis/scenario_summary.csv' if variant.startswith('analysis_') else 'raw/main_results.csv'
                path=directory/name
                with path.open(newline='') as file: reader=csv.DictReader(file); fields=reader.fieldnames; rows=list(reader)
                if variant=='missing_row': rows.pop()
                elif variant=='duplicate_row': rows[1]=rows[0]
                elif variant in ('reorder','analysis_order'): rows[0],rows[1]=rows[1],rows[0]
                else:
                    column,value = {'seed':('workload_seed','1'),'conversion':('energy_kwh','999'),
                        'sla':('sla_rate','0.5'),'placement':('vm_id','1'),'trace':('stage','predator'),
                        'analysis_value':('energy_mean_j','123')}[variant]
                    rows[0][column]=value
                with path.open('w',newline='') as file:
                    writer=csv.DictWriter(file,fieldnames=fields,lineterminator='\n'); writer.writeheader(); writer.writerows(rows)
            for name in manifest['files']:
                path=directory/name
                if path.is_file(): manifest['files'][name]=hashlib.sha256(path.read_bytes()).hexdigest()
            (directory/'run.json').write_text(json.dumps(manifest))
            try:
                with contextlib.redirect_stdout(io.StringIO()): v.validate(directory)
            except (ValueError,OSError,StopIteration): pass
            else: raise AssertionError('Validator accepted corruption: '+variant)
    print(f'PASS: {len(variants)} rehashed raw/analysis/configuration corruption variants rejected')
    check_metadata(source)
    from probe_frozen_metric_gaps import check_coherent_metrics
    check_coherent_metrics(source)


def check_metadata(source):
    mutations = [
        ('started_at','not-a-timestamp'), ('finished_at','not-a-timestamp'),
        ('started_at','2026-02-30T01:00:00Z'), ('finished_at','2020-01-01T00:00:00Z'),
        ('started_at','2026-09-20T01:00:00'), ('started_at','2026-09-20T01:00:00+05:30'),
        ('artifact_sha256','not-a-hash'), ('artifact_sha256',None),
        ('git_revision','unknown'), ('git_dirty','not-a-boolean'), ('git_dirty',1),
        ('java_version',None), ('java_version','25.0.1'), ('cloudsim_version','0.0.0'),
        ('max_heap_bytes',-1), ('max_heap_bytes',1.5), ('max_heap_bytes',True),
        ('cpu',None), ('cpu',{'available_processors':0,'model':'processor'}),
        ('cpu',{'available_processors':True,'model':'processor'}),
        ('cpu',{'available_processors':2,'model':None}), ('os',None), ('arch',''),
        ('source_version',None), ('schema_version',2.0), ('expected_cases',40.0),
        ('cases',None), ('files',[]), ('effective_config',[]), ('run_id',None),
    ]
    failures=[]
    with tempfile.TemporaryDirectory(prefix='statistics-metadata-') as tmp:
        directory=Path(tmp)/'run'
        shutil.copytree(source,directory)
        path=directory/'run.json'; original=json.loads(path.read_text())
        if original['source_version']=='development (unpackaged)':
            # Maven's caller is unpackaged. These local test values exercise format validation,
            # not authenticity; the --metadata packaged-dataset check uses its actual provenance.
            original.update(source_version='metadata-test-fixture',artifact_sha256='0'*64,git_revision='0'*40)
        def invoke(manifest):
            path.write_text(json.dumps(manifest))
            return subprocess.run([sys.executable,str(Path(v.__file__)),str(directory)],capture_output=True,text=True)
        for field,value in mutations:
            result=invoke(dict(original,**{field:value}))
            if result.returncode!=1 or 'INVALID:' not in result.stderr or 'Traceback' in result.stderr:
                failures.append(f'{field}={value!r}: exit={result.returncode}')
        # Fractional-second order must not be truncated to Python datetime microseconds.
        result=invoke(dict(original,started_at='2026-09-20T00:00:00.000000002Z',finished_at='2026-09-20T00:00:00.000000001Z'))
        if result.returncode!=1 or 'Traceback' in result.stderr: failures.append('reversed nanosecond timestamps')
        assert not failures, 'Malformed provenance accepted or unclean rejection:\n'+'\n'.join(failures)
        for dirty in (False,True):
            result=invoke(dict(original,git_dirty=dirty))
            assert result.returncode==0 and 'provenance=packaged-recorded' in result.stdout, result.stdout+result.stderr
        result=invoke(dict(original,source_version='development (unpackaged)',artifact_sha256=None,git_revision='unknown',git_dirty=True))
        assert result.returncode==0 and 'provenance=unpackaged-diagnostic' in result.stdout, result.stdout+result.stderr
    print(f'PASS: {len(mutations)+1} malformed provenance cases reject cleanly; packaged clean/dirty and explicit unpackaged diagnostics accepted distinctly')


def check_fixture(path):
    fixture = json.loads(path.read_text())
    for record in fixture['records']:
        name,margin = record['metric'],record['margin']
        bs = v.seed(123456,'analysis','Micro',0,'bca',f'GA:{name}:{margin}')
        ws = v.seed(123456,'analysis','Micro',0,'wild',f'GA:{name}:{margin}')
        assert bs==record['bca_seed'] and ws==record['wild_seed']
        b = v.bca(fixture['x'],v.JavaRandom(bs),10000)
        v.close(record['bias'],b['bias'],'fixture bias')
        v.close(record['acceleration'],b['acceleration'],'fixture acceleration')
        for key,alpha in [('low',.025),('high',.975),('upper',.95)]: v.close(record[key],v.endpoint(b,alpha),'fixture '+key)
        margin_value = 0 if margin=='0' else math.log(1.05) if margin=='ln1.05' else .01
        v.close(record['p'],v.wild(fixture['x'],margin_value,v.JavaRandom(ws),10000),'fixture wild',True)
    with tempfile.TemporaryDirectory(prefix='statistics-fixture-') as tmp:
        directory=Path(tmp)
        (directory/'analysis').mkdir()
        for name,data in fixture['analysis'].items(): (directory/name).write_text(data)
        expected=v.analysis_rows('research',123456,'synthetic-test-only',fixture['raw'])
        v.compare_analysis(directory,expected)
    print('PASS: independent Java/Python seeded BCa, wild and all 450 synthetic analysis inputs agree')


if __name__ == '__main__':
    if len(sys.argv)>1 and sys.argv[1]=='--dataset': check_corruptions(Path(sys.argv[2]))
    elif len(sys.argv)>1 and sys.argv[1]=='--metadata': check_metadata(Path(sys.argv[2]))
    elif len(sys.argv)>1 and sys.argv[1]=='--fixture': check_fixture(Path(sys.argv[2]))
    else: unittest.main()
