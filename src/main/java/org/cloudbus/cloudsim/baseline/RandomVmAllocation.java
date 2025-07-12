package org.cloudbus.cloudsim.baseline;

import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicy;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicyAbstract;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random VM Allocation Policy for baseline comparison in CloudSim research.
 * This class implements a simple random allocation strategy where VMs are
 * assigned to hosts randomly while respecting resource constraints.
 * 
 * This serves as a baseline algorithm for comparison with the Hippopotamus
 * Optimization algorithm and other sophisticated placement strategies.
 * 
 * Research Objectives Addressed:
 * - Provides baseline performance metrics for comparison
 * - Demonstrates resource constraint validation
 * - Supports statistical analysis of random placement effects
 * 
 * Metrics Calculated:
 * - Resource utilization patterns
 * - Fragmentation levels
 * - SLA violation rates
 * - Power consumption (basic)
 * 
 * @author Puneet Chandna
 * @version 1.0
 */
public class RandomVmAllocation extends VmAllocationPolicyAbstract {
    
    private static final Logger logger = LoggerFactory.getLogger(RandomVmAllocation.class);
    
    /** Random number generator for reproducible experiments */
    private final Random random;
    
    /** Seed for reproducible random allocation */
    private final long seed;
    
    /** Maximum allocation attempts before failure */
    private static final int MAX_ALLOCATION_ATTEMPTS = 100;
    
    /** Detailed allocation metrics for research analysis */
    private final AllocationMetrics metrics;
    
    /** Track allocation failures for analysis */
    private int allocationFailures = 0;
    
    /** Track successful allocations */
    private int successfulAllocations = 0;
    
    /** Track resource fragmentation */
    private double totalFragmentation = 0.0;
    
    /**
     * Creates a new Random VM Allocation Policy with system-generated seed.
     */
    public RandomVmAllocation() {
        this(System.currentTimeMillis());
    }
    
    /**
     * Creates a new Random VM Allocation Policy with specified seed.
     * 
     * @param seed The seed for random number generation (for reproducibility)
     */
    public RandomVmAllocation(long seed) {
        super();
        this.seed = seed;
        this.random = new Random(seed);
        this.metrics = new AllocationMetrics();
        logger.info("RandomVmAllocation initialized with seed: {}", seed);
    }
    
    /**
     * Default implementation for finding host for VM
     * 
     * @param vm the VM to be allocated
     * @return Optional containing the selected host, or empty if allocation fails
     */
    @Override
    public Optional<Host> defaultFindHostForVm(Vm vm) {
        if (vm == null) {
            logger.warn("Attempted to allocate null VM");
            return Optional.empty();
        }
        
        long startTime = System.nanoTime();
        List<Host> hostList = getHostList();
        
        if (hostList.isEmpty()) {
            logger.warn("No hosts available for VM allocation");
            allocationFailures++;
            return Optional.empty();
        }
        
        // Filter hosts that can accommodate the VM
        List<Host> suitableHosts = new ArrayList<>();
        for (Host host : hostList) {
            if (host.isSuitableForVm(vm)) {
                suitableHosts.add(host);
            }
        }
        
        if (suitableHosts.isEmpty()) {
            logger.debug("No suitable hosts found for VM {} (MIPS: {}, RAM: {}, BW: {})", 
                        vm.getId(), vm.getMips(), vm.getRam(), vm.getBw());
            allocationFailures++;
            return Optional.empty();
        }
        
        // Random selection from suitable hosts
        Host selectedHost = randomAllocation(suitableHosts);
        
        // Ensure constraints are met
        if (selectedHost != null && ensureConstraints(vm, selectedHost)) {
            long endTime = System.nanoTime();
            double allocationTime = (endTime - startTime) / 1_000_000.0; // Convert to milliseconds
            
            // Update metrics
            updateAllocationMetrics(vm, selectedHost, allocationTime);
            successfulAllocations++;
            
            logger.debug("VM {} allocated to Host {} (Time: {:.2f}ms)", 
                        vm.getId(), selectedHost.getId(), allocationTime);
            
            return Optional.of(selectedHost);
        }
        
        allocationFailures++;
        logger.debug("Failed to allocate VM {} after constraint validation", vm.getId());
        return Optional.empty();
    }
    
    /**
     * Finds a host for a VM using random selection strategy.
     * This method implements the core random allocation logic while
     * ensuring resource constraints are respected.
     * 
     * @param vm The VM to be allocated
     * @return Optional containing the selected host, or empty if allocation fails
     */
    
    /**
     * Performs random allocation from the list of suitable hosts.
     * Implements the core random selection logic with attempt limiting.
     * 
     * @param suitableHosts List of hosts that can potentially accommodate the VM
     * @return Selected host or null if allocation fails
     */
    private Host randomAllocation(List<Host> suitableHosts) {
        if (suitableHosts.isEmpty()) {
            return null;
        }
        
        int attempts = 0;
        while (attempts < MAX_ALLOCATION_ATTEMPTS) {
            int randomIndex = random.nextInt(suitableHosts.size());
            Host candidateHost = suitableHosts.get(randomIndex);
            
            if (candidateHost.isActive()) {
                return candidateHost;
            }
            
            attempts++;
        }
        
        // If all attempts failed, return the first suitable host
        return suitableHosts.get(0);
    }
    
    /**
     * Ensures resource constraints are satisfied for the VM-Host pair.
     * This method validates that the selected host can actually accommodate
     * the VM without violating resource limits.
     * 
     * @param vm The VM to be allocated
     * @param host The selected host
     * @return true if constraints are satisfied, false otherwise
     */
    private boolean ensureConstraints(Vm vm, Host host) {
        if (vm == null || host == null) {
            return false;
        }
        
        // Check CPU constraints
        double requiredMips = vm.getMips();
        double availableMips = host.getTotalMipsCapacity() - host.getCpuPercentUtilization() * host.getTotalMipsCapacity();
        if (requiredMips > availableMips) {
            logger.trace("CPU constraint violation: Required {} MIPS, Available {} MIPS", 
                        requiredMips, availableMips);
            return false;
        }
        
        // Check RAM constraints
        long requiredRam = vm.getRam().getCapacity();
        long availableRam = host.getRam().getAvailableResource();
        if (requiredRam > availableRam) {
            logger.trace("RAM constraint violation: Required {} MB, Available {} MB", 
                        requiredRam, availableRam);
            return false;
        }
        
        // Check Bandwidth constraints
        long requiredBw = vm.getBw().getCapacity();
        long availableBw = host.getBw().getAvailableResource();
        if (requiredBw > availableBw) {
            logger.trace("Bandwidth constraint violation: Required {} Mbps, Available {} Mbps", 
                        requiredBw, availableBw);
            return false;
        }
        
        // Check Storage constraints
        long requiredStorage = vm.getStorage().getCapacity();
        long availableStorage = host.getStorage().getAvailableResource();
        if (requiredStorage > availableStorage) {
            logger.trace("Storage constraint violation: Required {} MB, Available {} MB", 
                        requiredStorage, availableStorage);
            return false;
        }
        
        return true;
    }
    
    /**
     * Updates allocation metrics for research analysis.
     * This method tracks various performance indicators that are used
     * for statistical analysis and comparison with other algorithms.
     * 
     * @param vm The allocated VM
     * @param host The selected host
     * @param allocationTime Time taken for allocation in milliseconds
     */
    private void updateAllocationMetrics(Vm vm, Host host, double allocationTime) {
        metrics.recordAllocation(vm, host, allocationTime);
        
        // Calculate fragmentation
        double hostUtilization = calculateHostUtilization(host);
        double fragmentation = 1.0 - hostUtilization;
        totalFragmentation += fragmentation;
        
        // Update utilization statistics
        metrics.updateUtilizationStats(hostUtilization);
        
        // Record resource usage patterns
        metrics.recordResourceUsage(vm, host);
    }
    
    /**
     * Calculates current utilization of a host.
     * Used for fragmentation analysis and resource efficiency metrics.
     * 
     * @param host The host to analyze
     * @return Utilization ratio (0.0 to 1.0)
     */
    private double calculateHostUtilization(Host host) {
        if (host == null) return 0.0;
        
        double cpuUtilization = host.getCpuPercentUtilization();
        double ramUtilization = 1.0 - ((double) host.getRam().getAvailableResource() / host.getRam().getCapacity());
        double bwUtilization = 1.0 - ((double) host.getBw().getAvailableResource() / host.getBw().getCapacity());
        double storageUtilization = 1.0 - ((double) host.getStorage().getAvailableResource() / host.getStorage().getCapacity());
        
        // Return average utilization across all resources
        return (cpuUtilization + ramUtilization + bwUtilization + storageUtilization) / 4.0;
    }
    
    /**
     * Gets detailed metrics for research analysis.
     * This method provides comprehensive performance data for comparison
     * with other allocation algorithms.
     * 
     * @return Detailed allocation metrics
     */
    public DetailedMetrics getDetailedMetrics() {
        DetailedMetrics detailed = new DetailedMetrics();
        
        // Basic allocation statistics
        detailed.setTotalAllocations(successfulAllocations);
        detailed.setFailedAllocations(allocationFailures);
        detailed.setSuccessRate(calculateSuccessRate());
        
        // Performance metrics
        detailed.setAverageAllocationTime(metrics.getAverageAllocationTime());
        detailed.setMaxAllocationTime(metrics.getMaxAllocationTime());
        detailed.setMinAllocationTime(metrics.getMinAllocationTime());
        
        // Resource utilization metrics
        detailed.setAverageUtilization(metrics.getAverageUtilization());
        detailed.setMaxUtilization(metrics.getMaxUtilization());
        detailed.setMinUtilization(metrics.getMinUtilization());
        
        // Fragmentation metrics
        detailed.setAverageFragmentation(calculateAverageFragmentation());
        detailed.setTotalFragmentation(totalFragmentation);
        
        // Additional research metrics
        detailed.setResourceDistribution(metrics.getResourceDistribution());
        detailed.setAllocationPattern(metrics.getAllocationPattern());
        detailed.setSeed(seed);
        
        return detailed;
    }
    
    /**
     * Calculates allocation success rate.
     * 
     * @return Success rate as percentage (0.0 to 100.0)
     */
    private double calculateSuccessRate() {
        int totalAttempts = successfulAllocations + allocationFailures;
        if (totalAttempts == 0) return 0.0;
        return (double) successfulAllocations / totalAttempts * 100.0;
    }
    
    /**
     * Calculates average fragmentation across all allocations.
     * 
     * @return Average fragmentation ratio
     */
    private double calculateAverageFragmentation() {
        if (successfulAllocations == 0) return 0.0;
        return totalFragmentation / successfulAllocations;
    }
    
    /**
     * Resets all metrics and counters.
     * Used for multiple experiment runs with the same policy instance.
     */
    public void resetMetrics() {
        allocationFailures = 0;
        successfulAllocations = 0;
        totalFragmentation = 0.0;
        metrics.reset();
        logger.info("RandomVmAllocation metrics reset");
    }
    
    /**
     * Gets the seed used for random number generation.
     * Important for experiment reproducibility.
     * 
     * @return The random seed
     */
    public long getSeed() {
        return seed;
    }
    
    /**
     * Inner class to track allocation metrics for research analysis.
     */
    private static class AllocationMetrics {
        private final List<Double> allocationTimes = new ArrayList<>();
        private final List<Double> utilizationRatios = new ArrayList<>();
        private final Map<String, Integer> resourceDistribution = new HashMap<>();
        private final List<String> allocationPattern = new ArrayList<>();
        
        public void recordAllocation(Vm vm, Host host, double allocationTime) {
            allocationTimes.add(allocationTime);
            allocationPattern.add(String.format("VM_%d->Host_%d", vm.getId(), host.getId()));
        }
        
        public void updateUtilizationStats(double utilization) {
            utilizationRatios.add(utilization);
        }
        
        public void recordResourceUsage(Vm vm, Host host) {
            String resourceKey = String.format("MIPS_%d_RAM_%d", 
                                              (int) vm.getMips(), (int) vm.getRam().getCapacity());
            resourceDistribution.merge(resourceKey, 1, Integer::sum);
        }
        
        public double getAverageAllocationTime() {
            return allocationTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
        
        public double getMaxAllocationTime() {
            return allocationTimes.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        }
        
        public double getMinAllocationTime() {
            return allocationTimes.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        }
        
        public double getAverageUtilization() {
            return utilizationRatios.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
        
        public double getMaxUtilization() {
            return utilizationRatios.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        }
        
        public double getMinUtilization() {
            return utilizationRatios.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        }
        
        public Map<String, Integer> getResourceDistribution() {
            return new HashMap<>(resourceDistribution);
        }
        
        public List<String> getAllocationPattern() {
            return new ArrayList<>(allocationPattern);
        }
        
        public void reset() {
            allocationTimes.clear();
            utilizationRatios.clear();
            resourceDistribution.clear();
            allocationPattern.clear();
        }
    }
    
    /**
     * Detailed metrics class for research analysis and comparison.
     */
    public static class DetailedMetrics {
        private int totalAllocations;
        private int failedAllocations;
        private double successRate;
        private double averageAllocationTime;
        private double maxAllocationTime;
        private double minAllocationTime;
        private double averageUtilization;
        private double maxUtilization;
        private double minUtilization;
        private double averageFragmentation;
        private double totalFragmentation;
        private Map<String, Integer> resourceDistribution;
        private List<String> allocationPattern;
        private long seed;
        
        // Getters and setters
        public int getTotalAllocations() { return totalAllocations; }
        public void setTotalAllocations(int totalAllocations) { this.totalAllocations = totalAllocations; }
        
        public int getFailedAllocations() { return failedAllocations; }
        public void setFailedAllocations(int failedAllocations) { this.failedAllocations = failedAllocations; }
        
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
        
        public double getAverageAllocationTime() { return averageAllocationTime; }
        public void setAverageAllocationTime(double averageAllocationTime) { this.averageAllocationTime = averageAllocationTime; }
        
        public double getMaxAllocationTime() { return maxAllocationTime; }
        public void setMaxAllocationTime(double maxAllocationTime) { this.maxAllocationTime = maxAllocationTime; }
        
        public double getMinAllocationTime() { return minAllocationTime; }
        public void setMinAllocationTime(double minAllocationTime) { this.minAllocationTime = minAllocationTime; }
        
        public double getAverageUtilization() { return averageUtilization; }
        public void setAverageUtilization(double averageUtilization) { this.averageUtilization = averageUtilization; }
        
        public double getMaxUtilization() { return maxUtilization; }
        public void setMaxUtilization(double maxUtilization) { this.maxUtilization = maxUtilization; }
        
        public double getMinUtilization() { return minUtilization; }
        public void setMinUtilization(double minUtilization) { this.minUtilization = minUtilization; }
        
        public double getAverageFragmentation() { return averageFragmentation; }
        public void setAverageFragmentation(double averageFragmentation) { this.averageFragmentation = averageFragmentation; }
        
        public double getTotalFragmentation() { return totalFragmentation; }
        public void setTotalFragmentation(double totalFragmentation) { this.totalFragmentation = totalFragmentation; }
        
        public Map<String, Integer> getResourceDistribution() { return resourceDistribution; }
        public void setResourceDistribution(Map<String, Integer> resourceDistribution) { this.resourceDistribution = resourceDistribution; }
        
        public List<String> getAllocationPattern() { return allocationPattern; }
        public void setAllocationPattern(List<String> allocationPattern) { this.allocationPattern = allocationPattern; }
        
        public long getSeed() { return seed; }
        public void setSeed(long seed) { this.seed = seed; }
        
        @Override
        public String toString() {
            return String.format(
                "RandomVmAllocation Metrics:\n" +
                "  Total Allocations: %d\n" +
                "  Failed Allocations: %d\n" +
                "  Success Rate: %.2f%%\n" +
                "  Average Allocation Time: %.2f ms\n" +
                "  Average Utilization: %.2f%%\n" +
                "  Average Fragmentation: %.2f%%\n" +
                "  Seed: %d",
                totalAllocations, failedAllocations, successRate,
                averageAllocationTime, averageUtilization * 100,
                averageFragmentation * 100, seed
            );
        }
    }
}