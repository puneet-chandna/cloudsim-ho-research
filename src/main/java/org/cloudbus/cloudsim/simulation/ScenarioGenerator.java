package org.cloudbus.cloudsim.simulation;

import org.cloudbus.cloudsim.util.ExperimentException;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.ValidationUtils;
import org.cloudbus.cloudsim.dataset.WorkloadCharacteristics;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generate diverse experimental scenarios for comprehensive testing.
 * This class creates various simulation scenarios including realistic workloads,
 * scalability tests, and dataset-based scenarios for research experiments.
 * @author Puneet Chandna
 */
public class ScenarioGenerator {
    
    private final Random random;
    private final Map<String, Object> defaultConfigurations;
    private final Map<String, WorkloadCharacteristics> workloadTemplates;
    
    // Scenario generation parameters
    private static final int DEFAULT_MIN_HOSTS = 10;
    private static final int DEFAULT_MAX_HOSTS = 100;
    private static final int DEFAULT_MIN_VMS = 50;
    private static final int DEFAULT_MAX_VMS = 500;
    private static final int DEFAULT_MIN_CLOUDLETS = 100;
    private static final int DEFAULT_MAX_CLOUDLETS = 1000;
    
    // Workload pattern types
    private static final String[] WORKLOAD_PATTERNS = {
        "UNIFORM", "EXPONENTIAL", "NORMAL", "BURSTY", "PERIODIC", "MIXED"
    };
    
    // Resource requirement levels
    private static final String[] RESOURCE_LEVELS = {
        "LOW", "MEDIUM", "HIGH", "MIXED"
    };
    
    public ScenarioGenerator() {
        this.random = new Random(System.currentTimeMillis());
        this.defaultConfigurations = new HashMap<>();
        this.workloadTemplates = new HashMap<>();
        
        initializeDefaultConfigurations();
        initializeWorkloadTemplates();
        
        LoggingManager.logInfo("ScenarioGenerator initialized");
    }
    
    public ScenarioGenerator(long seed) {
        this.random = new Random(seed);
        this.defaultConfigurations = new HashMap<>();
        this.workloadTemplates = new HashMap<>();
        
        initializeDefaultConfigurations();
        initializeWorkloadTemplates();
        
        LoggingManager.logInfo("ScenarioGenerator initialized with seed: " + seed);
    }
    
    /**
     * Initialize default configurations for scenario generation.
     */
    private void initializeDefaultConfigurations() {
        try {
            // Host configurations
            Map<String, Object> hostConfig = new HashMap<>();
            hostConfig.put("cpu_cores", Arrays.asList(4, 8, 16, 32));
            hostConfig.put("ram_gb", Arrays.asList(8, 16, 32, 64, 128));
            hostConfig.put("storage_gb", Arrays.asList(500, 1000, 2000, 4000));
            hostConfig.put("bandwidth_mbps", Arrays.asList(1000, 2000, 5000, 10000));
            defaultConfigurations.put("hosts", hostConfig);
            
            // VM configurations
            Map<String, Object> vmConfig = new HashMap<>();
            vmConfig.put("cpu_cores", Arrays.asList(1, 2, 4, 8));
            vmConfig.put("ram_gb", Arrays.asList(1, 2, 4, 8, 16));
            vmConfig.put("storage_gb", Arrays.asList(20, 50, 100, 200));
            vmConfig.put("bandwidth_mbps", Arrays.asList(100, 250, 500, 1000));
            defaultConfigurations.put("vms", vmConfig);
            
            // Cloudlet configurations
            Map<String, Object> cloudletConfig = new HashMap<>();
            cloudletConfig.put("length_mi", Arrays.asList(1000, 5000, 10000, 50000, 100000));
            cloudletConfig.put("file_size_mb", Arrays.asList(100, 500, 1000, 2000));
            cloudletConfig.put("output_size_mb", Arrays.asList(50, 200, 500, 1000));
            cloudletConfig.put("pes_number", Arrays.asList(1, 2, 4, 8));
            defaultConfigurations.put("cloudlets", cloudletConfig);
            
            // SLA configurations
            Map<String, Object> slaConfig = new HashMap<>();
            slaConfig.put("max_response_time_ms", Arrays.asList(100, 500, 1000, 2000));
            slaConfig.put("min_availability_percent", Arrays.asList(95.0, 98.0, 99.0, 99.9));
            slaConfig.put("max_cpu_utilization_percent", Arrays.asList(70.0, 80.0, 90.0, 95.0));
            slaConfig.put("max_memory_utilization_percent", Arrays.asList(75.0, 85.0, 90.0, 95.0));
            defaultConfigurations.put("sla", slaConfig);
            
            LoggingManager.logInfo("Default configurations initialized");
            
        } catch (Exception e) {
            LoggingManager.logError("Failed to initialize default configurations", e);
            throw new ExperimentException("Configuration initialization failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Initialize workload templates based on realistic patterns.
     */
    private void initializeWorkloadTemplates() {
        try {
            // Web application workload
            WorkloadCharacteristics webWorkload = new WorkloadCharacteristics();
            webWorkload.setWorkloadType("WEB_APPLICATION");
            webWorkload.setCpuIntensive(false);
            webWorkload.setMemoryIntensive(true);
            webWorkload.setIoIntensive(true);
            webWorkload.setBurstiness(0.7);
            webWorkload.setSeasonality(0.3);
            workloadTemplates.put("WEB", webWorkload);
            
            // Scientific computing workload
            WorkloadCharacteristics scientificWorkload = new WorkloadCharacteristics();
            scientificWorkload.setWorkloadType("SCIENTIFIC_COMPUTING");
            scientificWorkload.setCpuIntensive(true);
            scientificWorkload.setMemoryIntensive(true);
            scientificWorkload.setIoIntensive(false);
            scientificWorkload.setBurstiness(0.2);
            scientificWorkload.setSeasonality(0.1);
            workloadTemplates.put("SCIENTIFIC", scientificWorkload);
            
            // Database workload
            WorkloadCharacteristics databaseWorkload = new WorkloadCharacteristics();
            databaseWorkload.setWorkloadType("DATABASE");
            databaseWorkload.setCpuIntensive(true);
            databaseWorkload.setMemoryIntensive(true);
            databaseWorkload.setIoIntensive(true);
            databaseWorkload.setBurstiness(0.5);
            databaseWorkload.setSeasonality(0.4);
            workloadTemplates.put("DATABASE", databaseWorkload);
            
            // Batch processing workload
            WorkloadCharacteristics batchWorkload = new WorkloadCharacteristics();
            batchWorkload.setWorkloadType("BATCH_PROCESSING");
            batchWorkload.setCpuIntensive(true);
            batchWorkload.setMemoryIntensive(false);
            batchWorkload.setIoIntensive(false);
            batchWorkload.setBurstiness(0.1);
            batchWorkload.setSeasonality(0.8);
            workloadTemplates.put("BATCH", batchWorkload);
            
            LoggingManager.logInfo("Workload templates initialized");
            
        } catch (Exception e) {
            LoggingManager.logError("Failed to initialize workload templates", e);
            throw new ExperimentException("Workload template initialization failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate multiple diverse experimental scenarios for comprehensive testing.
     * Creates a variety of scenarios covering different aspects of VM placement optimization.
     * 
     * @param count Number of scenarios to generate
     * @return List of generated experimental scenarios
     * @throws ExperimentException if scenario generation fails
     */
    public List<ExperimentalScenario> generateScenarios(int count) throws ExperimentException {
        try {
            LoggingManager.logInfo("Generating " + count + " experimental scenarios");
            
            List<ExperimentalScenario> scenarios = new ArrayList<>();
            
            // Generate diverse scenario types
            int baseScenarios = Math.max(1, count / 6); // Ensure at least one of each type
            
            // Basic performance scenarios
            scenarios.addAll(generateBasicScenarios(baseScenarios));
            
            // Scalability scenarios
            scenarios.addAll(generateScalabilityScenarios(baseScenarios));
            
            // Workload diversity scenarios
            scenarios.addAll(generateWorkloadScenarios(baseScenarios));
            
            // Resource constraint scenarios
            scenarios.addAll(generateResourceConstraintScenarios(baseScenarios));
            
            // SLA-focused scenarios
            scenarios.addAll(generateSLAScenarios(baseScenarios));
            
            // Stress test scenarios
            scenarios.addAll(generateStressTestScenarios(baseScenarios));
            
            // Fill remaining slots with random scenarios
            while (scenarios.size() < count) {
                scenarios.add(generateRandomScenario());
            }
            
            // Trim to exact count if needed
            if (scenarios.size() > count) {
                scenarios = scenarios.subList(0, count);
            }
            
            // Validate all scenarios
            for (ExperimentalScenario scenario : scenarios) {
                ValidationUtils.validateExperimentalScenario(scenario);
            }
            
            LoggingManager.logInfo("Successfully generated " + scenarios.size() + " scenarios");
            return scenarios;
            
        } catch (Exception e) {
            LoggingManager.logError("Failed to generate scenarios", e);
            throw new ExperimentException("Scenario generation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Create realistic workload patterns based on real-world characteristics.
     * Generates workloads that mimic actual cloud computing usage patterns.
     * 
     * @return ExperimentalScenario with realistic workload characteristics
     * @throws ExperimentException if workload creation fails
     */
    public ExperimentalScenario createRealisticWorkload() throws ExperimentException {
        try {
            LoggingManager.logInfo("Creating realistic workload scenario");
            
            ExperimentalScenario scenario = new ExperimentalScenario();
            scenario.setScenarioName("Realistic_Workload_" + System.currentTimeMillis());
            scenario.setScenarioType("REALISTIC");
            
            // Use realistic resource distributions
            int hostCount = ThreadLocalRandom.current().nextInt(20, 80);
            int vmCount = ThreadLocalRandom.current().nextInt(hostCount * 2, hostCount * 8);
            int cloudletCount = ThreadLocalRandom.current().nextInt(vmCount, vmCount * 5);
            
            scenario.setHostCount(hostCount);
            scenario.setVmCount(vmCount);
            scenario.setCloudletCount(cloudletCount);
            
            // Configure realistic host specifications
            Map<String, Object> hostSpec = createRealisticHostSpecification();
            scenario.setHostSpecification(hostSpec);
            
            // Configure heterogeneous VM specifications
            Map<String, Object> vmSpec = createHeterogeneousVmSpecification();
            scenario.setVmSpecification(vmSpec);
            
            // Configure realistic cloudlet characteristics
            Map<String, Object> cloudletSpec = createRealisticCloudletSpecification();
            scenario.setCloudletSpecification(cloudletSpec);
            
            // Set realistic SLA requirements
            Map<String, Object> slaRequirements = createRealisticSLARequirements();
            scenario.setSlaRequirements(slaRequirements);
            
            // Configure workload characteristics
            String[] workloadTypes = {"WEB", "SCIENTIFIC", "DATABASE", "BATCH"};
            String selectedWorkloadType = workloadTypes[random.nextInt(workloadTypes.length)];
            WorkloadCharacteristics workloadChar = workloadTemplates.get(selectedWorkloadType);
            scenario.setWorkloadCharacteristics(workloadChar);
            
            // Set temporal characteristics for realistic behavior
            Map<String, Object> temporalCharacteristics = new HashMap<>();
            temporalCharacteristics.put("peak_hours", Arrays.asList(9, 10, 11, 14, 15, 16)); // Business hours
            temporalCharacteristics.put("off_peak_hours", Arrays.asList(22, 23, 0, 1, 2, 3, 4, 5, 6));
            temporalCharacteristics.put("weekend_load_reduction", 0.3); // 30% reduction on weekends
            temporalCharacteristics.put("burst_probability", 0.15); // 15% chance of burst
            temporalCharacteristics.put("burst_multiplier", 2.5); // 2.5x normal load during bursts
            scenario.setTemporalCharacteristics(temporalCharacteristics);
            
            // Set resource utilization patterns
            Map<String, Object> utilizationPatterns = new HashMap<>();
            utilizationPatterns.put("cpu_base_utilization", 0.2 + random.nextDouble() * 0.3); // 20-50% base
            utilizationPatterns.put("memory_base_utilization", 0.3 + random.nextDouble() * 0.2); // 30-50% base
            utilizationPatterns.put("storage_base_utilization", 0.1 + random.nextDouble() * 0.3); // 10-40% base
            utilizationPatterns.put("network_base_utilization", 0.05 + random.nextDouble() * 0.15); // 5-20% base
            scenario.setUtilizationPatterns(utilizationPatterns);
            
            // Configure failure patterns
            Map<String, Object> failurePatterns = new HashMap<>();
            failurePatterns.put("host_failure_rate", 0.001 + random.nextDouble() * 0.004); // 0.1-0.5% failure rate
            failurePatterns.put("vm_failure_rate", 0.005 + random.nextDouble() * 0.015); // 0.5-2% failure rate
            failurePatterns.put("network_failure_rate", 0.002 + random.nextDouble() * 0.008); // 0.2-1% failure rate
            failurePatterns.put("mttr_minutes", 15 + random.nextInt(45)); // 15-60 minutes MTTR
            scenario.setFailurePatterns(failurePatterns);
            
            // Set performance targets
            Map<String, Object> performanceTargets = new HashMap<>();
            performanceTargets.put("target_response_time_ms", 100 + random.nextInt(900)); // 100-1000ms
            performanceTargets.put("target_throughput_ops_per_sec", 100 + random.nextInt(1900)); // 100-2000 ops/sec
            performanceTargets.put("target_availability_percent", 95.0 + random.nextDouble() * 4.5); // 95-99.5%
            performanceTargets.put("target_resource_efficiency", 0.7 + random.nextDouble() * 0.25); // 70-95%
            scenario.setPerformanceTargets(performanceTargets);
            
            LoggingManager.logInfo("Realistic workload scenario created: " + scenario.getScenarioName());
            return scenario;
            
        } catch (Exception e) {
            LoggingManager.logError("Failed to create realistic workload", e);
            throw new ExperimentException("Realistic workload creation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate basic performance evaluation scenarios.
     */
    private List<ExperimentalScenario> generateBasicScenarios(int count) throws ExperimentException {
        List<ExperimentalScenario> scenarios = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            ExperimentalScenario scenario = new ExperimentalScenario();
            scenario.setScenarioName("Basic_Performance_" + i);
            scenario.setScenarioType("BASIC");
            
            // Moderate scale for basic testing
            scenario.setHostCount(20 + random.nextInt(30));
            scenario.setVmCount(50 + random.nextInt(100));
            scenario.setCloudletCount(100 + random.nextInt(200));
            
            scenario.setHostSpecification(createStandardHostSpecification());
            scenario.setVmSpecification(createStandardVmSpecification());
            scenario.setCloudletSpecification(createStandardCloudletSpecification());
            scenario.setSlaRequirements(createStandardSLARequirements());
            
            scenarios.add(scenario);
        }
        
        return scenarios;
    }
    
    /**
     * Generate scalability test scenarios with varying scales.
     */
    private List<ExperimentalScenario> generateScalabilityScenarios(int count) throws ExperimentException {
        List<ExperimentalScenario> scenarios = new ArrayList<>();
        int[] scales = {100, 500, 1000, 2000, 5000}; // VM counts for scalability testing
        
        for (int i = 0; i < count; i++) {
            ExperimentalScenario scenario = new ExperimentalScenario();
            scenario.setScenarioName("Scalability_Test_" + i);
            scenario.setScenarioType("SCALABILITY");
            
            int vmCount = scales[i % scales.length];
            scenario.setHostCount(Math.max(10, vmCount / 10));
            scenario.setVmCount(vmCount);
            scenario.setCloudletCount(vmCount * 2);
            
            scenario.setHostSpecification(createHighCapacityHostSpecification());
            scenario.setVmSpecification(createStandardVmSpecification());
            scenario.setCloudletSpecification(createStandardCloudletSpecification());
            scenario.setSlaRequirements(createRelaxedSLARequirements());
            
            scenarios.add(scenario);
        }
        
        return scenarios;
    }
    
    /**
     * Generate workload diversity scenarios with different workload patterns.
     */
    private List<ExperimentalScenario> generateWorkloadScenarios(int count) throws ExperimentException {
        List<ExperimentalScenario> scenarios = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            ExperimentalScenario scenario = new ExperimentalScenario();
            scenario.setScenarioName("Workload_Diversity_" + i);
            scenario.setScenarioType("WORKLOAD_DIVERSITY");
            
            scenario.setHostCount(30 + random.nextInt(20));
            scenario.setVmCount(75 + random.nextInt(75));
            scenario.setCloudletCount(150 + random.nextInt(150));
            
            // Use different workload patterns
            String pattern = WORKLOAD_PATTERNS[i % WORKLOAD_PATTERNS.length];
            WorkloadCharacteristics workloadChar = createWorkloadForPattern(pattern);
            scenario.setWorkloadCharacteristics(workloadChar);
            
            scenario.setHostSpecification(createHeterogeneousHostSpecification());
            scenario.setVmSpecification(createHeterogeneousVmSpecification());
            scenario.setCloudletSpecification(createVariedCloudletSpecification());
            scenario.setSlaRequirements(createStandardSLARequirements());
            
            scenarios.add(scenario);
        }
        
        return scenarios;
    }
    
    /**
     * Generate resource constraint scenarios with limited resources.
     */
    private List<ExperimentalScenario> generateResourceConstraintScenarios(int count) throws ExperimentException {
        List<ExperimentalScenario> scenarios = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            ExperimentalScenario scenario = new ExperimentalScenario();
            scenario.setScenarioName("Resource_Constraint_" + i);
            scenario.setScenarioType("RESOURCE_CONSTRAINT");
            
            // Create resource pressure by having more VMs than optimal
            int hostCount = 15 + random.nextInt(15);
            scenario.setHostCount(hostCount);
            scenario.setVmCount(hostCount * 8 + random.nextInt(hostCount * 4)); // High VM to host ratio
            scenario.setCloudletCount(scenario.getVmCount() * 3);
            
            scenario.setHostSpecification(createLimitedHostSpecification());
            scenario.setVmSpecification(createDemandingVmSpecification());
            scenario.setCloudletSpecification(createIntensiveCloudletSpecification());
            scenario.setSlaRequirements(createStrictSLARequirements());
            
            scenarios.add(scenario);
        }
        
        return scenarios;
    }
    
    /**
     * Generate SLA-focused scenarios with strict requirements.
     */
    private List<ExperimentalScenario> generateSLAScenarios(int count) throws ExperimentException {
        List<ExperimentalScenario> scenarios = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            ExperimentalScenario scenario = new ExperimentalScenario();
            scenario.setScenarioName("SLA_Focused_" + i);
            scenario.setScenarioType("SLA_FOCUSED");
            
            scenario.setHostCount(25 + random.nextInt(25));
            scenario.setVmCount(60 + random.nextInt(60));
            scenario.setCloudletCount(120 + random.nextInt(120));
            
            scenario.setHostSpecification(createReliableHostSpecification());
            scenario.setVmSpecification(createStandardVmSpecification());
            scenario.setCloudletSpecification(createSLAConstrainedCloudletSpecification());
            scenario.setSlaRequirements(createStrictSLARequirements());
            
            scenarios.add(scenario);
        }
        
        return scenarios;
    }
    
    /**
     * Generate stress test scenarios with extreme conditions.
     */
    private List<ExperimentalScenario> generateStressTestScenarios(int count) throws ExperimentException {
        List<ExperimentalScenario> scenarios = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            ExperimentalScenario scenario = new ExperimentalScenario();
            scenario.setScenarioName("Stress_Test_" + i);
            scenario.setScenarioType("STRESS_TEST");
            
            // Extreme resource demands
            int hostCount = 50 + random.nextInt(50);
            scenario.setHostCount(hostCount);
            scenario.setVmCount(hostCount * 15); // Very high VM density
            scenario.setCloudletCount(scenario.getVmCount() * 10); // High workload
            
            scenario.setHostSpecification(createLimitedHostSpecification());
            scenario.setVmSpecification(createDemandingVmSpecification());
            scenario.setCloudletSpecification(createIntensiveCloudletSpecification());
            scenario.setSlaRequirements(createStrictSLARequirements());
            
            scenarios.add(scenario);
        }
        
        return scenarios;
    }
    
    /**
     * Generate a random scenario for variety.
     */
    private ExperimentalScenario generateRandomScenario() throws ExperimentException {
        ExperimentalScenario scenario = new ExperimentalScenario();
        scenario.setScenarioName("Random_" + System.currentTimeMillis());
        scenario.setScenarioType("RANDOM");
        
        scenario.setHostCount(DEFAULT_MIN_HOSTS + random.nextInt(DEFAULT_MAX_HOSTS - DEFAULT_MIN_HOSTS));
        scenario.setVmCount(DEFAULT_MIN_VMS + random.nextInt(DEFAULT_MAX_VMS - DEFAULT_MIN_VMS));
        scenario.setCloudletCount(DEFAULT_MIN_CLOUDLETS + random.nextInt(DEFAULT_MAX_CLOUDLETS - DEFAULT_MIN_CLOUDLETS));
        
        scenario.setHostSpecification(createRandomHostSpecification());
        scenario.setVmSpecification(createRandomVmSpecification());
        scenario.setCloudletSpecification(createRandomCloudletSpecification());
        scenario.setSlaRequirements(createRandomSLARequirements());
        
        return scenario;
    }
    
    // Helper methods for creating specifications
    
    private Map<String, Object> createRealisticHostSpecification() {
        Map<String, Object> spec = new HashMap<>();
        List<Integer> cpuCores = Arrays.asList(8, 16, 24, 32);
        List<Integer> ramGb = Arrays.asList(16, 32, 64, 128);
        List<Integer> storageGb = Arrays.asList(1000, 2000, 4000, 8000);
        
        spec.put("cpu_cores", cpuCores);
        spec.put("ram_gb", ramGb);
        spec.put("storage_gb", storageGb);
        spec.put("bandwidth_mbps", Arrays.asList(1000, 2000, 5000, 10000));
        spec.put("power_max_watts", Arrays.asList(200, 300, 450, 600));
        
        return spec;
    }
    
    private Map<String, Object> createHeterogeneousVmSpecification() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("cpu_cores", Arrays.asList(1, 2, 4, 8, 16));
        spec.put("ram_gb", Arrays.asList(1, 2, 4, 8, 16, 32));
        spec.put("storage_gb", Arrays.asList(20, 50, 100, 200, 500));
        spec.put("bandwidth_mbps", Arrays.asList(100, 250, 500, 1000, 2000));
        spec.put("heterogeneity", true);
        
        return spec;
    }
    
    private Map<String, Object> createRealisticCloudletSpecification() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("length_mi", Arrays.asList(5000, 10000, 25000, 50000, 100000));
        spec.put("file_size_mb", Arrays.asList(100, 250, 500, 1000, 2000));
        spec.put("output_size_mb", Arrays.asList(50, 125, 250, 500, 1000));
        spec.put("pes_number", Arrays.asList(1, 2, 4, 8));
        spec.put("priority", Arrays.asList(1, 2, 3, 4, 5)); // Different priority levels
        
        return spec;
    }
    
    private Map<String, Object> createRealisticSLARequirements() {
        Map<String, Object> sla = new HashMap<>();
        sla.put("max_response_time_ms", 500 + random.nextInt(1500));
        sla.put("min_availability_percent", 95.0 + random.nextDouble() * 4.0);
        sla.put("max_cpu_utilization_percent", 70.0 + random.nextDouble() * 20.0);
        sla.put("max_memory_utilization_percent", 75.0 + random.nextDouble() * 15.0);
        sla.put("min_throughput_ops_per_sec", 50 + random.nextInt(200));
        
        return sla;
    }
    
    // Additional helper methods for other specification types
    
    private Map<String, Object> createStandardHostSpecification() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("cpu_cores", Arrays.asList(8, 16));
        spec.put("ram_gb", Arrays.asList(32, 64));
        spec.put("storage_gb", Arrays.asList(1000, 2000));
        spec.put("bandwidth_mbps", Arrays.asList(1000, 2000));
        return spec;
    }
    
    private Map<String, Object> createHighCapacityHostSpecification() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("cpu_cores", Arrays.asList(32, 64, 128));
        spec.put("ram_gb", Arrays.asList(128, 256, 512));
        spec.put("storage_gb", Arrays.asList(4000, 8000, 16000));
        spec.put("bandwidth_mbps", Arrays.asList(10000, 25000, 40000));
        return spec;
    }
    
    private Map<String, Object> createLimitedHostSpecification() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("cpu_cores", Arrays.asList(4, 8));
        spec.put("ram_gb", Arrays.asList(8, 16));
        spec.put("storage_gb", Arrays.asList(500, 1000));
        spec.put("bandwidth_mbps", Arrays.asList(500, 1000));
        return spec;
    }
    
    private Map<String, Object> createReliableHostSpecification() {
        Map<String, Object> spec = createStandardHostSpecification();
        spec.put("reliability_factor", 0.99);
        spec.put("redundancy_level", "HIGH");
        return spec;
    }
    
    private Map<String, Object> createHeterogeneousHostSpecification() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("cpu_cores", Arrays.asList(4, 8, 16, 32, 64));
        spec.put("ram_gb", Arrays.asList(8, 16, 32, 64, 128));
        spec.put("storage_gb", Arrays.asList(500, 1000, 2000, 4000, 8000));
        spec.put("bandwidth_mbps", Arrays.asList(500, 1000, 2000, 5000, 10000));
        spec.put("heterogeneous", true);
        return spec;
    }
    
    private Map<String, Object> createStandardVmSpecification() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("cpu_cores", Arrays.asList(1, 2, 4));
        spec.put("ram_gb", Arrays.asList(2, 4, 8));
        spec.put("storage_gb", Arrays.asList(50, 100, 200));
        spec.put("bandwidth_mbps", Arrays.asList(250, 500, 1000));
        return spec;
    }
    
    private Map<String, Object> createDemandingVmSpecification() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("cpu_cores", Arrays.asList(4, 8, 16));
        spec.put("ram_gb", Arrays.asList(8, 16, 32));
        spec.put("storage_gb", Arrays.asList(200, 500, 1000));
        spec.put("bandwidth_mbps", Arrays.asList(1000, 2000, 5000));
        return spec;
    }
    
    private Map<String, Object> createVariedCloudletSpecification() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("length_mi", Arrays.asList(1000, 5000, 10000, 25000, 50000, 100000));
        spec.put("file_size_mb", Arrays.asList(50, 100, 250, 500, 1000, 2000));
        spec.put("output_size_mb", Arrays.asList(25, 50, 125, 250, 500, 1000));
        spec.put("pes_number", Arrays.asList(1, 2, 4, 8, 16));
        return spec;
    }
    
    private Map<String, Object> createStandardCloudletSpecification() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("length_mi", Arrays.asList(5000, 10000, 25000));
        spec.put("file_size_mb", Arrays.asList(100, 250, 500));
        spec.put("output_size_mb", Arrays.asList(50, 125, 250));
        spec.put("pes_number", Arrays.asList(1, 2, 4));
        return spec;
    }
    
    private Map<String, Object> createIntensiveCloudletSpecification() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("length_mi", Arrays.asList(50000, 100000, 250000));
        spec.put("file_size_mb", Arrays.asList(1000, 2000, 5000));
        spec.put("output_size_mb", Arrays.asList(500, 1000, 2500));
        spec.put("pes_number", Arrays.asList(4, 8, 16));
        return spec;
    }
    
    private Map<String, Object> createSLAConstrainedCloudletSpecification() {
        Map<String, Object> spec = createStandardCloudletSpecification();
        spec.put("deadline_ms", Arrays.asList(500, 1000, 2000));
        spec.put("priority", Arrays.asList(4, 5)); // High priority
        return spec;
    }
    
    private Map<String, Object> createStandardSLARequirements() {
        Map<String, Object> sla = new HashMap<>();
        sla.put("max_response_time_ms", 1000);
        sla.put("min_availability_percent", 98.0);
        sla.put("max_cpu_utilization_percent", 80.0);
        sla.put("max_memory_utilization_percent", 85.0);
        return sla;
    }
    
    private Map<String, Object> createStrictSLARequirements() {
        Map<String, Object> sla = new HashMap<>();
        sla.put("max_response_time_ms", 500);
        sla.put("min_availability_percent", 99.5);
        sla.put("max_cpu_utilization_percent", 70.0);
        sla.put("max_memory_utilization_percent", 75.0);
        return sla;
    }
    
    private Map<String, Object> createRelaxedSLARequirements() {
        Map<String, Object> sla = new HashMap<>();
        sla.put("max_response_time_ms", 2000);
        sla.put("min_availability_percent", 95.0);
        sla.put("max_cpu_utilization_percent", 90.0);
        sla.put("max_memory_utilization_percent", 90.0);
        return sla;
    }
    
    private Map<String, Object> createRandomHostSpecification() {
        @SuppressWarnings("unchecked")
        Map<String, Object> hostDefaults = (Map<String, Object>) defaultConfigurations.get("hosts");
        List<Integer> cpuOptions = (List<Integer>) hostDefaults.get("cpu_cores");
        List<Integer> ramOptions = (List<Integer>) hostDefaults.get("ram_gb");
        List<Integer> storageOptions = (List<Integer>) hostDefaults.get("storage_gb");
        
        Map<String, Object> spec = new HashMap<>();
        spec.put("cpu_cores", Arrays.asList(cpuOptions.get(random.nextInt(cpuOptions.size()))));
        spec.put("ram_gb", Arrays.asList(ramOptions.get(random.nextInt(ramOptions.size()))));
        spec.put("storage_gb", Arrays.asList(storageOptions.get(random.nextInt(storageOptions.size()))));
        
        return spec;
    }
    
    private Map<String, Object> createRandomVmSpecification() {
        @SuppressWarnings("unchecked")
        Map<String, Object> vmDefaults = (Map<String, Object>) defaultConfigurations.get("vms");
        @SuppressWarnings("unchecked")
        List<Integer> cpuOptions = (List<Integer>) vmDefaults.get("cpu_cores");
        @SuppressWarnings("unchecked")
        List<Integer> ramOptions = (List<Integer>) vmDefaults.get("ram_gb");
        
        Map<String, Object> spec = new HashMap<>();
        spec.put("cpu_cores", Arrays.asList(cpuOptions.get(random.nextInt(cpuOptions.size()))));
        spec.put("ram_gb", Arrays.asList(ramOptions.get(random.nextInt(ramOptions.size()))));
        
        return spec;
    }
    
    private Map<String, Object> createRandomCloudletSpecification() {
        @SuppressWarnings("unchecked")
        Map<String, Object> cloudletDefaults = (Map<String, Object>) defaultConfigurations.get("cloudlets");
        @SuppressWarnings("unchecked")
        List<Integer> lengthOptions = (List<Integer>) cloudletDefaults.get("length_mi");
        
        Map<String, Object> spec = new HashMap<>();
        spec.put("length_mi", Arrays.asList(lengthOptions.get(random.nextInt(lengthOptions.size()))));
        
        return spec;
    }
    
    private Map<String, Object> createRandomSLARequirements() {
        @SuppressWarnings("unchecked")
        Map<String, Object> slaDefaults = (Map<String, Object>) defaultConfigurations.get("sla");
        @SuppressWarnings("unchecked")
        List<Integer> responseTimeOptions = (List<Integer>) slaDefaults.get("max_response_time_ms");
        @SuppressWarnings("unchecked")
        List<Double> availabilityOptions = (List<Double>) slaDefaults.get("min_availability_percent");
        
        Map<String, Object> sla = new HashMap<>();
        sla.put("max_response_time_ms", responseTimeOptions.get(random.nextInt(responseTimeOptions.size())));
        sla.put("min_availability_percent", availabilityOptions.get(random.nextInt(availabilityOptions.size())));
        
        return sla;
    }
    
    private WorkloadCharacteristics createWorkloadForPattern(String pattern) {
        WorkloadCharacteristics workload = new WorkloadCharacteristics();
        workload.setWorkloadType(pattern);
        
        switch (pattern) {
            case "UNIFORM":
                workload.setCpuIntensive(false);
                workload.setMemoryIntensive(false);
                workload.setIoIntensive(false);
                workload.setBurstiness(0.1);
                workload.setSeasonality(0.0);
                break;
            case "EXPONENTIAL":
                workload.setCpuIntensive(true);
                workload.setMemoryIntensive(false);
                workload.setIoIntensive(false);
                workload.setBurstiness(0.8);
                workload.setSeasonality(0.2);
                break;
            case "NORMAL":
                workload.setCpuIntensive(true);
                workload.setMemoryIntensive(true);
                workload.setIoIntensive(false);
                workload.setBurstiness(0.3);
                workload.setSeasonality(0.3);
                break;
            case "BURSTY":
                workload.setCpuIntensive(true);
                workload.setMemoryIntensive(true);
                workload.setIoIntensive(true);
                workload.setBurstiness(0.9);
                workload.setSeasonality(0.1);
                break;
            case "PERIODIC":
                workload.setCpuIntensive(false);
                workload.setMemoryIntensive(true);
                workload.setIoIntensive(true);
                workload.setBurstiness(0.2);
                workload.setSeasonality(0.8);
                break;
            case "MIXED":
                workload.setCpuIntensive(random.nextBoolean());
                workload.setMemoryIntensive(random.nextBoolean());
                workload.setIoIntensive(random.nextBoolean());
                workload.setBurstiness(random.nextDouble());
                workload.setSeasonality(random.nextDouble());
                break;
            default:
                // Default to uniform
                workload.setCpuIntensive(false);
                workload.setMemoryIntensive(false);
                workload.setIoIntensive(false);
                workload.setBurstiness(0.1);
                workload.setSeasonality(0.0);
        }
        
        return workload;
    }
}
