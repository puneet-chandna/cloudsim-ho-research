package org.puneet.cloudsimplus.hiippo;
import org.puneet.cloudsimplus.hiippo.runtime.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RunOutputTest {
    @TempDir Path temp;
    @ParameterizedTest @ValueSource(strings={"INFO","DEBUG"})
    void fastLogBurstsRotateAtSizeAndRetainOnlyThreeArchives(String level) throws Exception {
        Path logs;
        try(var output=RunOutput.create(new RunConfig(Profile.smoke,123456,level,temp))) {
            logs=output.directory().resolve("logs");
            var logger=LoggerFactory.getLogger("RotationRegression");
            var eventLevel=org.slf4j.event.Level.valueOf(level);
            logger.atLevel(eventLevel).log("OLDEST_RECORD");
            String payload="x".repeat(1024);
            for(int i=0;i<55000;i++) logger.atLevel(eventLevel).log("{} {}",i,payload);
            logger.atLevel(eventLevel).log("LATEST_RECORD");
        }
        try(var paths=Files.list(logs)) {
            assertEquals(Set.of("run.log","run.1.log","run.2.log","run.3.log"),
                new HashSet<>(paths.map(p->p.getFileName().toString()).toList()));
        }
        for(String name:List.of("run.log","run.1.log","run.2.log","run.3.log")) {
            var file=logs.resolve(name); var text=Files.readString(file);
            long largestRecord=text.lines().mapToLong(line->line.getBytes(StandardCharsets.UTF_8).length
                +System.lineSeparator().getBytes(StandardCharsets.UTF_8).length).max().orElseThrow();
            // Logback rolls before the next append; one whole encoded event may cross the threshold.
            assertTrue(Files.size(file)<=10*1024*1024+largestRecord,name);
            assertFalse(text.contains("OLDEST_RECORD"),name);
            assertEquals(name.equals("run.log"),text.contains("LATEST_RECORD"),name);
        }
    }
    @Test void runningThenFailedRetainsCountsAndCanonicalProperties() throws Exception {
        var config=new RunConfig(Profile.research,123456,"INFO",temp);
        try(var output=RunOutput.create(config)) {
            var path=output.directory().resolve("run.json");
            var running=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            assertEquals("RUNNING",running.get("state").getAsString());
            assertTrue(running.get("finished_at").isJsonNull());
            assertEquals(450,running.getAsJsonArray("cases").size());
            var lines=Files.readAllLines(output.directory().resolve("effective.properties"));
            assertEquals(lines.stream().sorted().toList(),lines);
            assertTrue(lines.stream().noneMatch(l->l.startsWith("#")));
            output.fail("TEST_FAILURE","diagnostic");
            var failed=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            assertEquals("FAILED",failed.get("state").getAsString());
            assertEquals(450,failed.get("unattempted_cases").getAsInt());
            assertEquals(0,failed.get("failed_cases").getAsInt());
            assertFalse(Files.exists(output.directory().resolve("analysis")));
            try(var paths=Files.list(output.directory())) { assertTrue(paths.noneMatch(p->p.toString().endsWith(".tmp"))); }
        }
    }
}
