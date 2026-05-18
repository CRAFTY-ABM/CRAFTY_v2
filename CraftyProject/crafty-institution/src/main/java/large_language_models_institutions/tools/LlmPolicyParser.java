package large_language_models_institutions.tools;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class LlmPolicyParser {

	private static final ObjectMapper MAPPER = new ObjectMapper()
			// helpful if the LLM sometimes adds trailing commas or similar
			.enable(JsonParser.Feature.ALLOW_COMMENTS).enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES)
			.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);

	private LlmPolicyParser() {
	}

	public static HashMap<String, Double> extractPolicyDecisionsOrNull(String llmOutput) {
		if (llmOutput == null)
			return null;

		String s = sanitizeLlmJson(llmOutput);
		if (s == null)
			return null;

		JsonNode decisionsNode = null;

		// 1) Try normal parse
		try {
			JsonNode root = MAPPER.readTree(s);
			if (root != null && root.isObject()) {
				JsonNode d = root.get("policy_decisions");
				if (d != null && d.isObject())
					decisionsNode = d;
			}
		} catch (Exception ignore) {
			// fall through
		}

		// 2) Fallback: extract the policy_decisions {...} substring and parse only that
		if (decisionsNode == null) {
			String block = extractObjectBlockForKey(s, "policy_decisions");
			if (block == null)
				return null;
			try {
				JsonNode d = MAPPER.readTree(block);
				if (d != null && d.isObject())
					decisionsNode = d;
			} catch (Exception ex) {
				return null;
			}
		}

		if (decisionsNode == null || !decisionsNode.isObject())
			return null;

		HashMap<String, Double> out = new HashMap<>();
		Iterator<Map.Entry<String, JsonNode>> fields = decisionsNode.fields();

		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> e = fields.next();
			String policyName = e.getKey().replace(" ", "_").toLowerCase();
			JsonNode v = e.getValue();

			if (policyName.isBlank() || v == null || v.isNull())
				return null;

			double value;
			if (v.isNumber()) {
				value = v.asDouble();
			} else if (v.isTextual()) {
				try {
					value = Double.parseDouble(v.asText().trim());
				} catch (NumberFormatException nfe) {
					return null;
				}
			} else {
				return null;
			}

			if (!Double.isFinite(value))
				return null;
			out.put(policyName, value);
		}

		return out.isEmpty() ? null : out;
	}

	/**
	 * Extracts the JSON object value for a given key, e.g. policy_decisions: { ...
	 * } Returns the substring "{ ... }" or null if not found / not well-formed.
	 */
	private static String extractObjectBlockForKey(String s, String key) {
		int idx = s.indexOf("\"" + key + "\"");
		if (idx < 0)
			idx = s.indexOf(key); // in case of unquoted field names
		if (idx < 0)
			return null;

		int colon = s.indexOf(':', idx);
		if (colon < 0)
			return null;

		int i = colon + 1;
		while (i < s.length() && Character.isWhitespace(s.charAt(i)))
			i++;
		if (i >= s.length() || s.charAt(i) != '{')
			return null;

		int start = i;
		int depth = 0;
		boolean inString = false;
		char stringQuote = 0;
		boolean escape = false;

		for (; i < s.length(); i++) {
			char c = s.charAt(i);

			if (inString) {
				if (escape) {
					escape = false;
				} else if (c == '\\') {
					escape = true;
				} else if (c == stringQuote) {
					inString = false;
					stringQuote = 0;
				}
				continue;
			}

			if (c == '"' || c == '\'') {
				inString = true;
				stringQuote = c;
				continue;
			}

			if (c == '{')
				depth++;
			else if (c == '}') {
				depth--;
				if (depth == 0) {
					return s.substring(start, i + 1);
				}
			}
		}

		return null;
	}

	/**
	 * Extracts the "reasoning" field as a map: {"reasoning" -> "<text>"}. Returns
	 * null if input is corrupted or reasoning is missing / not a string.
	 */
	public static HashMap<String, String> extractReasoningOrNull(String llmOutput) {
		if (llmOutput == null)
			return null;

		String s = sanitizeLlmJson(llmOutput);
		if (s == null)
			return null;

		try {
			JsonNode root = MAPPER.readTree(s);
			if (root == null || !root.isObject())
				return null;

			JsonNode reasoning = root.get("reasoning");
			if (reasoning == null || !reasoning.isTextual())
				return null;

			String text = reasoning.asText();
			if (text == null || text.isBlank())
				return null;

			HashMap<String, String> out = new HashMap<>();
			out.put("reasoning", text);
			return out;
		} catch (Exception ex) {
			return null;
		}
	}

	/**
	 * Handles common LLM formatting issues: - ```json ... ``` code fences - curly
	 * quotes “ ” that break JSON
	 */
	private static String sanitizeLlmJson(String raw) {
		String s = raw.trim();
		if (s.isEmpty())
			return null;

		// Replace “smart quotes” with normal quotes (common LLM issue)
		s = s.replace('\u201C', '"').replace('\u201D', '"').replace('\u2018', '\'').replace('\u2019', '\'');

		// Strip code fences if present
		if (s.startsWith("```")) {
			int firstNewline = s.indexOf('\n');
			if (firstNewline < 0)
				return null; // fence but no content
			s = s.substring(firstNewline + 1);
			int lastFence = s.lastIndexOf("```");
			if (lastFence >= 0)
				s = s.substring(0, lastFence);
			s = s.trim();
		}

		return s.isEmpty() ? null : s;
	}

	public static String onlyWhenUnparseableOutput(String unparseableOutput) {
		return "AUTOMATIC PARSER ERROR (Java):\r\n" + "Your last response was not parseable JSON.\r\n" + "\r\n"
				+ "Return ONLY ONE valid JSON object and nothing else.\r\n" + "\r\n" + "Required schema:\r\n" + "{\r\n"
				+ "  \"reasoning\": \"<single-line string>\",\r\n"
				+ "  \"policy_decisions\": { \"<policy_key>\": <number>, ... }\r\n" + "}\r\n" + "\r\n" + "Rules:\r\n"
				+ "- Output must be strictly valid JSON.\r\n"
				+ "- \"reasoning\" must be a single-line string (no raw newlines; use \"\\\\n\" if needed).\r\n"
				+ "- policy_decisions values must be numbers (no quotes), finite, in [-5, 5].\r\n"
				+ "- policy_decisions MUST contain EXACTLY these keys (no more, no less):\r\n"
				+ "  [\"key0\",\"key1\",\"key2\", \"...\"]\r\n" + "\r\n"
				+ "Now re-emit corrected JSON using the SAME intended decisions as before.\r\n" + "\r\n"
				+ "Previous unparseable output:\r\n" + "<<<\r\n" + unparseableOutput + ">>>\r\n" + "";
	}
	
	
	public static String promptModefierToForceFormat(String originalPrompt, Collection<String> expectedPolicyKeys) {
	    if (originalPrompt == null) return null;

	    List<String> keys = (expectedPolicyKeys == null ? List.<String>of() : expectedPolicyKeys.stream()
	            .filter(Objects::nonNull)
	            .map(String::trim)
	            .filter(s -> !s.isEmpty())
	            .distinct()
	            .sorted()
	            .collect(Collectors.toList()));

	    // Build an example JSON with the exact keys
	    StringBuilder example = new StringBuilder();
	    example.append("{\n")
	           .append("  \"reasoning\": \"one line only\",\n")
	           .append("  \"policy_decisions\": {\n");
	    for (int i = 0; i < keys.size(); i++) {
	        String k = keys.get(i);
	        example.append("    \"").append(escapeJson(k)).append("\": 0");
	        if (i < keys.size() - 1) example.append(",");
	        example.append("\n");
	    }
	    example.append("  }\n")
	           .append("}\n");

	    String strictHeader =
	            "AUTOMATION NOTICE (Java JSON parser; no human supervision):\n" +
	            "Your response will be parsed automatically. If you output anything except valid JSON, it will fail.\n\n" +
	            "You MUST reply with EITHER:\n" +
	            "  (A) exactly ONE valid JSON object, OR\n" +
	            "  (B) exactly the literal: null\n\n" +
	            "If you cannot comply with the schema below, output: null\n\n" +
	            "Schema requirements:\n" +
	            "- Output must be STRICT valid JSON (no markdown, no code fences, no extra text).\n" +
	            "- Required top-level keys: \"reasoning\", \"policy_decisions\".\n" +
	            "- \"reasoning\" must be a SINGLE LINE string (no raw newlines; if needed use \\\\n inside the string).\n" +
	            "- \"policy_decisions\" must be an object with ONLY numeric values (no quotes) .\n" +
	            "- \"policy_decisions\" MUST contain EXACTLY these keys (no more, no less):\n" +
	            keys.toString() + "\n\n" +
	            "Example (structure only):\n" +
	            example +
	            "\nNow answer the policy task using the schema above.\n";

	    // Prepend strict header to the original prompt
	    return strictHeader + "\n\n" + originalPrompt;
	}

	private static String escapeJson(String s) {
	    // minimal JSON string escaping for keys/example
	    return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

}
