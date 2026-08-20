package de.cesr.crafty.institution.orchestration;

import java.util.Map;
import java.util.Objects;

public record InstitutionDecision(Map<String, Double> policyValues, DecisionMode mode, boolean accepted,
		String rawOutput) {
	public InstitutionDecision {
		policyValues = Map.copyOf(policyValues);
		Objects.requireNonNull(mode, "mode");
		rawOutput = rawOutput == null ? "" : rawOutput;
	}

	public static InstitutionDecision noDecision(DecisionMode mode) {
		return new InstitutionDecision(Map.of(), mode, false, "");
	}
}
