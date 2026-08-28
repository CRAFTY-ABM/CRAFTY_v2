package de.cesr.crafty.institution.decision.llm;

@FunctionalInterface
public interface LlmOutputWriter {
	void write(LlmOutputSnapshot snapshot);

	static LlmOutputWriter noOp() {
		return ignored -> { };
	}
}
