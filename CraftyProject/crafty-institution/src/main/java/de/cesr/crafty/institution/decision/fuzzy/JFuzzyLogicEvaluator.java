package de.cesr.crafty.institution.decision.fuzzy;

import java.util.Map;

import net.sourceforge.jFuzzyLogic.FIS;
import net.sourceforge.jFuzzyLogic.FunctionBlock;

public final class JFuzzyLogicEvaluator implements FuzzyEvaluator {
	private final FIS fis;

	public JFuzzyLogicEvaluator(FIS fis) {
		this.fis = java.util.Objects.requireNonNull(fis, "fis");
	}

	@Override
	public double evaluate(String functionBlock, Map<String, Double> inputs) {
		FunctionBlock block = fis.getFunctionBlock(functionBlock);
		if (block == null) {
			throw new IllegalStateException("Fuzzy block '" + functionBlock + "' not found in FCL file");
		}
		inputs.forEach((name, value) -> {
			if (block.getVariable(name) != null) {
				block.setVariable(name, value);
			}
		});
		block.evaluate();
		var intervention = block.getVariable("intervention");
		if (intervention == null) {
			throw new IllegalStateException(
					"Variable 'intervention' not found in fuzzy block '" + functionBlock + "'");
		}
		return intervention.getValue();
	}
}
