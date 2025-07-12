package org.cloudbus.cloudsim.analyzer;

import org.cloudbus.cloudsim.experiment.ExperimentalResult;
import org.cloudbus.cloudsim.util.LoggingManager;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * ScalabilityAnalyzer - Analyzes algorithm scalability characteristics
 * Part of the research framework for evaluating VM placement algorithms
 * 
 * Research objectives addressed:
 * - Analyze algorithm performance with varying problem sizes
 * - Determine computational complexity empirically
 * - Identify scalability bottlenecks
 * - Predict performance for larger problem instances
 * @author Puneet Chandna
 */
public class ScalabilityAnalyzer {
    
    private final Map<String, Map<Integer, List<ExperimentalResult>>> scalabilityData;
    private final Map<String, ScalabilityResults> analysisResults;
    private final Map<String, ComplexityModel> complexityModels;
    private final LoggingManager loggingManager;
    
    // Scalability dimensions
    public static final String VM_COUNT_DIMENSION = "vm_count";
    public static final String HOST_COUNT_DIMENSION = "host_count";
    public static final String COMBINED_DIMENSION = "combined_scale";
    
    // Constants for string literals
    private static final String EXECUTION_TIME = "execution_time";
    private static final String MEMORY_USAGE = "memory_usage";
    private static final String RESOURCE_UTILIZATION = "resource_utilization";
    private static final String SLA_VIOLATIONS = "sla_violations";
    private static final String POWER_CONSUMPTION = "power_consumption";
    private static final String PRACTICAL_LIMIT = "practical_limit";
    private static final String PROBLEM_SIZE_VMS = "Problem Size (VMs)";
    private static final String TIME_BASED_LIMIT = "time_based_limit";
    private static final String MEMORY_BASED_LIMIT = "memory_based_limit";
    private static final String SLA_BASED_LIMIT = "sla_based_limit";
    
    // Complexity models
    public enum ComplexityClass {
        CONSTANT("O(1)", 0),
        LOGARITHMIC("O(log n)", 1),
        LINEAR("O(n)", 2),
        LINEARITHMIC("O(n log n)", 3),
        QUADRATIC("O(n²)", 4),
        CUBIC("O(n³)", 5),
        EXPONENTIAL("O(2^n)", 6);
        
        private final String notation;
        private final int order;
        
        ComplexityClass(String notation, int order) {
            this.notation = notation;
            this.order = order;
        }
        
        public String getNotation() { return notation; }
        public int getOrder() { return order; }
    }
    
    public static class ComplexityModel {
        private ComplexityClass timeComplexity;
        private ComplexityClass spaceComplexity;
        private double rSquaredTime;
        private double rSquaredSpace;
        private PolynomialFunction timeModel;
        private PolynomialFunction spaceModel;
        private Map<String, Double> coefficients;
        
        public ComplexityModel() {
            this.coefficients = new HashMap<>();
        }
        
        // Getters and setters
        public ComplexityClass getTimeComplexity() { return timeComplexity; }
        public void setTimeComplexity(ComplexityClass timeComplexity) { this.timeComplexity = timeComplexity; }
        
        public ComplexityClass getSpaceComplexity() { return spaceComplexity; }
        public void setSpaceComplexity(ComplexityClass spaceComplexity) { this.spaceComplexity = spaceComplexity; }
        
        public double getRSquaredTime() { return rSquaredTime; }
        public void setRSquaredTime(double rSquaredTime) { this.rSquaredTime = rSquaredTime; }
        
        public double getRSquaredSpace() { return rSquaredSpace; }
        public void setRSquaredSpace(double rSquaredSpace) { this.rSquaredSpace = rSquaredSpace; }
        
        public PolynomialFunction getTimeModel() { return timeModel; }
        public void setTimeModel(PolynomialFunction timeModel) { this.timeModel = timeModel; }
        
        public PolynomialFunction getSpaceModel() { return spaceModel; }
        public void setSpaceModel(PolynomialFunction spaceModel) { this.spaceModel = spaceModel; }
        
        public Map<String, Double> getCoefficients() { return coefficients; }
        public void setCoefficients(Map<String, Double> coefficients) { this.coefficients = coefficients; }
    }  
    
    public ScalabilityAnalyzer() {
        this.scalabilityData = new HashMap<>();
        this.analysisResults = new HashMap<>();
        this.complexityModels = new HashMap<>();
        this.loggingManager = new LoggingManager();
    }
    
    /**
     * Perform comprehensive scalability analysis
     */
    public ScalabilityResults performScalabilityAnalysis(
            List<ExperimentalResult> results, String algorithm) {
        
        loggingManager.logInfo("Performing scalability analysis for: " + algorithm);
        
        try {
            // Organize results by scale
            organizeResultsByScale(results, algorithm);
            
            // Create scalability results object
            ScalabilityResults scalabilityResults = new ScalabilityResults();
            scalabilityResults.setAlgorithmName(algorithm);
            
            // Analyze different scaling dimensions
            Map<String, Object> vmScaling = analyzeVMScaling(algorithm);
            Map<String, Object> hostScaling = analyzeHostScaling(algorithm);
            Map<String, Object> combinedScaling = analyzeCombinedScaling(algorithm);
            
            // Calculate complexity metrics
            ComplexityModel complexity = calculateComplexityMetrics(algorithm);
            complexityModels.put(algorithm, complexity);
            
            // Analyze performance trends
            Map<String, List<PerformanceTrend>> trends = 
                analyzePerformanceTrends(algorithm);
            
            // Identify scalability limits
            Map<String, Integer> limits = predictScalabilityLimits(algorithm);
            
            // Generate scalability report
            Map<String, Object> report = generateScalabilityReport(
                algorithm, vmScaling, 
                complexity, trends, limits);
            
            // Populate results
            scalabilityResults.setVmScalingAnalysis(vmScaling);
            scalabilityResults.setHostScalingAnalysis(hostScaling);
            scalabilityResults.setCombinedScalingAnalysis(combinedScaling);
            scalabilityResults.setComplexityModel(complexity);
            scalabilityResults.setPerformanceTrends(trends);
            scalabilityResults.setScalabilityLimits(limits);
            scalabilityResults.setDetailedReport(report);
            
            // Store results
            analysisResults.put(algorithm, scalabilityResults);
            
            return scalabilityResults;
            
        } catch (Exception e) {
            loggingManager.logError("Error in scalability analysis", e);
            throw new ExperimentException("Failed to perform scalability analysis", e);
        }
    }
    
    /**
     * Calculate computational complexity metrics
     */
    public ComplexityModel calculateComplexityMetrics(String algorithm) {
        loggingManager.logInfo("Calculating complexity metrics for: " + algorithm);
        
        ComplexityModel model = new ComplexityModel();
        
        try {
            // Get scalability data
            Map<Integer, List<ExperimentalResult>> data = 
                scalabilityData.get(algorithm);
            
            if (data == null || data.isEmpty()) {
                throw new ExperimentException("No scalability data for " + algorithm);
            }
            
            // Extract time and space measurements
            List<Double> problemSizes = new ArrayList<>();
            List<Double> executionTimes = new ArrayList<>();
            List<Double> memoryUsage = new ArrayList<>();
            
            for (Map.Entry<Integer, List<ExperimentalResult>> entry : data.entrySet()) {
                int size = entry.getKey();
                List<ExperimentalResult> results = entry.getValue();
                
                // Average metrics for this size
                double avgTime = results.stream()
                    .mapToDouble(r -> r.getExecutionTime())
                    .average().orElse(0.0);
                double avgMemory = results.stream()
                    .mapToDouble(r -> r.getMemoryUsage())
                    .average().orElse(0.0);
                
                problemSizes.add((double) size);
                executionTimes.add(avgTime);
                memoryUsage.add(avgMemory);
            }
            
            // Fit complexity models
            model.setTimeComplexity(determineComplexityClass(
                problemSizes, executionTimes));
            model.setSpaceComplexity(determineComplexityClass(
                problemSizes, memoryUsage));
            
            // Fit polynomial models
            model.setTimeModel(fitPolynomialModel(problemSizes, executionTimes));
            model.setSpaceModel(fitPolynomialModel(problemSizes, memoryUsage));
            
            // Calculate R-squared values
            model.setRSquaredTime(calculateRSquared(
                problemSizes, executionTimes, model.getTimeModel()));
            model.setRSquaredSpace(calculateRSquared(
                problemSizes, memoryUsage, model.getSpaceModel()));
            
            // Extract coefficients
            extractCoefficients(model);
            
        } catch (Exception e) {
            loggingManager.logError("Error calculating complexity metrics", e);
            throw new ExperimentException("Failed to calculate complexity", e);
        }
        
        return model;
    }
    
    /**
     * Analyze performance trends across scales
     */
    public Map<String, List<PerformanceTrend>> analyzePerformanceTrends(
            String algorithm) {
        
        loggingManager.logInfo("Analyzing performance trends for: " + algorithm);
        
        Map<String, List<PerformanceTrend>> trends = new HashMap<>();
        
        try {
            Map<Integer, List<ExperimentalResult>> data = 
                scalabilityData.get(algorithm);
            
            // Analyze trends for each metric
            trends.put(EXECUTION_TIME, analyzeExecutionTimeTrend(data));
            trends.put(MEMORY_USAGE, analyzeMemoryUsageTrend(data));
            trends.put(RESOURCE_UTILIZATION, analyzeUtilizationTrend(data));
            trends.put(SLA_VIOLATIONS, analyzeSLATrend(data));
            trends.put(POWER_CONSUMPTION, analyzePowerTrend(data));
            
        } catch (Exception e) {
            loggingManager.logError("Error analyzing trends", e);
        }
        
        return trends;
    }
    
    /**
     * Predict scalability limits based on trends
     */
    public Map<String, Integer> predictScalabilityLimits(String algorithm) {
        loggingManager.logInfo("Predicting scalability limits for: " + algorithm);
        
        Map<String, Integer> limits = new HashMap<>();
        
        try {
            ComplexityModel model = complexityModels.get(algorithm);
            if (model == null) {
                model = calculateComplexityMetrics(algorithm);
            }
            
            // Define thresholds
            final double MAX_EXECUTION_TIME = 3600.0; // 1 hour
            final double MAX_MEMORY_GB = 32.0; // 32 GB
            final double MAX_SLA_VIOLATIONS = 0.1; // 10%
            
            // Predict limits based on models
            int timeLimit = predictLimitForMetric(
                model.getTimeModel(), MAX_EXECUTION_TIME);
            int memoryLimit = predictLimitForMetric(
                model.getSpaceModel(), MAX_MEMORY_GB * 1024);
            
            // Analyze SLA violation trends
            int slaLimit = predictSLALimit(algorithm, MAX_SLA_VIOLATIONS);
            
            limits.put(TIME_BASED_LIMIT, timeLimit);
            limits.put(MEMORY_BASED_LIMIT, memoryLimit);
            limits.put(SLA_BASED_LIMIT, slaLimit);
            limits.put(PRACTICAL_LIMIT, Math.min(timeLimit, 
                Math.min(memoryLimit, slaLimit)));
            
        } catch (Exception e) {
            loggingManager.logError("Error predicting limits", e);
        }
        
        return limits;
    }
    
    // Helper methods
    
    private void organizeResultsByScale(List<ExperimentalResult> results, 
                                       String algorithm) {
        
        Map<Integer, List<ExperimentalResult>> byScale = new HashMap<>();
        
        for (ExperimentalResult result : results) {
            Map<String, Object> scenario = result.getScenarioDetails();
            if (scenario != null) {
                // Use total problem size (VMs) as scale metric
                int vmCount = (Integer) scenario.getOrDefault(VM_COUNT_DIMENSION, 0);
                int scale = vmCount; // Can be changed to vmCount * hostCount if needed
                
                byScale.computeIfAbsent(scale, k -> new ArrayList<>()).add(result);
            }
        }
        
        scalabilityData.put(algorithm, byScale);
    }
    
    private Map<String, Object> analyzeVMScaling(String algorithm) {
        Map<String, Object> analysis = new HashMap<>();
        
        Map<Integer, List<ExperimentalResult>> data = scalabilityData.get(algorithm);
        
        // Group by VM count
        Map<Integer, List<ExperimentalResult>> byVMCount = new HashMap<>();
        for (List<ExperimentalResult> results : data.values()) {
            for (ExperimentalResult result : results) {
                Map<String, Object> scenario = result.getScenarioDetails();
                int vmCount = (Integer) scenario.getOrDefault(VM_COUNT_DIMENSION, 0);
                byVMCount.computeIfAbsent(vmCount, k -> new ArrayList<>()).add(result);
            }
        }
        
        // Analyze scaling behavior
        List<ScalingPoint> scalingPoints = new ArrayList<>();
        for (Map.Entry<Integer, List<ExperimentalResult>> entry : byVMCount.entrySet()) {
            ScalingPoint point = new ScalingPoint();
            point.scale = entry.getKey();
            point.avgExecutionTime = entry.getValue().stream()
                .mapToDouble(r -> r.getExecutionTime())
                .average().orElse(0.0);
            scalingPoints.add(point);
        }
        
        analysis.put("scaling_points", scalingPoints);
        analysis.put("scaling_factor", calculateScalingFactor(scalingPoints));
        analysis.put("efficiency_degradation", calculateEfficiencyDegradation(scalingPoints));
        
        return analysis;
    }
    
    private Map<String, Object> analyzeHostScaling(String algorithm) {
        Map<String, Object> analysis = new HashMap<>();
        
        Map<Integer, List<ExperimentalResult>> data = scalabilityData.get(algorithm);
        
        // Group by host count
        Map<Integer, List<ExperimentalResult>> byHostCount = new HashMap<>();
        for (List<ExperimentalResult> results : data.values()) {
            for (ExperimentalResult result : results) {
                Map<String, Object> scenario = result.getScenarioDetails();
                int hostCount = (Integer) scenario.getOrDefault(HOST_COUNT_DIMENSION, 0);
                byHostCount.computeIfAbsent(hostCount, k -> new ArrayList<>()).add(result);
            }
        }
        
        // Similar analysis for host scaling
        List<ScalingPoint> scalingPoints = new ArrayList<>();
        for (Map.Entry<Integer, List<ExperimentalResult>> entry : byHostCount.entrySet()) {
            ScalingPoint point = new ScalingPoint();
            point.scale = entry.getKey();
            point.avgExecutionTime = entry.getValue().stream()
                .mapToDouble(r -> r.getExecutionTime())
                .average().orElse(0.0);
            scalingPoints.add(point);
        }
        
        analysis.put("scaling_points", scalingPoints);
        analysis.put("impact_on_performance", analyzeHostImpact(byHostCount));
        
        return analysis;
    }
    
    private Map<String, Object> analyzeCombinedScaling(String algorithm) {
        Map<String, Object> analysis = new HashMap<>();
        
        Map<Integer, List<ExperimentalResult>> data = scalabilityData.get(algorithm);
        
        // Create 2D scaling analysis
        Map<String, List<ScalingPoint>> combinedScaling = new HashMap<>();
        
        for (List<ExperimentalResult> results : data.values()) {
            for (ExperimentalResult result : results) {
                Map<String, Object> scenario = result.getScenarioDetails();
                int vmCount = (Integer) scenario.getOrDefault(VM_COUNT_DIMENSION, 0);
                int hostCount = (Integer) scenario.getOrDefault(HOST_COUNT_DIMENSION, 0);
                String key = vmCount + "x" + hostCount;
                
                ScalingPoint point = new ScalingPoint();
                point.scale = vmCount * hostCount;
                point.vmCount = vmCount;
                point.hostCount = hostCount;
                point.avgExecutionTime = result.getExecutionTime();
                
                combinedScaling.computeIfAbsent(key, k -> new ArrayList<>()).add(point);
            }
        }
        
        analysis.put("combined_scaling_data", combinedScaling);
        analysis.put("interaction_effects", analyzeInteractionEffects(combinedScaling));
        
        return analysis;
    }
    
    private ComplexityClass determineComplexityClass(List<Double> sizes, 
                                                    List<Double> measurements) {
        
        // Try fitting different complexity models
        Map<ComplexityClass, Double> fitScores = new EnumMap<>(ComplexityClass.class);
        
        // Convert to arrays for regression
        double[] x = sizes.stream().mapToDouble(Double::doubleValue).toArray();
        double[] y = measurements.stream().mapToDouble(Double::doubleValue).toArray();
        
        // Test constant O(1)
        double constantScore = testConstantComplexity(y);
        fitScores.put(ComplexityClass.CONSTANT, constantScore);
        
        // Test linear O(n)
        double linearScore = testLinearComplexity(x, y);
        fitScores.put(ComplexityClass.LINEAR, linearScore);
        
        // Test logarithmic O(log n)
        double logScore = testLogarithmicComplexity(x, y);
        fitScores.put(ComplexityClass.LOGARITHMIC, logScore);
        
        // Test linearithmic O(n log n)
        double nLogNScore = testLinearithmicComplexity(x, y);
        fitScores.put(ComplexityClass.LINEARITHMIC, nLogNScore);
        
        // Test quadratic O(n²)
        double quadraticScore = testQuadraticComplexity(x, y);
        fitScores.put(ComplexityClass.QUADRATIC, quadraticScore);
        
        // Test cubic O(n³)
        double cubicScore = testCubicComplexity(x, y);
        fitScores.put(ComplexityClass.CUBIC, cubicScore);
        
        // Select best fitting model
        return fitScores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(ComplexityClass.LINEAR);
    }
    
    private double testConstantComplexity(double[] y) {
        DescriptiveStatistics stats = new DescriptiveStatistics(y);
        double mean = stats.getMean();
        double variance = stats.getVariance();
        
        // Lower variance relative to mean indicates constant complexity
        return 1.0 / (1.0 + variance / (mean * mean));
    }
    
    private double testLinearComplexity(double[] x, double[] y) {
        SimpleRegression regression = new SimpleRegression();
        for (int i = 0; i < x.length; i++) {
            regression.addData(x[i], y[i]);
        }
        return regression.getRSquare();
    }
    
    private double testLogarithmicComplexity(double[] x, double[] y) {
        SimpleRegression regression = new SimpleRegression();
        for (int i = 0; i < x.length; i++) {
            if (x[i] > 0) {
                regression.addData(Math.log(x[i]), y[i]);
            }
        }
        return regression.getRSquare();
    }
    
    private double testLinearithmicComplexity(double[] x, double[] y) {
        SimpleRegression regression = new SimpleRegression();
        for (int i = 0; i < x.length; i++) {
            if (x[i] > 0) {
                regression.addData(x[i] * Math.log(x[i]), y[i]);
            }
        }
        return regression.getRSquare();
    }
    
    private double testQuadraticComplexity(double[] x, double[] y) {
        SimpleRegression regression = new SimpleRegression();
        for (int i = 0; i < x.length; i++) {
            regression.addData(x[i] * x[i], y[i]);
        }
        return regression.getRSquare();
    }
    
    private double testCubicComplexity(double[] x, double[] y) {
        SimpleRegression regression = new SimpleRegression();
        for (int i = 0; i < x.length; i++) {
            regression.addData(x[i] * x[i] * x[i], y[i]);
        }
        return regression.getRSquare();
    }
    
    private PolynomialFunction fitPolynomialModel(List<Double> x, List<Double> y) {
        WeightedObservedPoints points = new WeightedObservedPoints();
        
        for (int i = 0; i < x.size(); i++) {
            points.add(x.get(i), y.get(i));
        }
        
        // Fit up to 3rd degree polynomial
        PolynomialCurveFitter fitter = PolynomialCurveFitter.create(3);
        double[] coefficients = fitter.fit(points.toList());
        
        return new PolynomialFunction(coefficients);
    }
    
    private double calculateRSquared(List<Double> x, List<Double> y, 
                                   PolynomialFunction model) {
        double yMean = y.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double ssTotal = 0.0;
        double ssResidual = 0.0;
        
        for (int i = 0; i < x.size(); i++) {
            double predicted = model.value(x.get(i));
            double actual = y.get(i);
            
            ssTotal += Math.pow(actual - yMean, 2);
            ssResidual += Math.pow(actual - predicted, 2);
        }
        
        return ssTotal > 0 ? 1.0 - (ssResidual / ssTotal) : 0.0;
    }
    
    private void extractCoefficients(ComplexityModel model) {
        if (model.getTimeModel() != null) {
            double[] timeCoeffs = model.getTimeModel().getCoefficients();
            for (int i = 0; i < timeCoeffs.length; i++) {
                model.getCoefficients().put("time_c" + i, timeCoeffs[i]);
            }
        }
        
        if (model.getSpaceModel() != null) {
            double[] spaceCoeffs = model.getSpaceModel().getCoefficients();
            for (int i = 0; i < spaceCoeffs.length; i++) {
                model.getCoefficients().put("space_c" + i, spaceCoeffs[i]);
            }
        }
    }
    
    private List<PerformanceTrend> analyzeExecutionTimeTrend(
            Map<Integer, List<ExperimentalResult>> data) {
        
        List<PerformanceTrend> trends = new ArrayList<>();
        
        List<Integer> sortedSizes = new ArrayList<>(data.keySet());
        Collections.sort(sortedSizes);
        
        for (int i = 1; i < sortedSizes.size(); i++) {
            int prevSize = sortedSizes.get(i - 1);
            int currSize = sortedSizes.get(i);
            
            double prevTime = data.get(prevSize).stream()
                .mapToDouble(r -> r.getExecutionTime())
                .average().orElse(0.0);
            double currTime = data.get(currSize).stream()
                .mapToDouble(r -> r.getExecutionTime())
                .average().orElse(0.0);
            
            PerformanceTrend trend = new PerformanceTrend();
            trend.fromScale = prevSize;
            trend.toScale = currSize;
            trend.metricName = EXECUTION_TIME;
            trend.percentChange = ((currTime - prevTime) / prevTime) * 100;
            trend.absoluteChange = currTime - prevTime;
            trend.scaleFactor = (double) currSize / prevSize;
            
            trends.add(trend);
        }
        
        return trends;
    }
    
    private List<PerformanceTrend> analyzeMemoryUsageTrend(
            Map<Integer, List<ExperimentalResult>> data) {
        
        List<PerformanceTrend> trends = new ArrayList<>();
        
        List<Integer> sortedSizes = new ArrayList<>(data.keySet());
        Collections.sort(sortedSizes);
        
        for (int i = 1; i < sortedSizes.size(); i++) {
            int prevSize = sortedSizes.get(i - 1);
            int currSize = sortedSizes.get(i);
            
            double prevMemory = data.get(prevSize).stream()
                .mapToDouble(r -> r.getMemoryUsage())
                .average().orElse(0.0);
            double currMemory = data.get(currSize).stream()
                .mapToDouble(r -> r.getMemoryUsage())
                .average().orElse(0.0);
            
            PerformanceTrend trend = new PerformanceTrend();
            trend.fromScale = prevSize;
            trend.toScale = currSize;
            trend.metricName = MEMORY_USAGE;
            trend.percentChange = ((currMemory - prevMemory) / prevMemory) * 100;
            trend.absoluteChange = currMemory - prevMemory;
            
            trends.add(trend);
        }
        
        return trends;
    }
    
    private List<PerformanceTrend> analyzeUtilizationTrend(
            Map<Integer, List<ExperimentalResult>> data) {
        
        List<PerformanceTrend> trends = new ArrayList<>();
        
        List<Integer> sortedSizes = new ArrayList<>(data.keySet());
        Collections.sort(sortedSizes);
        
        for (int i = 1; i < sortedSizes.size(); i++) {
            int prevSize = sortedSizes.get(i - 1);
            int currSize = sortedSizes.get(i);
            
            double prevUtil = data.get(prevSize).stream()
                .mapToDouble(r -> r.getResourceUtilization())
                .average().orElse(0.0);
            double currUtil = data.get(currSize).stream()
                .mapToDouble(r -> r.getResourceUtilization())
                .average().orElse(0.0);
            
            PerformanceTrend trend = new PerformanceTrend();
            trend.fromScale = prevSize;
            trend.toScale = currSize;
            trend.metricName = RESOURCE_UTILIZATION;
            trend.percentChange = ((currUtil - prevUtil) / prevUtil) * 100;
            trend.absoluteChange = currUtil - prevUtil;
            
            trends.add(trend);
        }
        
        return trends;
    }
    
    private List<PerformanceTrend> analyzeSLATrend(
            Map<Integer, List<ExperimentalResult>> data) {
        
        List<PerformanceTrend> trends = new ArrayList<>();
        
        List<Integer> sortedSizes = new ArrayList<>(data.keySet());
        Collections.sort(sortedSizes);
        
        for (int i = 1; i < sortedSizes.size(); i++) {
            int prevSize = sortedSizes.get(i - 1);
            int currSize = sortedSizes.get(i);
            
            double prevSLA = data.get(prevSize).stream()
                .mapToDouble(r -> r.getSlaViolations())
                .average().orElse(0.0);
            double currSLA = data.get(currSize).stream()
                .mapToDouble(r -> r.getSlaViolations())
                .average().orElse(0.0);
            
            PerformanceTrend trend = new PerformanceTrend();
            trend.fromScale = prevSize;
            trend.toScale = currSize;
            trend.metricName = SLA_VIOLATIONS;
            trend.percentChange = prevSLA > 0 ? 
                ((currSLA - prevSLA) / prevSLA) * 100 : 0;
            trend.absoluteChange = currSLA - prevSLA;
            
            trends.add(trend);
        }
        
        return trends;
    }
    
    private List<PerformanceTrend> analyzePowerTrend(
            Map<Integer, List<ExperimentalResult>> data) {
        
        List<PerformanceTrend> trends = new ArrayList<>();
        
        List<Integer> sortedSizes = new ArrayList<>(data.keySet());
        Collections.sort(sortedSizes);
        
        for (int i = 1; i < sortedSizes.size(); i++) {
            int prevSize = sortedSizes.get(i - 1);
            int currSize = sortedSizes.get(i);
            
            double prevPower = data.get(prevSize).stream()
                .mapToDouble(r -> r.getPowerConsumption())
                .average().orElse(0.0);
            double currPower = data.get(currSize).stream()
                .mapToDouble(r -> r.getPowerConsumption())
                .average().orElse(0.0);
            
            PerformanceTrend trend = new PerformanceTrend();
            trend.fromScale = prevSize;
            trend.toScale = currSize;
            trend.metricName = POWER_CONSUMPTION;
            trend.percentChange = ((currPower - prevPower) / prevPower) * 100;
            trend.absoluteChange = currPower - prevPower;
            
            trends.add(trend);
        }
        
        return trends;
    }
    
    private int predictLimitForMetric(PolynomialFunction model, double threshold) {
        // Binary search for the scale where metric exceeds threshold
        int low = 100;
        int high = 100000;
        int result = high;
        
        while (low <= high) {
            int mid = (low + high) / 2;
            double value = model.value(mid);
            
            if (value > threshold) {
                result = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        
        return result;
    }
    
    private int predictSLALimit(String algorithm, double maxViolations) {
        Map<Integer, List<ExperimentalResult>> data = scalabilityData.get(algorithm);
        
        List<Integer> sortedSizes = new ArrayList<>(data.keySet());
        Collections.sort(sortedSizes);
        
        for (int size : sortedSizes) {
            double avgSLA = data.get(size).stream()
                .mapToDouble(r -> r.getSlaViolations())
                .average().orElse(0.0);
            
            if (avgSLA > maxViolations) {
                return size;
            }
        }
        
        // If no limit found in current data, extrapolate
        return sortedSizes.get(sortedSizes.size() - 1) * 2;
    }
    
    private double calculateScalingFactor(List<ScalingPoint> points) {
        if (points.size() < 2) return 1.0;
        
        // Sort by scale
        points.sort(Comparator.comparingInt(p -> p.scale));
        
        // Calculate average scaling factor
        double totalFactor = 0.0;
        int count = 0;
        
        for (int i = 1; i < points.size(); i++) {
            ScalingPoint prev = points.get(i - 1);
            ScalingPoint curr = points.get(i);
            
            double scaleFactor = (double) curr.scale / prev.scale;
            double performanceFactor = curr.avgExecutionTime / prev.avgExecutionTime;
            
            totalFactor += performanceFactor / scaleFactor;
            count++;
        }
        
        return count > 0 ? totalFactor / count : 1.0;
    }
    
    private double calculateEfficiencyDegradation(List<ScalingPoint> points) {
        if (points.size() < 2) return 0.0;
        
        points.sort(Comparator.comparingInt(p -> p.scale));
        
        ScalingPoint first = points.get(0);
        ScalingPoint last = points.get(points.size() - 1);
        
        double baselineEfficiency = first.scale / first.avgExecutionTime;
        double finalEfficiency = last.scale / last.avgExecutionTime;
        
        return ((baselineEfficiency - finalEfficiency) / baselineEfficiency) * 100;
    }
    
    private Map<String, Double> analyzeHostImpact(
            Map<Integer, List<ExperimentalResult>> byHostCount) {
        
        Map<String, Double> impact = new HashMap<>();
        
        // Calculate correlation between host count and performance
        List<Double> hostCounts = new ArrayList<>();
        List<Double> executionTimes = new ArrayList<>();
        
        for (Map.Entry<Integer, List<ExperimentalResult>> entry : byHostCount.entrySet()) {
            hostCounts.add((double) entry.getKey());
            double avgTime = entry.getValue().stream()
                .mapToDouble(r -> r.getExecutionTime())
                .average().orElse(0.0);
            executionTimes.add(avgTime);
        }
        
        SimpleRegression regression = new SimpleRegression();
        for (int i = 0; i < hostCounts.size(); i++) {
            regression.addData(hostCounts.get(i), executionTimes.get(i));
        }
        
        impact.put("correlation", regression.getR());
        impact.put("slope", regression.getSlope());
        impact.put("r_squared", regression.getRSquare());
        
        return impact;
    }
    
    private Map<String, Double> analyzeInteractionEffects(
            Map<String, List<ScalingPoint>> combinedScaling) {
        
        Map<String, Double> effects = new HashMap<>();
        
        // Analyze how VM and host counts interact
        double totalEffect = 0.0;
        int count = 0;
        
        for (List<ScalingPoint> points : combinedScaling.values()) {
            for (ScalingPoint point : points) {
                double expectedTime = point.vmCount * 0.1 + point.hostCount * 0.05;
                double actualTime = point.avgExecutionTime;
                double interaction = Math.abs(actualTime - expectedTime) / expectedTime;
                
                totalEffect += interaction;
                count++;
            }
        }
        
        effects.put("average_interaction", count > 0 ? totalEffect / count : 0.0);
        
        return effects;
    }
    
    private Map<String, Object> generateScalabilityReport(
            String algorithm,
            Map<String, Object> vmScaling,
            ComplexityModel complexity,
            Map<String, List<PerformanceTrend>> trends,
            Map<String, Integer> limits) {
        
        Map<String, Object> report = new HashMap<>();
        
        // Summary
        Map<String, Object> summary = new HashMap<>();
        summary.put("algorithm", algorithm);
        summary.put("time_complexity", complexity.getTimeComplexity().getNotation());
        summary.put("space_complexity", complexity.getSpaceComplexity().getNotation());
        summary.put(PRACTICAL_LIMIT, limits.get(PRACTICAL_LIMIT));
        
        // Detailed findings
        List<String> findings = new ArrayList<>();
        
        // Complexity findings
        findings.add(String.format("%s exhibits %s time complexity with R²=%.3f",
            algorithm, complexity.getTimeComplexity().getNotation(), complexity.getRSquaredTime()));
        
        // Scalability findings
        double scalingFactor = (Double) vmScaling.get("scaling_factor");
        if (scalingFactor > 1.5) {
            findings.add("Algorithm shows super-linear scaling behavior");
        } else if (scalingFactor < 1.1) {
            findings.add("Algorithm demonstrates near-linear scalability");
        }
        
        // Limit findings
        findings.add(String.format("Practical scalability limit: %d VMs",
            limits.get(PRACTICAL_LIMIT)));
        
        // Performance trend findings
        analyzeTrendFindings(trends, findings);
        
        report.put("summary", summary);
        report.put("findings", findings);
        report.put("visualizations", generateScalabilityCharts(algorithm));
        
        return report;
    }
    
    private void analyzeTrendFindings(Map<String, List<PerformanceTrend>> trends,
                                     List<String> findings) {
        
        // Check execution time trends
        List<PerformanceTrend> timeTrends = trends.get(EXECUTION_TIME);
        if (timeTrends != null && !timeTrends.isEmpty()) {
            double avgIncrease = timeTrends.stream()
                .mapToDouble(t -> t.percentChange)
                .average().orElse(0.0);
            
            if (avgIncrease > 100) {
                findings.add("Execution time doubles or more with scale increases");
            }
        }
        
        // Check SLA trends
        List<PerformanceTrend> slaTrends = trends.get(SLA_VIOLATIONS);
        if (slaTrends != null && !slaTrends.isEmpty()) {
            boolean degrading = slaTrends.stream()
                .allMatch(t -> t.absoluteChange > 0);
            
            if (degrading) {
                findings.add("SLA compliance degrades consistently with scale");
            }
        }
    }
    
    private Map<String, JFreeChart> generateScalabilityCharts(String algorithm) {
        Map<String, JFreeChart> charts = new HashMap<>();
        
        try {
            // Execution time scaling chart
            JFreeChart timeChart = createTimeScalingChart(algorithm);
            charts.put("time_scaling", timeChart);
            
            // Memory scaling chart
            JFreeChart memoryChart = createMemoryScalingChart(algorithm);
            charts.put("memory_scaling", memoryChart);
            
            // Performance metrics chart
            JFreeChart metricsChart = createMetricsScalingChart(algorithm);
            charts.put("metrics_scaling", metricsChart);
            
        } catch (Exception e) {
            loggingManager.logError("Error generating charts", e);
        }
        
        return charts;
    }
    
    private JFreeChart createTimeScalingChart(String algorithm) {
        XYSeriesCollection dataset = new XYSeriesCollection();
        XYSeries actualSeries = new XYSeries("Actual");
        XYSeries predictedSeries = new XYSeries("Predicted");
        
        Map<Integer, List<ExperimentalResult>> data = scalabilityData.get(algorithm);
        ComplexityModel model = complexityModels.get(algorithm);
        
        List<Integer> sortedSizes = new ArrayList<>(data.keySet());
        Collections.sort(sortedSizes);
        
        for (int size : sortedSizes) {
            double avgTime = data.get(size).stream()
                .mapToDouble(r -> r.getExecutionTime())
                .average().orElse(0.0);
            
            actualSeries.add(size, avgTime);
            
            if (model != null && model.getTimeModel() != null) {
                double predicted = model.getTimeModel().value(size);
                predictedSeries.add(size, predicted);
            }
        }
        
        dataset.addSeries(actualSeries);
        dataset.addSeries(predictedSeries);
        
        JFreeChart chart = ChartFactory.createXYLineChart(
            "Execution Time Scaling - " + algorithm,
            PROBLEM_SIZE_VMS,
            "Execution Time (ms)",
            dataset,
            PlotOrientation.VERTICAL,
            true, true, false
        );
        
        // Customize chart
        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesLinesVisible(0, true);
        renderer.setSeriesShapesVisible(0, true);
        renderer.setSeriesLinesVisible(1, true);
        renderer.setSeriesShapesVisible(1, false);
        renderer.setSeriesStroke(1, new BasicStroke(2.0f, BasicStroke.CAP_ROUND, 
            BasicStroke.JOIN_ROUND, 1.0f, new float[] {10.0f, 10.0f}, 0.0f));
        plot.setRenderer(renderer);
        
        return chart;
    }
    
    private JFreeChart createMemoryScalingChart(String algorithm) {
        XYSeriesCollection dataset = new XYSeriesCollection();
        XYSeries series = new XYSeries("Memory Usage");
        
        Map<Integer, List<ExperimentalResult>> data = scalabilityData.get(algorithm);
        
        List<Integer> sortedSizes = new ArrayList<>(data.keySet());
        Collections.sort(sortedSizes);
        
        for (int size : sortedSizes) {
            double avgMemory = data.get(size).stream()
                .mapToDouble(r -> r.getMemoryUsage())
                .average().orElse(0.0);
            
            series.add(size, avgMemory / 1024.0); // Convert to GB
        }
        
        dataset.addSeries(series);
        
        return ChartFactory.createXYLineChart(
            "Memory Usage Scaling - " + algorithm,
            PROBLEM_SIZE_VMS,
            "Memory Usage (GB)",
            dataset,
            PlotOrientation.VERTICAL,
            true, true, false
        );
    }
    
    private JFreeChart createMetricsScalingChart(String algorithm) {
        XYSeriesCollection dataset = new XYSeriesCollection();
        XYSeries utilizationSeries = new XYSeries("Resource Utilization");
        XYSeries slaSeries = new XYSeries("SLA Violations (x10)");
        
        Map<Integer, List<ExperimentalResult>> data = scalabilityData.get(algorithm);
        
        List<Integer> sortedSizes = new ArrayList<>(data.keySet());
        Collections.sort(sortedSizes);
        
        for (int size : sortedSizes) {
            double avgUtil = data.get(size).stream()
                .mapToDouble(r -> r.getResourceUtilization())
                .average().orElse(0.0);
            double avgSLA = data.get(size).stream()
                .mapToDouble(r -> r.getSlaViolations())
                .average().orElse(0.0);
            
            utilizationSeries.add(size, avgUtil * 100); // Convert to percentage
            slaSeries.add(size, avgSLA * 10); // Scale for visibility
        }
        
        dataset.addSeries(utilizationSeries);
        dataset.addSeries(slaSeries);
        
        return ChartFactory.createXYLineChart(
            "Performance Metrics Scaling - " + algorithm,
            PROBLEM_SIZE_VMS,
            "Metric Value",
            dataset,
            PlotOrientation.VERTICAL,
            true, true, false
        );
    }
    
    // Helper classes
    
    private static class ScalingPoint {
        public int scale;
        public int vmCount;
        public int hostCount;
        public double avgExecutionTime;
    }
    
    public static class PerformanceTrend {
        public int fromScale;
        public int toScale;
        public String metricName;
        public double percentChange;
        public double absoluteChange;
        public double scaleFactor;
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