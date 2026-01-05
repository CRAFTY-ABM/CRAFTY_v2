package de.cesr.crafty.core.output;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.AbstractUpdater;
import de.cesr.crafty.core.updaters.Timestep;

/**
 * Optional diagnostics updater that tracks and exports detailed supply composition by AFT.
 *
 * When enabled (see {@link ConfigLoader#config} flags {@code track_changes} and {@code generate_output_files}),
 * this updater aggregates per-service production across cells and writes a CSV snapshot each year.
 * The output is primarily intended for debugging, calibration, and consistency checks (e.g., verifying
 * that total supply equals the sum of AFT contributions, or inspecting how supply shifts across AFTs over time).
 *
 * Two export modes are supported:
 * - Global tracking in {@link #step()} (aggregates over all regions and writes to the root output folder).
 * - Region-specific tracking via {@link #trackSupply(int, String)} (aggregates over one region and writes
 *   into the region subfolder).
 *
 * Implementation notes:
 * - Aggregation is performed with {@link ConcurrentHashMap#merge(Object, Object, java.util.function.BiFunction)}
 *   and uses parallel streams over region cells for performance.
 * - The resulting structure is a nested map: AFT label -> (service name -> summed production),
 *   with an extra "AggregateAFT" column storing the number of cells/agents for that AFT.
 * - {@link #writeCSV(ConcurrentHashMap, String)} writes a rectangular CSV table, using the union of all
 *   nested keys as column headers and filling missing values with 0.
 */

/**
 * @author Mohamed Byari
 *
 */

public class Tracker extends AbstractUpdater {
	private static final CustomLogger LOGGER = new CustomLogger(Tracker.class);

	@Override
	public void toSchedule() {
		modelRunner.scheduleRepeating(this);
	}

	@Override
	public void step() {
		if (ConfigLoader.config.track_changes && ConfigLoader.config.generate_output_files) {
			long staetTime = System.currentTimeMillis();
			ConcurrentHashMap<String, ConcurrentHashMap<String, Double>> container = new ConcurrentHashMap<>();
			AFTsLoader.getAftHash().values().forEach(a -> {
				ConcurrentHashMap<String, Double> tmp = new ConcurrentHashMap<>();
				container.put(a.getLabel(), tmp);
			});
			CellsLoader.regions.values().forEach(region -> {
				region.getCells().values()/**/ .parallelStream().forEach(c -> {
					for (int i = 0; i < ServiceSet.getServicesList().size(); i++) {
						if (c.getOwner() != null)
							container.get(c.getOwner().getLabel()).merge(ServiceSet.getServicesList().get(i),
									c.getCurrentProd()[i], Double::sum);
					}
				});
			});
			AFTsLoader.hashAgentNbr.forEach((label, a) -> {
				container.get(label).put("AggregateAFT", (double) a);
			});
			ConcurrentHashMap<String, Double> totalSupplyTracked = new ConcurrentHashMap<>();
			container.forEach((aft, v) -> {
				v.forEach((sn, sv) -> {
					totalSupplyTracked.merge(sn, sv, Double::sum);
				});
			});
			writeCSV(container, ConfigLoader.config.output_folder_name + File.separator + "SupplyTracker_"
					+ Timestep.getCurrentYear() + ".csv");
			LOGGER.trace("Time taken for trackSupply " + (System.currentTimeMillis() - staetTime) + " ms");
		}

	}

	public static void trackSupply(String regionName) {
		if (ConfigLoader.config.output_folder_name != null && ConfigLoader.config.track_changes) {
			ConcurrentHashMap<String, ConcurrentHashMap<String, Double>> container = new ConcurrentHashMap<>();
			AFTsLoader.getAftHash().values().forEach(a -> {
				ConcurrentHashMap<String, Double> tmp = new ConcurrentHashMap<>();
				container.put(a.getLabel(), tmp);
			});
			CellsLoader.regions.get(regionName).getCells().values()/**/ .parallelStream().forEach(c -> {
				for (int i = 0; i < ServiceSet.getServicesList().size(); i++) {
					if (c.getOwner() != null)
						container.get(c.getOwner().getLabel()).merge(ServiceSet.getServicesList().get(i),
								c.getCurrentProd()[i], Double::sum);
				}
			});
			AFTsLoader.hashAgentNbrRegions.get(regionName).forEach((label, a) -> {
				container.get(label).put("AggregateAFT", (double) a);
			});
			writeCSV(container, ConfigLoader.config.output_folder_name + File.separator + "region_" + regionName
					+ File.separator + "SupplyTracker_" + Timestep.getCurrentYear() + ".csv");
		}
	}

	public static void writeCSV(ConcurrentHashMap<String, ConcurrentHashMap<String, Double>> container,
			String fileName) {
		LOGGER.info("writing CSV file:" + fileName);
		// Collect all possible column headers (keys from nested HashMaps)
		Set<String> columnHeaders = new TreeSet<>();
		for (ConcurrentHashMap<String, Double> nestedMap : container.values()) {
			columnHeaders.addAll(nestedMap.keySet());
		}

		try (FileWriter writer = new FileWriter(fileName)) {
			// Write column headers
			writer.append("ID");
			for (String header : columnHeaders) {
				writer.append(',').append(header);
			}
			writer.append('\n');

			// Write rows
			for (Map.Entry<String, ConcurrentHashMap<String, Double>> entry : container.entrySet()) {
				String key = entry.getKey();
				ConcurrentHashMap<String, Double> valuesMap = entry.getValue();

				writer.append(key);
				for (String header : columnHeaders) {
					writer.append(',');
					if (valuesMap.containsKey(header)) {
						writer.append(valuesMap.get(header).toString());
					} else {
						writer.append("0"); // or writer.append("") for empty value if that's preferred
					}
				}
				writer.append('\n');
			}
		} catch (IOException e) {
			System.err.println("Error writing the CSV file: " + e.getMessage());
		}
	}

}
