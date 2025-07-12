package org.cloudbus.cloudsim.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.BufferedReader;
import java.io.File;
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
 * Manages comprehensive logging for the CloudSim HO Research Framework.
 * Provides centralized logging configuration, experiment tracking, metrics logging,
 * and log analysis capabilities for research experiments.
 *
 * @author Puneet Chandna
 * @version 1.0
 * @since 1.0
 */
public class LoggingManager {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(LoggingManager.class);
    
    // Singleton instance for static method access
    private static LoggingManager instance;
    
    private static final String LOG_DIR = "logs";
    private static final String EXPERIMENT_LOG_DIR = "logs/experiments";
    private static final String METRICS_LOG_DIR = "logs/metrics";
    private static final String ERROR_LOG_DIR = "logs/errors";
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    private final LoggerContext loggerContext;
    private final Map<String, Long> experimentStartTimes;
    private final Map<String, List<String>> experimentLogs;
    private final Path logDirectory;
    
    private String currentExperimentId;
    private FileAppender<ILoggingEvent> experimentAppender;
    private FileAppender<ILoggingEvent> metricsAppender;
    
    /**
     * Get the singleton instance of LoggingManager.
     * Creates a new instance if one doesn't exist.
     *
     * @return the singleton LoggingManager instance
     */
    public static synchronized LoggingManager getInstance() {
        if (instance == null) {
            instance = new LoggingManager();
        }
        return instance;
    }
    
    /**
     * Static method to log info messages.
     *
     * @param message the message to log
     * @param args optional arguments for message formatting
     */
    public static void logInfo(String message, Object... args) {
        logger.info(message, args);
    }
    
    /**
     * Static method to log warning messages.
     *
     * @param message the message to log
     * @param args optional arguments for message formatting
     */
    public static void logWarning(String message, Object... args) {
        logger.warn(message, args);
    }
    
    /**
     * Static method to log error messages.
     *
     * @param message the message to log
     * @param throwable the throwable to log
     */
    public static void logError(String message, Throwable throwable) {
        logger.error("ERROR: {}", message, throwable);
    }
    
    /**
     * Static method to log debug messages.
     *
     * @param message the message to log
     * @param args optional arguments for message formatting
     */
    public static void logDebug(String message, Object... args) {
        logger.debug(message, args);
    }
    
    /**
     * Static method to configure logging.
     *
    *public static void configureLogging() {
     *   getInstance().configureLogging();
    *}
    /
    /**
     * Static method to configure logging with custom directory.
     *
     * @param logDirectory the log directory path
     */
    public static void configureLogging(String logDirectory) {
        if (instance == null) {
            instance = new LoggingManager(Paths.get(logDirectory));
        }
        instance.configureLogging();
    }
    
    /**
     * Static method to initialize research logging.
     *
     * @param outputDirectory the output directory for logs
     */
    public static void initializeResearchLogging(Path outputDirectory) {
        if (instance == null) {
            instance = new LoggingManager(outputDirectory);
        }
        instance.configureLogging();
    }
    
    /**
     * Static method to generate log summary.
     *
     * @param outputDirectory the output directory
     */
    public static void generateLogSummary(Path outputDirectory) {
        getInstance().generateLogSummary("research");
    }
    
    /**
     * Constructs a LoggingManager with default log directory.
     */
    public LoggingManager() {
        this(Paths.get(LOG_DIR));
    }
    
    /**
     * Constructs a LoggingManager with specified log directory.
     *
     * @param logDirectory the directory for log files
     */
    public LoggingManager(Path logDirectory) {
        this.loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        this.experimentStartTimes = new ConcurrentHashMap<>();
        this.experimentLogs = new ConcurrentHashMap<>();
        this.logDirectory = logDirectory;
        
        try {
            createLogDirectories();
            configureLogging();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize logging system", e);
        }
    }
    
    /**
     * Configures the logging system for research experiments.
     */
    public void configureLogging() {
        logger.info("Configuring logging system for research experiments");
        
        try {
            // Reset existing configuration
            loggerContext.reset();
            
            // Configure console appender
            configureConsoleAppender();
            
            // Configure file appenders
            configureMainFileAppender();
            configureErrorFileAppender();
            
            // Set logging levels
            configureLoggingLevels();
            
            logger.info("Logging system configured successfully");
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure logging system", e);
        }
    }
    
    /**
     * Logs the start of an experiment.
     *
     * @param experimentId unique identifier for the experiment
     * @param configuration experiment configuration details
     */
    public void logExperimentStart(String experimentId, Map<String, Object> configuration) {
        this.currentExperimentId = experimentId;
        
        // Set MDC context for this experiment
        MDC.put("experimentId", experimentId);
        
        // Record start time
        experimentStartTimes.put(experimentId, System.currentTimeMillis());
        
        // Create experiment-specific log file
        createExperimentLogFile(experimentId);
        
        // Log experiment details
        logger.info("=== EXPERIMENT START ===");
        logger.info("Experiment ID: {}", experimentId);
        logger.info("Start Time: {}", LocalDateTime.now().format(TIMESTAMP_FORMAT));
        logger.info("Configuration:");
        logConfiguration(configuration, "  ");
        logger.info("========================");
        
        // Initialize experiment log list
        experimentLogs.put(experimentId, new ArrayList<>());
    }
    
    /**
     * Logs the end of an experiment.
     *
     * @param experimentId unique identifier for the experiment
     * @param success whether the experiment completed successfully
     */
    public void logExperimentEnd(String experimentId, boolean success) {
        Long startTime = experimentStartTimes.get(experimentId);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            
            logger.info("=== EXPERIMENT END ===");
            logger.info("Experiment ID: {}", experimentId);
            logger.info("End Time: {}", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            logger.info("Duration: {} ms ({} seconds)", duration, duration / 1000.0);
            logger.info("Status: {}", success ? "SUCCESS" : "FAILED");
            logger.info("======================");
            
            // Clean up
            experimentStartTimes.remove(experimentId);
            closeExperimentLogFile();
        }
        
        // Clear MDC context
        MDC.clear();
    }
    
    /**
     * Logs performance metrics for analysis.
     *
     * @param metricName name of the metric
     * @param value metric value
     * @param additionalInfo additional context information
     */
    public void logMetrics(String metricName, Object value, Map<String, Object> additionalInfo) {
        // Ensure metrics appender is active
        if (metricsAppender == null) {
            createMetricsLogFile();
        }
        
        // Create structured metric log entry
        Map<String, Object> metricEntry = new HashMap<>();
        metricEntry.put("timestamp", System.currentTimeMillis());
        metricEntry.put("experimentId", currentExperimentId);
        metricEntry.put("metric", metricName);
        metricEntry.put("value", value);
        
        if (additionalInfo != null) {
            metricEntry.putAll(additionalInfo);
        }
        
        // Log as structured data
        logger.info("METRIC: {}", formatMetricEntry(metricEntry));
        
        // Also log to experiment-specific log
        if (currentExperimentId != null && experimentLogs.containsKey(currentExperimentId)) {
            experimentLogs.get(currentExperimentId).add(
                String.format("METRIC[%s]: %s = %s", 
                    LocalDateTime.now().format(TIMESTAMP_FORMAT), metricName, value)
            );
        }
    }
    
    /**
     * Generates a summary of logs for an experiment.
     *
     * @param experimentId the experiment identifier
     * @return log summary
     */
    public String generateLogSummary(String experimentId) {
        logger.info("Generating log summary for experiment: {}", experimentId);
        
        StringBuilder summary = new StringBuilder();
        summary.append("=== LOG SUMMARY ===\n");
        summary.append("Experiment ID: ").append(experimentId).append("\n");
        
        try {
            // Analyze main log file
            Path experimentLogPath = logDirectory
                .resolve(EXPERIMENT_LOG_DIR)
                .resolve(experimentId + ".log");
            
            if (Files.exists(experimentLogPath)) {
                Map<String, Integer> logLevelCounts = analyzeLogFile(experimentLogPath);
                
                summary.append("\nLog Level Distribution:\n");
                logLevelCounts.forEach((level, count) -> 
                    summary.append(String.format("  %s: %d\n", level, count))
                );
                
                // Extract key metrics from logs
                List<String> metricLogs = extractMetricLogs(experimentLogPath);
                summary.append("\nRecorded Metrics: ").append(metricLogs.size()).append("\n");
                
                // Extract errors
                List<String> errorLogs = extractErrorLogs(experimentLogPath);
                summary.append("Errors Encountered: ").append(errorLogs.size()).append("\n");
                
                if (!errorLogs.isEmpty()) {
                    summary.append("\nError Summary:\n");
                    errorLogs.stream().limit(5).forEach(error -> 
                        summary.append("  - ").append(error).append("\n")
                    );
                    if (errorLogs.size() > 5) {
                        summary.append("  ... and ").append(errorLogs.size() - 5).append(" more\n");
                    }
                }
            }
            
            // Add experiment-specific logs if available
            List<String> expLogs = experimentLogs.get(experimentId);
            if (expLogs != null && !expLogs.isEmpty()) {
                summary.append("\nKey Events:\n");
                expLogs.stream()
                    .filter(log -> log.contains("METRIC") || log.contains("ERROR"))
                    .limit(10)
                    .forEach(log -> summary.append("  ").append(log).append("\n"));
            }
            
        } catch (Exception e) {
            logger.error("Failed to generate log summary", e);
            summary.append("\nError generating summary: ").append(e.getMessage());
        }
        
        summary.append("===================");
        return summary.toString();
    }
    
    /**
     * Sets the logging level for a specific logger.
     *
     * @param loggerName the logger name
     * @param level the logging level
     */
    public void setLogLevel(String loggerName, String level) {
        Logger specificLogger = loggerContext.getLogger(loggerName);
        specificLogger.setLevel(Level.toLevel(level));
        logger.info("Set logging level for {} to {}", loggerName, level);
    }
    
    /**
     * Flushes all log appenders.
     */
    public void flush() {
        loggerContext.getLoggerList().forEach(logger -> {
            logger.iteratorForAppenders().forEachRemaining(appender -> {
                if (appender instanceof FileAppender) {
                    ((FileAppender<?>) appender).getEncoder().stop();
                    ((FileAppender<?>) appender).getEncoder().start();
                }
            });
        });
    }
    
    // Private helper methods
    
    private void createLogDirectories() throws IOException {
        Files.createDirectories(logDirectory);
        Files.createDirectories(logDirectory.resolve(EXPERIMENT_LOG_DIR));
        Files.createDirectories(logDirectory.resolve(METRICS_LOG_DIR));
        Files.createDirectories(logDirectory.resolve(ERROR_LOG_DIR));
    }
    
    private void configureConsoleAppender() {
        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setContext(loggerContext);
        consoleAppender.setName("CONSOLE");
        
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        encoder.start();
        
        consoleAppender.setEncoder(encoder);
        consoleAppender.start();
        
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(consoleAppender);
    }
    
    private void configureMainFileAppender() {
        RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();
        fileAppender.setContext(loggerContext);
        fileAppender.setName("FILE");
        fileAppender.setFile(logDirectory.resolve("cloudsim-ho.log").toString());
        
        TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
        rollingPolicy.setContext(loggerContext);
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.setFileNamePattern(logDirectory.resolve("cloudsim-ho-%d{yyyy-MM-dd}.log").toString());
        rollingPolicy.setMaxHistory(30);
        rollingPolicy.start();
        
        fileAppender.setRollingPolicy(rollingPolicy);
        
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger - %msg%n");
        encoder.start();
        
        fileAppender.setEncoder(encoder);
        fileAppender.start();
        
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(fileAppender);
    }
    
    private void configureErrorFileAppender() {
        FileAppender<ILoggingEvent> errorAppender = new FileAppender<>();
        errorAppender.setContext(loggerContext);
        errorAppender.setName("ERROR_FILE");
        errorAppender.setFile(logDirectory.resolve(ERROR_LOG_DIR).resolve("errors.log").toString());
        errorAppender.setAppend(true);
        
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %logger - %msg%n%ex");
        encoder.start();
        
        errorAppender.setEncoder(encoder);
        errorAppender.addFilter(new ch.qos.logback.classic.filter.ThresholdFilter() {{
            setLevel("ERROR");
        }});
        errorAppender.start();
        
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(errorAppender);
    }
    
    private void configureLoggingLevels() {
        // Set default levels
        Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.INFO);
        
        // Set specific package levels
        loggerContext.getLogger("org.cloudbus.cloudsim").setLevel(Level.DEBUG);
        loggerContext.getLogger("org.cloudsimplus").setLevel(Level.INFO);
        
        // Reduce verbosity of external libraries
        loggerContext.getLogger("org.apache").setLevel(Level.WARN);
        loggerContext.getLogger("com.fasterxml").setLevel(Level.WARN);
    }
    
    private void createExperimentLogFile(String experimentId) {
        try {
            experimentAppender = new FileAppender<>();
            experimentAppender.setContext(loggerContext);
            experimentAppender.setName("EXPERIMENT_" + experimentId);
            experimentAppender.setFile(
                logDirectory.resolve(EXPERIMENT_LOG_DIR).resolve(experimentId + ".log").toString()
            );
            
            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(loggerContext);
            encoder.setPattern("%d{HH:mm:ss.SSS} %-5level - %msg%n");
            encoder.start();
            
            experimentAppender.setEncoder(encoder);
            experimentAppender.start();
            
            Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
            rootLogger.addAppender(experimentAppender);
            
        } catch (Exception e) {
            logger.error("Failed to create experiment log file", e);
        }
    }
    
    private void closeExperimentLogFile() {
        if (experimentAppender != null) {
            experimentAppender.stop();
            Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
            rootLogger.detachAppender(experimentAppender);
            experimentAppender = null;
        }
    }
    
    private void createMetricsLogFile() {
        try {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            metricsAppender = new FileAppender<>();
            metricsAppender.setContext(loggerContext);
            metricsAppender.setName("METRICS");
            metricsAppender.setFile(
                logDirectory.resolve(METRICS_LOG_DIR).resolve("metrics_" + timestamp + ".log").toString()
            );
            
            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(loggerContext);
            encoder.setPattern("%msg%n");
            encoder.start();
            
            metricsAppender.setEncoder(encoder);
            metricsAppender.start();
            
            Logger metricsLogger = loggerContext.getLogger("METRICS");
            metricsLogger.addAppender(metricsAppender);
            metricsLogger.setAdditive(false);
            
        } catch (Exception e) {
            logger.error("Failed to create metrics log file", e);
        }
    }
    
    private void logConfiguration(Map<String, Object> configuration, String indent) {
        configuration.forEach((key, value) -> {
            if (value instanceof Map) {
                logger.info("{}{}:", indent, key);
                logConfiguration((Map<String, Object>) value, indent + "  ");
            } else if (value instanceof List) {
                logger.info("{}{}: {}", indent, key, value);
            } else {
                logger.info("{}{}: {}", indent, key, value);
            }
        });
    }
    
    private String formatMetricEntry(Map<String, Object> metricEntry) {
        return metricEntry.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(", ", "{", "}"));
    }
    
    private Map<String, Integer> analyzeLogFile(Path logPath) throws IOException {
        Map<String, Integer> logLevelCounts = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(logPath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String level = extractLogLevel(line);
                logLevelCounts.merge(level, 1, Integer::sum);
            }
        }
        
        return logLevelCounts;
    }
    
    private String extractLogLevel(String logLine) {
        if (logLine.contains("ERROR")) return "ERROR";
        if (logLine.contains("WARN")) return "WARN";
        if (logLine.contains("INFO")) return "INFO";
        if (logLine.contains("DEBUG")) return "DEBUG";
        if (logLine.contains("TRACE")) return "TRACE";
        return "OTHER";
    }
    
    private List<String> extractMetricLogs(Path logPath) throws IOException {
        List<String> metricLogs = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(logPath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("METRIC:")) {
                    metricLogs.add(line);
                }
            }
        }
        
        return metricLogs;
    }
    
    private List<String> extractErrorLogs(Path logPath) throws IOException {
        List<String> errorLogs = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(logPath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("ERROR")) {
                    errorLogs.add(line);
                }
            }
        }
        
        return errorLogs;
    }
} 