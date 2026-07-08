package de.cesr.crafty.core.updaters;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cesr.crafty.core.ToyData;
import de.cesr.crafty.core.cli.Config;
import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.utils.file.CsvTools;

/**
 * Unit tests for {@link CapitalUpdater}.
 *
 * Focus: - capitals list initialisation from a small "capitals metadata" CSV -
 * CAPITALS_directory map wiring when a CAPTIALS_directory folder is provided
 *
 * NOTE: You may need to adapt the way the metadata path is set on ProjectLoader
 * depending on your actual API (e.g. setCapitalsMetadata(Path) or similar).
 */
class CapitalUpdaterTest {

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() throws IOException {

		ToyData toy = new ToyData();
		toy.resetStaticState(tempDir);
		if (ConfigLoader.config == null) {
			ConfigLoader.config = new Config();
		}
		// Small time horizon for the tests
		Timestep.setStartYear(2000);
		Timestep.setEndtYear(2010);

		Path worlds = tempDir.resolve("worlds");
		Path capitals = worlds.resolve("capitals");
		Path scenario = capitals.resolve("TestScenario");
		Files.createDirectories(scenario);
		for (int i = Timestep.getStartYear(); i < Timestep.getEndtYear(); i++) {
			// generate a capital file
			Path p = Paths.get(scenario + File.separator + "capitals_" + i + ".csv");
			CsvTools.writeCSVfile(new String[0][0], p);
		}
//		System.out.println(capitals);
//		System.out.println(scenario);
//		System.out.println("@@@    ");
//		PathTools.findAllFilePaths(scenario);
	}
	
    @AfterEach
    void tearDown() {
    	CustomLogger.shutdownRunFileLoggers();
    }

	@Test
	void constructor_readsCapitalsListFromMetadata() throws Exception {
		List<String> capitals = CapitalUpdater.getCapitalsList();

		assertEquals(3, capitals.size(), "Capitals list should contain 3 entries from metadata");
		assertTrue(capitals.contains("capi1"));
		assertTrue(capitals.contains("capi3"));
	}

	@Test
	void isSuitability_returnsTrueForSuitabilityType() {
		CapitalUpdater.getCapitalTypes().clear();
		CapitalUpdater.getCapitalTypes().put("ExtC3C_suit", "Suitability");
		CapitalUpdater.getCapitalTypes().put("soil_quality", "Capital");

		assertTrue(CapitalUpdater.isSuitability("ExtC3C_suit"));
		assertFalse(CapitalUpdater.isSuitability("soil_quality"));
	}

	@Test
	void isSuitability_returnsTrueForUnknownCapital() {
		CapitalUpdater.getCapitalTypes().clear();

		assertTrue(CapitalUpdater.isSuitability("never_seen_before"));
	}

	@Test
	void capitalTypes_storageWorksCorrectly() {
		CapitalUpdater.getCapitalTypes().clear();
		CapitalUpdater.getCapitalTypes().put("capi1", "Capital");
		CapitalUpdater.getCapitalTypes().put("capi2", "Suitability");
		CapitalUpdater.getCapitalTypes().put("capi3", "Suitability");

		assertEquals("Capital", CapitalUpdater.getCapitalTypes().get("capi1"));
		assertEquals("Suitability", CapitalUpdater.getCapitalTypes().get("capi2"));
		assertFalse(CapitalUpdater.isSuitability("capi1"));
		assertTrue(CapitalUpdater.isSuitability("capi2"));
		assertTrue(CapitalUpdater.isSuitability("capi3"));
	}
}
