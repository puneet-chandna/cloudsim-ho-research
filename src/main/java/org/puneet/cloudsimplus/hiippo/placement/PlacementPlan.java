package org.puneet.cloudsimplus.hiippo.placement;

import java.util.List;
import org.puneet.cloudsimplus.hiippo.scenario.ScenarioSpec;

/** Index is canonical VM ID: duplicate VM assignments are unrepresentable. */
public record PlacementPlan(List<Integer> hostIds) {
    public PlacementPlan { hostIds=List.copyOf(hostIds); }
    /** Fresh reservations independently recheck the entire completed plan. */
    public void validate(ScenarioSpec.Inputs inputs) {
        if(hostIds.size()!=inputs.vms().size()) throw new IllegalArgumentException("Missing or extra VM assignments");
        var ledger=new PlacementLedger(inputs);
        for(int vm=0;vm<hostIds.size();vm++) ledger.place(vm,hostIds.get(vm));
    }
}
