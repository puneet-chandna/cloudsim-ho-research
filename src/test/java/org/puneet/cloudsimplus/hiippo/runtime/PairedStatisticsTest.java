package org.puneet.cloudsimplus.hiippo.runtime;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.Gson;
import org.puneet.cloudsimplus.hiippo.scenario.Seeds;
import static org.junit.jupiter.api.Assertions.*;

class PairedStatisticsTest {
    @TempDir Path temp;
    @Test void typeSevenInterpolatesAndRejectsInvalidQuantiles() {
        assertEquals(1.75,PairedStatistics.quantile(new double[]{1,2,4,8},.25));
        assertEquals(8,PairedStatistics.quantile(new double[]{1,2,4,8},1));
        assertThrows(IllegalArgumentException.class,()->PairedStatistics.quantile(new double[]{1},Double.NaN));
    }
    @Test void fixedSignsUseMarginCenteredStudentizationAndCountUndefinedDraws() {
        // x=[-3,-2,-1], mean=-2, sd=1, observed t=-sqrt(12).
        // Residuals [-1,0,1]: +++ and --- give t=0; ++- gives -2; -++ gives +2.
        var signs=new Random() { int i; final boolean[] tape={true,true,true,false,false,false,true,true,false,false,true,true};
            public boolean nextBoolean() { return tape[i++]; } };
        assertEquals(.2,PairedStatistics.wild(new double[]{-3,-2,-1},0,signs,4));
        // [-1,1] and opposite signs yield constant [-1,-1] / [1,1]: both undefined, both extreme.
        var undefined=new Random() { int i; public boolean nextBoolean() { return i++%2==0; } };
        assertEquals(1,PairedStatistics.wild(new double[]{-1,1},0,undefined,2));
    }
    @Test void bcaFixedIndicesExposeBiasAccelerationAndInterpolation() {
        var indices=new Random() { int i; final int[] tape={0,0,0,0,0,1,0,1,2,1,2,2,2,2,2};
            public int nextInt(int bound) { return tape[i++]; } };
        var bca=PairedStatistics.bca(new double[]{1,2,3},indices,5);
        assertTrue(bca.valid());
        assertEquals(-.2533471031357997,bca.bias(),1e-12); // Phi^-1(2/5), strictly below mean 2.
        assertEquals(0,bca.acceleration(),1e-15); // leave-one means [2.5,2,1.5].
        assertEquals(1.0090920315474858,bca.at(.025),1e-9); // 1+(4/3)*Phi(2*Phi^-1(.4)+Phi^-1(.025)).
    }
    @Test void degenerateBcaAndWildNeverInventVariance() {
        assertEquals(1,PairedStatistics.wild(new double[]{0,0,0},.01,new Random(1),10));
        assertFalse(PairedStatistics.bca(new double[]{0,0,0},new Random(1),10).valid());
        var allHigh=new Random() { public int nextInt(int n) { return n-1; } };
        assertFalse(PairedStatistics.bca(new double[]{1,2,3},allHigh,10).valid());
        var allLow=new Random() { public int nextInt(int n) { return 0; } };
        assertFalse(PairedStatistics.bca(new double[]{1,2,3},allLow,10).valid());
        assertEquals("BCA_JACKKNIFE_UNDEFINED",PairedStatistics.bca(new double[]{1e-110,2e-110,3e-110},new Random(1),100).reason());
        assertNull(new PairedStatistics.Bca(new double[]{1,2,3},0,1,"").at(.975));
    }
    @Test void holmRetainsAllEighteenAndIsMonotoneInSortedOrder() {
        double[] p=new double[18]; Arrays.fill(p,1); p[0]=.001; p[1]=.002; p[2]=.002;
        assertArrayEquals(new double[]{.018,.034,.034},Arrays.copyOf(PairedStatistics.holm(p),3),1e-15);
        assertThrows(IllegalArgumentException.class,()->PairedStatistics.holm(new double[]{.01}));
    }
    @Test void practicalEqualityIsAcceptedButNoninferiorityEqualityIsNot() {
        assertTrue(PairedStatistics.claim(.05,Math.log(.95),.009,true));
        assertFalse(PairedStatistics.claim(.05,Math.log(.95),.01,true));
        assertTrue(PairedStatistics.claim(.05,-.01,Math.log(1.05)-1e-9,false));
        assertFalse(PairedStatistics.claim(.05,-.01,Math.log(1.05),false));
    }
    @Test void knownDirectionMirrorsAndWrongPairCountIsRejected() {
        double[] negative=new double[30],positive=new double[30];
        for(int i=0;i<30;i++) { negative[i]=-.2+i*.001; positive[i]=-negative[i]; }
        var benefit=RunAnalysis.metric(negative,123456,"Micro","GA","energy");
        assertEquals("",benefit.reason()); assertEquals(-.1855,benefit.mean(),1e-14);
        assertEquals(1.0/1_000_001,benefit.p0()); assertTrue(benefit.high()<Math.log(.95));
        var harm=RunAnalysis.metric(positive,123456,"Micro","GA","energy");
        assertEquals(1,harm.p0()); assertTrue(harm.low()>0);
        assertThrows(IllegalArgumentException.class,()->RunAnalysis.metric(new double[29],123456,"Micro","GA","energy"));
    }
    @Test void independentPythonMatchesSeededAndExplicitResamplingFixtures() throws Exception {
        assertEquals(-2393751145582113234L,Seeds.derive(123456,"analysis","Micro",0,"wild","GA:energy:0"));
        assertEquals(-6600210811896074323L,Seeds.derive(123456,"analysis","Micro",0,"wild","GA:energy:ln1.05"));
        assertEquals(-6586808163191916131L,Seeds.derive(123456,"analysis","Micro",0,"bca","GA:energy:0"));
        assertEquals(601807479927619275L,Seeds.derive(123456,"analysis","Micro",0,"bca","GA:energy:ln1.05"));
        assertEquals(-7884584301533592766L,Seeds.derive(123456,"analysis","Micro",0,"bca","GA:sla:0.01"));
        double[] x=new double[30]; for(int i=0;i<30;i++) x[i]=.001*(i-17)+(i%3)*.0001;
        var records=new ArrayList<Map<String,Object>>();
        for(String name:List.of("energy","sla")) for(String margin:List.of("0",name.equals("energy")?"ln1.05":"0.01")) {
            long bs=Seeds.derive(123456,"analysis","Micro",0,"bca","GA:"+name+":"+margin),ws=Seeds.derive(123456,"analysis","Micro",0,"wild","GA:"+name+":"+margin);
            var b=PairedStatistics.bca(x,new Random(bs),10000);
            double m=margin.equals("0")?0:margin.equals("ln1.05")?Math.log(1.05):.01;
            records.add(Map.of("metric",name,"margin",margin,"bca_seed",bs,"wild_seed",ws,"bias",b.bias(),"acceleration",b.acceleration(),"low",b.at(.025),"high",b.at(.975),"upper",b.at(.95),"p",PairedStatistics.wild(x,m,new Random(ws),10000)));
        }
        var path=temp.resolve("synthetic-statistics-fixture.json");
        Files.writeString(path,new Gson().toJson(Map.of("x",x,"records",records,"analysis",RunAnalysis.render(Profile.research,123456,RunAnalysisTest.directionFixture()),"raw",RunAnalysisTest.directionFixture())));
        var process=new ProcessBuilder("python3","scripts/test_statistics_validator.py","--fixture",path.toString()).redirectErrorStream(true).start();
        String log=new String(process.getInputStream().readAllBytes()); assertEquals(0,process.waitFor(),log);
    }
}
