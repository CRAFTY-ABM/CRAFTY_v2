package de.cesr.crafty.core.utils.graphics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.output.CellCsvColumn;

public class CellCsvOutputCli {

	public static CellCsvOutputOptions loadFromConfig() {
		if (ConfigLoader.config == null || ConfigLoader.config.cell_output_columns == null) {
			return null;
		}

		return parse(ConfigLoader.config.cell_output_columns);
	}

	public static CellCsvOutputOptions parse(List<Object> yamlItems) {
		List<CellCsvColumn> columns = new ArrayList<>();
		Set<CellCsvColumn> seenColumns = new HashSet<>();

		List<String> selectedServices = null;
		List<String> selectedCapitals = null;
		List<String> selectedServiceTaxes = null;
		List<String> selectedAftTaxes = null;

		Set<String> seenGroups = new HashSet<>();

		for (Object item : yamlItems) {
			if (item instanceof String name) {
				addSimpleColumn(name, columns, seenColumns);
				continue;
			}

			if (item instanceof Map<?, ?> map) {
				if (map.size() != 1) {
					throw new IllegalArgumentException(
							"Each csv_output_structure map entry must contain exactly one key: " + map);
				}

				Map.Entry<?, ?> entry = map.entrySet().iterator().next();

				String key = String.valueOf(entry.getKey()).trim();
				Object value = entry.getValue();

				if (!seenGroups.add(key.toLowerCase())) {
					throw new IllegalArgumentException("Duplicate csv_output_structure group: " + key);
				}

				switch (key.toLowerCase()) {
				case "services" -> {
					addColumn(CellCsvColumn.SERVICES, columns, seenColumns);
					selectedServices = parseSelection(value, key);
				}

				case "capitals" -> {
					addColumn(CellCsvColumn.CAPITALS, columns, seenColumns);
					selectedCapitals = parseSelection(value, key);
				}

				case "service_taxes", "services_taxes" -> {
					addColumn(CellCsvColumn.SERVICES_TAXES, columns, seenColumns);
					selectedServiceTaxes = parseSelection(value, key);
				}

				case "aft_taxes", "land_taxes" -> {
					addColumn(CellCsvColumn.AFT_TAXES, columns, seenColumns);
					selectedAftTaxes = parseSelection(value, key);
				}

				default -> throw new IllegalArgumentException("Unknown csv_output_structure group: " + key);
				}

				continue;
			}

			throw new IllegalArgumentException("Invalid csv_output_structure entry: " + item);
		}

		if (columns.isEmpty()) {
			return null;
		}

		return new CellCsvOutputOptions(columns, selectedServices, selectedCapitals, selectedServiceTaxes,
				selectedAftTaxes);
	}

	private static void addSimpleColumn(String rawName, List<CellCsvColumn> columns, Set<CellCsvColumn> seenColumns) {
		String name = rawName.trim().toLowerCase();

		switch (name) {
		case "cell_id", "id" -> addColumn(CellCsvColumn.ID, columns, seenColumns);

		case "coordinates" -> {
			addColumn(CellCsvColumn.X, columns, seenColumns);
			addColumn(CellCsvColumn.Y, columns, seenColumns);
		}

		case "x" -> addColumn(CellCsvColumn.X, columns, seenColumns);

		case "y" -> addColumn(CellCsvColumn.Y, columns, seenColumns);

		case "aft_id", "agent_id", "owner_id" -> addColumn(CellCsvColumn.AGENT_ID, columns, seenColumns);

		case "aft_name", "aft", "agent", "agent_name", "owner", "owner_name" ->
			addColumn(CellCsvColumn.AGENT, columns, seenColumns);

		case "utility" -> addColumn(CellCsvColumn.UTILITY, columns, seenColumns);

		case "owner_life_counter" -> addColumn(CellCsvColumn.OWNER_LIFE_COUNTER, columns, seenColumns);

		default -> throw new IllegalArgumentException("Unknown csv_output_structure column: " + rawName);
		}
	}

	private static void addColumn(CellCsvColumn column, List<CellCsvColumn> columns, Set<CellCsvColumn> seenColumns) {
		if (seenColumns.add(column)) {
			columns.add(column);
		}
	}

	private static List<String> parseSelection(Object value, String groupName) {
		if (value == null) {
			throw new IllegalArgumentException("csv_output_structure." + groupName + " must be ALL or a list");
		}

		if (value instanceof String s) {
			if ("ALL".equalsIgnoreCase(s.trim())) {
				return null;
			}

			throw new IllegalArgumentException(
					"csv_output_structure." + groupName + " must be ALL or a list, not: " + s);
		}

		if (value instanceof List<?> list) {
			if (list.isEmpty()) {
				throw new IllegalArgumentException(
						"csv_output_structure." + groupName + " list cannot be empty. Use ALL or remove the group.");
			}

			List<String> result = new ArrayList<>();

			for (Object item : list) {
				if (!(item instanceof String s) || s.isBlank()) {
					throw new IllegalArgumentException(
							"Invalid value in csv_output_structure." + groupName + ": " + item);
				}

				result.add(s.trim());
			}

			return result;
		}

		throw new IllegalArgumentException("csv_output_structure." + groupName
				+ " must be ALL or a YAML list like [Carbon, Softwood]. Invalid value: " + value);
	}

}