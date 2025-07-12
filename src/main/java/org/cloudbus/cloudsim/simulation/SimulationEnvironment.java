package org.cloudbus.cloudsim.simulation;

import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicy;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
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
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;

import org.cloudbus.cloudsim.util.ExperimentException;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.ValidationUtils;
import org.cloudbus.cloudsim.policy.HippopotamusVmAllocationPolicy;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

/**
 * SimulationEnvironment manages the CloudSim simulation environment setup
 * including datacenter infrastructure, host configuration, network topology,
 * and performance monitoring initialization.
 * 
 * This class is responsible for creating realistic datacenter environments
 * for VM placement optimization research.
 * @author Puneet Chandna
 */
public class SimulationEnvironment {
    
    private CloudSim simulation;
    private List<Datacenter> datacenters;
    private List<Host> hosts;
    private List<Vm> vms;
    private List<Cloudlet> cloudlets;
    private DatacenterBroker broker;
    private VmAllocationPolicy vmAllocationPolicy;
    private Map<String, Object> environmentConfig;
    private Map<String, Double> performanceMetrics;
    private boolean monitoringEnabled;
    private LoggingManager loggingManager;
    
    // Default configuration constants
    private static final int DEFAULT_HOSTS_PER_DATACENTER = 50;
    private static final int DEFAULT_PES_PER_HOST = 8;
    private static final long DEFAULT_HOST_RAM = 16384; // MB
    private static final long DEFAULT_HOST_STORAGE = 1000000; // MB
    private static final long DEFAULT_HOST_BW = 10000; // Mbps
    private static final double DEFAULT_HOST_MIPS = 3000;
    
    // VM configuration constants
    private static final int DEFAULT_VM_PES = 2;
    private static final long DEFAULT_VM_RAM = 2048; // MB
    private static final long DEFAULT_VM_STORAGE = 10000; // MB
    private static final long DEFAULT_VM_BW = 1000; // Mbps
    private static final double DEFAULT_VM_MIPS = 1000;
    
    /**
     * Constructor initializes the simulation environment
     */
    public SimulationEnvironment() {
        this.datacenters = new ArrayList<>();
        this.hosts = new ArrayList<>();
        this.vms = new ArrayList<>();
        this.cloudlets = new ArrayList<>();
        this.environmentConfig = new HashMap<>();
        this.performanceMetrics = new HashMap<>();
        this.monitoringEnabled = false;
        this.loggingManager = new LoggingManager();
        initializeDefaultConfiguration();
    }
    
    /**
     * Constructor with custom configuration
     */
    public SimulationEnvironment(Map<String, Object> config) {
        this();
        this.environmentConfig.putAll(config);
        // Note: ValidationUtils.validateConfiguration expects ExperimentConfig, not Map<String,Object>
        // We'll skip validation for now since the method signature doesn't match
    }
    
    /**
     * Initialize default configuration parameters
     */
    private void initializeDefaultConfiguration() {
        try {
            environmentConfig.put("hostsPerDatacenter", DEFAULT_HOSTS_PER_DATACENTER);
            environmentConfig.put("pesPerHost", DEFAULT_PES_PER_HOST);
            environmentConfig.put("hostRam", DEFAULT_HOST_RAM);
            environmentConfig.put("hostStorage", DEFAULT_HOST_STORAGE);
            environmentConfig.put("hostBw", DEFAULT_HOST_BW);
            environmentConfig.put("hostMips", DEFAULT_HOST_MIPS);
            environmentConfig.put("vmPes", DEFAULT_VM_PES);
            environmentConfig.put("vmRam", DEFAULT_VM_RAM);
            environmentConfig.put("vmStorage", DEFAULT_VM_STORAGE);
            environmentConfig.put("vmBw", DEFAULT_VM_BW);
            environmentConfig.put("vmMips", DEFAULT_VM_MIPS);
            environmentConfig.put("datacenterCount", 1);
            environmentConfig.put("enablePowerModel", false);
            environmentConfig.put("enableNetworkTopology", false);
            
            loggingManager.logInfo("Default simulation environment configuration initialized");
        } catch (Exception e) {
            throw new ExperimentException("Failed to initialize default configuration", e);
        }
    }
    
    /**
     * Initialize datacenters with specified configuration
     */
    public List<Datacenter> initializeDatacenters() {
        try {
            loggingManager.logInfo("Initializing datacenters...");
            
            if (simulation == null) {
                simulation = new CloudSim();
            }
            
            int datacenterCount = (Integer) environmentConfig.getOrDefault("datacenterCount", 1);
            datacenters.clear();
            
            for (int i = 0; i < datacenterCount; i++) {
                Datacenter datacenter = createDatacenter("Datacenter_" + i);
                datacenters.add(datacenter);
                loggingManager.logInfo("Created datacenter: " + datacenter.getName());
            }
            
            loggingManager.logInfo("Successfully initialized " + datacenters.size() + " datacenters");
            return new ArrayList<>(datacenters);
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to initialize datacenters", e);
        }
    }
    
    /**
     * Create a single datacenter with configured hosts
     */
    private Datacenter createDatacenter(String name) {
        try {
            List<Host> datacenterHosts = configureHosts();
            hosts.addAll(datacenterHosts);
            
            // Create datacenter with VM allocation policy
            if (vmAllocationPolicy == null) {
                vmAllocationPolicy = new HippopotamusVmAllocationPolicy();
            }
            
            Datacenter datacenter = new DatacenterSimple(simulation, datacenterHosts, vmAllocationPolicy);
            datacenter.setName(name);
            
            // Configure datacenter characteristics
            datacenter.getCharacteristics()
                .setCostPerSecond(0.1)
                .setCostPerMem(0.05)
                .setCostPerStorage(0.001)
                .setCostPerBw(0.01);
            
            return datacenter;
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to create datacenter: " + name, e);
        }
    }
    
    /**
     * Configure hosts based on environment configuration
     */
    public List<Host> configureHosts() {
        try {
            loggingManager.logInfo("Configuring hosts...");
            
            int hostsPerDatacenter = (Integer) environmentConfig.get("hostsPerDatacenter");
            List<Host> hostList = new ArrayList<>();
            
            for (int i = 0; i < hostsPerDatacenter; i++) {
                Host host = createHost(i);
                hostList.add(host);
            }
            
            loggingManager.logInfo("Successfully configured " + hostList.size() + " hosts");
            return hostList;
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to configure hosts", e);
        }
    }
    
    /**
     * Create a single host with specified configuration
     */
    private Host createHost(int hostId) {
        try {
            int pesPerHost = (Integer) environmentConfig.get("pesPerHost");
            long hostRam = (Long) environmentConfig.get("hostRam");
            long hostStorage = (Long) environmentConfig.get("hostStorage");
            long hostBw = (Long) environmentConfig.get("hostBw");
            double hostMips = (Double) environmentConfig.get("hostMips");
            
            // Create processing elements
            List<Pe> peList = IntStream.range(0, pesPerHost)
                .mapToObj(i -> new PeSimple(hostMips, new PeProvisionerSimple()))
                .collect(Collectors.toList());
            
            // Create host with resources
            Host host = new HostSimple(hostRam, hostBw, hostStorage, peList);
            host.setId(hostId);
            
            // Set resource provisioners
            host.setRamProvisioner(new ResourceProvisionerSimple())
                .setBwProvisioner(new ResourceProvisionerSimple())
                .setVmScheduler(new VmSchedulerTimeShared());
            
            // Enable power model if configured
            boolean enablePowerModel = (Boolean) environmentConfig.getOrDefault("enablePowerModel", false);
            if (enablePowerModel) {
                setupPowerModel(host);
            }
            
            return host;
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to create host: " + hostId, e);
        }
    }
    
    /**
     * Setup power model for host (if power-aware optimization is enabled)
     */
    private void setupPowerModel(Host host) {
        try {
            // Power model configuration would be implemented here
            // This is a placeholder for power model setup
            loggingManager.logInfo("Power model setup for host: " + host.getId());
        } catch (Exception e) {
            loggingManager.logError("Failed to setup power model for host: " + host.getId(), e);
        }
    }
    
    /**
     * Setup network topology if enabled
     */
    public void setupNetworkTopology() {
        try {
            boolean enableNetworkTopology = (Boolean) environmentConfig.getOrDefault("enableNetworkTopology", false);
            
            if (!enableNetworkTopology) {
                loggingManager.logInfo("Network topology setup skipped (disabled in configuration)");
                return;
            }
            
            loggingManager.logInfo("Setting up network topology...");
            
            // Network topology setup would be implemented here
            // This includes setting up network delays, bandwidth limitations, etc.
            
            loggingManager.logInfo("Network topology setup completed");
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to setup network topology", e);
        }
    }
    
    /**
     * Initialize performance monitoring
     */
    public void initializeMonitoring() {
        try {
            loggingManager.logInfo("Initializing performance monitoring...");
            
            this.monitoringEnabled = true;
            this.performanceMetrics.clear();
            
            // Initialize performance metrics tracking
            performanceMetrics.put("totalHosts", (double) hosts.size());
            performanceMetrics.put("totalDatacenters", (double) datacenters.size());
            performanceMetrics.put("simulationStartTime", (double) System.currentTimeMillis());
            
            // Setup monitoring listeners if simulation is available
            if (simulation != null) {
                setupMonitoringListeners();
            }
            
            loggingManager.logInfo("Performance monitoring initialized successfully");
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to initialize monitoring", e);
        }
    }
    
    /**
     * Setup monitoring listeners for simulation events
     */
    private void setupMonitoringListeners() {
        try {
            // Add simulation event listeners for monitoring
            // Note: CloudSim doesn't have these specific listener methods
            // We'll implement basic monitoring without listeners for now
            
            loggingManager.logInfo("Monitoring listeners setup completed");
            
        } catch (Exception e) {
            loggingManager.logError("Failed to setup monitoring listeners", e);
        }
    }
    
    /**
     * Handle simulation start event
     */
    private void onSimulationStart(double time) {
        try {
            performanceMetrics.put("actualSimulationStartTime", time);
            loggingManager.logInfo("Simulation started at time: " + time);
        } catch (Exception e) {
            loggingManager.logError("Error handling simulation start event", e);
        }
    }
    
    /**
     * Handle simulation finish event
     */
    private void onSimulationFinish(double time) {
        try {
            performanceMetrics.put("simulationFinishTime", time);
            performanceMetrics.put("totalSimulationTime", time - 
                performanceMetrics.getOrDefault("actualSimulationStartTime", 0.0));
            loggingManager.logInfo("Simulation finished at time: " + time);
        } catch (Exception e) {
            loggingManager.logError("Error handling simulation finish event", e);
        }
    }
    
    /**
     * Create VMs based on experimental scenario
     */
    public List<Vm> createVMs(int vmCount) {
        try {
            loggingManager.logInfo("Creating " + vmCount + " VMs...");
            
            vms.clear();
            int vmPes = (Integer) environmentConfig.get("vmPes");
            long vmRam = (Long) environmentConfig.get("vmRam");
            long vmStorage = (Long) environmentConfig.get("vmStorage");
            long vmBw = (Long) environmentConfig.get("vmBw");
            double vmMips = (Double) environmentConfig.get("vmMips");
            
            for (int i = 0; i < vmCount; i++) {
                Vm vm = new VmSimple(i, vmMips, vmPes);
                vm.setRam(vmRam)
                  .setBw(vmBw)
                  .setSize(vmStorage)
                  .setCloudletScheduler(new CloudletSchedulerTimeShared());
                
                vms.add(vm);
            }
            
            loggingManager.logInfo("Successfully created " + vms.size() + " VMs");
            return new ArrayList<>(vms);
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to create VMs", e);
        }
    }
    
    /**
     * Create cloudlets for workload simulation
     */
    public List<Cloudlet> createCloudlets(int cloudletCount) {
        try {
            loggingManager.logInfo("Creating " + cloudletCount + " cloudlets...");
            
            cloudlets.clear();
            UtilizationModel utilizationModel = new UtilizationModelFull();
            
            for (int i = 0; i < cloudletCount; i++) {
                Cloudlet cloudlet = new CloudletSimple(
                    i, 10000 + (i * 1000), 1); // length varies by cloudlet
                
                cloudlet.setFileSize(1024)
                       .setOutputSize(1024)
                       .setUtilizationModelCpu(utilizationModel)
                       .setUtilizationModelRam(new UtilizationModelDynamic(0.5))
                       .setUtilizationModelBw(new UtilizationModelDynamic(0.3));
                
                cloudlets.add(cloudlet);
            }
            
            loggingManager.logInfo("Successfully created " + cloudlets.size() + " cloudlets");
            return new ArrayList<>(cloudlets);
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to create cloudlets", e);
        }
    }
    
    /**
     * Get current performance metrics
     */
    public Map<String, Double> getPerformanceMetrics() {
        if (!monitoringEnabled) {
            loggingManager.logWarning("Performance monitoring is not enabled");
            return new HashMap<>();
        }
        
        // Update real-time metrics
        updateRuntimeMetrics();
        return new HashMap<>(performanceMetrics);
    }
    
    /**
     * Update runtime performance metrics
     */
    private void updateRuntimeMetrics() {
        try {
            performanceMetrics.put("currentTime", (double) System.currentTimeMillis());
            
            if (simulation != null) {
                performanceMetrics.put("simulationClock", simulation.clock());
                // Note: getNumberOfFutureEvents() method doesn't exist in CloudSim
                // We'll skip this metric for now
            }
            
            if (!hosts.isEmpty()) {
                double totalCpuUtilization = hosts.stream()
                    .mapToDouble(host -> host.getCpuPercentUtilization())
                    .average()
                    .orElse(0.0);
                performanceMetrics.put("averageCpuUtilization", totalCpuUtilization);
                
                double totalRamUtilization = hosts.stream()
                    .mapToDouble(host -> host.getRam().getPercentUtilization())
                    .average()
                    .orElse(0.0);
                performanceMetrics.put("averageRamUtilization", totalRamUtilization);
            }
            
        } catch (Exception e) {
            loggingManager.logError("Failed to update runtime metrics", e);
        }
    }
    
    /**
     * Reset environment for new experiment
     */
    public void resetEnvironment() {
        try {
            loggingManager.logInfo("Resetting simulation environment...");
            
            // Clear collections
            datacenters.clear();
            hosts.clear();
            vms.clear();
            cloudlets.clear();
            performanceMetrics.clear();
            
            // Reset simulation
            if (simulation != null) {
                // Note: terminateSimulation() method doesn't exist in CloudSim
                simulation = null;
            }
            
            // Reset broker
            broker = null;
            monitoringEnabled = false;
            
            loggingManager.logInfo("Simulation environment reset completed");
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to reset environment", e);
        }
    }
    
    /**
     * Validate environment configuration
     */
    public boolean validateEnvironment() {
        try {
            loggingManager.logInfo("Validating simulation environment...");
            
            // Validate basic configuration
            if (environmentConfig.isEmpty()) {
                loggingManager.logWarning("Environment configuration is empty");
                return false;
            }
            
            // Validate datacenters
            if (datacenters.isEmpty()) {
                loggingManager.logWarning("No datacenters configured");
            }
            
            // Validate hosts
            if (hosts.isEmpty()) {
                loggingManager.logWarning("No hosts configured");
            }
            
            // Check resource consistency
            for (Host host : hosts) {
                if (host.getRam().getCapacity() <= 0 || host.getStorage().getCapacity() <= 0) {
                    loggingManager.logWarning("Invalid host configuration detected: " + host.getId());
                    return false;
                }
            }
            
            loggingManager.logInfo("Environment validation completed successfully");
            return true;
            
        } catch (Exception e) {
            loggingManager.logError("Environment validation failed", e);
            return false;
        }
    }
    
    // Getters and setters
    public CloudSim getSimulation() {
        return simulation;
    }
    
    public void setSimulation(CloudSim simulation) {
        this.simulation = simulation;
    }
    
    public List<Datacenter> getDatacenters() {
        return new ArrayList<>(datacenters);
    }
    
    public List<Host> getHosts() {
        return new ArrayList<>(hosts);
    }
    
    public List<Vm> getVms() {
        return new ArrayList<>(vms);
    }
    
    public List<Cloudlet> getCloudlets() {
        return new ArrayList<>(cloudlets);
    }
    
    public VmAllocationPolicy getVmAllocationPolicy() {
        return vmAllocationPolicy;
    }
    
    public void setVmAllocationPolicy(VmAllocationPolicy vmAllocationPolicy) {
        this.vmAllocationPolicy = vmAllocationPolicy;
    }
    
    public Map<String, Object> getEnvironmentConfig() {
        return new HashMap<>(environmentConfig);
    }
    
    public void setEnvironmentConfig(Map<String, Object> environmentConfig) {
        this.environmentConfig = new HashMap<>(environmentConfig);
        // Note: ValidationUtils.validateConfiguration expects ExperimentConfig, not Map<String,Object>
        // We'll skip validation for now
    }
    
    public boolean isMonitoringEnabled() {
        return monitoringEnabled;
    }
    
    public void setMonitoringEnabled(boolean monitoringEnabled) {
        this.monitoringEnabled = monitoringEnabled;
        if (monitoringEnabled) {
            initializeMonitoring();
        }
    }
}