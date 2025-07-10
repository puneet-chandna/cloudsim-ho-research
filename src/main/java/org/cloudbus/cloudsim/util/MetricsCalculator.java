package org.cloudbus.cloudsim.util;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.Precision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MetricsCalculator provides comprehensive performance metrics calculation
 * for CloudSim research experiments. This class ensures consistent metric
 * calculation across all components in the research framework.
 */
public class MetricsCalculator {
    private static final Logger logger = LoggerFactory.getLogger(MetricsCalculator.class);
    
    // Precision for floating point comparisons
    private static final double PRECISION = 0.0001;
    
    // SLA violation thresholds
    private static final double SLA_CPU_THRESHOLD = 0.8;
    private static final double SLA_RAM_THRESHOLD = 0.8;
    private static final double SLA_RESPONSE_TIME_THRESHOLD = 10.0; // seconds
    
    /**
     * Calculate resource utilization metrics for hosts
     * 
     * @param hosts List of hosts to analyze
     * @return Map containing utilization metrics
     */
    public static Map<String, Double> calculateResourceUtilization(List<Host> hosts) {
        logger.debug("Calculating resource utilization for {} hosts", hosts.size());
        
        Map<String, Double> metrics = new HashMap<>();
        
        if (hosts.isEmpty()) {
            logger.warn("No hosts provided for utilization calculation");
            return getEmptyUtilizationMetrics();
        }
        
        DescriptiveStatistics cpuStats = new DescriptiveStatistics();
        DescriptiveStatistics ramStats = new DescriptiveStatistics();
        DescriptiveStatistics bwStats = new DescriptiveStatistics();
        DescriptiveStatistics storageStats = new DescriptiveStatistics();
        
        double totalCpuUsed = 0, totalCpuCapacity = 0;
        double totalRamUsed = 0, totalRamCapacity = 0;
        double totalBwUsed = 0, totalBwCapacity = 0;
        double totalStorageUsed = 0, totalStorageCapacity = 0;
        
        for (Host host : hosts) {
            // CPU utilization
            double cpuUsed = host.getCpuMipsUtilization() * host.getTotalMipsCapacity();
            double cpuCapacity = host.getTotalMipsCapacity();
            double cpuUtilization = cpuCapacity > 0 ? cpuUsed / cpuCapacity : 0;
            
            // RAM utilization
            double ramUsed = host.getRamUtilization() * host.getRam().getCapacity();
            double ramCapacity = host.getRam().getCapacity();
            double ramUtilization = ramCapacity > 0 ? ramUsed / ramCapacity : 0;
            
            // Bandwidth utilization
            double bwUsed = host.getBwUtilization() * host.getBw().getCapacity();
            double bwCapacity = host.getBw().getCapacity();
            double bwUtilization = bwCapacity > 0 ? bwUsed / bwCapacity : 0;
            
            // Storage utilization
            double storageUsed = host.getStorageUtilization() * host.getStorage().getCapacity();
            double storageCapacity = host.getStorage().getCapacity();
            double storageUtilization = storageCapacity > 0 ? storageUsed / storageCapacity : 0;
            
            // Add to statistics
            cpuStats.addValue(cpuUtilization);
            ramStats.addValue(ramUtilization);
            bwStats.addValue(bwUtilization);
            storageStats.addValue(storageUtilization);
            
            // Add to totals
            totalCpuUsed += cpuUsed;
            totalCpuCapacity += cpuCapacity;
            totalRamUsed += ramUsed;
            totalRamCapacity += ramCapacity;
            totalBwUsed += bwUsed;
            totalBwCapacity += bwCapacity;
            totalStorageUsed += storageUsed;
            totalStorageCapacity += storageCapacity;
        }
        
        // Overall utilization metrics
        metrics.put("overall_cpu_utilization", totalCpuCapacity > 0 ? totalCpuUsed / totalCpuCapacity : 0);
        metrics.put("overall_ram_utilization", totalRamCapacity > 0 ? totalRamUsed / totalRamCapacity : 0);
        metrics.put("overall_bw_utilization", totalBwCapacity > 0 ? totalBwUsed / totalBwCapacity : 0);
        metrics.put("overall_storage_utilization", totalStorageCapacity > 0 ? totalStorageUsed / totalStorageCapacity : 0);
        
        // Statistical metrics
        metrics.put("mean_cpu_utilization", cpuStats.getMean());
        metrics.put("std_cpu_utilization", cpuStats.getStandardDeviation());
        metrics.put("max_cpu_utilization", cpuStats.getMax());
        metrics.put("min_cpu_utilization", cpuStats.getMin());
        
        metrics.put("mean_ram_utilization", ramStats.getMean());
        metrics.put("std_ram_utilization", ramStats.getStandardDeviation());
        metrics.put("max_ram_utilization", ramStats.getMax());
        metrics.put("min_ram_utilization", ramStats.getMin());
        
        // Resource imbalance
        metrics.put("cpu_ram_imbalance", Math.abs(cpuStats.getMean() - ramStats.getMean()));
        
        // Active hosts ratio
        long activeHosts = hosts.stream().mapToLong(host -> host.getVmList().size() > 0 ? 1 : 0).sum();
        metrics.put("active_hosts_ratio", (double) activeHosts / hosts.size());
        
        logger.debug("Resource utilization calculation completed");
        return metrics;
    }
    
    /**
     * Calculate power consumption metrics
     * 
     * @param hosts List of hosts with power models
     * @return Map containing power consumption metrics
     */
    public static Map<String, Double> calculatePowerConsumption(List<Host> hosts) {
        logger.debug("Calculating power consumption for {} hosts", hosts.size());
        
        Map<String, Double> metrics = new HashMap<>();
        
        if (hosts.isEmpty()) {
            logger.warn("No hosts provided for power calculation");
            return getEmptyPowerMetrics();
        }
        
        DescriptiveStatistics powerStats = new DescriptiveStatistics();
        double totalPower = 0;
        double totalIdlePower = 0;
        double totalMaxPower = 0;
        int hostsWithPowerModel = 0;
        
        for (Host host : hosts) {
            PowerModel powerModel = host.getPowerModel();
            if (powerModel != null) {
                hostsWithPowerModel++;
                
                // Current power consumption
                double currentPower = powerModel.getPower(host.getCpuMipsUtilization());
                
                // Idle power consumption
                double idlePower = powerModel.getPower(0);
                
                // Maximum power consumption
                double maxPower = powerModel.getPower(1.0);
                
                powerStats.addValue(currentPower);
                totalPower += currentPower;
                totalIdlePower += idlePower;
                totalMaxPower += maxPower;
            }
        }
        
        if (hostsWithPowerModel == 0) {
            logger.warn("No hosts with power models found");
            return getEmptyPowerMetrics();
        }
        
        metrics.put("total_power_consumption", totalPower);
        metrics.put("average_power_consumption", totalPower / hostsWithPowerModel);
        metrics.put("total_idle_power", totalIdlePower);
        metrics.put("total_max_power", totalMaxPower);
        metrics.put("power_efficiency", totalMaxPower > 0 ? (totalMaxPower - totalPower) / totalMaxPower : 0);
        
        metrics.put("mean_host_power", powerStats.getMean());
        metrics.put("std_host_power", powerStats.getStandardDeviation());
        metrics.put("max_host_power", powerStats.getMax());
        metrics.put("min_host_power", powerStats.getMin());
        
        // Power Usage Effectiveness (PUE) approximation
        double pue = totalPower > 0 ? (totalPower + totalIdlePower * 0.1) / totalPower : 1.0;
        metrics.put("power_usage_effectiveness", pue);
        
        logger.debug("Power consumption calculation completed");
        return metrics;
    }
    
    /**
     * Calculate SLA violation metrics
     * 
     * @param vms List of VMs to check for violations
     * @param cloudlets List of cloudlets for response time analysis
     * @return Map containing SLA violation metrics
     */
    public static Map<String, Double> calculateSLAViolations(List<Vm> vms, List<Cloudlet> cloudlets) {
        logger.debug("Calculating SLA violations for {} VMs and {} cloudlets", vms.size(), cloudlets.size());
        
        Map<String, Double> metrics = new HashMap<>();
        
        if (vms.isEmpty()) {
            logger.warn("No VMs provided for SLA violation calculation");
            return getEmptySLAMetrics();
        }
        
        int cpuViolations = 0;
        int ramViolations = 0;
        int responseTimeViolations = 0;
        
        DescriptiveStatistics responseTimeStats = new DescriptiveStatistics();
        DescriptiveStatistics cpuUtilStats = new DescriptiveStatistics();
        DescriptiveStatistics ramUtilStats = new DescriptiveStatistics();
        
        // Check resource utilization violations
        for (Vm vm : vms) {
            double cpuUtil = vm.getCpuPercentUtilization();
            double ramUtil = vm.getRam().getPercentUtilization();
            
            cpuUtilStats.addValue(cpuUtil);
            ramUtilStats.addValue(ramUtil);
            
            if (cpuUtil > SLA_CPU_THRESHOLD) {
                cpuViolations++;
            }
            
            if (ramUtil > SLA_RAM_THRESHOLD) {
                ramViolations++;
            }
        }
        
        // Check response time violations
        for (Cloudlet cloudlet : cloudlets) {
            if (cloudlet.isFinished()) {
                double responseTime = cloudlet.getActualCpuTime();
                responseTimeStats.addValue(responseTime);
                
                if (responseTime > SLA_RESPONSE_TIME_THRESHOLD) {
                    responseTimeViolations++;
                }
            }
        }
        
        // Calculate violation ratios
        metrics.put("cpu_violation_ratio", vms.size() > 0 ? (double) cpuViolations / vms.size() : 0);
        metrics.put("ram_violation_ratio", vms.size() > 0 ? (double) ramViolations / vms.size() : 0);
        metrics.put("response_time_violation_ratio", cloudlets.size() > 0 ? (double) responseTimeViolations / cloudlets.size() : 0);
        
        // Overall SLA violation
        double totalViolations = cpuViolations + ramViolations + responseTimeViolations;
        double totalChecks = vms.size() * 2 + cloudlets.size(); // CPU + RAM per VM + response time per cloudlet
        metrics.put("overall_sla_violation_ratio", totalChecks > 0 ? totalViolations / totalChecks : 0);
        
        // Statistical metrics
        metrics.put("mean_response_time", responseTimeStats.getMean());
        metrics.put("max_response_time", responseTimeStats.getMax());
        metrics.put("std_response_time", responseTimeStats.getStandardDeviation());
        
        metrics.put("mean_cpu_utilization", cpuUtilStats.getMean());
        metrics.put("mean_ram_utilization", ramUtilStats.getMean());
        
        logger.debug("SLA violation calculation completed");
        return metrics;
    }
    
    /**
     * Calculate throughput metrics
     * 
     * @param cloudlets List of completed cloudlets
     * @param simulationTime Total simulation time
     * @return Map containing throughput metrics
     */
    public static Map<String, Double> calculateThroughput(List<Cloudlet> cloudlets, double simulationTime) {
        logger.debug("Calculating throughput for {} cloudlets over {} time units", cloudlets.size(), simulationTime);
        
        Map<String, Double> metrics = new HashMap<>();
        
        if (cloudlets.isEmpty() || simulationTime <= 0) {
            logger.warn("Invalid parameters for throughput calculation");
            return getEmptyThroughputMetrics();
        }
        
        List<Cloudlet> finishedCloudlets = cloudlets.stream()
                .filter(Cloudlet::isFinished)
                .collect(Collectors.toList());
        
        // Basic throughput
        double throughput = finishedCloudlets.size() / simulationTime;
        metrics.put("cloudlet_throughput", throughput);
        
        // MIPS throughput
        double totalMips = finishedCloudlets.stream()
                .mapToDouble(cloudlet -> cloudlet.getLength())
                .sum();
        metrics.put("mips_throughput", totalMips / simulationTime);
        
        // Completion rate
        metrics.put("completion_rate", cloudlets.size() > 0 ? (double) finishedCloudlets.size() / cloudlets.size() : 0);
        
        // Average execution time
        DescriptiveStatistics execTimeStats = new DescriptiveStatistics();
        for (Cloudlet cloudlet : finishedCloudlets) {
            execTimeStats.addValue(cloudlet.getActualCpuTime());
        }
        
        metrics.put("mean_execution_time", execTimeStats.getMean());
        metrics.put("std_execution_time", execTimeStats.getStandardDeviation());
        
        logger.debug("Throughput calculation completed");
        return metrics;
    }
    
    /**
     * Calculate response time metrics
     * 
     * @param cloudlets List of cloudlets to analyze
     * @return Map containing response time metrics
     */
    public static Map<String, Double> calculateResponseTime(List<Cloudlet> cloudlets) {
        logger.debug("Calculating response time for {} cloudlets", cloudlets.size());
        
        Map<String, Double> metrics = new HashMap<>();
        
        List<Cloudlet> finishedCloudlets = cloudlets.stream()
                .filter(Cloudlet::isFinished)
                .collect(Collectors.toList());
        
        if (finishedCloudlets.isEmpty()) {
            logger.warn("No finished cloudlets for response time calculation");
            return getEmptyResponseTimeMetrics();
        }
        
        DescriptiveStatistics responseTimeStats = new DescriptiveStatistics();
        DescriptiveStatistics waitTimeStats = new DescriptiveStatistics();
        
        for (Cloudlet cloudlet : finishedCloudlets) {
            double responseTime = cloudlet.getFinishTime() - cloudlet.getSubmissionTime();
            double waitTime = cloudlet.getExecStartTime() - cloudlet.getSubmissionTime();
            
            responseTimeStats.addValue(responseTime);
            if (waitTime >= 0) {
                waitTimeStats.addValue(waitTime);
            }
        }
        
        metrics.put("mean_response_time", responseTimeStats.getMean());
        metrics.put("median_response_time", responseTimeStats.getPercentile(50));
        metrics.put("p95_response_time", responseTimeStats.getPercentile(95));
        metrics.put("p99_response_time", responseTimeStats.getPercentile(99));
        metrics.put("max_response_time", responseTimeStats.getMax());
        metrics.put("min_response_time", responseTimeStats.getMin());
        metrics.put("std_response_time", responseTimeStats.getStandardDeviation());
        
        metrics.put("mean_wait_time", waitTimeStats.getMean());
        metrics.put("max_wait_time", waitTimeStats.getMax());
        
        logger.debug("Response time calculation completed");
        return metrics;
    }
    
    /**
     * Calculate cost-related metrics
     * 
     * @param hosts List of hosts for cost calculation
     * @param vms List of VMs for resource cost
     * @param simulationTime Total simulation time
     * @return Map containing cost metrics
     */
    public static Map<String, Double> calculateCostMetrics(List<Host> hosts, List<Vm> vms, double simulationTime) {
        logger.debug("Calculating cost metrics for {} hosts and {} VMs", hosts.size(), vms.size());
        
        Map<String, Double> metrics = new HashMap<>();
        
        if (hosts.isEmpty() || simulationTime <= 0) {
            logger.warn("Invalid parameters for cost calculation");
            return getEmptyCostMetrics();
        }
        
        // Basic cost assumptions (per hour)
        final double HOST_COST_PER_HOUR = 0.1;
        final double VM_COST_PER_MIPS_HOUR = 0.01;
        final double POWER_COST_PER_KWH = 0.12;
        
        double hoursOfOperation = simulationTime / 3600.0; // Convert seconds to hours
        
        // Infrastructure cost
        double infrastructureCost = hosts.size() * HOST_COST_PER_HOUR * hoursOfOperation;
        metrics.put("infrastructure_cost", infrastructureCost);
        
        // VM resource cost
        double vmCost = vms.stream()
                .mapToDouble(vm -> vm.getMips() * VM_COST_PER_MIPS_HOUR * hoursOfOperation)
                .sum();
        metrics.put("vm_resource_cost", vmCost);
        
        // Power cost (if power consumption data available)
        Map<String, Double> powerMetrics = calculatePowerConsumption(hosts);
        double powerCost = powerMetrics.getOrDefault("total_power_consumption", 0.0) * 
                          POWER_COST_PER_KWH * hoursOfOperation / 1000.0; // Convert W to kW
        metrics.put("power_cost", powerCost);
        
        // Total cost
        double totalCost = infrastructureCost + vmCost + powerCost;
        metrics.put("total_cost", totalCost);
        
        // Cost efficiency metrics
        Map<String, Double> utilizationMetrics = calculateResourceUtilization(hosts);
        double avgUtilization = utilizationMetrics.getOrDefault("overall_cpu_utilization", 0.0);
        metrics.put("cost_per_utilization", avgUtilization > 0 ? totalCost / avgUtilization : Double.MAX_VALUE);
        
        logger.debug("Cost metrics calculation completed");
        return metrics;
    }
    
    // Helper methods for empty metrics
    private static Map<String, Double> getEmptyUtilizationMetrics() {
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("overall_cpu_utilization", 0.0);
        metrics.put("overall_ram_utilization", 0.0);
        metrics.put("mean_cpu_utilization", 0.0);
        metrics.put("std_cpu_utilization", 0.0);
        metrics.put("active_hosts_ratio", 0.0);
        return metrics;
    }
    
    private static Map<String, Double> getEmptyPowerMetrics() {
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("total_power_consumption", 0.0);
        metrics.put("average_power_consumption", 0.0);
        metrics.put("power_efficiency", 0.0);
        return metrics;
    }
    
    private static Map<String, Double> getEmptySLAMetrics() {
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("cpu_violation_ratio", 0.0);
        metrics.put("ram_violation_ratio", 0.0);
        metrics.put("overall_sla_violation_ratio", 0.0);
        return metrics;
    }
    
    private static Map<String, Double> getEmptyThroughputMetrics() {
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("cloudlet_throughput", 0.0);
        metrics.put("completion_rate", 0.0);
        return metrics;
    }
    
    private static Map<String, Double> getEmptyResponseTimeMetrics() {
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("mean_response_time", 0.0);
        metrics.put("max_response_time", 0.0);
        return metrics;
    }
    
    private static Map<String, Double> getEmptyCostMetrics() {
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("total_cost", 0.0);
        metrics.put("infrastructure_cost", 0.0);
        return metrics;
    }
}
