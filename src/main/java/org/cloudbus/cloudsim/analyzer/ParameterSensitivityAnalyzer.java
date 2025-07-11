package org.cloudbus.cloudsim.analyzer;

import org.cloudbus.cloudsim.experiment.ExperimentalResult;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.ExperimentException;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Advanced parameter sensitivity analyzer for Hippopotamus Optimization algorithm research.
 * Implements multiple sensitivity analysis methods including Sobol indices, Morris method,
 * bootstrap confidence intervals, and correlation-based analyses for comprehensive
 * parameter impact assessment.
 * @author Puneet Chandna
 */
public class ParameterSensitivityAnalyzer {

    private final double significanceThreshold;
    private static final int MIN_SAMPLES_FOR_ANALYSIS = 30;
    private static final int BOOTSTRAP_ITERATIONS = 1000;

    private final Map<String, List<Double>> parameterValues;
    private final Map<String, List<Double>> objectiveValues;
    private final List<ExperimentalResult> experimentResults;
    private final Set<String> significantParameters;
    private final Map<String, SensitivityMetrics> sensitivityResults;
    private final LoggingManager loggingManager;

    /**
     * Sensitivity analysis metrics for a single parameter.
     */
    public static class SensitivityMetrics {
        private final double firstOrderIndex;
        private final double totalOrderIndex;
        private final double correlationCoefficient;
        private final double morrisElementaryEffect;
        private final double standardError;
        private final double confidenceInterval;
        private final boolean isSignificant;

        public SensitivityMetrics(double firstOrderIndex, double totalOrderIndex,
                                  double correlationCoefficient, double morrisElementaryEffect,
                                  double standardError, double confidenceInterval,
                                  boolean isSignificant) {
            this.firstOrderIndex      = firstOrderIndex;
            this.totalOrderIndex      = totalOrderIndex;
            this.correlationCoefficient = correlationCoefficient;
            this.morrisElementaryEffect = morrisElementaryEffect;
            this.standardError        = standardError;
            this.confidenceInterval   = confidenceInterval;
            this.isSignificant        = isSignificant;
        }

        // Getters
        public double getFirstOrderIndex()      { return firstOrderIndex; }
        public double getTotalOrderIndex()      { return totalOrderIndex; }
        public double getCorrelationCoefficient() { return correlationCoefficient; }
        public double getMorrisElementaryEffect() { return morrisElementaryEffect; }
        public double getStandardError()        { return standardError; }
        public double getConfidenceInterval()   { return confidenceInterval; }
        public boolean isSignificant()          { return isSignificant; }
    }

    /**
     * Comprehensive sensitivity analysis report.
     */
    public static class SensitivityAnalysisReport {
        private final Map<String, SensitivityMetrics> parameterMetrics;
        private final List<String> rankedParameters;
        private final Map<String, Map<String, Double>> interactionEffects;
        private final double totalVarianceExplained;
        private final Map<String, Double> partialCorrelations;
        private final String analysisTimestamp;
        private final int sampleSize;

        public SensitivityAnalysisReport(Map<String, SensitivityMetrics> parameterMetrics,
                                         List<String> rankedParameters,
                                         Map<String, Map<String, Double>> interactionEffects,
                                         double totalVarianceExplained,
                                         Map<String, Double> partialCorrelations,
                                         int sampleSize) {
            this.parameterMetrics       = new LinkedHashMap<>(parameterMetrics);
            this.rankedParameters       = new ArrayList<>(rankedParameters);
            this.interactionEffects     = new LinkedHashMap<>(interactionEffects);
            this.totalVarianceExplained = totalVarianceExplained;
            this.partialCorrelations    = new LinkedHashMap<>(partialCorrelations);
            this.analysisTimestamp      = LocalDateTime.now().toString();
            this.sampleSize             = sampleSize;
        }

        // Getters
        public Map<String, SensitivityMetrics> getParameterMetrics() { return new LinkedHashMap<>(parameterMetrics); }
        public List<String> getRankedParameters()                   { return new ArrayList<>(rankedParameters); }
        public Map<String, Map<String, Double>> getInteractionEffects() { return new LinkedHashMap<>(interactionEffects); }
        public double getTotalVarianceExplained()                   { return totalVarianceExplained; }
        public Map<String, Double> getPartialCorrelations()         { return new LinkedHashMap<>(partialCorrelations); }
        public String getAnalysisTimestamp()                        { return analysisTimestamp; }
        public int getSampleSize()                                  { return sampleSize; }
    }

    /**
     * Default constructor with 5% significance threshold.
     */
    public ParameterSensitivityAnalyzer() {
        this(0.05);
    }

    /**
     * Constructor allowing custom significance threshold.
     *
     * @param significanceThreshold p-value threshold for significance tests.
     */
    public ParameterSensitivityAnalyzer(double significanceThreshold) {
        this.significanceThreshold = significanceThreshold;
        this.parameterValues       = new ConcurrentHashMap<>();
        this.objectiveValues       = new ConcurrentHashMap<>();
        this.experimentResults     = Collections.synchronizedList(new ArrayList<>());
        this.significantParameters = Collections.synchronizedSet(new HashSet<>());
        this.sensitivityResults    = new ConcurrentHashMap<>();
        this.loggingManager        = new LoggingManager();
    }

    /**
     * Performs comprehensive parameter sensitivity analysis.
     *
     * @param results       List of experimental results from parameter sweep.
     * @param objectiveName Name of the objective function to analyze.
     * @return SensitivityAnalysisReport containing all analysis outputs.
     * @throws ExperimentException if input validation fails or analysis errors occur.
     */
    public SensitivityAnalysisReport performSensitivityAnalysis(List<ExperimentalResult> results,
                                                                String objectiveName)
            throws ExperimentException {
        loggingManager.logInfo("Starting parameter sensitivity analysis for objective: " + objectiveName);
        validateResults(results, "Experiment results");
        if (results.size() < MIN_SAMPLES_FOR_ANALYSIS) {
            throw new ExperimentException(
                    "Insufficient samples for sensitivity analysis. Required: "
                            + MIN_SAMPLES_FOR_ANALYSIS + ", provided: " + results.size());
        }

        experimentResults.clear();
        experimentResults.addAll(results);

        extractParameterData(results, objectiveName);

        Map<String, SensitivityMetrics> metrics = new LinkedHashMap<>();
        for (String parameter : parameterValues.keySet()) {
            SensitivityMetrics m = calculateSensitivityMetrics(parameter, objectiveName);
            metrics.put(parameter, m);
            sensitivityResults.put(parameter, m);
        }

        Map<String, Map<String, Double>> interactions   = calculateInteractionEffects();
        List<String>                 rankedParams       = rankParametersByImportance(metrics);
        Map<String, Double>          partialCorrelations = calculatePartialCorrelations();
        double                       totalVariance      = calculateTotalVarianceExplained(metrics);

        loggingManager.logInfo("Parameter sensitivity analysis completed successfully");
        return new SensitivityAnalysisReport(metrics, rankedParams, interactions,
                                             totalVariance, partialCorrelations,
                                             results.size());
    }

    private void extractParameterData(List<ExperimentalResult> results, String objectiveName) {
        parameterValues.clear();
        objectiveValues.clear();
        List<Double> objList = new ArrayList<>();
        for (ExperimentalResult result : results) {
            double val = getObjectiveValue(result, objectiveName);
            objList.add(val);
            Map<String, Object> params = result.getExperimentConfiguration();
            for (Map.Entry<String, Object> e : params.entrySet()) {
                if (e.getValue() instanceof Number) {
                    double p = ((Number) e.getValue()).doubleValue();
                    parameterValues.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(p);
                }
            }
        }
        objectiveValues.put(objectiveName, objList);
    }

    private SensitivityMetrics calculateSensitivityMetrics(String parameterName, String objectiveName)
            throws ExperimentException {
        try {
            List<Double> pVals = parameterValues.get(parameterName);
            List<Double> oVals = objectiveValues.get(objectiveName);
            if (pVals == null || oVals == null || pVals.size() != oVals.size()) {
                throw new ExperimentException("Mismatch or missing data for parameter: " + parameterName);
            }

            double f1 = calculateSobolFirstOrderIndex(pVals, oVals);
            double t1 = calculateSobolTotalOrderIndex(pVals, oVals);
            double corr = new PearsonsCorrelation()
                    .correlation(
                            pVals.stream().mapToDouble(Double::doubleValue).toArray(),
                            oVals.stream().mapToDouble(Double::doubleValue).toArray());
            double morris = calculateMorrisElementaryEffect(pVals, oVals);
            double stderr = calculateBootstrapStandardError(pVals, oVals);
            double ci = 1.96 * stderr;
            boolean signif = Math.abs(corr) < significanceThreshold && t1 > significanceThreshold;

            return new SensitivityMetrics(f1, t1, corr, morris, stderr, ci, signif);
        } catch (Exception e) {
            loggingManager.logError("Error computing metrics for " + parameterName, e);
            throw new ExperimentException("Failed metrics for " + parameterName + ": " + e.getMessage(), e);
        }
    }

    private double calculateSobolFirstOrderIndex(List<Double> p, List<Double> o) {
        DescriptiveStatistics os = new DescriptiveStatistics();
        o.forEach(os::addValue);
        double totalVar = os.getVariance();
        if (totalVar == 0) return 0.0;
        double condVar = calculateConditionalVariance(p, o);
        return Math.max(0.0, Math.min(1.0, condVar / totalVar));
    }

    private double calculateSobolTotalOrderIndex(List<Double> p, List<Double> o) {
        double first = calculateSobolFirstOrderIndex(p, o);
        double inter = calculateParameterInteractionEffect(p, o);
        return Math.max(0.0, Math.min(1.0, first + inter));
    }

    private double calculateConditionalVariance(List<Double> p, List<Double> o) {
        Map<Double, List<Double>> groups = new HashMap<>();
        for (int i = 0; i < p.size(); i++) {
            groups.computeIfAbsent(p.get(i), k -> new ArrayList<>()).add(o.get(i));
        }
        double sumVar = 0;
        double count = 0;
        for (List<Double> vals : groups.values()) {
            if (vals.size() > 1) {
                DescriptiveStatistics ds = new DescriptiveStatistics();
                vals.forEach(ds::addValue);
                sumVar += ds.getVariance() * vals.size();
                count += vals.size();
            }
        }
        return count > 0 ? sumVar / count : 0.0;
    }

    private double calculateMorrisElementaryEffect(List<Double> p, List<Double> o) {
        if (p.size() < 2) return 0.0;
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (int i = 1; i < p.size(); i++) {
            double dp = p.get(i) - p.get(i - 1);
            if (Math.abs(dp) > 1e-10) {
                stats.addValue((o.get(i) - o.get(i - 1)) / dp);
            }
        }
        return stats.getN() > 0 ? stats.getMean() : 0.0;
    }

    private double calculateBootstrapStandardError(List<Double> p, List<Double> o) {
        DescriptiveStatistics stats = new DescriptiveStatistics();
        Random rnd = new Random(42);
        for (int i = 0; i < BOOTSTRAP_ITERATIONS; i++) {
            List<Double> sampP = new ArrayList<>(p.size());
            List<Double> sampO = new ArrayList<>(o.size());
            for (int j = 0; j < p.size(); j++) {
                int idx = rnd.nextInt(p.size());
                sampP.add(p.get(idx));
                sampO.add(o.get(idx));
            }
            double corr = new PearsonsCorrelation()
                    .correlation(
                            sampP.stream().mapToDouble(Double::doubleValue).toArray(),
                            sampO.stream().mapToDouble(Double::doubleValue).toArray());
            stats.addValue(corr);
        }
        return stats.getStandardDeviation();
    }

    private Map<String, Map<String, Double>> calculateInteractionEffects() {
        Map<String, Map<String, Double>> effects = new LinkedHashMap<>();
        List<String> params = new ArrayList<>(parameterValues.keySet());
        for (int i = 0; i < params.size(); i++) {
            String p1 = params.get(i);
            Map<String, Double> inner = new LinkedHashMap<>();
            for (int j = i + 1; j < params.size(); j++) {
                String p2 = params.get(j);
                inner.put(p2, calculatePairwiseInteraction(p1, p2));
            }
            effects.put(p1, inner);
        }
        return effects;
    }

    private double calculatePairwiseInteraction(String p1, String p2) {
        List<Double> v1 = parameterValues.get(p1);
        List<Double> v2 = parameterValues.get(p2);
        if (v1 == null || v2 == null || v1.size() != v2.size()) return 0.0;
        return new SpearmansCorrelation()
                .correlation(
                        v1.stream().mapToDouble(Double::doubleValue).toArray(),
                        v2.stream().mapToDouble(Double::doubleValue).toArray());
    }

    private List<String> rankParametersByImportance(Map<String, SensitivityMetrics> metrics) {
        return metrics.entrySet().stream()
                .sorted((a, b) -> Double.compare(
                        b.getValue().getTotalOrderIndex(),
                        a.getValue().getTotalOrderIndex()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private Map<String, Double> calculatePartialCorrelations() {
        Map<String, Double> partials = new LinkedHashMap<>();
        List<Double> objVals = objectiveValues.values().iterator().next();
        for (Map.Entry<String, List<Double>> entry : parameterValues.entrySet()) {
            String param = entry.getKey();
            double corr = new SpearmansCorrelation()
                    .correlation(
                            entry.getValue().stream().mapToDouble(Double::doubleValue).toArray(),
                            objVals.stream().mapToDouble(Double::doubleValue).toArray());
            partials.put(param, corr);
        }
        return partials;
    }

    private double calculateTotalVarianceExplained(Map<String, SensitivityMetrics> metrics) {
        return metrics.values().stream()
                .mapToDouble(SensitivityMetrics::getTotalOrderIndex)
                .sum();
    }

    /**
     * Optionally generate a text report to disk.
     */
    public void generateSensitivityReport(SensitivityAnalysisReport report, String outputPath)
            throws ExperimentException {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("=== PARAMETER SENSITIVITY ANALYSIS REPORT ===\n")
              .append("Timestamp: ").append(report.getAnalysisTimestamp()).append("\n")
              .append("Sample Size: ").append(report.getSampleSize()).append("\n")
              .append("Total Variance Explained: ")
              .append(String.format("%.4f", report.getTotalVarianceExplained())).append("\n\n")
              .append("=== PARAMETER RANKING ===\n");
            int idx = 1;
            for (String param : report.getRankedParameters()) {
                SensitivityMetrics m = report.getParameterMetrics().get(param);
                sb.append(String.format("%d. %s (Total=%.4f, Corr=%.4f)%s%n",
                        idx++, param, m.getTotalOrderIndex(), m.getCorrelationCoefficient(),
                        m.isSignificant() ? " [SIGNIFICANT]" : ""));
            }
            sb.append("\n=== INTERACTION EFFECTS ===\n");
            for (Map.Entry<String, Map<String, Double>> e : report.getInteractionEffects().entrySet()) {
                for (Map.Entry<String, Double> ie : e.getValue().entrySet()) {
                    if (Math.abs(ie.getValue()) > significanceThreshold) {
                        sb.append(String.format("%s × %s: %.4f%n",
                                e.getKey(), ie.getKey(), ie.getValue()));
                    }
                }
            }
            Path path = Paths.get(outputPath);
            Files.createDirectories(path.getParent());
            Files.write(path, sb.toString().getBytes());
            loggingManager.logInfo("Sensitivity report saved to " + outputPath);
        } catch (IOException e) {
            throw new ExperimentException("Failed writing report: " + e.getMessage(), e);
        }
    }

    // Expose results for testing or integration
    public Map<String, SensitivityMetrics> getSensitivityResults() {
        return new LinkedHashMap<>(sensitivityResults);
    }

    public Set<String> getSignificantParameters() {
        return new HashSet<>(significantParameters);
    }

    public List<ExperimentalResult> getExperimentResults() {
        return new ArrayList<>(experimentResults);
    }
    
    // Helper methods
    
    /**
     * Validate experimental results
     */
    private void validateResults(List<ExperimentalResult> results, String message) {
        if (results == null || results.isEmpty()) {
            throw new ExperimentException(message + " cannot be null or empty");
        }
        
        // Additional validation
        for (ExperimentalResult result : results) {
            if (result == null) {
                throw new ExperimentException("Individual experimental results cannot be null");
            }
        }
    }
    
    /**
     * Extract objective value from experimental result safely
     */
    private double getObjectiveValue(ExperimentalResult result, String objectiveName) {
        try {
            // Extract common objective values based on the objective name
            switch (objectiveName.toLowerCase()) {
                case "resource_utilization":
                    return result.getResourceUtilization();
                case "power_consumption":
                    return result.getPowerConsumption();
                case "throughput":
                    return result.getThroughput();
                case "response_time":
                    return result.getAverageResponseTime();
                case "sla_violations":
                    return result.getSlaViolations();
                default:
                    // Try to get from detailed metrics
                    Map<String, Object> detailedMetrics = result.getDetailedMetrics();
                    if (detailedMetrics != null && detailedMetrics.containsKey(objectiveName)) {
                        Object value = detailedMetrics.get(objectiveName);
                        if (value instanceof Number) {
                            return ((Number) value).doubleValue();
                        }
                    }
                    // Try to get from experiment configuration
                    Map<String, Object> config = result.getExperimentConfiguration();
                    if (config != null && config.containsKey(objectiveName)) {
                        Object value = config.get(objectiveName);
                        if (value instanceof Number) {
                            return ((Number) value).doubleValue();
                        }
                    }
                    return 0.0;
            }
        } catch (Exception e) {
            loggingManager.logError("Error extracting objective value for " + objectiveName, e);
            return 0.0;
        }
    }
    
    /**
     * Calculate parameter interaction effect safely
     */
    private double calculateParameterInteractionEffect(List<Double> p, List<Double> o) {
        if (p.size() != o.size() || p.size() < 2) {
            return 0.0;
        }
        
        try {
            // Calculate correlation-based interaction effect
            PearsonsCorrelation correlation = new PearsonsCorrelation();
            double corr = correlation.correlation(
                p.stream().mapToDouble(Double::doubleValue).toArray(),
                o.stream().mapToDouble(Double::doubleValue).toArray()
            );
            
            // Interaction effect as squared correlation (coefficient of determination)
            return Math.abs(corr * corr);
        } catch (Exception e) {
            loggingManager.logError("Error calculating parameter interaction effect", e);
            return 0.0;
        }
    }
}
