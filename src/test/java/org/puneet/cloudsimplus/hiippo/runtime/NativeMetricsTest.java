package org.puneet.cloudsimplus.hiippo.runtime;

import org.junit.jupiter.api.Test;
import org.puneet.cloudsimplus.hiippo.scenario.*;
import org.puneet.cloudsimplus.hiippo.placement.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeMetricsTest {
    static ScenarioSpec tiny() {
        var seeds=new ScenarioSpec.SeedMetadata(1,"main","Micro",0,2,3);
        var hosts=List.of(new ScenarioSpec.HostSpec(0,16,3000,32768,10000,1000000,100,196),
            new ScenarioSpec.HostSpec(1,16,3000,32768,10000,1000000,200,296),
            new ScenarioSpec.HostSpec(2,16,3000,32768,10000,1000000,900,999));
        var vms=List.of(new ScenarioSpec.VmSpec(0,1,3000,1024,1000,10000),new ScenarioSpec.VmSpec(1,1,3000,1024,1000,10000));
        var clouds=List.of(new ScenarioSpec.CloudletSpec(0,0,1,30000),new ScenarioSpec.CloudletSpec(1,1,1,60000));
        return new ScenarioSpec(new ScenarioSpec.Inputs(seeds,hosts,vms,clouds),List.of(10.0,20.0));
    }
    @Test void eventIntervalsIncludeUsedIdleTailAndKeepUnusedHostOff() {
        var result=NativeMetrics.calculate(tiny(),new PlacementPlan(List.of(0,1)),0,List.of(
            new NativeMetrics.Observation(0,0.0,10.0,true),new NativeMetrics.Observation(0,0.0,20.0,true)));
        assertEquals(6180,result.energyJ()); // 100*20+6*10 + 200*20+6*20
        assertEquals(6180/3_600_000.0,result.energyKwh());
        assertEquals(20,result.horizonSeconds()); assertEquals(0,result.slaViolations());
        assertEquals(2,result.completed());
    }
    @Test void thresholdIsStrictAndUnfinishedAndFailedUseRequestedDenominator() {
        var spec=new ScenarioSpec(tiny().inputs(),List.of(100.0,100.0));
        var plan=new PlacementPlan(List.of(0,1));
        var exact=NativeMetrics.calculate(spec,plan,0,List.of(new NativeMetrics.Observation(0,0.0,110.0,true),
            new NativeMetrics.Observation(0,0.0,111.0,true)));
        assertEquals(1,exact.slaViolations()); assertEquals(.5,exact.slaRate());
        var incomplete=NativeMetrics.calculate(spec,plan,0,List.of(new NativeMetrics.Observation(0,0.0,50.0,false),
            new NativeMetrics.Observation(0,0.0,null,false)));
        assertEquals(1000,incomplete.horizonSeconds()); assertEquals(2,incomplete.slaViolations());
        assertEquals(0,incomplete.completed()); assertEquals(1,incomplete.failed()); assertEquals(1,incomplete.censored());
        assertEquals(306300,incomplete.energyJ()); // 300*1000 +6*50 +6*1000
        assertThrows(IllegalArgumentException.class,()->NativeMetrics.calculate(spec,plan,0,List.of(
            new NativeMetrics.Observation(0,Double.NaN,null,false),new NativeMetrics.Observation(0,0.0,null,false))));
    }
    @Test void realNativeRunMatchesAnalyticEnergyAndIndependentReferences() {
        var spec=new ScenarioSpec(tiny().inputs(),ScenarioObjects.measureReferences(tiny().inputs()));
        assertEquals(List.of(10.0,20.0),spec.referenceSeconds());
        var run=PlannedSimulation.create(spec.inputs(),new PlacementPlan(List.of(0,1)));
        assertFalse(run.objects().hosts().get(2).isActive());
        for(var cloudlet:run.objects().cloudlets()) cloudlet.addOnStartListener(e->assertFalse(run.objects().hosts().get(2).isActive()));
        run.objects().cloudlets().get(1).addOnFinishListener(e->{
            assertTrue(run.objects().hosts().get(0).isActive(),"shorter host remains on through idle tail");
            assertFalse(run.objects().hosts().get(2).isActive());
        });
        var result=NativeMetrics.run(spec,run);
        assertEquals(.125,result.releaseSeconds()); assertEquals(6180,result.energyJ());
        assertEquals(20,result.horizonSeconds()); assertEquals(2,result.completed());
        assertEquals(0,result.failed()); assertEquals(0,result.censored()); assertEquals(0,result.slaRate());
    }
    @Test void noStartEventsStillCountRequestedWorkAndUsedHostIdleEnergy() {
        var result=NativeMetrics.calculate(tiny(),new PlacementPlan(List.of(0,1)),.125,List.of(
            new NativeMetrics.Observation(.125,null,null,false),new NativeMetrics.Observation(.125,null,null,false)));
        assertEquals(200,result.horizonSeconds()); assertEquals(2,result.censored()); assertEquals(1,result.slaRate());
        assertEquals(60000,result.energyJ());
    }
    @Test void boundedNativeExecutionCensorsLongWorkWithoutCallingItError() {
        var spec=new ScenarioSpec(tiny().inputs(),List.of(1.0,1.0));
        var result=NativeMetrics.run(spec,new PlacementPlan(List.of(0,1)));
        assertEquals(10,result.horizonSeconds()); assertEquals(1,result.completed());
        assertEquals(1,result.censored()); assertEquals(2,result.slaViolations()); assertEquals(3120,result.energyJ());
    }
    @Test void nativeCancellationStopsCpuAtItsActualEventTime() {
        var spec=tiny(); var run=PlannedSimulation.create(spec.inputs(),new PlacementPlan(List.of(0,1)));
        new org.cloudsimplus.core.CloudSimEntity(run.objects().simulation()) {
            @Override protected void startInternal() { schedule(5.125,1); }
            @Override public void processEvent(org.cloudsimplus.core.events.SimEvent event) {
                run.objects().vms().getFirst().getCloudletScheduler().cloudletCancel(run.objects().cloudlets().getFirst());
            }
        };
        var result=NativeMetrics.run(spec,run);
        assertEquals(6150,result.energyJ()); // idle300*20 + host0 dynamic6*5 + host1 dynamic6*20
        assertEquals(20,result.horizonSeconds()); assertEquals(1,result.completed()); assertEquals(1,result.failed());
        assertEquals(0,result.censored()); assertEquals(.5,result.slaRate());
    }
    @Test void nativeMismatchFailsBeforeAnyWorkStarts() {
        var spec=tiny(); var run=PlannedSimulation.create(spec.inputs(),new PlacementPlan(List.of(0,1)));
        run.objects().vms().getFirst().setRam(1000000);
        assertThrows(IllegalStateException.class,()->NativeMetrics.run(spec,run));
        assertTrue(run.objects().cloudlets().stream().allMatch(c->c.getStartTime()<0));
    }
}
