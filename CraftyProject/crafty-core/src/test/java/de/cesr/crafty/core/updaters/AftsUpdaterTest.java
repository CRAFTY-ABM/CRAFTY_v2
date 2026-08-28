package de.cesr.crafty.core.updaters;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cesr.crafty.core.ToyData;
import de.cesr.crafty.core.cli.Config;
import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;

/**
 * Unit tests for {@link AftsUpdater}.
 *
 * These tests focus on: - updateAFTProduction(...) + updateSensitivty(...)
 * using a small CSV.
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
	
    @AfterEach
    void tearDown() {
    	CustomLogger.shutdownRunFileLoggers();
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

}
