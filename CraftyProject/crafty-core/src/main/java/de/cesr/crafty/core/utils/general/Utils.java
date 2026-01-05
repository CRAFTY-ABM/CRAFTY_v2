package de.cesr.crafty.core.utils.general;

import java.util.ArrayList;
import java.util.Arrays;
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
 * This class currently provides:
 * - Simple index lookup for a string in an array ({@link #indexof(String, String[])}).
 * - Fast and defensive parsing of numbers from strings:
 *   {@link #sToD(String)} returns 0.0 for null/empty/non-numeric values and otherwise parses using
 *   {@link ch.randelshofer.fastdoubleparser.JavaDoubleParser}.
 *   {@link #sToI(String)} is a convenience wrapper around {@link #sToD(String)}.
 * - Helpers for splitting/partitioning cell maps for parallel processing:
 *   {@link #splitIntoSubsets(ConcurrentHashMap, int)} randomly distributes entries into N concurrent subsets,
 *   and {@link #partitionMap(Map, int)} partitions an input map into fixed-size chunks.
 *
 * Notes:
 * - {@link #splitIntoSubsets(ConcurrentHashMap, int)} uses randomness and is therefore not deterministic.
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
}

