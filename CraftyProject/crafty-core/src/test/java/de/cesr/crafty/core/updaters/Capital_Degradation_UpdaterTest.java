package de.cesr.crafty.core.updaters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cesr.crafty.core.cli.Config;
import de.cesr.crafty.core.cli.ConfigLoader;

public class Capital_Degradation_UpdaterTest {

	 @TempDir
	    Path tempDir;

	    /**
	     * Test subclass that neutralises the step() side-effect.
	     * The base constructor still runs listPaths(), but its call to step()
	     * is dynamically dispatched to this no-op override.
	     */
	    static class TestableCapitalDegradationUpdater extends Capital_Degradation_Updater {
	        @Override
	        public void step() {
	            // no-op in tests: avoids calling CsvProcessors.processCSV(...)
	        }
	    }

	    @BeforeEach
	    void setUp() {
	        if (ConfigLoader.config == null) {
	            ConfigLoader.config = new Config();
	        }

	        // Make sure we have a reasonable time horizon
	        Timestep.setStartYear(2000);
	        Timestep.setEndtYear(2020);
	    }

	    @SuppressWarnings("unchecked")
	    private TreeMap<Integer, Path> getDegradationPaths(Capital_Degradation_Updater updater) throws Exception {
	        Field f = Capital_Degradation_Updater.class.getDeclaredField("degradation_paths");
	        f.setAccessible(true);
	        return (TreeMap<Integer, Path>) f.get(updater);
	    }

    @Test
    void listPaths_populatesDegradationPathsWhenDirectoryHasYearCsvFiles() throws Exception {
        // Create year-based CSV files in the temp directory:
        //  2000.csv and 2002.csv (no file for 2001)
        Path csv2000 = tempDir.resolve("2000.csv");
        Path csv2002 = tempDir.resolve("2002.csv");
        Files.writeString(csv2000, "dummy");
        Files.writeString(csv2002, "dummy");

        // Point config.capital_degradation_directory to this directory
        ConfigLoader.config.capital_degradation_directory = tempDir.toString();

        // Construct the testable updater (listPaths() will run in super ctor)
        TestableCapitalDegradationUpdater updater = new TestableCapitalDegradationUpdater();

        TreeMap<Integer, Path> degradationPaths = getDegradationPaths(updater);

        // We expect entries only for years that actually have a CSV file
        assertEquals(2, degradationPaths.size(), "Should have entries only for years with matching CSV files");
        assertTrue(degradationPaths.containsKey(2000), "Year 2000 should have a degradation path");
        assertTrue(degradationPaths.containsKey(2002), "Year 2002 should have a degradation path");
        assertEquals(csv2000.toAbsolutePath().normalize(),
                degradationPaths.get(2000).toAbsolutePath().normalize());
        assertEquals(csv2002.toAbsolutePath().normalize(),
                degradationPaths.get(2002).toAbsolutePath().normalize());

        // Year 2001 should have no entry
        assertFalse(degradationPaths.containsKey(2001),
                "Year 2001 should not have an entry when no CSV file exists for that year");
    }
    
    @Test
    void listPaths_leavesDegradationPathsEmptyWhenNoCsvFilesExist() throws Exception {
        // Create an empty directory (no .csv files matching any year)
        Path emptyDir = tempDir.resolve("empty");
        Files.createDirectories(emptyDir);

        ConfigLoader.config.capital_degradation_directory = emptyDir.toString();

        TestableCapitalDegradationUpdater updater = new TestableCapitalDegradationUpdater();

        TreeMap<Integer, Path> degradationPaths = getDegradationPaths(updater);

        assertTrue(degradationPaths.isEmpty(),
                "With no matching CSV files, degradation_paths should remain empty");
    }
}
