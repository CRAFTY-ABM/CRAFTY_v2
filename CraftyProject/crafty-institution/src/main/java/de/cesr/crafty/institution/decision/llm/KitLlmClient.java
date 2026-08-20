package de.cesr.crafty.institution.decision.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import de.cesr.crafty.core.cli.ConfigLoader;

public class KitLlmClient implements LlmClient {

	private static final String DEFAULT_BASE_URL = "https://ki-toolbox.scc.kit.edu/api/v1";

	private final HttpClient httpClient;
	private final String apiKey;
	private final String modelName;
	private final String baseUrl;

	public KitLlmClient() {
		if (ConfigLoader.config == null) {
			throw new IllegalStateException("ConfigLoader.config is null. Cannot create KIT LLM client.");
		}

		this.apiKey = ConfigLoader.config.llm_api_key;
		this.modelName = ConfigLoader.config.llm_model_name;
		this.baseUrl = removeTrailingSlash(readBaseUrlOrDefault());
		this.httpClient = HttpClient.newHttpClient();

		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("llm_api_key is missing. Cannot call KIT KI-Toolbox.");
		}

		if (modelName == null || modelName.isBlank()) {
			throw new IllegalStateException("llm_model_name is missing. Cannot call KIT KI-Toolbox.");
		}
	}

	@Override
	public String askLLM(String prompt) {
		try {
			String requestBody = """
					{
					  "model": "%s",
					  "messages": [
					    {
					      "role": "user",
					      "content": "%s"
					    }
					  ],
					  "temperature": 0.2
					}
					""".formatted(escapeJson(modelName), escapeJson(prompt));

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(baseUrl + "/chat/completions"))
					.header("Authorization", "Bearer " + apiKey)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
					.build();

			HttpResponse<String> response = httpClient.send(
					request,
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
			);

			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new RuntimeException("KIT LLM API error. HTTP " + response.statusCode()
						+ "\nURL: " + baseUrl + "/chat/completions"
						+ "\nResponse:\n" + response.body());
			}

			return extractFirstMessageContent(response.body());

		} catch (IOException e) {
			throw new RuntimeException("I/O error while calling KIT LLM API.", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while calling KIT LLM API.", e);
		}
	}

	private static String readBaseUrlOrDefault() {

		try {
			Object value = ConfigLoader.config.getClass()
					.getField("LLM_base_url")
					.get(ConfigLoader.config);

			if (value instanceof String s && !s.isBlank()) {
				return s;
			}
		} catch (Exception ignored) {
			// Config has no LLM_base_url field, so use default.
		}

		return DEFAULT_BASE_URL;
	}

	private static String removeTrailingSlash(String s) {
		while (s.endsWith("/")) {
			s = s.substring(0, s.length() - 1);
		}
		return s;
	}

	private static String escapeJson(String text) {
		if (text == null) {
			return "";
		}

		return text
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\t", "\\t");
	}

	private static String extractFirstMessageContent(String json) {
		String marker = "\"content\":\"";
		int start = json.indexOf(marker);

		if (start < 0) {
			return json;
		}

		start += marker.length();

		StringBuilder result = new StringBuilder();
		boolean escaping = false;

		for (int i = start; i < json.length(); i++) {
			char c = json.charAt(i);

			if (escaping) {
				switch (c) {
				case 'n' -> result.append('\n');
				case 'r' -> result.append('\r');
				case 't' -> result.append('\t');
				case '"' -> result.append('"');
				case '\\' -> result.append('\\');
				default -> result.append(c);
				}
				escaping = false;
			} else if (c == '\\') {
				escaping = true;
			} else if (c == '"') {
				break;
			} else {
				result.append(c);
			}
		}

		return result.toString();
	}
}
