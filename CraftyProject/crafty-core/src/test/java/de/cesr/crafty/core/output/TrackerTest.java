package de.cesr.crafty.core.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cesr.crafty.core.cli.ConfigLoader;

/**
 * Unit tests for {@link Tracker}.
 *
 * Focus: - writeCSV: header creation, numeric values, and filling missing
 * values with "0" - trackSupply: early exit when track_changes is false (no
 * file created)
 */
class TrackerTest {

	@TempDir
	Path tempDir;

	/**
	 * Ensure that ConfigLoader.config is non-null and return it.
	 */
	private Object ensureConfigNotNull() {
		try {
			Field configField = ConfigLoader.class.getField("config");
			Object config = configField.get(null);
			if (config == null) {
				Class<?> cfgType = configField.getType();
				Object instance = cfgType.getDeclaredConstructor().newInstance();
				configField.setAccessible(true);
				configField.set(null, instance);
				config = instance;
			}
			return config;
		} catch (Exception e) {
			throw new RuntimeException("Failed to ensure ConfigLoader.config is initialised", e);
		}
	}

	/**
	 * Set a field on ConfigLoader.config via reflection.
	 */
	private void setConfigField(String fieldName, Object value) {
		Object cfg = ensureConfigNotNull();
		try {
			Field f = cfg.getClass().getField(fieldName);
			f.setAccessible(true);
			f.set(cfg, value);
		} catch (NoSuchFieldException e) {
			throw new RuntimeException("Config class does not have public field '" + fieldName + "'", e);
		} catch (Exception e) {
			throw new RuntimeException("Failed to set config field '" + fieldName + "'", e);
		}
	}

	@Test
	void writeCSV_WritesHeaderAndValuesAndFillsMissingWithZero() throws Exception {
		// Arrange: container with two AFTs and non-identical keys
		ConcurrentHashMap<String, ConcurrentHashMap<String, Double>> container = new ConcurrentHashMap<>();

		ConcurrentHashMap<String, Double> aft1Map = new ConcurrentHashMap<>();
		aft1Map.put("S1", 1.0);
		aft1Map.put("AggregateAFT", 10.0);

		ConcurrentHashMap<String, Double> aft2Map = new ConcurrentHashMap<>();
		aft2Map.put("S1", 2.0); // no AggregateAFT -> should become "0" in CSV

		container.put("AFT1", aft1Map);
		container.put("AFT2", aft2Map);

		Path csvPath = tempDir.resolve("tracker.csv");

		// Act
		Tracker.writeCSV(container, csvPath.toString());

		// Assert: read file & check content
		try (BufferedReader reader = new BufferedReader(new FileReader(csvPath.toFile()))) {
			List<String> lines = reader.lines().collect(Collectors.toList());

			// We expect 3 lines: header + 2 rows
			assertEquals(3, lines.size());

			String header = lines.get(0);
			// Headers are built from TreeSet of keys, so sorted lexicographically:
			// "AggregateAFT", "S1" -> "ID,AggregateAFT,S1"
			assertEquals("ID,AggregateAFT,S1", header);

			// Remaining lines: we don't know row order (ConcurrentHashMap), so parse them
			List<String> dataLines = lines.subList(1, 3);

			// Convert each row to (ID -> rowValuesMap)
			// e.g. "AFT1,10.0,1.0"
			// "AFT2,0,2.0"
			dataLines.forEach(line -> {
				String[] parts = line.split(",");
				String id = parts[0];
				List<String> vals = Arrays.asList(parts).subList(1, parts.length);
				if ("AFT1".equals(id)) {
					// AFT1: AggregateAFT=10.0, S1=1.0
					assertEquals("10.0", vals.get(0));
					assertEquals("1.0", vals.get(1));
				} else if ("AFT2".equals(id)) {
					// AFT2: missing AggregateAFT -> "0", S1=2.0
					assertEquals("0", vals.get(0));
					assertEquals("2.0", vals.get(1));
				}
			});
		}
	}

	@Test
	void writeCSV_EmptyContainer_WritesOnlyHeader() throws Exception {
		ConcurrentHashMap<String, ConcurrentHashMap<String, Double>> container = new ConcurrentHashMap<>();
		Path csvPath = tempDir.resolve("empty.csv");

		Tracker.writeCSV(container, csvPath.toString());

		try (BufferedReader reader = new BufferedReader(new FileReader(csvPath.toFile()))) {
			List<String> lines = reader.lines().collect(Collectors.toList());

			// Only header row should be present
			assertEquals(1, lines.size());
			assertEquals("ID", lines.get(0));
		}
	}

	@Test
	void trackSupply_DoesNothingWhenTrackChangesDisabled() throws Exception {
		// Arrange
		ensureConfigNotNull();
		setConfigField("output_folder_name", tempDir.toString());
		setConfigField("track_changes", false);

		// Act: should return early and not write anything
		Tracker.trackSupply("RegionX");

		// Assert: temp directory should still be empty
		// (no SupplyTracker_*.csv created)
		try (var paths = Files.list(tempDir)) {
			assertFalse(paths.findAny().isPresent(), "No files should be created when track_changes is false");
		}
	}
}
