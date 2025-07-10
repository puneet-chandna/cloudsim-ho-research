package org.cloudbus.cloudsim.analyzer;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.math3.stat.inference.OneWayAnova;
import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.cloudbus.cloudsim.experiment.ExperimentalResult;
import org.cloudbus.cloudsim.util.ExperimentException;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive statistical analyzer for research experiments.
 * Provides descriptive and inferential statistical analysis capabilities.
 * @author Puneet Chandna
 */
public class ComprehensiveStatisticalAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(ComprehensiveStatisticalAnalyzer.class);
    
    private static final double DEFAULT_CONFIDENCE_LEVEL = 0.95;
    private static final double DEFAULT_SIGNIFICANCE_LEVEL = 0.05;
    
    private final Map<String, DescriptiveStatistics> metricStatistics;
    private final Map<String, List<Double>> rawData;
    
    public ComprehensiveStatisticalAnalyzer() {
        this.metricStatistics = new HashMap<>();
        this.rawData = new HashMap<>();
    }
    
    /**
     * Perform comprehensive descriptive analysis on experimental results.
     * 
     * @param results List of experimental results to analyze
     * @return Map containing descriptive statistics for each metric
     */
    public Map<String, DescriptiveAnalysisResult> performDescriptiveAnalysis(
            List<ExperimentalResult> results) {
        
        logger.info("Performing descriptive analysis on {} experimental results", results.size());
        
        if (results == null || results.isEmpty()) {
            throw new ExperimentException("Cannot perform analysis on null or empty results");
        }
        
        Map<String, DescriptiveAnalysisResult> analysisResults = new HashMap<>();
        
        try {
            // Extract metrics from results
            extractMetricsData(results);
            
            // Analyze each metric
            for (Map.Entry<String, List<Double>> entry : rawData.entrySet()) {
                String metricName = entry.getKey();
                List<Double> values = entry.getValue();
                
                DescriptiveStatistics stats = new DescriptiveStatistics();
                values.forEach(stats::addValue);
                
                DescriptiveAnalysisResult result = new DescriptiveAnalysisResult();
                result.setMetricName(metricName);
                result.setSampleSize(stats.getN());
                result.setMean(stats.getMean());
                result.setMedian(stats.getPercentile(50));
                result.setStandardDeviation(stats.getStandardDeviation());
                result.setVariance(stats.getVariance());
                result.setMin(stats.getMin());
                result.setMax(stats.getMax());
                result.setRange(stats.getMax() - stats.getMin());
                result.setSkewness(stats.getSkewness());
                result.setKurtosis(stats.getKurtosis());
                result.setPercentile25(stats.getPercentile(25));
                result.setPercentile75(stats.getPercentile(75));
                result.setIqr(stats.getPercentile(75) - stats.getPercentile(25));
                result.setCoefficientOfVariation(
                    stats.getStandardDeviation() / stats.getMean() * 100);
                
                // Store for later use
                metricStatistics.put(metricName, stats);
                analysisResults.put(metricName, result);
                
                logger.debug("Descriptive analysis for {}: mean={}, std={}", 
                    metricName, result.getMean(), result.getStandardDeviation());
            }
            
        } catch (Exception e) {
            throw new ExperimentException("Error performing descriptive analysis", e);
        }
        
        return analysisResults;
    }
    
    /**
     * Perform inferential statistical analysis including hypothesis testing.
     * 
     * @param algorithmResults Map of algorithm names to their results
     * @param baselineAlgorithm Name of the baseline algorithm for comparison
     * @return Map containing inferential analysis results
     */
    public Map<String, InferentialAnalysisResult> performInferentialAnalysis(
            Map<String, List<ExperimentalResult>> algorithmResults,
            String baselineAlgorithm) {
        
        logger.info("Performing inferential analysis with baseline: {}", baselineAlgorithm);
        
        if (!algorithmResults.containsKey(baselineAlgorithm)) {
            throw new ExperimentException("Baseline algorithm not found: " + baselineAlgorithm);
        }
        
        Map<String, InferentialAnalysisResult> analysisResults = new HashMap<>();
        
        try {
            List<ExperimentalResult> baselineResults = algorithmResults.get(baselineAlgorithm);
            Map<String, double[]> baselineData = extractMetricArrays(baselineResults);
            
            // Compare each algorithm against baseline
            for (Map.Entry<String, List<ExperimentalResult>> entry : algorithmResults.entrySet()) {
                String algorithmName = entry.getKey();
                if (algorithmName.equals(baselineAlgorithm)) {
                    continue;
                }
                
                List<ExperimentalResult> algorithmData = entry.getValue();
                Map<String, double[]> currentData = extractMetricArrays(algorithmData);
                
                // Perform tests for each metric
                for (String metric : baselineData.keySet()) {
                    String comparisonKey = algorithmName + "_vs_" + baselineAlgorithm + "_" + metric;
                    
                    double[] baselineValues = baselineData.get(metric);
                    double[] currentValues = currentData.get(metric);
                    
                    InferentialAnalysisResult result = performHypothesisTest(
                        baselineValues, currentValues, metric, algorithmName, baselineAlgorithm);
                    
                    analysisResults.put(comparisonKey, result);
                }
            }
            
        } catch (Exception e) {
            throw new ExperimentException("Error performing inferential analysis", e);
        }
        
        return analysisResults;
    }
    
    /**
     * Calculate confidence intervals for metrics.
     * 
     * @param results Experimental results
     * @param confidenceLevel Confidence level (e.g., 0.95 for 95%)
     * @return Map of metrics to their confidence intervals
     */
    public Map<String, ConfidenceInterval> calculateConfidenceIntervals(
            List<ExperimentalResult> results, double confidenceLevel) {
        
        logger.info("Calculating confidence intervals at {} level", confidenceLevel);
        
        Map<String, ConfidenceInterval> intervals = new HashMap<>();
        
        try {
            extractMetricsData(results);
            
            for (Map.Entry<String, List<Double>> entry : rawData.entrySet()) {
                String metricName = entry.getKey();
                List<Double> values = entry.getValue();
                
                SummaryStatistics stats = new SummaryStatistics();
                values.forEach(stats::addValue);
                
                double mean = stats.getMean();
                double stdError = stats.getStandardDeviation() / Math.sqrt(stats.getN());
                
                // Calculate t-value for given confidence level
                TDistribution tDist = new TDistribution(stats.getN() - 1);
                double tValue = tDist.inverseCumulativeProbability(
                    1 - (1 - confidenceLevel) / 2);
                
                double marginOfError = tValue * stdError;
                
                ConfidenceInterval ci = new ConfidenceInterval();
                ci.setMetricName(metricName);
                ci.setMean(mean);
                ci.setLowerBound(mean - marginOfError);
                ci.setUpperBound(mean + marginOfError);
                ci.setConfidenceLevel(confidenceLevel);
                ci.setMarginOfError(marginOfError);
                ci.setStandardError(stdError);
                
                intervals.put(metricName, ci);
                
                logger.debug("CI for {}: [{}, {}]", metricName, 
                    ci.getLowerBound(), ci.getUpperBound());
            }
            
        } catch (Exception e) {
            throw new ExperimentException("Error calculating confidence intervals", e);
        }
        
        return intervals;
    }
    
    /**
     * Perform Analysis of Variance (ANOVA) test.
     * 
     * @param algorithmResults Map of algorithm names to their results
     * @param metricName Name of the metric to analyze
     * @return ANOVA test result
     */
    public AnovaResult performANOVA(
            Map<String, List<ExperimentalResult>> algorithmResults, 
            String metricName) {
        
        logger.info("Performing ANOVA for metric: {}", metricName);
        
        if (algorithmResults.size() < 2) {
            throw new ExperimentException("ANOVA requires at least 2 groups");
        }
        
        try {
            OneWayAnova anova = new OneWayAnova();
            List<double[]> groups = new ArrayList<>();
            List<String> groupNames = new ArrayList<>();
            
            // Prepare data for ANOVA
            for (Map.Entry<String, List<ExperimentalResult>> entry : algorithmResults.entrySet()) {
                String algorithmName = entry.getKey();
                List<ExperimentalResult> results = entry.getValue();
                
                double[] values = extractMetricValues(results, metricName);
                groups.add(values);
                groupNames.add(algorithmName);
            }
            
            // Perform ANOVA
            double fStatistic = anova.anovaFValue(groups);
            double pValue = anova.anovaPValue(groups);
            boolean significant = pValue < DEFAULT_SIGNIFICANCE_LEVEL;
            
            // Calculate effect size (eta squared)
            double etaSquared = calculateEtaSquared(groups, fStatistic);
            
            AnovaResult result = new AnovaResult();
            result.setMetricName(metricName);
            result.setGroupNames(groupNames);
            result.setFStatistic(fStatistic);
            result.setPValue(pValue);
            result.setSignificant(significant);
            result.setEtaSquared(etaSquared);
            result.setGroupCount(groups.size());
            result.setTotalSampleSize(groups.stream().mapToInt(g -> g.length).sum());
            
            logger.info("ANOVA result: F={}, p={}, significant={}", 
                fStatistic, pValue, significant);
            
            return result;
            
        } catch (Exception e) {
            throw new ExperimentException("Error performing ANOVA", e);
        }
    }
    
    /**
     * Calculate effect sizes for comparing algorithms.
     * 
     * @param results1 Results from first algorithm
     * @param results2 Results from second algorithm
     * @param metricName Metric to analyze
     * @return Effect size calculations
     */
    public EffectSizeResult calculateEffectSizes(
            List<ExperimentalResult> results1,
            List<ExperimentalResult> results2,
            String metricName) {
        
        logger.info("Calculating effect sizes for metric: {}", metricName);
        
        try {
            double[] values1 = extractMetricValues(results1, metricName);
            double[] values2 = extractMetricValues(results2, metricName);
            
            DescriptiveStatistics stats1 = new DescriptiveStatistics(values1);
            DescriptiveStatistics stats2 = new DescriptiveStatistics(values2);
            
            double mean1 = stats1.getMean();
            double mean2 = stats2.getMean();
            double sd1 = stats1.getStandardDeviation();
            double sd2 = stats2.getStandardDeviation();
            double n1 = stats1.getN();
            double n2 = stats2.getN();
            
            // Cohen's d
            double pooledSD = Math.sqrt(((n1 - 1) * sd1 * sd1 + (n2 - 1) * sd2 * sd2) / 
                                       (n1 + n2 - 2));
            double cohensD = (mean1 - mean2) / pooledSD;
            
            // Hedges' g (corrected Cohen's d for small samples)
            double hedgesG = cohensD * (1 - 3 / (4 * (n1 + n2) - 9));
            
            // Glass's delta (when variances are unequal)
            double glassDelta = (mean1 - mean2) / sd2;
            
            // Probability of superiority (common language effect size)
            double probabilityOfSuperiority = calculateProbabilityOfSuperiority(values1, values2);
            
            EffectSizeResult result = new EffectSizeResult();
            result.setMetricName(metricName);
            result.setCohensD(cohensD);
            result.setHedgesG(hedgesG);
            result.setGlassDelta(glassDelta);
            result.setProbabilityOfSuperiority(probabilityOfSuperiority);
            result.setInterpretation(interpretEffectSize(Math.abs(cohensD)));
            
            logger.debug("Effect sizes: Cohen's d={}, Hedges' g={}", cohensD, hedgesG);
            
            return result;
            
        } catch (Exception e) {
            throw new ExperimentException("Error calculating effect sizes", e);
        }
    }
    
    // Helper methods
    
    private void extractMetricsData(List<ExperimentalResult> results) {
        rawData.clear();
        
        for (ExperimentalResult result : results) {
            Map<String, Double> metrics = result.getPerformanceMetrics();
            
            for (Map.Entry<String, Double> entry : metrics.entrySet()) {
                rawData.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                       .add(entry.getValue());
            }
        }
    }
    
    private Map<String, double[]> extractMetricArrays(List<ExperimentalResult> results) {
        Map<String, List<Double>> tempData = new HashMap<>();
        
        for (ExperimentalResult result : results) {
            Map<String, Double> metrics = result.getPerformanceMetrics();
            
            for (Map.Entry<String, Double> entry : metrics.entrySet()) {
                tempData.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                        .add(entry.getValue());
            }
        }
        
        Map<String, double[]> arrayData = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : tempData.entrySet()) {
            double[] array = entry.getValue().stream()
                                 .mapToDouble(Double::doubleValue)
                                 .toArray();
            arrayData.put(entry.getKey(), array);
        }
        
        return arrayData;
    }
    
    private double[] extractMetricValues(List<ExperimentalResult> results, String metricName) {
        return results.stream()
                     .map(r -> r.getPerformanceMetrics().get(metricName))
                     .filter(Objects::nonNull)
                     .mapToDouble(Double::doubleValue)
                     .toArray();
    }
    
    private InferentialAnalysisResult performHypothesisTest(
            double[] baseline, double[] current, String metric,
            String algorithm, String baselineAlgorithm) {
        
        TTest tTest = new TTest();
        
        double tStatistic = tTest.t(baseline, current);
        double pValue = tTest.tTest(baseline, current);
        boolean significant = pValue < DEFAULT_SIGNIFICANCE_LEVEL;
        
        InferentialAnalysisResult result = new InferentialAnalysisResult();
        result.setMetricName(metric);
        result.setAlgorithm1(baselineAlgorithm);
        result.setAlgorithm2(algorithm);
        result.setTestType("Two-sample t-test");
        result.setTestStatistic(tStatistic);
        result.setPValue(pValue);
        result.setSignificant(significant);
        result.setSignificanceLevel(DEFAULT_SIGNIFICANCE_LEVEL);
        
        return result;
    }
    
    private double calculateEtaSquared(List<double[]> groups, double fStatistic) {
        // Calculate total sum of squares and between-group sum of squares
        double grandMean = 0;
        int totalN = 0;
        
        for (double[] group : groups) {
            for (double value : group) {
                grandMean += value;
                totalN++;
            }
        }
        grandMean /= totalN;
        
        double totalSS = 0;
        for (double[] group : groups) {
            for (double value : group) {
                totalSS += Math.pow(value - grandMean, 2);
            }
        }
        
        // Eta squared = SS_between / SS_total
        // Can be approximated from F-statistic
        int k = groups.size();
        int dfBetween = k - 1;
        int dfWithin = totalN - k;
        
        return (fStatistic * dfBetween) / (fStatistic * dfBetween + dfWithin);
    }
    
    private double calculateProbabilityOfSuperiority(double[] values1, double[] values2) {
        int count = 0;
        int total = 0;
        
        for (double v1 : values1) {
            for (double v2 : values2) {
                if (v1 > v2) count++;
                total++;
            }
        }
        
        return (double) count / total;
    }
    
    private String interpretEffectSize(double cohensD) {
        if (cohensD < 0.2) return "Negligible";
        else if (cohensD < 0.5) return "Small";
        else if (cohensD < 0.8) return "Medium";
        else return "Large";
    }
    
    // Inner classes for results
    
    public static class DescriptiveAnalysisResult {
        private String metricName;
        private long sampleSize;
        private double mean;
        private double median;
        private double standardDeviation;
        private double variance;
        private double min;
        private double max;
        private double range;
        private double skewness;
        private double kurtosis;
        private double percentile25;
        private double percentile75;
        private double iqr;
        private double coefficientOfVariation;
        
        // Getters and setters
        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }
        
        public long getSampleSize() { return sampleSize; }
        public void setSampleSize(long sampleSize) { this.sampleSize = sampleSize; }
        
        public double getMean() { return mean; }
        public void setMean(double mean) { this.mean = mean; }
        
        public double getMedian() { return median; }
        public void setMedian(double median) { this.median = median; }
        
        public double getStandardDeviation() { return standardDeviation; }
        public void setStandardDeviation(double standardDeviation) { 
            this.standardDeviation = standardDeviation; 
        }
        
        public double getVariance() { return variance; }
        public void setVariance(double variance) { this.variance = variance; }
        
        public double getMin() { return min; }
        public void setMin(double min) { this.min = min; }
        
        public double getMax() { return max; }
        public void setMax(double max) { this.max = max; }
        
        public double getRange() { return range; }
        public void setRange(double range) { this.range = range; }
        
        public double getSkewness() { return skewness; }
        public void setSkewness(double skewness) { this.skewness = skewness; }
        
        public double getKurtosis() { return kurtosis; }
        public void setKurtosis(double kurtosis) { this.kurtosis = kurtosis; }
        
        public double getPercentile25() { return percentile25; }
        public void setPercentile25(double percentile25) { this.percentile25 = percentile25; }
        
        public double getPercentile75() { return percentile75; }
        public void setPercentile75(double percentile75) { this.percentile75 = percentile75; }
        
        public double getIqr() { return iqr; }
        public void setIqr(double iqr) { this.iqr = iqr; }
        
        public double getCoefficientOfVariation() { return coefficientOfVariation; }
        public void setCoefficientOfVariation(double coefficientOfVariation) { 
            this.coefficientOfVariation = coefficientOfVariation; 
        }
    }
    
    public static class InferentialAnalysisResult {
        private String metricName;
        private String algorithm1;
        private String algorithm2;
        private String testType;
        private double testStatistic;
        private double pValue;
        private boolean significant;
        private double significanceLevel;
        
        // Getters and setters
        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }
        
        public String getAlgorithm1() { return algorithm1; }
        public void setAlgorithm1(String algorithm1) { this.algorithm1 = algorithm1; }
        
        public String getAlgorithm2() { return algorithm2; }
        public void setAlgorithm2(String algorithm2) { this.algorithm2 = algorithm2; }
        
        public String getTestType() { return testType; }
        public void setTestType(String testType) { this.testType = testType; }
        
        public double getTestStatistic() { return testStatistic; }
        public void setTestStatistic(double testStatistic) { this.testStatistic = testStatistic; }
        
        public double getPValue() { return pValue; }
        public void setPValue(double pValue) { this.pValue = pValue; }
        
        public boolean isSignificant() { return significant; }
        public void setSignificant(boolean significant) { this.significant = significant; }
        
        public double getSignificanceLevel() { return significanceLevel; }
        public void setSignificanceLevel(double significanceLevel) { 
            this.significanceLevel = significanceLevel; 
        }
    }
    
    public static class ConfidenceInterval {
        private String metricName;
        private double mean;
        private double lowerBound;
        private double upperBound;
        private double confidenceLevel;
        private double marginOfError;
        private double standardError;
        
        // Getters and setters
        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }
        
        public double getMean() { return mean; }
        public void setMean(double mean) { this.mean = mean; }
        
        public double getLowerBound() { return lowerBound; }
        public void setLowerBound(double lowerBound) { this.lowerBound = lowerBound; }
        
        public double getUpperBound() { return upperBound; }
        public void setUpperBound(double upperBound) { this.upperBound = upperBound; }
        
        public double getConfidenceLevel() { return confidenceLevel; }
        public void setConfidenceLevel(double confidenceLevel) { 
            this.confidenceLevel = confidenceLevel; 
        }
        
        public double getMarginOfError() { return marginOfError; }
        public void setMarginOfError(double marginOfError) { this.marginOfError = marginOfError; }
        
        public double getStandardError() { return standardError; }
        public void setStandardError(double standardError) { this.standardError = standardError; }
    }
    
    public static class AnovaResult {
        private String metricName;
        private List<String> groupNames;
        private double fStatistic;
        private double pValue;
        private boolean significant;
        private double etaSquared;
        private int groupCount;
        private int totalSampleSize;
        
        // Getters and setters
        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }
        
        public List<String> getGroupNames() { return groupNames; }
        public void setGroupNames(List<String> groupNames) { this.groupNames = groupNames; }
        
        public double getFStatistic() { return fStatistic; }
        public void setFStatistic(double fStatistic) { this.fStatistic = fStatistic; }
        
        public double getPValue() { return pValue; }
        public void setPValue(double pValue) { this.pValue = pValue; }
        
        public boolean isSignificant() { return significant; }
        public void setSignificant(boolean significant) { this.significant = significant; }
        
        public double getEtaSquared() { return etaSquared; }
        public void setEtaSquared(double etaSquared) { this.etaSquared = etaSquared; }
        
        public int getGroupCount() { return groupCount; }
        public void setGroupCount(int groupCount) { this.groupCount = groupCount; }
        
        public int getTotalSampleSize() { return totalSampleSize; }
        public void setTotalSampleSize(int totalSampleSize) { 
            this.totalSampleSize = totalSampleSize; 
        }
    }
    
    public static class EffectSizeResult {
        private String metricName;
        private double cohensD;
        private double hedgesG;
        private double glassDelta;
        private double probabilityOfSuperiority;
        private String interpretation;
        
        // Getters and setters
        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }
        
        public double getCohensD() { return cohensD; }
        public void setCohensD(double cohensD) { this.cohensD = cohensD; }
        
        public double getHedgesG() { return hedgesG; }
        public void setHedgesG(double hedgesG) { this.hedgesG = hedgesG; }
        
        public double getGlassDelta() { return glassDelta; }
        public void setGlassDelta(double glassDelta) { this.glassDelta = glassDelta; }
        
        public double getProbabilityOfSuperiority() { return probabilityOfSuperiority; }
        public void setProbabilityOfSuperiority(double probabilityOfSuperiority) { 
            this.probabilityOfSuperiority = probabilityOfSuperiority; 
        }
        
        public String getInterpretation() { return interpretation; }
        public void setInterpretation(String interpretation) { 
            this.interpretation = interpretation; 
        }
    }
}