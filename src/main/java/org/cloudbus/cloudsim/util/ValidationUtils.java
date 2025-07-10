package org.cloudbus.cloudsim.util;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.TestUtils;
import org.cloudbus.cloudsim.experiment.ExperimentalResult;
import org.cloudbus.cloudsim.experiment.ExperimentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validation utilities for experiments
 */
public class ValidationUtils {
    private static final Logger logger = LoggerFactory.getLogger(ValidationUtils.class);
    
    // Validation thresholds
    private static final double MIN_RESOURCE_UTILIZATION = 0.0;
    private static final double MAX_RESOURCE_UTILIZATION = 100.0;
    private static final double MIN_POWER_CONSUMPTION = 0.0;
    private static final double EPSILON = 1e-9;
    private static final int MIN_SAMPLE_SIZE = 30;
    private static final double OUTLIER_THRESHOLD = 3.0; // 3 standard deviations
    
    /**
     * Validate experimental results
     */
    public static ValidationReport validateResults(List<ExperimentalResult> results) {
        if (results == null || results.isEmpty()) {
            throw new ExperimentException("No results to validate");
        }
        
        ValidationReport report = new ValidationReport();
        
        // Check data completeness
        report.addCheck("Data Completeness", validateDataCompleteness(results));
        
        // Check metric ranges
        report.addCheck("Metric Ranges", validateMetricRanges(results));
        
        // Check for outliers
        report.addCheck("Outlier Detection", detectOutliers(results));
        
        // Check consistency
        report.addCheck("Result Consistency", validateConsistency(results));
        
        // Check statistical validity
        report.addCheck("Statistical Validity", validateStatisticalProperties(results));
        
        return report;
    }
    
    /**
     * Check data integrity
     */
    public static DataIntegrityReport checkDataIntegrity(Object data) {
        DataIntegrityReport report = new DataIntegrityReport();
        
        if (data == null) {
            report.addError("Data is null");
            return report;
        }
        
        if (data instanceof List) {
            List<?> list = (List<?>) data;
            if (list.isEmpty()) {
                report.addError("List is empty");
            }
            
            // Check for null elements
            long nullCount = list.stream().filter(Objects::isNull).count();
            if (nullCount > 0) {
                report.addError("List contains " + nullCount + " null elements");
            }
            
            // Check for duplicates
            long uniqueCount = list.stream().filter(Objects::nonNull).distinct().count();
            if (uniqueCount < list.size() - nullCount) {
                report.addWarning("List contains duplicates");
            }
        } else if (data instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) data;
            if (map.isEmpty()) {
                report.addError("Map is empty");
            }
            
            // Check for null keys or values
            long nullKeys = map.keySet().stream().filter(Objects::isNull).count();
            long nullValues = map.values().stream().filter(Objects::isNull).count();
            
            if (nullKeys > 0) {
                report.addError("Map contains " + nullKeys + " null keys");
            }
            if (nullValues > 0) {
                report.addWarning("Map contains " + nullValues + " null values");
            }
        }
        
        report.setValid(report.getErrors().isEmpty());
        return report;
    }
    
    /**
     * Validate statistical test assumptions
     */
    public static StatisticalValidation validateStatisticalTests(double[] data1, double[] data2) {
        StatisticalValidation validation = new StatisticalValidation();
        
        // Check sample size
        if (data1.length < MIN_SAMPLE_SIZE || data2.length < MIN_SAMPLE_SIZE) {
            validation.addIssue("Sample size too small for reliable statistical tests");
            validation.setSufficientSampleSize(false);
        }
        
        // Test normality
        boolean data1Normal = testNormality(data1);
        boolean data2Normal = testNormality(data2);
        validation.setNormalityAssumptionMet(data1Normal && data2Normal);
        
        if (!data1Normal || !data2Normal) {
            validation.addIssue("Data does not follow normal distribution - use non-parametric tests");
            validation.addRecommendation("Use Wilcoxon rank-sum test instead of t-test");
        }
        
        // Test variance homogeneity
        boolean equalVariances = testVarianceHomogeneity(data1, data2);
        validation.setHomogeneityAssumptionMet(equalVariances);
        
        if (!equalVariances) {
            validation.addIssue("Variances are not equal - use Welch's t-test");
            validation.addRecommendation("Use Welch's t-test for unequal variances");
        }
        
        // Check independence
        validation.setIndependenceAssumptionMet(true); // Assumed unless proven otherwise
        
        return validation;
    }
    
    /**
     * Generate validation report
     */
    public static String generateValidationReport(ValidationReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== Validation Report ===\n");
        sb.append("Overall Status: ").append(report.isValid() ? "PASSED" : "FAILED").append("\n");
        sb.append("Timestamp: ").append(report.getTimestamp()).append("\n\n");
        
        sb.append("Validation Checks:\n");
        for (Map.Entry<String, ValidationCheck> entry : report.getChecks().entrySet()) {
            ValidationCheck check = entry.getValue();
            sb.append("  ").append(entry.getKey()).append(": ")
              .append(check.isPassed() ? "PASSED" : "FAILED").append("\n");
            
            if (!check.getIssues().isEmpty()) {
                sb.append("    Issues:\n");
                check.getIssues().forEach(issue -> 
                    sb.append("      - ").append(issue).append("\n"));
            }
        }
        
        if (!report.getWarnings().isEmpty()) {
            sb.append("\nWarnings:\n");
            report.getWarnings().forEach(warning -> 
                sb.append("  - ").append(warning).append("\n"));
        }
        
        if (!report.getErrors().isEmpty()) {
            sb.append("\nErrors:\n");
            report.getErrors().forEach(error -> 
                sb.append("  - ").append(error).append("\n"));
        }
        
        return sb.toString();
    }
    
    /**
     * Validate experiment configuration
     */
    public static void validateConfiguration(ExperimentConfig config) {
        if (config == null) {
            throw new ExperimentException("Configuration is null");
        }
        
        // Validate algorithm parameters
        if (config.getAlgorithmName() == null || config.getAlgorithmName().isEmpty()) {
            throw new ExperimentException("Algorithm name is not specified");
        }
        
        if (config.getParameters() == null || config.getParameters().isEmpty()) {
            throw new ExperimentException("Algorithm parameters are not specified");
        }
        
        // Validate numeric parameters
        config.getParameters().forEach((key, value) -> {
            if (value instanceof Number) {
                double numValue = ((Number) value).doubleValue();
                if (Double.isNaN(numValue) || Double.isInfinite(numValue)) {
                    throw new ExperimentException("Invalid numeric parameter: " + key);
                }
            }
        });
        
        // Validate scenario configuration
        if (config.getScenarioConfig() == null) {
            throw new ExperimentException("Scenario configuration is missing");
        }
        
        // Validate VM and host counts
        int vmCount = config.getScenarioConfig().getVmCount();
        int hostCount = config.getScenarioConfig().getHostCount();
        
        if (vmCount <= 0) {
            throw new ExperimentException("VM count must be positive");
        }
        
        if (hostCount <= 0) {
            throw new ExperimentException("Host count must be positive");
        }
        
        if (vmCount > hostCount * 100) {
            logger.warn("VM to host ratio is very high: {} VMs for {} hosts", vmCount, hostCount);
        }
    }
    
    // Private helper methods
    
    private static ValidationCheck validateDataCompleteness(List<ExperimentalResult> results) {
        ValidationCheck check = new ValidationCheck();
        
        for (ExperimentalResult result : results) {
            if (result.getMetrics() == null) {
                check.addIssue("Result missing metrics");
            } else {
                // Check for required metrics
                if (!result.getMetrics().containsKey("resourceUtilization")) {
                    check.addIssue("Missing resource utilization metric");
                }
                if (!result.getMetrics().containsKey("powerConsumption")) {
                    check.addIssue("Missing power consumption metric");
                }
                if (!result.getMetrics().containsKey("slaViolations")) {
                    check.addIssue("Missing SLA violations metric");
                }
            }
        }
        
        check.setPassed(check.getIssues().isEmpty());
        return check;
    }
    
    private static ValidationCheck validateMetricRanges(List<ExperimentalResult> results) {
        ValidationCheck check = new ValidationCheck();
        
        for (ExperimentalResult result : results) {
            Map<String, Double> metrics = result.getMetrics();
            if (metrics == null) continue;
            
            // Validate resource utilization
            Double utilization = metrics.get("resourceUtilization");
            if (utilization != null && (utilization < MIN_RESOURCE_UTILIZATION || 
                                       utilization > MAX_RESOURCE_UTILIZATION)) {
                check.addIssue("Resource utilization out of range: " + utilization);
            }
            
            // Validate power consumption
            Double power = metrics.get("powerConsumption");
            if (power != null && power < MIN_POWER_CONSUMPTION) {
                check.addIssue("Invalid power consumption: " + power);
            }
            
            // Validate SLA violations (should be non-negative)
            Double violations = metrics.get("slaViolations");
            if (violations != null && violations < 0) {
                check.addIssue("Negative SLA violations: " + violations);
            }
        }
        
        check.setPassed(check.getIssues().isEmpty());
        return check;
    }
    
    private static ValidationCheck detectOutliers(List<ExperimentalResult> results) {
        ValidationCheck check = new ValidationCheck();
        
        // Extract metrics for outlier detection
        Map<String, DescriptiveStatistics> statsMap = new HashMap<>();
        
        for (ExperimentalResult result : results) {
            if (result.getMetrics() == null) continue;
            
            result.getMetrics().forEach((metric, value) -> {
                statsMap.computeIfAbsent(metric, k -> new DescriptiveStatistics())
                       .addValue(value);
            });
        }
        
        // Detect outliers for each metric
        statsMap.forEach((metric, stats) -> {
            double mean = stats.getMean();
            double stdDev = stats.getStandardDeviation();
            double lowerBound = mean - OUTLIER_THRESHOLD * stdDev;
            double upperBound = mean + OUTLIER_THRESHOLD * stdDev;
            
            int outlierCount = 0;
            for (double value : stats.getValues()) {
                if (value < lowerBound || value > upperBound) {
                    outlierCount++;
                }
            }
            
            if (outlierCount > 0) {
                check.addIssue(String.format("Found %d outliers in %s metric", 
                                           outlierCount, metric));
            }
        });
        
        check.setPassed(check.getIssues().isEmpty());
        return check;
    }
    
    private static ValidationCheck validateConsistency(List<ExperimentalResult> results) {
        ValidationCheck check = new ValidationCheck();
        
        // Check if all results have the same metrics
        Set<String> referenceMetrics = null;
        for (ExperimentalResult result : results) {
            if (result.getMetrics() == null) continue;
            
            Set<String> currentMetrics = result.getMetrics().keySet();
            if (referenceMetrics == null) {
                referenceMetrics = new HashSet<>(currentMetrics);
            } else if (!referenceMetrics.equals(currentMetrics)) {
                check.addIssue("Inconsistent metrics across results");
                break;
            }
        }
        
        check.setPassed(check.getIssues().isEmpty());
        return check;
    }
    
    private static ValidationCheck validateStatisticalProperties(List<ExperimentalResult> results) {
        ValidationCheck check = new ValidationCheck();
        
        if (results.size() < MIN_SAMPLE_SIZE) {
            check.addIssue("Sample size too small for statistical analysis: " + results.size());
        }
        
        // Check for sufficient variability
        Map<String, DescriptiveStatistics> statsMap = new HashMap<>();
        
        for (ExperimentalResult result : results) {
            if (result.getMetrics() == null) continue;
            
            result.getMetrics().forEach((metric, value) -> {
                statsMap.computeIfAbsent(metric, k -> new DescriptiveStatistics())
                       .addValue(value);
            });
        }
        
        statsMap.forEach((metric, stats) -> {
            double cv = stats.getStandardDeviation() / stats.getMean();
            if (cv < EPSILON) {
                check.addIssue("No variability in " + metric + " metric");
            }
        });
        
        check.setPassed(check.getIssues().isEmpty());
        return check;
    }
    
    private static boolean testNormality(double[] data) {
        // Simplified normality test using Jarque-Bera test approximation
        DescriptiveStatistics stats = new DescriptiveStatistics(data);
        double skewness = stats.getSkewness();
        double kurtosis = stats.getKurtosis();
        int n = data.length;
        
        double jb = (n / 6.0) * (skewness * skewness + 0.25 * kurtosis * kurtosis);
        double criticalValue = 5.99; // Chi-square critical value at 0.05 significance
        
        return jb < criticalValue;
    }
    
    private static boolean testVarianceHomogeneity(double[] data1, double[] data2) {
        try {
            // F-test for variance equality
            double p = TestUtils.homoscedasticTTest(data1, data2);
            return p > 0.05; // Accept null hypothesis of equal variances
        } catch (Exception e) {
            logger.warn("Failed to test variance homogeneity: {}", e.getMessage());
            return false;
        }
    }
    
    // Inner classes for validation structures
    
    public static class ValidationReport {
        private final Map<String, ValidationCheck> checks;
        private final List<String> warnings;
        private final List<String> errors;
        private final Date timestamp;
        private boolean valid;
        
        public ValidationReport() {
            this.checks = new LinkedHashMap<>();
            this.warnings = new ArrayList<>();
            this.errors = new ArrayList<>();
            this.timestamp = new Date();
            this.valid = true;
        }
        
        public void addCheck(String name, ValidationCheck check) {
            checks.put(name, check);
            if (!check.isPassed()) {
                valid = false;
            }
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public void addError(String error) {
            errors.add(error);
            valid = false;
        }
        
        // Getters
        public Map<String, ValidationCheck> getChecks() { return checks; }
        public List<String> getWarnings() { return warnings; }
        public List<String> getErrors() { return errors; }
        public Date getTimestamp() { return timestamp; }
        public boolean isValid() { return valid; }
    }
    
    public static class ValidationCheck {
        private boolean passed;
        private final List<String> issues;
        
        public ValidationCheck() {
            this.passed = true;
            this.issues = new ArrayList<>();
        }
        
        public void addIssue(String issue) {
            issues.add(issue);
        }
        
        // Getters and setters
        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        public List<String> getIssues() { return issues; }
    }
    
    public static class DataIntegrityReport {
        private final List<String> errors;
        private final List<String> warnings;
        private boolean valid;
        
        public DataIntegrityReport() {
            this.errors = new ArrayList<>();
            this.warnings = new ArrayList<>();
            this.valid = true;
        }
        
        public void addError(String error) {
            errors.add(error);
            valid = false;
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        // Getters and setters
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
    }
    
    public static class StatisticalValidation {
        private boolean normalityAssumptionMet;
        private boolean homogeneityAssumptionMet;
        private boolean independenceAssumptionMet;
        private boolean sufficientSampleSize;
        private final List<String> issues;
        private final List<String> recommendations;
        
        public StatisticalValidation() {
            this.normalityAssumptionMet = true;
            this.homogeneityAssumptionMet = true;
            this.independenceAssumptionMet = true;
            this.sufficientSampleSize = true;
            this.issues = new ArrayList<>();
            this.recommendations = new ArrayList<>();
        }
        
        public void addIssue(String issue) {
            issues.add(issue);
        }
        
        public void addRecommendation(String recommendation) {
            recommendations.add(recommendation);
        }
        
        public boolean allAssumptionsMet() {
            return normalityAssumptionMet && homogeneityAssumptionMet && 
                   independenceAssumptionMet && sufficientSampleSize;
        }
        
        // Getters and setters
        public boolean isNormalityAssumptionMet() { return normalityAssumptionMet; }
        public void setNormalityAssumptionMet(boolean met) { this.normalityAssumptionMet = met; }
        public boolean isHomogeneityAssumptionMet() { return homogeneityAssumptionMet; }
        public void setHomogeneityAssumptionMet(boolean met) { this.homogeneityAssumptionMet = met; }
        public boolean isIndependenceAssumptionMet() { return independenceAssumptionMet; }
        public void setIndependenceAssumptionMet(boolean met) { this.independenceAssumptionMet = met; }
        public boolean isSufficientSampleSize() { return sufficientSampleSize; }
        public void setSufficientSampleSize(boolean sufficient) { this.sufficientSampleSize = sufficient; }
        public List<String> getIssues() { return issues; }
        public List<String> getRecommendations() { return recommendations; }
    }
}