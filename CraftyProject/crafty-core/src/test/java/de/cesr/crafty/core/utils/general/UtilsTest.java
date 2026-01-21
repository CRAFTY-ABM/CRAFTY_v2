package de.cesr.crafty.core.utils.general;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cesr.crafty.core.crafty.Cell;

class UtilsTest {

	@TempDir
	Path tempDir;

	// ---------- indexof ----------

	@Test
	void indexofShouldReturnCorrectIndexOrMinusOne() {
		String[] arr = { "A", "B", "C" };

		assertEquals(0, Utils.indexof("A", arr));
		assertEquals(1, Utils.indexof("B", arr));
		assertEquals(2, Utils.indexof("C", arr));
		assertEquals(-1, Utils.indexof("X", arr));
	}

	// ---------- sToD / sToI ----------

	@Test
	void sToDShouldParseValidNumbersAndReturnZeroForInvalid() {
		assertEquals(12.5, Utils.sToD("12.5"), 1e-9);
		assertEquals(-3.0, Utils.sToD("-3"), 1e-9);

		// null, empty and non-numeric → 0
		assertEquals(0.0, Utils.sToD(null), 1e-9);
		assertEquals(0.0, Utils.sToD(""), 1e-9);
		assertEquals(0.0, Utils.sToD("abc"), 1e-9);
	}

	@Test
	void sToIShouldCastDoubleResultToInt() {
		assertEquals(10, Utils.sToI("10"));
		assertEquals(10, Utils.sToI("10.9")); // cast from 10.9 → 10
		assertEquals(0, Utils.sToI("not-a-number"));
		assertEquals(0, Utils.sToI(null));
	}

	// ---------- splitIntoSubsets ----------

	@Test
	void splitIntoSubsetsShouldDistributeAllKeysAcrossSubsets() {
		ConcurrentHashMap<String, Cell> original = new ConcurrentHashMap<>();
		int total = 100;
		for (int i = 0; i < total; i++) {
			original.put("cell-" + i, new Cell(0, 0)); // value doesn't matter here
		}

		int n = 4;
		List<ConcurrentHashMap<String, Cell>> subsets = Utils.splitIntoSubsets(original, n);

		assertEquals(n, subsets.size(), "Number of subsets should match requested n");

		// All keys must appear exactly once across all subsets
		Set<String> union = new HashSet<>();
		int sumSizes = 0;
		for (Map<String, Cell> subset : subsets) {
			sumSizes += subset.size();
			subset.keySet().forEach(key -> {
				assertTrue(union.add(key), "Key " + key + " appears more than once across subsets");
			});
		}

		assertEquals(total, sumSizes, "Total size across subsets should equal original size");
		assertEquals(original.keySet(), union, "Union of subset keys should equal original key set");
	}

	@Test
	void splitIntoSubsetsCanHandleMoreSubsetsThanCells() {
		ConcurrentHashMap<String, Cell> original = new ConcurrentHashMap<>();
		original.put("a", new Cell(0, 0));
		original.put("b", new Cell(1, 0));
		original.put("c", new Cell(0, 1));

		int n = 5; // more subsets than cells
		List<ConcurrentHashMap<String, Cell>> subsets = Utils.splitIntoSubsets(original, n);

		assertEquals(n, subsets.size());

		Set<String> union = new HashSet<>();
		int sumSizes = 0;
		for (Map<String, Cell> subset : subsets) {
			sumSizes += subset.size();
			union.addAll(subset.keySet());
		}

		assertEquals(original.size(), sumSizes);
		assertEquals(original.keySet(), union);
	}

}
