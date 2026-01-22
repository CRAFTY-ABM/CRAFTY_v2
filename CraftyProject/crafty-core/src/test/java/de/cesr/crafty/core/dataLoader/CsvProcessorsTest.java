package de.cesr.crafty.core.dataLoader;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;

/**
 * Unit tests for CsvProcessors, focusing on the parallel behavior of
 * processCSV.
 *
 * NOTE: processCSV takes a CsvKind ENUM constant (e.g. CsvKind.BASELINE), not a
 * lambda expression.
 */
public class CsvProcessorsTest {

	@BeforeEach
	void resetGlobalState() {
		// Adjust these resets to your real implementations if needed.
		// The idea is to start each test with a clean global/static state.
		if (CellsLoader.hashCell != null) {
			CellsLoader.hashCell.clear();
		}

		if (AFTsLoader.getAftHash() != null) {
			AFTsLoader.getAftHash().clear();
		}

		// Minimal AFT mapping needed by CsvProcessors.createCells(...)
		Aft aft1 = mock(Aft.class);
		Aft aft2 = mock(Aft.class);

		when(aft1.getLabel()).thenReturn("aft1");
		when(aft2.getLabel()).thenReturn("aft2");
		AFTsLoader.getAftHash().put("AFT1", aft1);
		AFTsLoader.getAftHash().put("AFT2", aft2);
	}

	/**
	 * Stress-test: run the parallel CSV import multiple times. If the underlying
	 * data structures are not thread-safe, this is likely to fail (missing cells,
	 * wrong size, etc.).
	 */
	@RepeatedTest(5)
	void processCSV_baselinePopulatesCellsLoaderSafely(@TempDir Path tempDir) throws IOException {
		// 1) Create a temporary CSV file with many lines
		Path csv = tempDir.resolve("baseline.csv");

		int n = 1000; // number of data rows
		List<String> lines = new ArrayList<>();
		lines.add("X,Y,ID,FR"); // header

		// generate a grid of cells
		int width = 40; // width * height >= n
		int height = (n + width - 1) / width;

		int count = 0;
		for (int y = 0; y < height && count < n; y++) {
			for (int x = 0; x < width && count < n; x++) {
				String id = "id-" + count;
				// alternate FR between AFT1 and AFT2
				String fr = (count % 2 == 0) ? "AFT1" : "AFT2";
				lines.add(x + "," + y + "," + id + "," + fr);
				count++;
			}
		}
		Files.write(csv, lines);

		// 2) Call processCSV with the BASELINE kind.
		// BASELINE.apply(...) internally calls CsvProcessors.createCells(...)
		CsvProcessors.processCSV(csv, CsvKind.BASELINE);

		// 3) Assert: all rows were processed exactly once
		assertEquals(n, CellsLoader.hashCell.size(), "All data rows should result in exactly one Cell in hashCell");

		// Check a few sample keys (0,0) and (1,0)
		Cell c00 = CellsLoader.hashCell.get("0,0");
		assertNotNull(c00, "Cell at (0,0) should exist");
		assertEquals("id-0", c00.getID());
		assertEquals("aft1", c00.getOwner().getLabel());

		Cell c10 = CellsLoader.hashCell.get("1,0");
		assertNotNull(c10, "Cell at (1,0) should exist");
		assertEquals("id-1", c10.getID());
		assertEquals("aft2", c10.getOwner().getLabel());
	}

	/**
	 * Sanity check for the private buildIndex method: It should produce a correct
	 * index map and return it as an unmodifiable map.
	 */
	@Test
	void buildIndex_returnsUnmodifiableMap() throws Exception {
		// Access the private static method buildIndex via reflection
		Method m = CsvProcessors.class.getDeclaredMethod("buildIndex", String.class);
		m.setAccessible(true);

		@SuppressWarnings("unchecked")
		Map<String, Integer> index = (Map<String, Integer>) m.invoke(null, "X,Y,ID,FR");

		// Verify content
		assertEquals(4, index.size());
		assertEquals(0, index.get("X"));
		assertEquals(1, index.get("Y"));
		assertEquals(2, index.get("ID"));
		assertEquals(3, index.get("FR"));

		// Verify that the map is unmodifiable
		assertThrows(UnsupportedOperationException.class, () -> index.put("NEW_COL", 42));
	}
}
