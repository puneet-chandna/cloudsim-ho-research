package org.cloudbus.cloudsim.baseline;

import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicyAbstract;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.util.MetricsCalculator;
import org.cloudbus.cloudsim.util.ExperimentException;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.ValidationUtils;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Ant Colony Optimization (ACO) algorithm for VM placement optimization.
 * This class implements ACO-based VM allocation as a baseline algorithm for 
 * comparison with the Hippopotamus Optimization algorithm.
 * 
 * Research Focus: Provides ACO baseline for comparative analysis
 * Metrics Calculated: Resource utilization, placement efficiency, convergence behavior
 * Statistical Methods: Population-based optimization metrics, diversity measures
 * Publication Format: ACO performance benchmarks and comparison tables
 * 
 * @author Puneet Chandna
 * @version 1.0
 */
public class AntColonyVmAllocation extends VmAllocationPolicyAbstract {
    
    // ACO Algorithm Parameters
    private static final double DEFAULT_ALPHA = 1.0;           // Pheromone importance
    private static final double DEFAULT_BETA = 2.0;            // Heuristic importance
    private static final double DEFAULT_RHO = 0.1;             // Evaporation rate
    private static final double DEFAULT_Q = 100.0;             // Pheromone deposit constant
    private static final int DEFAULT_MAX_ITERATIONS = 100;     // Maximum iterations
    private static final int DEFAULT_ANT_COUNT = 20;           // Number of ants
    private static final double DEFAULT_INITIAL_PHEROMONE = 0.1; // Initial pheromone level
    
    // Algorithm parameters
    private double alpha;          // Pheromone importance factor
    private double beta;           // Heuristic importance factor
    private double rho;            // Evaporation rate
    private double qValue;         // Pheromone deposit constant
    private int maxIterations;     // Maximum number of iterations
    private int antCount;          // Number of ants in colony
    private double initialPheromone; // Initial pheromone level
    
    // ACO state variables
    private double[][] pheromoneMatrix;     // Pheromone levels between VMs and Hosts
    private List<Vm> vmList;               // List of VMs to be allocated
    private List<Host> hostList;           // List of available hosts
    private Random random;                 // Random number generator
    private Map<String, Double> metrics;   // Performance metrics
    private List<Double> convergenceHistory; // Convergence tracking
    
    // Research-specific tracking
    private long startTime;
    private long endTime;
    private int evaluationCount;
    private double bestFitness;
    private Map<Vm, Host> bestSolution;
    
    private final LoggingManager loggingManager = new LoggingManager();
    
    /**
     * Default constructor with standard ACO parameters
     */
    public AntColonyVmAllocation() {
        this(DEFAULT_ALPHA, DEFAULT_BETA, DEFAULT_RHO, DEFAULT_Q, 
             DEFAULT_MAX_ITERATIONS, DEFAULT_ANT_COUNT, DEFAULT_INITIAL_PHEROMONE);
    }
    
    /**
     * Constructor with custom ACO parameters
     * 
     * @param alpha Pheromone importance factor
     * @param beta Heuristic importance factor  
     * @param rho Evaporation rate
     * @param Q Pheromone deposit constant
     * @param maxIterations Maximum iterations
     * @param antCount Number of ants
     * @param initialPheromone Initial pheromone level
     */
    public AntColonyVmAllocation(double alpha, double beta, double rho, double Q,
                                int maxIterations, int antCount, double initialPheromone) {
        super();
        this.alpha = alpha;
        this.beta = beta;
        this.rho = rho;
        this.qValue = Q;
        this.maxIterations = maxIterations;
        this.antCount = antCount;
        this.initialPheromone = initialPheromone;
        
        this.random = new Random(System.currentTimeMillis());
        this.metrics = new HashMap<>();
        this.convergenceHistory = new ArrayList<>();
        this.evaluationCount = 0;
        this.bestFitness = Double.MAX_VALUE;
        this.bestSolution = new HashMap<>();
        
        loggingManager.logInfo("ACO VM Allocation initialized with parameters: alpha={}, beta={}, rho={}, maxIter={}", alpha, beta, rho, maxIterations);
    }
    
    /**
     * Allocate a host for a VM using ACO algorithm
     * 
     * @param vm The VM to allocate
     * @param hostList List of available hosts
     * @return Selected host or empty optional if allocation fails
     */
    public Optional<Host> findHostForVm(Vm vm, List<Host> hostList) {
        try {
            if (hostList.isEmpty()) {
                loggingManager.logWarning("No hosts available for VM allocation");
                return Optional.empty();
            }
            
            // For single VM allocation, use probabilistic selection
            Host selectedHost = selectNextHost(vm, hostList);
            
            if (selectedHost != null && selectedHost.isSuitableForVm(vm)) {
                loggingManager.logInfo("ACO allocated VM {} to Host {}", vm.getId(), selectedHost.getId());
                return Optional.of(selectedHost);
            }
            
            loggingManager.logWarning("ACO failed to find suitable host for VM {}", vm.getId());
            return Optional.empty();
            
        } catch (Exception e) {
            throw new ExperimentException("Error in ACO VM allocation: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Host> defaultFindHostForVm(Vm vm) {
        return findHostForVm(vm, getHostList());
    }
    
    /**
     * Optimize VM allocation using complete ACO algorithm
     * 
     * @param vmList List of VMs to allocate
     * @param hostList List of available hosts
     * @return Optimized VM-to-Host mapping
     */
    public Map<Vm, Host> optimizeAllocation(List<Vm> vmList, List<Host> hostList) {
        try {
            startTime = System.currentTimeMillis();
            this.vmList = new ArrayList<>(vmList);
            this.hostList = new ArrayList<>(hostList);
            
            loggingManager.logInfo("Starting ACO optimization for {} VMs and {} hosts", vmList.size(), hostList.size());
            
            // Initialize pheromone matrix
            initializePheromoneMatrix();
            
            Map<Vm, Host> bestGlobalSolution = new HashMap<>();
            double bestGlobalFitness = Double.MAX_VALUE;
            
            // ACO main loop
            for (int iteration = 0; iteration < maxIterations; iteration++) {
                List<Map<Vm, Host>> antSolutions = new ArrayList<>();
                List<Double> antFitnesses = new ArrayList<>();
                
                // Each ant constructs a solution
                for (int ant = 0; ant < antCount; ant++) {
                    Map<Vm, Host> antSolution = constructSolution();
                    double fitness = evaluateSolution(antSolution);
                    
                    antSolutions.add(antSolution);
                    antFitnesses.add(fitness);
                    
                    // Update best solution
                    if (fitness < bestGlobalFitness) {
                        bestGlobalFitness = fitness;
                        bestGlobalSolution = new HashMap<>(antSolution);
                    }
                }
                
                // Update pheromones
                updatePheromones(antSolutions, antFitnesses);
                
                // Track convergence
                convergenceHistory.add(bestGlobalFitness);
                
                // Log progress
                if (iteration % 10 == 0) {
                    loggingManager.logInfo("ACO Iteration {}, Best Fitness: {}", iteration, String.format("%.4f", bestGlobalFitness));
                }
            }
            
            endTime = System.currentTimeMillis();
            bestFitness = bestGlobalFitness;
            bestSolution = bestGlobalSolution;
            
            calculateMetrics(bestGlobalSolution);
            
            loggingManager.logInfo("ACO optimization completed. Best fitness: {}, Time: {}ms", String.format("%.4f", bestGlobalFitness), (endTime - startTime));
            
            return bestGlobalSolution;
            
        } catch (Exception e) {
            throw new ExperimentException("Error in ACO optimization: " + e.getMessage(), e);
        }
    }
    
    /**
     * Probabilistic host selection based on pheromone and heuristic information
     * 
     * @param vm VM to allocate
     * @param availableHosts List of available hosts
     * @return Selected host
     */
    public Host selectNextHost(Vm vm, List<Host> availableHosts) {
        try {
            if (availableHosts.isEmpty()) {
                return null;
            }
            
            List<Host> suitableHosts = availableHosts.stream()
                .filter(host -> host.isSuitableForVm(vm))
                .collect(Collectors.toList());
            
            if (suitableHosts.isEmpty()) {
                return null;
            }
            
            // Calculate probabilities for each suitable host
            double[] probabilities = new double[suitableHosts.size()];
            double totalProbability = 0.0;
            
            for (int i = 0; i < suitableHosts.size(); i++) {
                Host host = suitableHosts.get(i);
                
                // Get pheromone level (if matrix initialized)
                double pheromone = getPheromoneLevel(vm, host);
                
                // Calculate heuristic value (based on resource utilization)
                double heuristic = calculateHeuristic(vm, host);
                
                // Calculate probability
                probabilities[i] = Math.pow(pheromone, alpha) * Math.pow(heuristic, beta);
                totalProbability += probabilities[i];
            }
            
            // Normalize probabilities
            if (totalProbability > 0) {
                for (int i = 0; i < probabilities.length; i++) {
                    probabilities[i] /= totalProbability;
                }
            } else {
                // Equal probabilities if no valid calculation
                Arrays.fill(probabilities, 1.0 / probabilities.length);
            }
            
            // Roulette wheel selection
            double randomValue = random.nextDouble();
            double cumulativeProbability = 0.0;
            
            for (int i = 0; i < probabilities.length; i++) {
                cumulativeProbability += probabilities[i];
                if (randomValue <= cumulativeProbability) {
                    return suitableHosts.get(i);
                }
            }
            
            // Return last host if rounding errors occur
            return suitableHosts.get(suitableHosts.size() - 1);
            
        } catch (Exception e) {
            loggingManager.logError("Error in host selection: " + e.getMessage(), e);
            // Return first suitable host as fallback
            return availableHosts.stream()
                .filter(host -> host.isSuitableForVm(vm))
                .findFirst()
                .orElse(null);
        }
    }
    
    /**
     * Update pheromone levels based on ant solutions
     * 
     * @param antSolutions List of solutions from all ants
     * @param antFitnesses List of fitness values for ant solutions
     */
    public void updatePheromones(List<Map<Vm, Host>> antSolutions, List<Double> antFitnesses) {
        try {
            if (pheromoneMatrix == null) {
                return;
            }
            
            // Evaporation
            for (int i = 0; i < pheromoneMatrix.length; i++) {
                for (int j = 0; j < pheromoneMatrix[i].length; j++) {
                    pheromoneMatrix[i][j] *= (1.0 - rho);
                    // Ensure minimum pheromone level
                    pheromoneMatrix[i][j] = Math.max(pheromoneMatrix[i][j], 0.001);
                }
            }
            
            // Pheromone deposit
            for (int ant = 0; ant < antSolutions.size(); ant++) {
                Map<Vm, Host> solution = antSolutions.get(ant);
                double fitness = antFitnesses.get(ant);
                
                // Calculate pheromone amount (higher for better solutions)
                double pheromoneAmount = qValue / (1.0 + fitness);
                
                // Deposit pheromones for this solution
                for (Map.Entry<Vm, Host> entry : solution.entrySet()) {
                    Vm vm = entry.getKey();
                    Host host = entry.getValue();
                    
                    int vmIndex = vmList.indexOf(vm);
                    int hostIndex = hostList.indexOf(host);
                    
                    if (vmIndex >= 0 && hostIndex >= 0 && 
                        vmIndex < pheromoneMatrix.length && 
                        hostIndex < pheromoneMatrix[vmIndex].length) {
                        pheromoneMatrix[vmIndex][hostIndex] += pheromoneAmount;
                    }
                }
            }
            
            // Apply pheromone bounds
            for (int i = 0; i < pheromoneMatrix.length; i++) {
                for (int j = 0; j < pheromoneMatrix[i].length; j++) {
                    pheromoneMatrix[i][j] = Math.min(pheromoneMatrix[i][j], 10.0);
                }
            }
            
        } catch (Exception e) {
            loggingManager.logError("Error updating pheromones: " + e.getMessage(), e);
        }
    }
    
    /**
     * Initialize pheromone matrix with initial values
     */
    private void initializePheromoneMatrix() {
        if (vmList == null || hostList == null || vmList.isEmpty() || hostList.isEmpty()) {
            loggingManager.logWarning("Cannot initialize pheromone matrix: empty VM or host list");
            return;
        }
        
        pheromoneMatrix = new double[vmList.size()][hostList.size()];
        
        for (int i = 0; i < vmList.size(); i++) {
            for (int j = 0; j < hostList.size(); j++) {
                pheromoneMatrix[i][j] = initialPheromone;
            }
        }
        
        loggingManager.logInfo("Pheromone matrix initialized: " + vmList.size() + 
                             "x" + hostList.size());
    }
    
    /**
     * Construct a complete solution using one ant
     * 
     * @return VM-to-Host mapping solution
     */
    private Map<Vm, Host> constructSolution() {
        Map<Vm, Host> solution = new HashMap<>();
        
        // Create a copy of hosts to track availability
        List<Host> availableHosts = new ArrayList<>(hostList);
        
        for (Vm vm : vmList) {
            Host selectedHost = selectNextHost(vm, availableHosts);
            
            if (selectedHost != null) {
                solution.put(vm, selectedHost);
                // Note: In real implementation, we might update host capacity
                // Here we keep it simple for the baseline
            } else {
                // If no suitable host found, use least loaded host
                Host leastLoadedHost = findLeastLoadedHost(vm, availableHosts);
                if (leastLoadedHost != null) {
                    solution.put(vm, leastLoadedHost);
                    loggingManager.logInfo("ACO found least loaded host {} for VM {}", leastLoadedHost.getId(), vm.getId());
                }
            }
        }
        
        return solution;
    }
    
    /**
     * Evaluate the quality of a solution
     * 
     * @param solution VM-to-Host mapping
     * @return Fitness value (lower is better)
     */
    private double evaluateSolution(Map<Vm, Host> solution) {
        evaluationCount++;
        
        if (solution.isEmpty()) {
            return Double.MAX_VALUE;
        }
        
        double fitness = 0.0;
        
        // Calculate resource utilization imbalance
        Map<Host, Double> hostUtilization = new HashMap<>();
        
        for (Map.Entry<Vm, Host> entry : solution.entrySet()) {
            Vm vm = entry.getKey();
            Host host = entry.getValue();
            
            // Calculate utilization for this host
            double utilization = hostUtilization.getOrDefault(host, 0.0);
            utilization += vm.getMips() / host.getTotalMipsCapacity();
            hostUtilization.put(host, utilization);
        }
        
        // Calculate standard deviation of utilization (balance objective)
        double meanUtilization = hostUtilization.values().stream()
                                   .mapToDouble(Double::doubleValue)
                                   .average()
                                   .orElse(0.0);
        
        double utilizationVariance = hostUtilization.values().stream()
                                       .mapToDouble(util -> Math.pow(util - meanUtilization, 2))
                                       .average()
                                       .orElse(0.0);
        
        fitness += Math.sqrt(utilizationVariance);
        
        // Add penalty for overloaded hosts
        for (double utilization : hostUtilization.values()) {
            if (utilization > 1.0) {
                fitness += (utilization - 1.0) * 10.0; // Heavy penalty
            }
        }
        
        // Add penalty for unused hosts (resource waste)
        int unusedHosts = hostList.size() - hostUtilization.size();
        fitness += unusedHosts * 0.1;
        
        return fitness;
    }
    
    /**
     * Calculate heuristic value for VM-Host pair
     * 
     * @param vm Virtual machine
     * @param host Host machine
     * @return Heuristic value (higher is better)
     */
    private double calculateHeuristic(Vm vm, Host host) {
        // Calculate resource fitness
        double cpuRatio = vm.getMips() / host.getTotalMipsCapacity();
        double ramRatio = (double) vm.getRam().getCapacity() / host.getRam().getCapacity();
        double bwRatio = (double) vm.getBw().getCapacity() / host.getBw().getCapacity();
        
        // Prefer hosts that are not overloaded but well utilized
        double utilizationScore = 1.0 - Math.max(cpuRatio, Math.max(ramRatio, bwRatio));
        
        // Ensure positive heuristic value
        return Math.max(utilizationScore, 0.1);
    }
    
    /**
     * Get pheromone level between VM and Host
     * 
     * @param vm Virtual machine
     * @param host Host machine
     * @return Pheromone level
     */
    private double getPheromoneLevel(Vm vm, Host host) {
        if (pheromoneMatrix == null || vmList == null || hostList == null) {
            return initialPheromone;
        }
        
        int vmIndex = vmList.indexOf(vm);
        int hostIndex = hostList.indexOf(host);
        
        if (vmIndex >= 0 && hostIndex >= 0 && 
            vmIndex < pheromoneMatrix.length && 
            hostIndex < pheromoneMatrix[vmIndex].length) {
            return pheromoneMatrix[vmIndex][hostIndex];
        }
        
        return initialPheromone;
    }
    
    /**
     * Find least loaded host that can accommodate the VM
     * 
     * @param vm Virtual machine
     * @param hosts Available hosts
     * @return Least loaded suitable host
     */
    private Host findLeastLoadedHost(Vm vm, List<Host> hosts) {
        return hosts.stream()
            .filter(host -> host.isSuitableForVm(vm))
            .min(Comparator.comparing(host -> 
                host.getVmList().stream()
                    .mapToDouble(allocatedVm -> {
                        Object mips = allocatedVm.getCurrentRequestedMips();
                        if (mips instanceof Collection) {
                            return ((Collection<?>) mips).stream().mapToDouble(val -> ((Number) val).doubleValue()).sum();
                        } else if (mips instanceof Number) {
                            return ((Number) mips).doubleValue();
                        } else {
                            return 0.0;
                        }
                    })
                    .sum()))
            .orElse(null);
    }
    
    /**
     * Calculate comprehensive performance metrics
     * 
     * @param solution Final solution
     */
    private void calculateMetrics(Map<Vm, Host> solution) {
        try {
            // Execution metrics
            metrics.put("execution_time_ms", (double) (endTime - startTime));
            metrics.put("evaluations", (double) evaluationCount);
            metrics.put("iterations", (double) maxIterations);
            metrics.put("best_fitness", bestFitness);
            
            // Solution quality metrics
            if (!solution.isEmpty()) {
                metrics.put("vms_allocated", (double) solution.size());
                metrics.put("hosts_used", (double) solution.values().stream().collect(Collectors.toSet()).size());
                metrics.put("allocation_ratio", (double) solution.size() / vmList.size());
                
                // Resource utilization
                Map<String, Double> utilMetrics = MetricsCalculator.calculateResourceUtilization(new ArrayList<>(solution.values()));
                metrics.put("avg_resource_utilization", utilMetrics.getOrDefault("overall_cpu_utilization", 0.0));
                metrics.put("load_balance", utilMetrics.getOrDefault("std_cpu_utilization", 0.0));
            }
            
            // Convergence metrics
            if (!convergenceHistory.isEmpty()) {
                metrics.put("initial_fitness", convergenceHistory.get(0));
                metrics.put("final_fitness", convergenceHistory.get(convergenceHistory.size() - 1));
                metrics.put("improvement_ratio", 
                    convergenceHistory.get(0) / convergenceHistory.get(convergenceHistory.size() - 1));
            }
            
            loggingManager.logInfo("ACO metrics calculated: " + metrics.size() + " metrics");
            
        } catch (Exception e) {
            loggingManager.logError("Error calculating ACO metrics: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get detailed performance metrics for research analysis
     * 
     * @return Map of performance metrics
     */
    public Map<String, Double> getDetailedMetrics() {
        Map<String, Double> detailedMetrics = new HashMap<>(metrics);
        
        // Add algorithm-specific metrics
        detailedMetrics.put("alpha", alpha);
        detailedMetrics.put("beta", beta);
        detailedMetrics.put("rho", rho);
        detailedMetrics.put("q_value", qValue);
        detailedMetrics.put("ant_count", (double) antCount);
        detailedMetrics.put("max_iterations", (double) maxIterations);
        
        // Add convergence analysis
        if (!convergenceHistory.isEmpty()) {
            detailedMetrics.put("convergence_stability", calculateConvergenceStability());
            detailedMetrics.put("convergence_speed", calculateConvergenceSpeed());
        }
        
        return detailedMetrics;
    }
    
    /**
     * Calculate convergence stability metric
     * 
     * @return Stability measure
     */
    private double calculateConvergenceStability() {
        if (convergenceHistory.size() < 10) {
            return 0.0;
        }
        
        // Calculate variance in last 20% of iterations
        int startIndex = (int) (convergenceHistory.size() * 0.8);
        List<Double> lastValues = convergenceHistory.subList(startIndex, convergenceHistory.size());
        
        double mean = lastValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = lastValues.stream()
            .mapToDouble(val -> Math.pow(val - mean, 2))
            .average()
            .orElse(0.0);
        
        return 1.0 / (1.0 + variance); // Higher value = more stable
    }
    
    /**
     * Calculate convergence speed metric
     * 
     * @return Speed measure
     */
    private double calculateConvergenceSpeed() {
        if (convergenceHistory.size() < 5) {
            return 0.0;
        }
        
        double initialFitness = convergenceHistory.get(0);
        double finalFitness = convergenceHistory.get(convergenceHistory.size() - 1);
        
        // Find iteration where 90% of improvement was achieved
        double targetImprovement = (initialFitness - finalFitness) * 0.9;
        double targetFitness = initialFitness - targetImprovement;
        
        for (int i = 0; i < convergenceHistory.size(); i++) {
            if (convergenceHistory.get(i) <= targetFitness) {
                return (double) i / convergenceHistory.size(); // Earlier convergence = lower value = faster
            }
        }
        
        return 1.0; // Slow convergence
    }
    
    /**
     * Get convergence history for analysis
     * 
     * @return List of fitness values over iterations
     */
    public List<Double> getConvergenceHistory() {
        return new ArrayList<>(convergenceHistory);
    }
    
    /**
     * Get best solution found
     * 
     * @return Best VM-to-Host mapping
     */
    public Map<Vm, Host> getBestSolution() {
        return new HashMap<>(bestSolution);
    }
    
    /**
     * Reset algorithm state for new optimization
     */
    public void reset() {
        pheromoneMatrix = null;
        vmList = null;
        hostList = null;
        metrics.clear();
        convergenceHistory.clear();
        evaluationCount = 0;
        bestFitness = Double.MAX_VALUE;
        bestSolution.clear();
        
        loggingManager.logInfo("ACO algorithm state reset");
    }
    
    // Getter and setter methods for algorithm parameters
    public double getAlpha() { return alpha; }
    public void setAlpha(double alpha) { this.alpha = alpha; }
    
    public double getBeta() { return beta; }
    public void setBeta(double beta) { this.beta = beta; }
    
    public double getRho() { return rho; }
    public void setRho(double rho) { this.rho = rho; }
    
    public double getQ() { return qValue; }
    public void setQ(double Q) { this.qValue = Q; }
    
    public int getMaxIterations() { return maxIterations; }
    public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }
    
    public int getAntCount() { return antCount; }
    public void setAntCount(int antCount) { this.antCount = antCount; }
    
    public double getInitialPheromone() { return initialPheromone; }
    public void setInitialPheromone(double initialPheromone) { this.initialPheromone = initialPheromone; }
}