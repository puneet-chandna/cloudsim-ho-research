package org.cloudbus.cloudsim.experiment;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;

/**
 * Store comprehensive experimental results for research analysis
 * Supporting research objectives: comprehensive metrics collection and statistical analysis
 * @author Puneet Chandna
 */
public class ExperimentalResult {
    
    @JsonProperty("experiment_id")
    private String experimentId;
    
    @JsonProperty("experiment_config")
    private ExperimentConfig experimentConfig;
    
    @JsonProperty("start_time")
    private LocalDateTime startTime;
    
    @JsonProperty("end_time")
    private LocalDateTime endTime;
    
    @JsonProperty("execution_duration_ms")
    private long executionDurationMs;
    
    @JsonProperty("performance_metrics")
    private PerformanceMetrics performanceMetrics;
    
    @JsonProperty("statistical_measures")
    private StatisticalMeasures statisticalMeasures;
    
    @JsonProperty("execution_metadata")
    private ExecutionMetadata executionMetadata;
    
    @JsonProperty("convergence_data")
    private List<ConvergencePoint> convergenceData;
    
    @JsonProperty("raw_data")
    private Map<String, List<Double>> rawData;
    
    @JsonProperty("validation_status")
    private ValidationStatus validationStatus;
    
    // Nested classes for structured data
    public static class PerformanceMetrics {
        @JsonProperty("resource_utilization")
        private ResourceUtilizationMetrics resourceUtilization;
        
        @JsonProperty("power_consumption")
        private PowerConsumptionMetrics powerConsumption;
        
        @JsonProperty("sla_violations")
        private SLAViolationMetrics slaViolations;
        
        @JsonProperty("response_time")
        private ResponseTimeMetrics responseTime;
        
        @JsonProperty("throughput")
        private ThroughputMetrics throughput;
        
        @JsonProperty("cost_metrics")
        private CostMetrics costMetrics;
        
        @JsonProperty("migration_metrics")
        private MigrationMetrics migrationMetrics;
        
        public PerformanceMetrics() {
            this.resourceUtilization = new ResourceUtilizationMetrics();
            this.powerConsumption = new PowerConsumptionMetrics();
            this.slaViolations = new SLAViolationMetrics();
            this.responseTime = new ResponseTimeMetrics();
            this.throughput = new ThroughputMetrics();
            this.costMetrics = new CostMetrics();
            this.migrationMetrics = new MigrationMetrics();
        }
        
        // Getters and setters
        public ResourceUtilizationMetrics getResourceUtilization() { 
            return resourceUtilization; 
        }
        public void setResourceUtilization(ResourceUtilizationMetrics resourceUtilization) { 
            this.resourceUtilization = resourceUtilization; 
        }
        
        public PowerConsumptionMetrics getPowerConsumption() { 
            return powerConsumption; 
        }
        public void setPowerConsumption(PowerConsumptionMetrics powerConsumption) { 
            this.powerConsumption = powerConsumption; 
        }
        
        public SLAViolationMetrics getSlaViolations() { 
            return slaViolations; 
        }
        public void setSlaViolations(SLAViolationMetrics slaViolations) { 
            this.slaViolations = slaViolations; 
        }
        
        public ResponseTimeMetrics getResponseTime() { 
            return responseTime; 
        }
        public void setResponseTime(ResponseTimeMetrics responseTime) { 
            this.responseTime = responseTime; 
        }
        
        public ThroughputMetrics getThroughput() { 
            return throughput; 
        }
        public void setThroughput(ThroughputMetrics throughput) { 
            this.throughput = throughput; 
        }
        
        public CostMetrics getCostMetrics() { 
            return costMetrics; 
        }
        public void setCostMetrics(CostMetrics costMetrics) { 
            this.costMetrics = costMetrics; 
        }
        
        public MigrationMetrics getMigrationMetrics() { 
            return migrationMetrics; 
        }
        public void setMigrationMetrics(MigrationMetrics migrationMetrics) { 
            this.migrationMetrics = migrationMetrics; 
        }
    }
    
    public static class ResourceUtilizationMetrics {
        private double avgCpuUtilization;
        private double avgMemoryUtilization;
        private double avgStorageUtilization;
        private double avgNetworkUtilization;
        private double peakCpuUtilization;
        private double peakMemoryUtilization;
        private Map<String, Double> utilizationByHost;
        
        // Getters and setters
        public double getAvgCpuUtilization() { return avgCpuUtilization; }
        public void setAvgCpuUtilization(double avgCpuUtilization) { 
            this.avgCpuUtilization = avgCpuUtilization; 
        }
        
        public double getAvgMemoryUtilization() { return avgMemoryUtilization; }
        public void setAvgMemoryUtilization(double avgMemoryUtilization) { 
            this.avgMemoryUtilization = avgMemoryUtilization; 
        }
        
        public double getAvgStorageUtilization() { return avgStorageUtilization; }
        public void setAvgStorageUtilization(double avgStorageUtilization) { 
            this.avgStorageUtilization = avgStorageUtilization; 
        }
        
        public double getAvgNetworkUtilization() { return avgNetworkUtilization; }
        public void setAvgNetworkUtilization(double avgNetworkUtilization) { 
            this.avgNetworkUtilization = avgNetworkUtilization; 
        }
        
        public double getPeakCpuUtilization() { return peakCpuUtilization; }
        public void setPeakCpuUtilization(double peakCpuUtilization) { 
            this.peakCpuUtilization = peakCpuUtilization; 
        }
        
        public double getPeakMemoryUtilization() { return peakMemoryUtilization; }
        public void setPeakMemoryUtilization(double peakMemoryUtilization) { 
            this.peakMemoryUtilization = peakMemoryUtilization; 
        }
        
        public Map<String, Double> getUtilizationByHost() { return utilizationByHost; }
        public void setUtilizationByHost(Map<String, Double> utilizationByHost) { 
            this.utilizationByHost = utilizationByHost; 
        }
    }
    
    public static class PowerConsumptionMetrics {
        private double totalPowerConsumption;
        private double avgPowerConsumption;
        private double peakPowerConsumption;
        private double powerEfficiencyRatio;
        private Map<String, Double> powerByHost;
        
        // Getters and setters
        public double getTotalPowerConsumption() { return totalPowerConsumption; }
        public void setTotalPowerConsumption(double totalPowerConsumption) { 
            this.totalPowerConsumption = totalPowerConsumption; 
        }
        
        public double getAvgPowerConsumption() { return avgPowerConsumption; }
        public void setAvgPowerConsumption(double avgPowerConsumption) { 
            this.avgPowerConsumption = avgPowerConsumption; 
        }
        
        public double getPeakPowerConsumption() { return peakPowerConsumption; }
        public void setPeakPowerConsumption(double peakPowerConsumption) { 
            this.peakPowerConsumption = peakPowerConsumption; 
        }
        
        public double getPowerEfficiencyRatio() { return powerEfficiencyRatio; }
        public void setPowerEfficiencyRatio(double powerEfficiencyRatio) { 
            this.powerEfficiencyRatio = powerEfficiencyRatio; 
        }
        
        public Map<String, Double> getPowerByHost() { return powerByHost; }
        public void setPowerByHost(Map<String, Double> powerByHost) { 
            this.powerByHost = powerByHost; 
        }
    }
    
    public static class SLAViolationMetrics {
        private int totalViolations;
        private double violationRate;
        private Map<String, Integer> violationsByType;
        private double avgViolationDuration;
        private double totalPenaltyCost;
        
        // Getters and setters
        public int getTotalViolations() { return totalViolations; }
        public void setTotalViolations(int totalViolations) { 
            this.totalViolations = totalViolations; 
        }
        
        public double getViolationRate() { return violationRate; }
        public void setViolationRate(double violationRate) { 
            this.violationRate = violationRate; 
        }
        
        public Map<String, Integer> getViolationsByType() { return violationsByType; }
        public void setViolationsByType(Map<String, Integer> violationsByType) { 
            this.violationsByType = violationsByType; 
        }
        
        public double getAvgViolationDuration() { return avgViolationDuration; }
        public void setAvgViolationDuration(double avgViolationDuration) { 
            this.avgViolationDuration = avgViolationDuration; 
        }
        
        public double getTotalPenaltyCost() { return totalPenaltyCost; }
        public void setTotalPenaltyCost(double totalPenaltyCost) { 
            this.totalPenaltyCost = totalPenaltyCost; 
        }
    }
    
    public static class ResponseTimeMetrics {
        private double avgResponseTime;
        private double minResponseTime;
        private double maxResponseTime;
        private double p50ResponseTime;
        private double p95ResponseTime;
        private double p99ResponseTime;
        
        // Getters and setters
        public double getAvgResponseTime() { return avgResponseTime; }
        public void setAvgResponseTime(double avgResponseTime) { 
            this.avgResponseTime = avgResponseTime; 
        }
        
        public double getMinResponseTime() { return minResponseTime; }
        public void setMinResponseTime(double minResponseTime) { 
            this.minResponseTime = minResponseTime; 
        }
        
        public double getMaxResponseTime() { return maxResponseTime; }
        public void setMaxResponseTime(double maxResponseTime) { 
            this.maxResponseTime = maxResponseTime; 
        }
        
        public double getP50ResponseTime() { return p50ResponseTime; }
        public void setP50ResponseTime(double p50ResponseTime) { 
            this.p50ResponseTime = p50ResponseTime; 
        }
        
        public double getP95ResponseTime() { return p95ResponseTime; }
        public void setP95ResponseTime(double p95ResponseTime) { 
            this.p95ResponseTime = p95ResponseTime; 
        }
        
        public double getP99ResponseTime() { return p99ResponseTime; }
        public void setP99ResponseTime(double p99ResponseTime) { 
            this.p99ResponseTime = p99ResponseTime; 
        }
    }
    
    public static class ThroughputMetrics {
        private double avgThroughput;
        private double peakThroughput;
        private double totalJobsCompleted;
        private double successRate;
        
        // Getters and setters
        public double getAvgThroughput() { return avgThroughput; }
        public void setAvgThroughput(double avgThroughput) { 
            this.avgThroughput = avgThroughput; 
        }
        
        public double getPeakThroughput() { return peakThroughput; }
        public void setPeakThroughput(double peakThroughput) { 
            this.peakThroughput = peakThroughput; 
        }
        
        public double getTotalJobsCompleted() { return totalJobsCompleted; }
        public void setTotalJobsCompleted(double totalJobsCompleted) { 
            this.totalJobsCompleted = totalJobsCompleted; 
        }
        
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { 
            this.successRate = successRate; 
        }
    }
    
    public static class CostMetrics {
        private double totalOperationalCost;
        private double powerCost;
        private double resourceCost;
        private double slaPenaltyCost;
        private double costPerJob;
        
        // Getters and setters
        public double getTotalOperationalCost() { return totalOperationalCost; }
        public void setTotalOperationalCost(double totalOperationalCost) { 
            this.totalOperationalCost = totalOperationalCost; 
        }
        
        public double getPowerCost() { return powerCost; }
        public void setPowerCost(double powerCost) { 
            this.powerCost = powerCost; 
        }
        
        public double getResourceCost() { return resourceCost; }
        public void setResourceCost(double resourceCost) { 
            this.resourceCost = resourceCost; 
        }
        
        public double getSlaPenaltyCost() { return slaPenaltyCost; }
        public void setSlaPenaltyCost(double slaPenaltyCost) { 
            this.slaPenaltyCost = slaPenaltyCost; 
        }
        
        public double getCostPerJob() { return costPerJob; }
        public void setCostPerJob(double costPerJob) { 
            this.costPerJob = costPerJob; 
        }
    }
    
    public static class MigrationMetrics {
        private int totalMigrations;
        private double avgMigrationTime;
        private double totalMigrationDowntime;
        private double migrationOverhead;
        
        // Getters and setters
        public int getTotalMigrations() { return totalMigrations; }
        public void setTotalMigrations(int totalMigrations) { 
            this.totalMigrations = totalMigrations; 
        }
        
        public double getAvgMigrationTime() { return avgMigrationTime; }
        public void setAvgMigrationTime(double avgMigrationTime) { 
            this.avgMigrationTime = avgMigrationTime; 
        }
        
        public double getTotalMigrationDowntime() { return totalMigrationDowntime; }
        public void setTotalMigrationDowntime(double totalMigrationDowntime) { 
            this.totalMigrationDowntime = totalMigrationDowntime; 
        }
        
        public double getMigrationOverhead() { return migrationOverhead; }
        public void setMigrationOverhead(double migrationOverhead) { 
            this.migrationOverhead = migrationOverhead; 
        }
    }
    
    public static class StatisticalMeasures {
        private Map<String, Double> means;
        private Map<String, Double> standardDeviations;
        private Map<String, Double> confidenceIntervals;
        private Map<String, Double> minValues;
        private Map<String, Double> maxValues;
        private Map<String, Double> medians;
        private Map<String, Double> coefficientsOfVariation;
        
        public StatisticalMeasures() {
            this.means = new HashMap<>();
            this.standardDeviations = new HashMap<>();
            this.confidenceIntervals = new HashMap<>();
            this.minValues = new HashMap<>();
            this.maxValues = new HashMap<>();
            this.medians = new HashMap<>();
            this.coefficientsOfVariation = new HashMap<>();
        }
        
        // Getters and setters
        public Map<String, Double> getMeans() { return means; }
        public void setMeans(Map<String, Double> means) { 
            this.means = means; 
        }
        
        public Map<String, Double> getStandardDeviations() { return standardDeviations; }
        public void setStandardDeviations(Map<String, Double> standardDeviations) { 
            this.standardDeviations = standardDeviations; 
        }
        
        public Map<String, Double> getConfidenceIntervals() { return confidenceIntervals; }
        public void setConfidenceIntervals(Map<String, Double> confidenceIntervals) { 
            this.confidenceIntervals = confidenceIntervals; 
        }
        
        public Map<String, Double> getMinValues() { return minValues; }
        public void setMinValues(Map<String, Double> minValues) { 
            this.minValues = minValues; 
        }
        
        public Map<String, Double> getMaxValues() { return maxValues; }
        public void setMaxValues(Map<String, Double> maxValues) { 
            this.maxValues = maxValues; 
        }
        
        public Map<String, Double> getMedians() { return medians; }
        public void setMedians(Map<String, Double> medians) { 
            this.medians = medians; 
        }
        
        public Map<String, Double> getCoefficientsOfVariation() { 
            return coefficientsOfVariation; 
        }
        public void setCoefficientsOfVariation(Map<String, Double> coefficientsOfVariation) { 
            this.coefficientsOfVariation = coefficientsOfVariation; 
        }
    }
    
    public static class ExecutionMetadata {
        private String hostMachine;
        private String operatingSystem;
        private String javaVersion;
        private int availableProcessors;
        private long maxMemory;
        private long usedMemory;
        private Map<String, String> systemProperties;
        
        // Getters and setters
        public String getHostMachine() { return hostMachine; }
        public void setHostMachine(String hostMachine) { 
            this.hostMachine = hostMachine; 
        }
        
        public String getOperatingSystem() { return operatingSystem; }
        public void setOperatingSystem(String operatingSystem) { 
            this.operatingSystem = operatingSystem; 
        }
        
        public String getJavaVersion() { return javaVersion; }
        public void setJavaVersion(String javaVersion) { 
            this.javaVersion = javaVersion; 
        }
        
        public int getAvailableProcessors() { return availableProcessors; }
        public void setAvailableProcessors(int availableProcessors) { 
            this.availableProcessors = availableProcessors; 
        }
        
        public long getMaxMemory() { return maxMemory; }
        public void setMaxMemory(long maxMemory) { 
            this.maxMemory = maxMemory; 
        }
        
        public long getUsedMemory() { return usedMemory; }
        public void setUsedMemory(long usedMemory) { 
            this.usedMemory = usedMemory; 
        }
        
        public Map<String, String> getSystemProperties() { return systemProperties; }
        public void setSystemProperties(Map<String, String> systemProperties) { 
            this.systemProperties = systemProperties; 
        }
    }
    
    public static class ConvergencePoint {
        private int iteration;
        private double fitnessValue;
        private double timestamp;
        private Map<String, Double> objectiveValues;
        
        // Getters and setters
        public int getIteration() { return iteration; }
        public void setIteration(int iteration) { 
            this.iteration = iteration; 
        }
        
        public double getFitnessValue() { return fitnessValue; }
        public void setFitnessValue(double fitnessValue) { 
            this.fitnessValue = fitnessValue; 
        }
        
        public double getTimestamp() { return timestamp; }
        public void setTimestamp(double timestamp) { 
            this.timestamp = timestamp; 
        }
        
        public Map<String, Double> getObjectiveValues() { return objectiveValues; }
        public void setObjectiveValues(Map<String, Double> objectiveValues) { 
            this.objectiveValues = objectiveValues; 
        }
    }
    
    public static class ValidationStatus {
        private boolean isValid;
        private List<String> validationErrors;
        private Map<String, Boolean> checksPerformed;
        
        public ValidationStatus() {
            this.isValid = true;
            this.validationErrors = new ArrayList<>();
            this.checksPerformed = new HashMap<>();
        }
        
        // Getters and setters
        public boolean isValid() { return isValid; }
        public void setValid(boolean valid) { 
            isValid = valid; 
        }
        
        public List<String> getValidationErrors() { return validationErrors; }
        public void setValidationErrors(List<String> validationErrors) { 
            this.validationErrors = validationErrors; 
        }
        
        public Map<String, Boolean> getChecksPerformed() { return checksPerformed; }
        public void setChecksPerformed(Map<String, Boolean> checksPerformed) { 
            this.checksPerformed = checksPerformed; 
        }
    }
    
    // Constructors
    public ExperimentalResult() {
        this.experimentId = UUID.randomUUID().toString();
        this.startTime = LocalDateTime.now();
        this.performanceMetrics = new PerformanceMetrics();
        this.statisticalMeasures = new StatisticalMeasures();
        this.executionMetadata = new ExecutionMetadata();
        this.convergenceData = new ArrayList<>();
        this.rawData = new HashMap<>();
        this.validationStatus = new ValidationStatus();
        
        // Initialize execution metadata
        initializeExecutionMetadata();
    }
    
    private void initializeExecutionMetadata() {
        executionMetadata.setHostMachine(
            System.getProperty("os.name") + " " + System.getProperty("os.arch")
        );
        executionMetadata.setOperatingSystem(System.getProperty("os.version"));
        executionMetadata.setJavaVersion(System.getProperty("java.version"));
        executionMetadata.setAvailableProcessors(
            Runtime.getRuntime().availableProcessors()
        );
        executionMetadata.setMaxMemory(Runtime.getRuntime().maxMemory());
        executionMetadata.setSystemProperties(new HashMap<>());
    }
    
    // Utility methods
    public void completeExecution() {
        this.endTime = LocalDateTime.now();
        this.executionDurationMs = Duration.between(startTime, endTime).toMillis();
    }
    
    public void addConvergencePoint(int iteration, double fitness, 
                                   Map<String, Double> objectives) {
        ConvergencePoint point = new ConvergencePoint();
        point.setIteration(iteration);
        point.setFitnessValue(fitness);
        point.setTimestamp(System.currentTimeMillis());
        point.setObjectiveValues(objectives);
        convergenceData.add(point);
    }
    
    public void addRawDataPoint(String metric, double value) {
        rawData.computeIfAbsent(metric, k -> new ArrayList<>()).add(value);
    }
    
    public void saveToJSON(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules(); // For Java 8 time support
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), this);
    }
    
    public static ExperimentalResult loadFromJSON(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper.readValue(new File(filePath), ExperimentalResult.class);
    }
    
    // Getters and setters
    public String getExperimentId() { return experimentId; }
    public void setExperimentId(String experimentId) { 
        this.experimentId = experimentId; 
    }
    
    public ExperimentConfig getExperimentConfig() { return experimentConfig; }
    public void setExperimentConfig(ExperimentConfig experimentConfig) { 
        this.experimentConfig = experimentConfig; 
    }
    
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { 
        this.startTime = startTime; 
    }
    
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { 
        this.endTime = endTime; 
    }
    
    public long getExecutionDurationMs() { return executionDurationMs; }
    public void setExecutionDurationMs(long executionDurationMs) { 
        this.executionDurationMs = executionDurationMs; 
    }
    
    public PerformanceMetrics getPerformanceMetrics() { return performanceMetrics; }
    public void setPerformanceMetrics(PerformanceMetrics performanceMetrics) { 
        this.performanceMetrics = performanceMetrics; 
    }
    
    public StatisticalMeasures getStatisticalMeasures() { return statisticalMeasures; }
    public void setStatisticalMeasures(StatisticalMeasures statisticalMeasures) { 
        this.statisticalMeasures = statisticalMeasures; 
    }
    
    public ExecutionMetadata getExecutionMetadata() { return executionMetadata; }
    public void setExecutionMetadata(ExecutionMetadata executionMetadata) { 
        this.executionMetadata = executionMetadata; 
    }
    
    public List<ConvergencePoint> getConvergenceData() { return convergenceData; }
    public void setConvergenceData(List<ConvergencePoint> convergenceData) { 
        this.convergenceData = convergenceData; 
    }
    
    public Map<String, List<Double>> getRawData() { return rawData; }
    public void setRawData(Map<String, List<Double>> rawData) { 
        this.rawData = rawData; 
    }
    
    public ValidationStatus getValidationStatus() { return validationStatus; }
    public void setValidationStatus(ValidationStatus validationStatus) { 
        this.validationStatus = validationStatus; 
    }
    
    // Convenience methods for PerformanceMetricsAnalyzer
    public String getAlgorithmName() {
        return experimentConfig != null ? experimentConfig.getAlgorithmType() : "Unknown";
    }
    
    public double getResourceUtilization() {
        if (performanceMetrics != null && performanceMetrics.getResourceUtilization() != null) {
            return performanceMetrics.getResourceUtilization().getAvgCpuUtilization();
        }
        return 0.0;
    }
    
    public double getPowerConsumption() {
        if (performanceMetrics != null && performanceMetrics.getPowerConsumption() != null) {
            return performanceMetrics.getPowerConsumption().getAvgPowerConsumption();
        }
        return 0.0;
    }
    
    public double getThroughput() {
        if (performanceMetrics != null && performanceMetrics.getThroughput() != null) {
            return performanceMetrics.getThroughput().getAvgThroughput();
        }
        return 0.0;
    }
    
    public double getAverageResponseTime() {
        if (performanceMetrics != null && performanceMetrics.getResponseTime() != null) {
            return performanceMetrics.getResponseTime().getAvgResponseTime();
        }
        return 0.0;
    }
    
    public double getSlaViolations() {
        if (performanceMetrics != null && performanceMetrics.getSlaViolations() != null) {
            return performanceMetrics.getSlaViolations().getViolationRate();
        }
        return 0.0;
    }
    
    public long getExecutionTime() {
        return executionDurationMs;
    }
    
    public Map<String, Object> getDetailedMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        if (performanceMetrics != null) {
            if (performanceMetrics.getResourceUtilization() != null) {
                metrics.put("cpu_utilization", performanceMetrics.getResourceUtilization().getAvgCpuUtilization());
                metrics.put("memory_utilization", performanceMetrics.getResourceUtilization().getAvgMemoryUtilization());
            }
            if (performanceMetrics.getPowerConsumption() != null) {
                metrics.put("power_consumption", performanceMetrics.getPowerConsumption().getAvgPowerConsumption());
            }
            if (performanceMetrics.getThroughput() != null) {
                metrics.put("throughput", performanceMetrics.getThroughput().getAvgThroughput());
            }
            if (performanceMetrics.getResponseTime() != null) {
                metrics.put("response_time", performanceMetrics.getResponseTime().getAvgResponseTime());
            }
        }
        return metrics;
    }
    
    public Map<String, Object> getScenarioDetails() {
        Map<String, Object> details = new HashMap<>();
        if (experimentConfig != null) {
            details.put("algorithm", getAlgorithmName());
            details.put("experiment_id", experimentId);
            details.put("duration", executionDurationMs);
        }
        return details;
    }
    
    // Compatibility methods
    public Map<String, Object> getExperimentConfiguration() {
        Map<String, Object> config = new HashMap<>();
        if (experimentConfig != null) {
            config.put("algorithm", getAlgorithmName());
            config.put("experiment_id", experimentId);
            // Add other configuration parameters as needed
        }
        return config;
    }
}