package de.cesr.crafty.core.dataLoader.land;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MaskLoaderTest {

	@TempDir
	Path tmp;

	@BeforeEach
	void resetMetadata() {
		MaskLoader.mask_metadata = new LinkedHashMap<>();
		MaskLoader.restriction_paths = new LinkedHashMap<>();
		MaskLoader.scenario_restriction_paths = new LinkedHashMap<>();
		MaskLoader.default_restriction_paths = new LinkedHashMap<>();
	}

	@Test
	void restrictionDiscoveryUsesYearThenScenarioThenDefaultAcrossBothLayouts() throws Exception {
		Path maskRoot = Files.createDirectories(tmp.resolve("Urban"));
		Path scenarioDirectory = Files.createDirectories(maskRoot.resolve("Baseline"));
		Path rootYear = Files.writeString(maskRoot.resolve("Urban_Restriction_2030.csv"), "root year");
		Path selectedYear = Files.writeString(scenarioDirectory.resolve("Restriction_2030.csv"), "scenario year");
		Path scenario = Files.writeString(scenarioDirectory.resolve("Urban_Restriction.csv"), "scenario");
		Path defaultFile = Files.writeString(maskRoot.resolve("default_Urban_Restriction.csv"), "default");
		Path sibling = Files.createDirectories(maskRoot.resolve("Other"));
		Files.writeString(sibling.resolve("Restriction_2040.csv"), "must not be discovered");

		MaskLoader.RestrictionFiles files = MaskLoader.discoverRestrictionFiles(maskRoot, scenarioDirectory,
				"Baseline");
		MaskLoader.restriction_paths.put("Urban", files.yearly());
		MaskLoader.scenario_restriction_paths.put("Urban", files.scenario());
		MaskLoader.default_restriction_paths.put("Urban", files.defaultFile());

		assertEquals(selectedYear, MaskLoader.resolveRestrictionPath("Urban", 2030));
		assertEquals(scenario, MaskLoader.resolveRestrictionPath("Urban", 2040));
		assertEquals(defaultFile, files.defaultFile());
		assertFalse(files.yearly().containsValue(rootYear));
		assertFalse(files.yearly().containsKey(2040));
		MaskLoader.scenario_restriction_paths.remove("Urban");
		assertEquals(defaultFile, MaskLoader.resolveRestrictionPath("Urban", 2040));
	}

	@Test
	void rootScenarioNamedFileIsUsedBeforeDefaultWhenScenarioDirectoryIsAbsent() throws Exception {
		Path maskRoot = Files.createDirectories(tmp.resolve("Water"));
		Path scenario = Files.writeString(maskRoot.resolve("Baseline_Water_Restriction.csv"), "scenario");
		Path defaultFile = Files.writeString(maskRoot.resolve("default_Water_Restriction.csv"), "default");

		MaskLoader.RestrictionFiles files = MaskLoader.discoverRestrictionFiles(maskRoot, maskRoot, "Baseline");
		MaskLoader.restriction_paths.put("Water", files.yearly());
		MaskLoader.scenario_restriction_paths.put("Water", files.scenario());
		MaskLoader.default_restriction_paths.put("Water", files.defaultFile());

		assertEquals(scenario, MaskLoader.resolveRestrictionPath("Water", 2030));
		assertEquals(defaultFile, files.defaultFile());
	}

	@Test
	void loadMetadataReadsForcedFlagsAndUsesRowOrderWhenPriorityIsMissing() throws Exception {
		Path metadata = tmp.resolve("LandUseControl-metadata.csv");
		Files.writeString(metadata, "name,isForced,Priority\n" + "Urban,TRUE,1\n" + "Water,1,\n"
				+ "Protected,FALSE,9\n");

		assertTrue(MaskLoader.loadMetadata(metadata));
		assertTrue(MaskLoader.metadata("urban").forced());
		assertTrue(MaskLoader.metadata("Water").forced());
		assertFalse(MaskLoader.metadata("Protected").forced());
		assertEquals(2, MaskLoader.metadata("Water").priority(), "second metadata row supplies fallback priority 2");
		assertEquals("Urban", MaskLoader.orderedMetadata().get(0).name());
		assertEquals("Water", MaskLoader.orderedMetadata().get(1).name());
	}

	@Test
	void loadMetadataRejectsMissingRequiredColumns() throws Exception {
		Path metadata = tmp.resolve("LandUseControl-metadata.csv");
		Files.writeString(metadata, "name,Priority\nUrban,1\n");

		assertFalse(MaskLoader.loadMetadata(metadata));
		assertTrue(MaskLoader.mask_metadata.isEmpty());
	}
}
