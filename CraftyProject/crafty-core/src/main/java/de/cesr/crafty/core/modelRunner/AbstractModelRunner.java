package de.cesr.crafty.core.modelRunner;

import java.util.ArrayList;
import java.util.List;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.analysis.StepProfiler;

/**
 * Base class for CRAFTY model runners.
 *
 * This abstraction provides a minimal scheduler/execution loop for components that implement
 * {@link ModelState}. A model runner maintains a list of scheduled model states (loaders, updaters,
 * listeners, etc.) and executes them sequentially once per simulation tick.
 *
 * Scheduling model states:
 * - {@link #scheduleRepeating(ModelState)} registers a component to be executed every step.
 * - The schedule is stored in insertion order in {@link #scheduled}; components are executed in the same order
 *   they were added. This implies that update ordering is controlled by the order of registration.
 *
 * Execution:
 * - {@link #step()} performs one simulation step for the current model year.
 * - For each scheduled {@link ModelState}, the runner logs the class name and calls {@link ModelState#step()}.
 * - Runtime profiling is performed using {@link StepProfiler}, with one timing section per scheduled component.
 *   The aggregated timing report for the full step is printed at the end of the method.
 *
 * Initialization / wiring:
 * - {@link #setup(AbstractModelRunner)} provides each scheduled component with a back-pointer to this runner
 *   by calling {@link ModelState#setup(AbstractModelRunner)}.
 * - This enables model states to access shared runner context without requiring static global access.
 *
 * Notes and assumptions:
 * - The scheduler is intentionally simple and executes model states sequentially; internal parallelism is left
 *   to the components themselves.
 */

public abstract class AbstractModelRunner {
	private final List<ModelState> scheduled = new ArrayList<>();
	private static final CustomLogger LOGGER = new CustomLogger(AbstractModelRunner.class);

	public List<ModelState> getScheduled() {
		return scheduled;
	}

	public void scheduleRepeating(ModelState s) {
		scheduled.add(s);
	}

	private final StepProfiler profiler = new StepProfiler(ConfigLoader.config.print_abstract_model_runner_measures);

	public void step() {
		profiler.reset();
		System.out.println("--------------   Step:  " + Timestep.getCurrentYear() + "  ----------------------");
		for (ModelState s : scheduled) {
			LOGGER.info("ModelState runing class |" + s.getClass().getSimpleName() + "|");
			try (var t = profiler.section(s.getClass().getSimpleName())) {
				s.step();
			}
		}
//		LOGGER.info("Step timings (year=" + (Timestep.getCurrentYear() - 1) + ", all regions" + ")"));
		System.out.print(profiler.report("ModelRunner: (year=" + (Timestep.getCurrentYear() - 1) + ")"));
	}

	public void setup(AbstractModelRunner abstractModelRunner) {
		for (ModelState modelState : scheduled) {
			modelState.setup(this);
		}
	}
}
