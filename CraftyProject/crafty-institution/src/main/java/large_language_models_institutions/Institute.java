package large_language_models_institutions;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;
import large_language_models_institutions.llmModels.LlmClient;
import large_language_models_institutions.llmModels.LlmClientFactory;
import large_language_models_institutions.tools.LlmPolicyParser;

public class Institute {
	private static final CustomLogger LOGGER = new CustomLogger(Institute.class);

	private String name;
	private Paradigm paradigm;
	private int timeLag;
	private int startYear;
	private int endYear;

	private Map<String, Policy> policies = new ConcurrentHashMap<>();
	private Map<String, Target> targets = new ConcurrentHashMap<>();
	private String base_prompts;
	private String comletePrompte;
	private String outputLLM;

	public Institute(String name, Paradigm paradigm) {
		this.name = name;
		this.paradigm = paradigm;
	}

	public void step_preparePrompt() {
//		System.out.println("prepare Prompts  " + getName());
		outputLLM = "";
		modeloutputToPromptStyle();
		comletePrompte = base_prompts + "\n" + modeloutputToPromptStyle() + "\n"
				+ historicalPolicyEffectsToPromptStyle();
	}

	public void step_connectLLMs() {
		fromLLMtoPoliciesValue();
	}

	public void step_appliedPolicies() {
		LOGGER.info("Institute (applied policies)= " + getName() + ": ");
		policies.values().forEach(policy -> { // this no need to be in parallel
			LOGGER.info("Policy= " + policy.getName());
			policy.step();
		});
		writeOutput();
	}

	private String modeloutputToPromptStyle() {
		HashMap<String, ArrayList<String>> use = new HashMap<>();
		targets.forEach((targetName, target) -> {
			use.put(targetName, new ArrayList<>());
			target.getHistory().values().forEach(value -> {
				String v = (Math.floor((value - 1) * 100.0) / 100.0) + "%";
				use.get(targetName).add(v);
			});
		});
		String out = messagetToForceFormat(policies.keySet());

		out = out + "\n \""
				+ "Historical land use and/or ecosystem services supply relative to baseline values (annual time series starting from 2020).:\n {";
		for (String targetName : targets.keySet()) {
			out = out + "\n \"" + targetName + "\":" + use.get(targetName);
		}
		out = out + " \n }";
//		System.out.println("---  "+name + ": \n " + out);
		return out;
	}

	private String historicalPolicyEffectsToPromptStyle() {
		String out = "\n" + "Historical policy decisions on subsidy changes (time series, every " + timeLag
				+ " years starting from " + startYear + "): \n  \"policy_decisions\":{";

		for (Policy policy : policies.values()) {
			out = out + "\n \"" + policy.getName() + "\":" + policy.getDesicions_history();
		}
		out = out + " \n }";
		return out;
	}

	// still need to feel the comletePrompte
	private void fromLLMtoPoliciesValue() {
		int tick = Timestep.getCurrentYear() - startYear - 1;
		boolean llmConection = Timestep.getCurrentYear() < Math.min(endYear + 1, Timestep.getEndtYear()) && tick >= 0
				&& tick % timeLag == 0;

		if (llmConection) {
			String message = "Connected to LLM = (" + name + ")_(" + paradigm.getName() + ")";
			String inputLLM = comletePrompte;
			if (inputLLM == null) {
				LOGGER.fatal("Input to LLM is null: institute " + name + " year: " + Timestep.getCurrentYear());
				return;
			}
			LlmClient llm = LlmClientFactory.createFromConfig();
			outputLLM = llm.askLLM(inputLLM);

			HashMap<String, Double> LLMpolicies = LlmPolicyParser.extractPolicyDecisionsOrNull(outputLLM);
			if (LLMpolicies == null) {
				// 2nd try: reformat/salvage using the broken output + expected keys
				String secondTry = LlmPolicyParser.onlyWhenUnparseableOutput(outputLLM);
				outputLLM = llm.askLLM(secondTry);
				LLMpolicies = LlmPolicyParser.extractPolicyDecisionsOrNull(outputLLM);
				if (LLMpolicies == null) {
					// 3rd try: rerun original prompt but with strict-format header
					inputLLM = LlmPolicyParser.promptModefierToForceFormat(inputLLM, policies.keySet());
					outputLLM = llm.askLLM(inputLLM);
					LLMpolicies = LlmPolicyParser.extractPolicyDecisionsOrNull(outputLLM);
				}
			}
			if (LLMpolicies == null) {
				LOGGER.error(
						"LLM output invalid/corrupted JSON. CRAFTY will use 0 for all policies this year. institute="
								+ name + " year=" + Timestep.getCurrentYear() + " output=" + outputLLM);
				return;
			}
			if (LLMpolicies.keySet().equals(policies.keySet())) {
				LOGGER.info(message + ".... (ALL POLICIES ARE RECORDED CORRECTLY !)");
				System.out.println(message + ".... (ALL POLICIES ARE RECORDED CORRECTLY !)");
			} else {
				// 4rd try: rerun original prompt but with strict-format header
				inputLLM = LlmPolicyParser.promptModefierToForceFormat(inputLLM, policies.keySet());
				outputLLM = llm.askLLM(inputLLM);
				LLMpolicies = LlmPolicyParser.extractPolicyDecisionsOrNull(outputLLM);
				if (LLMpolicies.keySet().equals(policies.keySet())) {
					System.out.println(message + ".... (ALL POLICIES ARE RECORDED CORRECTLY !)  (4rd try)");
				} else {
					LOGGER.error("institute: (" + name + ")" + "paradigm =(" + paradigm.getName() + ") year= ("
							+ Timestep.getCurrentYear() + ") NOT ALL POLICIES ARE RECORDED CORRECTLY ");
				}
			}

			LLMpolicies.forEach((policyName, value) -> {
				if (policies.containsKey(policyName)) {
					policies.get(policyName).setValue(value);
					policies.get(policyName).setAccumulatedValue(value);
					policies.get(policyName).getDesicions_history().add(value);
					policies.get(policyName).getRecorder().add(policies.get(policyName).getAccumulatedValue());
				} else {
					LOGGER.error("institute: (" + name + ")" + "paradigm =(" + paradigm.getName()
							+ ") Policy Name Not found   " + policyName + " (will be ignored)");
				}
			});
		} else {
			policies.values().forEach(p -> {
				double v = !p.getDesicions_history().isEmpty() ? p.getDesicions_history().getLast() : 0;
				p.getRecorder().add(p.getRecorder().getLast());
				p.setValue(v);
			});
		}
//		policies.values().forEach(p -> {
//			System.out.println(
//					":: " + name + "=> " + p.getName() + ": " + p.getRecorder().size() + ": " + p.getRecorder());
//		});
	}

	Map<String, List<Double>> policiesVaraitionListner = new LinkedHashMap<>();
	Map<String, List<Double>> accumulativePoliciesListner = new LinkedHashMap<>();

	private void writeOutput() {
		policiesVaraitionListner.computeIfAbsent("Year", key -> new ArrayList<>())
				.add((double) Timestep.getCurrentYear());
		accumulativePoliciesListner.computeIfAbsent("Year", key -> new ArrayList<>())
				.add((double) Timestep.getCurrentYear());

		policies.values().forEach(policy -> {
			policiesVaraitionListner.computeIfAbsent(policy.getName(), key -> new ArrayList<>()).add(policy.getValue());
			accumulativePoliciesListner.computeIfAbsent(policy.getName(), key -> new ArrayList<>())
					.add(policy.getAccumulatedValue());
		});

		String dir = PathTools.makeDirectory(ConfigLoader.config.output_folder_name + File.separator + "LLM_outputs"
				+ File.separator + paradigm.getName());
		CsvTools.writeCSVfile(policiesVaraitionListner, Paths.get(dir + File.separator + name + "_policy_changes.csv"));
		CsvTools.writeCSVfile(accumulativePoliciesListner,
				Paths.get(dir + File.separator + name + "_policies_accumulation.csv"));

		if (!outputLLM.isEmpty()) {
			dir = PathTools.makeDirectory(dir + File.separator + name);
			String str = "";
			for (Policy p : policies.values()) {
				str = str + p.getName() + ": " + p.getValue() + "\n";
			}
			PathTools.writeFile(dir + File.separator + "" + (Timestep.getCurrentYear() - 1) + ".txt",
					"#########   Prompte (LLM Inputs):  ######### \n" + comletePrompte
							+ "\n\n #########  LLM output:  ######### \n" + outputLLM
							+ "\n\n #########  Crafty Input  ######### \n" + str,
					false);
		}
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Map<String, Policy> getPolicies() {
		return policies;
	}

	public Map<String, Target> getTargets() {
		return targets;
	}

	public void setTargets(HashMap<String, Target> targets) {
		this.targets = targets;
	}

	public String getBase_prompts() {
		return base_prompts;
	}

	public void setBase_prompts(String base_prompts) {
		this.base_prompts = base_prompts;
	}

	public String getComletePrompte() {
		return comletePrompte;
	}

	public void setComletePrompte(String comletePrompte) {
		this.comletePrompte = comletePrompte;
	}

	public void setPolicies(Map<String, Policy> policies) {
		this.policies = policies;
	}

	public int getTimeLag() {
		return timeLag;
	}

	public void setTimeLag(int timeLag) {
		this.timeLag = timeLag;
	}

	public int getStartYear() {
		return startYear;
	}

	public void setStartYear(int startYear) {
		this.startYear = startYear;
	}

	public int getEndYear() {
		return endYear;
	}

	public void setEndYear(int endYear) {
		this.endYear = endYear;
	}

	private String messagetToForceFormat(Collection<String> keys) {
		return "[ \n AUTOMATION NOTICE (Java JSON parser; no human supervision):\n"
				+ "Your response will be parsed automatically. If you output anything except valid JSON, it will fail.\n\n"
				+ "You MUST reply with EITHER:\n" + "  (A) exactly ONE valid JSON object, OR\n"
				+ "  (B) exactly the literal: null\n\n" + "If you cannot comply with the schema below, output: null\n\n"
				+ "Schema requirements:\n"
				+ "- Output must be STRICT valid JSON (no markdown, no code fences, no extra text).\n"
				+ "- Required top-level keys: \"reasoning\", \"policy_decisions\".\n"
				+ "- \"reasoning\" must be a SINGLE LINE string (no raw newlines; if needed use \\\\n inside the string).\n"
				+ "- \"policy_decisions\" must be an object with ONLY numeric values (no quotes) .\n"
				+ "- \"policy_decisions\" MUST contain EXACTLY these keys (no more, no less):\n" + keys.toString()
				+ "\n ]\n";
	}
}
