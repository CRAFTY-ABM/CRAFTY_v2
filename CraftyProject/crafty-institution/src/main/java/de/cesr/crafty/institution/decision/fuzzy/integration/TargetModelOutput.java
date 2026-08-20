package de.cesr.crafty.institution.decision.fuzzy.integration;

import java.util.ArrayList;
import java.util.HashMap;
/*
 * @author yongchao Zeng
 *
 */
public record TargetModelOutput(HashMap<String, ArrayList<Double>> modelOutput,
                HashMap<String, HashMap<String, Double>> estimatedQuantities, HashMap<String, Double> budgets) {

}
