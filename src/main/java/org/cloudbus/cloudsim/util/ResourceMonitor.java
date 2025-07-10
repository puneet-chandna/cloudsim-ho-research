package org.cloudbus.cloudsim.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Monitor system resources during experiments
 * @author Puneet Chandna
 */
public class ResourceMonitor {
    private static final Logger logger = LoggerFactory.getLogger(ResourceMonitor.class);
    private static ResourceMonitor instance;
    
    private final OperatingSystemMXBean osBean;
    private final Runtime runtime;
    private final ScheduledExecutorService monitorExecutor;
    private final Map<String, List<ResourceSnapshot>> monitoringSessions;
    private final Map<String, ScheduledFuture<?>> activeMonitors;
    
    private static final long MONITORING_INTERVAL_MS = 1000; // 1 second
    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    private ResourceMonitor() {
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.runtime = Runtime.getRuntime();
        this.monitorExecutor = Executors.newScheduledThreadPool(2);
        this.monitoringSessions = new ConcurrentHashMap<>();
        this.activeMonitors = new ConcurrentHashMap<>();
    }
    
    public static synchronized ResourceMonitor getInstance() {
        if (instance == null) {
            instance = new ResourceMonitor();
        }
        return instance;
    }
    
    /**
     * Monitor CPU usage
     */
    public double monitorCPUUsage() {
        double systemLoad = osBean.getSystemLoadAverage();
        int availableProcessors = osBean.getAvailableProcessors();
        
        // If system load average is available, use it as CPU usage approximation
        if (systemLoad >= 0) {
            return Math.min(100.0, (systemLoad / availableProcessors) * 100.0);
        }
        
        // Fallback: use a simple calculation based on active threads
        return Math.min(100.0, (Thread.activeCount() / (double) availableProcessors) * 25.0);
    }
    
    /**
     * Monitor memory usage
     */
    public MemoryUsage monitorMemoryUsage() {
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        return new MemoryUsage(
            usedMemory,
            totalMemory,
            maxMemory,
            (double) usedMemory / maxMemory * 100.0
        );
    }
    
    /**
     * Monitor disk I/O
     */
    public DiskUsage monitorDiskUsage() {
        File root = new File("/");
        long totalSpace = root.getTotalSpace();
        long freeSpace = root.getFreeSpace();
        long usedSpace = totalSpace - freeSpace;
        
        return new DiskUsage(
            usedSpace,
            totalSpace,
            freeSpace,
            (double) usedSpace / totalSpace * 100.0
        );
    }
    
    /**
     * Start continuous monitoring for an experiment
     */
    public void startMonitoring(String experimentId) {
        if (activeMonitors.containsKey(experimentId)) {
            logger.warn("Monitoring already active for experiment: {}", experimentId);
            return;
        }
        
        List<ResourceSnapshot> snapshots = new ArrayList<>();
        monitoringSessions.put(experimentId, snapshots);
        
        ScheduledFuture<?> future = monitorExecutor.scheduleAtFixedRate(() -> {
            try {
                ResourceSnapshot snapshot = captureSnapshot();
                snapshots.add(snapshot);
            } catch (Exception e) {
                logger.error("Error capturing resource snapshot", e);
            }
        }, 0, MONITORING_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        activeMonitors.put(experimentId, future);
        logger.info("Started resource monitoring for experiment: {}", experimentId);
    }
    
    /**
     * Stop monitoring for an experiment
     */
    public void stopMonitoring(String experimentId) {
        ScheduledFuture<?> future = activeMonitors.remove(experimentId);
        if (future != null) {
            future.cancel(false);
            logger.info("Stopped resource monitoring for experiment: {}", experimentId);
        }
    }
    
    /**
     * Get monitoring data for an experiment
     */
    public List<ResourceSnapshot> getMonitoringData(String experimentId) {
        return monitoringSessions.getOrDefault(experimentId, Collections.emptyList());
    }
    
    /**
     * Clear monitoring data for an experiment
     */
    public void clearMonitoringData(String experimentId) {
        monitoringSessions.remove(experimentId);
    }
    
    /**
     * Capture current resource snapshot
     */
    private ResourceSnapshot captureSnapshot() {
        return new ResourceSnapshot(
            LocalDateTime.now(),
            monitorCPUUsage(),
            monitorMemoryUsage(),
            monitorDiskUsage(),
            Thread.activeCount()
        );
    }
    
    /**
     * Generate resource usage report
     */
    public ResourceReport generateResourceReport(String experimentId) {
        List<ResourceSnapshot> snapshots = getMonitoringData(experimentId);
        if (snapshots.isEmpty()) {
            return new ResourceReport(experimentId, Collections.emptyList(), null);
        }
        
        // Calculate statistics
        DoubleSummaryStatistics cpuStats = snapshots.stream()
            .mapToDouble(ResourceSnapshot::getCpuUsage)
            .summaryStatistics();
            
        DoubleSummaryStatistics memoryStats = snapshots.stream()
            .mapToDouble(s -> s.getMemoryUsage().getUsagePercentage())
            .summaryStatistics();
            
        DoubleSummaryStatistics diskStats = snapshots.stream()
            .mapToDouble(s -> s.getDiskUsage().getUsagePercentage())
            .summaryStatistics();
        
        ResourceStatistics statistics = new ResourceStatistics(
            cpuStats.getAverage(),
            cpuStats.getMax(),
            cpuStats.getMin(),
            memoryStats.getAverage(),
            memoryStats.getMax(),
            memoryStats.getMin(),
            diskStats.getAverage(),
            diskStats.getMax(),
            diskStats.getMin()
        );
        
        return new ResourceReport(experimentId, snapshots, statistics);
    }
    
    /**
     * Shutdown the resource monitor
     */
    public void shutdown() {
        // Cancel all active monitors
        activeMonitors.values().forEach(future -> future.cancel(false));
        activeMonitors.clear();
        
        // Shutdown executor
        monitorExecutor.shutdown();
        try {
            if (!monitorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                monitorExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitorExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("Resource monitor shutdown completed");
    }
    
    // Inner classes for data structures
    public static class MemoryUsage {
        private final long usedMemory;
        private final long totalMemory;
        private final long maxMemory;
        private final double usagePercentage;
        
        public MemoryUsage(long usedMemory, long totalMemory, long maxMemory, double usagePercentage) {
            this.usedMemory = usedMemory;
            this.totalMemory = totalMemory;
            this.maxMemory = maxMemory;
            this.usagePercentage = usagePercentage;
        }
        
        // Getters
        public long getUsedMemory() { return usedMemory; }
        public long getTotalMemory() { return totalMemory; }
        public long getMaxMemory() { return maxMemory; }
        public double getUsagePercentage() { return usagePercentage; }
    }
    
    public static class DiskUsage {
        private final long usedSpace;
        private final long totalSpace;
        private final long freeSpace;
        private final double usagePercentage;
        
        public DiskUsage(long usedSpace, long totalSpace, long freeSpace, double usagePercentage) {
            this.usedSpace = usedSpace;
            this.totalSpace = totalSpace;
            this.freeSpace = freeSpace;
            this.usagePercentage = usagePercentage;
        }
        
        // Getters
        public long getUsedSpace() { return usedSpace; }
        public long getTotalSpace() { return totalSpace; }
        public long getFreeSpace() { return freeSpace; }
        public double getUsagePercentage() { return usagePercentage; }
    }
    
    public static class ResourceSnapshot {
        private final LocalDateTime timestamp;
        private final double cpuUsage;
        private final MemoryUsage memoryUsage;
        private final DiskUsage diskUsage;
        private final int activeThreads;
        
        public ResourceSnapshot(LocalDateTime timestamp, double cpuUsage, 
                              MemoryUsage memoryUsage, DiskUsage diskUsage, int activeThreads) {
            this.timestamp = timestamp;
            this.cpuUsage = cpuUsage;
            this.memoryUsage = memoryUsage;
            this.diskUsage = diskUsage;
            this.activeThreads = activeThreads;
        }
        
        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public double getCpuUsage() { return cpuUsage; }
        public MemoryUsage getMemoryUsage() { return memoryUsage; }
        public DiskUsage getDiskUsage() { return diskUsage; }
        public int getActiveThreads() { return activeThreads; }
        
        @Override
        public String toString() {
            return String.format("[%s] CPU: %.2f%%, Memory: %.2f%%, Disk: %.2f%%, Threads: %d",
                timestamp.format(TIME_FORMATTER),
                cpuUsage,
                memoryUsage.getUsagePercentage(),
                diskUsage.getUsagePercentage(),
                activeThreads);
        }
    }
    
    public static class ResourceStatistics {
        private final double avgCpuUsage;
        private final double maxCpuUsage;
        private final double minCpuUsage;
        private final double avgMemoryUsage;
        private final double maxMemoryUsage;
        private final double minMemoryUsage;
        private final double avgDiskUsage;
        private final double maxDiskUsage;
        private final double minDiskUsage;
        
        public ResourceStatistics(double avgCpuUsage, double maxCpuUsage, double minCpuUsage,
                                double avgMemoryUsage, double maxMemoryUsage, double minMemoryUsage,
                                double avgDiskUsage, double maxDiskUsage, double minDiskUsage) {
            this.avgCpuUsage = avgCpuUsage;
            this.maxCpuUsage = maxCpuUsage;
            this.minCpuUsage = minCpuUsage;
            this.avgMemoryUsage = avgMemoryUsage;
            this.maxMemoryUsage = maxMemoryUsage;
            this.minMemoryUsage = minMemoryUsage;
            this.avgDiskUsage = avgDiskUsage;
            this.maxDiskUsage = maxDiskUsage;
            this.minDiskUsage = minDiskUsage;
        }
        
        // Getters
        public double getAvgCpuUsage() { return avgCpuUsage; }
        public double getMaxCpuUsage() { return maxCpuUsage; }
        public double getMinCpuUsage() { return minCpuUsage; }
        public double getAvgMemoryUsage() { return avgMemoryUsage; }
        public double getMaxMemoryUsage() { return maxMemoryUsage; }
        public double getMinMemoryUsage() { return minMemoryUsage; }
        public double getAvgDiskUsage() { return avgDiskUsage; }
        public double getMaxDiskUsage() { return maxDiskUsage; }
        public double getMinDiskUsage() { return minDiskUsage; }
        
        @Override
        public String toString() {
            return String.format(
                "Resource Statistics:\n" +
                "  CPU Usage - Avg: %.2f%%, Max: %.2f%%, Min: %.2f%%\n" +
                "  Memory Usage - Avg: %.2f%%, Max: %.2f%%, Min: %.2f%%\n" +
                "  Disk Usage - Avg: %.2f%%, Max: %.2f%%, Min: %.2f%%",
                avgCpuUsage, maxCpuUsage, minCpuUsage,
                avgMemoryUsage, maxMemoryUsage, minMemoryUsage,
                avgDiskUsage, maxDiskUsage, minDiskUsage
            );
        }
    }
    
    public static class ResourceReport {
        private final String experimentId;
        private final List<ResourceSnapshot> snapshots;
        private final ResourceStatistics statistics;
        
        public ResourceReport(String experimentId, List<ResourceSnapshot> snapshots, 
                            ResourceStatistics statistics) {
            this.experimentId = experimentId;
            this.snapshots = new ArrayList<>(snapshots);
            this.statistics = statistics;
        }
        
        // Getters
        public String getExperimentId() { return experimentId; }
        public List<ResourceSnapshot> getSnapshots() { return new ArrayList<>(snapshots); }
        public ResourceStatistics getStatistics() { return statistics; }
        
        public void printReport() {
            System.out.println("\n=== Resource Usage Report ===");
            System.out.println("Experiment ID: " + experimentId);
            System.out.println("Total Snapshots: " + snapshots.size());
            if (statistics != null) {
                System.out.println(statistics);
            }
        }
    }
}