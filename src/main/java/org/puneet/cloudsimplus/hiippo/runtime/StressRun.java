package org.puneet.cloudsimplus.hiippo.runtime;

import org.puneet.cloudsimplus.hiippo.placement.*;
import org.puneet.cloudsimplus.hiippo.scenario.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Paired static cases with bounded workers and streamed, canonically ordered scalar evidence. */
public final class StressRun {
    private StressRun() {}
    public static Path execute(StressConfig config) throws Exception {
        try(var output=StressOutput.create(config)) {
            if(output.workers()>1) return executeParallel(config,output);
            ScenarioSpec spec=null;
            try {
                for(int replication=0;replication<config.replications();replication++) {
                    spec=null;
                    for(String algorithm:new String[]{"HO","GA","FirstFit","BestFit"}) {
                        var key=new Profile.CaseKey(config.experimentPhase(),config.effective().get("scenarios"),replication,
                            algorithm,algorithm.equals("HO")||algorithm.equals("GA")?config.population():null,
                            algorithm.equals("HO")||algorithm.equals("GA")?config.iterations():null);
                        output.begin(key);
                        String code="RUN_ERROR";
                        try {
                            if(spec==null) {
                                code="SCENARIO_REJECTED";
                                spec=ScenarioGenerator.generateStatic(config.masterSeed(),key.phase(),key.scenario(),replication,config.vmCount(),config.hostCount());
                                code="OUTPUT_ERROR"; output.specification(spec);
                            }
                            code="RUN_ERROR";
                            var result=evaluate(config,spec,key,output::evaluation,phase->{});
                            code="OUTPUT_ERROR"; output.success(spec,result.plan(),result.watts(),result.evaluations(),result.metrics(),result.nanos());
                        } catch(Exception e) {
                            if(e instanceof CaseFailure f) code=f.code;
                            else if(e instanceof java.io.IOException) code="OUTPUT_ERROR";
                            try { output.failed(spec,code,e.toString()); } catch(Exception diagnostic) { e.addSuppressed(diagnostic); }
                            throw e;
                        }
                    }
                }
                output.complete();
                return output.directory();
            } catch(Exception e) {
                // Also retain failures outside the active case (summary/hash publication).
                try { output.fail(e instanceof java.io.IOException?"OUTPUT_ERROR":"RUN_ERROR",e.toString()); } catch(Exception diagnostic) { e.addSuppressed(diagnostic); }
                throw e;
            }
        }
    }
    record CaseResult(PlacementPlan plan,double watts,int evaluations,NativeMetrics.Result metrics,long nanos) {}
    @FunctionalInterface interface CaseRunner {
        CaseResult run(StressConfig config,ScenarioSpec spec,Profile.CaseKey key,Search.EvaluationSink sink,Consumer<String> phase) throws Exception;
    }
    private static final class CaseFailure extends Exception {
        final String code;
        CaseFailure(String code,Exception cause) { super(cause); this.code=code; }
    }
    private record LiveProgress(String phase,Search.Evaluation evaluation) {}
    private record Work(Profile.CaseKey key,ScenarioSpec spec,Exception failure,Path spool,AtomicReference<LiveProgress> progress) {}
    private static CaseResult evaluate(StressConfig config,ScenarioSpec spec,Profile.CaseKey key,Search.EvaluationSink sink,Consumer<String> phase) throws Exception {
        String code="RUN_ERROR";
        try {
            PlacementPlan plan; double watts; int evaluations; long start=System.nanoTime();
            if(key.population()!=null) {
                phase.accept("optimizing");
                long seed=Seeds.derive(config.masterSeed(),key.phase(),key.scenario(),key.replication(),"optimizer",key.algorithm());
                var search=Search.runStreaming(spec.inputs(),Search.Algorithm.valueOf(key.algorithm()),config.population(),config.iterations(),seed,sink);
                code="ALGORITHM_NO_FEASIBLE_PLACEMENT";
                var best=search.best().orElseThrow(()->new IllegalStateException("No feasible final placement"));
                plan=best.plan(); watts=best.watts(); evaluations=search.evaluations();
            } else {
                var planned=key.algorithm().equals("FirstFit")?BaselinePlanner.firstFit(spec.inputs()):BaselinePlanner.bestFit(spec.inputs());
                code="ALGORITHM_NO_FEASIBLE_PLACEMENT";
                var best=planned.orElseThrow(()->new IllegalStateException("No feasible final placement"));
                plan=best.plan(); watts=best.objectiveWatts(); evaluations=best.evaluations();
            }
            long nanos=System.nanoTime()-start;
            code="RUN_ERROR";
            phase.accept("simulating");
            return new CaseResult(plan,watts,evaluations,NativeMetrics.run(spec,plan),nanos);
        } catch(Exception e) { throw new CaseFailure(e instanceof java.io.IOException?"OUTPUT_ERROR":code,e); }
    }
    private static Path executeParallel(StressConfig config,StressOutput output) throws Exception {
        return executeParallel(config,output,StressRun::evaluate);
    }
    static Path executeParallel(StressConfig config,StressOutput output,CaseRunner runner) throws Exception {
        Path spools=output.directory().resolve("case-spools");
        ScenarioSpec spec=null; int next=0;
        int pendingLimit=4*output.workers();
        Exception terminalFailure=null;
        try {
            Files.createDirectory(spools);
            while(next<config.expectedCases()) {
                var window=new ArrayList<Work>();
                var tasks=new ArrayList<java.util.concurrent.Callable<CaseResult>>();
                while(next<config.expectedCases() && window.size()<pendingLimit) {
                    int index=next++,replication=index/4; String algorithm=List.of("HO","GA","FirstFit","BestFit").get(index%4);
                    boolean search=algorithm.equals("HO")||algorithm.equals("GA");
                    var key=new Profile.CaseKey(config.experimentPhase(),config.effective().get("scenarios"),replication,algorithm,search?config.population():null,search?config.iterations():null);
                    Exception failure=null;
                    try { if(spec==null || spec.inputs().seeds().replication()!=replication)
                        spec=ScenarioGenerator.generateStatic(config.masterSeed(),key.phase(),key.scenario(),replication,config.vmCount(),config.hostCount()); }
                    catch(Exception e) { spec=null; failure=new CaseFailure("SCENARIO_REJECTED",e); }
                    var work=new Work(key,spec,failure,spools.resolve("case-"+index+".spool"),new AtomicReference<>(new LiveProgress("planning",null))); window.add(work);
                    tasks.add(()->{
                        if(work.failure()!=null) throw work.failure();
                        Consumer<String> phase=p->work.progress().updateAndGet(previous->new LiveProgress(p,previous.evaluation()));
                        CaseResult result;
                        if(!search) result=runner.run(config,work.spec(),work.key(),value->{ throw new IllegalStateException("Baseline emitted optimizer evidence"); },phase);
                        else try(var spool=new EvaluationSpool(work.spool())) {
                            result=runner.run(config,work.spec(),work.key(),value->{
                                spool.accept(value); work.progress().set(new LiveProgress("optimizing",value));
                            },phase);
                        }
                        phase.accept("finished"); return result;
                    });
                    if(failure!=null) break;
                }
                try(var executor=new CaseExecutor<>(output.workers(),pendingLimit,tasks)) {
                    for(int i=0;i<window.size();i++) {
                        var work=window.get(i); output.begin(work.key()); boolean replayed=false;
                        try {
                            if(work.spec()!=null && work.key().algorithm().equals("HO")) output.specification(work.spec());
                            var result=executor.await(i,()->{
                                var live=work.progress().get(); output.liveProgress(live.phase(),live.evaluation());
                            });
                            var live=work.progress().get(); output.liveProgress(live.phase(),live.evaluation());
                            EvaluationSpool.replay(work.spool(),output::evaluation); replayed=true;
                            output.success(work.spec(),result.plan(),result.watts(),result.evaluations(),result.metrics(),result.nanos());
                            Files.deleteIfExists(work.spool());
                        } catch(Exception e) {
                            executor.close();
                            if(!replayed) try { EvaluationSpool.replay(work.spool(),output::evaluation); }
                            catch(Exception diagnostic) { e.addSuppressed(diagnostic); }
                            String code=e instanceof CaseFailure f?f.code:e instanceof java.io.IOException?"OUTPUT_ERROR":"RUN_ERROR";
                            try { output.failed(work.spec(),code,e.toString()); } catch(Exception diagnostic) { e.addSuppressed(diagnostic); }
                            throw e;
                        }
                    }
                }
            }
            deleteSpools(spools);
            output.complete(); return output.directory();
        } catch(Exception e) {
            terminalFailure=e;
            String code=e instanceof CaseFailure f?f.code:e instanceof java.io.IOException?"OUTPUT_ERROR":"RUN_ERROR";
            try { output.fail(code,e.toString()); } catch(Exception diagnostic) { e.addSuppressed(diagnostic); }
            throw e;
        } finally {
            // Every owned worker has terminated before disposing unpublished evidence.
            try { deleteSpools(spools); }
            catch(Exception cleanup) { if(terminalFailure!=null) terminalFailure.addSuppressed(cleanup); else throw cleanup; }
        }
    }
    private static void deleteSpools(Path directory) throws java.io.IOException {
        if(!Files.exists(directory)) return;
        try(var files=Files.list(directory)) { for(var file:files.toList()) Files.deleteIfExists(file); }
        Files.deleteIfExists(directory);
    }
}
