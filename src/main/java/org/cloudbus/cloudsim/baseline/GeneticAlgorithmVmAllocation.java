package org.cloudbus.cloudsim.baseline;

import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicyAbstract;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.util.ExperimentException;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.MetricsCalculator;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

/**
 * Genetic Algorithm implementation for VM placement optimization.
 * 
 * This class implements a genetic algorithm approach to solve the VM placement
 * problem in cloud computing environments. It uses evolutionary principles
 * including selection, crossover, and mutation to find optimal VM-to-Host
 * mappings that minimize resource wastage and maximize performance.
 * 
 * Research Objectives Addressed:
 * - Baseline algorithm comparison for HO algorithm
 * - Multi-objective optimization capability assessment
 * - Performance benchmarking for evolutionary algorithms
 * 
 * Metrics Calculated:
 * - Resource utilization efficiency
 * - Load balancing metrics
 * - Convergence characteristics
 * - Solution diversity measures
 * 
 * Integration with Research Pipeline:
 * - Implements VmAllocationPolicy for CloudSim integration
 * - Provides detailed metrics for comparative analysis
 * - Supports parameter sensitivity analysis
 * 
 * Statistical Methods:
 * - Population fitness statistics
 * - Convergence rate analysis
 * - Diversity preservation metrics
 * 
 * Publication Formatting:
 * - Structured results for comparative tables
 * - Performance metrics formatted for research papers
 * - Statistical significance data collection
 * 
 * Dataset Support:
 * - Scales with Google trace dataset characteristics
 * - Handles Azure trace workload patterns
 * - Supports synthetic workload generation
 * 
 * @author Puneet Chandna
 * @version 1.0
 */
public class GeneticAlgorithmVmAllocation extends VmAllocationPolicyAbstract {
    
    // GA Parameters
    private int populationSize;
    private int maxGenerations;
    private double mutationRate;
    private double crossoverRate;
    private int elitismCount;
    private Random random;
    
    // Research Tracking
    private List<Double> convergenceHistory;
    private List<Double> diversityHistory;
    private Map<String, Double> performanceMetrics;
    private long startTime;
    private long endTime;
    
    // Algorithm State
    private List<Individual> population;
    private Individual bestSolution;
    private int currentGeneration;
    
    // Logging manager instance
    private final LoggingManager loggingManager = new LoggingManager();
    
    /**
     * Constructor with default GA parameters optimized for VM placement.
     */
    public GeneticAlgorithmVmAllocation() {
        this(50, 100, 0.05, 0.8, 2, System.currentTimeMillis());
    }
    
    /**
     * Constructor with configurable GA parameters.
     *
     * @param populationSize Size of the GA population
     * @param maxGenerations Maximum number of generations
     * @param mutationRate Probability of mutation (0.0 to 1.0)
     * @param crossoverRate Probability of crossover (0.0 to 1.0)
     * @param elitismCount Number of elite solutions to preserve
     * @param seed Random seed for reproducibility
     */
    public GeneticAlgorithmVmAllocation(int populationSize, int maxGenerations, 
                                      double mutationRate, double crossoverRate, 
                                      int elitismCount, long seed) {
        super();
        this.populationSize = populationSize;
        this.maxGenerations = maxGenerations;
        this.mutationRate = mutationRate;
        this.crossoverRate = crossoverRate;
        this.elitismCount = elitismCount;
        this.random = new Random(seed);
        
        // Initialize research tracking
        this.convergenceHistory = new ArrayList<>();
        this.diversityHistory = new ArrayList<>();
        this.performanceMetrics = new HashMap<>();
        this.population = new ArrayList<>();
        this.currentGeneration = 0;
    }

    @Override
    public Optional<Host> defaultFindHostForVm(Vm vm) {
        List<Host> hostList = getHostList();
        return findHostForVm(vm, hostList);
    }
    
    public Optional<Host> findHostForVm(Vm vm, List<Host> hostList) {
        try {
            List<Host> suitableHosts = hostList.stream()
                .filter(host -> host.isSuitableForVm(vm))
                .collect(Collectors.toList());
                
            if (suitableHosts.isEmpty()) {
                return Optional.empty();
            }
            
            // For single VM allocation, use simple best fit
            Host selectedHost = suitableHosts.stream()
                .min(Comparator.comparingDouble(host -> 
                    calculateHostUtilization(host, vm)))
                .orElse(null);
                
            if (selectedHost != null && selectedHost.isSuitableForVm(vm)) {
                vm.setHost(selectedHost);
                return Optional.of(selectedHost);
            }
            
            return Optional.empty();
        } catch (Exception e) {
            throw new ExperimentException("GA VM allocation failed for VM " + vm.getId(), e);
        }
    }

    @Override
    public void deallocateHostForVm(Vm vm) {
        Host host = vm.getHost();
        if (host != null && host != Host.NULL) {
            vm.setHost(Host.NULL);
        }
    }

    /**
     * Main GA optimization method for batch VM allocation.
     * 
     * @param vmList List of VMs to be allocated
     * @return Map of VM to Host allocations
     */
    public Map<Vm, Host> evolvePopulation(List<Vm> vmList) {
        try {
            startTime = System.currentTimeMillis();
            loggingManager.logInfo("Starting GA evolution for " + vmList.size() + " VMs");
            
            // Initialize population
            initializePopulation(vmList);
            
            // Evolution loop
            for (currentGeneration = 0; currentGeneration < maxGenerations; currentGeneration++) {
                // Evaluate fitness
                evaluatePopulation(vmList);
                
                // Track convergence and diversity
                trackProgress();
                
                // Check convergence
                if (hasConverged()) {
                    loggingManager.logInfo("GA converged at generation " + currentGeneration);
                    break;
                }
                
                // Create new population
                List<Individual> newPopulation = new ArrayList<>();
                
                // Elitism - preserve best solutions
                preserveElites(newPopulation);
                
                // Generate offspring
                while (newPopulation.size() < populationSize) {
                    Individual parent1 = selectParent();
                    Individual parent2 = selectParent();
                    
                    Individual[] offspring = crossover(parent1, parent2);
                    
                    mutation(offspring[0]);
                    mutation(offspring[1]);
                    
                    newPopulation.add(offspring[0]);
                    if (newPopulation.size() < populationSize) {
                        newPopulation.add(offspring[1]);
                    }
                }
                
                population = newPopulation;
            }
            
            endTime = System.currentTimeMillis();
            
            // Return best solution as allocation map
            return convertToAllocationMap(bestSolution, vmList);
            
        } catch (Exception e) {
            throw new ExperimentException("GA evolution failed", e);
        }
    }
    
    /**
     * Initialize the population with random solutions.
     *
     * @param vmList List of VMs to allocate
     */
    private void initializePopulation(List<Vm> vmList) {
        population.clear();
        List<Host> availableHosts = getHostList();
        
        for (int i = 0; i < populationSize; i++) {
            Individual individual = new Individual(vmList.size());
            
            // Create random allocation
            for (int j = 0; j < vmList.size(); j++) {
                final int vmIndex = j;
                List<Host> suitableHosts = availableHosts.stream()
                    .filter(host -> host.isSuitableForVm(vmList.get(vmIndex)))
                    .collect(Collectors.toList());
                    
                if (!suitableHosts.isEmpty()) {
                    int randomIndex = random.nextInt(suitableHosts.size());
                    individual.allocation[vmIndex] = availableHosts.indexOf(suitableHosts.get(randomIndex));
                } else {
                    individual.allocation[vmIndex] = -1; // No suitable host
                }
            }
            
            population.add(individual);
        }
        
        loggingManager.logInfo("Initialized GA population with " + populationSize + " individuals");
    }
    
    /**
     * Evaluate fitness for all individuals in the population.
     *
     * @param vmList List of VMs being allocated
     */
    private void evaluatePopulation(List<Vm> vmList) {
        for (Individual individual : population) {
            individual.fitness = evaluateFitness(individual, vmList);
        }
        
        // Update best solution
        Individual currentBest = Collections.max(population, 
            Comparator.comparingDouble(ind -> ind.fitness));
            
        if (bestSolution == null || currentBest.fitness > bestSolution.fitness) {
            bestSolution = new Individual(currentBest);
        }
    }
    
    /**
     * Evaluate fitness of an individual solution.
     *
     * @param individual The individual to evaluate
     * @param vmList List of VMs being allocated
     * @return Fitness value (higher is better)
     */
    private double evaluateFitness(Individual individual, List<Vm> vmList) {
        double fitness = 0.0;
        List<Host> hosts = getHostList();
        
        // Calculate resource utilization efficiency
        double totalUtilization = 0.0;
        double utilizationVariance = 0.0;
        List<Double> hostUtilizations = new ArrayList<>();
        
        for (Host host : hosts) {
            double hostUtilization = calculateHostUtilizationForAllocation(host, individual, vmList);
            hostUtilizations.add(hostUtilization);
            totalUtilization += hostUtilization;
        }
        
        // Calculate load balancing (lower variance is better)
        double meanUtilization = totalUtilization / hosts.size();
        for (double util : hostUtilizations) {
            utilizationVariance += Math.pow(util - meanUtilization, 2);
        }
        utilizationVariance /= hosts.size();
        
        // Fitness combines high utilization with good load balancing
        fitness = meanUtilization * 100 - Math.sqrt(utilizationVariance) * 10;
        
        // Penalty for invalid allocations
        int invalidAllocations = 0;
        for (int i = 0; i < individual.allocation.length; i++) {
            if (individual.allocation[i] == -1 || !isValidAllocation(vmList.get(i), hosts.get(individual.allocation[i]))) {
                invalidAllocations++;
            }
        }
        
        fitness -= invalidAllocations * 50; // Heavy penalty for invalid allocations
        
        return Math.max(0, fitness); // Ensure non-negative fitness
    }
    
    /**
     * Tournament selection for parent selection.
     *
     * @return Selected parent individual
     */
    private Individual selectParent() {
        int tournamentSize = Math.max(2, populationSize / 10);
        Individual best = null;
        
        for (int i = 0; i < tournamentSize; i++) {
            Individual candidate = population.get(random.nextInt(population.size()));
            if (best == null || candidate.fitness > best.fitness) {
                best = candidate;
            }
        }
        
        return best;
    }
    
    /**
     * Single-point crossover operation.
     *
     * @param parent1 First parent
     * @param parent2 Second parent
     * @return Array of two offspring
     */
    private Individual[] crossover(Individual parent1, Individual parent2) {
        Individual[] offspring = new Individual[2];
        offspring[0] = new Individual(parent1.allocation.length);
        offspring[1] = new Individual(parent2.allocation.length);
        
        if (random.nextDouble() < crossoverRate) {
            int crossoverPoint = random.nextInt(parent1.allocation.length);
            
            // Create offspring through crossover
            for (int i = 0; i < parent1.allocation.length; i++) {
                if (i < crossoverPoint) {
                    offspring[0].allocation[i] = parent1.allocation[i];
                    offspring[1].allocation[i] = parent2.allocation[i];
                } else {
                    offspring[0].allocation[i] = parent2.allocation[i];
                    offspring[1].allocation[i] = parent1.allocation[i];
                }
            }
        } else {
            // No crossover, copy parents
            System.arraycopy(parent1.allocation, 0, offspring[0].allocation, 0, parent1.allocation.length);
            System.arraycopy(parent2.allocation, 0, offspring[1].allocation, 0, parent2.allocation.length);
        }
        
        return offspring;
    }
    
    /**
     * Mutation operation on an individual.
     *
     * @param individual The individual to mutate
     */
    private void mutation(Individual individual) {
        for (int i = 0; i < individual.allocation.length; i++) {
            if (random.nextDouble() < mutationRate) {
                List<Host> availableHosts = getHostList();
                List<Host> suitableHosts = availableHosts.stream()
                    .filter(host -> host.isSuitableForVm(createDummyVm()))
                    .collect(Collectors.toList());
                    
                if (!suitableHosts.isEmpty()) {
                    int randomIndex = random.nextInt(suitableHosts.size());
                    individual.allocation[i] = availableHosts.indexOf(suitableHosts.get(randomIndex));
                }
            }
        }
    }
    
    /**
     * Preserve elite solutions in the new population.
     *
     * @param newPopulation The new population being built
     */
    private void preserveElites(List<Individual> newPopulation) {
        List<Individual> sortedPopulation = new ArrayList<>(population);
        sortedPopulation.sort(Comparator.comparingDouble(ind -> ind.fitness));
        Collections.reverse(sortedPopulation);
        
        for (int i = 0; i < elitismCount && i < sortedPopulation.size(); i++) {
            newPopulation.add(new Individual(sortedPopulation.get(i)));
        }
    }
    
    /**
     * Track convergence and diversity metrics.
     */
    private void trackProgress() {
        // Track convergence
        if (bestSolution != null) {
            convergenceHistory.add(bestSolution.fitness);
        }
        
        // Track diversity
        double diversity = calculatePopulationDiversity();
        diversityHistory.add(diversity);
        
        // Log progress every 10 generations
        if (currentGeneration % 10 == 0) {
            loggingManager.logInfo("Generation " + currentGeneration + 
                                 ": Best Fitness = " + (bestSolution != null ? bestSolution.fitness : 0) +
                                 ", Diversity = " + String.format("%.4f", diversity));
        }
    }
    
    /**
     * Check if the population has converged.
     *
     * @return True if converged, false otherwise
     */
    private boolean hasConverged() {
        if (convergenceHistory.size() < 10) {
            return false;
        }
        
        // Check if fitness hasn't improved in last 10 generations
        double recentBest = convergenceHistory.get(convergenceHistory.size() - 1);
        double tenGenerationsAgo = convergenceHistory.get(convergenceHistory.size() - 10);
        
        return Math.abs(recentBest - tenGenerationsAgo) < 0.001;
    }
    
    /**
     * Calculate population diversity using Hamming distance.
     *
     * @return Diversity measure
     */
    private double calculatePopulationDiversity() {
        if (population.size() < 2) {
            return 0.0;
        }
        
        double totalDistance = 0.0;
        int comparisons = 0;
        
        for (int i = 0; i < population.size(); i++) {
            for (int j = i + 1; j < population.size(); j++) {
                totalDistance += hammingDistance(population.get(i), population.get(j));
                comparisons++;
            }
        }
        
        return comparisons > 0 ? totalDistance / comparisons : 0.0;
    }
    
    /**
     * Calculate Hamming distance between two individuals.
     *
     * @param ind1 First individual
     * @param ind2 Second individual
     * @return Hamming distance
     */
    private double hammingDistance(Individual ind1, Individual ind2) {
        int distance = 0;
        for (int i = 0; i < ind1.allocation.length; i++) {
            if (ind1.allocation[i] != ind2.allocation[i]) {
                distance++;
            }
        }
        return (double) distance / ind1.allocation.length;
    }
    
    /**
     * Convert best solution to VM-to-Host allocation map.
     *
     * @param solution The best solution found
     * @param vmList List of VMs
     * @return Allocation map
     */
    private Map<Vm, Host> convertToAllocationMap(Individual solution, List<Vm> vmList) {
        Map<Vm, Host> allocationMap = new HashMap<>();
        List<Host> hosts = getHostList();
        
        for (int i = 0; i < solution.allocation.length && i < vmList.size(); i++) {
            if (solution.allocation[i] >= 0 && solution.allocation[i] < hosts.size()) {
                allocationMap.put(vmList.get(i), hosts.get(solution.allocation[i]));
            }
        }
        
        return allocationMap;
    }
    
    /**
     * Calculate host utilization for a specific allocation.
     *
     * @param host Host to evaluate
     * @param individual The individual solution
     * @param vmList List of VMs
     * @return Utilization percentage
     */
    private double calculateHostUtilizationForAllocation(Host host, Individual individual, List<Vm> vmList) {
        double cpuUsage = 0.0;
        double ramUsage = 0.0;
        
        for (int i = 0; i < individual.allocation.length && i < vmList.size(); i++) {
            if (individual.allocation[i] == getHostList().indexOf(host)) {
                Vm vm = vmList.get(i);
                cpuUsage += vm.getNumberOfPes() * vm.getMips();
                ramUsage += vm.getRam().getCapacity();
            }
        }
        
        double cpuUtil = cpuUsage / (host.getNumberOfPes() * host.getCpuPercentUtilization());
        double ramUtil = ramUsage / host.getRam().getCapacity();
        
        return Math.max(cpuUtil, ramUtil) * 100; // Return percentage
    }
    
    /**
     * Calculate host utilization with additional VM.
     *
     * @param host Host to evaluate
     * @param vm VM to add
     * @return Utilization percentage
     */
    private double calculateHostUtilization(Host host, Vm vm) {
        double cpuUtil = (host.getCpuPercentUtilization() * host.getNumberOfPes() + vm.getNumberOfPes()) 
                        / (double) host.getNumberOfPes();
        double ramUtil = (host.getRam().getAllocatedResource() + vm.getRam().getCapacity()) 
                        / host.getRam().getCapacity();
        
        return Math.max(cpuUtil, ramUtil) * 100;
    }
    
    /**
     * Check if VM allocation to host is valid.
     *
     * @param vm VM to allocate
     * @param host Target host
     * @return True if valid allocation
     */
    private boolean isValidAllocation(Vm vm, Host host) {
        return host.isSuitableForVm(vm);
    }
    
    /**
     * Create a dummy VM for validation purposes.
     *
     * @return Dummy VM instance
     */
    private Vm createDummyVm() {
        // Create a simple VM with basic requirements for validation
        Vm dummyVm = new org.cloudbus.cloudsim.vms.VmSimple(0, 1000, 1);
        return dummyVm;
    }
    
    /**
     * Get detailed performance metrics for research analysis.
     *
     * @return Map containing detailed metrics
     */
    public Map<String, Double> getDetailedMetrics() {
        Map<String, Double> metrics = new HashMap<>();
        
        // Basic performance metrics
        metrics.put("totalOptimizationTime", (double) (endTime - startTime));
        metrics.put("finalBestFitness", bestSolution != null ? bestSolution.fitness : 0.0);
        metrics.put("convergenceGeneration", (double) currentGeneration);
        metrics.put("finalPopulationDiversity", diversityHistory.isEmpty() ? 0.0 : diversityHistory.get(diversityHistory.size() - 1));
        
        // Resource utilization metrics
        metrics.put("averageResourceUtilization", calculateResourceUtilization());
        metrics.put("loadBalanceMetric", calculateLoadBalanceMetric());
        
        // Algorithm-specific metrics
        metrics.put("populationSize", (double) populationSize);
        metrics.put("mutationRate", mutationRate);
        metrics.put("crossoverRate", crossoverRate);
        metrics.put("elitismCount", (double) elitismCount);
        
        return metrics;
    }
    
    /**
     * Calculate average resource utilization across all hosts.
     *
     * @return Average utilization percentage
     */
    private double calculateResourceUtilization() {
        List<Host> hosts = getHostList();
        if (hosts.isEmpty()) {
            return 0.0;
        }
        
        return hosts.stream()
            .mapToDouble(Host::getCpuPercentUtilization)
            .average()
            .orElse(0.0);
    }
    
    /**
     * Calculate load balancing metric.
     *
     * @return Load balance measure
     */
    private double calculateLoadBalanceMetric() {
        List<Host> hosts = getHostList();
        if (hosts.size() < 2) {
            return 1.0; // Perfect balance for single host
        }
        
        List<Double> utilizations = hosts.stream()
            .map(Host::getCpuPercentUtilization)
            .collect(Collectors.toList());
        
        double mean = utilizations.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = utilizations.stream()
            .mapToDouble(util -> Math.pow(util - mean, 2))
            .average().orElse(0.0);
        
        return 1.0 / (1.0 + Math.sqrt(variance)); // Higher value = better balance
    }
    
    /**
     * Get convergence history for analysis.
     *
     * @return List of fitness values over generations
     */
    public List<Double> getConvergenceHistory() {
        return new ArrayList<>(convergenceHistory);
    }
    
    /**
     * Get diversity history for analysis.
     *
     * @return List of diversity values over generations
     */
    public List<Double> getDiversityHistory() {
        return new ArrayList<>(diversityHistory);
    }
    
    /**
     * Individual solution representation for GA.
     */
    private static class Individual {
        int[] allocation; // allocation[i] = host index for VM i
        double fitness;
        
        Individual(int vmCount) {
            this.allocation = new int[vmCount];
            this.fitness = 0.0;
        }
        
        Individual(Individual other) {
            this.allocation = Arrays.copyOf(other.allocation, other.allocation.length);
            this.fitness = other.fitness;
        }
    }
}