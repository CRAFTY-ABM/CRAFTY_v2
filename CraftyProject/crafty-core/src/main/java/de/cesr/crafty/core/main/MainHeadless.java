package de.cesr.crafty.core.main;

import java.nio.file.Paths;
import java.util.Iterator;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CraftyOptions;
import de.cesr.crafty.core.cli.OptionsParser;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.modelRunner.ModelRunner;
import de.cesr.crafty.core.updaters.SeedUpdater;

/**
 * Headless entry point for running CRAFTY from the command line (no GUI).
 *
 * This main class wires together the minimal startup sequence:
 * 1) Parse CLI arguments (e.g., config path and optional overrides).
 * 2) Load and initialize the YAML configuration via {@link ConfigLoader}.
 * 3) Apply CLI overrides for project directory, scenario name, and output path (if provided).
 * 4) Initialize project paths via {@link ProjectLoader}.
 * 5) Create a {@link ModelRunner} and execute the simulation lifecycle.
 *
 * This class is intended for batch/HPC execution and reproducible scripted runs.
 */

/**
 * @author Mohamed Byari
 *
 */
public class MainHeadless {
	public static ModelRunner runner;

	public static void main(String[] args) {
		System.out.println("--Starting CRAFTY execution--");
		initializeConfig(args);
		ProjectLoader.pathInitialisation(Paths.get(ConfigLoader.config.project_path));
		runner = new ModelRunner();
		runner.start();
		runner.initialzeRun();
		runner.run();
//		int k = 0;
//		for (int i = 0; i < 10; i++) {
//			for (int j = 0; j < 10; j++) {
////				float r = (float) (Math.random()*2 + i + j);
////				String str= r>0.75? "Crop":r>0.40? "Pasture":"Forest"; 
////				System.out.println((k++) + "," + i + "," + j + "," + (float)Math.abs(Math.sin(r)) + "," + (float)Math.abs(Math.cos(r))
////						+ "," + (float)Math.abs(1/(Math.sqrt(r)+1)) + "," + (float)Math.abs(1 / Math.exp(1 / r)) + ","
////						+ (float)Math.abs((Math.sin(r) + Math.cos(r))/2));
//				if (i < 3 && (j> 7)) {
//					System.out.println((k++) + "," + i + "," + j + "," + (Math.random() > 0.3 ? 1 : 0));
//				} else {
//					System.out.println((k++) + "," + i + "," + j + "," + (Math.random() > 0.2 ? 0 : 1));
//				}
//			}
//
//		}
	}

	public static void initializeConfig(String[] args) {
		// Load config using the path from CraftyOptions
		CraftyOptions options = OptionsParser.parseArguments(args);
		ConfigLoader.configPath = options.getConfigFilePath();
		ConfigLoader.init();
		SeedUpdater.inialize();
		// If the user specified a project directory and scenario name, override the
		// ones in the YAML
		String projectDirectoryPath = options.getProjectDirectoryPath();
		if (projectDirectoryPath != null) {
			ConfigLoader.config.project_path = projectDirectoryPath;
		}
		String scenarioName = options.getScenario_Name();
		if (scenarioName != null) {
			ConfigLoader.config.scenario = scenarioName;
		}
		String ouputPath = options.getOutput_path();
		if (ouputPath != null) {
			ConfigLoader.config.Output_path = ouputPath;
		}
	}

}
