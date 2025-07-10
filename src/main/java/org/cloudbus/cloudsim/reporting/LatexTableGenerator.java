package org.cloudbus.cloudsim.reporting;

import org.cloudbus.cloudsim.experiment.ExperimentalResult;
import org.cloudbus.cloudsim.analyzer.ScalabilityResults;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.ValidationUtils;
import org.cloudbus.cloudsim.core.ExperimentException;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.*;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates LaTeX tables for research publication, including results tables,
 * comparison tables, and statistical test tables with proper formatting.
 * 
 * @author Research Team
 * @since CloudSim Toolkit 1.0
 */
public class LatexTableGenerator {
    
    private static final String TABLE_DIRECTORY = "results/latex_tables/";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");
    private static final DecimalFormat SCIENTIFIC_FORMAT = new DecimalFormat("0.00E0");
    
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
        table.append("\\begin{table}[htbp]\n");
        table.append("\\centering\n");
        table.append("\\caption{").append(caption).append("}\n");
        table.append("\\label{tab:").append(tableId).append("}\n");
        
        // Determine number of algorithms
        int numAlgorithms = results.size();
        String columnSpec = "l" + "r".repeat(numAlgorithms);
        
        table.append("\\begin{tabular}{").append(columnSpec).append("}\n");
        table.append("\\toprule\n");
        
        // Header row
        table.append("\\textbf{Metric}");
        for (String algorithm : results.keySet()) {
            table.append(" & \\textbf{").append(formatAlgorithmName(algorithm)).append("}");
        }
        table.append(" \\\\\n");
        table.append("\\midrule\n");
        
        // Metric rows
        String[] metrics = {
            "Resource Utilization (\\%)",
            "Power Consumption (kWh)",
            "SLA Violations",
            "Response Time (ms)",
            "Throughput (VMs/s)"
        };
        
        String[] metricKeys = {
            "resourceUtilization",
            "powerConsumption",
            "slaViolations",
            "responseTime",
            "throughput"
        };
        
        for (int i = 0; i < metrics.length; i++) {
            table.append(metrics[i]);
            
            for (String algorithm : results.keySet()) {
                List<ExperimentalResult> algorithmResults = results.get(algorithm);
                DescriptiveStatistics stats = calculateStats(algorithmResults, metricKeys[i]);
                
                table.append(" & ");
                if (metricKeys[i].equals("slaViolations")) {
                    table.append(String.format("%.0f", stats.getMean()));
                } else {
                    table.append(String.format("%.2f $\\pm$ %.2f", stats.getMean(), stats.getStandardDeviation()));
                }
            }
            table.append(" \\\\\n");
        }
        
        // Bottom rule
        table.append("\\bottomrule\n");
        table.append("\\end{tabular}\n");
        table.append("\\end{table}\n");
        
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
        table.append("\\begin{table}[htbp]\n");
        table.append("\\centering\n");
        table.append("\\caption{").append(caption).append("}\n");
        table.append("\\label{tab:").append(tableId).append("}\n");
        table.append("\\begin{tabular}{lrrrrr}\n");
        table.append("\\toprule\n");
        
        // Header
        table.append("\\textbf{Algorithm} & ")
             .append("\\textbf{Util. (\\%)} & ")
             .append("\\textbf{Power (kWh)} & ")
             .append("\\textbf{SLA Viol.} & ")
             .append("\\textbf{Resp. (ms)} & ")
             .append("\\textbf{Improvement}\\\\\n");
        table.append("\\midrule\n");
        
        // Sort algorithms by performance
        List<Map.Entry<String, Double>> sortedAlgorithms = sortAlgorithmsByPerformance(results);
        
        for (Map.Entry<String, Double> entry : sortedAlgorithms) {
            String algorithm = entry.getKey();
            Map<String, Double> metrics = calculateAverageMetrics(results.get(algorithm));
            
            table.append(formatAlgorithmName(algorithm));
            table.append(" & ").append(String.format("%.1f", metrics.get("resourceUtilization")));
            table.append(" & ").append(String.format("%.0f", metrics.get("powerConsumption")));
            table.append(" & ").append(String.format("%.0f", metrics.get("slaViolations")));
            table.append(" & ").append(String.format("%.1f", metrics.get("responseTime")));
            
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
            
            table.append(" \\\\\n");
        }
        
        table.append("\\bottomrule\n");
        table.append("\\end{tabular}\n");
        table.append("\\end{table}\n");
        
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
        
        table.append("\\begin{table}[htbp]\n");
        table.append("\\centering\n");
        table.append("\\caption{").append(caption).append("}\n");
        table.append("\\label{tab:").append(tableId).append("}\n");
        table.append("\\begin{tabular}{llrrrr}\n");
        table.append("\\toprule\n");
        
        // Header
        table.append("\\textbf{Comparison} & ")
             .append("\\textbf{Metric} & ")
             .append("\\textbf{t-statistic} & ")
             .append("\\textbf{p-value} & ")
             .append("\\textbf{Effect Size} & ")
             .append("\\textbf{Sig.} \\\\\n");
        table.append("\\midrule\n");
        
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
                    table.append(" \\\\\n");
                }
                
                table.append("\\midrule\n");
            }
        }
        
        table.append("\\bottomrule\n");
        table.append("\\multicolumn{6}{l}{\\footnotesize ");
        table.append("Significance level: $\\alpha = 0.05$ with Bonferroni correction}\\\\\n");
        table.append("\\end{tabular}\n");
        table.append("\\end{table}\n");
        
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
        
        table.append("\\begin{table}[htbp]\n");
        table.append("\\centering\n");
        table.append("\\caption{").append(caption).append("}\n");
        table.append("\\label{tab:").append(tableId).append("}\n");
        
        // Determine algorithms
        Set<String> algorithms = scalabilityResults.keySet();
        String columnSpec = "r" + "r".repeat(algorithms.size());
        
        table.append("\\begin{tabular}{").append(columnSpec).append("}\n");
        table.append("\\toprule\n");
        
        // Header
        table.append("\\textbf{VMs}");
        for (String algorithm : algorithms) {
            table.append(" & \\textbf{").append(formatAlgorithmName(algorithm)).append("}");
        }
        table.append(" \\\\\n");
        table.append("\\midrule\n");
        
        // Get problem sizes
        List<Integer> problemSizes = scalabilityResults.values().iterator().next()
            .getExecutionTimes().keySet().stream()
            .sorted()
            .collect(Collectors.toList());
        
        // Data rows
        for (Integer size : problemSizes) {
            table.append(size);
            
            for (String algorithm : algorithms) {
                ScalabilityResults results = scalabilityResults.get(algorithm);
                Double time = results.getExecutionTimes().get(size);
                
                table.append(" & ");
                if (time != null) {
                    table.append(String.format("%.2f", time));
                } else {
                    table.append("--");
                }
            }
            table.append(" \\\\\n");
        }
        
        // Add complexity row
        table.append("\\midrule\n");
        table.append("\\textbf{Complexity}");
        for (String algorithm : algorithms) {
            ScalabilityResults results = scalabilityResults.get(algorithm);
            table.append(" & ").append(results.getTimeComplexity());
        }
        table.append(" \\\\\n");
        
        table.append("\\bottomrule\n");
        table.append("\\end{tabular}\n");
        table.append("\\end{table}\n");
        
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
        
        table.append("\\begin{table}[htbp]\n");
        table.append("\\centering\n");
        table.append("\\caption{").append(caption).append("}\n");
        table.append("\\label{tab:").append(tableId).append("}\n");
        table.append("\\begin{tabular}{lrrrr}\n");
        table.append("\\toprule\n");
        
        // Header
        table.append("\\textbf{Parameter} & ")
             .append("\\textbf{Range} & ")
             .append("\\textbf{Sensitivity} & ")
             .append("\\textbf{Optimal} & ")
             .append("\\textbf{Impact} \\\\\n");
        table.append("\\midrule\n");
        
        // Sort parameters by sensitivity
        List<Map.Entry<String, Map<String, Double>>> sortedParams = 
            sensitivityData.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(
                    e2.getValue().getOrDefault("sensitivity", 0.0),
                    e1.getValue().getOrDefault("sensitivity", 0.0)))
                .collect(Collectors.toList());
        
        for (Map.Entry<String, Map<String, Double>> entry : sortedParams) {
            String parameter = entry.getKey();
            Map<String, Double> data = entry.getValue();
            
            table.append(formatParameterName(parameter));
            table.append(" & [").append(data.get("min").intValue())
                 .append(", ").append(data.get("max").intValue()).append("]");
            table.append(" & ").append(String.format("%.3f", data.get("sensitivity")));
            table.append(" & ").append(data.get("optimal").intValue());
            table.append(" & ").append(categorizeImpact(data.get("sensitivity")));
            table.append(" \\\\\n");
        }
        
        table.append("\\bottomrule\n");
        table.append("\\end{tabular}\n");
        table.append("\\end{table}\n");
        
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
        metricMap.put("resourceUtilization", "Resource Util.");
        metricMap.put("powerConsumption", "Power");
        metricMap.put("slaViolations", "SLA Viol.");
        metricMap.put("responseTime", "Response Time");
        metricMap.put("throughput", "Throughput");
        
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
            Double value = result.getMetric(metric);
            if (value != null) {
                stats.addValue(value);
            }
        }
        
        return stats;
    }
    
    private Map<String, Double> calculateAverageMetrics(List<ExperimentalResult> results) {
        Map<String, Double> averages = new HashMap<>();
        
        String[] metrics = {"resourceUtilization", "powerConsumption", "slaViolations", "responseTime"};
        
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
            double score = metrics.get("resourceUtilization") - 
                          (metrics.get("powerConsumption") / 100.0) -
                          metrics.get("slaViolations");
            
            scores.put(entry.getKey(), score);
        }
        
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .collect(Collectors.toList());
    }
    
    private double calculateImprovement(Map<String, Double> metrics, Map<String, Double> baseline) {
        // Calculate overall improvement percentage
        double utilImprovement = (metrics.get("resourceUtilization") - baseline.get("resourceUtilization")) 
                                / baseline.get("resourceUtilization");
        double powerImprovement = (baseline.get("powerConsumption") - metrics.get("powerConsumption")) 
                                 / baseline.get("powerConsumption");
        double slaImprovement = (baseline.get("slaViolations") - metrics.get("slaViolations")) 
                               / Math.max(baseline.get("slaViolations"), 1.0);
        
        // Weighted average
        return (utilImprovement * 0.4 + powerImprovement * 0.3 + slaImprovement * 0.3) * 100;
    }
    
    private String ensureConsistentDecimals(String content) {
        // Regular expression to find decimal numbers
        return content.replaceAll("(\\d+\\.\\d{3,})", match -> {
            double value = Double.parseDouble(match.group());
            return String.format("%.2f", value);
        });
    }
}
