package de.cesr.crafty.core.utils.non_java_code_controller;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;

public class External_variables_Manager_manager {
	private static final CustomLogger LOGGER = new CustomLogger(External_variables_Manager_manager.class);
	private static Map<String, Double> external_variables = new HashMap<>();

	public static void Initializer() {
		if (!Paths.get(ConfigLoader.config.external_variable_values_directory).toFile().isDirectory()) {
			LOGGER.info("The external variables directory in the config-yaml file has not been defined:");
			return;
		}
	}

	public static Map<String, Double> getExternal_variables() {
		return external_variables;
	}

	public static double getExternal_variables(String VariableName) {
		if (external_variables.get(VariableName) != null) {
			return external_variables.get(VariableName);
		}
		LOGGER.warn("Variable Name not found (" + VariableName + ") ->" + VariableName + "= 0 ");
		return Double.NaN;
	}

	public static void valuesInjector(Path path) {
		external_variables.clear();

		Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(path);
		if (csv.keySet().size() != 2) {
			LOGGER.error("The file is not in the correct format. It must contain exactly two columns [variable,value]: "
					+ path);
			return;
		} else if (csv.values().iterator().next().size() == 0) {
			LOGGER.info("No external variables will be considered. A colmun in the file isEmpty: " + path);
			return;
		}

		for (int i = 0; i < csv.values().iterator().next().size(); i++) {
			external_variables.put(csv.get("variable").get(i), Utils.sToD(csv.get("value").get(i)));
		}
		LOGGER.info("External variables successfully imported from:" + path);
		LOGGER.info("External_variables:" + external_variables);
	}

	public static void valuesInjector() {
		external_variables.clear();
		LOGGER.info("Seaching for external_variable in directory: "
				+ ConfigLoader.config.external_variable_values_directory);

		System.out.println("== " + ConfigLoader.config.external_variable_values_directory);
		ArrayList<Path> list = PathTools
				.findAllFilePaths(Paths.get(ConfigLoader.config.external_variable_values_directory));
		ArrayList<Path> ps = PathTools.fileFilter(list, "year_" + (Timestep.getCurrentYear() - 1));
		if (ps == null || ps.isEmpty()) {
			LOGGER.info(
					"The external variables directory isEmpty or null (No external variables considered in this year"
							+ (Timestep.getCurrentYear() - 1) + " expected = year_" + (Timestep.getCurrentYear() - 1)
							+ ") ");
			return;
		}
		Path path = ps.get(0);
		Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(path);
		if (csv.keySet().size() != 2) {
			LOGGER.error("The file is not in the correct format. It must contain exactly two columns [variable,value]: "
					+ path);
			return;
		} else if (csv.values().iterator().next().size() == 0) {
			LOGGER.info("No external variables will be considered. A colmun in the file isEmpty: " + path);
			return;
		}

		for (int i = 0; i < csv.values().iterator().next().size(); i++) {
			external_variables.put(csv.get("variable").get(i), Utils.sToD(csv.get("value").get(i)));
		}
		LOGGER.info("External variables successfully imported from:" + path);
		LOGGER.info("External_variables:" + external_variables);
	}

}
