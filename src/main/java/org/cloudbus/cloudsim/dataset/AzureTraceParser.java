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
        this.workloadCharacteristics = new WorkloadCharacteristics();
        
        LoggingManager.logInfo(LOG_PREFIX + " Azure VM specification parser initialized");
    }
    
    /**
     * Parse Azure VM specification trace data
     * 
     * @param traceFilePath Path to Azure VM specification file
     * @return Map of VM ID to parsed VM specifications
     * @throws ExperimentException If trace parsing fails
     */
    public Map<String, VMSpecificationRecord> parseVMSpecificationTrace(String traceFilePath) {
        LoggingManager.logInfo(LOG_PREFIX + " Starting Azure VM specification parsing: " + traceFilePath);
        
        long recordCount = 0;
        long skippedRecords = 0;
        
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(traceFilePath));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            
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
                    vmSpecifications.put(vmSpec.id(), vmSpec);
                    
                    // Group by VM type for analysis
                    vmTypeGroups.computeIfAbsent(vmSpec.vmTypeId(), k -> new ArrayList<>())
                               .add(vmSpec);
                    
                    // Track machine utilization
                    updateMachineUtilization(vmSpec);
                    
                    // Update statistical tracking
                    if (enableStatisticalAnalysis) {
                        updateResourceDistribution(vmSpec);
                    }
                    
                    if (recordCount % 1000 == 0) {
                        LoggingManager.logInfo(LOG_PREFIX + " Processed " + recordCount + " VM specifications");
                    }
                    
                } catch (Exception e) {
                    LoggingManager.logError(LOG_PREFIX + " Error parsing record " + recordCount, e);
                    skippedRecords++;
                }
            }
            
        } catch (IOException e) {
            throw new ExperimentException("Failed to parse Azure VM specification trace: " + traceFilePath, e);
        }
        
        LoggingManager.logInfo(LOG_PREFIX + " Completed parsing. Total records: " + recordCount + 
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
        LoggingManager.logInfo(LOG_PREFIX + " Generating CloudSim workload from Azure VM specifications");
        
        List<Vm> vms = new ArrayList<>();
        List<Cloudlet> cloudlets = new ArrayList<>();
        
        int vmId = 0;
        int cloudletId = 0;
        
        try {
            for (VMSpecificationRecord vmSpec : vmSpecifications.values()) {
                // Create CloudSim VM from specification
                Vm vm = createCloudSimVM(vmId++, vmSpec, broker, scaleFactor);
                vms.add(vm);
                
                // Generate cloudlets based on VM capacity and type
                List<Cloudlet> vmCloudlets = generateCloudletsForVM(
                    cloudletId, vm, vmSpec, scaleFactor);
                cloudlets.addAll(vmCloudlets);
                cloudletId += vmCloudlets.size();
                
                if (vmId % 100 == 0) {
                    LoggingManager.logDebug(LOG_PREFIX + " Created " + vmId + " VMs");
                }
            }
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to generate CloudSim workload from Azure specifications", e);
        }
        
        CloudSimWorkload workload = new CloudSimWorkload(vms, cloudlets);
        workload.setSourceDataset("Azure VM Specifications");
        workload.setGenerationTimestamp(LocalDateTime.now());
        workload.setScaleFactor(scaleFactor);
        workload.setWorkloadCharacteristics(this.workloadCharacteristics);
        
        LoggingManager.logInfo(LOG_PREFIX + " Generated CloudSim workload: " + 
                             vms.size() + " VMs, " + cloudlets.size() + " Cloudlets");
        
        return workload;
    }
    
    /**
     * Create CloudSim VM from Azure specification
     */
    private Vm createCloudSimVM(int vmId, VMSpecificationRecord spec, 
                               DatacenterBroker broker, double scaleFactor) {
        // Calculate MIPS based on cores (assume 2.5 GHz per core)
        long mips = (long) (spec.cores() * 2500 * scaleFactor);
        
        // Memory is already in MB
        long ram = (long) (spec.memory() * scaleFactor);
        
        // Storage in MB (convert from GB)
        long storage = (long) ((spec.hdd() + spec.ssd()) * 1024 * scaleFactor);
        
        // Bandwidth based on NIC count (assume 1 Gbps per NIC)
        long bw = (long) (spec.nic() * 1000 * scaleFactor); // Mbps
        
        return new VmSimple(vmId, mips, spec.cores())
            .setRam(ram)
            .setSize(storage)
            .setBw(bw)
            .setCloudletScheduler(new CloudletSchedulerTimeShared())
            .setBroker(broker);
    }
    
    /**
     * Generate cloudlets for a VM based on its specifications
     */
    private List<Cloudlet> generateCloudletsForVM(int startCloudletId, Vm vm, 
                                                 VMSpecificationRecord spec, 
                                                 double scaleFactor) {
        List<Cloudlet> cloudlets = new ArrayList<>();
        
        // Determine workload intensity based on VM type
        WorkloadProfile profile = determineWorkloadProfile(spec);
        
        // Generate cloudlets based on profile
        int numCloudlets = profile.getCloudletCount();
        
        for (int i = 0; i < numCloudlets; i++) {
            long length = generateCloudletLength(profile, spec, scaleFactor);
            
            // Create utilization models based on workload profile
            UtilizationModel cpuModel = createCpuUtilizationModel(profile);
            UtilizationModel ramModel = createRamUtilizationModel(profile);
            UtilizationModel bwModel = createBwUtilizationModel(profile);
            
            Cloudlet cloudlet = new CloudletSimple(startCloudletId + i, length, spec.cores())
                .setFileSize(profile.getFileSize())
                .setOutputSize(profile.getOutputSize())
                .setUtilizationModelCpu(cpuModel)
                .setUtilizationModelRam(ramModel)
                .setUtilizationModelBw(bwModel)
                .setVm(vm);
            
            cloudlets.add(cloudlet);
        }
        
        return cloudlets;
    }
    
    /**
     * Determine workload profile based on VM specifications
     */
    private WorkloadProfile determineWorkloadProfile(VMSpecificationRecord spec) {
        // Categorize VMs based on their specifications
        if (spec.cores() >= 16 && spec.memory() >= 64000) {
            // High-performance computing profile
            return new WorkloadProfile("HPC", 20, 0.85, 0.75, 0.60, 10000, 5000);
        } else if (spec.cores() >= 8 && spec.memory() >= 32000) {
            // Database/analytics profile
            return new WorkloadProfile("Database", 15, 0.70, 0.85, 0.50, 5000, 10000);
        } else if (spec.cores() >= 4 && spec.memory() >= 16000) {
            // Web application profile
            return new WorkloadProfile("WebApp", 30, 0.60, 0.55, 0.70, 1000, 2000);
        } else {
            // General purpose profile
            return new WorkloadProfile("General", 10, 0.50, 0.50, 0.40, 500, 500);
        }
    }
    
    /**
     * Generate cloudlet length based on profile and VM specs
     */
    private long generateCloudletLength(WorkloadProfile profile, 
                                      VMSpecificationRecord spec, 
                                      double scaleFactor) {
        // Base length calculation
        long baseLength = (long) (profile.getBaseLength() * spec.cores() * 1000);
        
        // Add random variation (±20%)
        double variation = 0.8 + Math.random() * 0.4;
        
        return (long) (baseLength * variation * scaleFactor);
    }
    
    /**
     * Create CPU utilization model based on workload profile
     */
    private UtilizationModel createCpuUtilizationModel(WorkloadProfile profile) {
        if (generateSyntheticUsagePatterns) {
            // Create dynamic utilization with profile characteristics
            return new UtilizationModelDynamic(profile.getAvgCpuUtilization())
                .setMaxResourceUtilization(profile.getAvgCpuUtilization() * 1.2);
        } else {
            return new UtilizationModelFull();
        }
    }
    
    /**
     * Create RAM utilization model based on workload profile
     */
    private UtilizationModel createRamUtilizationModel(WorkloadProfile profile) {
        if (generateSyntheticUsagePatterns) {
            return new UtilizationModelDynamic(profile.getAvgRamUtilization())
                .setMaxResourceUtilization(profile.getAvgRamUtilization() * 1.1);
        } else {
            return new UtilizationModelFull();
        }
    }
    
    /**
     * Create bandwidth utilization model based on workload profile
     */
    private UtilizationModel createBwUtilizationModel(WorkloadProfile profile) {
        if (generateSyntheticUsagePatterns) {
            // Network usage is typically more variable
            return new UtilizationModelStochastic();
        } else {
            return new UtilizationModelDynamic(profile.getAvgBwUtilization());
        }
    }
    
    /**
     * Update machine utilization tracking
     */
    private void updateMachineUtilization(VMSpecificationRecord vmSpec) {
        machineUtilizationMap.computeIfAbsent(vmSpec.machineId(), 
            k -> new MachineUtilization(vmSpec.machineId()))
            .addVM(vmSpec);
    }
    
    /**
     * Update resource distribution statistics
     */
    private void updateResourceDistribution(VMSpecificationRecord vmSpec) {
        resourceDistribution.updateWithVM(vmSpec);
    }
    
    /**
     * Perform post-processing statistical analysis
     */
    private void performPostProcessingAnalysis() {
        LoggingManager.logInfo(LOG_PREFIX + " Performing post-processing analysis");
        
        // Update workload characteristics
        workloadCharacteristics.setTotalVMs(vmSpecifications.size());
        workloadCharacteristics.setUniqueVMTypes(vmTypeGroups.size());
        workloadCharacteristics.setUniqueMachines(machineUtilizationMap.size());
        
        // Calculate VM type distribution
        Map<String, Integer> vmTypeDistribution = new HashMap<>();
        for (Map.Entry<String, List<VMSpecificationRecord>> entry : vmTypeGroups.entrySet()) {
            vmTypeDistribution.put(entry.getKey(), entry.getValue().size());
        }
        workloadCharacteristics.setVmTypeDistribution(vmTypeDistribution);
        
        // Calculate resource statistics
        workloadCharacteristics.setResourceDistribution(
            resourceDistribution.getDistributionStats()
        );
        
        // Calculate machine utilization statistics
        Map<String, Double> machineStats = calculateMachineUtilizationStats();
        workloadCharacteristics.setMachineUtilizationStats(machineStats);
        
        LoggingManager.logInfo(LOG_PREFIX + " Analysis complete. " +
            "VM Types: " + vmTypeGroups.size() + 
            ", Machines: " + machineUtilizationMap.size());
    }
    
    /**
     * Calculate machine utilization statistics
     */
    private Map<String, Double> calculateMachineUtilizationStats() {
        Map<String, Double> stats = new HashMap<>();
        
        double totalVMsPerMachine = machineUtilizationMap.values().stream()
            .mapToInt(m -> m.getVMCount())
            .average()
            .orElse(0.0);
        
        double avgCoresPerMachine = machineUtilizationMap.values().stream()
            .mapToDouble(m -> m.getTotalCores())
            .average()
            .orElse(0.0);
        
        double avgMemoryPerMachine = machineUtilizationMap.values().stream()
            .mapToDouble(m -> m.getTotalMemory())
            .average()
            .orElse(0.0);
        
        stats.put("avgVMsPerMachine", totalVMsPerMachine);
        stats.put("avgCoresPerMachine", avgCoresPerMachine);
        stats.put("avgMemoryPerMachine", avgMemoryPerMachine);
        
        return stats;
    }
    
    /**
     * Extract VM type patterns for analysis
     * 
     * @return Map of VM type to characteristic patterns
     */
    public Map<String, VMTypePattern> extractVMTypePatterns() {
        LoggingManager.logInfo(LOG_PREFIX + " Extracting VM type patterns");
        
        Map<String, VMTypePattern> patterns = new HashMap<>();
        
        for (Map.Entry<String, List<VMSpecificationRecord>> entry : vmTypeGroups.entrySet()) {
            String vmType = entry.getKey();
            List<VMSpecificationRecord> vms = entry.getValue();
            
            VMTypePattern pattern = analyzeVMTypePattern(vmType, vms);
            patterns.put(vmType, pattern);
        }
        
        LoggingManager.logInfo(LOG_PREFIX + " Extracted patterns for " + 
                             patterns.size() + " VM types");
        
        return patterns;
    }
    
    /**
     * Analyze pattern for a specific VM type
     */
    private VMTypePattern analyzeVMTypePattern(String vmType, 
                                             List<VMSpecificationRecord> vms) {
        // Calculate average resources for this VM type
        double avgCores = vms.stream().mapToInt(VMSpecificationRecord::cores).average().orElse(0);
        double avgMemory = vms.stream().mapToLong(VMSpecificationRecord::memory).average().orElse(0);
        double avgHDD = vms.stream().mapToLong(VMSpecificationRecord::hdd).average().orElse(0);
        double avgSSD = vms.stream().mapToLong(VMSpecificationRecord::ssd).average().orElse(0);
        
        // Calculate resource variations
        double coreStdDev = calculateStdDev(vms.stream().mapToInt(VMSpecificationRecord::cores)
                                              .mapToDouble(i -> i).toArray());
        double memoryStdDev = calculateStdDev(vms.stream().mapToLong(VMSpecificationRecord::memory)
                                                .mapToDouble(l -> l).toArray());
        
        // Machine distribution
        Map<String, Integer> machineDistribution = vms.stream()
            .collect(Collectors.groupingBy(VMSpecificationRecord::machineId, 
                                         Collectors.collectingAndThen(Collectors.counting(), 
                                                                    Long::intValue)));
        
        return new VMTypePattern(vmType, vms.size(), avgCores, avgMemory, avgHDD, avgSSD,
                               coreStdDev, memoryStdDev, machineDistribution);
    }
    
    /**
     * Calculate standard deviation
     */
    private double calculateStdDev(double[] values) {
        if (values.length == 0) return 0.0;
        
        double mean = Arrays.stream(values).average().orElse(0.0);
        double variance = Arrays.stream(values)
            .map(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0.0);
        
        return Math.sqrt(variance);
    }
    
    /**
     * Configuration methods for research flexibility
     */
    public void setSamplingRatio(double ratio) {
        this.samplingRatio = Math.max(0.0, Math.min(1.0, ratio));
        LoggingManager.logInfo(LOG_PREFIX + " Set sampling ratio to: " + this.samplingRatio);
    }
    
    public void setStatisticalAnalysisEnabled(boolean enabled) {
        this.enableStatisticalAnalysis = enabled;
        LoggingManager.logInfo(LOG_PREFIX + " Statistical analysis enabled: " + enabled);
    }
    
    public void setSyntheticUsagePatterns(boolean enabled) {
        this.generateSyntheticUsagePatterns = enabled;
        LoggingManager.logInfo(LOG_PREFIX + " Synthetic usage patterns enabled: " + enabled);
    }
    
    public void setBaseUtilizationLevel(double level) {
        this.baseUtilizationLevel = Math.max(0.0, Math.min(1.0, level));
        LoggingManager.logInfo(LOG_PREFIX + " Set base utilization level to: " + this.baseUtilizationLevel);
    }
    
    /**
     * Get comprehensive parsing statistics for research reporting
     */
    public AzureVMStatistics getParsingStatistics() {
        return new AzureVMStatistics(
            vmSpecifications,
            vmTypeGroups,
            machineUtilizationMap,
            resourceDistribution,
            workloadCharacteristics
        );
    }
}

/**
 * Record class for VM Specification data
 */
record VMSpecificationRecord(
    String id, 
    String vmTypeId, 
    String machineId, 
    int cores, 
    long memory,  // MB
    long hdd,     // GB
    long ssd,     // GB
    int nic
) {}

/**
 * Class representing workload profile characteristics
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
 * Machine utilization tracking
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
        totalCores += vm.cores();
        totalMemory += vm.memory();
        totalStorage += (vm.hdd() + vm.ssd());
    }
    
    public String getMachineId() { return machineId; }
    public int getVMCount() { return vms.size(); }
    public int getTotalCores() { return totalCores; }
    public long getTotalMemory() { return totalMemory; }
    public long getTotalStorage() { return totalStorage; }
    public List<VMSpecificationRecord> getVMs() { return new ArrayList<>(vms); }
}

/**
 * Resource distribution statistics
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
        totalCores += vm.cores();
        totalMemory += vm.memory();
        totalHDD += vm.hdd();
        totalSSD += vm.ssd();
        
        maxCores = Math.max(maxCores, vm.cores());
        minCores = Math.min(minCores, vm.cores());
        maxMemory = Math.max(maxMemory, vm.memory());
        minMemory = Math.min(minMemory, vm.memory());
    }
    
    public Map<String, Double> getDistributionStats() {
        Map<String, Double> stats = new HashMap<>();
        
        if (totalVMs > 0) {
            stats.put("avgCores", (double) totalCores / totalVMs);
            stats.put("avgMemory", (double) totalMemory / totalVMs);
            stats.put("avgHDD", (double) totalHDD / totalVMs);
            stats.put("avgSSD", (double) totalSSD / totalVMs);
            stats.put("maxCores", (double) maxCores);
            stats.put("minCores", (double) minCores);
            stats.put("maxMemory", (double) maxMemory);
            stats.put("minMemory", (double) minMemory);
            stats.put("totalVMs", (double) totalVMs);
        }
        
        return stats;
    }
}

/**
 * VM Type pattern analysis
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
 * Comprehensive Azure VM statistics
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
    
    // Getters for research analysis
    public Map<String, VMSpecificationRecord> getVmSpecifications() { return vmSpecifications; }
    public Map<String, List<VMSpecificationRecord>> getVmTypeGroups() { return vmTypeGroups; }
    public Map<String, MachineUtilization> getMachineUtilization() { return machineUtilization; }
    public ResourceDistributionStats getResourceDistribution() { return resourceDistribution; }
    public WorkloadCharacteristics getWorkloadCharacteristics() { return workloadCharacteristics; }
}

// Additional classes referenced but abbreviated for space
class WorkloadCharacteristics {
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
    
    public List<Vm> getVms() { return vms; }
    public List<Cloudlet> getCloudlets() { return cloudlets; }
    public void setSourceDataset(String source) { this.sourceDataset = source; }
    public void setGenerationTimestamp(LocalDateTime timestamp) { this.generationTimestamp = timestamp; }
    public void setScaleFactor(double factor) { this.scaleFactor = factor; }
    public void setWorkloadCharacteristics(WorkloadCharacteristics characteristics) { 
        this.workloadCharacteristics = characteristics; 
    }
}

