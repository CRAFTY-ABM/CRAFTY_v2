package de.cesr.crafty.institution.decision.fuzzy;

/** Target settings used exclusively by the fuzzy gap calculation. */
public record FuzzyTargetSettings(double desiredValue) {
	public FuzzyTargetSettings {
		if (!Double.isFinite(desiredValue)) {
			throw new IllegalArgumentException("Desired target value must be finite");
		}
	}
}
