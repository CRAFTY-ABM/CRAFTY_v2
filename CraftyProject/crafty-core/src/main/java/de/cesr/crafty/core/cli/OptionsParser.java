package de.cesr.crafty.core.cli;

import org.apache.commons.cli.*;
/**
 * Parses CRAFTY command-line arguments using Apache Commons CLI and returns them as a {@link CraftyOptions} object.
 *
 * Supported overrides are intentionally minimal and focused on runtime convenience:
 * - YAML configuration file path (--config-file)
 * - project directory override (--project-dir)
 * - scenario name override (--scenario-name)
 * - output path override (--output-path)
 *
 * If parsing fails (invalid option usage), this parser prints a help message and terminates the JVM
 * with exit code 1.
 */

/**
 * @author Mohamed Byari
 *
 */

public class OptionsParser {


	public static CraftyOptions parseArguments(String[] args) {
		Options options = new Options();

		Option configOption = Option.builder("c").longOpt("config-file").desc("Path to the YAML config file.")
				.hasArg(true).argName("CONFIG_FILE").required(false).build();
		options.addOption(configOption);

		Option projectDirOption = Option.builder("p").longOpt("project-dir")
				.desc("Path to override the project directory in the config file.").hasArg(true).argName("PROJECT_DIR")
				.required(false).build();
		options.addOption(projectDirOption);
		Option scenarioOption = Option.builder("s").longOpt("scenario-name")
				.desc("scenario name to override the scenario name in the config file.").hasArg(true)
				.argName("SCENARIO_NAME").required(false).build();
		options.addOption(scenarioOption);
		
		Option outputPathOption = Option.builder("o").longOpt("output-path")
				.desc("CRAFTY output path, used to manually define the output file that will replace the default output folder."
						+ " It is optional and if no path is defined, the default output folder will be used.").hasArg(true)
				.argName("OUTPUT_Path").required(false).build();
		options.addOption(outputPathOption);

		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		CraftyOptions craftyOptions = new CraftyOptions();

		try {
			CommandLine cmd = parser.parse(options, args);
			if (cmd.hasOption("config-file")) {
				craftyOptions.setConfigFilePath(cmd.getOptionValue("config-file"));
			}
			if (cmd.hasOption("project-dir")) {
				craftyOptions.setProjectDirectoryPath(cmd.getOptionValue("project-dir"));
			}
			if (cmd.hasOption("scenario-name")) {
				craftyOptions.setScenario_Name(cmd.getOptionValue("scenario-name"));
			}
			if (cmd.hasOption("output-path")) {
				craftyOptions.setOutput_path(cmd.getOptionValue("output-path"));
			}

		} catch (ParseException e) {
			System.err.println("Error parsing arguments: " + e.getMessage());
			formatter.printHelp("CRAFTY", options);
			System.exit(1);
		}

		return craftyOptions;
	}
}
