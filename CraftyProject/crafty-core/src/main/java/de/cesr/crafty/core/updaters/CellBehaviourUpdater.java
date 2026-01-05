package de.cesr.crafty.core.updaters;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * {@link #cellsBehevoir} for later use by the competition logic (via {@link CellBehaviourUpdater#cellsBehevoir}
 * and {@link CellBehaviour#give_In(Aft)}).
 *
 * Activation logic:
 * - {@link #behaviourUsed} is set to {@code true} only if a behaviour parameter file exists for the start year
 *   under the expected scenario folder structure.
 * - The constructor triggers an initial {@link #step()} to populate behaviour values for the first year.
 *
 * Per-tick/year behavior ({@link #step()}):
 * - If {@link #behaviourUsed} is {@code false}, the updater does nothing.
 * - Otherwise, it searches for a file named {@code Cell_behaviour_parameters_<YEAR>.csv} (scenario-specific),
 *   reads it into a column-based map, and for each row:
 *   1) Resolves the target {@link Cell} by its {@code X,Y} coordinates from {@link CellsLoader#hashCell}.
 *   2) Instantiates a {@link CellBehaviour} object and populates its parameters:
 *      {@code Attitude_intensification}, {@code Weight_inertia}, {@code Weight-social}, {@code Critical_mass},
 *      {@code Neighborhood_size}, and {@code MaxGive_in}.
 *   3) Stores the resulting behaviour object in {@link #cellsBehevoir}.
 *
 * Input assumptions / notes:
 * - The CSV is expected to contain at least {@code X} and {@code Y} columns plus the behaviour parameter
 *   columns listed above.
 * - Behaviour objects are only created for the cells listed in the file; other cells will have no entry in
 *   {@link #cellsBehevoir} and will therefore fall back to the default values.
 */

/**
 * @author Mohamed Byari
 *
 */
public class CellBehaviourUpdater extends AbstractUpdater {

	public static boolean behaviourUsed = false;
	public static ConcurrentHashMap<Cell, CellBehaviour> cellsBehevoir = new ConcurrentHashMap<>();

	public CellBehaviourUpdater() {
		if (AftCategorised.useCategorisationGivIn) {
			behaviourUsed = PathTools.fileFilter(PathTools.asFolder("behaviour"), ProjectLoader.getScenario(),
					"Cell_behaviour_parameters", Timestep.getStartYear() + ".csv") != null;
			step();
		}
	}

	@Override
	public void toSchedule() {
		modelRunner.scheduleRepeating(this);
	}

	@Override
	public void step() throws IndexOutOfBoundsException {
		if (!behaviourUsed)
			return;
		ArrayList<Path> files = PathTools.fileFilter(PathTools.asFolder("behaviour"), ProjectLoader.getScenario(),
				"Cell_behaviour_parameters", Timestep.getCurrentYear() + ".csv");
		if (files == null || files.isEmpty())
			return;
		Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(files.get(0));
		if (csv == null)
			return;
		for (int i = 0; i < csv.get("X").size(); i++) {
			Cell c = CellsLoader.hashCell.get(csv.get("X").get(i) + "," + csv.get("Y").get(i));
			CellBehaviour behaviour = new CellBehaviour(c);
			behaviour.setAttitude_intensification(Utils.sToD(csv.get("Attitude_intensification").get(i)));
			behaviour.setWeight_inertia(Utils.sToD(csv.get("Weight_inertia").get(i)));
			behaviour.setWeight_social(Utils.sToD(csv.get("Weight-social").get(i)));
			behaviour.setCritical_mass(Utils.sToD(csv.get("Critical_mass").get(i)));
			behaviour.setNeighborhood_size(Utils.sToI(csv.get("Neighborhood_size").get(i)));
			behaviour.setMaxGive_in(Utils.sToD(csv.get("MaxGive_in").get(i)));
			cellsBehevoir.put(c, behaviour);
		}

	}

}
