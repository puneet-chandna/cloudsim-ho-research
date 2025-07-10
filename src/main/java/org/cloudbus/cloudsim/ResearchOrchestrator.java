package org.cloudbus.cloudsim;

import org.cloudbus.cloudsim.experiment.*;
import org.cloudbus.cloudsim.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Orchestrates the entire research workflow.
 * Manages experiment sequencing, progress monitoring, failure handling,
 * and result aggregation for comprehensive research execution.
 * 
 * Research objectives addressed:
 * - Efficient experiment execution management
 * - Robust failure handling and recovery
 * - Progress tracking and resource optimization
 * - Result aggregation and validation
 * 
 * @author Puneet Chandna
 * @since 1.0
 */
public class ResearchOrchestrator {
    
    private static final Logger logger = LoggerFactory.getLogger(ResearchOrchestrator.class);
    
    // Experiment execution management
    private final Map<String, ExperimentStatus> experimentStatusMap;
    private final Map<String, List<ExperimentalResult>> experimentResults;
    private final Queue<ExperimentConfig> experimentQueue;
    private final Map<String, Integer> retryCountMap;
    private final Set<String> failedExperiments;
    
    // Progress tracking
    private final AtomicInteger completedExperiments;
    private final AtomicInteger failedExperimentCount;
    private final AtomicLong totalExecutionTime;
    private LocalDateTime orchestrationStartTime;
    
    // Configuration
    private int maxRetries = 3;
    private int maxConcurrentExperiments = Runtime.getRuntime().availableProcessors();
    private long experimentTimeout = 3600; // 1 hour in seconds
    private boolean enableAdaptiveScheduling = true;
    private boolean enableResourceOptimization = true;
    
    // Monitoring and coordination
    private final ScheduledExecutorService monitoringExecutor;
    private final Map<String, ExperimentMetrics> performanceMetrics;
    private final List<ExperimentSequencePlan> executionPlans;
    
    /**
     * Constructor initializes orchestration components.
     */
    public ResearchOrchestrator() {
        this.experimentStatusMap = new ConcurrentHashMap<>();
        this.experimentResults = new ConcurrentHashMap<>();
        this.experimentQueue = new ConcurrentLinkedQueue<>();
        this.retryCountMap = new ConcurrentHashMap<>();
        this.failedExperiments = ConcurrentHashMap.newKeySet();
        
        this.completedExperiments = new AtomicInteger(0);
        this.failedExperimentCount = new AtomicInteger(0);
        this.totalExecutionTime = new AtomicLong(0);
        
        this.monitoringExecutor = Executors.newScheduledThreadPool(2);
        this.performanceMetrics = new ConcurrentHashMap<>();
        this.executionPlans = new ArrayList<>();
    }
    
    /**
     * Plan experiment execution sequence based on dependencies and resource requirements.
     * Optimizes execution order for maximum efficiency and resource utilization.
     * 
     * @param experiments List of experiment configurations to sequence
     * @return Optimized execution plan
     * @throws ExperimentException if planning fails
     */
    public ExperimentSequencePlan planExperimentSequence(List<ExperimentConfig> experiments) 
            throws ExperimentException {
        try {
            logger.info("Planning execution sequence for {} experiments", experiments.size());
            orchestrationStartTime = LocalDateTime.now();
            
            // Analyze experiment characteristics
            Map<String, List<ExperimentConfig>> categorizedExperiments = 
                categorizeExperiments(experiments);
            
            // Create execution plan with optimized ordering
            ExperimentSequencePlan plan = new ExperimentSequencePlan();
            
            // Phase 1: Quick baseline experiments for early feedback
            List<ExperimentConfig> baselineExperiments = 
                categorizedExperiments.getOrDefault("baseline", new ArrayList<>());
            plan.addPhase("Baseline Evaluation", baselineExperiments, 1);
            
            // Phase 2: Core algorithm experiments
            List<ExperimentConfig> coreExperiments = 
                categorizedExperiments.getOrDefault("core", new ArrayList<>());
            plan.addPhase("Core Algorithm Testing", coreExperiments, 2);
            
            // Phase 3: Real dataset experiments (resource intensive)
            List<ExperimentConfig> datasetExperiments = 
                categorizedExperiments.getOrDefault("dataset", new ArrayList<>());
            plan.addPhase("Real Dataset Analysis", datasetExperiments, 3);
            
            // Phase 4: Scalability experiments (most resource intensive)
            List<ExperimentConfig> scalabilityExperiments = 
                categorizedExperiments.getOrDefault("scalability", new ArrayList<>());
            plan.addPhase("Scalability Testing", scalabilityExperiments, 4);
            
            // Phase 5: Sensitivity analysis
            List<ExperimentConfig> sensitivityExperiments = 
                categorizedExperiments.getOrDefault("sensitivity", new ArrayList<>());
            plan.addPhase("Parameter Sensitivity", sensitivityExperiments, 5);
            
            // Optimize within each phase
            optimizePhaseExecution(plan);
            
            // Initialize status tracking
            initializeStatusTracking(experiments);
            
            executionPlans.add(plan);
            
            logger.info("Execution plan created with {} phases", plan.getPhaseCount());
            return plan;
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to plan experiment sequence", e);
        }
    }
    
    /**
     * Monitor progress of experiment execution.
     * Provides real-time tracking of experiment status, resource usage, and estimated completion.
     * 
     * @return Current progress report
     */
    public ProgressReport monitorProgress() {
        logger.debug("Generating progress report");
        
        ProgressReport report = new ProgressReport();
        
        // Calculate basic progress metrics
        int totalExperiments = experimentStatusMap.size();
        int completed = completedExperiments.get();
        int failed = failedExperimentCount.get();
        int running = countExperimentsByStatus(ExperimentStatus.RUNNING);
        int queued = countExperimentsByStatus(ExperimentStatus.QUEUED);
        
        report.setTotalExperiments(totalExperiments);
        report.setCompletedExperiments(completed);
        report.setFailedExperiments(failed);
        report.setRunningExperiments(running);
        report.setQueuedExperiments(queued);
        
        // Calculate completion percentage
        double completionPercentage = totalExperiments > 0 ? 
            (double) completed / totalExperiments * 100 : 0;
        report.setCompletionPercentage(completionPercentage);
        
        // Estimate time to completion
        if (completed > 0) {
            long avgExecutionTime = totalExecutionTime.get() / completed;
            long remainingExperiments = totalExperiments - completed - failed;
            long estimatedRemainingTime = avgExecutionTime * remainingExperiments / 
                Math.max(1, maxConcurrentExperiments);
            report.setEstimatedTimeToCompletion(Duration.ofMillis(estimatedRemainingTime));
        }
        
        // Add phase-specific progress
        for (ExperimentSequencePlan plan : executionPlans) {
            Map<String, PhaseProgress> phaseProgress = calculatePhaseProgress(plan);
            report.setPhaseProgress(phaseProgress);
        }
        
        // Add resource utilization metrics
        ResourceUtilization resourceUtil = calculateResourceUtilization();
        report.setResourceUtilization(resourceUtil);
        
        // Add performance metrics
        report.setAverageExecutionTime(
            completed > 0 ? Duration.ofMillis(totalExecutionTime.get() / completed) : Duration.ZERO
        );
        report.setSuccessRate(
            totalExperiments > 0 ? (double) completed / (completed + failed) * 100 : 0
        );
        
        // Log progress summary
        if (logger.isInfoEnabled() && completed % 10 == 0) {
            logger.info("Progress: {}/{} experiments completed ({:.1f}%), {} failed, {} running",
                       completed, totalExperiments, completionPercentage, failed, running);
        }
        
        return report;
    }
    
    /**
     * Handle experiment failures with retry logic and recovery strategies.
     * Implements intelligent retry mechanisms and failure analysis.
     * 
     * @param failedExperiment The failed experiment configuration
     * @param failure The exception that caused the failure
     * @return Recovery action taken
     * @throws ExperimentException if recovery fails
     */
    public RecoveryAction handleFailures(ExperimentConfig failedExperiment, Exception failure) 
            throws ExperimentException {
        String experimentId = failedExperiment.getExperimentId();
        logger.warn("Handling failure for experiment {}: {}", 
                   experimentId, failure.getMessage());
        
        try {
            // Update failure count
            int retryCount = retryCountMap.compute(experimentId, (k, v) -> v == null ? 1 : v + 1);
            
            // Analyze failure type
            FailureType failureType = analyzeFailure(failure);
            
            RecoveryAction action = new RecoveryAction();
            action.setExperimentId(experimentId);
            action.setFailureType(failureType);
            action.setRetryCount(retryCount);
            
            // Determine recovery strategy based on failure type and retry count
            if (retryCount <= maxRetries) {
                switch (failureType) {
                    case RESOURCE_EXHAUSTION:
                        // Wait and retry with reduced resource requirements
                        action.setActionType(RecoveryActionType.RETRY_WITH_BACKOFF);
                        action.setBackoffDelay(Duration.ofMinutes(5 * retryCount));
                        modifyResourceRequirements(failedExperiment);
                        experimentQueue.offer(failedExperiment);
                        logger.info("Scheduled retry {} for experiment {} with resource adjustment", 
                                   retryCount, experimentId);
                        break;
                        
                    case TIMEOUT:
                        // Retry with extended timeout
                        action.setActionType(RecoveryActionType.RETRY_WITH_EXTENDED_TIMEOUT);
                        failedExperiment.setTimeout(failedExperiment.getTimeout() * 2);
                        experimentQueue.offer(failedExperiment);
                        logger.info("Scheduled retry {} for experiment {} with extended timeout", 
                                   retryCount, experimentId);
                        break;
                        
                    case DATA_ERROR:
                        // Skip this experiment - data issue cannot be recovered
                        action.setActionType(RecoveryActionType.SKIP);
                        failedExperiments.add(experimentId);
                        failedExperimentCount.incrementAndGet();
                        logger.error("Skipping experiment {} due to data error", experimentId);
                        break;
                        
                    case CONFIGURATION_ERROR:
                        // Attempt to fix configuration and retry
                        action.setActionType(RecoveryActionType.RETRY_WITH_FIXED_CONFIG);
                        attemptConfigurationFix(failedExperiment);
                        experimentQueue.offer(failedExperiment);
                        logger.info("Scheduled retry {} for experiment {} with config fix", 
                                   retryCount, experimentId);
                        break;
                        
                    default:
                        // Generic retry
                        action.setActionType(RecoveryActionType.SIMPLE_RETRY);
                        experimentQueue.offer(failedExperiment);
                        logger.info("Scheduled simple retry {} for experiment {}", 
                                   retryCount, experimentId);
                }
            } else {
                // Max retries exceeded
                action.setActionType(RecoveryActionType.ABORT);
                failedExperiments.add(experimentId);
                failedExperimentCount.incrementAndGet();
                logger.error("Experiment {} failed after {} retries", experimentId, maxRetries);
                
                // Store failure information for analysis
                storeFailureInformation(failedExperiment, failure, retryCount);
            }
            
            // Update experiment status
            experimentStatusMap.put(experimentId, 
                action.getActionType() == RecoveryActionType.ABORT ? 
                ExperimentStatus.FAILED : ExperimentStatus.RETRY_SCHEDULED);
            
            return action;
            
        } catch (Exception e) {
            throw new ExperimentException(
                "Failed to handle experiment failure for " + experimentId, e);
        }
    }
    
    /**
     * Aggregate results from multiple experiments.
     * Combines and validates results from completed experiments for comprehensive analysis.
     * 
     * @param results List of experimental results to aggregate
     * @return Aggregated results organized by experiment type and algorithm
     * @throws ExperimentException if aggregation fails
     */
    public AggregatedResults aggregateResults(List<ExperimentalResult> results) 
            throws ExperimentException {
        try {
            logger.info("Aggregating results from {} experiments", results.size());
            
            AggregatedResults aggregated = new AggregatedResults();
            
            // Group results by experiment type
            Map<String, List<ExperimentalResult>> byType = results.stream()
                .collect(Collectors.groupingBy(ExperimentalResult::getExperimentType));
            
            // Group results by algorithm
            Map<String, List<ExperimentalResult>> byAlgorithm = results.stream()
                .collect(Collectors.groupingBy(ExperimentalResult::getAlgorithmName));
            
            // Aggregate metrics for each grouping
            for (Map.Entry<String, List<ExperimentalResult>> entry : byType.entrySet()) {
                String experimentType = entry.getKey();
                List<ExperimentalResult> typeResults = entry.getValue();
                
                AggregatedMetrics typeMetrics = calculateAggregatedMetrics(typeResults);
                aggregated.addTypeMetrics(experimentType, typeMetrics);
                
                // Further group by algorithm within type
                Map<String, List<ExperimentalResult>> typeByAlgorithm = typeResults.stream()
                    .collect(Collectors.groupingBy(ExperimentalResult::getAlgorithmName));
                
                for (Map.Entry<String, List<ExperimentalResult>> algoEntry : typeByAlgorithm.entrySet()) {
                    String algorithm = algoEntry.getKey();
                    List<ExperimentalResult> algoResults = algoEntry.getValue();
                    
                    AggregatedMetrics algoMetrics = calculateAggregatedMetrics(algoResults);
                    aggregated.addAlgorithmMetrics(experimentType, algorithm, algoMetrics);
                }
            }
            
            // Calculate overall statistics
            aggregated.setTotalExperiments(results.size());
            aggregated.setSuccessfulExperiments(
                results.stream().filter(r -> r.isSuccessful()).count()
            );
            aggregated.setAverageExecutionTime(
                calculateAverageExecutionTime(results)
            );
            
            // Validate aggregated results
            validateAggregatedResults(aggregated);
            
            // Store aggregated results for future reference
            storeAggregatedResults(aggregated);
            
            logger.info("Successfully aggregated results with {} experiment types and {} algorithms",
                       byType.size(), byAlgorithm.size());
            
            return aggregated;
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to aggregate results", e);
        }
    }
    
    // Progress monitoring methods
    
    /**
     * Start continuous progress monitoring.
     */
    public void startProgressMonitoring() {
        logger.info("Starting progress monitoring");
        
        // Schedule periodic progress reports
        monitoringExecutor.scheduleAtFixedRate(() -> {
            try {
                ProgressReport report = monitorProgress();
                logProgressReport(report);
                
                // Check for stalled experiments
                checkForStalledExperiments();
                
                // Optimize resource allocation if enabled
                if (enableResourceOptimization) {
                    optimizeResourceAllocation();
                }
                
            } catch (Exception e) {
                logger.error("Error in progress monitoring", e);
            }
        }, 0, 30, TimeUnit.SECONDS);
        
        // Schedule resource utilization monitoring
        monitoringExecutor.scheduleAtFixedRate(() -> {
            try {
                updateResourceMetrics();
            } catch (Exception e) {
                logger.error("Error in resource monitoring", e);
            }
        }, 0, 10, TimeUnit.SECONDS);
    }
    
    /**
     * Stop progress monitoring.
     */
    public void stopProgressMonitoring() {
        logger.info("Stopping progress monitoring");
        monitoringExecutor.shutdown();
        try {
            if (!monitoringExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                monitoringExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitoringExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // Private helper methods
    
    private Map<String, List<ExperimentConfig>> categorizeExperiments(
            List<ExperimentConfig> experiments) {
        Map<String, List<ExperimentConfig>> categorized = new HashMap<>();
        
        for (ExperimentConfig exp : experiments) {
            String category = determineExperimentCategory(exp);
            categorized.computeIfAbsent(category, k -> new ArrayList<>()).add(exp);
        }
        
        return categorized;
    }
    
    private String determineExperimentCategory(ExperimentConfig config) {
        String type = config.getExperimentType().toLowerCase();
        
        if (type.contains("baseline")) {
            return "baseline";
        } else if (type.contains("dataset")) {
            return "dataset";
        } else if (type.contains("scalability")) {
            return "scalability";
        } else if (type.contains("sensitivity")) {
            return "sensitivity";
        } else {
            return "core";
        }
    }
    
    private void optimizePhaseExecution(ExperimentSequencePlan plan) {
        // Sort experiments within each phase by estimated resource requirements
        for (ExperimentPhase phase : plan.getPhases()) {
            List<ExperimentConfig> experiments = phase.getExperiments();
            experiments.sort(Comparator.comparingInt(this::estimateResourceRequirement));
        }
    }
    
    private int estimateResourceRequirement(ExperimentConfig config) {
        // Estimate based on VM count, host count, and algorithm complexity
        int vmCount = config.getVmCount() != null ? config.getVmCount() : 100;
        int hostCount = config.getHostCount() != null ? config.getHostCount() : 10;
        int complexity = getAlgorithmComplexity(config.getAlgorithmName());
        
        return vmCount * hostCount * complexity;
    }
    
    private int getAlgorithmComplexity(String algorithmName) {
        // Relative complexity scores
        Map<String, Integer> complexityScores = Map.of(
            "FirstFit", 1,
            "BestFit", 2,
            "Random", 1,
            "GeneticAlgorithm", 5,
            "ParticleSwarm", 4,
            "AntColony", 4,
            "HippopotamusOptimization", 3
        );
        
        return complexityScores.getOrDefault(algorithmName, 3);
    }
    
    private void initializeStatusTracking(List<ExperimentConfig> experiments) {
        for (ExperimentConfig exp : experiments) {
            experimentStatusMap.put(exp.getExperimentId(), ExperimentStatus.QUEUED);
            experimentQueue.offer(exp);
        }
    }
    
    private int countExperimentsByStatus(ExperimentStatus status) {
        return (int) experimentStatusMap.values().stream()
            .filter(s -> s == status)
            .count();
    }
    
    private Map<String, PhaseProgress> calculatePhaseProgress(ExperimentSequencePlan plan) {
        Map<String, PhaseProgress> phaseProgress = new HashMap<>();
        
        for (ExperimentPhase phase : plan.getPhases()) {
            PhaseProgress progress = new PhaseProgress();
            progress.setPhaseName(phase.getPhaseName());
            progress.setTotalExperiments(phase.getExperiments().size());
            
            int completed = 0;
            int failed = 0;
            for (ExperimentConfig exp : phase.getExperiments()) {
                ExperimentStatus status = experimentStatusMap.get(exp.getExperimentId());
                if (status == ExperimentStatus.COMPLETED) completed++;
                else if (status == ExperimentStatus.FAILED) failed++;
            }
            
            progress.setCompletedExperiments(completed);
            progress.setFailedExperiments(failed);
            progress.setCompletionPercentage(
                phase.getExperiments().size() > 0 ? 
                (double) completed / phase.getExperiments().size() * 100 : 0
            );
            
            phaseProgress.put(phase.getPhaseName(), progress);
        }
        
        return phaseProgress;
    }
    
    private ResourceUtilization calculateResourceUtilization() {
        ResourceUtilization utilization = new ResourceUtilization();
        
        // Get current resource usage
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        utilization.setMemoryUsage((double) usedMemory / runtime.maxMemory() * 100);
        utilization.setCpuUsage(getCurrentCpuUsage());
        utilization.setActiveThreads(Thread.activeCount());
        utilization.setQueueSize(experimentQueue.size());
        
        return utilization;
    }
    
    private double getCurrentCpuUsage() {
        // Simplified CPU usage calculation
        // In production, use JMX or system-specific monitoring
        return Math.random() * 100; // Placeholder
    }
    
    private FailureType analyzeFailure(Exception failure) {
        String message = failure.getMessage() != null ? 
            failure.getMessage().toLowerCase() : "";
        
        if (failure instanceof OutOfMemoryError || 
            message.contains("memory") || message.contains("heap")) {
            return FailureType.RESOURCE_EXHAUSTION;
        } else if (failure instanceof TimeoutException || 
                  message.contains("timeout")) {
            return FailureType.TIMEOUT;
        } else if (message.contains("data") || message.contains("parse") || 
                  message.contains("format")) {
            return FailureType.DATA_ERROR;
        } else if (message.contains("config") || message.contains("parameter")) {
            return FailureType.CONFIGURATION_ERROR;
        } else {
            return FailureType.UNKNOWN;
        }
    }
    
    private void modifyResourceRequirements(ExperimentConfig config) {
        // Reduce resource requirements for retry
        if (config.getVmCount() != null && config.getVmCount() > 100) {
            config.setVmCount((int) (config.getVmCount() * 0.8));
        }
        if (config.getReplications() > 5) {
            config.setReplications(Math.max(5, config.getReplications() / 2));
        }
    }
    
    private void attemptConfigurationFix(ExperimentConfig config) {
        // Validate and fix common configuration issues
        if (config.getParameters() == null) {
            config.setParameters(new HashMap<>());
        }
        
        // Ensure required parameters have default values
        Map<String, Object> params = config.getParameters();
        params.putIfAbsent("population_size", 50);
        params.putIfAbsent("max_iterations", 100);
        params.putIfAbsent("convergence_threshold", 0.001);
    }
    
    private void storeFailureInformation(ExperimentConfig config, Exception failure, int retryCount) {
        // Store detailed failure information for post-analysis
        Map<String, Object> failureInfo = new HashMap<>();
        failureInfo.put("experiment_id", config.getExperimentId());
        failureInfo.put("algorithm", config.getAlgorithmName());
        failureInfo.put("failure_time", LocalDateTime.now());
        failureInfo.put("retry_count", retryCount);
        failureInfo.put("exception_type", failure.getClass().getName());
        failureInfo.put("error_message", failure.getMessage());
        failureInfo.put("stack_trace", Arrays.toString(failure.getStackTrace()));
        
        // This would typically be persisted to a file or database
        logger.error("Storing failure information: {}", failureInfo);
    }
    
    private AggregatedMetrics calculateAggregatedMetrics(List<ExperimentalResult> results) {
        AggregatedMetrics metrics = new AggregatedMetrics();
        
        if (results.isEmpty()) {
            return metrics;
        }
        
        // Calculate mean values
        double meanUtilization = results.stream()
            .mapToDouble(r -> r.getResourceUtilization())
            .average().orElse(0);
        double meanPower = results.stream()
            .mapToDouble(r -> r.getPowerConsumption())
            .average().orElse(0);
        double meanSlaViolations = results.stream()
            .mapToDouble(r -> r.getSlaViolations())
            .average().orElse(0);
        
        metrics.setMeanResourceUtilization(meanUtilization);
        metrics.setMeanPowerConsumption(meanPower);
        metrics.setMeanSlaViolations(meanSlaViolations);
        
        // Calculate standard deviations
        double stdUtilization = calculateStandardDeviation(
            results.stream().mapToDouble(r -> r.getResourceUtilization()).toArray()
        );
        double stdPower = calculateStandardDeviation(
            results.stream().mapToDouble(r -> r.getPowerConsumption()).toArray()
        );
        double stdSla = calculateStandardDeviation(
            results.stream().mapToDouble(r -> r.getSlaViolations()).toArray()
        );
        
        metrics.setStdResourceUtilization(stdUtilization);
        metrics.setStdPowerConsumption(stdPower);
        metrics.setStdSlaViolations(stdSla);
        
        // Calculate min/max values
        DoubleSummaryStatistics utilizationStats = results.stream()
            .mapToDouble(r -> r.getResourceUtilization())
            .summaryStatistics();
        metrics.setMinResourceUtilization(utilizationStats.getMin());
        metrics.setMaxResourceUtilization(utilizationStats.getMax());
        
        return metrics;
    }
    
    private double calculateStandardDeviation(double[] values) {
        if (values.length == 0) return 0;
        
        double mean = Arrays.stream(values).average().orElse(0);
        double variance = Arrays.stream(values)
            .map(v -> Math.pow(v - mean, 2))
            .average().orElse(0);
        
        return Math.sqrt(variance);
    }
    
    private Duration calculateAverageExecutionTime(List<ExperimentalResult> results) {
        long avgMillis = results.stream()
            .mapToLong(r -> r.getExecutionTime().toMillis())
            .average()
            .orElse(0);
        
        return Duration.ofMillis(avgMillis);
    }
    
    private void validateAggregatedResults(AggregatedResults results) throws ExperimentException {
        // Validate aggregated results for consistency
        if (results.getTotalExperiments() == 0) {
            throw new ExperimentException("No experiments in aggregated results");
        }
        
        if (results.getSuccessfulExperiments() > results.getTotalExperiments()) {
            throw new ExperimentException("Inconsistent experiment counts in aggregated results");
        }
        
        // Validate metrics ranges
        for (AggregatedMetrics metrics : results.getAllMetrics()) {
            if (metrics.getMeanResourceUtilization() < 0 || 
                metrics.getMeanResourceUtilization() > 100) {
                throw new ExperimentException("Invalid resource utilization metrics");
            }
            
            if (metrics.getMeanPowerConsumption() < 0) {
                throw new ExperimentException("Invalid power consumption metrics");
            }
        }
    }
    
    private void storeAggregatedResults(AggregatedResults results) {
        // Store aggregated results for persistence
        logger.info("Storing aggregated results with {} experiments", 
                   results.getTotalExperiments());
        // Implementation would persist to file or database
    }
    
    private void logProgressReport(ProgressReport report) {
        if (logger.isInfoEnabled()) {
            logger.info("Research Progress: {:.1f}% complete ({}/{} experiments), " +
                       "{} running, {} queued, {} failed",
                       report.getCompletionPercentage(),
                       report.getCompletedExperiments(),
                       report.getTotalExperiments(),
                       report.getRunningExperiments(),
                       report.getQueuedExperiments(),
                       report.getFailedExperiments());
        }
    }
    
    private void checkForStalledExperiments() {
        LocalDateTime now = LocalDateTime.now();
        
        for (Map.Entry<String, ExperimentStatus> entry : experimentStatusMap.entrySet()) {
            if (entry.getValue() == ExperimentStatus.RUNNING) {
                ExperimentMetrics metrics = performanceMetrics.get(entry.getKey());
                if (metrics != null && metrics.getStartTime() != null) {
                    Duration runtime = Duration.between(metrics.getStartTime(), now);
                    if (runtime.getSeconds() > experimentTimeout) {
                        logger.warn("Experiment {} appears to be stalled (runtime: {})", 
                                   entry.getKey(), runtime);
                        // Could implement automatic termination/retry here
                    }
                }
            }
        }
    }
    
    private void optimizeResourceAllocation() {
        ResourceUtilization currentUtil = calculateResourceUtilization();
        
        // Adjust concurrent experiments based on resource usage
        if (currentUtil.getMemoryUsage() > 80) {
            maxConcurrentExperiments = Math.max(1, maxConcurrentExperiments - 1);
            logger.info("Reduced concurrent experiments to {} due to high memory usage", 
                       maxConcurrentExperiments);
        } else if (currentUtil.getMemoryUsage() < 50 && 
                  maxConcurrentExperiments < Runtime.getRuntime().availableProcessors()) {
            maxConcurrentExperiments++;
            logger.info("Increased concurrent experiments to {}", maxConcurrentExperiments);
        }
    }
    
    private void updateResourceMetrics() {
        // Update resource usage metrics for all running experiments
        for (Map.Entry<String, ExperimentStatus> entry : experimentStatusMap.entrySet()) {
            if (entry.getValue() == ExperimentStatus.RUNNING) {
                ExperimentMetrics metrics = performanceMetrics.computeIfAbsent(
                    entry.getKey(), k -> new ExperimentMetrics()
                );
                metrics.updateResourceUsage();
            }
        }
    }
    
    // Inner classes for orchestration support
    
    public static class ExperimentSequencePlan {
        private final List<ExperimentPhase> phases = new ArrayList<>();
        
        public void addPhase(String name, List<ExperimentConfig> experiments, int priority) {
            phases.add(new ExperimentPhase(name, experiments, priority));
        }
        
        public List<ExperimentPhase> getPhases() {
            return phases;
        }
        
        public int getPhaseCount() {
            return phases.size();
        }
    }
    
    public static class ExperimentPhase {
        private final String phaseName;
        private final List<ExperimentConfig> experiments;
        private final int priority;
        
        public ExperimentPhase(String phaseName, List<ExperimentConfig> experiments, int priority) {
            this.phaseName = phaseName;
            this.experiments = new ArrayList<>(experiments);
            this.priority = priority;
        }
        
        public String getPhaseName() { return phaseName; }
        public List<ExperimentConfig> getExperiments() { return experiments; }
        public int getPriority() { return priority; }
    }
    
    public static class ProgressReport {
        private int totalExperiments;
        private int completedExperiments;
        private int failedExperiments;
        private int runningExperiments;
        private int queuedExperiments;
        private double completionPercentage;
        private Duration estimatedTimeToCompletion;
        private Map<String, PhaseProgress> phaseProgress;
        private ResourceUtilization resourceUtilization;
        private Duration averageExecutionTime;
        private double successRate;
        
        // Getters and setters
        public int getTotalExperiments() { return totalExperiments; }
        public void setTotalExperiments(int total) { this.totalExperiments = total; }
        
        public int getCompletedExperiments() { return completedExperiments; }
        public void setCompletedExperiments(int completed) { this.completedExperiments = completed; }
        
        public int getFailedExperiments() { return failedExperiments; }
        public void setFailedExperiments(int failed) { this.failedExperiments = failed; }
        
        public int getRunningExperiments() { return runningExperiments; }
        public void setRunningExperiments(int running) { this.runningExperiments = running; }
        
        public int getQueuedExperiments() { return queuedExperiments; }
        public void setQueuedExperiments(int queued) { this.queuedExperiments = queued; }
        
        public double getCompletionPercentage() { return completionPercentage; }
        public void setCompletionPercentage(double percentage) { this.completionPercentage = percentage; }
        
        public Duration getEstimatedTimeToCompletion() { return estimatedTimeToCompletion; }
        public void setEstimatedTimeToCompletion(Duration time) { this.estimatedTimeToCompletion = time; }
        
        public Map<String, PhaseProgress> getPhaseProgress() { return phaseProgress; }
        public void setPhaseProgress(Map<String, PhaseProgress> progress) { this.phaseProgress = progress; }
        
        public ResourceUtilization getResourceUtilization() { return resourceUtilization; }
        public void setResourceUtilization(ResourceUtilization util) { this.resourceUtilization = util; }
        
        public Duration getAverageExecutionTime() { return averageExecutionTime; }
        public void setAverageExecutionTime(Duration time) { this.averageExecutionTime = time; }
        
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double rate) { this.successRate = rate; }
    }
    
    public static class PhaseProgress {
        private String phaseName;
        private int totalExperiments;
        private int completedExperiments;
        private int failedExperiments;
        private double completionPercentage;
        
        // Getters and setters
        public String getPhaseName() { return phaseName; }
        public void setPhaseName(String name) { this.phaseName = name; }
        
        public int getTotalExperiments() { return totalExperiments; }
        public void setTotalExperiments(int total) { this.totalExperiments = total; }
        
        public int getCompletedExperiments() { return completedExperiments; }
        public void setCompletedExperiments(int completed) { this.completedExperiments = completed; }
        
        public int getFailedExperiments() { return failedExperiments; }
        public void setFailedExperiments(int failed) { this.failedExperiments = failed; }
        
        public double getCompletionPercentage() { return completionPercentage; }
        public void setCompletionPercentage(double percentage) { this.completionPercentage = percentage; }
    }
    
    public static class ResourceUtilization {
        private double memoryUsage;
        private double cpuUsage;
        private int activeThreads;
        private int queueSize;
        
        // Getters and setters
        public double getMemoryUsage() { return memoryUsage; }
        public void setMemoryUsage(double usage) { this.memoryUsage = usage; }
        
        public double getCpuUsage() { return cpuUsage; }
        public void setCpuUsage(double usage) { this.cpuUsage = usage; }
        
        public int getActiveThreads() { return activeThreads; }
        public void setActiveThreads(int threads) { this.activeThreads = threads; }
        
        public int getQueueSize() { return queueSize; }
        public void setQueueSize(int size) { this.queueSize = size; }
    }
    
    public static class RecoveryAction {
        private String experimentId;
        private RecoveryActionType actionType;
        private FailureType failureType;
        private int retryCount;
        private Duration backoffDelay;
        
        // Getters and setters
        public String getExperimentId() { return experimentId; }
        public void setExperimentId(String id) { this.experimentId = id; }
        
        public RecoveryActionType getActionType() { return actionType; }
        public void setActionType(RecoveryActionType type) { this.actionType = type; }
        
        public FailureType getFailureType() { return failureType; }
        public void setFailureType(FailureType type) { this.failureType = type; }
        
        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int count) { this.retryCount = count; }
        
        public Duration getBackoffDelay() { return backoffDelay; }
        public void setBackoffDelay(Duration delay) { this.backoffDelay = delay; }
    }
    
    public static class AggregatedResults {
        private long totalExperiments;
        private long successfulExperiments;
        private Duration averageExecutionTime;
        private Map<String, AggregatedMetrics> typeMetrics = new HashMap<>();
        private Map<String, Map<String, AggregatedMetrics>> algorithmMetrics = new HashMap<>();
        
        public void addTypeMetrics(String type, AggregatedMetrics metrics) {
            typeMetrics.put(type, metrics);
        }
        
        public void addAlgorithmMetrics(String type, String algorithm, AggregatedMetrics metrics) {
            algorithmMetrics.computeIfAbsent(type, k -> new HashMap<>())
                .put(algorithm, metrics);
        }
        
        public List<AggregatedMetrics> getAllMetrics() {
            List<AggregatedMetrics> allMetrics = new ArrayList<>(typeMetrics.values());
            algorithmMetrics.values().forEach(map -> allMetrics.addAll(map.values()));
            return allMetrics;
        }
        
        // Getters and setters
        public long getTotalExperiments() { return totalExperiments; }
        public void setTotalExperiments(long total) { this.totalExperiments = total; }
        
        public long getSuccessfulExperiments() { return successfulExperiments; }
        public void setSuccessfulExperiments(long successful) { this.successfulExperiments = successful; }
        
        public Duration getAverageExecutionTime() { return averageExecutionTime; }
        public void setAverageExecutionTime(Duration time) { this.averageExecutionTime = time; }
    }
    
    public static class AggregatedMetrics {
        private double meanResourceUtilization;
        private double stdResourceUtilization;
        private double minResourceUtilization;
        private double maxResourceUtilization;
        private double meanPowerConsumption;
        private double stdPowerConsumption;
        private double meanSlaViolations;
        private double stdSlaViolations;
        
        // Getters and setters
        public double getMeanResourceUtilization() { return meanResourceUtilization; }
        public void setMeanResourceUtilization(double mean) { this.meanResourceUtilization = mean; }
        
        public double getStdResourceUtilization() { return stdResourceUtilization; }
        public void setStdResourceUtilization(double std) { this.stdResourceUtilization = std; }
        
        public double getMinResourceUtilization() { return minResourceUtilization; }
        public void setMinResourceUtilization(double min) { this.minResourceUtilization = min; }
        
        public double getMaxResourceUtilization() { return maxResourceUtilization; }
        public void setMaxResourceUtilization(double max) { this.maxResourceUtilization = max; }
        
        public double getMeanPowerConsumption() { return meanPowerConsumption; }
        public void setMeanPowerConsumption(double mean) { this.meanPowerConsumption = mean; }
        
        public double getStdPowerConsumption() { return stdPowerConsumption; }
        public void setStdPowerConsumption(double std) { this.stdPowerConsumption = std; }
        
        public double getMeanSlaViolations() { return meanSlaViolations; }
        public void setMeanSlaViolations(double mean) { this.meanSlaViolations = mean; }
        
        public double getStdSlaViolations() { return stdSlaViolations; }
        public void setStdSlaViolations(double std) { this.stdSlaViolations = std; }
    }
    
    public static class ExperimentMetrics {
        private LocalDateTime startTime;
        private double peakMemoryUsage;
        private double avgCpuUsage;
        
        public void updateResourceUsage() {
            if (startTime == null) {
                startTime = LocalDateTime.now();
            }
            // Update resource metrics
            Runtime runtime = Runtime.getRuntime();
            double currentMemory = (runtime.totalMemory() - runtime.freeMemory()) / 
                                  (double) runtime.maxMemory() * 100;
            peakMemoryUsage = Math.max(peakMemoryUsage, currentMemory);
        }
        
        public LocalDateTime getStartTime() { return startTime; }
    }
    
    // Enums
    
    public enum ExperimentStatus {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED,
        RETRY_SCHEDULED,
        CANCELLED
    }
    
    public enum FailureType {
        RESOURCE_EXHAUSTION,
        TIMEOUT,
        DATA_ERROR,
        CONFIGURATION_ERROR,
        ALGORITHM_ERROR,
        UNKNOWN
    }
    
    public enum RecoveryActionType {
        SIMPLE_RETRY,
        RETRY_WITH_BACKOFF,
        RETRY_WITH_EXTENDED_TIMEOUT,
        RETRY_WITH_FIXED_CONFIG,
        SKIP,
        ABORT
    }
}