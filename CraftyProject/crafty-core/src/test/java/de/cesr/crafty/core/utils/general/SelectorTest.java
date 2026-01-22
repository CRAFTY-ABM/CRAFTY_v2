package de.cesr.crafty.core.utils.general;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import de.cesr.crafty.core.crafty.Cell;

class SelectorTest {

	private Cell mockCell(int x, int y) {
		Cell c = mock(Cell.class);
		when(c.getX()).thenReturn(x);
		when(c.getY()).thenReturn(y);
		return c;
	}

	// ---------- mix64 ----------

	@Test
	void mix64ShouldBeDeterministic() {
		long z = 123456789L;
		long r1 = Selector.mix64(z);
		long r2 = Selector.mix64(z);

		assertEquals(r1, r2, "mix64 should return the same output for the same input");
	}

	@Test
	void mix64ShouldChangeValueForDifferentInputs() {
		long z1 = 1L;
		long z2 = 2L;

		long r1 = Selector.mix64(z1);
		long r2 = Selector.mix64(z2);

		assertNotEquals(r1, r2, "mix64 should generally differ for different inputs");
	}

	// ---------- hash2D ----------

	@Test
	void hash2DShouldBeDeterministicForSameInputs() {
		int x = 10;
		int y = 20;
		long seed = 42L;

		long h1 = Selector.hash2D(x, y, seed);
		long h2 = Selector.hash2D(x, y, seed);

		assertEquals(h1, h2, "hash2D must be deterministic for same (x,y,seed)");
	}

	@Test
	void hash2DShouldChangeWhenSeedOrCoordsChange() {
		int x = 10;
		int y = 20;
		long seed1 = 42L;
		long seed2 = 43L;

		long hBase = Selector.hash2D(x, y, seed1);
		long hSeedChange = Selector.hash2D(x, y, seed2);
		long hXChange = Selector.hash2D(x + 1, y, seed1);
		long hYChange = Selector.hash2D(x, y + 1, seed1);

		assertNotEquals(hBase, hSeedChange);
		assertNotEquals(hBase, hXChange);
		assertNotEquals(hBase, hYChange);
	}

	// ---------- randomSeed edge cases ----------

	@Test
	void randomSeedShouldReturnEmptyIfInputIsEmpty() {
		ConcurrentHashMap<String, Cell> empty = new ConcurrentHashMap<>();

		ConcurrentHashMap<String, Cell> subset = Selector.randomSeed(empty, 0.5, 123L);

		assertTrue(subset.isEmpty(), "Empty input should produce empty subset");
	}

	@Test
	void randomSeedShouldReturnEmptyIfPercentageIsZeroOrNegative() {
		ConcurrentHashMap<String, Cell> input = new ConcurrentHashMap<>();
		input.put("0|0", mockCell(0, 0));
		input.put("1|0", mockCell(1, 0));

		ConcurrentHashMap<String, Cell> subsetZero = Selector.randomSeed(input, 0.0, 123L);
		ConcurrentHashMap<String, Cell> subsetNegative = Selector.randomSeed(input, -0.3, 123L);

		assertTrue(subsetZero.isEmpty(), "Percentage 0 should yield empty subset");
		assertTrue(subsetNegative.isEmpty(), "Negative percentage should be clamped to 0 -> empty subset");
	}

	@Test
	void randomSeedShouldReturnFullCopyIfPercentageIsOneOrMore() {
		ConcurrentHashMap<String, Cell> input = new ConcurrentHashMap<>();
		input.put("0|0", mockCell(0, 0));
		input.put("1|0", mockCell(1, 0));

		ConcurrentHashMap<String, Cell> subsetOne = Selector.randomSeed(input, 1.0, 123L);
		ConcurrentHashMap<String, Cell> subsetMore = Selector.randomSeed(input, 1.5, 123L);

		assertEquals(input.size(), subsetOne.size());
		assertEquals(input.size(), subsetMore.size());

		// Must contain the same keys
		assertEquals(input.keySet(), subsetOne.keySet());
		assertEquals(input.keySet(), subsetMore.keySet());
	}

	// ---------- randomSeed normal behaviour ----------

	@Test
	void randomSeedShouldSelectCorrectNumberOfCells() {
		// Create 10 cells
		ConcurrentHashMap<String, Cell> cells = new ConcurrentHashMap<>();
		for (int i = 0; i < 10; i++) {
			Cell c = mockCell(i, 0);
			cells.put(i + "|0", c);
		}

		// 30% of 10 => round(3.0) = 3
		ConcurrentHashMap<String, Cell> subset = Selector.randomSeed(cells, 0.3, 123L);

		assertEquals(3, subset.size(), "Subset should contain N = round(size * p) cells");
		assertTrue(cells.keySet().containsAll(subset.keySet()), "Subset keys must be subset of original keys");
	}

	@Test
	void randomSeedShouldBeDeterministicForSameSeed() {
		ConcurrentHashMap<String, Cell> cells = new ConcurrentHashMap<>();
		for (int i = 0; i < 20; i++) {
			Cell c = mockCell(i, i); // distinct coords
			cells.put(i + "|" + i, c);
		}

		ConcurrentHashMap<String, Cell> subset1 = Selector.randomSeed(cells, 0.25, 999L);
		ConcurrentHashMap<String, Cell> subset2 = Selector.randomSeed(cells, 0.25, 999L);

		assertEquals(subset1.keySet(), subset2.keySet(), "Same seed and input should produce the same subset of keys");
	}

	@Test
	void randomSeedShouldUsuallyDifferForDifferentSeeds() {
		ConcurrentHashMap<String, Cell> cells = new ConcurrentHashMap<>();
		for (int i = 0; i < 20; i++) {
			Cell c = mockCell(i, i * 2); // distinct coords
			cells.put(i + "|" + (i * 2), c);
		}

		ConcurrentHashMap<String, Cell> subset1 = Selector.randomSeed(cells, 0.25, 1L);
		ConcurrentHashMap<String, Cell> subset2 = Selector.randomSeed(cells, 0.25, 2L);

		// In theory they could be equal by chance, but probability is tiny for 20
		// cells.
		assertNotEquals(subset1.keySet(), subset2.keySet(),
				"Different seeds should (with high probability) produce different subsets");
	}
}