package org.cloudbus.cloudsim.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Memory optimization utility for CloudSim Hippopotamus Optimization.
 * Provides memory monitoring, cleanup, and optimization features.
 */
public class MemoryOptimizer {
    private static final Logger logger = LoggerFactory.getLogger(MemoryOptimizer.class);
    
    private final MemoryMXBean memoryBean;
    private final ScheduledExecutorService scheduler;
    private final long memoryThresholdMB;
    private final long maxMemoryMB;
    
    private boolean isMonitoring = false;
    private long lastCleanupTime = 0;
    private static final long CLEANUP_INTERVAL_MS = 30000; // 30 seconds
    
    public MemoryOptimizer() {
        this(1024); // Default 1GB threshold
    }
    
    public MemoryOptimizer(long memoryThresholdMB) {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.memoryThresholdMB = memoryThresholdMB;
        this.maxMemoryMB = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
        
        logger.info("MemoryOptimizer initialized - Threshold: {}MB, Max: {}MB", 
                   memoryThresholdMB, maxMemoryMB);
    }
    
    /**
     * Start memory monitoring with periodic cleanup.
     */
    public void startMonitoring() {
        if (isMonitoring) {
            logger.warn("Memory monitoring already started");
            return;
        }
        
        isMonitoring = true;
        scheduler.scheduleAtFixedRate(this::checkMemoryUsage, 5, 10, TimeUnit.SECONDS);
        logger.info("Memory monitoring started");
    }
    
    /**
     * Stop memory monitoring.
     */
    public void stopMonitoring() {
        if (!isMonitoring) {
            return;
        }
        
        isMonitoring = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("Memory monitoring stopped");
    }
    
    /**
     * Check current memory usage and trigger cleanup if needed.
     */
    private void checkMemoryUsage() {
        try {
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            long usedMB = heapUsage.getUsed() / (1024 * 1024);
            long committedMB = heapUsage.getCommitted() / (1024 * 1024);
            
            // Log memory usage every minute
            if (System.currentTimeMillis() % 60000 < 10000) {
                logger.debug("Memory usage - Used: {}MB, Committed: {}MB, Max: {}MB", 
                           usedMB, committedMB, maxMemoryMB);
            }
            
            // Check if cleanup is needed
            if (usedMB > memoryThresholdMB || 
                (System.currentTimeMillis() - lastCleanupTime) > CLEANUP_INTERVAL_MS) {
                performCleanup();
            }
            
            // Warn if memory usage is high
            if (usedMB > maxMemoryMB * 0.8) {
                logger.warn("High memory usage detected: {}MB ({}% of max)", 
                           usedMB, (usedMB * 100 / maxMemoryMB));
            }
            
        } catch (Exception e) {
            logger.error("Error checking memory usage", e);
        }
    }
    
    /**
     * Perform memory cleanup operations.
     */
    private void performCleanup() {
        try {
            long beforeUsed = memoryBean.getHeapMemoryUsage().getUsed();
            
            // Force garbage collection
            System.gc();
            
            // Wait a bit for GC to complete
            Thread.sleep(100);
            
            long afterUsed = memoryBean.getHeapMemoryUsage().getUsed();
            long freedMB = (beforeUsed - afterUsed) / (1024 * 1024);
            
            if (freedMB > 0) {
                logger.info("Memory cleanup completed - Freed: {}MB", freedMB);
            }
            
            lastCleanupTime = System.currentTimeMillis();
            
        } catch (Exception e) {
            logger.error("Error during memory cleanup", e);
        }
    }
    
    /**
     * Get current memory usage statistics.
     */
    public MemoryStats getMemoryStats() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        return new MemoryStats(
            heapUsage.getUsed() / (1024 * 1024),
            heapUsage.getCommitted() / (1024 * 1024),
            heapUsage.getMax() / (1024 * 1024),
            nonHeapUsage.getUsed() / (1024 * 1024),
            nonHeapUsage.getCommitted() / (1024 * 1024)
        );
    }
    
    /**
     * Force immediate memory cleanup.
     */
    public void forceCleanup() {
        logger.info("Forcing memory cleanup...");
        performCleanup();
    }
    
    /**
     * Check if memory usage is critical.
     */
    public boolean isMemoryCritical() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long usedMB = heapUsage.getUsed() / (1024 * 1024);
        return usedMB > maxMemoryMB * 0.9;
    }
    
    /**
     * Memory statistics container.
     */
    public static class MemoryStats {
        private final long heapUsedMB;
        private final long heapCommittedMB;
        private final long heapMaxMB;
        private final long nonHeapUsedMB;
        private final long nonHeapCommittedMB;
        
        public MemoryStats(long heapUsedMB, long heapCommittedMB, long heapMaxMB,
                          long nonHeapUsedMB, long nonHeapCommittedMB) {
            this.heapUsedMB = heapUsedMB;
            this.heapCommittedMB = heapCommittedMB;
            this.heapMaxMB = heapMaxMB;
            this.nonHeapUsedMB = nonHeapUsedMB;
            this.nonHeapCommittedMB = nonHeapCommittedMB;
        }
        
        public long getHeapUsedMB() { return heapUsedMB; }
        public long getHeapCommittedMB() { return heapCommittedMB; }
        public long getHeapMaxMB() { return heapMaxMB; }
        public long getNonHeapUsedMB() { return nonHeapUsedMB; }
        public long getNonHeapCommittedMB() { return nonHeapCommittedMB; }
        
        public double getHeapUsagePercentage() {
            return heapMaxMB > 0 ? (heapUsedMB * 100.0 / heapMaxMB) : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("Heap: %d/%d MB (%.1f%%), NonHeap: %d MB", 
                               heapUsedMB, heapMaxMB, getHeapUsagePercentage(), nonHeapUsedMB);
        }
    }
} 