package org.cloudbus.cloudsim.experiment;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Define parameter space for sensitivity analysis
 * Supporting research objectives: comprehensive parameter exploration and optimization
 * @author Puneet chandna
 * @version 1.0.0
 */
public class ParameterSpace {
    
    private Map<String, ParameterDefinition> parameters;
    private List<Map<String, Object>> generatedCombinations;
    private SamplingStrategy samplingStrategy;
    
    public enum SamplingStrategy {
        FULL_FACTORIAL,      // All combinations
        LATIN_HYPERCUBE,     // Latin hypercube sampling
        RANDOM,              // Random sampling
        GRID,                // Regular grid sampling
        ADAPTIVE             // Adaptive sampling based on results
    }
    
    public enum ParameterType {
        INTEGER,
        DOUBLE,
        BOOLEAN,
        CATEGORICAL
    }
    
    // Parameter definition class
    public static class ParameterDefinition {
        private String name;
        private ParameterType type;
        private Object minValue;
        private Object maxValue;
        private Object defaultValue;
        private List<Object> possibleValues;
        private double stepSize;
        private boolean isLogarithmic;
        
        public ParameterDefinition(String name, ParameterType type) {
            this.name = name;
            this.type = type;
            this.isLogarithmic = false;
        }
        
        // Builder pattern methods
        public ParameterDefinition withRange(Object min, Object max) {
            this.minValue = min;
            this.maxValue = max;
            return this;
        }
        
        public ParameterDefinition withDefault(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }
        
        public ParameterDefinition withValues(List<Object> values) {
            this.possibleValues = values;
            return this;
        }
        
        public ParameterDefinition withStep(double step) {
            this.stepSize = step;
            return this;
        }
        
        public ParameterDefinition logarithmic() {
            this.isLogarithmic = true;
            return this;
        }
        
        // Generate values based on definition
        public List<Object> generateValues(int numSamples) {
            List<Object> values = new ArrayList<>();
            
            switch (type) {
                case INTEGER:
                    generateIntegerValues(values, numSamples);
                    break;
                case DOUBLE:
                    generateDoubleValues(values, numSamples);
                    break;
                case BOOLEAN:
                    values.add(true);
                    values.add(false);
                    break;
                case CATEGORICAL:
                    values.addAll(possibleValues);
                    break;
            }
            
            return values;
        }
        
        private void generateIntegerValues(List<Object> values, int numSamples) {
            int min = (Integer) minValue;
            int max = (Integer) maxValue;
            
            if (stepSize > 0) {
                for (int i = min; i <= max; i += (int) stepSize) {
                    values.add(i);
                }
            } else {
                // Generate evenly spaced values
                double step = (double)(max - min) / (numSamples - 1);
                for (int i = 0; i < numSamples; i++) {
                    values.add(min + (int)(i * step));
                }
            }
        }
        
        private void generateDoubleValues(List<Object> values, int numSamples) {
            double min = ((Number) minValue).doubleValue();
            double max = ((Number) maxValue).doubleValue();
            
            if (isLogarithmic) {
                double logMin = Math.log10(min);
                double logMax = Math.log10(max);
                double logStep = (logMax - logMin) / (numSamples - 1);
                
                for (int i = 0; i < numSamples; i++) {
                    values.add(Math.pow(10, logMin + i * logStep));
                }
            } else if (stepSize > 0) {
                for (double v = min; v <= max; v += stepSize) {
                    values.add(v);
                }
            } else {
                double step = (max - min) / (numSamples - 1);
                for (int i = 0; i < numSamples; i++) {
                    values.add(min + i * step);
                }
            }
        }
        
        // Getters
        public String getName() { return name; }
        public ParameterType getType() { return type; }
        public Object getMinValue() { return minValue; }
        public Object getMaxValue() { return maxValue; }
        public Object getDefaultValue() { return defaultValue; }
        public List<Object> getPossibleValues() { return possibleValues; }
        public double getStepSize() { return stepSize; }
        public boolean isLogarithmic() { return isLogarithmic; }
    }
    
    // Constructor
    public ParameterSpace() {
        this.parameters = new HashMap<>();
        this.generatedCombinations = new ArrayList<>();
        this.samplingStrategy = SamplingStrategy.FULL_FACTORIAL;
    }
    
    // Constructor with parameter map
    public ParameterSpace(Map<String, Object> parameterMap) {
        this();
        if (parameterMap != null) {
            for (Map.Entry<String, Object> entry : parameterMap.entrySet()) {
                String name = entry.getKey();
                Object value = entry.getValue();
                
                if (value instanceof Integer) {
                    addIntegerParameter(name, (Integer) value, (Integer) value, (Integer) value);
                } else if (value instanceof Double) {
                    addDoubleParameter(name, (Double) value, (Double) value, (Double) value);
                } else if (value instanceof Boolean) {
                    addCategoricalParameter(name, Arrays.asList(true, false), value);
                } else {
                    addCategoricalParameter(name, Arrays.asList(value), value);
                }
            }
        }
    }
    
    // Parameter management methods
    public void addParameter(ParameterDefinition parameter) {
        parameters.put(parameter.getName(), parameter);
    }
    
    public void addIntegerParameter(String name, int min, int max, int defaultValue) {
        ParameterDefinition param = new ParameterDefinition(name, ParameterType.INTEGER)
            .withRange(min, max)
            .withDefault(defaultValue);
        addParameter(param);
    }
    
    public void addDoubleParameter(String name, double min, double max, double defaultValue) {
        ParameterDefinition param = new ParameterDefinition(name, ParameterType.DOUBLE)
            .withRange(min, max)
            .withDefault(defaultValue);
        addParameter(param);
    }
    
    public void addCategoricalParameter(String name, List<Object> values, Object defaultValue) {
        ParameterDefinition param = new ParameterDefinition(name, ParameterType.CATEGORICAL)
            .withValues(values)
            .withDefault(defaultValue);
        addParameter(param);
    }
    
    // Parameter combination generation
    public List<Map<String, Object>> generateParameterCombinations() {
        return generateParameterCombinations(10); // Default samples per parameter
    }
    
    public List<Map<String, Object>> generateParameterCombinations(int samplesPerParameter) {
        switch (samplingStrategy) {
            case FULL_FACTORIAL:
                return generateFullFactorial(samplesPerParameter);
            case LATIN_HYPERCUBE:
                return generateLatinHypercube(samplesPerParameter);
            case RANDOM:
                return generateRandom(samplesPerParameter * parameters.size());
            case GRID:
                return generateGrid(samplesPerParameter);
            case ADAPTIVE:
                return generateAdaptive(samplesPerParameter);
            default:
                return generateFullFactorial(samplesPerParameter);
        }
    }
    
    private List<Map<String, Object>> generateFullFactorial(int samplesPerParameter) {
        List<Map<String, Object>> combinations = new ArrayList<>();
        
        // Generate values for each parameter
        Map<String, List<Object>> parameterValues = new HashMap<>();
        for (ParameterDefinition param : parameters.values()) {
            parameterValues.put(param.getName(), param.generateValues(samplesPerParameter));
        }
        
        // Generate all combinations
        generateCombinationsRecursive(
            new ArrayList<>(parameters.keySet()),
            0,
            new HashMap<>(),
            parameterValues,
            combinations
        );
        
        generatedCombinations = combinations;
        return combinations;
    }
    
    private void generateCombinationsRecursive(
            List<String> paramNames,
            int index,
            Map<String, Object> current,
            Map<String, List<Object>> parameterValues,
            List<Map<String, Object>> results) {
        
        if (index == paramNames.size()) {
            results.add(new HashMap<>(current));
            return;
        }
        
        String paramName = paramNames.get(index);
        for (Object value : parameterValues.get(paramName)) {
            current.put(paramName, value);
            generateCombinationsRecursive(paramNames, index + 1, current, 
                                        parameterValues, results);
        }
    }
    
    private List<Map<String, Object>> generateLatinHypercube(int numSamples) {
        List<Map<String, Object>> combinations = new ArrayList<>();
        Random random = new Random();
        
        // Generate Latin Hypercube samples
        Map<String, List<Object>> parameterSamples = new HashMap<>();
        
        for (ParameterDefinition param : parameters.values()) {
            List<Object> values = param.generateValues(numSamples);
            Collections.shuffle(values, random);
            parameterSamples.put(param.getName(), values);
        }
        
        // Create combinations
        for (int i = 0; i < numSamples; i++) {
            Map<String, Object> combination = new HashMap<>();
            for (String paramName : parameters.keySet()) {
                combination.put(paramName, parameterSamples.get(paramName).get(i));
            }
            combinations.add(combination);
        }
        
        generatedCombinations = combinations;
        return combinations;
    }
    
    private List<Map<String, Object>> generateRandom(int numSamples) {
        List<Map<String, Object>> combinations = new ArrayList<>();
        Random random = new Random();
        
        for (int i = 0; i < numSamples; i++) {
            Map<String, Object> combination = new HashMap<>();
            
            for (ParameterDefinition param : parameters.values()) {
                Object value = generateRandomValue(param, random);
                combination.put(param.getName(), value);
            }
            
            combinations.add(combination);
        }
        
        generatedCombinations = combinations;
        return combinations;
    }
    
    private Object generateRandomValue(ParameterDefinition param, Random random) {
        switch (param.getType()) {
            case INTEGER:
                int min = (Integer) param.getMinValue();
                int max = (Integer) param.getMaxValue();
                return min + random.nextInt(max - min + 1);
                
            case DOUBLE:
                double dMin = ((Number) param.getMinValue()).doubleValue();
                double dMax = ((Number) param.getMaxValue()).doubleValue();
                if (param.isLogarithmic()) {
                    double logMin = Math.log10(dMin);
                    double logMax = Math.log10(dMax);
                    return Math.pow(10, logMin + random.nextDouble() * (logMax - logMin));
                } else {
                    return dMin + random.nextDouble() * (dMax - dMin);
                }
                
            case BOOLEAN:
                return random.nextBoolean();
                
            case CATEGORICAL:
                List<Object> values = param.getPossibleValues();
                return values.get(random.nextInt(values.size()));
                
            default:
                return param.getDefaultValue();
        }
    }
    
    private List<Map<String, Object>> generateGrid(int gridSize) {
        // Similar to full factorial but with fixed grid size
        return generateFullFactorial(gridSize);
    }
    
    private List<Map<String, Object>> generateAdaptive(int initialSamples) {
        // Start with Latin Hypercube, can be extended for adaptive sampling
        return generateLatinHypercube(initialSamples);
    }
    
    // Validation methods
    public boolean validateParameterRanges() {
        for (ParameterDefinition param : parameters.values()) {
            if (!validateParameter(param)) {
                return false;
            }
        }
        return true;
    }
    
    private boolean validateParameter(ParameterDefinition param) {
        switch (param.getType()) {
            case INTEGER:
            case DOUBLE:
                if (param.getMinValue() == null || param.getMaxValue() == null) {
                    return false;
                }
                double min = ((Number) param.getMinValue()).doubleValue();
                double max = ((Number) param.getMaxValue()).doubleValue();
                return min <= max;
                
            case CATEGORICAL:
                return param.getPossibleValues() != null && 
                       !param.getPossibleValues().isEmpty();
                
            case BOOLEAN:
                return true;
                
            default:
                return false;
        }
    }
    
    // Get parameter sets for experiments
    public List<Map<String, Object>> getParameterSets() {
        if (generatedCombinations.isEmpty()) {
            generateParameterCombinations();
        }
        return new ArrayList<>(generatedCombinations);
    }
    
    // Get default parameter set
    public Map<String, Object> getDefaultParameterSet() {
        Map<String, Object> defaults = new HashMap<>();
        for (ParameterDefinition param : parameters.values()) {
            defaults.put(param.getName(), param.getDefaultValue());
        }
        return defaults;
    }
    
    // Statistical methods for parameter space analysis
    public Map<String, Double> calculateParameterRanges() {
        Map<String, Double> ranges = new HashMap<>();
        
        for (ParameterDefinition param : parameters.values()) {
            if (param.getType() == ParameterType.INTEGER || 
                param.getType() == ParameterType.DOUBLE) {
                double min = ((Number) param.getMinValue()).doubleValue();
                double max = ((Number) param.getMaxValue()).doubleValue();
                ranges.put(param.getName(), max - min);
            }
        }
        
        return ranges;
    }
    
    // Getters and setters
    public Map<String, ParameterDefinition> getParameters() { return parameters; }
    
    public SamplingStrategy getSamplingStrategy() { return samplingStrategy; }
    public void setSamplingStrategy(SamplingStrategy samplingStrategy) { 
        this.samplingStrategy = samplingStrategy; 
    }
    
    public int getTotalCombinations() { return generatedCombinations.size(); }
    
    public List<Map<String, Object>> generateSensitivitySets() {
        return generateParameterCombinations();
    }
    
    @Override
    public String toString() {
        return String.format("ParameterSpace[parameters=%d, strategy=%s, combinations=%d]",
            parameters.size(), samplingStrategy, generatedCombinations.size());
    }
}