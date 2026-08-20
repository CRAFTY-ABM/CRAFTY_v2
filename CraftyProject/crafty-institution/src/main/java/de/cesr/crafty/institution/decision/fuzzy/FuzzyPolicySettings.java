package de.cesr.crafty.institution.decision.fuzzy;

import de.cesr.crafty.institution.model.NumericRange;

/** Per-policy configuration used exclusively by the fuzzy decision engine. */
public record FuzzyPolicySettings(String functionBlock, double stepSize, NumericRange change) {
	public FuzzyPolicySettings {
		if (functionBlock == null || functionBlock.isBlank()) {
			throw new IllegalArgumentException("Fuzzy function block cannot be blank");
		}
		functionBlock = functionBlock.trim();
		if (!Double.isFinite(stepSize) || stepSize < 0) {
			throw new IllegalArgumentException("Fuzzy step size must be finite and non-negative");
		}
		if (change == null) {
			throw new IllegalArgumentException("Fuzzy change constraint is required");
		}
	}
}
