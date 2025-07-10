package org.cloudbus.cloudsim.algorithm;

import java.util.Map;
import java.util.HashMap;

/**
 * Represents the weights for multi-objective optimization in Hippopotamus Optimization Algorithm.
 * This class encapsulates the weights for different objectives like resource utilization,
 * power consumption, SLA violations, load balancing, and communication costs.
 * 
 * @author Puneet Chandna
 * @since CloudSim Plus 7.0.1
 */
public class ObjectiveWeights {
    
    private double resourceWeight;
    private double powerWeight;
    private double slaWeight;
    private double loadBalanceWeight;
    private double communicationWeight;
    
    /**
     * Default constructor with equal weights
     */
    public ObjectiveWeights() {
        this(0.2, 0.2, 0.2, 0.2, 0.2);
    }
    
    /**
     * Constructor with specified weights
     * 
     * @param resourceWeight Weight for resource utilization objective
     * @param powerWeight Weight for power consumption objective
     * @param slaWeight Weight for SLA violations objective
     * @param loadBalanceWeight Weight for load balancing objective
     * @param communicationWeight Weight for communication cost objective
     */
    public ObjectiveWeights(double resourceWeight, double powerWeight, double slaWeight, 
                           double loadBalanceWeight, double communicationWeight) {
        this.resourceWeight = resourceWeight;
        this.powerWeight = powerWeight;
        this.slaWeight = slaWeight;
        this.loadBalanceWeight = loadBalanceWeight;
        this.communicationWeight = communicationWeight;
        
        normalizeWeights();
    }
    
    /**
     * Constructor from map of weights
     * 
     * @param weights Map containing weight values
     */
    public ObjectiveWeights(Map<String, Double> weights) {
        this.resourceWeight = weights.getOrDefault("resource_utilization", 0.2);
        this.powerWeight = weights.getOrDefault("power_consumption", 0.2);
        this.slaWeight = weights.getOrDefault("sla_violations", 0.2);
        this.loadBalanceWeight = weights.getOrDefault("load_balance", 0.2);
        this.communicationWeight = weights.getOrDefault("communication_cost", 0.2);
        
        normalizeWeights();
    }
    
    /**
     * Create from HippopotamusParameters
     * 
     * @param params Parameters containing weight values
     * @return ObjectiveWeights instance
     */
    public static ObjectiveWeights fromParameters(HippopotamusParameters params) {
        return new ObjectiveWeights(
            params.getResourceUtilizationWeight(),
            params.getPowerConsumptionWeight(),
            params.getSlaViolationWeight(),
            params.getLoadBalancingWeight(),
            params.getMigrationCostWeight()
        );
    }
    
    /**
     * Get default weights for research
     * 
     * @return Default ObjectiveWeights instance
     */
    public static ObjectiveWeights getDefaultWeights() {
        return new ObjectiveWeights(0.3, 0.25, 0.25, 0.15, 0.05);
    }
    
    /**
     * Normalize weights to sum to 1.0
     */
    private void normalizeWeights() {
        double totalWeight = resourceWeight + powerWeight + slaWeight + 
                           loadBalanceWeight + communicationWeight;
        
        if (totalWeight > 0) {
            resourceWeight /= totalWeight;
            powerWeight /= totalWeight;
            slaWeight /= totalWeight;
            loadBalanceWeight /= totalWeight;
            communicationWeight /= totalWeight;
        }
    }
    
    /**
     * Validate that weights are non-negative and sum to approximately 1.0
     * 
     * @throws IllegalArgumentException if weights are invalid
     */
    public void validate() {
        if (resourceWeight < 0 || powerWeight < 0 || slaWeight < 0 || 
            loadBalanceWeight < 0 || communicationWeight < 0) {
            throw new IllegalArgumentException("All weights must be non-negative");
        }
        
        double totalWeight = resourceWeight + powerWeight + slaWeight + 
                           loadBalanceWeight + communicationWeight;
        
        if (Math.abs(totalWeight - 1.0) > 0.001) {
            throw new IllegalArgumentException("Weights must sum to approximately 1.0, current sum: " + totalWeight);
        }
    }
    
    /**
     * Convert to map representation
     * 
     * @return Map of weight names to values
     */
    public Map<String, Double> toMap() {
        Map<String, Double> weights = new HashMap<>();
        weights.put("resource_utilization", resourceWeight);
        weights.put("power_consumption", powerWeight);
        weights.put("sla_violations", slaWeight);
        weights.put("load_balance", loadBalanceWeight);
        weights.put("communication_cost", communicationWeight);
        return weights;
    }
    
    // Getters and setters
    public double getResourceWeight() { return resourceWeight; }
    public void setResourceWeight(double resourceWeight) { 
        this.resourceWeight = resourceWeight; 
        normalizeWeights();
    }
    
    public double getPowerWeight() { return powerWeight; }
    public void setPowerWeight(double powerWeight) { 
        this.powerWeight = powerWeight; 
        normalizeWeights();
    }
    
    public double getSlaWeight() { return slaWeight; }
    public void setSlaWeight(double slaWeight) { 
        this.slaWeight = slaWeight; 
        normalizeWeights();
    }
    
    public double getLoadBalanceWeight() { return loadBalanceWeight; }
    public void setLoadBalanceWeight(double loadBalanceWeight) { 
        this.loadBalanceWeight = loadBalanceWeight; 
        normalizeWeights();
    }
    
    public double getCommunicationWeight() { return communicationWeight; }
    public void setCommunicationWeight(double communicationWeight) { 
        this.communicationWeight = communicationWeight; 
        normalizeWeights();
    }
    
    @Override
    public String toString() {
        return String.format(
            "ObjectiveWeights{resource=%.3f, power=%.3f, sla=%.3f, loadBalance=%.3f, communication=%.3f}",
            resourceWeight, powerWeight, slaWeight, loadBalanceWeight, communicationWeight
        );
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ObjectiveWeights other = (ObjectiveWeights) obj;
        return Double.compare(other.resourceWeight, resourceWeight) == 0 &&
               Double.compare(other.powerWeight, powerWeight) == 0 &&
               Double.compare(other.slaWeight, slaWeight) == 0 &&
               Double.compare(other.loadBalanceWeight, loadBalanceWeight) == 0 &&
               Double.compare(other.communicationWeight, communicationWeight) == 0;
    }
    
    @Override
    public int hashCode() {
        return java.util.Objects.hash(resourceWeight, powerWeight, slaWeight, 
                                     loadBalanceWeight, communicationWeight);
    }
}
