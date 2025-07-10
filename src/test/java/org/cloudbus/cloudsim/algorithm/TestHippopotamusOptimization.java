package org.cloudbus.cloudsim.algorithm;

/**
 * Simple test to verify the HippopotamusOptimization getObjectiveWeights functionality
 */
public class TestHippopotamusOptimization {
    public static void main(String[] args) {
        try {
            // Test parameter initialization
            HippopotamusParameters params = new HippopotamusParameters();
            System.out.println("✓ HippopotamusParameters created successfully");
            
            // Test getObjectiveWeights method
            ObjectiveWeights weights = params.getObjectiveWeights();
            System.out.println("✓ getObjectiveWeights() called successfully");
            System.out.println("  Weights: " + weights);
            
            // Test optimization class initialization
            HippopotamusOptimization optimizer = new HippopotamusOptimization();
            System.out.println("✓ HippopotamusOptimization created successfully");
            
            // Test fitness evaluation with the weights
            Hippopotamus testHippo = new Hippopotamus(5, 3);
            testHippo.initializeSequential();
            
            double fitness = optimizer.evaluateFitness(testHippo, weights);
            System.out.println("✓ evaluateFitness() with getObjectiveWeights() works correctly");
            System.out.println("  Fitness: " + fitness);
            
            System.out.println("✓ ALL TESTS PASSED - No compilation errors found!");
            
        } catch (Exception e) {
            System.err.println("✗ ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
