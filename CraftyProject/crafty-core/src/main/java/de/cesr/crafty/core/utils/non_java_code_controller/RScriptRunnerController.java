package de.cesr.crafty.core.utils.non_java_code_controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.updaters.Timestep;

public class RScriptRunnerController {

	public static void runScriptsForCurrentYear() {

		if (ConfigLoader.config == null || ConfigLoader.config.r_script_runner == null) {
			return;
		}

		RScriptRunnerConfig config = ConfigLoader.config.r_script_runner;

		if (!config.isEnabled()) {
			return;
		}

		int year = Timestep.getCurrentYear();

		List<RScriptRunnerConfig.RScriptTaskConfig> scripts = config.scriptsForYear(year);

		if (scripts.isEmpty()) {
			return;
		}

		System.out.println("[R] Found " + scripts.size() + " R script(s) for year " + year);

		for (RScriptRunnerConfig.RScriptTaskConfig script : scripts) {
			runOneScript(script, year, config.stop_on_error);
			R_outputsToExternalVariables(script, year);
		}

	}

	private static void runOneScript(RScriptRunnerConfig.RScriptTaskConfig script, int year, boolean stopOnError) {
		try {
			Path scriptPath = Path.of(resolveTemplate(script.path, year));

			List<String> args = buildRArgs(script, year);

			System.out.println("[R] Running script '" + script.displayName() + "' for year " + year);
			System.out.println("[R] Script path: " + scriptPath);

			createOutputDirectories(script, year);

			RScriptRunner.runRScript(scriptPath, args);

			System.out.println("[R] Finished script '" + script.displayName() + "' for year " + year);

		} catch (Exception e) {
			String message = "[R] Failed script '" + script.displayName() + "'";

			if (stopOnError) {
				throw new RuntimeException(message, e);
			}

			System.err.println(message);
			e.printStackTrace();
		}
	}

	private static List<String> buildRArgs(RScriptRunnerConfig.RScriptTaskConfig script, int year) {
		List<String> args = new ArrayList<>();

		args.add("--year=" + year);

		if (script.inputs != null) {
			for (Map.Entry<String, String> entry : script.inputs.entrySet()) {
				args.add("--input." + entry.getKey() + "=" + resolveTemplate(entry.getValue(), year));
			}
		}

		if (script.outputs != null) {
			for (Map.Entry<String, String> entry : script.outputs.entrySet()) {
				args.add("--output." + entry.getKey() + "=" + resolveTemplate(entry.getValue(), year));
			}
		}

		if (script.args != null) {
			for (Map.Entry<String, String> entry : script.args.entrySet()) {
				args.add("--" + entry.getKey() + "=" + resolveTemplate(entry.getValue(), year));
			}
		}

		return args;
	}

	private static void createOutputDirectories(RScriptRunnerConfig.RScriptTaskConfig script, int year)
			throws Exception {

		if (script.outputs == null) {
			return;
		}

		for (String outputPathTemplate : script.outputs.values()) {
			String resolved = resolveTemplate(outputPathTemplate, year);
			Path outputPath = Path.of(resolved).toAbsolutePath();
			Path parent = outputPath.getParent();

			if (parent != null) {
				Files.createDirectories(parent);
			}
		}
	}

	private static String resolveTemplate(String value, int year) {

		if (value == null) {
			return null;
		}

		Map<String, String> variables = new LinkedHashMap<>();

		variables.put("year", String.valueOf(year));
		variables.put("previous_year", String.valueOf(year - 1));
		variables.put("next_year", String.valueOf(year + 1));

		variables.put("project_path", safe(ConfigLoader.config.project_path));
		variables.put("output_folder_name", safe(ConfigLoader.config.output_folder_name));
		variables.put("scenario", safe(ConfigLoader.config.scenario));

		String resolved = value;

		for (Map.Entry<String, String> entry : variables.entrySet()) {
			resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
		}

		return resolved;
	}

	private static String safe(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private static void R_outputsToExternalVariables(RScriptRunnerConfig.RScriptTaskConfig script, int year) {
		script.outputs.values().forEach(p -> {
			Path path = Paths.get(resolveTemplate(p, year));
			if (path != null && path.toFile().isFile()) {
				External_variables_Manager_manager.valuesInjector(path);
			}
			System.out.println(">>>> " + path + "==> " + External_variables_Manager_manager.getExternal_variables());
		});

		// Loop for files for each script
		// Read each file
		// Check if the file in the correct format
		// extrait external variable

	}
}