package large_language_models_institutions;

import java.util.stream.Collectors;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;

public class Gpt_model {
	
	public static String askLLM(String prompt) {
		// Reads OPENAI_API_KEY from your environment automatically
		/// Configures using the `OPENAI_API_KEY`, `OPENAI_ORG_ID` and
		// `OPENAI_PROJECT_ID` environment variables
		OpenAIClient client = OpenAIOkHttpClient.fromEnv();
//		"gpt-5-nano" or /	// "gpt-4o-mini"																
		ResponseCreateParams params = ResponseCreateParams.builder().model("gpt-4.1-nano").input(prompt)
				.maxOutputTokens(1200).build();

		Response response = client.responses().create(params);

		return textOnly(response);
	}
	
	private static String textOnly(Response r) {
		return r.output().stream().flatMap(item -> item.message().stream()) // keep only message outputs
				.flatMap(msg -> msg.content().stream()) // message parts
				.flatMap(c -> c.outputText().stream()) // keep only output_text parts
				.map(t -> t.text()) // extract the actual string
				.collect(Collectors.joining());
	}

}
