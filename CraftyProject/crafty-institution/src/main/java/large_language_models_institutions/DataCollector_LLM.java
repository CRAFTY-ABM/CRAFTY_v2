package large_language_models_institutions;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.RegionalModelRunner;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;

/**
 * Collects and stores data from crafty model simulation for analysis.
 */
public class DataCollector_LLM {

//	private static final CustomLogger LOGGER = new CustomLogger(DataCollector_LLM.class);
	static String instititeDirectory;

	private String[][] serviceTxSu;
	private String[][] aftTxSu;
	private String[][] policies;
	private String[][] policiesEffect;
	private String[][] targets;

	Map<String, Double> policyListener = new HashMap<>();// <instutition@policy,policyValue>
	Map<String, Double> policyEffectsListner = new HashMap<>();// <instutition@policy@type@element,policyValue>
	HashMap<String, String> recorder = new HashMap<>();// <institute,promp_outputs>
	HashMap<String, ArrayList<Double>> crafty_Output_history = new HashMap<>();// <target,
	// List_time_stateObservation>

	public DataCollector_LLM(LLM_connector connector) {
		init(connector);
		int l = Timestep.getSize() + 1;
		serviceTxSu = new String[l][ServiceSet.getServicesList().size() + 1];
		aftTxSu = new String[l][AFTsLoader.getActivateAFTsHash().size() + 1];
		policies = new String[l][policyListener.size() + 1];
		policiesEffect = new String[l][policyEffectsListner.size() + 1];
		targets = new String[l][crafty_Output_history.size() + 1];

		targets[0][0] = policiesEffect[0][0] = policies[0][0] = aftTxSu[0][0] = serviceTxSu[0][0] = "Year";
		for (int i = 0; i < ServiceSet.getServicesList().size(); i++)
			serviceTxSu[0][i + 1] = ServiceSet.getServicesList().get(i);
		int i = 1;
		for (String aftName : AFTsLoader.getActivateAFTsHash().keySet())
			aftTxSu[0][i++] = aftName;
		i = 1;
		for (String p : policyListener.keySet())
			policies[0][i++] = p;
		i = 1;
		for (String pe : policyEffectsListner.keySet())
			policiesEffect[0][i++] = pe;
		i = 1;
		for (String pe : crafty_Output_history.keySet()) {
			targets[0][i] = pe + "_observed";
			i++;
		}
	}

	private void init(LLM_connector connector) {
		connector.IPs.forEach((instName, hash) -> {
			hash.forEach((policyName, has) -> {
				policyListener.put(instName + "@" + policyName, 0.);
				has.forEach((type, list) -> {
					list.forEach(ha -> {
						ha.forEach((ele, weight) -> {
							policyEffectsListner.put(instName + "@" + policyName + "@" + type + "@" + ele, 0.);
						});
					});
				});
			});
		});
		connector.targetToCraftyElements.keySet().forEach(target_name -> {
			crafty_Output_history.put(target_name, new ArrayList<>());
		});
	}

	public void outputFiles(RegionalModelRunner r) {
		instititeDirectory = PathTools.makeDirectory(
				ConfigLoader.config.output_folder_name + File.separator + "LLM_outputs" + File.separator);
		outputServicesTxSuCsv(r);
		outputLandTxSuCsv();
		outputPolicies();
		outputPoliciesEffects();
		outputTargets();
		outputs();
	}

	private void outputs() {
		String folder = PathTools
				.makeDirectory(ConfigLoader.config.output_folder_name + File.separator + "LLM_outputs");
		recorder.forEach((inst, txt) -> {
			String instFolder = PathTools.makeDirectory(folder + File.separator + inst);
			PathTools.writeFile(instFolder + File.separator + inst + "_" + (Timestep.getCurrentYear() - 1) + ".txt",
					txt, false);
		});
	}

	private void outputServicesTxSuCsv(RegionalModelRunner r) {
		// find crafty ouput add taxes/subsidies file
		// catch
		serviceTxSu[Timestep.getTick()][0] = String.valueOf(Timestep.getCurrentYear() - 1);
		for (int i = 0; i < ServiceSet.getServicesList().size(); i++) {
			Double t = r.R.getServicesHash().get(ServiceSet.getServicesList().get(i)).getTaxes_subsidies()
					.get(Timestep.getCurrentYear() - 1);
			serviceTxSu[Timestep.getTick()][i + 1] = String.valueOf(t);
		}
		Path csv = Paths.get(instititeDirectory + "services-taxes_subsidies.csv");
		CsvTools.writeCSVfile(serviceTxSu, csv);
	}

	private void outputLandTxSuCsv() {
		int iteration = Timestep.getTick();
		aftTxSu[iteration][0] = String.valueOf(Timestep.getCurrentYear() - 1);
		for (Aft aft : AFTsLoader.getActivateAFTsHash().values()) {
			Double t = aft.getLand_taxes_subsidies().get(Timestep.getCurrentYear() - 1);
			aftTxSu[iteration][Utils.indexof(aft.getLabel(), aftTxSu[0])] = String.valueOf(t);
		}
		Path csv = Paths.get(instititeDirectory + "land-taxes_subsidies.csv");
		CsvTools.writeCSVfile(aftTxSu, csv);
	}

	private void outputPolicies() {
		policies[Timestep.getTick()][0] = String.valueOf(Timestep.getCurrentYear() - 1);
		AtomicInteger index = new AtomicInteger(1);
		policyListener.forEach((pName, pValue) -> {
			policies[Timestep.getTick()][index.getAndIncrement()] = String.valueOf(pValue);
		});
		Path csv = Paths.get(instititeDirectory + "policies.csv");
		CsvTools.writeCSVfile(policies, csv);
	}

	private void outputPoliciesEffects() {
		policiesEffect[Timestep.getTick()][0] = String.valueOf(Timestep.getCurrentYear() - 1);
		AtomicInteger index = new AtomicInteger(1);
		policyEffectsListner.forEach((pName, pValue) -> {
			policiesEffect[Timestep.getTick()][index.getAndIncrement()] = String.valueOf(pValue);
		});
		Path csv = Paths.get(instititeDirectory + "policiesEffects.csv");
		CsvTools.writeCSVfile(policiesEffect, csv);
	}

	private void outputTargets() {
		int iteration = Timestep.getTick();
		targets[iteration][0] = String.valueOf(Timestep.getCurrentYear() - 1);
		AtomicInteger index = new AtomicInteger(1);
		crafty_Output_history.forEach((targetName, listValues) -> {
			targets[iteration][index.get()] = String.valueOf(listValues.get(iteration - 1));
			index.getAndIncrement();
		});
		Path csv = Paths.get(instititeDirectory + "targets.csv");
		CsvTools.writeCSVfile(targets, csv);
	}

}