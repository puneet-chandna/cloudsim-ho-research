package org.puneet.cloudsimplus.hiippo.runtime;

import org.puneet.cloudsimplus.hiippo.placement.*;
import org.puneet.cloudsimplus.hiippo.scenario.*;
import java.nio.file.Path;

/** Sequential paired static cases; only one immutable workload and live search are retained. */
public final class StressRun {
    private StressRun() {}
    public static Path execute(StressConfig config) throws Exception {
        try(var output=StressOutput.create(config)) {
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
                            PlacementPlan plan; double watts; int evaluations;
                            long start=System.nanoTime();
                            if(key.population()!=null) {
                                long seed=Seeds.derive(config.masterSeed(),key.phase(),key.scenario(),replication,"optimizer",algorithm);
                                var search=Search.runStreaming(spec.inputs(),Search.Algorithm.valueOf(algorithm),config.population(),config.iterations(),seed,output::evaluation);
                                code="ALGORITHM_NO_FEASIBLE_PLACEMENT";
                                var best=search.best().orElseThrow(()->new IllegalStateException("No feasible final placement"));
                                plan=best.plan(); watts=best.watts(); evaluations=search.evaluations();
                            } else {
                                var planned=algorithm.equals("FirstFit")?BaselinePlanner.firstFit(spec.inputs()):BaselinePlanner.bestFit(spec.inputs());
                                code="ALGORITHM_NO_FEASIBLE_PLACEMENT";
                                var best=planned.orElseThrow(()->new IllegalStateException("No feasible final placement"));
                                plan=best.plan(); watts=best.objectiveWatts(); evaluations=best.evaluations();
                            }
                            long nanos=System.nanoTime()-start;
                            code="RUN_ERROR"; var metrics=NativeMetrics.run(spec,plan);
                            code="OUTPUT_ERROR"; output.success(spec,plan,watts,evaluations,metrics,nanos);
                        } catch(Exception e) {
                            if(e instanceof java.io.IOException) code="OUTPUT_ERROR";
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
}
