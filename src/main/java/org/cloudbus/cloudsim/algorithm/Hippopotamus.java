package org.cloudbus.cloudsim.algorithm;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.stream.IntStream;

/**
 * Individual Hippopotamus representation for VM placement optimization
 * Enhanced with comprehensive research tracking and analysis capabilities
 * 
 * Research Features:
 * - Detailed solution metrics tracking
 * - Historical fitness evolution
 * - Diversity measurement support
 * - Constraint validation and repair
 * 
 * @author Research Framework
 * @version 2.0 - Enhanced for research analysis
 */
public class Hippopotamus {
    private static final Logger logger = LoggerFactory.getLogger(Hippopotamus.class);
    
    // Core solution representation
    private int[] position;           // VM-to-Host mapping (position[vm] = host)
    private double fitness;           // Overall fitness value
    private final int vmCount;        // Number of VMs
    private final int hostCount;      // Number of available hosts
    
    // Research-specific tracking
    private List<Double> fitnessHistory;          // Historical fitness values
    private Map<String, Double> detailedMetrics;  // Detailed performance metrics
    private double diversityMeasure;              // Solution diversity measure
    private long evaluationCount;                 // Number of evaluations
    private long lastEvaluationTime;              // Last evaluation timestamp
    
    // Solution quality indicators
    private boolean isValid;                      // Solution validity flag
    private int constraintViolations;             // Number of constraint violations
    private double solutionQuality;               // Overall solution quality
    
    // Optimization metadata
    private Map<String, Object> metadata;         // Additional metadata
    private int generationCreated;                // Generation when created
    private boolean isElite;                      // Elite solution flag
    
    // Multi-objective fitness components
    private double[] objectiveFitness;            // Individual objective values
    private boolean isDominatedBy;                // Pareto dominance flag
    private int dominationCount;                  // Number of solutions this dominates
    private List<Integer> dominatedSolutions;     // List of dominated solution indices
    
    /**
     * Constructor for new hippopotamus
     * @param vmCount Number of VMs to place
     * @param hostCount Number of available hosts
     */
    public Hippopotamus(int vmCount, int hostCount) {
        if (vmCount <= 0 || hostCount <= 0) {
            throw new IllegalArgumentException("VM count and host count must be positive");
        }
        
        this.vmCount = vmCount;
        this.hostCount = hostCount;
        this.position = new int[vmCount];
        this.fitness = Double.MAX_VALUE; // Minimization problem
        this.fitnessHistory = new ArrayList<>();
        this.detailedMetrics = new HashMap<>();
        this.metadata = new HashMap<>();
        this.isValid = false;
        this.constraintViolations = 0;
        this.evaluationCount = 0;
        this.diversityMeasure = 0.0;
        this.solutionQuality = 0.0;
        this.isElite = false;
        this.objectiveFitness = new double[5]; // 5 objectives
        this.isDominatedBy = false;
        this.dominationCount = 0;
        this.dominatedSolutions = new ArrayList<>();
        
        logger.debug("Created hippopotamus for {} VMs and {} hosts", vmCount, hostCount);
    }
    
    /**
     * Copy constructor for research reproducibility
     * @param other Hippopotamus to copy
     */
    public Hippopotamus(Hippopotamus other) {
        this.vmCount = other.vmCount;
        this.hostCount = other.hostCount;
        this.position = Arrays.copyOf(other.position, other.position.length);
        this.fitness = other.fitness;
        this.fitnessHistory = new ArrayList<>(other.fitnessHistory);
        this.detailedMetrics = new HashMap<>(other.detailedMetrics);
        this.diversityMeasure = other.diversityMeasure;
        this.evaluationCount = other.evaluationCount;
        this.lastEvaluationTime = other.lastEvaluationTime;
        this.isValid = other.isValid;
        this.constraintViolations = other.constraintViolations;
        this.solutionQuality = other.solutionQuality;
        this.metadata = new HashMap<>(other.metadata);
        this.generationCreated = other.generationCreated;
        this.isElite = other.isElite;
        this.objectiveFitness = Arrays.copyOf(other.objectiveFitness, other.objectiveFitness.length);
        this.isDominatedBy = other.isDominatedBy;
        this.dominationCount = other.dominationCount;
        this.dominatedSolutions = new ArrayList<>(other.dominatedSolutions);
    }
    
    /**
     * Initialize with sequential host assignment
     * Creates a balanced initial solution
     */
    public void initializeSequential() {
        for (int vm = 0; vm < vmCount; vm++) {
            position[vm] = vm % hostCount;
        }
        validateAndRepair();
        logger.debug("Initialized sequential assignment");
    }
    
    /**
     * Initialize with balanced host assignment
     * Distributes VMs evenly across hosts
     */
    public void initializeBalanced() {
        int vmsPerHost = vmCount / hostCount;
        int remainder = vmCount % hostCount;
        int vmIndex = 0;
        
        for (int host = 0; host < hostCount; host++) {
            int vmsForThisHost = vmsPerHost + (host < remainder ? 1 : 0);
            for (int i = 0; i < vmsForThisHost; i++) {
                if (vmIndex < vmCount) {
                    position[vmIndex++] = host;
                }
            }
        }
        
        validateAndRepair();
        logger.debug("Initialized balanced assignment");
    }
    
    /**
     * Initialize with random host assignment
     * @param random Random number generator for reproducibility
     */
    public void initializeRandom(MersenneTwister random) {
        for (int vm = 0; vm < vmCount; vm++) {
            position[vm] = random.nextInt(hostCount);
        }
        validateAndRepair();
        logger.debug("Initialized random assignment");
    }
    
    /**
     * Update position with Hippopotamus Optimization parameters
     * @param params Algorithm parameters
     */
    public void updatePosition(HippopotamusParameters params) {
        // This method is called from HippopotamusOptimization
        // Position updates are handled there, this validates the result
        validateAndRepair();
        
        // Update metadata
        lastEvaluationTime = System.currentTimeMillis();
        metadata.put("last_update_iteration", params.getCurrentIteration());
    }
    
    /**
     * Calculate multi-objective fitness with detailed tracking
     * @return Calculated fitness value
     */
    public double calculateMultiObjectiveFitness() {
        long startTime = System.nanoTime();
        
        // Calculate individual objectives
        double resourceUtilization = calculateResourceUtilizationObjective();
        double loadBalance = calculateLoadBalanceObjective();
        double powerEfficiency = calculatePowerEfficiencyObjective();
        double slaCompliance = calculateSLAComplianceObjective();
        double communicationCost = calculateCommunicationCostObjective();
        
        // Store individual objective values
        objectiveFitness[0] = resourceUtilization;
        objectiveFitness[1] = loadBalance;
        objectiveFitness[2] = powerEfficiency;
        objectiveFitness[3] = slaCompliance;
        objectiveFitness[4] = communicationCost;
        
        // Store detailed metrics
        detailedMetrics.put("resource_utilization", resourceUtilization);
        detailedMetrics.put("load_balance", loadBalance);
        detailedMetrics.put("power_efficiency", powerEfficiency);
        detailedMetrics.put("sla_compliance", slaCompliance);
        detailedMetrics.put("communication_cost", communicationCost);
        
        // Calculate weighted fitness (minimization)
        ObjectiveWeights weights = ObjectiveWeights.getDefaultWeights();
        double calculatedFitness = 
            weights.getResourceWeight() * (1.0 - resourceUtilization) +
            weights.getLoadBalanceWeight() * (1.0 - loadBalance) +
            weights.getPowerWeight() * (1.0 - powerEfficiency) +
            weights.getSlaWeight() * (1.0 - slaCompliance) +
            weights.getCommunicationWeight() * communicationCost;
        
        // Update fitness and tracking
        setFitness(calculatedFitness);
        
        long endTime = System.nanoTime();
        detailedMetrics.put("evaluation_time_ns", (double)(endTime - startTime));
        evaluationCount++;
        
        return calculatedFitness;
    }
    
    /**
     * Calculate resource utilization objective
     */
    private double calculateResourceUtilizationObjective() {
        int[] hostLoads = new int[hostCount];
        
        // Count VMs per host
        for (int host : position) {
            hostLoads[host]++;
        }
        
        // Calculate utilization efficiency
        int usedHosts = (int) Arrays.stream(hostLoads).filter(load -> load > 0).count();
        if (usedHosts == 0) return 0.0;
        
        // Resource consolidation metric (higher is better)
        double consolidationRatio = (double) usedHosts / hostCount;
        
        // Load distribution variance (lower is better)
        double avgLoad = (double) vmCount / usedHosts;
        double variance = Arrays.stream(hostLoads)
            .filter(load -> load > 0)
            .mapToDouble(load -> Math.pow(load - avgLoad, 2))
            .average()
            .orElse(0.0);
        
        // Combine metrics (0-1 scale, higher is better)
        return Math.max(0.0, 1.0 - consolidationRatio) * 0.7 + 
               Math.max(0.0, 1.0 - (variance / avgLoad)) * 0.3;
    }
    
    /**
     * Calculate load balance objective
     */
    private double calculateLoadBalanceObjective() {
        int[] hostLoads = new int[hostCount];
        
        // Count VMs per host
        for (int host : position) {
            hostLoads[host]++;
        }
        
        // Calculate load balance using coefficient of variation
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (int load : hostLoads) {
            stats.addValue(load);
        }
        
        double mean = stats.getMean();
        double stdDev = stats.getStandardDeviation();
        
        if (mean == 0) return 0.0;
        
        // Coefficient of variation (lower is better, more balanced)
        double cv = stdDev / mean;
        
        // Convert to 0-1 scale (higher is better)
        return Math.max(0.0, 1.0 - cv);
    }
    
    /**
     * Calculate power efficiency objective
     */
    private double calculatePowerEfficiencyObjective() {
        int[] hostLoads = new int[hostCount];
        
        // Count VMs per host
        for (int host : position) {
            hostLoads[host]++;
        }
        
        double totalPowerConsumption = 0.0;
        int activeHosts = 0;
        
        for (int host = 0; host < hostCount; host++) {
            if (hostLoads[host] > 0) {
                activeHosts++;
                // Power model: P = P_idle + (P_max - P_idle) * utilization
                double utilization = Math.min(1.0, hostLoads[host] / 10.0); // Assume max 10 VMs per host
                double idlePower = 150.0; // Watts
                double maxPower = 250.0; // Watts
                totalPowerConsumption += idlePower + (maxPower - idlePower) * utilization;
            }
        }
        
        // Calculate power efficiency (VMs per Watt)
        if (totalPowerConsumption == 0) return 0.0;
        
        double powerEfficiency = vmCount / totalPowerConsumption;
        
        // Normalize to 0-1 scale
        double maxPossibleEfficiency = vmCount / (activeHosts * 150.0); // Best case: all idle power
        return Math.min(1.0, powerEfficiency / maxPossibleEfficiency);
    }
    
    /**
     * Calculate SLA compliance objective
     */
    private double calculateSLAComplianceObjective() {
        int[] hostLoads = new int[hostCount];
        
        // Count VMs per host
        for (int host : position) {
            hostLoads[host]++;
        }
        
        int violations = 0;
        int totalChecks = 0;
        
        for (int host = 0; host < hostCount; host++) {
            if (hostLoads[host] > 0) {
                totalChecks++;
                
                // Check resource oversubscription
                double utilization = hostLoads[host] / 10.0; // Assume max 10 VMs per host
                if (utilization > 0.9) { // 90% threshold
                    violations++;
                }
                
                // Check response time violation (simplified)
                if (hostLoads[host] > 8) { // Performance degradation threshold
                    violations++;
                    totalChecks++;
                }
            }
        }
        
        if (totalChecks == 0) return 1.0;
        
        // SLA compliance rate (higher is better)
        return Math.max(0.0, 1.0 - (double) violations / totalChecks);
    }
    
    /**
     * Calculate communication cost objective
     */
    private double calculateCommunicationCostObjective() {
        // Simplified communication cost based on VM distribution
        Set<Integer> uniqueHosts = new HashSet<>();
        for (int host : position) {
            uniqueHosts.add(host);
        }
        
        // More hosts mean higher communication overhead
        double communicationOverhead = (double) uniqueHosts.size() / hostCount;
        
        // Calculate inter-host communication penalty
        double penalty = 0.0;
        Map<Integer, Integer> hostVmCount = new HashMap<>();
        
        for (int host : position) {
            hostVmCount.put(host, hostVmCount.getOrDefault(host, 0) + 1);
        }
        
        // Penalty for spreading VMs across many hosts
        for (Map.Entry<Integer, Integer> entry : hostVmCount.entrySet()) {
            if (entry.getValue() == 1) { // Single VM on host increases communication cost
                penalty += 0.1;
            }
        }
        
        return Math.min(1.0, communicationOverhead + penalty);
    }
    
    /**
     * Validate solution and repair if necessary
     */
    public void validateAndRepair() {
        constraintViolations = 0;
        
        // Check host bounds
        for (int vm = 0; vm < vmCount; vm++) {
            if (position[vm] < 0 || position[vm] >= hostCount) {
                position[vm] = vm % hostCount; // Repair
                constraintViolations++;
            }
        }
        
        // Check resource constraints (simplified)
        int[] hostLoads = new int[hostCount];
        for (int host : position) {
            hostLoads[host]++;
        }
        
        // Repair overloaded hosts
        for (int host = 0; host < hostCount; host++) {
            if (hostLoads[host] > 15) { // Maximum capacity per host
                // Find VMs on this host and redistribute
                List<Integer> vmsOnHost = new ArrayList<>();
                for (int vm = 0; vm < vmCount; vm++) {
                    if (position[vm] == host) {
                        vmsOnHost.add(vm);
                    }
                }
                
                // Redistribute excess VMs
                int excess = hostLoads[host] - 15;
                for (int i = 0; i < excess && i < vmsOnHost.size(); i++) {
                    int vmToMove = vmsOnHost.get(i);
                    // Find least loaded host
                    int targetHost = findLeastLoadedHost();
                    position[vmToMove] = targetHost;
                    constraintViolations++;
                }
            }
        }
        
        isValid = constraintViolations == 0;
        updateSolutionQuality();
    }
    
    /**
     * Find least loaded host
     */
    private int findLeastLoadedHost() {
        int[] hostLoads = new int[hostCount];
        for (int host : position) {
            hostLoads[host]++;
        }
        
        int leastLoadedHost = 0;
        int minLoad = hostLoads[0];
        
        for (int host = 1; host < hostCount; host++) {
            if (hostLoads[host] < minLoad) {
                minLoad = hostLoads[host];
                leastLoadedHost = host;
            }
        }
        
        return leastLoadedHost;
    }
    
    /**
     * Update overall solution quality
     */
    private void updateSolutionQuality() {
        if (!isValid) {
            solutionQuality = 0.0;
            return;
        }
        
        // Calculate quality based on multiple factors
        double resourceScore = detailedMetrics.getOrDefault("resource_utilization", 0.0);
        double balanceScore = detailedMetrics.getOrDefault("load_balance", 0.0);
        double powerScore = detailedMetrics.getOrDefault("power_efficiency", 0.0);
        double slaScore = detailedMetrics.getOrDefault("sla_compliance", 0.0);
        double commScore = 1.0 - detailedMetrics.getOrDefault("communication_cost", 1.0);
        
        solutionQuality = (resourceScore + balanceScore + powerScore + slaScore + commScore) / 5.0;
    }
    
    /**
     * Calculate diversity measure compared to other solution
     * @param other Other hippopotamus for comparison
     * @return Diversity measure (0-1, higher means more diverse)
     */
    public double calculateDiversityMeasure(Hippopotamus other) {
        if (other == null || other.position.length != this.position.length) {
            return 1.0; // Maximum diversity
        }
        
        int differences = 0;
        for (int i = 0; i < position.length; i++) {
            if (position[i] != other.position[i]) {
                differences++;
            }
        }
        
        this.diversityMeasure = (double) differences / position.length;
        return this.diversityMeasure;
    }
    
    /**
     * Check Pareto dominance relationship
     * @param other Other solution to compare against
     * @return true if this solution dominates the other
     */
    public boolean dominates(Hippopotamus other) {
        boolean atLeastOneBetter = false;
        
        for (int i = 0; i < objectiveFitness.length; i++) {
            if (objectiveFitness[i] < other.objectiveFitness[i]) {
                return false; // This is worse in at least one objective
            }
            if (objectiveFitness[i] > other.objectiveFitness[i]) {
                atLeastOneBetter = true;
            }
        }
        
        return atLeastOneBetter;
    }
    
    /**
     * Get comprehensive solution metrics for research analysis
     * @return Map of detailed metrics
     */
    public Map<String, Object> getDetailedMetrics() {
        Map<String, Object> metrics = new HashMap<>(detailedMetrics);
        
        // Add solution characteristics
        metrics.put("is_valid", isValid);
        metrics.put("constraint_violations", constraintViolations);
        metrics.put("solution_quality", solutionQuality);
        metrics.put("diversity_measure", diversityMeasure);
        metrics.put("evaluation_count", evaluationCount);
        metrics.put("generation_created", generationCreated);
        metrics.put("is_elite", isElite);
        
        // Add fitness statistics
        if (!fitnessHistory.isEmpty()) {
            DescriptiveStatistics stats = new DescriptiveStatistics();
            fitnessHistory.forEach(stats::addValue);
            
            metrics.put("fitness_mean", stats.getMean());
            metrics.put("fitness_std", stats.getStandardDeviation());
            metrics.put("fitness_min", stats.getMin());
            metrics.put("fitness_max", stats.getMax());
            metrics.put("fitness_range", stats.getMax() - stats.getMin());
        }
        
        // Add host distribution statistics
        int[] hostLoads = new int[hostCount];
        for (int host : position) {
            hostLoads[host]++;
        }
        
        DescriptiveStatistics hostStats = new DescriptiveStatistics();
        for (int load : hostLoads) {
            hostStats.addValue(load);
        }
        
        metrics.put("host_load_mean", hostStats.getMean());
        metrics.put("host_load_std", hostStats.getStandardDeviation());
        metrics.put("active_hosts", Arrays.stream(hostLoads).filter(load -> load > 0).count());
        metrics.put("max_host_load", Arrays.stream(hostLoads).max().orElse(0));
        
        return metrics;
    }
    
    // Getters and Setters
    public int[] getPosition() {
        return Arrays.copyOf(position, position.length);
    }
    
    public void setPosition(int[] position) {
        if (position.length != vmCount) {
            throw new IllegalArgumentException("Position array length must equal VM count");
        }
        this.position = Arrays.copyOf(position, position.length);
        validateAndRepair();
    }
    
    public double getFitness() {
        return fitness;
    }
    
    public void setFitness(double fitness) {
        this.fitness = fitness;
        fitnessHistory.add(fitness);
        
        // Limit history size for memory efficiency
        if (fitnessHistory.size() > 1000) {
            fitnessHistory.remove(0);
        }
    }
    
    public boolean isValid() {
        return isValid;
    }
    
    public int getConstraintViolations() {
        return constraintViolations;
    }
    
    public double getSolutionQuality() {
        return solutionQuality;
    }
    
    public long getEvaluationCount() {
        return evaluationCount;
    }
    
    public double getDiversityMeasure() {
        return diversityMeasure;
    }
    
    public List<Double> getFitnessHistory() {
        return new ArrayList<>(fitnessHistory);
    }
    
    public int getVmCount() {
        return vmCount;
    }
    
    public int getHostCount() {
        return hostCount;
    }
    
    public void setGenerationCreated(int generation) {
        this.generationCreated = generation;
    }
    
    public int getGenerationCreated() {
        return generationCreated;
    }
    
    public void setElite(boolean isElite) {
        this.isElite = isElite;
    }
    
    public boolean isElite() {
        return isElite;
    }
    
    public double[] getObjectiveFitness() {
        return Arrays.copyOf(objectiveFitness, objectiveFitness.length);
    }
    
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
    
    public Map<String, Object> getAllMetadata() {
        return new HashMap<>(metadata);
    }
    
    @Override
    public String toString() {
        return String.format("Hippopotamus{fitness=%.6f, valid=%s, violations=%d, quality=%.3f, evaluations=%d}", 
                           fitness, isValid, constraintViolations, solutionQuality, evaluationCount);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Hippopotamus other = (Hippopotamus) obj;
        return Arrays.equals(position, other.position);
    }
    
    @Override
    public int hashCode() {
        return Arrays.hashCode(position);
    }
    
    /**
     * Create a deep copy of this hippopotamus
     * @return Deep copy of this hippopotamus
     */
    public Hippopotamus deepCopy() {
        return new Hippopotamus(this);
    }
}