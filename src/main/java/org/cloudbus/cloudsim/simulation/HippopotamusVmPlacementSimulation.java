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
import org.cloudbus.cloudsim.power.models.PowerModelHostSimple;
import org.cloudbus.cloudsim.util.MetricsCalculator;
import org.cloudbus.cloudsim.util.ExperimentException;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.ValidationUtils;
import org.cloudbus.cloudsim.util.ResourceMonitor;
import org.cloudbus.cloudsim.experiment.ExperimentalResult;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main simulation engine with comprehensive monitoring for Hippopotamus Optimization research.
 * This class orchestrates the entire CloudSim simulation with detailed performance tracking
 * and validation for research purposes.
 * @author Puneet Chandna
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
        this.resourceMonitor = ResourceMonitor.getInstance();
        
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
                resourceMonitor.startMonitoring("simulation_" + currentScenario.getScenarioId());
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
                resourceMonitor.stopMonitoring("simulation_" + currentScenario.getScenarioId());
            }
            
            LoggingManager.logInfo("Simulation completed in " + executionTime + " ms");
            
            // Collect comprehensive metrics
            ExperimentalResult result = collectMetrics();
            result.setExecutionDurationMs((long) executionTime);
            
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
            result.getPerformanceMetrics().getResourceUtilization().setAvgCpuUtilization(
                calculateAverageCpuUtilization()
            );
            result.getPerformanceMetrics().getResourceUtilization().setAvgMemoryUtilization(
                calculateAverageMemoryUtilization()
            );
            
            // Resource utilization metrics
            Map<String, Double> resourceMetrics = MetricsCalculator.calculateResourceUtilization(hostList);
            updateResourceUtilizationMetrics(result, resourceMetrics);
            
            // Power consumption metrics
            Map<String, Double> powerMetrics = MetricsCalculator.calculatePowerConsumption(hostList);
            updatePowerConsumptionMetrics(result, powerMetrics);
            
            // SLA violation metrics
            Map<String, Double> slaMetrics = MetricsCalculator.calculateSLAViolations(vmList, cloudletList);
            updateSLAViolationMetrics(result, slaMetrics);
            
            // Throughput metrics
            Map<String, Double> throughputMetrics = MetricsCalculator.calculateThroughput(cloudletList, simulation.clock());
            updateThroughputMetrics(result, throughputMetrics);
            
            // Response time metrics
            Map<String, Double> responseTimeMetrics = MetricsCalculator.calculateResponseTime(cloudletList);
            updateResponseTimeMetrics(result, responseTimeMetrics);
            
            // Cost metrics
            Map<String, Double> costMetrics = MetricsCalculator.calculateCostMetrics(hostList, vmList, simulation.clock());
            updateCostMetrics(result, costMetrics);
            
            // System resource metrics
            Map<String, Object> systemMetrics = new HashMap<>();
            if (resourceMonitor.generateResourceReport("simulation_" + currentScenario.getScenarioId()).getStatistics() != null) {
                systemMetrics.put("cpu_usage", resourceMonitor.generateResourceReport("simulation_" + currentScenario.getScenarioId()).getStatistics().getAvgCpuUsage());
                systemMetrics.put("memory_usage", resourceMonitor.generateResourceReport("simulation_" + currentScenario.getScenarioId()).getStatistics().getAvgMemoryUsage());
                systemMetrics.put("disk_usage", resourceMonitor.generateResourceReport("simulation_" + currentScenario.getScenarioId()).getStatistics().getAvgDiskUsage());
            }
            result.getExecutionMetadata().setSystemProperties(systemMetrics.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
            
            // Algorithm-specific metrics
            Map<String, Object> algorithmMetrics = new HashMap<>();
            algorithmMetrics.put("allocation_policy", allocationPolicy.getClass().getSimpleName());
            algorithmMetrics.put("total_hosts", hostList.size());
            algorithmMetrics.put("total_vms", vmList.size());
            algorithmMetrics.put("total_cloudlets", cloudletList.size());
            algorithmMetrics.put("simulation_time", simulation.clock());
            
            // Store algorithm metrics in raw data
            algorithmMetrics.forEach((key, value) -> {
                if (value instanceof Number) {
                    result.addRawDataPoint(key, ((Number) value).doubleValue());
                }
            });
            
            LoggingManager.logInfo("Metrics collection completed successfully");
            return result;
            
        } catch (Exception e) {
            LoggingManager.logError("Failed to collect metrics", e);
            throw new ExperimentException("Metrics collection failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validate simulation results for consistency and accuracy.
     * Performs comprehensive validation of collected metrics.
     * 
     * @param result The experimental result to validate
     * @throws ExperimentException if validation fails
     */
    public void validateResults(ExperimentalResult result) throws ExperimentException {
        try {
            LoggingManager.logInfo("Validating simulation results");
            
            // Validate basic metrics
            validateResourceAllocation();
            validateSLACompliance(result);
            validatePowerCalculations(result);
            validateMetricsConsistency(result);
            detectAnomalies(result);
            calculateDerivedMetrics(result);
            
            // Use ValidationUtils to validate the result
            List<ExperimentalResult> resultsList = Arrays.asList(result);
            ValidationUtils.ValidationReport report = ValidationUtils.validateResults(resultsList);
            
            if (!report.isValid()) {
                LoggingManager.logWarning("Validation report shows issues: " + report.getErrors());
            }
            
            LoggingManager.logInfo("Result validation completed");
            
        } catch (Exception e) {
            LoggingManager.logError("Result validation failed", e);
            throw new ExperimentException("Result validation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Setup allocation policy based on experimental scenario.
     * Configures the appropriate VM allocation policy for the simulation.
     * 
     * @param scenario The experimental scenario
     */
    private void setupAllocationPolicy(ExperimentalScenario scenario) {
        try {
            // For now, use Hippopotamus allocation policy
            // In a real implementation, you would determine the policy based on scenario
            this.allocationPolicy = new HippopotamusVmAllocationPolicy();
            
            LoggingManager.logInfo("Allocation policy configured: " + allocationPolicy.getClass().getSimpleName());
            
        } catch (Exception e) {
            LoggingManager.logError("Failed to setup allocation policy", e);
            throw new ExperimentException("Allocation policy setup failed", e);
        }
    }
    
    /**
     * Create datacenter with hosts based on experimental scenario.
     * 
     * @param scenario The experimental scenario
     */
    private void createDatacenter(ExperimentalScenario scenario) {
        try {
            // Create hosts
            createHosts(scenario);
            
            // Create datacenter
            this.datacenter = new DatacenterSimple(simulation, hostList, allocationPolicy);
            
            LoggingManager.logInfo("Datacenter created with " + hostList.size() + " hosts");
            
        } catch (Exception e) {
            LoggingManager.logError("Failed to create datacenter", e);
            throw new ExperimentException("Datacenter creation failed", e);
        }
    }
    
    /**
     * Create hosts based on experimental scenario configuration.
     * 
     * @param scenario The experimental scenario
     */
    private void createHosts(ExperimentalScenario scenario) {
        int hostCount = scenario.getNumberOfHosts();
        
        for (int i = 0; i < hostCount; i++) {
            // Use default host configuration if not specified
            Map<String, Object> hostSpec = new HashMap<>();
            hostSpec.put("cpu_cores", 4);
            hostSpec.put("cpu_mips", 1000.0);  // Use Double instead of Integer
            hostSpec.put("ram", 8192L);  // Use Long instead of Integer
            hostSpec.put("storage", 1000000L);  // Use Long instead of Integer
            hostSpec.put("bandwidth", 10000L);  // Use Long instead of Integer
            
            Host host = createHost(hostSpec);
            hostList.add(host);
        }
    }
    
    /**
     * Create a single host with specified configuration.
     * 
     * @param hostSpec Host specification parameters
     * @return Created host
     */
    private Host createHost(Map<String, Object> hostSpec) {
        int cpuCores = ((Number) hostSpec.getOrDefault("cpu_cores", 4)).intValue();
        double cpuMips = ((Number) hostSpec.getOrDefault("cpu_mips", 1000.0)).doubleValue();
        long ram = ((Number) hostSpec.getOrDefault("ram", 8192L)).longValue();
        long storage = ((Number) hostSpec.getOrDefault("storage", 1000000L)).longValue();
        long bandwidth = ((Number) hostSpec.getOrDefault("bandwidth", 10000L)).longValue();
        
        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < cpuCores; i++) {
            peList.add(new PeSimple(cpuMips, new PeProvisionerSimple()));
        }
        
        Host host = new HostSimple(ram, bandwidth, storage, peList)
            .setRamProvisioner(new ResourceProvisionerSimple())
            .setBwProvisioner(new ResourceProvisionerSimple())
            .setVmScheduler(new VmSchedulerTimeShared());
        
        // Add power model for realistic power consumption calculation
        // maxPower = 400W, staticPower = 200W (maxPower must be > staticPower)
        host.setPowerModel(new PowerModelHostSimple(400.0, 200.0));
        
        return host;
    }
    
    /**
     * Create broker for the simulation.
     * 
     * @param scenario The experimental scenario
     */
    private void createBroker(ExperimentalScenario scenario) {
        this.broker = new DatacenterBrokerSimple(simulation);
        LoggingManager.logInfo("Broker created");
    }
    
    /**
     * Create VMs based on experimental scenario configuration.
     * 
     * @param scenario The experimental scenario
     */
    private void createVms(ExperimentalScenario scenario) {
        int vmCount = scenario.getNumberOfVms();
        
        for (int i = 0; i < vmCount; i++) {
            // Use default VM configuration if not specified
            Map<String, Object> vmSpec = new HashMap<>();
            vmSpec.put("cpu_cores", 1);
            vmSpec.put("cpu_mips", 250.0);  // Use Double instead of Integer
            vmSpec.put("ram", 512L);  // Use Long instead of Integer
            vmSpec.put("storage", 1000L);  // Use Long instead of Integer
            vmSpec.put("bandwidth", 1000L);  // Use Long instead of Integer
            
            Vm vm = createVm(i, vmSpec);
            vmList.add(vm);
        }
    }
    
    /**
     * Create a single VM with specified configuration.
     * 
     * @param id VM ID
     * @param vmSpec VM specification parameters
     * @return Created VM
     */
    private Vm createVm(int id, Map<String, Object> vmSpec) {
        int cpuCores = ((Number) vmSpec.getOrDefault("cpu_cores", 1)).intValue();
        double cpuMips = ((Number) vmSpec.getOrDefault("cpu_mips", 250.0)).doubleValue();
        long ram = ((Number) vmSpec.getOrDefault("ram", 512L)).longValue();
        long storage = ((Number) vmSpec.getOrDefault("storage", 1000L)).longValue();
        long bandwidth = ((Number) vmSpec.getOrDefault("bandwidth", 1000L)).longValue();
        
        return new VmSimple(id, cpuMips, cpuCores)
            .setRam(ram)
            .setSize(storage)
            .setBw(bandwidth)
            .setCloudletScheduler(new CloudletSchedulerTimeShared());
    }
    
    /**
     * Create cloudlets based on experimental scenario configuration.
     * 
     * @param scenario The experimental scenario
     */
    private void createCloudlets(ExperimentalScenario scenario) {
        // Create a reasonable number of cloudlets based on VM count
        int cloudletCount = scenario.getNumberOfVms() * 2; // 2 cloudlets per VM
        
        for (int i = 0; i < cloudletCount; i++) {
            // Use default cloudlet configuration if not specified
            Map<String, Object> cloudletSpec = new HashMap<>();
            cloudletSpec.put("length", 10000L);  // Use Long instead of Integer
            cloudletSpec.put("cpu_cores", 1);
            cloudletSpec.put("ram", 256L);  // Use Long instead of Integer
            cloudletSpec.put("storage", 100L);  // Use Long instead of Integer
            cloudletSpec.put("bandwidth", 100L);  // Use Long instead of Integer
            
            Cloudlet cloudlet = createCloudlet(i, cloudletSpec);
            cloudletList.add(cloudlet);
        }
    }
    
    /**
     * Create a single cloudlet with specified configuration.
     * 
     * @param id Cloudlet ID
     * @param cloudletSpec Cloudlet specification parameters
     * @return Created cloudlet
     */
    private Cloudlet createCloudlet(int id, Map<String, Object> cloudletSpec) {
        long length = ((Number) cloudletSpec.getOrDefault("length", 10000L)).longValue();
        int cpuCores = ((Number) cloudletSpec.getOrDefault("cpu_cores", 1)).intValue();
        long ram = ((Number) cloudletSpec.getOrDefault("ram", 256L)).longValue();
        long storage = ((Number) cloudletSpec.getOrDefault("storage", 100L)).longValue();
        long bandwidth = ((Number) cloudletSpec.getOrDefault("bandwidth", 100L)).longValue();
        
        UtilizationModel utilizationModel = new UtilizationModelFull();
        
        return new CloudletSimple(id, length, cpuCores)
            .setUtilizationModelCpu(utilizationModel)
            .setUtilizationModelRam(utilizationModel)
            .setUtilizationModelBw(utilizationModel);
    }
    
    /**
     * Initialize monitoring for the simulation.
     */
    private void initializeMonitoring() {
        try {
            // Configure monitoring parameters
            LoggingManager.logInfo("Initializing simulation monitoring");
            
        } catch (Exception e) {
            LoggingManager.logError("Failed to initialize monitoring", e);
        }
    }
    
    /**
     * Validate simulation setup for consistency.
     * 
     * @throws ExperimentException if setup validation fails
     */
    private void validateSimulationSetup() throws ExperimentException {
        try {
            // Validate basic components
            if (simulation == null) {
                throw new ExperimentException("CloudSim simulation not initialized");
            }
            
            if (datacenter == null) {
                throw new ExperimentException("Datacenter not created");
            }
            
            if (broker == null) {
                throw new ExperimentException("Broker not created");
            }
            
            if (hostList.isEmpty()) {
                throw new ExperimentException("No hosts created");
            }
            
            if (vmList.isEmpty()) {
                throw new ExperimentException("No VMs created");
            }
            
            if (cloudletList.isEmpty()) {
                throw new ExperimentException("No cloudlets created");
            }
            
            LoggingManager.logInfo("Simulation setup validation passed");
            
        } catch (Exception e) {
            LoggingManager.logError("Simulation setup validation failed", e);
            throw new ExperimentException("Setup validation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validate resource allocation for consistency.
     * 
     * @throws ExperimentException if resource allocation is invalid
     */
    private void validateResourceAllocation() throws ExperimentException {
        try {
            // Check if all VMs are allocated
            long allocatedVms = vmList.stream()
                .filter(vm -> vm.getHost() != null)
                .count();
            
            if (allocatedVms != vmList.size()) {
                LoggingManager.logWarning("Not all VMs are allocated: " + allocatedVms + "/" + vmList.size());
            }
            
            // Check resource utilization
            double avgCpuUtil = calculateAverageCpuUtilization();
            if (avgCpuUtil > 100.0) {
                throw new ExperimentException("Invalid CPU utilization: " + avgCpuUtil);
            }
            
            LoggingManager.logInfo("Resource allocation validation passed");
            
        } catch (Exception e) {
            LoggingManager.logError("Resource allocation validation failed", e);
            throw new ExperimentException("Resource allocation validation failed", e);
        }
    }
    
    /**
     * Validate SLA compliance of simulation results.
     * 
     * @param result The experimental result
     */
    private void validateSLACompliance(ExperimentalResult result) {
        try {
            // Check SLA violation metrics
            double violationRate = result.getPerformanceMetrics().getSlaViolations().getViolationRate();
            
            if (violationRate > 0.1) { // 10% threshold
                LoggingManager.logWarning("High SLA violation rate: " + violationRate);
            }
            
            LoggingManager.logInfo("SLA compliance validation completed");
            
        } catch (Exception e) {
            LoggingManager.logError("SLA compliance validation failed", e);
        }
    }
    
    /**
     * Validate power consumption calculations.
     * 
     * @param result The experimental result
     */
    private void validatePowerCalculations(ExperimentalResult result) {
        try {
            // Check power consumption metrics
            double totalPower = result.getPerformanceMetrics().getPowerConsumption().getTotalPowerConsumption();
            
            if (totalPower < 0) {
                LoggingManager.logWarning("Invalid power consumption: " + totalPower);
            }
            
            LoggingManager.logInfo("Power calculation validation completed");
            
        } catch (Exception e) {
            LoggingManager.logError("Power calculation validation failed", e);
        }
    }
    
    /**
     * Validate metrics consistency across different measurements.
     * 
     * @param result The experimental result
     */
    private void validateMetricsConsistency(ExperimentalResult result) {
        try {
            // Check for consistency between related metrics
            double cpuUtil = result.getPerformanceMetrics().getResourceUtilization().getAvgCpuUtilization();
            double memoryUtil = result.getPerformanceMetrics().getResourceUtilization().getAvgMemoryUtilization();
            
            if (cpuUtil > 100.0 || memoryUtil > 100.0) {
                LoggingManager.logWarning("Resource utilization exceeds 100%: CPU=" + cpuUtil + ", Memory=" + memoryUtil);
            }
            
            LoggingManager.logInfo("Metrics consistency validation completed");
            
        } catch (Exception e) {
            LoggingManager.logError("Metrics consistency validation failed", e);
        }
    }
    
    /**
     * Detect anomalies in simulation results.
     * 
     * @param result The experimental result
     */
    private void detectAnomalies(ExperimentalResult result) {
        try {
            // Detect statistical anomalies
            double cpuUtil = result.getPerformanceMetrics().getResourceUtilization().getAvgCpuUtilization();
            double memoryUtil = result.getPerformanceMetrics().getResourceUtilization().getAvgMemoryUtilization();
            
            // Check for extreme values
            if (cpuUtil < 1.0 && vmList.size() > 0) {
                LoggingManager.logWarning("Unusually low CPU utilization: " + cpuUtil);
            }
            
            if (memoryUtil < 1.0 && vmList.size() > 0) {
                LoggingManager.logWarning("Unusually low memory utilization: " + memoryUtil);
            }
            
            LoggingManager.logInfo("Anomaly detection completed");
            
        } catch (Exception e) {
            LoggingManager.logError("Anomaly detection failed", e);
        }
    }
    
    /**
     * Calculate derived metrics from basic measurements.
     * 
     * @param result The experimental result
     */
    private void calculateDerivedMetrics(ExperimentalResult result) {
        try {
            Map<String, Object> derivedMetrics = new HashMap<>();
            
            // Calculate efficiency metrics
            double cpuUtil = result.getPerformanceMetrics().getResourceUtilization().getAvgCpuUtilization();
            double memoryUtil = result.getPerformanceMetrics().getResourceUtilization().getAvgMemoryUtilization();
            double powerConsumption = result.getPerformanceMetrics().getPowerConsumption().getTotalPowerConsumption();
            
            // Resource efficiency
            derivedMetrics.put("resource_efficiency", (cpuUtil + memoryUtil) / 2.0);
            
            // Power efficiency (lower is better)
            derivedMetrics.put("power_efficiency", powerConsumption > 0 ? cpuUtil / powerConsumption : 0);
            
            // Overall performance score
            double slaViolationRate = result.getPerformanceMetrics().getSlaViolations().getViolationRate();
            derivedMetrics.put("performance_score", 
                (cpuUtil * 0.3 + memoryUtil * 0.3 + (1 - slaViolationRate) * 0.4));
            
            // Store derived metrics
            derivedMetrics.forEach((key, value) -> {
                if (value instanceof Number) {
                    result.addRawDataPoint(key, ((Number) value).doubleValue());
                }
            });
            
            LoggingManager.logInfo("Derived metrics calculation completed");
            
        } catch (Exception e) {
            LoggingManager.logError("Derived metrics calculation failed", e);
        }
    }
    
    // Helper methods for updating metrics
    private void updateResourceUtilizationMetrics(ExperimentalResult result, Map<String, Double> metrics) {
        result.getPerformanceMetrics().getResourceUtilization().setAvgCpuUtilization(
            metrics.getOrDefault("overall_cpu_utilization", 0.0) * 100
        );
        result.getPerformanceMetrics().getResourceUtilization().setAvgMemoryUtilization(
            metrics.getOrDefault("overall_ram_utilization", 0.0) * 100
        );
        result.getPerformanceMetrics().getResourceUtilization().setAvgStorageUtilization(
            metrics.getOrDefault("overall_storage_utilization", 0.0) * 100
        );
        result.getPerformanceMetrics().getResourceUtilization().setAvgNetworkUtilization(
            metrics.getOrDefault("overall_bw_utilization", 0.0) * 100
        );
    }
    
    private void updatePowerConsumptionMetrics(ExperimentalResult result, Map<String, Double> metrics) {
        result.getPerformanceMetrics().getPowerConsumption().setTotalPowerConsumption(
            metrics.getOrDefault("total_power_consumption", 0.0)
        );
        result.getPerformanceMetrics().getPowerConsumption().setAvgPowerConsumption(
            metrics.getOrDefault("average_power_consumption", 0.0)
        );
        result.getPerformanceMetrics().getPowerConsumption().setPeakPowerConsumption(
            metrics.getOrDefault("max_host_power", 0.0)
        );
    }
    
    private void updateSLAViolationMetrics(ExperimentalResult result, Map<String, Double> metrics) {
        result.getPerformanceMetrics().getSlaViolations().setTotalViolations(
            metrics.getOrDefault("total_violations", 0.0).intValue()
        );
        result.getPerformanceMetrics().getSlaViolations().setViolationRate(
            metrics.getOrDefault("violation_rate", 0.0)
        );
    }
    
    private void updateThroughputMetrics(ExperimentalResult result, Map<String, Double> metrics) {
        result.getPerformanceMetrics().getThroughput().setAvgThroughput(
            metrics.getOrDefault("average_throughput", 0.0)
        );
        result.getPerformanceMetrics().getThroughput().setPeakThroughput(
            metrics.getOrDefault("peak_throughput", 0.0)
        );
        result.getPerformanceMetrics().getThroughput().setTotalJobsCompleted(
            metrics.getOrDefault("total_jobs_completed", 0.0)
        );
    }
    
    private void updateResponseTimeMetrics(ExperimentalResult result, Map<String, Double> metrics) {
        result.getPerformanceMetrics().getResponseTime().setAvgResponseTime(
            metrics.getOrDefault("average_response_time", 0.0)
        );
        result.getPerformanceMetrics().getResponseTime().setMinResponseTime(
            metrics.getOrDefault("min_response_time", 0.0)
        );
        result.getPerformanceMetrics().getResponseTime().setMaxResponseTime(
            metrics.getOrDefault("max_response_time", 0.0)
        );
    }
    
    private void updateCostMetrics(ExperimentalResult result, Map<String, Double> metrics) {
        result.getPerformanceMetrics().getCostMetrics().setTotalOperationalCost(
            metrics.getOrDefault("total_operational_cost", 0.0)
        );
        result.getPerformanceMetrics().getCostMetrics().setPowerCost(
            metrics.getOrDefault("power_cost", 0.0)
        );
        result.getPerformanceMetrics().getCostMetrics().setResourceCost(
            metrics.getOrDefault("resource_cost", 0.0)
        );
    }
    
    // Helper methods for calculating averages
    private double calculateAverageCpuUtilization() {
        if (hostList.isEmpty()) return 0.0;
        return hostList.stream()
            .mapToDouble(host -> host.getCpuMipsUtilization() * 100)
            .average()
            .orElse(0.0);
    }
    
    private double calculateAverageMemoryUtilization() {
        if (hostList.isEmpty()) return 0.0;
        return hostList.stream()
            .mapToDouble(host -> host.getRamUtilization() * 100)
            .average()
            .orElse(0.0);
    }
    
    // Getter methods
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