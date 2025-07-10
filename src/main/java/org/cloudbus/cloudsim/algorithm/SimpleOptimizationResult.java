package org.cloudbus.cloudsim.algorithm;

import java.util.*;

/**
 * Simplified optimization results container for Hippopotamus Optimization algorithm.
 * This version removes CloudSim Plus dependencies and focuses on essential research data.
 * 
 * @author Puneet Chandna
 * @since CloudSim Plus 7.0.1
 */
public class SimpleOptimizationResult {
    
    // Core optimization results
    private final Hippopotamus bestSolution;
    private final double bestFitness;
    private final boolean converged;
    private final int totalIterations;
    
    // Convergence tracking
    private final List<Double> convergenceHistory;
    private final List<Double> diversityHistory;
    
    // Execution statistics
    private final long executionTimeMs;
    private final int functionEvaluations;
    
    // Statistical data
    private final Map<String, Object> executionMetrics;
    private final Map<String, Double> statisticalData;
    
    /**
     * Constructor for simplified optimization results
     * 
     * @param bestSolution Best hippopotamus solution found
     * @param convergenceHistory History of best fitness values per iteration
     * @param diversityHistory History of population diversity per iteration  
     * @param executionMetrics Map of execution metrics
     * @param statisticalData Map of statistical analysis data
     */
    public SimpleOptimizationResult(Hippopotamus bestSolution, 
                                   List<Double> convergenceHistory,
                                   List<Double> diversityHistory,
                                   Map<String, Object> executionMetrics,
                                   Map<String, Double> statisticalData) {
        this.bestSolution = bestSolution;
        this.bestFitness = bestSolution != null ? bestSolution.getFitness() : Double.MAX_VALUE;
        this.converged = (Boolean) executionMetrics.getOrDefault("converged", false);
        this.totalIterations = ((Number) executionMetrics.getOrDefault("final_iteration", 0)).intValue();
        
        this.convergenceHistory = new ArrayList<>(convergenceHistory != null ? convergenceHistory : new ArrayList<>());
        this.diversityHistory = new ArrayList<>(diversityHistory != null ? diversityHistory : new ArrayList<>());
        
        this.executionTimeMs = ((Number) executionMetrics.getOrDefault("execution_time_ms", 0L)).longValue();
        this.functionEvaluations = ((Number) executionMetrics.getOrDefault("function_evaluations", 0)).intValue();
        
        this.executionMetrics = new HashMap<>(executionMetrics != null ? executionMetrics : new HashMap<>());
        this.statisticalData = new HashMap<>(statisticalData != null ? statisticalData : new HashMap<>());
    }
    
    // Getters
    public Hippopotamus getBestSolution() { return bestSolution; }
    public double getBestFitness() { return bestFitness; }
    public boolean hasConverged() { return converged; }
    public int getTotalIterations() { return totalIterations; }
    public List<Double> getConvergenceHistory() { return Collections.unmodifiableList(convergenceHistory); }
    public List<Double> getDiversityHistory() { return Collections.unmodifiableList(diversityHistory); }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public int getFunctionEvaluations() { return functionEvaluations; }
    public Map<String, Object> getExecutionMetrics() { return Collections.unmodifiableMap(executionMetrics); }
    public Map<String, Double> getStatisticalData() { return Collections.unmodifiableMap(statisticalData); }
    
    @Override
    public String toString() {
        return String.format(
            "SimpleOptimizationResult{bestFitness=%.6f, converged=%s, iterations=%d, time=%dms, evaluations=%d}", 
            bestFitness, converged, totalIterations, executionTimeMs, functionEvaluations
        );
    }
}
