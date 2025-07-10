package org.cloudbus.cloudsim.baseline;

import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicyAbstract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Best Fit VM Allocation Policy
 * 
 * This class implements the Best Fit algorithm for VM placement, which allocates
 * each VM to the host with the minimum remaining resources that can still accommodate
 * the VM. This serves as a baseline comparison algorithm for the Hippopotamus Optimization research.
 * 
 * Research Objectives Addressed:
 * - Provides improved baseline performance compared to First Fit
 * - Demonstrates resource-aware allocation strategy
 * - Shows trade-off between allocation time and resource utilization
 * 
 * Metrics Calculated:
 * - Resource fragmentation minimization
 * - Host utilization efficiency
 * - Allocation success rate
 * - Resource wastage reduction
 * 
 * @author Puneet Chandna
 * @version 1.0
 */
public class BestFitVmAllocation extends VmAllocationPolicyAbstract {
    private static final Logger LOGGER = LoggerFactory.getLogger(BestFitVmAllocation.class);
    
    // Performance tracking metrics
    private long totalAllocationTime = 0;
    private int successfulAllocations = 0;
    private int failedAllocations = 0;
    private Map<Host, Integer> hostAllocationCount = new HashMap<>();
    private Map<String, Double> detailedMetrics = new HashMap<>();
    
    // Algorithm-specific parameters
    private boolean trackDetailedMetrics;
    private long startTime;
    private ResourceFitnessType fitnessType;
    
    /**
     * Enumeration for different fitness calculation methods
     */
    public enum ResourceFitnessType {
        CPU_ONLY,           // Consider only CPU resources
        MEMORY_ONLY,        // Consider only memory resources
        WEIGHTED_AVERAGE,   // Weighted average of CPU and memory
        EUCLIDEAN_DISTANCE  // Euclidean distance in resource space
    }
    
    /**
     * Constructor for Best Fit VM Allocation Policy with default settings
     */
    public BestFitVmAllocation() {
        super();
        this.trackDetailedMetrics = true;
        this.fitnessType = ResourceFitnessType.WEIGHTED_AVERAGE;
        initializeMetrics();
        LOGGER.info("BestFitVmAllocation policy initialized with default settings");
    }
    
    /**
     * Constructor with custom settings
     * @param trackDetailedMetrics Whether to track detailed performance metrics
     * @param fitnessType Type of fitness calculation to use
     */
    public BestFitVmAllocation(boolean trackDetailedMetrics, ResourceFitnessType fitnessType) {
        super();
        this.trackDetailedMetrics = trackDetailedMetrics;
        this.fitnessType = fitnessType != null ? fitnessType : ResourceFitnessType.WEIGHTED_AVERAGE;
        initializeMetrics();
        LOGGER.info("BestFitVmAllocation policy initialized with tracking: {}, fitness: {}", 
                   trackDetailedMetrics, this.fitnessType);
    }
    
    /**
     * Initialize performance metrics
     */
    private void initializeMetrics() {
        detailedMetrics.put("averageAllocationTime", 0.0);
        detailedMetrics.put("allocationSuccessRate", 0.0);
        detailedMetrics.put("hostUtilizationVariance", 0.0);
        detailedMetrics.put("fragmentationIndex", 0.0);
        detailedMetrics.put("resourceWastage", 0.0);
        detailedMetrics.put("averageFitnessScore", 0.0);
        detailedMetrics.put("resourcePackingEfficiency", 0.0);
    }
    
    /**
     * Allocates a host for a given VM using Best Fit algorithm
     * 
     * @param vm the VM to be allocated
     * @param hostList the list of available hosts
     * @return true if allocation was successful, false otherwise
     */
    @Override
    public boolean allocateHostForVm(Vm vm, List<Host> hostList) {
        if (trackDetailedMetrics) {
            startTime = System.nanoTime();
        }
        
        try {
            // Best Fit Algorithm: Find host with minimum remaining resources that can accommodate VM
            Host bestHost = findBestFitHost(vm, hostList);
            
            if (bestHost != null) {
                boolean allocationResult = allocateHostForVmInternal(vm, bestHost);
                
                if (allocationResult) {
                    recordSuccessfulAllocation(bestHost, vm);
                    updateHostAllocationCount(bestHost);
                    LOGGER.debug("VM {} allocated to Host {} using Best Fit (fitness: {})", 
                               vm.getId(), bestHost.getId(), calculateFitness(vm, bestHost));
                    return true;
                }
            }
            
            // If no suitable host found
            recordFailedAllocation();
            LOGGER.warn("Failed to allocate VM {} using Best Fit - no suitable host found", vm.getId());
            return false;
            
        } catch (Exception e) {
            recordFailedAllocation();
            LOGGER.error("Error during Best Fit allocation for VM {}: {}", vm.getId(), e.getMessage());
            return false;
        } finally {
            if (trackDetailedMetrics) {
                recordAllocationTime(System.nanoTime() - startTime);
            }
        }
    }
    
    /**
     * Find the best fit host for a VM based on the fitness function
     * @param vm the VM to place
     * @param hostList the list of available hosts
     * @return the best host or null if no suitable host found
     */
    private Host findBestFitHost(Vm vm, List<Host> hostList) {
        Host bestHost = null;
        double bestFitness = Double.MAX_VALUE;
        
        for (Host host : hostList) {
            if (host.isSuitableForVm(vm)) {
                double fitness = calculateFitness(vm, host);
                
                if (fitness < bestFitness) {
                    bestFitness = fitness;
                    bestHost = host;
                }
            }
        }
        
        return bestHost;
    }
    
    /**
     * Calculate fitness score for VM-Host pair
     * Lower values indicate better fit
     * @param vm the VM to place
     * @param host the host to evaluate
     * @return fitness score
     */
    private double calculateFitness(Vm vm, Host host) {
        switch (fitnessType) {
            case CPU_ONLY:
                return calculateCpuFitness(vm, host);
            case MEMORY_ONLY:
                return calculateMemoryFitness(vm, host);
            case WEIGHTED_AVERAGE:
                return calculateWeightedFitness(vm, host);
            case EUCLIDEAN_DISTANCE:
                return calculateEuclideanFitness(vm, host);
            default:
                return calculateWeightedFitness(vm, host);
        }
    }
    
    /**
     * Calculate CPU-only fitness
     * @param vm the VM
     * @param host the host
     * @return CPU fitness score
     */
    private double calculateCpuFitness(Vm vm, Host host) {
        double availableCpu = host.getTotalMipsCapacity() - host.getTotalAllocatedMips();
        double requiredCpu = vm.getTotalMipsCapacity();
        
        if (availableCpu < requiredCpu) {
            return Double.MAX_VALUE; // Invalid allocation
        }
        
        return availableCpu - requiredCpu; // Remaining CPU after allocation
    }
    
    /**
     * Calculate memory-only fitness
     * @param vm the VM
     * @param host the host
     * @return memory fitness score
     */
    private double calculateMemoryFitness(Vm vm, Host host) {
        long availableRam = host.getRam().getAvailableResource();
        long requiredRam = vm.getRam().getCapacity();
        
        if (availableRam < requiredRam) {
            return Double.MAX_VALUE; // Invalid allocation
        }
        
        return availableRam - requiredRam; // Remaining RAM after allocation
    }
    
    /**
     * Calculate weighted fitness combining CPU and memory
     * @param vm the VM
     * @param host the host
     * @return weighted fitness score
     */
    private double calculateWeightedFitness(Vm vm, Host host) {
        double cpuWeight = 0.6; // CPU weight
        double memoryWeight = 0.4; // Memory weight
        
        // Normalize CPU fitness
        double cpuFitness = calculateCpuFitness(vm, host);
        if (cpuFitness == Double.MAX_VALUE) {
            return Double.MAX_VALUE;
        }
        double normalizedCpuFitness = cpuFitness / host.getTotalMipsCapacity();
        
        // Normalize memory fitness
        double memoryFitness = calculateMemoryFitness(vm, host);
        if (memoryFitness == Double.MAX_VALUE) {
            return Double.MAX_VALUE;
        }
        double normalizedMemoryFitness = memoryFitness / host.getRam().getCapacity();
        
        return cpuWeight * normalizedCpuFitness + memoryWeight * normalizedMemoryFitness;
    }
    
    /**
     * Calculate Euclidean distance fitness in resource space
     * @param vm the VM
     * @param host the host
     * @return Euclidean fitness score
     */
    private double calculateEuclideanFitness(Vm vm, Host host) {
        double cpuFitness = calculateCpuFitness(vm, host);
        double memoryFitness = calculateMemoryFitness(vm, host);
        
        if (cpuFitness == Double.MAX_VALUE || memoryFitness == Double.MAX_VALUE) {
            return Double.MAX_VALUE;
        }
        
        // Normalize to [0,1] range
        double normalizedCpuFitness = cpuFitness / host.getTotalMipsCapacity();
        double normalizedMemoryFitness = memoryFitness / host.getRam().getCapacity();
        
        return Math.sqrt(normalizedCpuFitness * normalizedCpuFitness + 
                        normalizedMemoryFitness * normalizedMemoryFitness);
    }
    
    /**
     * Internal method to perform the actual allocation
     * @param vm the VM to allocate
     * @param host the host to allocate to
     * @return true if successful, false otherwise
     */
    private boolean allocateHostForVmInternal(Vm vm, Host host) {
        if (host.createVm(vm)) {
            setVmToHost(vm, host);
            return true;
        }
        return false;
    }
    
    /**
     * Record successful allocation metrics
     * @param host the host that was allocated
     * @param vm the VM that was allocated
     */
    private void recordSuccessfulAllocation(Host host, Vm vm) {
        if (trackDetailedMetrics) {
            successfulAllocations++;
            double fitness = calculateFitness(vm, host);
            updateAverageFitnessScore(fitness);
            updateAllocationSuccessRate();
        }
    }
    
    /**
     * Record failed allocation metrics
     */
    private void recordFailedAllocation() {
        if (trackDetailedMetrics) {
            failedAllocations++;
            updateAllocationSuccessRate();
        }
    }
    
    /**
     * Update host allocation count
     * @param host the host to update count for
     */
    private void updateHostAllocationCount(Host host) {
        if (trackDetailedMetrics) {
            hostAllocationCount.merge(host, 1, Integer::sum);
        }
    }
    
    /**
     * Record allocation time
     * @param allocationTime time taken for allocation in nanoseconds
     */
    private void recordAllocationTime(long allocationTime) {
        if (trackDetailedMetrics) {
            totalAllocationTime += allocationTime;
            updateAverageAllocationTime();
        }
    }
    
    /**
     * Update average allocation time metric
     */
    private void updateAverageAllocationTime() {
        int totalAllocations = successfulAllocations + failedAllocations;
        if (totalAllocations > 0) {
            double avgTime = (double) totalAllocationTime / totalAllocations / 1_000_000; // Convert to milliseconds
            detailedMetrics.put("averageAllocationTime", avgTime);
        }
    }
    
    /**
     * Update allocation success rate metric
     */
    private void updateAllocationSuccessRate() {
        int totalAllocations = successfulAllocations + failedAllocations;
        if (totalAllocations > 0) {
            double successRate = (double) successfulAllocations / totalAllocations * 100.0;
            detailedMetrics.put("allocationSuccessRate", successRate);
        }
    }
    
    /**
     * Update average fitness score
     * @param fitness the fitness score to add
     */
    private void updateAverageFitnessScore(double fitness) {
        if (fitness != Double.MAX_VALUE && successfulAllocations > 0) {
            double currentAvg = detailedMetrics.get("averageFitnessScore");
            double newAvg = (currentAvg * (successfulAllocations - 1) + fitness) / successfulAllocations;
            detailedMetrics.put("averageFitnessScore", newAvg);
        }
    }
    
    /**
     * Deallocate a VM from its host
     * @param vm the VM to deallocate
     */
    @Override
    public void deallocateHostForVm(Vm vm) {
        Host host = vm.getHost();
        if (host != null && host != Host.NULL) {
            vm.setHost(Host.NULL);
            host.destroyVm(vm);
            LOGGER.debug("VM {} deallocated from Host {}", vm.getId(), host.getId());
        }
    }
    
    /**
     * Calculate fragmentation metric specific to Best Fit
     * @return fragmentation index
     */
    public double calculateFragmentation() {
        if (getHostList().isEmpty()) {
            return 0.0;
        }
        
        double totalFragmentation = getHostList().stream()
            .mapToDouble(this::calculateHostFragmentation)
            .sum();
        
        return totalFragmentation / getHostList().size();
    }
    
    /**
     * Calculate fragmentation for a specific host
     * @param host the host to calculate fragmentation for
     * @return fragmentation value
     */
    private double calculateHostFragmentation(Host host) {
        if (host.getVmList().isEmpty()) {
            return 0.0; // No fragmentation if no VMs
        }
        
        // Calculate resource utilization
        double cpuUtilization = host.getCpuPercentUtilization();
        double memoryUtilization = host.getRamPercentUtilization();
        
        // Calculate fragmentation as inverse of utilization balance
        double utilizationBalance = Math.abs(cpuUtilization - memoryUtilization);
        return utilizationBalance / 100.0; // Normalize to [0,1]
    }
    
   
    /**
     * Get detailed performance metrics for research analysis
     * @return Map containing detailed metrics for comparison and analysis
     */

    public Map<String, Double> getDetailedMetrics() {
        if (!trackDetailedMetrics) {
            LOGGER.warn("Detailed metrics not being tracked. Enable tracking for comprehensive analysis.");
            return new HashMap<>();
        }
        
        // Update calculated metrics
        updateCalculatedMetrics();
        
        // Create a copy to prevent external modification
        Map<String, Double> metricsCopy = new HashMap<>(detailedMetrics);
        
        // Add additional computed metrics
        metricsCopy.put("totalAllocations", (double) (successfulAllocations + failedAllocations));
        metricsCopy.put("successfulAllocations", (double) successfulAllocations);
        metricsCopy.put("failedAllocations", (double) failedAllocations);
        metricsCopy.put("hostCount", (double) getHostList().size());
        metricsCopy.put("activeHosts", (double) getActiveHostCount());
        metricsCopy.put("averageVmsPerHost", calculateAverageVmsPerHost());
        
        LOGGER.debug("Generated detailed metrics: {}", metricsCopy);
        return metricsCopy;
    }
    
    /**
     * Update calculated metrics that depend on current system state
     */
    private void updateCalculatedMetrics() {
        detailedMetrics.put("hostUtilizationVariance", calculateHostUtilizationVariance());
        detailedMetrics.put("fragmentationIndex", calculateFragmentation());
        detailedMetrics.put("resourceWastage", calculateResourceWastage());
        detailedMetrics.put("resourcePackingEfficiency", calculateResourcePackingEfficiency());
    }
    
    /**
     * Calculate variance in host utilization
     * @return utilization variance
     */
    private double calculateHostUtilizationVariance() {
        if (getHostList().isEmpty()) {
            return 0.0;
        }
        
        List<Double> utilizationValues = getHostList().stream()
            .mapToDouble(host -> (host.getCpuPercentUtilization() + host.getRamPercentUtilization()) / 2.0)
            .boxed()
            .collect(Collectors.toList());
        
        double mean = utilizationValues.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        
        double variance = utilizationValues.stream()
            .mapToDouble(util -> Math.pow(util - mean, 2))
            .average()
            .orElse(0.0);
        
        return variance;
    }
    
    /**
     * Calculate resource wastage metric
     * @return resource wastage percentage
     */
    private double calculateResourceWastage() {
        if (getHostList().isEmpty()) {
            return 0.0;
        }
        
        double totalWastage = 0.0;
        int activeHosts = 0;
        
        for (Host host : getHostList()) {
            if (!host.getVmList().isEmpty()) {
                activeHosts++;
                double cpuUtil = host.getCpuPercentUtilization();
                double memUtil = host.getRamPercentUtilization();
                
                // Wastage is the difference between allocated and efficiently used resources
                double utilizationImbalance = Math.abs(cpuUtil - memUtil);
                totalWastage += utilizationImbalance;
            }
        }
        
        return activeHosts > 0 ? totalWastage / activeHosts : 0.0;
    }
    
    /**
     * Calculate resource packing efficiency
     * @return packing efficiency percentage
     */
    private double calculateResourcePackingEfficiency() {
        if (getHostList().isEmpty()) {
            return 0.0;
        }
        
        double totalEfficiency = 0.0;
        int activeHosts = 0;
        
        for (Host host : getHostList()) {
            if (!host.getVmList().isEmpty()) {
                activeHosts++;
                double cpuUtil = host.getCpuPercentUtilization();
                double memUtil = host.getRamPercentUtilization();
                
                // Efficiency is the geometric mean of resource utilizations
                double efficiency = Math.sqrt(cpuUtil * memUtil);
                totalEfficiency += efficiency;
            }
        }
        
        return activeHosts > 0 ? totalEfficiency / activeHosts : 0.0;
    }
    
    /**
     * Get count of active hosts (hosts with at least one VM)
     * @return number of active hosts
     */
    private int getActiveHostCount() {
        return (int) getHostList().stream()
            .filter(host -> !host.getVmList().isEmpty())
            .count();
    }
    
    /**
     * Calculate average number of VMs per host
     * @return average VMs per host
     */
    private double calculateAverageVmsPerHost() {
        if (getHostList().isEmpty()) {
            return 0.0;
        }
        
        int totalVms = getHostList().stream()
            .mapToInt(host -> host.getVmList().size())
            .sum();
        
        return (double) totalVms / getHostList().size();
    }
    
    /**
     * Get allocation statistics for research reporting
     * @return allocation statistics map
     */
    public Map<String, Object> getAllocationStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalAllocations", successfulAllocations + failedAllocations);
        stats.put("successfulAllocations", successfulAllocations);
        stats.put("failedAllocations", failedAllocations);
        stats.put("allocationSuccessRate", detailedMetrics.get("allocationSuccessRate"));
        stats.put("averageAllocationTime", detailedMetrics.get("averageAllocationTime"));
        stats.put("fitnessType", fitnessType.toString());
        
        // Host distribution statistics
        Map<Integer, Integer> hostDistribution = new HashMap<>();
        for (Map.Entry<Host, Integer> entry : hostAllocationCount.entrySet()) {
            int hostId = (int) entry.getKey().getId();
            int allocCount = entry.getValue();
            hostDistribution.put(hostId, allocCount);
        }
        stats.put("hostAllocationDistribution", hostDistribution);
        
        return stats;
    }
    
    /**
     * Reset all metrics and counters
     */
    public void resetMetrics() {
        totalAllocationTime = 0;
        successfulAllocations = 0;
        failedAllocations = 0;
        hostAllocationCount.clear();
        initializeMetrics();
        LOGGER.info("BestFitVmAllocation metrics reset");
    }
    
    /**
     * Set whether to track detailed metrics
     * @param trackDetailedMetrics true to enable detailed tracking
     */
    public void setTrackDetailedMetrics(boolean trackDetailedMetrics) {
        this.trackDetailedMetrics = trackDetailedMetrics;
        if (trackDetailedMetrics) {
            LOGGER.info("Detailed metrics tracking enabled for BestFitVmAllocation");
        } else {
            LOGGER.info("Detailed metrics tracking disabled for BestFitVmAllocation");
        }
    }
    
    /**
     * Set the fitness calculation type
     * @param fitnessType the fitness calculation method to use
     */
    public void setFitnessType(ResourceFitnessType fitnessType) {
        this.fitnessType = fitnessType != null ? fitnessType : ResourceFitnessType.WEIGHTED_AVERAGE;
        LOGGER.info("BestFitVmAllocation fitness type set to: {}", this.fitnessType);
    }
    
    /**
     * Get current fitness type
     * @return current fitness calculation type
     */
    public ResourceFitnessType getFitnessType() {
        return fitnessType;
    }
    
    /**
     * Generate a comprehensive performance report for research analysis
     * @return formatted performance report string
     */
    public String generatePerformanceReport() {
        StringBuilder report = new StringBuilder();
        report.append(String.format("=== Best Fit VM Allocation Performance Report ===%n"));
        report.append(String.format("Fitness Type: %s%n", fitnessType));
        report.append(String.format("Tracking Detailed Metrics: %s%n", trackDetailedMetrics));
        report.append(String.format("%n--- Allocation Statistics ---%n"));
        report.append(String.format("Total Allocations: %d%n", successfulAllocations + failedAllocations));
        report.append(String.format("Successful Allocations: %d%n", successfulAllocations));
        report.append(String.format("Failed Allocations: %d%n", failedAllocations));
        report.append(String.format("Success Rate: %.2f%%%n", detailedMetrics.get("allocationSuccessRate")));
        report.append(String.format("Average Allocation Time: %.4f ms%n", detailedMetrics.get("averageAllocationTime")));
        
        if (trackDetailedMetrics) {
            report.append(String.format("%n--- Resource Utilization Metrics ---%n"));
            report.append(String.format("Host Utilization Variance: %.4f%n", detailedMetrics.get("hostUtilizationVariance")));
            report.append(String.format("Fragmentation Index: %.4f%n", detailedMetrics.get("fragmentationIndex")));
            report.append(String.format("Resource Wastage: %.2f%%%n", detailedMetrics.get("resourceWastage")));
            report.append(String.format("Resource Packing Efficiency: %.2f%%%n", detailedMetrics.get("resourcePackingEfficiency")));
            report.append(String.format("Average Fitness Score: %.4f%n", detailedMetrics.get("averageFitnessScore")));
            
            report.append(String.format("%n--- Host Distribution ---%n"));
            report.append(String.format("Total Hosts: %d%n", getHostList().size()));
            report.append(String.format("Active Hosts: %d%n", getActiveHostCount()));
            report.append(String.format("Average VMs per Host: %.2f%n", calculateAverageVmsPerHost()));
        }
        
        report.append(String.format("%n=== End Report ===%n"));
        return report.toString();
    }
    
    /**
     * Validate the current allocation state
     * @return true if allocation state is valid, false otherwise
     */
    public boolean validateAllocationState() {
        try {
            for (Host host : getHostList()) {
                // Check resource constraints
                if (host.getTotalAllocatedMips() > host.getTotalMipsCapacity()) {
                    LOGGER.error("Host {} has over-allocated CPU: {} > {}", 
                               host.getId(), host.getTotalAllocatedMips(), host.getTotalMipsCapacity());
                    return false;
                }
                
                if (host.getRam().getAllocatedResource() > host.getRam().getCapacity()) {
                    LOGGER.error("Host {} has over-allocated RAM: {} > {}", 
                               host.getId(), host.getRam().getAllocatedResource(), host.getRam().getCapacity());
                    return false;
                }
                
                // Check VM-Host consistency
                for (Vm vm : host.getVmList()) {
                    if (!vm.getHost().equals(host)) {
                        LOGGER.error("VM {} host reference inconsistency: VM thinks it's on Host {}, but Host {} has it", 
                                   vm.getId(), vm.getHost().getId(), host.getId());
                        return false;
                    }
                }
            }
            
            LOGGER.debug("Best Fit allocation state validation passed");
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Error during allocation state validation: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Get summary of Best Fit algorithm characteristics for research documentation
     * @return algorithm summary map
     */
    public Map<String, Object> getAlgorithmSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        summary.put("algorithmName", "Best Fit");
        summary.put("algorithmType", "Deterministic Heuristic");
        summary.put("timeComplexity", "O(n*m) where n=VMs, m=Hosts");
        summary.put("spaceComplexity", "O(1) additional space");
        summary.put("optimizationObjective", "Minimize resource fragmentation");
        summary.put("fitnessFunction", fitnessType.toString());
        summary.put("allocationStrategy", "Select host with minimum remaining resources that can accommodate VM");
        summary.put("suitableFor", Arrays.asList("Resource-efficient allocation", "Fragmentation minimization", "Baseline comparison"));
        summary.put("limitations", Arrays.asList("Higher computation cost than First Fit", "May not globally optimize", "Greedy approach"));
        
        return summary;
    }
    
    @Override
    public String toString() {
        return String.format("BestFitVmAllocation[fitnessType=%s, tracking=%s, success_rate=%.2f%%]", 
                           fitnessType, trackDetailedMetrics, detailedMetrics.get("allocationSuccessRate"));
    }
}