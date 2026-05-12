package large_language_models_institutions;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;

public class Institute {
	private static final CustomLogger LOGGER = new CustomLogger(Institute.class);

	private String name;
	private Paradigm paradigm;
	private int timeLag;
	private int startYear;
	private int endYear;

	private Map<String, Policy> policies = new HashMap<>();
	private HashMap<String, Target> targets = new HashMap<>();
	private String base_prompts;
	private String comletePrompte;
	private String outputLLM;

	public Institute(String name, Paradigm paradigm) {
		this.name = name;
		this.paradigm = paradigm;
	}

	public void step() {
		outputLLM = "";
		modeloutputToPromptStyle();
		comletePrompte = base_prompts + "\n" + modeloutputToPromptStyle() + "\n"
				+ historicalPolicyEffectsToPromptStyle();
		fromLLMtoPoliciesValue();
		policies.values().forEach(policy -> {
			policy.step();
		});
		writeOutput();
		
//		policies.values().forEach(p -> {
//			System.out.println(
//					":: " + name + "=> " + p.getName() + ": " + p.getRecorder().size() + ": " + p.getRecorder());
//		});
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
		String out = "Historical land use and/or ecosystem services supply relative to baseline values (annual time series):\n {";
		for (String targetName : targets.keySet()) {
			out = out + "\n \"" + targetName + "\":" + use.get(targetName);
		}
		out = out + " \n }";
//		System.out.println("---  "+name + ": \n " + out);
		return out;
	}

	private String historicalPolicyEffectsToPromptStyle() {
		String out = "\n" + "Historical policy decisions on subsidy changes (time series, every " + timeLag
				+ " years): \n  \"policy_decisions\":{";

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

			System.out.println("Connect to LLM (" + name + ") Paradigm= " + paradigm.getName());
			String inputLLM = comletePrompte;
			if (inputLLM == null) {
				LOGGER.fatal("Input to LLM is null: institute " + name + " year: " + Timestep.getCurrentYear());
				return;
			}
			outputLLM = Gpt_model.askLLM(inputLLM);

			HashMap<String, Double> LLMpolicies = LlmPolicyParser.extractPolicyDecisionsOrNull(outputLLM);
			if (LLMpolicies == null) {
				// 2nd try: reformat/salvage using the broken output + expected keys
				String secondTry = LlmPolicyParser.onlyWhenUnparseableOutput(outputLLM);
				outputLLM = Gpt_model.askLLM(secondTry);
				LLMpolicies = LlmPolicyParser.extractPolicyDecisionsOrNull(outputLLM);
				if (LLMpolicies == null) {
					// 3rd try: rerun original prompt but with strict-format header
					inputLLM = LlmPolicyParser.promptModefierToForceFormat(inputLLM, policies.keySet());
					outputLLM = Gpt_model.askLLM(inputLLM);
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
				System.out.println(".... ALL POLICIES ARE RECORDED CORRECTLY !  ");
			} else {
				// 4rd try: rerun original prompt but with strict-format header
				inputLLM = LlmPolicyParser.promptModefierToForceFormat(inputLLM, policies.keySet());
				outputLLM = Gpt_model.askLLM(inputLLM);
				LLMpolicies = LlmPolicyParser.extractPolicyDecisionsOrNull(outputLLM);
				if (LLMpolicies.keySet().equals(policies.keySet())) {
					System.out.println(".... ALL POLICIES ARE RECORDED CORRECTLY !  (4rd try)");
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

	public HashMap<String, Target> getTargets() {
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
}
