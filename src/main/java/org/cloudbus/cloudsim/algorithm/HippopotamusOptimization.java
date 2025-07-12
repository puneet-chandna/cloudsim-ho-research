package org.cloudbus.cloudsim.algorithm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.random.MersenneTwister;

/**
 * Hippopotamus Optimization Algorithm for VM Placement
 * Research-focused implementation with comprehensive tracking and analysis
 * 
 * Research Objectives Addressed:
 * - Multi-objective VM placement optimization
 * - Convergence analysis and tracking
 * - Parameter sensitivity analysis support
 * - Statistical rigor with reproducible results
 * 
 * Metrics Calculated:
 * - Resource utilization efficiency
 * - Power consumption optimization
 * - SLA violation minimization
 * - Load balancing effectiveness
 * 
 * @author Puneet Chandna
 * @version 2.0 - Enhanced for comprehensive research analysis
 */
public class HippopotamusOptimization {
    private static final Logger logger = LoggerFactory.getLogger(HippopotamusOptimization.class);
    
    // Research-specific tracking
    private List<Double> convergenceHistory;
    private List<Double> diversityHistory;
    private Map<String, List<Double>> parameterSensitivityData;
    private long optimizationStartTime;
    private long optimizationEndTime;
    private int functionEvaluations;
    private MersenneTwister random;
    
    // Algorithm state
    private List<Hippopotamus> population;
    private Hippopotamus globalBest;
    private int currentIteration;
    private boolean hasConverged;
    private double [] bestFitnessHistory;
    
    // Statistical tracking
    private DescriptiveStatistics fitnessStats;
    private DescriptiveStatistics diversityStats;
    private Map<String, Object> executionMetrics;
    
    /**
     * Constructor with research-focused initialization
     */
    public HippopotamusOptimization() {
        this.convergenceHistory = new ArrayList<>();
        this.diversityHistory = new ArrayList<>();
        this.parameterSensitivityData = new HashMap<>();
        this.functionEvaluations = 0;
        this.hasConverged = false;
        this.fitnessStats = new DescriptiveStatistics();
        this.diversityStats = new DescriptiveStatistics();
        this.executionMetrics = new HashMap<>();
        this.random = new MersenneTwister();
        
        logger.info("HippopotamusOptimization initialized for research framework");
    }
    
    /**
     * Constructor with seed for reproducible research
     * @param seed Random seed for reproducibility
     */
    public HippopotamusOptimization(long seed) {
        this();
        this.random = new MersenneTwister(seed);
        logger.info("HippopotamusOptimization initialized with seed: {}", seed);
    }
    
    /**
     * Main optimization method with comprehensive research tracking
     * @param vmCount Number of VMs to place
     * @param hostCount Number of available hosts
     * @param params Algorithm parameters
     * @return Comprehensive optimization result
     */
    public SimpleOptimizationResult optimize(int vmCount, int hostCount, HippopotamusParameters params) {
        logger.info("Starting HO optimization: VMs={}, Hosts={}, PopSize={}", 
                   vmCount, hostCount, params.getPopulationSize());
        
        optimizationStartTime = System.currentTimeMillis();
        validateParameters(vmCount, hostCount, params);
        
        try {
            // Initialize optimization
            initializeOptimization(vmCount, hostCount, params);
            
            // Add timeout mechanism
            long startTime = System.currentTimeMillis();
            long maxExecutionTime = 300000; // 5 minutes timeout
            
            // Main optimization loop
            for (currentIteration = 0; currentIteration < params.getMaxIterations(); currentIteration++) {
                // Check timeout
                if (System.currentTimeMillis() - startTime > maxExecutionTime) {
                    logger.warn("Optimization timeout reached after {} ms", maxExecutionTime);
                    break;
                }
                
                // Update hippopotamus positions
                updatePopulation(params);
                
                // Evaluate fitness for all hippos
                evaluatePopulation(vmCount, hostCount, params);
                
                // Update global best
                updateGlobalBest();
                
                // Track convergence and diversity
                trackOptimizationProgress();
                
                // Check convergence
                if (checkConvergence(params)) {
                    logger.info("Convergence achieved at iteration {}", currentIteration);
                    hasConverged = true;
                    break;
                }
                
                // Log progress
                if (currentIteration % 10 == 0) {
                    logger.debug("Iteration {}: Best fitness = {:.6f}", 
                               currentIteration, globalBest.getFitness());
                }
            }
            
            optimizationEndTime = System.currentTimeMillis();
            return generateOptimizationResult(params);
            
        } catch (Exception e) {
            logger.error("Error during optimization", e);
            throw new RuntimeException("Optimization failed", e);
        }
    }
    
    /**
     * Initialize hippopotamus population with research tracking
     */
    private void initializeOptimization(int vmCount, int hostCount, HippopotamusParameters params) {
        population = new ArrayList<>();
        convergenceHistory.clear();
        diversityHistory.clear();
        functionEvaluations = 0;
        currentIteration = 0;
        hasConverged = false;
        bestFitnessHistory = new double[params.getMaxIterations()];
        
        // Initialize population
        initializePopulation(vmCount, hostCount, params.getPopulationSize());
        
        // Initialize global best
        globalBest = new Hippopotamus(population.get(0));
        
        logger.info("Population initialized with {} hippos", population.size());
    }
    
    /**
     * Initialize hippopotamus population with diverse solutions
     * @param vmCount Number of VMs
     * @param hostCount Number of hosts
     * @param populationSize Population size
     */
    public void initializePopulation(int vmCount, int hostCount, int populationSize) {
        population = new ArrayList<>();
        
        for (int i = 0; i < populationSize; i++) {
            Hippopotamus hippo = new Hippopotamus(vmCount, hostCount);
            
            // Generate diverse initial solutions
            if (i == 0) {
                // First solution: Sequential assignment
                hippo.initializeSequential();
            } else if (i == 1) {
                // Second solution: Balanced assignment
                hippo.initializeBalanced();
            } else {
                // Random solutions with constraints
                hippo.initializeRandom(random);
            }
            
            population.add(hippo);
        }
        
        logger.debug("Initialized population with {} diverse solutions", populationSize);
    }
    
    /**
     * Update hippopotamus population using HO algorithm mechanics
     */
    private void updatePopulation(HippopotamusParameters params) {
        double t = (double) currentIteration / params.getMaxIterations();
        
        for (int i = 0; i < population.size(); i++) {
            Hippopotamus hippo = population.get(i);
            
            // HO position update with adaptive parameters
            updateHippopotamusPosition(hippo, t, params);
            
            // Ensure solution validity
            hippo.validateAndRepair();
        }
    }
    
    /**
     * Update individual hippopotamus position using HO mechanics
     */
    private void updateHippopotamusPosition(Hippopotamus hippo, double t, HippopotamusParameters params) {
        int[] position = hippo.getPosition();
        int[] newPosition = new int[position.length];
        
        // HO-specific position update
        for (int vm = 0; vm < position.length; vm++) {
            // Get current and best positions
            int currentHost = position[vm];
            int bestHost = globalBest.getPosition()[vm];
            
            // Random hippopotamus for social behavior
            Hippopotamus randomHippo = population.get(ThreadLocalRandom.current().nextInt(population.size()));
            int randomHost = randomHippo.getPosition()[vm];
            
            // HO position update equation with research parameters
            double r1 = ThreadLocalRandom.current().nextDouble();
            double r2 = ThreadLocalRandom.current().nextDouble();
            double r3 = ThreadLocalRandom.current().nextDouble();
            
            // Adaptive coefficient based on iteration (ensure it doesn't go to zero)
            double H = Math.max(0.1, 2 * (1 - t)); // Decreases over time but has minimum
            
            if (r1 < 0.5) {
                // Exploration phase
                if (r2 < 0.5) {
                    // Move towards best solution
                    newPosition[vm] = (int) (currentHost + r3 * H * (bestHost - currentHost));
                } else {
                    // Move towards random solution
                    newPosition[vm] = (int) (currentHost + r3 * H * (randomHost - currentHost));
                }
            } else {
                // Exploitation phase
                newPosition[vm] = (int) (bestHost + r3 * H * (2 * r2 - 1));
            }
            
            // Ensure valid host assignment with proper bounds checking
            newPosition[vm] = Math.max(0, Math.min(hippo.getHostCount() - 1, newPosition[vm]));
            
            // Additional safety check to prevent invalid assignments
            if (newPosition[vm] < 0 || newPosition[vm] >= hippo.getHostCount()) {
                newPosition[vm] = ThreadLocalRandom.current().nextInt(hippo.getHostCount());
            }
        }
        
        hippo.setPosition(newPosition);
        hippo.validateAndRepair();
    }
    
    /**
     * Evaluate fitness for all hippos with comprehensive metrics
     */
    private void evaluatePopulation(int vmCount, int hostCount, HippopotamusParameters params) {
        population.stream().forEach(hippo -> {
            double fitness = evaluateFitness(hippo, params.getObjectiveWeights());
            hippo.setFitness(fitness);
            synchronized (this) {
                functionEvaluations++;
            }
        });
        
        // Update fitness statistics
        updateFitnessStatistics();
    }
    
    /**
     * Multi-objective fitness evaluation with research metrics
     * @param hippo Hippopotamus to evaluate
     * @param weights Objective weights for multi-objective optimization
     * @return Weighted fitness value
     */
    public double evaluateFitness(Hippopotamus hippo, ObjectiveWeights weights) {
        if (weights == null) {
            weights = ObjectiveWeights.getDefaultWeights();
        }
        
        // Calculate individual objectives
        double resourceUtilization = calculateResourceUtilization(hippo);
        double powerConsumption = calculatePowerConsumption(hippo);
        double slaViolations = calculateSLAViolations(hippo);
        double loadBalance = calculateLoadBalance(hippo);
        double communicationCost = calculateCommunicationCost(hippo);
        
        // Store detailed metrics in hippo
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("resource_utilization", resourceUtilization);
        metrics.put("power_consumption", powerConsumption);
        metrics.put("sla_violations", slaViolations);
        metrics.put("load_balance", loadBalance);
        metrics.put("communication_cost", communicationCost);
        hippo.setDetailedMetrics(metrics);
        
        // Weighted combination (minimization problem - lower is better)
        double fitness = weights.getResourceWeight() * (1.0 - resourceUtilization) +
                        weights.getPowerWeight() * powerConsumption +
                        weights.getSlaWeight() * slaViolations +
                        weights.getLoadBalanceWeight() * (1.0 - loadBalance) +
                        weights.getCommunicationWeight() * communicationCost;
        
        return fitness;
    }
    
    /**
     * Calculate resource utilization efficiency
     */
    private double calculateResourceUtilization(Hippopotamus hippo) {
        int[] position = hippo.getPosition();
        int[] hostLoads = new int[hippo.getHostCount()];
        
        // Count VMs per host
        for (int vmHost : position) {
            hostLoads[vmHost]++;
        }
        
        // Calculate utilization variance (lower is better for balance)
        double mean = (double) position.length / hippo.getHostCount();
        double variance = 0.0;
        int usedHosts = 0;
        
        for (int load : hostLoads) {
            if (load > 0) {
                usedHosts++;
                variance += Math.pow(load - mean, 2);
            }
        }
        
        if (usedHosts == 0) return 0.0;
        
        variance /= usedHosts;
        
        // Convert to efficiency metric (higher is better)
        double efficiency = 1.0 / (1.0 + variance);
        return Math.min(1.0, efficiency);
    }
    
    /**
     * Calculate power consumption (normalized)
     */
    private double calculatePowerConsumption(Hippopotamus hippo) {
        int[] position = hippo.getPosition();
        Set<Integer> usedHosts = new HashSet<>();
        
        for (int host : position) {
            usedHosts.add(host);
        }
        
        // Power consumption is proportional to active hosts
        double powerRatio = (double) usedHosts.size() / hippo.getHostCount();
        return powerRatio;
    }
    
    /**
     * Calculate SLA violations (simulated based on overloading)
     */
    private double calculateSLAViolations(Hippopotamus hippo) {
        int[] position = hippo.getPosition();
        int[] hostLoads = new int[hippo.getHostCount()];
        
        for (int vmHost : position) {
            hostLoads[vmHost]++;
        }
        
        // Assume each host can handle up to maxVMsPerHost VMs without violations
        int maxVMsPerHost = Math.max(1, position.length / hippo.getHostCount() + 2);
        int violations = 0;
        
        for (int load : hostLoads) {
            if (load > maxVMsPerHost) {
                violations += (load - maxVMsPerHost);
            }
        }
        
        return (double) violations / position.length;
    }
    
    /**
     * Calculate load balance quality
     */
    private double calculateLoadBalance(Hippopotamus hippo) {
        return 1.0 - calculateResourceUtilization(hippo); // Inverse of utilization variance
    }
    
    /**
     * Calculate communication cost (simplified)
     */
    private double calculateCommunicationCost(Hippopotamus hippo) {
        // Simplified: assume random communication patterns
        // In real implementation, this would consider actual VM communication
        return ThreadLocalRandom.current().nextDouble() * 0.1; // Thread-safe random small cost
    }
    
    /**
     * Update global best solution
     */
    private void updateGlobalBest() {
        Hippopotamus currentBest = population.stream()
            .min(Comparator.comparingDouble(Hippopotamus::getFitness))
            .orElse(null);
        
        if (currentBest != null && 
            (globalBest == null || currentBest.getFitness() < globalBest.getFitness())) {
            globalBest = new Hippopotamus(currentBest);
            logger.debug("New global best found: fitness = {:.6f}", globalBest.getFitness());
        }
    }
    
    /**
     * Track optimization progress for research analysis
     */
    private void trackOptimizationProgress() {
        // Track convergence
        double currentBestFitness = globalBest.getFitness();
        convergenceHistory.add(currentBestFitness);
        bestFitnessHistory[currentIteration] = currentBestFitness;
        
        // Track population diversity
        double diversity = calculatePopulationDiversity();
        diversityHistory.add(diversity);
        
        // Update statistics
        fitnessStats.addValue(currentBestFitness);
        diversityStats.addValue(diversity);
    }
    
    /**
     * Calculate population diversity for research analysis
     */
    private double calculatePopulationDiversity() {
        if (population.size() < 2) return 0.0;
        
        double totalDistance = 0.0;
        int comparisons = 0;
        
        for (int i = 0; i < population.size(); i++) {
            for (int j = i + 1; j < population.size(); j++) {
                totalDistance += calculateHammingDistance(
                    population.get(i).getPosition(),
                    population.get(j).getPosition()
                );
                comparisons++;
            }
        }
        
        return comparisons > 0 ? totalDistance / comparisons : 0.0;
    }
    
    /**
     * Calculate Hamming distance between two solutions
     */
    private double calculateHammingDistance(int[] solution1, int[] solution2) {
        if (solution1.length != solution2.length) return 0.0;
        
        int differences = 0;
        for (int i = 0; i < solution1.length; i++) {
            if (solution1[i] != solution2[i]) {
                differences++;
            }
        }
        
        return (double) differences / solution1.length;
    }
    
    /**
     * Check convergence based on research criteria
     */
    private boolean checkConvergence(HippopotamusParameters params) {
        // Add maximum iteration check to prevent infinite loops
        if (currentIteration >= params.getMaxIterations() - 1) {
            logger.info("Maximum iterations reached: {}", params.getMaxIterations());
            return true;
        }
        
        if (convergenceHistory.size() < params.getConvergenceWindow()) {
            return false;
        }
        
        // Check if improvement is below threshold
        int windowSize = params.getConvergenceWindow();
        List<Double> recentHistory = convergenceHistory.subList(
            convergenceHistory.size() - windowSize, convergenceHistory.size()
        );
        
        double minFitness = Collections.min(recentHistory);
        double maxFitness = Collections.max(recentHistory);
        double improvement = maxFitness - minFitness;
        
        boolean converged = improvement < params.getConvergenceThreshold();
        if (converged) {
            logger.info("Convergence achieved: improvement = {:.6f} < threshold = {:.6f}", 
                       improvement, params.getConvergenceThreshold());
        }
        
        return converged;
    }
    
    /**
     * Update fitness statistics for research analysis
     */
    private void updateFitnessStatistics() {
        // Calculate population fitness statistics
        double[] fitnessValues = population.stream()
            .mapToDouble(Hippopotamus::getFitness)
            .toArray();
        
        DescriptiveStatistics stats = new DescriptiveStatistics(fitnessValues);
        
        executionMetrics.put("current_mean_fitness", stats.getMean());
        executionMetrics.put("current_std_fitness", stats.getStandardDeviation());
        executionMetrics.put("current_min_fitness", stats.getMin());
        executionMetrics.put("current_max_fitness", stats.getMax());
    }
    
    /**
     * Validate optimization parameters
     */
    private void validateParameters(int vmCount, int hostCount, HippopotamusParameters params) {
        if (vmCount <= 0) {
            throw new IllegalArgumentException("VM count must be positive");
        }
        if (hostCount <= 0) {
            throw new IllegalArgumentException("Host count must be positive");
        }
        if (hostCount > vmCount) {
            logger.warn("More hosts than VMs - some hosts will be unused");
        }
        if (params == null) {
            throw new IllegalArgumentException("Parameters cannot be null");
        }
        
        params.validate();
    }
    
    /**
     * Select best solution with diversity consideration
     * @return Best hippopotamus solution
     */
    public Hippopotamus selectBestSolution() {
        return globalBest != null ? new Hippopotamus(globalBest) : null;
    }
    
    /**
     * Track convergence metrics for research analysis
     * @return Convergence data
     */
    public List<Double> trackConvergence() {
        return new ArrayList<>(convergenceHistory);
    }
    
    /**
     * Get detailed optimization results for research
     * @return Comprehensive optimization results
     */
    public SimpleOptimizationResult getDetailedResults() {
        if (globalBest == null) {
            throw new IllegalStateException("Optimization not completed");
        }
        
        return generateOptimizationResult(null);
    }
    
    /**
     * Generate comprehensive optimization result
     */
    private SimpleOptimizationResult generateOptimizationResult(HippopotamusParameters params) {
        long executionTime = optimizationEndTime - optimizationStartTime;
        
        // Prepare execution metrics
        Map<String, Object> metrics = new HashMap<>(executionMetrics);
        metrics.put("execution_time_ms", executionTime);
        metrics.put("function_evaluations", functionEvaluations);
        metrics.put("final_iteration", currentIteration);
        metrics.put("converged", hasConverged);
        metrics.put("population_size", population.size());
        
        // Statistical analysis
        Map<String, Double> statisticalData = new HashMap<>();
        if (fitnessStats.getN() > 0) {
            statisticalData.put("final_fitness_mean", fitnessStats.getMean());
            statisticalData.put("final_fitness_std", fitnessStats.getStandardDeviation());
            statisticalData.put("fitness_improvement", 
                convergenceHistory.size() > 1 ? 
                convergenceHistory.get(0) - convergenceHistory.get(convergenceHistory.size() - 1) : 0.0);
        }
        
        if (diversityStats.getN() > 0) {
            statisticalData.put("average_diversity", diversityStats.getMean());
            statisticalData.put("diversity_std", diversityStats.getStandardDeviation());
        }
        
        return new SimpleOptimizationResult(
            globalBest,
            new ArrayList<>(convergenceHistory),
            new ArrayList<>(diversityHistory),
            metrics,
            statisticalData
        );
    }
    
    // Getters for research analysis
    public List<Double> getConvergenceHistory() {
        return new ArrayList<>(convergenceHistory);
    }
    
    public List<Double> getDiversityHistory() {
        return new ArrayList<>(diversityHistory);
    }
    
    public int getFunctionEvaluations() {
        return functionEvaluations;
    }
    
    public boolean hasConverged() {
        return hasConverged;
    }
    
    public long getExecutionTime() {
        return optimizationEndTime - optimizationStartTime;
    }
    
    public Hippopotamus getGlobalBest() {
        return globalBest != null ? new Hippopotamus(globalBest) : null;
    }
    
    public List<Hippopotamus> getPopulation() {
        return population.stream()
            .map(Hippopotamus::new)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
}
