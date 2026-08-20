package de.cesr.crafty.institution.decision.llm;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.institution.orchestration.DecisionMode;
import de.cesr.crafty.institution.orchestration.InstitutionContext;
import de.cesr.crafty.institution.orchestration.InstitutionDecision;
import de.cesr.crafty.institution.orchestration.InstitutionDecisionEngine;
/** Strict LLM call/parse/retry behavior behind the shared engine contract. */
public final class LlmDecisionEngine implements InstitutionDecisionEngine {
	private static final CustomLogger LOGGER = new CustomLogger(LlmDecisionEngine.class);
	private final Supplier<LlmClient> clientSupplier;
	private final Set<String> expectedPolicies;
	private final int retryCount;

	public LlmDecisionEngine(Supplier<LlmClient> clientSupplier, Set<String> expectedPolicies, int retryCount) {
		this.clientSupplier = java.util.Objects.requireNonNull(clientSupplier, "clientSupplier");
		this.expectedPolicies = Set.copyOf(expectedPolicies);
		if (expectedPolicies.isEmpty()) {
			throw new IllegalArgumentException("LLM engine requires at least one policy");
		}
		if (retryCount < 1) {
			throw new IllegalArgumentException("LLM retry count must be at least one");
		}
		this.retryCount = retryCount;
	}

	@Override
	public InstitutionDecision decide(InstitutionContext context) {
		if (!context.decisionDue()) {
			return InstitutionDecision.noDecision(DecisionMode.CHANGE);
		}
		String originalPrompt = context.engineInput();
		if (originalPrompt.isBlank()) {
			LOGGER.error("LLM input is blank: institution=" + context.institutionId() + " year=" + context.year());
			return InstitutionDecision.noDecision(DecisionMode.CHANGE);
		}

		LlmClient client = clientSupplier.get();
		String prompt = originalPrompt;
		String output = "";
		for (int attempt = 1; attempt <= retryCount; attempt++) {
			try {
				output = client.askLLM(prompt);
			} catch (Exception exception) {
				LOGGER.error("LLM call failed. institution=" + context.institutionId() + " year=" + context.year()
						+ " attempt=" + attempt, exception);
				output = "";
			}

			Map<String, Double> values = output.isEmpty() ? null
					: LlmPolicyParser.extractPolicyDecisionsOrNull(output);
			if (isValid(values)) {
				return new InstitutionDecision(values, DecisionMode.CHANGE, true, output);
			}
			LOGGER.warn("Invalid or incomplete LLM output. institution=" + context.institutionId() + " year="
					+ context.year() + " attempt=" + attempt + " expected=" + expectedPolicies + " got="
					+ (values == null ? "null" : values.keySet()));
			if (attempt == 1 && !output.isEmpty()) {
				prompt = LlmPolicyParser.onlyWhenUnparseableOutput(output, expectedPolicies);
			} else {
				prompt = LlmPolicyParser.promptModifierToForceFormat(originalPrompt, expectedPolicies);
			}
		}
		LOGGER.error("All LLM attempts failed; using zero change. institution=" + context.institutionId()
				+ " year=" + context.year());
		return new InstitutionDecision(Map.of(), DecisionMode.CHANGE, false, output);
	}

	private boolean isValid(Map<String, Double> values) {
		return values != null && values.keySet().equals(expectedPolicies)
				&& values.values().stream().allMatch(value -> value != null && Double.isFinite(value));
	}
}
