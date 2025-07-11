package org.cloudbus.cloudsim.util;

import org.cloudbus.cloudsim.vms.Vm;
import java.time.LocalDateTime;

/**
 * Represents an SLA violation event in the cloud system.
 * Used for tracking and analyzing SLA compliance.
 * 
 * @author Puneet Chandna
 */
public class SLAViolation {
    
    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    private String type;
    private String subType;
    private double timestamp;
    private double actualValue;
    private double thresholdValue;
    private Severity severity;
    private Vm affectedVm;
    private String description;
    private LocalDateTime occurrenceTime;
    
    public SLAViolation() {
        this.occurrenceTime = LocalDateTime.now();
    }
    
    public SLAViolation(String type, String subType, double timestamp, 
                       double actualValue, double thresholdValue, Severity severity) {
        this();
        this.type = type;
        this.subType = subType;
        this.timestamp = timestamp;
        this.actualValue = actualValue;
        this.thresholdValue = thresholdValue;
        this.severity = severity;
    }
    
    // Getters and setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getSubType() { return subType; }
    public void setSubType(String subType) { this.subType = subType; }
    
    public double getTimestamp() { return timestamp; }
    public void setTimestamp(double timestamp) { this.timestamp = timestamp; }
    
    public double getActualValue() { return actualValue; }
    public void setActualValue(double actualValue) { this.actualValue = actualValue; }
    
    public double getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(double thresholdValue) { this.thresholdValue = thresholdValue; }
    
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    
    public Vm getAffectedVm() { return affectedVm; }
    public void setAffectedVm(Vm affectedVm) { this.affectedVm = affectedVm; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public LocalDateTime getOccurrenceTime() { return occurrenceTime; }
    public void setOccurrenceTime(LocalDateTime occurrenceTime) { this.occurrenceTime = occurrenceTime; }
    
    public double getViolationPercentage() {
        if (thresholdValue == 0) return 0.0;
        return ((actualValue - thresholdValue) / thresholdValue) * 100.0;
    }
    
    @Override
    public String toString() {
        return String.format("SLAViolation{type=%s, subType=%s, severity=%s, violation=%.2f%%}", 
                           type, subType, severity, getViolationPercentage());
    }
}