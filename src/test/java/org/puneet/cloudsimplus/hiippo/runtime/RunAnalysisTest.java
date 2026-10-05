package org.puneet.cloudsimplus.hiippo.runtime;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RunAnalysisTest {
    static List<Map<String,String>> fixture(Profile profile) {
        var rows=new ArrayList<Map<String,String>>();
        for(var k:profile.cases()) {
            var row=new LinkedHashMap<String,String>();
            row.put("schema_version","2"); row.put("run_id","synthetic-test-only"); row.put("phase",k.phase()); row.put("scenario",k.scenario());
            row.put("replication",""+k.replication()); row.put("algorithm",k.algorithm()); row.put("population",Objects.toString(k.population(),"")); row.put("iterations",Objects.toString(k.iterations(),""));
            row.put("status","RUN_OK"); row.put("scenario_sha256",k.phase()+k.scenario()+k.replication()); row.put("workload_seed",""+k.replication());
            row.put("energy_j",""+(100+k.replication())); row.put("sla_rate","0.0"); row.put("allocation_wall_ns","100"); rows.add(row);
        }
        return rows;
    }
    static List<Map<String,String>> directionFixture() {
        var rows=fixture(Profile.research);
        for(var row:rows) if(row.get("scenario").equals("Micro") && row.get("algorithm").equals("GA")) {
            int r=Integer.parseInt(row.get("replication"));
            row.put("energy_j",Double.toString((100+r)*Math.exp(.2-.001*r)));
            row.put("sla_rate",Double.toString(.04+.001*r));
        }
        return rows;
    }
    @Test void onlyTheKnownBeneficialPairPassesBothCompositeClaims() throws Exception {
        var csv=RunAnalysis.render(Profile.research,123456,directionFixture()).get("analysis/pairwise_primary.csv");
        assertEquals(18,csv.lines().skip(1).count());
        assertEquals(2,csv.lines().filter(r->r.contains(",true,CLAIM,ALL_GATES_PASSED")).count());
        assertTrue(csv.lines().filter(r->r.contains(",true,CLAIM,")).allMatch(r->r.contains(",Micro,GA,30,")));
    }
    @Test void researchRetainsEighteenDegenerateClaimsAndNineDistinctSensitivityRows() throws Exception {
        var files=RunAnalysis.render(Profile.research,123456,fixture(Profile.research));
        var claims=files.get("analysis/pairwise_primary.csv").lines().skip(1).toList();
        assertEquals(18,claims.size());
        assertTrue(claims.stream().allMatch(s->s.contains(",1.0,1.0,1.0,1.0,false,NO_CLAIM,")));
        var sensitivity=files.get("analysis/sensitivity_summary.csv").lines().skip(1).toList();
        assertEquals(9,sensitivity.size()); assertEquals(1,sensitivity.stream().filter(s->s.contains(",Small,30,40,10,")).count());
    }
    @Test void invalidMatricesInputsAndPairingPreventAnyAnalysis() {
        for(String mutation:List.of("missing","duplicate","energyZero","energyNegative","pair","reorder")) {
            var rows=fixture(Profile.research);
            switch(mutation) {
                case "missing"->rows.removeLast(); case "duplicate"->rows.set(1,rows.getFirst());
                case "energyZero"->rows.getFirst().put("energy_j","0"); case "energyNegative"->rows.getFirst().put("energy_j","-1");
                case "pair"->rows.get(1).put("scenario_sha256","different"); case "reorder"->Collections.swap(rows,0,1);
            }
            assertThrows(IllegalArgumentException.class,()->RunAnalysis.render(Profile.research,123456,rows),mutation);
        }
    }
    @Test void exploreIsDescriptiveAndZeroRuntimeIsExplicitlyUnavailable() throws Exception {
        var files=RunAnalysis.render(Profile.explore,123456,fixture(Profile.explore));
        assertEquals(Set.of("analysis/scenario_summary.csv","analysis/report.md"),files.keySet());
        assertEquals(9,files.get("analysis/scenario_summary.csv").lines().count());
        var rows=fixture(Profile.research); rows.getFirst().put("allocation_wall_ns","0");
        var runtime=RunAnalysis.render(Profile.research,123456,rows).get("analysis/runtime_secondary.csv");
        assertTrue(runtime.contains(",Micro,GA,30,,,,INVALID_RUNTIME\n"));
    }
}
