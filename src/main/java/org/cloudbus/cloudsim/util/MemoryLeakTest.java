package org.cloudbus.cloudsim.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * Memory Leak Test Utility
 * 
 * Demonstrates memory leak detection capabilities and provides
 * test scenarios for identifying memory leaks in the CloudSim framework.
 * 
 * @author Puneet Chandna
 * @version 1.0
 */
public class MemoryLeakTest {
    private static final Logger logger = LoggerFactory.getLogger(MemoryLeakTest.class);
    
    private final MemoryLeakDetectionManager memoryManager;
    private final List<Object> testObjects;
    
    public MemoryLeakTest() {
        this.memoryManager = MemoryLeakDetectionManager.getInstance();
        this.testObjects = new ArrayList<>();
    }
    
    /**
     * Run comprehensive memory leak tests
     */
    public void runMemoryLeakTests() {
        logger.info("Starting memory leak detection tests...");
        
        try {
            // Test 1: Basic memory monitoring
            testBasicMemoryMonitoring();
            
            // Test 2: Growing collections leak
            testGrowingCollectionsLeak();
            
            // Test 3: Optimization algorithm memory leak simulation
            testOptimizationAlgorithmLeak();
            
            // Test 4: Resource cleanup verification
            testResourceCleanup();
            
            // Test 5: Memory cleanup effectiveness
            testMemoryCleanup();
            
            logger.info("Memory leak tests completed successfully");
            
        } catch (Exception e) {
            logger.error("Error during memory leak tests", e);
        }
    }
    
    /**
     * Test basic memory monitoring functionality
     */
    private void testBasicMemoryMonitoring() {
        logger.info("=== Test 1: Basic Memory Monitoring ===");
        
        String experimentId = "test_basic_monitoring_" + System.currentTimeMillis();
        memoryManager.startExperimentMonitoring(experimentId);
        
        // Simulate some memory allocations
        for (int i = 0; i < 10; i++) {
            byte[] data = new byte[1024 * 1024]; // 1MB allocation
            testObjects.add(data);
            
            memoryManager.recordMemoryAllocation(experimentId, data.length, "test_allocation");
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Get memory statistics
        Map<String, Object> stats = memoryManager.getGlobalMemoryStats();
        logger.info("Memory stats: {}", stats);
        
        memoryManager.stopExperimentMonitoring(experimentId);
    }
    
    /**
     * Test growing collections memory leak
     */
    private void testGrowingCollectionsLeak() {
        logger.info("=== Test 2: Growing Collections Leak ===");
        
        String experimentId = "test_growing_collections_" + System.currentTimeMillis();
        memoryManager.startExperimentMonitoring(experimentId);
        
        List<String> growingList = new ArrayList<>();
        
        // Simulate growing collection without cleanup
        for (int i = 0; i < 1000; i++) {
            String data = "Test data " + i + " with some content to simulate real data";
            growingList.add(data);
            
            // Record allocation
            memoryManager.recordMemoryAllocation(experimentId, data.length(), "growing_list");
            
            if (i % 100 == 0) {
                logger.info("Added {} items to growing list", i);
            }
        }
        
        // This should trigger memory leak detection
        memoryManager.stopExperimentMonitoring(experimentId);
    }
    
    /**
     * Test optimization algorithm memory leak simulation
     */
    private void testOptimizationAlgorithmLeak() {
        logger.info("=== Test 3: Optimization Algorithm Leak ===");
        
        String experimentId = "test_optimization_leak_" + System.currentTimeMillis();
        memoryManager.startExperimentMonitoring(experimentId);
        
        // Simulate optimization algorithm state
        Map<String, Object> algorithmState = new HashMap<>();
        
        // Simulate growing population
        List<Object> population = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            population.add(new Object());
        }
        algorithmState.put("population", population);
        
        // Simulate growing fitness history
        List<Double> fitnessHistory = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            fitnessHistory.add(Math.random());
        }
        algorithmState.put("fitnessHistory", fitnessHistory);
        
        // Simulate growing convergence history
        List<Double> convergenceHistory = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            convergenceHistory.add(Math.random());
        }
        algorithmState.put("convergenceHistory", convergenceHistory);
        
        // Check for memory leaks
        memoryManager.checkOptimizationAlgorithmMemory(experimentId, "TestOptimization", algorithmState);
        
        memoryManager.stopExperimentMonitoring(experimentId);
    }
    
    /**
     * Test resource cleanup verification
     */
    private void testResourceCleanup() {
        logger.info("=== Test 4: Resource Cleanup Verification ===");
        
        String experimentId = "test_resource_cleanup_" + System.currentTimeMillis();
        memoryManager.startExperimentMonitoring(experimentId);
        
        // Simulate resource allocation and cleanup
        for (int i = 0; i < 100; i++) {
            // Allocate resource
            byte[] resource = new byte[1024 * 100]; // 100KB
            memoryManager.recordMemoryAllocation(experimentId, resource.length, "resource_allocation");
            
            // Simulate work
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            
            // Cleanup resource
            resource = null;
            memoryManager.recordMemoryDeallocation(experimentId, 1024 * 100, "resource_cleanup");
        }
        
        memoryManager.stopExperimentMonitoring(experimentId);
    }
    
    /**
     * Test memory cleanup effectiveness
     */
    private void testMemoryCleanup() {
        logger.info("=== Test 5: Memory Cleanup Effectiveness ===");
        
        // Create some memory pressure
        List<byte[]> memoryPressure = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            memoryPressure.add(new byte[1024 * 1024]); // 1MB each
        }
        
        logger.info("Created memory pressure: {} MB", memoryPressure.size());
        
        // Perform memory cleanup
        memoryManager.performMemoryCleanup();
        
        // Clear memory pressure
        memoryPressure.clear();
        System.gc();
        
        logger.info("Memory cleanup test completed");
    }
    
    /**
     * Run specific memory leak pattern test
     */
    public void testSpecificLeakPattern(String patternName) {
        logger.info("Testing specific leak pattern: {}", patternName);
        
        String experimentId = "test_" + patternName + "_" + System.currentTimeMillis();
        memoryManager.startExperimentMonitoring(experimentId);
        
        switch (patternName.toLowerCase()) {
            case "collections":
                testGrowingCollectionsLeak();
                break;
            case "optimization":
                testOptimizationAlgorithmLeak();
                break;
            case "resources":
                testResourceCleanup();
                break;
            default:
                logger.warn("Unknown leak pattern: {}", patternName);
        }
        
        memoryManager.stopExperimentMonitoring(experimentId);
    }
    
    /**
     * Get memory leak detection recommendations
     */
    public void printMemoryLeakRecommendations() {
        logger.info("=== MEMORY LEAK DETECTION RECOMMENDATIONS ===");
        logger.info("1. Monitor memory usage during long-running experiments");
        logger.info("2. Implement proper cleanup in optimization algorithms");
        logger.info("3. Use bounded collections for history tracking");
        logger.info("4. Implement resource pooling for frequently created objects");
        logger.info("5. Add memory-aware termination conditions");
        logger.info("6. Use weak references for caching mechanisms");
        logger.info("7. Implement periodic garbage collection in long loops");
        logger.info("8. Monitor heap usage and set appropriate JVM parameters");
        logger.info("9. Use profiling tools for detailed memory analysis");
        logger.info("10. Implement memory leak detection in CI/CD pipeline");
    }
    
    /**
     * Main method for running tests
     */
    public static void main(String[] args) {
        MemoryLeakTest test = new MemoryLeakTest();
        
        if (args.length > 0) {
            // Run specific test
            test.testSpecificLeakPattern(args[0]);
        } else {
            // Run all tests
            test.runMemoryLeakTests();
        }
        
        // Print recommendations
        test.printMemoryLeakRecommendations();
        
        // Shutdown memory manager
        test.memoryManager.shutdown();
    }
} 