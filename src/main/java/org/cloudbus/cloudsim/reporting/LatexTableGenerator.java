package org.cloudbus.cloudsim.reporting;

import org.cloudbus.cloudsim.experiment.ExperimentalResult;
import org.cloudbus.cloudsim.analyzer.ScalabilityResults;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.ValidationUtils;
import org.cloudbus.cloudsim.util.ExperimentException;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates LaTeX tables for research publication, including results tables,
 * comparison tables, and statistical test tables with proper formatting.
 * 
 * @author Puneet Chandna
 * @since CloudSim Toolkit 1.0
 */
public class LatexTableGenerator {
    
    // Constants for LaTeX table formatting
    private static final String TABLE_DIRECTORY = "results/latex_tables/";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    // LaTeX table constants
    private static final String TABLE_BEGIN = "\\begin{table}[htbp]\n";
    private static final String TABLE_CENTER = "\\centering\n";
    private static final String TABLE_CAPTION_START = "\\caption{";
    private static final String TABLE_CAPTION_END = "}\n";
    private static final String TABLE_LABEL_START = "\\label{tab:";
    private static final String TABLE_LABEL_END = "}\n";
    private static final String TABLE_TOP_RULE = "\\toprule\n";
    private static final String TABLE_MID_RULE = "\\midrule\n";
    private static final String TABLE_BOTTOM_RULE = "\\bottomrule\n";
    private static final String TABLE_END_TABULAR = "\\end{tabular}\n";
    private static final String TABLE_END = "\\end{table}\n";
    private static final String TABLE_ROW_END = " \\\\\n";
    
    // Metric constants
    private static final String METRIC_RESOURCE_UTILIZATION = "resourceUtilization";
    private static final String METRIC_POWER_CONSUMPTION = "powerConsumption";
    private static final String METRIC_SLA_VIOLATIONS = "slaViolations";
    private static final String METRIC_RESPONSE_TIME = "responseTime";
    private static final String METRIC_THROUGHPUT = "throughput";
    
    // Other constants
    private static final String EXECUTION_TIMES_KEY = "execution_times";
    private static final String SENSITIVITY_KEY = "sensitivity";
    
    private String tableTimestamp;
    
    /**
     * Constructs a LatexTableGenerator instance.
     */
    public LatexTableGenerator() {
        this.tableTimestamp = LocalDateTime.now().format(DATE_FORMAT);
        
        try {
            Files.createDirectories(Paths.get(TABLE_DIRECTORY));
        } catch (IOException e) {
            throw new ExperimentException("Failed to create table directory", e);
        }
    }
    
    /**
     * Generates results table in LaTeX format.
     * 
     * @param results Map of algorithm results
     * @param tableId LaTeX table ID for referencing
     * @param caption Table caption
     * @return LaTeX table string
     */
    public String generateResultsTable(Map<String, List<ExperimentalResult>> results,
                                     String tableId, String caption) {
        LoggingManager.logInfo("Generating LaTeX results table: " + tableId);
        
        StringBuilder table = new StringBuilder();
        
        // Begin table
        table.append(TABLE_BEGIN);
        table.append(TABLE_CENTER);
        table.append(TABLE_CAPTION_START).append(caption).append(TABLE_CAPTION_END);
        table.append(TABLE_LABEL_START).append(tableId).append(TABLE_LABEL_END);
        
        // Determine number of algorithms
        int numAlgorithms = results.size();
        String columnSpec = "l" + repeatString("r", numAlgorithms);
        
        table.append("\\begin{tabular}{").append(columnSpec).append("}\n");
        table.append(TABLE_TOP_RULE);
        
        // Header row
        table.append("\\textbf{Metric}");
        for (String algorithm : results.keySet()) {
            table.append(" & \\textbf{").append(formatAlgorithmName(algorithm)).append("}");
        }
        table.append(TABLE_ROW_END);
        table.append(TABLE_MID_RULE);
        
        // Metric rows
        String[] metrics = {
            "Resource Utilization (\\%)",
            "Power Consumption (kWh)",
            "SLA Violations",
            "Response Time (ms)",
            "Throughput (VMs/s)"
        };
        
        String[] metricKeys = {
            METRIC_RESOURCE_UTILIZATION,
            METRIC_POWER_CONSUMPTION,
            METRIC_SLA_VIOLATIONS,
            METRIC_RESPONSE_TIME,
            METRIC_THROUGHPUT
        };
        
        for (int i = 0; i < metrics.length; i++) {
            table.append(metrics[i]);
            
            for (String algorithm : results.keySet()) {
                List<ExperimentalResult> algorithmResults = results.get(algorithm);
                DescriptiveStatistics stats = calculateStats(algorithmResults, metricKeys[i]);
                
                table.append(" & ");
                if (metricKeys[i].equals(METRIC_SLA_VIOLATIONS)) {
                    table.append(String.format("%.0f", stats.getMean()));
                } else {
                    table.append(String.format("%.2f $\\pm$ %.2f", stats.getMean(), stats.getStandardDeviation()));
                }
            }
            table.append(TABLE_ROW_END);
        }
        
        // Bottom rule
        table.append(TABLE_BOTTOM_RULE);
        table.append(TABLE_END_TABULAR);
        table.append(TABLE_END);
        
        return table.toString();
    }
    
    /**
     * Generates comparison table for algorithms.
     * 
     * @param results Map of algorithm results
     * @param baseline Baseline algorithm name
     * @param tableId LaTeX table ID
     * @param caption Table caption
     * @return LaTeX comparison table string
     */
    public String generateComparisonTable(Map<String, List<ExperimentalResult>> results,
                                        String baseline, String tableId, String caption) {
        LoggingManager.logInfo("Generating LaTeX comparison table: " + tableId);
        
        StringBuilder table = new StringBuilder();
        
        // Calculate baseline metrics
        Map<String, Double> baselineMetrics = calculateAverageMetrics(results.get(baseline));
        
        // Begin table
        table.append(TABLE_BEGIN);
        table.append(TABLE_CENTER);
        table.append(TABLE_CAPTION_START).append(caption).append(TABLE_CAPTION_END);
        table.append(TABLE_LABEL_START).append(tableId).append(TABLE_LABEL_END);
        table.append("\\begin{tabular}{lrrrrr}\n");
        table.append(TABLE_TOP_RULE);
        
        // Header
        table.append("\\textbf{Algorithm} & ")
             .append("\\textbf{Util. (\\%)} & ")
             .append("\\textbf{Power (kWh)} & ")
             .append("\\textbf{SLA Viol.} & ")
             .append("\\textbf{Resp. (ms)} & ")
             .append("\\textbf{Improvement}\\\\\n");
        table.append(TABLE_MID_RULE);
        
        // Sort algorithms by performance
        List<Map.Entry<String, Double>> sortedAlgorithms = sortAlgorithmsByPerformance(results);
        
        for (Map.Entry<String, Double> entry : sortedAlgorithms) {
            String algorithm = entry.getKey();
            Map<String, Double> metrics = calculateAverageMetrics(results.get(algorithm));
            
            table.append(formatAlgorithmName(algorithm));
            table.append(" & ").append(String.format("%.1f", metrics.get(METRIC_RESOURCE_UTILIZATION)));
            table.append(" & ").append(String.format("%.0f", metrics.get(METRIC_POWER_CONSUMPTION)));
            table.append(" & ").append(String.format("%.0f", metrics.get(METRIC_SLA_VIOLATIONS)));
            table.append(" & ").append(String.format("%.1f", metrics.get(METRIC_RESPONSE_TIME)));
            
            // Calculate improvement
            if (algorithm.equals(baseline)) {
                table.append(" & --");
            } else {
                double improvement = calculateImprovement(metrics, baselineMetrics);
                table.append(" & ");
                if (improvement > 0) {
                    table.append("+");
                }
                table.append(String.format("%.1f\\%%", improvement));
            }
            
            table.append(TABLE_ROW_END);
        }
        
        table.append(TABLE_BOTTOM_RULE);
        table.append(TABLE_END_TABULAR);
        table.append(TABLE_END);
        
        return table.toString();
    }
    
    /**
     * Generates statistical test results table.
     * 
     * @param testResults Statistical test results
     * @param tableId LaTeX table ID
     * @param caption Table caption
     * @return LaTeX statistical table string
     */
    public String generateStatisticalTable(Map<String, Object> testResults,
                                         String tableId, String caption) {
        LoggingManager.logInfo("Generating LaTeX statistical table: " + tableId);
        
        StringBuilder table = new StringBuilder();
        
        table.append(TABLE_BEGIN);
        table.append(TABLE_CENTER);
        table.append(TABLE_CAPTION_START).append(caption).append(TABLE_CAPTION_END);
        table.append(TABLE_LABEL_START).append(tableId).append(TABLE_LABEL_END);
        table.append("\\begin{tabular}{llrrrr}\n");
        table.append(TABLE_TOP_RULE);
        
        // Header
        table.append("\\textbf{Comparison} & ")
             .append("\\textbf{Metric} & ")
             .append("\\textbf{t-statistic} & ")
             .append("\\textbf{p-value} & ")
             .append("\\textbf{Effect Size} & ")
             .append("\\textbf{Sig.} \\\\\n");
        table.append(TABLE_MID_RULE);
        
        // Extract and format test results
        if (testResults.containsKey("pairwise_comparisons")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> comparisons = 
                (List<Map<String, Object>>) testResults.get("pairwise_comparisons");
            
            for (Map<String, Object> comparison : comparisons) {
                String algo1 = (String) comparison.get("algorithm1");
                String algo2 = (String) comparison.get("algorithm2");
                
                table.append(formatAlgorithmName(algo1))
                     .append(" vs ")
                     .append(formatAlgorithmName(algo2));
                
                @SuppressWarnings("unchecked")
                Map<String, Double> metrics = (Map<String, Double>) comparison.get("metrics");
                
                for (Map.Entry<String, Double> metric : metrics.entrySet()) {
                    table.append(" & ").append(formatMetricName(metric.getKey()));
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Double> stats = (Map<String, Double>) comparison.get(metric.getKey());
                    
                    double tStat = stats.getOrDefault("t_statistic", 0.0);
                    double pValue = stats.getOrDefault("p_value", 1.0);
                    double effectSize = stats.getOrDefault("effect_size", 0.0);
                    
                    table.append(" & ").append(String.format("%.3f", tStat));
                    table.append(" & ").append(formatPValue(pValue));
                    table.append(" & ").append(String.format("%.3f", effectSize));
                    table.append(" & ").append(pValue < 0.05 ? "\\checkmark" : "--");
                    table.append(TABLE_ROW_END);
                }
                
                table.append(TABLE_MID_RULE);
            }
        }
        
        table.append(TABLE_BOTTOM_RULE);
        table.append("\\multicolumn{6}{l}{\\footnotesize ");
        table.append("Significance level: $\\alpha = 0.05$ with Bonferroni correction}\\\\\n");
        table.append(TABLE_END_TABULAR);
        table.append(TABLE_END);
        
        return table.toString();
    }
    
    /**
     * Generates scalability analysis table.
     * 
     * @param scalabilityResults Scalability analysis results
     * @param tableId LaTeX table ID
     * @param caption Table caption
     * @return LaTeX scalability table string
     */
    public String generateScalabilityTable(Map<String, ScalabilityResults> scalabilityResults,
                                         String tableId, String caption) {
        LoggingManager.logInfo("Generating LaTeX scalability table: " + tableId);
        
        StringBuilder table = new StringBuilder();
        
        table.append(TABLE_BEGIN);
        table.append(TABLE_CENTER);
        table.append(TABLE_CAPTION_START).append(caption).append(TABLE_CAPTION_END);
        table.append(TABLE_LABEL_START).append(tableId).append(TABLE_LABEL_END);
        
        // Determine algorithms
        Set<String> algorithms = scalabilityResults.keySet();
        String columnSpec = "r" + repeatString("r", algorithms.size());
        
        table.append("\\begin{tabular}{").append(columnSpec).append("}\n");
        table.append(TABLE_TOP_RULE);
        
        // Header
        table.append("\\textbf{VMs}");
        for (String algorithm : algorithms) {
            table.append(" & \\textbf{").append(formatAlgorithmName(algorithm)).append("}");
        }
        table.append(TABLE_ROW_END);
        table.append(TABLE_MID_RULE);
        
        // Get problem sizes from the first algorithm's VM scaling analysis
        List<Integer> problemSizes = new ArrayList<>();
        ScalabilityResults firstResult = scalabilityResults.values().iterator().next();
        if (firstResult.getVmScalingAnalysis() != null && 
            firstResult.getVmScalingAnalysis().containsKey(EXECUTION_TIMES_KEY)) {
            @SuppressWarnings("unchecked")
            Map<Integer, Double> executionTimes = (Map<Integer, Double>) 
                firstResult.getVmScalingAnalysis().get(EXECUTION_TIMES_KEY);
            problemSizes = executionTimes.keySet().stream()
                .sorted()
                .collect(Collectors.toList());
        }
        
        // Data rows
        for (Integer size : problemSizes) {
            table.append(size);
            
            for (String algorithm : algorithms) {
                ScalabilityResults results = scalabilityResults.get(algorithm);
                Double time = null;
                
                if (results.getVmScalingAnalysis() != null && 
                    results.getVmScalingAnalysis().containsKey(EXECUTION_TIMES_KEY)) {
                    @SuppressWarnings("unchecked")
                    Map<Integer, Double> executionTimes = (Map<Integer, Double>) 
                        results.getVmScalingAnalysis().get(EXECUTION_TIMES_KEY);
                    time = executionTimes.get(size);
                }
                
                table.append(" & ");
                if (time != null) {
                    table.append(String.format("%.2f", time));
                } else {
                    table.append("--");
                }
            }
            table.append(TABLE_ROW_END);
        }
        
        // Add complexity row
        table.append(TABLE_MID_RULE);
        table.append("\\textbf{Complexity}");
        for (String algorithm : algorithms) {
            ScalabilityResults results = scalabilityResults.get(algorithm);
            String complexity = "N/A";
            if (results.getComplexityModel() != null && 
                results.getComplexityModel().getTimeComplexity() != null) {
                complexity = results.getComplexityModel().getTimeComplexity().getNotation();
            }
            table.append(" & ").append(complexity);
        }
        table.append(TABLE_ROW_END);
        
        table.append(TABLE_BOTTOM_RULE);
        table.append(TABLE_END_TABULAR);
        table.append(TABLE_END);
        
        return table.toString();
    }
    
    /**
     * Generates parameter sensitivity table.
     * 
     * @param sensitivityData Parameter sensitivity data
     * @param tableId LaTeX table ID
     * @param caption Table caption
     * @return LaTeX sensitivity table string
     */
    public String generateSensitivityTable(Map<String, Map<String, Double>> sensitivityData,
                                         String tableId, String caption) {
        LoggingManager.logInfo("Generating LaTeX sensitivity table: " + tableId);
        
        StringBuilder table = new StringBuilder();
        
        table.append(TABLE_BEGIN);
        table.append(TABLE_CENTER);
        table.append(TABLE_CAPTION_START).append(caption).append(TABLE_CAPTION_END);
        table.append(TABLE_LABEL_START).append(tableId).append(TABLE_LABEL_END);
        table.append("\\begin{tabular}{lrrrr}\n");
        table.append(TABLE_TOP_RULE);
        
        // Header
        table.append("\\textbf{Parameter} & ")
             .append("\\textbf{Range} & ")
             .append("\\textbf{Sensitivity} & ")
             .append("\\textbf{Optimal} & ")
             .append("\\textbf{Impact} \\\\\n");
        table.append(TABLE_MID_RULE);
        
        // Sort parameters by sensitivity
        List<Map.Entry<String, Map<String, Double>>> sortedParams = 
            sensitivityData.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(
                    e2.getValue().getOrDefault(SENSITIVITY_KEY, 0.0),
                    e1.getValue().getOrDefault(SENSITIVITY_KEY, 0.0)))
                .collect(Collectors.toList());
        
        for (Map.Entry<String, Map<String, Double>> entry : sortedParams) {
            String parameter = entry.getKey();
            Map<String, Double> data = entry.getValue();
            
            table.append(formatParameterName(parameter));
            table.append(" & [").append(data.get("min").intValue())
                 .append(", ").append(data.get("max").intValue()).append("]");
            table.append(" & ").append(String.format("%.3f", data.get(SENSITIVITY_KEY)));
            table.append(" & ").append(data.get("optimal").intValue());
            table.append(" & ").append(categorizeImpact(data.get(SENSITIVITY_KEY)));
            table.append(TABLE_ROW_END);
        }
        
        table.append(TABLE_BOTTOM_RULE);
        table.append(TABLE_END_TABULAR);
        table.append(TABLE_END);
        
        return table.toString();
    }
    
    /**
     * Formats tables for publication requirements.
     * 
     * @param tableContent Original table content
     * @return Formatted table content
     */
    public String formatForPublication(String tableContent) {
        LoggingManager.logInfo("Formatting table for publication");
        
        // Apply publication-specific formatting
        String formatted = tableContent;
        
        // Ensure proper spacing
        formatted = formatted.replace("\\toprule", "\\toprule\n");
        formatted = formatted.replace("\\midrule", "\\midrule\n");
        formatted = formatted.replace("\\bottomrule", "\\bottomrule\n");
        
        // Add spacing for readability
        formatted = formatted.replace("\\\\", "\\\\\n");
        
        // Ensure consistent decimal places
        formatted = ensureConsistentDecimals(formatted);
        
        return formatted;
    }
    
    /**
     * Saves table to file.
     * 
     * @param tableContent Table content
     * @param filename Filename without extension
     * @return Path to saved file
     */
    public Path saveTable(String tableContent, String filename) {
        try {
            String fullFilename = String.format("%s_%s.tex", filename, tableTimestamp);
            Path outputPath = Paths.get(TABLE_DIRECTORY, fullFilename);
            
            Files.write(outputPath, tableContent.getBytes());
            LoggingManager.logInfo("LaTeX table saved: " + outputPath);
            
            return outputPath;
        } catch (IOException e) {
            throw new ExperimentException("Failed to save LaTeX table", e);
        }
    }
    
    // Private helper methods
    
    /**
     * Repeats a string n times. Compatible with all Java versions.
     */
    private String repeatString(String str, int count) {
        // Manual implementation compatible with all Java versions
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
    
    private String formatAlgorithmName(String algorithm) {
        Map<String, String> nameMap = new HashMap<>();
        nameMap.put("HippopotamusOptimization", "HO");
        nameMap.put("GeneticAlgorithm", "GA");
        nameMap.put("ParticleSwarm", "PSO");
        nameMap.put("AntColony", "ACO");
        nameMap.put("FirstFit", "FF");
        nameMap.put("BestFit", "BF");
        nameMap.put("Random", "Rand");
        
        return nameMap.getOrDefault(algorithm, algorithm);
    }
    
    private String formatMetricName(String metric) {
        Map<String, String> metricMap = new HashMap<>();
        metricMap.put(METRIC_RESOURCE_UTILIZATION, "Resource Util.");
        metricMap.put(METRIC_POWER_CONSUMPTION, "Power");
        metricMap.put(METRIC_SLA_VIOLATIONS, "SLA Viol.");
        metricMap.put(METRIC_RESPONSE_TIME, "Response Time");
        metricMap.put(METRIC_THROUGHPUT, "Throughput");
        
        return metricMap.getOrDefault(metric, metric);
    }
    
    private String formatParameterName(String parameter) {
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("populationSize", "Population Size");
        paramMap.put("maxIterations", "Max Iterations");
        paramMap.put("alpha", "$\\alpha$");
        paramMap.put("beta", "$\\beta$");
        paramMap.put("convergenceThreshold", "Conv. Threshold");
        
        return paramMap.getOrDefault(parameter, parameter);
    }
    
    private String formatPValue(double pValue) {
        if (pValue < 0.001) {
            return "$< 0.001$";
        } else if (pValue < 0.01) {
            return "$< 0.01$";
        } else if (pValue < 0.05) {
            return "$< 0.05$";
        } else {
            return String.format("%.3f", pValue);
        }
    }
    
    private String categorizeImpact(double sensitivity) {
        if (sensitivity > 0.7) {
            return "High";
        } else if (sensitivity > 0.3) {
            return "Medium";
        } else {
            return "Low";
        }
    }
    
    private DescriptiveStatistics calculateStats(List<ExperimentalResult> results, String metric) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        
        for (ExperimentalResult result : results) {
            Double value = result.getMetrics().get(metric);
            if (value != null) {
                stats.addValue(value);
            }
        }
        
        return stats;
    }
    
    private Map<String, Double> calculateAverageMetrics(List<ExperimentalResult> results) {
        Map<String, Double> averages = new HashMap<>();
        
        String[] metrics = {METRIC_RESOURCE_UTILIZATION, METRIC_POWER_CONSUMPTION, 
                           METRIC_SLA_VIOLATIONS, METRIC_RESPONSE_TIME};
        
        for (String metric : metrics) {
            DescriptiveStatistics stats = calculateStats(results, metric);
            averages.put(metric, stats.getMean());
        }
        
        return averages;
    }
    
    private List<Map.Entry<String, Double>> sortAlgorithmsByPerformance(
            Map<String, List<ExperimentalResult>> results) {
        
        Map<String, Double> scores = new HashMap<>();
        
        for (Map.Entry<String, List<ExperimentalResult>> entry : results.entrySet()) {
            Map<String, Double> metrics = calculateAverageMetrics(entry.getValue());
            
            // Simple scoring: higher utilization and lower power is better
            double score = metrics.get(METRIC_RESOURCE_UTILIZATION) - 
                          (metrics.get(METRIC_POWER_CONSUMPTION) / 100.0) -
                          metrics.get(METRIC_SLA_VIOLATIONS);
            
            scores.put(entry.getKey(), score);
        }
        
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .collect(Collectors.toList());
    }
    
    private double calculateImprovement(Map<String, Double> metrics, Map<String, Double> baseline) {
        // Calculate overall improvement percentage
        double utilImprovement = (metrics.get(METRIC_RESOURCE_UTILIZATION) - baseline.get(METRIC_RESOURCE_UTILIZATION)) 
                                / baseline.get(METRIC_RESOURCE_UTILIZATION);
        double powerImprovement = (baseline.get(METRIC_POWER_CONSUMPTION) - metrics.get(METRIC_POWER_CONSUMPTION)) 
                                 / baseline.get(METRIC_POWER_CONSUMPTION);
        double slaImprovement = (baseline.get(METRIC_SLA_VIOLATIONS) - metrics.get(METRIC_SLA_VIOLATIONS)) 
                               / Math.max(baseline.get(METRIC_SLA_VIOLATIONS), 1.0);
        
        // Weighted average
        return (utilImprovement * 0.4 + powerImprovement * 0.3 + slaImprovement * 0.3) * 100;
    }
    
    private String ensureConsistentDecimals(String content) {
        // Regular expression to find decimal numbers and format them to 2 decimal places
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+\\.\\d{3,})");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            double value = Double.parseDouble(matcher.group());
            matcher.appendReplacement(sb, String.format("%.2f", value));
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
}
