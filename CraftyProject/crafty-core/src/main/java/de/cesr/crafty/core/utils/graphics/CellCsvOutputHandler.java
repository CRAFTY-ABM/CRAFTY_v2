package de.cesr.crafty.core.utils.graphics;


import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.output.CellsToCSV;

public class CellCsvOutputHandler {

	private static final CustomLogger LOGGER = new CustomLogger(CellCsvOutputHandler.class);

	private final CellCsvOutputOptions options;

	public CellCsvOutputHandler(CellCsvOutputOptions options) {
		this.options = options;
	}

	public static CellCsvOutputHandler fromConfig() {
		return new CellCsvOutputHandler(CellCsvOutputCli.loadFromConfig());
	}

	public void export(String filePath, ConcurrentHashMap<String, Cell> cells) {
		if (cells == null || cells.isEmpty()) {
			LOGGER.warn("No cells available for CSV export: " + filePath);
			return;
		}

		CellsToCSV.exportCellsToCSV(filePath, cells, options);
	}
}