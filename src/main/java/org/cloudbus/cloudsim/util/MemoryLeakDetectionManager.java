package org.cloudbus.cloudsim.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Memory leak detection manager for CloudSim Hippopotamus Optimization.
 * Provides efficient experiment monitoring with file management to prevent system overload.
 */
public class MemoryLeakDetectionManager {
    private static final Logger logger = LoggerFactory.getLogger(MemoryLeakDetectionManager.class);
    
    // Singleton instance
    private static volatile MemoryLeakDetectionManager instance;
    
    // Core components
    private final MemoryLeakDetector detector;
    private final ResourceMonitor resourceMonitor;
    
    // Experiment tracking with limits
    private final Map<String, ExperimentMemoryTracker> experimentTrackers;
    private final AtomicBoolean globalMonitoringEnabled;
    private final AtomicLong totalMemoryAllocated;
    private final AtomicLong totalMemoryFreed;
    
    // Memory leak patterns
    private final List<MemoryLeakPattern> leakPatterns;
    
    // Configuration with limits to prevent system overload
    private static final long MEMORY_CHECK_INTERVAL_MS = 30000; // 30 seconds (increased)
    private static final double MEMORY_LEAK_THRESHOLD = 0.15; // 15% growth
    private static final int MEMORY_LEAK_CHECK_COUNT = 3; // Check 3 times before alerting
    private static final int MAX_ACTIVE_EXPERIMENTS = 50; // Limit concurrent experiments
    private static final int MAX_TRACKERS_PER_SESSION = 100; // Limit total trackers
    
    private MemoryLeakDetectionManager() {
        this.detector = MemoryLeakDetector.getInstance();
        this.resourceMonitor = ResourceMonitor.getInstance();
        this.experimentTrackers = new ConcurrentHashMap<>();
        this.globalMonitoringEnabled = new AtomicBoolean(false);
        this.totalMemoryAllocated = new AtomicLong(0);
        this.totalMemoryFreed = new AtomicLong(0);
        this.leakPatterns = initializeLeakPatterns();
        
        logger.info("MemoryLeakDetectionManager initialized with limits enabled");
    }
    
    public static MemoryLeakDetectionManager getInstance() {
        if (instance == null) {
            synchronized (MemoryLeakDetectionManager.class) {
                if (instance == null) {
                    instance = new MemoryLeakDetectionManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Start memory leak detection for an experiment with limits
     */
    public void startExperimentMonitoring(String experimentId) {
        try {
            // Check if we've exceeded the limit for active experiments
            if (experimentTrackers.size() >= MAX_ACTIVE_EXPERIMENTS) {
                logger.warn("Maximum active experiments reached ({}), skipping monitoring for: {}", 
                           MAX_ACTIVE_EXPERIMENTS, experimentId);
                return;
            }
            
            // Check if we've exceeded the total tracker limit
            if (experimentTrackers.size() >= MAX_TRACKERS_PER_SESSION) {
                logger.warn("Maximum trackers per session reached ({}), skipping monitoring for: {}", 
                           MAX_TRACKERS_PER_SESSION, experimentId);
                return;
            }
            
            // Create experiment tracker
            ExperimentMemoryTracker tracker = new ExperimentMemoryTracker(experimentId);
            experimentTrackers.put(experimentId, tracker);
            
            // Start memory monitoring (only if not already monitoring)
            if (!globalMonitoringEnabled.get()) {
                detector.startMonitoring(experimentId);
                resourceMonitor.startMonitoring(experimentId);
                globalMonitoringEnabled.set(true);
            }
            
            logger.info("Memory leak detection started for experiment: {} (Active: {}/{})", 
                       experimentId, experimentTrackers.size(), MAX_ACTIVE_EXPERIMENTS);
            
        } catch (Exception e) {
            logger.error("Failed to start memory leak detection for experiment: {}", experimentId, e);
        }
    }
    
    /**
     * Stop memory leak detection for an experiment
     */
    public void stopExperimentMonitoring(String experimentId) {
        try {
            // Remove tracker
            ExperimentMemoryTracker tracker = experimentTrackers.remove(experimentId);
            if (tracker != null) {
                // Generate final analysis only if we have meaningful data
                if (tracker.getTotalAllocated() > 0 || tracker.getTotalFreed() > 0) {
                    generateExperimentMemoryReport(experimentId, tracker);
                }
            }
            
            // Stop monitoring if no more active experiments
            if (experimentTrackers.isEmpty()) {
                detector.stopMonitoring(experimentId);
                resourceMonitor.stopMonitoring(experimentId);
                globalMonitoringEnabled.set(false);
                logger.info("All experiments completed, monitoring stopped");
            }
            
            logger.info("Memory leak detection stopped for experiment: {} (Remaining: {})", 
                       experimentId, experimentTrackers.size());
            
        } catch (Exception e) {
            logger.error("Failed to stop memory leak detection for experiment: {}", experimentId, e);
        }
    }
    
    /**
     * Record memory allocation for tracking
     */
    public void recordMemoryAllocation(String experimentId, long bytesAllocated, String source) {
        ExperimentMemoryTracker tracker = experimentTrackers.get(experimentId);
        if (tracker != null) {
            tracker.recordAllocation(bytesAllocated, source);
            totalMemoryAllocated.addAndGet(bytesAllocated);
        }
    }
    
    /**
     * Record memory deallocation for tracking
     */
    public void recordMemoryDeallocation(String experimentId, long bytesFreed, String source) {
        ExperimentMemoryTracker tracker = experimentTrackers.get(experimentId);
        if (tracker != null) {
            tracker.recordDeallocation(bytesFreed, source);
            totalMemoryFreed.addAndGet(bytesFreed);
        }
    }
    
    /**
     * Check for specific memory leak patterns in optimization algorithms
     */
    public void checkOptimizationAlgorithmMemory(String experimentId, String algorithmName, 
                                                Map<String, Object> algorithmState) {
        try {
            // Only perform detailed checks if we have an active tracker
            ExperimentMemoryTracker tracker = experimentTrackers.get(experimentId);
            if (tracker == null) {
                return;
            }
            
            // Check for common optimization algorithm memory leaks
            checkPopulationMemoryLeak(experimentId, algorithmName, algorithmState);
            checkFitnessHistoryLeak(experimentId, algorithmName, algorithmState);
            checkConvergenceHistoryLeak(experimentId, algorithmName, algorithmState);
            checkTemporaryDataLeak(experimentId, algorithmName, algorithmState);
            
        } catch (Exception e) {
            logger.error("Error checking optimization algorithm memory for experiment: {}", experimentId, e);
        }
    }
    
    /**
     * Check for population-related memory leaks
     */
    private void checkPopulationMemoryLeak(String experimentId, String algorithmName, 
                                         Map<String, Object> algorithmState) {
        Object population = algorithmState.get("population");
        if (population instanceof List) {
            List<?> pop = (List<?>) population;
            if (pop.size() > 1000) { // Large population
                logger.warn("Large population detected in {}: {} individuals", algorithmName, pop.size());
                
                // Check if population is growing without bounds
                ExperimentMemoryTracker tracker = experimentTrackers.get(experimentId);
                if (tracker != null && tracker.getPopulationGrowthRate() > 0.1) {
                    logger.error("MEMORY LEAK: Population growing without bounds in {}", algorithmName);
                }
            }
        }
    }
    
    /**
     * Check for fitness history memory leaks
     */
    private void checkFitnessHistoryLeak(String experimentId, String algorithmName, 
                                       Map<String, Object> algorithmState) {
        Object fitnessHistory = algorithmState.get("fitnessHistory");
        if (fitnessHistory instanceof List) {
            List<?> history = (List<?>) fitnessHistory;
            if (history.size() > 10000) { // Very large history
                logger.warn("Large fitness history detected in {}: {} entries", algorithmName, history.size());
                
                // Suggest history cleanup
                logger.info("Consider implementing history cleanup for {}", algorithmName);
            }
        }
    }
    
    /**
     * Check for convergence history memory leaks
     */
    private void checkConvergenceHistoryLeak(String experimentId, String algorithmName, 
                                           Map<String, Object> algorithmState) {
        Object convergenceHistory = algorithmState.get("convergenceHistory");
        if (convergenceHistory instanceof List) {
            List<?> history = (List<?>) convergenceHistory;
            if (history.size() > 5000) { // Large convergence history
                logger.warn("Large convergence history detected in {}: {} entries", algorithmName, history.size());
            }
        }
    }
    
    /**
     * Check for temporary data memory leaks
     */
    private void checkTemporaryDataLeak(String experimentId, String algorithmName, 
                                      Map<String, Object> algorithmState) {
        // Check for temporary arrays, matrices, or other large data structures
        for (Map.Entry<String, Object> entry : algorithmState.entrySet()) {
            String key = entry.getKey().toLowerCase();
            Object value = entry.getValue();
            
            if ((key.contains("temp") || key.contains("tmp") || key.contains("cache")) && 
                value instanceof List) {
                List<?> tempData = (List<?>) value;
                if (tempData.size() > 1000) {
                    logger.warn("Large temporary data detected in {}: {} entries in {}", 
                              algorithmName, tempData.size(), entry.getKey());
                }
            }
        }
    }
    
    /**
     * Generate comprehensive memory report for an experiment
     */
    private void generateExperimentMemoryReport(String experimentId, ExperimentMemoryTracker tracker) {
        try {
            Map<String, Object> memoryStats = detector.getCurrentMemoryStats();
            
            logger.info("=== MEMORY REPORT FOR EXPERIMENT: {} ===", experimentId);
            logger.info("Total Memory Allocated: {} MB", 
                       tracker.getTotalAllocated() / (1024 * 1024));
            logger.info("Total Memory Freed: {} MB", 
                       tracker.getTotalFreed() / (1024 * 1024));
            logger.info("Net Memory Usage: {} MB", 
                       (tracker.getTotalAllocated() - tracker.getTotalFreed()) / (1024 * 1024));
            logger.info("Current Heap Usage: {} MB", 
                       (Long) memoryStats.get("heapUsed") / (1024 * 1024));
            logger.info("Memory Leak Score: {:.2f}%", tracker.getMemoryLeakScore() * 100);
            
            // Check for memory leaks
            if (tracker.getMemoryLeakScore() > MEMORY_LEAK_THRESHOLD) {
                logger.error("MEMORY LEAK DETECTED in experiment: {} (Score: {:.2f}%)", 
                           experimentId, tracker.getMemoryLeakScore() * 100);
                
                // Provide recommendations
                provideMemoryLeakRecommendations(experimentId, tracker);
            }
            
        } catch (Exception e) {
            logger.error("Error generating memory report for experiment: {}", experimentId, e);
        }
    }
    
    /**
     * Provide memory leak recommendations
     */
    private void provideMemoryLeakRecommendations(String experimentId, ExperimentMemoryTracker tracker) {
        logger.info("=== MEMORY LEAK RECOMMENDATIONS FOR EXPERIMENT: {} ===", experimentId);
        
        for (MemoryLeakPattern pattern : leakPatterns) {
            logger.info("Pattern: {}", pattern.getName());
            logger.info("Description: {}", pattern.getDescription());
            logger.info("Recommendation: {}", pattern.getRecommendation());
            logger.info("---");
        }
    }
    
    /**
     * Initialize common memory leak patterns
     */
    private List<MemoryLeakPattern> initializeLeakPatterns() {
        List<MemoryLeakPattern> patterns = new ArrayList<>();
        
        patterns.add(new MemoryLeakPattern(
            "Growing Collections",
            "Collections that grow without bounds",
            "Implement size limits or cleanup mechanisms"
        ));
        
        patterns.add(new MemoryLeakPattern(
            "Unclosed Resources",
            "Resources not properly closed",
            "Use try-with-resources or explicit cleanup"
        ));
        
        patterns.add(new MemoryLeakPattern(
            "Cached Objects",
            "Objects cached without eviction",
            "Implement LRU or time-based eviction"
        ));
        
        patterns.add(new MemoryLeakPattern(
            "Event Listeners",
            "Listeners not properly removed",
            "Remove listeners when objects are destroyed"
        ));
        
        return patterns;
    }
    
    /**
     * Get global memory statistics
     */
    public Map<String, Object> getGlobalMemoryStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalAllocated", totalMemoryAllocated.get());
        stats.put("totalFreed", totalMemoryFreed.get());
        stats.put("netMemoryUsage", totalMemoryAllocated.get() - totalMemoryFreed.get());
        stats.put("activeExperiments", experimentTrackers.size());
        stats.put("maxActiveExperiments", MAX_ACTIVE_EXPERIMENTS);
        stats.put("maxTrackersPerSession", MAX_TRACKERS_PER_SESSION);
        
        // Add detector stats
        Map<String, Object> detectorStats = detector.getCurrentMemoryStats();
        stats.putAll(detectorStats);
        
        return stats;
    }
    
    /**
     * Perform memory cleanup
     */
    public void performMemoryCleanup() {
        try {
            logger.info("Performing global memory cleanup...");
            
            // Force garbage collection
            detector.forceGarbageCollection();
            
            // Clear old trackers if we have too many
            if (experimentTrackers.size() > MAX_ACTIVE_EXPERIMENTS * 2) {
                logger.info("Cleaning up old experiment trackers...");
                // Keep only the most recent trackers
                List<String> trackerIds = new ArrayList<>(experimentTrackers.keySet());
                trackerIds.sort((a, b) -> {
                    ExperimentMemoryTracker trackerA = experimentTrackers.get(a);
                    ExperimentMemoryTracker trackerB = experimentTrackers.get(b);
                    return trackerB.getStartTime().compareTo(trackerA.getStartTime());
                });
                
                // Remove old trackers
                for (int i = MAX_ACTIVE_EXPERIMENTS; i < trackerIds.size(); i++) {
                    experimentTrackers.remove(trackerIds.get(i));
                }
                
                logger.info("Cleaned up {} old trackers", trackerIds.size() - MAX_ACTIVE_EXPERIMENTS);
            }
            
        } catch (Exception e) {
            logger.error("Error during memory cleanup", e);
        }
    }
    
    /**
     * Shutdown the memory leak detection manager
     */
    public void shutdown() {
        try {
            logger.info("Shutting down MemoryLeakDetectionManager...");
            
            // Stop all monitoring
            globalMonitoringEnabled.set(false);
            
            // Generate final reports for remaining experiments
            for (Map.Entry<String, ExperimentMemoryTracker> entry : experimentTrackers.entrySet()) {
                generateExperimentMemoryReport(entry.getKey(), entry.getValue());
            }
            
            // Clear trackers
            experimentTrackers.clear();
            
            // Shutdown detector
            detector.shutdown();
            
            logger.info("MemoryLeakDetectionManager shutdown completed");
            
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
    }
    
    /**
     * Memory leak pattern definition
     */
    public static class MemoryLeakPattern {
        private final String name;
        private final String description;
        private final String recommendation;
        
        public MemoryLeakPattern(String name, String description, String recommendation) {
            this.name = name;
            this.description = description;
            this.recommendation = recommendation;
        }
        
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getRecommendation() { return recommendation; }
    }
    
    /**
     * Experiment memory tracker with limits
     */
    public static class ExperimentMemoryTracker {
        private final String experimentId;
        private final AtomicLong totalAllocated;
        private final AtomicLong totalFreed;
        private final Map<String, Long> allocationsBySource;
        private final Map<String, Long> deallocationsBySource;
        private final List<Long> memorySnapshots;
        private final LocalDateTime startTime;
        
        // Limits to prevent memory overflow
        private static final int MAX_SNAPSHOTS = 100;
        private static final int MAX_SOURCE_ENTRIES = 50;
        
        public ExperimentMemoryTracker(String experimentId) {
            this.experimentId = experimentId;
            this.totalAllocated = new AtomicLong(0);
            this.totalFreed = new AtomicLong(0);
            this.allocationsBySource = new HashMap<>();
            this.deallocationsBySource = new HashMap<>();
            this.memorySnapshots = new ArrayList<>();
            this.startTime = LocalDateTime.now();
        }
        
        public void recordAllocation(long bytes, String source) {
            totalAllocated.addAndGet(bytes);
            
            // Limit source entries to prevent memory overflow
            if (allocationsBySource.size() < MAX_SOURCE_ENTRIES) {
                allocationsBySource.merge(source, bytes, Long::sum);
            }
        }
        
        public void recordDeallocation(long bytes, String source) {
            totalFreed.addAndGet(bytes);
            
            // Limit source entries to prevent memory overflow
            if (deallocationsBySource.size() < MAX_SOURCE_ENTRIES) {
                deallocationsBySource.merge(source, bytes, Long::sum);
            }
        }
        
        public void recordMemorySnapshot(long currentMemory) {
            // Limit snapshots to prevent memory overflow
            if (memorySnapshots.size() < MAX_SNAPSHOTS) {
                memorySnapshots.add(currentMemory);
            }
        }
        
        public double getMemoryLeakScore() {
            if (totalAllocated.get() == 0) {
                return 0.0;
            }
            
            long netMemory = totalAllocated.get() - totalFreed.get();
            return Math.min(1.0, (double) netMemory / totalAllocated.get());
        }
        
        public double getPopulationGrowthRate() {
            if (memorySnapshots.size() < 2) {
                return 0.0;
            }
            
            // Calculate growth rate from snapshots
            long firstSnapshot = memorySnapshots.get(0);
            long lastSnapshot = memorySnapshots.get(memorySnapshots.size() - 1);
            
            if (firstSnapshot == 0) {
                return 0.0;
            }
            
            return (double) (lastSnapshot - firstSnapshot) / firstSnapshot;
        }
        
        // Getters
        public String getExperimentId() { return experimentId; }
        public long getTotalAllocated() { return totalAllocated.get(); }
        public long getTotalFreed() { return totalFreed.get(); }
        public Map<String, Long> getAllocationsBySource() { return new HashMap<>(allocationsBySource); }
        public Map<String, Long> getDeallocationsBySource() { return new HashMap<>(deallocationsBySource); }
        public LocalDateTime getStartTime() { return startTime; }
    }
} 