package org.cloudbus.cloudsim.util;

/**
 * Custom unchecked exception for experiment-related errors
 * Used throughout the research framework for consistent error handling
 * @author Puneet Chandna
 * @version 2.0
 */
public class ExperimentException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    // Error categories for better error classification
    public enum ErrorCategory {
        CONFIGURATION_ERROR("Configuration Error"),
        VALIDATION_ERROR("Validation Error"),
        DATA_ERROR("Data Error"),
        ALGORITHM_ERROR("Algorithm Error"),
        SIMULATION_ERROR("Simulation Error"),
        ANALYSIS_ERROR("Analysis Error"),
        REPORTING_ERROR("Reporting Error"),
        IO_ERROR("I/O Error"),
        RESOURCE_ERROR("Resource Error"),
        UNKNOWN_ERROR("Unknown Error");
        
        private final String description;
        
        ErrorCategory(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    private final ErrorCategory category;
    private final String experimentId;
    private final String componentName;
    
    /**
     * Constructor with message only
     * @param message Error message
     */
    public ExperimentException(String message) {
        super(message);
        this.category = ErrorCategory.UNKNOWN_ERROR;
        this.experimentId = null;
        this.componentName = null;
    }
    
    /**
     * Constructor with message and cause
     * @param message Error message
     * @param cause Underlying cause
     */
    public ExperimentException(String message, Throwable cause) {
        super(message, cause);
        this.category = ErrorCategory.UNKNOWN_ERROR;
        this.experimentId = null;
        this.componentName = null;
    }
    
    /**
     * Constructor with message and error category
     * @param message Error message
     * @param category Error category
     */
    public ExperimentException(String message, ErrorCategory category) {
        super(message);
        this.category = category;
        this.experimentId = null;
        this.componentName = null;
    }
    
    /**
     * Constructor with message, cause, and error category
     * @param message Error message
     * @param cause Underlying cause
     * @param category Error category
     */
    public ExperimentException(String message, Throwable cause, ErrorCategory category) {
        super(message, cause);
        this.category = category;
        this.experimentId = null;
        this.componentName = null;
    }
    
    /**
     * Full constructor with all details
     * @param message Error message
     * @param cause Underlying cause
     * @param category Error category
     * @param experimentId Experiment ID where error occurred
     * @param componentName Component name where error occurred
     */
    public ExperimentException(String message, Throwable cause, ErrorCategory category,
                             String experimentId, String componentName) {
        super(message, cause);
        this.category = category;
        this.experimentId = experimentId;
        this.componentName = componentName;
    }
    
    /**
     * Get error category
     * @return Error category
     */
    public ErrorCategory getCategory() {
        return category;
    }
    
    /**
     * Get experiment ID
     * @return Experiment ID or null if not applicable
     */
    public String getExperimentId() {
        return experimentId;
    }
    
    /**
     * Get component name
     * @return Component name or null if not applicable
     */
    public String getComponentName() {
        return componentName;
    }
    
    /**
     * Get detailed error message including all context
     * @return Detailed error message
     */
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(category.getDescription()).append("] ");
        
        if (experimentId != null) {
            sb.append("Experiment: ").append(experimentId).append(" | ");
        }
        
        if (componentName != null) {
            sb.append("Component: ").append(componentName).append(" | ");
        }
        
        sb.append("Message: ").append(getMessage());
        
        if (getCause() != null) {
            sb.append(" | Cause: ").append(getCause().getClass().getSimpleName())
              .append(" - ").append(getCause().getMessage());
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return getDetailedMessage();
    }
    
    // Static factory methods for common error scenarios
    
    /**
     * Create configuration error
     */
    public static ExperimentException configurationError(String message) {
        return new ExperimentException(message, ErrorCategory.CONFIGURATION_ERROR);
    }
    
    /**
     * Create configuration error with cause
     */
    public static ExperimentException configurationError(String message, Throwable cause) {
        return new ExperimentException(message, cause, ErrorCategory.CONFIGURATION_ERROR);
    }
    
    /**
     * Create validation error
     */
    public static ExperimentException validationError(String message) {
        return new ExperimentException(message, ErrorCategory.VALIDATION_ERROR);
    }
    
    /**
     * Create data error
     */
    public static ExperimentException dataError(String message) {
        return new ExperimentException(message, ErrorCategory.DATA_ERROR);
    }
    
    /**
     * Create data error with cause
     */
    public static ExperimentException dataError(String message, Throwable cause) {
        return new ExperimentException(message, cause, ErrorCategory.DATA_ERROR);
    }
    
    /**
     * Create algorithm error
     */
    public static ExperimentException algorithmError(String message) {
        return new ExperimentException(message, ErrorCategory.ALGORITHM_ERROR);
    }
    
    /**
     * Create algorithm error with cause
     */
    public static ExperimentException algorithmError(String message, Throwable cause) {
        return new ExperimentException(message, cause, ErrorCategory.ALGORITHM_ERROR);
    }
    
    /**
     * Create simulation error
     */
    public static ExperimentException simulationError(String message) {
        return new ExperimentException(message, ErrorCategory.SIMULATION_ERROR);
    }
    
    /**
     * Create simulation error with cause
     */
    public static ExperimentException simulationError(String message, Throwable cause) {
        return new ExperimentException(message, cause, ErrorCategory.SIMULATION_ERROR);
    }
    
    /**
     * Create analysis error
     */
    public static ExperimentException analysisError(String message) {
        return new ExperimentException(message, ErrorCategory.ANALYSIS_ERROR);
    }
    
    /**
     * Create reporting error
     */
    public static ExperimentException reportingError(String message) {
        return new ExperimentException(message, ErrorCategory.REPORTING_ERROR);
    }
    
    /**
     * Create I/O error
     */
    public static ExperimentException ioError(String message, Throwable cause) {
        return new ExperimentException(message, cause, ErrorCategory.IO_ERROR);
    }
    
    /**
     * Create resource error
     */
    public static ExperimentException resourceError(String message) {
        return new ExperimentException(message, ErrorCategory.RESOURCE_ERROR);
    }
    /**
     * Create unknown error
     */
    public static ExperimentException unknownError(String message) {
        return new ExperimentException(message, ErrorCategory.UNKNOWN_ERROR);
    }
}