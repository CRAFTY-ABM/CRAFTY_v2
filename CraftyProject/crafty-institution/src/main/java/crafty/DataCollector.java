package crafty;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
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
public class DataCollector {
	static Connector connector;
	static String instititeDirectory;

	private static String[][] serviceTxSu;
	private static String[][] aftTxSu;
	private static String[][] policies;
	private static String[][] policiesEffect;
	private static String[][] targets;
//	private static String[][] targetsObservation;

	public static void init(Connector connector) {
		DataCollector.connector = connector;
		int l = Timestep.getSize() + 1;
		serviceTxSu = new String[l][ServiceSet.getServicesList().size() + 1];
		aftTxSu = new String[l][AFTsLoader.getActivateAFTsHash().size() + 1];
		policies = new String[l][connector.policyListener.size() + 1];
		policiesEffect = new String[l][connector.policyEffectsListner.size() + 1];
		targets = new String[l][2 * connector.prepModelOutput().size() + 1];

		targets[0][0] = policiesEffect[0][0] = policies[0][0] = aftTxSu[0][0] = serviceTxSu[0][0] = "Year";
		for (int i = 0; i < ServiceSet.getServicesList().size(); i++)
			serviceTxSu[0][i + 1] = ServiceSet.getServicesList().get(i);
		int i = 1;
		for (String aftName : AFTsLoader.getActivateAFTsHash().keySet())
			aftTxSu[0][i++] = aftName;
		i = 1;
		for (String p : connector.policyListener.keySet())
			policies[0][i++] = p;
		i = 1;
		for (String pe : connector.policyEffectsListner.keySet())
			policiesEffect[0][i++] = pe;
		i = 1;
		for (String pe : connector.prepModelOutput().keySet()) {
			targets[0][i] = pe + "_observed";
			targets[0][connector.prepModelOutput().size() + i] = pe + "_target";
			i++;
		}
	}

	public static void outputFiles(RegionalModelRunner r) {
		instititeDirectory = PathTools.makeDirectory(
				ConfigLoader.config.output_folder_name + File.separator + "instititions" + File.separator);
		outputServicesTxSuCsv(r);
		outputLandTxSuCsv();
		outputPolicies();
		outputPoliciesEffects();
		outputTargets();
	}

	private static void outputServicesTxSuCsv(RegionalModelRunner r) {
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

	private static void outputLandTxSuCsv() {
		aftTxSu[Timestep.getTick()][0] = String.valueOf(Timestep.getCurrentYear() - 1);
		for (Aft aft : AFTsLoader.getActivateAFTsHash().values()) {
			Double t = aft.getLand_taxes_subsidies().get(Timestep.getCurrentYear() - 1);
			aftTxSu[Timestep.getTick()][Utils.indexof(aft.getLabel(), aftTxSu[0])] = String.valueOf(t);
		}
		Path csv = Paths.get(instititeDirectory + "land-taxes_subsidies.csv");
		CsvTools.writeCSVfile(aftTxSu, csv);
	}

	private static void outputPolicies() {
		policies[Timestep.getTick()][0] = String.valueOf(Timestep.getCurrentYear() - 1);
		AtomicInteger index = new AtomicInteger(1);
		connector.policyListener.forEach((pName, pValue) -> {
			policies[Timestep.getTick()][index.getAndIncrement()] = String.valueOf(pValue);
		});
		Path csv = Paths.get(instititeDirectory + "policies.csv");
		CsvTools.writeCSVfile(policies, csv);
	}

	private static void outputPoliciesEffects() {
		policiesEffect[Timestep.getTick()][0] = String.valueOf(Timestep.getCurrentYear() - 1);
		AtomicInteger index = new AtomicInteger(1);
		connector.policyEffectsListner.forEach((pName, pValue) -> {
			policiesEffect[Timestep.getTick()][index.getAndIncrement()] = String.valueOf(pValue);
		});
		Path csv = Paths.get(instititeDirectory + "policiesEffects.csv");
		CsvTools.writeCSVfile(policiesEffect, csv);
	}

	private static void outputTargets() {
		int iteration = Timestep.getTick();
		targets[iteration][0] = String.valueOf(Timestep.getCurrentYear() - 1);
		AtomicInteger index = new AtomicInteger(1);
		connector.prepModelOutput().forEach((targetName, listValues) -> {
			targets[iteration][index.get()] = String.valueOf(listValues.get(iteration - 1));
			targets[iteration][index.get() + connector.prepModelOutput().size()] = String
					.valueOf(connector.targetToValue.get(targetName));
			index.getAndIncrement();
		});
		Path csv = Paths.get(instititeDirectory + "targets.csv");
		CsvTools.writeCSVfile(targets, csv);
	}

}