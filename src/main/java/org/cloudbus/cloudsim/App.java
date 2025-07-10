package org.cloudbus.cloudsim;

import org.apache.commons.cli.*;
import org.cloudbus.cloudsim.util.LoggingManager;
import org.cloudbus.cloudsim.util.ConfigurationManager;
import org.cloudbus.cloudsim.util.ValidationUtils;
import org.cloudbus.cloudsim.util.ExperimentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Main application entry point for CloudSim Hippopotamus Optimization Research Framework.
 * 
 * Research Objectives Addressed:
 * - Initialize complete research pipeline
 * - Validate experimental environment
 * - Configure logging and monitoring systems
 * - Parse command-line arguments for experiment control
 * 
 * Integration Points:
 * - Initiates MainResearchController for complete research workflow
 * - Integrates with LoggingManager for comprehensive logging
 * - Uses ConfigurationManager for environment setup
 * - Implements ExperimentException handling pattern
 * 
 * Statistical Methods: N/A (Entry point only)
 * Publication Output: Generates execution logs and environment validation reports
 * Dataset Support: Environment validation for all supported datasets
 * 
 * @author Puneet Chandna
 * @version 2.0
 * @since CloudSim Plus 7.0.1
 */
public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);
    private static final String VERSION = "2.0.0";
    private static final String DEFAULT_CONFIG_PATH = "src/main/resources/config/experiment_config.yaml";
    
    // Exit codes for different error scenarios
    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_VALIDATION_ERROR = 1;
    private static final int EXIT_RUNTIME_ERROR = 2;
    private static final int EXIT_ENVIRONMENT_ERROR = 3;
    
    /**
     * Main application entry point with comprehensive command-line parsing.
     * Supports various execution modes for different research scenarios.
     * 
     * @param args Command line arguments for experiment configuration
     */
    public static void main(String[] args) {
        try {
            // Initialize logging system first
            initializeLogging();
            LoggingManager.logInfo("Starting CloudSim Hippopotamus Optimization Research Framework v" + VERSION);
            
            // Parse command line arguments
            CommandLine cmd = parseCommandLineArguments(args);
            
            // Handle help request
            if (cmd.hasOption("help")) {
                printHelp();
                System.exit(EXIT_SUCCESS);
            }
            
            // Validate environment before proceeding
            validateEnvironment();
            
            // Load configuration
            String configPath = cmd.getOptionValue("config", DEFAULT_CONFIG_PATH);
            ConfigurationManager.loadConfiguration(configPath);
            
            // Determine execution mode
            String mode = cmd.getOptionValue("mode", "full");
            boolean dryRun = cmd.hasOption("dry-run");
            int parallelism = Integer.parseInt(cmd.getOptionValue("parallel", "1"));
            
            // Create and configure main research controller
            MainResearchController controller = new MainResearchController();
            controller.setDryRun(dryRun);
            controller.setParallelism(parallelism);
            
            switch (mode.toLowerCase()) {
                case "full":
                    LoggingManager.logInfo("Executing full research pipeline");
                    controller.executeFullResearchPipeline();
                    break;
                    
                case "single":
                    String algorithm = cmd.getOptionValue("algorithm", "HippopotamusOptimization");
                    String dataset = cmd.getOptionValue("dataset", "synthetic");
                    LoggingManager.logInfo("Executing single experiment: " + algorithm + " on " + dataset);
                    controller.executeSingleExperiment(algorithm, dataset);
                    break;
                    
                case "comparison":
                    LoggingManager.logInfo("Executing algorithm comparison analysis");
                    controller.executeComparisonAnalysis();
                    break;
                    
                case "scalability":
                    LoggingManager.logInfo("Executing scalability analysis");
                    controller.executeScalabilityAnalysis();
                    break;
                    
                case "sensitivity":
                    LoggingManager.logInfo("Executing parameter sensitivity analysis");
                    controller.executeParameterSensitivityAnalysis();
                    break;
                    
                case "analysis":
                    String resultsPath = cmd.getOptionValue("results");
                    if (resultsPath == null) {
                        throw new ExperimentException("Results path required for analysis mode");
                    }
                    LoggingManager.logInfo("Executing analysis only on: " + resultsPath);
                    controller.executeAnalysisOnly(resultsPath);
                    break;
                    
                case "report":
                    LoggingManager.logInfo("Generating reports only");
                    controller.generateReportsOnly();
                    break;
                    
                default:
                    throw new ExperimentException("Unknown execution mode: " + mode);
            }
            
            LoggingManager.logInfo("Research framework execution completed successfully");
            System.exit(EXIT_SUCCESS);
            
        } catch (ExperimentException e) {
            LoggingManager.logError("Fatal research error: " + e.getMessage(), e);
            System.exit(EXIT_VALIDATION_ERROR);
        } catch (Exception e) {
            LoggingManager.logError("Unexpected system error: " + e.getMessage(), e);
            System.exit(EXIT_RUNTIME_ERROR);
        }
    }
    
    /**
     * Initialize logging system with research-specific configuration.
     */
    private static void initializeLogging() {
        try {
            // Create logs directory if it doesn't exist
            Path logsDir = Paths.get("logs");
            if (!Files.exists(logsDir)) {
                Files.createDirectories(logsDir);
            }
            
            LoggingManager.initialize("logs/cloudsim-ho-research.log");
            LoggingManager.logInfo("Logging system initialized successfully");
            
            // Log system properties for reproducibility
            logger.debug("Java version: {}", System.getProperty("java.version"));
            logger.debug("Available processors: {}", Runtime.getRuntime().availableProcessors());
            logger.debug("Max memory: {} MB", Runtime.getRuntime().maxMemory() / (1024 * 1024));
            
        } catch (Exception e) {
            System.err.println("Failed to initialize logging system: " + e.getMessage());
            System.exit(EXIT_ENVIRONMENT_ERROR);
        }
    }
    
    /**
     * Validate system environment and requirements for research experiments.
     */
    private static void validateEnvironment() {
        try {
            LoggingManager.logInfo("Validating experimental environment...");
            
            // Create required directories
            List<String> requiredDirs = Arrays.asList(
                "datasets", "results", "results/raw_data",
                "results/statistical_analysis", "results/comparison_reports",
                "results/visualizations"
            );
            
            for (String dir : requiredDirs) {
                Path dirPath = Paths.get(dir);
                if (!Files.exists(dirPath)) {
                    Files.createDirectories(dirPath);
                    LoggingManager.logInfo("Created directory: " + dir);
                }
            }
            
            // Validate using ValidationUtils
            ValidationUtils.validateDiskSpace();
            ValidationUtils.validateDirectoryStructure();
            ValidationUtils.validateDatasetAccess();
            
            LoggingManager.logInfo("Environment validation completed successfully");
            
        } catch (Exception e) {
            throw new ExperimentException("Environment validation failed", e);
        }
    }
    
    // ... (rest of the implementation combining best features from both)
        /**
     * Parse command line arguments for experiment configuration.
     * Supports various options for controlling experiment execution.
     * 
     * @param args Command line arguments
     * @return Parsed command line options
     */
    private static CommandLine parseCommandLineArguments(String[] args) {
        Options options = new Options();
        
        // Execution mode options
        options.addOption(Option.builder("m")
                .longOpt("mode")
                .hasArg()
                .argName("MODE")
                .desc("Execution mode: full, single, comparison, scalability, sensitivity, analysis, report")
                .build());
        
        // Configuration file option
        options.addOption(Option.builder("c")
                .longOpt("config")
                .hasArg()
                .argName("FILE")
                .desc("Path to experiment configuration file (default: " + DEFAULT_CONFIG_PATH + ")")
                .build());
        
        // Algorithm selection for single mode
        options.addOption(Option.builder("a")
                .longOpt("algorithm")
                .hasArg()
                .argName("NAME")
                .desc("Algorithm to test: HippopotamusOptimization, GeneticAlgorithm, ParticleSwarm, etc.")
                .build());
        
        // Dataset selection for single mode
        options.addOption(Option.builder("d")
                .longOpt("dataset")
                .hasArg()
                .argName("NAME")
                .desc("Dataset to use: google_traces, azure_traces, synthetic")
                .build());
        
        // Results path for analysis mode
        options.addOption(Option.builder("r")
                .longOpt("results")
                .hasArg()
                .argName("PATH")
                .desc("Results path for analysis-only mode")
                .build());
        
        // Output directory option
        options.addOption(Option.builder("o")
                .longOpt("output")
                .hasArg()
                .argName("DIR")
                .desc("Output directory for results (default: results/)")
                .build());
        
        // Parallel execution option
        options.addOption(Option.builder("p")
                .longOpt("parallel")
                .hasArg()
                .argName("N")
                .desc("Number of parallel threads (default: 1)")
                .build());
        
        // Dry run option
        options.addOption(Option.builder("dry")
                .longOpt("dry-run")
                .desc("Perform dry run without actual execution")
                .build());
        
        // Verbose logging option
        options.addOption(Option.builder("v")
                .longOpt("verbose")
                .desc("Enable verbose logging")
                .build());
        
        // Version option
        options.addOption(Option.builder("V")
                .longOpt("version")
                .desc("Show version information")
                .build());
        
        // Help option
        options.addOption(Option.builder("h")
                .longOpt("help")
                .desc("Show help message")
                .build());
        
        // Statistical options
        options.addOption(Option.builder("rep")
                .longOpt("replications")
                .hasArg()
                .argName("N")
                .desc("Number of replications for statistical analysis (default: 30)")
                .build());
        
        options.addOption(Option.builder("conf")
                .longOpt("confidence")
                .hasArg()
                .argName("LEVEL")
                .desc("Confidence level for statistical tests (default: 0.95)")
                .build());
        
        // Experiment-specific options
        options.addOption(Option.builder("seed")
                .longOpt("random-seed")
                .hasArg()
                .argName("SEED")
                .desc("Random seed for reproducibility")
                .build());
        
        options.addOption(Option.builder("timeout")
                .longOpt("experiment-timeout")
                .hasArg()
                .argName("MINUTES")
                .desc("Timeout for individual experiments in minutes")
                .build());
        
        CommandLineParser parser = new DefaultParser();
        
        try {
            CommandLine cmd = parser.parse(options, args);
            
            // Handle version request
            if (cmd.hasOption("version")) {
                printVersion();
                System.exit(EXIT_SUCCESS);
            }
            
            // Configure verbose logging if requested
            if (cmd.hasOption("verbose")) {
                LoggingManager.setLogLevel("DEBUG");
            }
            
            // Set output directory if specified
            if (cmd.hasOption("output")) {
                System.setProperty("results.dir", cmd.getOptionValue("output"));
            }
            
            // Set random seed if specified
            if (cmd.hasOption("seed")) {
                long seed = Long.parseLong(cmd.getOptionValue("seed"));
                System.setProperty("experiment.seed", String.valueOf(seed));
                LoggingManager.logInfo("Random seed set to: " + seed);
            }
            
            // Set statistical parameters if specified
            if (cmd.hasOption("replications")) {
                System.setProperty("stats.replications", cmd.getOptionValue("replications"));
            }
            
            if (cmd.hasOption("confidence")) {
                System.setProperty("stats.confidence", cmd.getOptionValue("confidence"));
            }
            
            // Set experiment timeout if specified
            if (cmd.hasOption("timeout")) {
                System.setProperty("experiment.timeout", cmd.getOptionValue("timeout"));
            }
            
            return cmd;
            
        } catch (ParseException e) {
            System.err.println("Command line parsing failed: " + e.getMessage());
            printHelp();
            throw new ExperimentException("Invalid command line arguments", e);
        }
    }
    
    /**
     * Print comprehensive help message with examples.
     */
    private static void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(120);
        
        String header = "\nCloudSim Hippopotamus Optimization Research Framework v" + VERSION + "\n" +
                       "A comprehensive research tool for VM placement optimization using the Hippopotamus Optimization algorithm.\n\n" +
                       "This framework supports:\n" +
                       "  • Multiple VM placement algorithms for comparison\n" +
                       "  • Real-world datasets (Google, Azure) and synthetic workloads\n" +
                       "  • Statistical analysis and hypothesis testing\n" +
                       "  • Publication-ready report generation\n" +
                       "  • Scalability and sensitivity analysis\n\n";
        
        String footer = "\n\nExecution Modes:\n" +
                       "  full        - Execute complete research pipeline with all experiments\n" +
                       "  single      - Run single algorithm on specific dataset\n" +
                       "  comparison  - Compare all algorithms on all datasets\n" +
                       "  scalability - Test algorithm scalability with varying problem sizes\n" +
                       "  sensitivity - Analyze parameter sensitivity\n" +
                       "  analysis    - Analyze existing results without re-running experiments\n" +
                       "  report      - Generate reports from existing analysis\n\n" +
                       "Examples:\n" +
                       "  # Run full research pipeline\n" +
                       "  java -jar cloudsim-ho.jar\n\n" +
                       "  # Run single experiment with HO on Google traces\n" +
                       "  java -jar cloudsim-ho.jar -m single -a HippopotamusOptimization -d google_traces\n\n" +
                       "  # Run comparison analysis with 4 parallel threads\n" +
                       "  java -jar cloudsim-ho.jar -m comparison -p 4\n\n" +
                       "  # Run scalability analysis with custom configuration\n" +
                       "  java -jar cloudsim-ho.jar -m scalability -c config/scalability.yaml\n\n" +
                       "  # Analyze existing results\n" +
                       "  java -jar cloudsim-ho.jar -m analysis -r results/experiment_20240115\n\n" +
                       "  # Run with reproducible seed and verbose logging\n" +
                       "  java -jar cloudsim-ho.jar -m full --seed 42 -v\n\n" +
                       "  # Dry run to validate configuration\n" +
                       "  java -jar cloudsim-ho.jar --dry-run\n\n" +
                       "For more information, visit: https://github.com/cloudsim/hippopotamus-optimization\n";
        
        formatter.printHelp("java -jar cloudsim-ho.jar [options]", header, getOptions(), footer, true);
    }
    
    /**
     * Get all command-line options for help display.
     * 
     * @return Options object with all available options
     */
    private static Options getOptions() {
        Options options = new Options();
        
        // Create option groups for better organization
        OptionGroup modeGroup = new OptionGroup();
        modeGroup.setRequired(false);
        
        // Basic options
        options.addOption("h", "help", false, "Show this help message");
        options.addOption("V", "version", false, "Show version information");
        options.addOption("v", "verbose", false, "Enable verbose logging");
        
        // Mode options
        options.addOption("m", "mode", true, "Execution mode (see below for details)");
        
        // Configuration options
        options.addOption("c", "config", true, "Configuration file path");
        options.addOption("o", "output", true, "Output directory for results");
        
        // Experiment options
        options.addOption("a", "algorithm", true, "Algorithm to test (single mode)");
        options.addOption("d", "dataset", true, "Dataset to use (single mode)");
        options.addOption("r", "results", true, "Results path (analysis mode)");
        
        // Execution options
        options.addOption("p", "parallel", true, "Number of parallel threads");
        options.addOption("dry", "dry-run", false, "Perform dry run");
        options.addOption("seed", "random-seed", true, "Random seed for reproducibility");
        options.addOption("timeout", "experiment-timeout", true, "Experiment timeout (minutes)");
        
        // Statistical options
        options.addOption("rep", "replications", true, "Number of replications");
        options.addOption("conf", "confidence", true, "Confidence level (0-1)");
        
        return options;
    }
    
    /**
     * Print version information.
     */
    private static void printVersion() {
        System.out.println("CloudSim Hippopotamus Optimization Research Framework");
        System.out.println("Version: " + VERSION);
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println("CloudSim Plus Version: 7.0.1");
        System.out.println("Build Date: " + getBuildDate());
        System.out.println("Research Team: CloudSim HO Research Group");
    }
    
    /**
     * Get build date from manifest or return current date.
     * 
     * @return Build date string
     */
    private static String getBuildDate() {
        try {
            // Try to read from manifest
            Package pkg = App.class.getPackage();
            String buildDate = pkg.getImplementationVersion();
            if (buildDate != null) {
                return buildDate;
            }
        } catch (Exception e) {
            // Ignore and return current date
        }
        return java.time.LocalDate.now().toString();
    }
    
    /**
     * Configure system properties based on command-line options.
     * 
     * @param cmd Parsed command line
     */
    private static void configureSystemProperties(CommandLine cmd) {
        // Set system properties for global access
        if (cmd.hasOption("output")) {
            System.setProperty("experiment.output.dir", cmd.getOptionValue("output"));
        }
        
        if (cmd.hasOption("parallel")) {
            System.setProperty("experiment.parallel.threads", cmd.getOptionValue("parallel"));
        }
        
        if (cmd.hasOption("dry-run")) {
            System.setProperty("experiment.dry.run", "true");
        }
        
        if (cmd.hasOption("verbose")) {
            System.setProperty("experiment.verbose", "true");
        }
        
        // Set default properties if not specified
        System.setProperty("experiment.output.dir", 
            System.getProperty("experiment.output.dir", "results"));
        System.setProperty("experiment.parallel.threads", 
            System.getProperty("experiment.parallel.threads", "1"));
    }
    
    /**
     * Register shutdown hook for graceful termination.
     */
    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LoggingManager.logInfo("Shutting down CloudSim HO Research Framework...");
            
            try {
                // Save any pending results
                MainResearchController.getInstance().savePartialResults();
                
                // Close logging system
                LoggingManager.close();
                
            } catch (Exception e) {
                System.err.println("Error during shutdown: " + e.getMessage());
            }
        }));
    }
    
    /**
     * Print research framework banner on startup.
     */
    private static void printBanner() {
        System.out.println("\n" +
            "╔══════════════════════════════════════════════════════════════════════╗\n" +
            "║     CloudSim Hippopotamus Optimization Research Framework v" + VERSION + "     ║\n" +
            "║                                                                      ║\n" +
            "║  Advancing VM Placement Optimization through Nature-Inspired AI      ║\n" +
            "╚══════════════════════════════════════════════════════════════════════╝\n");
    }
}
