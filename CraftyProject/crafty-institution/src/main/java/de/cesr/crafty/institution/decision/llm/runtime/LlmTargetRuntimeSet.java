package de.cesr.crafty.institution.decision.llm.runtime;

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
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;
public final class LlmTargetRuntimeSet {
	private static final CustomLogger LOGGER = new CustomLogger(LlmTargetRuntimeSet.class);

	private static HashMap<String, LlmTargetState> targets = new HashMap<>();
	Map<String, List<Double>> targetsBaselines = new HashMap<>();

	public void step() {
		LOGGER.info("Targets_Set..");
		targets.values().forEach(LlmTargetState::prepareModelOutput);
		writeOutput();
	}

	public void reset() {
		targets = new HashMap<>();
		targetsBaselines.clear();
		listner.clear();
		listnerSeparetTargets.clear();
		loadBaselines();
	}

	private void loadBaselines() {
		ArrayList<Path> list = PathTools.findAllFilePaths(Paths.get(ConfigLoader.config.institutions_directory));
		ArrayList<Path> baseline = PathTools.fileFilter(list, "targets_baseline.csv");
		if (baseline != null && !baseline.isEmpty()) {
			Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(baseline.getFirst());
			csv.forEach((targetName, values) -> {
				targetsBaselines.putIfAbsent(targetName, new ArrayList<>());
				values.forEach(value -> targetsBaselines.get(targetName).add(Utils.sToD(value)));
			});
		}
	}

	public static void register(String runtimeId, LlmTargetState target) {
		targets.put(runtimeId, target);
	}

	public void initializeBaselines() {
		targets.values().forEach(LlmTargetState::initializeObservationBaselines);
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

}
