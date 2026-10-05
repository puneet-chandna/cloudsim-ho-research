package org.puneet.cloudsimplus.hiippo.placement;

import java.util.*;
import org.puneet.cloudsimplus.hiippo.scenario.ScenarioSpec;

public final class BaselinePlanner {
    private BaselinePlanner() {}
    public record Result(PlacementPlan plan,double objectiveWatts) { public int evaluations() { return 1; } }
    public static Optional<Result> firstFit(ScenarioSpec.Inputs inputs) { return plan(inputs,false); }
    public static Optional<Result> bestFit(ScenarioSpec.Inputs inputs) { return plan(inputs,true); }
    private static Optional<Result> plan(ScenarioSpec.Inputs inputs,boolean bestFit) {
        var ledger=new PlacementLedger(inputs); var ids=new ArrayList<Integer>();
        for(var vm:inputs.vms()) {
            if(Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Case placement cancelled");
            int selected=-1; double best=Double.POSITIVE_INFINITY;
            for(var host:inputs.hosts()) if(ledger.canPlace(vm.id(),host.id())) {
                double score=bestFit?ledger.residualAfter(vm.id(),host.id()):0;
                if(score<best) { best=score; selected=host.id(); }
                if(!bestFit) break;
            }
            if(selected<0) return Optional.empty();
            ledger.place(vm.id(),selected); ids.add(selected);
        }
        var plan=new PlacementPlan(ids);
        return Optional.of(new Result(plan,PlacementObjective.watts(inputs,plan)));
    }
}
