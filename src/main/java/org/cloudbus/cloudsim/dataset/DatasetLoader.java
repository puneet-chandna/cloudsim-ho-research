package org.cloudbus.cloudsim.dataset;

import org.cloudbus.cloudsim.util.ExperimentException;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.ValidationUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * Generic dataset loader with caching and validation capabilities.
 * Supports multiple dataset formats and provides statistics.
 * Used by simulation and experiment packages for data loading.
 * @author Puneet Chandna
 */
public class DatasetLoader {
    private static final Logger logger = LoggerFactory.getLogger(DatasetLoader.class);
    
    // Dataset cache for multiple experiments
    private static final Map<String, List<CSVRecord>> datasetCache = new ConcurrentHashMap<>();
    private static final Map<String, DatasetStatistics> statisticsCache = new ConcurrentHashMap<>();
    
    // Supported file extensions
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".csv", ".tsv", ".gz");
    
    /**
     * Generic dataset loader that handles various formats.
     * Implements caching for performance optimization.
     * 
     * @param datasetPath Path to the dataset file
     * @return List of CSV records from the dataset
     * @throws ExperimentException if dataset loading fails
     */
    public static List<CSVRecord> loadDataset(String datasetPath) {
        try {
            LoggingManager.logInfo("Loading dataset from: " + datasetPath);
            
            // Check cache first
            if (datasetCache.containsKey(datasetPath)) {
                LoggingManager.logInfo("Dataset loaded from cache: " + datasetPath);
                return datasetCache.get(datasetPath);
            }
            
            // Validate dataset path
            validateDatasetPath(datasetPath);
            
            // Load dataset based on file extension
            List<CSVRecord> records = loadDatasetFromFile(datasetPath);
            
            // Validate dataset format
            validateDatasetFormat(records, datasetPath);
            
            // Cache the dataset
            cacheDataset(datasetPath, records);
            
            LoggingManager.logInfo("Successfully loaded dataset: " + datasetPath + 
                                 " (" + records.size() + " records)");
            
            return records;
            
        } catch (IOException e) {
            throw new ExperimentException("Failed to load dataset: " + datasetPath, e);
        } catch (Exception e) {
            throw new ExperimentException("Unexpected error loading dataset: " + datasetPath, e);
        }
    }
    
    /**
     * Validate dataset format and structure.
     * Ensures dataset meets minimum requirements for simulation.
     * 
     * @param records Dataset records to validate
     * @param datasetPath Path for error reporting
     * @throws ExperimentException if validation fails
     */
    public static void validateDatasetFormat(List<CSVRecord> records, String datasetPath) {
        try {
            if (records == null || records.isEmpty()) {
                throw new ExperimentException("Dataset is empty: " + datasetPath);
            }
            
            // Get header from first record
            CSVRecord headerRecord = records.get(0);
            Set<String> headers = new HashSet<>();
            
            for (int i = 0; i < headerRecord.size(); i++) {
                String header = headerRecord.get(i).trim().toLowerCase();
                if (header.isEmpty()) {
                    throw new ExperimentException("Empty header found in dataset: " + datasetPath);
                }
                
                if (headers.contains(header)) {
                    throw new ExperimentException("Duplicate header found: " + header + 
                                                " in dataset: " + datasetPath);
                }
                headers.add(header);
            }
            
            // Validate minimum required columns based on dataset type
            validateRequiredColumns(headers, datasetPath);
            
            // Validate data consistency
            validateDataConsistency(records, datasetPath);
            
            LoggingManager.logInfo("Dataset format validation successful: " + datasetPath);
            
        } catch (ExperimentException e) {
            throw e;
        } catch (Exception e) {
            throw new ExperimentException("Dataset format validation failed: " + datasetPath, e);
        }
    }
    
    /**
     * Cache dataset for multiple experiments.
     * Implements LRU eviction when cache size exceeds limit.
     * 
     * @param datasetPath Path to the dataset
     * @param records Dataset records to cache
     */
    public static void cacheDataset(String datasetPath, List<CSVRecord> records) {
        try {
            // Check cache size limit (max 10 datasets)
            if (datasetCache.size() >= 10) {
                // Remove oldest entry (simple FIFO for now)
                String oldestKey = datasetCache.keySet().iterator().next();
                datasetCache.remove(oldestKey);
                statisticsCache.remove(oldestKey);
                LoggingManager.logInfo("Evicted cached dataset: " + oldestKey);
            }
            
            // Cache the dataset
            datasetCache.put(datasetPath, new ArrayList<>(records));
            
            // Generate and cache statistics
            DatasetStatistics stats = calculateDatasetStatistics(records, datasetPath);
            statisticsCache.put(datasetPath, stats);
            
            LoggingManager.logInfo("Dataset cached successfully: " + datasetPath);
            
        } catch (Exception e) {
            LoggingManager.logError("Failed to cache dataset: " + datasetPath, e);
            // Don't throw exception - caching failure shouldn't stop processing
        }
    }
    
    /**
     * Get comprehensive dataset statistics.
     * Calculates record count, column statistics, and data quality metrics.
     * 
     * @param datasetPath Path to the dataset
     * @return DatasetStatistics object with comprehensive metrics
     * @throws ExperimentException if statistics calculation fails
     */
    public static DatasetStatistics getDatasetStatistics(String datasetPath) {
        try {
            // Check if statistics are already cached
            if (statisticsCache.containsKey(datasetPath)) {
                return statisticsCache.get(datasetPath);
            }
            
            // Load dataset if not cached
            List<CSVRecord> records = loadDataset(datasetPath);
            
            // Calculate statistics
            DatasetStatistics stats = calculateDatasetStatistics(records, datasetPath);
            statisticsCache.put(datasetPath, stats);
            
            return stats;
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to calculate dataset statistics: " + datasetPath, e);
        }
    }
    
    /**
     * Clear dataset cache to free memory.
     * Useful between experiment batches.
     */
    public static void clearCache() {
        datasetCache.clear();
        statisticsCache.clear();
        LoggingManager.logInfo("Dataset cache cleared");
    }
    
    /**
     * Get cache status information.
     * 
     * @return Map with cache status information
     */
    public static Map<String, Object> getCacheStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("cached_datasets", datasetCache.size());
        status.put("cached_statistics", statisticsCache.size());
        status.put("dataset_paths", new ArrayList<>(datasetCache.keySet()));
        return status;
    }
    
    // Private helper methods
    
    private static void validateDatasetPath(String datasetPath) {
        if (datasetPath == null || datasetPath.trim().isEmpty()) {
            throw new ExperimentException("Dataset path cannot be null or empty");
        }
        
        Path path = Paths.get(datasetPath);
        if (!Files.exists(path)) {
            throw new ExperimentException("Dataset file does not exist: " + datasetPath);
        }
        
        if (!Files.isReadable(path)) {
            throw new ExperimentException("Dataset file is not readable: " + datasetPath);
        }
        
        // Check file extension
        String fileName = path.getFileName().toString().toLowerCase();
        boolean supportedExtension = SUPPORTED_EXTENSIONS.stream()
            .anyMatch(fileName::endsWith);
        
        if (!supportedExtension) {
            throw new ExperimentException("Unsupported file format: " + datasetPath + 
                                        ". Supported: " + SUPPORTED_EXTENSIONS);
        }
    }
    
    private static List<CSVRecord> loadDatasetFromFile(String datasetPath) throws IOException {
        Path path = Paths.get(datasetPath);
        String fileName = path.getFileName().toString().toLowerCase();
        
        InputStream inputStream = Files.newInputStream(path);
        
        // Handle compressed files
        if (fileName.endsWith(".gz")) {
            inputStream = new GZIPInputStream(inputStream);
        }
        
        // Determine CSV format
        CSVFormat format = CSVFormat.DEFAULT;
        if (fileName.contains(".tsv")) {
            format = CSVFormat.TDF;
        }
        
        try (Reader reader = new InputStreamReader(inputStream);
             CSVParser parser = new CSVParser(reader, format.withFirstRecordAsHeader())) {
            
            return new ArrayList<>(parser.getRecords());
        }
    }
    
    private static void validateRequiredColumns(Set<String> headers, String datasetPath) {
        // Basic validation - at least one column should be present
        if (headers.isEmpty()) {
            throw new ExperimentException("No valid headers found in dataset: " + datasetPath);
        }
        
        // Check for common required columns based on dataset type
        if (datasetPath.toLowerCase().contains("google")) {
            validateGoogleTraceColumns(headers, datasetPath);
        } else if (datasetPath.toLowerCase().contains("azure")) {
            validateAzureTraceColumns(headers, datasetPath);
        }
        
        LoggingManager.logInfo("Required columns validation passed for: " + datasetPath);
    }
    
    private static void validateGoogleTraceColumns(Set<String> headers, String datasetPath) {
        List<String> requiredColumns = Arrays.asList("timestamp", "job_id", "task_index");
        for (String required : requiredColumns) {
            if (!headers.contains(required)) {
                LoggingManager.logWarning("Recommended Google trace column missing: " + required + 
                                        " in dataset: " + datasetPath);
            }
        }
    }
    
    private static void validateAzureTraceColumns(Set<String> headers, String datasetPath) {
        List<String> requiredColumns = Arrays.asList("timestamp", "vm_id");
        for (String required : requiredColumns) {
            if (!headers.contains(required)) {
                LoggingManager.logWarning("Recommended Azure trace column missing: " + required + 
                                        " in dataset: " + datasetPath);
            }
        }
    }
    
    private static void validateDataConsistency(List<CSVRecord> records, String datasetPath) {
        if (records.size() < 2) {
            return; // Cannot validate consistency with less than 2 records
        }
        
        int expectedColumnCount = records.get(0).size();
        int inconsistentRows = 0;
        
        for (int i = 1; i < Math.min(records.size(), 1000); i++) { // Check first 1000 rows
            if (records.get(i).size() != expectedColumnCount) {
                inconsistentRows++;
            }
        }
        
        if (inconsistentRows > 0) {
            LoggingManager.logWarning("Found " + inconsistentRows + 
                                    " rows with inconsistent column count in dataset: " + datasetPath);
        }
    }
    
    private static DatasetStatistics calculateDatasetStatistics(List<CSVRecord> records, String datasetPath) {
        DatasetStatistics stats = new DatasetStatistics();
        stats.setDatasetPath(datasetPath);
        stats.setRecordCount(records.size());
        
        if (records.isEmpty()) {
            return stats;
        }
        
        // Calculate column statistics
        CSVRecord firstRecord = records.get(0);
        stats.setColumnCount(firstRecord.size());
        
        // Calculate file size
        try {
            Path path = Paths.get(datasetPath);
            stats.setFileSizeBytes(Files.size(path));
        } catch (IOException e) {
            LoggingManager.logWarning("Could not determine file size for: " + datasetPath);
        }
        
        // Calculate data quality metrics
        calculateDataQualityMetrics(records, stats);
        
        return stats;
    }
    
    private static void calculateDataQualityMetrics(List<CSVRecord> records, DatasetStatistics stats) {
        int totalCells = 0;
        int emptyCells = 0;
        
        // Sample first 1000 records for performance
        int sampleSize = Math.min(records.size(), 1000);
        
        for (int i = 0; i < sampleSize; i++) {
            CSVRecord record = records.get(i);
            for (int j = 0; j < record.size(); j++) {
                totalCells++;
                String value = record.get(j);
                if (value == null || value.trim().isEmpty()) {
                    emptyCells++;
                }
            }
        }
        
        double completenessRatio = totalCells > 0 ? (double)(totalCells - emptyCells) / totalCells : 0.0;
        stats.setDataCompletenessRatio(completenessRatio);
        stats.setEmptyCellCount(emptyCells);
    }
    
    /**
     * Inner class to hold dataset statistics
     */
    public static class DatasetStatistics {
        private String datasetPath;
        private int recordCount;
        private int columnCount;
        private long fileSizeBytes;
        private double dataCompletenessRatio;
        private int emptyCellCount;
        
        // Getters and setters
        public String getDatasetPath() { return datasetPath; }
        public void setDatasetPath(String datasetPath) { this.datasetPath = datasetPath; }
        
        public int getRecordCount() { return recordCount; }
        public void setRecordCount(int recordCount) { this.recordCount = recordCount; }
        
        public int getColumnCount() { return columnCount; }
        public void setColumnCount(int columnCount) { this.columnCount = columnCount; }
        
        public long getFileSizeBytes() { return fileSizeBytes; }
        public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
        
        public double getDataCompletenessRatio() { return dataCompletenessRatio; }
        public void setDataCompletenessRatio(double dataCompletenessRatio) { 
            this.dataCompletenessRatio = dataCompletenessRatio; 
        }
        
        public int getEmptyCellCount() { return emptyCellCount; }
        public void setEmptyCellCount(int emptyCellCount) { this.emptyCellCount = emptyCellCount; }
        
        @Override
        public String toString() {
            return String.format("DatasetStatistics{path='%s', records=%d, columns=%d, " +
                               "fileSize=%d bytes, completeness=%.2f%%, emptyCells=%d}",
                               datasetPath, recordCount, columnCount, fileSizeBytes, 
                               dataCompletenessRatio * 100, emptyCellCount);
        }
    }
}