package org.cloudbus.cloudsim.algorithm;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;

import java.util.*;
import java.time.LocalDateTime;
import java.time.Duration;

/**
 * Comprehensive optimization results container for Hippopotamus Optimization algorithm
 * designed for extensive research analysis and publication-ready reporting.
 *
 * This class stores all optimization outcomes including the best solution,
 * convergence history, diversity metrics, execution statistics, and detailed
 * performance analysis data required for research publications.
 *
 * @author Puneet Chandna
 * @since CloudSim Plus 7.0.1
 */
public class OptimizationResult {

    // Core optimization results
    private final Map<Vm, Host> bestSolution;
    private final double bestFitness;
    private final MultiObjectiveMetrics bestMetrics;
    private final int solutionIteration;
    private final boolean converged;
    private final String terminationReason;

    // Convergence tracking
    private final List<Double> convergenceHistory;
    private final List<Double> averageFitnessHistory;
    private final List<Double> worstFitnessHistory;
    private final List<Integer> improvementIterations;
    private final double convergenceRate;
    private final int totalIterations;

    // Diversity analysis
    private final List<Double> diversityHistory;
    private final double initialDiversity;
    private final double finalDiversity;
    private final double averageDiversity;
    private final List<Double> populationEntropy;
    private final int prematureConvergenceIteration;

    // Execution statistics
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Duration executionTime;
    private final long totalFunctionEvaluations;
    private final double averageIterationTime;
    private final Map<String, Long> operationTimings;
    private final Map<String, Integer> operationCounts;

    // Resource usage tracking
    private final long peakMemoryUsage;
    private final double averageCpuUsage;
    private final long totalMemoryAllocations;
    private final int garbageCollectionCount;

    // Statistical analysis data
    private final Map<String, Double> statisticalMeasures;
    private final List<Double> fitnessDistribution;
    private final double fitnessVariance;
    private final double fitnessStandardDeviation;
    private final double fitnessSkewness;
    private final double fitnessKurtosis;

    // Algorithm-specific metrics
    private final Map<String, Double> algorithmSpecificMetrics;
    private final List<Map<Vm, Host>> eliteSolutions;
    private final List<Double> eliteFitnesses;
    private final int localSearchApplications;
    private final int successfulLocalSearches;

    // Problem-specific analysis
    private final int totalVms;
    private final int totalHosts;
    private final double problemComplexity;
    private final Map<String, Double> resourceUtilizationMetrics;
    private final Map<String, Double> loadBalancingMetrics;
    private final Map<String, Double> powerConsumptionMetrics;
    private final Map<String, Double> slaComplianceMetrics;

    // Research metadata
    private final HippopotamusParameters usedParameters;
    private final String experimentId;
    private final Map<String, Object> additionalData;
    private final List<String> warnings;
    private final List<String> errors;

    /**
     * Constructor for comprehensive optimization results
     *
     * @param builder Builder instance with all result data
     */
    private OptimizationResult(Builder builder) {
        // Core results
        this.bestSolution = new HashMap<>(builder.bestSolution);
        this.bestFitness = builder.bestFitness;
        this.bestMetrics = builder.bestMetrics;
        this.solutionIteration = builder.solutionIteration;
        this.converged = builder.converged;
        this.terminationReason = builder.terminationReason;

        // Convergence data
        this.convergenceHistory = new ArrayList<>(builder.convergenceHistory);
        this.averageFitnessHistory = new ArrayList<>(builder.averageFitnessHistory);
        this.worstFitnessHistory = new ArrayList<>(builder.worstFitnessHistory);
        this.improvementIterations = new ArrayList<>(builder.improvementIterations);
        this.convergenceRate = builder.convergenceRate;
        this.totalIterations = builder.totalIterations;

        // Diversity data
        this.diversityHistory = new ArrayList<>(builder.diversityHistory);
        this.initialDiversity = builder.initialDiversity;
        this.finalDiversity = builder.finalDiversity;
        this.averageDiversity = builder.averageDiversity;
        this.populationEntropy = new ArrayList<>(builder.populationEntropy);
        this.prematureConvergenceIteration = builder.prematureConvergenceIteration;

        // Execution data
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.executionTime = builder.executionTime;
        this.totalFunctionEvaluations = builder.totalFunctionEvaluations;
        this.averageIterationTime = builder.averageIterationTime;
        this.operationTimings = new HashMap<>(builder.operationTimings);
        this.operationCounts = new HashMap<>(builder.operationCounts);

        // Resource usage
        this.peakMemoryUsage = builder.peakMemoryUsage;
        this.averageCpuUsage = builder.averageCpuUsage;
        this.totalMemoryAllocations = builder.totalMemoryAllocations;
        this.garbageCollectionCount = builder.garbageCollectionCount;

        // Statistical data
        this.statisticalMeasures = new HashMap<>(builder.statisticalMeasures);
        this.fitnessDistribution = new ArrayList<>(builder.fitnessDistribution);
        this.fitnessVariance = builder.fitnessVariance;
        this.fitnessStandardDeviation = builder.fitnessStandardDeviation;
        this.fitnessSkewness = builder.fitnessSkewness;
        this.fitnessKurtosis = builder.fitnessKurtosis;

        // Algorithm-specific data
        this.algorithmSpecificMetrics = new HashMap<>(builder.algorithmSpecificMetrics);
        this.eliteSolutions = new ArrayList<>(builder.eliteSolutions);
        this.eliteFitnesses = new ArrayList<>(builder.eliteFitnesses);
        this.localSearchApplications = builder.localSearchApplications;
        this.successfulLocalSearches = builder.successfulLocalSearches;

        // Problem-specific data
        this.totalVms = builder.totalVms;
        this.totalHosts = builder.totalHosts;
        this.problemComplexity = builder.problemComplexity;
        this.resourceUtilizationMetrics = new HashMap<>(builder.resourceUtilizationMetrics);
        this.loadBalancingMetrics = new HashMap<>(builder.loadBalancingMetrics);
        this.powerConsumptionMetrics = new HashMap<>(builder.powerConsumptionMetrics);
        this.slaComplianceMetrics = new HashMap<>(builder.slaComplianceMetrics);

        // Research metadata
        this.usedParameters = builder.usedParameters;
        this.experimentId = builder.experimentId;
        this.additionalData = new HashMap<>(builder.additionalData);
        this.warnings = new ArrayList<>(builder.warnings);
        this.errors = new ArrayList<>(builder.errors);
    }

    // Getters for core results
    public Map<Vm, Host> getBestSolution() { return Collections.unmodifiableMap(bestSolution); }
    public double getBestFitness() { return bestFitness; }
    public MultiObjectiveMetrics getBestMetrics() { return bestMetrics; }
    public int getSolutionIteration() { return solutionIteration; }
    public boolean isConverged() { return converged; }
    public String getTerminationReason() { return terminationReason; }

    // Getters for convergence data
    public List<Double> getConvergenceHistory() { return Collections.unmodifiableList(convergenceHistory); }
    public List<Double> getAverageFitnessHistory() { return Collections.unmodifiableList(averageFitnessHistory); }
    public List<Double> getWorstFitnessHistory() { return Collections.unmodifiableList(worstFitnessHistory); }
    public List<Integer> getImprovementIterations() { return Collections.unmodifiableList(improvementIterations); }
    public double getConvergenceRate() { return convergenceRate; }
    public int getTotalIterations() { return totalIterations; }

    // Getters for diversity data
    public List<Double> getDiversityHistory() { return Collections.unmodifiableList(diversityHistory); }
    public double getInitialDiversity() { return initialDiversity; }
    public double getFinalDiversity() { return finalDiversity; }
    public double getAverageDiversity() { return averageDiversity; }
    public List<Double> getPopulationEntropy() { return Collections.unmodifiableList(populationEntropy); }
    public int getPrematureConvergenceIteration() { return prematureConvergenceIteration; }

    // Getters for execution data
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public Duration getExecutionTime() { return executionTime; }
    public long getTotalFunctionEvaluations() { return totalFunctionEvaluations; }
    public double getAverageIterationTime() { return averageIterationTime; }
    public Map<String, Long> getOperationTimings() { return Collections.unmodifiableMap(operationTimings); }
    public Map<String, Integer> getOperationCounts() { return Collections.unmodifiableMap(operationCounts); }

    // Getters for resource usage
    public long getPeakMemoryUsage() { return peakMemoryUsage; }
    public double getAverageCpuUsage() { return averageCpuUsage; }
    public long getTotalMemoryAllocations() { return totalMemoryAllocations; }
    public int getGarbageCollectionCount() { return garbageCollectionCount; }

    // Getters for statistical data
    public Map<String, Double> getStatisticalMeasures() { return Collections.unmodifiableMap(statisticalMeasures); }
    public List<Double> getFitnessDistribution() { return Collections.unmodifiableList(fitnessDistribution); }
    public double getFitnessVariance() { return fitnessVariance; }
    public double getFitnessStandardDeviation() { return fitnessStandardDeviation; }
    public double getFitnessSkewness() { return fitnessSkewness; }
    public double getFitnessKurtosis() { return fitnessKurtosis; }

    // Getters for algorithm-specific data
    public Map<String, Double> getAlgorithmSpecificMetrics() { return Collections.unmodifiableMap(algorithmSpecificMetrics); }
    public List<Map<Vm, Host>> getEliteSolutions() { return Collections.unmodifiableList(eliteSolutions); }
    public List<Double> getEliteFitnesses() { return Collections.unmodifiableList(eliteFitnesses); }
    public int getLocalSearchApplications() { return localSearchApplications; }
    public int getSuccessfulLocalSearches() { return successfulLocalSearches; }

    // Getters for problem-specific data
    public int getTotalVms() { return totalVms; }
    public int getTotalHosts() { return totalHosts; }
    public double getProblemComplexity() { return problemComplexity; }
    public Map<String, Double> getResourceUtilizationMetrics() { return Collections.unmodifiableMap(resourceUtilizationMetrics); }
    public Map<String, Double> getLoadBalancingMetrics() { return Collections.unmodifiableMap(loadBalancingMetrics); }
    public Map<String, Double> getPowerConsumptionMetrics() { return Collections.unmodifiableMap(powerConsumptionMetrics); }
    public Map<String, Double> getSlaComplianceMetrics() { return Collections.unmodifiableMap(slaComplianceMetrics); }

    // Getters for research metadata
    public HippopotamusParameters getUsedParameters() { return usedParameters; }
    public String getExperimentId() { return experimentId; }
    public Map<String, Object> getAdditionalData() { return Collections.unmodifiableMap(additionalData); }
    public List<String> getWarnings() { return Collections.unmodifiableList(warnings); }
    public List<String> getErrors() { return Collections.unmodifiableList(errors); }

    /**
     * Get comprehensive summary of optimization results for reporting
     *
     * @return Formatted summary string
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("=== Hippopotamus Optimization Results Summary ===\n");
        summary.append(String.format("Experiment ID: %s\n", experimentId));
        summary.append(String.format("Best Fitness: %.6f\n", bestFitness));
        summary.append(String.format("Solution Found at Iteration: %d/%d\n", solutionIteration, totalIterations));
        summary.append(String.format("Converged: %s (%s)\n", converged, terminationReason));
        summary.append(String.format("Execution Time: %s\n", formatDuration(executionTime)));
        summary.append(String.format("Total Function Evaluations: %d\n", totalFunctionEvaluations));
        summary.append(String.format("VMs Placed: %d on %d Hosts\n", totalVms, totalHosts));

        if (bestMetrics != null) {
            summary.append("\n--- Multi-Objective Metrics ---\n");
            summary.append(bestMetrics.toString());
        }

        summary.append("\n--- Convergence Analysis ---\n");
        summary.append(String.format("Convergence Rate: %.6f\n", convergenceRate));
        summary.append(String.format("Initial Diversity: %.6f\n", initialDiversity));
        summary.append(String.format("Final Diversity: %.6f\n", finalDiversity));

        if (prematureConvergenceIteration >= 0) {
            summary.append(String.format("Premature Convergence at Iteration: %d\n", prematureConvergenceIteration));
        }

        summary.append("\n--- Performance Statistics ---\n");
        summary.append(String.format("Fitness Std Dev: %.6f\n", fitnessStandardDeviation));
        summary.append(String.format("Peak Memory Usage: %s\n", formatBytes(peakMemoryUsage)));
        summary.append(String.format("Average CPU Usage: %.2f%%\n", averageCpuUsage));

        if (!warnings.isEmpty()) {
            summary.append(String.format("\n--- Warnings (%d) ---\n", warnings.size()));
            warnings.forEach(w -> summary.append("- ").append(w).append("\n"));
        }

        if (!errors.isEmpty()) {
            summary.append(String.format("\n--- Errors (%d) ---\n", errors.size()));
            errors.forEach(e -> summary.append("- ").append(e).append("\n"));
        }

        return summary.toString();
    }

    /**
     * Export results to structured data format for analysis
     *
     * @return Map containing all result data
     */
    public Map<String, Object> exportData() {
        Map<String, Object> data = new HashMap<>();

        // Core results
        data.put("bestFitness", bestFitness);
        data.put("solutionIteration", solutionIteration);
        data.put("converged", converged);
        data.put("terminationReason", terminationReason);
        data.put("totalIterations", totalIterations);

        // Convergence data
        data.put("convergenceHistory", convergenceHistory);
        data.put("convergenceRate", convergenceRate);
        data.put("improvementIterations", improvementIterations);

        // Diversity data
        data.put("diversityHistory", diversityHistory);
        data.put("initialDiversity", initialDiversity);
        data.put("finalDiversity", finalDiversity);
        data.put("averageDiversity", averageDiversity);

        // Execution data
        data.put("executionTimeMs", executionTime.toMillis());
        data.put("totalFunctionEvaluations", totalFunctionEvaluations);
        data.put("averageIterationTime", averageIterationTime);

        // Statistical data
        data.put("fitnessVariance", fitnessVariance);
        data.put("fitnessStandardDeviation", fitnessStandardDeviation);
        data.put("fitnessSkewness", fitnessSkewness);
        data.put("fitnessKurtosis", fitnessKurtosis);

        // Problem data
        data.put("totalVms", totalVms);
        data.put("totalHosts", totalHosts);
        data.put("problemComplexity", problemComplexity);

        // Metrics
        data.put("resourceUtilizationMetrics", resourceUtilizationMetrics);
        data.put("loadBalancingMetrics", loadBalancingMetrics);
        data.put("powerConsumptionMetrics", powerConsumptionMetrics);
        data.put("slaComplianceMetrics", slaComplianceMetrics);

        // Algorithm-specific
        data.put("algorithmSpecificMetrics", algorithmSpecificMetrics);
        data.put("localSearchApplications", localSearchApplications);
        data.put("successfulLocalSearches", successfulLocalSearches);

        // Resource usage
        data.put("peakMemoryUsage", peakMemoryUsage);
        data.put("averageCpuUsage", averageCpuUsage);
        data.put("garbageCollectionCount", garbageCollectionCount);

        // Metadata
        data.put("experimentId", experimentId);
        data.put("usedParameters", usedParameters.exportData());
        data.put("warnings", warnings);
        data.put("errors", errors);
        data.put("additionalData", additionalData);

        return data;
    }

    /**
     * Check if optimization was successful based on multiple criteria
     *
     * @return true if optimization was successful
     */
    public boolean isSuccessful() {
        return converged &&
               errors.isEmpty() &&
               bestFitness > 0 &&
               !bestSolution.isEmpty() &&
               totalIterations > 0;
    }

    /**
     * Get convergence quality score (0-1, higher is better)
     *
     * @return Convergence quality score
     */
    public double getConvergenceQuality() {
        if (!converged || convergenceHistory.isEmpty()) {
            return 0.0;
        }

        double improvementRatio = (double) improvementIterations.size() / totalIterations;
        double diversityMaintenance = averageDiversity / Math.max(initialDiversity, 0.001);
        double convergenceEfficiency = 1.0 - (double) solutionIteration / totalIterations;

        return (improvementRatio * 0.4 + diversityMaintenance * 0.3 + convergenceEfficiency * 0.3);
    }

    /**
     * Calculate algorithm efficiency score based on multiple factors
     *
     * @return Efficiency score (0-1, higher is better)
     */
    public double getEfficiencyScore() {
        double timeEfficiency = 1.0 / (1.0 + averageIterationTime / 1000.0); // Normalize by seconds
        double evaluationEfficiency = bestFitness / Math.max(totalFunctionEvaluations, 1);
        double memoryEfficiency = 1.0 / (1.0 + peakMemoryUsage / (1024.0 * 1024.0 * 1024.0)); // Normalize by GB

        return (timeEfficiency * 0.4 + evaluationEfficiency * 0.4 + memoryEfficiency * 0.2);
    }

    // Utility methods
    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        long millis = duration.toMillisPart();

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else if (seconds > 0) {
            return String.format("%d.%03ds", seconds, millis);
        } else {
            return String.format("%dms", millis);
        }
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1024 * 1024 * 1024) {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else if (bytes >= 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else if (bytes >= 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return bytes + " B";
        }
    }

    /**
     * Builder class for constructing OptimizationResult instances
     */
    public static class Builder {
        // Core optimization results
        private Map<Vm, Host> bestSolution = new HashMap<>();
        private double bestFitness = 0.0;
        private MultiObjectiveMetrics bestMetrics;
        private int solutionIteration = -1;
        private boolean converged = false;
        private String terminationReason = "Unknown";

        // Convergence tracking
        private List<Double> convergenceHistory = new ArrayList<>();
        private List<Double> averageFitnessHistory = new ArrayList<>();
        private List<Double> worstFitnessHistory = new ArrayList<>();
        private List<Integer> improvementIterations = new ArrayList<>();
        private double convergenceRate = 0.0;
        private int totalIterations = 0;

        // Diversity analysis
        private List<Double> diversityHistory = new ArrayList<>();
        private double initialDiversity = 0.0;
        private double finalDiversity = 0.0;
        private double averageDiversity = 0.0;
        private List<Double> populationEntropy = new ArrayList<>();
        private int prematureConvergenceIteration = -1;

        // Execution statistics
        private LocalDateTime startTime = LocalDateTime.now();
        private LocalDateTime endTime = LocalDateTime.now();
        private Duration executionTime = Duration.ZERO;
        private long totalFunctionEvaluations = 0;
        private double averageIterationTime = 0.0;
        private Map<String, Long> operationTimings = new HashMap<>();
        private Map<String, Integer> operationCounts = new HashMap<>();

        // Resource usage tracking
        private long peakMemoryUsage = 0;
        private double averageCpuUsage = 0.0;
        private long totalMemoryAllocations = 0;
        private int garbageCollectionCount = 0;

        // Statistical analysis data
        private Map<String, Double> statisticalMeasures = new HashMap<>();
        private List<Double> fitnessDistribution = new ArrayList<>();
        private double fitnessVariance = 0.0;
        private double fitnessStandardDeviation = 0.0;
        private double fitnessSkewness = 0.0;
        private double fitnessKurtosis = 0.0;

        // Algorithm-specific metrics
        private Map<String, Double> algorithmSpecificMetrics = new HashMap<>();
        private List<Map<Vm, Host>> eliteSolutions = new ArrayList<>();
        private List<Double> eliteFitnesses = new ArrayList<>();
        private int localSearchApplications = 0;
        private int successfulLocalSearches = 0;

        // Problem-specific analysis
        private int totalVms = 0;
        private int totalHosts = 0;
        private double problemComplexity = 0.0;
        private Map<String, Double> resourceUtilizationMetrics = new HashMap<>();
        private Map<String, Double> loadBalancingMetrics = new HashMap<>();
        private Map<String, Double> powerConsumptionMetrics = new HashMap<>();
        private Map<String, Double> slaComplianceMetrics = new HashMap<>();

        // Research metadata
        private HippopotamusParameters usedParameters;
        private String experimentId = UUID.randomUUID().toString();
        private Map<String, Object> additionalData = new HashMap<>();
        private List<String> warnings = new ArrayList<>();
        private List<String> errors = new ArrayList<>();

        // Builder methods for core results
        public Builder setBestSolution(Map<Vm, Host> bestSolution) { this.bestSolution = new HashMap<>(bestSolution); return this; }
        public Builder setBestFitness(double bestFitness) { this.bestFitness = bestFitness; return this; }
        public Builder setBestMetrics(MultiObjectiveMetrics bestMetrics) { this.bestMetrics = bestMetrics; return this; }
        public Builder setSolutionIteration(int solutionIteration) { this.solutionIteration = solutionIteration; return this; }
        public Builder setConverged(boolean converged) { this.converged = converged; return this; }
        public Builder setTerminationReason(String terminationReason) { this.terminationReason = terminationReason; return this; }

        // Builder methods for convergence data
        public Builder setConvergenceHistory(List<Double> convergenceHistory) { this.convergenceHistory = new ArrayList<>(convergenceHistory); return this; }
        public Builder setAverageFitnessHistory(List<Double> averageFitnessHistory) { this.averageFitnessHistory = new ArrayList<>(averageFitnessHistory); return this; }
        public Builder setWorstFitnessHistory(List<Double> worstFitnessHistory) { this.worstFitnessHistory = new ArrayList<>(worstFitnessHistory); return this; }
        public Builder setImprovementIterations(List<Integer> improvementIterations) { this.improvementIterations = new ArrayList<>(improvementIterations); return this; }
        public Builder setConvergenceRate(double convergenceRate) { this.convergenceRate = convergenceRate; return this; }
        public Builder setTotalIterations(int totalIterations) { this.totalIterations = totalIterations; return this; }

        // Builder methods for diversity data
        public Builder setDiversityHistory(List<Double> diversityHistory) { this.diversityHistory = new ArrayList<>(diversityHistory); return this; }
        public Builder setInitialDiversity(double initialDiversity) { this.initialDiversity = initialDiversity; return this; }
        public Builder setFinalDiversity(double finalDiversity) { this.finalDiversity = finalDiversity; return this; }
        public Builder setAverageDiversity(double averageDiversity) { this.averageDiversity = averageDiversity; return this; }
        public Builder setPopulationEntropy(List<Double> populationEntropy) { this.populationEntropy = new ArrayList<>(populationEntropy); return this; }
        public Builder setPrematureConvergenceIteration(int prematureConvergenceIteration) { this.prematureConvergenceIteration = prematureConvergenceIteration; return this; }

        // Builder methods for execution data
        public Builder setStartTime(LocalDateTime startTime) { this.startTime = startTime; return this; }
        public Builder setEndTime(LocalDateTime endTime) { this.endTime = endTime; return this; }
        public Builder setExecutionTime(Duration executionTime) { this.executionTime = executionTime; return this; }
        public Builder setTotalFunctionEvaluations(long totalFunctionEvaluations) { this.totalFunctionEvaluations = totalFunctionEvaluations; return this; }
        public Builder setAverageIterationTime(double averageIterationTime) { this.averageIterationTime = averageIterationTime; return this; }
        public Builder setOperationTimings(Map<String, Long> operationTimings) { this.operationTimings = new HashMap<>(operationTimings); return this; }
        public Builder setOperationCounts(Map<String, Integer> operationCounts) { this.operationCounts = new HashMap<>(operationCounts); return this; }

        // Builder methods for resource usage
        public Builder setPeakMemoryUsage(long peakMemoryUsage) { this.peakMemoryUsage = peakMemoryUsage; return this; }
        public Builder setAverageCpuUsage(double averageCpuUsage) { this.averageCpuUsage = averageCpuUsage; return this; }
        public Builder setTotalMemoryAllocations(long totalMemoryAllocations) { this.totalMemoryAllocations = totalMemoryAllocations; return this; }
        public Builder setGarbageCollectionCount(int garbageCollectionCount) { this.garbageCollectionCount = garbageCollectionCount; return this; }

        // Builder methods for statistical data
        public Builder setStatisticalMeasures(Map<String, Double> statisticalMeasures) { this.statisticalMeasures = new HashMap<>(statisticalMeasures); return this; }
        public Builder setFitnessDistribution(List<Double> fitnessDistribution) { this.fitnessDistribution = new ArrayList<>(fitnessDistribution); return this; }
        public Builder setFitnessVariance(double fitnessVariance) { this.fitnessVariance = fitnessVariance; return this; }
        public Builder setFitnessStandardDeviation(double fitnessStandardDeviation) { this.fitnessStandardDeviation = fitnessStandardDeviation; return this; }
        public Builder setFitnessSkewness(double fitnessSkewness) { this.fitnessSkewness = fitnessSkewness; return this; }
        public Builder setFitnessKurtosis(double fitnessKurtosis) { this.fitnessKurtosis = fitnessKurtosis; return this; }
        
        // Builder methods for algorithm-specific metrics
        public Builder setAlgorithmSpecificMetrics(Map<String, Double> algorithmSpecificMetrics) { this.algorithmSpecificMetrics = new HashMap<>(algorithmSpecificMetrics); return this; }
        public Builder setEliteSolutions(List<Map<Vm, Host>> eliteSolutions) { this.eliteSolutions = new ArrayList<>(eliteSolutions); return this; }
        public Builder setEliteFitnesses(List<Double> eliteFitnesses) { this.eliteFitnesses = new ArrayList<>(eliteFitnesses); return this; }
        public Builder setLocalSearchApplications(int localSearchApplications) { this.localSearchApplications = localSearchApplications; return this; }
        public Builder setSuccessfulLocalSearches(int successfulLocalSearches) { this.successfulLocalSearches = successfulLocalSearches; return this; }

        // Builder methods for problem-specific analysis
        public Builder setTotalVms(int totalVms) { this.totalVms = totalVms; return this; }
        public Builder setTotalHosts(int totalHosts) { this.totalHosts = totalHosts; return this; }
        public Builder setProblemComplexity(double problemComplexity) { this.problemComplexity = problemComplexity; return this; }
        public Builder setResourceUtilizationMetrics(Map<String, Double> resourceUtilizationMetrics) { this.resourceUtilizationMetrics = new HashMap<>(resourceUtilizationMetrics); return this; }
        public Builder setLoadBalancingMetrics(Map<String, Double> loadBalancingMetrics) { this.loadBalancingMetrics = new HashMap<>(loadBalancingMetrics); return this; }
        public Builder setPowerConsumptionMetrics(Map<String, Double> powerConsumptionMetrics) { this.powerConsumptionMetrics = new HashMap<>(powerConsumptionMetrics); return this; }
        public Builder setSlaComplianceMetrics(Map<String, Double> slaComplianceMetrics) { this.slaComplianceMetrics = new HashMap<>(slaComplianceMetrics); return this; }

        // Builder methods for research metadata
        public Builder setUsedParameters(HippopotamusParameters usedParameters) { this.usedParameters = usedParameters; return this; }
        public Builder setExperimentId(String experimentId) { this.experimentId = experimentId; return this; }
        public Builder setAdditionalData(Map<String, Object> additionalData) { this.additionalData = new HashMap<>(additionalData); return this; }
        public Builder addWarning(String warning) { this.warnings.add(warning); return this; }
        public Builder addError(String error) { this.errors.add(error); return this; }

        /**
         * Build the OptimizationResult instance.
         *
         * @return A new instance of OptimizationResult
         */
        public OptimizationResult build() {
            // Perform final calculations before building
            if (startTime != null && endTime != null) {
                this.executionTime = Duration.between(startTime, endTime);
            }
            return new OptimizationResult(this);
        }
    }

    /**
     * Inner class to represent multi-objective metrics for a given solution.
     * This enhances reporting and detailed analysis capabilities.
     */
    public static class MultiObjectiveMetrics {
        private final double resourceUtilization;
        private final double powerConsumption;
        private final double slaViolations;
        private final double loadStandardDeviation;
        private final double cost;
        private final double responseTime;
        private final double throughput;

        public MultiObjectiveMetrics(double resourceUtilization, double powerConsumption, double slaViolations,
                                     double loadStandardDeviation, double cost, double responseTime, double throughput) {
            this.resourceUtilization = resourceUtilization;
            this.powerConsumption = powerConsumption;
            this.slaViolations = slaViolations;
            this.loadStandardDeviation = loadStandardDeviation;
            this.cost = cost;
            this.responseTime = responseTime;
            this.throughput = throughput;
        }

        // Getters for all metrics
        public double getResourceUtilization() { return resourceUtilization; }
        public double getPowerConsumption() { return powerConsumption; }
        public double getSlaViolations() { return slaViolations; }
        public double getLoadStandardDeviation() { return loadStandardDeviation; }
        public double getCost() { return cost; }
        public double getResponseTime() { return responseTime; }
        public double getThroughput() { return throughput; }

        @Override
        public String toString() {
            return String.format(
                "  Resource Utilization : %.4f\n" +
                "  Power Consumption    : %.2f W\n" +
                "  SLA Violations       : %.4f%%\n" +
                "  Load Std Deviation   : %.4f\n" +
                "  Cost                 : %.2f\n" +
                "  Response Time        : %.4f s\n" +
                "  Throughput           : %.2f MIPS",
                resourceUtilization, powerConsumption, slaViolations * 100,
                loadStandardDeviation, cost, responseTime, throughput
            );
        }
    }
}