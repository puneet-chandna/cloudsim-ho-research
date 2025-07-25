package org.cloudbus.cloudsim.baseline;

import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicy;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicyAbstract;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * First Fit VM Allocation Policy
 * 
 * This class implements the First Fit algorithm for VM placement, which allocates
 * each VM to the first host that has sufficient resources. This serves as a baseline
 * comparison algorithm for the Hippopotamus Optimization research.
 * 
 * Research Objectives Addressed:
 * - Provides baseline performance comparison
 * - Demonstrates simple heuristic approach
 * - Establishes lower bound for optimization algorithms
 * 
 * Metrics Calculated:
 * - Resource utilization efficiency
 * - Allocation success rate
 * - Host fragmentation metrics
 * - Execution time performance
 * 
 * @author Puneet Chandna
 * @version 1.0
 */
public class FirstFitVmAllocation extends VmAllocationPolicyAbstract {
    private static final Logger LOGGER = LoggerFactory.getLogger(FirstFitVmAllocation.class);
    
    // Constants for metric names to avoid string duplication
    private static final String METRIC_AVERAGE_ALLOCATION_TIME = "averageAllocationTime";
    private static final String METRIC_ALLOCATION_SUCCESS_RATE = "allocationSuccessRate";
    private static final String METRIC_HOST_UTILIZATION_VARIANCE = "hostUtilizationVariance";
    private static final String METRIC_FRAGMENTATION_INDEX = "fragmentationIndex";
    private static final String METRIC_RESOURCE_WASTAGE = "resourceWastage";
    
    // Performance tracking metrics
    private long totalAllocationTime = 0;
    private int successfulAllocations = 0;
    private int failedAllocations = 0;
    private Map<Host, Integer> hostAllocationCount = new HashMap<>();
    private Map<String, Double> detailedMetrics = new HashMap<>();
    
    // Algorithm-specific parameters
    private boolean trackDetailedMetrics;
    private long startTime;
    
    /**
     * Constructor for First Fit VM Allocation Policy
     */
    public FirstFitVmAllocation() {
        super();
        this.trackDetailedMetrics = true;
        initializeMetrics();
        LOGGER.info("FirstFitVmAllocation policy initialized");
    }
    
    /**
     * Constructor with metric tracking option
     * @param trackDetailedMetrics Whether to track detailed performance metrics
     */
    public FirstFitVmAllocation(boolean trackDetailedMetrics) {
        super();
        this.trackDetailedMetrics = trackDetailedMetrics;
        initializeMetrics();
        LOGGER.info("FirstFitVmAllocation policy initialized with tracking: {}", trackDetailedMetrics);
    }
    
    /**
     * Initialize performance metrics
     */
    private void initializeMetrics() {
        detailedMetrics.put(METRIC_AVERAGE_ALLOCATION_TIME, 0.0);
        detailedMetrics.put(METRIC_ALLOCATION_SUCCESS_RATE, 0.0);
        detailedMetrics.put(METRIC_HOST_UTILIZATION_VARIANCE, 0.0);
        detailedMetrics.put(METRIC_FRAGMENTATION_INDEX, 0.0);
        detailedMetrics.put(METRIC_RESOURCE_WASTAGE, 0.0);
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
        return findHostForVm(vm, hostList);
    }
    
    /**
     * Allocates a host for a given VM using First Fit algorithm
     * 
     * @param vm the VM to be allocated
     * @param hostList the list of available hosts
     * @return Optional containing the selected host, or empty if allocation fails
     */
    public Optional<Host> findHostForVm(Vm vm, List<Host> hostList) {
        if (trackDetailedMetrics) {
            startTime = System.nanoTime();
        }
        
        try {
            // First Fit Algorithm: Try to allocate VM to first suitable host
            for (Host host : hostList) {
                if (host.isSuitableForVm(vm)) {
                    boolean allocationResult = allocateHostForVmInternal(vm, host);
                    
                    if (allocationResult) {
                        recordSuccessfulAllocation();
                        updateHostAllocationCount(host);
                        LOGGER.debug("VM {} allocated to Host {} using First Fit", 
                                   vm.getId(), host.getId());
                        return Optional.of(host);
                    }
                }
            }
            
            // If no suitable host found
            recordFailedAllocation();
            LOGGER.warn("Failed to allocate VM {} using First Fit - no suitable host found", vm.getId());
            return Optional.empty();
            
        } catch (Exception e) {
            recordFailedAllocation();
            LOGGER.error("Error during First Fit allocation for VM {}: {}", vm.getId(), e.getMessage());
            return Optional.empty();
        } finally {
            if (trackDetailedMetrics) {
                recordAllocationTime(System.nanoTime() - startTime);
            }
        }
    }
    
    /**
     * Internal method to perform the actual allocation
     * @param vm the VM to allocate
     * @param host the host to allocate to
     * @return true if successful, false otherwise
     */
    private boolean allocateHostForVmInternal(Vm vm, Host host) {
        if (host.isSuitableForVm(vm)) {
            vm.setHost(host);
            return true;
        }
        return false;
    }
    
    /**
     * Record successful allocation metrics
     */
    private void recordSuccessfulAllocation() {
        if (trackDetailedMetrics) {
            successfulAllocations++;
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
            detailedMetrics.put(METRIC_AVERAGE_ALLOCATION_TIME, avgTime);
        }
    }
    
    /**
     * Update allocation success rate metric
     */
    private void updateAllocationSuccessRate() {
        int totalAllocations = successfulAllocations + failedAllocations;
        if (totalAllocations > 0) {
            double successRate = (double) successfulAllocations / totalAllocations * 100.0;
            detailedMetrics.put(METRIC_ALLOCATION_SUCCESS_RATE, successRate);
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
     * Get detailed performance metrics for research analysis
     * @return Map containing detailed performance metrics
     */
    public Map<String, Double> getDetailedMetrics() {
        if (trackDetailedMetrics) {
            calculateAdvancedMetrics();
        }
        return new HashMap<>(detailedMetrics);
    }
    
    /**
     * Calculate advanced performance metrics
     */
    private void calculateAdvancedMetrics() {
        calculateHostUtilizationVariance();
        calculateFragmentationIndex();
        calculateResourceWastage();
    }
    
    /**
     * Calculate host utilization variance to measure load balancing
     */
    private void calculateHostUtilizationVariance() {
        if (getHostList().isEmpty()) {
            detailedMetrics.put(METRIC_HOST_UTILIZATION_VARIANCE, 0.0);
            return;
        }
        
        List<Double> utilizationRates = getHostList().stream()
            .map(this::calculateHostUtilization)
            .collect(Collectors.toList());
        
        double mean = utilizationRates.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = utilizationRates.stream()
            .mapToDouble(util -> Math.pow(util - mean, 2))
            .average().orElse(0.0);
        
        detailedMetrics.put(METRIC_HOST_UTILIZATION_VARIANCE, variance);
    }
    
    /**
     * Calculate host utilization rate
     * @param host the host to calculate utilization for
     * @return utilization rate as percentage
     */
    private double calculateHostUtilization(Host host) {
        if (host.getTotalMipsCapacity() == 0) return 0.0;
        
        double totalAllocatedMips = host.getVmList().stream()
            .mapToDouble(vm -> vm.getTotalMipsCapacity())
            .sum();
        
        return (totalAllocatedMips / host.getTotalMipsCapacity()) * 100.0;
    }
    
    /**
     * Calculate fragmentation index to measure resource fragmentation
     */
    private void calculateFragmentationIndex() {
        if (getHostList().isEmpty()) {
            detailedMetrics.put(METRIC_FRAGMENTATION_INDEX, 0.0);
            return;
        }
        
        double totalFragmentation = getHostList().stream()
            .mapToDouble(this::calculateHostFragmentation)
            .sum();
        
        double averageFragmentation = totalFragmentation / getHostList().size();
        detailedMetrics.put(METRIC_FRAGMENTATION_INDEX, averageFragmentation);
    }
    
    /**
     * Calculate fragmentation for a specific host
     * @param host the host to calculate fragmentation for
     * @return fragmentation value
     */
    private double calculateHostFragmentation(Host host) {
        // Calculate CPU fragmentation
        double cpuUsage = host.getCpuPercentUtilization();
        double cpuFragmentation = cpuUsage > 0 ? (100.0 - cpuUsage) / 100.0 : 0.0;
        
        // Calculate RAM fragmentation
        double ramUsage = host.getRam().getPercentUtilization();
        double ramFragmentation = ramUsage > 0 ? (100.0 - ramUsage) / 100.0 : 0.0;
        
        // Return average fragmentation
        return (cpuFragmentation + ramFragmentation) / 2.0;
    }
    
    /**
     * Calculate resource wastage metric
     */
    private void calculateResourceWastage() {
        if (getHostList().isEmpty()) {
            detailedMetrics.put(METRIC_RESOURCE_WASTAGE, 0.0);
            return;
        }
        
        double totalWastage = getHostList().stream()
            .mapToDouble(this::calculateHostResourceWastage)
            .sum();
        
        double averageWastage = totalWastage / getHostList().size();
        detailedMetrics.put(METRIC_RESOURCE_WASTAGE, averageWastage);
    }
    
    /**
     * Calculate resource wastage for a specific host
     * @param host the host to calculate wastage for
     * @return wastage percentage
     */
    private double calculateHostResourceWastage(Host host) {
        if (host.getVmList().isEmpty()) {
            return 100.0; // Completely wasted if no VMs
        }
        
        double cpuWastage = 100.0 - host.getCpuPercentUtilization();
        double ramWastage = 100.0 - host.getRam().getPercentUtilization();
        
        return (cpuWastage + ramWastage) / 2.0;
    }
    
    /**
     * Get allocation statistics for research reporting
     * @return Map containing allocation statistics
     */
    public Map<String, Object> getAllocationStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("successfulAllocations", successfulAllocations);
        stats.put("failedAllocations", failedAllocations);
        stats.put("totalAllocationTime", totalAllocationTime);
        stats.put("hostAllocationDistribution", new HashMap<>(hostAllocationCount));
        return stats;
    }
    
    /**
     * Reset all metrics for new experiment run
     */
    public void resetMetrics() {
        totalAllocationTime = 0;
        successfulAllocations = 0;
        failedAllocations = 0;
        hostAllocationCount.clear();
        initializeMetrics();
        LOGGER.info("FirstFitVmAllocation metrics reset for new experiment");
    }
    
    /**
     * Get algorithm name for reporting
     * @return algorithm name
     */
    public String getAlgorithmName() {
        return "First Fit";
    }
    
    /**
     * Get algorithm description for research documentation
     * @return algorithm description
     */
    public String getAlgorithmDescription() {
        return "First Fit algorithm allocates each VM to the first host that has sufficient resources. " +
               "This is a simple, greedy approach that provides fast allocation but may not optimize " +
               "resource utilization or load balancing.";
    }
    
    /**
     * Check if the algorithm supports online allocation
     * @return true as First Fit supports online allocation
     */
    public boolean supportsOnlineAllocation() {
        return true;
    }
    
    /**
     * Get computational complexity information
     * @return Map containing complexity information
     */
    public Map<String, String> getComplexityInfo() {
        Map<String, String> complexity = new HashMap<>();
        complexity.put("timeComplexity", "O(n*m) where n=VMs, m=Hosts");
        complexity.put("spaceComplexity", "O(1)");
        complexity.put("description", "Linear search through hosts for each VM");
        return complexity;
    }
    
    @Override
    public String toString() {
        return String.format("FirstFitVmAllocation{successfulAllocations=%d, failedAllocations=%d, " +
                           "avgAllocationTime=%.2f ms}", 
                           successfulAllocations, failedAllocations, 
                           detailedMetrics.get(METRIC_AVERAGE_ALLOCATION_TIME));
    }
}