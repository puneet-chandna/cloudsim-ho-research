package org.cloudbus.cloudsim.experiment;

import org.cloudbus.cloudsim.util.ExperimentException;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.ResourceMonitor;
import org.cloudbus.cloudsim.util.ValidationUtils;
import org.cloudbus.cloudsim.analyzer.ComprehensiveStatisticalAnalyzer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Execute batch experiments with parallel processing and failure handling
 * Supporting research objectives: large-scale experimentation and statistical validity
 * @author Puneet Chandna
 */
public class BatchExperimentExecutor {
    
    private final ExecutorService executorService;
    private final Map<String, ExperimentalResult> completedExperiments;
    private final Map<String, Exception> failedExperiments;
    private ProgressMonitor progressMonitor;
    private final ResourceMonitor resourceMonitor;
    
    // Batch execution configuration
    private int maxRetries = 3;
    private long retryDelayMs = 5000;
    private boolean stopOnFailure = false;
    
    /**
     * Progress monitoring for batch execution
     */
    private static class ProgressMonitor {
        private final int totalExperiments;
        private final AtomicInteger completed;
        private final AtomicInteger failed;
        private final LocalDateTime startTime;
        private volatile LocalDateTime lastUpdate;
        
        public ProgressMonitor(int totalExperiments) {
            this.totalExperiments = totalExperiments;
            this.completed = new AtomicInteger(0);
            this.failed = new AtomicInteger(0);
            this.startTime = LocalDateTime.now();
            this.lastUpdate = startTime;
        }
        
        public void incrementCompleted() {
            completed.incrementAndGet();
            lastUpdate = LocalDateTime.now();
        }
        
        public void incrementFailed() {
            failed.incrementAndGet();
            lastUpdate = LocalDateTime.now();
        }
        
        public double getProgressPercentage() {
            return ((completed.get() + failed.get()) * 100.0) / totalExperiments;
        }
        
        public Duration getElapsedTime() {
            return Duration.between(startTime, LocalDateTime.now());
        }
        
        public Duration getEstimatedTimeRemaining() {
            int processed = completed.get() + failed.get();
            if (processed == 0) return Duration.ZERO;
            
            Duration elapsed = getElapsedTime();
            long avgTimePerExperiment = elapsed.toMillis() / processed;
            long remainingExperiments = totalExperiments - processed;
            
            return Duration.ofMillis(avgTimePerExperiment * remainingExperiments);
        }
        
        public String getProgressReport() {
            return String.format(
                "Progress: %.1f%% | Completed: %d | Failed: %d | Total: %d | " +
                "Elapsed: %s | ETA: %s",
                getProgressPercentage(),
                completed.get(),
                failed.get(),
                totalExperiments,
                formatDuration(getElapsedTime()),
                formatDuration(getEstimatedTimeRemaining())
            );
        }
        
        private String formatDuration(Duration duration) {
            long hours = duration.toHours();
            long minutes = duration.toMinutesPart();
            long seconds = duration.toSecondsPart();
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
    }
    
    // Constructors
    public BatchExperimentExecutor() {
        this(Runtime.getRuntime().availableProcessors());
    }
    
    public BatchExperimentExecutor(int maxParallelThreads) {
        this.executorService = Executors.newFixedThreadPool(
            maxParallelThreads,
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setName("BatchExperiment-" + threadNumber.getAndIncrement());
                    t.setDaemon(false);
                    return t;
                }
            }
        );
        this.completedExperiments = new ConcurrentHashMap<>();
        this.failedExperiments = new ConcurrentHashMap<>();
        this.resourceMonitor = new ResourceMonitor();
    }
    
    /**
     * Execute batch of experiments with monitoring and failure handling
     */
    public BatchExecutionResult executeBatch(List<ExperimentConfig> configs) {
        try {
            ValidationUtils.validateNotNull(configs, "Experiment configurations cannot be null");
            ValidationUtils.validateNotEmpty(configs, "Experiment configurations cannot be empty");
            
            LoggingManager.logInfo("Starting batch execution of " + configs.size() + " experiments");
            
            // Initialize progress monitor
            progressMonitor = new ProgressMonitor(configs.size());
            
            // Clear previous results
            completedExperiments.clear();
            failedExperiments.clear();
            
            // Start resource monitoring
            resourceMonitor.startMonitoring();
            
            // Start progress monitoring
            ScheduledExecutorService progressExecutor = Executors.newSingleThreadScheduledExecutor();
            progressExecutor.scheduleAtFixedRate(
                this::reportProgress, 
                0, 
                10, 
                TimeUnit.SECONDS
            );
            
            BatchExecutionResult batchResult;
            
            try {
                // Check if parallel execution is enabled
                boolean parallelEnabled = configs.stream()
                    .anyMatch(config -> config.isParallelExecutionEnabled());
                
                if (parallelEnabled) {
                    batchResult = parallelExecution(configs);
                } else {
                    batchResult = sequentialExecution(configs);
                }
            } finally {
                // Stop progress monitoring
                progressExecutor.shutdown();
                resourceMonitor.stopMonitoring();
                
                // Final progress report
                reportProgress();
            }
            
            // Handle failures
            handleBatchFailures();
            
            // Perform batch analysis
            performBatchAnalysis(batchResult);
            
            return batchResult;
            
        } catch (Exception e) {
            LoggingManager.logError("Fatal error in batch execution", e);
            throw new ExperimentException("Batch execution failed", e);
        }
    }
    
    /**
     * Execute experiments in parallel
     */
    public BatchExecutionResult parallelExecution(List<ExperimentConfig> configs) {
        LoggingManager.logInfo("Executing experiments in parallel mode");
        
        List<CompletableFuture<ExperimentExecutionResult>> futures = new ArrayList<>();
        
        for (ExperimentConfig config : configs) {
            CompletableFuture<ExperimentExecutionResult> future = CompletableFuture
                .supplyAsync(() -> executeExperimentWithRetry(config), executorService)
                .exceptionally(throwable -> {
                    LoggingManager.logError("Experiment failed: " + config.getExperimentName(), 
                                  (Exception) throwable);
                    progressMonitor.incrementFailed();
                    failedExperiments.put(config.getExperimentName(), (Exception) throwable);
                    return new ExperimentExecutionResult(config, null, (Exception) throwable);
                });
            
            futures.add(future);
        }
        
        // Wait for all experiments to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        try {
            allFutures.join();
        } catch (CompletionException e) {
            LoggingManager.logError("Batch execution encountered errors", e);
        }
        
        // Collect results
        List<ExperimentExecutionResult> executionResults = futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());
        
        return createBatchResult(executionResults);
    }
    
    /**
     * Execute experiments sequentially
     */
    private BatchExecutionResult sequentialExecution(List<ExperimentConfig> configs) {
        LoggingManager.logInfo("Executing experiments in sequential mode");
        
        List<ExperimentExecutionResult> executionResults = new ArrayList<>();
        
        for (ExperimentConfig config : configs) {
            if (stopOnFailure && !failedExperiments.isEmpty()) {
                LoggingManager.logInfo("Stopping batch execution due to failure");
                break;
            }
            
            ExperimentExecutionResult result = executeExperimentWithRetry(config);
            executionResults.add(result);
            
            // Small delay between experiments to avoid resource contention
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        return createBatchResult(executionResults);
    }
    
    /**
     * Execute single experiment with retry logic
     */
    private ExperimentExecutionResult executeExperimentWithRetry(ExperimentConfig config) {
        ExperimentRunner runner = null;
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                LoggingManager.logInfo(String.format("Executing experiment: %s (attempt %d/%d)",
                    config.getExperimentName(), attempt, maxRetries));
                
                runner = new ExperimentRunner();
                
                // Execute experiment with replications
                List<ExperimentalResult> replicationResults = new ArrayList<>();
                int replications = config.getReplications();
                
                for (int rep = 1; rep <= replications; rep++) {
                    // Create replication config
                    ExperimentConfig repConfig = createReplicationConfig(config, rep);
                    
                    ExperimentalResult result = runner.runExperiment(repConfig);
                    replicationResults.add(result);
                }
                
                // Aggregate replication results
                ExperimentalResult aggregatedResult = aggregateReplicationResults(
                    config, replicationResults
                );
                
                completedExperiments.put(config.getExperimentName(), aggregatedResult);
                progressMonitor.incrementCompleted();
                
                return new ExperimentExecutionResult(config, aggregatedResult, null);
                
            } catch (Exception e) {
                lastException = e;
                LoggingManager.logError(String.format(
                    "Experiment failed (attempt %d/%d): %s",
                    attempt, maxRetries, config.getExperimentName()
                ), e);
                
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                if (runner != null) {
                    runner.cleanup();
                }
            }
        }
        
        // All retries failed
        progressMonitor.incrementFailed();
        failedExperiments.put(config.getExperimentName(), lastException);
        return new ExperimentExecutionResult(config, null, lastException);
    }
    
    /**
     * Create configuration for experiment replication
     */
    private ExperimentConfig createReplicationConfig(ExperimentConfig original, int replicationNumber) {
        ExperimentConfig repConfig = new ExperimentConfig();
        
        // Copy all settings from original
        repConfig.setExperimentName(original.getExperimentName() + "_rep" + replicationNumber);
        repConfig.setAlgorithmConfig(original.getAlgorithmConfig());
        repConfig.setScenarioConfig(original.getScenarioConfig());
        repConfig.setMeasurementSettings(original.getMeasurementSettings());
        repConfig.setOutputSettings(original.getOutputSettings());
        
        // Modify seed for replication
        repConfig.setRandomSeed(original.getRandomSeed() + replicationNumber);
        
        // Set replication to 1 to avoid nested replications
        repConfig.setReplications(1);
        
        return repConfig;
    }
    
    /**
     * Aggregate results from multiple replications
     */
    private ExperimentalResult aggregateReplicationResults(ExperimentConfig config,
                                                         List<ExperimentalResult> replications) {
        ExperimentalResult aggregated = new ExperimentalResult();
        aggregated.setExperimentConfig(config);
        aggregated.setStartTime(replications.get(0).getStartTime());
        aggregated.setEndTime(replications.get(replications.size() - 1).getEndTime());
        
        // Aggregate performance metrics
        aggregatePerformanceMetrics(aggregated, replications);
        
        // Aggregate raw data
        aggregateRawData(aggregated, replications);
        
        // Calculate statistical measures across replications
        calculateReplicationStatistics(aggregated, replications);
        
        return aggregated;
    }
    
    /**
     * Aggregate performance metrics from replications
     */
    private void aggregatePerformanceMetrics(ExperimentalResult aggregated,
                                           List<ExperimentalResult> replications) {
        ExperimentalResult.PerformanceMetrics avgMetrics = aggregated.getPerformanceMetrics();
        
        // Calculate averages across replications
        double avgCpuUtil = replications.stream()
            .mapToDouble(r -> r.getPerformanceMetrics().getResourceUtilization()
                .getAvgCpuUtilization())
            .average()
            .orElse(0.0);
        
        avgMetrics.getResourceUtilization().setAvgCpuUtilization(avgCpuUtil);
        
        double avgMemUtil = replications.stream()
            .mapToDouble(r -> r.getPerformanceMetrics().getResourceUtilization()
                .getAvgMemoryUtilization())
            .average()
            .orElse(0.0);
        
        avgMetrics.getResourceUtilization().setAvgMemoryUtilization(avgMemUtil);
        
        double totalPower = replications.stream()
            .mapToDouble(r -> r.getPerformanceMetrics().getPowerConsumption()
                .getTotalPowerConsumption())
            .average()
            .orElse(0.0);
        
        avgMetrics.getPowerConsumption().setTotalPowerConsumption(totalPower);
        
        int totalViolations = (int) replications.stream()
            .mapToInt(r -> r.getPerformanceMetrics().getSlaViolations()
                .getTotalViolations())
            .average()
            .orElse(0.0);
        
        avgMetrics.getSlaViolations().setTotalViolations(totalViolations);
    }
    
    /**
     * Aggregate raw data from replications
     */
    private void aggregateRawData(ExperimentalResult aggregated,
                                List<ExperimentalResult> replications) {
        Map<String, List<Double>> aggregatedRawData = new HashMap<>();
        
        // Collect all raw data points
        for (ExperimentalResult rep : replications) {
            for (Map.Entry<String, List<Double>> entry : rep.getRawData().entrySet()) {
                aggregatedRawData.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                    .addAll(entry.getValue());
            }
        }
        
        aggregated.setRawData(aggregatedRawData);
    }
    
    /**
     * Calculate statistics across replications
     */
    private void calculateReplicationStatistics(ExperimentalResult aggregated,
                                              List<ExperimentalResult> replications) {
        // Use comprehensive statistical analyzer
        ComprehensiveStatisticalAnalyzer analyzer = new ComprehensiveStatisticalAnalyzer();
        analyzer.performDescriptiveAnalysis(Collections.singletonList(aggregated));
    }
    
    /**
     * Create batch execution result
     */
    private BatchExecutionResult createBatchResult(List<ExperimentExecutionResult> execResults) {
        BatchExecutionResult batchResult = new BatchExecutionResult();
        
        batchResult.setStartTime(progressMonitor.startTime);
        batchResult.setEndTime(LocalDateTime.now());
        batchResult.setTotalExperiments(execResults.size());
        batchResult.setSuccessfulExperiments(completedExperiments.size());
        batchResult.setFailedExperiments(failedExperiments.size());
        batchResult.setExecutionResults(execResults);
        batchResult.setCompletedResults(new HashMap<>(completedExperiments));
        batchResult.setFailureReasons(new HashMap<>(failedExperiments));
        
        return batchResult;
    }
    
    /**
     * Monitor batch execution progress
     */
    public void monitorBatchProgress() {
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        
        monitor.scheduleAtFixedRate(() -> {
            try {
                double cpuUsage = resourceMonitor.monitorCPUUsage();
                double memoryUsage = resourceMonitor.monitorMemoryUsage();
                
                LoggingManager.logInfo(String.format(
                    "Batch Progress: %s | System Resources - CPU: %.1f%%, Memory: %.1f%%",
                    progressMonitor.getProgressReport(),
                    cpuUsage,
                    memoryUsage
                ));
            } catch (Exception e) {
                LoggingManager.logError("Error monitoring batch progress", e);
            }
        }, 0, 30, TimeUnit.SECONDS);
    }
    
    /**
     * Report current progress
     */
    private void reportProgress() {
        if (progressMonitor != null) {
            LoggingManager.logInfo(progressMonitor.getProgressReport());
        }
    }
    
    /**
     * Handle batch execution failures
     */
    public void handleBatchFailures() {
        if (!failedExperiments.isEmpty()) {
            LoggingManager.logWarning("Failed experiments detected: " + failedExperiments.size());
            
            // Generate failure report
            StringBuilder failureReport = new StringBuilder();
            failureReport.append("\n=== Batch Execution Failure Report ===\n");
            failureReport.append("Generated at: ").append(LocalDateTime.now()).append("\n\n");
            
            for (Map.Entry<String, Exception> entry : failedExperiments.entrySet()) {
                failureReport.append(String.format(
                    "Experiment: %s\nError Type: %s\nError Message: %s\nStack Trace: %s\n\n",
                    entry.getKey(),
                    entry.getValue().getClass().getName(),
                    entry.getValue().getMessage(),
                    getStackTraceString(entry.getValue())
                ));
            }
            
            LoggingManager.logWarning(failureReport.toString());
            
            // Save failure report to file
            saveFailureReport(failureReport.toString());
        }
    }
    
    /**
     * Get stack trace as string
     */
    private String getStackTraceString(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * Save failure report to file
     */
    private void saveFailureReport(String report) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File reportFile = new File("results/batch_failures_" + timestamp + ".txt");
            reportFile.getParentFile().mkdirs();
            
            Files.write(reportFile.toPath(), report.getBytes(), 
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            LoggingManager.logInfo("Failure report saved to: " + reportFile.getAbsolutePath());
        } catch (IOException e) {
            LoggingManager.logError("Failed to save failure report", e);
        }
    }
    
    /**
     * Perform analysis across all batch results
     */
    private void performBatchAnalysis(BatchExecutionResult batchResult) {
        if (completedExperiments.size() > 1) {
            LoggingManager.logInfo("Performing cross-experiment analysis");
            
            try {
                // Statistical comparison across experiments
                ComprehensiveStatisticalAnalyzer analyzer = new ComprehensiveStatisticalAnalyzer();
                List<ExperimentalResult> results = new ArrayList<>(completedExperiments.values());
                
                analyzer.performInferentialAnalysis(results);
                
                // Calculate batch-level statistics
                calculateBatchStatistics(batchResult);
                
            } catch (Exception e) {
                LoggingManager.logError("Error performing batch analysis", e);
            }
        }
    }
    
    /**
     * Calculate statistics across the entire batch
     */
    private void calculateBatchStatistics(BatchExecutionResult batchResult) {
        List<ExperimentalResult> successfulResults = new ArrayList<>(completedExperiments.values());
        
        if (successfulResults.isEmpty()) {
            return;
        }
        
        // Calculate mean execution time
        double avgExecutionTime = successfulResults.stream()
            .mapToLong(result -> Duration.between(result.getStartTime(), result.getEndTime()).toMillis())
            .average()
            .orElse(0.0);
        
        // Calculate success rate
        double successRate = batchResult.getSuccessRate();
        
        // Calculate average metrics across all successful experiments
        double avgResourceUtil = successfulResults.stream()
            .mapToDouble(r -> r.getPerformanceMetrics().getResourceUtilization().getAvgCpuUtilization())
            .average()
            .orElse(0.0);
        
        double avgPowerConsumption = successfulResults.stream()
            .mapToDouble(r -> r.getPerformanceMetrics().getPowerConsumption().getTotalPowerConsumption())
            .average()
            .orElse(0.0);
        
        // Log batch statistics
        LoggingManager.logInfo(String.format(
            "Batch Statistics: Success Rate: %.2f%%, Avg Execution Time: %.2f ms, " +
            "Avg Resource Utilization: %.2f%%, Avg Power Consumption: %.2f W",
            successRate, avgExecutionTime, avgResourceUtil * 100, avgPowerConsumption
        ));
    }
    
    /**
     * Shutdown executor service and cleanup resources
     */
    public void shutdown() {
        LoggingManager.logInfo("Shutting down BatchExperimentExecutor");
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    LoggingManager.logWarning("Executor did not terminate gracefully");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // Getters and setters
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = Math.max(1, maxRetries);
    }
    
    public void setRetryDelayMs(long retryDelayMs) {
        this.retryDelayMs = Math.max(0, retryDelayMs);
    }
    
    public void setStopOnFailure(boolean stopOnFailure) {
        this.stopOnFailure = stopOnFailure;
    }
    
    public Map<String, ExperimentalResult> getCompletedExperiments() {
        return new HashMap<>(completedExperiments);
    }
    
    public Map<String, Exception> getFailedExperiments() {
        return new HashMap<>(failedExperiments);
    }
    
    /**
     * Result of single experiment execution
     */
    public static class ExperimentExecutionResult {
        private final ExperimentConfig config;
        private final ExperimentalResult result;
        private final Exception exception;
        
        public ExperimentExecutionResult(ExperimentConfig config,
                                       ExperimentalResult result,
                                       Exception exception) {
            this.config = config;
            this.result = result;
            this.exception = exception;
        }
        
        public boolean isSuccessful() {
            return exception == null && result != null;
        }
        
        public ExperimentConfig getConfig() { return config; }
        public ExperimentalResult getResult() { return result; }
        public Exception getException() { return exception; }
        
        @Override
        public String toString() {
            return String.format("ExperimentExecutionResult{config=%s, successful=%s}", 
                config.getExperimentName(), isSuccessful());
        }
    }
    
    /**
     * Result of batch execution
     */
    public static class BatchExecutionResult {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private int totalExperiments;
        private int successfulExperiments;
        private int failedExperiments;
        private List<ExperimentExecutionResult> executionResults;
        private Map<String, ExperimentalResult> completedResults;
        private Map<String, Exception> failureReasons;
        
        public BatchExecutionResult() {
            this.executionResults = new ArrayList<>();
            this.completedResults = new HashMap<>();
            this.failureReasons = new HashMap<>();
        }
        
        // Getters and setters
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { 
            this.startTime = startTime; 
        }
        
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { 
            this.endTime = endTime; 
        }
        
        public int getTotalExperiments() { return totalExperiments; }
        public void setTotalExperiments(int totalExperiments) { 
            this.totalExperiments = totalExperiments; 
        }
        
        public int getSuccessfulExperiments() { return successfulExperiments; }
        public void setSuccessfulExperiments(int successfulExperiments) { 
            this.successfulExperiments = successfulExperiments; 
        }
        
        public int getFailedExperiments() { return failedExperiments; }
        public void setFailedExperiments(int failedExperiments) { 
            this.failedExperiments = failedExperiments; 
        }
        
        public List<ExperimentExecutionResult> getExecutionResults() { 
            return executionResults; 
        }
        public void setExecutionResults(List<ExperimentExecutionResult> executionResults) { 
            this.executionResults = executionResults != null ? executionResults : new ArrayList<>();
        }
        
        public Map<String, ExperimentalResult> getCompletedResults() { 
            return completedResults; 
        }
        public void setCompletedResults(Map<String, ExperimentalResult> completedResults) { 
            this.completedResults = completedResults != null ? completedResults : new HashMap<>();
        }
        
        public Map<String, Exception> getFailureReasons() { 
            return failureReasons; 
        }
        public void setFailureReasons(Map<String, Exception> failureReasons) { 
            this.failureReasons = failureReasons != null ? failureReasons : new HashMap<>();
        }
        
        public Duration getTotalExecutionTime() {
            if (startTime != null && endTime != null) {
                return Duration.between(startTime, endTime);
            }
            return Duration.ZERO;
        }
        
        public double getSuccessRate() {
            if (totalExperiments == 0) return 0.0;
            return (successfulExperiments * 100.0) / totalExperiments;
        }
        
        public boolean hasFailures() {
            return failedExperiments > 0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "BatchExecutionResult{total=%d, successful=%d, failed=%d, successRate=%.2f%%, duration=%s}",
                totalExperiments, successfulExperiments, failedExperiments, 
                getSuccessRate(), getTotalExecutionTime()
            );
        }
    }
}