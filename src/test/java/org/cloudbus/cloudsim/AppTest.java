package org.cloudbus.cloudsim;

import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.ConfigurationManager;
import org.cloudbus.cloudsim.util.ExperimentException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for the Hippopotamus Optimization CloudSim Research Application
 * 
 * This test class validates the main application entry point, command-line argument processing,
 * configuration management, and proper initialization of the research pipeline.
 * 
 * @author Puneet Chandna
 * @version 1.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Hippopotamus Optimization CloudSim Application Tests")
public class AppTest {
    
    @TempDir
    Path tempDir;
    
    private static ByteArrayOutputStream outputStream;
    private static ByteArrayOutputStream errorStream;
    private static PrintStream originalOut;
    private static PrintStream originalErr;
    
    /**
     * Set up test environment before all tests
     */
    @BeforeAll
    static void setupTestEnvironment() {
        // Save original streams
        originalOut = System.out;
        originalErr = System.err;
        
        // Initialize logging for tests
        System.setProperty("log.level", "DEBUG");
        System.setProperty("test.mode", "true");
    }
    
    /**
     * Set up before each test
     */
    @BeforeEach
    void setUp() {
        // Redirect system output for testing
        outputStream = new ByteArrayOutputStream();
        errorStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
        System.setErr(new PrintStream(errorStream));
    }
    
    /**
     * Clean up after each test
     */
    @AfterEach
    void tearDown() {
        // Restore original streams
        System.setOut(originalOut);
        System.setErr(originalErr);
        
        // Clear any test configurations
        System.clearProperty("test.mode");
    }
    
    /**
     * Restore environment after all tests
     */
    @AfterAll
    static void cleanupTestEnvironment() {
        System.clearProperty("log.level");
        System.clearProperty("test.mode");
    }
    
    /**
     * Test 1: Verify application can be instantiated
     */
    @Test
    @Order(1)
    @DisplayName("Test application instantiation")
    void testApplicationInstantiation() {
        assertDoesNotThrow(() -> {
            App app = new App();
            assertNotNull(app, "App instance should not be null");
        });
    }
    
    /**
     * Test 2: Test application with no arguments (should show help)
     */
    @Test
    @Order(2)
    @DisplayName("Test application with no arguments")
    void testNoArguments() {
        // Run application with no arguments
        App.main(new String[]{});
        
        String output = outputStream.toString();
        
        // Verify help message is displayed
        assertTrue(output.contains("Hippopotamus Optimization") || 
                  output.contains("Usage:") || 
                  output.contains("--help"),
                  "Should display help or usage information");
    }
    
    /**
     * Test 3: Test help command
     */
    @Test
    @Order(3)
    @DisplayName("Test help command")
    void testHelpCommand() {
        String[] args = {"--help"};
        
        App.main(args);
        
        String output = outputStream.toString();
        
        // Verify help content
        assertTrue(output.contains("Hippopotamus Optimization CloudSim Research Framework"),
                  "Should display framework name");
        assertTrue(output.contains("Usage:"), "Should contain usage information");
        assertTrue(output.contains("Options:"), "Should contain options");
        assertTrue(output.contains("--config"), "Should mention config option");
        assertTrue(output.contains("--experiment"), "Should mention experiment option");
    }
    
    /**
     * Test 4: Test version command
     */
    @Test
    @Order(4)
    @DisplayName("Test version command")
    void testVersionCommand() {
        String[] args = {"--version"};
        
        App.main(args);
        
        String output = outputStream.toString();
        
        assertTrue(output.contains("Version") || output.contains("v1.0"),
                  "Should display version information");
    }
    
    /**
     * Test 5: Test configuration file loading
     */
    @Test
    @Order(5)
    @DisplayName("Test configuration file loading")
    void testConfigurationLoading() throws IOException {
        // Create a test configuration file
        Path configFile = tempDir.resolve("test_config.yaml");
        String configContent = """
            experiments:
              algorithms:
                - name: "HippopotamusOptimization"
                  parameters:
                    population_size: 50
                    max_iterations: 100
              datasets:
                - "test_dataset"
              metrics:
                - "resource_utilization"
                - "power_consumption"
            """;
        Files.writeString(configFile, configContent);
        
        String[] args = {"--config", configFile.toString(), "--validate"};
        
        App.main(args);
        
        String output = outputStream.toString();
        
        assertTrue(output.contains("Configuration validated successfully") ||
                  output.contains("Config loaded"),
                  "Should validate configuration successfully");
    }
    
    /**
     * Test 6: Test invalid configuration file
     */
    @Test
    @Order(6)
    @DisplayName("Test invalid configuration file")
    void testInvalidConfigurationFile() {
        String[] args = {"--config", "non_existent_file.yaml"};
        
        App.main(args);
        
        String output = outputStream.toString();
        String error = errorStream.toString();
        
        assertTrue(output.contains("Error") || error.contains("Error"),
                  "Should report configuration error");
        assertTrue(output.contains("not found") || error.contains("not found"),
                  "Should indicate file not found");
    }
    
    /**
     * Test 7: Test experiment modes
     */
    @ParameterizedTest
    @Order(7)
    @ValueSource(strings = {"quick", "standard", "comprehensive", "scalability", "comparison"})
    @DisplayName("Test different experiment modes")
    void testExperimentModes(String mode) {
        // Create minimal config for testing
        Path configFile = createMinimalConfig();
        
        String[] args = {"--config", configFile.toString(), "--experiment", mode, "--dry-run"};
        
        assertDoesNotThrow(() -> App.main(args),
                          "Should handle experiment mode: " + mode);
        
        String output = outputStream.toString();
        assertTrue(output.contains(mode) || output.contains("Experiment mode"),
                  "Should acknowledge experiment mode: " + mode);
    }
    
    /**
     * Test 8: Test dataset specification
     */
    @Test
    @Order(8)
    @DisplayName("Test dataset specification")
    void testDatasetSpecification() throws IOException {
        // Create test dataset directory
        Path datasetDir = tempDir.resolve("datasets");
        Path azureDir = datasetDir.resolve("azure_traces");
        Files.createDirectories(azureDir);
        
        // Create a dummy trace file
        Path traceFile = azureDir.resolve("azure_traces.csv");
        String traceContent = """
            id,vmTypeId,machineId,core,memory,hdd,ssd,nic
            vm1,type1,machine1,4,8192,100,50,1
            vm2,type1,machine2,8,16384,200,100,2
            """;
        Files.writeString(traceFile, traceContent);
        
        Path configFile = createMinimalConfig();
        
        String[] args = {
            "--config", configFile.toString(),
            "--dataset", "azure",
            "--dataset-path", datasetDir.toString(),
            "--validate"
        };
        
        App.main(args);
        
        String output = outputStream.toString();
        assertTrue(output.contains("dataset") || output.contains("azure"),
                  "Should process dataset specification");
    }
    
    /**
     * Test 9: Test output directory specification
     */
    @Test
    @Order(9)
    @DisplayName("Test output directory specification")
    void testOutputDirectory() {
        Path outputDir = tempDir.resolve("results");
        Path configFile = createMinimalConfig();
        
        String[] args = {
            "--config", configFile.toString(),
            "--output", outputDir.toString(),
            "--dry-run"
        };
        
        App.main(args);
        
        assertTrue(Files.exists(outputDir) || outputStream.toString().contains("output"),
                  "Should handle output directory specification");
    }
    
    /**
     * Test 10: Test parallel execution option
     */
    @Test
    @Order(10)
    @DisplayName("Test parallel execution option")
    void testParallelExecution() {
        Path configFile = createMinimalConfig();
        
        String[] args = {
            "--config", configFile.toString(),
            "--parallel", "4",
            "--dry-run"
        };
        
        App.main(args);
        
        String output = outputStream.toString();
        assertTrue(output.contains("parallel") || output.contains("threads"),
                  "Should acknowledge parallel execution setting");
    }
    
    /**
     * Test 11: Test seed specification for reproducibility
     */
    @Test
    @Order(11)
    @DisplayName("Test seed specification for reproducibility")
    void testSeedSpecification() {
        Path configFile = createMinimalConfig();
        
        String[] args = {
            "--config", configFile.toString(),
            "--seed", "12345",
            "--dry-run"
        };
        
        App.main(args);
        
        String output = outputStream.toString();
        assertTrue(output.contains("seed") || output.contains("12345"),
                  "Should acknowledge seed specification");
    }
    
    /**
     * Test 12: Test multiple algorithm comparison
     */
    @Test
    @Order(12)
    @DisplayName("Test multiple algorithm comparison")
    void testMultipleAlgorithms() {
        Path configFile = createMinimalConfig();
        
        String[] args = {
            "--config", configFile.toString(),
            "--algorithms", "HO,GA,PSO",
            "--dry-run"
        };
        
        App.main(args);
        
        String output = outputStream.toString();
        assertTrue(output.contains("algorithm") || output.contains("comparison"),
                  "Should handle multiple algorithm specification");
    }
    
    /**
     * Test 13: Test statistical analysis options
     */
    @Test
    @Order(13)
    @DisplayName("Test statistical analysis options")
    void testStatisticalAnalysis() {
        Path configFile = createMinimalConfig();
        
        String[] args = {
            "--config", configFile.toString(),
            "--statistical-analysis",
            "--confidence-level", "0.95",
            "--dry-run"
        };
        
        App.main(args);
        
        String output = outputStream.toString();
        assertTrue(output.contains("statistical") || output.contains("confidence"),
                  "Should enable statistical analysis");
    }
    
    /**
     * Test 14: Test report generation options
     */
    @ParameterizedTest
    @Order(14)
    @ValueSource(strings = {"pdf", "latex", "excel", "csv"})
    @DisplayName("Test report generation formats")
    void testReportFormats(String format) {
        Path configFile = createMinimalConfig();
        
        String[] args = {
            "--config", configFile.toString(),
            "--report-format", format,
            "--dry-run"
        };
        
        App.main(args);
        
        String output = outputStream.toString();
        assertTrue(output.contains(format) || output.contains("report"),
                  "Should handle report format: " + format);
    }
    
    /**
     * Test 15: Test environment validation
     */
    @Test
    @Order(15)
    @DisplayName("Test environment validation")
    void testEnvironmentValidation() {
        String[] args = {"--validate-env"};
        
        App.main(args);
        
        String output = outputStream.toString();
        
        // Should check Java version, memory, etc.
        assertTrue(output.contains("Java") || output.contains("environment"),
                  "Should validate environment");
        assertTrue(output.contains("Memory") || output.contains("RAM"),
                  "Should check available memory");
    }
    
    /**
     * Test 16: Test error handling for invalid arguments
     */
    @ParameterizedTest
    @Order(16)
    @MethodSource("provideInvalidArguments")
    @DisplayName("Test error handling for invalid arguments")
    void testInvalidArguments(String[] args, String expectedError) {
        App.main(args);
        
        String output = outputStream.toString();
        String error = errorStream.toString();
        
        assertTrue(output.contains(expectedError) || error.contains(expectedError),
                  "Should report error: " + expectedError);
    }
    
    /**
     * Test 17: Test logging configuration
     */
    @Test
    @Order(17)
    @DisplayName("Test logging configuration")
    void testLoggingConfiguration() {
        Path configFile = createMinimalConfig();
        
        String[] args = {
            "--config", configFile.toString(),
            "--log-level", "DEBUG",
            "--dry-run"
        };
        
        App.main(args);
        
        String output = outputStream.toString();
        assertTrue(output.contains("DEBUG") || output.contains("log"),
                  "Should configure logging level");
    }
    
    /**
     * Test 18: Test batch experiment execution
     */
    @Test
    @Order(18)
    @DisplayName("Test batch experiment execution")
    void testBatchExecution() throws IOException {
        // Create batch configuration
        Path batchFile = tempDir.resolve("batch_experiments.yaml");
        String batchContent = """
            batch_experiments:
              - name: "Experiment1"
                algorithm: "HO"
                dataset: "synthetic"
                vms: 100
              - name: "Experiment2"
                algorithm: "GA"
                dataset: "synthetic"
                vms: 200
            """;
        Files.writeString(batchFile, batchContent);
        
        String[] args = {
            "--batch", batchFile.toString(),
            "--dry-run"
        };
        
        App.main(args);
        
        String output = outputStream.toString();
        assertTrue(output.contains("batch") || output.contains("experiments"),
                  "Should handle batch execution");
    }
    
    /**
     * Test 19: Test resume capability
     */
    @Test
    @Order(19)
    @DisplayName("Test experiment resume capability")
    void testResumeCapability() throws IOException {
        // Create checkpoint file
        Path checkpointDir = tempDir.resolve("checkpoints");
        Files.createDirectories(checkpointDir);
        Path checkpointFile = checkpointDir.resolve("experiment_checkpoint.json");
        String checkpointContent = """
            {
              "experiment_id": "exp_001",
              "completed_iterations": 50,
              "total_iterations": 100,
              "status": "interrupted"
            }
            """;
        Files.writeString(checkpointFile, checkpointContent);
        
        String[] args = {
            "--resume", checkpointFile.toString()
        };
        
        App.main(args);
        
        String output = outputStream.toString();
        assertTrue(output.contains("resume") || output.contains("checkpoint"),
                  "Should handle experiment resume");
    }
    
    /**
     * Test 20: Integration test - Full pipeline dry run
     */
    @Test
    @Order(20)
    @DisplayName("Integration test - Full research pipeline dry run")
    void testFullPipelineDryRun() throws IOException {
        // Create comprehensive test configuration
        Path configFile = createComprehensiveConfig();
        
        String[] args = {
            "--config", configFile.toString(),
            "--experiment", "comprehensive",
            "--algorithms", "HO,GA,PSO",
            "--dataset", "synthetic",
            "--parallel", "2",
            "--seed", "42",
            "--statistical-analysis",
            "--report-format", "pdf",
            "--output", tempDir.resolve("results").toString(),
            "--dry-run"
        };
        
        assertDoesNotThrow(() -> App.main(args),
                          "Full pipeline dry run should complete without errors");
        
        String output = outputStream.toString();
        
        // Verify key components are initialized
        assertTrue(output.contains("Configuration loaded"), "Should load configuration");
        assertTrue(output.contains("Algorithms"), "Should mention algorithms");
        assertTrue(output.contains("Dataset"), "Should mention dataset");
        assertTrue(output.contains("Dry run"), "Should indicate dry run mode");
    }
    
    /**
     * Helper method to create minimal configuration file
     */
    private Path createMinimalConfig() {
        try {
            Path configFile = tempDir.resolve("minimal_config.yaml");
            String content = """
                experiments:
                  algorithms:
                    - name: "HippopotamusOptimization"
                  datasets:
                    - "synthetic"
                  metrics:
                    - "resource_utilization"
                """;
            Files.writeString(configFile, content);
            return configFile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create test config", e);
        }
    }
    
    /**
     * Helper method to create comprehensive configuration file
     */
    private Path createComprehensiveConfig() {
        try {
            Path configFile = tempDir.resolve("comprehensive_config.yaml");
            String content = """
                experiments:
                  algorithms:
                    - name: "HippopotamusOptimization"
                      parameters:
                        population_size: [20, 50]
                        max_iterations: [100, 200]
                    - name: "GeneticAlgorithm"
                      parameters:
                        population_size: [50]
                        mutation_rate: [0.05]
                    - name: "ParticleSwarm"
                      parameters:
                        swarm_size: [30]
                  
                  datasets:
                    - "synthetic"
                    - "azure_traces"
                  
                  metrics:
                    - "resource_utilization"
                    - "power_consumption"
                    - "sla_violations"
                    - "response_time"
                  
                  scalability_tests:
                    vm_counts: [100, 500]
                    host_counts: [10, 50]
                  
                  statistical_tests:
                    confidence_level: 0.95
                    replications: 10
                """;
            Files.writeString(configFile, content);
            return configFile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create comprehensive config", e);
        }
    }
    
    /**
     * Provide invalid argument combinations for testing
     */
    private static Stream<Arguments> provideInvalidArguments() {
        return Stream.of(
            Arguments.of(new String[]{"--invalid-option"}, "Unknown option"),
            Arguments.of(new String[]{"--parallel", "-1"}, "Invalid"),
            Arguments.of(new String[]{"--confidence-level", "2.0"}, "Invalid"),
            Arguments.of(new String[]{"--experiment", "invalid_mode"}, "Unknown experiment mode"),
            Arguments.of(new String[]{"--report-format", "invalid_format"}, "Unsupported format")
        );
    }
    
    /**
     * Test 21: Performance test - Ensure app starts quickly
     */
    @Test
    @Order(21)
    @DisplayName("Performance test - Application startup time")
    void testStartupPerformance() {
        long startTime = System.currentTimeMillis();
        
        App.main(new String[]{"--version"});
        
        long duration = System.currentTimeMillis() - startTime;
        
        assertTrue(duration < 5000, 
                  "Application should start within 5 seconds, took: " + duration + "ms");
    }
    
    /**
     * Test 22: Memory usage test
     */
    @Test
    @Order(22)
    @DisplayName("Memory usage test")
    void testMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        
        App.main(new String[]{"--help"});
        
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;
        
        // Basic help should not use excessive memory (less than 50MB)
        assertTrue(memoryUsed < 50 * 1024 * 1024,
                  "Help command should not use excessive memory: " + (memoryUsed / 1024 / 1024) + "MB");
    }
    
    /**
     * Test 23: Test graceful shutdown
     */
    @Test
    @Order(23)
    @DisplayName("Test graceful shutdown")
    @Timeout(10) // Timeout after 10 seconds
    void testGracefulShutdown() {
        Thread appThread = new Thread(() -> {
            App.main(new String[]{"--experiment", "quick", "--dry-run"});
        });
        
        appThread.start();
        
        assertDoesNotThrow(() -> {
            Thread.sleep(1000); // Let it start
            appThread.interrupt(); // Simulate interrupt
            appThread.join(5000); // Wait for graceful shutdown
        });
        
        assertFalse(appThread.isAlive(), "Application should shut down gracefully");
    }
}