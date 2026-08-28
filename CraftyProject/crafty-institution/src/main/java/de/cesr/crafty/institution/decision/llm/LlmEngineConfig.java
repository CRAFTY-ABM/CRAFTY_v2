package de.cesr.crafty.institution.decision.llm;

import java.nio.file.Path;

/** Configuration used exclusively by the LLM decision engine. */
public record LlmEngineConfig(Path promptFile, int retryCount) {
	public LlmEngineConfig {
		if (promptFile == null) {
			throw new IllegalArgumentException("LLM prompt file cannot be null");
		}
		promptFile = promptFile.normalize();
		if (retryCount < 1) {
			throw new IllegalArgumentException("LLM retry count must be at least one");
		}
	}
}
