package de.cesr.crafty.institution.decision.llm.runtime;

import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.main.MainHeadless;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.analysis.StepProfiler;
import de.cesr.crafty.core.utils.non_java_code_controller.External_variables_Manager_manager;
import de.cesr.crafty.institution.model.InstitutionConfiguration;
public final class LlmConnector {
	private static final CustomLogger LOGGER = new CustomLogger(LlmConnector.class);
	private final ParadigmRuntimeSet paradigms = new ParadigmRuntimeSet();
	private final LlmTargetRuntimeSet targets = new LlmTargetRuntimeSet();
	public void setup(InstitutionConfiguration configuration) {
		targets.reset();
		paradigms.setup(configuration);
		targets.initializeBaselines();
	}

	private final StepProfiler profiler = new StepProfiler(true);

	public void step() {
		try (var t = profiler.section("CRAFTY Step")) {
			MainHeadless.runner.step();
		}
		External_variables_Manager_manager.valuesInjector();
		System.out.println("External_variables =>  " + External_variables_Manager_manager.getExternal_variables());
		try (var t = profiler.section("Targets set Listeners")) {
			targets.step();
		}
		try (var t = profiler.section("Institutions Step")) {
			paradigms.step();
		}
		LOGGER.info(profiler.report("Coupling step (year=" + Timestep.getCurrentYear() + ")"));
	}

}
