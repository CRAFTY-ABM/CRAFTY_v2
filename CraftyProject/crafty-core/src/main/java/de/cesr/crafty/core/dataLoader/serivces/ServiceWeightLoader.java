package de.cesr.crafty.core.dataLoader.serivces;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Region;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;

/**
 * Loads and manages time-series service utility weights.
 *
 * This loader reads per-service weight trajectories (one value per year) from CSV inputs and assigns them
 * to each {@link de.cesr.crafty.core.crafty.Region}'s {@link de.cesr.crafty.core.crafty.Service} object.
 * Weights are used downstream in the marginal-utility / utility calculation to prioritise services in the
 * competitiveness and land-use change processes.
 *
 * Input discovery:
 * If {@code ConfigLoader.config.service_utility_weight_path} is set and non-empty:
 * - If it points to a file, that file is used (typically a single file for all regions but could be by regions).
 * - If it points to a directory, the loader searches within it for a region-specific file containing
 *       the token "..;RegionName;..;".
 * Otherwise, it falls back to the scenario structure and searches under
 *   {@code Service_Utility_Weights/} for a file matching the region name.
 *
 * Update behaviour:
 * -{@link #updateRegionsWeight()} clears existing weight maps for all region services, then calls
 *   {@link #updateWeight(Region)} to reload them from disk.
 * - The CSV is expected to have service names as column headers, and each column provides a vector of weights
 *   aligned with the simulation horizon [{@link Timestep#getStartYear()} .. {@link Timestep#getEndtYear()}].
 * - Only services present in {@link ServiceSet#getServicesList()} are applied; unknown columns are ignored.
 *
 * Defaults:
 * - If no weight file is found for a region, {@link #useDefaultUtilityW(Region)} assigns a constant weight of
 *   {@code 1.0} for all services and all years, ensuring the model remains runnable without optional inputs.
 */
/**
 * @author Mohamed Byari
 *
 */

public class ServiceWeightLoader {
	private static final CustomLogger LOGGER = new CustomLogger(ServiceWeightLoader.class);

	public static void updateRegionsWeight() {
		CellsLoader.regions.values().forEach(r -> {
			r.getServicesHash().values().forEach(s -> {
				s.getWeights().clear();
			});
			updateWeight(r);
		});
	}

	private static Path uwPath(Region R) {
		Path path;
		String uw = ConfigLoader.config.service_utility_weight_path;
		try {
			if (!uw.isEmpty()) {
				if (Paths.get(uw).toFile().isFile()) {
					path = Paths.get(uw);
				} else {
					ArrayList<Path> directory = PathTools.findAllFilePaths(Paths.get(uw));
					path = PathTools.fileFilter(directory, "_" + R.getName()).get(0);
				}
			} else {
				path = PathTools.fileFilter(ProjectLoader.getScenario(), PathTools.asFolder("Service_Utility_Weights"),
						R.getName()).get(0);
			}
		} catch (NullPointerException e) {
			LOGGER.info("Utility Weight file not fund for |" + R.getName()
					+ "| default value will use: Utility_Weights = 1 for all services");
			useDefaultUtilityW(R);
			return null;
		}
		return path;
	}

	private static void updateWeight(Region R) {
		Path path = uwPath(R);
		if (path != null) {
			LOGGER.info("Update Weight for [" + R.getName() + "]: " + path);
			Map<String, List<String>> hashWeight = CsvProcessors.ReadAsaHash(path);

			hashWeight.forEach((serviceName, vect) -> {
				if (ServiceSet.getServicesList().contains(serviceName)) {
					ConcurrentHashMap<Integer, Double> dv = new ConcurrentHashMap<>();
					for (int i = 0; i < Timestep.getSize(); i++) {
						if (i < vect.size()) {
							dv.put(i + Timestep.getStartYear(), Utils.sToD(vect.get(i)));
						}
					}
					R.getServicesHash().get(serviceName).setWeights(dv);
				}
			});
		}
	}

	private static void useDefaultUtilityW(Region R) {
		ServiceSet.getServicesList().forEach((serviceName) -> {
			ConcurrentHashMap<Integer, Double> dv = new ConcurrentHashMap<>();
			for (int i = 0; i < Timestep.getSize(); i++) {
				dv.put(i + Timestep.getStartYear(), 1.);
			}
			R.getServicesHash().get(serviceName).setWeights(dv);
		});
	}

}
