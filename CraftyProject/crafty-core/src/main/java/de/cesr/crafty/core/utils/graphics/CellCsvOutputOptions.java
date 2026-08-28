package de.cesr.crafty.core.utils.graphics;


import java.util.List;

import de.cesr.crafty.core.output.CellCsvColumn;

public record CellCsvOutputOptions(
		List<CellCsvColumn> columns,
		List<String> selectedServices,
		List<String> selectedCapitals,
		List<String> selectedServiceTaxes,
		List<String> selectedAftTaxes
) {

	public CellCsvOutputOptions {
		columns = columns == null ? List.of() : List.copyOf(columns);
		selectedServices = copyOrNull(selectedServices);
		selectedCapitals = copyOrNull(selectedCapitals);
		selectedServiceTaxes = copyOrNull(selectedServiceTaxes);
		selectedAftTaxes = copyOrNull(selectedAftTaxes);
	}

	private static List<String> copyOrNull(List<String> list) {
		return list == null ? null : List.copyOf(list);
	}
}