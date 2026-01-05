package crafty;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.RegionalModelRunner;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.main.MainHeadless;
import de.cesr.crafty.core.updaters.RegionsModelRunnerUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;
import institutions.InstitutionManager;
import utils.InstitutionOutput;
import utils.ModelOutputProvider;
import utils.TargetModelOutput;

/*
 * @author Mohamed Byari
 *
 */

public class Connector implements ModelOutputProvider {
	private static final CustomLogger LOGGER = new CustomLogger(Connector.class);

	private InstitutionManager institutionManager;
	private HashMap<String, ArrayList<Double>> prepModelOutput = new HashMap<>();// <target, List_time_stateObservation>
	Map<String, Double> policyListener = new HashMap<>();// <instutition@policy,policyValue>
	Map<String, Double> policyEffectsListner = new HashMap<>();// <instutition@policy@service,policyValue>
	///////////////////
	private Map<String, Map<String, List<String>>> IPTs = new HashMap<>(); // <instutition,policy,targets>
	private Map<String, Map<String, Double>> targetToServiceHash = new HashMap<>();// <target,service,weight>
	Map<String, Map<String, Double>> policyEffects = new HashMap<>();// <policy, service,weight>

	Map<String, Double> targetToValue = new HashMap<>();

	public Connector(InstitutionManager institutionManager) {
		this.institutionManager = institutionManager;
//		 based in the jason detect csv file with instututionName, policyName, Target Name
		this.institutionManager.getInstitutions().forEach(institut -> {
			institut.getPolicies().forEach(poli -> {
				policyListener.put(institut.getName() + "@" + poli.getPolicyName(), 0.0);
				ArrayList<String> list = new ArrayList<>();
				poli.getInputNameToGoal().forEach((targetName, v) -> {
					prepModelOutput.put(targetName, new ArrayList<>());
					list.add(targetName);
					targetToValue.merge(targetName, v, Double::sum);
				});
			});
		});

		initializeIPTs();
		InitializePolicies();
		InitializeTargets();
		DataCollector.init(this);
	}

	private void initializeIPTs() {
		institutionManager.getInstitutions().forEach(institut -> {
			getIPTs().put(institut.getName(), new HashMap<>());
			institut.getPolicies().forEach(poli -> {
				ArrayList<String> list = new ArrayList<>();
				poli.getInputNameToGoal().keySet().forEach(target -> {
					list.add(target);
				});
				getIPTs().get(institut.getName()).put(poli.getPolicyName(), list);
			});
		});
	}

	private void InitializePolicies() {
		ArrayList<Path> directory = PathTools.findAllFilePaths(Paths.get(ConfigLoader.config.institutions_directory));
		getIPTs().forEach((instituteName, poliHash) -> {
			poliHash.keySet().forEach(policyName -> {
				policyEffects.put(instituteName + "@" + policyName, new HashMap<>());
				ArrayList<Path> p = PathTools.fileFilter(directory, instituteName, policyName, "policy_effects");
				if (p == null) {
					LOGGER.error("Policy path not fund: " + instituteName + "@" + policyName + "@policy_effects.csv");
				} else {
					// read file and associet it into policyEffects
					Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(p.get(0));
					for (int i = 0; i < csv.values().iterator().next().size(); i++) {
						if (csv.containsKey("Service")) {
							policyEffects.get(instituteName + "@" + policyName).put(csv.get("Service").get(i),
									Utils.sToD(csv.get("Weight").get(i)));
							policyEffectsListner.put(instituteName + "@" + policyName + "@" + csv.get("Service").get(i),
									0.);
						} else if (csv.containsKey("AFT")) {
							policyEffects.get(instituteName + "@" + policyName).put(csv.get("AFT").get(i),
									Utils.sToD(csv.get("Weight").get(i)));
							policyEffectsListner.put(instituteName + "@" + policyName + "@" + csv.get("AFT").get(i),
									0.);
						}
					}
				}
			});
		});
	}

	private void InitializeTargets() {
		ArrayList<Path> directory = PathTools.findAllFilePaths(Paths.get(ConfigLoader.config.institutions_directory));
		getIPTs().forEach((in, poliHash) -> {
			poliHash.forEach((name, targets) -> {
				targets.forEach(target -> {
					targetToServiceHash.put(target, new HashMap<>());
					ArrayList<Path> p = PathTools.fileFilter(directory, "target@" + target);
					if (p == null) {
						LOGGER.error("Target path not fund: " + "target@" + target);
					} else {
						Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(p.get(0));
						for (int i = 0; i < csv.values().iterator().next().size(); i++) {
							if (csv.containsKey("Service")) {
								targetToServiceHash.get(target).put(csv.get("Service").get(i),
										Utils.sToD(csv.get("Weight").get(i)));
							} else if (csv.containsKey("AFT")) {
								targetToServiceHash.get(target).put(csv.get("AFT").get(i),
										Utils.sToD(csv.get("Weight").get(i)));
							}
						}
					}
				});
			});
		});
	}

	private void PreparModelOutput(RegionalModelRunner r) {
		targetToServiceHash.keySet().forEach(target -> {
			Map<String, Double> supplies = new HashMap<>();
			Map<String, Double> weights = new HashMap<>();
			targetToServiceHash.get(target).forEach((name, weight) -> {
				if (ServiceSet.getServicesList().contains(name)) {
					supplies.merge(target, weight * r.getRegionalSupply().get(name), Double::sum);
					weights.merge(target, weight, Double::sum);
				} else if (AFTsLoader.getAftHash().keySet().contains(name)) {
					supplies.merge(target, weight * AFTsLoader.hashAgentNbr.get(name), Double::sum);
					weights.merge(target, weight, Double::sum);
					System.out.println("If is an AFT not implemeted yet: Add sub/tex to Management ");
				}
			});
			prepModelOutput.get(target).add(supplies.get(target) / weights.get(target));
		});
	}

	@Override
	public TargetModelOutput step(InstitutionOutput institutionOutput) {
		MainHeadless.runner.step();
		RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(r -> {
			applyPolicyEffects(institutionOutput, r);
			PreparModelOutput(r);
			DataCollector.outputFiles(r);
		});
		TargetModelOutput targetModelOutput = getOutput();
		return targetModelOutput;
	}

	private void applyPolicyEffects(InstitutionOutput institutionOutput, RegionalModelRunner r) {
		// Apply each policy effect
		for (Map.Entry<String, HashMap<String, Double>> entryInstitution : institutionOutput.output().entrySet()) {
			String institutionName = entryInstitution.getKey();
			HashMap<String, Double> policyValues = entryInstitution.getValue();
			for (Map.Entry<String, Double> entryPolicies : policyValues.entrySet()) {
				String policyName = entryPolicies.getKey();
				Double policyValue = entryPolicies.getValue();
				System.out.println(institutionName + ", " + policyName + "-> " + policyValue);
				// Update current policy values map
				policyListener.put(institutionName + "@" + policyName, policyValue);
				applyOnePolicy(r, institutionName, policyName, policyValue);
			}
		}
	}

	private void applyOnePolicy(RegionalModelRunner r, String instuteName, String policyName, double policyValue) {
		if (!policyEffects.keySet().contains(instuteName + "@" + policyName)) {
			LOGGER.warn("Unknown policy or instute: " + policyName + ", " + instuteName);
			return;
		}
		policyEffects.get(instuteName + "@" + policyName).forEach((serviceOrAFT, wieght) -> {
			if (ServiceSet.getServicesList().contains(serviceOrAFT)) {
				r.R.getServicesHash().get(serviceOrAFT).getTaxes_subsidies().merge(Timestep.getCurrentYear() + 1,
						wieght * policyValue, Double::sum);
			} else if (AFTsLoader.getAftHash().keySet().contains(serviceOrAFT)) {
				// add tax to AFt
				AFTsLoader.getAftHash().get(serviceOrAFT).getLand_taxes_subsidies().merge(Timestep.getCurrentYear() + 1,
						wieght * policyValue, Double::sum);
			}
			policyEffectsListner.put(instuteName + "@" + policyName + "@" + serviceOrAFT, wieght * policyValue);
		});
	}

	@Override
	public HashMap<String, ArrayList<Double>> prepModelOutput() {
		return prepModelOutput;
	}

	@Override
	public HashMap<String, HashMap<String, Double>> prepEstimatedQuantities() {
		HashMap<String, HashMap<String, Double>> estimatedQuantities = new HashMap<>();

		institutionManager.getInitPolicies().output().forEach((instututionName, policiesMap) -> {
			HashMap<String, Double> instute = new HashMap<>();
			policiesMap.keySet().forEach(policyName -> {
				instute.put(policyName, 1000.);
			});
			estimatedQuantities.put(instututionName, instute);
		});
		return estimatedQuantities;

	}

	@Override
	public HashMap<String, Double> prepBudget() {
		HashMap<String, Double> budget = new HashMap<>();
		institutionManager.getInitPolicies().output().keySet().forEach(instututionName -> {
			budget.put(instututionName, 10000.);
		});
		return budget;
	}

	public Map<String, Map<String, List<String>>> getIPTs() {
		return IPTs;
	}

	public void setIPTs(Map<String, Map<String, List<String>>> iPTs) {
		IPTs = iPTs;
	}

}
