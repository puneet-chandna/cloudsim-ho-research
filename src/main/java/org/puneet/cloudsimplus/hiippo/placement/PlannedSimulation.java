package org.puneet.cloudsimplus.hiippo.placement;

import java.util.Optional;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyAbstract;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.*;
import org.cloudsimplus.vms.Vm;
import org.puneet.cloudsimplus.hiippo.scenario.*;

/** Fresh, unstarted simulator with strict placement before broker workload release. */
public final class PlannedSimulation {
    private final ScenarioObjects objects;
    private final PlacementPlan plan;
    private final ExactPolicy policy;
    private final DatacenterBrokerSimple broker;
    private boolean placementVerified;
    private PlannedSimulation(ScenarioSpec.Inputs inputs,PlacementPlan plan) {
        plan.validate(inputs); this.plan=plan; objects=ScenarioObjects.create(inputs);
        // HostSimple defaults to automatic activation; only the exact policy powers used hosts on.
        objects.hosts().forEach(host->host.setActive(false));
        policy=new ExactPolicy();
        new DatacenterSimple(objects.simulation(),objects.hosts(),policy).setSchedulingInterval(0);
        broker=new DatacenterBrokerSimple(objects.simulation());
        broker.setVmDestructionDelay(-1);
        // CloudSim invokes this after clearing waiting VMs, before submitting waiting cloudlets.
        broker.addOnVmsCreatedListener(e->{ verifyActualPlacement(); placementVerified=true; });
        broker.submitVmList(objects.vms()); broker.submitCloudletList(objects.cloudlets());
    }
    public static PlannedSimulation create(ScenarioSpec.Inputs inputs,PlacementPlan plan) { return new PlannedSimulation(inputs,plan); }
    /** Configure lifecycle/metrics before start; do not change VM/host capacities or submission delays. */
    public ScenarioObjects objects() { return objects; }
    /** Configure lifecycle/metrics before start; do not submit extra VMs/cloudlets or change submission delays. */
    public DatacenterBrokerSimple broker() { return broker; }
    public ExactPolicy policy() { return policy; }
    public PlacementPlan plan() { return plan; }
    public boolean placementVerified() { return placementVerified; }
    public void verifyActualPlacement() {
        for(var vm:objects.vms()) if(!vm.isCreated() || vm.getHost()!=objects.hosts().get(plan.hostIds().get((int)vm.getId())))
            throw new IllegalStateException("RUN_ERROR: actual placement differs for VM "+vm.getId());
    }
    public final class ExactPolicy extends VmAllocationPolicyAbstract {
        private final boolean[] attempted=new boolean[objects.vms().size()];
        private ExactPolicy() {}
        public int attempts() { int count=0; for(boolean value:attempted) if(value) count++; return count; }
        @Override protected Optional<Host> defaultFindHostForVm(Vm vm) {
            return Optional.of(objects.hosts().get(plan.hostIds().get(id(vm))));
        }
        private int id(Vm vm) {
            long id=vm.getId();
            if(id<0 || id>=objects.vms().size() || objects.vms().get((int)id)!=vm)
                throw new IllegalStateException("RUN_ERROR: foreign VM");
            return (int)id;
        }
        @Override public HostSuitability allocateHostForVm(Vm vm) {
            return allocateHostForVm(vm,objects.hosts().get(plan.hostIds().get(id(vm))));
        }
        @Override public HostSuitability allocateHostForVm(Vm vm,Host host) {
            int id=id(vm);
            if(attempted[id] || host!=objects.hosts().get(plan.hostIds().get(id)))
                throw new IllegalStateException("RUN_ERROR: repeated or changed placement");
            attempted[id]=true;
            if(!Double.isFinite(vm.getMips()) || vm.getMips()<=0 || !Double.isFinite(host.getTotalMipsCapacity()) || host.getTotalMipsCapacity()<=0)
                throw new IllegalStateException("RUN_ERROR: invalid native MIPS capacity");
            host.setActive(true);
            var suitability=super.allocateHostForVm(vm,host);
            if(!suitability.fully()) throw new IllegalStateException("RUN_ERROR: native rejection for VM "+id+": "+suitability);
            return suitability;
        }
    }
}
