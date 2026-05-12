package large_language_models_institutions;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cli.InstitutionTargetsLoader;
import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.RegionalModelRunner;
import de.cesr.crafty.core.updaters.RegionsModelRunnerUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;

public class Targets_Set {
	private static final CustomLogger LOGGER = new CustomLogger(Targets_Set.class);

	private static HashMap<String, Target> targets = new HashMap<>();
	static Map<String, Map<String, Double>> initial_supplys = new HashMap<>();// <regionName,serviceName,year,value>

	public void setup() {
		ArrayList<Path> list = PathTools.findAllFilePaths(Paths.get(ConfigLoader.config.institutions_directory));
		targets = new HashMap<String, Target>(
				InstitutionTargetsLoader.loadTargetsAsMap(PathTools.fileFilter(list, "targets.yaml").getFirst()));
		targets.values().forEach(t -> {
			LOGGER.info("Target Name = " + t.getName() + ", type= " + t.getType() + " =>" + t.getCraftyElem());
		});

		RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(r -> {
			initialSupp(r);
		});
	}

	public void step() {
		targets.values().forEach(target -> {
			RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(r -> {
				target.PreparModelOutput(r);
			});
		});

		writeOutput();
//		targets.forEach((k, v) -> {
//			System.out.println(k + ", " + v.getType());
//			System.out.println(v.getCraftyElem());
//			System.out.println(v.getHistory());
//			System.out.println("----||---");
//
//		});
	}

	private void initialSupp(RegionalModelRunner r) {
		initial_supplys.put(r.R.getName(), new HashMap<>());
		targets.forEach((targetName, target) -> {
			if (target.getType().equals("Service")) {
				target.getCraftyElem().forEach((serviceName, weight) -> {
					initial_supplys.get(r.R.getName()).put(serviceName, r.getRegionalSupply().get(serviceName));
				});
			}
		});
//		System.out.println("initial_supplys:   " + initial_supplys);
	}

	Map<String, List<Double>> listner = new LinkedHashMap<>();

	private void writeOutput() {
		listner.computeIfAbsent("Year", key -> new ArrayList<>()).add((double) Timestep.getCurrentYear());
		targets.values().forEach(target -> {
			listner.computeIfAbsent(target.getName(), key -> new ArrayList<>()).add(target.getAnnualValue());
		});

		String dir = PathTools.makeDirectory(ConfigLoader.config.output_folder_name + File.separator + "LLM_outputs");
		CsvTools.writeCSVfile(listner, Paths.get(dir + File.separator + "targets.csv"));
	}

	public static HashMap<String, Target> getTargets() {
		return targets;
	}

}
