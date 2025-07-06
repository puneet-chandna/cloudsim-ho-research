package org.cloudbus.cloudsim.simulation;

import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicy;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudbus.cloudsim.policy.HippopotamusVmAllocationPolicy;
import org.cloudbus.cloudsim.util.MetricsCalculator;
import org.cloudbus.cloudsim.util.ExperimentException;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.ValidationUtils;
import org.cloudbus.cloudsim.util.ResourceMonitor;
import org.cloudbus.cloudsim.experiment.ExperimentalResult;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Main simulation engine with comprehensive monitoring for Hippopotamus Optimization research.
 * This class orchestrates the entire CloudSim simulation with detailed performance tracking
 * and validation for research purposes.
 */
public class HippopotamusVmPlacementSimulation {
    
    private CloudSim simulation;
    private DatacenterBroker broker;
    private Datacenter datacenter;
    private List<Host> hostList;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private VmAllocationPolicy allocationPolicy;
    private ExperimentalScenario currentScenario;
    private ResourceMonitor resourceMonitor;
    private MetricsCalculator metricsCalculator;
    private Map<String, Object> simulationMetrics;
    private long simulationStartTime;
    private long simulationEndTime;
    
    // Configuration parameters
    private static final int SCHEDULING_INTERVAL = 10;
    private static final boolean ENABLE_MONITORING = true;
    private static final boolean VALIDATE_RESULTS = true;
    
    public HippopotamusVmPlacementSimulation() {
        this.hostList = new ArrayList<>();
        this.vmList = new ArrayList<>();
        this.cloudletList = new ArrayList<>();
        this.simulationMetrics = new HashMap<>();
        this.metricsCalculator = new MetricsCalculator();
        this.resourceMonitor = new ResourceMonitor();
        
        LoggingManager.logInfo("HippopotamusVmPlacementSimulation initialized");
    }
    
    /**
     * Setup simulation environment based on experimental scenario.
     * Configures all simulation components including datacenter, hosts, VMs, and cloudlets.
     * 
     * @param scenario The experimental scenario defining the simulation parameters
     * @throws ExperimentException if simulation setup fails
     */
    public void setupSimulation(ExperimentalScenario scenario) throws ExperimentException {
        try {
            LoggingManager.logInfo("Setting up simulation for scenario: " + scenario.getScenarioName());
            this.currentScenario = scenario;
            this.simulationStartTime = System.currentTimeMillis();
            
            // Initialize CloudSim
            this.simulation = new CloudSim();
            
            // Setup allocation policy based on scenario
            setupAllocationPolicy(scenario);
            
            // Create datacenter infrastructure
            createDatacenter(scenario);
            
            // Create broker
            createBroker(scenario);
            
            // Create VMs
            createVms(scenario);
            
            // Create cloudlets
            createCloudlets(scenario);
            
            // Submit VMs and cloudlets to broker
            broker.submitVmList(vmList);
            broker.submitCloudletList(cloudletList);
            
            // Initialize monitoring
            if (ENABLE_MONITORING) {
                initializeMonitoring();
            }
            
            // Validate setup
            validateSimulationSetup();
            
            LoggingManager.logInfo("Simulation setup completed successfully");
            
        } catch (Exception e) {
            LoggingManager.logError("Failed to setup simulation", e);
            throw new ExperimentException("Simulation setup failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Execute simulation with comprehensive monitoring and data collection.
     * Runs the CloudSim simulation while tracking performance metrics.
     * 
     * @return ExperimentalResult containing comprehensive simulation results
     * @throws ExperimentException if simulation execution fails
     */
    public ExperimentalResult runSimulation() throws ExperimentException {
        try {
            LoggingManager.logInfo("Starting simulation execution");
            
            // Start resource monitoring
            if (ENABLE_MONITORING) {
                resourceMonitor.startMonitoring();
            }
            
            // Record simulation start time
            double startTime = System.nanoTime();
            
            // Run the simulation
            simulation.start();
            
            // Record simulation end time
            double endTime = System.nanoTime();
            double executionTime = (endTime - startTime) / 1_000_000.0; // Convert to milliseconds
            
            // Stop resource monitoring
            if (ENABLE_MONITORING) {
                resourceMonitor.stopMonitoring();
            }
            
            LoggingManager.logInfo("Simulation completed in " + executionTime + " ms");
            
            // Collect comprehensive metrics
            ExperimentalResult result = collectMetrics();
            result.setExecutionTime(executionTime);
            result.setScenarioName(currentScenario.getScenarioName());
            
            // Validate results if enabled
            if (VALIDATE_RESULTS) {
                validateResults(result);
            }
            
            LoggingManager.logInfo("Simulation execution completed successfully");
            return result;
            
        } catch (Exception e) {
            LoggingManager.logError("Simulation execution failed", e);
            throw new ExperimentException("Simulation execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Collect comprehensive performance metrics from the simulation.
     * Gathers all relevant metrics for research analysis.
     * 
     * @return ExperimentalResult containing all collected metrics
     * @throws ExperimentException if metrics collection fails
     */
    public ExperimentalResult collectMetrics() throws ExperimentException {
        try {
            LoggingManager.logInfo("Collecting simulation metrics");
            
            ExperimentalResult result = new ExperimentalResult();
            
            // Basic simulation metrics
            result.setTotalHosts(hostList.size());
            result.setTotalVms(vmList.size());
            result.setTotalCloudlets(cloudletList.size());
            
            // Resource utilization metrics
            Map<String, Double> resourceMetrics = metricsCalculator.calculateResourceUtilization(hostList, vmList);
            result.setResourceUtilizationMetrics(resourceMetrics);
            
            // Power consumption metrics
            Map<String, Double> powerMetrics = metricsCalculator.calculatePowerConsumption(hostList);
            result.setPowerConsumptionMetrics(powerMetrics);
            
            // SLA violation metrics
            Map<String, Double> slaMetrics = metricsCalculator.calculateSLAViolations(cloudletList, currentScenario.getSlaRequirements());
            result.setSlaViolationMetrics(slaMetrics);
            
            // Throughput metrics
            Map<String, Double> throughputMetrics = metricsCalculator.calculateThroughput(cloudletList);
            result.setThroughputMetrics(throughputMetrics);
            
            // Response time metrics
            Map<String, Double> responseTimeMetrics = metricsCalculator.calculateResponseTime(cloudletList);
            result.setResponseTimeMetrics(responseTimeMetrics);
            
            // Cost metrics
            Map<String, Double> costMetrics = metricsCalculator.calculateCostMetrics(hostList, vmList, cloudletList);
            result.setCostMetrics(costMetrics);
            
            // System resource usage during simulation
            if (ENABLE_MONITORING) {
                Map<String, Object> systemMetrics = resourceMonitor.getResourceReport();
                result.setSystemResourceMetrics(systemMetrics);
            }
            
            // Algorithm-specific metrics if using Hippopotamus policy
            if (allocationPolicy instanceof HippopotamusVmAllocationPolicy) {
                HippopotamusVmAllocationPolicy hippoPolicy = (HippopotamusVmAllocationPolicy) allocationPolicy;
                Map<String, Object> optimizationMetrics = hippoPolicy.getOptimizationMetrics();
                result.setAlgorithmSpecificMetrics(optimizationMetrics);
            }
            
            // Calculate derived metrics
            calculateDerivedMetrics(result);
            
            LoggingManager.logInfo("Metrics collection completed");
            return result;
            
        } catch (Exception e) {
            LoggingManager.logError("Failed to collect metrics", e);
            throw new ExperimentException("Metrics collection failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validate simulation results for correctness and consistency.
     * Performs comprehensive validation of all simulation outcomes.
     * 
     * @param result The experimental result to validate
     * @throws ExperimentException if validation fails
     */
    public void validateResults(ExperimentalResult result) throws ExperimentException {
        try {
            LoggingManager.logInfo("Validating simulation results");
            
            // Validate basic constraints
            ValidationUtils.validateResults(result);
            
            // Validate resource allocation consistency
            validateResourceAllocation();
            
            // Validate SLA compliance
            validateSLACompliance(result);
            
            // Validate power consumption calculations
            validatePowerCalculations(result);
            
            // Validate metrics consistency
            validateMetricsConsistency(result);
            
            // Check for anomalies
            detectAnomalies(result);
            
            LoggingManager.logInfo("Result validation completed successfully");
            
        } catch (Exception e) {
            LoggingManager.logError("Result validation failed", e);
            throw new ExperimentException("Result validation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Setup the VM allocation policy based on the experimental scenario.
     */
    private void setupAllocationPolicy(ExperimentalScenario scenario) {
        String algorithmName = scenario.getAlgorithmName();
        
        switch (algorithmName.toLowerCase()) {
            case "hippopotamus":
            case "ho":
                this.allocationPolicy = new HippopotamusVmAllocationPolicy();
                break;
            default:
                // Default to Hippopotamus if not specified
                this.allocationPolicy = new HippopotamusVmAllocationPolicy();
                LoggingManager.logWarning("Unknown algorithm: " + algorithmName + ". Using Hippopotamus as default.");
        }
        
        LoggingManager.logInfo("Allocation policy set to: " + allocationPolicy.getClass().getSimpleName());
    }
    
    /**
     * Create datacenter with specified configuration.
     */
    private void createDatacenter(ExperimentalScenario scenario) {
        // Create hosts
        createHosts(scenario);
        
        // Create datacenter
        this.datacenter = new DatacenterSimple(simulation, hostList, allocationPolicy);
        datacenter.setSchedulingInterval(SCHEDULING_INTERVAL);
        
        LoggingManager.logInfo("Datacenter created with " + hostList.size() + " hosts");
    }
    
    /**
     * Create hosts based on scenario specifications.
     */
    private void createHosts(ExperimentalScenario scenario) {
        int hostCount = scenario.getHostCount();
        
        for (int i = 0; i < hostCount; i++) {
            Host host = createHost(scenario.getHostSpecification());
            hostList.add(host);
        }
        
        LoggingManager.logInfo("Created " + hostCount + " hosts");
    }
    
    /**
     * Create a single host with specified configuration.
     */
    private Host createHost(Map<String, Object> hostSpec) {
        int peCount = (Integer) hostSpec.getOrDefault("peCount", 4);
        long mips = (Long) hostSpec.getOrDefault("mips", 1000L);
        long ram = (Long) hostSpec.getOrDefault("ram", 4096L);
        long storage = (Long) hostSpec.getOrDefault("storage", 10000L);
        long bandwidth = (Long) hostSpec.getOrDefault("bandwidth", 1000L);
        
        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < peCount; i++) {
            peList.add(new PeSimple(mips, new PeProvisionerSimple()));
        }
        
        return new HostSimple(ram, bandwidth, storage, peList)
                .setRamProvisioner(new ResourceProvisionerSimple())
                .setBwProvisioner(new ResourceProvisionerSimple())
                .setVmScheduler(new VmSchedulerTimeShared());
    }
    
    /**
     * Create broker for the simulation.
     */
    private void createBroker(ExperimentalScenario scenario) {
        this.broker = new DatacenterBrokerSimple(simulation);
        LoggingManager.logInfo("Broker created: " + broker.getName());
    }
    
    /**
     * Create VMs based on scenario specifications.
     */
    private void createVms(ExperimentalScenario scenario) {
        int vmCount = scenario.getVmCount();
        
        for (int i = 0; i < vmCount; i++) {
            Vm vm = createVm(i, scenario.getVmSpecification());
            vmList.add(vm);
        }
        
        LoggingManager.logInfo("Created " + vmCount + " VMs");
    }
    
    /**
     * Create a single VM with specified configuration.
     */
    private Vm createVm(int id, Map<String, Object> vmSpec) {
        long mips = (Long) vmSpec.getOrDefault("mips", 1000L);
        long ram = (Long) vmSpec.getOrDefault("ram", 512L);
        long bandwidth = (Long) vmSpec.getOrDefault("bandwidth", 1000L);
        long storage = (Long) vmSpec.getOrDefault("storage", 10000L);
        int peCount = (Integer) vmSpec.getOrDefault("peCount", 1);
        
        return new VmSimple(id, mips, peCount)
                .setRam(ram)
                .setBw(bandwidth)
                .setSize(storage)
                .setCloudletScheduler(new CloudletSchedulerTimeShared());
    }
    
    /**
     * Create cloudlets based on scenario specifications.
     */
    private void createCloudlets(ExperimentalScenario scenario) {
        int cloudletCount = scenario.getCloudletCount();
        
        for (int i = 0; i < cloudletCount; i++) {
            Cloudlet cloudlet = createCloudlet(i, scenario.getCloudletSpecification());
            cloudletList.add(cloudlet);
        }
        
        LoggingManager.logInfo("Created " + cloudletCount + " cloudlets");
    }
    
    /**
     * Create a single cloudlet with specified configuration.
     */
    private Cloudlet createCloudlet(int id, Map<String, Object> cloudletSpec) {
        long length = (Long) cloudletSpec.getOrDefault("length", 10000L);
        int peCount = (Integer) cloudletSpec.getOrDefault("peCount", 1);
        long fileSize = (Long) cloudletSpec.getOrDefault("fileSize", 300L);
        long outputSize = (Long) cloudletSpec.getOrDefault("outputSize", 300L);
        
        UtilizationModel utilizationModel = new UtilizationModelFull();
        
        return new CloudletSimple(id, length, peCount)
                .setFileSize(fileSize)
                .setOutputSize(outputSize)
                .setUtilizationModelCpu(utilizationModel)
                .setUtilizationModelRam(utilizationModel)
                .setUtilizationModelBw(utilizationModel);
    }
    
    /**
     * Initialize comprehensive monitoring for the simulation.
     */
    private void initializeMonitoring() {
        resourceMonitor.configureMonitoring();
        LoggingManager.logInfo("Monitoring initialized");
    }
    
    /**
     * Validate simulation setup before execution.
     */
    private void validateSimulationSetup() throws ExperimentException {
        if (hostList.isEmpty()) {
            throw new ExperimentException("No hosts configured for simulation");
        }
        
        if (vmList.isEmpty()) {
            throw new ExperimentException("No VMs configured for simulation");
        }
        
        if (cloudletList.isEmpty()) {
            throw new ExperimentException("No cloudlets configured for simulation");
        }
        
        if (broker == null) {
            throw new ExperimentException("Broker not configured");
        }
        
        if (datacenter == null) {
            throw new ExperimentException("Datacenter not configured");
        }
        
        LoggingManager.logInfo("Simulation setup validation passed");
    }
    
    /**
     * Validate resource allocation consistency.
     */
    private void validateResourceAllocation() throws ExperimentException {
        // Check if all VMs are allocated
        long unallocatedVms = vmList.stream()
                .filter(vm -> vm.getHost() == null)
                .count();
        
        if (unallocatedVms > 0) {
            LoggingManager.logWarning("Found " + unallocatedVms + " unallocated VMs");
        }
        
        // Check resource constraints
        for (Host host : hostList) {
            if (host.getVmList().isEmpty()) {
                continue;
            }
            
            // Validate CPU allocation
            double totalAllocatedMips = host.getVmList().stream()
                    .mapToDouble(vm -> vm.getMips() * vm.getNumberOfPes())
                    .sum();
            
            double hostCapacity = host.getTotalMipsCapacity();
            
            if (totalAllocatedMips > hostCapacity * 1.1) { // Allow 10% overallocation
                throw new ExperimentException("CPU overallocation detected on host " + host.getId());
            }
        }
    }
    
    /**
     * Validate SLA compliance in results.
     */
    private void validateSLACompliance(ExperimentalResult result) {
        Map<String, Double> slaMetrics = result.getSlaViolationMetrics();
        
        if (slaMetrics != null && slaMetrics.containsKey("violationRate")) {
            double violationRate = slaMetrics.get("violationRate");
            
            if (violationRate > 0.5) { // More than 50% violations
                LoggingManager.logWarning("High SLA violation rate detected: " + violationRate);
            }
        }
    }
    
    /**
     * Validate power consumption calculations.
     */
    private void validatePowerCalculations(ExperimentalResult result) {
        Map<String, Double> powerMetrics = result.getPowerConsumptionMetrics();
        
        if (powerMetrics != null && powerMetrics.containsKey("totalPowerConsumption")) {
            double totalPower = powerMetrics.get("totalPowerConsumption");
            
            if (totalPower <= 0) {
                LoggingManager.logWarning("Invalid power consumption value: " + totalPower);
            }
        }
    }
    
    /**
     * Validate metrics consistency across different calculations.
     */
    private void validateMetricsConsistency(ExperimentalResult result) {
        // Check if resource utilization is within valid bounds
        Map<String, Double> resourceMetrics = result.getResourceUtilizationMetrics();
        
        if (resourceMetrics != null) {
            for (Map.Entry<String, Double> entry : resourceMetrics.entrySet()) {
                double utilization = entry.getValue();
                
                if (utilization < 0 || utilization > 1) {
                    LoggingManager.logWarning("Invalid utilization value for " + entry.getKey() + ": " + utilization);
                }
            }
        }
    }
    
    /**
     * Detect anomalies in simulation results.
     */
    private void detectAnomalies(ExperimentalResult result) {
        // Check for extremely low or high performance values
        Map<String, Double> throughputMetrics = result.getThroughputMetrics();
        
        if (throughputMetrics != null && throughputMetrics.containsKey("averageThroughput")) {
            double avgThroughput = throughputMetrics.get("averageThroughput");
            
            if (avgThroughput <= 0) {
                LoggingManager.logWarning("Anomaly detected: Zero or negative throughput");
            }
        }
    }
    
    /**
     * Calculate derived metrics from basic measurements.
     */
    private void calculateDerivedMetrics(ExperimentalResult result) {
        Map<String, Object> derivedMetrics = new HashMap<>();
        
        // Calculate efficiency metrics
        Map<String, Double> resourceMetrics = result.getResourceUtilizationMetrics();
        Map<String, Double> powerMetrics = result.getPowerConsumptionMetrics();
        
        if (resourceMetrics != null && powerMetrics != null) {
            Double cpuUtilization = resourceMetrics.get("cpuUtilization");
            Double totalPower = powerMetrics.get("totalPowerConsumption");
            
            if (cpuUtilization != null && totalPower != null && totalPower > 0) {
                double efficiency = cpuUtilization / totalPower;
                derivedMetrics.put("powerEfficiency", efficiency);
            }
        }
        
        // Calculate consolidation ratio
        long activeHosts = hostList.stream()
                .mapToLong(host -> host.getVmList().isEmpty() ? 0 : 1)
                .sum();
        
        if (activeHosts > 0) {
            double consolidationRatio = (double) vmList.size() / activeHosts;
            derivedMetrics.put("consolidationRatio", consolidationRatio);
        }
        
        result.setDerivedMetrics(derivedMetrics);
    }
    
    // Getter methods for accessing simulation components
    public CloudSim getSimulation() { return simulation; }
    public DatacenterBroker getBroker() { return broker; }
    public Datacenter getDatacenter() { return datacenter; }
    public List<Host> getHostList() { return new ArrayList<>(hostList); }
    public List<Vm> getVmList() { return new ArrayList<>(vmList); }
    public List<Cloudlet> getCloudletList() { return new ArrayList<>(cloudletList); }
    public VmAllocationPolicy getAllocationPolicy() { return allocationPolicy; }
    public ExperimentalScenario getCurrentScenario() { return currentScenario; }
    public Map<String, Object> getSimulationMetrics() { return new HashMap<>(simulationMetrics); }
}
// End of HippopotamusVmPlacementSimulation.java