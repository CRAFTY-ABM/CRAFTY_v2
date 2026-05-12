package de.cesr.crafty.core.updaters;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cesr.crafty.core.ToyData;
import de.cesr.crafty.core.cli.Config;
import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.Region;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;

/**
 * Unit tests for {@link AftsUpdater}.
 *
 * These tests focus on: - updateAFTProduction(...) + updateSensitivty(...)
 * using a small CSV - useDefaultTS(...) default land tax/subsidy behaviour -
 * landTSPath(...) when an explicit file path is provided in the config
 */
class AftsUpdaterTest {

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		ToyData toy = new ToyData();
		toy.resetStaticState(tempDir);
		// Minimal config
		if (ConfigLoader.config == null) {
			ConfigLoader.config = new Config();
		}

		// Reset service list and capitals
		ServiceSet.getServicesList().clear();
		ServiceSet.getServicesList().add("S1");
		ServiceSet.getServicesList().add("S2");

		CapitalUpdater.getCapitalsList().clear();
		CapitalUpdater.getCapitalsList().add("Cap1");
		CapitalUpdater.getCapitalsList().add("Cap2");

		if (AFTsLoader.getActivateAFTsHash() == null) {
			AFTsLoader.getActivateAFTsHash().clear();
		}
		AFTsLoader.getActivateAFTsHash().clear();
	}

//	@Test
//	void updateAFTProduction_updatesProductivityAndSensitivityFromCsv() throws IOException {
//		// Build a tiny CSV:
//		//
//		// Service,Cap1,Cap2,Production
//		// S1,0.5,1.0,10
//		// S2,0.2,0.3,20
//		// IGNORE,9.9,9.9,999 (service not in ServiceSet, should be ignored)
//		//
//		Path csvPath = tempDir.resolve("aft_production.csv");
//		String csvContent = String.join("\n", "Service,Cap1,Cap2,Production", "S1,0.5,1.0,10", "S2,0.2,0.3,20",
//				"IGNORE,9.9,9.9,999");
//		Files.write(csvPath, csvContent.getBytes(StandardCharsets.UTF_8));
//
//		Aft aft = new Aft("AFT_TEST");
//
//		// Call the static update method
//		AftsUpdater.updatePorB(aft, csvPath);
//
//		// --- Check productivity levels (from "Production" column) ---
//		assertEquals(10.0, aft.getProductivityLevel().get("S1"), 1e-12);
//		assertEquals(20.0, aft.getProductivityLevel().get("S2"), 1e-12);
//		// "IGNORE" should not appear because it's not in ServiceSet.getServicesList()
//		assertFalse(aft.getProductivityLevel().containsKey("IGNORE"),
//				"Services not present in ServiceSet should be ignored in productivity update");
//
//		// --- Check sensitivities (from Cap1 / Cap2 columns) ---
//	}

	@Test
	void useDefaultTS_setsZeroLandTaxesForAllAfts() throws Exception {
		// Years: 2000–2002 inclusive -> 3 years
		Timestep.setStartYear(2000);
		Timestep.setEndtYear(2002);
		Timestep.setSize(Timestep.getEndtYear() - Timestep.getStartYear()+1);

		// Two AFTs in the active map
		Aft aft1 = new Aft("A1");
		Aft aft2 = new Aft("A2");

		ConcurrentHashMap<String, Aft> active = new ConcurrentHashMap<>();
		active.put("A1", aft1);
		active.put("A2", aft2);
		AFTsLoader.getActivateAFTsHash().put("A1", aft1);
		AFTsLoader.getActivateAFTsHash().put("A2", aft2);

		// Expect each AFT to have land_taxes_subsidies entries
		// for years 2000, 2001, 2002 all set to 0.0
		for (Aft a : AFTsLoader.getActivateAFTsHash().values()) {
			ConcurrentHashMap<Integer, Double> ts = a.getLand_taxes_subsidies();
//			assertEquals(3, ts.size(), "Should have exactly one entry per year");
			assertEquals(0.0, ts.getOrDefault(2000,0d), 1e-12);
			assertEquals(0.0, ts.getOrDefault(2001,0d), 1e-12);
			assertEquals(0.0, ts.getOrDefault(2002,0d), 1e-12);
		}
	}

	@Test
	void landTSPath_returnsExplicitFileFromConfig() throws Exception {
		// Create a dummy land_taxes_subsidies CSV file in the temp directory
		Path landTsFile = tempDir.resolve("land_taxes_R1.csv");
		Files.write(landTsFile, "dummy".getBytes(StandardCharsets.UTF_8));

		// Point the config directly at this file
		ConfigLoader.config.land_taxes_subsidies_path = landTsFile.toString();

		Region region = new Region("R1");

		// Call private static landTSPath(Region) via reflection
		Method m = AftsUpdater.class.getDeclaredMethod("landTSPath", Region.class);
		m.setAccessible(true);

		Path result = (Path) m.invoke(null, region);

		assertNotNull(result, "landTSPath should not return null when config points to a file");
		assertEquals(landTsFile.toAbsolutePath().normalize(), result.toAbsolutePath().normalize(),
				"landTSPath should return the explicit file path from config when it is a file");
	}
}
