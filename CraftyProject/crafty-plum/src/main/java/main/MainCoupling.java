package main;

import java.io.File;
import java.nio.file.Paths;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.main.MainHeadless;
import de.cesr.crafty.core.modelRunner.ModelRunner;
import de.cesr.crafty.core.output.Listener;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.PathTools;
import newCoupler.Mapper;

public class MainCoupling {

	// use .sh to controle models
	// use crfaty only to look if plum outputs is correcte and make a loop
	// .sh configuration.
	// 1. Run calibration FAO.
	// 3. Run plum config with crafty.
	// 1. carfty run always year 2020 [new plum output cofig file].
	// 2. calculate EQ for each iteration (check the EQ stability)?.
	// 3. return carfty file.
	// 5. Run the copling
	// 6. Generate .jar for copling

	// initilase the loops
	// Clean plum folder
	// Use only one time main function for the complete run

	// Create a Main that run crafty one iteration if there is Plum input then
	// convert data
	// write a carfty for plum
	// waiting for plum run
	// loop

	public static void main(String[] args) {
		System.out.println("--  Starting CRAFTY-PLUM execution  --"); 

		MainHeadless.initializeConfig(args);
		ConfigLoader.config.COUPLED_WITH_PLUM = true;
		PathTools.makeDirectory(ConfigLoader.config.plumOutPutPath + File.separator + "crafty");
		ProjectLoader.pathInitialisation(Paths.get(ConfigLoader.config.project_path));
		MainHeadless.runner = new ModelRunner();
		MainHeadless.runner.start();
		initialzeRun();

		Mapper mapper = new Mapper();
		mapper.setup();
		for (int i = 0; i < Timestep.getSize(); i++) {
			mapper.step();
		}
		ModelRunner.exportChartsPlots();
	}

	private static void initialzeRun() {
		String generatedPath = PathTools.makeDirectory(ConfigLoader.config.Output_path);
		Listener.outputfolderPath(generatedPath, ConfigLoader.config.output_folder_name);
		if (ConfigLoader.config.export_LOGGER) {
			CustomLogger
					.configureLogger(Paths.get(ConfigLoader.config.output_folder_name + File.separator + "LOGGER.txt"));
		}
		PathTools.writeFile(ConfigLoader.config.output_folder_name + File.separator + "config.txt",
				Listener.exportConfigurationFile(), false);
	}

}
