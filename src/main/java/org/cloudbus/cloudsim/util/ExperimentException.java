package org.cloudbus.cloudsim.util;

/**
 * Custom unchecked exception for experiment-related failures in the research framework.
 * This exception is used throughout the CloudSim Hippopotamus Optimization research
 * framework to handle errors in a consistent manner.
 * 
 * As per the cross-cutting concerns, this exception is thrown when:
 * - Configuration validation fails
 * - Data integrity issues are detected
 * - Experiment execution encounters fatal errors
 * - Resource allocation failures occur
 * - Statistical analysis prerequisites are not met
 * 
 * @author CloudSim HO Research Team
 * @since 1.0
 */
public class ExperimentException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Error codes for categorizing experiment exceptions
     */
    public enum ErrorCode {
        CONFIGURATION_ERROR("CONF_ERR", "Configuration error"),
        VALIDATION_ERROR("VAL_ERR", "Validation error"),
        EXECUTION_ERROR("EXEC_ERR", "Execution error"),
        RESOURCE_ERROR("RES_ERR", "Resource allocation error"),
        DATA_ERROR("DATA_ERR", "Data integrity error"),
        ANALYSIS_ERROR("ANAL_ERR", "Analysis error"),
        IO_ERROR("IO_ERR", "Input/Output error"),
        TIMEOUT_ERROR("TIME_ERR", "Timeout error"),
        UNKNOWN_ERROR("UNK_ERR", "Unknown error");
        
        private final String code;
        private final String description;
        
        ErrorCode(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    private ErrorCode errorCode;
    private String experimentName;
    private String additionalInfo;
    
    /**
     * Constructs a new experiment exception with the specified detail message.
     * 
     * @param message the detail message (which is saved for later retrieval by the getMessage() method)
     */
    public ExperimentException(String message) {
        super(message);
        this.errorCode = ErrorCode.UNKNOWN_ERROR;
    }
    
    /**
     * Constructs a new experiment exception with the specified detail message and cause.
     * 
     * @param message the detail message (which is saved for later retrieval by the getMessage() method)
     * @param cause the cause (which is saved for later retrieval by the getCause() method)
     */
    public ExperimentException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = ErrorCode.UNKNOWN_ERROR;
        
        // Try to determine error code from cause
        if (cause != null) {
            if (cause instanceof java.io.IOException) {
                this.errorCode = ErrorCode.IO_ERROR;
            } else if (cause instanceof IllegalArgumentException || 
                       cause instanceof IllegalStateException) {
                this.errorCode = ErrorCode.VALIDATION_ERROR;
            } else if (cause instanceof OutOfMemoryError || 
                       cause instanceof java.util.concurrent.RejectedExecutionException) {
                this.errorCode = ErrorCode.RESOURCE_ERROR;
            }
        }
    }
    
    /**
     * Constructs a new experiment exception with error code and message.
     * 
     * @param errorCode the error code for categorization
     * @param message the detail message
     */
    public ExperimentException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    /**
     * Constructs a new experiment exception with error code, message and cause.
     * 
     * @param errorCode the error code for categorization
     * @param message the detail message
     * @param cause the cause
     */
    public ExperimentException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    /**
     * Constructs a new experiment exception with full details.
     * 
     * @param errorCode the error code for categorization
     * @param message the detail message
     * @param experimentName the name of the experiment that failed
     * @param cause the cause
     */
    public ExperimentException(ErrorCode errorCode, String message, 
                             String experimentName, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.experimentName = experimentName;
    }
    
    /**
     * Gets the error code associated with this exception.
     * 
     * @return the error code
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    /**
     * Sets the error code for this exception.
     * 
     * @param errorCode the error code to set
     */
    public void setErrorCode(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }
    
    /**
     * Gets the experiment name associated with this exception.
     * 
     * @return the experiment name, or null if not set
     */
    public String getExperimentName() {
        return experimentName;
    }
    
    /**
     * Sets the experiment name associated with this exception.
     * 
     * @param experimentName the experiment name
     */
    public void setExperimentName(String experimentName) {
        this.experimentName = experimentName;
    }
    
    /**
     * Gets additional information about the error.
     * 
     * @return additional information, or null if not set
     */
    public String getAdditionalInfo() {
        return additionalInfo;
    }
    
    /**
     * Sets additional information about the error.
     * 
     * @param additionalInfo additional error details
     */
    public void setAdditionalInfo(String additionalInfo) {
        this.additionalInfo = additionalInfo;
    }
    
    /**
     * Returns a formatted error message including all available details.
     * 
     * @return formatted error message
     */
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(errorCode.getCode()).append("] ");
        sb.append(errorCode.getDescription()).append(": ");
        sb.append(getMessage());
        
        if (experimentName != null) {
            sb.append(" (Experiment: ").append(experimentName).append(")");
        }
        
        if (additionalInfo != null) {
            sb.append(" - ").append(additionalInfo);
        }
        
        if (getCause() != null) {
            sb.append(" [Caused by: ").append(getCause().getClass().getSimpleName());
            sb.append(" - ").append(getCause().getMessage()).append("]");
        }
        
        return sb.toString();
    }
    
    /**
     * Checks if this exception represents a recoverable error.
     * Some errors like timeout or temporary resource issues might be recoverable.
     * 
     * @return true if the error might be recoverable through retry
     */
    public boolean isRecoverable() {
        return errorCode == ErrorCode.TIMEOUT_ERROR ||
               errorCode == ErrorCode.RESOURCE_ERROR ||
               (errorCode == ErrorCode.IO_ERROR && 
                getCause() instanceof java.io.IOException &&
                !(getCause() instanceof java.io.FileNotFoundException));
    }
    
    /**
     * Checks if this exception represents a critical error that should
     * stop the entire experiment batch.
     * 
     * @return true if this is a critical error
     */
    public boolean isCritical() {
        return errorCode == ErrorCode.CONFIGURATION_ERROR ||
               (errorCode == ErrorCode.RESOURCE_ERROR && 
                getCause() instanceof OutOfMemoryError);
    }
    
    @Override
    public String toString() {
        return getDetailedMessage();
    }
    
    /**
     * Factory method to create a configuration error exception.
     * 
     * @param message the error message
     * @return ExperimentException for configuration error
     */
    public static ExperimentException configurationError(String message) {
        return new ExperimentException(ErrorCode.CONFIGURATION_ERROR, message);
    }
    
    /**
     * Factory method to create a validation error exception.
     * 
     * @param message the error message
     * @return ExperimentException for validation error
     */
    public static ExperimentException validationError(String message) {
        return new ExperimentException(ErrorCode.VALIDATION_ERROR, message);
    }
    
    /**
     * Factory method to create an execution error exception.
     * 
     * @param message the error message
     * @param cause the underlying cause
     * @return ExperimentException for execution error
     */
    public static ExperimentException executionError(String message, Throwable cause) {
        return new ExperimentException(ErrorCode.EXECUTION_ERROR, message, cause);
    }
    
    /**
     * Factory method to create a data error exception.
     * 
     * @param message the error message
     * @return ExperimentException for data error
     */
    public static ExperimentException dataError(String message) {
        return new ExperimentException(ErrorCode.DATA_ERROR, message);
    }
    
    /**
     * Factory method to create an analysis error exception.
     * 
     * @param message the error message
     * @param cause the underlying cause
     * @return ExperimentException for analysis error
     */
    public static ExperimentException analysisError(String message, Throwable cause) {
        return new ExperimentException(ErrorCode.ANALYSIS_ERROR, message, cause);
    }
    
    /**
     * Factory method to create a resource error exception.
     * 
     * @param message the error message
     * @param cause the underlying cause
     * @return ExperimentException for resource error
     */
    public static ExperimentException resourceError(String message, Throwable cause) {
        return new ExperimentException(ErrorCode.RESOURCE_ERROR, message, cause);
    }
    
    /**
     * Factory method to wrap IO exceptions.
     * 
     * @param message the error message
     * @param ioException the IO exception to wrap
     * @return ExperimentException wrapping the IO error
     */
    public static ExperimentException wrapIOException(String message, java.io.IOException ioException) {
        return new ExperimentException(ErrorCode.IO_ERROR, message, ioException);
    }
}