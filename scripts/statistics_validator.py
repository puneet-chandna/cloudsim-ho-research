#!/usr/bin/env python3
"""Independent schema-2 raw/analysis validator. Python stdlib only.

Usage: python3 scripts/statistics_validator.py RUN_DIRECTORY
Numerical comparisons: abs=1e-10, rel=2e-12 (p-values abs=1e-12).
Identifiers, order, counts, eligibility and decisions must match exactly.
The report is hash-checked, but no reported decision is trusted as evidence.
Packaged provenance is format-checked recorded metadata, not proof of artifact
authenticity. Null artifact hashes are accepted only with the writer's explicit
development (unpackaged) marker and are labeled unpackaged diagnostics.
"""
import argparse
import csv
from datetime import datetime
import hashlib
import io
import json
import math
from pathlib import Path
import re
from statistics import NormalDist
import struct
import sys

ALGORITHMS = ['HO', 'GA', 'FirstFit', 'BestFit']
PROFILES = {'smoke': (10,4,1,['Micro']), 'explore': (20,20,5,['Micro','Small']),
            'research': (30,40,30,['Micro','Small','Medium'])}
SETTINGS = [(n,40) for n in (10,20,30,40,50)] + [(30,t) for t in (20,30,50,60)]
KEY = 'phase,scenario,replication,algorithm,population,iterations'.split(',')
IDENTITY = ['schema_version','run_id'] + KEY
RAW = 'schema_version,run_id,phase,scenario,replication,algorithm,population,iterations,scenario_seed,workload_seed,optimizer_seed,scenario_sha256,status,error_code,error_message,vm_count,host_count,evaluations,objective_w,energy_j,energy_kwh,sla_violations,sla_rate,completed_cloudlets,failed_cloudlets,censored_cloudlets,horizon_s,allocation_wall_ns'
PLACEMENTS = ','.join(IDENTITY)+',vm_id,host_id'
TRACE = ','.join(IDENTITY)+',evaluation,iteration,stage,candidate_index,feasible,fitness_w,best_fitness_w'
SUMMARY = 'schema_version,run_id,scenario,algorithm,n,energy_mean_j,energy_sd_j,sla_mean,sla_sd,allocation_mean_ns,allocation_sd_ns'
PRIMARY = 'schema_version,run_id,scenario,baseline,n,claim,mean_log_energy_ratio,mean_sla_difference,energy_ci_low,energy_ci_high,sla_ci_low,sla_ci_high,energy_ni_upper,sla_ni_upper,p_superiority,p_noninferiority,p_composite,p_holm,eligible,decision,reason'
RUNTIME = 'schema_version,run_id,scenario,baseline,n,mean_log_runtime_ratio,geometric_runtime_ratio,magnitude_at_least_10_percent,reason'
SENSITIVITY = 'schema_version,run_id,scenario,population,iterations,n,energy_mean_j,energy_sd_j,sla_mean,sla_sd,allocation_mean_ns,allocation_sd_ns'
NORMAL = NormalDist()
FROZEN = dict(line.split('=',1) for line in '''schema.version=2
protocol.version=2
algorithms=HO,GA,FirstFit,BestFit
scenario.Micro=10,3
scenario.Small=50,10
scenario.Medium=100,20
host.pes=16
host.mips.per.pe=3000
host.ram.mib=32768
host.bw.mbps=10000
host.storage.mib=1000000
host.power.idle.w=175,210,280
host.power.max.w=250,300,400
vm.pes=1,2
vm.mips.per.pe=1000,2000,3000
vm.ram.mib=1024,2048,4096
vm.bw.mbps=1000
vm.storage.mib=10000
cloudlet.duration.seconds=60,120
cloudlet.utilization=1.0
cloudlet.release.seconds=0.0
cloudlet.scheduler=CloudletSchedulerSpaceShared
host.scheduler=VmSchedulerSpaceShared
simulation.min.event.interval.seconds=0.125
rng=java.util.Random
seed.derivation=SHA-256-length-prefixed-UTF-8-first-8-bytes-big-endian
power.policy=used_on_until_horizon
power.curve=linear
censor.reference.multiplier=10
sla.slowdown.threshold=1.10
ga.tournament.size=3
ga.crossover.probability=0.8
ga.mutation.probability=1/V
ga.elites=1
ho.levy.beta=1.5
ho.levy.scale=0.05
ho.defense.d=2,3
ho.defense.f=2,4
ho.defense.c=1,1.5
ho.defense.g=-1,1
ho.zero.distance=Double.MIN_NORMAL
sensitivity.replications=10
sensitivity.populations=10,20,30,40,50
sensitivity.iterations=20,30,40,50,60
sensitivity.shared.setting=30,40
analysis.wild.resamples=1000000
analysis.bca.resamples=10000
analysis.alpha=0.05
analysis.holm.family.size=18
analysis.energy.noninferiority=ln1.05
analysis.sla.noninferiority=0.01
analysis.energy.practical.upper=ln0.95
analysis.sla.practical.upper=-0.01
analysis.runtime.magnitude=0.10
analysis.quantile=type7
analysis.ci.two.sided=0.025,0.975
analysis.ci.one.sided=0.95'''.splitlines())


def require(condition, message):
    if not condition: raise ValueError(message)


def seed(master, phase, scenario, replication, component, algorithm=None):
    fields = ['2',str(master),phase,scenario,str(replication),component]
    if algorithm is not None: fields.append(algorithm)
    digest = hashlib.sha256()
    for field in fields:
        data = field.encode('utf-8')
        digest.update(struct.pack('>I',len(data)))
        digest.update(data)
    return int.from_bytes(digest.digest()[:8], 'big', signed=True)


class JavaRandom:
    """java.util.Random's specified 48-bit LCG; no platform/random-module RNG."""
    def __init__(self, value): self.state = (value ^ 0x5DEECE66D) & ((1 << 48)-1)
    def next(self, bits):
        self.state = (self.state * 0x5DEECE66D + 11) & ((1 << 48)-1)
        return self.state >> (48-bits)
    def boolean(self): return self.next(1) != 0
    def integer(self, bound):
        require(0 < bound <= 2147483647, 'Invalid Random bound')
        if bound & (bound-1) == 0: return bound*self.next(31) >> 31
        while True:
            bits = self.next(31)
            value = bits % bound
            # Java rejects when this expression overflows signed int to negative.
            if bits-value+bound-1 < 1 << 31: return value


def ordered_sum(x):
    # Python 3.12+ sum compensates floats; strict BCa ties require Java's specified order.
    result = 0.0
    for value in x: result += value
    return result


def mean(x): return ordered_sum(x)/len(x)


def sd(x):
    if len(x)<2: return None
    if all(v == x[0] for v in x): return 0.0
    center = mean(x)
    return math.sqrt(ordered_sum((v-center)*(v-center) for v in x)/(len(x)-1))


def statistic(x, margin):
    scale = sd(x)
    if scale is None or scale == 0: return None
    result = (mean(x)-margin)/(scale/math.sqrt(len(x)))
    return result if math.isfinite(result) else None


def wild(x, margin, rng, draws=1_000_000):
    observed = statistic(x,margin)
    if observed is None: return 1.0
    residuals = [v-mean(x) for v in x]
    count = 0
    for _ in range(draws):
        sample = [v if rng.boolean() else -v for v in residuals]
        t = statistic(sample,0)
        if t is None or t <= observed: count += 1
    return (1+count)/(draws+1)


def quantile(ordered, q):
    require(ordered and math.isfinite(q) and 0 <= q <= 1, 'Invalid type-7 quantile')
    h = (len(ordered)-1)*q
    lo,hi = math.floor(h),math.ceil(h)
    return ordered[lo]+(h-lo)*(ordered[hi]-ordered[lo])


def bca(x, rng, draws=10_000):
    if len(x)<2 or not sd(x): return {'reason':'ZERO_VARIANCE'}
    theta = mean(x)
    distribution = [mean([x[rng.integer(len(x))] for _ in x]) for _ in range(draws)]
    below = sum(v < theta for v in distribution)
    if below in (0,draws): return {'reason':'BCA_BIAS_EXTREME'}
    jack = [mean(x[:i]+x[i+1:]) for i in range(len(x))]
    center = mean(jack)
    d = [center-v for v in jack]
    denominator = 6*ordered_sum(v*v for v in d)**1.5
    if not denominator or not math.isfinite(denominator): return {'reason':'BCA_JACKKNIFE_UNDEFINED'}
    return {'reason':'', 'sorted':sorted(distribution), 'bias':NORMAL.inv_cdf(below/draws),
            'acceleration':ordered_sum(v*v*v for v in d)/denominator}


def endpoint(b, alpha):
    if b['reason']: return None
    bias,accel = b['bias'],b['acceleration']
    z = NORMAL.inv_cdf(alpha)
    denominator = 1-accel*(bias+z)
    if denominator <= 0 or not math.isfinite(denominator): return None
    q = NORMAL.cdf(bias+(bias+z)/denominator)
    return quantile(b['sorted'],q) if math.isfinite(q) and 0 <= q <= 1 else None


def holm(p):
    require(len(p)==18 and all(math.isfinite(v) and 0<=v<=1 for v in p),'Required family of 18 finite probabilities')
    result = [0.0]*18
    previous = 0
    for rank,index in enumerate(sorted(range(18),key=lambda i:(p[i],i))):
        previous = min(1.0,max(previous,(18-rank)*p[index]))
        result[index] = previous
    return result


def claim(p, practical, ni, energy):
    return p<=.05 and practical<=(math.log(.95) if energy else -.01) and ni<(.01 if energy else math.log(1.05))


def metric(x, master, scenario, baseline, name):
    require(len(x)==30 and all(math.isfinite(v) for v in x),'Requires 30 finite paired differences')
    margin = 'ln1.05' if name=='energy' else '0.01'
    streams = [JavaRandom(seed(master,'analysis',scenario,0,'bca',f'{baseline}:{name}:{m}')) for m in ('0',margin)]
    two,one = [bca(x,rng) for rng in streams]
    low,high,upper = endpoint(two,.025),endpoint(two,.975),endpoint(one,.95)
    reason = two['reason'] or one['reason'] or ('BCA_QUANTILE_UNDEFINED' if None in (low,high,upper) else '')
    if not reason and (statistic(x,0) is None or statistic(x, math.log(1.05) if name=='energy' else .01) is None):
        reason = 'STUDENTIZATION_UNDEFINED'
    return {'mean':mean(x),'low':low,'high':high,'ni':upper,'reason':reason}


def matrix(profile):
    n,t,reps,scenarios = PROFILES[profile]
    keys = [('main',s,r,a,n if a in ALGORITHMS[:2] else None,t if a in ALGORITHMS[:2] else None)
            for s in scenarios for r in range(reps) for a in ALGORITHMS]
    if profile=='research': keys += [('sensitivity','Small',r,'HO',n,t) for r in range(10) for n,t in SETTINGS]
    return keys


def text(value):
    if value is None: return ''
    if isinstance(value,bool): return str(value).lower()
    return str(value)


def finite(value):
    result = float(value)
    require(math.isfinite(result),'Nonfinite number')
    return result


def close(actual, expected, label, probability=False):
    require(math.isclose(finite(actual),finite(expected),rel_tol=2e-12,abs_tol=1e-12 if probability else 1e-10),f'Numeric mismatch {label}: {actual} vs {expected}')


def read_csv(path, header):
    raw = path.read_bytes()
    require(b'\r' not in raw and raw.endswith(b'\n'),'CSV must use LF and final newline')
    reader = csv.DictReader(io.StringIO(raw.decode('utf-8')),strict=True)
    require(reader.fieldnames==header.split(','),f'Wrong CSV schema: {path.name}')
    rows = list(reader)
    require(all(None not in r and None not in r.values() for r in rows),f'Ragged CSV: {path.name}')
    return rows


def generated_spec(master, phase, scenario, replication):
    count = {'Micro':10,'Small':50,'Medium':100}[scenario]
    hosts = []
    for i in range(3 if count==10 else count//5):
        hosts.append(dict(id=i,pes=16,mipsPerPe=3000,ramMiB=32768,bwMbps=10000,storageMiB=1000000,idleW=[175,210,280][i%3],maxW=[250,300,400][i%3]))
    ss,ws = seed(master,phase,scenario,replication,'scenario'),seed(master,phase,scenario,replication,'workload')
    a,b = JavaRandom(ss),JavaRandom(ws)
    vms,cloudlets,refs = [],[],[]
    for i in range(count):
        pes,mips,ram = 1+a.integer(2),1000*(1+a.integer(3)),1024 << a.integer(3)
        duration = 60+b.integer(61)
        vms.append(dict(id=i,pes=pes,mipsPerPe=mips,ramMiB=ram,bwMbps=1000,storageMiB=10000))
        cloudlets.append(dict(id=i,vmId=i,pes=pes,lengthMi=mips*duration))
        refs.append(float(duration))
    inputs = {'seeds':dict(master=master,phase=phase,scenario=scenario,replication=replication,scenarioSeed=ss,workloadSeed=ws),
              'hosts':hosts,'vms':vms,'cloudlets':cloudlets}
    lines = ['kind,id,pes,mips_per_pe,ram_mib,bw_mbps,storage_mib,idle_w,max_w,vm_id,length_mi']
    for h in hosts: lines.append(','.join(map(str,['host',h['id'],16,3000,32768,10000,1000000,h['idleW'],h['maxW'],'',''])))
    for v in vms: lines.append(','.join(map(str,['vm',v['id'],v['pes'],v['mipsPerPe'],v['ramMiB'],1000,10000,'','','',''])))
    for c in cloudlets: lines.append(','.join(map(str,['cloudlet',c['id'],c['pes'],'','','','','','',c['vmId'],c['lengthMi']])))
    keys = dict(cloudlet_scheduler='CloudletSchedulerSpaceShared',host_scheduler='VmSchedulerSpaceShared',min_event_interval_s='0.125',power_policy='used_on_until_horizon',power_curve='linear',release_s='0.0',utilization_model='constant_full',censor_s=str(10*max(refs)))
    keys.update({f'reference.{i}':str(v) for i,v in enumerate(refs)})
    canonical = '\n'.join(lines+['key,value']+[f'{k},{keys[k]}' for k in sorted(keys)])+'\n'
    return dict(inputs=inputs,reference_seconds=refs,censor_s=10*max(refs),canonical_text=canonical,scenario_sha256=hashlib.sha256(canonical.encode()).hexdigest())


def check_identity(row, key, run):
    require([row[f] for f in IDENTITY] == ['2',run]+list(map(text,key)), 'Duplicate, missing, reordered or wrong case identity')


def frozen_metrics(spec, placement):
    """Independent integral for a feasible, strictly reserved frozen workload.

    Each VM starts its single full-utilization cloudlet at the common release,
    runs for its reference duration, then draws no CPU power. Used hosts retain
    idle power until the longest cloudlet finishes; unused hosts stay off.
    validate_raw verifies canonical inputs and feasibility before calling this.
    """
    hosts = spec['inputs']['hosts']
    horizon = max(spec['reference_seconds'])
    work = [0.0]*len(hosts)
    for vm,host,duration in zip(spec['inputs']['vms'],placement,spec['reference_seconds']):
        work[host] += vm['pes']*vm['mipsPerPe']*duration
    energy = sum(host['idleW']*horizon+(host['maxW']-host['idleW'])*integral/(host['pes']*host['mipsPerPe'])
                 for host,integral in zip(hosts,work) if integral)
    return energy,horizon


def validate_raw(directory, manifest):
    profile,master,run = manifest['profile'],manifest['master_seed'],manifest['run_id']
    keys = matrix(profile)
    require(manifest['schema_version']==2 and manifest['protocol_version']==2 and manifest['state']=='COMPLETE', 'Not a complete schema-2 run')
    require(manifest['error'] is None and manifest['finished_at'] is not None,'Invalid terminal metadata')
    for field in ('expected_cases','attempted_cases','successful_cases'): require(manifest[field]==len(keys),f'Incomplete {field}')
    for field in ('failed_cases','unattempted_cases'): require(manifest[field]==0,f'Invalid {field}')
    require(len(manifest['cases'])==len(keys),'Wrong manifest case count')
    workloads = list(dict.fromkeys(k[:3] for k in keys))
    require(len(manifest['specifications'])==len(workloads),'Wrong specification count')
    specs = {}
    for workload,stored in zip(workloads,manifest['specifications']):
        expected = generated_spec(master,*workload)
        require(stored==expected,'Canonical input, reference, seed or fingerprint mismatch')
        specs[workload] = expected
    raw = read_csv(directory/'raw/main_results.csv',RAW)
    require(len(raw)==sum(k[0]=='main' for k in keys),'Wrong main matrix')
    if profile=='research': raw += read_csv(directory/'raw/sensitivity_results.csv',RAW)
    require(len(raw)==len(keys),'Wrong raw matrix')
    placements = iter(read_csv(directory/'raw/placements.csv',PLACEMENTS))
    traces = iter(read_csv(directory/'raw/optimizer_trace.csv',TRACE))
    for key,row,case in zip(keys,raw,manifest['cases']):
        check_identity(row,key,run)
        require([case[f] for f in KEY]==list(key) and case['status']=='RUN_OK','Wrong manifest case')
        require(row['status']=='RUN_OK' and row['error_code']==row['error_message']=='','Failed result')
        spec = specs[key[:3]]
        require(row['scenario_sha256']==case['scenario_sha256']==spec['scenario_sha256'],'Unpaired fingerprint')
        close(case['release_s'],.125,'native release')
        for component in ('scenario','workload'):
            require(row[component+'_seed']==str(seed(master,*key[:3],component)),'Wrong case seed')
        optimizer = seed(master,*key[:3],'optimizer',key[3]) if key[4] else None
        require(row['optimizer_seed']==text(optimizer),'Wrong optimizer seed')
        vms,hosts = spec['inputs']['vms'],spec['inputs']['hosts']
        require(row['vm_count']==str(len(vms)) and row['host_count']==str(len(hosts)),'Wrong sizes')
        used = [[0]*5 for _ in hosts]
        placement = []
        for vm in vms:
            p = next(placements)
            check_identity(p,key,run)
            require(p['vm_id']==str(vm['id']),'Wrong VM order')
            host = int(p['host_id'])
            require(0<=host<len(hosts),'Nonexistent host')
            require(vm['mipsPerPe']<=hosts[host]['mipsPerPe'],'Per-PE MIPS exceeds capacity')
            placement.append(host)
            for j,value in enumerate([vm['pes'],vm['pes']*vm['mipsPerPe'],vm['ramMiB'],vm['bwMbps'],vm['storageMiB']]): used[host][j] += value
        require(all(all(v<=cap for v,cap in zip(usage,[16,48000,32768,10000,1000000])) for usage in used),'Infeasible stored placement')
        objective = sum(h['idleW']+(h['maxW']-h['idleW'])*u[1]/48000 for h,u in zip(hosts,used) if u[0])
        close(row['objective_w'],objective,'placement objective')
        for field in RAW.split(',')[17:]: finite(row[field])
        energy = finite(row['energy_j'])
        require(energy>0,'Energy must be positive')
        close(row['energy_kwh'],energy/3_600_000,'energy conversion')
        completed,failed,censored,violations = [int(row[f]) for f in ('completed_cloudlets','failed_cloudlets','censored_cloudlets','sla_violations')]
        require(min(completed,failed,censored,violations)>=0 and completed+failed+censored==len(vms) and failed+censored<=violations<=len(vms),'Invalid completion/SLA counts')
        close(row['sla_rate'],violations/len(vms),'SLA denominator')
        horizon = finite(row['horizon_s'])
        require(0<horizon<=spec['censor_s'] and (not censored or horizon==spec['censor_s']),'Invalid horizon')
        expected_energy,expected_horizon = frozen_metrics(spec,placement)
        close(horizon,expected_horizon,'frozen horizon')
        close(energy,expected_energy,'frozen energy')
        require((completed,failed,censored,violations)==(len(vms),0,0,0),'Invalid frozen completion/SLA counts')
        require(int(row['allocation_wall_ns'])>=0,'Negative wall time')
        n,t = key[4:]
        budget = n+3*n*t if n else 1
        require(row['evaluations']==str(budget),'Wrong evaluation budget')
        best = None
        for e in range(1,budget+1) if n else []:
            trace = next(traces)
            check_identity(trace,key,run)
            if e<=n: iteration,member,stage = 0,e-1,'initial'
            elif key[3]=='GA': iteration,member,stage = (e-n-1)//(n-1)+1,e-1,'ga_child'
            else:
                iteration,position = (e-n-1)//(3*n)+1,(e-n-1)%(3*n)
                if position<n: member,stage = position//2,['river_male','river_female'][position%2]
                elif position<2*n: member,stage = n//2+(position-n)//2,['predator','defense'][position%2]
                else: member,stage = position-2*n,'escape'
            require([trace[f] for f in ('evaluation','iteration','stage','candidate_index')]==list(map(str,[e,iteration,stage,member])),'Wrong trace position')
            require(trace['feasible'] in ('true','false'),'Invalid trace feasibility')
            if trace['feasible']=='true':
                fitness = finite(trace['fitness_w'])
                require(fitness>0,'Invalid trace fitness')
                if stage!='predator': best = fitness if best is None else min(best,fitness)
            else: require(trace['fitness_w']=='','Infeasible numeric fitness')
            if best is None: require(trace['best_fitness_w']=='','Uninitialized trace best')
            else: close(trace['best_fitness_w'],best,'eligible trace best')
        if n: close(best,objective,'final trace best')
    require(next(placements,None) is None and next(traces,None) is None,'Extra placements or traces')
    return raw


def analysis_rows(profile, master, run, raw):
    def group(phase,scenario,algorithm,n=None,t=None):
        return [r for r in raw if (r['phase'],r['scenario'],r['algorithm'])==(phase,scenario,algorithm)
                and (n is None or (r['population'],r['iterations'])==(str(n),str(t)))]
    def summary(rows):
        result = [len(rows)]
        for field in ('energy_j','sla_rate','allocation_wall_ns'):
            values = [finite(r[field]) for r in rows]
            result += [mean(values),sd(values)]
        return result
    if profile=='smoke': return {}
    scenarios = PROFILES[profile][3]
    result = {'scenario_summary.csv':(SUMMARY,[[2,run,s,a]+summary(group('main',s,a)) for s in scenarios for a in ALGORITHMS])}
    if profile!='research': return result
    claims,runtimes = [],[]
    for scenario in scenarios:
        for baseline in ALGORITHMS[1:]:
            ho,other = group('main',scenario,'HO'),group('main',scenario,baseline)
            require(len(ho)==len(other)==30,'Required 30 matched replications')
            energy = [math.log(finite(a['energy_j'])/finite(b['energy_j'])) for a,b in zip(ho,other)]
            sla = [finite(a['sla_rate'])-finite(b['sla_rate']) for a,b in zip(ho,other)]
            e,s = metric(energy,master,scenario,baseline,'energy'),metric(sla,master,scenario,baseline,'sla')
            eligible = not e['reason'] and not s['reason']
            reason = ';'.join(f'{name}:{m["reason"]}' for name,m in [('energy',e),('sla',s)] if m['reason'])
            p = {}
            # Degenerate composite components are all p=1; unreported wild draws need not be generated.
            if eligible:
                for name,values,margin in [('energy',energy,'ln1.05'),('sla',sla,'0.01')]:
                    for m in ['0',margin]:
                        p[name,m] = wild(values,0 if m=='0' else math.log(1.05) if m=='ln1.05' else .01,
                                         JavaRandom(seed(master,'analysis',scenario,0,'wild',f'{baseline}:{name}:{m}')))
            for energy_benefit in [True,False]:
                ps = p['energy' if energy_benefit else 'sla','0'] if eligible else 1.0
                pn = p['sla','0.01'] if eligible and energy_benefit else p['energy','ln1.05'] if eligible else 1.0
                claims.append([2,run,scenario,baseline,30,'energy_benefit' if energy_benefit else 'sla_benefit',e['mean'],s['mean'],e['low'],e['high'],s['low'],s['high'],e['ni'],s['ni'],ps,pn,max(ps,pn),None,bool(eligible),'NO_CLAIM',reason])
            times = [(finite(a['allocation_wall_ns']),finite(b['allocation_wall_ns'])) for a,b in zip(ho,other)]
            if all(a>0 and b>0 for a,b in times):
                average = mean([math.log(a/b) for a,b in times]); ratio = math.exp(average)
                runtimes.append([2,run,scenario,baseline,30,average,ratio,ratio<=.9 or ratio>=1.1,'DESCRIPTIVE_ONLY'])
            else: runtimes.append([2,run,scenario,baseline,30,None,None,None,'INVALID_RUNTIME'])
    for row,adjusted in zip(claims,holm([r[16] for r in claims])):
        row[17] = adjusted
        if row[18]:
            energy_benefit = row[5]=='energy_benefit'
            passed = claim(adjusted,row[9 if energy_benefit else 11],row[13 if energy_benefit else 12],energy_benefit)
            row[19],row[20] = ('CLAIM','ALL_GATES_PASSED') if passed else ('NO_CLAIM','HOLM_OR_PRACTICAL_GATE')
    result.update({'pairwise_primary.csv':(PRIMARY,claims),'runtime_secondary.csv':(RUNTIME,runtimes),
                   'sensitivity_summary.csv':(SENSITIVITY,[[2,run,'Small',n,t]+summary(group('sensitivity','Small','HO',n,t)) for n,t in SETTINGS])})
    return result


def compare_analysis(directory, expected):
    for name,(header,rows) in expected.items():
        actual = read_csv(directory/'analysis'/name,header)
        require(len(actual)==len(rows),f'Wrong analysis row count: {name}')
        for row,wanted in zip(actual,rows):
            for key,value in zip(header.split(','),wanted):
                if isinstance(value,float): close(row[key],value,f'{name}/{key}',key.startswith('p_'))
                else: require(row[key]==text(value),f'Analysis identity/decision mismatch: {name}/{key}')


def unique_object(pairs):
    result = {}
    for key,value in pairs:
        require(key not in result,'Duplicate JSON key')
        result[key] = value
    return result


def utc_instant(value):
    match = re.fullmatch(r'(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(?:Z|\+00:00)',value) if isinstance(value,str) else None
    require(match is not None,'Invalid UTC timestamp')
    # datetime truncates fractions to microseconds; retain Java Instant nanoseconds for ordering.
    return datetime.strptime(match[1],'%Y-%m-%dT%H:%M:%S'),int((match[2] or '').ljust(9,'0'))


def validate_metadata(manifest):
    required = 'schema_version protocol_version run_id state profile started_at finished_at master_seed source_version artifact_sha256 git_revision git_dirty java_version cloudsim_version os arch cpu max_heap_bytes effective_config expected_cases attempted_cases successful_cases failed_cases unattempted_cases cases specifications files error'.split()
    require(isinstance(manifest,dict) and all(f in manifest for f in required),'Missing manifest metadata')
    for key in ('schema_version','protocol_version'):
        require(type(manifest[key]) is int and manifest[key]==2,'Invalid '+key)
    for key in ('run_id','state','profile','source_version','git_revision','java_version','cloudsim_version','os','arch'):
        require(isinstance(manifest[key],str) and bool(manifest[key].strip()),'Invalid '+key)
    require(manifest['profile'] in PROFILES,'Unknown profile')
    require(type(manifest['master_seed']) is int and -(1<<63)<=manifest['master_seed']<(1<<63),'Invalid master seed')
    require(utc_instant(manifest['started_at'])<=utc_instant(manifest['finished_at']),'Finish precedes start')
    require(type(manifest['git_dirty']) is bool,'Invalid git_dirty')
    diagnostic = manifest['source_version']=='development (unpackaged)'
    if diagnostic:
        require(manifest['artifact_sha256'] is None and manifest['git_revision']=='unknown' and manifest['git_dirty'],'Inconsistent unpackaged provenance')
    else:
        require(isinstance(manifest['artifact_sha256'],str) and re.fullmatch('[0-9a-f]{64}',manifest['artifact_sha256']) is not None,'Invalid packaged artifact SHA-256')
        require(re.fullmatch('[0-9a-f]{40}',manifest['git_revision']) is not None,'Invalid packaged Git revision')
    require(re.fullmatch(r'21(?:\.\d+)*(?:[+-][0-9A-Za-z][0-9A-Za-z.+-]*)?',manifest['java_version']) is not None,'Requires recorded Java 21 runtime')
    require(manifest['cloudsim_version']=='8.5.7','Requires CloudSim 8.5.7')
    require(type(manifest['max_heap_bytes']) is int and manifest['max_heap_bytes']>0,'Invalid max_heap_bytes')
    cpu = manifest['cpu']
    require(isinstance(cpu,dict) and type(cpu.get('available_processors')) is int and cpu['available_processors']>0
            and isinstance(cpu.get('model'),str) and bool(cpu['model'].strip()),'Invalid CPU metadata')
    for key in ('expected_cases','attempted_cases','successful_cases','failed_cases','unattempted_cases'):
        require(type(manifest[key]) is int and manifest[key]>=0,'Invalid '+key)
    for key in ('cases','specifications'): require(isinstance(manifest[key],list),'Invalid '+key)
    for key in ('effective_config','files'):
        require(isinstance(manifest[key],dict) and all(isinstance(k,str) and isinstance(v,str) for k,v in manifest[key].items()),'Invalid '+key)
    return 'unpackaged-diagnostic' if diagnostic else 'packaged-recorded'


def validate(directory):
    directory = Path(directory)
    manifest = json.loads((directory/'run.json').read_text(),object_pairs_hook=unique_object,
                          parse_constant=lambda v: (_ for _ in ()).throw(ValueError('Nonfinite JSON')))
    provenance = validate_metadata(manifest)
    profile = manifest['profile']
    required = {'effective.properties','raw/main_results.csv','raw/placements.csv','raw/optimizer_trace.csv'}
    if profile!='smoke': required |= {'analysis/scenario_summary.csv','analysis/report.md'}
    if profile=='research': required |= {'raw/sensitivity_results.csv','analysis/pairwise_primary.csv','analysis/runtime_secondary.csv','analysis/sensitivity_summary.csv'}
    require(set(manifest['files'])==required,'Wrong required hashed artifact set')
    for name in sorted(required):
        data = (directory/name).read_bytes()
        require(hashlib.sha256(data).hexdigest()==manifest['files'][name],f'Hash mismatch: {name}')
    require((directory/'logs/run.log').is_file(),'Missing required log')
    effective = manifest['effective_config']
    expected = ''.join(f'{k}={effective[k]}\n' for k in sorted(effective))
    require((directory/'effective.properties').read_bytes()==expected.encode(),'Effective configuration mismatch')
    n,t,reps,scenarios = PROFILES[profile]
    require(effective.get('log.level') in ('INFO','DEBUG'),'Invalid log level')
    constants = dict(FROZEN, **{'profile':profile,'master.seed':str(manifest['master_seed']),'log.level':effective['log.level'],'population':str(n),'iterations':str(t),'replications':str(reps),'evaluation.budget':str(n+3*n*t),'expected.cases':str(len(matrix(profile))),'scenarios':','.join(scenarios)})
    require(effective==constants,'Wrong effective protocol constants')
    raw = validate_raw(directory,manifest)
    compare_analysis(directory,analysis_rows(profile,manifest['master_seed'],manifest['run_id'],raw))
    if profile!='smoke': require(bool((directory/'analysis/report.md').read_text().strip()),'Empty report')
    print(f'VALID schema=2 profile={profile} cases={len(raw)} provenance={provenance}: hashes, canonical inputs, raw identities, placements, traces, metrics and independent analysis agree')
    return raw


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory',type=Path)
    args = parser.parse_args()
    try: validate(args.directory)
    except (ValueError,KeyError,TypeError,OSError,StopIteration,OverflowError) as error:
        print(f'INVALID: {error}',file=sys.stderr)
        sys.exit(1)
