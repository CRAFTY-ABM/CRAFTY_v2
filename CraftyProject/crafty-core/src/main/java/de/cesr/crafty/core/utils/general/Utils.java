package de.cesr.crafty.core.utils.general;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.math.NumberUtils;
import ch.randelshofer.fastdoubleparser.JavaDoubleParser;
import de.cesr.crafty.core.crafty.Cell;

/**
 * Miscellaneous general-purpose utilities used across the CRAFTY codebase.
 *
 * This class currently provides: - Simple index lookup for a string in an array
 * ({@link #indexof(String, String[])}). - Fast and defensive parsing of numbers
 * from strings: {@link #sToD(String)} returns 0.0 for null/empty/non-numeric
 * values and otherwise parses using
 * {@link ch.randelshofer.fastdoubleparser.JavaDoubleParser}.
 * {@link #sToI(String)} is a convenience wrapper around {@link #sToD(String)}.
 * - Helpers for deterministic partitioning of cells for parallel processing.
 */

public class Utils {

	public static int indexof(String s, String[] tmp) {
		return Arrays.asList(tmp).indexOf(s);
	}

	public static double sToD(String s) {
		return (s == null || s.isEmpty()) || !NumberUtils.isCreatable(s) ? 0d : JavaDoubleParser.parseDouble(s);
	}

	public static int sToI(String s) {
		return (int) sToD(s);
	}

	public static List<List<Cell>> splitIntoSubsetsDeterministic(Collection<Cell> cells, int n, long runSeed, int year,
			int processId) {

		if (cells == null || cells.isEmpty()) {
			return List.of();
		}

		if (n <= 1) {
			return List.of(new ArrayList<>(cells));
		}

		class OrderedCell {
			final Cell cell;
			final long key;

			OrderedCell(Cell cell, long key) {
				this.cell = cell;
				this.key = key;
			}
		}

		List<OrderedCell> ordered = new ArrayList<>(cells.size());
		for (Cell c : cells) {
			long stableId = DeterministicRandom.stableCellId(c.getX(), c.getY());
			long key = DeterministicRandom.randomLong(runSeed, year, processId, stableId, 0L, 0);
			ordered.add(new OrderedCell(c, key));
		}

		ordered.sort(Comparator.comparingLong((OrderedCell o) -> o.key)
				.thenComparingLong(o -> DeterministicRandom.stableCellId(o.cell.getX(), o.cell.getY())));

		int total = ordered.size();
		List<List<Cell>> subsets = new ArrayList<>(n);

		for (int i = 0; i < n; i++) {
			int from = i * total / n;
			int to = (i + 1) * total / n;

			List<Cell> subset = new ArrayList<>(to - from);
			for (int j = from; j < to; j++) {
				subset.add(ordered.get(j).cell);
			}
			subsets.add(subset);
		}

		return subsets;
	}

}
