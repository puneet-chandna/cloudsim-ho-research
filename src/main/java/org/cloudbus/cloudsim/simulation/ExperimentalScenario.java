package org.cloudbus.cloudsim.simulation;

import org.cloudbus.cloudsim.util.ExperimentException;
import org.cloudbus.cloudsim.util.ValidationUtils;
import org.cloudbus.cloudsim.dataset.WorkloadCharacteristics;

import java.util.*;

/**
 * Purpose: Represent experimental scenarios with comprehensive workload and resource configurations
 * Research Integration: Supports diverse experimental scenarios for comprehensive algorithm evaluation
 * Metrics Support: Tracks workload characteristics and SLA requirements for performance analysis
 * Statistical Methods: Provides scenario diversity metrics for experimental design validation
 * Publication Format: Generates scenario descriptions suitable for methodology sections
 * Dataset Integration: Supports both real-world and synthetic scenario configurations
 * @author Puneet Chandna
 */
public class ExperimentalScenario {
    
    // Core scenario identification
    private String scenarioId;
    private String scenarioName;
    private String description;
    private Date creationTimestamp;
    
    // Workload characteristics
    private WorkloadCharacteristics workloadCharacteristics;
    private int numberOfVms;
    private int numberOfHosts;
    private double arrivalRate;
    private double executionTime;
    private Map<String, Double> resourceDemandDistribution;
    
    // Resource configurations
    private List<HostConfiguration> hostConfigurations;
    private List<VmConfiguration> vmConfigurations;
    private NetworkConfiguration networkConfiguration;
    private PowerConfiguration powerConfiguration;
    
    // SLA requirements
    private Map<String, SLARequirement> slaRequirements;
    private double maxResponseTime;
    private double minAvailability;
    private double maxPowerConsumption;
    
    // Performance targets
    private Map<String, Double> performanceTargets;
    private double targetUtilization;
    private double maxSlaViolationRate;
    private double targetThroughput;
    
    // Experimental metadata
    private String datasetSource;
    private String workloadType;
    private String scenarioCategory;
    private Map<String, Object> experimentalParameters;
    private Set<String> applicableAlgorithms;
    
    // Statistical properties
    private Map<String, Object> statisticalProperties;
    private int replicationSeed;
    private boolean isDeterministic;
    
    /**
     * Default constructor for ExperimentalScenario
     */
    public ExperimentalScenario() {
        this.scenarioId = UUID.randomUUID().toString();
        this.creationTimestamp = new Date();
        this.resourceDemandDistribution = new HashMap<>();
        this.hostConfigurations = new ArrayList<>();
        this.vmConfigurations = new ArrayList<>();
        this.slaRequirements = new HashMap<>();
        this.performanceTargets = new HashMap<>();
        this.experimentalParameters = new HashMap<>();
        this.applicableAlgorithms = new HashSet<>();
        this.statisticalProperties = new HashMap<>();
        this.isDeterministic = true;
    }
    
    /**
     * Constructor with basic scenario information
     */
    public ExperimentalScenario(String scenarioName, String description) {
        this();
        this.scenarioName = scenarioName;
        this.description = description;
    }
    
    /**
     * Validate scenario configuration for experimental consistency
     * @throws ExperimentException if scenario configuration is invalid
     */
    public void validateScenario() {
        try {
            ValidationUtils.validateNotNull(scenarioName, "Scenario name cannot be null");
            ValidationUtils.validateNotNull(workloadCharacteristics, "Workload characteristics must be defined");
            
            if (numberOfVms <= 0) {
                throw new ExperimentException("Number of VMs must be positive: " + numberOfVms);
            }
            
            if (numberOfHosts <= 0) {
                throw new ExperimentException("Number of hosts must be positive: " + numberOfHosts);
            }
            
            if (numberOfVms > numberOfHosts * 10) {
                throw new ExperimentException("VM to Host ratio exceeds reasonable limits");
            }
            
            if (arrivalRate < 0) {
                throw new ExperimentException("Arrival rate cannot be negative: " + arrivalRate);
            }
            
            if (executionTime <= 0) {
                throw new ExperimentException("Execution time must be positive: " + executionTime);
            }
            
            validateSLARequirements();
            validateResourceConfigurations();
            validatePerformanceTargets();
            
        } catch (Exception e) {
            throw new ExperimentException("Scenario validation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Calculate scenario complexity metrics for experimental design
     * @return Map containing complexity metrics
     */
    public Map<String, Double> calculateComplexityMetrics() {
        Map<String, Double> metrics = new HashMap<>();
        
        // Basic complexity metrics
        metrics.put("vm_count", (double) numberOfVms);
        metrics.put("host_count", (double) numberOfHosts);
        metrics.put("vm_host_ratio", (double) numberOfVms / numberOfHosts);
        
        // Resource diversity metrics
        double resourceDiversity = calculateResourceDiversity();
        metrics.put("resource_diversity", resourceDiversity);
        
        // Workload complexity
        double workloadComplexity = calculateWorkloadComplexity();
        metrics.put("workload_complexity", workloadComplexity);
        
        // SLA complexity
        double slaComplexity = slaRequirements.size() * 0.1;
        metrics.put("sla_complexity", slaComplexity);
        
        // Overall complexity score
        double overallComplexity = (numberOfVms * 0.001) + (numberOfHosts * 0.01) + 
                                 resourceDiversity + workloadComplexity + slaComplexity;
        metrics.put("overall_complexity", overallComplexity);
        
        return metrics;
    }
    
    /**
     * Generate scenario fingerprint for reproducibility
     * @return Unique fingerprint string for scenario
     */
    public String generateScenarioFingerprint() {
        StringBuilder fingerprint = new StringBuilder();
        
        fingerprint.append("SC_").append(scenarioName.hashCode()).append("_");
        fingerprint.append("VM").append(numberOfVms).append("_");
        fingerprint.append("H").append(numberOfHosts).append("_");
        fingerprint.append("AR").append(String.format("%.2f", arrivalRate)).append("_");
        fingerprint.append("ET").append(String.format("%.2f", executionTime)).append("_");
        fingerprint.append("SEED").append(replicationSeed);
        
        if (datasetSource != null) {
            fingerprint.append("_DS").append(datasetSource.hashCode());
        }
        
        return fingerprint.toString();
    }
    
    /**
     * Get scenario summary for reporting
     * @return Formatted scenario summary
     */
    public String getScenarioSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Scenario: ").append(scenarioName).append("\n");
        summary.append("Description: ").append(description).append("\n");
        summary.append("VMs: ").append(numberOfVms).append(", Hosts: ").append(numberOfHosts).append("\n");
        summary.append("Arrival Rate: ").append(String.format("%.2f", arrivalRate)).append(" req/s\n");
        summary.append("Execution Time: ").append(String.format("%.2f", executionTime)).append(" seconds\n");
        
        if (datasetSource != null) {
            summary.append("Dataset Source: ").append(datasetSource).append("\n");
        }
        
        summary.append("Workload Type: ").append(workloadType != null ? workloadType : "Unknown").append("\n");
        summary.append("SLA Requirements: ").append(slaRequirements.size()).append(" constraints\n");
        summary.append("Performance Targets: ").append(performanceTargets.size()).append(" metrics\n");
        
        return summary.toString();
    }
    
    /**
     * Create a copy of the scenario with different parameters
     * @param modificationMap Parameters to modify
     * @return New scenario with modified parameters
     */
    public ExperimentalScenario createVariant(Map<String, Object> modificationMap) {
        ExperimentalScenario variant = new ExperimentalScenario();
        
        // Copy basic properties
        variant.scenarioName = this.scenarioName + "_variant";
        variant.description = this.description + " (Modified)";
        variant.numberOfVms = this.numberOfVms;
        variant.numberOfHosts = this.numberOfHosts;
        variant.arrivalRate = this.arrivalRate;
        variant.executionTime = this.executionTime;
        variant.datasetSource = this.datasetSource;
        variant.workloadType = this.workloadType;
        variant.scenarioCategory = this.scenarioCategory;
        
        // Copy collections
        variant.resourceDemandDistribution = new HashMap<>(this.resourceDemandDistribution);
        variant.slaRequirements = new HashMap<>(this.slaRequirements);
        variant.performanceTargets = new HashMap<>(this.performanceTargets);
        variant.experimentalParameters = new HashMap<>(this.experimentalParameters);
        variant.applicableAlgorithms = new HashSet<>(this.applicableAlgorithms);
        variant.statisticalProperties = new HashMap<>(this.statisticalProperties);
        
        // Apply modifications
        for (Map.Entry<String, Object> modification : modificationMap.entrySet()) {
            applyModification(variant, modification.getKey(), modification.getValue());
        }
        
        return variant;
    }
    
    /**
     * Export scenario configuration for external tools
     * @return Map containing all scenario configuration data
     */
    public Map<String, Object> exportConfiguration() {
        Map<String, Object> config = new HashMap<>();
        
        config.put("scenario_id", scenarioId);
        config.put("scenario_name", scenarioName);
        config.put("description", description);
        config.put("creation_timestamp", creationTimestamp);
        
        config.put("number_of_vms", numberOfVms);
        config.put("number_of_hosts", numberOfHosts);
        config.put("arrival_rate", arrivalRate);
        config.put("execution_time", executionTime);
        config.put("resource_demand_distribution", resourceDemandDistribution);
        
        config.put("sla_requirements", slaRequirements);
        config.put("performance_targets", performanceTargets);
        config.put("experimental_parameters", experimentalParameters);
        
        config.put("dataset_source", datasetSource);
        config.put("workload_type", workloadType);
        config.put("scenario_category", scenarioCategory);
        config.put("applicable_algorithms", applicableAlgorithms);
        
        config.put("statistical_properties", statisticalProperties);
        config.put("replication_seed", replicationSeed);
        config.put("is_deterministic", isDeterministic);
        
        return config;
    }
    
    // Private helper methods
    
    private void validateSLARequirements() {
        if (maxResponseTime < 0) {
            throw new ExperimentException("Max response time cannot be negative: " + maxResponseTime);
        }
        
        if (minAvailability < 0 || minAvailability > 1) {
            throw new ExperimentException("Min availability must be between 0 and 1: " + minAvailability);
        }
        
        if (maxPowerConsumption < 0) {
            throw new ExperimentException("Max power consumption cannot be negative: " + maxPowerConsumption);
        }
    }
    
    private void validateResourceConfigurations() {
        if (hostConfigurations.isEmpty()) {
            throw new ExperimentException("At least one host configuration must be defined");
        }
        
        if (vmConfigurations.isEmpty()) {
            throw new ExperimentException("At least one VM configuration must be defined");
        }
    }
    
    private void validatePerformanceTargets() {
        if (targetUtilization < 0 || targetUtilization > 1) {
            throw new ExperimentException("Target utilization must be between 0 and 1: " + targetUtilization);
        }
        
        if (maxSlaViolationRate < 0 || maxSlaViolationRate > 1) {
            throw new ExperimentException("Max SLA violation rate must be between 0 and 1: " + maxSlaViolationRate);
        }
        
        if (targetThroughput < 0) {
            throw new ExperimentException("Target throughput cannot be negative: " + targetThroughput);
        }
    }
    
    private double calculateResourceDiversity() {
        if (resourceDemandDistribution.isEmpty()) {
            return 0.0;
        }
        
        double sum = resourceDemandDistribution.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum == 0) {
            return 0.0;
        }
        
        double entropy = 0.0;
        for (double value : resourceDemandDistribution.values()) {
            if (value > 0) {
                double p = value / sum;
                entropy -= p * Math.log(p) / Math.log(2);
            }
        }
        
        return entropy / Math.log(resourceDemandDistribution.size()) / Math.log(2);
    }
    
    private double calculateWorkloadComplexity() {
        double complexity = 0.0;
        
        if (workloadCharacteristics != null) {
            complexity += 0.1; // Base complexity for having workload characteristics
        }
        
        complexity += arrivalRate * 0.001; // Higher arrival rates increase complexity
        complexity += Math.log(executionTime) * 0.01; // Longer execution times add complexity
        
        return Math.min(complexity, 1.0); // Cap at 1.0
    }
    
    private void applyModification(ExperimentalScenario variant, String key, Object value) {
        switch (key) {
            case "numberOfVms":
                variant.numberOfVms = (Integer) value;
                break;
            case "numberOfHosts":
                variant.numberOfHosts = (Integer) value;
                break;
            case "arrivalRate":
                variant.arrivalRate = (Double) value;
                break;
            case "executionTime":
                variant.executionTime = (Double) value;
                break;
            case "targetUtilization":
                variant.targetUtilization = (Double) value;
                break;
            case "maxSlaViolationRate":
                variant.maxSlaViolationRate = (Double) value;
                break;
            case "replicationSeed":
                variant.replicationSeed = (Integer) value;
                break;
            default:
                variant.experimentalParameters.put(key, value);
                break;
        }
    }
    
    // Getter and Setter methods
    
    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }
    
    public String getScenarioName() { return scenarioName; }
    public void setScenarioName(String scenarioName) { this.scenarioName = scenarioName; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Date getCreationTimestamp() { return creationTimestamp; }
    public void setCreationTimestamp(Date creationTimestamp) { this.creationTimestamp = creationTimestamp; }
    
    public WorkloadCharacteristics getWorkloadCharacteristics() { return workloadCharacteristics; }
    public void setWorkloadCharacteristics(WorkloadCharacteristics workloadCharacteristics) { 
        this.workloadCharacteristics = workloadCharacteristics; 
    }
    
    public int getNumberOfVms() { return numberOfVms; }
    public void setNumberOfVms(int numberOfVms) { this.numberOfVms = numberOfVms; }
    
    public int getNumberOfHosts() { return numberOfHosts; }
    public void setNumberOfHosts(int numberOfHosts) { this.numberOfHosts = numberOfHosts; }
    
    public double getArrivalRate() { return arrivalRate; }
    public void setArrivalRate(double arrivalRate) { this.arrivalRate = arrivalRate; }
    
    public double getExecutionTime() { return executionTime; }
    public void setExecutionTime(double executionTime) { this.executionTime = executionTime; }
    
    public Map<String, Double> getResourceDemandDistribution() { return resourceDemandDistribution; }
    public void setResourceDemandDistribution(Map<String, Double> resourceDemandDistribution) { 
        this.resourceDemandDistribution = resourceDemandDistribution; 
    }
    
    public List<HostConfiguration> getHostConfigurations() { return hostConfigurations; }
    public void setHostConfigurations(List<HostConfiguration> hostConfigurations) { 
        this.hostConfigurations = hostConfigurations; 
    }
    
    public List<VmConfiguration> getVmConfigurations() { return vmConfigurations; }
    public void setVmConfigurations(List<VmConfiguration> vmConfigurations) { 
        this.vmConfigurations = vmConfigurations; 
    }
    
    public NetworkConfiguration getNetworkConfiguration() { return networkConfiguration; }
    public void setNetworkConfiguration(NetworkConfiguration networkConfiguration) { 
        this.networkConfiguration = networkConfiguration; 
    }
    
    public PowerConfiguration getPowerConfiguration() { return powerConfiguration; }
    public void setPowerConfiguration(PowerConfiguration powerConfiguration) { 
        this.powerConfiguration = powerConfiguration; 
    }
    
    public Map<String, SLARequirement> getSlaRequirements() { return slaRequirements; }
    public void setSlaRequirements(Map<String, SLARequirement> slaRequirements) { 
        this.slaRequirements = slaRequirements; 
    }
    
    public double getMaxResponseTime() { return maxResponseTime; }
    public void setMaxResponseTime(double maxResponseTime) { this.maxResponseTime = maxResponseTime; }
    
    public double getMinAvailability() { return minAvailability; }
    public void setMinAvailability(double minAvailability) { this.minAvailability = minAvailability; }
    
    public double getMaxPowerConsumption() { return maxPowerConsumption; }
    public void setMaxPowerConsumption(double maxPowerConsumption) { 
        this.maxPowerConsumption = maxPowerConsumption; 
    }
    
    public Map<String, Double> getPerformanceTargets() { return performanceTargets; }
    public void setPerformanceTargets(Map<String, Double> performanceTargets) { 
        this.performanceTargets = performanceTargets; 
    }
    
    public double getTargetUtilization() { return targetUtilization; }
    public void setTargetUtilization(double targetUtilization) { this.targetUtilization = targetUtilization; }
    
    public double getMaxSlaViolationRate() { return maxSlaViolationRate; }
    public void setMaxSlaViolationRate(double maxSlaViolationRate) { 
        this.maxSlaViolationRate = maxSlaViolationRate; 
    }
    
    public double getTargetThroughput() { return targetThroughput; }
    public void setTargetThroughput(double targetThroughput) { this.targetThroughput = targetThroughput; }
    
    public String getDatasetSource() { return datasetSource; }
    public void setDatasetSource(String datasetSource) { this.datasetSource = datasetSource; }
    
    public String getWorkloadType() { return workloadType; }
    public void setWorkloadType(String workloadType) { this.workloadType = workloadType; }
    
    public String getScenarioCategory() { return scenarioCategory; }
    public void setScenarioCategory(String scenarioCategory) { this.scenarioCategory = scenarioCategory; }
    
    public Map<String, Object> getExperimentalParameters() { return experimentalParameters; }
    public void setExperimentalParameters(Map<String, Object> experimentalParameters) { 
        this.experimentalParameters = experimentalParameters; 
    }
    
    public Set<String> getApplicableAlgorithms() { return applicableAlgorithms; }
    public void setApplicableAlgorithms(Set<String> applicableAlgorithms) { 
        this.applicableAlgorithms = applicableAlgorithms; 
    }
    
    public Map<String, Object> getStatisticalProperties() { return statisticalProperties; }
    public void setStatisticalProperties(Map<String, Object> statisticalProperties) { 
        this.statisticalProperties = statisticalProperties; 
    }
    
    public int getReplicationSeed() { return replicationSeed; }
    public void setReplicationSeed(int replicationSeed) { this.replicationSeed = replicationSeed; }
    
    public boolean isDeterministic() { return isDeterministic; }
    public void setDeterministic(boolean isDeterministic) { this.isDeterministic = isDeterministic; }
    
    // Inner classes for configuration components
    
    public static class HostConfiguration {
        private int hostId;
        private double cpuCapacity;
        private double memoryCapacity;
        private double storageCapacity;
        private double networkBandwidth;
        private double powerConsumption;
        private String hostType;
        
        public HostConfiguration(int hostId, double cpuCapacity, double memoryCapacity, 
                               double storageCapacity, double networkBandwidth) {
            this.hostId = hostId;
            this.cpuCapacity = cpuCapacity;
            this.memoryCapacity = memoryCapacity;
            this.storageCapacity = storageCapacity;
            this.networkBandwidth = networkBandwidth;
        }
        
        // Getters and setters
        public int getHostId() { return hostId; }
        public void setHostId(int hostId) { this.hostId = hostId; }
        
        public double getCpuCapacity() { return cpuCapacity; }
        public void setCpuCapacity(double cpuCapacity) { this.cpuCapacity = cpuCapacity; }
        
        public double getMemoryCapacity() { return memoryCapacity; }
        public void setMemoryCapacity(double memoryCapacity) { this.memoryCapacity = memoryCapacity; }
        
        public double getStorageCapacity() { return storageCapacity; }
        public void setStorageCapacity(double storageCapacity) { this.storageCapacity = storageCapacity; }
        
        public double getNetworkBandwidth() { return networkBandwidth; }
        public void setNetworkBandwidth(double networkBandwidth) { this.networkBandwidth = networkBandwidth; }
        
        public double getPowerConsumption() { return powerConsumption; }
        public void setPowerConsumption(double powerConsumption) { this.powerConsumption = powerConsumption; }
        
        public String getHostType() { return hostType; }
        public void setHostType(String hostType) { this.hostType = hostType; }
    }
    
    public static class VmConfiguration {
        private int vmId;
        private double cpuRequirement;
        private double memoryRequirement;
        private double storageRequirement;
        private double networkRequirement;
        private String vmType;
        private double priority;
        
        public VmConfiguration(int vmId, double cpuRequirement, double memoryRequirement, 
                             double storageRequirement, double networkRequirement) {
            this.vmId = vmId;
            this.cpuRequirement = cpuRequirement;
            this.memoryRequirement = memoryRequirement;
            this.storageRequirement = storageRequirement;
            this.networkRequirement = networkRequirement;
        }
        
        // Getters and setters
        public int getVmId() { return vmId; }
        public void setVmId(int vmId) { this.vmId = vmId; }
        
        public double getCpuRequirement() { return cpuRequirement; }
        public void setCpuRequirement(double cpuRequirement) { this.cpuRequirement = cpuRequirement; }
        
        public double getMemoryRequirement() { return memoryRequirement; }
        public void setMemoryRequirement(double memoryRequirement) { this.memoryRequirement = memoryRequirement; }
        
        public double getStorageRequirement() { return storageRequirement; }
        public void setStorageRequirement(double storageRequirement) { this.storageRequirement = storageRequirement; }
        
        public double getNetworkRequirement() { return networkRequirement; }
        public void setNetworkRequirement(double networkRequirement) { this.networkRequirement = networkRequirement; }
        
        public String getVmType() { return vmType; }
        public void setVmType(String vmType) { this.vmType = vmType; }
        
        public double getPriority() { return priority; }
        public void setPriority(double priority) { this.priority = priority; }
    }
    
    public static class NetworkConfiguration {
        private double latency;
        private double bandwidth;
        private double packetLoss;
        private String topology;
        
        public NetworkConfiguration(double latency, double bandwidth, double packetLoss, String topology) {
            this.latency = latency;
            this.bandwidth = bandwidth;
            this.packetLoss = packetLoss;
            this.topology = topology;
        }
        
        // Getters and setters
        public double getLatency() { return latency; }
        public void setLatency(double latency) { this.latency = latency; }
        
        public double getBandwidth() { return bandwidth; }
        public void setBandwidth(double bandwidth) { this.bandwidth = bandwidth; }
        
        public double getPacketLoss() { return packetLoss; }
        public void setPacketLoss(double packetLoss) { this.packetLoss = packetLoss; }
        
        public String getTopology() { return topology; }
        public void setTopology(String topology) { this.topology = topology; }
    }
    
    public static class PowerConfiguration {
        private double basePowerConsumption;
        private double maxPowerConsumption;
        private String powerModel;
        
        public PowerConfiguration(double basePowerConsumption, double maxPowerConsumption, String powerModel) {
            this.basePowerConsumption = basePowerConsumption;
            this.maxPowerConsumption = maxPowerConsumption;
            this.powerModel = powerModel;
        }
        
        // Getters and setters
        public double getBasePowerConsumption() { return basePowerConsumption; }
        public void setBasePowerConsumption(double basePowerConsumption) { 
            this.basePowerConsumption = basePowerConsumption; 
        }
        
        public double getMaxPowerConsumption() { return maxPowerConsumption; }
        public void setMaxPowerConsumption(double maxPowerConsumption) { 
            this.maxPowerConsumption = maxPowerConsumption; 
        }
        
        public String getPowerModel() { return powerModel; }
        public void setPowerModel(String powerModel) { this.powerModel = powerModel; }
    }
    
    public static class SLARequirement {
        private String metricName;
        private double threshold;
        private String comparisonOperator;
        private double penalty;
        
        public SLARequirement(String metricName, double threshold, String comparisonOperator, double penalty) {
            this.metricName = metricName;
            this.threshold = threshold;
            this.comparisonOperator = comparisonOperator;
            this.penalty = penalty;
        }
        
        // Getters and setters
        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }
        
        public double getThreshold() { return threshold; }
        public void setThreshold(double threshold) { this.threshold = threshold; }
        
        public String getComparisonOperator() { return comparisonOperator; }
        public void setComparisonOperator(String comparisonOperator) { 
            this.comparisonOperator = comparisonOperator; 
        }
        
        public double getPenalty() { return penalty; }
        public void setPenalty(double penalty) { this.penalty = penalty; }
    }
}