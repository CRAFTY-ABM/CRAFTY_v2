package large_language_models_institutions.llmModels;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.cesr.crafty.core.cli.ConfigLoader;

/**
 * Simple Gemini API client for CRAFTY institutional LLM calls.
 *
 * This class sends a prompt to Gemini using the generateContent REST endpoint
 * and returns the generated model text.
 */
public class GeminiLlmClient implements LlmClient {

	private static final String GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

	private final HttpClient httpClient;
	private final ObjectMapper mapper;

	@Override
	public String askLLM(String prompt) {
		return generate("", prompt, true);
	}

	public GeminiLlmClient() {

		this.mapper = new ObjectMapper();
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
	}

	/**
	 * Sends a prompt to Gemini and returns the generated text.
	 *
	 * @param systemPrompt optional system instruction, can be null
	 * @param userPrompt   main user prompt
	 * @param forceJson    if true, asks Gemini to return JSON using
	 *                     responseMimeType
	 * @return generated model output as plain string
	 */
	public String generate(String systemPrompt, String userPrompt, boolean forceJson) {
		if (userPrompt == null || userPrompt.isBlank()) {
			throw new IllegalArgumentException("User prompt is empty.");
		}

		try {
			String requestBody = buildRequestBody(systemPrompt, userPrompt, forceJson);

			String encodedModel = URLEncoder.encode(ConfigLoader.config.LLM_model_name, StandardCharsets.UTF_8).replace("+", "%20");

			String endpoint = String.format(GEMINI_ENDPOINT, encodedModel);

			HttpRequest request = HttpRequest.newBuilder().uri(URI.create(endpoint)).timeout(Duration.ofMinutes(3))
					.header("Content-Type", "application/json").header("x-goog-api-key", ConfigLoader.config.LLM_API_KEY)
					.POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new RuntimeException("Gemini API error. HTTP " + response.statusCode() + ": " + response.body());
			}

			return extractText(response.body());

		} catch (IOException e) {
			throw new RuntimeException("Failed to call Gemini API due to I/O error.", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Gemini API call was interrupted.", e);
		}
	}

	private String buildRequestBody(String systemPrompt, String userPrompt, boolean forceJson) throws IOException {
		ObjectNode root = mapper.createObjectNode();

		if (systemPrompt != null && !systemPrompt.isBlank()) {
			ObjectNode systemInstruction = mapper.createObjectNode();
			ArrayNode systemParts = mapper.createArrayNode();

			ObjectNode systemTextPart = mapper.createObjectNode();
			systemTextPart.put("text", systemPrompt);
			systemParts.add(systemTextPart);

			systemInstruction.set("parts", systemParts);
			root.set("systemInstruction", systemInstruction);
		}

		ArrayNode contents = mapper.createArrayNode();

		ObjectNode userContent = mapper.createObjectNode();
		userContent.put("role", "user");

		ArrayNode parts = mapper.createArrayNode();
		ObjectNode textPart = mapper.createObjectNode();
		textPart.put("text", userPrompt);
		parts.add(textPart);

		userContent.set("parts", parts);
		contents.add(userContent);

		root.set("contents", contents);

		ObjectNode generationConfig = mapper.createObjectNode();
		generationConfig.put("temperature", 0.2);
		generationConfig.put("maxOutputTokens", 4096);

		if (forceJson) {
			generationConfig.put("responseMimeType", "application/json");
		}

		root.set("generationConfig", generationConfig);

		return mapper.writeValueAsString(root);
	}

	private String extractText(String responseBody) throws IOException {
		JsonNode root = mapper.readTree(responseBody);

		JsonNode candidates = root.path("candidates");
		if (!candidates.isArray() || candidates.isEmpty()) {
			throw new RuntimeException("Gemini response has no candidates: " + responseBody);
		}

		JsonNode parts = candidates.get(0).path("content").path("parts");

		if (!parts.isArray() || parts.isEmpty()) {
			throw new RuntimeException("Gemini response has no text parts: " + responseBody);
		}

		StringBuilder result = new StringBuilder();

		for (JsonNode part : parts) {
			JsonNode text = part.path("text");
			if (!text.isMissingNode() && !text.asText().isBlank()) {
				result.append(text.asText());
			}
		}

		if (result.isEmpty()) {
			throw new RuntimeException("Gemini response text is empty: " + responseBody);
		}

		return result.toString().trim();
	}

}