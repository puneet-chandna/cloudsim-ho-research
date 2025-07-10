package org.cloudbus.cloudsim.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.cloudbus.cloudsim.util.ExperimentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages experimental configurations for the CloudSim HO Research Framework.
 * Handles loading, validation, saving, and generation of configuration sets
 * for reproducible experiments.
 *
 * @author Puneet Chandna
 * @since 1.0
 */
public class ConfigurationManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationManager.class);
    
    private static final String DEFAULT_CONFIG_DIR = "src/main/resources/config";
    private static final String EXPERIMENT_CONFIG_FILE = "experiment_config.yaml";
    private static final String ALGORITHM_PARAMS_FILE = "algorithm_parameters.yaml";
    
    private final ObjectMapper yamlMapper;
    private final Map<String, Object> configCache;
    private final Path configDirectory;
    
    /**
     * Constructs a ConfigurationManager with default configuration directory.
     */
    public ConfigurationManager() {
        this(Paths.get(DEFAULT_CONFIG_DIR));
    }
    
    /**
     * Constructs a ConfigurationManager with specified configuration directory.
     *
     * @param configDirectory the directory containing configuration files
     */
    public ConfigurationManager(Path configDirectory) {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.configCache = new ConcurrentHashMap<>();
        this.configDirectory = configDirectory;
        
        try {
            Files.createDirectories(configDirectory);
        } catch (IOException e) {
            throw new ExperimentException("Failed to create configuration directory", e);
        }
    }
    
    /**
     * Loads experiment configuration from YAML file.
     *
     * @return map containing experiment configuration
     */
    public Map<String, Object> loadConfiguration() {
        logger.info("Loading experiment configuration from: {}", configDirectory);
        
        try {
            // Load main experiment configuration
            Path experimentConfigPath = configDirectory.resolve(EXPERIMENT_CONFIG_FILE);
            Map<String, Object> experimentConfig = loadYamlFile(experimentConfigPath);
            
            // Load algorithm parameters
            Path algorithmParamsPath = configDirectory.resolve(ALGORITHM_PARAMS_FILE);
            Map<String, Object> algorithmParams = loadYamlFile(algorithmParamsPath);
            
            // Merge configurations
            Map<String, Object> mergedConfig = new HashMap<>();
            mergedConfig.put("experiments", experimentConfig);
            mergedConfig.put("algorithmParameters", algorithmParams);
            
            // Cache the configuration
            configCache.putAll(mergedConfig);
            
            logger.info("Configuration loaded successfully");
            return mergedConfig;
            
        } catch (IOException e) {
            throw new ExperimentException("Failed to load configuration", e);
        }
    }
    
    /**
     * Validates configuration parameters.
     *
     * @param config the configuration to validate
     * @return true if configuration is valid
     */
    public boolean validateConfiguration(Map<String, Object> config) {
        logger.info("Validating configuration parameters");
        
        try {
            // Validate required sections
            validateRequiredSections(config);
            
            // Validate algorithm parameters
            validateAlgorithmParameters(config);
            
            // Validate experiment settings
            validateExperimentSettings(config);
            
            // Validate dataset configurations
            validateDatasetConfigurations(config);
            
            // Validate metric configurations
            validateMetricConfigurations(config);
            
            logger.info("Configuration validation successful");
            return true;
            
        } catch (Exception e) {
            logger.error("Configuration validation failed: {}", e.getMessage());
            throw new ExperimentException("Configuration validation failed", e);
        }
    }
    
    /**
     * Saves configuration for reproducibility.
     *
     * @param config the configuration to save
     * @param fileName the name of the file to save to
     */
    public void saveConfiguration(Map<String, Object> config, String fileName) {
        logger.info("Saving configuration to: {}", fileName);
        
        try {
            Path outputPath = configDirectory.resolve(fileName);
            yamlMapper.writeValue(outputPath.toFile(), config);
            
            logger.info("Configuration saved successfully to: {}", outputPath);
            
        } catch (IOException e) {
            throw new ExperimentException("Failed to save configuration", e);
        }
    }
    
    /**
     * Generates configuration sets for parameter sweep experiments.
     *
     * @return list of configuration sets
     */
    public List<Map<String, Object>> generateConfigurationSets() {
        logger.info("Generating configuration sets for parameter sweep");
        
        List<Map<String, Object>> configSets = new ArrayList<>();
        
        try {
            Map<String, Object> baseConfig = loadConfiguration();
            Map<String, Object> experiments = (Map<String, Object>) baseConfig.get("experiments");
            
            // Extract parameter variations
            List<Map<String, Object>> algorithms = (List<Map<String, Object>>) 
                experiments.get("algorithms");
            
            for (Map<String, Object> algorithm : algorithms) {
                String algorithmName = (String) algorithm.get("name");
                Map<String, List<Object>> parameters = (Map<String, List<Object>>) 
                    algorithm.get("parameters");
                
                // Generate all parameter combinations
                List<Map<String, Object>> combinations = generateParameterCombinations(parameters);
                
                // Create configuration for each combination
                for (Map<String, Object> combination : combinations) {
                    Map<String, Object> configSet = new HashMap<>(baseConfig);
                    configSet.put("algorithmName", algorithmName);
                    configSet.put("parameters", combination);
                    configSets.add(configSet);
                }
            }
            
            logger.info("Generated {} configuration sets", configSets.size());
            return configSets;
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to generate configuration sets", e);
        }
    }
    
    /**
     * Gets a specific configuration value.
     *
     * @param key the configuration key
     * @return the configuration value
     */
    public Object getConfigValue(String key) {
        if (configCache.isEmpty()) {
            loadConfiguration();
        }
        
        return getNestedValue(configCache, key);
    }
    
    /**
     * Sets a specific configuration value.
     *
     * @param key the configuration key
     * @param value the configuration value
     */
    public void setConfigValue(String key, Object value) {
        if (configCache.isEmpty()) {
            loadConfiguration();
        }
        
        setNestedValue(configCache, key, value);
    }
    
    // Private helper methods
    
    private Map<String, Object> loadYamlFile(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            logger.warn("Configuration file not found: {}, creating default", filePath);
            createDefaultConfigFile(filePath);
        }
        
        return yamlMapper.readValue(filePath.toFile(), Map.class);
    }
    
    private void createDefaultConfigFile(Path filePath) throws IOException {
        Map<String, Object> defaultConfig = createDefaultConfiguration(filePath.getFileName().toString());
        yamlMapper.writeValue(filePath.toFile(), defaultConfig);
    }
    
    private Map<String, Object> createDefaultConfiguration(String fileName) {
        Map<String, Object> defaultConfig = new HashMap<>();
        
        if (fileName.equals(EXPERIMENT_CONFIG_FILE)) {
            // Create default experiment configuration
            defaultConfig.put("algorithms", createDefaultAlgorithms());
            defaultConfig.put("datasets", Arrays.asList("google_traces", "azure_traces", "synthetic_workloads"));
            defaultConfig.put("metrics", Arrays.asList("resource_utilization", "power_consumption", 
                "sla_violations", "response_time", "throughput"));
            defaultConfig.put("scalability_tests", createDefaultScalabilityTests());
            defaultConfig.put("statistical_tests", createDefaultStatisticalTests());
        } else if (fileName.equals(ALGORITHM_PARAMS_FILE)) {
            // Create default algorithm parameters
            defaultConfig.put("hippopotamus", createDefaultHippopotamusParams());
            defaultConfig.put("genetic_algorithm", createDefaultGeneticParams());
            defaultConfig.put("particle_swarm", createDefaultPSOParams());
        }
        
        return defaultConfig;
    }
    
    private List<Map<String, Object>> createDefaultAlgorithms() {
        List<Map<String, Object>> algorithms = new ArrayList<>();
        
        Map<String, Object> hippopotamus = new HashMap<>();
        hippopotamus.put("name", "HippopotamusOptimization");
        Map<String, List<Object>> hoParams = new HashMap<>();
        hoParams.put("population_size", Arrays.asList(20, 50, 100));
        hoParams.put("max_iterations", Arrays.asList(100, 200, 500));
        hoParams.put("convergence_threshold", Arrays.asList(0.001));
        hippopotamus.put("parameters", hoParams);
        algorithms.add(hippopotamus);
        
        return algorithms;
    }
    
    private Map<String, Object> createDefaultScalabilityTests() {
        Map<String, Object> scalability = new HashMap<>();
        scalability.put("vm_counts", Arrays.asList(100, 500, 1000, 2000, 5000));
        scalability.put("host_counts", Arrays.asList(10, 50, 100, 200, 500));
        return scalability;
    }
    
    private Map<String, Object> createDefaultStatisticalTests() {
        Map<String, Object> statistical = new HashMap<>();
        statistical.put("confidence_level", 0.95);
        statistical.put("replications", 30);
        statistical.put("significance_level", 0.05);
        return statistical;
    }
    
    private Map<String, Object> createDefaultHippopotamusParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("alpha", 0.5);
        params.put("beta", 0.5);
        params.put("gamma", 0.3);
        params.put("delta", 0.7);
        return params;
    }
    
    private Map<String, Object> createDefaultGeneticParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("crossover_rate", 0.8);
        params.put("mutation_rate", 0.1);
        params.put("elitism_rate", 0.1);
        return params;
    }
    
    private Map<String, Object> createDefaultPSOParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("inertia_weight", 0.7);
        params.put("cognitive_coefficient", 2.0);
        params.put("social_coefficient", 2.0);
        return params;
    }
    
    private void validateRequiredSections(Map<String, Object> config) {
        List<String> requiredSections = Arrays.asList("experiments", "algorithmParameters");
        
        for (String section : requiredSections) {
            if (!config.containsKey(section)) {
                throw new ExperimentException("Missing required configuration section: " + section);
            }
        }
    }
    
    private void validateAlgorithmParameters(Map<String, Object> config) {
        Map<String, Object> algorithmParams = (Map<String, Object>) config.get("algorithmParameters");
        
        if (algorithmParams == null || algorithmParams.isEmpty()) {
            throw new ExperimentException("Algorithm parameters cannot be empty");
        }
        
        // Validate specific algorithm parameters
        for (Map.Entry<String, Object> entry : algorithmParams.entrySet()) {
            Map<String, Object> params = (Map<String, Object>) entry.getValue();
            validateParameterRanges(entry.getKey(), params);
        }
    }
    
    private void validateParameterRanges(String algorithmName, Map<String, Object> params) {
        // Validate common parameter ranges
        for (Map.Entry<String, Object> param : params.entrySet()) {
            String paramName = param.getKey();
            Object value = param.getValue();
            
            if (value instanceof Number) {
                double numValue = ((Number) value).doubleValue();
                
                // Check common parameter constraints
                if (paramName.contains("rate") && (numValue < 0 || numValue > 1)) {
                    throw new ExperimentException(
                        String.format("Parameter %s.%s must be between 0 and 1", algorithmName, paramName));
                }
                
                if (paramName.contains("size") && numValue <= 0) {
                    throw new ExperimentException(
                        String.format("Parameter %s.%s must be positive", algorithmName, paramName));
                }
            }
        }
    }
    
    private void validateExperimentSettings(Map<String, Object> config) {
        Map<String, Object> experiments = (Map<String, Object>) config.get("experiments");
        
        // Validate statistical test settings
        Map<String, Object> statisticalTests = (Map<String, Object>) experiments.get("statistical_tests");
        if (statisticalTests != null) {
            double confidenceLevel = ((Number) statisticalTests.get("confidence_level")).doubleValue();
            if (confidenceLevel <= 0 || confidenceLevel >= 1) {
                throw new ExperimentException("Confidence level must be between 0 and 1");
            }
            
            int replications = ((Number) statisticalTests.get("replications")).intValue();
            if (replications < 2) {
                throw new ExperimentException("Number of replications must be at least 2");
            }
        }
    }
    
    private void validateDatasetConfigurations(Map<String, Object> config) {
        Map<String, Object> experiments = (Map<String, Object>) config.get("experiments");
        List<String> datasets = (List<String>) experiments.get("datasets");
        
        if (datasets == null || datasets.isEmpty()) {
            throw new ExperimentException("At least one dataset must be specified");
        }
    }
    
    private void validateMetricConfigurations(Map<String, Object> config) {
        Map<String, Object> experiments = (Map<String, Object>) config.get("experiments");
        List<String> metrics = (List<String>) experiments.get("metrics");
        
        if (metrics == null || metrics.isEmpty()) {
            throw new ExperimentException("At least one metric must be specified");
        }
        
        // Validate metric names
        List<String> validMetrics = Arrays.asList(
            "resource_utilization", "power_consumption", "sla_violations",
            "response_time", "throughput", "cost", "migration_count"
        );
        
        for (String metric : metrics) {
            if (!validMetrics.contains(metric)) {
                logger.warn("Unknown metric specified: {}", metric);
            }
        }
    }
    
    private List<Map<String, Object>> generateParameterCombinations(Map<String, List<Object>> parameters) {
        List<Map<String, Object>> combinations = new ArrayList<>();
        generateCombinationsRecursive(parameters, new HashMap<>(), 
            new ArrayList<>(parameters.keySet()), 0, combinations);
        return combinations;
    }
    
    private void generateCombinationsRecursive(Map<String, List<Object>> parameters,
                                               Map<String, Object> current,
                                               List<String> keys,
                                               int index,
                                               List<Map<String, Object>> result) {
        if (index == keys.size()) {
            result.add(new HashMap<>(current));
            return;
        }
        
        String key = keys.get(index);
        List<Object> values = parameters.get(key);
        
        for (Object value : values) {
            current.put(key, value);
            generateCombinationsRecursive(parameters, current, keys, index + 1, result);
        }
    }
    
    private Object getNestedValue(Map<String, Object> map, String key) {
        String[] parts = key.split("\\.");
        Object current = map;
        
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }
        
        return current;
    }
    
    private void setNestedValue(Map<String, Object> map, String key, Object value) {
        String[] parts = key.split("\\.");
        Map<String, Object> current = map;
        
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (!current.containsKey(part)) {
                current.put(part, new HashMap<String, Object>());
            }
            current = (Map<String, Object>) current.get(part);
        }
        
        current.put(parts[parts.length - 1], value);
    }
}