package de.cesr.crafty.gui.institutes;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.ArrayList;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;

public class Targets_Set {
	private static final CustomLogger LOGGER = new CustomLogger(Targets_Set.class);
	public static void initialize() {
		GuiInstitutionBootstrap.initialize();
		updateTargetsComparisonData();
	}

	public static Map<String, TargetViewModel> getTargets() {
		return GuiInstitutionBootstrap.targets();
	}

	public static void recordTargetsValues() {
		int completedYear = Timestep.getCurrentYear() - 1;
		getTargets().values().forEach(target -> target.recordCraftyElementValues(completedYear));
		writeTargetsCsvFiles();
	}

	public static void resetTargetsValues() {
		getTargets().values().forEach(TargetViewModel::resetRecordedValues);
	}

	public static void updateTargetsComparisonData() {
		getTargets().values().forEach(target -> {
			target.setReferenceHistory(Map.of());
			target.setGoalHistory(target.configuredGoal(ProjectLoader.getScenario()));
		});

		String scenario = ProjectLoader.getScenario();
		if (scenario == null || scenario.isBlank()) {
			return;
		}

		String comparisonFileName = "targetsToBeCompare_" + scenario + ".csv";
		ArrayList<Path> csvPath = PathTools.fileFilter(PathTools.asFolder("institutes"), comparisonFileName);
		if (csvPath == null || csvPath.isEmpty()) {
			return;
		}

		Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(csvPath.getFirst(), true);
		if (csv == null || csv.isEmpty()) {
			return;
		}

		List<String> years = findColumn(csv, "Year");
		if (years == null) {
			LOGGER.warn(comparisonFileName + " has no Year column. Target comparison data skipped.");
			return;
		}

		getTargets().values().forEach(target -> {
			List<String> reference = findColumn(csv, target.getName() + "_reference");
			List<String> goal = findColumn(csv, target.getName() + "_goal");
			if (goal == null) {
				goal = findColumn(csv, target.getName());
			}

			if (reference != null) {
				target.setReferenceHistory(parseYearValueMap(years, reference));
			}
			if (goal != null) {
				target.setGoalHistory(parseYearValueMap(years, goal));
			}
		});
	}

	private static List<String> findColumn(Map<String, List<String>> csv, String columnName) {
		for (Map.Entry<String, List<String>> entry : csv.entrySet()) {
			if (entry.getKey().trim().equalsIgnoreCase(columnName)) {
				return entry.getValue();
			}
		}
		return null;
	}

	private static Map<Integer, Double> parseYearValueMap(List<String> years, List<String> values) {
		Map<Integer, Double> result = new LinkedHashMap<>();
		int size = Math.min(years.size(), values.size());
		for (int i = 0; i < size; i++) {
			String year = years.get(i);
			String value = values.get(i);
			if (year == null || year.isBlank() || value == null || value.isBlank()) {
				continue;
			}
			result.put((int) Utils.sToD(year), Utils.sToD(value));
		}
		return result;
	}

	private static void writeTargetsCsvFiles() {
		if (ConfigLoader.config.output_folder_name == null || ConfigLoader.config.output_folder_name.isBlank()) {
			return;
		}

		String dir = PathTools.makeDirectory(ConfigLoader.config.output_folder_name + File.separator + "targets");
		getTargets().values().forEach(target -> CsvTools.writeCSVfile(targetCsvData(target),
				Paths.get(dir + File.separator + target.getName() + ".csv")));
	}

	private static Map<String, List<Double>> targetCsvData(TargetViewModel target) {
		TreeSet<Integer> years = new TreeSet<>();
		years.addAll(target.getHistory().keySet());
		years.addAll(target.getReferenceHistory().keySet());
		years.addAll(target.getGoalHistory().keySet());

		Map<String, List<Double>> csv = new LinkedHashMap<>();
		csv.put("Year", new ArrayList<>());
		csv.put("observed", new ArrayList<>());
		if (!target.getReferenceHistory().isEmpty()) {
			csv.put("baseline", new ArrayList<>());
		}
		if (!target.getGoalHistory().isEmpty()) {
			csv.put("goal", new ArrayList<>());
		}

		years.forEach(year -> {
			csv.get("Year").add((double) year);
			csv.get("observed").add(target.getHistory().getOrDefault(year, Double.NaN));
			if (csv.containsKey("baseline")) {
				csv.get("baseline").add(target.getReferenceHistory().getOrDefault(year, Double.NaN));
			}
			if (csv.containsKey("goal")) {
				csv.get("goal").add(target.getGoalHistory().getOrDefault(year, Double.NaN));
			}
		});

		return csv;
	}
}
