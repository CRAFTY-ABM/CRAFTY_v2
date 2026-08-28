package de.cesr.crafty.core.output;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.CapitalUpdater;
import de.cesr.crafty.core.utils.graphics.CellCsvOutputOptions;

public class CellsToCSV {

	private static final CustomLogger LOGGER = new CustomLogger(CellsToCSV.class);

	private static final List<CellCsvColumn> DEFAULT_CELL_COLUMNS = List.of(
			CellCsvColumn.ID,
			CellCsvColumn.X,
			CellCsvColumn.Y,
//			CellCsvColumn.AGENT_ID,
			CellCsvColumn.AGENT,
//			CellCsvColumn.UTILITY,
//			CellCsvColumn.OWNER_LIFE_COUNTER,
			CellCsvColumn.SERVICES
//			,
//			CellCsvColumn.CAPITALS,
//			CellCsvColumn.SERVICES_TAXES,
//			CellCsvColumn.AFT_TAXES
	);

	public static void exportCellsToCSV(String filePath, ConcurrentHashMap<String, Cell> cells,
			CellCsvOutputOptions options) {
		if (options == null) {
			exportCellsToCSV(filePath, cells);
			return;
		}

		exportCellsToCSV(filePath, cells, options.columns(), options.selectedServices(), options.selectedCapitals(),
				options.selectedServiceTaxes(), options.selectedAftTaxes());
	}

	public static void exportCellsToCSV(String filePath, ConcurrentHashMap<String, Cell> cells) {
		exportCellsToCSV(filePath, cells, DEFAULT_CELL_COLUMNS, null, null, null, null);
	}

	public static void exportCellsToCSV(String filePath, ConcurrentHashMap<String, Cell> cells,
			List<CellCsvColumn> columns) {
		exportCellsToCSV(filePath, cells, columns, null, null, null, null);
	}

	public static void exportCellsToCSV(String filePath, ConcurrentHashMap<String, Cell> cells,
			List<CellCsvColumn> columns, List<String> selectedServices, List<String> selectedCapitals,
			List<String> selectedServiceTaxes, List<String> selectedAftTaxes) {
		LOGGER.info("Processing data to write a csv file...");

		if (columns == null || columns.isEmpty()) {
			columns = DEFAULT_CELL_COLUMNS;
		}

		columns = List.copyOf(columns);

		List<String> allServices = List.copyOf(ServiceSet.getServicesList());
		List<String> allCapitals = List.copyOf(CapitalUpdater.getCapitalsList());

		List<String> servicesToWrite = columns.contains(CellCsvColumn.SERVICES)
				? selectOrAll(selectedServices, allServices, "service")
				: List.of();

		List<String> capitalsToWrite = columns.contains(CellCsvColumn.CAPITALS)
				? selectOrAll(selectedCapitals, allCapitals, "capital")
				: List.of();

		List<String> serviceTaxesToWrite = columns.contains(CellCsvColumn.SERVICES_TAXES)
				? selectOrAll(selectedServiceTaxes, allServices, "service tax")
				: List.of();

		List<String> allAfts = AFTsLoader.getAftHash().keySet().stream().sorted().toList();

		List<String> aftTaxesToWrite = columns.contains(CellCsvColumn.AFT_TAXES)
				? selectOrAll(selectedAftTaxes, allAfts, "AFT tax")
				: List.of();

		int[] serviceIndexes = buildServiceIndexes(servicesToWrite, allServices);

		LOGGER.info("Writing processed lines to the CSV file : " + filePath);

		try {
			Path out = Path.of(filePath);

			if (out.getParent() != null) {
				Files.createDirectories(out.getParent());
			}

			try (BufferedWriter writer = new BufferedWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8),
					1024 * 1024)) {
				writer.write(
						buildHeader(columns, servicesToWrite, capitalsToWrite, serviceTaxesToWrite, aftTaxesToWrite));
				writer.newLine();

				for (Cell c : cells.values()) {
					writer.write(buildCsvLine(c, columns, serviceIndexes, capitalsToWrite, serviceTaxesToWrite,
							aftTaxesToWrite));
					writer.newLine();
				}
			}

		} catch (IOException | RuntimeException e) {
			LOGGER.error("Unable to export file: " + filePath + "\n" + e.getMessage());
		}
	}

	private static List<String> selectOrAll(List<String> selected, List<String> all, String typeName) {
		if (selected == null || selected.isEmpty()) {
			return all;
		}

		Set<String> validNames = new HashSet<>(all);

		for (String name : selected) {
			if (!validNames.contains(name)) {
				throw new IllegalArgumentException("Unknown " + typeName + ": " + name);
			}
		}

		return List.copyOf(selected);
	}

	private static int[] buildServiceIndexes(List<String> servicesToWrite, List<String> allServices) {
		Map<String, Integer> indexByService = new HashMap<>();

		for (int i = 0; i < allServices.size(); i++) {
			indexByService.put(allServices.get(i), i);
		}

		int[] indexes = new int[servicesToWrite.size()];

		for (int i = 0; i < servicesToWrite.size(); i++) {
			indexes[i] = indexByService.get(servicesToWrite.get(i));
		}

		return indexes;
	}

	private static String buildHeader(List<CellCsvColumn> columns, List<String> servicesToWrite,
			List<String> capitalsToWrite, List<String> serviceTaxesToWrite, List<String> aftTaxesToWrite) {
		List<String> headers = new ArrayList<>();

		for (CellCsvColumn column : columns) {
			switch (column) {
			case SERVICES -> headers.addAll(servicesToWrite);

			case CAPITALS -> headers.addAll(capitalsToWrite);

			case SERVICES_TAXES -> {
				for (String serviceName : serviceTaxesToWrite) {
					headers.add("Tax_Service_" + serviceName);
				}
			}

			case AFT_TAXES -> {
				for (String aftName : aftTaxesToWrite) {
					headers.add("Tax_AFT_" + aftName);
				}
			}

			default -> headers.add(column.getHeader());
			}
		}

		return String.join(",", headers);
	}

	private static String buildCsvLine(Cell c, List<CellCsvColumn> columns, int[] serviceIndexes,
			List<String> capitalsToWrite, List<String> serviceTaxesToWrite, List<String> aftTaxesToWrite) {
		List<String> values = new ArrayList<>();

		for (CellCsvColumn column : columns) {
			switch (column) {
			case ID -> values.add(csv(c.getID()));

			case X -> values.add(csv(c.getX()));

			case Y -> values.add(csv(c.getY()));

			case AGENT -> values.add(csv(c.getOwner() != null ? c.getOwner().getLabel() : "null"));

			case AGENT_ID -> values.add(csv(getAgentId(c)));

			case UTILITY -> values.add(csv(c.getCurrentUtility()));

			case OWNER_LIFE_COUNTER -> values.add(csv(c.getOwnerLifeCounter()));

			case SERVICES -> {
				double[] currentProd = c.getCurrentProd();

				for (int serviceIndex : serviceIndexes) {
					if (currentProd != null && serviceIndex >= 0 && serviceIndex < currentProd.length) {
						values.add(csv(currentProd[serviceIndex]));
					} else {
						values.add("");
					}
				}
			}

			case CAPITALS -> {
				Map<String, Double> capitals = c.getCapitals();

				for (String capitalName : capitalsToWrite) {
					values.add(csv(capitals != null ? capitals.get(capitalName) : null));
				}
			}

			case SERVICES_TAXES -> {
				Map<String, Double> serviceTaxes = c.getServicesTax();

				for (String serviceName : serviceTaxesToWrite) {
					values.add(csv(getMapValueOrZero(serviceTaxes, serviceName)));
				}
			}

			case AFT_TAXES -> {
				Map<String, Double> aftTaxes = c.getLandTax();

				for (String aftName : aftTaxesToWrite) {
					values.add(csv(getMapValueOrZero(aftTaxes, aftName)));
				}
			}
			}
		}

		return String.join(",", values);
	}

	private static String getAgentId(Cell c) {
		if (c.getOwner() == null) {
			return "0";
		}
		return String.valueOf(c.getOwner().getId());
	}

	private static double getMapValueOrZero(Map<String, Double> map, String key) {
		if (map == null) {
			return 0.0;
		}

		Double value = map.get(key);
		return value != null ? value : 0.0;
	}

	private static String csv(Object value) {
		String s = String.valueOf(value);

		if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
			return "\"" + s.replace("\"", "\"\"") + "\"";
		}

		return s;
	}
}
