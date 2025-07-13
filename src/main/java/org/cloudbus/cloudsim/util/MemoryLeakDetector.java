package org.cloudbus.cloudsim.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.management.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Memory leak detection utility for CloudSim Hippopotamus Optimization.
 * Implements efficient memory monitoring with file rotation to prevent system overload.
 */
public class MemoryLeakDetector {
    private static final Logger logger = LoggerFactory.getLogger(MemoryLeakDetector.class);
    
    // Singleton instance
    private static volatile MemoryLeakDetector instance;
    
    // JMX beans for monitoring
    private final MemoryMXBean memoryBean;
    private final List<MemoryPoolMXBean> memoryPoolBeans;
    private final List<GarbageCollectorMXBean> gcBeans;
    private final ThreadMXBean threadBean;
    
    // Monitoring infrastructure
    private final ScheduledExecutorService monitorExecutor;
    private final Map<String, MemorySnapshot> memorySnapshots;
    private final Map<String, GCSnapshot> gcSnapshots;
    private final AtomicLong totalAllocatedMemory;
    private final AtomicInteger gcCount;
    private final AtomicLong totalGCTime;
    
    // Configuration
    private volatile boolean monitoringEnabled;
    private volatile long monitoringIntervalMs;
    private volatile String outputDirectory;
    private volatile int maxSnapshots;
    
    // Memory leak detection thresholds
    private static final double MEMORY_GROWTH_THRESHOLD = 0.1; // 10% growth
    private static final double GC_EFFICIENCY_THRESHOLD = 0.8; // 80% efficiency
    private static final int CONSECUTIVE_GROWTH_LIMIT = 5;
    private static final long MEMORY_LEAK_ALERT_THRESHOLD = 100 * 1024 * 1024; // 100MB
    
    // File management to prevent system overload
    private static final int MAX_REPORTS_PER_SESSION = 10; // Limit reports per session
    private static final int MAX_SNAPSHOTS_PER_EXPERIMENT = 50; // Limit snapshots per experiment
    private final AtomicInteger reportCounter = new AtomicInteger(0);
    private final String sessionId = "session_" + System.currentTimeMillis();
    private final Set<String> reportedExperiments = Collections.synchronizedSet(new HashSet<>());
    
    // Time formatter for reports
    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private MemoryLeakDetector() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.memoryPoolBeans = ManagementFactory.getMemoryPoolMXBeans();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        this.threadBean = ManagementFactory.getThreadMXBean();
        
        this.monitorExecutor = Executors.newSingleThreadScheduledExecutor();
        this.memorySnapshots = new HashMap<>();
        this.gcSnapshots = new HashMap<>();
        this.totalAllocatedMemory = new AtomicLong(0);
        this.gcCount = new AtomicInteger(0);
        this.totalGCTime = new AtomicLong(0);
        
        // Default configuration
        this.monitoringEnabled = false;
        this.monitoringIntervalMs = 10000; // 10 seconds
        this.outputDirectory = "logs/memory";
        this.maxSnapshots = 100;
        
        logger.info("MemoryLeakDetector initialized with file rotation enabled");
    }
    
    public static MemoryLeakDetector getInstance() {
        if (instance == null) {
            synchronized (MemoryLeakDetector.class) {
                if (instance == null) {
                    instance = new MemoryLeakDetector();
                }
            }
        }
        return instance;
    }
    
    /**
     * Start memory leak monitoring for an experiment
     */
    public void startMonitoring(String experimentId) {
        if (!monitoringEnabled) {
            monitoringEnabled = true;
            monitorExecutor.scheduleAtFixedRate(
                () -> performMemoryMonitoring(experimentId),
                0, monitoringIntervalMs, TimeUnit.MILLISECONDS
            );
            logger.info("Memory leak monitoring started for experiment: {}", experimentId);
        }
    }
    
    /**
     * Stop memory leak monitoring
     */
    public void stopMonitoring(String experimentId) {
        monitoringEnabled = false;
        logger.info("Memory leak monitoring stopped for experiment: {}", experimentId);
        
        // Only generate report if we haven't exceeded the limit and haven't reported this experiment
        if (reportCounter.get() < MAX_REPORTS_PER_SESSION && 
            !reportedExperiments.contains(experimentId)) {
            generateMemoryLeakReport(experimentId);
            reportedExperiments.add(experimentId);
        }
    }
    
    /**
     * Perform memory monitoring with file rotation
     */
    private void performMemoryMonitoring(String experimentId) {
        try {
            // Capture memory snapshot (with limit)
            captureMemorySnapshot(experimentId);
            
            // Capture GC snapshot
            captureGCSnapshot(experimentId);
            
            // Analyze memory trends (less frequently to reduce overhead)
            if (System.currentTimeMillis() % 60000 < 10000) { // Every minute
                analyzeMemoryTrends(experimentId);
            }
            
        } catch (Exception e) {
            logger.error("Error during memory monitoring", e);
        }
    }
    
    /**
     * Capture detailed memory snapshot with limits
     */
    private void captureMemorySnapshot(String experimentId) {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        MemorySnapshot snapshot = new MemorySnapshot(
            LocalDateTime.now(),
            heapUsage.getUsed(),
            heapUsage.getCommitted(),
            heapUsage.getMax(),
            nonHeapUsage.getUsed(),
            nonHeapUsage.getCommitted(),
            nonHeapUsage.getMax(),
            threadBean.getThreadCount(),
            threadBean.getPeakThreadCount(),
            Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        );
        
        // Limit snapshots per experiment to prevent memory overflow
        if (memorySnapshots.size() < MAX_SNAPSHOTS_PER_EXPERIMENT) {
            memorySnapshots.put(experimentId, snapshot);
        }
        
        // Check for memory growth patterns (less frequently)
        if (System.currentTimeMillis() % 30000 < 5000) { // Every 30 seconds
            detectMemoryGrowth(experimentId);
        }
        
        // Log if memory usage is high
        if (heapUsage.getUsed() > MEMORY_LEAK_ALERT_THRESHOLD) {
            logger.warn("High memory usage detected: {} MB", 
                       heapUsage.getUsed() / (1024 * 1024));
        }
    }
    
    /**
     * Capture garbage collection snapshot
     */
    private void captureGCSnapshot(String experimentId) {
        long totalGCCount = 0;
        long totalGCTime = 0;
        
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            totalGCCount += gcBean.getCollectionCount();
            totalGCTime += gcBean.getCollectionTime();
        }
        
        GCSnapshot snapshot = new GCSnapshot(
            LocalDateTime.now(),
            totalGCCount,
            totalGCTime,
            gcBeans.size()
        );
        
        gcSnapshots.put(experimentId, snapshot);
        
        // Update global counters
        this.gcCount.set((int) totalGCCount);
        this.totalGCTime.set(totalGCTime);
    }
    
    /**
     * Detect memory growth patterns indicating potential leaks
     */
    private void detectMemoryGrowth(String experimentId) {
        List<MemorySnapshot> snapshots = getMemorySnapshots(experimentId);
        if (snapshots.size() < 3) {
            return; // Need at least 3 snapshots for trend analysis
        }
        
        // Calculate memory growth rate
        MemorySnapshot latest = snapshots.get(snapshots.size() - 1);
        MemorySnapshot previous = snapshots.get(snapshots.size() - 2);
        
        long memoryGrowth = latest.getHeapUsed() - previous.getHeapUsed();
        double growthRate = (double) memoryGrowth / previous.getHeapUsed();
        
        // Check for consecutive growth
        int consecutiveGrowthCount = 0;
        for (int i = snapshots.size() - 1; i >= 1; i--) {
            MemorySnapshot current = snapshots.get(i);
            MemorySnapshot prev = snapshots.get(i - 1);
            
            if (current.getHeapUsed() > prev.getHeapUsed()) {
                consecutiveGrowthCount++;
            } else {
                break;
            }
        }
        
        // Alert if memory leak pattern detected
        if (growthRate > MEMORY_GROWTH_THRESHOLD && consecutiveGrowthCount >= CONSECUTIVE_GROWTH_LIMIT) {
            logger.error("MEMORY LEAK DETECTED: Growth rate: {:.2f}%, Consecutive growth: {} times", 
                        growthRate * 100, consecutiveGrowthCount);
            
            // Only trigger heap dump if we haven't exceeded report limits
            if (reportCounter.get() < MAX_REPORTS_PER_SESSION) {
                triggerHeapDump(experimentId);
            }
        }
    }
    
    /**
     * Analyze memory usage trends
     */
    private void analyzeMemoryTrends(String experimentId) {
        List<MemorySnapshot> snapshots = getMemorySnapshots(experimentId);
        if (snapshots.size() < 5) {
            return;
        }
        
        // Calculate trend statistics
        double avgGrowthRate = calculateAverageGrowthRate(snapshots);
        double maxMemoryUsage = snapshots.stream()
            .mapToDouble(s -> s.getHeapUsed())
            .max()
            .orElse(0.0);
        
        // Log trend analysis
        if (avgGrowthRate > 0.05) { // 5% average growth
            logger.warn("Memory trend analysis: Average growth rate: {:.2f}%, Max usage: {} MB", 
                       avgGrowthRate * 100, maxMemoryUsage / (1024 * 1024));
        }
    }
    
    /**
     * Calculate average memory growth rate
     */
    private double calculateAverageGrowthRate(List<MemorySnapshot> snapshots) {
        if (snapshots.size() < 2) {
            return 0.0;
        }
        
        double totalGrowthRate = 0.0;
        int growthCount = 0;
        
        for (int i = 1; i < snapshots.size(); i++) {
            MemorySnapshot current = snapshots.get(i);
            MemorySnapshot previous = snapshots.get(i - 1);
            
            if (previous.getHeapUsed() > 0) {
                double growthRate = (double) (current.getHeapUsed() - previous.getHeapUsed()) / previous.getHeapUsed();
                totalGrowthRate += growthRate;
                growthCount++;
            }
        }
        
        return growthCount > 0 ? totalGrowthRate / growthCount : 0.0;
    }
    
    /**
     * Trigger heap dump for memory leak analysis (with limits)
     */
    private void triggerHeapDump(String experimentId) {
        try {
            // Only create heap dump if we haven't exceeded limits
            if (reportCounter.get() < MAX_REPORTS_PER_SESSION) {
                String dumpFileName = String.format("%s/heapdump_%s_%s.hprof", 
                    outputDirectory, sessionId, 
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
                
                logger.info("Triggering heap dump: {}", dumpFileName);
                logger.info("HEAP DUMP RECOMMENDATION: Use jmap -dump:format=b,file={} <pid>", dumpFileName);
            } else {
                logger.warn("Heap dump skipped - maximum reports per session reached");
            }
            
        } catch (Exception e) {
            logger.error("Failed to trigger heap dump", e);
        }
    }
    
    /**
     * Generate comprehensive memory leak report with file rotation
     */
    public void generateMemoryLeakReport(String experimentId) {
        // Check if we've exceeded the report limit
        if (reportCounter.get() >= MAX_REPORTS_PER_SESSION) {
            logger.warn("Memory leak report skipped for {} - maximum reports per session reached", experimentId);
            return;
        }
        
        try {
            // Use session-based file naming to prevent file explosion
            String reportFileName = String.format("%s/memory_leak_report_%s_%d.txt", 
                outputDirectory, sessionId, reportCounter.incrementAndGet());
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(reportFileName))) {
                writer.println("=== MEMORY LEAK DETECTION REPORT ===");
                writer.println("Session ID: " + sessionId);
                writer.println("Report #: " + reportCounter.get());
                writer.println("Experiment ID: " + experimentId);
                writer.println("Generated: " + LocalDateTime.now().format(TIME_FORMATTER));
                writer.println();
                
                // Memory usage summary
                MemorySnapshot latest = memorySnapshots.get(experimentId);
                if (latest != null) {
                    writer.println("=== MEMORY USAGE SUMMARY ===");
                    writer.printf("Heap Used: %d MB%n", latest.getHeapUsed() / (1024 * 1024));
                    writer.printf("Heap Committed: %d MB%n", latest.getHeapCommitted() / (1024 * 1024));
                    writer.printf("Heap Max: %d MB%n", latest.getHeapMax() / (1024 * 1024));
                    writer.printf("Non-Heap Used: %d MB%n", latest.getNonHeapUsed() / (1024 * 1024));
                    writer.printf("Active Threads: %d%n", latest.getActiveThreads());
                    writer.printf("Peak Threads: %d%n", latest.getPeakThreads());
                    writer.println();
                }
                
                // GC statistics
                GCSnapshot gcSnapshot = gcSnapshots.get(experimentId);
                if (gcSnapshot != null) {
                    writer.println("=== GARBAGE COLLECTION STATISTICS ===");
                    writer.printf("Total GC Count: %d%n", gcSnapshot.getTotalGCCount());
                    writer.printf("Total GC Time: %d ms%n", gcSnapshot.getTotalGCTime());
                    writer.printf("GC Efficiency: %.2f%%%n", 
                        calculateGCEfficiency(gcSnapshot));
                    writer.println();
                }
                
                // Memory leak analysis
                writer.println("=== MEMORY LEAK ANALYSIS ===");
                List<MemorySnapshot> snapshots = getMemorySnapshots(experimentId);
                if (snapshots.size() >= 3) {
                    double avgGrowthRate = calculateAverageGrowthRate(snapshots);
                    writer.printf("Average Memory Growth Rate: %.2f%%%n", avgGrowthRate * 100);
                    
                    if (avgGrowthRate > MEMORY_GROWTH_THRESHOLD) {
                        writer.println("WARNING: Potential memory leak detected!");
                        writer.println("Recommendations:");
                        writer.println("1. Review object creation and disposal patterns");
                        writer.println("2. Check for unclosed resources (streams, connections)");
                        writer.println("3. Analyze heap dump for object retention");
                        writer.println("4. Review caching mechanisms and eviction policies");
                    } else {
                        writer.println("No significant memory leak patterns detected.");
                    }
                }
                
                writer.println();
                writer.println("=== MEMORY POOL DETAILS ===");
                for (MemoryPoolMXBean pool : memoryPoolBeans) {
                    MemoryUsage usage = pool.getUsage();
                    writer.printf("Pool: %s%n", pool.getName());
                    writer.printf("  Used: %d MB%n", usage.getUsed() / (1024 * 1024));
                    writer.printf("  Committed: %d MB%n", usage.getCommitted() / (1024 * 1024));
                    writer.printf("  Max: %d MB%n", usage.getMax() / (1024 * 1024));
                    writer.println();
                }
                
                // File management info
                writer.println("=== FILE MANAGEMENT INFO ===");
                writer.printf("Reports generated this session: %d/%d%n", reportCounter.get(), MAX_REPORTS_PER_SESSION);
                writer.printf("Experiments reported: %d%n", reportedExperiments.size());
                writer.printf("Snapshots stored: %d%n", memorySnapshots.size());
                writer.println();
            }
            
            logger.info("Memory leak report generated: {} (Report #{})", reportFileName, reportCounter.get());
            
        } catch (Exception e) {
            logger.error("Failed to generate memory leak report", e);
        }
    }
    
    /**
     * Calculate GC efficiency
     */
    private double calculateGCEfficiency(GCSnapshot snapshot) {
        if (snapshot.getTotalGCCount() == 0) {
            return 100.0;
        }
        
        // Simple efficiency calculation
        // In production, use more sophisticated metrics
        return Math.max(0.0, 100.0 - (snapshot.getTotalGCTime() / 1000.0));
    }
    
    /**
     * Get memory snapshots for analysis
     */
    private List<MemorySnapshot> getMemorySnapshots(String experimentId) {
        // This is a simplified implementation
        // In production, you would store multiple snapshots per experiment
        MemorySnapshot snapshot = memorySnapshots.get(experimentId);
        return snapshot != null ? Arrays.asList(snapshot) : Collections.emptyList();
    }
    
    /**
     * Force garbage collection and measure effectiveness
     */
    public void forceGarbageCollection() {
        try {
            long memoryBefore = memoryBean.getHeapMemoryUsage().getUsed();
            long startTime = System.currentTimeMillis();
            
            System.gc();
            
            long memoryAfter = memoryBean.getHeapMemoryUsage().getUsed();
            long endTime = System.currentTimeMillis();
            
            long memoryFreed = memoryBefore - memoryAfter;
            long gcTime = endTime - startTime;
            
            logger.info("Forced GC completed: Freed {} MB in {} ms", 
                       memoryFreed / (1024 * 1024), gcTime);
            
        } catch (Exception e) {
            logger.error("Error during forced garbage collection", e);
        }
    }
    
    /**
     * Get current memory usage statistics
     */
    public Map<String, Object> getCurrentMemoryStats() {
        Map<String, Object> stats = new HashMap<>();
        
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        stats.put("heapUsed", heapUsage.getUsed());
        stats.put("heapCommitted", heapUsage.getCommitted());
        stats.put("heapMax", heapUsage.getMax());
        stats.put("nonHeapUsed", nonHeapUsage.getUsed());
        stats.put("nonHeapCommitted", nonHeapUsage.getCommitted());
        stats.put("nonHeapMax", nonHeapUsage.getMax());
        stats.put("activeThreads", threadBean.getThreadCount());
        stats.put("peakThreads", threadBean.getPeakThreadCount());
        stats.put("totalGCCount", gcCount.get());
        stats.put("totalGCTime", totalGCTime.get());
        
        return stats;
    }
    
    /**
     * Shutdown the memory leak detector
     */
    public void shutdown() {
        monitoringEnabled = false;
        monitorExecutor.shutdown();
        
        try {
            if (!monitorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                monitorExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitorExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("MemoryLeakDetector shutdown completed");
    }
    
    // Configuration methods
    public void setMonitoringInterval(long intervalMs) {
        this.monitoringIntervalMs = intervalMs;
    }
    
    public void setOutputDirectory(String directory) {
        this.outputDirectory = directory;
    }
    
    public void setMaxSnapshots(int max) {
        this.maxSnapshots = max;
    }
    
    // Data classes
    public static class MemorySnapshot {
        private final LocalDateTime timestamp;
        private final long heapUsed;
        private final long heapCommitted;
        private final long heapMax;
        private final long nonHeapUsed;
        private final long nonHeapCommitted;
        private final long nonHeapMax;
        private final int activeThreads;
        private final int peakThreads;
        private final long totalMemory;
        
        public MemorySnapshot(LocalDateTime timestamp, long heapUsed, long heapCommitted, 
                            long heapMax, long nonHeapUsed, long nonHeapCommitted, 
                            long nonHeapMax, int activeThreads, int peakThreads, long totalMemory) {
            this.timestamp = timestamp;
            this.heapUsed = heapUsed;
            this.heapCommitted = heapCommitted;
            this.heapMax = heapMax;
            this.nonHeapUsed = nonHeapUsed;
            this.nonHeapCommitted = nonHeapCommitted;
            this.nonHeapMax = nonHeapMax;
            this.activeThreads = activeThreads;
            this.peakThreads = peakThreads;
            this.totalMemory = totalMemory;
        }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public long getHeapUsed() { return heapUsed; }
        public long getHeapCommitted() { return heapCommitted; }
        public long getHeapMax() { return heapMax; }
        public long getNonHeapUsed() { return nonHeapUsed; }
        public long getNonHeapCommitted() { return nonHeapCommitted; }
        public long getNonHeapMax() { return nonHeapMax; }
        public int getActiveThreads() { return activeThreads; }
        public int getPeakThreads() { return peakThreads; }
        public long getTotalMemory() { return totalMemory; }
    }
    
    public static class GCSnapshot {
        private final LocalDateTime timestamp;
        private final long totalGCCount;
        private final long totalGCTime;
        private final int gcCount;
        
        public GCSnapshot(LocalDateTime timestamp, long totalGCCount, 
                         long totalGCTime, int gcCount) {
            this.timestamp = timestamp;
            this.totalGCCount = totalGCCount;
            this.totalGCTime = totalGCTime;
            this.gcCount = gcCount;
        }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public long getTotalGCCount() { return totalGCCount; }
        public long getTotalGCTime() { return totalGCTime; }
        public int getGcCount() { return gcCount; }
    }
} 