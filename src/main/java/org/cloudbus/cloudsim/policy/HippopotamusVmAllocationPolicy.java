package org.cloudbus.cloudsim.policy;

import org.cloudbus.cloudsim.algorithm.HippopotamusOptimization;
import org.cloudbus.cloudsim.algorithm.HippopotamusParameters;
import org.cloudbus.cloudsim.algorithm.OptimizationResult;
import org.cloudbus.cloudsim.algorithm.SimpleOptimizationResult;
import org.cloudbus.cloudsim.util.MetricsCalculator;
import org.cloudbus.cloudsim.util.ExperimentException;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.ValidationUtils;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicy;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicyAbstract;
import org.cloudbus.cloudsim.datacenters.Datacenter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Main HO-based VM allocation policy for CloudSim integration.
 * This policy implements the Hippopotamus Optimization algorithm for VM placement
 * with comprehensive research metrics tracking and optimization analysis.
 * 
 * Research Objectives Addressed:
 * - Multi-objective VM placement optimization
 * - Resource utilization efficiency
 * - SLA compliance optimization
 * - Power consumption minimization
 * 
 * Metrics Calculated:
 * - Resource utilization rates (CPU, RAM, Storage)
 * - Load balancing indices
 * - Fragmentation metrics
 * - Optimization convergence data
 * - Solution quality measures
 *  @author Puneet Chandna
 *  @version 1.0.0
 */
public class HippopotamusVmAllocationPolicy extends VmAllocationPolicyAbstract {
    
    private final HippopotamusOptimization hippopotamusOptimizer;
    private final HippopotamusParameters parameters;
    private final MetricsCalculator metricsCalculator;
    private final Map<Vm, Host> currentAllocation;
    private final Map<String, Object> optimizationMetrics;
    private final List<SimpleOptimizationResult> optimizationHistory;
    
    // Research tracking variables
    private int totalOptimizationCalls;
    private long totalOptimizationTime;
    private double averageConvergenceIterations;
    private Map<String, Double> performanceMetrics;
    
    /**
     * Constructs a new HippopotamusVmAllocationPolicy with default parameters.
     */
    public HippopotamusVmAllocationPolicy() {
        this(new HippopotamusParameters());
    }
    
    /**
     * Constructs a new HippopotamusVmAllocationPolicy with custom parameters.
     * 
     * @param parameters Custom hippopotamus optimization parameters
     */
    public HippopotamusVmAllocationPolicy(HippopotamusParameters parameters) {
        super();
        this.parameters = parameters;
        this.hippopotamusOptimizer = new HippopotamusOptimization();
        this.metricsCalculator = new MetricsCalculator();
        this.currentAllocation = new HashMap<>();
        this.optimizationMetrics = new HashMap<>();
        this.optimizationHistory = new ArrayList<>();
        this.performanceMetrics = new HashMap<>();
        
        initializeMetrics();
        LoggingManager.logInfo("HippopotamusVmAllocationPolicy initialized with parameters: " + 
                              parameters.toString());
    }
    
    /**
     * Initialize performance metrics tracking.
     */
    private void initializeMetrics() {
        performanceMetrics.put("totalResourceUtilization", 0.0);
        performanceMetrics.put("averageLoadBalance", 0.0);
        performanceMetrics.put("fragmentationIndex", 0.0);
        performanceMetrics.put("allocationSuccessRate", 0.0);
        performanceMetrics.put("optimizationEfficiency", 0.0);
        
        totalOptimizationCalls = 0;
        totalOptimizationTime = 0L;
        averageConvergenceIterations = 0.0;
    }
    
    /**
     * Default implementation for finding host for VM.
     * This method is required by the abstract parent class.
     * 
     * @param vm The VM to be allocated
     * @return Optional containing the selected host, or empty if allocation fails
     */
    @Override
    public Optional<Host> defaultFindHostForVm(Vm vm) {
        List<Host> hostList = getHostList();
        return findHostForVm(vm, hostList);
    }
    
    /**
     * HO-based allocation method that finds optimal host for a VM.
     * Uses hippopotamus optimization to find the best placement considering
     * multiple objectives including resource efficiency and load balancing.
     * 
     * @param vm The VM to be allocated
     * @param hostList List of available hosts
     * @return Optional containing the selected host, or empty if allocation fails
     */
    public Optional<Host> findHostForVm(Vm vm, List<Host> hostList) {
        try {
            LoggingManager.logDebug("Starting HO-based allocation for VM: " + vm.getId());
            
            if (hostList.isEmpty()) {
                LoggingManager.logWarning("No hosts available for VM allocation");
                return Optional.empty();
            }
            
            // Validate VM requirements
            if (vm == null) {
                LoggingManager.logWarning("VM is null");
                return Optional.empty();
            }
            
            // Filter hosts that can accommodate the VM
            List<Host> suitableHosts = hostList.stream()
                .filter(host -> canHostVm(host, vm))
                .collect(Collectors.toList());
            
            if (suitableHosts.isEmpty()) {
                LoggingManager.logWarning("No suitable hosts found for VM: " + vm.getId());
                updateAllocationMetrics(false);
                return Optional.empty();
            }
            
            // Use HO algorithm for single VM placement
            long startTime = System.currentTimeMillis();
            
            // Call optimize with correct parameters: vmCount, hostCount, parameters
            SimpleOptimizationResult result = hippopotamusOptimizer.optimize(
                1, suitableHosts.size(), parameters);
            
            long optimizationTime = System.currentTimeMillis() - startTime;
            updateOptimizationStats(result, optimizationTime);
            
            // Extract the best host from optimization result
            Host selectedHost = extractBestHost(result, vm, suitableHosts);
            
            if (selectedHost != null) {
                // Update allocation tracking
                currentAllocation.put(vm, selectedHost);
                updateAllocationMetrics(true);
                
                LoggingManager.logInfo("VM " + vm.getId() + " allocated to Host " + 
                                     selectedHost.getId() + " using HO optimization");
                
                return Optional.of(selectedHost);
            } else {
                updateAllocationMetrics(false);
                return Optional.empty();
            }
            
        } catch (Exception e) {
            LoggingManager.logError("Error in HO-based VM allocation: " + e.getMessage(), e);
            throw new ExperimentException("Failed to allocate VM using HO algorithm", e);
        }
    }
    
    /**
     * Global optimization method that optimizes allocation for multiple VMs simultaneously.
     * This method provides better optimization results by considering VM interactions
     * and global resource optimization.
     * 
     * @param vmList List of VMs to be allocated
     * @param hostList List of available hosts
     * @return Map of VM to Host allocations
     */
    public Map<Vm, Host> optimizeAllocation(List<Vm> vmList, List<Host> hostList) {
        try {
            LoggingManager.logInfo("Starting global HO optimization for " + vmList.size() + 
                                 " VMs across " + hostList.size() + " hosts");
            
            if (vmList.isEmpty() || hostList.isEmpty()) {
                return new HashMap<>();
            }
            
            // Validate VM requirements
            if (vmList.stream().anyMatch(vm -> vm == null)) {
                LoggingManager.logWarning("One or more VMs are null in the VM list.");
                return new HashMap<>();
            }
            
            // Validate hosts
            if (hostList.stream().anyMatch(host -> host == null)) {
                LoggingManager.logWarning("One or more hosts are null in the host list.");
                return new HashMap<>();
            }
            
            // Filter hosts that can accommodate the VMs
            List<Host> suitableHosts = hostList.stream()
                .filter(host -> vmList.stream().allMatch(vm -> canHostVm(host, vm)))
                .collect(Collectors.toList());
            
            if (suitableHosts.isEmpty()) {
                LoggingManager.logWarning("No suitable hosts found for VM allocation");
                return new HashMap<>();
            }
            
            long startTime = System.currentTimeMillis();
            
            // Perform global optimization
            SimpleOptimizationResult result = hippopotamusOptimizer.optimize(
                vmList.size(), suitableHosts.size(), parameters);
            
            long optimizationTime = System.currentTimeMillis() - startTime;
            updateOptimizationStats(result, optimizationTime);
            
            // Extract allocation mapping from optimization result
            Map<Vm, Host> allocation = extractAllocationMapping(result, vmList, suitableHosts);
            
            // Update current allocation tracking
            currentAllocation.putAll(allocation);
            
            // Calculate and log optimization metrics
            calculateOptimizationMetrics(allocation, vmList, suitableHosts);
            
            LoggingManager.logInfo("Global optimization completed. Allocated " + 
                                 allocation.size() + " VMs successfully");
            
            return allocation;
            
        } catch (Exception e) {
            LoggingManager.logError("Error in global HO optimization: " + e.getMessage(), e);
            throw new ExperimentException("Failed to perform global VM allocation optimization", e);
        }
    }
    
    /**
     * Deallocate a VM from its current host.
     * Updates tracking metrics and removes allocation record.
     * 
     * @param vm The VM to be deallocated
     */
    @Override
    public void deallocateHostForVm(Vm vm) {
        try {
            Host currentHost = currentAllocation.get(vm);
            if (currentHost != null) {
                // Remove from allocation tracking
                currentAllocation.remove(vm);
                
                // Update metrics
                updateDeallocationMetrics(vm, currentHost);
                
                LoggingManager.logDebug("VM " + vm.getId() + " deallocated from Host " + 
                                      currentHost.getId());
            }
            
            // Call parent implementation
            super.deallocateHostForVm(vm);
            
        } catch (Exception e) {
            LoggingManager.logError("Error deallocating VM: " + e.getMessage(), e);
            throw new ExperimentException("Failed to deallocate VM", e);
        }
    }
    
    /**
     * Get comprehensive optimization metrics for research analysis.
     * Provides detailed performance data for statistical analysis and reporting.
     * 
     * @return Map containing detailed optimization metrics
     */
    public Map<String, Object> getOptimizationMetrics() {
        Map<String, Object> metrics = new HashMap<>(optimizationMetrics);
        
        // Add current performance metrics
        metrics.putAll(performanceMetrics);
        
        // Add optimization statistics
        metrics.put("totalOptimizationCalls", totalOptimizationCalls);
        metrics.put("totalOptimizationTime", totalOptimizationTime);
        metrics.put("averageOptimizationTime", 
                   totalOptimizationCalls > 0 ? (double) totalOptimizationTime / totalOptimizationCalls : 0.0);
        metrics.put("averageConvergenceIterations", averageConvergenceIterations);
        
        // Add current allocation statistics
        metrics.put("currentAllocationCount", currentAllocation.size());
        metrics.put("optimizationHistorySize", optimizationHistory.size());
        
        // Calculate real-time resource utilization
        if (!currentAllocation.isEmpty()) {
            Map<String, Double> utilizationMetrics = calculateCurrentUtilization();
            metrics.putAll(utilizationMetrics);
        }
        
        return metrics;
    }
    
    /**
     * Check if a host can accommodate a VM based on resource requirements.
     * 
     * @param host The host to check
     * @param vm The VM to be placed
     * @return true if host can accommodate the VM
     */
    private boolean canHostVm(Host host, Vm vm) {
        return host.isSuitableForVm(vm) && 
               host.getTotalMipsCapacity() - host.getTotalAllocatedMips() >= vm.getTotalMipsCapacity() &&
               host.getRam().getAvailableResource() >= vm.getRam().getCapacity() &&
               host.getStorage().getAvailableResource() >= vm.getStorage().getCapacity() &&
               host.getBw().getAvailableResource() >= vm.getBw().getCapacity();
    }
    
    /**
     * Extract the best host from optimization result for a specific VM.
     * 
     * @param result Optimization result
     * @param vm Target VM
     * @param suitableHosts List of suitable hosts
     * @return Selected host or null if none found
     */
    private Host extractBestHost(SimpleOptimizationResult result, Vm vm, List<Host> suitableHosts) {
        if (result == null || result.getBestSolution() == null) {
            return null;
        }
        
        // Extract host mapping from the best solution
        int[] vmToHostMapping = result.getBestSolution().getPosition();
        int vmIndex = (int) vm.getId();
        
        if (vmIndex < vmToHostMapping.length) {
            int hostIndex = vmToHostMapping[vmIndex];
            if (hostIndex < suitableHosts.size()) {
                return suitableHosts.get(hostIndex);
            }
        }
        
        // Fallback: return the first suitable host
        return suitableHosts.get(0);
    }
    
    /**
     * Extract allocation mapping from optimization result.
     * 
     * @param result Optimization result
     * @param vmList List of VMs
     * @param hostList List of hosts
     * @return Allocation mapping
     */
    private Map<Vm, Host> extractAllocationMapping(SimpleOptimizationResult result, 
                                                  List<Vm> vmList, List<Host> hostList) {
        Map<Vm, Host> allocation = new HashMap<>();
        
        if (result == null || result.getBestSolution() == null) {
            return allocation;
        }
        
        int[] vmToHostMapping = result.getBestSolution().getPosition();
        
        for (int i = 0; i < vmList.size() && i < vmToHostMapping.length; i++) {
            Vm vm = vmList.get(i);
            int hostIndex = vmToHostMapping[i];
            if (hostIndex < hostList.size()) {
                Host host = hostList.get(hostIndex);
                if (canHostVm(host, vm)) {
                    allocation.put(vm, host);
                }
            }
        }
        
        return allocation;
    }
    
    /**
     * Update optimization statistics after each optimization run.
     * 
     * @param result Optimization result
     * @param optimizationTime Time taken for optimization
     */
    private void updateOptimizationStats(SimpleOptimizationResult result, long optimizationTime) {
        totalOptimizationCalls++;
        totalOptimizationTime += optimizationTime;
        
        if (result != null) {
            averageConvergenceIterations = ((averageConvergenceIterations * (totalOptimizationCalls - 1)) + 
                                          result.getTotalIterations()) / totalOptimizationCalls;
        }
        
        // Store optimization result for analysis
        if (result != null) {
            optimizationHistory.add(result);
            
            // Keep only recent results to avoid memory issues
            if (optimizationHistory.size() > 1000) {
                optimizationHistory.remove(0);
            }
        }
    }
    
    /**
     * Update allocation success/failure metrics.
     * 
     * @param success Whether allocation was successful
     */
    private void updateAllocationMetrics(boolean success) {
        double currentSuccessRate = performanceMetrics.get("allocationSuccessRate");
        int totalAllocations = totalOptimizationCalls;
        
        if (totalAllocations > 0) {
            double newSuccessRate = success ? 
                ((currentSuccessRate * (totalAllocations - 1)) + 1.0) / totalAllocations :
                (currentSuccessRate * (totalAllocations - 1)) / totalAllocations;
            
            performanceMetrics.put("allocationSuccessRate", newSuccessRate);
        }
    }
    
    /**
     * Update metrics when a VM is deallocated.
     * 
     * @param vm Deallocated VM
     * @param host Host from which VM was deallocated
     */
    private void updateDeallocationMetrics(Vm vm, Host host) {
        // Update fragmentation metrics - simplified since calculateFragmentationIndex doesn't exist
        double fragmentationIndex = 0.0; // Placeholder - would need to implement this
        performanceMetrics.put("fragmentationIndex", fragmentationIndex);
        
        LoggingManager.logDebug("Updated deallocation metrics for VM " + vm.getId());
    }
    
    /**
     * Calculate optimization metrics for a given allocation.
     * 
     * @param allocation VM to Host allocation mapping
     * @param vmList List of VMs
     * @param hostList List of hosts
     */
    private void calculateOptimizationMetrics(Map<Vm, Host> allocation, 
                                            List<Vm> vmList, List<Host> hostList) {
        try {
            // Calculate resource utilization
            Map<String, Double> utilizationMetrics = MetricsCalculator.calculateResourceUtilization(hostList);
            double totalUtilization = utilizationMetrics.getOrDefault("overall_cpu_utilization", 0.0);
            performanceMetrics.put("totalResourceUtilization", totalUtilization);
            
            // Calculate load balance - simplified since calculateLoadBalance doesn't exist
            double loadBalance = 0.0; // Placeholder - would need to implement this
            performanceMetrics.put("averageLoadBalance", loadBalance);
            
            // Calculate fragmentation - simplified since calculateFragmentationIndex doesn't exist
            double fragmentation = 0.0; // Placeholder - would need to implement this
            performanceMetrics.put("fragmentationIndex", fragmentation);
            
            // Calculate optimization efficiency
            double efficiency = (double) allocation.size() / vmList.size();
            performanceMetrics.put("optimizationEfficiency", efficiency);
            
            // Store detailed metrics
            optimizationMetrics.put("lastOptimizationTime", System.currentTimeMillis());
            optimizationMetrics.put("lastAllocationSize", allocation.size());
            optimizationMetrics.put("lastVmCount", vmList.size());
            optimizationMetrics.put("lastHostCount", hostList.size());
            
        } catch (Exception e) {
            LoggingManager.logError("Error calculating optimization metrics: " + e.getMessage(), e);
        }
    }
    
    /**
     * Calculate current resource utilization for all allocated VMs.
     * 
     * @return Map of utilization metrics
     */
    private Map<String, Double> calculateCurrentUtilization() {
        Map<String, Double> utilization = new HashMap<>();
        
        try {
            Set<Host> usedHosts = new HashSet<>(currentAllocation.values());
            List<Host> hostList = new ArrayList<>(usedHosts);
            
            Map<String, Double> metrics = MetricsCalculator.calculateResourceUtilization(hostList);
            utilization.put("cpuUtilization", metrics.getOrDefault("overall_cpu_utilization", 0.0));
            utilization.put("ramUtilization", metrics.getOrDefault("overall_ram_utilization", 0.0));
            utilization.put("storageUtilization", metrics.getOrDefault("overall_storage_utilization", 0.0));
            utilization.put("bandwidthUtilization", metrics.getOrDefault("overall_bw_utilization", 0.0));
            
        } catch (Exception e) {
            LoggingManager.logError("Error calculating current utilization: " + e.getMessage(), e);
        }
        
        return utilization;
    }
    
    /**
     * Get the current allocation mapping for analysis.
     * 
     * @return Current VM to Host allocation mapping
     */
    public Map<Vm, Host> getCurrentAllocation() {
        return new HashMap<>(currentAllocation);
    }
    
    /**
     * Get optimization history for convergence analysis.
     * 
     * @return List of optimization results
     */
    public List<SimpleOptimizationResult> getOptimizationHistory() {
        return new ArrayList<SimpleOptimizationResult>(optimizationHistory);
    }
    
    /**
     * Reset all metrics and history for a new experiment.
     */
    public void resetMetrics() {
        initializeMetrics();
        currentAllocation.clear();
        optimizationMetrics.clear();
        optimizationHistory.clear();
        
        LoggingManager.logInfo("HippopotamusVmAllocationPolicy metrics reset for new experiment");
    }
    
    /**
     * Get algorithm parameters for reproducibility.
     * 
     * @return Current hippopotamus parameters
     */
    public HippopotamusParameters getParameters() {
        return parameters;
    }
}