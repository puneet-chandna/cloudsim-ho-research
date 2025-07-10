package org.cloudbus.cloudsim.dataset;

import org.cloudbus.cloudsim.util.ExperimentException;
import org.cloudbus.cloudsim.util.ValidationUtils;
import org.cloudbus.cloudsim.util.LoggingManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive workload characteristics representation for research analysis.
 * 
 * This class encapsulates all workload characteristics needed for:
 * - Statistical workload modeling and analysis
 * - Publication-ready workload description
 * - Reproducible experiment configuration
 * - Multi-objective optimization benchmarking
 * 
 * Research Metrics: Workload complexity, resource demand patterns, temporal characteristics
 * Publication Output: Workload description tables, statistical workload summaries
 * Dataset Compatibility: Google traces, Azure traces, synthetic workloads
 * 
 * @author Puneet Chandna
 * @version 1.0
 */
public class WorkloadCharacteristics {
    
    // Resource requirement patterns
    private final ResourceRequirementPatterns resourcePatterns;
    
    // Temporal characteristics
    private final TemporalCharacteristics temporalCharacteristics;
    
    // SLA requirements
    private final SlaRequirements slaRequirements;
    
    // Performance targets
    private final PerformanceTargets performanceTargets;
    
    // Workload metadata for research
    private final WorkloadMetadata metadata;
    
    // Statistical characteristics
    private final StatisticalCharacteristics statisticalCharacteristics;
    
    /**
     * Constructor for comprehensive workload characteristics.
     * 
     * @param builder WorkloadCharacteristics builder
     */
    private WorkloadCharacteristics(Builder builder) {
        this.resourcePatterns = builder.resourcePatterns;
        this.temporalCharacteristics = builder.temporalCharacteristics;
        this.slaRequirements = builder.slaRequirements;
        this.performanceTargets = builder.performanceTargets;
        this.metadata = builder.metadata;
        this.statisticalCharacteristics = builder.statisticalCharacteristics;
        
        validateWorkloadCharacteristics();
        LoggingManager.logInfo("WorkloadCharacteristics created: " + metadata.workloadName);
    }
    
    /**
     * Validate workload characteristics for research integrity.
     * 
     * @throws ExperimentException if validation fails
     */
    private void validateWorkloadCharacteristics() {
        try {
            ValidationUtils.validateNotNull(resourcePatterns, "Resource patterns cannot be null");
            ValidationUtils.validateNotNull(temporalCharacteristics, "Temporal characteristics cannot be null");
            ValidationUtils.validateNotNull(slaRequirements, "SLA requirements cannot be null");
            ValidationUtils.validateNotNull(performanceTargets, "Performance targets cannot be null");
            ValidationUtils.validateNotNull(metadata, "Workload metadata cannot be null");
            
            // Validate consistency between components
            validateCharacteristicsConsistency();
            
        } catch (Exception e) {
            throw new ExperimentException("Workload characteristics validation failed", e);
        }
    }
    
    /**
     * Validate consistency between workload characteristics components.
     */
    private void validateCharacteristicsConsistency() {
        // Validate temporal duration consistency
        if (temporalCharacteristics.totalDuration <= 0) {
            throw new ExperimentException("Invalid temporal duration: " + temporalCharacteristics.totalDuration);
        }
        
        // Validate resource requirement bounds
        if (resourcePatterns.maxCpuRequirement <= 0 || resourcePatterns.maxMemoryRequirement <= 0) {
            throw new ExperimentException("Invalid resource requirements bounds");
        }
        
        // Validate SLA targets
        if (slaRequirements.maxResponseTime <= 0 || slaRequirements.minAvailability <= 0 || 
            slaRequirements.minAvailability > 1.0) {
            throw new ExperimentException("Invalid SLA requirements");
        }
    }
    
    // Getters for all components
    public ResourceRequirementPatterns getResourcePatterns() { return resourcePatterns; }
    public TemporalCharacteristics getTemporalCharacteristics() { return temporalCharacteristics; }
    public SlaRequirements getSlaRequirements() { return slaRequirements; }
    public PerformanceTargets getPerformanceTargets() { return performanceTargets; }
    public WorkloadMetadata getMetadata() { return metadata; }
    public StatisticalCharacteristics getStatisticalCharacteristics() { return statisticalCharacteristics; }
    
    /**
     * Get workload complexity score for research analysis.
     * 
     * @return workload complexity score (0.0 to 1.0)
     */
    public double getWorkloadComplexityScore() {
        double resourceComplexity = calculateResourceComplexity();
        double temporalComplexity = calculateTemporalComplexity();
        double slaComplexity = calculateSlaComplexity();
        
        return (resourceComplexity + temporalComplexity + slaComplexity) / 3.0;
    }
    
    /**
     * Calculate resource complexity for statistical analysis.
     */
    private double calculateResourceComplexity() {
        double cpuVariability = resourcePatterns.cpuVariabilityCoefficient;
        double memoryVariability = resourcePatterns.memoryVariabilityCoefficient;
        double resourceCorrelation = Math.abs(resourcePatterns.cpuMemoryCorrelation);
        
        return Math.min(1.0, (cpuVariability + memoryVariability + resourceCorrelation) / 3.0);
    }
    
    /**
     * Calculate temporal complexity for statistical analysis.
     */
    private double calculateTemporalComplexity() {
        double arrivalVariability = temporalCharacteristics.arrivalRateVariability;
        double durationVariability = temporalCharacteristics.executionTimeVariability;
        double seasonality = temporalCharacteristics.seasonalityIndex;
        
        return Math.min(1.0, (arrivalVariability + durationVariability + seasonality) / 3.0);
    }
    
    /**
     * Calculate SLA complexity for statistical analysis.
     */
    private double calculateSlaComplexity() {
        double availabilityStrictness = 1.0 - slaRequirements.minAvailability;
        double responseTimeStrictness = 1.0 / (1.0 + slaRequirements.maxResponseTime);
        double throughputStrictness = slaRequirements.minThroughput / 1000.0; // Normalize
        
        return Math.min(1.0, (availabilityStrictness + responseTimeStrictness + throughputStrictness) / 3.0);
    }
    
    /**
     * Generate publication-ready workload description.
     * 
     * @return formatted workload description for research papers
     */
    public String generatePublicationDescription() {
        StringBuilder description = new StringBuilder();
        
        description.append("Workload: ").append(metadata.workloadName).append("\n");
        description.append("Type: ").append(metadata.workloadType).append("\n");
        description.append("Scale: ").append(metadata.vmCount).append(" VMs, ")
                  .append(metadata.taskCount).append(" tasks\n");
        
        description.append("Resource Characteristics:\n");
        description.append("  CPU: μ=").append(String.format("%.2f", resourcePatterns.avgCpuRequirement))
                  .append(", σ=").append(String.format("%.2f", resourcePatterns.cpuStandardDeviation)).append("\n");
        description.append("  Memory: μ=").append(String.format("%.2f", resourcePatterns.avgMemoryRequirement))
                  .append(", σ=").append(String.format("%.2f", resourcePatterns.memoryStandardDeviation)).append("\n");
        
        description.append("Temporal Characteristics:\n");
        description.append("  Duration: ").append(temporalCharacteristics.totalDuration).append(" time units\n");
        description.append("  Arrival Pattern: ").append(temporalCharacteristics.arrivalPattern).append("\n");
        
        description.append("SLA Requirements:\n");
        description.append("  Availability: ≥").append(String.format("%.3f", slaRequirements.minAvailability)).append("\n");
        description.append("  Response Time: ≤").append(slaRequirements.maxResponseTime).append(" ms\n");
        
        description.append("Complexity Score: ").append(String.format("%.3f", getWorkloadComplexityScore()));
        
        return description.toString();
    }
    
    /**
     * Generate LaTeX table row for publication.
     * 
     * @return LaTeX-formatted table row
     */
    public String generateLatexTableRow() {
        return String.format("%s & %s & %d & %.2f & %.2f & %.3f & %.2f & %.3f \\\\",
            metadata.workloadName,
            metadata.workloadType,
            metadata.vmCount,
            resourcePatterns.avgCpuRequirement,
            resourcePatterns.avgMemoryRequirement,
            slaRequirements.minAvailability,
            slaRequirements.maxResponseTime,
            getWorkloadComplexityScore());
    }
    
    /**
     * Resource requirement patterns for comprehensive workload modeling.
     */
    public static class ResourceRequirementPatterns {
        public final double avgCpuRequirement;
        public final double avgMemoryRequirement;
        public final double maxCpuRequirement;
        public final double maxMemoryRequirement;
        public final double minCpuRequirement;
        public final double minMemoryRequirement;
        public final double cpuStandardDeviation;
        public final double memoryStandardDeviation;
        public final double cpuVariabilityCoefficient;
        public final double memoryVariabilityCoefficient;
        public final double cpuMemoryCorrelation;
        public final String resourceDistribution;
        
        public ResourceRequirementPatterns(double avgCpu, double avgMemory, double maxCpu, double maxMemory,
                                         double minCpu, double minMemory, double cpuStdDev, double memoryStdDev,
                                         double cpuVariability, double memoryVariability, double correlation,
                                         String distribution) {
            this.avgCpuRequirement = avgCpu;
            this.avgMemoryRequirement = avgMemory;
            this.maxCpuRequirement = maxCpu;
            this.maxMemoryRequirement = maxMemory;
            this.minCpuRequirement = minCpu;
            this.minMemoryRequirement = minMemory;
            this.cpuStandardDeviation = cpuStdDev;
            this.memoryStandardDeviation = memoryStdDev;
            this.cpuVariabilityCoefficient = cpuVariability;
            this.memoryVariabilityCoefficient = memoryVariability;
            this.cpuMemoryCorrelation = correlation;
            this.resourceDistribution = distribution;
        }
    }
    
    /**
     * Temporal characteristics for workload timing analysis.
     */
    public static class TemporalCharacteristics {
        public final double totalDuration;
        public final String arrivalPattern;
        public final double arrivalRate;
        public final double arrivalRateVariability;
        public final double avgExecutionTime;
        public final double executionTimeVariability;
        public final double seasonalityIndex;
        public final List<Double> peakPeriods;
        public final Map<String, Double> temporalDistributionParameters;
        
        public TemporalCharacteristics(double duration, String arrivalPattern, double arrivalRate,
                                     double arrivalVariability, double avgExecutionTime, double executionVariability,
                                     double seasonalityIndex, List<Double> peakPeriods,
                                     Map<String, Double> distributionParams) {
            this.totalDuration = duration;
            this.arrivalPattern = arrivalPattern;
            this.arrivalRate = arrivalRate;
            this.arrivalRateVariability = arrivalVariability;
            this.avgExecutionTime = avgExecutionTime;
            this.executionTimeVariability = executionVariability;
            this.seasonalityIndex = seasonalityIndex;
            this.peakPeriods = new ArrayList<>(peakPeriods);
            this.temporalDistributionParameters = new HashMap<>(distributionParams);
        }
    }
    
    /**
     * SLA requirements for compliance analysis.
     */
    public static class SlaRequirements {
        public final double minAvailability;
        public final double maxResponseTime;
        public final double minThroughput;
        public final double maxErrorRate;
        public final Map<String, Double> customSlaMetrics;
        public final String slaClass;
        public final double penaltyCost;
        
        public SlaRequirements(double minAvailability, double maxResponseTime, double minThroughput,
                             double maxErrorRate, Map<String, Double> customMetrics, String slaClass,
                             double penaltyCost) {
            this.minAvailability = minAvailability;
            this.maxResponseTime = maxResponseTime;
            this.minThroughput = minThroughput;
            this.maxErrorRate = maxErrorRate;
            this.customSlaMetrics = new HashMap<>(customMetrics);
            this.slaClass = slaClass;
            this.penaltyCost = penaltyCost;
        }
    }
    
    /**
     * Performance targets for optimization objectives.
     */
    public static class PerformanceTargets {
        public final double targetResourceUtilization;
        public final double targetPowerEfficiency;
        public final double targetCostEfficiency;
        public final double targetLoadBalancing;
        public final Map<String, Double> customPerformanceTargets;
        public final List<String> optimizationObjectives;
        
        public PerformanceTargets(double resourceUtilization, double powerEfficiency, double costEfficiency,
                                double loadBalancing, Map<String, Double> customTargets,
                                List<String> objectives) {
            this.targetResourceUtilization = resourceUtilization;
            this.targetPowerEfficiency = powerEfficiency;
            this.targetCostEfficiency = costEfficiency;
            this.targetLoadBalancing = loadBalancing;
            this.customPerformanceTargets = new HashMap<>(customTargets);
            this.optimizationObjectives = new ArrayList<>(objectives);
        }
    }
    
    /**
     * Workload metadata for research documentation.
     */
    public static class WorkloadMetadata {
        public final String workloadName;
        public final String workloadType;
        public final String datasetSource;
        public final int vmCount;
        public final int taskCount;
        public final String description;
        public final Map<String, String> additionalMetadata;
        public final long timestamp;
        
        public WorkloadMetadata(String name, String type, String source, int vmCount, int taskCount,
                              String description, Map<String, String> additionalMetadata) {
            this.workloadName = name;
            this.workloadType = type;
            this.datasetSource = source;
            this.vmCount = vmCount;
            this.taskCount = taskCount;
            this.description = description;
            this.additionalMetadata = new HashMap<>(additionalMetadata);
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * Statistical characteristics for research analysis.
     */
    public static class StatisticalCharacteristics {
        public final Map<String, Double> distributionParameters;
        public final String resourceDistributionType;
        public final String temporalDistributionType;
        public final double autocorrelationCoefficient;
        public final List<Double> quantiles;
        public final double entropy;
        public final Map<String, Double> momentStatistics;
        
        public StatisticalCharacteristics(Map<String, Double> distributionParams, String resourceDistType,
                                        String temporalDistType, double autocorrelation, List<Double> quantiles,
                                        double entropy, Map<String, Double> moments) {
            this.distributionParameters = new HashMap<>(distributionParams);
            this.resourceDistributionType = resourceDistType;
            this.temporalDistributionType = temporalDistType;
            this.autocorrelationCoefficient = autocorrelation;
            this.quantiles = new ArrayList<>(quantiles);
            this.entropy = entropy;
            this.momentStatistics = new HashMap<>(moments);
        }
    }
    
    /**
     * Builder pattern for comprehensive workload characteristics construction.
     */
    public static class Builder {
        private ResourceRequirementPatterns resourcePatterns;
        private TemporalCharacteristics temporalCharacteristics;
        private SlaRequirements slaRequirements;
        private PerformanceTargets performanceTargets;
        private WorkloadMetadata metadata;
        private StatisticalCharacteristics statisticalCharacteristics;
        
        public Builder setResourcePatterns(ResourceRequirementPatterns patterns) {
            this.resourcePatterns = patterns;
            return this;
        }
        
        public Builder setTemporalCharacteristics(TemporalCharacteristics characteristics) {
            this.temporalCharacteristics = characteristics;
            return this;
        }
        
        public Builder setSlaRequirements(SlaRequirements requirements) {
            this.slaRequirements = requirements;
            return this;
        }
        
        public Builder setPerformanceTargets(PerformanceTargets targets) {
            this.performanceTargets = targets;
            return this;
        }
        
        public Builder setMetadata(WorkloadMetadata metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public Builder setStatisticalCharacteristics(StatisticalCharacteristics characteristics) {
            this.statisticalCharacteristics = characteristics;
            return this;
        }
        
        public WorkloadCharacteristics build() {
            return new WorkloadCharacteristics(this);
        }
    }
    
    /**
     * Create a default workload characteristics for testing.
     * 
     * @return default workload characteristics
     */
    public static WorkloadCharacteristics createDefault() {
        ResourceRequirementPatterns defaultResource = new ResourceRequirementPatterns(
            50.0, 1024.0, 100.0, 2048.0, 10.0, 512.0, 20.0, 256.0, 0.4, 0.25, 0.6, "Normal"
        );
        
        TemporalCharacteristics defaultTemporal = new TemporalCharacteristics(
            3600.0, "Poisson", 10.0, 0.3, 300.0, 0.5, 0.2, 
            Arrays.asList(900.0, 1800.0, 2700.0), 
            Map.of("lambda", 10.0, "shape", 2.0)
        );
        
        SlaRequirements defaultSla = new SlaRequirements(
            0.99, 100.0, 1000.0, 0.01, Map.of(), "Gold", 100.0
        );
        
        PerformanceTargets defaultTargets = new PerformanceTargets(
            0.8, 0.9, 0.85, 0.9, Map.of(), Arrays.asList("utilization", "power", "sla")
        );
        
        WorkloadMetadata defaultMetadata = new WorkloadMetadata(
            "DefaultWorkload", "Mixed", "Synthetic", 100, 1000, "Default test workload", Map.of()
        );
        
        StatisticalCharacteristics defaultStats = new StatisticalCharacteristics(
            Map.of("mean", 50.0, "stddev", 20.0), "Normal", "Exponential", 0.3,
            Arrays.asList(0.25, 0.5, 0.75, 0.95), 3.5, 
            Map.of("skewness", 0.1, "kurtosis", 3.0)
        );
        
        return new Builder()
            .setResourcePatterns(defaultResource)
            .setTemporalCharacteristics(defaultTemporal)
            .setSlaRequirements(defaultSla)
            .setPerformanceTargets(defaultTargets)
            .setMetadata(defaultMetadata)
            .setStatisticalCharacteristics(defaultStats)
            .build();
    }
}
