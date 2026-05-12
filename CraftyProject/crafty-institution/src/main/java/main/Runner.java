package main;

import java.nio.file.Paths;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.main.MainHeadless;
import de.cesr.crafty.core.modelRunner.ModelRunner;
import de.cesr.crafty.core.updaters.Timestep;
import large_language_models_institutions.Connector;
import utils.External_variables_Manager;

public class Runner {

	public static void main(String[] args) {

		System.out.println("this is LLM ");
		MainHeadless.initializeConfig(args);
		ProjectLoader.pathInitialisation(Paths.get(ConfigLoader.config.project_path));
		
		MainHeadless.runner = new ModelRunner();
		MainHeadless.runner.start();
		MainHeadless.runner.initialzeRun();
		External_variables_Manager.Initializer();
		ConfigLoader.config.consider_subsidies_taxes = false;
		Connector llm = new Connector();
		llm.setup();
		
		for (int i = Timestep.getStartYear(); i <= Timestep.getEndtYear(); i++) {
			llm.step();
			System.out.println("!! "+ConfigLoader.config.consider_subsidies_taxes );
		}
		ModelRunner.exportChartsPlots();
	}
}
