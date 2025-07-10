import org.cloudbus.cloudsim.algorithm.HippopotamusParameters;
import org.cloudbus.cloudsim.algorithm.ObjectiveWeights;

public class TestObjectiveWeights {
    public static void main(String[] args) {
        HippopotamusParameters params = new HippopotamusParameters();
        ObjectiveWeights weights = params.getObjectiveWeights();
        System.out.println("Test successful: " + weights);
    }
}
