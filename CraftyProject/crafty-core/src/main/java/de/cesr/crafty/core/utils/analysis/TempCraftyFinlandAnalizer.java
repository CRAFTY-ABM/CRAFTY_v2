package de.cesr.crafty.core.utils.analysis;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.main.MainHeadless;
import de.cesr.crafty.core.modelRunner.ModelRunner;
import de.cesr.crafty.core.output.Listener;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.general.Utils;

public class TempCraftyFinlandAnalizer {
// crafty one run 
// loop runs
// validation data updater
// compariason between crafty and validation and re-run crafty
	static ModelRunner runner;
	static HashMap<String, LinkedHashMap<Integer, Double>> va = new HashMap<>();
	static HashMap<String, LinkedHashMap<Integer, Double>> output = new HashMap<>();

	public static void main(String[] args) {
		System.out.println("--Starting CRAFTY execution--");
		MainHeadless.initializeConfig(args);
//  Initize validation terms
		InitValidator();
//		start loop
		for (int simStep = 0; simStep < 1; simStep++) {
			initCraftyRunner();
			injectNewInput(simStep);
			runner.run();
			listner();
//		condition to run again

		}

// if yes => change config targeted and run again
//		end loop
	}

	private static void listner() {
		output.put("MetsoT", new LinkedHashMap<>());
		output.put("Metso", new LinkedHashMap<>());
		int metsoTIndex = Utils.indexof("MetsoT", Listener.newAftsInLandListener[0]);
		int metsoIndex = Utils.indexof("Metso", Listener.newAftsInLandListener[0]);
		for (int j = 5; j < Timestep.getSize(); j++) {// Timestep.getStartYear()+5=2008
			output.get("MetsoT").put(j + Timestep.getStartYear(),
					Utils.sToD(Listener.newAftsInLandListener[j][metsoTIndex]));
			output.get("Metso").put(j + Timestep.getStartYear(),
					Utils.sToD(Listener.newAftsInLandListener[j][metsoIndex]));
		}

		output.forEach((name, list) -> {

			System.out.println(name);
			list.forEach((year, value) -> {
				System.out.println(year + ": " + value + " => " + va.get(name).get(year));
			});
		});
	}

	private static void injectNewInput(int step) {
// increase carbon wait ultility 
		CellsLoader.regions.values().forEach(r -> {
			System.out.println(r.getServicesHash().get("Carbon").getWeights());
			for (int j = Timestep.getStartYear(); j < Timestep.getEndtYear(); j++) {
				r.getServicesHash().get("Carbon").getWeights().put(j, 2.);
			}
		});
	}

	static void initCraftyRunner() {
		ProjectLoader.pathInitialisation(Paths.get(ConfigLoader.config.project_path));
		runner = new ModelRunner();
		runner.start();
		runner.initialzeRun();
	}

	private static void InitValidator() {
		va.put("Metso", new LinkedHashMap<>());
		va.put("MetsoT", new LinkedHashMap<>());
		va.put("M_Accumulation", new LinkedHashMap<>());
		va.put("T_Accumulation", new LinkedHashMap<>());

		List<Double> m = List.of(14.06, 21.93, 40.29, 60.12, 75.04, 76.11, 81.00, 59.53, 53.05, 33.88, 42.94, 46.96,
				55.21);
		List<Double> t = List.of(65.00, 65.00, 50.07, 53.02, 31.02, 19.60, 40.27, 13.72, 24.31, 18.55, 25.41, 24.03,
				35.48);

		for (int i = 0; i <= 12; i++) {
			va.get("Metso").put(i + 2008, m.get(i));
			va.get("MetsoT").put(i + 2008, t.get(i));
		}

		double d = 0;
		for (Map.Entry<Integer, Double> entry : va.get("Metso").entrySet()) {
			d += entry.getValue();
			va.get("M_Accumulation").put(entry.getKey(), d);
		}
		d = 0;
		for (Map.Entry<Integer, Double> entry : va.get("MetsoT").entrySet()) {
			d += entry.getValue();
			va.get("T_Accumulation").put(entry.getKey(), d);
		}
		va.forEach((k, v) -> {
			System.out.println(k + " -> " + v.values());
		});
	}

}
