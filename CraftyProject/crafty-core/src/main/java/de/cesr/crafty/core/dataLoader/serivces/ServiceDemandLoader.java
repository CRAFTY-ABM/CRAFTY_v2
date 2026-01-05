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
 * Loads and aggregates time-series service demand data.
 *
 * This component is responsible for reading demand trajectories (per service, per year) from CSV input files
 * and attaching them to each {@link de.cesr.crafty.core.crafty.Region}'s {@link de.cesr.crafty.core.crafty.Service}
 * objects. It also provides utilities to aggregate regional demand into a single world-level demand series
 * (stored in {@link ServiceSet#worldService}) for reporting and plotting.
 *
 * Input resolution:
 * - If {@code ConfigLoader.config.service_demands_path} is provided:
 * - If it points to a file, it is used directly.
 * - If it points to a directory, the loader searches within it for a region-specific file containing
 *       the token "..;RegionName&gt;..".
 * - Otherwise, the loader falls back to the scenario folder structure and searches under the scenario's
 *   {@code demand/} directory for a file matching the region name.
 *
 * Demand assignment:
 * - Each CSV column corresponds to a service name and contains a vector of yearly demand values.
 * - For every service that exists in {@link ServiceSet#getServicesList()}, a year-indexed map is built
 *   for the model horizon [{@link Timestep#getStartYear()} .. {@link Timestep#getEndtYear()}].
 * - Missing values beyond the vector length are filled with {@code 0.0} to ensure that every year has a value.
 *
 * Aggregation to world:
 * {@link #aggregateRegionalToWorldServiceDemand()} sums regional demands into {@link ServiceSet#worldService}
 * for each year. In addition, it computes simple averages for weights and taxes/subsidies across regions
 * (by dividing each region value by {@code CellsLoader.regions.size()} and summing).
 */
/**
 * @author Mohamed Byari
 *
 */

public class ServiceDemandLoader {
	private static final CustomLogger LOGGER = new CustomLogger(ServiceDemandLoader.class);

//	private static Map<Integer, Path> service_utility_weight_directory;

	public static void updateRegionsDemand() {
		CellsLoader.regions.values().forEach(r -> {
			updateDemand(r);
		});
	}

	private static Path dmPath(Region R) {
		Path path;
		String dm = ConfigLoader.config.service_demands_path;
		if (dm != null && !dm.isEmpty()) {
			if (Paths.get(dm).toFile().isFile()) {
				path = Paths.get(dm);
			} else {
				ArrayList<Path> directory = PathTools.findAllFilePaths(Paths.get(dm));
				path = PathTools.fileFilter(directory, "_" + R.getName()).get(0);
			}
		} else {
			try {
				path = PathTools
						.fileFilter(ProjectLoader.getScenario(), PathTools.asFolder("demand"), "_" + R.getName())
						.get(0);
			} catch (NullPointerException e) {
				LOGGER.warn("No demand file fund for region: |" + R.getName() + "|");
				return null;
			}
		}

		return path;
	}

	private static void updateDemand(Region R) {
		Path path = dmPath(R);

		Map<String, List<String>> demandData = CsvProcessors.ReadAsaHash(path);
		LOGGER.info("Update Demand for [" + R.getName() + "]: " + path);
		demandData.forEach((serviceName, vect) -> {
			if (ServiceSet.getServicesList().contains(serviceName)) {
				ConcurrentHashMap<Integer, Double> dv = new ConcurrentHashMap<>();
				for (int i = 0; i < Timestep.getSize(); i++) {
					if (i < vect.size()) {
						dv.put(Timestep.getStartYear() + i, Utils.sToD(vect.get(i)));
					} else {
						dv.put(Timestep.getStartYear() + i, 0.);
					}
				}
				R.getServicesHash().get(serviceName).getDemands().clear();
				R.getServicesHash().get(serviceName).setDemands(dv);
				LOGGER.trace("Demand for [" + serviceName + "]: " + R.getServicesHash().get(serviceName).getDemands());
			}
		});

	}

	public static void aggregateRegionalToWorldServiceDemand() {
		for (int i = Timestep.getStartYear(); i <= Timestep.getEndtYear(); i++) {
			int year = i;
			ServiceSet.worldService.forEach((ns, s) -> {
				s.getDemands().put(year, 0.);
			});
			CellsLoader.regions.values().forEach(r -> {
				r.getServicesHash().forEach((ns, s) -> {
					double nbr = s.getDemands().get(year) != null ? s.getDemands().get(year) : 0;
					ServiceSet.worldService.get(ns).getDemands().merge(year, nbr, Double::sum);
//					System.out.println("ns= " + ns + " year= " + year + " getWeights= " + s.getWeights().get(year) 
//							+ " regions.size" + CellsLoader.regions.size());
					
					
					ServiceSet.worldService.get(ns).getWeights().merge(year,
							s.getWeights().get(year) / CellsLoader.regions.size(), Double::sum);
					ServiceSet.worldService.get(ns).getTaxes_subsidies().merge(year,
							s.getTaxes_subsidies().get(year) / CellsLoader.regions.size(), Double::sum);

				});
			});
		}
	}

}
