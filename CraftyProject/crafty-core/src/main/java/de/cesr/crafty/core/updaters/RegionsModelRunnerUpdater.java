package de.cesr.crafty.core.updaters;

import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.RegionalModelRunner;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.afts.AftCategorised;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;

/**
 * Scheduler component responsible for advancing the regional sub-models each simulation tick.
 *
 * This updater owns a {@code RegionalModelRunner} instance per {@link de.cesr.crafty.core.crafty.Region}.
 * At construction time, it creates and stores these runners in {@link #regionsModelRunner}, using the
 * region names currently available in {@link CellsLoader#regions}.
 *
 * On every tick/year ({@link #step()}):
 * - It calls {@link RegionalModelRunner#step(int)} for each region using the current simulation year
 *   from {@link Timestep#getCurrentYear()}.
 * - It refreshes global and per-region AFT occupancy counters via
 *   {@link AFTsLoader#hashAgentNbrRegions()} and {@link AFTsLoader#hashAgentNbr()} so other components
 *   (e.g., listeners, taxes/subsidies, calibration, reporting) can use up-to-date distributions.
 *
 * Notes / assumptions:
 * - This class assumes {@link CellsLoader#regions} has already been initialized before instantiation.
 *   If regions are rebuilt at runtime, {@link #regionsModelRunner} would need to be rebuilt accordingly.
 * - Runners are stored in a {@link ConcurrentHashMap} to allow safe concurrent reads; the updater itself
 *   iterates sequentially, while each {@link RegionalModelRunner} may internally use parallel operations.
 */

/**
 * @author Mohamed Byari
 *
 */

public class RegionsModelRunnerUpdater extends AbstractUpdater {
	private static final CustomLogger LOGGER = new CustomLogger(RegionsModelRunnerUpdater.class);

	public static ConcurrentHashMap<String, RegionalModelRunner> regionsModelRunner;

	private Runnable step = this::doStep;

	public RegionsModelRunnerUpdater() {
		regionsModelRunner = new ConcurrentHashMap<>();
		CellsLoader.regions.keySet().forEach(regionName -> {
			regionsModelRunner.put(regionName, new RegionalModelRunner(regionName));
		});
	}

	@Override
	public void toSchedule() {
		modelRunner.scheduleRepeating(this);
	}

	@Override
	public void step() {
		LOGGER.info("Use Behevoir Model= " + (AftCategorised.useCategorisationGivIn && CellBehaviourUpdater.behaviourUsed));
		step.run();
		AFTsLoader.hashAgentNbrRegions();
		AFTsLoader.hashAgentNbr();
	}

	private void doStep() {
		regionsModelRunner.values().forEach(RegionalRunner -> {
			RegionalRunner.step();
		});
	}

	public void setStep(Runnable step) {
		this.step = (step != null) ? step : this::doStep;
	}
//			RegionalRunner.setCellsUtilities(() -> {
//			System.out.println("change the cells utilities ");
//		});
}
