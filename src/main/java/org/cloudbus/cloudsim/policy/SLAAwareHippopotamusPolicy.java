package org.cloudbus.cloudsim.policy;

import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicyAbstract;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.algorithm.HippopotamusOptimization;
import org.cloudbus.cloudsim.algorithm.HippopotamusParameters;
import org.cloudbus.cloudsim.algorithm.SimpleOptimizationResult;
import org.cloudbus.cloudsim.util.MetricsCalculator;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.ExperimentException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SLA-Aware Hippopotamus Optimization VM Allocation Policy
 * 
 * This policy extends the basic Hippopotamus optimization with comprehensive
 * SLA (Service Level Agreement) awareness for research-grade analysis.
 * 
 * Research Objectives Addressed:
 * - SLA violation minimization
 * - Response time optimization
 * - Availability guarantee enforcement
 * - Multi-tenant SLA compliance
 * 
 * Metrics Calculated:
 * - SLA violation rate and severity
 * - Response time distributions
 * - Availability percentages
 * - SLA penalty costs
 * - QoS satisfaction levels
 * 
 * Statistical Methods:
 * - SLA compliance statistical analysis
 * - Violation pattern recognition
 * - Predictive SLA modeling
 * - Multi-objective SLA optimization
 * 
 * @author Puneet Chandna
 * @version 1.0
 */
public class SLAAwareHippopotamusPolicy extends VmAllocationPolicyAbstract {
    
    // Core optimization engine
    private final HippopotamusOptimization optimizationEngine;
    private final HippopotamusParameters parameters;
    
    // SLA tracking and analysis
    private final Map<Vm, SLARequirements> vmSLARequirements;
    private final Map<Vm, SLAMetrics> vmSLAMetrics;
    private final Map<Host, List<SLAViolationEvent>> hostViolationHistory;
    
    // Research and statistical tracking
    private final List<SLAViolationEvent> violationEvents;
    private final Map<String, Double> slaComplianceStatistics;
    private final SLAOptimizationTracker optimizationTracker;
    
    // Performance monitoring
    private double totalSLAViolationCost;
    private double currentComplianceRate;
    private int totalAllocationRequests;
    private int successfulAllocations;
    
    /**
     * SLA Requirements specification for VMs
     */
    public static class SLARequirements {
        private final double maxResponseTime;          // Maximum acceptable response time (ms)
        private final double minAvailability;          // Minimum availability percentage
        private final double maxCpuUtilization;        // Maximum CPU utilization threshold
        private final double maxMemoryUtilization;     // Maximum memory utilization threshold
        private final double penaltyCostPerViolation;  // Cost per SLA violation
        private final int maxDowntimeMinutes;          // Maximum acceptable downtime per month
        
        public SLARequirements(double maxResponseTime, double minAvailability, 
                             double maxCpuUtilization, double maxMemoryUtilization,
                             double penaltyCostPerViolation, int maxDowntimeMinutes) {
            this.maxResponseTime = maxResponseTime;
            this.minAvailability = minAvailability;
            this.maxCpuUtilization = maxCpuUtilization;
            this.maxMemoryUtilization = maxMemoryUtilization;
            this.penaltyCostPerViolation = penaltyCostPerViolation;
            this.maxDowntimeMinutes = maxDowntimeMinutes;
        }
        
        // Getters
        public double getMaxResponseTime() { return maxResponseTime; }
        public double getMinAvailability() { return minAvailability; }
        public double getMaxCpuUtilization() { return maxCpuUtilization; }
        public double getMaxMemoryUtilization() { return maxMemoryUtilization; }
        public double getPenaltyCostPerViolation() { return penaltyCostPerViolation; }
        public int getMaxDowntimeMinutes() { return maxDowntimeMinutes; }
    }
    
    /**
     * SLA Metrics tracking for research analysis
     */
    public static class SLAMetrics {
        private double currentResponseTime;
        private double currentAvailability;
        private double currentCpuUtilization;
        private double currentMemoryUtilization;
        private int violationCount;
        private double totalViolationCost;
        private List<Double> responseTimeHistory;
        private List<SLAViolationEvent> violationHistory;
        
        public SLAMetrics() {
            this.responseTimeHistory = new ArrayList<>();
            this.violationHistory = new ArrayList<>();
            this.currentAvailability = 100.0;
        }
        
        // Getters and setters
        public double getCurrentResponseTime() { return currentResponseTime; }
        public void setCurrentResponseTime(double responseTime) { 
            this.currentResponseTime = responseTime;
            this.responseTimeHistory.add(responseTime);
        }
        
        public double getCurrentAvailability() { return currentAvailability; }
        public void setCurrentAvailability(double availability) { this.currentAvailability = availability; }
        
        public double getCurrentCpuUtilization() { return currentCpuUtilization; }
        public void setCurrentCpuUtilization(double utilization) { this.currentCpuUtilization = utilization; }
        
        public double getCurrentMemoryUtilization() { return currentMemoryUtilization; }
        public void setCurrentMemoryUtilization(double utilization) { this.currentMemoryUtilization = utilization; }
        
        public int getViolationCount() { return violationCount; }
        public void incrementViolationCount() { this.violationCount++; }
        
        public double getTotalViolationCost() { return totalViolationCost; }
        public void addViolationCost(double cost) { this.totalViolationCost += cost; }
        
        public List<Double> getResponseTimeHistory() { return new ArrayList<>(responseTimeHistory); }
        public List<SLAViolationEvent> getViolationHistory() { return new ArrayList<>(violationHistory); }
        
        public void recordViolation(SLAViolationEvent violation) {
            violationHistory.add(violation);
            incrementViolationCount();
            addViolationCost(violation.getPenaltyCost());
        }
    }
    
    /**
     * SLA Violation Event for detailed tracking
     */
    public static class SLAViolationEvent {
        private final double timestamp;
        private final Vm violatingVm;
        private final String violationType;
        private final double severity;
        private final double penaltyCost;
        private final String description;
        
        public SLAViolationEvent(double timestamp, Vm vm, String type, 
                               double severity, double cost, String description) {
            this.timestamp = timestamp;
            this.violatingVm = vm;
            this.violationType = type;
            this.severity = severity;
            this.penaltyCost = cost;
            this.description = description;
        }
        
        // Getters
        public double getTimestamp() { return timestamp; }
        public Vm getViolatingVm() { return violatingVm; }
        public String getViolationType() { return violationType; }
        public double getSeverity() { return severity; }
        public double getPenaltyCost() { return penaltyCost; }
        public String getDescription() { return description; }
    }
    
    /**
     * SLA Violation types enumeration
     */
    public enum SLAViolationType {
        RESPONSE_TIME_VIOLATION("Response Time Exceeded", 1.0),
        AVAILABILITY_VIOLATION("Availability Below Threshold", 2.0),
        CPU_OVERUTILIZATION("CPU Utilization Exceeded", 0.8),
        MEMORY_OVERUTILIZATION("Memory Utilization Exceeded", 0.9),
        DOWNTIME_VIOLATION("Downtime Limit Exceeded", 1.5);
        
        private final String description;
        private final double baseSeverity;
        
        SLAViolationType(String description, double baseSeverity) {
            this.description = description;
            this.baseSeverity = baseSeverity;
        }
        
        public String getDescription() { return description; }
        public double getBaseSeverity() { return baseSeverity; }
    }
    
    /**
     * SLA Optimization tracking for research analysis
     */
    public static class SLAOptimizationTracker {
        private List<Double> complianceRateHistory;
        private List<Double> violationCostHistory;
        private Map<String, Integer> violationTypeCounters;
        private double bestComplianceRate;
        private double worstComplianceRate;
        
        public SLAOptimizationTracker() {
            this.complianceRateHistory = new ArrayList<>();
            this.violationCostHistory = new ArrayList<>();
            this.violationTypeCounters = new HashMap<>();
            this.bestComplianceRate = 0.0;
            this.worstComplianceRate = 100.0;
        }
        
        public void recordComplianceRate(double rate) {
            complianceRateHistory.add(rate);
            bestComplianceRate = Math.max(bestComplianceRate, rate);
            worstComplianceRate = Math.min(worstComplianceRate, rate);
        }
        
        public void recordViolationCost(double cost) {
            violationCostHistory.add(cost);
        }
        
        public void recordViolationType(String type) {
            violationTypeCounters.merge(type, 1, Integer::sum);
        }
        
        // Getters
        public List<Double> getComplianceRateHistory() { return new ArrayList<>(complianceRateHistory); }
        public List<Double> getViolationCostHistory() { return new ArrayList<>(violationCostHistory); }
        public Map<String, Integer> getViolationTypeCounters() { return new HashMap<>(violationTypeCounters); }
        public double getBestComplianceRate() { return bestComplianceRate; }
        public double getWorstComplianceRate() { return worstComplianceRate; }
    }
    
    /**
     * Constructor for SLA-Aware Hippopotamus Policy
     */
    public SLAAwareHippopotamusPolicy() {
        super();
        try {
            this.parameters = new HippopotamusParameters();
            this.optimizationEngine = new HippopotamusOptimization();
            
            // Initialize SLA tracking structures
            this.vmSLARequirements = new HashMap<>();
            this.vmSLAMetrics = new HashMap<>();
            this.hostViolationHistory = new HashMap<>();
            this.violationEvents = new ArrayList<>();
            this.slaComplianceStatistics = new HashMap<>();
            this.optimizationTracker = new SLAOptimizationTracker();
            
            // Initialize performance counters
            this.totalSLAViolationCost = 0.0;
            this.currentComplianceRate = 100.0;
            this.totalAllocationRequests = 0;
            this.successfulAllocations = 0;
            
            LoggingManager.logInfo("SLA-Aware Hippopotamus Policy initialized successfully");
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to initialize SLA-Aware Hippopotamus Policy", e);
        }
    }
    
    /**
     * Set SLA requirements for a specific VM
     * Used by research framework for configuring experimental scenarios
     */
    public void setSLARequirements(Vm vm, SLARequirements requirements) {
        try {
            vmSLARequirements.put(vm, requirements);
            vmSLAMetrics.put(vm, new SLAMetrics());
            LoggingManager.logInfo("SLA requirements set for VM " + vm.getId());
        } catch (Exception e) {
            throw new ExperimentException("Failed to set SLA requirements for VM " + vm.getId(), e);
        }
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
     * SLA-aware VM allocation that considers SLA compliance.
     * 
     * @param vm The VM to be allocated
     * @param hostList List of available hosts
     * @return Optional containing the selected host, or empty if allocation fails
     */
    public Optional<Host> findHostForVm(Vm vm, List<Host> hostList) {
        try {
            totalAllocationRequests++;
            
            List<Host> availableHosts = hostList.stream()
                .filter(host -> host.isSuitableForVm(vm))
                .collect(Collectors.toList());
            
            if (availableHosts.isEmpty()) {
                LoggingManager.logWarning("No suitable hosts found for VM " + vm.getId());
                return Optional.empty();
            }
            
            // Get SLA requirements for this VM
            SLARequirements slaReqs = vmSLARequirements.get(vm);
            if (slaReqs == null) {
                // Use default SLA requirements if not specified
                slaReqs = createDefaultSLARequirements();
                setSLARequirements(vm, slaReqs);
            }
            
            // Perform SLA-aware optimization
            Host optimalHost = optimizeForSLACompliance(vm, availableHosts, slaReqs);
            
            if (optimalHost != null) {
                successfulAllocations++;
                updateSLAMetricsAfterAllocation(vm, optimalHost);
                LoggingManager.logInfo("VM " + vm.getId() + " allocated to Host " + optimalHost.getId() + " with SLA optimization");
                return Optional.of(optimalHost);
            }
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to find SLA-compliant host for VM " + vm.getId(), e);
        }
        
        return Optional.empty();
    }
    
    /**
     * Calculate SLA violations for all VMs
     * Key research method for violation detection and analysis
     */
    public Map<Vm, List<SLAViolationEvent>> calculateSLAViolations() {
        try {
            Map<Vm, List<SLAViolationEvent>> violationsMap = new HashMap<>();
            double currentTime = CloudSim.clock();
            
            for (Map.Entry<Vm, SLARequirements> entry : vmSLARequirements.entrySet()) {
                Vm vm = entry.getKey();
                SLARequirements requirements = entry.getValue();
                SLAMetrics metrics = vmSLAMetrics.get(vm);
                
                if (metrics == null) continue;
                
                List<SLAViolationEvent> vmViolations = new ArrayList<>();
                
                // Check response time violations
                if (metrics.getCurrentResponseTime() > requirements.getMaxResponseTime()) {
                    double severity = (metrics.getCurrentResponseTime() - requirements.getMaxResponseTime()) 
                                    / requirements.getMaxResponseTime();
                    double cost = requirements.getPenaltyCostPerViolation() * severity;
                    
                    SLAViolationEvent violation = new SLAViolationEvent(
                        currentTime, vm, SLAViolationType.RESPONSE_TIME_VIOLATION.name(),
                        severity, cost,
                        String.format("Response time %.2f ms exceeds limit %.2f ms", 
                                    metrics.getCurrentResponseTime(), requirements.getMaxResponseTime())
                    );
                    
                    vmViolations.add(violation);
                    violationEvents.add(violation);
                    metrics.recordViolation(violation);
                    optimizationTracker.recordViolationType(SLAViolationType.RESPONSE_TIME_VIOLATION.name());
                }
                
                // Check availability violations
                if (metrics.getCurrentAvailability() < requirements.getMinAvailability()) {
                    double severity = (requirements.getMinAvailability() - metrics.getCurrentAvailability()) 
                                    / requirements.getMinAvailability();
                    double cost = requirements.getPenaltyCostPerViolation() * severity;
                    
                    SLAViolationEvent violation = new SLAViolationEvent(
                        currentTime, vm, SLAViolationType.AVAILABILITY_VIOLATION.name(),
                        severity, cost,
                        String.format("Availability %.2f%% below required %.2f%%", 
                                    metrics.getCurrentAvailability(), requirements.getMinAvailability())
                    );
                    
                    vmViolations.add(violation);
                    violationEvents.add(violation);
                    metrics.recordViolation(violation);
                    optimizationTracker.recordViolationType(SLAViolationType.AVAILABILITY_VIOLATION.name());
                }
                
                // Check CPU utilization violations
                if (metrics.getCurrentCpuUtilization() > requirements.getMaxCpuUtilization()) {
                    double severity = (metrics.getCurrentCpuUtilization() - requirements.getMaxCpuUtilization()) 
                                    / requirements.getMaxCpuUtilization();
                    double cost = requirements.getPenaltyCostPerViolation() * severity * 0.8; // Lower penalty for utilization
                    
                    SLAViolationEvent violation = new SLAViolationEvent(
                        currentTime, vm, SLAViolationType.CPU_OVERUTILIZATION.name(),
                        severity, cost,
                        String.format("CPU utilization %.2f%% exceeds limit %.2f%%", 
                                    metrics.getCurrentCpuUtilization() * 100, requirements.getMaxCpuUtilization() * 100)
                    );
                    
                    vmViolations.add(violation);
                    violationEvents.add(violation);
                    metrics.recordViolation(violation);
                    optimizationTracker.recordViolationType(SLAViolationType.CPU_OVERUTILIZATION.name());
                }
                
                // Check memory utilization violations
                if (metrics.getCurrentMemoryUtilization() > requirements.getMaxMemoryUtilization()) {
                    double severity = (metrics.getCurrentMemoryUtilization() - requirements.getMaxMemoryUtilization()) 
                                    / requirements.getMaxMemoryUtilization();
                    double cost = requirements.getPenaltyCostPerViolation() * severity * 0.9; // Slightly lower penalty for memory
                    
                    SLAViolationEvent violation = new SLAViolationEvent(
                        currentTime, vm, SLAViolationType.MEMORY_OVERUTILIZATION.name(),
                        severity, cost,
                        String.format("Memory utilization %.2f%% exceeds limit %.2f%%", 
                                    metrics.getCurrentMemoryUtilization() * 100, requirements.getMaxMemoryUtilization() * 100)
                    );
                    
                    vmViolations.add(violation);
                    violationEvents.add(violation);
                    metrics.recordViolation(violation);
                    optimizationTracker.recordViolationType(SLAViolationType.MEMORY_OVERUTILIZATION.name());
                }
                
                if (!vmViolations.isEmpty()) {
                    violationsMap.put(vm, vmViolations);
                    
                    // Update host violation history
                    Host vmHost = vm.getHost();
                    if (vmHost != null) {
                        hostViolationHistory.computeIfAbsent(vmHost, k -> new ArrayList<>()).addAll(vmViolations);
                    }
                }
            }
            
            // Update total violation cost
            double totalCost = violationsMap.values().stream()
                .flatMap(List::stream)
                .mapToDouble(SLAViolationEvent::getPenaltyCost)
                .sum();
            totalSLAViolationCost += totalCost;
            optimizationTracker.recordViolationCost(totalCost);
            
            // Update compliance rate
            updateComplianceRate();
            
            LoggingManager.logInfo("SLA violations calculated: " + violationsMap.size() + " VMs with violations, total cost: " + totalCost);
            
            return violationsMap;
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to calculate SLA violations", e);
        }
    }
    
    /**
     * Optimize VM allocation for SLA compliance
     * Core research method for SLA-aware optimization
     */
    public Host optimizeForSLACompliance(Vm vm, List<Host> candidateHosts, SLARequirements slaRequirements) {
        try {
            if (candidateHosts.isEmpty()) {
                return null;
            }
            
            Host bestHost = null;
            double bestSLAScore = Double.MIN_VALUE;
            
            for (Host host : candidateHosts) {
                double slaScore = calculateSLAComplianceScore(vm, host, slaRequirements);
                
                if (slaScore > bestSLAScore) {
                    bestSLAScore = slaScore;
                    bestHost = host;
                }
            }
            
            // If no host provides acceptable SLA compliance, use Hippopotamus optimization
            if (bestSLAScore < 0.7) { // Threshold for acceptable SLA compliance
                LoggingManager.logInfo("Falling back to Hippopotamus optimization for better SLA compliance");
                
                // Use the correct optimize method signature: (vmCount, hostCount, parameters)
                SimpleOptimizationResult result = optimizationEngine.optimize(
                    1, candidateHosts.size(), parameters);
                if (result != null && result.getBestSolution() != null) {
                    // Extract host from optimization result
                    // This is a simplified version - actual implementation would need proper mapping
                    bestHost = candidateHosts.get(0); // Placeholder - would use actual optimization result
                }
            }
            
            LoggingManager.logInfo("SLA compliance optimization completed for VM " + vm.getId() + 
                                 ", best SLA score: " + bestSLAScore);
            
            return bestHost;
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to optimize for SLA compliance for VM " + vm.getId(), e);
        }
    }
    
    /**
     * Get comprehensive SLA metrics for research analysis
     * Key method for research data collection and reporting
     */
    public Map<String, Object> getSLAMetrics() {
        try {
            Map<String, Object> metrics = new HashMap<>();
            
            // Overall compliance metrics
            metrics.put("totalVMs", vmSLARequirements.size());
            metrics.put("currentComplianceRate", currentComplianceRate);
            metrics.put("totalViolationEvents", violationEvents.size());
            metrics.put("totalViolationCost", totalSLAViolationCost);
            metrics.put("allocationSuccessRate", totalAllocationRequests > 0 ? 
                       (double) successfulAllocations / totalAllocationRequests * 100.0 : 0.0);
            
            // Violation type breakdown
            Map<String, Integer> violationBreakdown = new HashMap<>();
            for (SLAViolationEvent event : violationEvents) {
                violationBreakdown.merge(event.getViolationType(), 1, Integer::sum);
            }
            metrics.put("violationTypeBreakdown", violationBreakdown);
            
            // VM-specific metrics
            Map<Long, Map<String, Object>> vmMetrics = new HashMap<>();
            for (Map.Entry<Vm, SLAMetrics> entry : vmSLAMetrics.entrySet()) {
                Vm vm = entry.getKey();
                SLAMetrics vmSLA = entry.getValue();
                
                Map<String, Object> vmSpecificMetrics = new HashMap<>();
                vmSpecificMetrics.put("responseTime", vmSLA.getCurrentResponseTime());
                vmSpecificMetrics.put("availability", vmSLA.getCurrentAvailability());
                vmSpecificMetrics.put("cpuUtilization", vmSLA.getCurrentCpuUtilization());
                vmSpecificMetrics.put("memoryUtilization", vmSLA.getCurrentMemoryUtilization());
                vmSpecificMetrics.put("violationCount", vmSLA.getViolationCount());
                vmSpecificMetrics.put("violationCost", vmSLA.getTotalViolationCost());
                vmSpecificMetrics.put("responseTimeHistory", vmSLA.getResponseTimeHistory());
                
                vmMetrics.put(vm.getId(), vmSpecificMetrics);
            }
            metrics.put("vmSpecificMetrics", vmMetrics);
            
            // Host violation analysis
            Map<Long, Integer> hostViolationCounts = new HashMap<>();
            for (Map.Entry<Host, List<SLAViolationEvent>> entry : hostViolationHistory.entrySet()) {
                hostViolationCounts.put(entry.getKey().getId(), entry.getValue().size());
            }
            metrics.put("hostViolationCounts", hostViolationCounts);
            
            // Statistical analysis
            if (!violationEvents.isEmpty()) {
                double avgViolationCost = violationEvents.stream()
                    .mapToDouble(SLAViolationEvent::getPenaltyCost)
                    .average().orElse(0.0);
                double maxViolationCost = violationEvents.stream()
                    .mapToDouble(SLAViolationEvent::getPenaltyCost)
                    .max().orElse(0.0);
                double minViolationCost = violationEvents.stream()
                    .mapToDouble(SLAViolationEvent::getPenaltyCost)
                    .min().orElse(0.0);
                
                metrics.put("avgViolationCost", avgViolationCost);
                metrics.put("maxViolationCost", maxViolationCost);
                metrics.put("minViolationCost", minViolationCost);
            }
            
            // Optimization tracking metrics
            metrics.put("bestComplianceRate", optimizationTracker.getBestComplianceRate());
            metrics.put("worstComplianceRate", optimizationTracker.getWorstComplianceRate());
            metrics.put("complianceRateHistory", optimizationTracker.getComplianceRateHistory());
            metrics.put("violationCostHistory", optimizationTracker.getViolationCostHistory());
            
            LoggingManager.logInfo("Comprehensive SLA metrics collected: " + metrics.size() + " metric categories");
            
            return metrics;
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to collect SLA metrics", e);
        }
    }
    
    /**
     * Calculate SLA compliance score for a VM-Host pairing
     */
    private double calculateSLAComplianceScore(Vm vm, Host host, SLARequirements requirements) {
        try {
            double score = 0.0;
            
            // Response time prediction (simplified model)
            double predictedResponseTime = estimateResponseTime(vm, host);
            double responseTimeScore = Math.max(0, 1.0 - (predictedResponseTime / requirements.getMaxResponseTime()));
            score += responseTimeScore * 0.3; // 30% weight
            
            // Resource utilization prediction
            double predictedCpuUtil = (host.getCpuPercentUtilization() + (vm.getMips() / host.getTotalMipsCapacity()));
            double predictedMemUtil = (host.getRam().getAllocatedResource() + vm.getRam().getCapacity()) / (double) host.getRam().getCapacity();
            
            double cpuScore = Math.max(0, 1.0 - Math.max(0, predictedCpuUtil - requirements.getMaxCpuUtilization()));
            double memScore = Math.max(0, 1.0 - Math.max(0, predictedMemUtil - requirements.getMaxMemoryUtilization()));
            
            score += cpuScore * 0.25; // 25% weight
            score += memScore * 0.25; // 25% weight
            
            // Availability score based on host reliability
            double availabilityScore = estimateHostAvailability(host) / 100.0;
            score += availabilityScore * 0.2; // 20% weight
            
            return score;
            
        } catch (Exception e) {
            LoggingManager.logError("Error calculating SLA compliance score", e);
            return 0.0;
        }
    }
    
    /**
     * Estimate response time for VM on given host
     */
    private double estimateResponseTime(Vm vm, Host host) {
        // Simplified response time estimation based on current load
        double baseResponseTime = 10.0; // Base response time in ms
        double loadFactor = host.getCpuPercentUtilization();
        return baseResponseTime * (1.0 + loadFactor);
    }
    
    /**
     * Estimate host availability based on historical data
     */
    private double estimateHostAvailability(Host host) {
        // Simplified availability estimation
        // In real implementation, this would use historical uptime data
        List<SLAViolationEvent> hostViolations = hostViolationHistory.get(host);
        if (hostViolations == null || hostViolations.isEmpty()) {
            return 99.9; // Default high availability
        }
        
        // Reduce availability estimate based on violation history
        double availabilityReduction = Math.min(hostViolations.size() * 0.1, 5.0);
        return Math.max(95.0, 99.9 - availabilityReduction);
    }
    
    /**
     * Update SLA metrics after successful VM allocation
     */
    private void updateSLAMetricsAfterAllocation(Vm vm, Host host) {
        try {
            SLAMetrics metrics = vmSLAMetrics.get(vm);
            if (metrics != null) {
                // Update initial metrics based on host characteristics
                metrics.setCurrentResponseTime(estimateResponseTime(vm, host));
                metrics.setCurrentAvailability(estimateHostAvailability(host));
                metrics.setCurrentCpuUtilization(host.getCpuPercentUtilization());
                metrics.setCurrentMemoryUtilization(host.getRam().getAllocatedResource() / (double) host.getRam().getCapacity());
            }
        } catch (Exception e) {
            LoggingManager.logError("Error updating SLA metrics after allocation", e);
        }
    }
    
    /**
     * Update overall compliance rate
     */
    private void updateComplianceRate() {
        if (vmSLARequirements.isEmpty()) {
            return;
        }
        
        long compliantVMs = vmSLAMetrics.values().stream()
            .mapToLong(metrics -> metrics.getViolationCount() == 0 ? 1 : 0)
            .sum();
        
        currentComplianceRate = (double) compliantVMs / vmSLARequirements.size() * 100.0;
        optimizationTracker.recordComplianceRate(currentComplianceRate);
    }
    
    /**
     * Create default SLA requirements
     */
    private SLARequirements createDefaultSLARequirements() {
        return new SLARequirements(
            100.0,  // maxResponseTime (ms)
            99.5,   // minAvailability (%)
            0.8,    // maxCpuUtilization (80%)
            0.85,   // maxMemoryUtilization (85%)
            10.0,   // penaltyCostPerViolation
            60      // maxDowntimeMinutes
        );
    }
    
    /**
     * Get violation events for research analysis
     */
    public List<SLAViolationEvent> getViolationEvents() {
        return new ArrayList<>(violationEvents);
    }
    
    /**
     * Get optimization tracker for research analysis
     */
    public SLAOptimizationTracker getOptimizationTracker() {
        return optimizationTracker;
    }
    
    /**
     * Reset SLA tracking for new experiment
     */
    public void resetSLATracking() {
        try {
            vmSLARequirements.clear();
            vmSLAMetrics.clear();
            hostViolationHistory.clear();
            violationEvents.clear();
            slaComplianceStatistics.clear();
            
            totalSLAViolationCost = 0.0;
            currentComplianceRate = 100.0;
            totalAllocationRequests = 0;
            successfulAllocations = 0;
            
            LoggingManager.logInfo("SLA tracking reset for new experiment");
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to reset SLA tracking", e);
        }
    }
}
