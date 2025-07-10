public class TestObjectiveWeightsCall {
    public static void main(String[] args) {
        try {
            // Test the getObjectiveWeights method call
            org.cloudbus.cloudsim.algorithm.HippopotamusParameters params = 
                new org.cloudbus.cloudsim.algorithm.HippopotamusParameters();
            
            // This should not throw any compilation error
            org.cloudbus.cloudsim.algorithm.ObjectiveWeights weights = params.getObjectiveWeights();
            
            System.out.println("SUCCESS: getObjectiveWeights() method works correctly");
            System.out.println("Weights: " + weights);
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
