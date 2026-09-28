package org.puneet.cloudsimplus.hiippo.runtime;

import org.puneet.cloudsimplus.hiippo.placement.*;
import org.puneet.cloudsimplus.hiippo.scenario.*;
import java.util.*;

/** Fixed sequential protocol matrix; no retry, fallback placement, or partial-data analysis. */
public final class RunCoordinator {
    private RunCoordinator() {}
    public record CaseResult(PlacementPlan plan,double objectiveWatts,int evaluations,List<Search.Trace> trace,
                             NativeMetrics.Result metrics,long allocationNanos) {
        public CaseResult { trace=List.copyOf(trace); }
    }
    @FunctionalInterface interface CaseRunner { CaseResult run(ScenarioSpec spec,Profile.CaseKey key) throws Exception; }
    static final class NoFeasiblePlacement extends Exception {
        final List<Search.Trace> trace;
        NoFeasiblePlacement(List<Search.Trace> trace) { super("No feasible final placement"); this.trace=List.copyOf(trace); }
    }
    static final class SimulationFailure extends Exception {
        final List<Search.Trace> trace;
        SimulationFailure(Exception cause,List<Search.Trace> trace) { super(cause); this.trace=List.copyOf(trace); }
    }
    public static void execute(RunConfig config,RunOutput output) throws Exception { execute(config,output,RunCoordinator::evaluate); }
    static void execute(RunConfig config,RunOutput output,CaseRunner runner) throws Exception {
        ScenarioSpec spec=null;
        try {
            for(var key:config.profile().cases()) {
                output.begin(key);
                CaseResult result=null;
                try {
                    if(spec==null || !sameWorkload(spec,key)) {
                        spec=null;
                        spec=ScenarioGenerator.generate(config.masterSeed(),key.phase(),key.scenario(),key.replication());
                        output.addSpecification(spec);
                    }
                    result=runner.run(spec,key);
                    output.success(key,spec,result);
                } catch(Exception e) {
                    var trace=e instanceof NoFeasiblePlacement n?n.trace:e instanceof SimulationFailure s?s.trace:result==null?List.<Search.Trace>of():result.trace();
                    try { output.caseFailed(key,spec,code(e),e.toString(),trace); }
                    catch(Exception diagnostic) { e.addSuppressed(diagnostic); }
                    throw e;
                }
            }
            output.analyze();
            output.complete();
        } catch(Exception e) {
            try { output.fail(code(e),e.toString()); } catch(Exception diagnostic) { e.addSuppressed(diagnostic); }
            throw e;
        }
    }
    private static String code(Exception e) {
        if(e instanceof NoFeasiblePlacement) return "ALGORITHM_NO_FEASIBLE_PLACEMENT";
        return e instanceof java.io.IOException?"OUTPUT_ERROR":"RUN_ERROR";
    }
    private static boolean sameWorkload(ScenarioSpec spec,Profile.CaseKey key) {
        var seeds=spec.inputs().seeds();
        return seeds.phase().equals(key.phase()) && seeds.scenario().equals(key.scenario()) && seeds.replication()==key.replication();
    }
    static Long optimizerSeed(ScenarioSpec spec,Profile.CaseKey key) {
        return key.population()==null?null:Seeds.derive(spec.inputs().seeds().master(),key.phase(),key.scenario(),key.replication(),"optimizer",key.algorithm());
    }
    public static CaseResult evaluate(ScenarioSpec spec,Profile.CaseKey key) throws Exception {
        PlacementPlan plan; double watts; List<Search.Trace> trace=List.of(); int evaluations;
        Long seed=optimizerSeed(spec,key);
        long started=System.nanoTime();
        if(seed!=null) {
            var result=Search.run(spec.inputs(),Search.Algorithm.valueOf(key.algorithm()),key.population(),key.iterations(),seed);
            trace=result.trace();
            var best=result.best().orElseThrow(()->new NoFeasiblePlacement(result.trace()));
            plan=best.plan(); watts=best.watts(); evaluations=trace.size();
        } else {
            var result=switch(key.algorithm()) {
                case "FirstFit" -> BaselinePlanner.firstFit(spec.inputs());
                case "BestFit" -> BaselinePlanner.bestFit(spec.inputs());
                default -> throw new IllegalArgumentException("Unknown algorithm");
            };
            var best=result.orElseThrow(()->new NoFeasiblePlacement(List.of()));
            plan=best.plan(); watts=best.objectiveWatts(); evaluations=best.evaluations();
        }
        long nanos=System.nanoTime()-started;
        try { return new CaseResult(plan,watts,evaluations,trace,NativeMetrics.run(spec,plan),nanos); }
        catch(Exception e) { throw new SimulationFailure(e,trace); }
    }
}
