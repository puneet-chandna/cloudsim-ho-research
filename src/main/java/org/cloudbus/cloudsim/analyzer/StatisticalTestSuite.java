package org.cloudbus.cloudsim.analyzer;

import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.*;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import org.apache.commons.math3.stat.ranking.NaNStrategy;
import org.apache.commons.math3.stat.ranking.TiesStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive statistical test suite for research validation.
 * Implements various parametric and non-parametric tests for
 * algorithm comparison and result validation.
 * 
 * Research focus: Provides rigorous statistical testing required
 * for publication-quality research results.
 * @author Puneet Chandna
 */
public class StatisticalTestSuite {
    private static final Logger logger = LoggerFactory.getLogger(StatisticalTestSuite.class);
    
    // Statistical test parameters
    private static final double DEFAULT_SIGNIFICANCE_LEVEL = 0.05;
    private static final double NORMALITY_TEST_ALPHA = 0.05;
    private static final int MIN_SAMPLE_SIZE = 5;
    private static final int BOOTSTRAP_ITERATIONS = 10000;
    
    private final TTest tTest;
    private final MannWhitneyUTest mannWhitneyUTest;
    private final WilcoxonSignedRankTest wilcoxonTest;
    private final OneWayAnova anovaTest;
    private final KolmogorovSmirnovTest ksTest;
    
    public StatisticalTestSuite() {
        this.tTest = new TTest();
        this.mannWhitneyUTest = new MannWhitneyUTest();
        this.wilcoxonTest = new WilcoxonSignedRankTest();
        this.anovaTest = new OneWayAnova();
        this.ksTest = new KolmogorovSmirnovTest();
    }
    
    /**
     * Perform Student's t-test for comparing two algorithms
     */
    public TTestResult performTTest(double[] sample1, double[] sample2, 
                                   boolean pairedSamples) {
        logger.info("Performing {} t-test", pairedSamples ? "paired" : "independent");
        
        validateSampleSize(sample1, sample2);
        
        TTestResult result = new TTestResult();
        result.sampleSize1 = sample1.length;
        result.sampleSize2 = sample2.length;
        result.mean1 = StatUtils.mean(sample1);
        result.mean2 = StatUtils.mean(sample2);
        result.stdDev1 = Math.sqrt(StatUtils.variance(sample1));
        result.stdDev2 = Math.sqrt(StatUtils.variance(sample2));
        
        try {
            if (pairedSamples) {
                result.tStatistic = tTest.pairedT(sample1, sample2);
                result.pValue = tTest.pairedTTest(sample1, sample2);
                result.degreesOfFreedom = sample1.length - 1;
            } else {
                result.tStatistic = tTest.t(sample1, sample2);
                result.pValue = tTest.tTest(sample1, sample2);
                result.degreesOfFreedom = sample1.length + sample2.length - 2;
            }
            
            result.significant = result.pValue < DEFAULT_SIGNIFICANCE_LEVEL;
            result.effectSize = calculateCohenD(sample1, sample2);
            result.confidenceInterval = calculateConfidenceInterval(
                result.mean1 - result.mean2, 
                result.stdDev1, 
                result.stdDev2, 
                sample1.length, 
                sample2.length
            );
            
            // Check assumptions
            result.normalityAssumptionMet = checkNormality(sample1) && checkNormality(sample2);
            result.equalVarianceAssumptionMet = checkEqualVariance(sample1, sample2);
            
        } catch (Exception e) {
            logger.error("Error performing t-test: {}", e.getMessage());
            result.valid = false;
        }
        
        return result;
    }
    
    /**
     * Perform Wilcoxon signed-rank test for paired samples
     */
    public WilcoxonTestResult performWilcoxonTest(double[] sample1, double[] sample2) {
        logger.info("Performing Wilcoxon signed-rank test");
        
        validateSampleSize(sample1, sample2);
        
        WilcoxonTestResult result = new WilcoxonTestResult();
        result.sampleSize = sample1.length;
        result.median1 = calculateMedian(sample1);
        result.median2 = calculateMedian(sample2);
        
        try {
            result.wStatistic = wilcoxonTest.wilcoxonSignedRank(sample1, sample2);
            result.pValue = wilcoxonTest.wilcoxonSignedRankTest(sample1, sample2, true);
            result.significant = result.pValue < DEFAULT_SIGNIFICANCE_LEVEL;
            
            // Calculate effect size (r = Z / sqrt(N))
            double z = calculateZScore(result.wStatistic, sample1.length);
            result.effectSize = Math.abs(z) / Math.sqrt(sample1.length);
            
            // Calculate confidence interval for median difference
            result.confidenceInterval = calculateWilcoxonConfidenceInterval(sample1, sample2);
            
        } catch (Exception e) {
            logger.error("Error performing Wilcoxon test: {}", e.getMessage());
            result.valid = false;
        }
        
        return result;
    }
    
    /**
     * Perform Kruskal-Wallis test for multiple groups
     */
    public KruskalWallisResult performKruskalWallisTest(List<double[]> samples, 
                                                       List<String> groupNames) {
        logger.info("Performing Kruskal-Wallis test for {} groups", samples.size());
        
        if (samples.size() < 3) {
            throw new IllegalArgumentException("Kruskal-Wallis test requires at least 3 groups");
        }
        
        KruskalWallisResult result = new KruskalWallisResult();
        result.numberOfGroups = samples.size();
        result.groupNames = new ArrayList<>(groupNames);
        
        try {
            // Prepare data for Kruskal-Wallis test
            List<Double> allData = new ArrayList<>();
            List<Integer> groups = new ArrayList<>();
            
            for (int i = 0; i < samples.size(); i++) {
                double[] sample = samples.get(i);
                for (double value : sample) {
                    allData.add(value);
                    groups.add(i);
                }
                
                result.groupSizes.add(sample.length);
                result.groupMedians.add(calculateMedian(sample));
            }
            
            // Perform test using custom implementation
            result.hStatistic = calculateKruskalWallisH(samples);
            result.degreesOfFreedom = samples.size() - 1;
            result.pValue = calculateKruskalWallisPValue(result.hStatistic, result.degreesOfFreedom);
            result.significant = result.pValue < DEFAULT_SIGNIFICANCE_LEVEL;
            
            // Post-hoc analysis if significant
            if (result.significant) {
                result.postHocResults = performDunnPostHoc(samples, groupNames);
            }
            
        } catch (Exception e) {
            logger.error("Error performing Kruskal-Wallis test: {}", e.getMessage());
            result.valid = false;
        }
        
        return result;
    }
    
    /**
     * Adjust p-values for multiple comparisons
     */
    public MultipleComparisonResult adjustPValues(List<Double> pValues, 
                                                 List<String> comparisonNames,
                                                 String method) {
        logger.info("Adjusting p-values using {} method", method);
        
        MultipleComparisonResult result = new MultipleComparisonResult();
        result.method = method;
        result.originalPValues = new ArrayList<>(pValues);
        result.comparisonNames = new ArrayList<>(comparisonNames);
        
        switch (method.toUpperCase()) {
            case "BONFERRONI":
                result.adjustedPValues = bonferroniCorrection(pValues);
                break;
            case "HOLM":
                result.adjustedPValues = holmCorrection(pValues);
                break;
            case "BENJAMINI-HOCHBERG":
            case "FDR":
                result.adjustedPValues = benjaminiHochbergCorrection(pValues);
                break;
            default:
                throw new IllegalArgumentException("Unknown correction method: " + method);
        }
        
        // Determine which comparisons remain significant
        for (int i = 0; i < result.adjustedPValues.size(); i++) {
            result.significantAfterCorrection.add(
                result.adjustedPValues.get(i) < DEFAULT_SIGNIFICANCE_LEVEL
            );
        }
        
        return result;
    }
    
    /**
     * Calculate statistical power for a given effect size
     */
    public PowerAnalysisResult calculatePowerAnalysis(int sampleSize, 
                                                     double effectSize, 
                                                     double alpha,
                                                     String testType) {
        logger.info("Calculating statistical power for {} test", testType);
        
        PowerAnalysisResult result = new PowerAnalysisResult();
        result.sampleSize = sampleSize;
        result.effectSize = effectSize;
        result.alpha = alpha;
        result.testType = testType;
        
        try {
            // Calculate power based on test type
            switch (testType.toUpperCase()) {
                case "T-TEST":
                    result.power = calculateTTestPower(sampleSize, effectSize, alpha);
                    break;
                case "ANOVA":
                    result.power = calculateAnovaPower(sampleSize, effectSize, alpha);
                    break;
                case "CORRELATION":
                    result.power = calculateCorrelationPower(sampleSize, effectSize, alpha);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown test type: " + testType);
            }
            
            // Calculate required sample size for target power
            result.requiredSampleSizeFor80Power = calculateRequiredSampleSize(
                effectSize, alpha, 0.80, testType
            );
            result.requiredSampleSizeFor90Power = calculateRequiredSampleSize(
                effectSize, alpha, 0.90, testType
            );
            
        } catch (Exception e) {
            logger.error("Error calculating power: {}", e.getMessage());
            result.valid = false;
        }
        
        return result;
    }
    
    // Private helper methods
    
    private void validateSampleSize(double[]... samples) {
        for (double[] sample : samples) {
            if (sample.length < MIN_SAMPLE_SIZE) {
                throw new IllegalArgumentException(
                    "Sample size must be at least " + MIN_SAMPLE_SIZE
                );
            }
        }
    }
    
    private double calculateCohenD(double[] sample1, double[] sample2) {
        double mean1 = StatUtils.mean(sample1);
        double mean2 = StatUtils.mean(sample2);
        double pooledStdDev = Math.sqrt(
            ((sample1.length - 1) * StatUtils.variance(sample1) + 
             (sample2.length - 1) * StatUtils.variance(sample2)) /
            (sample1.length + sample2.length - 2)
        );
        
        return (mean1 - mean2) / pooledStdDev;
    }
    
    private double[] calculateConfidenceInterval(double meanDiff, double std1, double std2,
                                               int n1, int n2) {
        double standardError = Math.sqrt((std1 * std1) / n1 + (std2 * std2) / n2);
        double degreesOfFreedom = n1 + n2 - 2;
        TDistribution tDist = new TDistribution(degreesOfFreedom);
        double tCritical = tDist.inverseCumulativeProbability(1 - DEFAULT_SIGNIFICANCE_LEVEL / 2);
        
        double marginOfError = tCritical * standardError;
        return new double[]{meanDiff - marginOfError, meanDiff + marginOfError};
    }
    
    private boolean checkNormality(double[] sample) {
        try {
            // Use Kolmogorov-Smirnov test for normality
            NormalDistribution normalDist = new NormalDistribution(
                StatUtils.mean(sample),
                Math.sqrt(StatUtils.variance(sample))
            );
            
            double pValue = ksTest.kolmogorovSmirnovTest(normalDist, sample);
            return pValue > NORMALITY_TEST_ALPHA;
        } catch (Exception e) {
            logger.warn("Error checking normality: {}", e.getMessage());
            return false;
        }
    }
    
    private boolean checkEqualVariance(double[] sample1, double[] sample2) {
        double var1 = StatUtils.variance(sample1);
        double var2 = StatUtils.variance(sample2);
        double fStatistic = Math.max(var1, var2) / Math.min(var1, var2);
        
        // Simple F-test approximation
        return fStatistic < 3.0; // Rule of thumb
    }
    
    private double calculateMedian(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        
        if (n % 2 == 0) {
            return (sorted[n/2 - 1] + sorted[n/2]) / 2.0;
        } else {
            return sorted[n/2];
        }
    }
    
    private double calculateZScore(double wStatistic, int n) {
        // Approximation for large samples
        double mean = n * (n + 1) / 4.0;
        double stdDev = Math.sqrt(n * (n + 1) * (2 * n + 1) / 24.0);
        return (wStatistic - mean) / stdDev;
    }
    
    private double[] calculateWilcoxonConfidenceInterval(double[] sample1, double[] sample2) {
        // Simplified confidence interval calculation
        double[] differences = new double[sample1.length];
        for (int i = 0; i < sample1.length; i++) {
            differences[i] = sample1[i] - sample2[i];
        }
        
        Arrays.sort(differences);
        int n = differences.length;
        int lowerIndex = (int) Math.ceil(n * DEFAULT_SIGNIFICANCE_LEVEL / 2) - 1;
        int upperIndex = n - lowerIndex - 1;
        
        return new double[]{differences[lowerIndex], differences[upperIndex]};
    }
    
    private double calculateKruskalWallisH(List<double[]> samples) {
        // Combine all samples and rank them
        List<Double> allValues = new ArrayList<>();
        List<Integer> groupLabels = new ArrayList<>();
        
        for (int i = 0; i < samples.size(); i++) {
            for (double value : samples.get(i)) {
                allValues.add(value);
                groupLabels.add(i);
            }
        }
        
        // Rank the combined data
        double[] ranks = rankData(allValues);
        
        // Calculate H statistic
        int n = allValues.size();
        double sumOfRankSums = 0.0;
        
        for (int i = 0; i < samples.size(); i++) {
            double rankSum = 0.0;
            int groupSize = 0;
            
            for (int j = 0; j < n; j++) {
                if (groupLabels.get(j) == i) {
                    rankSum += ranks[j];
                    groupSize++;
                }
            }
            
            sumOfRankSums += (rankSum * rankSum) / groupSize;
        }
        
        double h = (12.0 / (n * (n + 1))) * sumOfRankSums - 3 * (n + 1);
        
        return h;
    }
    
    private double[] rankData(List<Double> data) {
        int n = data.size();
        double[] ranks = new double[n];
        
        // Create index array
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) {
            indices[i] = i;
        }
        
        // Sort indices by data values
        Arrays.sort(indices, Comparator.comparing(data::get));
        
        // Assign ranks, handling ties
        for (int i = 0; i < n; ) {
            int j = i;
            while (j < n && data.get(indices[j]).equals(data.get(indices[i]))) {
                j++;
            }
            
            double avgRank = (i + j + 1) / 2.0;
            for (int k = i; k < j; k++) {
                ranks[indices[k]] = avgRank;
            }
            
            i = j;
        }
        
        return ranks;
    }
    
    private double calculateKruskalWallisPValue(double hStatistic, int degreesOfFreedom) {
        // Chi-square approximation
        org.apache.commons.math3.distribution.ChiSquaredDistribution chiSquared = 
            new org.apache.commons.math3.distribution.ChiSquaredDistribution(degreesOfFreedom);
        
        return 1 - chiSquared.cumulativeProbability(hStatistic);
    }
    
    private Map<String, Double> performDunnPostHoc(List<double[]> samples, 
                                                   List<String> groupNames) {
        Map<String, Double> postHocPValues = new HashMap<>();
        
        // Perform pairwise comparisons
        for (int i = 0; i < samples.size() - 1; i++) {
            for (int j = i + 1; j < samples.size(); j++) {
                String comparison = groupNames.get(i) + " vs " + groupNames.get(j);
                
                // Use Mann-Whitney U test for pairwise comparison
                double pValue = mannWhitneyUTest.mannWhitneyUTest(
                    samples.get(i), samples.get(j)
                );
                
                postHocPValues.put(comparison, pValue);
            }
        }
        
        return postHocPValues;
    }
    
    private List<Double> bonferroniCorrection(List<Double> pValues) {
        int m = pValues.size();
        return pValues.stream()
            .map(p -> Math.min(p * m, 1.0))
            .collect(Collectors.toList());
    }
    
    private List<Double> holmCorrection(List<Double> pValues) {
        int m = pValues.size();
        
        // Create index array and sort by p-values
        Integer[] indices = new Integer[m];
        for (int i = 0; i < m; i++) {
            indices[i] = i;
        }
        Arrays.sort(indices, Comparator.comparing(pValues::get));
        
        // Apply Holm correction
        List<Double> adjusted = new ArrayList<>(Collections.nCopies(m, 0.0));
        for (int i = 0; i < m; i++) {
            double adjustedP = pValues.get(indices[i]) * (m - i);
            adjustedP = Math.min(adjustedP, 1.0);
            
            if (i > 0) {
                adjustedP = Math.max(adjustedP, adjusted.get(indices[i-1]));
            }
            
            adjusted.set(indices[i], adjustedP);
        }
        
        return adjusted;
    }
    
    private List<Double> benjaminiHochbergCorrection(List<Double> pValues) {
        int m = pValues.size();
        
        // Create index array and sort by p-values
        Integer[] indices = new Integer[m];
        for (int i = 0; i < m; i++) {
            indices[i] = i;
        }
        Arrays.sort(indices, Comparator.comparing(pValues::get));
        
        // Apply Benjamini-Hochberg correction
        List<Double> adjusted = new ArrayList<>(Collections.nCopies(m, 0.0));
        for (int i = m - 1; i >= 0; i--) {
            double adjustedP = pValues.get(indices[i]) * m / (i + 1);
            adjustedP = Math.min(adjustedP, 1.0);
            
            if (i < m - 1) {
                adjustedP = Math.min(adjustedP, adjusted.get(indices[i+1]));
            }
            
            adjusted.set(indices[i], adjustedP);
        }
        
        return adjusted;
    }
    
    private double calculateTTestPower(int n, double effectSize, double alpha) {
        // Non-central t-distribution approach
        double ncp = effectSize * Math.sqrt(n / 2.0); // Non-centrality parameter
        TDistribution centralT = new TDistribution(2 * n - 2);
        double criticalValue = centralT.inverseCumulativeProbability(1 - alpha / 2);
        
        // Approximation using normal distribution
        NormalDistribution normal = new NormalDistribution(ncp, 1.0);
        return 1 - normal.cumulativeProbability(criticalValue) + 
               normal.cumulativeProbability(-criticalValue);
    }
    
    private double calculateAnovaPower(int n, double effectSize, double alpha) {
        // Simplified power calculation for one-way ANOVA
        double ncp = n * effectSize * effectSize; // Non-centrality parameter
        
        // Use chi-square approximation
        org.apache.commons.math3.distribution.ChiSquaredDistribution chiSquared = 
            new org.apache.commons.math3.distribution.ChiSquaredDistribution(2); // df = k-1
        
        double criticalValue = chiSquared.inverseCumulativeProbability(1 - alpha);
        
        // Non-central chi-square approximation
        return 1 - new NormalDistribution(ncp, Math.sqrt(2 * ncp)).cumulativeProbability(criticalValue);
    }
    
    private double calculateCorrelationPower(int n, double effectSize, double alpha) {
        // Fisher's z transformation
        double z = 0.5 * Math.log((1 + effectSize) / (1 - effectSize));
        double se = 1.0 / Math.sqrt(n - 3);
        
        NormalDistribution normal = new NormalDistribution();
        double criticalZ = normal.inverseCumulativeProbability(1 - alpha / 2);
        
        double power = 1 - normal.cumulativeProbability((criticalZ - z) / se) +
                      normal.cumulativeProbability((-criticalZ - z) / se);
        
        return power;
    }
    
    private int calculateRequiredSampleSize(double effectSize, double alpha, 
                                          double targetPower, String testType) {
        // Binary search for required sample size
        int minN = 5;
        int maxN = 10000;
        
        while (minN < maxN) {
            int midN = (minN + maxN) / 2;
            double power = 0.0;
            
            switch (testType.toUpperCase()) {
                case "T-TEST":
                    power = calculateTTestPower(midN, effectSize, alpha);
                    break;
                case "ANOVA":
                    power = calculateAnovaPower(midN, effectSize, alpha);
                    break;
                case "CORRELATION":
                    power = calculateCorrelationPower(midN, effectSize, alpha);
                    break;
            }
            
            if (power < targetPower) {
                minN = midN + 1;
            } else {
                maxN = midN;
            }
        }
        
        return minN;
    }
    
    // Result classes
    
    public static class TTestResult {
        public int sampleSize1;
        public int sampleSize2;
        public double mean1;
        public double mean2;
        public double stdDev1;
        public double stdDev2;
        public double tStatistic;
        public double pValue;
        public double degreesOfFreedom;
        public boolean significant;
        public double effectSize;
        public double[] confidenceInterval;
        public boolean normalityAssumptionMet;
        public boolean equalVarianceAssumptionMet;
        public boolean valid = true;
    }
    
    public static class WilcoxonTestResult {
        public int sampleSize;
        public double median1;
        public double median2;
        public double wStatistic;
        public double pValue;
        public boolean significant;
        public double effectSize;
        public double[] confidenceInterval;
        public boolean valid = true;
    }
    
    public static class KruskalWallisResult {
        public int numberOfGroups;
        public List<String> groupNames;
        public List<Integer> groupSizes = new ArrayList<>();
        public List<Double> groupMedians = new ArrayList<>();
        public double hStatistic;
        public double degreesOfFreedom;
        public double pValue;
        public boolean significant;
        public Map<String, Double> postHocResults;
        public boolean valid = true;
    }
    
    public static class MultipleComparisonResult {
        public String method;
        public List<String> comparisonNames;
        public List<Double> originalPValues;
        public List<Double> adjustedPValues;
        public List<Boolean> significantAfterCorrection = new ArrayList<>();
    }
    
    public static class PowerAnalysisResult {
        public int sampleSize;
        public double effectSize;
        public double alpha;
        public String testType;
        public double power;
        public int requiredSampleSizeFor80Power;
        public int requiredSampleSizeFor90Power;
        public boolean valid = true;
    }
}