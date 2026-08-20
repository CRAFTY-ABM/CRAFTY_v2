package de.cesr.crafty.institution.decision.fuzzy;

import java.util.Map;

@FunctionalInterface
public interface FuzzyEvaluator {
	double evaluate(String functionBlock, Map<String, Double> inputs);
}
