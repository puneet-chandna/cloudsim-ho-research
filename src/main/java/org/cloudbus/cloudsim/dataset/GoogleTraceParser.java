package org.cloudbus.cloudsim.dataset;

import org.cloudbus.cloudsim.util.ExperimentException;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.simulation.ExperimentalScenario;
import org.apache.commons.csv.CSVRecord;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Parser for Google cluster trace data.
 * Extracts workload patterns and generates CloudSim scenarios.
 * Handles task usage and task events traces.
 */
public class GoogleTraceParser {
    // Google trace column indices (based on trace format)
    private static final int TIMESTAMP_COL = 0;
    private static final int JOB_ID_COL = 1;
    private static final int TASK_INDEX_COL = 2;
    private static final int CPU_USAGE_COL = 3;
    private static final int MEMORY_USAGE_COL = 4;
    private static final int DISK_USAGE_COL = 5;
    
    // Task events columns
    private static final int EVENT_TYPE_COL = 3;
    private static final int CPU_REQUEST_COL = 4;
    private static final int MEMORY_REQUEST_COL = 5;
    
    // Scaling factors for CloudSim
    private static final double CPU_SCALING_FACTOR = 1000.0; // MIPS
    private static final double MEMORY_SCALING_FACTOR = 1024.0; // MB
    private static final double DISK_SCALING_FACTOR = 1024.0; // MB
    
    /**
     * Parse Google task usage trace data.
     * Extracts resource usage patterns for CloudSim simulation.
     * 
     * @param traceRecords Raw trace records from Google dataset
     * @return Parsed task usage data with resource utilization patterns
     * @throws ExperimentException if parsing fails
     */
    public static List<TaskUsageData> parseTaskUsageTrace(List<CSVRecord> traceRecords) {
        try {
            LoggingManager.logInfo("Parsing Google task usage trace (" + traceRecords.size() + " records)");
            
            List<TaskUsageData> taskUsageList = new ArrayList<>();
            Map<String, List<CSVRecord>> taskGroups = new HashMap<>();
            
            // Group records by task (job_id + task_index)
            for (CSVRecord record : traceRecords) {
                if (record.size() < 6) {
                    continue; // Skip malformed records
                }
                
                String taskId = record.get(JOB_ID_COL) + "_" + record.get(TASK_INDEX_COL);
                taskGroups.computeIfAbsent(taskId, k -> new ArrayList<>()).add(record);
            }
            
            // Process each task group
            for (Map.Entry<String, List<CSVRecord>> entry : taskGroups.entrySet()) {
                String taskId = entry.getKey();
                List<CSVRecord> taskRecords = entry.getValue();
                
                TaskUsageData taskData = parseTaskUsageData(taskId, taskRecords);
                if (taskData != null) {
                    taskUsageList.add(taskData);
                }
            }
            
            LoggingManager.logInfo("Parsed " + taskUsageList.size() + " tasks from Google trace");
            return taskUsageList;
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to parse Google task usage trace", e);
        }
    }
    
    /**
     * Parse Google task events trace data.
     * Extracts task lifecycle events and resource requirements.
     * 
     * @param traceRecords Raw trace records from Google task events
     * @return Parsed task events data with lifecycle information
     * @throws ExperimentException if parsing fails
     */
    public static List<TaskEventData> parseTaskEventsTrace(List<CSVRecord> traceRecords) {
        try {
            LoggingManager.logInfo("Parsing Google task events trace (" + traceRecords.size() + " records)");
            
            List<TaskEventData> taskEventsList = new ArrayList<>();
            
            for (CSVRecord record : traceRecords) {
                if (record.size() < 6) {
                    continue; // Skip malformed records
                }
                
                TaskEventData eventData = parseTaskEventData(record);
                if (eventData != null) {
                    taskEventsList.add(eventData);
                }
            }
            
            LoggingManager.logInfo("Parsed " + taskEventsList.size() + " task events from Google trace");
            return taskEventsList;
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to parse Google task events trace", e);
        }
    }
    
    /**
     * Extract workload patterns from Google trace data.
     * Analyzes temporal patterns, resource usage patterns, and task characteristics.
     * 
     * @param taskUsageData Parsed task usage data
     * @return Extracted workload patterns for analysis
     */
    public static WorkloadPatterns extractWorkloadPatterns(List<TaskUsageData> taskUsageData) {
        try {
            LoggingManager.logInfo("Extracting workload patterns from " + taskUsageData.size() + " tasks");
            
            WorkloadPatterns patterns = new WorkloadPatterns();
            
            // Calculate resource utilization statistics
            calculateResourceUtilizationPatterns(taskUsageData, patterns);
            
            // Calculate temporal patterns
            calculateTemporalPatterns(taskUsageData, patterns);
            
            // Calculate task duration patterns
            calculateTaskDurationPatterns(taskUsageData, patterns);
            
            // Calculate resource request patterns
            calculateResourceRequestPatterns(taskUsageData, patterns);
            
            LoggingManager.logInfo("Extracted workload patterns: " + patterns.getSummary());
            return patterns;
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to extract workload patterns", e);
        }
    }
    
    /**
     * Generate CloudSim scenarios from Google trace data.
     * Creates VMs and Cloudlets based on trace patterns.
     * 
     * @param taskUsageData Parsed task usage data
     * @param taskEventsData Parsed task events data
     * @param maxVms Maximum number of VMs to create
     * @return Generated CloudSim scenarios
     * @throws ExperimentException if scenario generation fails
     */
    public static List<ExperimentalScenario> generateSimulationScenarios(
            List<TaskUsageData> taskUsageData, 
            List<TaskEventData> taskEventsData,
            int maxVms) {
        
        try {
            LoggingManager.logInfo("Generating CloudSim scenarios from Google trace data");
            
            List<ExperimentalScenario> scenarios = new ArrayList<>();
            
            // Create scenarios with different scales
            int[] vmCounts = {50, 100, 200, Math.min(maxVms, 500)};
            
            for (int vmCount : vmCounts) {
                ExperimentalScenario scenario = createScenarioFromTrace(
                    taskUsageData, taskEventsData, vmCount
                );
                scenarios.add(scenario);
            }
            
            LoggingManager.logInfo("Generated " + scenarios.size() + " scenarios from Google trace");
            return scenarios;
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to generate CloudSim scenarios", e);
        }
    }
    
    // Private helper methods
    
    private static TaskUsageData parseTaskUsageData(String taskId, List<CSVRecord> taskRecords) {
        try {
            TaskUsageData taskData = new TaskUsageData();
            taskData.setTaskId(taskId);
            
            List<ResourceUsagePoint> usagePoints = new ArrayList<>();
            
            for (CSVRecord record : taskRecords) {
                ResourceUsagePoint point = new ResourceUsagePoint();
                
                // Parse timestamp
                point.setTimestamp(parseDoubleValue(record.get(TIMESTAMP_COL)));
                
                // Parse resource usage (normalized values 0-1)
                point.setCpuUsage(parseDoubleValue(record.get(CPU_USAGE_COL)));
                point.setMemoryUsage(parseDoubleValue(record.get(MEMORY_USAGE_COL)));
                point.setDiskUsage(parseDoubleValue(record.get(DISK_USAGE_COL)));
                
                // Validate usage values
                if (isValidUsagePoint(point)) {
                    usagePoints.add(point);
                }
            }
            
            if (usagePoints.isEmpty()) {
                return null;
            }
            
            taskData.setUsagePoints(usagePoints);
            calculateTaskStatistics(taskData);
            
            return taskData;
            
        } catch (Exception e) {
            LoggingManager.logWarning("Failed to parse task usage data for task: " + taskId, e);
            return null;
        }
    }
    
    private static TaskEventData parseTaskEventData(CSVRecord record) {
        try {
            TaskEventData eventData = new TaskEventData();
            
            // Parse basic task information
            eventData.setTimestamp(parseDoubleValue(record.get(TIMESTAMP_COL)));
            eventData.setJobId(record.get(JOB_ID_COL));
            eventData.setTaskIndex(parseIntValue(record.get(TASK_INDEX_COL)));
            eventData.setEventType(parseIntValue(record.get(EVENT_TYPE_COL)));
            
            // Parse resource requirements
            eventData.setCpuRequest(parseDoubleValue(record.get(CPU_REQUEST_COL)));
            eventData.setMemoryRequest(parseDoubleValue(record.get(MEMORY_REQUEST_COL)));
            
            return eventData;
            
        } catch (Exception e) {
            LoggingManager.logWarning("Failed to parse task event data", e);
            return null;
        }
    }
    
    private static void calculateResourceUtilizationPatterns(List<TaskUsageData> taskData, WorkloadPatterns patterns) {
        List<Double> cpuUsages = new ArrayList<>();
        List<Double> memoryUsages = new ArrayList<>();
        List<Double> diskUsages = new ArrayList<>();
        
        for (TaskUsageData task : taskData) {
            for (ResourceUsagePoint point : task.getUsagePoints()) {
                cpuUsages.add(point.getCpuUsage());
                memoryUsages.add(point.getMemoryUsage());
                diskUsages.add(point.getDiskUsage());
            }
        }
        
        // Calculate statistics
        patterns.setAvgCpuUtilization(calculateMean(cpuUsages));
        patterns.setAvgMemoryUtilization(calculateMean(memoryUsages));
        patterns.setAvgDiskUtilization(calculateMean(diskUsages));
        
        patterns.setCpuUtilizationStdDev(calculateStdDev(cpuUsages));
        patterns.setMemoryUtilizationStdDev(calculateStdDev(memoryUsages));
        patterns.setDiskUtilizationStdDev(calculateStdDev(diskUsages));
    }
    
    private static void calculateTemporalPatterns(List<TaskUsageData> taskData, WorkloadPatterns patterns) {
        List<Double> taskDurations = new ArrayList<>();
        
        for (TaskUsageData task : taskData) {
            if (!task.getUsagePoints().isEmpty()) {
                double duration = calculateTaskDuration(task);
                taskDurations.add(duration);
            }
        }
        
        patterns.setAvgTaskDuration(calculateMean(taskDurations));
        patterns.setTaskDurationStdDev(calculateStdDev(taskDurations));
    }
    
    private static void calculateTaskDurationPatterns(List<TaskUsageData> taskData, WorkloadPatterns patterns) {
        Map<String, Integer> durationBuckets = new HashMap<>();
        
        for (TaskUsageData task : taskData) {
            double duration = calculateTaskDuration(task);
            String bucket = categorizeDuration(duration);
            durationBuckets.merge(bucket, 1, Integer::sum);
        }
        
        patterns.setDurationDistribution(durationBuckets);
    }
    
    private static void calculateResourceRequestPatterns(List<TaskUsageData> taskData, WorkloadPatterns patterns) {
        // Calculate resource request patterns from usage data
        List<Double> peakCpuUsages = new ArrayList<>();
        List<Double> peakMemoryUsages = new ArrayList<>();
        
        for (TaskUsageData task : taskData) {
            double peakCpu = task.getUsagePoints().stream()
                .mapToDouble(ResourceUsagePoint::getCpuUsage)
                .max().orElse(0.0);
            double peakMemory = task.getUsagePoints().stream()
                .mapToDouble(ResourceUsagePoint::getMemoryUsage)
                .max().orElse(0.0);
            
            peakCpuUsages.add(peakCpu);
            peakMemoryUsages.add(peakMemory);
        }
        
        patterns.setAvgPeakCpuUsage(calculateMean(peakCpuUsages));
        patterns.setAvgPeakMemoryUsage(calculateMean(peakMemoryUsages));
    }
    
    private static ExperimentalScenario createScenarioFromTrace(
            List<TaskUsageData> taskUsageData,
            List<TaskEventData> taskEventsData,
            int vmCount) {
        
        ExperimentalScenario scenario = new ExperimentalScenario();
        scenario.setScenarioName("Google_Trace_" + vmCount + "_VMs");
        scenario.setDescription("Scenario generated from Google cluster trace with " + vmCount + " VMs");
        
        // Set workload characteristics
        WorkloadCharacteristics characteristics = deriveWorkloadCharacteristics(taskUsageData);
        scenario.setWorkloadCharacteristics(characteristics);
        
        // Set scenario properties based on trace data
        scenario.setNumberOfVms(vmCount);
        scenario.setNumberOfHosts(Math.max(1, vmCount / 10)); // 10 VMs per host on average
        scenario.setArrivalRate(10.0); // Default arrival rate
        scenario.setExecutionTime(3600.0); // 1 hour execution time
        scenario.setDatasetSource("Google_Cluster_Trace");
        scenario.setWorkloadType("Google_Cluster_Trace");
        
        return scenario;
    }
    
    private static List<Vm> createVmsFromTrace(List<TaskUsageData> taskUsageData, int vmCount) {
        List<Vm> vms = new ArrayList<>();
        
        // Calculate VM specifications based on trace data
        WorkloadPatterns patterns = extractWorkloadPatterns(taskUsageData);
        
        for (int i = 0; i < vmCount; i++) {
            // Create VM with specifications derived from trace
            long mips = Math.round(patterns.getAvgPeakCpuUsage() * CPU_SCALING_FACTOR);
            long ram = Math.round(patterns.getAvgPeakMemoryUsage() * MEMORY_SCALING_FACTOR);
            long storage = Math.round(patterns.getAvgDiskUtilization() * DISK_SCALING_FACTOR);
            
            // Ensure minimum values
            mips = Math.max(mips, 1000);
            ram = Math.max(ram, 512);
            storage = Math.max(storage, 10000);
            
            Vm vm = new VmSimple(i, mips, 1) // 1 PE per VM
                .setRam(ram)
                .setBw(1000)
                .setSize(storage);
            
            vms.add(vm);
        }
        
        return vms;
    }
    
    private static List<Cloudlet> createCloudletsFromTrace(
            List<TaskUsageData> taskUsageData,
            int vmCount) {
        
        List<Cloudlet> cloudlets = new ArrayList<>();
        
        // Create cloudlets based on task data
        int cloudletId = 0;
        int maxCloudlets = vmCount * 3; // 3 cloudlets per VM on average
        
        for (int i = 0; i < Math.min(taskUsageData.size(), maxCloudlets); i++) {
            TaskUsageData task = taskUsageData.get(i);
            Cloudlet cloudlet = createCloudletFromTask(task, cloudletId++);
            cloudlets.add(cloudlet);
        }
        
        // If we have fewer tasks than needed, create synthetic cloudlets
        while (cloudlets.size() < maxCloudlets && !taskUsageData.isEmpty()) {
            TaskUsageData randomTask = taskUsageData.get(cloudletId % taskUsageData.size());
            Cloudlet cloudlet = createCloudletFromTask(randomTask, cloudletId++);
            cloudlets.add(cloudlet);
        }
        
        return cloudlets;
    }
    
    private static Cloudlet createCloudletFromTask(TaskUsageData task, int cloudletId) {
        // Calculate cloudlet length based on task usage
        double avgCpuUsage = task.getUsagePoints().stream()
            .mapToDouble(ResourceUsagePoint::getCpuUsage)
            .average().orElse(0.5);
        
        long length = Math.round(avgCpuUsage * 10000); // Scale to reasonable MI
        length = Math.max(length, 1000); // Minimum length
        
        // Create utilization models based on trace data
        UtilizationModel cpuModel = createCpuUtilizationModel(task);
        UtilizationModel ramModel = createRamUtilizationModel(task);
        UtilizationModel bwModel = new UtilizationModelFull();
        
        return new CloudletSimple(cloudletId, length, 1)
            .setUtilizationModelCpu(cpuModel)
            .setUtilizationModelRam(ramModel)
            .setUtilizationModelBw(bwModel);
    }
    
    private static UtilizationModel createCpuUtilizationModel(TaskUsageData task) {
        List<Double> cpuUsages = task.getUsagePoints().stream()
            .map(ResourceUsagePoint::getCpuUsage)
            .collect(Collectors.toList());
        
        if (cpuUsages.isEmpty()) {
            return new UtilizationModelFull();
        }
        
        // Create dynamic utilization model based on trace data
        return new UtilizationModelDynamic(0.1) // 10% minimum utilization
            .setMaxResourceUtilization(cpuUsages.stream().mapToDouble(Double::doubleValue).max().orElse(1.0));
    }
    
    private static UtilizationModel createRamUtilizationModel(TaskUsageData task) {
        List<Double> memoryUsages = task.getUsagePoints().stream()
            .map(ResourceUsagePoint::getMemoryUsage)
            .collect(Collectors.toList());
        
        if (memoryUsages.isEmpty()) {
            return new UtilizationModelFull();
        }
        
        double avgMemoryUsage = memoryUsages.stream().mapToDouble(Double::doubleValue).average().orElse(0.5);
        return new UtilizationModelDynamic(avgMemoryUsage * 0.5) // 50% of average as minimum
            .setMaxResourceUtilization(memoryUsages.stream().mapToDouble(Double::doubleValue).max().orElse(1.0));
    }
    
    private static WorkloadCharacteristics deriveWorkloadCharacteristics(List<TaskUsageData> taskUsageData) {
        // Calculate resource intensity
        double avgCpuIntensity = taskUsageData.stream()
            .flatMap(task -> task.getUsagePoints().stream())
            .mapToDouble(ResourceUsagePoint::getCpuUsage)
            .average().orElse(0.5);
        
        double avgMemoryIntensity = taskUsageData.stream()
            .flatMap(task -> task.getUsagePoints().stream())
            .mapToDouble(ResourceUsagePoint::getMemoryUsage)
            .average().orElse(0.5);
        
        // Calculate temporal characteristics
        double avgTaskDuration = taskUsageData.stream()
            .mapToDouble(GoogleTraceParser::calculateTaskDuration)
            .average().orElse(300.0); // 5 minutes default
        
        // Create resource patterns
        WorkloadCharacteristics.ResourceRequirementPatterns resourcePatterns = 
            new WorkloadCharacteristics.ResourceRequirementPatterns(
                avgCpuIntensity * 100.0, // avg CPU requirement
                avgMemoryIntensity * 1024.0, // avg memory requirement
                avgCpuIntensity * 200.0, // max CPU requirement
                avgMemoryIntensity * 2048.0, // max memory requirement
                avgCpuIntensity * 10.0, // min CPU requirement
                avgMemoryIntensity * 512.0, // min memory requirement
                avgCpuIntensity * 20.0, // CPU std dev
                avgMemoryIntensity * 256.0, // memory std dev
                0.4, // CPU variability coefficient
                0.25, // memory variability coefficient
                0.6, // CPU-memory correlation
                "Normal" // resource distribution
            );
        
        // Create temporal characteristics
        Map<String, Double> distributionParams = new HashMap<>();
        distributionParams.put("lambda", 10.0);
        distributionParams.put("shape", 2.0);
        
        WorkloadCharacteristics.TemporalCharacteristics temporalCharacteristics = 
            new WorkloadCharacteristics.TemporalCharacteristics(
                avgTaskDuration * taskUsageData.size(), // total duration
                "Poisson", // arrival pattern
                10.0, // arrival rate
                0.3, // arrival rate variability
                avgTaskDuration, // avg execution time
                0.5, // execution time variability
                0.2, // seasonality index
                Arrays.asList(900.0, 1800.0, 2700.0), // peak periods
                distributionParams // distribution parameters
            );
        
        // Create SLA requirements
        WorkloadCharacteristics.SlaRequirements slaRequirements = 
            new WorkloadCharacteristics.SlaRequirements(
                0.99, // min availability
                100.0, // max response time
                1000.0, // min throughput
                0.01, // max error rate
                new HashMap<>(), // custom SLA metrics
                "Gold", // SLA class
                100.0 // penalty cost
            );
        
        // Create performance targets
        WorkloadCharacteristics.PerformanceTargets performanceTargets = 
            new WorkloadCharacteristics.PerformanceTargets(
                0.8, // target resource utilization
                0.9, // target power efficiency
                0.85, // target cost efficiency
                0.9, // target load balancing
                new HashMap<>(), // custom performance targets
                Arrays.asList("utilization", "power", "sla") // optimization objectives
            );
        
        // Create metadata
        WorkloadCharacteristics.WorkloadMetadata metadata = 
            new WorkloadCharacteristics.WorkloadMetadata(
                "Google_Cluster_Trace", // workload name
                "Google_Cluster_Trace", // workload type
                "Google_Cluster_Trace", // dataset source
                taskUsageData.size(), // VM count
                taskUsageData.size() * 3, // task count
                "Workload derived from Google cluster trace data", // description
                new HashMap<>() // additional metadata
            );
        
        // Create statistical characteristics
        Map<String, Double> statDistributionParams = new HashMap<>();
        statDistributionParams.put("mean", avgCpuIntensity * 100.0);
        statDistributionParams.put("stddev", avgCpuIntensity * 20.0);
        
        Map<String, Double> momentStats = new HashMap<>();
        momentStats.put("skewness", 0.1);
        momentStats.put("kurtosis", 3.0);
        
        WorkloadCharacteristics.StatisticalCharacteristics statisticalCharacteristics = 
            new WorkloadCharacteristics.StatisticalCharacteristics(
                statDistributionParams, // distribution parameters
                "Normal", // resource distribution type
                "Exponential", // temporal distribution type
                0.3, // autocorrelation coefficient
                Arrays.asList(0.25, 0.5, 0.75, 0.95), // quantiles
                3.5, // entropy
                momentStats // moment statistics
            );
        
        return new WorkloadCharacteristics.Builder()
            .setResourcePatterns(resourcePatterns)
            .setTemporalCharacteristics(temporalCharacteristics)
            .setSlaRequirements(slaRequirements)
            .setPerformanceTargets(performanceTargets)
            .setMetadata(metadata)
            .setStatisticalCharacteristics(statisticalCharacteristics)
            .build();
    }
    
    // Utility methods
    
    private static double parseDoubleValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    
    private static int parseIntValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private static boolean isValidUsagePoint(ResourceUsagePoint point) {
        return point.getCpuUsage() >= 0 && point.getCpuUsage() <= 1 &&
               point.getMemoryUsage() >= 0 && point.getMemoryUsage() <= 1 &&
               point.getDiskUsage() >= 0 && point.getDiskUsage() <= 1;
    }
    
    private static void calculateTaskStatistics(TaskUsageData taskData) {
        List<ResourceUsagePoint> points = taskData.getUsagePoints();
        
        // Calculate average utilization
        double avgCpu = points.stream().mapToDouble(ResourceUsagePoint::getCpuUsage).average().orElse(0.0);
        double avgMemory = points.stream().mapToDouble(ResourceUsagePoint::getMemoryUsage).average().orElse(0.0);
        double avgDisk = points.stream().mapToDouble(ResourceUsagePoint::getDiskUsage).average().orElse(0.0);
        
        taskData.setAvgCpuUsage(avgCpu);
        taskData.setAvgMemoryUsage(avgMemory);
        taskData.setAvgDiskUsage(avgDisk);
        
        // Calculate peak utilization
        double peakCpu = points.stream().mapToDouble(ResourceUsagePoint::getCpuUsage).max().orElse(0.0);
        double peakMemory = points.stream().mapToDouble(ResourceUsagePoint::getMemoryUsage).max().orElse(0.0);
        double peakDisk = points.stream().mapToDouble(ResourceUsagePoint::getDiskUsage).max().orElse(0.0);
        
        taskData.setPeakCpuUsage(peakCpu);
        taskData.setPeakMemoryUsage(peakMemory);
        taskData.setPeakDiskUsage(peakDisk);
    }
    
    private static double calculateTaskDuration(TaskUsageData task) {
        List<ResourceUsagePoint> points = task.getUsagePoints();
        if (points.size() < 2) {
            return 300.0; // Default 5 minutes
        }
        
        double startTime = points.get(0).getTimestamp();
        double endTime = points.get(points.size() - 1).getTimestamp();
        return Math.max(endTime - startTime, 60.0); // Minimum 1 minute
    }
    
    private static String categorizeDuration(double duration) {
        if (duration < 300) return "short"; // < 5 minutes
        else if (duration < 1800) return "medium"; // 5-30 minutes
        else if (duration < 3600) return "long"; // 30-60 minutes
        else return "very_long"; // > 1 hour
    }
    
    private static double calculateMean(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
    
    private static double calculateStdDev(List<Double> values) {
        if (values.size() < 2) return 0.0;
        
        double mean = calculateMean(values);
        double variance = values.stream()
            .mapToDouble(val -> Math.pow(val - mean, 2))
            .average().orElse(0.0);
        
        return Math.sqrt(variance);
    }
    
    // Inner classes for data structures
    
    public static class TaskUsageData {
        private String taskId;
        private List<ResourceUsagePoint> usagePoints;
        private double avgCpuUsage;
        private double avgMemoryUsage;
        private double avgDiskUsage;
        private double peakCpuUsage;
        private double peakMemoryUsage;
        private double peakDiskUsage;
        
        // Getters and setters
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        
        public List<ResourceUsagePoint> getUsagePoints() { return usagePoints; }
        public void setUsagePoints(List<ResourceUsagePoint> usagePoints) { this.usagePoints = usagePoints; }
        
        public double getAvgCpuUsage() { return avgCpuUsage; }
        public void setAvgCpuUsage(double avgCpuUsage) { this.avgCpuUsage = avgCpuUsage; }
        
        public double getAvgMemoryUsage() { return avgMemoryUsage; }
        public void setAvgMemoryUsage(double avgMemoryUsage) { this.avgMemoryUsage = avgMemoryUsage; }
        
        public double getAvgDiskUsage() { return avgDiskUsage; }
        public void setAvgDiskUsage(double avgDiskUsage) { this.avgDiskUsage = avgDiskUsage; }
        
        public double getPeakCpuUsage() { return peakCpuUsage; }
        public void setPeakCpuUsage(double peakCpuUsage) { this.peakCpuUsage = peakCpuUsage; }
        
        public double getPeakMemoryUsage() { return peakMemoryUsage; }
        public void setPeakMemoryUsage(double peakMemoryUsage) { this.peakMemoryUsage = peakMemoryUsage; }
        
        public double getPeakDiskUsage() { return peakDiskUsage; }
        public void setPeakDiskUsage(double peakDiskUsage) { this.peakDiskUsage = peakDiskUsage; }
    }
    
    public static class TaskEventData {
        private double timestamp;
        private String jobId;
        private int taskIndex;
        private int eventType;
        private double cpuRequest;
        private double memoryRequest;
        
        // Getters and setters
        public double getTimestamp() { return timestamp; }
        public void setTimestamp(double timestamp) { this.timestamp = timestamp; }
        
        public String getJobId() { return jobId; }
        public void setJobId(String jobId) { this.jobId = jobId; }
        
        public int getTaskIndex() { return taskIndex; }
        public void setTaskIndex(int taskIndex) { this.taskIndex = taskIndex; }
        
        public int getEventType() { return eventType; }
        public void setEventType(int eventType) { this.eventType = eventType; }
        
        public double getCpuRequest() { return cpuRequest; }
        public void setCpuRequest(double cpuRequest) { this.cpuRequest = cpuRequest; }
        
        public double getMemoryRequest() { return memoryRequest; }
        public void setMemoryRequest(double memoryRequest) { this.memoryRequest = memoryRequest; }
    }
    
    public static class ResourceUsagePoint {
        private double timestamp;
        private double cpuUsage;
        private double memoryUsage;
        private double diskUsage;
        
        // Getters and setters
        public double getTimestamp() { return timestamp; }
        public void setTimestamp(double timestamp) { this.timestamp = timestamp; }
        
        public double getCpuUsage() { return cpuUsage; }
        public void setCpuUsage(double cpuUsage) { this.cpuUsage = cpuUsage; }
        
        public double getMemoryUsage() { return memoryUsage; }
        public void setMemoryUsage(double memoryUsage) { this.memoryUsage = memoryUsage; }
        
        public double getDiskUsage() { return diskUsage; }
        public void setDiskUsage(double diskUsage) { this.diskUsage = diskUsage; }
    }
    
    public static class WorkloadPatterns {
        private double avgCpuUtilization;
        private double avgMemoryUtilization;
        private double avgDiskUtilization;
        private double cpuUtilizationStdDev;
        private double memoryUtilizationStdDev;
        private double diskUtilizationStdDev;
        private double avgTaskDuration;
        private double taskDurationStdDev;
        private double avgPeakCpuUsage;
        private double avgPeakMemoryUsage;
        private Map<String, Integer> durationDistribution;
        
        public WorkloadPatterns() {
            this.durationDistribution = new HashMap<>();
        }
        
        public String getSummary() {
            return String.format("CPU: %.2fÂ±%.2f, Memory: %.2fÂ±%.2f, Duration: %.2fÂ±%.2f", 
                avgCpuUtilization, cpuUtilizationStdDev,
                avgMemoryUtilization, memoryUtilizationStdDev,
                avgTaskDuration, taskDurationStdDev);
        }
        
        // Getters and setters for WorkloadPatterns
        public double getAvgCpuUtilization() { return avgCpuUtilization; }
        public void setAvgCpuUtilization(double avgCpuUtilization) { this.avgCpuUtilization = avgCpuUtilization; }
        
        public double getAvgMemoryUtilization() { return avgMemoryUtilization; }
        public void setAvgMemoryUtilization(double avgMemoryUtilization) { this.avgMemoryUtilization = avgMemoryUtilization; }
        
        public double getAvgDiskUtilization() { return avgDiskUtilization; }
        public void setAvgDiskUtilization(double avgDiskUtilization) { this.avgDiskUtilization = avgDiskUtilization; }
        
        public double getCpuUtilizationStdDev() { return cpuUtilizationStdDev; }
        public void setCpuUtilizationStdDev(double cpuUtilizationStdDev) { this.cpuUtilizationStdDev = cpuUtilizationStdDev; }
        
        public double getMemoryUtilizationStdDev() { return memoryUtilizationStdDev; }
        public void setMemoryUtilizationStdDev(double memoryUtilizationStdDev) { this.memoryUtilizationStdDev = memoryUtilizationStdDev; }
        
        public double getDiskUtilizationStdDev() { return diskUtilizationStdDev; }
        public void setDiskUtilizationStdDev(double diskUtilizationStdDev) { this.diskUtilizationStdDev = diskUtilizationStdDev; }
        
        public double getAvgTaskDuration() { return avgTaskDuration; }
        public void setAvgTaskDuration(double avgTaskDuration) { this.avgTaskDuration = avgTaskDuration; }
        
        public double getTaskDurationStdDev() { return taskDurationStdDev; }
        public void setTaskDurationStdDev(double taskDurationStdDev) { this.taskDurationStdDev = taskDurationStdDev; }
        
        public double getAvgPeakCpuUsage() { return avgPeakCpuUsage; }
        public void setAvgPeakCpuUsage(double avgPeakCpuUsage) { this.avgPeakCpuUsage = avgPeakCpuUsage; }
        
        public double getAvgPeakMemoryUsage() { return avgPeakMemoryUsage; }
        public void setAvgPeakMemoryUsage(double avgPeakMemoryUsage) { this.avgPeakMemoryUsage = avgPeakMemoryUsage; }
        
        public Map<String, Integer> getDurationDistribution() { return durationDistribution; }
        public void setDurationDistribution(Map<String, Integer> durationDistribution) { this.durationDistribution = durationDistribution; }
        
        /**
         * Get detailed workload analysis for research reporting.
         * 
         * @return Comprehensive workload analysis results
         */
        public Map<String, Object> getDetailedAnalysis() {
            Map<String, Object> analysis = new HashMap<>();
            
            // Resource utilization analysis
            Map<String, Double> resourceUtilization = new HashMap<>();
            resourceUtilization.put("avgCpuUtilization", avgCpuUtilization);
            resourceUtilization.put("avgMemoryUtilization", avgMemoryUtilization);
            resourceUtilization.put("avgDiskUtilization", avgDiskUtilization);
            resourceUtilization.put("cpuUtilizationStdDev", cpuUtilizationStdDev);
            resourceUtilization.put("memoryUtilizationStdDev", memoryUtilizationStdDev);
            resourceUtilization.put("diskUtilizationStdDev", diskUtilizationStdDev);
            analysis.put("resourceUtilization", resourceUtilization);
            
            // Temporal analysis
            Map<String, Double> temporalAnalysis = new HashMap<>();
            temporalAnalysis.put("avgTaskDuration", avgTaskDuration);
            temporalAnalysis.put("taskDurationStdDev", taskDurationStdDev);
            analysis.put("temporalAnalysis", temporalAnalysis);
            
            // Peak usage analysis
            Map<String, Double> peakUsage = new HashMap<>();
            peakUsage.put("avgPeakCpuUsage", avgPeakCpuUsage);
            peakUsage.put("avgPeakMemoryUsage", avgPeakMemoryUsage);
            analysis.put("peakUsage", peakUsage);
            
            // Duration distribution
            analysis.put("durationDistribution", durationDistribution);
            
            return analysis;
        }
        
        /**
         * Calculate resource intensity classification for research analysis.
         * 
         * @return Resource intensity classification
         */
        public String getResourceIntensityClassification() {
            double combinedIntensity = (avgCpuUtilization + avgMemoryUtilization) / 2.0;
            
            if (combinedIntensity < 0.3) {
                return "LOW_INTENSITY";
            } else if (combinedIntensity < 0.6) {
                return "MEDIUM_INTENSITY";
            } else {
                return "HIGH_INTENSITY";
            }
        }
        
        /**
         * Calculate workload variability for research analysis.
         * 
         * @return Workload variability metrics
         */
        public Map<String, Double> getVariabilityMetrics() {
            Map<String, Double> variability = new HashMap<>();
            
            // Coefficient of variation for resource utilization
            if (avgCpuUtilization > 0) {
                variability.put("cpuVariabilityCoeff", cpuUtilizationStdDev / avgCpuUtilization);
            }
            if (avgMemoryUtilization > 0) {
                variability.put("memoryVariabilityCoeff", memoryUtilizationStdDev / avgMemoryUtilization);
            }
            if (avgTaskDuration > 0) {
                variability.put("durationVariabilityCoeff", taskDurationStdDev / avgTaskDuration);
            }
            
            return variability;
        }
    }
}
