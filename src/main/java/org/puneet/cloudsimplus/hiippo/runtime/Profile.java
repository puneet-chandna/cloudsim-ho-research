package org.puneet.cloudsimplus.hiippo.runtime;
import java.util.*;

public enum Profile {
    smoke(10,4,1,List.of("Micro")), explore(20,20,5,List.of("Micro","Small")),
    research(30,40,30,List.of("Micro","Small","Medium"));
    public final int population, iterations, replications;
    public final List<String> scenarios;
    Profile(int population,int iterations,int replications,List<String> scenarios) {
        this.population=population; this.iterations=iterations; this.replications=replications; this.scenarios=scenarios;
    }
    public int budget() { return population + 3*population*iterations; }
    public record CaseKey(String phase,String scenario,int replication,String algorithm,Integer population,Integer iterations) {}
    public List<CaseKey> cases() {
        var cases = new ArrayList<CaseKey>();
        for(var scenario:scenarios) for(int r=0;r<replications;r++)
            for(var a:List.of("HO","GA","FirstFit","BestFit")) {
                boolean search=a.equals("HO") || a.equals("GA");
                cases.add(new CaseKey("main",scenario,r,a,search?population:null,search?iterations:null));
            }
        if(this==research) for(int r=0;r<10;r++) {
            for(int n:new int[]{10,20,30,40,50}) cases.add(new CaseKey("sensitivity","Small",r,"HO",n,40));
            for(int t:new int[]{20,30,50,60}) cases.add(new CaseKey("sensitivity","Small",r,"HO",30,t));
        }
        return List.copyOf(cases);
    }
}
