package large_language_models_institutions;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.RegionalModelRunner;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.updaters.RegionsModelRunnerUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;
import large_language_models_institutions.cli.InstitutionTargetsLoader;

public class Targets_Set {
	private static final CustomLogger LOGGER = new CustomLogger(Targets_Set.class);

	private static HashMap<String, Target> targets = new HashMap<>();
	static Map<String, Map<String, Double>> initial_supplys = new HashMap<>();// <regionName,serviceName,year,value>
	Map<String, List<Double>> targetsBaselines = new HashMap<>();

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
		ArrayList<Path> baseline = PathTools.fileFilter(list, "targets_baseline.csv");
		if (baseline != null && !baseline.isEmpty()) {
			Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(baseline.getFirst());
			csv.forEach((targetName, l) -> {
				targetsBaselines.putIfAbsent(targetName, new ArrayList<>());
				l.forEach(v -> targetsBaselines.get(targetName).add(Utils.sToD(v)));
			});
		}

	}

	public void step() {
		LOGGER.info("Targets_Set..");
		targets.values().forEach(target -> {
			RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(r -> {
				target.PreparModelOutput(r);
			});
		});

		writeOutput();

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
	Map<String, Map<String, List<Double>>> listnerSeparetTargets = new LinkedHashMap<>();

	private void writeOutput() {
		int year = Timestep.getCurrentYear() - 1;
		listner.computeIfAbsent("Year", key -> new ArrayList<>()).add((double) year);
		targets.values().forEach(target -> {
			listner.computeIfAbsent(target.getName(), key -> new ArrayList<>()).add(target.getAnnualValue());

			listnerSeparetTargets.putIfAbsent(target.getName(), new LinkedHashMap<>());
			listnerSeparetTargets.get(target.getName()).computeIfAbsent("Year", key -> new ArrayList<>())
					.add((double) year);
			listnerSeparetTargets.get(target.getName()).computeIfAbsent("observed", key -> new ArrayList<>())
					.add(target.getAnnualValue());
			if (!targetsBaselines.isEmpty() && targetsBaselines.get(target.getName()) != null) {
				double v = targetsBaselines.get(target.getName()).get(Timestep.getTick() - 1) == null ? Double.NaN
						: targetsBaselines.get(target.getName()).get(Timestep.getTick() - 1);
				listnerSeparetTargets.get(target.getName()).computeIfAbsent("baseline", key -> new ArrayList<>())
						.add(v);
			}

			listnerSeparetTargets.get(target.getName()).computeIfAbsent("optimist_goals", key -> new ArrayList<>())
					.add(target.getOptimist_goals().getOrDefault(year, Double.NaN));
			listnerSeparetTargets.get(target.getName()).computeIfAbsent("realist_goals", key -> new ArrayList<>())
					.add(target.getRealist_goals().getOrDefault(year, Double.NaN));
			listnerSeparetTargets.get(target.getName()).computeIfAbsent("pessimist_goals", key -> new ArrayList<>())
					.add(target.getPessimist_goals().getOrDefault(year, Double.NaN));
		});

		String dir = PathTools.makeDirectory(ConfigLoader.config.output_folder_name + File.separator + "LLM_outputs");
		CsvTools.writeCSVfile(listner, Paths.get(dir + File.separator + "targets.csv"));
		targets.values().forEach(target -> {
			CsvTools.writeCSVfile(listnerSeparetTargets.get(target.getName()),
					Paths.get(dir + File.separator + target.getName() + ".csv"));
		});

	}

	public static HashMap<String, Target> getTargets() {
		return targets;
	}

}
