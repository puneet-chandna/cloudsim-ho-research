package org.puneet.cloudsimplus.hiippo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;

/**
 * Unit test for simple App.
 */
public class AppTest 
{
    @TempDir Path temp;
    /**
     * Rigorous Test :-)
     */
    @Test
    void sanityCheck() {
        assertTrue(true, "Sanity check should always pass");
    }
    @Test void invalidJvmWorkerSettingIsAConfigurationErrorWithoutArtifacts() throws Exception {
        String previous=System.getProperty("cloudsim.workers");
        try {
            System.setProperty("cloudsim.workers","auto");
            assertEquals(2,App.run(new String[]{"--profile","smoke","--output-dir",temp.toString()}));
            assertEquals(2,App.run(new String[]{"--profile","stress","--vms","10","--hosts","3","--population","4","--iterations","2",
                "--replications","1","--seed","123456","--experiment-phase","stress","--output-dir",temp.toString()}));
            try(var files=Files.list(temp)) { assertEquals(0,files.count()); }
        } finally { if(previous==null) System.clearProperty("cloudsim.workers"); else System.setProperty("cloudsim.workers",previous); }
    }
}
