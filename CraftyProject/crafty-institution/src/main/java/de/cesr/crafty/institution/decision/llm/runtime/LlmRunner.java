package de.cesr.crafty.institution.decision.llm.runtime;

import de.cesr.crafty.core.modelRunner.ModelRunner;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.non_java_code_controller.External_variables_Manager_manager;
import de.cesr.crafty.institution.model.InstitutionConfiguration;
public final class LlmRunner {
	private LlmRunner() {
	}

	public static void run(InstitutionConfiguration configuration) {
		External_variables_Manager_manager.Initializer();
		LlmConnector llm = new LlmConnector();
		llm.setup(configuration);
		for (int year = Timestep.getStartYear(); year <= Timestep.getEndtYear(); year++) {
			llm.step();
		}
		ModelRunner.exportChartsPlots();
	}
}
