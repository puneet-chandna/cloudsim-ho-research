package org.puneet.cloudsimplus.hiippo.runtime;

import java.io.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.puneet.cloudsimplus.hiippo.scenario.Seeds;

/** Fixed protocol analysis, consuming only a complete ordered, validated raw matrix. */
final class RunAnalysis {
    static final String SUMMARY="schema_version,run_id,scenario,algorithm,n,energy_mean_j,energy_sd_j,sla_mean,sla_sd,allocation_mean_ns,allocation_sd_ns";
    static final String PRIMARY="schema_version,run_id,scenario,baseline,n,claim,mean_log_energy_ratio,mean_sla_difference,energy_ci_low,energy_ci_high,sla_ci_low,sla_ci_high,energy_ni_upper,sla_ni_upper,p_superiority,p_noninferiority,p_composite,p_holm,eligible,decision,reason";
    static final String RUNTIME="schema_version,run_id,scenario,baseline,n,mean_log_runtime_ratio,geometric_runtime_ratio,magnitude_at_least_10_percent,reason";
    static final String SENSITIVITY="schema_version,run_id,scenario,population,iterations,n,energy_mean_j,energy_sd_j,sla_mean,sla_sd,allocation_mean_ns,allocation_sd_ns";
    private RunAnalysis() {}
    static Set<String> required(Profile profile) {
        return switch(profile) {
            case smoke->Set.of(); case explore->Set.of("analysis/scenario_summary.csv","analysis/report.md");
            case research->Set.of("analysis/scenario_summary.csv","analysis/report.md","analysis/pairwise_primary.csv","analysis/runtime_secondary.csv","analysis/sensitivity_summary.csv");
        };
    }
    private static double number(Map<String,String> row,String key) {
        double v=Double.parseDouble(row.get(key)); if(!Double.isFinite(v)) throw new IllegalArgumentException("Nonfinite "+key); return v;
    }
    private static void validate(Profile profile,List<Map<String,String>> rows) {
        var keys=profile.cases(); if(rows.size()!=keys.size()) throw new IllegalArgumentException("Incomplete analysis matrix");
        var paired=new HashMap<String,String>(); String run=rows.getFirst().get("run_id");
        for(int i=0;i<rows.size();i++) {
            var row=rows.get(i); var k=keys.get(i);
            var actual=List.of(row.get("phase"),row.get("scenario"),row.get("replication"),row.get("algorithm"),row.get("population"),row.get("iterations"));
            if(!actual.equals(List.of(k.phase(),k.scenario(),""+k.replication(),k.algorithm(),Objects.toString(k.population(),""),Objects.toString(k.iterations(),"")))
                || !"2".equals(row.get("schema_version")) || !run.equals(row.get("run_id")) || !"RUN_OK".equals(row.get("status")))
                throw new IllegalArgumentException("Invalid analysis case identity/status");
            if(number(row,"energy_j")<=0 || number(row,"sla_rate")<0 || number(row,"sla_rate")>1) throw new IllegalArgumentException("Invalid paired outcome");
            String workload=k.phase()+":"+k.scenario()+":"+k.replication(),fingerprint=row.get("scenario_sha256")+":"+row.get("workload_seed");
            var previous=paired.putIfAbsent(workload,fingerprint);
            if(previous!=null && !previous.equals(fingerprint)) throw new IllegalArgumentException("Unpaired canonical inputs");
        }
    }
    private static List<Map<String,String>> group(List<Map<String,String>> rows,String phase,String scenario,String algorithm,Integer population,Integer iterations) {
        return rows.stream().filter(r->r.get("phase").equals(phase) && r.get("scenario").equals(scenario) && r.get("algorithm").equals(algorithm)
            && (population==null || r.get("population").equals(population.toString()) && r.get("iterations").equals(iterations.toString()))).toList();
    }
    private static double[] values(List<Map<String,String>> rows,String field) { return rows.stream().mapToDouble(r->number(r,field)).toArray(); }
    private static List<Object> row(Object... values) { return new ArrayList<>(Arrays.asList(values)); }
    private static void summary(List<Object> row,List<Map<String,String>> samples) {
        row.add(samples.size());
        for(String key:List.of("energy_j","sla_rate","allocation_wall_ns")) {
            var v=values(samples,key); row.add(PairedStatistics.mean(v)); row.add(v.length<2?null:PairedStatistics.sd(v));
        }
    }
    private static String csv(String header,List<List<Object>> rows) throws IOException {
        var writer=new StringWriter(); writer.append(header).append('\n');
        try(var csv=new CSVPrinter(writer,CSVFormat.DEFAULT.builder().setRecordSeparator('\n').build())) {
            for(var row:rows) {
                for(Object value:row) if(value instanceof Double d && !Double.isFinite(d)) throw new IllegalArgumentException("Nonfinite analysis result");
                csv.printRecord(row);
            }
        }
        return writer.toString();
    }
    record Metric(double mean,Double low,Double high,Double ni,double p0,double pni,String reason) {}
    static Metric metric(double[] x,long master,String scenario,String baseline,String metric) {
        if(x.length!=30) throw new IllegalArgumentException("Inference requires 30 matched pairs");
        for(double v:x) if(!Double.isFinite(v)) throw new IllegalArgumentException("Invalid paired difference");
        String margin=metric.equals("energy")?"ln1.05":"0.01";
        var bca=PairedStatistics.bca(x,random(master,scenario,baseline,metric,"0","bca"),PairedStatistics.BCA_DRAWS);
        var ni=PairedStatistics.bca(x,random(master,scenario,baseline,metric,margin,"bca"),PairedStatistics.BCA_DRAWS);
        Double low=bca.at(.025),high=bca.at(.975),upper=ni.at(.95);
        String reason=!bca.valid()?bca.reason():!ni.valid()?ni.reason():low==null || high==null || upper==null?"BCA_QUANTILE_UNDEFINED":"";
        if(!reason.isEmpty()) return new Metric(PairedStatistics.mean(x),low,high,upper,1,1,reason);
        double m=metric.equals("energy")?Math.log(1.05):.01;
        if(!Double.isFinite(PairedStatistics.statistic(x,0)) || !Double.isFinite(PairedStatistics.statistic(x,m)))
            return new Metric(PairedStatistics.mean(x),low,high,upper,1,1,"STUDENTIZATION_UNDEFINED");
        return new Metric(PairedStatistics.mean(x),low,high,upper,
            PairedStatistics.wild(x,0,random(master,scenario,baseline,metric,"0","wild"),PairedStatistics.WILD_DRAWS),
            PairedStatistics.wild(x,m,random(master,scenario,baseline,metric,margin,"wild"),PairedStatistics.WILD_DRAWS),"");
    }
    private static Random random(long master,String scenario,String baseline,String metric,String margin,String component) {
        return new Random(Seeds.derive(master,"analysis",scenario,0,component,baseline+":"+metric+":"+margin));
    }
    static Map<String,String> render(Profile profile,long master,List<Map<String,String>> raw) throws IOException {
        validate(profile,raw); if(profile==Profile.smoke) return Map.of();
        String run=raw.getFirst().get("run_id"); var files=new LinkedHashMap<String,String>();
        var summaries=new ArrayList<List<Object>>();
        for(String scenario:profile.scenarios) for(String algorithm:List.of("HO","GA","FirstFit","BestFit")) {
            var row=row(2,run,scenario,algorithm); summary(row,group(raw,"main",scenario,algorithm,null,null)); summaries.add(row);
        }
        files.put("analysis/scenario_summary.csv",csv(SUMMARY,summaries));
        var report=new StringBuilder("# Protocol 2 analysis\n\nProfile: "+profile+". All required cases are RUN_OK; scenarios are analyzed separately.\n\n");
        report.append("Energy observations concern this synthetic workload, static placement, heterogeneous linear power model, and repair; they do not establish universal algorithm superiority or real datacenter savings. The frozen no-contention workload structurally yields zero SLA differences and can prevent every composite claim. SLA is a control endpoint.\n\n");
        if(profile==Profile.research) {
            var claims=new ArrayList<List<Object>>(); var runtimes=new ArrayList<List<Object>>();
            for(String scenario:profile.scenarios) for(String baseline:List.of("GA","FirstFit","BestFit")) {
                var ho=group(raw,"main",scenario,"HO",null,null); var other=group(raw,"main",scenario,baseline,null,null);
                double[] energy=new double[30],sla=new double[30],runtime=new double[30]; boolean validRuntime=true;
                for(int i=0;i<30;i++) {
                    energy[i]=Math.log(number(ho.get(i),"energy_j")/number(other.get(i),"energy_j")); sla[i]=number(ho.get(i),"sla_rate")-number(other.get(i),"sla_rate");
                    double a=Double.parseDouble(ho.get(i).get("allocation_wall_ns")),b=Double.parseDouble(other.get(i).get("allocation_wall_ns"));
                    if(!Double.isFinite(a) || !Double.isFinite(b) || a<=0 || b<=0) validRuntime=false;
                    else runtime[i]=Math.log(a/b);
                }
                var e=metric(energy,master,scenario,baseline,"energy"); var s=metric(sla,master,scenario,baseline,"sla");
                boolean eligible=e.reason().isEmpty() && s.reason().isEmpty();
                for(boolean benefitEnergy:new boolean[]{true,false}) {
                    double ps=eligible?(benefitEnergy?e.p0():s.p0()):1,pn=eligible?(benefitEnergy?s.pni():e.pni()):1;
                    String reason=eligible?"":(!e.reason().isEmpty()?"energy:"+e.reason():"")+(!s.reason().isEmpty()?(e.reason().isEmpty()?"":";")+"sla:"+s.reason():"");
                    claims.add(row(2,run,scenario,baseline,30,benefitEnergy?"energy_benefit":"sla_benefit",e.mean(),s.mean(),e.low(),e.high(),s.low(),s.high(),e.ni(),s.ni(),ps,pn,Math.max(ps,pn),null,eligible,"NO_CLAIM",reason));
                }
                double rm=validRuntime?PairedStatistics.mean(runtime):0,ratio=Math.exp(rm);
                runtimes.add(row(2,run,scenario,baseline,30,validRuntime?rm:null,validRuntime?ratio:null,validRuntime?ratio<=.9 || ratio>=1.1:null,validRuntime?"DESCRIPTIVE_ONLY":"INVALID_RUNTIME"));
                report.append(scenario).append(" / ").append(baseline).append(": observed geometric energy ratio ").append(Math.exp(e.mean())).append("; SLA mean difference ").append(s.mean()).append(".\n\n");
            }
            double[] adjusted=PairedStatistics.holm(claims.stream().mapToDouble(c->(Double)c.get(16)).toArray());
            for(int i=0;i<claims.size();i++) {
                var c=claims.get(i); c.set(17,adjusted[i]);
                if((Boolean)c.get(18)) {
                    boolean energy=c.get(5).equals("energy_benefit");
                    boolean passed=PairedStatistics.claim(adjusted[i],(Double)c.get(energy?9:11),(Double)c.get(energy?13:12),energy);
                    c.set(19,passed?"CLAIM":"NO_CLAIM"); c.set(20,passed?"ALL_GATES_PASSED":"HOLM_OR_PRACTICAL_GATE");
                }
            }
            files.put("analysis/pairwise_primary.csv",csv(PRIMARY,claims)); files.put("analysis/runtime_secondary.csv",csv(RUNTIME,runtimes));
            var sensitivity=new ArrayList<List<Object>>(); var settings=new LinkedHashSet<String>();
            for(var k:profile.cases()) if(k.phase().equals("sensitivity") && settings.add(k.population()+":"+k.iterations())) {
                var row=row(2,run,"Small",k.population(),k.iterations()); summary(row,group(raw,"sensitivity","Small","HO",k.population(),k.iterations())); sensitivity.add(row);
            }
            files.put("analysis/sensitivity_summary.csv",csv(SENSITIVITY,sensitivity));
            report.append("Inference uses 30 matched replications per scenario, 1,000,000 studentized margin-centered Rademacher draws and 10,000 paired BCa draws with jackknife acceleration and type-7 quantiles. Wild p=(1+extreme)/(1000001); undefined draws count as extreme. This is approximate mean inference requiring independent replications and symmetric/exchangeable centered errors; skewness can impair calibration. It is not an exact generic paired randomization test.\n\n");
            report.append("The fixed family retains all 18 intersection-union claims, composite p=max(component p), Holm alpha=.05. Energy benefit requires energy 95% upper<=ln(.95) and SLA one-sided 95% upper<.01; SLA benefit requires SLA 95% upper<=-.01 and energy one-sided 95% upper<ln(1.05). Degenerate required statistics imply ineligible NO_CLAIM with both component p=1. BCa estimation intervals are unadjusted; Holm gates claims. BCa two-sided streams use margin 0; separate one-sided streams use ln1.05 / 0.01.\n\n");
            report.append("Sensitivity is descriptive OAT: nine distinct N/T settings, ten paired replications each, 90 real cases with (30,40) once per replication, disjoint from main workload seeds. It cannot select main settings. Runtime log ratios are descriptive, unavailable for zero/invalid clocks, and are not comparable winner inference across machines/JIT states.\n");
        } else report.append("Explore provides means and sample standard deviations only; no inferential claims, intervals, or parameter selection.\n");
        files.put("analysis/report.md",report.toString()); return Collections.unmodifiableMap(files);
    }
}
