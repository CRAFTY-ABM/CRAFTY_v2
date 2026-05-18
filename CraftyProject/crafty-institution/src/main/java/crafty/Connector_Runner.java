package crafty;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.main.MainHeadless;
import de.cesr.crafty.core.modelRunner.ModelRunner;
import de.cesr.crafty.core.output.Listener;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.PathTools;
import institutions_Fuzzy.InstitutionManager;
import utils.External_variables_Manager;
import utils.InstitutionOutput;
import utils.TargetModelOutput;

/*
 * @author Mohamed Byari
 *
 */
public class Connector_Runner {
	private static final CustomLogger LOGGER = new CustomLogger(Connector_Runner.class);

	private static Path institutionsJsonPath;
	private static Path fclPath;

	public static void main(String[] args) {
		System.out.println("-- institutions--");

		MainHeadless.initializeConfig(args);
		ProjectLoader.pathInitialisation(Paths.get(ConfigLoader.config.project_path));
		MainHeadless.runner = new ModelRunner();
		MainHeadless.runner.start();
		MainHeadless.runner.initialzeRun();
		External_variables_Manager.Initializer();
		ConfigLoader.config.consider_subsidies_taxes = true;
		// -------------------
		InstitutionManager institutionManager = new InstitutionManager();
		Path directory = Paths.get(ConfigLoader.config.institutions_directory);
		if (!directory.toFile().isDirectory()) {
			LOGGER.fatal("Institutions  directory not found : " + ConfigLoader.config.institutions_directory);
		} else {
			// find institutionsJsonPath
			institutionsJsonPath = PathTools.findAllFilePaths(directory).stream()
					.filter(p -> p.toString().endsWith(".json")).findFirst().orElse(null);
			if (institutionsJsonPath == null) {
				LOGGER.fatal("No Institutions Json Path found : ");
			}
			fclPath = PathTools.findAllFilePaths(directory).stream().filter(p -> p.toString().endsWith(".fcl"))
					.findFirst().orElse(null);
			if (fclPath == null) {
				LOGGER.fatal("No fuzzy_rules '.fcl' File found : ");
			}
			try {
				// Load institutions
				institutionManager.loadInstitutions(institutionsJsonPath.toString(), fclPath.toString(),
						4, true);
				LOGGER.info("institution_time_lag= "+4);
				// get initial policy values
				InstitutionOutput institutionOutput = institutionManager.getInitPolicies();
				Connector model = new Connector(institutionManager);
				for (int i = Timestep.getStartYear(); i <= Timestep.getEndtYear(); i++) {
					TargetModelOutput targetModelOutput = model.step(institutionOutput);
					institutionOutput = institutionManager.step(targetModelOutput);
				}
			} catch (IOException e) {
				LOGGER.fatal("Error running test: " + e.getMessage());
				e.printStackTrace();
			}
			ModelRunner.exportChartsPlots();
		}
	}

	public void initialRunner() {
		String generatedPath = PathTools.makeDirectory(ConfigLoader.config.Output_path);
		Listener.outputfolderPath(generatedPath, ConfigLoader.config.output_folder_name);
		PathTools.writeFile(ConfigLoader.config.output_folder_name + File.separator + "config.txt",
				Listener.exportConfigurationFile(), false);
		ModelRunner.demandEquilibrium();
	}

}
