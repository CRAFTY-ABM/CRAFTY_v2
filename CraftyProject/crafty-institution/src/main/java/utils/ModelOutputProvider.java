package utils;

import java.util.ArrayList;
import java.util.HashMap;
/*
 * @author yongchao Zeng
 *
 */
public interface ModelOutputProvider {

    public TargetModelOutput step(InstitutionOutput institutionOutput);

    HashMap<String, ArrayList<Double>> prepModelOutput();// output of crafty

    HashMap<String, HashMap<String, Double>> prepEstimatedQuantities ();// this for units

    HashMap<String, Double> prepBudget();// 

    default TargetModelOutput getOutput() {
        return new TargetModelOutput(
            prepModelOutput(),
            prepEstimatedQuantities(),
            prepBudget()
        );
    }
    
}