package org.cloudbus.cloudsim.dataset;

import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelStochastic;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudbus.cloudsim.util.ExperimentException;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.ValidationUtils;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * SyntheticWorkloadGenerator - Generate synthetic workloads for CloudSim experiments
 * 
 * This class generates various types of synthetic workloads to test the Hippopotamus
 * Optimization algorithm under different scenarios including random, realistic,
 * stress test, and scalability test workloads.
 * 
 * Research Focus: Provides controlled experimental conditions for algorithm evaluation
 * Metrics: Generates workloads with known characteristics for baseline comparisons
 * Integration: Works with ExperimentalScenario and ScenarioGenerator components
 * Statistical Methods: Uses statistical distributions for realistic workload patterns
 * Publication: Enables reproducible synthetic workload generation for research
 * Datasets: Creates synthetic datasets with controllable parameters
 * 
 * @author Puneet Chandna
 * @version 1.0
 */
public class SyntheticWorkloadGenerator {
    
    private static final Random random = new Random();
    private long seed = System.currentTimeMillis();
    
    // Default workload parameters
    private static final int DEFAULT_VM_COUNT = 100;
    private static final int DEFAULT_CLOUDLET_COUNT = 200;
    private static final double DEFAULT_CPU_UTILIZATION_MEAN = 0.6;
    private static final double DEFAULT_CPU_UTILIZATION_STD = 0.2;
    private static final double DEFAULT_MEMORY_UTILIZATION_MEAN = 0.5;
    private static final double DEFAULT_MEMORY_UTILIZATION_STD = 0.15;
    
    // VM specifications ranges
    private static final int[] VM_MIPS_RANGE = {1000, 2000, 4000, 8000};
    private static final int[] VM_PE_RANGE = {1, 2, 4, 8};
    private static final int[] VM_RAM_RANGE = {512, 1024, 2048, 4096};
    private static final long[] VM_BW_RANGE = {1000, 10000, 100000, 1000000};
    private static final int[] VM_STORAGE_RANGE = {10000, 50000, 100000, 500000};
    
    // Cloudlet specifications ranges
    private static final long[] CLOUDLET_LENGTH_RANGE = {10000, 50000, 100000, 500000, 1000000};
    private static final int[] CLOUDLET_FILE_SIZE_RANGE = {300, 500, 1000, 2000};
    private static final int[] CLOUDLET_OUTPUT_SIZE_RANGE = {300, 500, 1000, 2000};
    
    /**
     * Constructor with default seed
     */
    public SyntheticWorkloadGenerator() {
        LoggingManager.logInfo("SyntheticWorkloadGenerator initialized with seed: " + seed);
        random.setSeed(seed);
    }
    
    /**
     * Constructor with custom seed for reproducibility
     * @param seed Random seed for reproducible workload generation
     */
    public SyntheticWorkloadGenerator(long seed) {
        this.seed = seed;
        random.setSeed(seed);
        LoggingManager.logInfo("SyntheticWorkloadGenerator initialized with custom seed: " + seed);
    }
    
    /**
     * Generate random workload with completely random characteristics
     * 
     * @param vmCount Number of VMs to generate
     * @param cloudletCount Number of cloudlets to generate
     * @return WorkloadCharacteristics containing the generated workload
     * @throws ExperimentException if generation fails
     */
    public WorkloadCharacteristics generateRandomWorkload(int vmCount, int cloudletCount) {
        try {
            LoggingManager.logInfo("Generating random workload: " + vmCount + " VMs, " + cloudletCount + " cloudlets");
            
            // Validate parameters
            ValidationUtils.validatePositive(vmCount, "VM count");
            ValidationUtils.validatePositive(cloudletCount, "Cloudlet count");
            
            List<Vm> vms = new ArrayList<>();
            List<Cloudlet> cloudlets = new ArrayList<>();
            
            // Generate random VMs
            for (int i = 0; i < vmCount; i++) {
                Vm vm = createRandomVm(i);
                vms.add(vm);
            }
            
            // Generate random cloudlets
            for (int i = 0; i < cloudletCount; i++) {
                Cloudlet cloudlet = createRandomCloudlet(i);
                cloudlets.add(cloudlet);
            }
            
            // Create workload characteristics
            WorkloadCharacteristics characteristics = new WorkloadCharacteristics();
            characteristics.setVms(vms);
            characteristics.setCloudlets(cloudlets);
            characteristics.setWorkloadType("RANDOM");
            characteristics.setGenerationSeed(seed);
            characteristics.calculateStatistics();
            
            LoggingManager.logInfo("Random workload generation completed successfully");
            return characteristics;
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to generate random workload", e);
        }
    }
    
    /**
     * Generate random workload with default parameters
     * 
     * @return WorkloadCharacteristics with default random workload
     * @throws ExperimentException if generation fails
     */
    public WorkloadCharacteristics generateRandomWorkload() {
        return generateRandomWorkload(DEFAULT_VM_COUNT, DEFAULT_CLOUDLET_COUNT);
    }
    
    /**
     * Generate realistic workload patterns based on real-world characteristics
     * 
     * @param vmCount Number of VMs to generate
     * @param cloudletCount Number of cloudlets to generate
     * @param pattern Workload pattern type ("WEB_SERVER", "BATCH_PROCESSING", "MIXED", "DATABASE")
     * @return WorkloadCharacteristics containing the realistic workload
     * @throws ExperimentException if generation fails
     */
    public WorkloadCharacteristics generateRealisticWorkload(int vmCount, int cloudletCount, String pattern) {
        try {
            LoggingManager.logInfo("Generating realistic workload pattern: " + pattern + 
                                 " (" + vmCount + " VMs, " + cloudletCount + " cloudlets)");
            
            // Validate parameters
            ValidationUtils.validatePositive(vmCount, "VM count");
            ValidationUtils.validatePositive(cloudletCount, "Cloudlet count");
            ValidationUtils.validateNotNull(pattern, "Workload pattern");
            
            List<Vm> vms = new ArrayList<>();
            List<Cloudlet> cloudlets = new ArrayList<>();
            
            switch (pattern.toUpperCase()) {
                case "WEB_SERVER":
                    generateWebServerWorkload(vms, cloudlets, vmCount, cloudletCount);
                    break;
                case "BATCH_PROCESSING":
                    generateBatchProcessingWorkload(vms, cloudlets, vmCount, cloudletCount);
                    break;
                case "DATABASE":
                    generateDatabaseWorkload(vms, cloudlets, vmCount, cloudletCount);
                    break;
                case "MIXED":
                default:
                    generateMixedWorkload(vms, cloudlets, vmCount, cloudletCount);
                    break;
            }
            
            // Create workload characteristics
            WorkloadCharacteristics characteristics = new WorkloadCharacteristics();
            characteristics.setVms(vms);
            characteristics.setCloudlets(cloudlets);
            characteristics.setWorkloadType("REALISTIC_" + pattern.toUpperCase());
            characteristics.setGenerationSeed(seed);
            characteristics.calculateStatistics();
            
            LoggingManager.logInfo("Realistic workload generation completed successfully");
            return characteristics;
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to generate realistic workload pattern: " + pattern, e);
        }
    }
    
    /**
     * Generate stress test workload for algorithm testing under extreme conditions
     * 
     * @param vmCount Number of VMs to generate
     * @param cloudletCount Number of cloudlets to generate
     * @param stressType Type of stress test ("HIGH_UTILIZATION", "RESOURCE_CONTENTION", "OVERSUBSCRIPTION")
     * @return WorkloadCharacteristics containing the stress test workload
     * @throws ExperimentException if generation fails
     */
    public WorkloadCharacteristics generateStressTestWorkload(int vmCount, int cloudletCount, String stressType) {
        try {
            LoggingManager.logInfo("Generating stress test workload: " + stressType + 
                                 " (" + vmCount + " VMs, " + cloudletCount + " cloudlets)");
            
            // Validate parameters
            ValidationUtils.validatePositive(vmCount, "VM count");
            ValidationUtils.validatePositive(cloudletCount, "Cloudlet count");
            ValidationUtils.validateNotNull(stressType, "Stress test type");
            
            List<Vm> vms = new ArrayList<>();
            List<Cloudlet> cloudlets = new ArrayList<>();
            
            switch (stressType.toUpperCase()) {
                case "HIGH_UTILIZATION":
                    generateHighUtilizationWorkload(vms, cloudlets, vmCount, cloudletCount);
                    break;
                case "RESOURCE_CONTENTION":
                    generateResourceContentionWorkload(vms, cloudlets, vmCount, cloudletCount);
                    break;
                case "OVERSUBSCRIPTION":
                    generateOversubscriptionWorkload(vms, cloudlets, vmCount, cloudletCount);
                    break;
                default:
                    throw new ExperimentException("Unknown stress test type: " + stressType);
            }
            
            // Create workload characteristics
            WorkloadCharacteristics characteristics = new WorkloadCharacteristics();
            characteristics.setVms(vms);
            characteristics.setCloudlets(cloudlets);
            characteristics.setWorkloadType("STRESS_" + stressType.toUpperCase());
            characteristics.setGenerationSeed(seed);
            characteristics.calculateStatistics();
            
            LoggingManager.logInfo("Stress test workload generation completed successfully");
            return characteristics;
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to generate stress test workload: " + stressType, e);
        }
    }
    
    /**
     * Generate scalability test workload for testing algorithm performance at different scales
     * 
     * @param vmCount Number of VMs to generate
     * @param cloudletCount Number of cloudlets to generate
     * @param scalabilityFactor Scaling factor for resource requirements
     * @return WorkloadCharacteristics containing the scalability test workload
     * @throws ExperimentException if generation fails
     */
    public WorkloadCharacteristics generateScalabilityWorkload(int vmCount, int cloudletCount, double scalabilityFactor) {
        try {
            LoggingManager.logInfo("Generating scalability test workload: factor=" + scalabilityFactor + 
                                 " (" + vmCount + " VMs, " + cloudletCount + " cloudlets)");
            
            // Validate parameters
            ValidationUtils.validatePositive(vmCount, "VM count");
            ValidationUtils.validatePositive(cloudletCount, "Cloudlet count");
            ValidationUtils.validatePositive(scalabilityFactor, "Scalability factor");
            
            List<Vm> vms = new ArrayList<>();
            List<Cloudlet> cloudlets = new ArrayList<>();
            
            // Generate VMs with scaled characteristics
            IntStream.range(0, vmCount).forEach(i -> {
                Vm vm = createScalableVm(i, scalabilityFactor);
                vms.add(vm);
            });
            
            // Generate cloudlets with scaled characteristics
            IntStream.range(0, cloudletCount).forEach(i -> {
                Cloudlet cloudlet = createScalableCloudlet(i, scalabilityFactor);
                cloudlets.add(cloudlet);
            });
            
            // Create workload characteristics
            WorkloadCharacteristics characteristics = new WorkloadCharacteristics();
            characteristics.setVms(vms);
            characteristics.setCloudlets(cloudlets);
            characteristics.setWorkloadType("SCALABILITY_" + String.valueOf(scalabilityFactor).replace(".", "_"));
            characteristics.setGenerationSeed(seed);
            characteristics.setScalabilityFactor(scalabilityFactor);
            characteristics.calculateStatistics();
            
            LoggingManager.logInfo("Scalability test workload generation completed successfully");
            return characteristics;
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to generate scalability test workload", e);
        }
    }
    
    /**
     * Create a random VM with random specifications
     */
    private Vm createRandomVm(int id) {
        int mips = VM_MIPS_RANGE[random.nextInt(VM_MIPS_RANGE.length)];
        int pesNumber = VM_PE_RANGE[random.nextInt(VM_PE_RANGE.length)];
        int ram = VM_RAM_RANGE[random.nextInt(VM_RAM_RANGE.length)];
        long bw = VM_BW_RANGE[random.nextInt(VM_BW_RANGE.length)];
        int storage = VM_STORAGE_RANGE[random.nextInt(VM_STORAGE_RANGE.length)];
        
        return new VmSimple(id, mips, pesNumber)
                .setRam(ram)
                .setBw(bw)
                .setSize(storage);
    }
    
    /**
     * Create a random cloudlet with random specifications
     */
    private Cloudlet createRandomCloudlet(int id) {
        long length = CLOUDLET_LENGTH_RANGE[random.nextInt(CLOUDLET_LENGTH_RANGE.length)];
        int pesNumber = random.nextInt(8) + 1; // 1-8 PEs
        int fileSize = CLOUDLET_FILE_SIZE_RANGE[random.nextInt(CLOUDLET_FILE_SIZE_RANGE.length)];
        int outputSize = CLOUDLET_OUTPUT_SIZE_RANGE[random.nextInt(CLOUDLET_OUTPUT_SIZE_RANGE.length)];
        
        UtilizationModel utilizationModel = createRandomUtilizationModel();
        
        return new CloudletSimple(id, length, pesNumber)
                .setFileSize(fileSize)
                .setOutputSize(outputSize)
                .setUtilizationModelCpu(utilizationModel)
                .setUtilizationModelRam(utilizationModel)
                .setUtilizationModelBw(utilizationModel);
    }
    
    /**
     * Generate web server workload pattern
     */
    private void generateWebServerWorkload(List<Vm> vms, List<Cloudlet> cloudlets, int vmCount, int cloudletCount) {
        // Web servers typically have moderate CPU, high network I/O
        for (int i = 0; i < vmCount; i++) {
            Vm vm = new VmSimple(i, 2000, 2) // Moderate CPU
                    .setRam(2048) // Moderate RAM
                    .setBw(100000) // High bandwidth
                    .setSize(50000); // Moderate storage
            vms.add(vm);
        }
        
        // Web requests are typically short, frequent
        for (int i = 0; i < cloudletCount; i++) {
            UtilizationModel webUtilization = new UtilizationModelDynamic(0.3);
            Cloudlet cloudlet = new CloudletSimple(i, 25000, 1) // Short tasks
                    .setFileSize(500)
                    .setOutputSize(1000) // Web responses
                    .setUtilizationModelCpu(webUtilization)
                    .setUtilizationModelRam(webUtilization)
                    .setUtilizationModelBw(new UtilizationModelDynamic(0.7)); // High network usage
            cloudlets.add(cloudlet);
        }
    }
    
    /**
     * Generate batch processing workload pattern
     */
    private void generateBatchProcessingWorkload(List<Vm> vms, List<Cloudlet> cloudlets, int vmCount, int cloudletCount) {
        // Batch processing typically needs high CPU, moderate I/O
        for (int i = 0; i < vmCount; i++) {
            Vm vm = new VmSimple(i, 4000, 4) // High CPU
                    .setRam(4096) // High RAM
                    .setBw(10000) // Moderate bandwidth
                    .setSize(100000); // High storage
            vms.add(vm);
        }
        
        // Batch jobs are typically long-running, CPU intensive
        for (int i = 0; i < cloudletCount; i++) {
            UtilizationModel batchUtilization = new UtilizationModelDynamic(0.8);
            Cloudlet cloudlet = new CloudletSimple(i, 500000, 4) // Long tasks
                    .setFileSize(2000)
                    .setOutputSize(2000)
                    .setUtilizationModelCpu(batchUtilization) // High CPU usage
                    .setUtilizationModelRam(new UtilizationModelDynamic(0.6))
                    .setUtilizationModelBw(new UtilizationModelDynamic(0.2)); // Low network usage
            cloudlets.add(cloudlet);
        }
    }
    
    /**
     * Generate database workload pattern
     */
    private void generateDatabaseWorkload(List<Vm> vms, List<Cloudlet> cloudlets, int vmCount, int cloudletCount) {
        // Databases need high I/O, moderate CPU
        for (int i = 0; i < vmCount; i++) {
            Vm vm = new VmSimple(i, 3000, 4) // Moderate-high CPU
                    .setRam(8192) // Very high RAM
                    .setBw(50000) // High bandwidth
                    .setSize(500000); // Very high storage
            vms.add(vm);
        }
        
        // Database queries have mixed characteristics
        for (int i = 0; i < cloudletCount; i++) {
            UtilizationModel dbUtilization = new UtilizationModelDynamic(0.4);
            Cloudlet cloudlet = new CloudletSimple(i, 100000, 2)
                    .setFileSize(1000)
                    .setOutputSize(500)
                    .setUtilizationModelCpu(dbUtilization)
                    .setUtilizationModelRam(new UtilizationModelDynamic(0.7)) // High RAM usage
                    .setUtilizationModelBw(new UtilizationModelDynamic(0.5));
            cloudlets.add(cloudlet);
        }
    }
    
    /**
     * Generate mixed workload pattern
     */
    private void generateMixedWorkload(List<Vm> vms, List<Cloudlet> cloudlets, int vmCount, int cloudletCount) {
        // Mix of different VM types
        for (int i = 0; i < vmCount; i++) {
            Vm vm;
            if (i % 3 == 0) {
                // Web server type
                vm = new VmSimple(i, 2000, 2).setRam(2048).setBw(100000).setSize(50000);
            } else if (i % 3 == 1) {
                // Batch processing type
                vm = new VmSimple(i, 4000, 4).setRam(4096).setBw(10000).setSize(100000);
            } else {
                // Database type
                vm = new VmSimple(i, 3000, 4).setRam(8192).setBw(50000).setSize(500000);
            }
            vms.add(vm);
        }
        
        // Mix of different cloudlet types
        for (int i = 0; i < cloudletCount; i++) {
            Cloudlet cloudlet;
            if (i % 3 == 0) {
                // Web request type
                cloudlet = new CloudletSimple(i, 25000, 1)
                        .setFileSize(500).setOutputSize(1000)
                        .setUtilizationModelCpu(new UtilizationModelDynamic(0.3));
            } else if (i % 3 == 1) {
                // Batch job type
                cloudlet = new CloudletSimple(i, 500000, 4)
                        .setFileSize(2000).setOutputSize(2000)
                        .setUtilizationModelCpu(new UtilizationModelDynamic(0.8));
            } else {
                // Database query type
                cloudlet = new CloudletSimple(i, 100000, 2)
                        .setFileSize(1000).setOutputSize(500)
                        .setUtilizationModelCpu(new UtilizationModelDynamic(0.4));
            }
            cloudlets.add(cloudlet);
        }
    }
    
    /**
     * Generate high utilization stress test workload
     */
    private void generateHighUtilizationWorkload(List<Vm> vms, List<Cloudlet> cloudlets, int vmCount, int cloudletCount) {
        // High-end VMs
        for (int i = 0; i < vmCount; i++) {
            Vm vm = new VmSimple(i, 8000, 8) // Maximum CPU
                    .setRam(4096)
                    .setBw(1000000)
                    .setSize(500000);
            vms.add(vm);
        }
        
        // Very demanding cloudlets
        for (int i = 0; i < cloudletCount; i++) {
            UtilizationModel highUtilization = new UtilizationModelDynamic(0.95);
            Cloudlet cloudlet = new CloudletSimple(i, 1000000, 8) // Very long tasks
                    .setFileSize(2000)
                    .setOutputSize(2000)
                    .setUtilizationModelCpu(highUtilization)
                    .setUtilizationModelRam(highUtilization)
                    .setUtilizationModelBw(highUtilization);
            cloudlets.add(cloudlet);
        }
    }
    
    /**
     * Generate resource contention stress test workload
     */
    private void generateResourceContentionWorkload(List<Vm> vms, List<Cloudlet> cloudlets, int vmCount, int cloudletCount) {
        // Limited variety of VM types to create contention
        for (int i = 0; i < vmCount; i++) {
            Vm vm = new VmSimple(i, 2000, 2) // Limited CPU variety
                    .setRam(1024) // Limited RAM
                    .setBw(10000)
                    .setSize(50000);
            vms.add(vm);
        }
        
        // More cloudlets than optimal for available resources
        for (int i = 0; i < cloudletCount; i++) {
            UtilizationModel contentionUtilization = new UtilizationModelDynamic(0.7);
            Cloudlet cloudlet = new CloudletSimple(i, 200000, 2)
                    .setFileSize(1500)
                    .setOutputSize(1500)
                    .setUtilizationModelCpu(contentionUtilization)
                    .setUtilizationModelRam(contentionUtilization)
                    .setUtilizationModelBw(contentionUtilization);
            cloudlets.add(cloudlet);
        }
    }
    
    /**
     * Generate oversubscription stress test workload
     */
    private void generateOversubscriptionWorkload(List<Vm> vms, List<Cloudlet> cloudlets, int vmCount, int cloudletCount) {
        // Many small VMs
        for (int i = 0; i < vmCount; i++) {
            Vm vm = new VmSimple(i, 1000, 1) // Small VMs
                    .setRam(512)
                    .setBw(1000)
                    .setSize(10000);
            vms.add(vm);
        }
        
        // Cloudlets that exceed VM capacity when combined
        for (int i = 0; i < cloudletCount; i++) {
            UtilizationModel oversubscriptionUtilization = new UtilizationModelFull();
            Cloudlet cloudlet = new CloudletSimple(i, 150000, 1)
                    .setFileSize(1000)
                    .setOutputSize(1000)
                    .setUtilizationModelCpu(oversubscriptionUtilization)
                    .setUtilizationModelRam(oversubscriptionUtilization)
                    .setUtilizationModelBw(oversubscriptionUtilization);
            cloudlets.add(cloudlet);
        }
    }
    
    /**
     * Create a scalable VM based on scaling factor
     */
    private Vm createScalableVm(int id, double scalabilityFactor) {
        int baseMips = 2000;
        int basePes = 2;
        int baseRam = 2048;
        long baseBw = 10000;
        int baseStorage = 50000;
        
        int mips = (int) (baseMips * scalabilityFactor);
        int pesNumber = Math.max(1, (int) (basePes * scalabilityFactor));
        int ram = (int) (baseRam * scalabilityFactor);
        long bw = (long) (baseBw * scalabilityFactor);
        int storage = (int) (baseStorage * scalabilityFactor);
        
        return new VmSimple(id, mips, pesNumber)
                .setRam(ram)
                .setBw(bw)
                .setSize(storage);
    }
    
    /**
     * Create a scalable cloudlet based on scaling factor
     */
    private Cloudlet createScalableCloudlet(int id, double scalabilityFactor) {
        long baseLength = 100000;
        int basePes = 1;
        int baseFileSize = 1000;
        int baseOutputSize = 1000;
        
        long length = (long) (baseLength * scalabilityFactor);
        int pesNumber = Math.max(1, (int) (basePes * scalabilityFactor));
        int fileSize = (int) (baseFileSize * scalabilityFactor);
        int outputSize = (int) (baseOutputSize * scalabilityFactor);
        
        UtilizationModel utilizationModel = new UtilizationModelDynamic(
                DEFAULT_CPU_UTILIZATION_MEAN * scalabilityFactor);
        
        return new CloudletSimple(id, length, pesNumber)
                .setFileSize(fileSize)
                .setOutputSize(outputSize)
                .setUtilizationModelCpu(utilizationModel)
                .setUtilizationModelRam(utilizationModel)
                .setUtilizationModelBw(utilizationModel);
    }
    
    /**
     * Create a random utilization model
     */
    private UtilizationModel createRandomUtilizationModel() {
        double utilizationMean = ThreadLocalRandom.current().nextDouble(0.2, 0.9);
        return new UtilizationModelDynamic(utilizationMean);
    }
    
    /**
     * Set seed for reproducible workload generation
     * @param seed Random seed
     */
    public void setSeed(long seed) {
        this.seed = seed;
        random.setSeed(seed);
        LoggingManager.logInfo("SyntheticWorkloadGenerator seed set to: " + seed);
    }
    
    /**
     * Get current seed
     * @return Current random seed
     */
    public long getSeed() {
        return seed;
    }
}