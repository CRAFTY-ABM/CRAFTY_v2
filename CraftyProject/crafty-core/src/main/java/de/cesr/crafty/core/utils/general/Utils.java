package de.cesr.crafty.core.utils.general;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

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
 * - Helpers for splitting/partitioning cell maps for parallel processing:
 * {@link #splitIntoSubsets(ConcurrentHashMap, int)} randomly distributes
 * entries into N concurrent subsets, and {@link #partitionMap(Map, int)}
 * partitions an input map into fixed-size chunks.
 *
 * Notes: - {@link #splitIntoSubsets(ConcurrentHashMap, int)} uses randomness
 * and is therefore not deterministic.
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

	public static List<ConcurrentHashMap<String, Cell>> splitIntoSubsets(ConcurrentHashMap<String, Cell> cellsHash,
			int n) {
		// Create a list to hold the n subsets
		List<ConcurrentHashMap<String, Cell>> subsets = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			subsets.add(new ConcurrentHashMap<>());
		}

		// Distribute keys randomly across the n subsets
		cellsHash.keySet()/**/ .parallelStream().forEach(key -> {
			int subsetIndex = ThreadLocalRandom.current().nextInt(n);
			subsets.get(subsetIndex).put(key, cellsHash.get(key));
		});
		return subsets;
	}
	
	public static List<ConcurrentHashMap<String, Cell>> splitIntoSubsets(List<Cell> cells, int n) {
	    if (n <= 0) {
	        throw new IllegalArgumentException("n must be greater than 0");
	    }

	    // Create a list to hold the n subsets
	    List<ConcurrentHashMap<String, Cell>> subsets = new ArrayList<>(n);

	    for (int i = 0; i < n; i++) {
	        subsets.add(new ConcurrentHashMap<>());
	    }

	    // Distribute cells randomly across the n subsets
	    cells.parallelStream().forEach(cell -> {
	        int subsetIndex = ThreadLocalRandom.current().nextInt(n);

	        // Replace getId() with the correct method that gives the String key of your Cell
	        subsets.get(subsetIndex).put(cell.getId(), cell);
	    });

	    return subsets;
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

		ordered.sort(Comparator.comparingLong(o -> o.key));

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
