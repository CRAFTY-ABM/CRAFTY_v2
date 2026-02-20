package utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Minimal OpenAI-compatible Chat Completions client.
 *
 * Works with: - Ollama (local): baseUrl = "http://localhost:11434/v1" (apiKey
 * can be "ollama") // free local - Groq (cloud): baseUrl =
 * "https://api.groq.com/openai/v1" (apiKey = GROQ_API_KEY) - OpenRouter:
 * baseUrl = "https://openrouter.ai/api/v1" (apiKey = OPENROUTER_API_KEY)
 *
 * Endpoint called: POST {baseUrl}/chat/completions
 */
public final class LLM_Local {
	
	public static void main(String[] args) {
		String LLMoutput = TheLLM(null, "gza");
		System.out.println(LLMoutput);
	}
	
	public static String TheLLM(String systemPrompt, String historyData) {
		LLM_Local client = LLM_Local.builder().baseUrl("http://localhost:11434/v1")
				.apiKey("ollama") 
				.model("gpt-oss:20b") // must match the local model name
				.temperature(0.0).maxTokens(512).build();
		try {
			return client.chat(systemPrompt, historyData);
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
		return null;
	}

	// ----- Public types -----

	public record Message(String role, String content) {
		public static Message system(String content) {
			return new Message("system", content);
		}

		public static Message user(String content) {
			return new Message("user", content);
		}

		public static Message assistant(String content) {
			return new Message("assistant", content);
		}
	}

	public static final class Builder {
		private String baseUrl;
		private String apiKey; // "Bearer <key>" will be used if non-empty
		private String model;
		private double temperature = 0.0;
		private Integer maxTokens = 512;
		private Duration timeout = Duration.ofSeconds(60);
		private final Map<String, String> extraHeaders = new LinkedHashMap<>();

		public Builder baseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder apiKey(String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		public Builder model(String model) {
			this.model = model;
			return this;
		}

		public Builder temperature(double temperature) {
			this.temperature = temperature;
			return this;
		}

		public Builder maxTokens(Integer maxTokens) {
			this.maxTokens = maxTokens;
			return this;
		}

		public Builder timeout(Duration timeout) {
			this.timeout = timeout;
			return this;
		}

		/**
		 * Useful for OpenRouter optional headers: - "HTTP-Referer": your app/site -
		 * "X-Title": your app name
		 */
		public Builder header(String name, String value) {
			if (name != null && value != null)
				extraHeaders.put(name, value);
			return this;
		}

		public LLM_Local build() {
			Objects.requireNonNull(baseUrl, "baseUrl is required");
			Objects.requireNonNull(model, "model is required");
			return new LLM_Local(baseUrl, apiKey, model, temperature, maxTokens, timeout,
					extraHeaders);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	// ----- Impl -----

	private static final ObjectMapper MAPPER = new ObjectMapper()
			.setSerializationInclusion(JsonInclude.Include.NON_NULL);

	private final HttpClient http;
	private final String baseUrl;
	private final String apiKey;
	private final String model;
	private final double temperature;
	private final Integer maxTokens;
	private final Duration timeout;
	private final Map<String, String> extraHeaders;

	private LLM_Local(String baseUrl, String apiKey, String model, double temperature,
			Integer maxTokens, Duration timeout, Map<String, String> extraHeaders) {
		this.baseUrl = stripTrailingSlash(baseUrl);
		this.apiKey = (apiKey == null ? "" : apiKey.trim());
		this.model = model;
		this.temperature = temperature;
		this.maxTokens = maxTokens;
		this.timeout = timeout;
		this.extraHeaders = new LinkedHashMap<>(extraHeaders);

		this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
	}

	/**
	 * Convenience: system + user -> assistant text.
	 */
	public String chat(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
		return chat(List.of(Message.system(systemPrompt == null ? "" : systemPrompt),
				Message.user(userPrompt == null ? "" : userPrompt)));
	}

	/**
	 * Full control: pass all messages (conversation). Returns assistant message
	 * content (choices[0].message.content).
	 */
	public String chat(List<Message> messages) throws IOException, InterruptedException {
		Objects.requireNonNull(messages, "messages");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model);
		body.put("messages", messages);
		body.put("temperature", temperature);
		if (maxTokens != null)
			body.put("max_tokens", maxTokens);

		String url = baseUrl + "/chat/completions";
		String json = MAPPER.writeValueAsString(body);

		HttpRequest.Builder req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(timeout).header("Content-Type",
				"application/json");

		if (!apiKey.isEmpty()) {
			// Most OpenAI-compatible providers accept this (Ollama ignores it but wants it
			// present).
			req.header("Authorization", "Bearer " + apiKey);
		}

		// Optional extras (e.g., OpenRouter app identification)
		for (var e : extraHeaders.entrySet()) {
			req.header(e.getKey(), e.getValue());
		}

		HttpResponse<String> resp = http.send(req.POST(HttpRequest.BodyPublishers.ofString(json)).build(),
				HttpResponse.BodyHandlers.ofString());

		if (resp.statusCode() / 100 != 2) {
			throw new IOException("LLM HTTP " + resp.statusCode() + " from " + url + ":\n" + resp.body());
		}

		JsonNode root = MAPPER.readTree(resp.body());
		JsonNode choices = root.path("choices");
		if (!choices.isArray() || choices.isEmpty()) {
			throw new IOException("LLM response missing choices:\n" + resp.body());
		}

		JsonNode content = choices.get(0).path("message").path("content");
		if (content.isMissingNode() || content.isNull()) {
			// Some providers may return tool calls, etc. For “start simple” we just guard.
			throw new IOException("LLM response missing message.content:\n" + resp.body());
		}
		return content.asText();
	}

	private static String stripTrailingSlash(String s) {
		if (s == null)
			return null;
		return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
	}



}
