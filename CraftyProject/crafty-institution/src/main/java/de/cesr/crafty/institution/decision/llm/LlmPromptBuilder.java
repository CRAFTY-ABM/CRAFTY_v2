package de.cesr.crafty.institution.decision.llm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.cesr.crafty.institution.decision.llm.runtime.LlmPolicyState;
import de.cesr.crafty.institution.decision.llm.runtime.LlmTargetState;

/** Builds the structured LLM prompt from common-backed runtime state. */
public final class LlmPromptBuilder {
	public String build(String basePrompt, Map<String, LlmPolicyState> policies,
			Map<String, LlmTargetState> targets, int timeLag,
			int institutionStartYear, int simulationStartYear) {
		return (basePrompt == null ? "" : basePrompt) + "\n" + formatNotice(policies.keySet()) + "\n"
				+ indicators("EU-historical_indicators (EU_GLOBAL)", targets, false, timeLag, simulationStartYear)
				+ "\n" + indicators("historical_indicators (REGIONAL)", targets, true, timeLag,
						simulationStartYear)
				+ "\n" + policyHistory(policies, timeLag, institutionStartYear);
	}

	private static String formatNotice(java.util.Collection<String> keys) {
		return "AUTOMATION NOTICE (Java JSON parser; no human supervision):\n"
				+ "Reply with exactly one valid JSON object, or exactly the literal null.\n"
				+ "Required top-level keys: \"reasoning\", \"policy_decisions\".\n"
				+ "Do not use markdown, code fences, extra text, or quoted numeric values.\n"
				+ "\"policy_decisions\" MUST contain EXACTLY these keys: " + keys;
	}

	private static String indicators(String label, Map<String, LlmTargetState> targets, boolean paradigm, int timeLag,
			int startYear) {
		StringBuilder out = new StringBuilder();
		out.append("{\n  \"").append(label).append("\": {\n")
				.append("    \"start_year\": ").append(startYear).append(",\n")
				.append("    \"time_step_years\": ").append(timeLag).append(",\n")
				.append("    \"indicators\": {\n");
		List<String> names = new ArrayList<>(targets.keySet());
		names.sort(String::compareTo);
		for (int i = 0; i < names.size(); i++) {
			String name = names.get(i);
			var history = paradigm ? targets.get(name).getHistoryInParadigm() : targets.get(name).getHistory();
			out.append("      \"").append(jsonEscape(name)).append("\": {\"values\": {");
			int index = 0;
			boolean first = true;
			for (double value : history.values()) {
				if (index % timeLag == 0) {
					if (!first) {
						out.append(", ");
					}
					out.append("\"").append(startYear + index).append("\": ").append(percentOrNull(value));
					first = false;
				}
				index++;
			}
			out.append("}}");
			if (i < names.size() - 1) {
				out.append(',');
			}
			out.append('\n');
		}
		return out.append("    }\n  }\n}").toString();
	}

	private static String policyHistory(Map<String, LlmPolicyState> policies, int timeLag, int startYear) {
		StringBuilder out = new StringBuilder("Historical policy decisions (changes, not absolute levels):\n{");
		List<LlmPolicyState> sorted = new ArrayList<>(policies.values());
		sorted.sort(Comparator.comparing(LlmPolicyState::getName));
		for (int i = 0; i < sorted.size(); i++) {
			LlmPolicyState policy = sorted.get(i);
			out.append("\n  \"").append(jsonEscape(policy.getName())).append("\": {")
					.append("\"changes_by_decision_year\": {");
			double cumulative = 0;
			for (int k = 0; k < policy.decisionHistory().size(); k++) {
				if (k > 0) {
					out.append(", ");
				}
				Double value = policy.decisionHistory().get(k);
				out.append("\"").append(startYear + k * timeLag).append("\": ").append(numberOrNull(value));
				if (value != null && Double.isFinite(value)) {
					cumulative += value;
				}
			}
			out.append("}, \"latest_cumulative_change\": ").append(number(cumulative)).append('}');
			if (i < sorted.size() - 1) {
				out.append(',');
			}
		}
		return out.append("\n}\nNegative cumulative subsidy values turn subsidies into taxes and should be avoided.")
				.toString();
	}

	private static String percentOrNull(double value) {
		return Double.isFinite(value) ? number(value * 100) : "null";
	}

	private static String numberOrNull(Double value) {
		return value == null || !Double.isFinite(value) ? "null" : number(value);
	}

	private static String number(double value) {
		return String.format(Locale.US, "%.2f", value == -0.0 ? 0.0 : value);
	}

	private static String jsonEscape(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
				.replace("\r", "\\r").replace("\t", "\\t");
	}
}
