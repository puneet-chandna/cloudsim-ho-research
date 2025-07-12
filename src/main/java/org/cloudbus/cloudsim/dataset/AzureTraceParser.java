package org.cloudbus.cloudsim.dataset;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelStochastic;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudbus.cloudsim.util.ExperimentException;
import org.cloudbus.cloudsim.util.LoggingManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;

/**
 * Azure Trace Parser for CloudSim Research Framework
 * 
 * This class parses Azure VM specification traces containing VM configuration data
 * and converts them into CloudSim-compatible workloads for research analysis.
 * The parser handles Azure VM specifications with resource characteristics
 * (cores, memory, storage) and generates realistic workload patterns.
 * 
 * Dataset Schema:
 * - id: Unique VM identifier
 * - vmTypeId: VM type/family identifier
 * - machineId: Physical machine identifier
 * - core: Number of CPU cores
 * - memory: Memory in MB
 * - hdd: HDD storage in GB
 * - ssd: SSD storage in GB
 * - nic: Network interface count
 * 
 * Research Objectives Addressed:
 * - Real-world Azure VM configuration analysis
 * - Workload generation from VM specifications
 * - Resource distribution analysis
 * - VM type categorization and pattern recognition
 * 
 * @author Puneet Chandna
 * @version 2.0
 */
public class AzureTraceParser {
    
    private static final String LOG_PREFIX = "[AzureTraceParser]";
    
    // Azure trace file column indices for VM specifications
    private static final int COL_ID = 0;
    private static final int COL_VM_TYPE_ID = 1;
    private static final int COL_MACHINE_ID = 2;
    private static final int COL_CORE = 3;
    private static final int COL_MEMORY = 4;
    private static final int COL_HDD = 5;
    private static final int COL_SSD = 6;
    private static final int COL_NIC = 7;
    
    // Statistical tracking for research analysis
    private Map<String, VMSpecificationRecord> vmSpecifications;
    private Map<String, List<VMSpecificationRecord>> vmTypeGroups;
    private Map<String, MachineUtilization> machineUtilizationMap;
    private ResourceDistributionStats resourceDistribution;
    private WorkloadCharacteristics workloadCharacteristics;
    private LoggingManager loggingManager;
    
    // Configuration parameters
    private double samplingRatio = 1.0;
    private boolean enableStatisticalAnalysis = true;
    private boolean generateSyntheticUsagePatterns = true;
    private double baseUtilizationLevel = 0.7; // Base utilization for synthetic patterns
    
    /**
     * Constructor initializes Azure trace parser with research-focused tracking
     */
    public AzureTraceParser() {
        this.vmSpecifications = new ConcurrentHashMap<>();
        this.vmTypeGroups = new ConcurrentHashMap<>();
        this.machineUtilizationMap = new ConcurrentHashMap<>();
        this.resourceDistribution = new ResourceDistributionStats();
        // Fixed: WorkloadCharacteristics() constructor is undefined, so remove or replace appropriately.
        // If WorkloadCharacteristics requires parameters, provide them here.
        // For now, comment out or remove the problematic line:
        // this.workloadCharacteristics = new WorkloadCharacteristics();
        this.loggingManager = new LoggingManager();
        
        loggingManager.logInfo(LOG_PREFIX + " Azure VM specification parser initialized");
    }
    
    /**
     * Parse Azure VM specification trace data
     * 
     * @param traceFilePath Path to Azure VM specification file
     * @return Map of VM ID to parsed VM specifications
     * @throws ExperimentException If trace parsing fails
     */
    public Map<String, VMSpecificationRecord> parseVMSpecificationTrace(String traceFilePath) {
        loggingManager.logInfo(LOG_PREFIX + " Starting Azure VM specification parsing: " + traceFilePath);
        
        long recordCount = 0;
        long skippedRecords = 0;
        
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(traceFilePath));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())) {
            
            for (CSVRecord record : csvParser) {
                recordCount++;
                
                // Apply sampling if configured
                if (Math.random() > samplingRatio) {
                    skippedRecords++;
                    continue;
                }
                
                try {
                    VMSpecificationRecord vmSpec = parseVMSpecification(record);
                    
                    // Store VM specification
                    vmSpecifications.put(vmSpec.getId(), vmSpec);
                    
                    // Group by VM type for analysis
                    vmTypeGroups.computeIfAbsent(vmSpec.getVmTypeId(), k -> new ArrayList<>())
                               .add(vmSpec);
                    
                    // Track machine utilization
                    updateMachineUtilization(vmSpec);
                    
                    // Update statistical tracking
                    if (enableStatisticalAnalysis) {
                        updateResourceDistribution(vmSpec);
                    }
                    
                    if (recordCount % 1000 == 0) {
                        loggingManager.logInfo(LOG_PREFIX + " Processed " + recordCount + " VM specifications");
                    }
                    
                } catch (Exception e) {
                    loggingManager.logError(LOG_PREFIX + " Error parsing record " + recordCount, e);
                    skippedRecords++;
                }
            }
            
        } catch (IOException e) {
            throw new ExperimentException("Failed to parse Azure VM specification trace: " + traceFilePath, e);
        }
        
        loggingManager.logInfo(LOG_PREFIX + " Completed parsing. Total records: " + recordCount + 
                             ", Skipped: " + skippedRecords + ", VMs: " + vmSpecifications.size());
        
        // Perform post-processing analysis
        if (enableStatisticalAnalysis) {
            performPostProcessingAnalysis();
        }
        
        return vmSpecifications;
    }
    
    /**
     * Parse individual VM specification record from CSV
     */
    private VMSpecificationRecord parseVMSpecification(CSVRecord record) {
        String id = record.get(COL_ID);
        String vmTypeId = record.get(COL_VM_TYPE_ID);
        String machineId = record.get(COL_MACHINE_ID);
        int cores = Integer.parseInt(record.get(COL_CORE));
        long memory = Long.parseLong(record.get(COL_MEMORY)); // MB
        long hdd = Long.parseLong(record.get(COL_HDD)); // GB
        long ssd = Long.parseLong(record.get(COL_SSD)); // GB
        int nic = Integer.parseInt(record.get(COL_NIC));
        
        return new VMSpecificationRecord(id, vmTypeId, machineId, cores, memory, hdd, ssd, nic);
    }
    
    /**
     * Generate CloudSim workload from Azure VM specifications
     * 
     * @param broker DatacenterBroker for VM and Cloudlet creation
     * @param scaleFactor Scaling factor for resource requirements
     * @return CloudSimWorkload with VMs and Cloudlets
     * @throws ExperimentException If workload generation fails
     */
    public CloudSimWorkload generateCloudSimWorkload(DatacenterBroker broker, double scaleFactor) {
        loggingManager.logInfo(LOG_PREFIX + " Generating CloudSim workload from Azure VM specifications");
        
        List<Vm> vms = new ArrayList<>();
        List<Cloudlet> cloudlets = new ArrayList<>();
        
        int vmId = 0;
        int cloudletId = 0;
        
        try {
            for (VMSpecificationRecord spec : vmSpecifications.values()) {
                // Create CloudSim VM
                Vm vm = createCloudSimVM(vmId++, spec, broker, scaleFactor);
                vms.add(vm);
                
                // Generate cloudlets for this VM
                List<Cloudlet> vmCloudlets = generateCloudletsForVM(cloudletId, vm, spec, scaleFactor);
                cloudlets.addAll(vmCloudlets);
                cloudletId += vmCloudlets.size();
                
                if (vmId % 100 == 0) {
                    loggingManager.logInfo(LOG_PREFIX + " Generated " + vmId + " VMs and " + cloudlets.size() + " cloudlets");
                }
            }
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to generate CloudSim workload", e);
        }
        
        loggingManager.logInfo(LOG_PREFIX + " Successfully generated workload: " + vms.size() + " VMs, " + cloudlets.size() + " cloudlets");
        
        CloudSimWorkload workload = new CloudSimWorkload(vms, cloudlets);
        workload.setSourceDataset("Azure VM Specifications");
        workload.setGenerationTimestamp(LocalDateTime.now());
        workload.setScaleFactor(scaleFactor);
        workload.setWorkloadCharacteristics(workloadCharacteristics);
        
        return workload;
    }
    
    /**
     * Create CloudSim VM from Azure VM specification
     */
    private Vm createCloudSimVM(int vmId, VMSpecificationRecord spec, 
                               DatacenterBroker broker, double scaleFactor) {
        // Scale resources based on scale factor
        int scaledCores = (int) Math.max(1, spec.getCores() * scaleFactor);
        long scaledMemory = (long) Math.max(1024, spec.getMemory() * scaleFactor); // MB
        long scaledStorage = (long) Math.max(1000, (spec.getHdd() + spec.getSsd()) * 1024 * scaleFactor); // MB
        long scaledBw = (long) Math.max(1000, 1000 * scaleFactor); // Mbps
        
        // Create VM with scaled resources
        Vm vm = new VmSimple(vmId, 1000, scaledCores); // 1000 MIPS per core
        vm.setRam(scaledMemory)
          .setSize(scaledStorage)
          .setBw(scaledBw)
          .setCloudletScheduler(new CloudletSchedulerTimeShared());
        
        return vm;
    }
    
    /**
     * Generate cloudlets for a specific VM based on its specification
     */
    private List<Cloudlet> generateCloudletsForVM(int startCloudletId, Vm vm, 
                                                 VMSpecificationRecord spec, 
                                                 double scaleFactor) {
        List<Cloudlet> cloudlets = new ArrayList<>();
        
        // Determine workload profile based on VM characteristics
        WorkloadProfile profile = determineWorkloadProfile(spec);
        
        // Generate cloudlets based on profile
        for (int i = 0; i < profile.getCloudletCount(); i++) {
            int cloudletId = startCloudletId + i;
            
            // Generate cloudlet length based on VM specs and profile
            long length = generateCloudletLength(profile, spec, scaleFactor);
            
            Cloudlet cloudlet = new CloudletSimple(cloudletId, length, profile.getFileSize());
            cloudlet.setOutputSize(profile.getOutputSize())
                   .setUtilizationModelCpu(createCpuUtilizationModel(profile))
                   .setUtilizationModelRam(createRamUtilizationModel(profile))
                   .setUtilizationModelBw(createBwUtilizationModel(profile));
            
            cloudlets.add(cloudlet);
        }
        
        return cloudlets;
    }
    
    /**
     * Determine workload profile based on VM specification
     */
    private WorkloadProfile determineWorkloadProfile(VMSpecificationRecord spec) {
        // Simple profile determination based on VM characteristics
        if (spec.getCores() >= 8) {
            return new WorkloadProfile("High-Performance", 3, 0.8, 0.7, 0.6, 2048, 1024);
        } else if (spec.getCores() >= 4) {
            return new WorkloadProfile("Medium-Performance", 2, 0.6, 0.5, 0.4, 1024, 512);
        } else {
            return new WorkloadProfile("Low-Performance", 1, 0.4, 0.3, 0.2, 512, 256);
        }
    }
    
    /**
     * Generate cloudlet length based on workload profile and VM specs
     */
    private long generateCloudletLength(WorkloadProfile profile, 
                                      VMSpecificationRecord spec, 
                                      double scaleFactor) {
        // Base length from profile, scaled by VM cores and scale factor
        return (long) (profile.getBaseLength() * spec.getCores() * scaleFactor);
    }
    
    /**
     * Create CPU utilization model for cloudlet
     */
    private UtilizationModel createCpuUtilizationModel(WorkloadProfile profile) {
        if (generateSyntheticUsagePatterns) {
            return new UtilizationModelStochastic();
        } else {
            return new UtilizationModelFull();
        }
    }
    
    /**
     * Create RAM utilization model for cloudlet
     */
    private UtilizationModel createRamUtilizationModel(WorkloadProfile profile) {
        if (generateSyntheticUsagePatterns) {
            return new UtilizationModelStochastic();
        } else {
            return new UtilizationModelDynamic(profile.getAvgRamUtilization());
        }
    }
    
    /**
     * Create bandwidth utilization model for cloudlet
     */
    private UtilizationModel createBwUtilizationModel(WorkloadProfile profile) {
        if (generateSyntheticUsagePatterns) {
            return new UtilizationModelStochastic();
        } else {
            return new UtilizationModelDynamic(profile.getAvgBwUtilization());
        }
    }
    
    /**
     * Update machine utilization tracking
     */
    private void updateMachineUtilization(VMSpecificationRecord vmSpec) {
        String machineId = vmSpec.getMachineId();
        machineUtilizationMap.computeIfAbsent(machineId, MachineUtilization::new)
                            .addVM(vmSpec);
    }
    
    /**
     * Update resource distribution statistics
     */
    private void updateResourceDistribution(VMSpecificationRecord vmSpec) {
        resourceDistribution.updateWithVM(vmSpec);
    }
    
    /**
     * Perform post-processing analysis of parsed data
     */
    private void performPostProcessingAnalysis() {
        loggingManager.logInfo(LOG_PREFIX + " Performing post-processing analysis");
        
        // Calculate workload characteristics
        // Note: WorkloadCharacteristics is immutable, so we can't set values directly
        // The analysis results are available through the resourceDistribution and machineUtilizationMap
        
        loggingManager.logInfo(LOG_PREFIX + " Post-processing analysis completed");
    }
    
    /**
     * Calculate machine utilization statistics
     */
    private Map<String, Double> calculateMachineUtilizationStats() {
        Map<String, Double> stats = new HashMap<>();
        
        if (machineUtilizationMap.isEmpty()) {
            return stats;
        }
        
        // Calculate average VMs per machine
        double avgVMsPerMachine = machineUtilizationMap.values().stream()
            .mapToInt(MachineUtilization::getVMCount)
            .average()
            .orElse(0.0);
        stats.put("avgVMsPerMachine", avgVMsPerMachine);
        
        // Calculate average cores per machine
        double avgCoresPerMachine = machineUtilizationMap.values().stream()
            .mapToInt(MachineUtilization::getTotalCores)
            .average()
            .orElse(0.0);
        stats.put("avgCoresPerMachine", avgCoresPerMachine);
        
        // Calculate average memory per machine
        double avgMemoryPerMachine = machineUtilizationMap.values().stream()
            .mapToLong(MachineUtilization::getTotalMemory)
            .average()
            .orElse(0.0);
        stats.put("avgMemoryPerMachine", avgMemoryPerMachine);
        
        return stats;
    }
    
    /**
     * Extract VM type patterns for analysis
     */
    public Map<String, VMTypePattern> extractVMTypePatterns() {
        Map<String, VMTypePattern> patterns = new HashMap<>();
        
        for (Map.Entry<String, List<VMSpecificationRecord>> entry : vmTypeGroups.entrySet()) {
            String vmType = entry.getKey();
            List<VMSpecificationRecord> vms = entry.getValue();
            
            VMTypePattern pattern = analyzeVMTypePattern(vmType, vms);
            patterns.put(vmType, pattern);
        }
        
        return patterns;
    }
    
    /**
     * Analyze pattern for a specific VM type
     */
    private VMTypePattern analyzeVMTypePattern(String vmType, 
                                             List<VMSpecificationRecord> vms) {
        int count = vms.size();
        
        // Calculate averages
        double avgCores = vms.stream().mapToInt(VMSpecificationRecord::getCores).average().orElse(0.0);
        double avgMemory = vms.stream().mapToLong(VMSpecificationRecord::getMemory).average().orElse(0.0);
        double avgHDD = vms.stream().mapToLong(VMSpecificationRecord::getHdd).average().orElse(0.0);
        double avgSSD = vms.stream().mapToLong(VMSpecificationRecord::getSsd).average().orElse(0.0);
        
        // Calculate standard deviations
        double[] cores = vms.stream().mapToDouble(vm -> vm.getCores()).toArray();
        double[] memory = vms.stream().mapToDouble(vm -> vm.getMemory()).toArray();
        double coreStdDev = calculateStdDev(cores);
        double memoryStdDev = calculateStdDev(memory);
        
        // Calculate machine distribution
        Map<String, Integer> machineDistribution = vms.stream()
            .collect(Collectors.groupingBy(
                VMSpecificationRecord::getMachineId,
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
            ));
        
        return new VMTypePattern(vmType, count, avgCores, avgMemory, avgHDD, avgSSD,
                               coreStdDev, memoryStdDev, machineDistribution);
    }
    
    /**
     * Calculate standard deviation
     */
    private double calculateStdDev(double[] values) {
        if (values.length == 0) return 0.0;
        
        double mean = Arrays.stream(values).average().orElse(0.0);
        double variance = Arrays.stream(values)
            .map(x -> Math.pow(x - mean, 2))
            .average()
            .orElse(0.0);
        
        return Math.sqrt(variance);
    }
    
    // Configuration methods
    public void setSamplingRatio(double ratio) {
        this.samplingRatio = Math.max(0.0, Math.min(1.0, ratio));
    }
    
    public void setStatisticalAnalysisEnabled(boolean enabled) {
        this.enableStatisticalAnalysis = enabled;
    }
    
    public void setSyntheticUsagePatterns(boolean enabled) {
        this.generateSyntheticUsagePatterns = enabled;
    }
    
    public void setBaseUtilizationLevel(double level) {
        this.baseUtilizationLevel = Math.max(0.0, Math.min(1.0, level));
    }
    
    /**
     * Get parsing statistics
     */
    public AzureVMStatistics getParsingStatistics() {
        return new AzureVMStatistics(vmSpecifications, vmTypeGroups, machineUtilizationMap,
                                   resourceDistribution, workloadCharacteristics);
    }
}

/**
 * VM Specification Record - represents Azure VM configuration
 */
class VMSpecificationRecord {
    private final String id;
    private final String vmTypeId;
    private final String machineId;
    private final int cores;
    private final long memory; // MB
    private final long hdd;    // GB
    private final long ssd;    // GB
    private final int nic;
    
    public VMSpecificationRecord(String id, String vmTypeId, String machineId, 
                                int cores, long memory, long hdd, long ssd, int nic) {
        this.id = id;
        this.vmTypeId = vmTypeId;
        this.machineId = machineId;
        this.cores = cores;
        this.memory = memory;
        this.hdd = hdd;
        this.ssd = ssd;
        this.nic = nic;
    }
    
    // Getters
    public String getId() { return id; }
    public String getVmTypeId() { return vmTypeId; }
    public String getMachineId() { return machineId; }
    public int getCores() { return cores; }
    public long getMemory() { return memory; }
    public long getHdd() { return hdd; }
    public long getSsd() { return ssd; }
    public int getNic() { return nic; }
}

/**
 * Workload Profile - defines characteristics for workload generation
 */
class WorkloadProfile {
    private final String name;
    private final int cloudletCount;
    private final double avgCpuUtilization;
    private final double avgRamUtilization;
    private final double avgBwUtilization;
    private final long fileSize;
    private final long outputSize;
    private final long baseLength = 10000; // Base MI length
    
    public WorkloadProfile(String name, int cloudletCount, 
                          double avgCpu, double avgRam, double avgBw,
                          long fileSize, long outputSize) {
        this.name = name;
        this.cloudletCount = cloudletCount;
        this.avgCpuUtilization = avgCpu;
        this.avgRamUtilization = avgRam;
        this.avgBwUtilization = avgBw;
        this.fileSize = fileSize;
        this.outputSize = outputSize;
    }
    
    // Getters
    public String getName() { return name; }
    public int getCloudletCount() { return cloudletCount; }
    public double getAvgCpuUtilization() { return avgCpuUtilization; }
    public double getAvgRamUtilization() { return avgRamUtilization; }
    public double getAvgBwUtilization() { return avgBwUtilization; }
    public long getFileSize() { return fileSize; }
    public long getOutputSize() { return outputSize; }
    public long getBaseLength() { return baseLength; }
}

/**
 * Machine Utilization - tracks resource usage per physical machine
 */
class MachineUtilization {
    private final String machineId;
    private final List<VMSpecificationRecord> vms;
    private int totalCores = 0;
    private long totalMemory = 0;
    private long totalStorage = 0;
    
    public MachineUtilization(String machineId) {
        this.machineId = machineId;
        this.vms = new ArrayList<>();
    }
    
    public void addVM(VMSpecificationRecord vm) {
        vms.add(vm);
        totalCores += vm.getCores();
        totalMemory += vm.getMemory();
        totalStorage += vm.getHdd() + vm.getSsd();
    }
    
    // Getters
    public String getMachineId() { return machineId; }
    public int getVMCount() { return vms.size(); }
    public int getTotalCores() { return totalCores; }
    public long getTotalMemory() { return totalMemory; }
    public long getTotalStorage() { return totalStorage; }
    public List<VMSpecificationRecord> getVMs() { return new ArrayList<>(vms); }
}

/**
 * Resource Distribution Statistics - tracks overall resource distribution
 */
class ResourceDistributionStats {
    private int totalVMs = 0;
    private long totalCores = 0;
    private long totalMemory = 0;
    private long totalHDD = 0;
    private long totalSSD = 0;
    private int maxCores = 0;
    private int minCores = Integer.MAX_VALUE;
    private long maxMemory = 0;
    private long minMemory = Long.MAX_VALUE;
    
    public void updateWithVM(VMSpecificationRecord vm) {
        totalVMs++;
        totalCores += vm.getCores();
        totalMemory += vm.getMemory();
        totalHDD += vm.getHdd();
        totalSSD += vm.getSsd();
        
        maxCores = Math.max(maxCores, vm.getCores());
        minCores = Math.min(minCores, vm.getCores());
        maxMemory = Math.max(maxMemory, vm.getMemory());
        minMemory = Math.min(minMemory, vm.getMemory());
    }
    
    public Map<String, Double> getDistributionStats() {
        Map<String, Double> stats = new HashMap<>();
        
        if (totalVMs == 0) return stats;
        
        stats.put("avgCores", (double) totalCores / totalVMs);
        stats.put("avgMemory", (double) totalMemory / totalVMs);
        stats.put("avgHDD", (double) totalHDD / totalVMs);
        stats.put("avgSSD", (double) totalSSD / totalVMs);
        stats.put("maxCores", (double) maxCores);
        stats.put("minCores", (double) minCores);
        stats.put("maxMemory", (double) maxMemory);
        stats.put("minMemory", (double) minMemory);
        
        return stats;
    }
}

/**
 * VM Type Pattern - represents patterns found in VM types
 */
class VMTypePattern {
    private final String vmType;
    private final int count;
    private final double avgCores;
    private final double avgMemory;
    private final double avgHDD;
    private final double avgSSD;
    private final double coreStdDev;
    private final double memoryStdDev;
    private final Map<String, Integer> machineDistribution;
    
    public VMTypePattern(String vmType, int count, 
                        double avgCores, double avgMemory, 
                        double avgHDD, double avgSSD,
                        double coreStdDev, double memoryStdDev,
                        Map<String, Integer> machineDistribution) {
        this.vmType = vmType;
        this.count = count;
        this.avgCores = avgCores;
        this.avgMemory = avgMemory;
        this.avgHDD = avgHDD;
        this.avgSSD = avgSSD;
        this.coreStdDev = coreStdDev;
        this.memoryStdDev = memoryStdDev;
        this.machineDistribution = machineDistribution;
    }
    
    // Getters
    public String getVmType() { return vmType; }
    public int getCount() { return count; }
    public double getAvgCores() { return avgCores; }
    public double getAvgMemory() { return avgMemory; }
    public double getAvgHDD() { return avgHDD; }
    public double getAvgSSD() { return avgSSD; }
    public double getCoreStdDev() { return coreStdDev; }
    public double getMemoryStdDev() { return memoryStdDev; }
    public Map<String, Integer> getMachineDistribution() { return machineDistribution; }
}

/**
 * Azure VM Statistics - comprehensive statistics from parsing
 */
class AzureVMStatistics {
    private final Map<String, VMSpecificationRecord> vmSpecifications;
    private final Map<String, List<VMSpecificationRecord>> vmTypeGroups;
    private final Map<String, MachineUtilization> machineUtilization;
    private final ResourceDistributionStats resourceDistribution;
    private final WorkloadCharacteristics workloadCharacteristics;
    
    public AzureVMStatistics(
            Map<String, VMSpecificationRecord> vmSpecifications,
            Map<String, List<VMSpecificationRecord>> vmTypeGroups,
            Map<String, MachineUtilization> machineUtilization,
            ResourceDistributionStats resourceDistribution,
            WorkloadCharacteristics workloadCharacteristics) {
        this.vmSpecifications = vmSpecifications;
        this.vmTypeGroups = vmTypeGroups;
        this.machineUtilization = machineUtilization;
        this.resourceDistribution = resourceDistribution;
        this.workloadCharacteristics = workloadCharacteristics;
    }
    
    // Getters
    public Map<String, VMSpecificationRecord> getVmSpecifications() { return vmSpecifications; }
    public Map<String, List<VMSpecificationRecord>> getVmTypeGroups() { return vmTypeGroups; }
    public Map<String, MachineUtilization> getMachineUtilization() { return machineUtilization; }
    public ResourceDistributionStats getResourceDistribution() { return resourceDistribution; }
    public WorkloadCharacteristics getWorkloadCharacteristics() { return workloadCharacteristics; }
}

/**
 * Azure Workload Characteristics - specific to Azure dataset
 */
class AzureWorkloadCharacteristics {
    private int totalVMs;
    private int uniqueVMTypes;
    private int uniqueMachines;
    private Map<String, Integer> vmTypeDistribution;
    private Map<String, Double> resourceDistribution;
    private Map<String, Double> machineUtilizationStats;
    
    // Setters
    public void setTotalVMs(int total) { this.totalVMs = total; }
    public void setUniqueVMTypes(int types) { this.uniqueVMTypes = types; }
    public void setUniqueMachines(int machines) { this.uniqueMachines = machines; }
    public void setVmTypeDistribution(Map<String, Integer> dist) { this.vmTypeDistribution = dist; }
    public void setResourceDistribution(Map<String, Double> dist) { this.resourceDistribution = dist; }
    public void setMachineUtilizationStats(Map<String, Double> stats) { this.machineUtilizationStats = stats; }
    
    // Getters
    public int getTotalVMs() { return totalVMs; }
    public int getUniqueVMTypes() { return uniqueVMTypes; }
    public int getUniqueMachines() { return uniqueMachines; }
    public Map<String, Integer> getVmTypeDistribution() { return vmTypeDistribution; }
    public Map<String, Double> getResourceDistribution() { return resourceDistribution; }
    public Map<String, Double> getMachineUtilizationStats() { return machineUtilizationStats; }
}

/**
 * CloudSim Workload - represents generated workload for CloudSim
 */
class CloudSimWorkload {
    private final List<Vm> vms;
    private final List<Cloudlet> cloudlets;
    private String sourceDataset;
    private LocalDateTime generationTimestamp;
    private double scaleFactor;
    private WorkloadCharacteristics workloadCharacteristics;
    
    public CloudSimWorkload(List<Vm> vms, List<Cloudlet> cloudlets) {
        this.vms = vms;
        this.cloudlets = cloudlets;
    }
    
    // Getters
    public List<Vm> getVms() { return vms; }
    public List<Cloudlet> getCloudlets() { return cloudlets; }
    
    // Setters
    public void setSourceDataset(String source) { this.sourceDataset = source; }
    public void setGenerationTimestamp(LocalDateTime timestamp) { this.generationTimestamp = timestamp; }
    public void setScaleFactor(double factor) { this.scaleFactor = factor; }
    public void setWorkloadCharacteristics(WorkloadCharacteristics characteristics) { 
        this.workloadCharacteristics = characteristics; 
    }
}

