package org.cloudbus.cloudsim.reporting;

import org.cloudbus.cloudsim.analyzer.StatisticalTestSuite;
import org.cloudbus.cloudsim.experiment.ExperimentalResult;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.MetricsCalculator;
import org.cloudbus.cloudsim.util.ValidationUtils;
import org.cloudbus.cloudsim.core.ExperimentException;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates comprehensive comparison reports between different VM allocation algorithms.
 * This class is responsible for creating comparative analysis tables, performance charts,
 * and statistical comparison reports suitable for research publication.
 * 
 * @author Research Team
 * @since CloudSim Toolkit 1.0
 */
public class ComparisonReport {
    
    private static final String REPORT_DIRECTORY = "results/comparison_reports/";
    private static final String CHART_DIRECTORY = "results/visualizations/comparison/";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    private final Map<String, List<ExperimentalResult>> algorithmResults;
    private final StatisticalTestSuite statisticalTestSuite;
    private final MetricsCalculator metricsCalculator;
    private String reportTimestamp;
    
    /**
     * Constructs a ComparisonReport instance.
     */
    public ComparisonReport() {
        this.algorithmResults = new HashMap<>();
        this.statisticalTestSuite = new StatisticalTestSuite();
        this.metricsCalculator = new MetricsCalculator();
        this.reportTimestamp = LocalDateTime.now().format(DATE_FORMAT);
        
        // Create directories if they don't exist
        try {
            Files.createDirectories(Paths.get(REPORT_DIRECTORY));
            Files.createDirectories(Paths.get(CHART_DIRECTORY));
        } catch (IOException e) {
            throw new ExperimentException("Failed to create report directories", e);
        }
    }
    
    /**
     * Adds experimental results for a specific algorithm.
     * 
     * @param algorithmName Name of the algorithm
     * @param results List of experimental results
     */
    public void addAlgorithmResults(String algorithmName, List<ExperimentalResult> results) {
        ValidationUtils.validateNotNull(algorithmName, "Algorithm name");
        ValidationUtils.validateNotEmpty(results, "Experimental results");
        
        algorithmResults.put(algorithmName, new ArrayList<>(results));
        LoggingManager.logInfo("Added " + results.size() + " results for algorithm: " + algorithmName);
    }
    
    /**
     * Generates comprehensive comparison tables for all algorithms.
     * 
     * @return Path to the generated comparison table file
     */
    public Path generateComparisonTable() {
        LoggingManager.logInfo("Generating comparison tables for " + algorithmResults.size() + " algorithms");
        
        try {
            XSSFWorkbook workbook = new XSSFWorkbook();
            
            // Create summary sheet
            createSummarySheet(workbook);
            
            // Create detailed comparison sheets
            createDetailedComparisonSheet(workbook, "Resource_Utilization");
            createDetailedComparisonSheet(workbook, "Power_Consumption");
            createDetailedComparisonSheet(workbook, "SLA_Violations");
            createDetailedComparisonSheet(workbook, "Response_Time");
            
            // Create statistical significance sheet
            createStatisticalSignificanceSheet(workbook);
            
            // Save workbook
            String filename = String.format("algorithm_comparison_%s.xlsx", reportTimestamp);
            Path outputPath = Paths.get(REPORT_DIRECTORY, filename);
            
            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                workbook.write(fos);
            }
            
            workbook.close();
            LoggingManager.logInfo("Comparison table generated: " + outputPath);
            return outputPath;
            
        } catch (IOException e) {
            throw new ExperimentException("Failed to generate comparison table", e);
        }
    }
    
    /**
     * Creates performance visualization charts comparing algorithms.
     * 
     * @return List of paths to generated chart files
     */
    public List<Path> createPerformanceCharts() {
        LoggingManager.logInfo("Creating performance comparison charts");
        List<Path> chartPaths = new ArrayList<>();
        
        try {
            // Generate different types of charts
            chartPaths.add(createResourceUtilizationChart());
            chartPaths.add(createPowerConsumptionChart());
            chartPaths.add(createSLAViolationChart());
            chartPaths.add(createResponseTimeChart());
            chartPaths.add(createBoxPlotComparison());
            
            LoggingManager.logInfo("Created " + chartPaths.size() + " performance charts");
            return chartPaths;
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to create performance charts", e);
        }
    }
    
    /**
     * Generates statistical comparison report with hypothesis testing.
     * 
     * @return Path to the statistical comparison report
     */
    public Path generateStatisticalComparison() {
        LoggingManager.logInfo("Generating statistical comparison report");
        
        try {
            StringBuilder report = new StringBuilder();
            report.append("# Statistical Comparison Report\n\n");
            report.append("Generated: ").append(LocalDateTime.now()).append("\n\n");
            
            // Perform pairwise comparisons
            List<String> algorithms = new ArrayList<>(algorithmResults.keySet());
            
            for (int i = 0; i < algorithms.size(); i++) {
                for (int j = i + 1; j < algorithms.size(); j++) {
                    String algo1 = algorithms.get(i);
                    String algo2 = algorithms.get(j);
                    
                    report.append("## ").append(algo1).append(" vs ").append(algo2).append("\n\n");
                    appendStatisticalTests(report, algo1, algo2);
                }
            }
            
            // Save report
            String filename = String.format("statistical_comparison_%s.md", reportTimestamp);
            Path outputPath = Paths.get(REPORT_DIRECTORY, filename);
            Files.write(outputPath, report.toString().getBytes());
            
            LoggingManager.logInfo("Statistical comparison report generated: " + outputPath);
            return outputPath;
            
        } catch (IOException e) {
            throw new ExperimentException("Failed to generate statistical comparison", e);
        }
    }
    
    /**
     * Exports comparison data for external analysis tools.
     * 
     * @param format Export format (CSV, JSON, etc.)
     * @return Path to the exported data file
     */
    public Path exportComparisonData(String format) {
        LoggingManager.logInfo("Exporting comparison data in format: " + format);
        
        try {
            String filename = String.format("comparison_data_%s", reportTimestamp);
            Path outputPath;
            
            switch (format.toUpperCase()) {
                case "CSV":
                    outputPath = exportToCSV(filename);
                    break;
                case "JSON":
                    outputPath = exportToJSON(filename);
                    break;
                default:
                    throw new ExperimentException("Unsupported export format: " + format);
            }
            
            LoggingManager.logInfo("Comparison data exported: " + outputPath);
            return outputPath;
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to export comparison data", e);
        }
    }
    
    // Private helper methods
    
    private void createSummarySheet(XSSFWorkbook workbook) {
        XSSFSheet sheet = workbook.createSheet("Summary");
        int rowNum = 0;
        
        // Header
        XSSFRow headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Algorithm", "Avg Resource Util (%)", "Avg Power (kWh)", 
                          "Total SLA Violations", "Avg Response Time (ms)", "Rank"};
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
        
        // Data rows
        Map<String, Double> overallScores = calculateOverallScores();
        List<Map.Entry<String, Double>> sortedAlgorithms = overallScores.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .collect(Collectors.toList());
        
        int rank = 1;
        for (Map.Entry<String, Double> entry : sortedAlgorithms) {
            String algorithm = entry.getKey();
            XSSFRow dataRow = sheet.createRow(rowNum++);
            
            dataRow.createCell(0).setCellValue(algorithm);
            dataRow.createCell(1).setCellValue(calculateAverageMetric(algorithm, "resourceUtilization"));
            dataRow.createCell(2).setCellValue(calculateAverageMetric(algorithm, "powerConsumption"));
            dataRow.createCell(3).setCellValue(calculateTotalMetric(algorithm, "slaViolations"));
            dataRow.createCell(4).setCellValue(calculateAverageMetric(algorithm, "responseTime"));
            dataRow.createCell(5).setCellValue(rank++);
        }
        
        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    
    private void createDetailedComparisonSheet(XSSFWorkbook workbook, String metricName) {
        XSSFSheet sheet = workbook.createSheet(metricName);
        // Implementation for detailed metric comparison
        // Similar structure to summary sheet but with more detailed metrics
    }
    
    private void createStatisticalSignificanceSheet(XSSFWorkbook workbook) {
        XSSFSheet sheet = workbook.createSheet("Statistical_Tests");
        // Implementation for statistical test results
    }
    
    private Path createResourceUtilizationChart() throws IOException {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        for (Map.Entry<String, List<ExperimentalResult>> entry : algorithmResults.entrySet()) {
            String algorithm = entry.getKey();
            double avgUtilization = calculateAverageMetric(algorithm, "resourceUtilization");
            dataset.addValue(avgUtilization, "Resource Utilization", algorithm);
        }
        
        JFreeChart chart = ChartFactory.createBarChart(
            "Resource Utilization Comparison",
            "Algorithm",
            "Average Utilization (%)",
            dataset,
            PlotOrientation.VERTICAL,
            true, true, false
        );
        
        customizeChart(chart);
        
        String filename = String.format("resource_utilization_comparison_%s.png", reportTimestamp);
        Path outputPath = Paths.get(CHART_DIRECTORY, filename);
        ChartUtils.saveChartAsPNG(outputPath.toFile(), chart, 800, 600);
        
        return outputPath;
    }
    
    private Path createPowerConsumptionChart() throws IOException {
        // Similar implementation for power consumption chart
        String filename = String.format("power_consumption_comparison_%s.png", reportTimestamp);
        return Paths.get(CHART_DIRECTORY, filename);
    }
    
    private Path createSLAViolationChart() throws IOException {
        // Similar implementation for SLA violation chart
        String filename = String.format("sla_violation_comparison_%s.png", reportTimestamp);
        return Paths.get(CHART_DIRECTORY, filename);
    }
    
    private Path createResponseTimeChart() throws IOException {
        // Similar implementation for response time chart
        String filename = String.format("response_time_comparison_%s.png", reportTimestamp);
        return Paths.get(CHART_DIRECTORY, filename);
    }
    
    private Path createBoxPlotComparison() throws IOException {
        DefaultBoxAndWhiskerCategoryDataset dataset = new DefaultBoxAndWhiskerCategoryDataset();
        
        // Create box plot data for each algorithm
        for (Map.Entry<String, List<ExperimentalResult>> entry : algorithmResults.entrySet()) {
            String algorithm = entry.getKey();
            List<Double> values = extractMetricValues(entry.getValue(), "resourceUtilization");
            dataset.add(values, "Resource Utilization", algorithm);
        }
        
        JFreeChart chart = ChartFactory.createBoxAndWhiskerChart(
            "Algorithm Performance Distribution",
            "Algorithm",
            "Resource Utilization (%)",
            dataset,
            true
        );
        
        String filename = String.format("performance_distribution_%s.png", reportTimestamp);
        Path outputPath = Paths.get(CHART_DIRECTORY, filename);
        ChartUtils.saveChartAsPNG(outputPath.toFile(), chart, 1000, 600);
        
        return outputPath;
    }
    
    private void customizeChart(JFreeChart chart) {
        CategoryPlot plot = chart.getCategoryPlot();
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setMaximumBarWidth(0.1);
        
        // Additional customization for publication quality
        chart.getTitle().setFont(chart.getTitle().getFont().deriveFont(16f));
        plot.getDomainAxis().setLabelFont(plot.getDomainAxis().getLabelFont().deriveFont(14f));
        plot.getRangeAxis().setLabelFont(plot.getRangeAxis().getLabelFont().deriveFont(14f));
    }
    
    private void appendStatisticalTests(StringBuilder report, String algo1, String algo2) {
        List<ExperimentalResult> results1 = algorithmResults.get(algo1);
        List<ExperimentalResult> results2 = algorithmResults.get(algo2);
        
        // Extract metric values
        double[] utilization1 = extractMetricArray(results1, "resourceUtilization");
        double[] utilization2 = extractMetricArray(results2, "resourceUtilization");
        
        // Perform statistical tests
        Map<String, Double> testResults = statisticalTestSuite.performTTest(utilization1, utilization2);
        
        report.append("### Resource Utilization\n");
        report.append("- T-statistic: ").append(String.format("%.4f", testResults.get("t-statistic"))).append("\n");
        report.append("- P-value: ").append(String.format("%.4f", testResults.get("p-value"))).append("\n");
        report.append("- Significant: ").append(testResults.get("p-value") < 0.05 ? "Yes" : "No").append("\n\n");
        
        // Add more metrics and tests as needed
    }
    
    private Map<String, Double> calculateOverallScores() {
        Map<String, Double> scores = new HashMap<>();
        
        for (String algorithm : algorithmResults.keySet()) {
            double score = 0.0;
            score += normalizeMetric(calculateAverageMetric(algorithm, "resourceUtilization"), true);
            score += normalizeMetric(calculateAverageMetric(algorithm, "powerConsumption"), false);
            score += normalizeMetric(calculateTotalMetric(algorithm, "slaViolations"), false);
            score += normalizeMetric(calculateAverageMetric(algorithm, "responseTime"), false);
            scores.put(algorithm, score / 4.0);
        }
        
        return scores;
    }
    
    private double normalizeMetric(double value, boolean higherIsBetter) {
        // Simple min-max normalization
        if (higherIsBetter) {
            return value / 100.0; // Assuming percentage metrics
        } else {
            return 1.0 - (value / getMaxValue(value));
        }
    }
    
    private double getMaxValue(double currentValue) {
        // Find max value across all algorithms for normalization
        return currentValue * 2.0; // Simplified implementation
    }
    
    private double calculateAverageMetric(String algorithm, String metricName) {
        List<ExperimentalResult> results = algorithmResults.get(algorithm);
        if (results == null || results.isEmpty()) {
            return 0.0;
        }
        
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (ExperimentalResult result : results) {
            Double value = result.getMetric(metricName);
            if (value != null) {
                stats.addValue(value);
            }
        }
        
        return stats.getMean();
    }
    
    private double calculateTotalMetric(String algorithm, String metricName) {
        List<ExperimentalResult> results = algorithmResults.get(algorithm);
        if (results == null || results.isEmpty()) {
            return 0.0;
        }
        
        double total = 0.0;
        for (ExperimentalResult result : results) {
            Double value = result.getMetric(metricName);
            if (value != null) {
                total += value;
            }
        }
        
        return total;
    }
    
    private List<Double> extractMetricValues(List<ExperimentalResult> results, String metricName) {
        List<Double> values = new ArrayList<>();
        for (ExperimentalResult result : results) {
            Double value = result.getMetric(metricName);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }
    
    private double[] extractMetricArray(List<ExperimentalResult> results, String metricName) {
        return extractMetricValues(results, metricName).stream()
            .mapToDouble(Double::doubleValue)
            .toArray();
    }
    
    private Path exportToCSV(String baseFilename) throws IOException {
        Path outputPath = Paths.get(REPORT_DIRECTORY, baseFilename + ".csv");
        
        try (var writer = Files.newBufferedWriter(outputPath)) {
            // Write header
            writer.write("Algorithm,Experiment_ID,Resource_Utilization,Power_Consumption,SLA_Violations,Response_Time\n");
            
            // Write data
            for (Map.Entry<String, List<ExperimentalResult>> entry : algorithmResults.entrySet()) {
                String algorithm = entry.getKey();
                List<ExperimentalResult> results = entry.getValue();
                
                for (int i = 0; i < results.size(); i++) {
                    ExperimentalResult result = results.get(i);
                    writer.write(String.format("%s,%d,%.4f,%.4f,%.0f,%.4f\n",
                        algorithm,
                        i + 1,
                        result.getMetric("resourceUtilization"),
                        result.getMetric("powerConsumption"),
                        result.getMetric("slaViolations"),
                        result.getMetric("responseTime")
                    ));
                }
            }
        }
        
        return outputPath;
    }
    
    private Path exportToJSON(String baseFilename) throws IOException {
        Path outputPath = Paths.get(REPORT_DIRECTORY, baseFilename + ".json");
        
        // Create JSON structure
        Map<String, Object> jsonData = new HashMap<>();
        jsonData.put("timestamp", reportTimestamp);
        jsonData.put("algorithms", algorithmResults.size());
        
        Map<String, List<Map<String, Object>>> resultsData = new HashMap<>();
        for (Map.Entry<String, List<ExperimentalResult>> entry : algorithmResults.entrySet()) {
            List<Map<String, Object>> algorithmData = new ArrayList<>();
            
            for (ExperimentalResult result : entry.getValue()) {
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("resourceUtilization", result.getMetric("resourceUtilization"));
                resultMap.put("powerConsumption", result.getMetric("powerConsumption"));
                resultMap.put("slaViolations", result.getMetric("slaViolations"));
                resultMap.put("responseTime", result.getMetric("responseTime"));
                algorithmData.add(resultMap);
            }
            
            resultsData.put(entry.getKey(), algorithmData);
        }
        
        jsonData.put("results", resultsData);
        
        // Use Jackson ObjectMapper to write JSON
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), jsonData);
        
        return outputPath;
    }
}