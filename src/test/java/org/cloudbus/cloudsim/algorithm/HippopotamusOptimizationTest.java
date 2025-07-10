package org.cloudbus.cloudsim.algorithm;

import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.util.ExperimentException;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for the Hippopotamus Optimization algorithm.
 * Tests algorithm correctness, convergence, parameter sensitivity, and edge cases.
 * 
 * Research Focus: Validates HO algorithm implementation for VM placement optimization
 * Statistical Rigor: Includes statistical validation of optimization results
 * Reproducibility: Uses fixed seeds for deterministic testing
 * @author Puneet Chandna
 * @version 1.0
 */
public class HippopotamusOptimizationTest {
    
    private HippopotamusOptimization optimizer;
    private List<Vm> testVms;
    private List<Host> testHosts;
    private HippopotamusParameters defaultParams;
    
    @BeforeEach
    public void setUp() {
        // Initialize logging for tests
        LoggingManager.configureLogging("test");
        
        // Create default parameters
        defaultParams = new HippopotamusParameters();
        defaultParams.setPopulationSize(20);
        defaultParams.setMaxIterations(50);
        defaultParams.setConvergenceThreshold(0.001);
        defaultParams.setSeed(42L); // Fixed seed for reproducibility
        
        // Initialize optimizer
        optimizer = new HippopotamusOptimization();
        
        // Create test VMs and hosts
        createTestEnvironment();
    }
    
    @AfterEach
    public void tearDown() {
        testVms = null;
        testHosts = null;
        optimizer = null;
    }
    
    private void createTestEnvironment() {
        // Create test hosts with varying capacities
        testHosts = new ArrayList<>();
        int[] hostMips = {1000, 2000, 1500, 3000, 2500};
        int[] hostRam = {8192, 16384, 12288, 32768, 24576};
        long[] hostBw = {10000, 10000, 10000, 10000, 10000};
        long[] hostStorage = {100000, 200000, 150000, 300000, 250000};
        
        for (int i = 0; i < 5; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int j = 0; j < 4; j++) {
                peList.add(new PeSimple(hostMips[i]));
            }
            
            Host host = new HostSimple(hostRam[i], hostBw[i], hostStorage[i], peList);
            host.setId(i);
            testHosts.add(host);
        }
        
        // Create test VMs with varying requirements
        testVms = new ArrayList<>();
        int[] vmMips = {500, 1000, 750, 1500, 1250, 800, 600, 1100};
        int[] vmRam = {2048, 4096, 3072, 8192, 6144, 2560, 1536, 5120};
        
        for (int i = 0; i < 8; i++) {
            Vm vm = new VmSimple(vmMips[i], 2, vmRam[i], 
                                1000, 10000, "Xen", 
                                null, 0, 0);
            vm.setId(i);
            testVms.add(vm);
        }
    }
    
    @Test
    @DisplayName("Test basic HO algorithm functionality")
    public void testBasicOptimization() {
        try {
            OptimizationResult result = optimizer.optimize(testVms, testHosts, defaultParams);
            
            assertNotNull(result, "Optimization result should not be null");
            assertNotNull(result.getBestSolution(), "Best solution should not be null");
            assertTrue(result.getBestSolution().getFitness() > 0, 
                      "Fitness should be positive");
            assertFalse(result.getConvergenceData().isEmpty(), 
                       "Convergence data should not be empty");
            
            // Verify all VMs are allocated
            Map<Vm, Host> allocation = result.getBestSolution().getVmHostMapping();
            assertEquals(testVms.size(), allocation.size(), 
                        "All VMs should be allocated");
            
            // Verify resource constraints are satisfied
            validateResourceConstraints(allocation);
            
        } catch (ExperimentException e) {
            fail("Optimization should not throw exception: " + e.getMessage());
        }
    }
    
    @Test
    @DisplayName("Test convergence behavior")
    public void testConvergenceBehavior() {
        OptimizationResult result = optimizer.optimize(testVms, testHosts, defaultParams);
        List<Double> convergenceHistory = result.getConvergenceData();
        
        // Verify convergence trend
        assertTrue(convergenceHistory.size() > 0, "Convergence history should not be empty");
        
        // Check if fitness generally improves (allowing for some fluctuation)
        double improvementCount = 0;
        for (int i = 1; i < convergenceHistory.size(); i++) {
            if (convergenceHistory.get(i) >= convergenceHistory.get(i-1)) {
                improvementCount++;
            }
        }
        double improvementRatio = improvementCount / (convergenceHistory.size() - 1);
        assertTrue(improvementRatio > 0.7, 
                  "At least 70% of iterations should show improvement or stability");
        
        // Verify convergence detection
        if (result.isConverged()) {
            double lastChange = Math.abs(
                convergenceHistory.get(convergenceHistory.size() - 1) - 
                convergenceHistory.get(convergenceHistory.size() - 2)
            );
            assertTrue(lastChange < defaultParams.getConvergenceThreshold(),
                      "Convergence should be detected correctly");
        }
    }
    
    @ParameterizedTest
    @DisplayName("Test parameter sensitivity - population size")
    @ValueSource(ints = {10, 20, 50, 100})
    public void testPopulationSizeSensitivity(int populationSize) {
        HippopotamusParameters params = new HippopotamusParameters(defaultParams);
        params.setPopulationSize(populationSize);
        
        OptimizationResult result = optimizer.optimize(testVms, testHosts, params);
        
        assertNotNull(result, "Result should not be null for population size " + populationSize);
        assertTrue(result.getBestSolution().getFitness() > 0,
                  "Fitness should be positive for population size " + populationSize);
        
        // Larger populations should generally yield better or equal results
        if (populationSize >= 50) {
            OptimizationResult smallPopResult = optimizer.optimize(testVms, testHosts, defaultParams);
            assertTrue(result.getBestSolution().getFitness() >= 
                      smallPopResult.getBestSolution().getFitness() * 0.95,
                      "Larger population should not significantly degrade performance");
        }
    }
    
    @Test
    @DisplayName("Test edge case - empty VM list")
    public void testEmptyVmList() {
        List<Vm> emptyVms = new ArrayList<>();
        
        assertThrows(ExperimentException.class, () -> {
            optimizer.optimize(emptyVms, testHosts, defaultParams);
        }, "Should throw exception for empty VM list");
    }
    
    @Test
    @DisplayName("Test edge case - empty host list")
    public void testEmptyHostList() {
        List<Host> emptyHosts = new ArrayList<>();
        
        assertThrows(ExperimentException.class, () -> {
            optimizer.optimize(testVms, emptyHosts, defaultParams);
        }, "Should throw exception for empty host list");
    }
    
    @Test
    @DisplayName("Test infeasible scenario - insufficient resources")
    public void testInfeasibleScenario() {
        // Create VMs that require more resources than available
        List<Vm> largeVms = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Vm vm = new VmSimple(5000, 8, 32768, 1000, 10000, "Xen", null, 0, 0);
            vm.setId(i);
            largeVms.add(vm);
        }
        
        OptimizationResult result = optimizer.optimize(largeVms, testHosts, defaultParams);
        
        // Should still return a result, but with poor fitness or partial allocation
        assertNotNull(result, "Should return result even for infeasible scenario");
        assertTrue(result.getBestSolution().getFitness() <= 1.0,
                  "Fitness should reflect infeasibility");
    }
    
    @Test
    @DisplayName("Test deterministic behavior with seed")
    public void testDeterministicBehavior() {
        // Run optimization twice with same seed
        OptimizationResult result1 = optimizer.optimize(testVms, testHosts, defaultParams);
        OptimizationResult result2 = optimizer.optimize(testVms, testHosts, defaultParams);
        
        // Results should be identical
        assertEquals(result1.getBestSolution().getFitness(), 
                    result2.getBestSolution().getFitness(), 0.0001,
                    "Results should be identical with same seed");
        
        assertEquals(result1.getConvergenceData().size(),
                    result2.getConvergenceData().size(),
                    "Convergence history should be identical");
    }
    
    @Test
    @DisplayName("Test multi-objective optimization")
    public void testMultiObjectiveOptimization() {
        // Configure multi-objective weights
        Map<String, Double> objectiveWeights = new HashMap<>();
        objectiveWeights.put("resource_utilization", 0.4);
        objectiveWeights.put("power_consumption", 0.3);
        objectiveWeights.put("sla_violations", 0.3);
        defaultParams.setObjectiveWeights(objectiveWeights);
        
        OptimizationResult result = optimizer.optimize(testVms, testHosts, defaultParams);
        
        assertNotNull(result.getBestSolution().getDetailedMetrics(),
                     "Detailed metrics should be available");
        assertTrue(result.getBestSolution().getDetailedMetrics().containsKey("resource_utilization"),
                  "Should include resource utilization metric");
        assertTrue(result.getBestSolution().getDetailedMetrics().containsKey("power_consumption"),
                  "Should include power consumption metric");
        assertTrue(result.getBestSolution().getDetailedMetrics().containsKey("sla_violations"),
                  "Should include SLA violations metric");
    }
    
    @Test
    @DisplayName("Test population diversity tracking")
    public void testPopulationDiversity() {
        OptimizationResult result = optimizer.optimize(testVms, testHosts, defaultParams);
        
        assertNotNull(result.getDiversityMetrics(), "Diversity metrics should not be null");
        assertFalse(result.getDiversityMetrics().isEmpty(), 
                   "Diversity metrics should not be empty");
        
        // Diversity should generally decrease over iterations
        List<Double> diversity = result.getDiversityMetrics();
        double earlyDiversity = diversity.subList(0, 5).stream()
                                      .mapToDouble(Double::doubleValue).average().orElse(0);
        double lateDiversity = diversity.subList(diversity.size() - 5, diversity.size()).stream()
                                      .mapToDouble(Double::doubleValue).average().orElse(0);
        
        assertTrue(lateDiversity <= earlyDiversity * 1.1, 
                  "Diversity should generally decrease or stabilize");
    }
    
    @Test
    @DisplayName("Test execution metrics collection")
    public void testExecutionMetrics() {
        long startTime = System.currentTimeMillis();
        OptimizationResult result = optimizer.optimize(testVms, testHosts, defaultParams);
        long endTime = System.currentTimeMillis();
        
        assertNotNull(result.getExecutionMetrics(), "Execution metrics should not be null");
        assertTrue(result.getExecutionMetrics().containsKey("execution_time"),
                  "Should include execution time");
        assertTrue(result.getExecutionMetrics().containsKey("iterations_performed"),
                  "Should include iterations performed");
        assertTrue(result.getExecutionMetrics().containsKey("memory_usage"),
                  "Should include memory usage");
        
        double recordedTime = (Double) result.getExecutionMetrics().get("execution_time");
        assertTrue(recordedTime > 0 && recordedTime <= (endTime - startTime),
                  "Execution time should be realistic");
    }
    
    private void validateResourceConstraints(Map<Vm, Host> allocation) {
        // Group VMs by host
        Map<Host, List<Vm>> hostVmMap = allocation.entrySet().stream()
            .collect(Collectors.groupingBy(Map.Entry::getValue,
                    Collectors.mapping(Map.Entry::getKey, Collectors.toList())));
        
        // Validate each host's resource usage
        for (Map.Entry<Host, List<Vm>> entry : hostVmMap.entrySet()) {
            Host host = entry.getKey();
            List<Vm> vms = entry.getValue();
            
            double totalMips = vms.stream().mapToDouble(Vm::getMips).sum();
            double totalRam = vms.stream().mapToDouble(Vm::getRam).sum();
            
            assertTrue(totalMips <= host.getTotalMips(),
                      "Total MIPS requirement should not exceed host capacity");
            assertTrue(totalRam <= host.getRamProvisioner().getCapacity(),
                      "Total RAM requirement should not exceed host capacity");
        }
    }
}