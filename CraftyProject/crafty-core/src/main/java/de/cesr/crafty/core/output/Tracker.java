package de.cesr.crafty.core.output;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.AbstractUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.DeterministicAggregation;
import de.cesr.crafty.core.utils.general.Utils;

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

	public static Map<String, Map<Integer, Map<String, Integer>>> sankeydata = new ConcurrentHashMap<>();

	public Tracker() {
		Iterator<String> var1 = AFTsLoader.getAftHash().keySet().iterator();

		while (var1.hasNext()) {
			String label = (String) var1.next();
			Map<Integer, Map<String, Integer>> yearly = new LinkedHashMap<>();

			for (int i = Timestep.getStartYear(); i <= Timestep.getEndtYear(); ++i) {
				Map<String, Integer> tmp = new ConcurrentHashMap<>();
				Iterator<String> var6 = AFTsLoader.getAftHash().keySet().iterator();

				while (var6.hasNext()) {
					String label2 = (String) var6.next();
					tmp.put(label2, 0);
				}

				yearly.put(i, tmp);
			}

			sankeydata.put(label, yearly);
		}
	}

	@Override
	public void toSchedule() {
		modelRunner.scheduleRepeating(this);
	}

	@Override
	public void step() {
		if (ConfigLoader.config.track_changes && ConfigLoader.config.generate_output_files) {
			long staetTime = System.currentTimeMillis();
			Map<String, Map<String, Double>> container = new ConcurrentHashMap<>();
			AFTsLoader.getAftHash().keySet().stream().sorted().forEach(label -> {
				ConcurrentHashMap<String, Double> tmp = new ConcurrentHashMap<>();
				container.put(label, tmp);
			});
			CellsLoader.regions.keySet().stream().sorted()
					.forEach(regionName -> accumulateCellSupply(CellsLoader.regions.get(regionName).getCells().values(),
							container));
			AFTsLoader.hashAgentNbr.forEach((label, a) -> {
				container.get(label).put("AggregateAFT", (double) a);
			});
			ConcurrentHashMap<String, Double> totalSupplyTracked = new ConcurrentHashMap<>();
			container.keySet().stream().sorted().forEach(aft -> container.get(aft).entrySet().stream()
					.sorted(Map.Entry.comparingByKey())
					.forEach(entry -> totalSupplyTracked.merge(entry.getKey(), entry.getValue(), Double::sum)));
			writeMatrixToCSV(container, ConfigLoader.config.output_folder_name + File.separator + "SupplyTracker_"
					+ Timestep.getCurrentYear() + ".csv");
			LOGGER.trace("Time taken for trackSupply " + (System.currentTimeMillis() - staetTime) + " ms");
		}
		if (Timestep.getTick() == Timestep.getSize() - 1) {
			sankyData();
		}
	}

	private void sankyData() {
		String p = PathTools
				.makeDirectory(ConfigLoader.config.output_folder_name + File.separator + "land_Use_Transitions");
		Iterator<String> var2 = AFTsLoader.getAftHash().keySet().iterator();

		while (var2.hasNext()) {
			String newOwner = var2.next();
			String[][] cs = new String[Timestep.getSize() + 1][AFTsLoader.getAftHash().size() + 1];
			cs[0][0] = "";
			int k = 1;

			String oldOwner;
			for (Iterator<String> var6 = AFTsLoader.getAftHash().keySet().iterator(); var6
					.hasNext(); cs[0][k++] = oldOwner) {
				oldOwner = var6.next();
			}

			for (int year = 0; year < Timestep.getSize(); year++) {
				cs[year + 1][0] = String.valueOf(year + Timestep.getStartYear());
			}

			for (int year = 0; year < Timestep.getSize(); year++) {
				for (String label : AFTsLoader.getAftHash().keySet()) {
					cs[year + 1][Utils.indexof(label, cs[0])] = String
							.valueOf(sankeydata.get(newOwner).get(year + Timestep.getStartYear()).get(label));
				}
			}

			CsvTools.writeCSVfile(cs, Paths.get(p + File.separator + "annual_" + newOwner + ".csv"));
		}

		Map<String, Map<String, Double>> total = new ConcurrentHashMap<>();

		for (String newOwner : AFTsLoader.getAftHash().keySet()) {
			total.put(newOwner, new ConcurrentHashMap<>());
			for (int year = 0; year < Timestep.getSize(); year++) {
				for (String oldOwner : AFTsLoader.getAftHash().keySet()) {
					double value = sankeydata.get(newOwner).get(year + Timestep.getStartYear()).get(oldOwner);
					total.get(newOwner).merge(oldOwner, value, Double::sum);
				}
			}
		}
		writeMatrixToCSV(total,
				ConfigLoader.config.output_folder_name + File.separator + "land_use_transition_aggregation.csv",
				"new\\old");
	}

	public static void trackSupply(String regionName) {
		if (ConfigLoader.config.output_folder_name != null && ConfigLoader.config.track_changes) {
			Map<String, Map<String, Double>> container = new ConcurrentHashMap<>();
			AFTsLoader.getAftHash().keySet().stream().sorted().forEach(label -> {
				ConcurrentHashMap<String, Double> tmp = new ConcurrentHashMap<>();
				container.put(label, tmp);
			});
			accumulateCellSupply(CellsLoader.regions.get(regionName).getCells().values(), container);
			AFTsLoader.hashAgentNbrRegions.get(regionName).forEach((label, a) -> {
				container.get(label).put("AggregateAFT", (double) a);
			});
			writeMatrixToCSV(container, ConfigLoader.config.output_folder_name + File.separator + "region_" + regionName
					+ File.separator + "SupplyTracker_" + Timestep.getCurrentYear() + ".csv");
		}
	}

	private static void accumulateCellSupply(Collection<Cell> cells, Map<String, Map<String, Double>> container) {
		for (Cell cell : DeterministicAggregation.cellsInStableOrder(cells)) {
			if (cell.getOwner() == null) {
				continue;
			}
			for (int i = 0; i < ServiceSet.getServicesList().size(); i++) {
				container.get(cell.getOwner().getLabel()).merge(ServiceSet.getServicesList().get(i),
						cell.getCurrentProd()[i], Double::sum);
			}
		}
	}

	public static void writeMatrixToCSV(Map<String, Map<String, Double>> container, String fileName) {
		writeMatrixToCSV(container, fileName, "");
	}

	public static void writeMatrixToCSV(Map<String, Map<String, Double>> container, String fileName, String origine) {

		LOGGER.info("writing CSV file:" + fileName);

		// Comparator<String> order = String.CASE_INSENSITIVE_ORDER;
		Comparator<String> order = Comparator.naturalOrder();

		// Collect & sort column headers
		Set<String> columnHeaders = new TreeSet<>(order);
		for (Map<String, Double> nestedMap : container.values()) {
			if (nestedMap != null) {
				columnHeaders.addAll(nestedMap.keySet());
			}
		}

		// Collect & sort row keys
		Set<String> rowHeaders = new TreeSet<>(order);
		rowHeaders.addAll(container.keySet());

		try (FileWriter writer = new FileWriter(fileName)) {
			// Write header row
			writer.append(origine == null ? "" : origine);
			for (String header : columnHeaders) {
				writer.append(',').append(header);
			}
			writer.append('\n');

			// Write rows in sorted order
			for (String rowKey : rowHeaders) {
				Map<String, Double> valuesMap = container.get(rowKey);

				writer.append(rowKey);
				for (String col : columnHeaders) {
					writer.append(',');

					Double v = (valuesMap == null) ? null : valuesMap.get(col);
					writer.append(v == null ? "0" : v.toString());
				}
				writer.append('\n');
			}
		} catch (IOException e) {
			System.err.println("Error writing the CSV file: " + e.getMessage());
		}
	}

}
