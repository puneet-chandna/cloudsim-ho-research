package org.cloudbus.cloudsim.analyzer;

import org.cloudbus.cloudsim.experiment.ExperimentalResult;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.MetricsCalculator;
import org.cloudbus.cloudsim.util.ValidationUtils;
import org.cloudbus.cloudsim.reporting.VisualizationGenerator;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.BoxAndWhiskerRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.statistics.BoxAndWhiskerCategoryDataset;
import org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PerformanceMetricsAnalyzer - Comprehensive analysis of VM placement performance metrics
 * Part of the research framework for evaluating VM placement algorithms
 * 
 * Research objectives addressed:
 * - Analyze resource utilization patterns and efficiency
 * - Evaluate power consumption and energy efficiency
 * - Measure throughput and response time characteristics
 * - Identify performance bottlenecks and optimization opportunities
 *  @author Puneet Chandna
 */
public class PerformanceMetricsAnalyzer {
    
    private final Map<String, List<ExperimentalResult>> algorithmResults;
    private final Map<String, PerformanceProfile> performanceProfiles;
    private final Map<String, Map<String, DescriptiveStatistics>> metricStatistics;
    
    // Metric categories
    public static final String RESOURCE_UTILIZATION = "resource_utilization";
    public static final String POWER_CONSUMPTION = "power_consumption";
    public static final String THROUGHPUT = "throughput";
    public static final String RESPONSE_TIME = "response_time";
    public static final String VM_MIGRATIONS = "vm_migrations";
    public static final String LOAD_BALANCE = "load_balance";
    
    // Performance thresholds
    private static final double OPTIMAL_UTILIZATION_MIN = 0.6;
    private static final double OPTIMAL_UTILIZATION_MAX = 0.85;
    private static final double CRITICAL_RESPONSE_TIME = 1000.0; // ms
    private static final double POWER_EFFICIENCY_THRESHOLD = 0.8;
    
    /**
     * Performance profile for an algorithm
     */
    public static class PerformanceProfile {
        public String algorithmName;
        public Map<String, Double> averageMetrics;
        public Map<String, Double> metricVariability;
        public Map<String, Double> percentileMetrics;
        public Map<String, List<Double>> timeSeriesMetrics;
        public Map<String, String> performanceClassification;
        public double overallScore;
        
        public PerformanceProfile(String algorithmName) {
            this.algorithmName = algorithmName;
            this.averageMetrics = new HashMap<>();
            this.metricVariability = new HashMap<>();
            this.percentileMetrics = new HashMap<>();
            this.timeSeriesMetrics = new HashMap<>();
            this.performanceClassification = new HashMap<>();
        }
    }
    
    public PerformanceMetricsAnalyzer() {
        this.algorithmResults = new HashMap<>();
        this.performanceProfiles = new HashMap<>();
        this.metricStatistics = new HashMap<>();
    }
    
    /**
     * Calculate and analyze resource utilization metrics
     */
    public Map<String, Object> calculateResourceUtilization(List<ExperimentalResult> results) {
        LoggingManager.logInfo("Calculating resource utilization metrics");
        
        Map<String, Object> utilizationAnalysis = new HashMap<>();
        
        try {
            // Group results by algorithm
            Map<String, List<ExperimentalResult>> byAlgorithm = results.stream()
                .collect(Collectors.groupingBy(r -> r.getAlgorithmName()));
            
            // Store for later use
            algorithmResults.putAll(byAlgorithm);
            
            // Analyze utilization for each algorithm
            Map<String, Map<String, Double>> algorithmUtilization = new HashMap<>();
            
            for (Map.Entry<String, List<ExperimentalResult>> entry : byAlgorithm.entrySet()) {
                String algorithm = entry.getKey();
                List<ExperimentalResult> algoResults = entry.getValue();
                
                Map<String, Double> utilMetrics = analyzeUtilizationMetrics(algoResults);
                algorithmUtilization.put(algorithm, utilMetrics);
                
                // Update performance profile
                updatePerformanceProfile(algorithm, RESOURCE_UTILIZATION, algoResults);
            }
            
            // Calculate overall utilization statistics
            Map<String, Double> overallStats = calculateOverallUtilizationStats(results);
            
            // Identify utilization patterns
            Map<String, String> utilizationPatterns = identifyUtilizationPatterns(byAlgorithm);
            
            // Calculate utilization efficiency
            Map<String, Double> efficiencyScores = calculateUtilizationEfficiency(byAlgorithm);
            
            // Generate utilization distribution analysis
            Map<String, Object> distributionAnalysis = analyzeUtilizationDistribution(byAlgorithm);
            
            utilizationAnalysis.put("algorithm_utilization", algorithmUtilization);
            utilizationAnalysis.put("overall_statistics", overallStats);
            utilizationAnalysis.put("utilization_patterns", utilizationPatterns);
            utilizationAnalysis.put("efficiency_scores", efficiencyScores);
            utilizationAnalysis.put("distribution_analysis", distributionAnalysis);
            utilizationAnalysis.put("optimal_algorithm", identifyOptimalUtilization(efficiencyScores));
            
        } catch (Exception e) {
            LoggingManager.logError("Error calculating resource utilization", e);
            throw new ExperimentException("Failed to calculate resource utilization", e);
        }
        
        return utilizationAnalysis;
    }
    
    /**
     * Analyze power consumption patterns
     */
    public Map<String, Object> analyzePowerConsumption(List<ExperimentalResult> results) {
        LoggingManager.logInfo("Analyzing power consumption metrics");
        
        Map<String, Object> powerAnalysis = new HashMap<>();
        
        try {
            Map<String, List<ExperimentalResult>> byAlgorithm = results.stream()
                .collect(Collectors.groupingBy(r -> r.getAlgorithmName()));
            
            // Analyze power consumption for each algorithm
            Map<String, Map<String, Double>> algorithmPower = new HashMap<>();
            
            for (Map.Entry<String, List<ExperimentalResult>> entry : byAlgorithm.entrySet()) {
                String algorithm = entry.getKey();
                List<ExperimentalResult> algoResults = entry.getValue();
                
                Map<String, Double> powerMetrics = analyzePowerMetrics(algoResults);
                algorithmPower.put(algorithm, powerMetrics);
                
                updatePerformanceProfile(algorithm, POWER_CONSUMPTION, algoResults);
            }
            
            // Calculate power efficiency metrics
            Map<String, Double> powerEfficiency = calculatePowerEfficiency(byAlgorithm);
            
            // Analyze power consumption patterns
            Map<String, Object> powerPatterns = analyzePowerPatterns(byAlgorithm);
            
            // Calculate PUE (Power Usage Effectiveness) metrics
            Map<String, Double> pueMetrics = calculatePUEMetrics(byAlgorithm);
            
            // Identify energy-saving opportunities
            List<String> energySavingOpportunities = identifyEnergySavingOpportunities(
                algorithmPower, powerEfficiency);
            
            powerAnalysis.put("algorithm_power_consumption", algorithmPower);
            powerAnalysis.put("power_efficiency", powerEfficiency);
            powerAnalysis.put("power_patterns", powerPatterns);
            powerAnalysis.put("pue_metrics", pueMetrics);
            powerAnalysis.put("energy_saving_opportunities", energySavingOpportunities);
            powerAnalysis.put("most_efficient_algorithm", 
                identifyMostPowerEfficient(powerEfficiency));
            
        } catch (Exception e) {
            LoggingManager.logError("Error analyzing power consumption", e);
            throw new ExperimentException("Failed to analyze power consumption", e);
        }
        
        return powerAnalysis;
    }
    
    /**
     * Calculate throughput metrics
     */
    public Map<String, Object> calculateThroughput(List<ExperimentalResult> results) {
        LoggingManager.logInfo("Calculating throughput metrics");
        
        Map<String, Object> throughputAnalysis = new HashMap<>();
        
        try {
            Map<String, List<ExperimentalResult>> byAlgorithm = results.stream()
                .collect(Collectors.groupingBy(r -> r.getAlgorithmName()));
            
            // Calculate throughput for each algorithm
            Map<String, Map<String, Double>> algorithmThroughput = new HashMap<>();
            
            for (Map.Entry<String, List<ExperimentalResult>> entry : byAlgorithm.entrySet()) {
                String algorithm = entry.getKey();
                List<ExperimentalResult> algoResults = entry.getValue();
                
                Map<String, Double> throughputMetrics = calculateThroughputMetrics(algoResults);
                algorithmThroughput.put(algorithm, throughputMetrics);
                
                updatePerformanceProfile(algorithm, THROUGHPUT, algoResults);
            }
            
            // Analyze throughput scalability
            Map<String, Object> scalabilityAnalysis = analyzeThroughputScalability(byAlgorithm);
            
            // Calculate throughput efficiency
            Map<String, Double> throughputEfficiency = calculateThroughputEfficiency(byAlgorithm);
            
            // Identify throughput bottlenecks
            Map<String, List<String>> bottlenecks = identifyThroughputBottlenecks(byAlgorithm);
            
            throughputAnalysis.put("algorithm_throughput", algorithmThroughput);
            throughputAnalysis.put("scalability_analysis", scalabilityAnalysis);
            throughputAnalysis.put("throughput_efficiency", throughputEfficiency);
            throughputAnalysis.put("bottlenecks", bottlenecks);
            throughputAnalysis.put("highest_throughput_algorithm", 
                identifyHighestThroughput(algorithmThroughput));
            
        } catch (Exception e) {
            LoggingManager.logError("Error calculating throughput", e);
            throw new ExperimentException("Failed to calculate throughput", e);
        }
        
        return throughputAnalysis;
    }
    
    /**
     * Analyze response time characteristics
     */
    public Map<String, Object> analyzeResponseTime(List<ExperimentalResult> results) {
        LoggingManager.logInfo("Analyzing response time metrics");
        
        Map<String, Object> responseTimeAnalysis = new HashMap<>();
        
        try {
            Map<String, List<ExperimentalResult>> byAlgorithm = results.stream()
                .collect(Collectors.groupingBy(r -> r.getAlgorithmName()));
            
            // Analyze response time for each algorithm
            Map<String, Map<String, Double>> algorithmResponseTime = new HashMap<>();
            
            for (Map.Entry<String, List<ExperimentalResult>> entry : byAlgorithm.entrySet()) {
                String algorithm = entry.getKey();
                List<ExperimentalResult> algoResults = entry.getValue();
                
                Map<String, Double> rtMetrics = analyzeResponseTimeMetrics(algoResults);
                algorithmResponseTime.put(algorithm, rtMetrics);
                
                updatePerformanceProfile(algorithm, RESPONSE_TIME, algoResults);
            }
            
            // Calculate response time percentiles
            Map<String, Map<String, Double>> percentiles = 
                calculateResponseTimePercentiles(byAlgorithm);
            
            // Analyze response time distribution
            Map<String, Object> distributionAnalysis = 
                analyzeResponseTimeDistribution(byAlgorithm);
            
            // Identify response time violations
            Map<String, Double> violations = identifyResponseTimeViolations(byAlgorithm);
            
            // Calculate jitter and stability metrics
            Map<String, Map<String, Double>> stabilityMetrics = 
                calculateResponseTimeStability(byAlgorithm);
            
            responseTimeAnalysis.put("algorithm_response_time", algorithmResponseTime);
            responseTimeAnalysis.put("percentiles", percentiles);
            responseTimeAnalysis.put("distribution_analysis", distributionAnalysis);
            responseTimeAnalysis.put("violations", violations);
            responseTimeAnalysis.put("stability_metrics", stabilityMetrics);
            responseTimeAnalysis.put("lowest_latency_algorithm", 
                identifyLowestLatency(algorithmResponseTime));
            
        } catch (Exception e) {
            LoggingManager.logError("Error analyzing response time", e);
            throw new ExperimentException("Failed to analyze response time", e);
        }
        
        return responseTimeAnalysis;
    }
    
    /**
     * Generate comprehensive performance report
     */
    public Map<String, Object> generatePerformanceReport() {
        LoggingManager.logInfo("Generating comprehensive performance report");
        
        Map<String, Object> report = new HashMap<>();
        
        try {
            // Calculate overall performance scores
            Map<String, Double> overallScores = calculateOverallPerformanceScores();
            
            // Generate performance rankings
            List<Map<String, Object>> rankings = generatePerformanceRankings();
            
            // Identify strengths and weaknesses
            Map<String, Map<String, String>> strengthsWeaknesses = 
                identifyStrengthsAndWeaknesses();
            
            // Generate performance recommendations
            Map<String, List<String>> recommendations = 
                generatePerformanceRecommendations();
            
            // Create performance matrix
            double[][] performanceMatrix = createPerformanceMatrix();
            
            // Generate visualizations
            Map<String, JFreeChart> visualizations = generatePerformanceVisualizations();
            
            report.put("overall_scores", overallScores);
            report.put("rankings", rankings);
            report.put("strengths_weaknesses", strengthsWeaknesses);
            report.put("recommendations", recommendations);
            report.put("performance_matrix", performanceMatrix);
            report.put("visualizations", visualizations);
            report.put("performance_profiles", performanceProfiles);
            
        } catch (Exception e) {
            LoggingManager.logError("Error generating performance report", e);
            throw new ExperimentException("Failed to generate performance report", e);
        }
        
        return report;
    }
    
    // Helper methods for resource utilization analysis
    
    private Map<String, Double> analyzeUtilizationMetrics(List<ExperimentalResult> results) {
        Map<String, Double> metrics = new HashMap<>();
        
        DescriptiveStatistics utilStats = new DescriptiveStatistics();
        DescriptiveStatistics cpuStats = new DescriptiveStatistics();
        DescriptiveStatistics memStats = new DescriptiveStatistics();
        
        for (ExperimentalResult result : results) {
            utilStats.addValue(result.getResourceUtilization());
            
            // Extract detailed metrics if available
            Map<String, Object> details = result.getDetailedMetrics();
            if (details != null) {
                cpuStats.addValue((Double) details.getOrDefault("cpu_utilization", 0.0));
                memStats.addValue((Double) details.getOrDefault("memory_utilization", 0.0));
            }
        }
        
        metrics.put("avg_utilization", utilStats.getMean());
        metrics.put("std_utilization", utilStats.getStandardDeviation());
        metrics.put("min_utilization", utilStats.getMin());
        metrics.put("max_utilization", utilStats.getMax());
        metrics.put("utilization_range", utilStats.getMax() - utilStats.getMin());
        metrics.put("utilization_cv", utilStats.getStandardDeviation() / utilStats.getMean());
        
        if (cpuStats.getN() > 0) {
            metrics.put("avg_cpu_utilization", cpuStats.getMean());
            metrics.put("avg_memory_utilization", memStats.getMean());
        }
        
        return metrics;
    }
    
    private void updatePerformanceProfile(String algorithm, String metricType, 
                                         List<ExperimentalResult> results) {
        
        PerformanceProfile profile = performanceProfiles.computeIfAbsent(
            algorithm, k -> new PerformanceProfile(algorithm));
        
        DescriptiveStatistics stats = new DescriptiveStatistics();
        List<Double> timeSeries = new ArrayList<>();
        
        for (ExperimentalResult result : results) {
            double value = 0.0;
            switch (metricType) {
                case RESOURCE_UTILIZATION:
                    value = result.getResourceUtilization();
                    break;
                case POWER_CONSUMPTION:
                    value = result.getPowerConsumption();
                    break;
                case THROUGHPUT:
                    value = result.getThroughput();
                    break;
                case RESPONSE_TIME:
                    value = result.getAverageResponseTime();
                    break;
            }
            stats.addValue(value);
            timeSeries.add(value);
        }
        
        // Update profile
        profile.averageMetrics.put(metricType, stats.getMean());
        profile.metricVariability.put(metricType, stats.getStandardDeviation());
        profile.percentileMetrics.put(metricType + "_p50", stats.getPercentile(50));
        profile.percentileMetrics.put(metricType + "_p90", stats.getPercentile(90));
        profile.percentileMetrics.put(metricType + "_p99", stats.getPercentile(99));
        profile.timeSeriesMetrics.put(metricType, timeSeries);
        
        // Classify performance
        String classification = classifyPerformance(metricType, stats.getMean());
        profile.performanceClassification.put(metricType, classification);
        
        // Store statistics for later use
        metricStatistics.computeIfAbsent(algorithm, k -> new HashMap<>())
            .put(metricType, stats);
    }
    
    private String classifyPerformance(String metricType, double value) {
        switch (metricType) {
            case RESOURCE_UTILIZATION:
                if (value >= OPTIMAL_UTILIZATION_MIN && value <= OPTIMAL_UTILIZATION_MAX) {
                    return "OPTIMAL";
                } else if (value < OPTIMAL_UTILIZATION_MIN) {
                    return "UNDERUTILIZED";
                } else {
                    return "OVERUTILIZED";
                }
            case POWER_CONSUMPTION:
                if (value < 500) return "LOW";
                else if (value < 1000) return "MODERATE";
                else return "HIGH";
            case RESPONSE_TIME:
                if (value < 100) return "EXCELLENT";
                else if (value < 500) return "GOOD";
                else if (value < CRITICAL_RESPONSE_TIME) return "ACCEPTABLE";
                else return "POOR";
            case THROUGHPUT:
                if (value > 1000) return "HIGH";
                else if (value > 500) return "MODERATE";
                else return "LOW";
            default:
                return "UNKNOWN";
        }
    }
    
    private Map<String, Double> calculateOverallUtilizationStats(
            List<ExperimentalResult> results) {
        
        Map<String, Double> stats = new HashMap<>();
        
        DescriptiveStatistics overall = new DescriptiveStatistics();
        results.forEach(r -> overall.addValue(r.getResourceUtilization()));
        
        stats.put("global_avg_utilization", overall.getMean());
        stats.put("global_std_utilization", overall.getStandardDeviation());
        stats.put("global_min_utilization", overall.getMin());
        stats.put("global_max_utilization", overall.getMax());
        
        // Calculate utilization balance
        double balance = 1.0 - (overall.getStandardDeviation() / overall.getMean());
        stats.put("utilization_balance", balance);
        
        return stats;
    }
    
    private Map<String, String> identifyUtilizationPatterns(
            Map<String, List<ExperimentalResult>> byAlgorithm) {
        
        Map<String, String> patterns = new HashMap<>();
        
        for (Map.Entry<String, List<ExperimentalResult>> entry : byAlgorithm.entrySet()) {
            String algorithm = entry.getKey();
            List<ExperimentalResult> results = entry.getValue();
            
            // Analyze utilization trend
            List<Double> utilizations = results.stream()
                .map(r -> r.getResourceUtilization())
                .collect(Collectors.toList());
            
            String pattern = detectUtilizationPattern(utilizations);
            patterns.put(algorithm, pattern);
        }
        
        return patterns;
    }
    
    private String detectUtilizationPattern(List<Double> utilizations) {
        if (utilizations.size() < 3) return "INSUFFICIENT_DATA";
        
        // Check for stability
        DescriptiveStatistics stats = new DescriptiveStatistics();
        utilizations.forEach(stats::addValue);
        
        double cv = stats.getStandardDeviation() / stats.getMean();
        
        if (cv < 0.1) {
            return "STABLE";
        } else if (cv < 0.25) {
            return "MODERATE_VARIATION";
        } else {
            // Check for trends
            boolean increasing = true;
            boolean decreasing = true;
            
            for (int i = 1; i < utilizations.size(); i++) {
                if (utilizations.get(i) <= utilizations.get(i-1)) increasing = false;
                if (utilizations.get(i) >= utilizations.get(i-1)) decreasing = false;
            }
            
            if (increasing) return "INCREASING_TREND";
            if (decreasing) return "DECREASING_TREND";
            return "HIGH_VARIATION";
        }
    }
    
    private Map<String, Double> calculateUtilizationEfficiency(
            Map<String, List<ExperimentalResult>> byAlgorithm) {
        
        Map<String, Double> efficiency = new HashMap<>();
        
        for (Map.Entry<String, List<ExperimentalResult>> entry : byAlgorithm.entrySet()) {
            String algorithm = entry.getKey();
            List<ExperimentalResult> results = entry.getValue();
            
            double efficiencyScore = 0.0;
            
            for (ExperimentalResult result : results) {
                double util = result.getResourceUtilization();
                
                // Efficiency is highest in optimal range
                if (util >= OPTIMAL_UTILIZATION_MIN && util <= OPTIMAL_UTILIZATION_MAX) {
                    efficiencyScore += 1.0;
                } else if (util < OPTIMAL_UTILIZATION_MIN) {
                    efficiencyScore += util / OPTIMAL_UTILIZATION_MIN;
                } else {
                    efficiencyScore += OPTIMAL_UTILIZATION_MAX / util;
                }
            }
            
            efficiency.put(algorithm, efficiencyScore / results.size());
        }
        
        return efficiency;
    }
    
    private Map<String, Object> analyzeUtilizationDistribution(
            Map<String, List<ExperimentalResult>> byAlgorithm) {
        
        Map<String, Object> distribution = new HashMap<>();
        
        for (Map.Entry<String, List<ExperimentalResult>> entry : byAlgorithm.entrySet()) {
            String algorithm = entry.getKey();
            List<ExperimentalResult> results = entry.getValue();
            
            Map<String, Object> algoDistribution = new HashMap<>();
            
            // Calculate distribution statistics
            double[] utilizations = results.stream()
                .mapToDouble(r -> r.getResourceUtilization())
                .toArray();
            
            // Test for normality
            boolean isNormal = testNormality(utilizations);
            algoDistribution.put("is_normal_distribution", isNormal);
            
            // Calculate distribution parameters
            if (isNormal) {
                DescriptiveStatistics stats = new DescriptiveStatistics(utilizations);
                algoDistribution.put("distribution_mean", stats.getMean());
                algoDistribution.put("distribution_std", stats.getStandardDeviation());
            }
            
            // Calculate histogram bins
            Map<String, Integer> histogram = createUtilizationHistogram(utilizations);
            algoDistribution.put("histogram", histogram);
            
            distribution.put(algorithm, algoDistribution);
        }
        
        return distribution;
    }
    
    private boolean testNormality(double[] data) {
        // Simplified normality test using Jarque-Bera test approximation
        DescriptiveStatistics stats = new DescriptiveStatistics(data);
        double skewness = stats.getSkewness();
        double kurtosis = stats.getKurtosis();
        
        double jb = (data.length / 6.0) * (skewness * skewness + 
            (kurtosis * kurtosis) / 4.0);
        
        // Critical value at 5% significance level
        return jb < 5.99;
    }
    
    private Map<String, Integer> createUtilizationHistogram(double[] utilizations) {
        Map<String, Integer> histogram = new LinkedHashMap<>();
        
        // Create 10 bins from 0 to 1
        for (int i = 0; i < 10; i++) {
            String bin = String.format("%.1f-%.1f", i * 0.1, (i + 1) * 0.1);
            histogram.put(bin, 0);
        }
        
        // Count values in each bin
        for (double util : utilizations) {
            int binIndex = Math.min((int)(util * 10), 9);
            String bin = String.format("%.1f-%.1f", binIndex * 0.1, (binIndex + 1) * 0.1);
            histogram.merge(bin, 1, Integer::sum);
        }
        
        return histogram;
    }
    
    private String identifyOptimalUtilization(Map<String, Double> efficiencyScores) {
        return efficiencyScores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("NONE");
    }
    
    // Helper methods for power consumption analysis
    
    private Map<String, Double> analyzePowerMetrics(List<ExperimentalResult> results) {
        Map<String, Double> metrics = new HashMap<>();
        
        DescriptiveStatistics powerStats = new DescriptiveStatistics();
        List<Double> powerValues = new ArrayList<>();
        
        for (ExperimentalResult result : results) {
            double power = result.getPowerConsumption();
            powerStats.addValue(power);
            powerValues.add(power);
        }
        
        metrics.put("avg_power", powerStats.getMean());
        metrics.put("std_power", powerStats.getStandardDeviation());
        metrics.put("min_power", powerStats.getMin());
        metrics.put("max_power", powerStats.getMax());
        metrics.put("peak_to_avg_ratio", powerStats.getMax() / powerStats.getMean());
        
        // Calculate power per VM
        double avgVMs = results.stream()
            .mapToDouble(r -> r.getScenarioDetails() != null ? 
                (Integer) r.getScenarioDetails().getOrDefault("vm_count", 1) : 1)
            .average().orElse(1.0);
        
        metrics.put("power_per_vm", powerStats.getMean() / avgVMs);
        
        // Calculate energy (power * time)
        double totalEnergy = results.stream()
            .mapToDouble(r -> r.getPowerConsumption() * r.getExecutionTime() / 1000.0)
            .sum();
        metrics.put("total_energy", totalEnergy);
        
        return metrics;
    }
    
    private Map<String, Double> calculatePowerEfficiency(
            Map<String, List<ExperimentalResult>> byAlgorithm) {
        
        Map<String, Double> efficiency = new HashMap<>();
        
        for (Map.Entry<String, List<ExperimentalResult>> entry : byAlgorithm.entrySet()) {
            String algorithm = entry.getKey();
            List<ExperimentalResult> results = entry.getValue();
            
            // Calculate work done per unit of power
            double avgThroughput = results.stream()
                .mapToDouble(r -> r.getThroughput())
                .average().orElse(1.0);
            double avgPower = results.stream()
                .mapToDouble(r -> r.getPowerConsumption())
                .average().orElse(1.0);
            
            double powerEfficiency = avgThroughput / avgPower;
            efficiency.put(algorithm, powerEfficiency);
        }
        
        return efficiency;
    }
    
    private Map<String, Object> analyzePowerPatterns(
            Map<String, List<ExperimentalResult>> byAlgorithm) {
        
        Map<String, Object> patterns = new HashMap<>();
        
        for (Map.Entry<String, List<ExperimentalResult>> entry : byAlgorithm.entrySet()) {
            String algorithm = entry.getKey();
            List<ExperimentalResult> results = entry.getValue();
            
            Map<String, Object> algoPattern = new HashMap<>();
            
            // Analyze power variation with load
            Map<String, Double> loadVsPower = analyzeLoadPowerRelationship(results);
            algoPattern.put("load_power_correlation", loadVsPower);
            
            // Identify power spikes
            List<Integer> spikeIndices = identifyPowerSpikes(results);
            algoPattern.put("power_spike_count", spikeIndices.size());
            
            // Calculate power stability
            double stability = calculatePowerStability(results);
            algoPattern.put("power_stability", stability);
            
            patterns.put(algorithm, algoPattern);
        }
        
        return patterns;
    }
    
    private Map<String, Double> analyzeLoadPowerRelationship(
            List<ExperimentalResult> results) {
        
        Map<String, Double> relationship = new HashMap<>();
        
        double[] loads = new double[results.size()];
        double[] powers = new double[results.size()];
        
        for (int i = 0; i < results.size(); i++) {
            loads[i] = results.get(i).getResourceUtilization();
            powers[i] = results.get(i).getPowerConsumption();
        }
        
        // Calculate correlation
        PearsonsCorrelation correlation = new PearsonsCorrelation();
        double corr = correlation.correlation(loads, powers);
        
        relationship.put("load_power_correlation", corr);
        relationship.put("correlation_strength", Math.abs(corr));
        
        return relationship;
    }
    
    private List<Integer> identifyPowerSpikes(List<ExperimentalResult> results) {
        List<Integer> spikes = new ArrayList<>();
        
        if (results.size() < 3) return spikes;
        
        // Calculate moving average
        int window = Math.min(5, results.size() / 3);
        
        for (int i = window; i < results.size() - window; i++) {
            double current = results.get(i).getPowerConsumption();
            
            // Calculate local average
            double localAvg = 0.0;
            for (int j = i - window; j <= i + window; j++) {
                if (j != i) {
                    localAvg += results.get(j).getPowerConsumption();
                }
            }
            localAvg /= (2 * window);
            
            // Check if current is a spike (>50% above local average)
            if (current > localAvg * 1.5) {
                spikes.add(i);
            }
        }
        
        return spikes;
    }
    
    private double calculatePowerStability(List<ExperimentalResult> results) {
        if (results.size() < 2) return 1.0;
        
        double totalVariation = 0.0;
        
        for (int i = 1; i < results.size(); i++) {
            double prev = results.get(i-1).getPowerConsumption();
            double curr = results.get(i).getPowerConsumption();
            totalVariation += Math.abs(curr - prev) / prev;
        }
        
        // Lower variation means higher stability
        return 1.0 / (1.0 + totalVariation / (results.size() - 1));
    }
    
    private Map<String, Double> calculatePUEMetrics(
            Map<String, List<ExperimentalResult>> byAlgorithm) {
        
        Map<String, Double> pueMetrics = new HashMap<>();
        
        for (Map.Entry<String, List<ExperimentalResult>> entry : byAlgorithm.entrySet()) {
            String algorithm = entry.getKey();
            List<ExperimentalResult> results = entry.getValue();
            
            // Simplified PUE calculation
            double avgITPower = results.stream()
                .mapToDouble(r -> r.getPowerConsumption() * 0.7) // Assume 70% is IT power
                .average().orElse(1.0);
            double avgTotalPower = results.stream()
                .mapToDouble(r -> r.getPowerConsumption())
                .average().orElse(1.0);
            
            double pue = avgTotalPower / avgITPower;
            pueMetrics.put(algorithm, pue);
        }
        
        return pueMetrics;
    }
    
    private List<String> identifyEnergySavingOpportunities(
            Map<String, Map<String, Double>> algorithmPower,
            Map<String, Double> powerEfficiency) {
        
        List<String> opportunities = new ArrayList<>();
        
        // Find algorithm with lowest average power
        String lowestPowerAlgo = algorithmPower.entrySet().stream()
            .min((a, b) -> Double.compare(
                a.getValue().get("avg_power"), 
                b.getValue().get("avg_power")))
            .map(Map.Entry::getKey)
            .orElse("");
        
        opportunities.add(String.format(
            "%s achieves lowest average power consumption", lowestPowerAlgo));
        
        // Check for high peak-to-average ratios
        for (Map.Entry<String, Map<String, Double>> entry : algorithmPower.entrySet()) {
            double peakRatio = entry.getValue().get("peak_to_avg_ratio");
            if (peakRatio > 2.0) {
                opportunities.add(String.format(
                    "%s has high peak-to-average ratio (%.2f) - consider load smoothing",
                    entry.getKey(), peakRatio));
            }
        }
        
        // Identify efficiency improvements
        double maxEfficiency = powerEfficiency.values().stream()
            .mapToDouble(Double::doubleValue)
            .max().orElse(0.0);
        
        for (Map.Entry<String, Double> entry : powerEfficiency.entrySet()) {
            if (entry.getValue() < maxEfficiency * 0.8) {
                opportunities.add(String.format(
                    "%s has 20%% lower power efficiency than best algorithm",
                    entry.getKey()));
            }
        }
        
        return opportunities;
    }
    
    private String identifyMostPowerEfficient(Map<String, Double> powerEfficiency) {
        return powerEfficiency.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("NONE");
    }
    
    // Helper methods for throughput analysis
    
    private Map<String, Double> calculateThroughputMetrics(List<ExperimentalResult> results) {
        Map<String, Double> metrics = new HashMap<>();
        
        DescriptiveStatistics throughputStats = new DescriptiveStatistics();
        
        for (ExperimentalResult result : results) {
            throughputStats.addValue(result.getThroughput());
        }
        
        metrics.put("avg_throughput", throughputStats.getMean());
        metrics.put("std_throughput", throughputStats.getStandardDeviation());
        metrics.put("min_throughput", throughputStats.getMin());
        metrics.put("max_throughput", throughputStats.getMax());
        metrics.put("throughput_consistency", 
            1.0 - (throughputStats.getStandardDeviation() / throughputStats.getMean()));
        
        return metrics;
    }
    
    private Map<String, Object> analyzeThroughputScalability(
            Map<String, List<ExperimentalResult>> byAlgorithm) {
        
        Map<String, Object> scalability = new HashMap<>();
        
        for (Map.Entry<String, List<ExperimentalResult>> entry : byAlgorithm.entrySet()) {
            String algorithm = entry.getKey();
            List<ExperimentalResult> results = entry.getValue();
            
            // Group by scale (VM count)
            Map<Integer, List<ExperimentalResult>> byScale = results.stream()
                .collect(Collectors.groupingBy(r -> 
                    (Integer) r.getScenarioDetails().getOrDefault("vm_count", 0)));
            
            Map<String, Double> scaleMetrics = new HashMap<>();
            
            // Calculate throughput scaling factor
            if (byScale.size() >= 2) {
                List<Integer> scales = new ArrayList<>(byScale.keySet());
                Collections.sort(scales);
                
                double firstThroughput = byScale.get(scales.get(0)).stream()
                    .mapToDouble(r -> r.getThroughput())
                    .average().orElse(1.0);
                double lastThroughput = byScale.get(scales.get(scales.size()-1)).stream()
                    .mapToDouble(r -> r.getThroughput())
                    .average().orElse(1.0);
                
                double scalingFactor = lastThroughput / firstThroughput;
                double idealScaling = (double) scales.get(scales.size()-1) / scales.get(0);
                
                scaleMetrics.put("scaling_factor", scalingFactor);
                scaleMetrics.put("scaling_efficiency", scalingFactor / idealScaling);
            }
            
            scalability.put(algorithm, scaleMetrics);
        }
        
        return scalability;
    }
    
    private Map<String, Double> calculateThroughputEfficiency(
            Map<String, List<ExperimentalResult>> byAlgorithm) {
        
        Map<String, Double> efficiency = new HashMap<>();
        
        for (Map.Entry<String, List<ExperimentalResult>> entry : byAlgorithm.entrySet()) {
            String algorithm = entry.getKey();
            List<ExperimentalResult> results = entry.getValue();
            
            // Calculate throughput per resource unit
            double avgThroughput = results.stream()
                .mapToDouble(r -> r.getThroughput())
                .average().orElse(0.0);
            double avgUtilization = results.stream()
                .mapToDouble(r -> r.getResourceUtilization())
                .average().orElse(1.0);
            
            double throughputEfficiency = avgThroughput / avgUtilization;
            efficiency.put(algorithm, throughputEfficiency);
        }
        
        return efficiency;
    }
    
    private Map<String, List<String>> identifyThroughputBottlenecks(
            Map<String, List<ExperimentalResult>> byAlgorithm) {
        
        Map<String, List<String>> bottlenecks = new HashMap<>();
        
        for (Map.Entry<String, List<ExperimentalResult>> entry : byAlgorithm.entrySet()) {
            String algorithm = entry.getKey();
            List<ExperimentalResult> results = entry.getValue();
            List<String> algoBottlenecks = new ArrayList<>();
            
            // Check for throughput degradation
            boolean degrading = false;
            for (int i = 1; i < results.size(); i++) {
                if (results.get(i).getThroughput() < results.get(i-1).getThroughput() * 0.9) {
                    degrading = true;
                    break;
                }
            }
            
            if (degrading) {
                algoBottlenecks.add("Throughput degradation detected");
            }
            
            // Check for low throughput consistency
            DescriptiveStatistics stats = new DescriptiveStatistics();
            results.forEach(r -> stats.addValue(r.getThroughput()));
            
            if (stats.getStandardDeviation() / stats.getMean() > 0.3) {
                algoBottlenecks.add("High throughput variability");
            }
            
            bottlenecks.put(algorithm, algoBottlenecks);
        }
        
        return bottlenecks;
    }
    
    private String identifyHighestThroughput(
            Map<String, Map<String, Double>> algorithmThroughput) {
        
        return algorithmThroughput.entrySet().stream()
            .max((a, b) -> Double.compare(
                a.getValue().get("avg_throughput"),
                b.getValue().get("avg_throughput")))
            .map(Map.Entry::getKey)
            .orElse("NONE");
    }
    
    // Helper methods for response time analysis
    
    private Map<String, Double> analyzeResponseTimeMetrics(List<ExperimentalResult> results) {
        Map<String, Double> metrics = new HashMap<>();
        
        DescriptiveStatistics rtStats = new DescriptiveStatistics();
        
        for (ExperimentalResult result : results) {
            rtStats.addValue(result.getAverageResponseTime());
        }
        
        metrics.put("avg_response_time", rtStats.getMean());
        metrics.put("std_response_time", rtStats.getStandardDeviation());
        metrics.put("min_response_time", rtStats.getMin());
        metrics.put("max_response_time", rtStats.getMax());
        metrics.put("response_time_range", rtStats.getMax() - rtStats.getMin());
        
        return metrics;
    }
    
    private Map<String, Map<String, Double>> calculateResponseTimePercentiles(
            Map<String, List<ExperimentalResult>> byAlgorithm) {
        
        Map<String, Map<String, Double>> percentiles = new HashMap<>();
        
        for (Map.Entry<String, List<ExperimentalResult>> entry : byAlgorithm.entrySet()) {
            String algorithm = entry.getKey();
            List<ExperimentalResult> results = entry.getValue();
            
            DescriptiveStatistics stats = new DescriptiveStatistics();
            results.forEach(r -> stats.addValue(r.getAverageResponseTime()));
            
            Map<String, Double> algoPercentiles = new HashMap<>();
            algoPercentiles.put("p50", stats.getPercentile(50));
            algoPercentiles.put("p90", stats.getPercentile(90));
            algoPercentiles.put("p95", stats.getPercentile(95));
            algoPercentiles.put("p99", stats.getPercentile(99));
            
            percentiles.put(algorithm, algoPercentiles);
        }
        
        return percentiles;
    }
    
    private Map<String, Object> analyzeResponseTimeDistribution(
            Map<String, List<ExperimentalResult>> byAlgorithm) {
        
        Map<String, Object> distribution = new HashMap<>();
        
        for (Map.Entry<String, List<ExperimentalResult>> entry : byAlgorithm.entrySet()) {
            String algorithm = entry.getKey();
            List<ExperimentalResult> results = entry.getValue();
            
            Map<String, Object> algoDist = new HashMap<>();
            
            // Check for long tail
            DescriptiveStatistics stats = new DescriptiveStatistics();
            results.forEach(r -> stats.addValue(r.getAverageResponseTime()));
            
            double p90 = stats.getPercentile(90);
            double p99 = stats.getPercentile(99);
            double mean = stats.getMean();
            
            boolean hasLongTail = (p99 > p90 * 2) || (p99 > mean * 3);
            algoDist.put("has_long_tail", hasLongTail);
            algoDist.put("tail_ratio", p99 / p90);
            
            distribution.put(algorithm, algoDist);
        }
        
        return distribution;
    }
    
    private Map<String, Double> identifyResponseTimeViolations(
            Map<String, List<ExperimentalResult>> byAlgorithm) {
        
        Map<String, Double> violations = new HashMap<>();
        
        for (Map.Entry<String, List<ExperimentalResult>> entry : byAlgorithm.entrySet()) {
            String algorithm = entry.getKey();
            List<ExperimentalResult> results = entry.getValue();
            
            long violationCount = results.stream()
                .filter(r -> r.getAverageResponseTime() > CRITICAL_RESPONSE_TIME)
                .count();
            
            double violationRate = (double) violationCount / results.size();
            violations.put(algorithm, violationRate);
        }
        
        return violations;
    }
    
    private Map<String, Map<String, Double>> calculateResponseTimeStability(
            Map<String, List<ExperimentalResult>> byAlgorithm) {
        
        Map<String, Map<String, Double>> stability = new HashMap<>();
        
        for (Map.Entry<String, List<ExperimentalResult>> entry : byAlgorithm.entrySet()) {
            String algorithm = entry.getKey();
            List<ExperimentalResult> results = entry.getValue();
            
            Map<String, Double> stabilityMetrics = new HashMap<>();
            
            // Calculate jitter
            double jitter = calculateJitter(results);
            stabilityMetrics.put("jitter", jitter);
            
            // Calculate consistency
            DescriptiveStatistics stats = new DescriptiveStatistics();
            results.forEach(r -> stats.addValue(r.getAverageResponseTime()));
            
            double consistency = 1.0 - (stats.getStandardDeviation() / stats.getMean());
            stabilityMetrics.put("consistency", consistency);
            
            stability.put(algorithm, stabilityMetrics);
        }
        
        return stability;
    }
    
    private double calculateJitter(List<ExperimentalResult> results) {
        if (results.size() < 2) return 0.0;
        
        double totalJitter = 0.0;
        
        for (int i = 1; i < results.size(); i++) {
            double prev = results.get(i-1).getAverageResponseTime();
            double curr = results.get(i).getAverageResponseTime();
            totalJitter += Math.abs(curr - prev);
        }
        
        return totalJitter / (results.size() - 1);
    }
    
    private String identifyLowestLatency(
            Map<String, Map<String, Double>> algorithmResponseTime) {
        
        return algorithmResponseTime.entrySet().stream()
            .min((a, b) -> Double.compare(
                a.getValue().get("avg_response_time"),
                b.getValue().get("avg_response_time")))
            .map(Map.Entry::getKey)
            .orElse("NONE");
    }
    
    // Overall performance analysis methods
    
    private Map<String, Double> calculateOverallPerformanceScores() {
        Map<String, Double> scores = new HashMap<>();
        
        for (PerformanceProfile profile : performanceProfiles.values()) {
            double score = calculateCompositeScore(profile);
            profile.overallScore = score;
            scores.put(profile.algorithmName, score);
        }
        
        return scores;
    }
    
    private double calculateCompositeScore(PerformanceProfile profile) {
        // Weighted scoring based on normalized metrics
        double utilizationScore = normalizeUtilizationScore(
            profile.averageMetrics.getOrDefault(RESOURCE_UTILIZATION, 0.0));
        double powerScore = normalizePowerScore(
            profile.averageMetrics.getOrDefault(POWER_CONSUMPTION, 0.0));
        double throughputScore = normalizeThroughputScore(
            profile.averageMetrics.getOrDefault(THROUGHPUT, 0.0));
        double responseTimeScore = normalizeResponseTimeScore(
            profile.averageMetrics.getOrDefault(RESPONSE_TIME, 0.0));
        
        // Weights
        double utilizationWeight = 0.25;
        double powerWeight = 0.25;
        double throughputWeight = 0.25;
        double responseTimeWeight = 0.25;
        
        return utilizationScore * utilizationWeight +
               powerScore * powerWeight +
               throughputScore * throughputWeight +
               responseTimeScore * responseTimeWeight;
    }
    
    private double normalizeUtilizationScore(double utilization) {
        if (utilization >= OPTIMAL_UTILIZATION_MIN && 
            utilization <= OPTIMAL_UTILIZATION_MAX) {
            return 1.0;
        } else if (utilization < OPTIMAL_UTILIZATION_MIN) {
            return utilization / OPTIMAL_UTILIZATION_MIN;
        } else {
            return OPTIMAL_UTILIZATION_MAX / utilization;
        }
    }
    
    private double normalizePowerScore(double power) {
        // Lower power is better
        return 1.0 / (1.0 + power / 1000.0);
    }
    
    private double normalizeThroughputScore(double throughput) {
        // Higher throughput is better
        return throughput / 1000.0;
    }
    
    private double normalizeResponseTimeScore(double responseTime) {
        // Lower response time is better
        return 1.0 / (1.0 + responseTime / 100.0);
    }
    
    private List<Map<String, Object>> generatePerformanceRankings() {
        List<Map<String, Object>> rankings = new ArrayList<>();
        
        // Rank by each metric
        for (String metric : new String[]{RESOURCE_UTILIZATION, POWER_CONSUMPTION, 
                                         THROUGHPUT, RESPONSE_TIME}) {
            
            Map<String, Object> metricRanking = new HashMap<>();
            metricRanking.put("metric", metric);
            
            List<Map.Entry<String, Double>> sorted = performanceProfiles.entrySet().stream()
                .map(e -> new AbstractMap.SimpleEntry<>(
                    e.getKey(), 
                    e.getValue().averageMetrics.getOrDefault(metric, 0.0)))
                .sorted((a, b) -> {
                    // For power and response time, lower is better
                    if (metric.equals(POWER_CONSUMPTION) || metric.equals(RESPONSE_TIME)) {
                        return Double.compare(a.getValue(), b.getValue());
                    } else {
                        return Double.compare(b.getValue(), a.getValue());
                    }
                })
                .collect(Collectors.toList());
            
            List<String> rankedAlgorithms = new ArrayList<>();
            for (int i = 0; i < sorted.size(); i++) {
                rankedAlgorithms.add(String.format("%d. %s (%.2f)", 
                    i + 1, sorted.get(i).getKey(), sorted.get(i).getValue()));
            }
            
            metricRanking.put("ranking", rankedAlgorithms);
            rankings.add(metricRanking);
        }
        
        return rankings;
    }
    
    private Map<String, Map<String, String>> identifyStrengthsAndWeaknesses() {
        Map<String, Map<String, String>> analysis = new HashMap<>();
        
        for (PerformanceProfile profile : performanceProfiles.values()) {
            Map<String, String> algoAnalysis = new HashMap<>();
            List<String> strengths = new ArrayList<>();
            List<String> weaknesses = new ArrayList<>();
            
            // Check each metric classification
            for (Map.Entry<String, String> entry : 
                    profile.performanceClassification.entrySet()) {
                
                String metric = entry.getKey();
                String classification = entry.getValue();
                
                if (classification.equals("OPTIMAL") || classification.equals("EXCELLENT") ||
                    classification.equals("HIGH")) {
                    strengths.add(metric + ": " + classification);
                } else if (classification.equals("POOR") || classification.equals("LOW") ||
                           classification.equals("OVERUTILIZED")) {
                    weaknesses.add(metric + ": " + classification);
                }
            }
            
            algoAnalysis.put("strengths", String.join(", ", strengths));
            algoAnalysis.put("weaknesses", String.join(", ", weaknesses));
            
            analysis.put(profile.algorithmName, algoAnalysis);
        }
        
        return analysis;
    }
    
    private Map<String, List<String>> generatePerformanceRecommendations() {
        Map<String, List<String>> recommendations = new HashMap<>();
        
        for (PerformanceProfile profile : performanceProfiles.values()) {
            List<String> algoRecommendations = new ArrayList<>();
            
            // Utilization recommendations
            String utilClass = profile.performanceClassification.get(RESOURCE_UTILIZATION);
            if (utilClass != null) {
                if (utilClass.equals("UNDERUTILIZED")) {
                    algoRecommendations.add("Consider consolidation to improve resource utilization");
                } else if (utilClass.equals("OVERUTILIZED")) {
                    algoRecommendations.add("Add more resources or improve load balancing");
                }
            }
            
            // Power recommendations
            double avgPower = profile.averageMetrics.getOrDefault(POWER_CONSUMPTION, 0.0);
            if (avgPower > 1000) {
                algoRecommendations.add("High power consumption - consider power-aware optimizations");
            }
            
            // Response time recommendations
            double p99ResponseTime = profile.percentileMetrics.getOrDefault(
                RESPONSE_TIME + "_p99", 0.0);
            if (p99ResponseTime > CRITICAL_RESPONSE_TIME) {
                algoRecommendations.add("High tail latency - optimize for worst-case scenarios");
            }
            
            recommendations.put(profile.algorithmName, algoRecommendations);
        }
        
        return recommendations;
    }
    
    private double[][] createPerformanceMatrix() {
        List<String> algorithms = new ArrayList<>(performanceProfiles.keySet());
        String[] metrics = {RESOURCE_UTILIZATION, POWER_CONSUMPTION, THROUGHPUT, RESPONSE_TIME};
        
        double[][] matrix = new double[algorithms.size()][metrics.length];
        
        for (int i = 0; i < algorithms.size(); i++) {
            PerformanceProfile profile = performanceProfiles.get(algorithms.get(i));
            for (int j = 0; j < metrics.length; j++) {
                double value = profile.averageMetrics.getOrDefault(metrics[j], 0.0);
                // Normalize values
                switch (metrics[j]) {
                    case RESOURCE_UTILIZATION:
                        matrix[i][j] = normalizeUtilizationScore(value);
                        break;
                    case POWER_CONSUMPTION:
                        matrix[i][j] = normalizePowerScore(value);
                        break;
                    case THROUGHPUT:
                        matrix[i][j] = normalizeThroughputScore(value);
                        break;
                    case RESPONSE_TIME:
                        matrix[i][j] = normalizeResponseTimeScore(value);
                        break;
                }
            }
        }
        
        return matrix;
    }
    
    private Map<String, JFreeChart> generatePerformanceVisualizations() {
        Map<String, JFreeChart> charts = new HashMap<>();
        
        try {
            // Utilization comparison chart
            JFreeChart utilizationChart = createUtilizationComparisonChart();
            charts.put("utilization_comparison", utilizationChart);
            
            // Power consumption chart
            JFreeChart powerChart = createPowerConsumptionChart();
            charts.put("power_consumption", powerChart);
            
            // Response time percentiles chart
            JFreeChart responseTimeChart = createResponseTimePercentilesChart();
            charts.put("response_time_percentiles", responseTimeChart);
            
            // Overall performance radar chart
            JFreeChart radarChart = createPerformanceRadarChart();
            charts.put("performance_radar", radarChart);
            
        } catch (Exception e) {
            LoggingManager.logError("Error generating visualizations", e);
        }
        
        return charts;
    }
    
    private JFreeChart createUtilizationComparisonChart() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        for (PerformanceProfile profile : performanceProfiles.values()) {
            double avgUtil = profile.averageMetrics.getOrDefault(RESOURCE_UTILIZATION, 0.0);
            dataset.addValue(avgUtil * 100, "Average", profile.algorithmName);
            
            double p90Util = profile.percentileMetrics.getOrDefault(
                RESOURCE_UTILIZATION + "_p90", 0.0);
            dataset.addValue(p90Util * 100, "90th Percentile", profile.algorithmName);
        }
        
        return ChartFactory.createBarChart(
            "Resource Utilization Comparison",
            "Algorithm",
            "Utilization (%)",
            dataset,
            PlotOrientation.VERTICAL,
            true, true, false
        );
    }
    
    private JFreeChart createPowerConsumptionChart() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        for (PerformanceProfile profile : performanceProfiles.values()) {
            double avgPower = profile.averageMetrics.getOrDefault(POWER_CONSUMPTION, 0.0);
            dataset.addValue(avgPower, "Power Consumption", profile.algorithmName);
        }
        
        return ChartFactory.createBarChart(
            "Power Consumption Comparison",
            "Algorithm",
            "Power (Watts)",
            dataset,
            PlotOrientation.VERTICAL,
            false, true, false
        );
    }
    
    private JFreeChart createResponseTimePercentilesChart() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        for (PerformanceProfile profile : performanceProfiles.values()) {
            dataset.addValue(
                profile.percentileMetrics.getOrDefault(RESPONSE_TIME + "_p50", 0.0),
                "50th %ile", profile.algorithmName);
            dataset.addValue(
                profile.percentileMetrics.getOrDefault(RESPONSE_TIME + "_p90", 0.0),
                "90th %ile", profile.algorithmName);
            dataset.addValue(
                profile.percentileMetrics.getOrDefault(RESPONSE_TIME + "_p99", 0.0),
                "99th %ile", profile.algorithmName);
        }
        
        return ChartFactory.createBarChart(
            "Response Time Percentiles",
            "Algorithm",
            "Response Time (ms)",
            dataset,
            PlotOrientation.VERTICAL,
            true, true, false
        );
    }
    
    private JFreeChart createPerformanceRadarChart() {
        // Simplified implementation - would use specialized radar chart library
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        for (PerformanceProfile profile : performanceProfiles.values()) {
            dataset.addValue(profile.overallScore * 100, 
                "Overall Score", profile.algorithmName);
        }
        
        return ChartFactory.createLineChart(
            "Overall Performance Score",
            "Algorithm",
            "Score",
            dataset,
            PlotOrientation.VERTICAL,
            false, true, false
        );
    }
    
    // Custom exception class
    public static class ExperimentException extends RuntimeException {
        public ExperimentException(String message) {
            super(message);
        }
        
        public ExperimentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
