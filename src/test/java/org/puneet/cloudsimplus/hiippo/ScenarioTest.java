package org.puneet.cloudsimplus.hiippo;

import org.junit.jupiter.api.Test;
import org.puneet.cloudsimplus.hiippo.scenario.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScenarioTest {
    @Test void legacyScenarioBytesRemainFrozen() {
        var hashes=List.of("9437f11bd38a8709380cd6e28e960ee37f37a6f3cf7831d60d7196d9403f6cd7",
            "60b56c2e86c16ada2031b089ebd01d3a2947dd6547c5f57ebfb29bb499bf339f",
            "cb68700a68e7511a8dc18a4ee53ea1eb6e414d0da0ae5f3c3e677a9bbd52fc66");
        int i=0;
        for(String name:List.of("Micro","Small","Medium"))
            assertEquals(hashes.get(i++),ScenarioGenerator.generate(123456,"main",name,0).fingerprint());
    }

    @Test void stressUsesExplicitDimensionsAndPairedInputsWithDistinctIdentities() {
        for(String phase:List.of("stress","stress_calibration")) {
            var first=ScenarioGenerator.generateStatic(123456,phase,"Static-V17-H4",0,17,4);
            assertEquals(17,first.inputs().vms().size()); assertEquals(4,first.inputs().hosts().size());
            Witness.validate(first.inputs()); Witness.validateNative(first.inputs());
            for(String algorithm:List.of("HO","GA","FirstFit","BestFit")) {
                assertEquals(first,ScenarioGenerator.generateStatic(123456,phase,"Static-V17-H4",0,17,4),algorithm);
            }
            assertNotEquals(first.fingerprint(),ScenarioGenerator.generateStatic(123457,phase,"Static-V17-H4",0,17,4).fingerprint());
            assertNotEquals(first.fingerprint(),ScenarioGenerator.generateStatic(123456,phase,"Static-V17-H4",1,17,4).fingerprint());
            assertNotEquals(first.fingerprint(),ScenarioGenerator.generateStatic(123456,phase,"Static-V18-H4",0,18,4).fingerprint());
            assertNotEquals(first.fingerprint(),ScenarioGenerator.generateStatic(123456,phase,"Static-V17-H5",0,17,5).fingerprint());
            for(var host:first.inputs().hosts()) {
                assertEquals(16,host.pes()); assertEquals(3000,host.mipsPerPe());
                assertEquals(32768,host.ramMiB()); assertEquals(10000,host.bwMbps()); assertEquals(1000000,host.storageMiB());
                assertEquals(new int[]{175,210,280}[host.id()%3],host.idleW());
                assertEquals(new int[]{250,300,400}[host.id()%3],host.maxW());
            }
            for(int i=0;i<17;i++) {
                var vm=first.inputs().vms().get(i); var c=first.inputs().cloudlets().get(i);
                assertTrue(List.of(1,2).contains(vm.pes()));
                assertTrue(List.of(1000L,2000L,3000L).contains(vm.mipsPerPe()));
                assertTrue(List.of(1024L,2048L,4096L).contains(vm.ramMiB()));
                assertEquals(1000,vm.bwMbps()); assertEquals(10000,vm.storageMiB());
                assertEquals(vm.pes(),c.pes()); assertEquals(0,c.lengthMi()%vm.mipsPerPe());
                assertTrue(c.lengthMi()/vm.mipsPerPe()>=60 && c.lengthMi()/vm.mipsPerPe()<=120);
                assertEquals((double)c.lengthMi()/vm.mipsPerPe(),first.referenceSeconds().get(i));
            }
        }
        var stress=ScenarioGenerator.generateStatic(123456,"stress","Static-V17-H4",0,17,4);
        var pilot=ScenarioGenerator.generateStatic(123456,"stress_calibration","Static-V17-H4",0,17,4);
        assertNotEquals(stress.inputs().seeds().scenarioSeed(),pilot.inputs().seeds().scenarioSeed());
        assertNotEquals(stress.inputs().seeds().workloadSeed(),pilot.inputs().seeds().workloadSeed());
    }

    @Test void stressIdentityAndWitnessFailuresNeverRelaxDimensions() {
        for(String name:List.of("Micro","Static-V18-H4","Static-V17-H5","Static-V017-H4","Static-V0-H4"))
            assertThrows(IllegalArgumentException.class,()->ScenarioGenerator.generateStatic(1,"stress",name,0,17,4));
        for(String phase:List.of("main","sensitivity","other"))
            assertThrows(IllegalArgumentException.class,()->ScenarioGenerator.generateStatic(1,phase,"Static-V17-H4",0,17,4));
        assertThrows(IllegalArgumentException.class,()->ScenarioGenerator.generateStatic(1,"stress","Static-V17-H4",-1,17,4));
        assertThrows(IllegalArgumentException.class,()->ScenarioGenerator.generateStatic(1,"stress","Static-V0-H4",0,0,4));
        assertThrows(IllegalArgumentException.class,()->ScenarioGenerator.generateStatic(1,"stress","Static-V17-H0",0,17,0));
        assertThrows(IllegalArgumentException.class,()->ScenarioGenerator.generateStatic(1,"stress","Static-V17-H1",0,17,1));
        for(String phase:List.of("main","sensitivity"))
            assertThrows(IllegalArgumentException.class,()->new ScenarioSpec.SeedMetadata(1,phase,"Static-V17-H4",0,1,2));
        for(String phase:List.of("stress","stress_calibration")) {
            assertThrows(IllegalArgumentException.class,()->new ScenarioSpec.SeedMetadata(1,phase,"Micro",0,1,2));
            for(String name:List.of("Static-V0-H1","Static-V1-H0","Static-V01-H1","Static-V1-H2147483648","Static-V2147483648-H1"))
                assertThrows(IllegalArgumentException.class,()->new ScenarioSpec.SeedMetadata(1,phase,name,0,1,2));
        }
    }
    @Test void isolatedBatchMatchesSeparateSingleVmSimulators() {
        var original=ScenarioGenerator.inputs(123456,"main","Micro",0);
        var vms=new ArrayList<ScenarioSpec.VmSpec>(); var cloudlets=new ArrayList<ScenarioSpec.CloudletSpec>();
        for(int pes=1;pes<=2;pes++) for(long mips:new long[]{1000,2000,3000}) {
            int id=vms.size();
            vms.add(new ScenarioSpec.VmSpec(id,pes,mips,4096,1000,10000));
            cloudlets.add(new ScenarioSpec.CloudletSpec(id,id,pes,mips*(60+id*12)));
        }
        var input=new ScenarioSpec.Inputs(original.seeds(),original.hosts(),vms,cloudlets);
        var batch=ScenarioObjects.measureReferences(input);
        for(int i=0;i<input.vms().size();i++) {
            var v=input.vms().get(i); var c=input.cloudlets().get(i);
            var single=new ScenarioSpec.Inputs(input.seeds(),List.of(input.hosts().getFirst()),
                List.of(new ScenarioSpec.VmSpec(0,v.pes(),v.mipsPerPe(),v.ramMiB(),v.bwMbps(),v.storageMiB())),
                List.of(new ScenarioSpec.CloudletSpec(0,0,c.pes(),c.lengthMi())));
            assertEquals(batch.get(i),ScenarioObjects.measureReferences(single).getFirst());
        }
    }

    @Test void nativeStartArrivalAndFinishUseServiceTimeRatherThanAbsoluteClock() {
        var input=new ScenarioSpec.Inputs(ScenarioGenerator.inputs(1,"main","Micro",0).seeds(),
            List.of(new ScenarioSpec.HostSpec(0,16,3000,32768,10000,1000000,175,250)),
            List.of(new ScenarioSpec.VmSpec(0,2,2000,4096,1000,10000)),
            List.of(new ScenarioSpec.CloudletSpec(0,0,2,120000)));
        var objects=ScenarioObjects.create(input);
        new org.cloudsimplus.datacenters.DatacenterSimple(objects.simulation(),objects.hosts());
        var broker=new org.cloudsimplus.brokers.DatacenterBrokerSimple(objects.simulation());
        broker.setVmDestructionDelay(-1);
        broker.submitVmList(objects.vms()); broker.submitCloudletList(objects.cloudlets());
        objects.simulation().start();
        var cloudlet=objects.cloudlets().getFirst();
        System.out.printf(Locale.ROOT,"REFERENCE status=%s arrival=%s start=%s finish=%s service=%s%n",
            cloudlet.getStatus(),cloudlet.getDcArrivalTime(),cloudlet.getStartTime(),cloudlet.getFinishTime(),cloudlet.getFinishTime()-cloudlet.getStartTime());
        assertEquals(org.cloudsimplus.cloudlets.Cloudlet.Status.SUCCESS,cloudlet.getStatus());
        assertEquals(0.125,cloudlet.getDcArrivalTime());
        assertEquals(0.125,cloudlet.getStartTime());
        assertEquals(60.125,cloudlet.getFinishTime());
        assertEquals(List.of(60.0),ScenarioObjects.measureReferences(input));
        // Independent Python hashlib fixture, over the protocol's exact CSV bytes.
        assertEquals("1a0acd708a6cb1c64bc76418d126374b9c7de7e0d804d733de81cd0f6a0254a8",
            new ScenarioSpec(input,List.of(60.0)).fingerprint());
    }

    @Test void seedMatchesIndependentSha256Fixtures() {
        assertEquals(-3130559546828495159L, Seeds.derive(123456,"main","Micro",0,"scenario"));
        assertEquals(3884938460602371489L, Seeds.derive(123456,"main","Micro",0,"workload"));
        assertEquals(134775275101058480L, Seeds.derive(123456,"main","Micro",0,"optimizer","HO"));
        assertEquals(4035125502828838690L, Seeds.derive(123456,"main","Micro",0,"optimizer","GA"));
        assertEquals(-2508496779481226902L, Seeds.derive(123456,"sensitivity","Small",0,"scenario"));
    }

    @Test void generatedRangesAndIndependentStrictAndNativeWitnessAcrossMatrix() {
        for(String name:List.of("Micro","Small","Medium")) for(int r=0;r<30;r++) {
            var input=ScenarioGenerator.inputs(123456,"main",name,r);
            int count=switch(name){case "Micro" -> 10; case "Small" -> 50; default -> 100;};
            assertEquals(count,input.vms().size());
            Witness.validate(input);
            Witness.validateNative(input);
            for(int i=0;i<count;i++) {
                var vm=input.vms().get(i); var c=input.cloudlets().get(i);
                assertEquals(i,vm.id()); assertTrue(vm.pes()==1 || vm.pes()==2);
                assertTrue(List.of(1000L,2000L,3000L).contains(vm.mipsPerPe()));
                assertTrue(List.of(1024L,2048L,4096L).contains(vm.ramMiB()));
                assertEquals(vm.pes(),c.pes()); assertEquals(i,c.vmId());
                assertEquals(0,c.lengthMi()%vm.mipsPerPe());
                assertTrue(c.lengthMi()/vm.mipsPerPe()>=60 && c.lengthMi()/vm.mipsPerPe()<=120);
            }
        }
    }

    @Test void referencesAreMeasuredReproduciblyAndObjectsAreFresh() {
        var input=ScenarioGenerator.inputs(123456,"main","Micro",0);
        var a=ScenarioGenerator.generate(123456,"main","Micro",0);
        var b=ScenarioGenerator.generate(123456,"main","Micro",0);
        assertEquals(a,b);
        for(int i=0;i<input.vms().size();i++)
            assertEquals((double)input.cloudlets().get(i).lengthMi()/input.vms().get(i).mipsPerPe(),a.referenceSeconds().get(i),1e-8);
        assertEquals(10*Collections.max(a.referenceSeconds()),a.censorSeconds());
        var first=ScenarioObjects.create(input); var second=ScenarioObjects.create(input);
        assertNotSame(first.simulation(),second.simulation());
        assertNotSame(first.hosts().getFirst(),second.hosts().getFirst());
        assertNotSame(first.vms().getFirst(),second.vms().getFirst());
        assertNotSame(first.cloudlets().getFirst(),second.cloudlets().getFirst());
        assertNotSame(first.vms().getFirst().getCloudletScheduler(),second.vms().getFirst().getCloudletScheduler());
        assertNotSame(first.hosts().getFirst().getVmScheduler(),second.hosts().getFirst().getVmScheduler());
        first.vms().getFirst().setRam(1);
        assertEquals(input.vms().getFirst().ramMiB(),second.vms().getFirst().getRam().getCapacity());
        assertThrows(UnsupportedOperationException.class,()->input.vms().clear());
        assertThrows(UnsupportedOperationException.class,()->a.referenceSeconds().clear());
        assertNotEquals(a.fingerprint(),ScenarioGenerator.generate(123457,"main","Micro",0).fingerprint());
        var refs=new ArrayList<>(a.referenceSeconds()); refs.set(0,refs.getFirst()+1);
        var changed=new ScenarioSpec(input,refs);
        assertNotEquals(a.fingerprint(),changed.fingerprint());
        assertTrue(a.canonicalText().endsWith("utilization_model,constant_full\n"));
        assertTrue(a.canonicalText().contains("host,0,16,3000,32768,10000,1000000,175,250,,\n"));
        assertTrue(a.canonicalText().contains("cloudlet,0,"+input.cloudlets().getFirst().pes()+",,,,,,,0,"+input.cloudlets().getFirst().lengthMi()+"\n"));
    }

    @Test void invalidResourcesReferencesAndWitnessAreRejected() {
        assertThrows(IllegalArgumentException.class,()->ScenarioGenerator.inputs(1,"main","Huge",0));
        assertThrows(IllegalArgumentException.class,()->ScenarioGenerator.inputs(1,"main","Micro",-1));
        assertThrows(IllegalArgumentException.class,()->new ScenarioSpec.VmSpec(0,0,1000,1024,1000,10000));
        assertThrows(ArithmeticException.class,()->new ScenarioSpec.VmSpec(0,2,Long.MAX_VALUE,1024,1000,10000));
        var input=ScenarioGenerator.inputs(1,"main","Micro",0);
        assertThrows(IllegalArgumentException.class,()->new ScenarioSpec(input,List.of(Double.NaN)));
        var hosts=new ArrayList<>(input.hosts());
        hosts.set(0,new ScenarioSpec.HostSpec(0,1,3000,32768,10000,1000000,175,250));
        var bad=new ScenarioSpec.Inputs(input.seeds(),hosts,input.vms(),input.cloudlets());
        assertThrows(IllegalArgumentException.class,()->Witness.validate(bad));
        assertThrows(IllegalArgumentException.class,()->Witness.validateNative(bad));
    }

    @Test void strictWitnessRejectsEveryResourceDimensionAndAllowsEquality() {
        var seeds=ScenarioGenerator.inputs(1,"main","Micro",0).seeds();
        var vm=new ScenarioSpec.VmSpec(0,2,2000,4096,1000,10000);
        var cloudlet=new ScenarioSpec.CloudletSpec(0,0,2,120000);
        var exact=new ScenarioSpec.HostSpec(0,2,2000,4096,1000,10000,175,250);
        var input=new ScenarioSpec.Inputs(seeds,List.of(exact),List.of(vm),List.of(cloudlet));
        Witness.validate(input); Witness.validateNative(input);
        for(var host:List.of(
            new ScenarioSpec.HostSpec(0,1,2000,4096,1000,10000,175,250),
            new ScenarioSpec.HostSpec(0,4,1999,4096,1000,10000,175,250),
            new ScenarioSpec.HostSpec(0,2,2000,4095,1000,10000,175,250),
            new ScenarioSpec.HostSpec(0,2,2000,4096,999,10000,175,250),
            new ScenarioSpec.HostSpec(0,2,2000,4096,1000,9999,175,250))) {
            var bad=new ScenarioSpec.Inputs(seeds,List.of(host),List.of(vm),List.of(cloudlet));
            assertThrows(IllegalArgumentException.class,()->Witness.validate(bad));
            assertThrows(IllegalArgumentException.class,()->Witness.validateNative(bad));
        }
    }

    @Test void allScenarioReferenceBatchesHaveAnalyticalServiceTimesAndPhaseSeedsAreDisjoint() {
        for(String name:List.of("Micro","Small","Medium")) {
            var spec=ScenarioGenerator.generate(123456,"main",name,29);
            for(int i=0;i<spec.inputs().vms().size();i++)
                assertEquals((double)spec.inputs().cloudlets().get(i).lengthMi()/spec.inputs().vms().get(i).mipsPerPe(),spec.referenceSeconds().get(i));
        }
        for(int r=0;r<10;r++) {
            var main=ScenarioGenerator.inputs(123456,"main","Small",r);
            var sensitivity=ScenarioGenerator.inputs(123456,"sensitivity","Small",r);
            Witness.validate(sensitivity); Witness.validateNative(sensitivity);
            assertNotEquals(main.seeds().scenarioSeed(),sensitivity.seeds().scenarioSeed());
            assertNotEquals(main.seeds().workloadSeed(),sensitivity.seeds().workloadSeed());
        }
    }
}
