package large_language_models_institutions.llmModels;

import java.util.stream.Collectors;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;

import de.cesr.crafty.core.cli.ConfigLoader;

public class GptLlmClient implements LlmClient {

	private static final OpenAIClient client = OpenAIOkHttpClient.builder().apiKey(ConfigLoader.config.LLM_API_KEY)
			.build();

	@Override
	public String askLLM(String prompt) {

		ResponseCreateParams params = ResponseCreateParams.builder().model(ConfigLoader.config.LLM_model_name)
				.input(prompt).build();

		Response response = client.responses().create(params);

		return textOnly(response);
	}

	private static String textOnly(Response r) {
		return r.output().stream().flatMap(item -> item.message().stream()).flatMap(msg -> msg.content().stream())
				.flatMap(c -> c.outputText().stream()).map(t -> t.text()).collect(Collectors.joining());
	}
}