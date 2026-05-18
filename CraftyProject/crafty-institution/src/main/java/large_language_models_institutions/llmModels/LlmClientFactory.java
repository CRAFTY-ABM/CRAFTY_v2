package large_language_models_institutions.llmModels;

import de.cesr.crafty.core.cli.ConfigLoader;

public final class LlmClientFactory {

	private LlmClientFactory() {
		// Utility class
	}

	public static LlmClient createFromConfig() {
		if (ConfigLoader.config == null) {
			throw new IllegalStateException("ConfigLoader.config is null. Cannot create LLM client.");
		}

		String provider = readProvider();
		String modelName = ConfigLoader.config.LLM_model_name;

		if (provider == null || provider.isBlank()) {
			provider = guessProviderFromModelName(modelName);
		}

		switch (provider.trim().toLowerCase()) {
		case "openai":
		case "gpt":
			return new GptLlmClient();

		case "gemini":
		case "google":
			return new GeminiLlmClient();

		case "via_kit":
		case "kit":
		case "ki-toolbox":
		case "ki_toolbox":
		case "kit-ki-toolbox":
			return new KitLlmClient();

		default:
			throw new IllegalArgumentException(
					"Unsupported LLM provider: " + provider
							+ ". Supported values are: openai, gpt, gemini, google, via_KIT, kit.");
		}
	}

	private static String readProvider() {
		try {
			String provider = ConfigLoader.config.LLM_provider;

			if (provider == null || provider.isBlank()) {
				return null;
			}

			return provider;
		} catch (Exception e) {
			return null;
		}
	}

	private static String guessProviderFromModelName(String modelName) {
		if (modelName == null || modelName.isBlank()) {
			throw new IllegalArgumentException("LLM_model_name is missing.");
		}

		String lower = modelName.trim().toLowerCase();

		if (lower.startsWith("gemini")) {
			return "gemini";
		}

		if (lower.startsWith("gpt") || lower.startsWith("o")) {
			return "openai";
		}

		if (lower.startsWith("azure.") || lower.startsWith("kit.")) {
			return "via_kit";
		}

		throw new IllegalArgumentException("Cannot guess LLM provider from model name: " + modelName
				+ ". Please add LLM_provider: \"openai\", \"gemini\", or \"via_KIT\" to the YAML config.");
	}
}