package de.cesr.crafty.institution.orchestration;

import java.util.Map;

public record OrchestrationResult(boolean decisionRecorded, Map<String, Double> decisionValues,
		Map<String, Double> effectiveValues, String rawOutput) {
	public OrchestrationResult {
		decisionValues = Map.copyOf(decisionValues);
		effectiveValues = Map.copyOf(effectiveValues);
		rawOutput = rawOutput == null ? "" : rawOutput;
	}
}
