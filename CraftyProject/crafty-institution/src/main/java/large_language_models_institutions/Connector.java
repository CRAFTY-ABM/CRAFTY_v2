package large_language_models_institutions;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.main.MainHeadless;
import de.cesr.crafty.core.updaters.RegionsModelRunnerUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.analysis.StepProfiler;
import large_language_models_institutions.main.Runner;
import utils.External_variables_Manager;

public class Connector {
	private static final CustomLogger LOGGER = new CustomLogger(Runner.class);
	Paradigms_Set paradingms = new Paradigms_Set();
	Targets_Set targets_set = new Targets_Set();
	private static Map<String, Map<String, Double>> initial_supplys = new HashMap<>();// <regionName,serviceName,year,value>

	public void setup() {
		if (Paths.get(ConfigLoader.config.institutions_directory).toFile().isDirectory()) {
			targets_set.setup();
			paradingms.setup();
			RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(r -> {
				initial_supplys.put(r.R.getName(), new HashMap<>());
			});

		} else {
			LOGGER.fatal("institutions_directory in config file is not a directory");
		}
	}

	private final StepProfiler profiler = new StepProfiler(true);

	public void step() {
		try (var t = profiler.section("CRAFTY Step")) {
			MainHeadless.runner.step();
		}
		External_variables_Manager.valuesInjector();
		try (var t = profiler.section("Targets set Listeners")) {
			targets_set.step();
		}
		try (var t = profiler.section("Institutions Step")) {
			paradingms.step();
		}
		LOGGER.info(profiler.report("Coupling step (year=" + Timestep.getCurrentYear() + ")"));
	}

}
