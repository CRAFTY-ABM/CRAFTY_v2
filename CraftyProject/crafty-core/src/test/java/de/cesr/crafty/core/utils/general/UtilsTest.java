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
		List<Cell> original = new ArrayList<>();
		int total = 100;
		for (int i = 0; i < total; i++) {
			original.add(new Cell(i, 0));
		}

		int n = 4;
		List<List<Cell>> subsets = Utils.splitIntoSubsetsDeterministic(original, n, 123L, 2030,
				DeterministicRandom.Process.COMPETITION_BATCH_ORDER);

		assertEquals(n, subsets.size(), "Number of subsets should match requested n");

		// All keys must appear exactly once across all subsets
		Set<Cell> union = new HashSet<>();
		int sumSizes = 0;
		for (List<Cell> subset : subsets) {
			sumSizes += subset.size();
			subset.forEach(cell -> {
				assertTrue(union.add(cell), "Cell appears more than once across subsets");
			});
		}

		assertEquals(total, sumSizes, "Total size across subsets should equal original size");
		assertEquals(new HashSet<>(original), union, "Union of subsets should equal the original cells");
	}

	@Test
	void splitIntoSubsetsCanHandleMoreSubsetsThanCells() {
		List<Cell> original = List.of(new Cell(0, 0), new Cell(1, 0), new Cell(0, 1));

		int n = 5; // more subsets than cells
		List<List<Cell>> subsets = Utils.splitIntoSubsetsDeterministic(original, n, 123L, 2030,
				DeterministicRandom.Process.COMPETITION_BATCH_ORDER);

		assertEquals(n, subsets.size());

		Set<Cell> union = new HashSet<>();
		int sumSizes = 0;
		for (List<Cell> subset : subsets) {
			sumSizes += subset.size();
			union.addAll(subset);
		}

		assertEquals(original.size(), sumSizes);
		assertEquals(new HashSet<>(original), union);
	}

	@Test
	void deterministicSubsetsRepeatExactlyForSameSeed() {
		List<Cell> cells = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			cells.add(new Cell(i, i % 3));
		}

		List<List<Cell>> first = Utils.splitIntoSubsetsDeterministic(cells, 4, 99L, 2040,
				DeterministicRandom.Process.COMPETITION_BATCH_ORDER);
		Collections.reverse(cells);
		List<List<Cell>> second = Utils.splitIntoSubsetsDeterministic(cells, 4, 99L, 2040,
				DeterministicRandom.Process.COMPETITION_BATCH_ORDER);

		assertEquals(first, second);
	}

}
