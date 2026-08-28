package de.cesr.crafty.institution.decision.llm;

import java.util.Map;

public record LlmOutputSnapshot(String institutionName, String scopeName, int year, String prompt, String rawOutput,
		Map<String, Double> decisions, Map<String, Double> effectiveValues) {
	public LlmOutputSnapshot {
		prompt = prompt == null ? "" : prompt;
		rawOutput = rawOutput == null ? "" : rawOutput;
		decisions = Map.copyOf(decisions);
		effectiveValues = Map.copyOf(effectiveValues);
	}
}
