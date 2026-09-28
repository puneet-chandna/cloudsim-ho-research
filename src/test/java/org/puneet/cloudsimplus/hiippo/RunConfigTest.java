package org.puneet.cloudsimplus.hiippo;

import org.puneet.cloudsimplus.hiippo.runtime.RunConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import org.puneet.cloudsimplus.hiippo.runtime.Profile;
import static org.junit.jupiter.api.Assertions.*;

class RunConfigTest {
    @TempDir Path temp;
    @Test void profilesHaveFrozenBudgetsAndMatrices() throws Exception {
        var names=new String[]{"smoke","explore","research"};
        var budgets=new int[]{130,1220,3630}; var cases=new int[]{4,40,450};
        for(int i=0;i<3;i++) {
            var c=RunConfig.parse(new String[]{"--profile",names[i]});
            assertEquals(budgets[i],c.profile().budget());
            assertEquals(cases[i],c.profile().cases().size());
            var matrix=c.profile().cases();
            assertEquals(matrix.size(),new HashSet<>(matrix).size());
            int index=0;
            for(var scenario:c.profile().scenarios) for(int r=0;r<c.profile().replications;r++)
                for(var algorithm:List.of("HO","GA","FirstFit","BestFit")) {
                    boolean search=algorithm.equals("HO") || algorithm.equals("GA");
                    assertEquals(new Profile.CaseKey("main",scenario,r,algorithm,search?c.profile().population:null,search?c.profile().iterations:null),matrix.get(index++));
                }
            if(names[i].equals("research")) for(int r=0;r<10;r++) {
                var settings=new HashSet<String>();
                for(var setting:List.of("10/40","20/40","30/40","40/40","50/40","30/20","30/30","30/50","30/60")) {
                    var key=matrix.get(index++);
                    assertEquals("sensitivity",key.phase()); assertEquals("Small",key.scenario());
                    assertEquals("HO",key.algorithm()); assertEquals(r,key.replication());
                    assertEquals(setting,key.population()+"/"+key.iterations()); settings.add(setting);
                }
                assertEquals(9,settings.size());
            }
            assertEquals(matrix.size(),index);
        }
    }
    @Test void propertiesAreStrictAndCliWins() throws Exception {
        var p=temp.resolve("overlay.properties");
        Files.writeString(p,"master.seed=-9223372036854775808\nlog.level=INFO\n");
        var c=RunConfig.parse(new String[]{"--profile","smoke","--config",p.toString(),"--debug"});
        assertEquals(Long.MIN_VALUE,c.masterSeed()); assertEquals("DEBUG",c.logLevel());
        for(var content:new String[]{"master.seed=1\nmaster\\u002eseed=2", "output.dir=bad", "log.level=warn", "master.seed=9223372036854775808", "master.seed=0x1", "log\\.level=INFO\nlog.level=DEBUG", "clé=1"}) {
            Files.writeString(p,content);
            assertThrows(IllegalArgumentException.class,()->RunConfig.parse(new String[]{"--profile","smoke","--config",p.toString()}),content);
        }
        assertFalse(Files.exists(temp.resolve("results")));
    }
}
