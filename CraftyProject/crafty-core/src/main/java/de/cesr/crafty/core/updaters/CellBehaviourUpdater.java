package de.cesr.crafty.core.updaters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.crafty.CellBehaviour;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.dataLoader.afts.AftCategorised;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;

/**
 * Updates per-cell behavioural parameters used by the category-based “give-in” mechanism.
 *
 * This updater is only active when AFT categorisation is enabled and the category-level give-in matrices
 * (mean/SD) are available (see {@link AftCategorised#useCategorisationGivIn}). If active, it loads a
 * year-specific CSV containing behavioural parameters for selected cells and stores them in
 * {@link #cellBehaviours} for later use by the competition logic (via {@link CellBehaviourUpdater#cellBehaviours}
 * and {@link CellBehaviour#give_In(Aft)}).
 *
 * Activation logic:
 * - {@link #behaviourUsed} becomes {@code true} after the first behaviour parameter file is loaded successfully.
 *   It remains enabled for the rest of the run.
 * - The constructor triggers an initial {@link #step()} to populate behaviour values for the first year.
 *
 * Per-tick/year behavior ({@link #step()}):
 * - It searches for a file named {@code Cell_behaviour_parameters_<YEAR>.csv} (scenario-specific).
 * - When a file exists, it replaces the current parameters with the newly loaded values. When no file exists,
 *   the most recently loaded parameters remain active.
 * - For each row in a newly available file:
 *   1) Resolves the target {@link Cell} by its {@code X,Y} coordinates from {@link CellsLoader#hashCell}.
 *   2) Instantiates a {@link CellBehaviour} object and populates its parameters:
 *      {@code Attitude_intensification}, {@code Weight_inertia}, {@code Weight-social}, {@code Critical_mass},
 *      {@code Neighborhood_size}, and {@code MaxGive_in}.
 *   3) Stores the resulting behaviour object in {@link #cellBehaviours}.
 *
 * Input assumptions / notes:
 * - The CSV is expected to contain at least {@code X} and {@code Y} columns plus the behaviour parameter
 *   columns listed above.
 * - Behaviour objects are only created for the cells listed in the file; other cells will have no entry in
 *   {@link #cellBehaviours} and will therefore fall back to category or AFT give-in parameters.
 */

/**
 * @author Mohamed Byari
 *
 */
public class CellBehaviourUpdater extends AbstractUpdater {
	private static final CustomLogger LOGGER = new CustomLogger(CellBehaviourUpdater.class);

	public static boolean behaviourUsed = false;
	public static ConcurrentHashMap<Cell, CellBehaviour> cellBehaviours = new ConcurrentHashMap<>();
	private static Integer loadedParameterYear;

	public CellBehaviourUpdater() {
		behaviourUsed = false;
		loadedParameterYear = null;
		cellBehaviours.clear();
		step();
	}

	@Override
	public void toSchedule() {
		modelRunner.scheduleRepeating(this);
	}

	@Override
	public void step() throws IndexOutOfBoundsException {
		if (!ConfigLoader.config.use_cell_behaviour_model || !AftCategorised.useCategorisationGivIn) {
			return;
		}

		ArrayList<Path> files = null;
		String configuredDirectory = ConfigLoader.config.cell_behaviour_parameters_directory;
		if (configuredDirectory != null && !configuredDirectory.isBlank()
				&& Paths.get(configuredDirectory).toFile().isDirectory()) {
			files = PathTools.findAllFilePaths(Paths.get(configuredDirectory));
		}
		if (files != null && !files.isEmpty()) {
			files = PathTools.fileFilter(files, "Cell_behaviour_parameters", Timestep.getCurrentYear() + "", ".csv");
		} else {
			files = PathTools.fileFilter(PathTools.asFolder("behaviour"), ProjectLoader.getScenario(),
					"Cell_behaviour_parameters", Timestep.getCurrentYear() + ".csv");
		}
		if (files == null || files.isEmpty()) {
			if (behaviourUsed) {
				LOGGER.info("No cell behaviour parameters found for " + Timestep.getCurrentYear()
						+ "; retaining parameters from " + loadedParameterYear + ".");
			} else {
				LOGGER.warn("Behaviour model not yet active for " + Timestep.getCurrentYear()
						+ ": no cell behaviour parameter file was found.");
			}
			return;
		}
		Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(files.get(0));

		if (csv == null) {
			LOGGER.warn("Could not read cell behaviour parameters for " + Timestep.getCurrentYear()
					+ (behaviourUsed ? "; retaining parameters from " + loadedParameterYear + "." : "."));
			return;
		}
		ConcurrentHashMap<Cell, CellBehaviour> updatedBehaviours = new ConcurrentHashMap<>();
		for (int i = 0; i < csv.get("X").size(); i++) {
			Cell c = CellsLoader.hashCell.get(csv.get("X").get(i) + "," + csv.get("Y").get(i));
			if (c == null) {
				LOGGER.warn("Ignoring cell behaviour row with unknown coordinates: " + csv.get("X").get(i) + ","
						+ csv.get("Y").get(i));
				continue;
			}
			CellBehaviour behaviour = new CellBehaviour(c);
			behaviour.setAttitude_intensification(Utils.sToD(csv.get("Attitude_intensification").get(i)));
			behaviour.setWeight_inertia(Utils.sToD(csv.get("Weight_inertia").get(i)));
			behaviour.setWeight_social(Utils.sToD(csv.get("Weight-social").get(i)));
			behaviour.setCritical_mass(Utils.sToD(csv.get("Critical_mass").get(i)));
			behaviour.setNeighborhood_size(Utils.sToI(csv.get("Neighborhood_size").get(i)));
			behaviour.setMaxGive_in(Utils.sToD(csv.get("MaxGive_in").get(i)));
			updatedBehaviours.put(c, behaviour);
		}
		cellBehaviours.clear();
		cellBehaviours.putAll(updatedBehaviours);
		behaviourUsed = true;
		loadedParameterYear = Timestep.getCurrentYear();
		LOGGER.info("Loaded cell behaviour parameters for " + loadedParameterYear + ".");
	}

	public static Integer getLoadedParameterYear() {
		return loadedParameterYear;
	}

}
