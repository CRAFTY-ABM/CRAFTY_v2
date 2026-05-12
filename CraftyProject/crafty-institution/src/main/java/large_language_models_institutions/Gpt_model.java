package large_language_models_institutions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;

import de.cesr.crafty.core.cli.ConfigLoader;

public class Gpt_model {

	private static final String MODEL = ConfigLoader.config.LLM_model_name;

	// Create client only once
	private static final OpenAIClient client = OpenAIOkHttpClient.builder().apiKey(ConfigLoader.config.LLM_API_KEY)
			.build();// createClient();

	public static String askLLM(String prompt) {

		ResponseCreateParams params = ResponseCreateParams.builder().model(MODEL).input(prompt).maxOutputTokens(1200)
				.build();

		Response response = client.responses().create(params);

		return textOnly(response);
	}

	private static OpenAIClient createClient() {

		// 1. First try environment variable OPENAI_API_KEY
		String envKey = System.getenv("OPENAI_API_KEY");

		if (envKey != null && !envKey.trim().isEmpty()) {
			System.out.println("Using OpenAI API key from environment variable OPENAI_API_KEY");
			return OpenAIOkHttpClient.fromEnv();
		}

		// 2. If environment variable is missing, read from ~/.openai_api_key
		String fileKey = readApiKeyFromFile();

		System.out.println("Using OpenAI API key from ~/.openai_api_key");

		return OpenAIOkHttpClient.builder().apiKey(fileKey).build();
	}

	private static String readApiKeyFromFile() {
		try {
			Path keyPath = Path.of(System.getProperty("user.home"), ".openai_api_key");

			if (!Files.exists(keyPath)) {
				throw new RuntimeException(
						"OpenAI API key not found. Please set OPENAI_API_KEY or create file: " + keyPath);
			}

			String key = Files.readString(keyPath).trim();

			if (key.isEmpty()) {
				throw new RuntimeException("OpenAI API key file is empty: " + keyPath);
			}

			return key;

		} catch (Exception e) {
			throw new RuntimeException("Could not read OpenAI API key from ~/.openai_api_key", e);
		}
	}

	private static String textOnly(Response r) {
		return r.output().stream().flatMap(item -> item.message().stream()).flatMap(msg -> msg.content().stream())
				.flatMap(c -> c.outputText().stream()).map(t -> t.text()).collect(Collectors.joining());
	}
}