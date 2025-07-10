package org.cloudbus.cloudsim;

import org.cloudbus.cloudsim.experiment.*;
import org.cloudbus.cloudsim.analyzer.*;
import org.cloudbus.cloudsim.reporting.*;
import org.cloudbus.cloudsim.util.*;
import org.cloudbus.cloudsim.dataset.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

/**
 * Central controller for research experiments.
 * Coordinates the entire research workflow from experiment setup through
 * analysis and report generation.
 * 
 * Research objectives addressed:
 * - Comprehensive experimental evaluation of HO algorithm
 * - Statistical comparison with baseline algorithms
 * - Real-world dataset integration and analysis
 * - Publication-ready result generation
 * 
 * @author Puneet Chandna
 * @since 1.0
 */
public class MainResearchController {
    
    private static final Logger logger = LoggerFactory.getLogger(MainResearchController.class);
    
    private final ResearchOrchestrator orchestrator;
    private final ConfigurationManager configManager;
    private final LoggingManager loggingManager;
    private final ResourceMonitor resourceMonitor;
    private final ValidationUtils validator;
    
    private List<ExperimentConfig> experimentConfigs;
    private Map<String, List<ExperimentalResult>> aggregatedResults;
    private ExecutorService executorService;
    private LocalDateTime researchStartTime;
    private Path outputDirectory;
    
    // Research pipeline configuration
    private boolean enableRealDatasetAnalysis = true;
    private boolean enableScalabilityAnalysis = true;
    private boolean enableSensitivityAnalysis = true;
    private boolean enableStatisticalTesting = true;
    private boolean generatePublicationMaterials = true;
    
    /**
     * Constructor initializes all research components.
     */
    public MainResearchController() {
        this.orchestrator = new ResearchOrchestrator();
        this.configManager = new ConfigurationManager();
        this.loggingManager = new LoggingManager();
        this.resourceMonitor = new ResourceMonitor();
        this.validator = new ValidationUtils();
        this.aggregatedResults = new HashMap<>();
        this.executorService = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
        );
    }
    
    /**
     * Execute the full research pipeline.
     * This is the main entry point for conducting the complete research study.
     * 
     * @throws ExperimentException if critical research errors occur
     */
    public void executeFullResearchPipeline() throws ExperimentException {
        try {
            logger.info("Starting comprehensive research pipeline execution");
            researchStartTime = LocalDateTime.now();
            
            // Initialize research environment
            initializeResearchEnvironment();
            
            // Phase 1: Configuration and validation
            logger.info("Phase 1: Configuring experiments");
            configureExperiments();
            
            // Phase 2: Execute experiments
            logger.info("Phase 2: Executing experiments");
            executeExperiments();
            
            // Phase 3: Coordinate analysis
            logger.info("Phase 3: Coordinating comprehensive analysis");
            coordinateAnalysis();
            
            // Phase 4: Generate final results
            logger.info("Phase 4: Generating final results and reports");
            generateFinalResults();
            
            // Cleanup and finalization
            finalizeResearch();
            
            Duration totalDuration = Duration.between(researchStartTime, LocalDateTime.now());
            logger.info("Research pipeline completed successfully in {} hours", 
                       totalDuration.toHours());
            
        } catch (Exception e) {
            logger.error("Critical error in research pipeline", e);
            generateFailureReport(e);
            throw new ExperimentException("Research pipeline failed: " + e.getMessage(), e);
        } finally {
            shutdownExecutorService();
        }
    }
    
    /**
     * Configure experiments based on research requirements.
     * Sets up all experimental configurations including parameter spaces,
     * datasets, and algorithm configurations.
     * 
     * @throws ExperimentException if configuration fails
     */
    public void configureExperiments() throws ExperimentException {
        try {
            logger.info("Configuring experimental setup");
            
            // Load base configuration
            Map<String, Object> baseConfig = configManager.loadConfiguration("experiment_config.yaml");
            validator.validateConfiguration(baseConfig);
            
            // Generate experiment configurations
            experimentConfigs = new ArrayList<>();
            
            // 1. Configure baseline comparison experiments
            configureBaselineExperiments(baseConfig);
            
            // 2. Configure real dataset experiments
            if (enableRealDatasetAnalysis) {
                configureRealDatasetExperiments(baseConfig);
            }
            
            // 3. Configure scalability experiments
            if (enableScalabilityAnalysis) {
                configureScalabilityExperiments(baseConfig);
            }
            
            // 4. Configure parameter sensitivity experiments
            if (enableSensitivityAnalysis) {
                configureSensitivityExperiments(baseConfig);
            }
            
            logger.info("Configured {} total experiments", experimentConfigs.size());
            
            // Validate all configurations
            for (ExperimentConfig config : experimentConfigs) {
                validator.validateExperimentConfig(config);
            }
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to configure experiments", e);
        }
    }
    
    /**
     * Coordinate all analysis phases for comprehensive research evaluation.
     * Integrates results from multiple analyzers to provide holistic insights.
     * 
     * @throws ExperimentException if analysis coordination fails
     */
    public void coordinateAnalysis() throws ExperimentException {
        try {
            logger.info("Coordinating comprehensive analysis across all results");
            
            // 1. Performance metrics analysis
            logger.info("Analyzing performance metrics");
            PerformanceMetricsAnalyzer perfAnalyzer = new PerformanceMetricsAnalyzer();
            Map<String, Object> performanceResults = perfAnalyzer.analyzeResults(aggregatedResults);
            
            // 2. Statistical analysis
            if (enableStatisticalTesting) {
                logger.info("Performing statistical analysis");
                ComprehensiveStatisticalAnalyzer statAnalyzer = new ComprehensiveStatisticalAnalyzer();
                Map<String, Object> statisticalResults = statAnalyzer.performFullAnalysis(aggregatedResults);
                
                // Validate statistical assumptions
                StatisticalTestSuite testSuite = new StatisticalTestSuite();
                testSuite.validateAssumptions(aggregatedResults);
            }
            
            // 3. SLA violation analysis
            logger.info("Analyzing SLA violations");
            SLAViolationAnalyzer slaAnalyzer = new SLAViolationAnalyzer();
            Map<String, Object> slaResults = slaAnalyzer.analyzeViolations(aggregatedResults);
            
            // 4. Real dataset analysis
            if (enableRealDatasetAnalysis) {
                logger.info("Analyzing real dataset performance");
                RealDatasetAnalyzer datasetAnalyzer = new RealDatasetAnalyzer();
                datasetAnalyzer.analyzeAllDatasets(aggregatedResults);
            }
            
            // 5. Scalability analysis
            if (enableScalabilityAnalysis) {
                logger.info("Performing scalability analysis");
                ScalabilityAnalyzer scalabilityAnalyzer = new ScalabilityAnalyzer();
                ScalabilityResults scalabilityResults = 
                    scalabilityAnalyzer.performScalabilityAnalysis(aggregatedResults);
            }
            
            // 6. Parameter sensitivity analysis
            if (enableSensitivityAnalysis) {
                logger.info("Analyzing parameter sensitivity");
                ParameterSensitivityAnalyzer sensitivityAnalyzer = new ParameterSensitivityAnalyzer();
                sensitivityAnalyzer.performSensitivityAnalysis(aggregatedResults);
            }
            
            logger.info("Analysis coordination completed successfully");
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to coordinate analysis", e);
        }
    }
    
    /**
     * Generate comprehensive final results including reports, visualizations,
     * and publication-ready materials.
     * 
     * @throws ExperimentException if result generation fails
     */
    public void generateFinalResults() throws ExperimentException {
        try {
            logger.info("Generating comprehensive final results");
            
            // 1. Generate comparison report
            logger.info("Generating algorithm comparison report");
            ComparisonReport comparisonReport = new ComparisonReport(outputDirectory);
            comparisonReport.generateFullComparison(aggregatedResults);
            
            // 2. Generate visualizations
            logger.info("Generating research visualizations");
            VisualizationGenerator vizGen = new VisualizationGenerator(outputDirectory);
            vizGen.generateAllVisualizations(aggregatedResults);
            
            // 3. Generate statistical reports
            if (enableStatisticalTesting) {
                logger.info("Generating statistical analysis reports");
                generateStatisticalReports();
            }
            
            // 4. Generate publication materials
            if (generatePublicationMaterials) {
                logger.info("Generating publication-ready materials");
                generatePublicationMaterials();
            }
            
            // 5. Generate final comprehensive report
            logger.info("Generating final comprehensive report");
            FinalReportGenerator finalReportGen = new FinalReportGenerator(outputDirectory);
            finalReportGen.generateComprehensiveReport(aggregatedResults, getAllAnalysisResults());
            
            // 6. Export data for external analysis
            logger.info("Exporting data for external analysis tools");
            PublicationDataExporter dataExporter = new PublicationDataExporter(outputDirectory);
            dataExporter.exportAllFormats(aggregatedResults);
            
            // 7. Generate research summary
            ResearchSummary summary = new ResearchSummary();
            summary.generateExecutiveSummary(aggregatedResults, outputDirectory);
            
            logger.info("Final results generation completed");
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to generate final results", e);
        }
    }
    
    // Private helper methods
    
    private void initializeResearchEnvironment() throws ExperimentException {
        try {
            // Setup output directory structure
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            outputDirectory = Paths.get("results", "research_" + timestamp);
            Files.createDirectories(outputDirectory);
            Files.createDirectories(outputDirectory.resolve("raw_data"));
            Files.createDirectories(outputDirectory.resolve("statistical_analysis"));
            Files.createDirectories(outputDirectory.resolve("comparison_reports"));
            Files.createDirectories(outputDirectory.resolve("visualizations"));
            Files.createDirectories(outputDirectory.resolve("publication_materials"));
            
            // Initialize logging for this research session
            loggingManager.initializeResearchLogging(outputDirectory);
            
            // Start resource monitoring
            resourceMonitor.startMonitoring();
            
            // Log research environment information
            logResearchEnvironment();
            
        } catch (Exception e) {
            throw new ExperimentException("Failed to initialize research environment", e);
        }
    }
    
    private void executeExperiments() throws ExperimentException {
        logger.info("Executing {} experiments", experimentConfigs.size());
        
        // Use batch executor for efficient execution
        BatchExperimentExecutor batchExecutor = new BatchExperimentExecutor(executorService);
        
        try {
            // Execute experiments in batches
            List<CompletableFuture<List<ExperimentalResult>>> futures = 
                batchExecutor.executeBatchAsync(experimentConfigs);
            
            // Collect and aggregate results
            for (int i = 0; i < futures.size(); i++) {
                List<ExperimentalResult> batchResults = futures.get(i).get();
                String experimentType = experimentConfigs.get(i).getExperimentType();
                
                aggregatedResults.computeIfAbsent(experimentType, k -> new ArrayList<>())
                    .addAll(batchResults);
                
                logger.info("Completed batch {} of {} with {} results", 
                           i + 1, futures.size(), batchResults.size());
            }
            
        } catch (Exception e) {
            throw new ExperimentException("Experiment execution failed", e);
        }
    }
    
    private void configureBaselineExperiments(Map<String, Object> baseConfig) {
        logger.info("Configuring baseline comparison experiments");
        
        // Extract algorithm configurations
        List<Map<String, Object>> algorithms = 
            (List<Map<String, Object>>) baseConfig.get("algorithms");
        
        // Create experiment configs for each algorithm
        for (Map<String, Object> algoConfig : algorithms) {
            String algorithmName = (String) algoConfig.get("name");
            Map<String, Object> parameters = (Map<String, Object>) algoConfig.get("parameters");
            
            // Generate parameter combinations
            ParameterSpace paramSpace = new ParameterSpace(parameters);
            List<Map<String, Object>> paramSets = paramSpace.generateParameterCombinations();
            
            for (Map<String, Object> paramSet : paramSets) {
                ExperimentConfig config = new ExperimentConfig();
                config.setExperimentType("baseline_comparison");
                config.setAlgorithmName(algorithmName);
                config.setParameters(paramSet);
                config.setReplications(30); // For statistical significance
                config.setRandomSeed(12345L); // For reproducibility
                
                experimentConfigs.add(config);
            }
        }
    }
    
    private void configureRealDatasetExperiments(Map<String, Object> baseConfig) {
        logger.info("Configuring real dataset experiments");
        
        List<String> datasets = (List<String>) baseConfig.get("datasets");
        
        for (String dataset : datasets) {
            if (dataset.equals("google_traces") || dataset.equals("azure_traces")) {
                ExperimentConfig config = new ExperimentConfig();
                config.setExperimentType("real_dataset");
                config.setDatasetName(dataset);
                config.setAlgorithmName("HippopotamusOptimization");
                config.setReplications(10); // Fewer replications due to dataset size
                
                experimentConfigs.add(config);
            }
        }
    }
    
    private void configureScalabilityExperiments(Map<String, Object> baseConfig) {
        logger.info("Configuring scalability experiments");
        
        Map<String, Object> scalabilityTests = 
            (Map<String, Object>) baseConfig.get("scalability_tests");
        
        List<Integer> vmCounts = (List<Integer>) scalabilityTests.get("vm_counts");
        List<Integer> hostCounts = (List<Integer>) scalabilityTests.get("host_counts");
        
        for (Integer vmCount : vmCounts) {
            for (Integer hostCount : hostCounts) {
                if (vmCount <= hostCount * 10) { // Reasonable VM to host ratio
                    ExperimentConfig config = new ExperimentConfig();
                    config.setExperimentType("scalability");
                    config.setAlgorithmName("HippopotamusOptimization");
                    config.setVmCount(vmCount);
                    config.setHostCount(hostCount);
                    config.setReplications(5);
                    
                    experimentConfigs.add(config);
                }
            }
        }
    }
    
    private void configureSensitivityExperiments(Map<String, Object> baseConfig) {
        logger.info("Configuring parameter sensitivity experiments");
        
        // Focus on HO algorithm parameters
        Map<String, Object> hoParams = new HashMap<>();
        hoParams.put("population_size", List.of(10, 20, 50, 100, 200));
        hoParams.put("max_iterations", List.of(50, 100, 200, 500));
        hoParams.put("convergence_threshold", List.of(0.0001, 0.001, 0.01));
        
        ParameterSpace paramSpace = new ParameterSpace(hoParams);
        List<Map<String, Object>> paramSets = paramSpace.generateSensitivitySets();
        
        for (Map<String, Object> paramSet : paramSets) {
            ExperimentConfig config = new ExperimentConfig();
            config.setExperimentType("sensitivity");
            config.setAlgorithmName("HippopotamusOptimization");
            config.setParameters(paramSet);
            config.setReplications(10);
            
            experimentConfigs.add(config);
        }
    }
    
    private void generateStatisticalReports() throws Exception {
        ComprehensiveStatisticalAnalyzer statAnalyzer = new ComprehensiveStatisticalAnalyzer();
        statAnalyzer.generateDetailedStatisticalReport(aggregatedResults, outputDirectory);
        
        StatisticalTestSuite testSuite = new StatisticalTestSuite();
        testSuite.generateSignificanceReport(aggregatedResults, outputDirectory);
    }
    
    private void generatePublicationMaterials() throws Exception {
        // Generate LaTeX tables
        LatexTableGenerator latexGen = new LatexTableGenerator();
        latexGen.generateAllTables(aggregatedResults, outputDirectory.resolve("publication_materials"));
        
        // Generate research paper sections
        ResearchPaperGenerator paperGen = new ResearchPaperGenerator();
        paperGen.generatePaperSections(aggregatedResults, outputDirectory.resolve("publication_materials"));
        
        // Generate high-quality figures
        VisualizationGenerator vizGen = new VisualizationGenerator(outputDirectory);
        vizGen.exportPublicationQualityFigures(outputDirectory.resolve("publication_materials"));
    }
    
    private Map<String, Object> getAllAnalysisResults() {
        // Aggregate all analysis results for final report
        Map<String, Object> allResults = new HashMap<>();
        allResults.put("performance_analysis", aggregatedResults);
        allResults.put("research_metadata", getResearchMetadata());
        allResults.put("resource_usage", resourceMonitor.getResourceUsageReport());
        return allResults;
    }
    
    private Map<String, Object> getResearchMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("start_time", researchStartTime);
        metadata.put("end_time", LocalDateTime.now());
        metadata.put("total_experiments", experimentConfigs.size());
        metadata.put("output_directory", outputDirectory.toString());
        metadata.put("java_version", System.getProperty("java.version"));
        metadata.put("available_processors", Runtime.getRuntime().availableProcessors());
        return metadata;
    }
    
    private void logResearchEnvironment() {
        logger.info("Research Environment Information:");
        logger.info("  Output Directory: {}", outputDirectory);
        logger.info("  Java Version: {}", System.getProperty("java.version"));
        logger.info("  Available Memory: {} MB", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        logger.info("  Available Processors: {}", Runtime.getRuntime().availableProcessors());
        logger.info("  Research Features Enabled:");
        logger.info("    - Real Dataset Analysis: {}", enableRealDatasetAnalysis);
        logger.info("    - Scalability Analysis: {}", enableScalabilityAnalysis);
        logger.info("    - Sensitivity Analysis: {}", enableSensitivityAnalysis);
        logger.info("    - Statistical Testing: {}", enableStatisticalTesting);
        logger.info("    - Publication Materials: {}", generatePublicationMaterials);
    }
    
    private void finalizeResearch() {
        try {
            // Generate resource usage report
            resourceMonitor.stopMonitoring();
            resourceMonitor.generateResourceReport(outputDirectory);
            
            // Save all configurations for reproducibility
            configManager.saveAllConfigurations(experimentConfigs, outputDirectory);
            
            // Generate research completion summary
            loggingManager.generateLogSummary(outputDirectory);
            
        } catch (Exception e) {
            logger.error("Error during research finalization", e);
        }
    }
    
    private void generateFailureReport(Exception e) {
        try {
            Path failureReport = outputDirectory.resolve("failure_report.txt");
            String report = String.format(
                "Research Pipeline Failure Report\n" +
                "================================\n" +
                "Timestamp: %s\n" +
                "Failed after: %s\n" +
                "Completed experiments: %d\n" +
                "Exception: %s\n" +
                "Stack trace:\n%s",
                LocalDateTime.now(),
                Duration.between(researchStartTime, LocalDateTime.now()),
                aggregatedResults.values().stream().mapToInt(List::size).sum(),
                e.getMessage(),
                getStackTraceString(e)
            );
            Files.write(failureReport, report.getBytes());
        } catch (Exception ex) {
            logger.error("Failed to generate failure report", ex);
        }
    }
    
    private String getStackTraceString(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("  at ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }
    
    private void shutdownExecutorService() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}