package de.cesr.crafty.core.dataLoader;

import java.util.Map;

/**
 * Dispatch enum describing the supported CSV “row processing” modes.
 *
 * {@link CsvProcessors#processCSV(java.nio.file.Path, CsvKind)} reads a CSV file as a stream of lines,
 * builds a header index once, and then delegates each data row to the selected {@code CsvKind}.
 * Each enum constant represents a specific interpretation of the CSV schema and applies the row
 * to the current model state (cells, capitals, services, etc.).
 *
 * Current kinds:
 * - {@link #BASELINE}: creates cells and assigns initial owners from a baseline map.
 * - {@link #CAPITALS}: attaches capital values to existing cells.
 * - {@link #CAPITALS_DEGRADATION}: applies degradation factors and updates cell capitals accordingly.
 * - {@link #SERVICES}: assigns owners and service production values from a services/output CSV (in output data reading, used more for GUI).
 *
 * The {@link #apply(String, Map)} method is intentionally package-private and implemented per constant
 * to keep the streaming import logic fast and avoid repeated if/else dispatch in tight loops.
 */

/**
 * @author Mohamed Byari
 *
 */

public enum CsvKind {

	NFERT_COST {
		@Override
		void apply(String line, Map<String, Integer> index) {
			CsvProcessors.associateNfertCostsToCells(index, line);
		}
	},
	IRRIGATION_COST {
		@Override
		void apply(String line, Map<String, Integer> index) {
			CsvProcessors.associateIrrigationCostToCells(index, line);
		}
	},
	INTENSITY_COST {
		@Override
		void apply(String line, Map<String, Integer> index) {
			CsvProcessors.associateIntensityCostsToCells(index, line);
		}
	},
	CAPITALS {
		@Override
		void apply(String line, Map<String, Integer> index) {
			CsvProcessors.associateCapitalsToCells(index, line);
		}
	},
	CAPITALS_DEGRADATION {
		@Override
		void apply(String line, Map<String, Integer> index) {
			CsvProcessors.associateCapitalsDegradationToCells(index, line);
		}
	},

	SERVICES {
		@Override
		void apply(String line, Map<String, Integer> index) {
			CsvProcessors.associateOutPutServicesToCells(index, line);
		}
	},

	BASELINE {
		@Override
		void apply(String line, Map<String, Integer> index) {
			CsvProcessors.createCells(index, line);
		}
	};

	/** One behaviour per constant */
	abstract void apply(String line, Map<String, Integer> index);
}