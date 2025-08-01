package org.cloudbus.cloudsim.reporting;

import org.cloudbus.cloudsim.experiment.ExperimentalResult;
import org.cloudbus.cloudsim.experiment.ExperimentConfig;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.ValidationUtils;
import org.cloudbus.cloudsim.util.ExperimentException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Exports experimental data in various formats suitable for external analysis
 * tools such as R, MATLAB, SPSS, and Python.
 * 
 * @author Puneet Chandna
 * @since CloudSim Toolkit 1.0
 */
public class PublicationDataExporter {
    
    private static final String EXPORT_DIRECTORY = "results/exported_data/";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    // Constants for directory names
    private static final String CSV_DIR = "csv";
    private static final String R_DIR = "r";
    private static final String MATLAB_DIR = "matlab";
    private static final String SPSS_DIR = "spss";
    private static final String JSON_DIR = "json";
    
    // Constants for file names
    private static final String EXPERIMENT_DATA_CSV_FORMAT = "experiment_data_%s.csv";
    private static final String DETAILED_METRICS_CSV_FORMAT = "detailed_metrics_%s.csv";
    private static final String EXPERIMENT_DATA_JSON_FORMAT = "experiment_data_%s.json";
    
    // Constants for metric names
    private static final String ALGORITHM = "algorithm";
    private static final String RESOURCE_UTILIZATION = "resourceUtilization";
    private static final String POWER_CONSUMPTION = "powerConsumption";
    private static final String SLA_VIOLATIONS = "slaViolations";
    private static final String RESPONSE_TIME = "responseTime";
    private static final String THROUGHPUT = "throughput";
    private static final String EXECUTION_TIME = "executionTime";
    
    // Constants for MATLAB script
    private static final String MATLAB_XLABEL = "xlabel('Algorithm');\n";
    private static final String MATLAB_GRID_ON = "grid on;\n\n";
    
    private final List<ExperimentalResult> experimentalResults;
    private final Map<String, Object> metadata;
    private String exportTimestamp;
    
    /**
     * Constructs a PublicationDataExporter instance.
     */
    public PublicationDataExporter() {
        this.experimentalResults = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.exportTimestamp = LocalDateTime.now().format(DATE_FORMAT);
        
        try {
            Files.createDirectories(Paths.get(EXPORT_DIRECTORY));
            Files.createDirectories(Paths.get(EXPORT_DIRECTORY, CSV_DIR));
            Files.createDirectories(Paths.get(EXPORT_DIRECTORY, R_DIR));
            Files.createDirectories(Paths.get(EXPORT_DIRECTORY, MATLAB_DIR));
            Files.createDirectories(Paths.get(EXPORT_DIRECTORY, SPSS_DIR));
            Files.createDirectories(Paths.get(EXPORT_DIRECTORY, JSON_DIR));
        } catch (IOException e) {
            throw new ExperimentException("Failed to create export directories", e);
        }
        
        initializeMetadata();
    }
    
    /**
     * Sets experimental results for export.
     * 
     * @param results List of experimental results
     */
    public void setExperimentalResults(List<ExperimentalResult> results) {
        ValidationUtils.validateNotEmpty(results, "Experimental results");
        this.experimentalResults.clear();
        this.experimentalResults.addAll(results);
        LoggingManager.logInfo("Set " + results.size() + " experimental results for export");
    }
    
    /**
     * Adds metadata for the export.
     * 
     * @param key Metadata key
     * @param value Metadata value
     */
    public void addMetadata(String key, Object value) {
        ValidationUtils.validateNotNull(key, "Metadata key");
        metadata.put(key, value);
    }
    
    /**
     * Exports data to CSV format.
     * 
     * @return Path to the exported CSV file
     */
    public Path exportToCSV() {
        LoggingManager.logInfo("Exporting data to CSV format");
        
        try {
            String filename = String.format(EXPERIMENT_DATA_CSV_FORMAT, exportTimestamp);
            Path outputPath = Paths.get(EXPORT_DIRECTORY, CSV_DIR, filename);
            
            try (BufferedWriter writer = Files.newBufferedWriter(outputPath);
                 CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                    .setHeader("experiment_id", ALGORITHM, "run_number", "vm_count", "host_count",
                    "resource_utilization", "power_consumption", "sla_violations",
                    "response_time", THROUGHPUT, "execution_time", "convergence_iterations")
                    .build())) {
                
                int experimentId = 1;
                for (ExperimentalResult result : experimentalResults) {
                    csvPrinter.printRecord(
                        experimentId++,
                        result.getAlgorithmName(),
                        result.getRunNumber(),
                                    (Integer) result.getExperimentConfigData().getOrDefault("vmCount", 0),
            (Integer) result.getExperimentConfigData().getOrDefault("hostCount", 0),
                        result.getMetric(RESOURCE_UTILIZATION),
                        result.getMetric(POWER_CONSUMPTION),
                        result.getMetric(SLA_VIOLATIONS),
                        result.getMetric(RESPONSE_TIME),
                        result.getMetric(THROUGHPUT),
                        result.getMetric(EXECUTION_TIME),
                        result.getConvergenceData() != null ? result.getConvergenceData().size() : 0
                    );
                }
            }
            
            // Also export detailed metrics CSV
            exportDetailedMetricsCSV();
            
            LoggingManager.logInfo("CSV export completed: " + outputPath);
            return outputPath;
            
        } catch (IOException e) {
            throw new ExperimentException("Failed to export to CSV", e);
        }
    }
    
    /**
     * Exports data for R analysis.
     * 
     * @return Path to the exported R data file
     */
    public Path exportToR() {
        LoggingManager.logInfo("Exporting data for R analysis");
        
        try {
            // Export as RData format (simplified - using CSV with R script)
            Path csvPath = exportToCSV();
            
            // Generate R script for loading and analyzing data
            String scriptFilename = String.format("analysis_script_%s.R", exportTimestamp);
            Path scriptPath = Paths.get(EXPORT_DIRECTORY, R_DIR, scriptFilename);
            
            StringBuilder rScript = new StringBuilder();
            rScript.append("# R Analysis Script for Hippopotamus Optimization Results\n");
            rScript.append("# Generated: ").append(LocalDateTime.now()).append("\n\n");
            
            rScript.append("# Load required libraries\n");
            rScript.append("library(ggplot2)\n");
            rScript.append("library(dplyr)\n");
            rScript.append("library(tidyr)\n");
            rScript.append("library(effsize)\n\n");
            
            rScript.append("# Load data\n");
            rScript.append("data <- read.csv(\"").append(csvPath.toAbsolutePath()).append("\")\n\n");
            
            rScript.append("# Basic analysis\n");
            rScript.append("summary_stats <- data %>%\n");
            rScript.append("  group_by(algorithm) %>%\n");
            rScript.append("  summarise(\n");
            rScript.append("    mean_utilization = mean(resource_utilization),\n");
            rScript.append("    sd_utilization = sd(resource_utilization),\n");
            rScript.append("    mean_power = mean(power_consumption),\n");
            rScript.append("    sd_power = sd(power_consumption),\n");
            rScript.append("    mean_sla = mean(sla_violations),\n");
            rScript.append("    mean_response = mean(response_time)\n");
            rScript.append("  )\n\n");
            
            rScript.append("# Statistical tests\n");
            rScript.append("# Pairwise t-tests\n");
            rScript.append("pairwise_tests <- pairwise.t.test(data$resource_utilization, ");
            rScript.append("data$algorithm, p.adjust.method = \"bonferroni\")\n\n");
            
            rScript.append("# ANOVA\n");
            rScript.append("anova_result <- aov(resource_utilization ~ algorithm, data = data)\n");
            rScript.append("summary(anova_result)\n\n");
            
            rScript.append("# Effect sizes\n");
            rScript.append("ho_data <- data[data$algorithm == \"HippopotamusOptimization\",]\n");
            rScript.append("bf_data <- data[data$algorithm == \"BestFit\",]\n");
            rScript.append("effect_size <- cohen.d(ho_data$resource_utilization, ");
            rScript.append("bf_data$resource_utilization)\n\n");
            
            rScript.append("# Visualizations\n");
            rScript.append("# Box plot\n");
            rScript.append("p1 <- ggplot(data, aes(x = algorithm, y = resource_utilization, ");
            rScript.append("fill = algorithm)) +\n");
            rScript.append("  geom_boxplot() +\n");
            rScript.append("  theme_minimal() +\n");
            rScript.append("  labs(title = \"Resource Utilization by Algorithm\",\n");
            rScript.append("       x = \"Algorithm\", y = \"Resource Utilization (%)\")\n\n");
            
            rScript.append("# Save plot\n");
            rScript.append("ggsave(\"resource_utilization_boxplot.pdf\", p1, ");
            rScript.append("width = 10, height = 6)\n\n");
            
            rScript.append("# Print results\n");
            rScript.append("print(summary_stats)\n");
            rScript.append("print(pairwise_tests)\n");
            rScript.append("print(effect_size)\n");
            
            Files.write(scriptPath, rScript.toString().getBytes());
            
            // Also create RData format file
            String rdataFilename = String.format("experiment_data_%s.RData", exportTimestamp);
            Path rdataPath = Paths.get(EXPORT_DIRECTORY, R_DIR, rdataFilename);
            
            // Note: Actual RData export would require R integration
            // For now, we'll create a CSV that R can easily import
            Files.copy(csvPath, rdataPath.resolveSibling(rdataFilename.replace(".RData", ".csv")));
            
            LoggingManager.logInfo("R export completed: " + scriptPath);
            return scriptPath;
            
        } catch (IOException e) {
            throw new ExperimentException("Failed to export to R", e);
        }
    }
    
    /**
     * Exports data for MATLAB analysis.
     * 
     * @return Path to the exported MATLAB file
     */
    public Path exportToMATLAB() {
        LoggingManager.logInfo("Exporting data for MATLAB analysis");
        
        try {
            // Create MATLAB script
            String scriptFilename = String.format("analysis_script_%s.m", exportTimestamp);
            Path scriptPath = Paths.get(EXPORT_DIRECTORY, MATLAB_DIR, scriptFilename);
            
            // Export data as CSV for MATLAB to read
            Path csvPath = exportToCSV();
            String dataFilename = String.format(EXPERIMENT_DATA_CSV_FORMAT, exportTimestamp);
            Path matlabCsvPath = Paths.get(EXPORT_DIRECTORY, MATLAB_DIR, dataFilename);
            Files.copy(csvPath, matlabCsvPath, StandardCopyOption.REPLACE_EXISTING);
            
            StringBuilder matlabScript = new StringBuilder();
            matlabScript.append("% MATLAB Analysis Script for Hippopotamus Optimization Results\n");
            matlabScript.append("% Generated: ").append(LocalDateTime.now()).append("\n\n");
            
            matlabScript.append("% Clear workspace\n");
            matlabScript.append("clear all; close all; clc;\n\n");
            
            matlabScript.append("% Load data\n");
            matlabScript.append("data = readtable('").append(dataFilename).append("');\n\n");
            
            matlabScript.append("% Extract algorithm names\n");
            matlabScript.append("algorithms = unique(data.algorithm);\n");
            matlabScript.append("num_algorithms = length(algorithms);\n\n");
            
            matlabScript.append("% Initialize result arrays\n");
            matlabScript.append("mean_utilization = zeros(num_algorithms, 1);\n");
            matlabScript.append("std_utilization = zeros(num_algorithms, 1);\n");
            matlabScript.append("mean_power = zeros(num_algorithms, 1);\n");
            matlabScript.append("std_power = zeros(num_algorithms, 1);\n\n");
            
            matlabScript.append("% Calculate statistics for each algorithm\n");
            matlabScript.append("for i = 1:num_algorithms\n");
            matlabScript.append("    algo_data = data(strcmp(data.algorithm, algorithms{i}), :);\n");
            matlabScript.append("    mean_utilization(i) = mean(algo_data.resource_utilization);\n");
            matlabScript.append("    std_utilization(i) = std(algo_data.resource_utilization);\n");
            matlabScript.append("    mean_power(i) = mean(algo_data.power_consumption);\n");
            matlabScript.append("    std_power(i) = std(algo_data.power_consumption);\n");
            matlabScript.append("end\n\n");
            
            matlabScript.append("% Create comparison plots\n");
            matlabScript.append("figure('Position', [100, 100, 1200, 800]);\n\n");
            
            matlabScript.append("% Resource utilization subplot\n");
            matlabScript.append("subplot(2, 2, 1);\n");
            matlabScript.append("bar(categorical(algorithms), mean_utilization);\n");
            matlabScript.append("hold on;\n");
            matlabScript.append("errorbar(1:num_algorithms, mean_utilization, std_utilization, 'k.');\n");
            matlabScript.append(MATLAB_XLABEL);
            matlabScript.append("ylabel('Resource Utilization (%)');\n");
            matlabScript.append("title('Average Resource Utilization');\n");
            matlabScript.append(MATLAB_GRID_ON);
            
            matlabScript.append("% Power consumption subplot\n");
            matlabScript.append("subplot(2, 2, 2);\n");
            matlabScript.append("bar(categorical(algorithms), mean_power);\n");
            matlabScript.append("hold on;\n");
            matlabScript.append("errorbar(1:num_algorithms, mean_power, std_power, 'k.');\n");
            matlabScript.append(MATLAB_XLABEL);
            matlabScript.append("ylabel('Power Consumption (kWh)');\n");
            matlabScript.append("title('Average Power Consumption');\n");
            matlabScript.append(MATLAB_GRID_ON);
            
            matlabScript.append("% Box plots\n");
            matlabScript.append("subplot(2, 2, 3);\n");
            matlabScript.append("boxplot(data.resource_utilization, data.algorithm);\n");
            matlabScript.append(MATLAB_XLABEL);
            matlabScript.append("ylabel('Resource Utilization (%)');\n");
            matlabScript.append("title('Resource Utilization Distribution');\n");
            matlabScript.append(MATLAB_GRID_ON);
            
            matlabScript.append("% Statistical tests\n");
            matlabScript.append("% ANOVA test\n");
            matlabScript.append("[p_anova, tbl_anova, stats_anova] = anova1(data.resource_utilization, ");
            matlabScript.append("data.algorithm, 'off');\n");
            matlabScript.append("fprintf('ANOVA p-value: %.4f\\n', p_anova);\n\n");
            
            matlabScript.append("% Multiple comparison test\n");
            matlabScript.append("figure;\n");
            matlabScript.append("[c, m, h, gnames] = multcompare(stats_anova);\n");
            matlabScript.append("title('Multiple Comparison of Algorithms');\n\n");
            
            matlabScript.append("% Save figures\n");
            matlabScript.append("saveas(gcf, 'algorithm_comparison.fig');\n");
            matlabScript.append("saveas(gcf, 'algorithm_comparison.png');\n\n");
            
            matlabScript.append("% Export results\n");
            matlabScript.append("results_table = table(algorithms, mean_utilization, std_utilization, ");
            matlabScript.append("mean_power, std_power);\n");
            matlabScript.append("writetable(results_table, 'summary_statistics.csv');\n");
            
            Files.write(scriptPath, matlabScript.toString().getBytes());
            
            LoggingManager.logInfo("MATLAB export completed: " + scriptPath);
            return scriptPath;
            
        } catch (IOException e) {
            throw new ExperimentException("Failed to export to MATLAB", e);
        }
    }
    
    /**
     * Exports data for SPSS analysis.
     * 
     * @return Path to the exported SPSS file
     */
    public Path exportToSPSS() {
        LoggingManager.logInfo("Exporting data for SPSS analysis");
        
        try {
            // SPSS syntax file
            String syntaxFilename = String.format("analysis_syntax_%s.sps", exportTimestamp);
            Path syntaxPath = Paths.get(EXPORT_DIRECTORY, SPSS_DIR, syntaxFilename);
            
            // Export data as CSV for SPSS to import
            Path csvPath = exportToCSV();
            String dataFilename = String.format(EXPERIMENT_DATA_CSV_FORMAT, exportTimestamp);
            Path spssCsvPath = Paths.get(EXPORT_DIRECTORY, SPSS_DIR, dataFilename);
            Files.copy(csvPath, spssCsvPath, StandardCopyOption.REPLACE_EXISTING);
            
            StringBuilder spssSyntax = new StringBuilder();
            spssSyntax.append("* SPSS Syntax for Hippopotamus Optimization Analysis.\n");
            spssSyntax.append("* Generated: ").append(LocalDateTime.now()).append(".\n\n");
            
            spssSyntax.append("* Import data.\n");
            spssSyntax.append("GET DATA\n");
            spssSyntax.append("  /TYPE=TXT\n");
            spssSyntax.append("  /FILE='").append(dataFilename).append("'\n");
            spssSyntax.append("  /DELIMITERS=\",\"\n");
            spssSyntax.append("  /QUALIFIER='\"'\n");
            spssSyntax.append("  /ARRANGEMENT=DELIMITED\n");
            spssSyntax.append("  /FIRSTCASE=2\n");
            spssSyntax.append("  /VARIABLES=\n");
            spssSyntax.append("    experiment_id F4.0\n");
            spssSyntax.append("    algorithm A50\n");
            spssSyntax.append("    run_number F4.0\n");
            spssSyntax.append("    vm_count F6.0\n");
            spssSyntax.append("    host_count F6.0\n");
            spssSyntax.append("    resource_utilization F8.2\n");
            spssSyntax.append("    power_consumption F10.2\n");
            spssSyntax.append("    sla_violations F6.0\n");
            spssSyntax.append("    response_time F8.2\n");
            spssSyntax.append("    throughput F8.2\n");
            spssSyntax.append("    execution_time F10.2\n");
            spssSyntax.append("    convergence_iterations F6.0.\n");
            spssSyntax.append("EXECUTE.\n\n");
            
            spssSyntax.append("* Descriptive statistics.\n");
            spssSyntax.append("DESCRIPTIVES VARIABLES=resource_utilization power_consumption ");
            spssSyntax.append("sla_violations response_time\n");
            spssSyntax.append("  /STATISTICS=MEAN STDDEV MIN MAX.\n\n");
            
            spssSyntax.append("* Descriptive statistics by algorithm.\n");
            spssSyntax.append("MEANS TABLES=resource_utilization power_consumption BY algorithm\n");
            spssSyntax.append("  /CELLS=MEAN STDDEV COUNT.\n\n");
            
            spssSyntax.append("* One-way ANOVA.\n");
            spssSyntax.append("ONEWAY resource_utilization BY algorithm\n");
            spssSyntax.append("  /STATISTICS DESCRIPTIVES HOMOGENEITY\n");
            spssSyntax.append("  /PLOT MEANS\n");
            spssSyntax.append("  /POSTHOC=BONFERRONI ALPHA(0.05).\n\n");
            
            spssSyntax.append("* Box plots.\n");
            spssSyntax.append("EXAMINE VARIABLES=resource_utilization BY algorithm\n");
            spssSyntax.append("  /PLOT BOXPLOT\n");
            spssSyntax.append("  /STATISTICS DESCRIPTIVES.\n\n");
            
            spssSyntax.append("* Correlation analysis.\n");
            spssSyntax.append("CORRELATIONS\n");
            spssSyntax.append("  /VARIABLES=vm_count resource_utilization power_consumption response_time\n");
            spssSyntax.append("  /PRINT=TWOTAIL NOSIG\n");
            spssSyntax.append("  /MISSING=PAIRWISE.\n\n");
            
            spssSyntax.append("* Save output.\n");
            spssSyntax.append("OUTPUT SAVE OUTFILE='ho_analysis_output.spv'.\n\n");
            
            spssSyntax.append("* Export results.\n");
            spssSyntax.append("OUTPUT EXPORT\n");
            spssSyntax.append("  /CONTENTS EXPORT=ALL\n");
            spssSyntax.append("  /PDF DOCUMENTFILE='ho_analysis_results.pdf'.\n");
            
            Files.write(syntaxPath, spssSyntax.toString().getBytes());
            
            LoggingManager.logInfo("SPSS export completed: " + syntaxPath);
            return syntaxPath;
            
        } catch (IOException e) {
            throw new ExperimentException("Failed to export to SPSS", e);
        }
    }
    
    /**
     * Exports data to JSON format.
     * 
     * @return Path to the exported JSON file
     */
    public Path exportToJSON() {
        LoggingManager.logInfo("Exporting data to JSON format");
        
        try {
            Map<String, Object> exportData = new HashMap<>();
            
            // Add metadata
            exportData.put("metadata", metadata);
            
            // Add experimental results
            List<Map<String, Object>> resultsData = new ArrayList<>();
            for (ExperimentalResult result : experimentalResults) {
                Map<String, Object> resultMap = new HashMap<>();
                
                resultMap.put("algorithm", result.getAlgorithmName());
                resultMap.put("runNumber", result.getRunNumber());
                resultMap.put("timestamp", result.getTimestamp());
                
                // Configuration
                Map<String, Object> configMap = new HashMap<>();
                Map<String, Object> configData = result.getExperimentConfigData();
                configMap.put("vmCount", configData.getOrDefault("vmCount", 0));
                configMap.put("hostCount", configData.getOrDefault("hostCount", 0));
                configMap.put("simulationDuration", configData.getOrDefault("simulationDuration", 0));
                resultMap.put("configuration", configMap);
                
                // Metrics
                Map<String, Double> metrics = new HashMap<>();
                metrics.put(RESOURCE_UTILIZATION, result.getMetric(RESOURCE_UTILIZATION));
                metrics.put(POWER_CONSUMPTION, result.getMetric(POWER_CONSUMPTION));
                metrics.put(SLA_VIOLATIONS, result.getMetric(SLA_VIOLATIONS));
                metrics.put(RESPONSE_TIME, result.getMetric(RESPONSE_TIME));
                metrics.put(THROUGHPUT, result.getMetric(THROUGHPUT));
                metrics.put(EXECUTION_TIME, result.getMetric(EXECUTION_TIME));
                resultMap.put("metrics", metrics);
                
                // Convergence data (if available)
                if (result.getConvergenceData() != null) {
                    resultMap.put("convergenceData", result.getConvergenceData());
                }
                
                resultsData.add(resultMap);
            }
            exportData.put("results", resultsData);
            
            // Summary statistics
            exportData.put("summary", generateSummaryStatistics());
            
            // Write JSON file
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            
            String filename = String.format(EXPERIMENT_DATA_JSON_FORMAT, exportTimestamp);
            Path outputPath = Paths.get(EXPORT_DIRECTORY, JSON_DIR, filename);
            mapper.writeValue(outputPath.toFile(), exportData);
            
            LoggingManager.logInfo("JSON export completed: " + outputPath);
            return outputPath;
            
        } catch (IOException e) {
            throw new ExperimentException("Failed to export to JSON", e);
        }
    }
    
    // Private helper methods
    
    private void initializeMetadata() {
        metadata.put("exportTimestamp", exportTimestamp);
        metadata.put("frameworkVersion", "CloudSim Plus 7.0.1");
        metadata.put("javaVersion", "21");
        metadata.put("experimentType", "VM Placement Optimization");
        metadata.put("algorithm", "Hippopotamus Optimization");
    }
    
    private void exportDetailedMetricsCSV() throws IOException {
        String filename = String.format(DETAILED_METRICS_CSV_FORMAT, exportTimestamp);
        Path outputPath = Paths.get(EXPORT_DIRECTORY, CSV_DIR, filename);
        
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                .setHeader(ALGORITHM, "metric_name", "mean", "std_dev", "min", "max", "median", "q1", "q3")
                .build())) {
            
            // Group results by algorithm
            Map<String, List<ExperimentalResult>> algorithmGroups = 
                experimentalResults.stream()
                    .collect(Collectors.groupingBy(ExperimentalResult::getAlgorithmName));
            
            String[] metrics = {RESOURCE_UTILIZATION, POWER_CONSUMPTION, SLA_VIOLATIONS, 
                              RESPONSE_TIME, THROUGHPUT, EXECUTION_TIME};
            
            for (Map.Entry<String, List<ExperimentalResult>> entry : algorithmGroups.entrySet()) {
                String algorithm = entry.getKey();
                List<ExperimentalResult> results = entry.getValue();
                
                for (String metric : metrics) {
                    List<Double> values = results.stream()
                        .<Double>map(r -> r.getMetric(metric))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                    
                    if (!values.isEmpty()) {
                        double[] array = values.stream().mapToDouble(Double::doubleValue).toArray();
                        Arrays.sort(array);
                        
                        double mean = Arrays.stream(array).average().orElse(0);
                        double stdDev = calculateStandardDeviation(array, mean);
                        double min = array[0];
                        double max = array[array.length - 1];
                        double median = calculatePercentile(array, 50);
                        double q1 = calculatePercentile(array, 25);
                        double q3 = calculatePercentile(array, 75);
                        
                        csvPrinter.printRecord(
                            algorithm, metric, mean, stdDev, min, max, median, q1, q3
                        );
                    }
                }
            }
        }
    }
    
    private Map<String, Object> generateSummaryStatistics() {
        Map<String, Object> summary = new HashMap<>();
        
        // Group by algorithm
        Map<String, List<ExperimentalResult>> algorithmGroups = 
            experimentalResults.stream()
                .collect(Collectors.groupingBy(ExperimentalResult::getAlgorithmName));
        
        Map<String, Map<String, Object>> algorithmSummaries = new HashMap<>();
        
        for (Map.Entry<String, List<ExperimentalResult>> entry : algorithmGroups.entrySet()) {
            String algorithm = entry.getKey();
            List<ExperimentalResult> results = entry.getValue();
            
            Map<String, Object> algorithmStats = new HashMap<>();
            algorithmStats.put("sampleSize", results.size());
            
                    // Calculate statistics for each metric
        String[] metrics = {RESOURCE_UTILIZATION, POWER_CONSUMPTION, SLA_VIOLATIONS, RESPONSE_TIME};
        
        for (String metric : metrics) {
            Map<String, Double> metricStats = calculateMetricStatistics(results, metric);
            algorithmStats.put(metric + "_stats", metricStats);
        }
            
            algorithmSummaries.put(algorithm, algorithmStats);
        }
        
        summary.put("algorithms", algorithmSummaries);
        summary.put("totalExperiments", experimentalResults.size());
        summary.put("uniqueAlgorithms", algorithmGroups.size());
        
        return summary;
    }
    
    private Map<String, Double> calculateMetricStatistics(List<ExperimentalResult> results, String metric) {
        Map<String, Double> stats = new HashMap<>();
        
        List<Double> values = results.stream()
            .<Double>map(r -> r.getMetric(metric))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        if (!values.isEmpty()) {
            double[] array = values.stream().mapToDouble(Double::doubleValue).toArray();
            
            stats.put("mean", Arrays.stream(array).average().orElse(0));
            stats.put("min", Arrays.stream(array).min().orElse(0));
            stats.put("max", Arrays.stream(array).max().orElse(0));
            stats.put("stdDev", calculateStandardDeviation(array, stats.get("mean")));
            
            Arrays.sort(array);
            stats.put("median", calculatePercentile(array, 50));
            stats.put("q1", calculatePercentile(array, 25));
            stats.put("q3", calculatePercentile(array, 75));
        }
        
        return stats;
    }
    
    private double calculateStandardDeviation(double[] values, double mean) {
        if (values.length <= 1) return 0.0;
        
        double variance = Arrays.stream(values)
            .map(v -> Math.pow(v - mean, 2))
            .sum() / (values.length - 1);
        
        return Math.sqrt(variance);
    }
    
    private double calculatePercentile(double[] sortedValues, double percentile) {
        if (sortedValues.length == 0) return 0.0;
        
        double index = (percentile / 100.0) * (sortedValues.length - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        
        if (lower == upper) {
            return sortedValues[lower];
        }
        
        double weight = index - lower;
        return sortedValues[lower] * (1 - weight) + sortedValues[upper] * weight;
    }
}