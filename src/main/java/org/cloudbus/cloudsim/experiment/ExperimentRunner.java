package org.cloudbus.cloudsim.experiment;

import org.cloudbus.cloudsim.simulation.HippopotamusVmPlacementSimulation;
import org.cloudbus.cloudsim.simulation.ExperimentalScenario;
import org.cloudbus.cloudsim.simulation.ScenarioGenerator;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.ResourceMonitor;
import org.cloudbus.cloudsim.util.ValidationUtils;
import org.cloudbus.cloudsim.util.MetricsCalculator;
import org.cloudbus.cloudsim.policy.HippopotamusVmAllocationPolicy;
import org.cloudbus.cloudsim.baseline.*;
import org.cloudbus.cloudsim.core.CloudSim;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Execute individual experiments with comprehensive monitoring
 * Supporting research objectives: reliable experiment execution and data collection
 * @author Puneet Chandna
 */
public class ExperimentRunner {
    
    private static final LoggingManager logger = LoggingManager.getInstance();
    private final ResourceMonitor resourceMonitor;
    private final MetricsCalculator metricsCalculator;
    private final ExecutorService monitoringExecutor;
    private volatile boolean experimentRunning;
    
    // Constructor
    public ExperimentRunner() {
        this.resourceMonitor = new ResourceMonitor();
        this.metricsCalculator = new MetricsCalculator();
        this.monitoringExecutor = Executors.newSingleThreadExecutor();
        this.experimentRunning = false;
    }
    
    /**
     * Run single experiment with full monitoring and data collection
     */
    public ExperimentalResult runExperiment(ExperimentConfig config) {
        logger.logExperimentStart(config.getExperimentName());
        
        // Validate configuration
        try {
            config.validate();
            ValidationUtils.validateExperimentConfig(config);
        } catch (Exception e) {
            throw new ExperimentException("Invalid experiment configuration", e);
        }
        
        // Initialize result container
        ExperimentalResult result = new ExperimentalResult();
        result.setExperimentConfig(config);
        result.setStartTime(LocalDateTime.now());
        
        try {
            // Setup experiment environment
            setupExperimentEnvironment(config);
            
            // Start resource monitoring
            Future<Map<String, Object>> monitoringFuture = startResourceMonitoring(config);
            
            // Execute simulation
            executeSimulation(config, result);
            
            // Stop monitoring and collect resource usage
            experimentRunning = false;
            Map<String, Object> resourceUsage = monitoringFuture.get(5, TimeUnit.SECONDS);
            updateResourceMetrics(result, resourceUsage);
            
            // Collect and validate results
            collectResults(config, result);
            validateResults(result);
            
            // Clean up
            cleanupExperiment(config);
            
            // Mark completion
            result.completeExecution();
            logger.logMetrics(result.getPerformanceMetrics());
            
        } catch (Exception e) {
            logger.logError("Experiment failed: " + config.getExperimentName(), e);
            result.getValidationStatus().setValid(false);
            result.getValidationStatus().getValidationErrors().add(
                "Experiment execution failed: " + e.getMessage()
            );
            throw new ExperimentException("Experiment execution failed", e);
        }
        
        return result;
    }
    
    /**
     * Setup experiment environment
     */
    private void setupExperimentEnvironment(ExperimentConfig config) {
        logger.info("Setting up experiment environment for: " + config.getExperimentName());
        
        // Create output directories
        File outputDir = new File(config.getOutputSettings().getOutputDirectory());
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        // Create experiment-specific directory
        File expDir = new File(outputDir, config.getExperimentName());
        if (!expDir.exists()) {
            expDir.mkdirs();
        }
        
        // Set random seed for reproducibility
        Random.rand.setSeed(config.getRandomSeed());
        
        // Initialize CloudSim with minimal output
        CloudSim.setVerbose(config.getMeasurementSettings().isEnableDetailedLogging());
    }
    
    /**
     * Start resource monitoring in background
     */
    private Future<Map<String, Object>> startResourceMonitoring(ExperimentConfig config) {
        experimentRunning = true;
        
        return monitoringExecutor.submit(() -> {
            Map<String, Object> aggregatedMetrics = new HashMap<>();
            List<Double> cpuUsages = new ArrayList<>();
            List<Double> memoryUsages = new ArrayList<>();
            
            while (experimentRunning) {
                try {
                    cpuUsages.add(resourceMonitor.monitorCPUUsage());
                    memoryUsages.add(resourceMonitor.monitorMemoryUsage());
                    
                    Thread.sleep(config.getMeasurementSettings().getSampleIntervalMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            // Calculate aggregated metrics
            aggregatedMetrics.put("avg_cpu_usage", 
                cpuUsages.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            aggregatedMetrics.put("max_cpu_usage", 
                cpuUsages.stream().mapToDouble(Double::doubleValue).max().orElse(0));
            aggregatedMetrics.put("avg_memory_usage", 
                memoryUsages.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            aggregatedMetrics.put("max_memory_usage", 
                memoryUsages.stream().mapToDouble(Double::doubleValue).max().orElse(0));
            
            return aggregatedMetrics;
        });
    }
    
    /**
     * Execute the main simulation
     */
    private void executeSimulation(ExperimentConfig config, ExperimentalResult result) {
        logger.info("Executing simulation for algorithm: " + config.getAlgorithmType());
        
        // Generate scenario
        ScenarioGenerator scenarioGenerator = new ScenarioGenerator();
        ExperimentalScenario scenario = createScenario(config, scenarioGenerator);
        
        // Create simulation
        HippopotamusVmPlacementSimulation simulation = new HippopotamusVmPlacementSimulation();
        
        // Setup simulation with appropriate policy
        simulation.setupSimulation(scenario);
        
        // Configure allocation policy based on algorithm type
        configureAllocationPolicy(simulation, config, result);
        
        // Run simulation with timeout
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Void> simulationFuture = executor.submit(() -> {
            simulation.runSimulation();
            return null;
        });
        
        try {
            simulationFuture.get(config.getTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            simulationFuture.cancel(true);
            throw new ExperimentException("Simulation timeout exceeded", e);
        } catch (Exception e) {
            throw new ExperimentException("Simulation execution failed", e);
        } finally {
            executor.shutdown();
        }
        
        // Collect simulation metrics
        collectSimulationMetrics(simulation, result);
    }
    
    /**
     * Create experimental scenario based on configuration
     */
    private ExperimentalScenario createScenario(ExperimentConfig config, 
                                               ScenarioGenerator generator) {
        ExperimentalScenario scenario;
        
        if (config.getDatasetName() != null && !config.getDatasetName().isEmpty()) {
            // Load from dataset
            scenario = generator.loadDatasetScenario(config.getDatasetName());
        } else {
            // Generate synthetic scenario
            scenario = generator.generateScenario(
                config.getVmCount(),
                config.getHostCount(),
                config.getScenarioType()
            );
        }
        
        // Apply SLA requirements
        scenario.setSlaRequirements(config.getSlaRequirements());
        
        return scenario;
    }
    
    /**
     * Configure allocation policy based on algorithm type
     */
    private void configureAllocationPolicy(HippopotamusVmPlacementSimulation simulation,
                                         ExperimentConfig config,
                                         ExperimentalResult result) {
        switch (config.getAlgorithmType()) {
            case "HippopotamusOptimization":
                HippopotamusVmAllocationPolicy hoPolicy = new HippopotamusVmAllocationPolicy();
                hoPolicy.setParameters(config.toHippopotamusParameters());
                simulation.setAllocationPolicy(hoPolicy);
                
                // Track convergence data
                hoPolicy.setConvergenceCallback((iteration, fitness, objectives) -> {
                    result.addConvergencePoint(iteration, fitness, objectives);
                });
                break;
                
            case "FirstFit":
                simulation.setAllocationPolicy(new FirstFitVmAllocation());
                break;
                
            case "BestFit":
                simulation.setAllocationPolicy(new BestFitVmAllocation());
                break;
                
            case "GeneticAlgorithm":
                GeneticAlgorithmVmAllocation gaPolicy = new GeneticAlgorithmVmAllocation();
                gaPolicy.setParameters(config.getAlgorithmParameters());
                simulation.setAllocationPolicy(gaPolicy);
                break;
                
            case "ParticleSwarm":
                ParticleSwarmVmAllocation psoPolicy = new ParticleSwarmVmAllocation();
                psoPolicy.setParameters(config.getAlgorithmParameters());
                simulation.setAllocationPolicy(psoPolicy);
                break;
                
            case "AntColony":
                AntColonyVmAllocation acoPolicy = new AntColonyVmAllocation();
                acoPolicy.setParameters(config.getAlgorithmParameters());
                simulation.setAllocationPolicy(acoPolicy);
                break;
                
            case "Random":
                simulation.setAllocationPolicy(new RandomVmAllocation());
                break;
                
            default:
                throw new ExperimentException("Unknown algorithm type: " + 
                    config.getAlgorithmType());
        }
    }
    
    /**
     * Collect metrics from simulation
     */
    private void collectSimulationMetrics(HippopotamusVmPlacementSimulation simulation,
                                        ExperimentalResult result) {
        Map<String, Object> metrics = simulation.collectMetrics();
        
        // Update performance metrics
        ExperimentalResult.PerformanceMetrics perfMetrics = result.getPerformanceMetrics();
        
        // Resource utilization
        if (metrics.containsKey("resource_utilization")) {
            Map<String, Double> utilization = (Map<String, Double>) metrics.get("resource_utilization");
            perfMetrics.getResourceUtilization().setAvgCpuUtilization(
                utilization.getOrDefault("cpu", 0.0));
            perfMetrics.getResourceUtilization().setAvgMemoryUtilization(
                utilization.getOrDefault("memory", 0.0));
        }
        
        // Power consumption
        if (metrics.containsKey("power_consumption")) {
            Map<String, Double> power = (Map<String, Double>) metrics.get("power_consumption");
            perfMetrics.getPowerConsumption().setTotalPowerConsumption(
                power.getOrDefault("total", 0.0));
            perfMetrics.getPowerConsumption().setAvgPowerConsumption(
                power.getOrDefault("average", 0.0));
        }
        
        // SLA violations
        if (metrics.containsKey("sla_violations")) {
            Map<String, Object> sla = (Map<String, Object>) metrics.get("sla_violations");
            perfMetrics.getSlaViolations().setTotalViolations(
                (Integer) sla.getOrDefault("count", 0));
            perfMetrics.getSlaViolations().setViolationRate(
                (Double) sla.getOrDefault("rate", 0.0));
        }
        
        // Add raw data points for statistical analysis
        for (String metric : metrics.keySet()) {
            if (metrics.get(metric) instanceof Number) {
                result.addRawDataPoint(metric, ((Number) metrics.get(metric)).doubleValue());
            }
        }
    }
    
    /**
     * Update resource metrics in result
     */
    private void updateResourceMetrics(ExperimentalResult result, 
                                     Map<String, Object> resourceUsage) {
        ExperimentalResult.ExecutionMetadata metadata = result.getExecutionMetadata();
        
        metadata.setUsedMemory((long) resourceUsage.getOrDefault("max_memory_usage", 0L));
        
        // Add system resource usage to raw data
        result.addRawDataPoint("system_cpu_usage", 
            (Double) resourceUsage.getOrDefault("avg_cpu_usage", 0.0));
        result.addRawDataPoint("system_memory_usage", 
            (Double) resourceUsage.getOrDefault("avg_memory_usage", 0.0));
    }
    
    /**
     * Collect final results and calculate statistics
     */
    private void collectResults(ExperimentConfig config, ExperimentalResult result) {
        logger.info("Collecting results for experiment: " + config.getExperimentName());
        
        // Calculate statistical measures
        calculateStatisticalMeasures(result);
        
        // Save results if configured
        if (config.getOutputSettings().isSaveRawData()) {
            saveRawData(config, result);
        }
        
        // Generate charts if configured
        if (config.getOutputSettings().isGenerateCharts()) {
            generateCharts(config, result);
        }
        
        // Export in requested formats
        exportResults(config, result);
    }
    
    /**
     * Calculate statistical measures for all metrics
     */
    private void calculateStatisticalMeasures(ExperimentalResult result) {
        ExperimentalResult.StatisticalMeasures stats = result.getStatisticalMeasures();
        
        for (Map.Entry<String, List<Double>> entry : result.getRawData().entrySet()) {
            String metric = entry.getKey();
            List<Double> values = entry.getValue();
            
            if (values != null && !values.isEmpty()) {
                DoubleSummaryStatistics summary = values.stream()
                    .mapToDouble(Double::doubleValue)
                    .summaryStatistics();
                
                stats.getMeans().put(metric, summary.getAverage());
                stats.getMinValues().put(metric, summary.getMin());
                stats.getMaxValues().put(metric, summary.getMax());
                
                // Calculate standard deviation
                double mean = summary.getAverage();
                double variance = values.stream()
                    .mapToDouble(v -> Math.pow(v - mean, 2))
                    .average()
                    .orElse(0.0);
                double stdDev = Math.sqrt(variance);
                stats.getStandardDeviations().put(metric, stdDev);
                
                // Calculate confidence interval (95%)
                double confidenceInterval = 1.96 * stdDev / Math.sqrt(values.size());
                stats.getConfidenceIntervals().put(metric, confidenceInterval);
                
                // Calculate median
                List<Double> sorted = new ArrayList<>(values);
                Collections.sort(sorted);
                double median = sorted.size() % 2 == 0 ?
                    (sorted.get(sorted.size()/2 - 1) + sorted.get(sorted.size()/2)) / 2 :
                    sorted.get(sorted.size()/2);
                stats.getMedians().put(metric, median);
                
                // Coefficient of variation
                if (mean != 0) {
                    stats.getCoefficientsOfVariation().put(metric, stdDev / mean);
                }
            }
        }
    }
    
    /**
     * Save raw data for future analysis
     */
    private void saveRawData(ExperimentConfig config, ExperimentalResult result) {
        String outputPath = config.getOutputSettings().getOutputDirectory() + 
                          "/" + config.getExperimentName() + "/raw_data.json";
        
        try {
            result.saveToJSON(outputPath);
            logger.info("Raw data saved to: " + outputPath);
        } catch (IOException e) {
            logger.logError("Failed to save raw data", e);
        }
    }
    
    /**
     * Generate visualization charts
     */
    private void generateCharts(ExperimentConfig config, ExperimentalResult result) {
        // Chart generation would be implemented here
        // Using JFreeChart or similar library
        logger.info("Chart generation requested but not implemented in this version");
    }
    
    /**
     * Export results in various formats
     */
    private void exportResults(ExperimentConfig config, ExperimentalResult result) {
        for (String format : config.getOutputSettings().getExportFormats()) {
            String outputPath = config.getOutputSettings().getOutputDirectory() + 
                              "/" + config.getExperimentName() + "/results." + format;
            
            switch (format.toLowerCase()) {
                case "json":
                    try {
                        result.saveToJSON(outputPath);
                    } catch (IOException e) {
                        logger.logError("Failed to export JSON", e);
                    }
                    break;
                    
                case "csv":
                    // CSV export implementation
                    logger.info("CSV export requested but not implemented");
                    break;
                    
                case "xlsx":
                    // Excel export implementation
                    logger.info("Excel export requested but not implemented");
                    break;
            }
        }
    }
    
    /**
     * Validate experimental results
     */
    private void validateResults(ExperimentalResult result) {
        ValidationUtils.validateExperimentalResults(result);
        
        // Check for anomalies
        ExperimentalResult.ValidationStatus validation = result.getValidationStatus();
        
        // Check resource utilization bounds
        double cpuUtil = result.getPerformanceMetrics()
            .getResourceUtilization().getAvgCpuUtilization();
        if (cpuUtil < 0 || cpuUtil > 100) {
            validation.setValid(false);
            validation.getValidationErrors().add(
                "Invalid CPU utilization: " + cpuUtil);
        }
        
        // Check power consumption
        double power = result.getPerformanceMetrics()
            .getPowerConsumption().getTotalPowerConsumption();
        if (power < 0) {
            validation.setValid(false);
            validation.getValidationErrors().add(
                "Invalid power consumption: " + power);
        }
        
        // Record checks performed
        validation.getChecksPerformed().put("resource_bounds", true);
        validation.getChecksPerformed().put("power_validity", true);
        validation.getChecksPerformed().put("statistical_validity", true);
    }
    
    /**
     * Clean up after experiment
     */
    private void cleanupExperiment(ExperimentConfig config) {
        logger.info("Cleaning up experiment: " + config.getExperimentName());
        
        // Force garbage collection for memory-intensive experiments
        if (config.getVmCount() > 1000 || config.getHostCount() > 100) {
            System.gc();
        }
        
        // Clear any temporary files
        // Implementation depends on specific requirements
    }
    
    /**
     * Shutdown resources
     */
    public void shutdown() {
        monitoringExecutor.shutdown();
        try {
            if (!monitoringExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                monitoringExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitoringExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Custom exception for experiment failures
     */
    public static class ExperimentException extends RuntimeException {
        public ExperimentException(String message) {
            super(message);
        }
        
        public ExperimentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}