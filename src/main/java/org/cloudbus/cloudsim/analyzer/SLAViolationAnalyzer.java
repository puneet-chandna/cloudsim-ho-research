package org.cloudbus.cloudsim.analyzer;

import org.cloudbus.cloudsim.experiment.ExperimentalResult;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.MetricsCalculator;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Comprehensive SLA violation analysis for VM placement research.
 * Analyzes various types of SLA violations including availability,
 * performance, and resource-related violations.
 * 
 * Research focus: Detailed SLA violation patterns and their impact
 * on cloud service quality for publication analysis.
 */
public class SLAViolationAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(SLAViolationAnalyzer.class);
    
    // SLA violation thresholds
    private static final double CPU_OVERUTILIZATION_THRESHOLD = 0.9;
    private static final double MEMORY_OVERUTILIZATION_THRESHOLD = 0.9;
    private static final double AVAILABILITY_THRESHOLD = 0.999; // 99.9% availability
    private static final double RESPONSE_TIME_THRESHOLD = 1000; // ms
    private static final double MIGRATION_TIME_THRESHOLD = 30000; // ms
    
    private final MetricsCalculator metricsCalculator;
    private final Map<String, List<SLAViolation>> violationsByType;
    private final Map<String, DescriptiveStatistics> violationStatistics;
    
    public SLAViolationAnalyzer() {
        this.metricsCalculator = new MetricsCalculator();
        this.violationsByType = new HashMap<>();
        this.violationStatistics = new HashMap<>();
    }
    
    /**
     * Detect all types of SLA violations in experimental results
     */
    public Map<String, List<SLAViolation>> detectSLAViolations(
            List<ExperimentalResult> results, 
            Map<String, Object> simulationData) {
        
        logger.info("Starting comprehensive SLA violation detection");
        violationsByType.clear();
        
        // Initialize violation categories
        initializeViolationCategories();
        
        // Detect different types of violations
        detectPerformanceViolations(results, simulationData);
        detectAvailabilityViolations(results, simulationData);
        detectResourceViolations(results, simulationData);
        detectMigrationViolations(results, simulationData);
        detectQoSViolations(results, simulationData);
        
        // Log summary
        logViolationSummary();
        
        return new HashMap<>(violationsByType);
    }
    
    /**
     * Categorize violations by type and severity
     */
    public Map<String, Map<String, List<SLAViolation>>> categorizeViolations(
            Map<String, List<SLAViolation>> violations) {
        
        logger.info("Categorizing SLA violations by type and severity");
        
        Map<String, Map<String, List<SLAViolation>>> categorized = new HashMap<>();
        
        for (Map.Entry<String, List<SLAViolation>> entry : violations.entrySet()) {
            String violationType = entry.getKey();
            List<SLAViolation> violationList = entry.getValue();
            
            Map<String, List<SLAViolation>> severityMap = violationList.stream()
                .collect(Collectors.groupingBy(v -> v.severity.toString()));
            
            categorized.put(violationType, severityMap);
        }
        
        return categorized;
    }
    
    /**
     * Calculate comprehensive violation metrics
     */
    public SLAViolationMetrics calculateViolationMetrics(
            Map<String, List<SLAViolation>> violations,
            List<ExperimentalResult> results) {
        
        logger.info("Calculating comprehensive SLA violation metrics");
        
        SLAViolationMetrics metrics = new SLAViolationMetrics();
        
        // Overall violation rate
        int totalViolations = violations.values().stream()
            .mapToInt(List::size)
            .sum();
        int totalOperations = calculateTotalOperations(results);
        metrics.overallViolationRate = (double) totalViolations / totalOperations;
        
        // Per-type violation metrics
        for (Map.Entry<String, List<SLAViolation>> entry : violations.entrySet()) {
            String type = entry.getKey();
            List<SLAViolation> typeViolations = entry.getValue();
            
            ViolationTypeMetrics typeMetrics = new ViolationTypeMetrics();
            typeMetrics.count = typeViolations.size();
            typeMetrics.rate = (double) typeViolations.size() / totalOperations;
            
            // Calculate severity distribution
            Map<Severity, Long> severityCount = typeViolations.stream()
                .collect(Collectors.groupingBy(
                    v -> v.severity,
                    Collectors.counting()
                ));
            typeMetrics.severityDistribution = severityCount;
            
            // Calculate temporal patterns
            typeMetrics.temporalPattern = analyzeTemporalPattern(typeViolations);
            
            // Calculate impact metrics
            typeMetrics.averageImpact = calculateAverageImpact(typeViolations);
            typeMetrics.maxImpact = calculateMaxImpact(typeViolations);
            
            metrics.perTypeMetrics.put(type, typeMetrics);
        }
        
        // Calculate cost impact
        metrics.totalCostImpact = calculateCostImpact(violations);
        
        // Calculate availability impact
        metrics.availabilityImpact = calculateAvailabilityImpact(violations);
        
        return metrics;
    }
    
    /**
     * Analyze violation patterns over time
     */
    public ViolationPatternAnalysis analyzeViolationPatterns(
            Map<String, List<SLAViolation>> violations,
            double simulationTime) {
        
        logger.info("Analyzing SLA violation patterns");
        
        ViolationPatternAnalysis analysis = new ViolationPatternAnalysis();
        
        // Temporal clustering
        analysis.temporalClusters = identifyTemporalClusters(violations, simulationTime);
        
        // Spatial patterns (host/datacenter correlation)
        analysis.spatialPatterns = identifySpatialPatterns(violations);
        
        // Workload correlation
        analysis.workloadCorrelation = analyzeWorkloadCorrelation(violations);
        
        // Cascading violations
        analysis.cascadingPatterns = identifyCascadingViolations(violations);
        
        // Periodic patterns
        analysis.periodicPatterns = identifyPeriodicPatterns(violations, simulationTime);
        
        return analysis;
    }
    
    // Private helper methods
    
    private void initializeViolationCategories() {
        violationsByType.put("PERFORMANCE", new ArrayList<>());
        violationsByType.put("AVAILABILITY", new ArrayList<>());
        violationsByType.put("RESOURCE", new ArrayList<>());
        violationsByType.put("MIGRATION", new ArrayList<>());
        violationsByType.put("QOS", new ArrayList<>());
    }
    
    private void detectPerformanceViolations(
            List<ExperimentalResult> results,
            Map<String, Object> simulationData) {
        
        List<SLAViolation> violations = new ArrayList<>();
        
        for (ExperimentalResult result : results) {
            // Response time violations
            if (result.getAverageResponseTime() > RESPONSE_TIME_THRESHOLD) {
                SLAViolation violation = new SLAViolation();
                violation.type = "PERFORMANCE";
                violation.subType = "RESPONSE_TIME";
                violation.timestamp = result.getTimestamp();
                violation.actualValue = result.getAverageResponseTime();
                violation.thresholdValue = RESPONSE_TIME_THRESHOLD;
                violation.severity = calculateSeverity(
                    result.getAverageResponseTime(), 
                    RESPONSE_TIME_THRESHOLD
                );
                violation.affectedEntities = extractAffectedVMs(result);
                violations.add(violation);
            }
            
            // Throughput violations
            double expectedThroughput = (double) simulationData.getOrDefault("expectedThroughput", 1000.0);
            if (result.getThroughput() < expectedThroughput * 0.95) {
                SLAViolation violation = new SLAViolation();
                violation.type = "PERFORMANCE";
                violation.subType = "THROUGHPUT";
                violation.timestamp = result.getTimestamp();
                violation.actualValue = result.getThroughput();
                violation.thresholdValue = expectedThroughput;
                violation.severity = calculateSeverity(
                    expectedThroughput - result.getThroughput(),
                    expectedThroughput * 0.05
                );
                violations.add(violation);
            }
        }
        
        violationsByType.get("PERFORMANCE").addAll(violations);
    }
    
    private void detectAvailabilityViolations(
            List<ExperimentalResult> results,
            Map<String, Object> simulationData) {
        
        List<SLAViolation> violations = new ArrayList<>();
        
        // Calculate availability from uptime/downtime data
        double totalTime = (double) simulationData.getOrDefault("totalSimulationTime", 86400.0);
        
        for (ExperimentalResult result : results) {
            double availability = calculateAvailability(result, totalTime);
            
            if (availability < AVAILABILITY_THRESHOLD) {
                SLAViolation violation = new SLAViolation();
                violation.type = "AVAILABILITY";
                violation.subType = "UPTIME";
                violation.timestamp = result.getTimestamp();
                violation.actualValue = availability;
                violation.thresholdValue = AVAILABILITY_THRESHOLD;
                violation.severity = Severity.HIGH; // Availability violations are always high severity
                violation.duration = calculateDowntime(result, totalTime);
                violations.add(violation);
            }
        }
        
        violationsByType.get("AVAILABILITY").addAll(violations);
    }
    
    private void detectResourceViolations(
            List<ExperimentalResult> results,
            Map<String, Object> simulationData) {
        
        List<SLAViolation> violations = new ArrayList<>();
        
        for (ExperimentalResult result : results) {
            // CPU overutilization
            if (result.getAverageCpuUtilization() > CPU_OVERUTILIZATION_THRESHOLD) {
                SLAViolation violation = new SLAViolation();
                violation.type = "RESOURCE";
                violation.subType = "CPU_OVERUTILIZATION";
                violation.timestamp = result.getTimestamp();
                violation.actualValue = result.getAverageCpuUtilization();
                violation.thresholdValue = CPU_OVERUTILIZATION_THRESHOLD;
                violation.severity = calculateResourceSeverity(
                    result.getAverageCpuUtilization(),
                    CPU_OVERUTILIZATION_THRESHOLD
                );
                violations.add(violation);
            }
            
            // Memory overutilization
            if (result.getAverageMemoryUtilization() > MEMORY_OVERUTILIZATION_THRESHOLD) {
                SLAViolation violation = new SLAViolation();
                violation.type = "RESOURCE";
                violation.subType = "MEMORY_OVERUTILIZATION";
                violation.timestamp = result.getTimestamp();
                violation.actualValue = result.getAverageMemoryUtilization();
                violation.thresholdValue = MEMORY_OVERUTILIZATION_THRESHOLD;
                violation.severity = calculateResourceSeverity(
                    result.getAverageMemoryUtilization(),
                    MEMORY_OVERUTILIZATION_THRESHOLD
                );
                violations.add(violation);
            }
        }
        
        violationsByType.get("RESOURCE").addAll(violations);
    }
    
    private void detectMigrationViolations(
            List<ExperimentalResult> results,
            Map<String, Object> simulationData) {
        
        List<SLAViolation> violations = new ArrayList<>();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> migrations = (List<Map<String, Object>>) 
            simulationData.getOrDefault("migrations", new ArrayList<>());
        
        for (Map<String, Object> migration : migrations) {
            double migrationTime = (double) migration.getOrDefault("duration", 0.0);
            
            if (migrationTime > MIGRATION_TIME_THRESHOLD) {
                SLAViolation violation = new SLAViolation();
                violation.type = "MIGRATION";
                violation.subType = "EXCESSIVE_MIGRATION_TIME";
                violation.timestamp = (double) migration.getOrDefault("startTime", 0.0);
                violation.actualValue = migrationTime;
                violation.thresholdValue = MIGRATION_TIME_THRESHOLD;
                violation.severity = Severity.MEDIUM;
                violation.affectedEntities = Arrays.asList(
                    (String) migration.getOrDefault("vmId", "unknown")
                );
                violations.add(violation);
            }
        }
        
        violationsByType.get("MIGRATION").addAll(violations);
    }
    
    private void detectQoSViolations(
            List<ExperimentalResult> results,
            Map<String, Object> simulationData) {
        
        List<SLAViolation> violations = new ArrayList<>();
        
        // Detect composite QoS violations
        for (ExperimentalResult result : results) {
            double qosScore = calculateQoSScore(result);
            double qosThreshold = 0.95; // 95% QoS requirement
            
            if (qosScore < qosThreshold) {
                SLAViolation violation = new SLAViolation();
                violation.type = "QOS";
                violation.subType = "COMPOSITE_QOS";
                violation.timestamp = result.getTimestamp();
                violation.actualValue = qosScore;
                violation.thresholdValue = qosThreshold;
                violation.severity = calculateQoSSeverity(qosScore, qosThreshold);
                violations.add(violation);
            }
        }
        
        violationsByType.get("QOS").addAll(violations);
    }
    
    private Severity calculateSeverity(double actual, double threshold) {
        double deviation = Math.abs(actual - threshold) / threshold;
        if (deviation > 0.5) return Severity.HIGH;
        if (deviation > 0.2) return Severity.MEDIUM;
        return Severity.LOW;
    }
    
    private Severity calculateResourceSeverity(double utilization, double threshold) {
        if (utilization > 0.95) return Severity.HIGH;
        if (utilization > threshold) return Severity.MEDIUM;
        return Severity.LOW;
    }
    
    private Severity calculateQoSSeverity(double score, double threshold) {
        double gap = threshold - score;
        if (gap > 0.1) return Severity.HIGH;
        if (gap > 0.05) return Severity.MEDIUM;
        return Severity.LOW;
    }
    
    private List<String> extractAffectedVMs(ExperimentalResult result) {
        // Extract affected VM IDs from result metadata
        return new ArrayList<>(); // Placeholder
    }
    
    private double calculateAvailability(ExperimentalResult result, double totalTime) {
        double downtime = result.getTotalDowntime();
        return (totalTime - downtime) / totalTime;
    }
    
    private double calculateDowntime(ExperimentalResult result, double totalTime) {
        return result.getTotalDowntime();
    }
    
    private double calculateQoSScore(ExperimentalResult result) {
        // Composite QoS score based on multiple metrics
        double performanceScore = 1.0 - (result.getAverageResponseTime() / RESPONSE_TIME_THRESHOLD);
        double resourceScore = 1.0 - Math.max(
            result.getAverageCpuUtilization() - 0.8,
            result.getAverageMemoryUtilization() - 0.8
        ) / 0.2;
        
        return (performanceScore + resourceScore) / 2.0;
    }
    
    private int calculateTotalOperations(List<ExperimentalResult> results) {
        return results.stream()
            .mapToInt(r -> r.getTotalVmRequests())
            .sum();
    }
    
    private String analyzeTemporalPattern(List<SLAViolation> violations) {
        // Analyze temporal clustering of violations
        if (violations.isEmpty()) return "NONE";
        
        // Sort by timestamp
        violations.sort(Comparator.comparing(v -> v.timestamp));
        
        // Calculate inter-arrival times
        List<Double> interArrivalTimes = new ArrayList<>();
        for (int i = 1; i < violations.size(); i++) {
            interArrivalTimes.add(
                violations.get(i).timestamp - violations.get(i-1).timestamp
            );
        }
        
        if (interArrivalTimes.isEmpty()) return "ISOLATED";
        
        // Calculate coefficient of variation
        DescriptiveStatistics stats = new DescriptiveStatistics();
        interArrivalTimes.forEach(stats::addValue);
        
        double cv = stats.getStandardDeviation() / stats.getMean();
        
        if (cv < 0.5) return "REGULAR";
        if (cv < 1.0) return "CLUSTERED";
        return "RANDOM";
    }
    
    private double calculateAverageImpact(List<SLAViolation> violations) {
        return violations.stream()
            .mapToDouble(v -> v.impactScore)
            .average()
            .orElse(0.0);
    }
    
    private double calculateMaxImpact(List<SLAViolation> violations) {
        return violations.stream()
            .mapToDouble(v -> v.impactScore)
            .max()
            .orElse(0.0);
    }
    
    private double calculateCostImpact(Map<String, List<SLAViolation>> violations) {
        double totalCost = 0.0;
        
        // Cost per violation type (example values)
        Map<String, Double> costPerViolation = Map.of(
            "PERFORMANCE", 10.0,
            "AVAILABILITY", 100.0,
            "RESOURCE", 5.0,
            "MIGRATION", 2.0,
            "QOS", 20.0
        );
        
        for (Map.Entry<String, List<SLAViolation>> entry : violations.entrySet()) {
            String type = entry.getKey();
            int count = entry.getValue().size();
            totalCost += count * costPerViolation.getOrDefault(type, 1.0);
        }
        
        return totalCost;
    }
    
    private double calculateAvailabilityImpact(Map<String, List<SLAViolation>> violations) {
        List<SLAViolation> availabilityViolations = violations.get("AVAILABILITY");
        if (availabilityViolations == null || availabilityViolations.isEmpty()) {
            return 1.0; // No impact
        }
        
        double totalDowntime = availabilityViolations.stream()
            .mapToDouble(v -> v.duration)
            .sum();
        
        // Assuming 24-hour period
        return 1.0 - (totalDowntime / 86400.0);
    }
    
    private List<TemporalCluster> identifyTemporalClusters(
            Map<String, List<SLAViolation>> violations,
            double simulationTime) {
        
        List<TemporalCluster> clusters = new ArrayList<>();
        
        // Merge all violations and sort by timestamp
        List<SLAViolation> allViolations = violations.values().stream()
            .flatMap(List::stream)
            .sorted(Comparator.comparing(v -> v.timestamp))
            .collect(Collectors.toList());
        
        if (allViolations.isEmpty()) return clusters;
        
        // Simple clustering based on time proximity
        double clusterThreshold = simulationTime * 0.01; // 1% of simulation time
        
        TemporalCluster currentCluster = new TemporalCluster();
        currentCluster.startTime = allViolations.get(0).timestamp;
        currentCluster.violations.add(allViolations.get(0));
        
        for (int i = 1; i < allViolations.size(); i++) {
            SLAViolation violation = allViolations.get(i);
            
            if (violation.timestamp - currentCluster.endTime > clusterThreshold) {
                // Start new cluster
                clusters.add(currentCluster);
                currentCluster = new TemporalCluster();
                currentCluster.startTime = violation.timestamp;
            }
            
            currentCluster.violations.add(violation);
            currentCluster.endTime = violation.timestamp;
        }
        
        clusters.add(currentCluster);
        
        return clusters;
    }
    
    private Map<String, SpatialPattern> identifySpatialPatterns(
            Map<String, List<SLAViolation>> violations) {
        
        Map<String, SpatialPattern> patterns = new HashMap<>();
        
        for (Map.Entry<String, List<SLAViolation>> entry : violations.entrySet()) {
            String type = entry.getKey();
            List<SLAViolation> typeViolations = entry.getValue();
            
            SpatialPattern pattern = new SpatialPattern();
            
            // Group by host/datacenter
            Map<String, Long> hostDistribution = typeViolations.stream()
                .filter(v -> v.hostId != null)
                .collect(Collectors.groupingBy(
                    v -> v.hostId,
                    Collectors.counting()
                ));
            
            pattern.hostConcentration = calculateConcentration(hostDistribution);
            pattern.affectedHosts = hostDistribution.keySet();
            
            patterns.put(type, pattern);
        }
        
        return patterns;
    }
    
    private double calculateConcentration(Map<String, Long> distribution) {
        if (distribution.isEmpty()) return 0.0;
        
        long total = distribution.values().stream().mapToLong(Long::longValue).sum();
        
        // Calculate Gini coefficient for concentration
        List<Long> values = new ArrayList<>(distribution.values());
        values.sort(Long::compare);
        
        double sum = 0.0;
        for (int i = 0; i < values.size(); i++) {
            sum += (2 * (i + 1) - values.size() - 1) * values.get(i);
        }
        
        return sum / (values.size() * total);
    }
    
    private Map<String, Double> analyzeWorkloadCorrelation(
            Map<String, List<SLAViolation>> violations) {
        
        Map<String, Double> correlations = new HashMap<>();
        
        // Placeholder for workload correlation analysis
        // In practice, this would correlate violations with workload intensity
        
        return correlations;
    }
    
    private List<CascadingPattern> identifyCascadingViolations(
            Map<String, List<SLAViolation>> violations) {
        
        List<CascadingPattern> patterns = new ArrayList<>();
        
        // Identify violations that occur in sequence
        // Placeholder implementation
        
        return patterns;
    }
    
    private Map<String, PeriodicPattern> identifyPeriodicPatterns(
            Map<String, List<SLAViolation>> violations,
            double simulationTime) {
        
        Map<String, PeriodicPattern> patterns = new HashMap<>();
        
        // Placeholder for periodic pattern detection
        // Would use FFT or autocorrelation in practice
        
        return patterns;
    }
    
    private void logViolationSummary() {
        logger.info("SLA Violation Detection Summary:");
        for (Map.Entry<String, List<SLAViolation>> entry : violationsByType.entrySet()) {
            logger.info("  {} violations: {}", entry.getKey(), entry.getValue().size());
        }
    }
    
    // Inner classes for structured results
    
    public static class SLAViolation {
        public String type;
        public String subType;
        public double timestamp;
        public double actualValue;
        public double thresholdValue;
        public Severity severity;
        public List<String> affectedEntities;
        public String hostId;
        public double duration;
        public double impactScore;
        
        public SLAViolation() {
            this.affectedEntities = new ArrayList<>();
            this.impactScore = 1.0;
        }
    }
    
    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public static class SLAViolationMetrics {
        public double overallViolationRate;
        public Map<String, ViolationTypeMetrics> perTypeMetrics = new HashMap<>();
        public double totalCostImpact;
        public double availabilityImpact;
    }
    
    public static class ViolationTypeMetrics {
        public int count;
        public double rate;
        public Map<Severity, Long> severityDistribution;
        public String temporalPattern;
        public double averageImpact;
        public double maxImpact;
    }
    
    public static class ViolationPatternAnalysis {
        public List<TemporalCluster> temporalClusters;
        public Map<String, SpatialPattern> spatialPatterns;
        public Map<String, Double> workloadCorrelation;
        public List<CascadingPattern> cascadingPatterns;
        public Map<String, PeriodicPattern> periodicPatterns;
    }
    
    public static class TemporalCluster {
        public double startTime;
        public double endTime;
        public List<SLAViolation> violations = new ArrayList<>();
    }
    
    public static class SpatialPattern {
        public double hostConcentration;
        public Set<String> affectedHosts;
    }
    
    public static class CascadingPattern {
        public List<SLAViolation> sequence;
        public double probability;
        public double averageDelay;
    }
    
    public static class PeriodicPattern {
        public double period;
        public double amplitude;
        public double confidence;
    }
}