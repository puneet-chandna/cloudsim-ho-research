package org.cloudbus.cloudsim.util;

import org.cloudbus.cloudsim.dataset.AzureTraceParser;
import org.cloudbus.cloudsim.dataset.DatasetLoader;
import org.cloudbus.cloudsim.dataset.WorkloadCharacteristics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import java.util.Collections;

/**
 * DatasetIntegration - Utility class for integrating real-world datasets
 * into the CloudSim research framework.
 * 
 * Research Objectives Addressed:
 * - Real-world dataset integration for validation
 * - Dataset preprocessing and validation
 * - Performance comparison across different dataset sources
 * 
 * Metrics Calculated:
 * - Dataset integrity metrics
 * - Preprocessing performance metrics
 * - Data quality assessment metrics
 * 
 * Statistical Methods:
 * - Data validation and integrity checks
 * - Preprocessing performance analysis
 * - Dataset characteristic analysis
 *  @author Puneet Chandna
 */
public class DatasetIntegration {
    
    private static final Logger logger = LoggerFactory.getLogger(DatasetIntegration.class);
    
    private final AzureTraceParser azureTraceParser;
    private final Map<String, WorkloadCharacteristics> datasetCache;
    
    // Dataset validation parameters
    private static final int MIN_DATASET_SIZE = 100;
    private static final int MAX_CACHE_SIZE = 10;
    
    public DatasetIntegration() {
        this.azureTraceParser = new AzureTraceParser();
        this.datasetCache = new ConcurrentHashMap<>();
    }
    
    /**
     * Load Azure traces with comprehensive validation
     * 
     * @param tracePath Path to Azure trace files
     * @return List of WorkloadCharacteristics from the traces
     * @throws ExperimentException if loading fails
     */
    public List<WorkloadCharacteristics> loadAzureTraces(String tracePath) {
        logger.info("Loading Azure traces from: {}", tracePath);
        try {
            if (!Files.exists(Paths.get(tracePath))) {
                throw new ExperimentException("Azure trace path does not exist: " + tracePath);
            }
            String cacheKey = generateCacheKey("azure", tracePath);
            if (datasetCache.containsKey(cacheKey)) {
                logger.info("Using cached Azure traces for path: {}", tracePath);
                return Collections.singletonList(datasetCache.get(cacheKey));
            }
            long startTime = System.currentTimeMillis();
            // Parse Azure VM specifications
            azureTraceParser.parseVMSpecificationTrace(tracePath); // just to trigger parsing
            // Create dummy CloudSim and DatacenterBroker for workload generation
            CloudSim cloudSim = new CloudSim();
            DatacenterBrokerSimple broker = new DatacenterBrokerSimple(cloudSim);
            // Generate CloudSimWorkload
            Object workloadObj = azureTraceParser.generateCloudSimWorkload(broker, 1.0);
            // Use reflection to get VMs and Cloudlets if needed, or cast to expected interface if available
            // But here, we know from previous code that workloadObj has getVms() and getCloudlets() methods
            // So we can use them via reflection or by casting to Object and calling methods
            // (If this fails, user should make CloudSimWorkload public)
            List<?> vms = null;
            List<?> cloudlets = null;
            try {
                vms = (List<?>) workloadObj.getClass().getMethod("getVms").invoke(workloadObj);
                cloudlets = (List<?>) workloadObj.getClass().getMethod("getCloudlets").invoke(workloadObj);
            } catch (Exception e) {
                throw new RuntimeException("Failed to extract VMs/Cloudlets from CloudSimWorkload", e);
            }
            // Create WorkloadCharacteristics
            WorkloadCharacteristics characteristics = new WorkloadCharacteristics();
            characteristics.setVms((List) vms);
            characteristics.setCloudlets((List) cloudlets);
            characteristics.setWorkloadType("AZURE");
            characteristics.calculateStatistics();
            long loadTime = System.currentTimeMillis() - startTime;
            validateAzureTraces(Collections.singletonList(characteristics));
            cacheDataset(cacheKey, characteristics);
            logger.info("Successfully loaded Azure trace workload in {} ms", loadTime);
            return Collections.singletonList(characteristics);
        } catch (Exception e) {
            logger.error("Failed to load Azure traces from: {}", tracePath, e);
            throw new ExperimentException("Failed to load Azure traces", e);
        }
    }
    
    /**
     * Validate dataset integrity with comprehensive checks
     * 
     * @param datasetPath Path to dataset files
     * @param datasetType Type of dataset (google, azure, synthetic)
     * @return true if dataset is valid, false otherwise
     * @throws ExperimentException if validation fails
     */
    public boolean validateDataset(String datasetPath, String datasetType) {
        logger.info("Validating dataset: {} of type: {}", datasetPath, datasetType);
        
        try {
            // Basic path validation
            Path path = Paths.get(datasetPath);
            if (!Files.exists(path)) {
                logger.error("Dataset path does not exist: {}", datasetPath);
                return false;
            }
            
            // Check file accessibility
            if (!Files.isReadable(path)) {
                logger.error("Dataset is not readable: {}", datasetPath);
                return false;
            }
            
            // Size validation
            long fileSize = Files.size(path);
            if (fileSize == 0) {
                logger.error("Dataset file is empty: {}", datasetPath);
                return false;
            }
            
            // Type-specific validation
            boolean isValid = false;
            switch (datasetType.toLowerCase()) {
                case "azure":
                    isValid = validateAzureDatasetFormat(datasetPath);
                    break;
                case "synthetic":
                    isValid = validateSyntheticDatasetFormat(datasetPath);
                    break;
                default:
                    logger.error("Unknown dataset type: {}", datasetType);
                    return false;
            }
            
            if (isValid) {
                logger.info("Dataset validation successful for: {}", datasetPath);
            } else {
                logger.error("Dataset validation failed for: {}", datasetPath);
            }
            
            return isValid;
            
        } catch (Exception e) {
            logger.error("Dataset validation error for: {}", datasetPath, e);
            throw new ExperimentException("Dataset validation failed", e);
        }
    }
    
    /**
     * Preprocess dataset for simulation with performance tracking
     * 
     * @param workloads List of workload characteristics to preprocess
     * @param preprocessingConfig Configuration for preprocessing
     * @return Preprocessed workload characteristics
     * @throws ExperimentException if preprocessing fails
     */
    public List<WorkloadCharacteristics> preprocessData(
            List<WorkloadCharacteristics> workloads, 
            Map<String, Object> preprocessingConfig) {
        
        logger.info("Preprocessing {} workloads with config: {}", workloads.size(), preprocessingConfig);
        
        try {
            long startTime = System.currentTimeMillis();
            List<WorkloadCharacteristics> preprocessedWorkloads = new ArrayList<>();
            
            for (WorkloadCharacteristics workload : workloads) {
                WorkloadCharacteristics preprocessed = preprocessSingleWorkload(workload, preprocessingConfig);
                preprocessedWorkloads.add(preprocessed);
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            // Validate preprocessed data
            validatePreprocessedData(preprocessedWorkloads);
            
            logger.info("Successfully preprocessed {} workloads in {} ms", 
                       preprocessedWorkloads.size(), processingTime);
            
            return preprocessedWorkloads;
            
        } catch (Exception e) {
            logger.error("Data preprocessing failed", e);
            throw new ExperimentException("Data preprocessing failed", e);
        }
    }
    
    /**
     * Get comprehensive dataset statistics
     * 
     * @param datasetPath Path to dataset
     * @param datasetType Type of dataset
     * @return Map containing dataset statistics
     */
    public Map<String, Object> getDatasetStatistics(String datasetPath, String datasetType) {
        logger.info("Calculating statistics for dataset: {}", datasetPath);
        
        Map<String, Object> statistics = new HashMap<>();
        
        try {
            // Basic file statistics
            Path path = Paths.get(datasetPath);
            statistics.put("file_size_bytes", Files.size(path));
            statistics.put("file_exists", Files.exists(path));
            statistics.put("file_readable", Files.isReadable(path));
            
            // Dataset-specific statistics
            switch (datasetType.toLowerCase()) {
                case "azure":
                    // No getTraceStatistics in AzureTraceParser, so just add basic stats
                    statistics.put("dataset_type", "azure");
                    break;
                case "synthetic":
                    addSyntheticDatasetStatistics(statistics, datasetPath);
                    break;
            }
            
            logger.info("Dataset statistics calculated for: {}", datasetPath);
            
        } catch (Exception e) {
            logger.error("Failed to calculate dataset statistics for: {}", datasetPath, e);
            statistics.put("error", e.getMessage());
        }
        
        return statistics;
    }
    
    // Private helper methods
    
    private void validateAzureTraces(List<WorkloadCharacteristics> workloads) {
        if (workloads.isEmpty()) {
            throw new ExperimentException("No workloads found in Azure traces");
        }
        
        for (WorkloadCharacteristics workload : workloads) {
            if (workload.getVmCount() < MIN_DATASET_SIZE) {
                throw new ExperimentException("Azure trace dataset too small: " + workload.getVmCount());
            }
        }
    }
    
    private boolean validateAzureDatasetFormat(String datasetPath) {
        try {
            // Basic validation for Azure datasets: file exists and is not empty
            Path path = Paths.get(datasetPath);
            return Files.exists(path) && Files.size(path) > 0;
        } catch (Exception e) {
            logger.error("Azure dataset format validation failed", e);
            return false;
        }
    }
    
    private boolean validateSyntheticDatasetFormat(String datasetPath) {
        try {
            // Basic validation for synthetic datasets
            return Files.exists(Paths.get(datasetPath)) && Files.size(Paths.get(datasetPath)) > 0;
        } catch (Exception e) {
            logger.error("Synthetic dataset format validation failed", e);
            return false;
        }
    }
    
    private WorkloadCharacteristics preprocessSingleWorkload(
            WorkloadCharacteristics workload, 
            Map<String, Object> config) {
        
        // Create preprocessed copy
        WorkloadCharacteristics preprocessed = new WorkloadCharacteristics(workload);
        
        // Apply preprocessing based on configuration
        if (config.containsKey("normalize_resources")) {
            preprocessed.normalizeResourceRequirements();
        }
        
        if (config.containsKey("filter_outliers")) {
            preprocessed.filterOutliers((Double) config.get("outlier_threshold"));
        }
        
        if (config.containsKey("scale_factor")) {
            preprocessed.scaleWorkload((Double) config.get("scale_factor"));
        }
        
        return preprocessed;
    }
    
    private void validatePreprocessedData(List<WorkloadCharacteristics> preprocessedWorkloads) {
        for (WorkloadCharacteristics workload : preprocessedWorkloads) {
            if (workload.getVmCount() == 0) {
                throw new ExperimentException("Preprocessed workload has no VMs");
            }
            
            if (workload.getTotalCpuRequirement() <= 0) {
                throw new ExperimentException("Preprocessed workload has invalid CPU requirements");
            }
            
            if (workload.getTotalMemoryRequirement() <= 0) {
                throw new ExperimentException("Preprocessed workload has invalid memory requirements");
            }
        }
    }
    
    private String generateCacheKey(String datasetType, String datasetPath) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String input = datasetType + ":" + datasetPath;
            byte[] hash = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple key
            return datasetType + "_" + datasetPath.hashCode();
        }
    }
    
    private void cacheDataset(String cacheKey, WorkloadCharacteristics workload) {
        if (datasetCache.size() >= MAX_CACHE_SIZE) {
            // Remove oldest entry
            String oldestKey = datasetCache.keySet().iterator().next();
            datasetCache.remove(oldestKey);
        }
        datasetCache.put(cacheKey, workload);
    }
    
    private void addSyntheticDatasetStatistics(Map<String, Object> statistics, String datasetPath) {
        try {
            // Basic statistics for synthetic datasets
            Path path = Paths.get(datasetPath);
            statistics.put("line_count", Files.lines(path).count());
            statistics.put("dataset_type", "synthetic");
        } catch (Exception e) {
            logger.error("Failed to get synthetic dataset statistics", e);
            statistics.put("synthetic_stats_error", e.getMessage());
        }
    }
    
    /**
     * Clear dataset cache
     */
    public void clearCache() {
        datasetCache.clear();
        logger.info("Dataset cache cleared");
    }
    
    /**
     * Get cache statistics
     * 
     * @return Map containing cache statistics
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cache_size", datasetCache.size());
        stats.put("max_cache_size", MAX_CACHE_SIZE);
        stats.put("cache_keys", new ArrayList<>(datasetCache.keySet()));
        return stats;
    }
}