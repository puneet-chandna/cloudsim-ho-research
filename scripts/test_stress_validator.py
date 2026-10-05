import csv
import hashlib
import json
from pathlib import Path
import tempfile
import tracemalloc
import unittest

import stress_validator as validator

ID='stress_schema_version,run_id,phase,scenario,replication,algorithm,population,iterations'
CASE=ID+',scenario_seed,workload_seed,optimizer_seed,scenario_sha256,status,error_code,error_message,vm_count,host_count,evaluations,objective_w,energy_j,energy_kwh,sla_violations,sla_rate,completed_cloudlets,failed_cloudlets,censored_cloudlets,horizon_s,release_s,allocation_wall_ns'
PLACE=ID+',vm_id,host_id'
TRACE=ID+',evaluation,iteration,stage,candidate_index,feasible,fitness_w,accepted,best_fitness_w'
SUMMARY='stress_schema_version,run_id,phase,scenario,algorithm,n,objective_mean_w,energy_mean_j,sla_mean,allocation_mean_ns'
ALGORITHMS=['HO','GA','FirstFit','BestFit']


class StressValidatorTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.directory=Path(self.temp.name)/'stress-fixture'
        self.fixture()

    def write_csv(self,path,header,rows):
        file=self.directory/path; file.parent.mkdir(parents=True,exist_ok=True)
        with file.open('w',newline='') as f:
            writer=csv.writer(f,lineterminator='\n'); writer.writerow(header.split(',')); writer.writerows(rows)

    def rehash(self):
        m=json.loads((self.directory/'run.json').read_text())
        m['files']={str(p.relative_to(self.directory)):hashlib.sha256(p.read_bytes()).hexdigest()
                    for p in sorted(self.directory.rglob('*')) if p.is_file() and p.name not in ('run.json','progress.json')}
        (self.directory/'run.json').write_text(json.dumps(m)+'\n')

    def fixture(self,phase='stress'):
        self.directory.mkdir(exist_ok=True)
        calibration=phase=='stress_calibration'
        ss,ws,os=( (-7490455756080796740,1847119552629817066,[5801718139938849624,-4168991084096517727]) if calibration
                   else (7059302582120722517,6523083832771500299,[3326694158380340225,-5894428481873417686]))
        pes,mips,ram,duration=(1,2000,2048,60) if calibration else (2,1000,1024,76)
        host=dict(id=0,pes=16,mipsPerPe=3000,ramMiB=32768,bwMbps=10000,storageMiB=1000000,idleW=175,maxW=250)
        vm=dict(id=0,pes=pes,mipsPerPe=mips,ramMiB=ram,bwMbps=1000,storageMiB=10000)
        cloudlet=dict(id=0,vmId=0,pes=pes,lengthMi=mips*duration)
        canonical=(f'kind,id,pes,mips_per_pe,ram_mib,bw_mbps,storage_mib,idle_w,max_w,vm_id,length_mi\n'
                   f'host,0,16,3000,32768,10000,1000000,175,250,,\n'
                   f'vm,0,{pes},{mips},{ram},1000,10000,,,,\n'
                   f'cloudlet,0,{pes},,,,,,,0,{mips*duration}\nkey,value\n'
                   f'censor_s,{duration*10}.0\ncloudlet_scheduler,CloudletSchedulerSpaceShared\n'
                   f'host_scheduler,VmSchedulerSpaceShared\nmin_event_interval_s,0.125\npower_curve,linear\n'
                   f'power_policy,used_on_until_horizon\nreference.0,{duration}.0\nrelease_s,0.0\nutilization_model,constant_full\n')
        fingerprint=hashlib.sha256(canonical.encode()).hexdigest()
        spec=dict(inputs=dict(seeds=dict(master=123456,phase=phase,scenario='Static-V1-H1',replication=0,scenarioSeed=ss,workloadSeed=ws),
                              hosts=[host],vms=[vm],cloudlets=[cloudlet]),reference_seconds=[float(duration)],censor_s=float(duration*10),canonical_text=canonical,scenario_sha256=fingerprint)
        (self.directory/'scenarios').mkdir(exist_ok=True)
        (self.directory/'scenarios/replication-0.json').write_text(json.dumps(spec)+'\n')
        config={'profile':'stress','experiment.kind':'static_stress','stress.schema.version':'1','experiment.phase':phase,'vm.count':'1','host.count':'1',
                'population':'2','iterations':'1','replications':'1','master.seed':'123456','evaluation.budget':'8','expected.cases':'4',
                'scenarios':'Static-V1-H1','algorithms':'HO,GA,FirstFit,BestFit'}
        (self.directory/'effective.properties').write_text(''.join(f'{k}={config[k]}\n' for k in sorted(config)))
        cases=[];places=[];traces=[];summaries=[]
        for index,algorithm in enumerate(ALGORITHMS):
            identity=[1,'stress-fixture',phase,'Static-V1-H1',0,algorithm,2 if index<2 else '',1 if index<2 else '']
            cases.append(identity+[ss,ws,os[index] if index<2 else '',fingerprint,'RUN_OK','','',1,1,8 if index<2 else 1,178.125,178.125*duration,178.125*duration/3600000,0,0,1,0,0,duration,.125,100])
            places.append(identity+[0,0])
            if index<2:
                sequence=[(0,'INITIAL',0),(0,'INITIAL',1)]
                sequence+=([(1,'RIVER_MALE',0),(1,'RIVER_FEMALE',0),(1,'PREDATOR',1),(1,'DEFENSE',1),(1,'ESCAPE',0),(1,'ESCAPE',1)] if index==0
                           else [(generation,'CHILD',1) for generation in range(1,7)])
                for evaluation,(iteration,stage,member) in enumerate(sequence,1):
                    traces.append(identity+[evaluation,iteration,stage,member,'true',178.125,'true' if stage in ('INITIAL','CHILD') else 'false',178.125])
            summaries.append([1,'stress-fixture',phase,'Static-V1-H1',algorithm,1,178.125,178.125*duration,0,100])
        self.write_csv('raw/cases.csv',CASE,cases); self.write_csv('raw/placements.csv',PLACE,places)
        self.write_csv('raw/evaluations.csv',TRACE,traces); self.write_csv('analysis/summary.csv',SUMMARY,summaries)
        counters=dict(expected_cases=4,attempted_cases=4,successful_cases=4,failed_cases=0,unattempted_cases=0,expected_evaluations=18,completed_evaluations=18)
        m=dict(experiment_kind='static_stress',stress_schema_version=1,profile='stress',experiment_phase=phase,run_id='stress-fixture',state='COMPLETE',
               started_at='2026-10-01T00:00:00Z',finished_at='2026-10-01T00:00:01Z',master_seed=123456,effective_config=config,error=None,
               source_version='development (unpackaged)',artifact_sha256=None,git_revision='unknown',git_dirty=True,java_version='21',cloudsim_version='8.5.7',
               os='Linux',arch='amd64',max_heap_bytes=33554432,evidence_limit=validator.EVIDENCE_LIMIT,allocation_timing=validator.ALLOCATION_TIMING,**counters)
        (self.directory/'run.json').write_text(json.dumps(m)+'\n')
        progress={k:m[k] for k in ['experiment_kind','stress_schema_version','run_id','state','error']+list(counters)}; progress['current_case']=None
        (self.directory/'progress.json').write_text(json.dumps(progress)+'\n'); self.rehash()

    def mutate_csv(self,path,mutator):
        file=self.directory/path
        with file.open(newline='') as f: rows=list(csv.reader(f))
        mutator(rows)
        with file.open('w',newline='') as f: csv.writer(f,lineterminator='\n').writerows(rows)
        self.rehash()

    def test_contract_accepts_production_and_separate_calibration(self):
        validator.validate(self.directory)
        self.fixture('stress_calibration');validator.validate(self.directory)

    def test_rehashed_missing_duplicate_reordered_and_truncated_evidence_is_rejected(self):
        for path in ['raw/cases.csv','raw/placements.csv','raw/evaluations.csv','analysis/summary.csv']:
            for mutation in [lambda rows:rows.pop(),lambda rows:rows.append(rows[1]),lambda rows:rows.__setitem__(slice(1,3),rows[1:3][::-1])]:
                self.fixture(); self.mutate_csv(path,mutation)
                with self.subTest(path=path),self.assertRaises(ValueError): validator.validate(self.directory)
        self.fixture();path=self.directory/'raw/evaluations.csv';path.write_bytes(path.read_bytes()[:-1]);self.rehash()
        with self.assertRaises(ValueError):validator.validate(self.directory)

    def test_rehashed_final_metrics_objective_and_feasibility_are_independently_checked(self):
        for column,value in [('energy_j','9999'),('energy_kwh','0.001'),('horizon_s','70'),('release_s','0'),('objective_w','175'),('sla_violations','1'),('sla_rate','1'),('completed_cloudlets','0'),('allocation_wall_ns','NaN')]:
            self.fixture(); self.mutate_csv('raw/cases.csv',lambda rows:rows[1].__setitem__(rows[0].index(column),value))
            with self.subTest(column=column),self.assertRaises(ValueError):validator.validate(self.directory)
        self.fixture();self.mutate_csv('raw/placements.csv',lambda rows:rows[1].__setitem__(-1,'1'))
        with self.assertRaises(ValueError):validator.validate(self.directory)

    def test_rehashed_seed_spec_config_and_phase_splicing_are_rejected(self):
        for column,value in [('scenario_seed','1'),('workload_seed','1'),('optimizer_seed','1'),('scenario_sha256','0'*64),('phase','stress_calibration'),('evaluations','9')]:
            self.fixture();self.mutate_csv('raw/cases.csv',lambda rows:rows[1].__setitem__(rows[0].index(column),value))
            with self.subTest(column=column),self.assertRaises(ValueError):validator.validate(self.directory)
        for key,value in [('canonical_text','tampered'),('reference_seconds',[75.0]),('censor_s',750.0)]:
            self.fixture(); path=self.directory/'scenarios/replication-0.json';spec=json.loads(path.read_text());spec[key]=value;path.write_text(json.dumps(spec));self.rehash()
            with self.subTest(key=key),self.assertRaises(ValueError):validator.validate(self.directory)
        self.fixture(); path=self.directory/'effective.properties';path.write_text(path.read_text().replace('population=2','population=4'));self.rehash()
        with self.assertRaises(ValueError):validator.validate(self.directory)

    def test_scalar_budget_order_eligibility_and_best_chain_are_checked(self):
        for column,value in [('evaluation','2'),('iteration','1'),('stage','CHILD'),('candidate_index','1'),('feasible','false'),('fitness_w','NaN'),('best_fitness_w','100')]:
            self.fixture();self.mutate_csv('raw/evaluations.csv',lambda rows:rows[1].__setitem__(rows[0].index(column),value))
            with self.subTest(column=column),self.assertRaises(ValueError):validator.validate(self.directory)
        self.fixture();self.mutate_csv('raw/evaluations.csv',lambda rows:rows[5].__setitem__(rows[0].index('accepted'),'true'))
        with self.assertRaises(ValueError):validator.validate(self.directory)

    def test_accepted_infeasible_initial_entry_is_permitted_and_compact_limit_is_explicit(self):
        def mutation(rows):
            for index in [1,9]:
                for name,value in [('feasible','false'),('fitness_w',''),('best_fitness_w','')]:rows[index][rows[0].index(name)]=value
            rows[3][rows[0].index('accepted')]='true' # First feasible proposal replaces HO's infeasible slot.
        self.mutate_csv('raw/evaluations.csv',mutation); validator.validate(self.directory)
        self.assertIn('discarded',validator.EVIDENCE_LIMIT)

    def test_incomplete_runs_and_schema_two_are_never_accepted(self):
        for mutation in [dict(state='FAILED'),dict(stress_schema_version=2),dict(schema_version=2),dict(experiment_phase='main')]:
            self.fixture();path=self.directory/'run.json';m=json.loads(path.read_text());m.update(mutation);path.write_text(json.dumps(m))
            with self.assertRaises(ValueError):validator.validate(self.directory)

    def test_incremental_csv_reader_memory_is_independent_of_total_rows(self):
        path=self.directory/'many.csv'
        with path.open('w') as f:
            f.write('value\n'); f.writelines('123\n' for _ in range(200000))
        tracemalloc.start()
        self.assertEqual(200000,sum(1 for _ in validator.rows(path,'value')))
        _,peak=tracemalloc.get_traced_memory();tracemalloc.stop()
        self.assertLess(peak,300000)

    def test_full_validator_keeps_a_bounded_peak_for_sixty_thousand_scalar_rows(self):
        iterations=5000;budget=30002
        m=json.loads((self.directory/'run.json').read_text())
        m['effective_config'].update({'iterations':str(iterations),'evaluation.budget':str(budget)})
        m['expected_evaluations']=m['completed_evaluations']=60006
        (self.directory/'run.json').write_text(json.dumps(m))
        progress=json.loads((self.directory/'progress.json').read_text());progress.update(expected_evaluations=60006,completed_evaluations=60006)
        (self.directory/'progress.json').write_text(json.dumps(progress))
        (self.directory/'effective.properties').write_text(''.join(f'{key}={value}\n' for key,value in sorted(m['effective_config'].items())))
        def case_budget(rows):
            for row in rows[1:3]:row[7]=str(iterations);row[rows[0].index('evaluations')]=str(budget)
        self.mutate_csv('raw/cases.csv',case_budget)
        self.mutate_csv('raw/placements.csv',lambda rows:[row.__setitem__(7,str(iterations)) for row in rows[1:3]])
        with (self.directory/'raw/evaluations.csv').open('w',newline='') as file:
            writer=csv.writer(file,lineterminator='\n');writer.writerow(TRACE.split(','))
            for algorithm in ['HO','GA']:
                identity=[1,'stress-fixture','stress','Static-V1-H1',0,algorithm,2,iterations]
                for evaluation,(iteration,stage,member) in enumerate(validator.trace_order(algorithm,2,iterations),1):
                    writer.writerow(identity+[evaluation,iteration,stage,member,'true',178.125,'true' if stage in ('INITIAL','CHILD') else 'false',178.125])
        self.rehash();tracemalloc.start()
        validator.validate(self.directory)
        _,peak=tracemalloc.get_traced_memory();tracemalloc.stop()
        self.assertLess(peak,2500000) # Includes two 1MiB buffers while incrementally hashing.


if __name__=='__main__': unittest.main()
