package org.cloudbus.cloudsim.analyzer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.Serializable;

/**
 * ScalabilityResults - Data class to store comprehensive scalability analysis results
 * Part of the research framework for evaluating VM placement algorithms
 * 
 * This class serves as a structured container for all scalability analysis outputs,
 * making it easy to pass results between components and generate reports.
 */
public class ScalabilityResults implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // Basic information
    private String algorithmName;
    private long analysisTimestamp;
    private int totalExperimentsAnalyzed;
    
    // Scaling analysis results
    private Map<String, Object> vmScalingAnalysis;
    private Map<String, Object> hostScalingAnalysis;
    private Map<String, Object> combinedScalingAnalysis;
    
    // Complexity analysis
    private ScalabilityAnalyzer.ComplexityModel complexityModel;
    private Map<String, String> complexitySummary;
    
    // Performance trends
    private Map<String, List<ScalabilityAnalyzer.PerformanceTrend>> performanceTrends;
    private Map<String, Double> trendSummaryStatistics;
    
    // Scalability limits
    private Map<String, Integer> scalabilityLimits;
    private String limitingFactor;
    private int recommendedMaxScale;
    
    // Efficiency metrics
    private Map<Integer, Double> efficiencyByScale;
    private double efficiencyDegradationRate;
    private Map<String, Double> resourceEfficiency;
    
    // Statistical measures
    private Map<String, Map<String, Double>> scalingStatistics;
    private Map<String, Double> confidenceIntervals;
    
    // Visualization data
    private Map<String, double[][]> plottingData;
    private Map<String, String> chartTitles;
    
    // Detailed report
    private Map<String, Object> detailedReport;
    private List<String> keyFindings;
    private List<String> recommendations;
    
    // Comparison data (for multi-algorithm analysis)
    private Map<String, Double> comparativeMetrics;
    private int scalabilityRank;
    
    public ScalabilityResults() {
        this.analysisTimestamp = System.currentTimeMillis();
        this.vmScalingAnalysis = new HashMap<>();
        this.hostScalingAnalysis = new HashMap<>();
        this.combinedScalingAnalysis = new HashMap<>();
        this.complexitySummary = new HashMap<>();
        this.performanceTrends = new HashMap<>();
        this.trendSummaryStatistics = new HashMap<>();
        this.scalabilityLimits = new HashMap<>();
        this.efficiencyByScale = new HashMap<>();
        this.resourceEfficiency = new HashMap<>();
        this.scalingStatistics = new HashMap<>();
        this.confidenceIntervals = new HashMap<>();
        this.plottingData = new HashMap<>();
        this.chartTitles = new HashMap<>();
        this.detailedReport = new HashMap<>();
        this.comparativeMetrics = new HashMap<>();
    }
    
    // Getters and Setters
    
    public String getAlgorithmName() {
        return algorithmName;
    }
    
    public void setAlgorithmName(String algorithmName) {
        this.algorithmName = algorithmName;
    }
    
    public long getAnalysisTimestamp() {
        return analysisTimestamp;
    }
    
    public void setAnalysisTimestamp(long analysisTimestamp) {
        this.analysisTimestamp = analysisTimestamp;
    }
    
    public int getTotalExperimentsAnalyzed() {
        return totalExperimentsAnalyzed;
    }
    
    public void setTotalExperimentsAnalyzed(int totalExperimentsAnalyzed) {
        this.totalExperimentsAnalyzed = totalExperimentsAnalyzed;
    }
    
    public Map<String, Object> getVmScalingAnalysis() {
        return vmScalingAnalysis;
    }
    
    public void setVmScalingAnalysis(Map<String, Object> vmScalingAnalysis) {
        this.vmScalingAnalysis = vmScalingAnalysis;
    }
    
    public Map<String, Object> getHostScalingAnalysis() {
        return hostScalingAnalysis;
    }
    
    public void setHostScalingAnalysis(Map<String, Object> hostScalingAnalysis) {
        this.hostScalingAnalysis = hostScalingAnalysis;
    }
    
    public Map<String, Object> getCombinedScalingAnalysis() {
        return combinedScalingAnalysis;
    }
    
    public void setCombinedScalingAnalysis(Map<String, Object> combinedScalingAnalysis) {
        this.combinedScalingAnalysis = combinedScalingAnalysis;
    }
    
    public ScalabilityAnalyzer.ComplexityModel getComplexityModel() {
        return complexityModel;
    }
    
    public void setComplexityModel(ScalabilityAnalyzer.ComplexityModel complexityModel) {
        this.complexityModel = complexityModel;
        // Update complexity summary
        if (complexityModel != null) {
            this.complexitySummary.put("time_complexity", 
                complexityModel.timeComplexity.getNotation());
            this.complexitySummary.put("space_complexity", 
                complexityModel.spaceComplexity.getNotation());
            this.complexitySummary.put("time_r_squared", 
                String.format("%.4f", complexityModel.rSquaredTime));
            this.complexitySummary.put("space_r_squared", 
                String.format("%.4f", complexityModel.rSquaredSpace));
        }
    }
    
    public Map<String, String> getComplexitySummary() {
        return complexitySummary;
    }
    
    public Map<String, List<ScalabilityAnalyzer.PerformanceTrend>> getPerformanceTrends() {
        return performanceTrends;
    }
    
    public void setPerformanceTrends(
            Map<String, List<ScalabilityAnalyzer.PerformanceTrend>> performanceTrends) {
        this.performanceTrends = performanceTrends;
        // Calculate summary statistics
        calculateTrendSummaryStatistics();
    }
    
    public Map<String, Double> getTrendSummaryStatistics() {
        return trendSummaryStatistics;
    }
    
    public Map<String, Integer> getScalabilityLimits() {
        return scalabilityLimits;
    }
    
    public void setScalabilityLimits(Map<String, Integer> scalabilityLimits) {
        this.scalabilityLimits = scalabilityLimits;
        // Determine limiting factor and recommended max scale
        determineLimitingFactor();
    }
    
    public String getLimitingFactor() {
        return limitingFactor;
    }
    
    public int getRecommendedMaxScale() {
        return recommendedMaxScale;
    }
    
    public Map<Integer, Double> getEfficiencyByScale() {
        return efficiencyByScale;
    }
    
    public void setEfficiencyByScale(Map<Integer, Double> efficiencyByScale) {
        this.efficiencyByScale = efficiencyByScale;
        calculateEfficiencyDegradationRate();
    }
    
    public double getEfficiencyDegradationRate() {
        return efficiencyDegradationRate;
    }
    
    public Map<String, Double> getResourceEfficiency() {
        return resourceEfficiency;
    }
    
    public void setResourceEfficiency(Map<String, Double> resourceEfficiency) {
        this.resourceEfficiency = resourceEfficiency;
    }
    
    public Map<String, Map<String, Double>> getScalingStatistics() {
        return scalingStatistics;
    }
    
    public void setScalingStatistics(Map<String, Map<String, Double>> scalingStatistics) {
        this.scalingStatistics = scalingStatistics;
    }
    
    public Map<String, Double> getConfidenceIntervals() {
        return confidenceIntervals;
    }
    
    public void setConfidenceIntervals(Map<String, Double> confidenceIntervals) {
        this.confidenceIntervals = confidenceIntervals;
    }
    
    public Map<String, double[][]> getPlottingData() {
        return plottingData;
    }
    
    public void setPlottingData(Map<String, double[][]> plottingData) {
        this.plottingData = plottingData;
    }
    
    public Map<String, String> getChartTitles() {
        return chartTitles;
    }
    
    public void setChartTitles(Map<String, String> chartTitles) {
        this.chartTitles = chartTitles;
    }
    
    public Map<String, Object> getDetailedReport() {
        return detailedReport;
    }
    
    public void setDetailedReport(Map<String, Object> detailedReport) {
        this.detailedReport = detailedReport;
    }
    
    public List<String> getKeyFindings() {
        return keyFindings;
    }
    
    public void setKeyFindings(List<String> keyFindings) {
        this.keyFindings = keyFindings;
    }
    
    public List<String> getRecommendations() {
        return recommendations;
    }
    
    public void setRecommendations(List<String> recommendations) {
        this.recommendations = recommendations;
    }
    
    public Map<String, Double> getComparativeMetrics() {
        return comparativeMetrics;
    }
    
    public void setComparativeMetrics(Map<String, Double> comparativeMetrics) {
        this.comparativeMetrics = comparativeMetrics;
    }
    
    public int getScalabilityRank() {
        return scalabilityRank;
    }
    
    public void setScalabilityRank(int scalabilityRank) {
        this.scalabilityRank = scalabilityRank;
    }
    
    // Helper methods
    
    private void calculateTrendSummaryStatistics() {
        if (performanceTrends == null) return;
        
        // Calculate average trend rates for each metric
        for (Map.Entry<String, List<ScalabilityAnalyzer.PerformanceTrend>> entry : 
                performanceTrends.entrySet()) {
            
            String metric = entry.getKey();
            List<ScalabilityAnalyzer.PerformanceTrend> trends = entry.getValue();
            
            if (trends != null && !trends.isEmpty()) {
                double avgPercentChange = trends.stream()
                    .mapToDouble(t -> t.percentChange)
                    .average()
                    .orElse(0.0);
                
                trendSummaryStatistics.put(metric + "_avg_change", avgPercentChange);
                
                // Check if trend is accelerating
                boolean accelerating = isAcceleratingTrend(trends);
                trendSummaryStatistics.put(metric + "_accelerating", 
                    accelerating ? 1.0 : 0.0);
            }
        }
    }
    
    private boolean isAcceleratingTrend(List<ScalabilityAnalyzer.PerformanceTrend> trends) {
        if (trends.size() < 2) return false;
        
        // Check if rate of change is increasing
        double prevChange = trends.get(0).percentChange;
        int increasingCount = 0;
        
        for (int i = 1; i < trends.size(); i++) {
            double currChange = trends.get(i).percentChange;
            if (currChange > prevChange) {
                increasingCount++;
            }
            prevChange = currChange;
        }
        
        return increasingCount > trends.size() / 2;
    }
    
    private void determineLimitingFactor() {
        if (scalabilityLimits == null || scalabilityLimits.isEmpty()) return;
        
        int minLimit = Integer.MAX_VALUE;
        String factor = "unknown";
        
        for (Map.Entry<String, Integer> entry : scalabilityLimits.entrySet()) {
            if (entry.getValue() < minLimit) {
                minLimit = entry.getValue();
                factor = entry.getKey();
            }
        }
        
        this.limitingFactor = factor;
        this.recommendedMaxScale = (int) (minLimit * 0.8); // 80% of limit for safety
    }
    
    private void calculateEfficiencyDegradationRate() {
        if (efficiencyByScale == null || efficiencyByScale.size() < 2) {
            this.efficiencyDegradationRate = 0.0;
            return;
        }
        
        // Find min and max scales
        int minScale = efficiencyByScale.keySet().stream()
            .mapToInt(Integer::intValue)
            .min()
            .orElse(0);
        int maxScale = efficiencyByScale.keySet().stream()
            .mapToInt(Integer::intValue)
            .max()
            .orElse(0);
        
        if (minScale == maxScale) {
            this.efficiencyDegradationRate = 0.0;
            return;
        }
        
        double minEfficiency = efficiencyByScale.getOrDefault(minScale, 1.0);
        double maxEfficiency = efficiencyByScale.getOrDefault(maxScale, 1.0);
        
        // Calculate percentage degradation per scale unit
        this.efficiencyDegradationRate = 
            ((minEfficiency - maxEfficiency) / minEfficiency) / (maxScale - minScale) * 100;
    }
    
    /**
     * Generate a summary suitable for reporting
     */
    public Map<String, Object> generateSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        summary.put("algorithm", algorithmName);
        summary.put("complexity", complexitySummary);
        summary.put("scalability_limits", scalabilityLimits);
        summary.put("efficiency_degradation_rate", efficiencyDegradationRate);
        summary.put("limiting_factor", limitingFactor);
        summary.put("recommended_max_scale", recommendedMaxScale);
        
        // Add key metrics
        Map<String, Double> keyMetrics = new HashMap<>();
        keyMetrics.put("time_complexity_r_squared", 
            complexityModel != null ? complexityModel.rSquaredTime : 0.0);
        keyMetrics.put("space_complexity_r_squared", 
            complexityModel != null ? complexityModel.rSquaredSpace : 0.0);
        
        summary.put("key_metrics", keyMetrics);
        
        return summary;
    }
    
    /**
     * Check if the algorithm is scalable based on analysis
     */
    public boolean isScalable() {
        // Algorithm is considered scalable if:
        // 1. Complexity is not exponential
        // 2. Efficiency degradation is less than 5% per scale unit
        // 3. Practical limit is greater than 1000 VMs
        
        if (complexityModel == null) return false;
        
        boolean notExponential = complexityModel.timeComplexity.getOrder() < 
            ScalabilityAnalyzer.ComplexityClass.EXPONENTIAL.getOrder();
        boolean acceptableDegradation = Math.abs(efficiencyDegradationRate) < 5.0;
        boolean goodLimit = recommendedMaxScale > 1000;
        
        return notExponential && acceptableDegradation && goodLimit;
    }
    
    @Override
    public String toString() {
        return String.format(
            "ScalabilityResults{algorithm='%s', timeComplexity='%s', " +
            "spaceComplexity='%s', practicalLimit=%d, scalable=%s}",
            algorithmName,
            complexityModel != null ? complexityModel.timeComplexity.getNotation() : "N/A",
            complexityModel != null ? complexityModel.spaceComplexity.getNotation() : "N/A",
            recommendedMaxScale,
            isScalable()
        );
    }
}