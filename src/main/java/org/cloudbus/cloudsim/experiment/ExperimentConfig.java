package org.cloudbus.cloudsim.experiment;

import org.cloudbus.cloudsim.algorithm.HippopotamusParameters;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Configuration for experiments with comprehensive parameter management
 * Supporting research objectives: parameter sensitivity analysis and reproducibility
 * @author Puneet Chandna
 */
public class ExperimentConfig {
    
    @JsonProperty("experiment_name")
    private String experimentName;
    
    @JsonProperty("algorithm_type")
    private String algorithmType;
    
    @JsonProperty("algorithm_parameters")
    private Map<String, Object> algorithmParameters;
    
    @JsonProperty("scenario_type")
    private String scenarioType;
    
    @JsonProperty("dataset_name")
    private String datasetName;
    
    @JsonProperty("vm_count")
    private int vmCount;
    
    @JsonProperty("host_count")
    private int hostCount;
    
    @JsonProperty("measurement_settings")
    private MeasurementSettings measurementSettings;
    
    @JsonProperty("output_settings")
    private OutputSettings outputSettings;
    
    @JsonProperty("replication_settings")
    private ReplicationSettings replicationSettings;
    
    @JsonProperty("sla_requirements")
    private SLARequirements slaRequirements;
    
    @JsonProperty("random_seed")
    private long randomSeed;
    
    @JsonProperty("timeout_seconds")
    private int timeoutSeconds;
    
    // Nested configuration classes
    public static class MeasurementSettings {
        @JsonProperty("sample_interval_ms")
        private int sampleIntervalMs = 1000;
        
        @JsonProperty("metrics_to_collect")
        private List<String> metricsToCollect = Arrays.asList(
            "resource_utilization", "power_consumption", "sla_violations",
            "response_time", "throughput", "cost", "migration_count"
        );
        
        @JsonProperty("enable_detailed_logging")
        private boolean enableDetailedLogging = false;
        
        // Getters and setters
        public int getSampleIntervalMs() { return sampleIntervalMs; }
        public void setSampleIntervalMs(int sampleIntervalMs) { 
            this.sampleIntervalMs = sampleIntervalMs; 
        }
        
        public List<String> getMetricsToCollect() { return metricsToCollect; }
        public void setMetricsToCollect(List<String> metricsToCollect) { 
            this.metricsToCollect = metricsToCollect; 
        }
        
        public boolean isEnableDetailedLogging() { return enableDetailedLogging; }
        public void setEnableDetailedLogging(boolean enableDetailedLogging) { 
            this.enableDetailedLogging = enableDetailedLogging; 
        }
    }
    
    public static class OutputSettings {
        @JsonProperty("output_directory")
        private String outputDirectory = "results/";
        
        @JsonProperty("save_raw_data")
        private boolean saveRawData = true;
        
        @JsonProperty("generate_charts")
        private boolean generateCharts = true;
        
        @JsonProperty("export_formats")
        private List<String> exportFormats = Arrays.asList("csv", "json", "xlsx");
        
        // Getters and setters
        public String getOutputDirectory() { return outputDirectory; }
        public void setOutputDirectory(String outputDirectory) { 
            this.outputDirectory = outputDirectory; 
        }
        
        public boolean isSaveRawData() { return saveRawData; }
        public void setSaveRawData(boolean saveRawData) { 
            this.saveRawData = saveRawData; 
        }
        
        public boolean isGenerateCharts() { return generateCharts; }
        public void setGenerateCharts(boolean generateCharts) { 
            this.generateCharts = generateCharts; 
        }
        
        public List<String> getExportFormats() { return exportFormats; }
        public void setExportFormats(List<String> exportFormats) { 
            this.exportFormats = exportFormats; 
        }
    }
    
    public static class ReplicationSettings {
        @JsonProperty("number_of_replications")
        private int numberOfReplications = 30;
        
        @JsonProperty("enable_parallel_execution")
        private boolean enableParallelExecution = true;
        
        @JsonProperty("max_parallel_threads")
        private int maxParallelThreads = Runtime.getRuntime().availableProcessors();
        
        // Getters and setters
        public int getNumberOfReplications() { return numberOfReplications; }
        public void setNumberOfReplications(int numberOfReplications) { 
            this.numberOfReplications = numberOfReplications; 
        }
        
        public boolean isEnableParallelExecution() { return enableParallelExecution; }
        public void setEnableParallelExecution(boolean enableParallelExecution) { 
            this.enableParallelExecution = enableParallelExecution; 
        }
        
        public int getMaxParallelThreads() { return maxParallelThreads; }
        public void setMaxParallelThreads(int maxParallelThreads) { 
            this.maxParallelThreads = maxParallelThreads; 
        }
    }
    
    public static class SLARequirements {
        @JsonProperty("max_response_time_ms")
        private double maxResponseTimeMs = 100.0;
        
        @JsonProperty("min_availability_percent")
        private double minAvailabilityPercent = 99.9;
        
        @JsonProperty("max_migration_time_ms")
        private double maxMigrationTimeMs = 50.0;
        
        // Getters and setters
        public double getMaxResponseTimeMs() { return maxResponseTimeMs; }
        public void setMaxResponseTimeMs(double maxResponseTimeMs) { 
            this.maxResponseTimeMs = maxResponseTimeMs; 
        }
        
        public double getMinAvailabilityPercent() { return minAvailabilityPercent; }
        public void setMinAvailabilityPercent(double minAvailabilityPercent) { 
            this.minAvailabilityPercent = minAvailabilityPercent; 
        }
        
        public double getMaxMigrationTimeMs() { return maxMigrationTimeMs; }
        public void setMaxMigrationTimeMs(double maxMigrationTimeMs) { 
            this.maxMigrationTimeMs = maxMigrationTimeMs; 
        }
    }
    
    // Constructors
    public ExperimentConfig() {
        this.experimentName = "default_experiment";
        this.algorithmType = "HippopotamusOptimization";
        this.algorithmParameters = new HashMap<>();
        this.measurementSettings = new MeasurementSettings();
        this.outputSettings = new OutputSettings();
        this.replicationSettings = new ReplicationSettings();
        this.slaRequirements = new SLARequirements();
        this.randomSeed = System.currentTimeMillis();
        this.timeoutSeconds = 3600; // 1 hour default
    }
    
    // Factory methods
    public static ExperimentConfig loadFromYAML(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(new File(filePath), ExperimentConfig.class);
    }
    
    public static ExperimentConfig createDefaultConfig() {
        ExperimentConfig config = new ExperimentConfig();
        
        // Set default HO parameters
        config.algorithmParameters.put("population_size", 50);
        config.algorithmParameters.put("max_iterations", 200);
        config.algorithmParameters.put("convergence_threshold", 0.001);
        config.algorithmParameters.put("exploration_rate", 0.3);
        config.algorithmParameters.put("exploitation_rate", 0.7);
        
        return config;
    }
    
    // Utility methods
    public HippopotamusParameters toHippopotamusParameters() {
        HippopotamusParameters params = new HippopotamusParameters();
        
        if (algorithmParameters.containsKey("population_size")) {
            params.setPopulationSize((Integer) algorithmParameters.get("population_size"));
        }
        if (algorithmParameters.containsKey("max_iterations")) {
            params.setMaxIterations((Integer) algorithmParameters.get("max_iterations"));
        }
        if (algorithmParameters.containsKey("convergence_threshold")) {
            params.setConvergenceThreshold(
                ((Number) algorithmParameters.get("convergence_threshold")).doubleValue()
            );
        }
        
        return params;
    }
    
    public void saveToYAML(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), this);
    }
    
    public ExperimentConfig deepCopy() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(this);
            return mapper.readValue(json, ExperimentConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deep copy configuration", e);
        }
    }
    
    // Validation method
    public void validate() {
        if (experimentName == null || experimentName.isEmpty()) {
            throw new IllegalArgumentException("Experiment name cannot be empty");
        }
        if (vmCount <= 0) {
            throw new IllegalArgumentException("VM count must be positive");
        }
        if (hostCount <= 0) {
            throw new IllegalArgumentException("Host count must be positive");
        }
        if (replicationSettings.getNumberOfReplications() <= 0) {
            throw new IllegalArgumentException("Number of replications must be positive");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
    }
    
    // Getters and setters
    public String getExperimentName() { return experimentName; }
    public void setExperimentName(String experimentName) { 
        this.experimentName = experimentName; 
    }
    
    public String getAlgorithmType() { return algorithmType; }
    public void setAlgorithmType(String algorithmType) { 
        this.algorithmType = algorithmType; 
    }
    
    public Map<String, Object> getAlgorithmParameters() { return algorithmParameters; }
    public void setAlgorithmParameters(Map<String, Object> algorithmParameters) { 
        this.algorithmParameters = algorithmParameters; 
    }
    
    public String getScenarioType() { return scenarioType; }
    public void setScenarioType(String scenarioType) { 
        this.scenarioType = scenarioType; 
    }
    
    public String getDatasetName() { return datasetName; }
    public void setDatasetName(String datasetName) { 
        this.datasetName = datasetName; 
    }
    
    public int getVmCount() { return vmCount; }
    public void setVmCount(int vmCount) { 
        this.vmCount = vmCount; 
    }
    
    public int getHostCount() { return hostCount; }
    public void setHostCount(int hostCount) { 
        this.hostCount = hostCount; 
    }
    
    public MeasurementSettings getMeasurementSettings() { return measurementSettings; }
    public void setMeasurementSettings(MeasurementSettings measurementSettings) { 
        this.measurementSettings = measurementSettings; 
    }
    
    public OutputSettings getOutputSettings() { return outputSettings; }
    public void setOutputSettings(OutputSettings outputSettings) { 
        this.outputSettings = outputSettings; 
    }
    
    public ReplicationSettings getReplicationSettings() { return replicationSettings; }
    public void setReplicationSettings(ReplicationSettings replicationSettings) { 
        this.replicationSettings = replicationSettings; 
    }
    
    public SLARequirements getSlaRequirements() { return slaRequirements; }
    public void setSlaRequirements(SLARequirements slaRequirements) { 
        this.slaRequirements = slaRequirements; 
    }
    
    public long getRandomSeed() { return randomSeed; }
    public void setRandomSeed(long randomSeed) { 
        this.randomSeed = randomSeed; 
    }
    
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { 
        this.timeoutSeconds = timeoutSeconds; 
    }
    
    @Override
    public String toString() {
        return String.format("ExperimentConfig[name=%s, algorithm=%s, vms=%d, hosts=%d, replications=%d]",
            experimentName, algorithmType, vmCount, hostCount, 
            replicationSettings.getNumberOfReplications());
    }
}