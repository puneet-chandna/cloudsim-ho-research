package org.cloudbus.cloudsim.baseline;

import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicyAbstract;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Particle Swarm Optimization (PSO) algorithm for VM placement optimization.
 * This class implements PSO as a baseline comparison algorithm for the research framework.
 * 
 * Research Context:
 * - Serves as a baseline metaheuristic algorithm for comparison with Hippopotamus Optimization
 * - Implements multi-objective optimization for resource utilization and power consumption
 * - Provides detailed metrics for statistical analysis and research publication
 * 
 * Statistical Metrics Calculated:
 * - Resource utilization efficiency
 * - Power consumption optimization
 * - Convergence behavior analysis
 * - Solution diversity metrics
 * 
 * @author Puneet Chandna
 * @version 1.0
 */
public class ParticleSwarmVmAllocation extends VmAllocationPolicyAbstract {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ParticleSwarmVmAllocation.class);
    
    // PSO Parameters
    private static final int SWARM_SIZE = 30;
    private static final int MAX_ITERATIONS = 100;
    private static final double INERTIA_WEIGHT = 0.7;
    private static final double COGNITIVE_COEFFICIENT = 1.5;
    private static final double SOCIAL_COEFFICIENT = 1.5;
    private static final double CONVERGENCE_THRESHOLD = 1e-6;
    
    // Research metrics tracking
    private List<Double> convergenceHistory;
    private List<Double> diversityHistory;
    private long optimizationTime;
    private int convergenceIteration;
    private double finalBestFitness;
    private Map<String, Double> detailedMetrics;
    
    // PSO algorithm components
    private List<Particle> swarm;
    private Particle globalBest;
    private Random random;
    
    /**
     * Constructor for ParticleSwarmVmAllocation
     */
    public ParticleSwarmVmAllocation() {
        super();
        this.convergenceHistory = new ArrayList<>();
        this.diversityHistory = new ArrayList<>();
        this.detailedMetrics = new HashMap<>();
        this.swarm = new ArrayList<>();
        this.random = new Random(System.currentTimeMillis());
        
        LOGGER.info("ParticleSwarmVmAllocation initialized with swarm size: {}, max iterations: {}", 
                   SWARM_SIZE, MAX_ITERATIONS);
    }
    
    /**
     * Default implementation for finding host for VM
     * 
     * @param vm the VM to be allocated
     * @return Optional containing the selected host, or empty if allocation fails
     */
    @Override
    public Optional<Host> defaultFindHostForVm(Vm vm) {
        List<Host> hostList = getHostList();
        if (hostList.isEmpty()) {
            LOGGER.warn("No hosts available for VM allocation");
            return Optional.empty();
        }
        
        long startTime = System.currentTimeMillis();
        
        // Initialize swarm if not already done
        if (swarm.isEmpty()) {
            initializeSwarm(hostList);
        }
        
        // Run PSO optimization
        Host selectedHost = optimizeVmPlacement(vm, hostList);
        
        this.optimizationTime = System.currentTimeMillis() - startTime;
        
        if (selectedHost != null && selectedHost.isSuitableForVm(vm)) {
            LOGGER.debug("PSO selected host {} for VM {}", selectedHost.getId(), vm.getId());
            return Optional.of(selectedHost);
        }
        
        LOGGER.warn("PSO failed to find suitable host for VM {}", vm.getId());
        return Optional.empty();
    }
    
    /**
     * Allocate host for VM using PSO optimization.
     * This method implements the main PSO algorithm for VM placement.
     * 
     * @param vm the VM to be allocated
     * @param hostList the list of available hosts
     * @return the selected host or Host.NULL if allocation fails
     */
    public Optional<Host> findHostForVm(Vm vm, List<Host> hostList) {
        if (hostList.isEmpty()) {
            LOGGER.warn("No hosts available for VM allocation");
            return Optional.empty();
        }
        
        long startTime = System.currentTimeMillis();
        
        // Initialize swarm if not already done
        if (swarm.isEmpty()) {
            initializeSwarm(hostList);
        }
        
        // Run PSO optimization
        Host selectedHost = optimizeVmPlacement(vm, hostList);
        
        this.optimizationTime = System.currentTimeMillis() - startTime;
        
        if (selectedHost != null && selectedHost.isSuitableForVm(vm)) {
            LOGGER.debug("PSO selected host {} for VM {}", selectedHost.getId(), vm.getId());
            return Optional.of(selectedHost);
        }
        
        LOGGER.warn("PSO failed to find suitable host for VM {}", vm.getId());
        return Optional.empty();
    }
    
    /**
     * Initialize the particle swarm for optimization.
     * Each particle represents a potential VM-to-Host mapping solution.
     * 
     * @param hostList the list of available hosts
     */
    private void initializeSwarm(List<Host> hostList) {
        swarm.clear();
        
        for (int i = 0; i < SWARM_SIZE; i++) {
            Particle particle = new Particle(hostList.size());
            
            // Initialize position randomly
            for (int j = 0; j < hostList.size(); j++) {
                particle.position[j] = random.nextDouble();
            }
            
            // Initialize velocity
            for (int j = 0; j < hostList.size(); j++) {
                particle.velocity[j] = (random.nextDouble() - 0.5) * 2;
            }
            
            // Set initial personal best
            particle.personalBest = particle.position.clone();
            particle.personalBestFitness = Double.POSITIVE_INFINITY;
            
            swarm.add(particle);
        }
        
        this.globalBest = new Particle(hostList.size());
        this.globalBest.fitness = Double.POSITIVE_INFINITY;
        
        LOGGER.debug("Initialized PSO swarm with {} particles", SWARM_SIZE);
    }
    
    /**
     * Optimize VM placement using PSO algorithm.
     * Implements the main PSO optimization loop with convergence tracking.
     * 
     * @param vm the VM to be placed
     * @param hostList the list of available hosts
     * @return the optimal host for VM placement
     */
    private Host optimizeVmPlacement(Vm vm, List<Host> hostList) {
        convergenceHistory.clear();
        diversityHistory.clear();
        
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            // Evaluate fitness for all particles
            evaluateSwarmFitness(vm, hostList);
            
            // Update global best
            updateGlobalBest();
            
            // Update velocities and positions
            updateVelocities();
            updatePositions();
            
            // Track convergence and diversity
            trackConvergenceMetrics(iteration);
            
            // Check convergence
            if (hasConverged(iteration)) {
                this.convergenceIteration = iteration;
                LOGGER.debug("PSO converged at iteration {}", iteration);
                break;
            }
        }
        
        // Select host based on global best solution
        return selectHostFromSolution(hostList, globalBest);
    }
    
    /**
     * Update velocities of all particles in the swarm.
     * Implements PSO velocity update equation with inertia, cognitive, and social components.
     */
    public void updateVelocities() {
        for (Particle particle : swarm) {
            for (int i = 0; i < particle.velocity.length; i++) {
                double inertiaComponent = INERTIA_WEIGHT * particle.velocity[i];
                double cognitiveComponent = COGNITIVE_COEFFICIENT * random.nextDouble() * 
                                         (particle.personalBest[i] - particle.position[i]);
                double socialComponent = SOCIAL_COEFFICIENT * random.nextDouble() * 
                                       (globalBest.position[i] - particle.position[i]);
                
                particle.velocity[i] = inertiaComponent + cognitiveComponent + socialComponent;
                
                // Velocity clamping
                particle.velocity[i] = Math.max(-2.0, Math.min(2.0, particle.velocity[i]));
            }
        }
    }
    
    /**
     * Update positions of all particles in the swarm.
     * Implements PSO position update with boundary constraints.
     */
    public void updatePositions() {
        for (Particle particle : swarm) {
            for (int i = 0; i < particle.position.length; i++) {
                particle.position[i] += particle.velocity[i];
                
                // Position boundary constraints
                particle.position[i] = Math.max(0.0, Math.min(1.0, particle.position[i]));
            }
        }
    }
    
    /**
     * Update global and local best positions.
     * Tracks the best solutions found by the swarm and individual particles.
     */
    public void updateBestPositions() {
        for (Particle particle : swarm) {
            if (particle.fitness < particle.personalBestFitness) {
                particle.personalBest = particle.position.clone();
                particle.personalBestFitness = particle.fitness;
            }
            
            if (particle.fitness < globalBest.fitness) {
                globalBest.position = particle.position.clone();
                globalBest.fitness = particle.fitness;
            }
        }
    }
    
    /**
     * Evaluate fitness for all particles in the swarm.
     * Calculates multi-objective fitness considering resource utilization and power consumption.
     * 
     * @param vm the VM to be placed
     * @param hostList the list of available hosts
     */
    private void evaluateSwarmFitness(Vm vm, List<Host> hostList) {
        for (Particle particle : swarm) {
            particle.fitness = calculateFitness(particle, vm, hostList);
        }
    }
    
    /**
     * Calculate fitness for a particle solution.
     * Implements multi-objective fitness function for research analysis.
     * 
     * @param particle the particle to evaluate
     * @param vm the VM to be placed
     * @param hostList the list of available hosts
     * @return the fitness value (lower is better)
     */
    private double calculateFitness(Particle particle, Vm vm, List<Host> hostList) {
        // Convert particle position to host selection
        Host selectedHost = selectHostFromParticle(particle, hostList);
        
        if (selectedHost == null || !selectedHost.isSuitableForVm(vm)) {
            return Double.POSITIVE_INFINITY; // Infeasible solution
        }
        
        // Multi-objective fitness calculation
        double resourceUtilization = calculateResourceUtilizationFitness(selectedHost, vm);
        double powerConsumption = calculatePowerConsumptionFitness(selectedHost, vm);
        double loadBalancing = calculateLoadBalancingFitness(selectedHost, hostList);
        
        // Weighted combination of objectives
        return 0.4 * resourceUtilization + 0.4 * powerConsumption + 0.2 * loadBalancing;
    }
    
    /**
     * Calculate resource utilization fitness component.
     * 
     * @param host the host to evaluate
     * @param vm the VM to be placed
     * @return resource utilization fitness value
     */
    private double calculateResourceUtilizationFitness(Host host, Vm vm) {
        double cpuUtilization = (host.getCpuPercentUtilization() * host.getTotalMipsCapacity() + 
                               vm.getTotalMipsCapacity()) / host.getTotalMipsCapacity();
        double ramUtilization = (host.getRamUtilization() + vm.getRam().getCapacity()) / 
                              host.getRam().getCapacity();
        
        // Penalize both under-utilization and over-utilization
        double cpuFitness = Math.abs(cpuUtilization - 0.8); // Target 80% utilization
        double ramFitness = Math.abs(ramUtilization - 0.8);
        
        return (cpuFitness + ramFitness) / 2.0;
    }
    
    /**
     * Calculate power consumption fitness component.
     * 
     * @param host the host to evaluate
     * @param vm the VM to be placed
     * @return power consumption fitness value
     */
    private double calculatePowerConsumptionFitness(Host host, Vm vm) {
        double projectedUtilization = (host.getCpuPercentUtilization() * host.getTotalMipsCapacity() + 
                                     vm.getTotalMipsCapacity()) / host.getTotalMipsCapacity();
        double projectedPower = host.getPowerModel().getPower(projectedUtilization);
        
        // Use a reasonable maximum power value for normalization
        double maxPower = 1000.0; // Default maximum power in watts
        return projectedPower / maxPower; // Normalized power consumption
    }
    
    /**
     * Calculate load balancing fitness component.
     * 
     * @param host the host to evaluate
     * @param hostList the list of all hosts
     * @return load balancing fitness value
     */
    private double calculateLoadBalancingFitness(Host host, List<Host> hostList) {
        double avgUtilization = hostList.stream()
                                      .mapToDouble(Host::getCpuPercentUtilization)
                                      .average()
                                      .orElse(0.0);
        
        return Math.abs(host.getCpuPercentUtilization() - avgUtilization);
    }
    
    /**
     * Select host from particle position.
     * 
     * @param particle the particle solution
     * @param hostList the list of available hosts
     * @return the selected host
     */
    private Host selectHostFromParticle(Particle particle, List<Host> hostList) {
        if (hostList.isEmpty()) return null;
        
        // Find the host with maximum position value
        int maxIndex = 0;
        double maxValue = particle.position[0];
        
        for (int i = 1; i < Math.min(particle.position.length, hostList.size()); i++) {
            if (particle.position[i] > maxValue) {
                maxValue = particle.position[i];
                maxIndex = i;
            }
        }
        
        return hostList.get(maxIndex);
    }
    
    /**
     * Update global best solution.
     */
    private void updateGlobalBest() {
        for (Particle particle : swarm) {
            if (particle.fitness < globalBest.fitness) {
                globalBest.position = particle.position.clone();
                globalBest.fitness = particle.fitness;
            }
        }
        
        this.finalBestFitness = globalBest.fitness;
    }
    
    /**
     * Track convergence and diversity metrics for research analysis.
     * 
     * @param iteration current iteration number
     */
    private void trackConvergenceMetrics(int iteration) {
        convergenceHistory.add(globalBest.fitness);
        
        // Calculate swarm diversity
        double diversity = calculateSwarmDiversity();
        diversityHistory.add(diversity);
        
        // Update detailed metrics
        detailedMetrics.put("iteration_" + iteration + "_best_fitness", globalBest.fitness);
        detailedMetrics.put("iteration_" + iteration + "_diversity", diversity);
    }
    
    /**
     * Calculate swarm diversity metric.
     * 
     * @return diversity measure of the swarm
     */
    private double calculateSwarmDiversity() {
        if (swarm.size() < 2) return 0.0;
        
        double totalDistance = 0.0;
        int comparisons = 0;
        
        for (int i = 0; i < swarm.size(); i++) {
            for (int j = i + 1; j < swarm.size(); j++) {
                double distance = euclideanDistance(swarm.get(i).position, swarm.get(j).position);
                totalDistance += distance;
                comparisons++;
            }
        }
        
        return comparisons > 0 ? totalDistance / (double) comparisons : 0.0;
    }
    
    /**
     * Calculate Euclidean distance between two positions.
     * 
     * @param pos1 first position
     * @param pos2 second position
     * @return Euclidean distance
     */
    private double euclideanDistance(double[] pos1, double[] pos2) {
        double sum = 0.0;
        for (int i = 0; i < Math.min(pos1.length, pos2.length); i++) {
            sum += Math.pow(pos1[i] - pos2[i], 2);
        }
        return Math.sqrt(sum);
    }
    
    /**
     * Check if PSO has converged.
     * 
     * @param iteration current iteration
     * @return true if converged, false otherwise
     */
    private boolean hasConverged(int iteration) {
        if (iteration < 10) return false; // Minimum iterations
        
        // Check if improvement is below threshold for last 5 iterations
        if (convergenceHistory.size() >= 5) {
            double recentImprovement = convergenceHistory.get(convergenceHistory.size() - 5) - 
                                     convergenceHistory.get(convergenceHistory.size() - 1);
            return Math.abs(recentImprovement) < CONVERGENCE_THRESHOLD;
        }
        
        return false;
    }
    
    /**
     * Select final host from the best solution found.
     * 
     * @param hostList the list of available hosts
     * @param bestSolution the best solution found by PSO
     * @return the selected host
     */
    private Host selectHostFromSolution(List<Host> hostList, Particle bestSolution) {
        if (bestSolution == null || bestSolution.position == null) {
            return null;
        }
        
        return selectHostFromParticle(bestSolution, hostList);
    }
    
    /**
     * Get detailed metrics for research analysis.
     * This method provides comprehensive metrics for statistical analysis and comparison.
     * 
     * @return map of detailed performance metrics
     */
    public Map<String, Double> getDetailedMetrics() {
        Map<String, Double> metrics = new HashMap<>(detailedMetrics);
        
        // Add summary metrics
        metrics.put("optimization_time_ms", (double) optimizationTime);
        metrics.put("convergence_iteration", (double) convergenceIteration);
        metrics.put("final_best_fitness", finalBestFitness);
        metrics.put("total_iterations", (double) convergenceHistory.size());
        
        // Add convergence statistics
        if (!convergenceHistory.isEmpty()) {
            metrics.put("initial_fitness", convergenceHistory.get(0));
            metrics.put("fitness_improvement", convergenceHistory.get(0) - finalBestFitness);
            metrics.put("convergence_rate", calculateConvergenceRate());
        }
        
        // Add diversity statistics
        if (!diversityHistory.isEmpty()) {
            metrics.put("initial_diversity", diversityHistory.get(0));
            metrics.put("final_diversity", diversityHistory.get(diversityHistory.size() - 1));
            metrics.put("avg_diversity", diversityHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
        }
        
        return metrics;
    }
    
    /**
     * Calculate convergence rate for analysis.
     * 
     * @return convergence rate metric
     */
    private double calculateConvergenceRate() {
        if (convergenceHistory.size() < 2) return 0.0;
        
        double initialFitness = convergenceHistory.get(0);
        double finalFitness = convergenceHistory.get(convergenceHistory.size() - 1);
        
        return (initialFitness - finalFitness) / convergenceHistory.size();
    }
    
    /**
     * Get convergence history for research analysis.
     * 
     * @return list of fitness values over iterations
     */
    public List<Double> getConvergenceHistory() {
        return new ArrayList<>(convergenceHistory);
    }
    
    /**
     * Get diversity history for research analysis.
     * 
     * @return list of diversity values over iterations
     */
    public List<Double> getDiversityHistory() {
        return new ArrayList<>(diversityHistory);
    }
    
    /**
     * Inner class representing a particle in the PSO swarm.
     */
    private static class Particle {
        double[] position;
        double[] velocity;
        double[] personalBest;
        double fitness;
        double personalBestFitness;
        
        public Particle(int dimensions) {
            this.position = new double[dimensions];
            this.velocity = new double[dimensions];
            this.personalBest = new double[dimensions];
            this.fitness = Double.POSITIVE_INFINITY;
            this.personalBestFitness = Double.POSITIVE_INFINITY;
        }
    }
}