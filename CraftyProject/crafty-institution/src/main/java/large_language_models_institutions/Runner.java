package large_language_models_institutions;

import java.nio.file.Paths;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.main.MainHeadless;
import de.cesr.crafty.core.modelRunner.ModelRunner;
import de.cesr.crafty.core.updaters.Timestep;
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

		LLM_connector llm = new LLM_connector();
		llm.setup();
		for (int i = Timestep.getStartYear(); i <= Timestep.getEndtYear(); i++) {
			llm.step();
		}
		ModelRunner.exportChartsPlots();
	}
}
