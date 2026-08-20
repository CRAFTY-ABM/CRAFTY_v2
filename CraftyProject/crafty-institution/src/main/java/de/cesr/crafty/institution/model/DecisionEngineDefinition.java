package de.cesr.crafty.institution.model;

import de.cesr.crafty.institution.decision.fuzzy.FuzzyEngineConfig;
import de.cesr.crafty.institution.decision.llm.LlmEngineConfig;

public record DecisionEngineDefinition(DecisionEngineType type, FuzzyEngineConfig fuzzy, LlmEngineConfig llm) {
	public DecisionEngineDefinition {
		if (type == null) {
			throw new IllegalArgumentException("Decision engine type is required");
		}
		if (type == DecisionEngineType.FUZZY && (fuzzy == null || llm != null)) {
			throw new IllegalArgumentException("Fuzzy engine requires only fuzzy configuration");
		}
		if (type == DecisionEngineType.LLM && (llm == null || fuzzy != null)) {
			throw new IllegalArgumentException("LLM engine requires only LLM configuration");
		}
		if (type == DecisionEngineType.MANUAL && (fuzzy != null || llm != null)) {
			throw new IllegalArgumentException("Manual engine does not accept fuzzy or LLM configuration");
		}
	}
}
