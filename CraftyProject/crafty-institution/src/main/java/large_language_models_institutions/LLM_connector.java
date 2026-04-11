package large_language_models_institutions;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.RegionalModelRunner;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.main.MainHeadless;
import de.cesr.crafty.core.updaters.AftsUpdater;
import de.cesr.crafty.core.updaters.CapitalUpdater;
import de.cesr.crafty.core.updaters.RegionsModelRunnerUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;
import utils.External_variables_Manager;

public class LLM_connector {

	private static final CustomLogger LOGGER = new CustomLogger(Runner.class);
	private AtomicBoolean shoudlUpdateCapitalAdjustments = new AtomicBoolean(false);

	Map<String, Map<String, Map<String, List<Map<String, Double>>>>> IPs = new HashMap<>(); // <instutite_name,policy_name,type,crafy_elements(service,aft,
	// capital)>
	Map<String, Map<String, Map<String, Double>>> targetToCraftyElements = new HashMap<>();// <target_Name,Type=service/AFt/externalVariable,crafty_element_name,weight>
	private HashMap<String, ArrayList<String>> instituteTocrafty_Output_Observed = new HashMap<>();
	private Map<String, String> base_prompts = new HashMap<>(); // <instutite_name, propmt_value>
	private HashMap<String, String> comletePrompte = new HashMap<>();

	// policy effects
	private Map<String, Map<String, Double>> policiesValues = new HashMap<>();// <institute,policy,value>
	private Map<String, Map<String, Double>> accumulativePolicies = new HashMap<>();// <institute,policy,value>

	private Map<String, Map<String, ArrayList<Double>>> policiesValues_history = new HashMap<>();// <institute,policy,list<values>

	DataCollector_LLM collector;

	public void setup() {
		if (Paths.get(ConfigLoader.config.institutions_directory).toFile().isDirectory()) {
			setup_targets();
			setup_instutites();
			collector = new DataCollector_LLM(this);
			RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(r -> {
				initial_supplys.put(r.R.getName(), new HashMap<>());
			});
		} else {
			LOGGER.fatal("institutions_directory in config file is not a directory");
		}
	}

	public void step() {
		MainHeadless.runner.step();
		comletePrompte.clear();
		collector.recorder.clear();
		shoudlUpdateCapitalAdjustments.set(false);
		policiesValues.forEach((i, p) -> {
			p.clear();
		});
		External_variables_Manager.valuesInjector();

		RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(r -> {
			PreparModelOutput(r);
		});

		IPs.keySet().forEach(intitute_name -> {
			comletePrompte.put(intitute_name,
					base_prompts.get(intitute_name) + "\n" + modeloutputToPromptStyle(intitute_name) + "\n"
							+ historicalPolicyEffectsToPromptStyle(intitute_name));
		});

		policiesValues.forEach((instituteName, hash) -> {
			fromLLMtoPoliciesValue(instituteName);
		});

		RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(r -> {
			accumulativePolicies.forEach((instituteName, hash) -> {// Acumulative Policy not the policy
				hash.forEach((policyName, value) -> {
					applyOnePolicy(r, instituteName, policyName, value);
				});
			});
			collector.outputFiles(r);
		});

		adjustCapitalIfneeded();

	}

	private void applyOnePolicy(RegionalModelRunner r, String institutionName, String policyName, double policyValue) {

		IPs.get(institutionName).get(policyName).forEach((typeName, listOfElementMaps) -> {
//			System.out.println(institutionName + ", " + policyName + ",  " + typeName + "==> " + listOfElementMaps
//					+ " ( " + policyValue + ")");
//			int tick = Math.max(0, policiesValues_history.get(institutionName).get(policyName).size() -2);
//			System.out.println(tick+",  "+policiesValues_history.get(institutionName).get(policyName));
//			Double oldPolicy = previous_policiesValues.get(institutionName).get(policyName);
//			if (oldPolicy == null) {
//				oldPolicy = 0d;
//			}
//			double policyValueChange = policyValue - oldPolicy;
			listOfElementMaps.forEach((hash) -> {
				hash.forEach((element, weight) -> {
					if (typeName.equalsIgnoreCase("AFT")) {
						AFTsLoader.getAftHash().get(element).getLand_taxes_subsidies()
								.merge(Timestep.getCurrentYear() + 1, (weight * policyValue / 100), Double::sum);
					} else if (typeName.equalsIgnoreCase("Service")) {
						r.R.getServicesHash().get(element).getTaxes_subsidies().merge(Timestep.getCurrentYear() + 1,
								(weight * policyValue / 100), Double::sum);
					} else if (typeName.equalsIgnoreCase("Capital")) {
						String[] str = element.split(":");
						if (AFTsLoader.getActivateAFTsHash().keySet().contains(str[0])
								&& CapitalUpdater.getCapitalsList().contains(str[1])) {
							shoudlUpdateCapitalAdjustments.set(true);
							AFTsLoader.getActivateAFTsHash().get(str[0]).getCapital_adjustments().merge(str[1],
									(weight * policyValue / 100), Double::sum);
						}
					}
					collector.policyEffectsListner
							.put(institutionName + "@" + policyName + "@" + typeName + "@" + element, policyValue);
				});
			});
		});
		collector.policyListener.put(institutionName + "@" + policyName, policyValue);
	}

	private void adjustCapitalIfneeded() {
		if (shoudlUpdateCapitalAdjustments.get()) {
			LOGGER.info("###### adjust_cell_capitals #######");
			AftsUpdater.adjust_cell_capitals();
		}
	}

	private void fromLLMtoPoliciesValue(String instituteName) {
		if ((Timestep.getTick() - 1) % ConfigLoader.config.institution_time_lag == 0) {
			System.out.println("Connect to LLM (" + instituteName + ")");
			String inputLLM = comletePrompte.get(instituteName);
			if (inputLLM == null) {
				LOGGER.fatal(
						"Input to LLM is null: institute " + instituteName + " year: " + Timestep.getCurrentYear());
				return;
			}
			String outputLLM = Gpt_model.askLLM(inputLLM);
			HashMap<String, Double> policies = LlmPolicyParser.extractPolicyDecisionsOrNull(outputLLM);
			if (policies == null) {
				// 2nd try: reformat/salvage using the broken output + expected keys
				String secondTry = LlmPolicyParser.onlyWhenUnparseableOutput(outputLLM);
				outputLLM = Gpt_model.askLLM(secondTry);
				policies = LlmPolicyParser.extractPolicyDecisionsOrNull(outputLLM);
				if (policies == null) {
					// 3rd try: rerun original prompt but with strict-format header
					inputLLM = LlmPolicyParser.promptModefierToForceFormat(inputLLM, IPs.get(instituteName).keySet());
					outputLLM = Gpt_model.askLLM(inputLLM);
					policies = LlmPolicyParser.extractPolicyDecisionsOrNull(outputLLM);
				}
			}
			if (policies == null) {
				LOGGER.error(
						"LLM output invalid/corrupted JSON. CRAFTY will use 0 for all policies this year. institute="
								+ instituteName + " year=" + Timestep.getCurrentYear() + " output=" + outputLLM);
				return;
			}
			collector.recorder.put(instituteName, "=============== LLM input\n" + inputLLM
					+ "\n=============== LLM output \n" + outputLLM + "\n=============== Crafty input\n" + policies);

			policies.forEach((policyName, value) -> {
				if (IPs.get(instituteName).containsKey(policyName)) {
					policiesValues.get(instituteName).put(policyName, value);
					accumulativePolicies.get(instituteName).merge(policyName, value, Double::sum);
					policiesValues_history.get(instituteName).get(policyName).add(value);
				}
			});
		} else {

			policiesValues_history.get(instituteName).forEach((policyName, list) -> {
				double v = !list.isEmpty() ? list.getLast() : 0;
				policiesValues.get(instituteName).put(policyName, v);

			});
		}
	}

	private static Map<String, Map<String, Double>> initial_supplys = new HashMap<>();// <regionName,serviceName,year,value>

	private void PreparModelOutput(RegionalModelRunner r) {

		targetToCraftyElements.keySet().forEach(target -> {
			Map<String, Double> supplies = new HashMap<>();
			Map<String, Double> weights = new HashMap<>();
			targetToCraftyElements.get(target).forEach((type, hash_element_weight) -> {
				if (type.equals("Service")) {
					hash_element_weight.forEach((name, weight) -> {

						if (Timestep.getTick() == 1) {
							initial_supplys.get(r.R.getName()).put(name, r.getRegionalSupply().get(name));
						}
						double initial_supply = initial_supplys.get(r.R.getName()).get(name);
						initial_supply = initial_supply != 0 ? initial_supply : 1;
						supplies.merge(target, weight * (r.getRegionalSupply().get(name) / initial_supply),
								Double::sum);
						weights.merge(target, weight, Double::sum);
					});
				} else if (type.equals("AFT")) {
					hash_element_weight.forEach((name, weight) -> {
						int nmbr0 = AFTsLoader.hashAgentNbr_initialYear.get(name);
						nmbr0 = nmbr0 != 0 ? nmbr0 : 1;
						supplies.merge(target, weight * AFTsLoader.hashAgentNbr.get(name) / nmbr0, Double::sum);
						weights.merge(target, weight, Double::sum);
					});
				} else if (type.equals("external")) {
					hash_element_weight.forEach((name, weight) -> {
						supplies.merge(target,
								External_variables_Manager.getExternal_variables(name) /* /initila_eternal? */,
								Double::sum);
						weights.merge(target, weight, Double::sum);
					});
				}
			});
			LOGGER.info(target + "; " + supplies.get(target) + ":  " + weights.get(target));
			collector.crafty_Output_history.get(target).add(supplies.get(target) / weights.get(target));
		});
	}

	private String historicalPolicyEffectsToPromptStyle(String intitute_name) {
		String out = "\n" + "Historical policy decisions on subsidy changes (time series, every "
				+ ConfigLoader.config.institution_time_lag + " years): \n  \"policy_decisions\":{";

		for (String policyName : policiesValues_history.get(intitute_name).keySet()) {
			out = out + "\n \"" + policyName + "\":" + policiesValues_history.get(intitute_name).get(policyName);
		}
		policiesValues_history.get(intitute_name).forEach((policyName, list) -> {
		});
		out = out + " \n }";
		return out;
	}

	private String modeloutputToPromptStyle(String intitute_name) {
		HashMap<String, ArrayList<String>> use = new HashMap<>();
		collector.crafty_Output_history.forEach((targetName, list) -> {
			use.put(targetName, new ArrayList<>());
			list.forEach(value -> {
				String v = (Math.floor((value - 1) * 100.0) / 100.0) + "%";
				use.get(targetName).add(v);
			});
		});

		String out = "Historical land use and/or ecosystem services supply relative to baseline values (annual time series):\n {";
		for (String target : instituteTocrafty_Output_Observed.get(intitute_name)) {
			out = out + "\n \"" + target + "\":" + use.get(target);
		}
		out = out + " \n }";
		return out;
	}

	private void setup_targets() {
		ArrayList<Path> list = PathTools.findAllFilePaths(Paths.get(ConfigLoader.config.institutions_directory));
		ArrayList<Path> targetsFiles = PathTools.fileFilter(list, PathTools.asFolder("targets"), ".csv");

		targetsFiles.forEach(file -> {
			Map<String, Map<String, Double>> tmp = new HashMap<>();
			targetToCraftyElements.put(file.getFileName().toString().replace(".csv", "").toLowerCase(), tmp);
			Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(file);
			String name = csv.containsKey("AFT") ? "AFT" : csv.containsKey("Service") ? "Service" : "external";
			tmp.put(name, new HashMap<>());
			for (int i = 0; i < csv.values().iterator().next().size(); i++) {
				int ii = i;
				tmp.values().forEach(hash -> {
					hash.put(csv.get(name).get(ii), Utils.sToD(csv.get("Weight").get(ii)));
				});
			}
		});
	}

	private void setup_instutites() {
		List<File> instututionsFiles = PathTools
				.detectFolders(ConfigLoader.config.institutions_directory + PathTools.asFolder("institutes"));
		instututionsFiles.forEach(dir -> {
			Map<String, Map<String, List<Map<String, Double>>>> tmp = new HashMap<>();
			String intName = dir.getName().replace("institute@", "").toLowerCase();
			instituteTocrafty_Output_Observed.put(intName, new ArrayList<>());
			policiesValues.put(intName, new HashMap<>());
			accumulativePolicies.put(intName, new HashMap<>());
			policiesValues_history.put(intName, new HashMap<>());
			IPs.put(intName, tmp);
			List<Path> policy_List = PathTools.findAllFilePaths(dir.toPath());
			policy_List.forEach(file -> {
				Map<String, Double> listOfCraftyElement = new HashMap<>();
				if (file.getFileName().toString().contains("policy@")) {
					Map<String, List<Map<String, Double>>> policyTypeTocraftyValues = new HashMap<>();
					String policyName = file.getFileName().toString().replace(".csv", "").replace("policy@", "")
							.toLowerCase();
					tmp.put(policyName, policyTypeTocraftyValues);
					System.out.println("!@!   " + intName + ", " + policyName);
					policiesValues_history.get(intName).put(policyName, new ArrayList<>());
					Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(file);

					for (int i = 0; i < csv.values().iterator().next().size(); i++) {
						String[] policy_effects = csv.get("policy_effects").get(i).split("@");
						List<Map<String, Double>> l = new ArrayList<>();
						listOfCraftyElement.put(policy_effects[1], Utils.sToD(csv.get("Weight").get(i)));
						l.add(listOfCraftyElement);
						System.out.println("!!! " + policy_effects[0] + " => " + l);
						policyTypeTocraftyValues.put(policy_effects[0], l);
					}
				} else if (file.getFileName().toString().contains("prompt")) {
					base_prompts.put(intName, PathTools.readFileToString(file));
				} else if (file.getFileName().toString().contains("Targets_to_observe.csv")) {
					String[][] csv = CsvTools.csvReader(file);
					for (int i = 0; i < csv.length; i++) {
						instituteTocrafty_Output_Observed.get(intName).add(csv[i][0].toLowerCase());
					}
				}
			});
		});
//		System.out.println(IPs);
//		IPs.forEach((i,hash)->{
//			System.out.println("!@# "+i+":  "+hash);
//		});
	}

}
