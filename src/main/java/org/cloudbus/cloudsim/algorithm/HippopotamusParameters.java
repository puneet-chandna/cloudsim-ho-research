package org.cloudbus.cloudsim.algorithm;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;


/**
 * Configuration class for Hippopotamus Optimization algorithm parameters
 * with comprehensive sensitivity analysis support for research experiments.
 * 
 * This class manages all algorithm parameters including their valid ranges,
 * default values, and validation rules for extensive research analysis.
 * 
 * @author Puneet Chandna
 * @since CloudSim Plus 7.0.1
 */
public class HippopotamusParameters {
    
    // Core Algorithm Parameters
    private int populationSize;
    private int maxIterations;
    private double convergenceThreshold;
    private double inertiaWeight;
    private double socialFactor;
    private double cognitiveFactor;
    private double mutationRate;
    private double explorationFactor;
    private double exploitationFactor;
    private boolean adaptiveParameters;
    
    // Multi-objective weights
    private double resourceUtilizationWeight;
    private double powerConsumptionWeight;
    private double slaViolationWeight;
    private double loadBalancingWeight;
    private double migrationCostWeight;
    
    // Advanced Parameters
    private int eliteSize;
    private double diversityThreshold;
    private int stagnationLimit;
    private boolean enableLocalSearch;
    private double localSearchProbability;
    private int maxLocalSearchIterations;
    
    // Research Parameters
    private long randomSeed;
    private boolean trackConvergence;
    private boolean trackDiversity;
    private boolean detailedLogging;
    private int statisticalRuns;
    private int convergenceWindow;
    
    // Objective weights instance
    private ObjectiveWeights objectiveWeights;
    
    // Parameter Ranges for Sensitivity Analysis
    private static final Map<String, ParameterRange> PARAMETER_RANGES = new HashMap<>();
    
    static {
        // Initialize parameter ranges for sensitivity analysis
        PARAMETER_RANGES.put("populationSize", new ParameterRange(10, 200, 50));
        PARAMETER_RANGES.put("maxIterations", new ParameterRange(50, 1000, 200));
        PARAMETER_RANGES.put("convergenceThreshold", new ParameterRange(0.0001, 0.01, 0.001));
        PARAMETER_RANGES.put("inertiaWeight", new ParameterRange(0.1, 0.9, 0.5));
        PARAMETER_RANGES.put("socialFactor", new ParameterRange(0.5, 3.0, 2.0));
        PARAMETER_RANGES.put("cognitiveFactor", new ParameterRange(0.5, 3.0, 2.0));
        PARAMETER_RANGES.put("mutationRate", new ParameterRange(0.01, 0.3, 0.1));
        PARAMETER_RANGES.put("explorationFactor", new ParameterRange(0.1, 0.9, 0.7));
        PARAMETER_RANGES.put("exploitationFactor", new ParameterRange(0.1, 0.9, 0.3));
        
        // Multi-objective weights (sum should equal 1.0)
        PARAMETER_RANGES.put("resourceUtilizationWeight", new ParameterRange(0.0, 1.0, 0.3));
        PARAMETER_RANGES.put("powerConsumptionWeight", new ParameterRange(0.0, 1.0, 0.25));
        PARAMETER_RANGES.put("slaViolationWeight", new ParameterRange(0.0, 1.0, 0.25));
        PARAMETER_RANGES.put("loadBalancingWeight", new ParameterRange(0.0, 1.0, 0.15));
        PARAMETER_RANGES.put("migrationCostWeight", new ParameterRange(0.0, 1.0, 0.05));
        
        // Advanced parameters
        PARAMETER_RANGES.put("eliteSize", new ParameterRange(1, 20, 5));
        PARAMETER_RANGES.put("diversityThreshold", new ParameterRange(0.01, 0.5, 0.1));
        PARAMETER_RANGES.put("stagnationLimit", new ParameterRange(10, 100, 30));
        PARAMETER_RANGES.put("localSearchProbability", new ParameterRange(0.0, 1.0, 0.3));
        PARAMETER_RANGES.put("maxLocalSearchIterations", new ParameterRange(5, 50, 20));
    }
    
    /**
     * Default constructor with research-optimized default values
     */
    public HippopotamusParameters() {
        setDefaultValues();
    }
    
    /**
     * Constructor with basic parameters
     * 
     * @param populationSize Size of hippopotamus population
     * @param maxIterations Maximum number of iterations
     * @param convergenceThreshold Convergence threshold for termination
     */
    public HippopotamusParameters(int populationSize, int maxIterations, double convergenceThreshold) {
        setDefaultValues();
        this.populationSize = populationSize;
        this.maxIterations = maxIterations;
        this.convergenceThreshold = convergenceThreshold;
        validateParameters();
    }
    
    /**
     * Copy constructor for parameter cloning
     * 
     * @param other Parameters to copy
     */
    public HippopotamusParameters(HippopotamusParameters other) {
        copyFrom(other);
    }
    
    /**
     * Set default parameter values optimized for research
     */
    private void setDefaultValues() {
        // Core parameters
        this.populationSize = 50;
        this.maxIterations = 200;
        this.convergenceThreshold = 0.001;
        this.inertiaWeight = 0.5;
        this.socialFactor = 2.0;
        this.cognitiveFactor = 2.0;
        this.mutationRate = 0.1;
        this.explorationFactor = 0.7;
        this.exploitationFactor = 0.3;
        this.adaptiveParameters = true;
        
        // Multi-objective weights (normalized to sum to 1.0)
        this.resourceUtilizationWeight = 0.3;
        this.powerConsumptionWeight = 0.25;
        this.slaViolationWeight = 0.25;
        this.loadBalancingWeight = 0.15;
        this.migrationCostWeight = 0.05;
        
        // Advanced parameters
        this.eliteSize = 5;
        this.diversityThreshold = 0.1;
        this.stagnationLimit = 30;
        this.enableLocalSearch = true;
        this.localSearchProbability = 0.3;
        this.maxLocalSearchIterations = 20;
        
        // Research parameters
        this.randomSeed = System.currentTimeMillis();
        this.trackConvergence = true;
        this.trackDiversity = true;
        this.detailedLogging = true;
        this.statisticalRuns = 30;
        this.convergenceWindow = 10;
        
        // Initialize objective weights
        this.objectiveWeights = new ObjectiveWeights(
            this.resourceUtilizationWeight,
            this.powerConsumptionWeight,
            this.slaViolationWeight,
            this.loadBalancingWeight,
            this.migrationCostWeight
        );
    }
    
    /**
     * Copy parameters from another instance
     * 
     * @param other Parameters to copy from
     */
    public void copyFrom(HippopotamusParameters other) {
        this.populationSize = other.populationSize;
        this.maxIterations = other.maxIterations;
        this.convergenceThreshold = other.convergenceThreshold;
        this.inertiaWeight = other.inertiaWeight;
        this.socialFactor = other.socialFactor;
        this.cognitiveFactor = other.cognitiveFactor;
        this.mutationRate = other.mutationRate;
        this.explorationFactor = other.explorationFactor;
        this.exploitationFactor = other.exploitationFactor;
        this.adaptiveParameters = other.adaptiveParameters;
        
        this.resourceUtilizationWeight = other.resourceUtilizationWeight;
        this.powerConsumptionWeight = other.powerConsumptionWeight;
        this.slaViolationWeight = other.slaViolationWeight;
        this.loadBalancingWeight = other.loadBalancingWeight;
        this.migrationCostWeight = other.migrationCostWeight;
        
        this.eliteSize = other.eliteSize;
        this.diversityThreshold = other.diversityThreshold;
        this.stagnationLimit = other.stagnationLimit;
        this.enableLocalSearch = other.enableLocalSearch;
        this.localSearchProbability = other.localSearchProbability;
        this.maxLocalSearchIterations = other.maxLocalSearchIterations;
        
        this.randomSeed = other.randomSeed;
        this.trackConvergence = other.trackConvergence;
        this.trackDiversity = other.trackDiversity;
        this.detailedLogging = other.detailedLogging;
        this.statisticalRuns = other.statisticalRuns;
        this.convergenceWindow = other.convergenceWindow;
        
        // Copy objective weights
        this.objectiveWeights = other.objectiveWeights != null ? 
            new ObjectiveWeights(other.objectiveWeights.toMap()) : null;
    }
    
    /**
     * Validate all parameters and throw exception if invalid
     * 
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public void validateParameters() {
        validateParameter("populationSize", populationSize);
        validateParameter("maxIterations", maxIterations);
        validateParameter("convergenceThreshold", convergenceThreshold);
        validateParameter("inertiaWeight", inertiaWeight);
        validateParameter("socialFactor", socialFactor);
        validateParameter("cognitiveFactor", cognitiveFactor);
        validateParameter("mutationRate", mutationRate);
        validateParameter("explorationFactor", explorationFactor);
        validateParameter("exploitationFactor", exploitationFactor);
        
        validateParameter("eliteSize", eliteSize);
        validateParameter("diversityThreshold", diversityThreshold);
        validateParameter("stagnationLimit", stagnationLimit);
        validateParameter("localSearchProbability", localSearchProbability);
        validateParameter("maxLocalSearchIterations", maxLocalSearchIterations);
        
        // Validate elite size doesn't exceed population size
        if (eliteSize > populationSize) {
            throw new IllegalArgumentException("Elite size cannot exceed population size");
        }
        
        // Validate weight normalization
        validateWeightNormalization();
    }
    
    /**
     * Validate individual parameter against its range
     * 
     * @param parameterName Name of the parameter
     * @param value Value to validate
     */
    private void validateParameter(String parameterName, double value) {
        ParameterRange range = PARAMETER_RANGES.get(parameterName);
        if (range != null && !range.isValid(value)) {
            throw new IllegalArgumentException(
                String.format("Parameter %s = %f is outside valid range [%f, %f]",
                    parameterName, value, range.getMin(), range.getMax()));
        }
    }
    
    /**
     * Validate that multi-objective weights sum to 1.0
     */
    private void validateWeightNormalization() {
        double totalWeight = resourceUtilizationWeight + powerConsumptionWeight + 
                           slaViolationWeight + loadBalancingWeight + migrationCostWeight;
        
        if (Math.abs(totalWeight - 1.0) > 0.001) {
            throw new IllegalArgumentException(
                String.format("Multi-objective weights must sum to 1.0, current sum: %f", totalWeight));
        }
    }
    
    /**
     * Normalize multi-objective weights to sum to 1.0
     */
    public void normalizeWeights() {
        double totalWeight = resourceUtilizationWeight + powerConsumptionWeight + 
                           slaViolationWeight + loadBalancingWeight + migrationCostWeight;
        
        if (totalWeight > 0) {
            resourceUtilizationWeight /= totalWeight;
            powerConsumptionWeight /= totalWeight;
            slaViolationWeight /= totalWeight;
            loadBalancingWeight /= totalWeight;
            migrationCostWeight /= totalWeight;
        }
    }
    
    /**
     * Generate parameter combinations for sensitivity analysis
     * 
     * @param parameterName Name of parameter to vary
     * @param numLevels Number of levels to test
     * @return List of parameter sets for sensitivity analysis
     */
    public List<HippopotamusParameters> generateSensitivityParameterSets(String parameterName, int numLevels) {
        List<HippopotamusParameters> parameterSets = new ArrayList<>();
        ParameterRange range = PARAMETER_RANGES.get(parameterName);
        
        if (range == null) {
            throw new IllegalArgumentException("Unknown parameter: " + parameterName);
        }
        
        double step = (range.getMax() - range.getMin()) / (numLevels - 1);
        
        for (int i = 0; i < numLevels; i++) {
            double value = range.getMin() + i * step;
            HippopotamusParameters params = new HippopotamusParameters(this);
            setParameterValue(params, parameterName, value);
            parameterSets.add(params);
        }
        
        return parameterSets;
    }
    
    /**
     * Set parameter value by name using reflection-like approach
     * 
     * @param params Parameter instance to modify
     * @param parameterName Name of parameter
     * @param value New value
     */
    private void setParameterValue(HippopotamusParameters params, String parameterName, double value) {
        switch (parameterName) {
            case "populationSize":
                params.populationSize = (int) value;
                break;
            case "maxIterations":
                params.maxIterations = (int) value;
                break;
            case "convergenceThreshold":
                params.convergenceThreshold = value;
                break;
            case "inertiaWeight":
                params.inertiaWeight = value;
                break;
            case "socialFactor":
                params.socialFactor = value;
                break;
            case "cognitiveFactor":
                params.cognitiveFactor = value;
                break;
            case "mutationRate":
                params.mutationRate = value;
                break;
            case "explorationFactor":
                params.explorationFactor = value;
                break;
            case "exploitationFactor":
                params.exploitationFactor = value;
                break;
            case "resourceUtilizationWeight":
                params.resourceUtilizationWeight = value;
                params.normalizeWeights();
                break;
            case "powerConsumptionWeight":
                params.powerConsumptionWeight = value;
                params.normalizeWeights();
                break;
            case "slaViolationWeight":
                params.slaViolationWeight = value;
                params.normalizeWeights();
                break;
            case "loadBalancingWeight":
                params.loadBalancingWeight = value;
                params.normalizeWeights();
                break;
            case "migrationCostWeight":
                params.migrationCostWeight = value;
                params.normalizeWeights();
                break;
            case "eliteSize":
                params.eliteSize = (int) value;
                break;
            case "diversityThreshold":
                params.diversityThreshold = value;
                break;
            case "stagnationLimit":
                params.stagnationLimit = (int) value;
                break;
            case "localSearchProbability":
                params.localSearchProbability = value;
                break;
            case "maxLocalSearchIterations":
                params.maxLocalSearchIterations = (int) value;
                break;
            case "convergenceWindow":
                params.convergenceWindow = (int) value;
                break;
            default:
                throw new IllegalArgumentException("Unknown parameter: " + parameterName);
        }
    }
    
    /**
     * Get parameter range for sensitivity analysis
     * 
     * @param parameterName Name of parameter
     * @return Parameter range
     */
    public static ParameterRange getParameterRange(String parameterName) {
        return PARAMETER_RANGES.get(parameterName);
    }
    
    /**
     * Get all parameter names available for sensitivity analysis
     * 
     * @return List of parameter names
     */
    public static List<String> getAvailableParameters() {
        return new ArrayList<>(PARAMETER_RANGES.keySet());
    }
    
    /**
     * Generate full factorial design for multiple parameters
     * 
     * @param parameters Map of parameter names to their test values
     * @return List of parameter combinations
     */
    public List<HippopotamusParameters> generateFactorialDesign(Map<String, List<Double>> parameters) {
        List<HippopotamusParameters> combinations = new ArrayList<>();
        generateFactorialRecursive(parameters, new ArrayList<>(parameters.keySet()), 
                                 new HashMap<>(), 0, combinations);
        return combinations;
    }
    
    /**
     * Recursive helper for factorial design generation
     */
    private void generateFactorialRecursive(Map<String, List<Double>> parameters, 
                                          List<String> paramNames, 
                                          Map<String, Double> currentCombination, 
                                          int paramIndex, 
                                          List<HippopotamusParameters> combinations) {
        if (paramIndex >= paramNames.size()) {
            HippopotamusParameters params = new HippopotamusParameters(this);
            for (Map.Entry<String, Double> entry : currentCombination.entrySet()) {
                setParameterValue(params, entry.getKey(), entry.getValue());
            }
            combinations.add(params);
            return;
        }
        
        String paramName = paramNames.get(paramIndex);
        List<Double> values = parameters.get(paramName);
        
        for (Double value : values) {
            currentCombination.put(paramName, value);
            generateFactorialRecursive(parameters, paramNames, currentCombination, 
                                     paramIndex + 1, combinations);
            currentCombination.remove(paramName);
        }
    }
    
    // Getter and Setter methods
    
    public int getPopulationSize() { return populationSize; }
    public void setPopulationSize(int populationSize) { 
        this.populationSize = populationSize; 
        validateParameter("populationSize", populationSize);
    }
    
    public int getMaxIterations() { return maxIterations; }
    public void setMaxIterations(int maxIterations) { 
        this.maxIterations = maxIterations;
        validateParameter("maxIterations", maxIterations);
    }
    
    public double getConvergenceThreshold() { return convergenceThreshold; }
    public void setConvergenceThreshold(double convergenceThreshold) { 
        this.convergenceThreshold = convergenceThreshold;
        validateParameter("convergenceThreshold", convergenceThreshold);
    }
    
    public double getInertiaWeight() { return inertiaWeight; }
    public void setInertiaWeight(double inertiaWeight) { 
        this.inertiaWeight = inertiaWeight;
        validateParameter("inertiaWeight", inertiaWeight);
    }
    
    public double getSocialFactor() { return socialFactor; }
    public void setSocialFactor(double socialFactor) { 
        this.socialFactor = socialFactor;
        validateParameter("socialFactor", socialFactor);
    }
    
    public double getCognitiveFactor() { return cognitiveFactor; }
    public void setCognitiveFactor(double cognitiveFactor) { 
        this.cognitiveFactor = cognitiveFactor;
        validateParameter("cognitiveFactor", cognitiveFactor);
    }
    
    public double getMutationRate() { return mutationRate; }
    public void setMutationRate(double mutationRate) { 
        this.mutationRate = mutationRate;
        validateParameter("mutationRate", mutationRate);
    }
    
    public double getExplorationFactor() { return explorationFactor; }
    public void setExplorationFactor(double explorationFactor) { 
        this.explorationFactor = explorationFactor;
        validateParameter("explorationFactor", explorationFactor);
    }
    
    public double getExploitationFactor() { return exploitationFactor; }
    public void setExploitationFactor(double exploitationFactor) { 
        this.exploitationFactor = exploitationFactor;
        validateParameter("exploitationFactor", exploitationFactor);
    }
    
    public boolean isAdaptiveParameters() { return adaptiveParameters; }
    public void setAdaptiveParameters(boolean adaptiveParameters) { 
        this.adaptiveParameters = adaptiveParameters; 
    }
    
    // Multi-objective weight getters and setters
    public double getResourceUtilizationWeight() { return resourceUtilizationWeight; }
    public void setResourceUtilizationWeight(double weight) { 
        this.resourceUtilizationWeight = weight; 
        normalizeWeights();
    }
    
    public double getPowerConsumptionWeight() { return powerConsumptionWeight; }
    public void setPowerConsumptionWeight(double weight) { 
        this.powerConsumptionWeight = weight; 
        normalizeWeights();
    }
    
    public double getSlaViolationWeight() { return slaViolationWeight; }
    public void setSlaViolationWeight(double weight) { 
        this.slaViolationWeight = weight; 
        normalizeWeights();
    }
    
    public double getLoadBalancingWeight() { return loadBalancingWeight; }
    public void setLoadBalancingWeight(double weight) { 
        this.loadBalancingWeight = weight; 
        normalizeWeights();
    }
    
    public double getMigrationCostWeight() { return migrationCostWeight; }
    public void setMigrationCostWeight(double weight) { 
        this.migrationCostWeight = weight; 
        normalizeWeights();
    }
    
    // Advanced parameter getters and setters
    public int getEliteSize() { return eliteSize; }
    public void setEliteSize(int eliteSize) { 
        this.eliteSize = eliteSize;
        validateParameter("eliteSize", eliteSize);
    }
    
    public double getDiversityThreshold() { return diversityThreshold; }
    public void setDiversityThreshold(double diversityThreshold) { 
        this.diversityThreshold = diversityThreshold;
        validateParameter("diversityThreshold", diversityThreshold);
    }
    
    public int getStagnationLimit() { return stagnationLimit; }
    public void setStagnationLimit(int stagnationLimit) { 
        this.stagnationLimit = stagnationLimit;
        validateParameter("stagnationLimit", stagnationLimit);
    }
    
    public boolean isEnableLocalSearch() { return enableLocalSearch; }
    public void setEnableLocalSearch(boolean enableLocalSearch) { 
        this.enableLocalSearch = enableLocalSearch; 
    }
    
    public double getLocalSearchProbability() { return localSearchProbability; }
    public void setLocalSearchProbability(double localSearchProbability) { 
        this.localSearchProbability = localSearchProbability;
        validateParameter("localSearchProbability", localSearchProbability);
    }
    
    public int getMaxLocalSearchIterations() { return maxLocalSearchIterations; }
    public void setMaxLocalSearchIterations(int maxLocalSearchIterations) { 
        this.maxLocalSearchIterations = maxLocalSearchIterations;
        validateParameter("maxLocalSearchIterations", maxLocalSearchIterations);
    }
    
    // Research parameter getters and setters
    public long getRandomSeed() { return randomSeed; }
    public void setRandomSeed(long randomSeed) { this.randomSeed = randomSeed; }
    
    public boolean isTrackConvergence() { return trackConvergence; }
    public void setTrackConvergence(boolean trackConvergence) { 
        this.trackConvergence = trackConvergence; 
    }
    
    public boolean isTrackDiversity() { return trackDiversity; }
    public void setTrackDiversity(boolean trackDiversity) { 
        this.trackDiversity = trackDiversity; 
    }
    
    public boolean isDetailedLogging() { return detailedLogging; }
    public void setDetailedLogging(boolean detailedLogging) { 
        this.detailedLogging = detailedLogging; 
    }
    
    public int getStatisticalRuns() { return statisticalRuns; }
    public void setStatisticalRuns(int statisticalRuns) { 
        this.statisticalRuns = Math.max(1, statisticalRuns); 
    }
    
    public int getConvergenceWindow() { return convergenceWindow; }
    public void setConvergenceWindow(int convergenceWindow) { 
        this.convergenceWindow = convergenceWindow; 
    }
    
    // Objective weights methods
    public ObjectiveWeights getObjectiveWeights() { 
        if (objectiveWeights == null) {
            objectiveWeights = new ObjectiveWeights(
                resourceUtilizationWeight, powerConsumptionWeight, 
                slaViolationWeight, loadBalancingWeight, migrationCostWeight
            );
        }
        return objectiveWeights; 
    }
    
    public void setObjectiveWeights(ObjectiveWeights objectiveWeights) { 
        this.objectiveWeights = objectiveWeights;
        if (objectiveWeights != null) {
            // Sync individual weights with the ObjectiveWeights
            this.resourceUtilizationWeight = objectiveWeights.getResourceWeight();
            this.powerConsumptionWeight = objectiveWeights.getPowerWeight();
            this.slaViolationWeight = objectiveWeights.getSlaWeight();
            this.loadBalancingWeight = objectiveWeights.getLoadBalanceWeight();
            this.migrationCostWeight = objectiveWeights.getCommunicationWeight();
        }
    }
    
    public void setObjectiveWeights(Map<String, Double> weights) {
        this.objectiveWeights = new ObjectiveWeights(weights);
        setObjectiveWeights(this.objectiveWeights);
    }
    
    @Override
    public String toString() {
        return String.format(
            "HippopotamusParameters{" +
            "populationSize=%d, maxIterations=%d, convergenceThreshold=%f, " +
            "inertiaWeight=%f, socialFactor=%f, cognitiveFactor=%f, " +
            "mutationRate=%f, explorationFactor=%f, exploitationFactor=%f, " +
            "resourceUtilizationWeight=%f, powerConsumptionWeight=%f, " +
            "slaViolationWeight=%f, loadBalancingWeight=%f, migrationCostWeight=%f, " +
            "eliteSize=%d, diversityThreshold=%f, stagnationLimit=%d, " +
            "enableLocalSearch=%b, adaptiveParameters=%b, randomSeed=%d}",
            populationSize, maxIterations, convergenceThreshold,
            inertiaWeight, socialFactor, cognitiveFactor,
            mutationRate, explorationFactor, exploitationFactor,
            resourceUtilizationWeight, powerConsumptionWeight,
            slaViolationWeight, loadBalancingWeight, migrationCostWeight,
            eliteSize, diversityThreshold, stagnationLimit,
            enableLocalSearch, adaptiveParameters, randomSeed
        );
    }
    
    /**
     * Helper class to define parameter ranges for sensitivity analysis
     */
    public static class ParameterRange {
        private final double min;
        private final double max;
        private final double defaultValue;
        
        public ParameterRange(double min, double max, double defaultValue) {
            this.min = min;
            this.max = max;
            this.defaultValue = defaultValue;
        }
        
        public boolean isValid(double value) {
            return value >= min && value <= max;
        }
        
        public double getMin() { return min; }
        public double getMax() { return max; }
        public double getDefaultValue() { return defaultValue; }
        
        @Override
        public String toString() {
            return String.format("Range[%f, %f] default=%f", min, max, defaultValue);
        }
    }
    
    /**
     * Validate all parameters using existing validateParameters method
     * 
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public void validate() {
        validateParameters();
    }
    
    /**
     * Get current iteration for algorithm tracking
     * 
     * @return current iteration
     */
    public int getCurrentIteration() {
        return currentIteration;
    }
    
    /**
     * Set current iteration for algorithm tracking
     * 
     * @param currentIteration current iteration value
     */
    public void setCurrentIteration(int currentIteration) {
        this.currentIteration = currentIteration;
    }
    
    // Current iteration tracking for algorithm state
    private int currentIteration = 0;
}