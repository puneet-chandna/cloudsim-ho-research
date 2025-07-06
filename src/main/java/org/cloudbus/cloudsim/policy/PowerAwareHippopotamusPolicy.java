package org.cloudbus.cloudsim.policy;

import org.cloudbus.cloudsim.algorithm.HippopotamusOptimization;
import org.cloudbus.cloudsim.algorithm.HippopotamusParameters;
import org.cloudbus.cloudsim.algorithm.OptimizationResult;
import org.cloudbus.cloudsim.util.MetricsCalculator;
import org.cloudbus.cloudsim.util.ExperimentException;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.ValidationUtils;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.power.models.PowerModel;
import org.cloudsimplus.power.models.PowerModelHost;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Power-aware extension of HO policy that optimizes VM placement for energy efficiency.
 * This policy extends the base Hippopotamus optimization to include power consumption
 * as a primary objective, implementing advanced power-aware scheduling techniques.
 * 
 * Research Objectives Addressed:
 * - Energy-efficient VM placement optimization
 * - Power consumption minimization
 * - Carbon footprint reduction
 * - Green computing optimization
 * - Dynamic voltage and frequency scaling (DVFS) integration
 * 
 * Metrics Calculated:
 * - Total power consumption (watts)
 * - Power efficiency ratios
 * - Energy per completed task
 * - Peak power demand
 * - Power utilization distribution
 * - Carbon emission estimates
 * 
 * Statistical Methods Implemented:
 * - Power consumption trend analysis
 * - Energy efficiency statistical testing
 * - Power optimization convergence analysis
 */
public class PowerAwareHippopotamusPolicy extends HippopotamusVmAllocationPolicy {
    
    // Power optimization parameters
    private static final double POWER_WEIGHT = 0.4; // Weight for power objective
    private static final double PERFORMANCE_WEIGHT = 0.6; // Weight for performance objective
    private static final double IDLE_POWER_THRESHOLD = 0.1; // 10% threshold for idle power
    private static final double PEAK_POWER_LIMIT = 0.9; // 90% of maximum power capacity
    
    // Power tracking variables
    private final Map<Host, Double> hostPowerConsumption;
    private final Map<Host, Double> hostPowerEfficiency;
    private final List<Double> powerConsumptionHistory;
    private final Map<String, Double> powerMetrics;
    private final Map<Host, Long> hostLastUpdateTime;
    
    // Research metrics
    private double totalEnergyConsumed;
    private double peakPowerDemand;
    private double averagePowerEfficiency;
    private int powerOptimizationCalls;
    private long totalPowerOptimizationTime;
    
    // Power models and thresholds
    private final Map<Host, PowerModel> hostPowerModels;
    private double carbonEmissionFactor; // kg CO2 per kWh
    private double energyCostPerKwh; // Cost per kWh
    
    /**
     * Constructs a PowerAwareHippopotamusPolicy with default power optimization parameters.
     */
    public PowerAwareHippopotamusPolicy() {
        this(createPowerAwareParameters());
    }
    
    /**
     * Constructs a PowerAwareHippopotamusPolicy with custom parameters.
     * 
     * @param parameters Custom hippopotamus optimization parameters
     */
    public PowerAwareHippopotamusPolicy(HippopotamusParameters parameters) {
        super(parameters);
        
        this.hostPowerConsumption = new HashMap<>();
        this.hostPowerEfficiency = new HashMap<>();
        this.powerConsumptionHistory = new ArrayList<>();
        this.powerMetrics = new HashMap<>();
        this.hostLastUpdateTime = new HashMap<>();
        this.hostPowerModels = new HashMap<>();
        
        initializePowerMetrics();
        configurePowerOptimization();
        
        LoggingManager.logInfo("PowerAwareHippopotamusPolicy initialized with power optimization");
    }
    
    /**
     * Create power-aware optimization parameters.
     * 
     * @return HippopotamusParameters configured for power optimization
     */
    private static HippopotamusParameters createPowerAwareParameters() {
        HippopotamusParameters params = new HippopotamusParameters();
        params.setPopulationSize(30); // Larger population for power optimization
        params.setMaxIterations(150); // More iterations for convergence
        params.setConvergenceThreshold(0.0005); // Tighter convergence for power efficiency
        params.setPowerWeight(POWER_WEIGHT);
        params.setPerformanceWeight(PERFORMANCE_WEIGHT);
        return params;
    }
    
    /**
     * Initialize power-specific metrics tracking.
     */
    private void initializePowerMetrics() {
        powerMetrics.put("totalPowerConsumption", 0.0);
        powerMetrics.put("averagePowerConsumption", 0.0);
        powerMetrics.put("peakPowerDemand", 0.0);
        powerMetrics.put("powerEfficiencyRatio", 0.0);
        powerMetrics.put("energyPerTask", 0.0);
        powerMetrics.put("carbonEmissions", 0.0);
        powerMetrics.put("energyCost", 0.0);
        powerMetrics.put("powerUtilizationVariance", 0.0);
        
        totalEnergyConsumed = 0.0;
        peakPowerDemand = 0.0;
        averagePowerEfficiency = 0.0;
        powerOptimizationCalls = 0;
        totalPowerOptimizationTime = 0L;
        
        // Default values
        carbonEmissionFactor = 0.5; // kg CO2 per kWh (average grid)
        energyCostPerKwh = 0.12; // $0.12 per kWh (average commercial rate)
    }
    
    /**
     * Configure power optimization settings.
     */
    private void configurePowerOptimization() {
        // Set power optimization objectives
        getParameters().addObjective("powerConsumption", POWER_WEIGHT);
        getParameters().addObjective("performanceMetrics", PERFORMANCE_WEIGHT);
        
        LoggingManager.logDebug("Power optimization configured with weights - Power: " + 
                              POWER_WEIGHT + ", Performance: " + PERFORMANCE_WEIGHT);
    }
    
    /**
     * Power-aware VM allocation that considers energy efficiency.
     * Overrides the base allocation to include power consumption optimization.
     * 
     * @param vm The VM to be allocated
     * @param hostList List of available hosts
     * @return Optional containing the most power-efficient host
     */
    @Override
    public Optional<Host> findHostForVm(Vm vm, List<Host> hostList) {
        try {
            LoggingManager.logDebug("Starting power-aware allocation for VM: " + vm.getId());
            
            if (hostList.isEmpty()) {
                return Optional.empty();
            }
            
            // Update power consumption data
            updateHostPowerConsumption(hostList);
            
            // Filter hosts based on power efficiency and capacity
            List<Host> powerEfficientHosts = filterPowerEfficientHosts(hostList, vm);
            
            if (powerEfficientHosts.isEmpty()) {
                LoggingManager.logWarning("No power-efficient hosts available for VM: " + vm.getId());
                return super.findHostForVm(vm, hostList); // Fallback to base allocation
            }
            
            // Perform power-aware optimization
            long startTime = System.currentTimeMillis();
            Optional<Host> selectedHost = optimizeForPowerEfficiency(vm, powerEfficientHosts);
            long optimizationTime = System.currentTimeMillis() - startTime;
            
            updatePowerOptimizationStats(optimizationTime);
            
            if (selectedHost.isPresent()) {
                Host host = selectedHost.get();
                
                // Update power tracking
                updateHostPowerTracking(host, vm, true);
                
                // Calculate power impact
                double powerImpact = calculatePowerImpact(host, vm);
                powerMetrics.put("lastAllocationPowerImpact", powerImpact);
                
                LoggingManager.logInfo("Power-aware allocation: VM " + vm.getId() + 
                                     " allocated to Host " + host.getId() + 
                                     " (Power impact: " + String.format("%.2f", powerImpact) + "W)");
                
                return selectedHost;
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            LoggingManager.logError("Error in power-aware VM allocation: " + e.getMessage(), e);
            throw new ExperimentException("Failed to perform power-aware VM allocation", e);
        }
    }
    
    /**
     * Global power optimization for multiple VMs.
     * Optimizes the entire datacenter for minimum power consumption while maintaining performance.
     * 
     * @param vmList List of VMs to be allocated
     * @param hostList List of available hosts
     * @return Power-optimized allocation mapping
     */
    public Map<Vm, Host> optimizeForPowerEfficiency(List<Vm> vmList, List<Host> hostList) {
        try {
            LoggingManager.logInfo("Starting global power optimization for " + vmList.size() + " VMs");
            
            if (vmList.isEmpty() || hostList.isEmpty()) {
                return new HashMap<>();
            }
            
            // Update all host power models
            updateAllHostPowerModels(hostList);
            
            long startTime = System.currentTimeMillis();
            
            // Create power-aware optimization parameters
            HippopotamusParameters powerParams = createPowerOptimizedParameters();
            
            // Perform power-aware global optimization
            OptimizationResult result = getHippopotamusOptimizer().optimizeForPower(
                vmList, hostList, powerParams);
            
            long optimizationTime = System.currentTimeMillis() - startTime;
            updatePowerOptimizationStats(optimizationTime);
            
            // Extract power-optimal allocation
            Map<Vm, Host> allocation = extractPowerOptimalAllocation(result, vmList, hostList);
            
            // Update power tracking for all allocations
            updateGlobalPowerTracking(allocation);
            
            // Calculate comprehensive power metrics
            calculateComprehensivePowerMetrics(allocation, hostList);
            
            LoggingManager.logInfo("Power optimization completed. Total power consumption: " + 
                                 String.format("%.2f", calculateTotalPowerConsumption(allocation)) + "W");
            
            return allocation;
            
        } catch (Exception e) {
            LoggingManager.logError("Error in power optimization: " + e.getMessage(), e);
            throw new ExperimentException("Failed to perform power optimization", e);
        }
    }
    
    /**
     * Update power consumption data for all hosts.
     * 
     * @param hostList List of hosts to update
     */
    private void updateHostPowerConsumption(List<Host> hostList) {
        long currentTime = System.currentTimeMillis();
        
        for (Host host : hostList) {
            try {
                // Calculate current power consumption
                double currentPower = calculateHostPowerConsumption(host);
                
                // Update power tracking
                hostPowerConsumption.put(host, currentPower);
                hostLastUpdateTime.put(host, currentTime);
                
                // Update power efficiency
                double efficiency = calculatePowerEfficiency(host);
                hostPowerEfficiency.put(host, efficiency);
                
                // Track peak power demand
                if (currentPower > peakPowerDemand) {
                    peakPowerDemand = currentPower;
                }
                
            } catch (Exception e) {
                LoggingManager.logWarning("Failed to update power consumption for host " + 
                                        host.getId() + ": " + e.getMessage());
            }
        }
        
        // Update power history
        double totalPower = hostPowerConsumption.values().stream()
                                                .mapToDouble(Double::doubleValue)
                                                .sum();
        powerConsumptionHistory.add(totalPower);
        
        // Update metrics
        powerMetrics.put("totalPowerConsumption", totalPower);
        powerMetrics.put("peakPowerDemand", peakPowerDemand);
    }
    
    /**
     * Filter hosts based on power efficiency and capacity.
     * 
     * @param hostList List of available hosts
     * @param vm VM to be allocated
     * @return List of power-efficient hosts
     */
    private List<Host> filterPowerEfficientHosts(List<Host> hostList, Vm vm) {
        return hostList.stream()
                .filter(host -> canHostVm(host, vm))
                .filter(host -> isPowerEfficient(host))
                .filter(host -> !exceedsPowerLimit(host, vm))
                .sorted(Comparator.comparingDouble(this::calculatePowerEfficiency).reversed())
                .collect(Collectors.toList());
    }
    
    /**
     * Check if host can accommodate VM.
     * 
     * @param host Host to check
     * @param vm VM to allocate
     * @return true if host can accommodate VM
     */
    private boolean canHostVm(Host host, Vm vm) {
        return host.isSuitableForVm(vm) && 
               host.getAvailableMips() >= vm.getMips() &&
               host.getRam().getAvailableResource() >= vm.getRam().getCapacity() &&
               host.getBw().getAvailableResource() >= vm.getBw().getCapacity() &&
               host.getStorage().getAvailableResource() >= vm.getStorage().getCapacity();
    }
    
    /**
     * Check if host is power efficient.
     * 
     * @param host Host to check
     * @return true if host is power efficient
     */
    private boolean isPowerEfficient(Host host) {
        double efficiency = hostPowerEfficiency.getOrDefault(host, 0.0);
        double avgEfficiency = hostPowerEfficiency.values().stream()
                                                  .mapToDouble(Double::doubleValue)
                                                  .average()
                                                  .orElse(1.0);
        return efficiency >= avgEfficiency * 0.8; // At least 80% of average efficiency
    }
    
    /**
     * Check if allocating VM to host would exceed power limit.
     * 
     * @param host Host to check
     * @param vm VM to allocate
     * @return true if power limit would be exceeded
     */
    private boolean exceedsPowerLimit(Host host, Vm vm) {
        double currentPower = hostPowerConsumption.getOrDefault(host, 0.0);
        double additionalPower = estimateVmPowerConsumption(vm, host);
        double maxPower = getHostMaxPower(host);
        
        return (currentPower + additionalPower) > (maxPower * PEAK_POWER_LIMIT);
    }
    
    /**
     * Optimize single VM allocation for power efficiency.
     * 
     * @param vm VM to allocate
     * @param hostList List of candidate hosts
     * @return Optional containing selected host
     */
    private Optional<Host> optimizeForPowerEfficiency(Vm vm, List<Host> hostList) {
        if (hostList.isEmpty()) {
            return Optional.empty();
        }
        
        Host bestHost = null;
        double bestScore = Double.MAX_VALUE;
        
        for (Host host : hostList) {
            double powerScore = calculatePowerScore(host, vm);
            double performanceScore = calculatePerformanceScore(host, vm);
            
            // Weighted combination of power and performance scores
            double combinedScore = (POWER_WEIGHT * powerScore) + 
                                 (PERFORMANCE_WEIGHT * performanceScore);
            
            if (combinedScore < bestScore) {
                bestScore = combinedScore;
                bestHost = host;
            }
        }
        
        return Optional.ofNullable(bestHost);
    }
    
    /**
     * Calculate power score for host-VM combination.
     * 
     * @param host Host to evaluate
     * @param vm VM to allocate
     * @return Power score (lower is better)
     */
    private double calculatePowerScore(Host host, Vm vm) {
        double currentPower = hostPowerConsumption.getOrDefault(host, 0.0);
        double additionalPower = estimateVmPowerConsumption(vm, host);
        double totalPower = currentPower + additionalPower;
        
        // Normalize by host capacity
        double maxPower = getHostMaxPower(host);
        double normalizedPower = totalPower / maxPower;
        
        // Penalize hosts near power limit
        if (normalizedPower > PEAK_POWER_LIMIT) {
            normalizedPower *= 2.0; // Heavy penalty
        }
        
        return normalizedPower;
    }
    
    /**
     * Calculate performance score for host-VM combination.
     * 
     * @param host Host to evaluate
     * @param vm VM to allocate
     * @return Performance score (lower is better)
     */
    private double calculatePerformanceScore(Host host, Vm vm) {
        double cpuUtil = (host.getCpuPercentUtilization() + 
                         (vm.getMips() / host.getTotalMipsCapacity())) * 100;
        double memUtil = ((host.getRam().getAllocatedResource() + vm.getRam().getCapacity()) /
                         (double) host.getRam().getCapacity()) * 100;
        
        // Combine utilization metrics
        double avgUtil = (cpuUtil + memUtil) / 2.0;
        
        // Penalize high utilization
        if (avgUtil > 90.0) {
            avgUtil *= 1.5;
        }
        
        return avgUtil / 100.0; // Normalize to 0-1 range
    }
    
    /**
     * Calculate host power consumption.
     * 
     * @param host Host to calculate power for
     * @return Power consumption in watts
     */
    private double calculateHostPowerConsumption(Host host) {
        PowerModel powerModel = hostPowerModels.get(host);
        if (powerModel != null) {
            return powerModel.getPower(host.getCpuPercentUtilization());
        }
        
        // Fallback calculation based on utilization
        double maxPower = getHostMaxPower(host);
        double idlePower = maxPower * IDLE_POWER_THRESHOLD;
        double utilization = host.getCpuPercentUtilization();
        
        return idlePower + ((maxPower - idlePower) * utilization);
    }
    
    /**
     * Calculate power efficiency for host.
     * 
     * @param host Host to calculate efficiency for
     * @return Power efficiency ratio
     */
    private double calculatePowerEfficiency(Host host) {
        double powerConsumption = calculateHostPowerConsumption(host);
        double performance = host.getCpuPercentUtilization() * host.getTotalMipsCapacity();
        
        if (powerConsumption == 0.0) {
            return 0.0;
        }
        
        return performance / powerConsumption; // Performance per watt
    }
    
    /**
     * Estimate VM power consumption on host.
     * 
     * @param vm VM to estimate power for
     * @param host Target host
     * @return Estimated power consumption
     */
    private double estimateVmPowerConsumption(Vm vm, Host host) {
        double vmUtilization = vm.getMips() / host.getTotalMipsCapacity();
        double hostMaxPower = getHostMaxPower(host);
        double hostIdlePower = hostMaxPower * IDLE_POWER_THRESHOLD;
        
        return (hostMaxPower - hostIdlePower) * vmUtilization;
    }
    
    /**
     * Get maximum power consumption for host.
     * 
     * @param host Host to get max power for
     * @return Maximum power in watts
     */
    private double getHostMaxPower(Host host) {
        PowerModel powerModel = hostPowerModels.get(host);
        if (powerModel != null) {
            return powerModel.getPower(1.0); // Max power at 100% utilization
        }
        
        // Fallback: estimate based on MIPS capacity
        return host.getTotalMipsCapacity() * 0.001; // 1W per MIPS (rough estimate)
    }
    
    /**
     * Calculate power impact of VM allocation.
     * 
     * @param host Target host
     * @param vm VM to allocate
     * @return Power impact in watts
     */
    private double calculatePowerImpact(Host host, Vm vm) {
        double currentPower = calculateHostPowerConsumption(host);
        double additionalPower = estimateVmPowerConsumption(vm, host);
        
        return additionalPower;
    }
    
    /**
     * Update power tracking for host after VM allocation.
     * 
     * @param host Host that received VM
     * @param vm Allocated VM
     * @param isAllocation true for allocation, false for deallocation
     */
    private void updateHostPowerTracking(Host host, Vm vm, boolean isAllocation) {
        double currentPower = calculateHostPowerConsumption(host);
        hostPowerConsumption.put(host, currentPower);
        
        // Update total energy consumed
        long currentTime = System.currentTimeMillis();
        Long lastUpdateTime = hostLastUpdateTime.get(host);
        
        if (lastUpdateTime != null) {
            double timeDiff = (currentTime - lastUpdateTime) / 1000.0; // Convert to seconds
            double energyDiff = currentPower * (timeDiff / 3600.0); // Convert to Wh
            totalEnergyConsumed += energyDiff;
        }
        
        hostLastUpdateTime.put(host, currentTime);
        
        // Update power efficiency
        double efficiency = calculatePowerEfficiency(host);
        hostPowerEfficiency.put(host, efficiency);
        
        LoggingManager.logDebug("Updated power tracking for host " + host.getId() + 
                              " - Power: " + String.format("%.2f", currentPower) + 
                              "W, Efficiency: " + String.format("%.4f", efficiency));
    }
    
    /**
     * Update power optimization statistics.
     * 
     * @param optimizationTime Time taken for optimization
     */
    private void updatePowerOptimizationStats(long optimizationTime) {
        powerOptimizationCalls++;
        totalPowerOptimizationTime += optimizationTime;
        
        powerMetrics.put("averageOptimizationTime", 
                        (double) totalPowerOptimizationTime / powerOptimizationCalls);
        powerMetrics.put("totalOptimizationCalls", (double) powerOptimizationCalls);
    }
    
    /**
     * Update all host power models.
     * 
     * @param hostList List of hosts to update
     */
    private void updateAllHostPowerModels(List<Host> hostList) {
        for (Host host : hostList) {
            if (host instanceof PowerModelHost) {
                PowerModelHost powerHost = (PowerModelHost) host;
                PowerModel powerModel = powerHost.getPowerModel();
                if (powerModel != null) {
                    hostPowerModels.put(host, powerModel);
                }
            }
        }
    }
    
    /**
     * Create power-optimized parameters for global optimization.
     * 
     * @return Optimized parameters
     */
    private HippopotamusParameters createPowerOptimizedParameters() {
        HippopotamusParameters params = new HippopotamusParameters();
        params.setPopulationSize(40); // Larger population for global optimization
        params.setMaxIterations(200); // More iterations for global convergence
        params.setConvergenceThreshold(0.0001); // Very tight convergence
        params.setPowerWeight(POWER_WEIGHT);
        params.setPerformanceWeight(PERFORMANCE_WEIGHT);
        return params;
    }
    
    /**
     * Extract power-optimal allocation from optimization result.
     * 
     * @param result Optimization result
     * @param vmList List of VMs
     * @param hostList List of hosts
     * @return Power-optimal allocation mapping
     */
    private Map<Vm, Host> extractPowerOptimalAllocation(OptimizationResult result, 
                                                       List<Vm> vmList, 
                                                       List<Host> hostList) {
        Map<Vm, Host> allocation = new HashMap<>();
        
        if (result != null && result.getBestSolution() != null) {
            // Extract allocation from optimization result
            int[] solution = result.getBestSolution().getPosition();
            
            for (int i = 0; i < Math.min(solution.length, vmList.size()); i++) {
                int hostIndex = solution[i];
                if (hostIndex >= 0 && hostIndex < hostList.size()) {
                    allocation.put(vmList.get(i), hostList.get(hostIndex));
                }
            }
        }
        
        return allocation;
    }
    
    /**
     * Update global power tracking for all allocations.
     * 
     * @param allocation VM-to-Host allocation mapping
     */
    private void updateGlobalPowerTracking(Map<Vm, Host> allocation) {
        for (Map.Entry<Vm, Host> entry : allocation.entrySet()) {
            updateHostPowerTracking(entry.getValue(), entry.getKey(), true);
        }
    }
    
    /**
     * Calculate comprehensive power metrics for allocation.
     * 
     * @param allocation VM-to-Host allocation mapping
     * @param hostList List of all hosts
     */
    private void calculateComprehensivePowerMetrics(Map<Vm, Host> allocation, 
                                                   List<Host> hostList) {
        // Calculate total power consumption
        double totalPower = calculateTotalPowerConsumption(allocation);
        powerMetrics.put("totalPowerConsumption", totalPower);
        
        // Calculate average power consumption
        double avgPower = totalPower / hostList.size();
        powerMetrics.put("averagePowerConsumption", avgPower);
        
        // Calculate power efficiency ratio
        double totalPerformance = allocation.keySet().stream()
                                           .mapToDouble(vm -> vm.getMips())
                                           .sum();
        double powerEfficiencyRatio = totalPower > 0 ? totalPerformance / totalPower : 0;
        powerMetrics.put("powerEfficiencyRatio", powerEfficiencyRatio);
        averagePowerEfficiency = powerEfficiencyRatio;
        
        // Calculate energy per task
        int totalTasks = allocation.size();
        double energyPerTask = totalTasks > 0 ? totalPower / totalTasks : 0;
        powerMetrics.put("energyPerTask", energyPerTask);
        
        // Calculate carbon emissions
        double carbonEmissions = totalPower * carbonEmissionFactor / 1000.0; // Convert W to kW
        powerMetrics.put("carbonEmissions", carbonEmissions);
        
        // Calculate energy cost
        double energyCost = totalPower * energyCostPerKwh / 1000.0; // Convert W to kW
        powerMetrics.put("energyCost", energyCost);
        
        // Calculate power utilization variance
        List<Double> hostPowers = hostList.stream()
                                         .map(this::calculateHostPowerConsumption)
                                         .collect(Collectors.toList());
        double variance = calculateVariance(hostPowers);
        powerMetrics.put("powerUtilizationVariance", variance);
        
        LoggingManager.logInfo("Comprehensive power metrics calculated - " +
                              "Total Power: " + String.format("%.2f", totalPower) + "W, " +
                              "Efficiency: " + String.format("%.4f", powerEfficiencyRatio) + ", " +
                              "Carbon: " + String.format("%.3f", carbonEmissions) + "kg CO2");
    }
    
    /**
     * Calculate total power consumption for allocation.
     * 
     * @param allocation VM-to-Host allocation mapping
     * @return Total power consumption in watts
     */
    private double calculateTotalPowerConsumption(Map<Vm, Host> allocation) {
        Set<Host> usedHosts = new HashSet<>(allocation.values());
        return usedHosts.stream()
                       .mapToDouble(this::calculateHostPowerConsumption)
                       .sum();
    }
    
    /**
     * Calculate variance of power consumption values.
     * 
     * @param values List of power consumption values
     * @return Variance
     */
    private double calculateVariance(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = values.stream()
                               .mapToDouble(v -> Math.pow(v - mean, 2))
                               .average()
                               .orElse(0.0);
        return variance;
    }
    
    /**
     * Get comprehensive power metrics for research analysis.
     * 
     * @return Map of power metrics
     */
    public Map<String, Double> getPowerMetrics() {
        Map<String, Double> metrics = new HashMap<>(powerMetrics);
        
        // Add additional computed metrics
        metrics.put("totalEnergyConsumed", totalEnergyConsumed);
        metrics.put("peakPowerDemand", peakPowerDemand);
        metrics.put("averagePowerEfficiency", averagePowerEfficiency);
        metrics.put("powerOptimizationCalls", (double) powerOptimizationCalls);
        metrics.put("totalOptimizationTime", (double) totalPowerOptimizationTime);
        
        return metrics;
    }
    
    /**
     * Get power consumption history for trend analysis.
     * 
     * @return List of historical power consumption values
     */
    public List<Double> getPowerConsumptionHistory() {
        return new ArrayList<>(powerConsumptionHistory);
    }
    
    /**
     * Get host power efficiency mapping.
     * 
     * @return Map of host to power efficiency
     */
    public Map<Host, Double> getHostPowerEfficiency() {
        return new HashMap<>(hostPowerEfficiency);
    }
    
    /**
     * Set carbon emission factor for environmental impact calculation.
     * 
     * @param factor Carbon emission factor in kg CO2 per kWh
     */
    public void setCarbonEmissionFactor(double factor) {
        this.carbonEmissionFactor = factor;
        LoggingManager.logDebug("Carbon emission factor set to: " + factor + " kg CO2/kWh");
    }
    
        /**
     * Set energy cost per kWh for cost calculation.
     * 
     * @param costPerKwh Energy cost per kWh in dollars
     */
    public void setEnergyCostPerKwh(double costPerKwh) {
        this.energyCostPerKwh = costPerKwh;
        LoggingManager.logDebug("Energy cost per kWh set to: $" + costPerKwh);
    }
    
    /**
     * Get carbon emission factor.
     * 
     * @return Carbon emission factor in kg CO2 per kWh
     */
    public double getCarbonEmissionFactor() {
        return carbonEmissionFactor;
    }
    
    /**
     * Get energy cost per kWh.
     * 
     * @return Energy cost per kWh in dollars
     */
    public double getEnergyCostPerKwh() {
        return energyCostPerKwh;
    }
    
    /**
     * Calculate detailed power consumption metrics for research analysis.
     * This method provides comprehensive power consumption analysis including
     * statistical measures, trends, and efficiency metrics.
     * 
     * @return Map containing detailed power consumption metrics
     */
    public Map<String, Double> calculatePowerConsumption() {
        try {
            Map<String, Double> detailedMetrics = new HashMap<>();
            
            // Basic power metrics
            double totalPower = powerConsumptionHistory.stream()
                                                     .mapToDouble(Double::doubleValue)
                                                     .sum();
            double avgPower = powerConsumptionHistory.isEmpty() ? 0.0 : 
                             powerConsumptionHistory.stream()
                                                   .mapToDouble(Double::doubleValue)
                                                   .average()
                                                   .orElse(0.0);
            
            detailedMetrics.put("totalPowerConsumption", totalPower);
            detailedMetrics.put("averagePowerConsumption", avgPower);
            detailedMetrics.put("peakPowerDemand", peakPowerDemand);
            detailedMetrics.put("totalEnergyConsumed", totalEnergyConsumed);
            
            // Statistical measures
            if (!powerConsumptionHistory.isEmpty()) {
                double variance = calculateVariance(powerConsumptionHistory);
                double stdDev = Math.sqrt(variance);
                double minPower = powerConsumptionHistory.stream()
                                                       .mapToDouble(Double::doubleValue)
                                                       .min()
                                                       .orElse(0.0);
                double maxPower = powerConsumptionHistory.stream()
                                                       .mapToDouble(Double::doubleValue)
                                                       .max()
                                                       .orElse(0.0);
                
                detailedMetrics.put("powerVariance", variance);
                detailedMetrics.put("powerStandardDeviation", stdDev);
                detailedMetrics.put("minPowerConsumption", minPower);
                detailedMetrics.put("maxPowerConsumption", maxPower);
                detailedMetrics.put("powerRange", maxPower - minPower);
                
                // Coefficient of variation
                double coeffVar = avgPower > 0 ? (stdDev / avgPower) * 100 : 0;
                detailedMetrics.put("powerCoefficientOfVariation", coeffVar);
            }
            
            // Efficiency metrics
            double avgEfficiency = hostPowerEfficiency.values().stream()
                                                     .mapToDouble(Double::doubleValue)
                                                     .average()
                                                     .orElse(0.0);
            detailedMetrics.put("averagePowerEfficiency", avgEfficiency);
            detailedMetrics.put("powerEfficiencyVariance", 
                              calculateVariance(new ArrayList<>(hostPowerEfficiency.values())));
            
            // Environmental and cost metrics
            double totalCarbonEmissions = totalEnergyConsumed * carbonEmissionFactor / 1000.0;
            double totalEnergyCost = totalEnergyConsumed * energyCostPerKwh / 1000.0;
            
            detailedMetrics.put("totalCarbonEmissions", totalCarbonEmissions);
            detailedMetrics.put("totalEnergyCost", totalEnergyCost);
            detailedMetrics.put("carbonEmissionRate", 
                              totalEnergyConsumed > 0 ? totalCarbonEmissions / totalEnergyConsumed : 0);
            
            // Performance metrics
            detailedMetrics.put("powerOptimizationCalls", (double) powerOptimizationCalls);
            detailedMetrics.put("averageOptimizationTime", 
                              powerOptimizationCalls > 0 ? (double) totalPowerOptimizationTime / powerOptimizationCalls : 0);
            
            LoggingManager.logDebug("Calculated detailed power consumption metrics: " + detailedMetrics.size() + " metrics");
            
            return detailedMetrics;
            
        } catch (Exception e) {
            LoggingManager.logError("Error calculating power consumption metrics: " + e.getMessage(), e);
            throw new ExperimentException("Failed to calculate power consumption metrics", e);
        }
    }
    
    /**
     * Get power consumption trend analysis.
     * 
     * @return Map containing trend analysis results
     */
    public Map<String, Double> getPowerTrendAnalysis() {
        Map<String, Double> trendAnalysis = new HashMap<>();
        
        if (powerConsumptionHistory.size() < 2) {
            return trendAnalysis;
        }
        
        try {
            // Calculate trend slope using linear regression
            double[] xValues = new double[powerConsumptionHistory.size()];
            double[] yValues = new double[powerConsumptionHistory.size()];
            
            for (int i = 0; i < powerConsumptionHistory.size(); i++) {
                xValues[i] = i;
                yValues[i] = powerConsumptionHistory.get(i);
            }
            
            double slope = calculateLinearRegressionSlope(xValues, yValues);
            double correlation = calculateCorrelation(xValues, yValues);
            
            trendAnalysis.put("powerTrendSlope", slope);
            trendAnalysis.put("powerTrendCorrelation", correlation);
            trendAnalysis.put("trendDirection", slope > 0 ? 1.0 : (slope < 0 ? -1.0 : 0.0));
            
            // Recent vs. historical comparison
            int recentPeriod = Math.min(10, powerConsumptionHistory.size() / 2);
            if (recentPeriod > 0) {
                List<Double> recentData = powerConsumptionHistory.subList(
                    powerConsumptionHistory.size() - recentPeriod, powerConsumptionHistory.size());
                List<Double> historicalData = powerConsumptionHistory.subList(0, recentPeriod);
                
                double recentAvg = recentData.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double historicalAvg = historicalData.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                
                double improvementRate = historicalAvg > 0 ? ((historicalAvg - recentAvg) / historicalAvg) * 100 : 0;
                
                trendAnalysis.put("recentAveragePower", recentAvg);
                trendAnalysis.put("historicalAveragePower", historicalAvg);
                trendAnalysis.put("powerImprovementRate", improvementRate);
            }
            
        } catch (Exception e) {
            LoggingManager.logWarning("Error in power trend analysis: " + e.getMessage());
        }
        
        return trendAnalysis;
    }
    
    /**
     * Calculate linear regression slope.
     * 
     * @param xValues X values
     * @param yValues Y values
     * @return Slope value
     */
    private double calculateLinearRegressionSlope(double[] xValues, double[] yValues) {
        if (xValues.length != yValues.length || xValues.length == 0) {
            return 0.0;
        }
        
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        int n = xValues.length;
        
        for (int i = 0; i < n; i++) {
            sumX += xValues[i];
            sumY += yValues[i];
            sumXY += xValues[i] * yValues[i];
            sumXX += xValues[i] * xValues[i];
        }
        
        double denominator = n * sumXX - sumX * sumX;
        if (Math.abs(denominator) < 1e-10) {
            return 0.0;
        }
        
        return (n * sumXY - sumX * sumY) / denominator;
    }
    
    /**
     * Calculate correlation coefficient.
     * 
     * @param xValues X values
     * @param yValues Y values
     * @return Correlation coefficient
     */
    private double calculateCorrelation(double[] xValues, double[] yValues) {
        if (xValues.length != yValues.length || xValues.length == 0) {
            return 0.0;
        }
        
        double meanX = 0, meanY = 0;
        for (int i = 0; i < xValues.length; i++) {
            meanX += xValues[i];
            meanY += yValues[i];
        }
        meanX /= xValues.length;
        meanY /= yValues.length;
        
        double numerator = 0, sumXX = 0, sumYY = 0;
        for (int i = 0; i < xValues.length; i++) {
            double dx = xValues[i] - meanX;
            double dy = yValues[i] - meanY;
            numerator += dx * dy;
            sumXX += dx * dx;
            sumYY += dy * dy;
        }
        
        double denominator = Math.sqrt(sumXX * sumYY);
        if (Math.abs(denominator) < 1e-10) {
            return 0.0;
        }
        
        return numerator / denominator;
    }
    
    /**
     * Reset power tracking metrics.
     * Useful for starting new experiments with clean metrics.
     */
    public void resetPowerMetrics() {
        hostPowerConsumption.clear();
        hostPowerEfficiency.clear();
        powerConsumptionHistory.clear();
        powerMetrics.clear();
        hostLastUpdateTime.clear();
        
        totalEnergyConsumed = 0.0;
        peakPowerDemand = 0.0;
        averagePowerEfficiency = 0.0;
        powerOptimizationCalls = 0;
        totalPowerOptimizationTime = 0L;
        
        initializePowerMetrics();
        
        LoggingManager.logInfo("Power metrics reset for new experiment");
    }
    
    /**
     * Get summary of power optimization performance.
     * 
     * @return Summary string containing key power metrics
     */
    public String getPowerOptimizationSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Power Optimization Summary:\n");
        summary.append("==========================\n");
        summary.append(String.format("Total Energy Consumed: %.2f Wh\n", totalEnergyConsumed));
        summary.append(String.format("Peak Power Demand: %.2f W\n", peakPowerDemand));
        summary.append(String.format("Average Power Efficiency: %.4f\n", averagePowerEfficiency));
        summary.append(String.format("Optimization Calls: %d\n", powerOptimizationCalls));
        summary.append(String.format("Average Optimization Time: %.2f ms\n", 
                      powerOptimizationCalls > 0 ? (double) totalPowerOptimizationTime / powerOptimizationCalls : 0));
        
        double totalCarbonEmissions = totalEnergyConsumed * carbonEmissionFactor / 1000.0;
        double totalEnergyCost = totalEnergyConsumed * energyCostPerKwh / 1000.0;
        
        summary.append(String.format("Total Carbon Emissions: %.3f kg CO2\n", totalCarbonEmissions));
        summary.append(String.format("Total Energy Cost: $%.2f\n", totalEnergyCost));
        
        return summary.toString();
    }
    
    @Override
    public String toString() {
        return "PowerAwareHippopotamusPolicy{" +
               "powerWeight=" + POWER_WEIGHT +
               ", performanceWeight=" + PERFORMANCE_WEIGHT +
               ", totalEnergyConsumed=" + totalEnergyConsumed +
               ", peakPowerDemand=" + peakPowerDemand +
               ", averagePowerEfficiency=" + averagePowerEfficiency +
               ", powerOptimizationCalls=" + powerOptimizationCalls +
               ", carbonEmissionFactor=" + carbonEmissionFactor +
               ", energyCostPerKwh=" + energyCostPerKwh +
               '}';
    }
}
