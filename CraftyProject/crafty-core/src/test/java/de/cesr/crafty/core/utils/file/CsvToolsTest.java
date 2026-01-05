package de.cesr.crafty.core.utils.file;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvToolsTest {

	@TempDir
	Path tempDir;

	@Test
	void csvReaderAndWriterShouldRoundTripTable() throws IOException {
		// Arrange
		String[][] table = { { "ID", "X", "Y" }, { "1", "10", "20" }, { "2", "30", "40" } };
		Path csvPath = tempDir.resolve("test_table.csv");

		// Act: write then read
		CsvTools.writeCSVfile(table, csvPath);
		String[][] readBack = CsvTools.csvReader(csvPath);

		// Assert
		assertNotNull(readBack);
		assertEquals(table.length, readBack.length);
		assertEquals(table[0].length, readBack[0].length);

		for (int i = 0; i < table.length; i++) {
			assertArrayEquals(table[i], readBack[i], "Row " + i + " should match original data");
		}
	}

	// ---------- detectFiles ----------

	@Test
	void detectFilesShouldReturnOnlyFilesInDirectory() throws IOException {
		// Arrange: one subdirectory and two files
		Path subDir = Files.createDirectory(tempDir.resolve("sub"));
		Files.createFile(tempDir.resolve("a.csv"));
		Files.createFile(tempDir.resolve("b.txt"));
		Files.createFile(subDir.resolve("insideSub.txt")); // should not be listed

		// Act
		List<java.io.File> detected = CsvTools.detectFiles(tempDir);

		// Assert
		List<String> names = new ArrayList<>();
		detected.forEach(f -> names.add(f.getName()));

		assertEquals(2, detected.size(), "Should see only the two files in the top folder");
		assertTrue(names.contains("a.csv"));
		assertTrue(names.contains("b.txt"));
		assertFalse(names.contains("insideSub.txt"));
	}

	@Test
	void detectFilesShouldThrowForNonDirectory() {
		Path file = tempDir.resolve("not_a_dir.txt");
		try {
			Files.createFile(file);
		} catch (IOException e) {
			fail("Failed to create temporary file");
		}

		assertThrows(IllegalArgumentException.class, () -> CsvTools.detectFiles(file),
				"Non-directory input should throw IllegalArgumentException");
	}

    // ---------- readCsvFile ----------

    @Test
    void readCsvFileShouldReturnListOfRows() throws IOException {
        Path csvPath = tempDir.resolve("simple.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(csvPath)) {
            writer.write("A,B\n");
            writer.write("1,2\n");
            writer.write("3,4\n");
        }

        List<List<String>> rows = CsvTools.readCsvFile(csvPath);
        

        assertEquals(3, rows.size());
        assertEquals(Arrays.asList("A", "B"), rows.get(0));
        assertEquals(Arrays.asList("1", "2"), rows.get(1));
        assertEquals(Arrays.asList("3", "4"), rows.get(2));
    }

    // ---------- writeCSVfile(Map<String, ArrayList<Double>>, Path) ----------

    @Test
    void writeCsvFileFromMapShouldWriteColumnsPerHeader() throws IOException {
        // Use LinkedHashMap to preserve insertion order of headers
        Map<String, ArrayList<Double>> data = new LinkedHashMap<>();

        data.put("A", new ArrayList<>(Arrays.asList(1.0, 2.0)));
        data.put("B", new ArrayList<>(Arrays.asList(10.0, 20.0, 30.0)));

        Path csvPath = tempDir.resolve("map_output.csv");

        // Act
        CsvTools.writeCSVfile(data, csvPath);

        // Assert
        List<String> lines = Files.readAllLines(csvPath);
        assertEquals(1 + 3, lines.size(), "Header + maxRows lines expected");

        assertEquals("A,B", lines.get(0));          // header
        assertEquals("1.0,10.0", lines.get(1));     // row 0
        assertEquals("2.0,20.0", lines.get(2));     // row 1
        assertEquals(",30.0", lines.get(3));        // row 2: A empty, B has value
    }


//	 @Test
//	 void exportToCSVShouldCreateCsvWithExpectedHeaderAndLines() {
//	 // TODO: initialise a tiny CellsLoader.hashCell with a few Cell instances,
//	 // ensure ServiceSet.getServicesList() returns a known list,
//	 // call CsvTools.exportToCSV(...) with a temp path,
//	 // then assert on header and a few lines.
//	 }

}
