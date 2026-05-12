package de.cesr.crafty.core.updaters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.TreeMap;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.CsvKind;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.utils.file.PathTools;
/**
 * Updater responsible for applying year-specific capital degradation (“shock”) maps to cells.
 *
 * This component maintains a mapping from simulation year to a degradation CSV file
 * ({@link #degradation_paths}). Each degradation file encodes, per cell (X/Y), a set of degradation
 * factors for one or more capitals (e.g., reductions due to hazards, policy restrictions or PA).
 *
 * During initialisation, the updater resolves the degradation files either:
 * - From an explicit directory provided by {@code config.capital_degradation_directory}, or
 * - From the default scenario structure under the project data tree (scenario-specific first,
 *   otherwise falling back to {@code default_cellsShocks}).
 *
 * On every tick/year ({@link #step()}):
 * - If a degradation file exists for {@link Timestep#getCurrentYear()}, it is processed via
 *   {@link CsvProcessors#processCSV(Path, #CAPITALS_DEGRADATION)}.
 *   This delegates to {@link CsvProcessors#associateCapitalsDegradationToCells(Map, String)}, which:
 *   - Populates {@link #cellsShocks} with per-cell degradation values (cell → capital → factor), and
 *   - Applies the degradation directly to the cell’s current capital values (typically:
 *        {@code capital := capital * (1 - degradation)}).
 *
 * Input expectations (degradation CSV):
 * - Must contain "X" and "Y" columns.
 * - Any capital column present is interpreted as a degradation factor for that capital in that year
 *   (commonly in [0,1], where 0 = no degradation and 1 = full loss; conventions depend on the scenario setup).
 *
 * Notes / assumptions:
 * - The updater calls {@link #step()} once in the constructor to apply any degradation for the initial year
 *   before the first scheduled tick (useful for baseline hazard initialisation).
 * - If no file is found for a year, the updater logs a warning and leaves capitals unchanged for that year.
 * - {@link #cellsShocks} is static and therefore shared across runs within the same JVM unless cleared.
 */
/**
 * @author Mohamed Byari
 *
 */

public class Capital_Degradation_Updater extends AbstractUpdater {

	private static final CustomLogger LOGGER = new CustomLogger(Capital_Degradation_Updater.class);
//	public static ConcurrentHashMap<Cell, ConcurrentHashMap<String, Double>> cellsShocks = new ConcurrentHashMap<>();

	// This updater is responsible for associating shocks to cells based on the
	// current year and scenario.
	// <cell, <shock-capital, shockValue>>
	private TreeMap<Integer, Path> degradation_paths = new TreeMap<>();// <year,path>

	public Capital_Degradation_Updater() {
		LOGGER.info("CellsShocksUpdater initialized");
		listPaths();
		step();
	}

	private void listPaths() {
		Path p = Paths.get(ConfigLoader.config.capital_degradation_directory);
		if (p.toFile().isDirectory()) {
			ArrayList<Path> directory = PathTools.findAllFilePaths(p);
			for (int i = Timestep.getStartYear(); i <= Timestep.getEndtYear(); i++) {
				ArrayList<Path> listMasks = PathTools.fileFilter(directory, i + ".csv");
				if (listMasks != null && listMasks.size() > 0) {
					degradation_paths.put(i, listMasks.get(0));
				}

			}
		} else {
			ArrayList<Path> paths = PathTools.fileFilter(PathTools.asFolder("capitals_degradation"), PathTools.asFolder("worlds"),
					".csv");
			if (paths != null) {
				for (int i = Timestep.getStartYear(); i <= Timestep.getEndtYear(); i++) {
					int ii = i;
					Path shockPath = paths.stream()
							.filter(pp -> (pp.toString().contains("capitals_degradation_" + ProjectLoader.getScenario()))
									&& (pp.toString().contains(ii + ".csv")))
							.findFirst()
							.orElse(paths.stream().filter(pp -> (pp.toString().contains("default_capitals_degradation"))
									&& (pp.toString().contains(ii + ".csv"))).findFirst().orElse(null));
					degradation_paths.put(i, shockPath);
				}
			}
		}
		if (degradation_paths.size() != 0) {
			LOGGER.info("capital degradation file found for: " + degradation_paths.keySet());
		} else {
			LOGGER.warn("No Capital_Degradation folder found");
		}

	}

	@Override
	public void toSchedule() {
		modelRunner.scheduleRepeating(this);
		LOGGER.info("CellsShocksUpdater scheduled for repeating updates.");

	}

	@Override
	public void step() {
		if (degradation_paths.size() != 0) {
			LOGGER.info("Updating cells capital degradation...");
			if (degradation_paths.get(Timestep.getCurrentYear()) != null) {
				LOGGER.info("Capital_Degradation Map path found: " + degradation_paths.get(Timestep.getCurrentYear()));
				CsvProcessors.processCSV(degradation_paths.get(Timestep.getCurrentYear()), CsvKind.CAPITALS_DEGRADATION);
				LOGGER.info("Cells Capital_Degradation associated successfully.");
			} else {
				LOGGER.warn("No Capital_Degradation folder found for: " + Timestep.getCurrentYear());
			}
		}
	}

}
