package org.puneet.cloudsimplus.hiippo.scenario;

/** Independent constructive feasibility check; never a planner or repair input. */
public final class Witness {
    private Witness() {}
    public static void validate(ScenarioSpec.Inputs inputs) {
        for(var host:inputs.hosts()) {
            long pes=0,mips=0,ram=0,bw=0,storage=0;
            for(var vm:inputs.vms()) if(vm.id()%inputs.hosts().size()==host.id()) {
                if(vm.mipsPerPe()>host.mipsPerPe()) throw new IllegalArgumentException("Witness per-PE MIPS exceeded");
                pes=Math.addExact(pes,vm.pes()); mips=Math.addExact(mips,vm.totalMips()); ram=Math.addExact(ram,vm.ramMiB());
                bw=Math.addExact(bw,vm.bwMbps()); storage=Math.addExact(storage,vm.storageMiB());
            }
            if(pes>host.pes() || mips>host.totalMips() || ram>host.ramMiB() || bw>host.bwMbps() || storage>host.storageMiB())
                throw new IllegalArgumentException("Witness capacity exceeded on host "+host.id());
        }
    }
    public static void validateNative(ScenarioSpec.Inputs inputs) {
        var objects=ScenarioObjects.create(inputs);
        // Native allocations are cumulative and confined to disposable fresh objects.
        for(var vm:objects.vms()) {
            var host=objects.hosts().get((int)(vm.getId()%objects.hosts().size()));
            if(!host.createVm(vm).fully()) throw new IllegalArgumentException("Native witness rejected VM "+vm.getId());
        }
    }
}
