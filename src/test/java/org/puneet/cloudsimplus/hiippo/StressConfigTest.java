package org.puneet.cloudsimplus.hiippo;

import org.junit.jupiter.api.Test;
import org.puneet.cloudsimplus.hiippo.runtime.StressConfig;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StressConfigTest {
    private static String[] arguments() {
        return new String[]{"--profile","stress","--vms","17","--hosts","4","--population","6",
            "--iterations","7","--replications","3","--seed","-123","--experiment-phase","stress",
            "--output-dir","custom-output"};
    }
    private static String[] with(String flag,String value) {
        var args=arguments();
        args[Arrays.asList(args).indexOf(flag)+1]=value;
        return args;
    }

    @Test void explicitFieldsDetermineCountsAndEffectiveConfiguration() {
        var c=StressConfig.parse(arguments());
        assertEquals(17,c.vmCount()); assertEquals(4,c.hostCount());
        assertEquals(6,c.population()); assertEquals(7,c.iterations()); assertEquals(3,c.replications());
        assertEquals(-123,c.masterSeed()); assertEquals("stress",c.experimentPhase());
        assertEquals(Path.of("custom-output"),c.outputRoot());
        assertEquals(132,c.evaluationBudget()); assertEquals(12,c.expectedCases());
        assertEquals(Map.ofEntries(
            Map.entry("profile","stress"),Map.entry("experiment.kind","static_stress"),
            Map.entry("stress.schema.version","1"),Map.entry("experiment.phase","stress"),
            Map.entry("vm.count","17"),Map.entry("host.count","4"),Map.entry("population","6"),
            Map.entry("iterations","7"),Map.entry("replications","3"),Map.entry("master.seed","-123"),
            Map.entry("evaluation.budget","132"),Map.entry("expected.cases","12"),
            Map.entry("scenarios","Static-V17-H4"),Map.entry("algorithms","HO,GA,FirstFit,BestFit")),c.effective());
        assertThrows(UnsupportedOperationException.class,()->c.effective().put("population","2"));
    }

    @Test void everyFieldIsRequiredOnceAndUnknownOptionsAreRejected() {
        var args=arguments();
        for(int i=0;i<args.length;i+=2) {
            String flag=args[i];
            var missing=new ArrayList<>(List.of(args)); missing.subList(i,i+2).clear();
            assertThrows(IllegalArgumentException.class,()->StressConfig.parse(missing.toArray(String[]::new)),args[i]);
            var duplicate=new ArrayList<>(List.of(args)); duplicate.add(args[i]); duplicate.add(args[i+1]);
            assertThrows(IllegalArgumentException.class,()->StressConfig.parse(duplicate.toArray(String[]::new)),args[i]);
            assertThrows(IllegalArgumentException.class,()->StressConfig.parse(with(flag,"")),flag);
        }
        for(String flag:List.of("--preset","--config","--debug","--time-limit","--heap-mib","--unknown")) {
            var unknown=new ArrayList<>(List.of(args)); unknown.add(flag); unknown.add("1");
            assertThrows(IllegalArgumentException.class,()->StressConfig.parse(unknown.toArray(String[]::new)),flag);
        }
        assertThrows(IllegalArgumentException.class,()->StressConfig.parse(new String[]{"--profile"}));
        assertThrows(IllegalArgumentException.class,()->StressConfig.parse(with("--vms","--hosts")));
        assertThrows(IllegalArgumentException.class,()->StressConfig.parse(with("--profile","research")));
    }

    @Test void signedDecimalSeedsAndBothIsolatedPhasesAreAccepted() {
        assertEquals(Long.MIN_VALUE,StressConfig.parse(with("--seed","-9223372036854775808")).masterSeed());
        assertEquals(Long.MAX_VALUE,StressConfig.parse(with("--seed","+9223372036854775807")).masterSeed());
        assertEquals("stress_calibration",StressConfig.parse(with("--experiment-phase","stress_calibration")).experimentPhase());
        for(String value:List.of("main","sensitivity","calibration","Stress"))
            assertThrows(IllegalArgumentException.class,()->StressConfig.parse(with("--experiment-phase",value)));
        for(String value:List.of("9223372036854775808","-9223372036854775809","0x1","1.0"," 1"))
            assertThrows(IllegalArgumentException.class,()->StressConfig.parse(with("--seed",value)));
    }

    @Test void dimensionsSearchSettingsAndCountsAreValidatedWithoutOverflow() {
        for(String flag:List.of("--vms","--hosts","--population","--iterations","--replications"))
            for(String value:List.of("0","-1","2147483648","1.0","0x2"," 2"))
                assertThrows(IllegalArgumentException.class,()->StressConfig.parse(with(flag,value)),flag+"="+value);
        for(String value:List.of("1","3"))
            assertThrows(IllegalArgumentException.class,()->StressConfig.parse(with("--population",value)));
        assertEquals(8,new StressConfig(1,1,2,1,1,0,"stress",Path.of("out")).evaluationBudget());
        assertEquals(2147483642,new StressConfig(1,1,2,357913940,1,0,"stress",Path.of("out")).evaluationBudget());
        assertThrows(ArithmeticException.class,()->new StressConfig(1,1,2,357913941,1,0,"stress",Path.of("out")));
        assertThrows(ArithmeticException.class,()->StressConfig.parse(with("--iterations","2147483647")));
        assertThrows(ArithmeticException.class,()->StressConfig.parse(with("--population","2147483646")));
        assertThrows(ArithmeticException.class,()->StressConfig.parse(with("--replications","536870912")));
        assertEquals(2147483644,StressConfig.parse(with("--replications","536870911")).expectedCases());
        assertThrows(IllegalArgumentException.class,()->new StressConfig(0,1,2,1,1,0,"stress",Path.of("out")));
        assertThrows(IllegalArgumentException.class,()->new StressConfig(1,1,3,1,1,0,"stress",Path.of("out")));
        assertThrows(IllegalArgumentException.class,()->new StressConfig(1,1,2,1,1,0,"main",Path.of("out")));
        assertThrows(IllegalArgumentException.class,()->new StressConfig(1,1,2,1,1,0,"stress",Path.of("")));
    }
}
