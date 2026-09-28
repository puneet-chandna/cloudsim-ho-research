package org.puneet.cloudsimplus.hiippo.scenario;

import java.util.*;
import org.puneet.cloudsimplus.hiippo.scenario.ScenarioSpec.*;

public final class ScenarioGenerator {
    private ScenarioGenerator() {}
    public static Inputs inputs(long master,String phase,String name,int replication) {
        int count=switch(name) { case "Micro" -> 10; case "Small" -> 50; case "Medium" -> 100; default -> throw new IllegalArgumentException("Unknown scenario: "+name); };
        int hostCount=name.equals("Micro")?3:count/5;
        var seeds=new SeedMetadata(master,phase,name,replication,Seeds.derive(master,phase,name,replication,"scenario"),Seeds.derive(master,phase,name,replication,"workload"));
        var scenario=new Random(seeds.scenarioSeed()); var workload=new Random(seeds.workloadSeed());
        var hosts=new ArrayList<HostSpec>(); var vms=new ArrayList<VmSpec>(); var cloudlets=new ArrayList<CloudletSpec>();
        for(int i=0;i<hostCount;i++) hosts.add(new HostSpec(i,16,3000,32768,10000,1000000,new int[]{175,210,280}[i%3],new int[]{250,300,400}[i%3]));
        for(int i=0;i<count;i++) {
            int pes=1+scenario.nextInt(2); long mips=1000L*(1+scenario.nextInt(3)); long ram=1024L<<scenario.nextInt(3);
            vms.add(new VmSpec(i,pes,mips,ram,1000,10000));
            cloudlets.add(new CloudletSpec(i,i,pes,Math.multiplyExact(mips,60L+workload.nextInt(61))));
        }
        var inputs=new Inputs(seeds,hosts,vms,cloudlets);
        Witness.validate(inputs);
        return inputs;
    }
    public static ScenarioSpec generate(long master,String phase,String name,int replication) {
        var inputs=inputs(master,phase,name,replication);
        Witness.validateNative(inputs);
        return new ScenarioSpec(inputs,ScenarioObjects.measureReferences(inputs));
    }
}
