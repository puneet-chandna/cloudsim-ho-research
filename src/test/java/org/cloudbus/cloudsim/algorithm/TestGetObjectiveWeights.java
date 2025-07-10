package org.cloudbus.cloudsim.algorithm;

public class TestGetObjectiveWeights {
    public static void main(String[] args) {
        // Create a HippopotamusParameters instance
        HippopotamusParameters params = new HippopotamusParameters();
        
        
        ObjectiveWeights weights = params.getObjectiveWeights();
        
        System.out.println("Success! Method exists and works.");
        System.out.println("Weights: " + weights);
    }
}
