package de.cesr.crafty.institution.decision.fuzzy.integration;

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

public final class FuzzyExternalValues {
	private static final CustomLogger LOGGER = new CustomLogger(FuzzyExternalValues.class);
	private static final Map<String, Double> externalValues = new HashMap<>();

	private static boolean enabled;

	private FuzzyExternalValues() {
	}

	public static void initialize() {
		if (!Paths.get(ConfigLoader.config.external_variable_values_directory).toFile().isDirectory()) {
			LOGGER.info("The external variables directory in the config-yaml file has not been defined:");
			return;
		}
		enabled = true;
	}

	public static double value(String variableName) {
		if (externalValues.get(variableName) != null) {
			return externalValues.get(variableName);
		}
		LOGGER.warn("Variable Name not found (" + variableName + ") ->" + variableName + "=0 ");
		return 0;
	}

	public static void injectValues() {
		externalValues.clear();
		if (enabled) {
			LOGGER.info("Seaching for external_variable in directory: "
					+ ConfigLoader.config.external_variable_values_directory);
			ArrayList<Path> list = PathTools
					.findAllFilePaths(Paths.get(ConfigLoader.config.external_variable_values_directory));
			ArrayList<Path> ps = PathTools.fileFilter(list, "year_" + (Timestep.getCurrentYear()-1));
			if (ps == null || ps.isEmpty()) {
				LOGGER.info(
						"The external variables directory isEmpty or null (No external variables considered in this year"
								+ (Timestep.getCurrentYear()-1) + " expected = year_" + (Timestep.getCurrentYear()-1)
								+ ") ");
				return;
			}
			Path path = ps.get(0);
			Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(path);
			if (csv.keySet().size() != 2) {
				LOGGER.error(
						"The file is not in the correct format. It must contain exactly two columns [variable,value]: "
								+ path);
				return;
			} else if (csv.values().iterator().next().size() == 0) {
				LOGGER.info("No external variables will be considered. A colmun in the file isEmpty: " + path);
				return;
			}

			for (int i = 0; i < csv.values().iterator().next().size(); i++) {
				externalValues.put(csv.get("variable").get(i), Utils.sToD(csv.get("value").get(i)));
			}
			LOGGER.info("External variables successfully imported from:" + path);
			LOGGER.info("External_variables:" + externalValues);

		}
	}
}
