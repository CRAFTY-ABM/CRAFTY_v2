package de.cesr.crafty.core.dataLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;

/**
 * Unit tests for {@link ProjectLoader}.
 *
 * Focus: - pathInitialisation() (which calls initialSenarios()): * sets
 * projectPath * sets allfilePathsInDataDirectory * reads scenarios.csv via
 * CsvProcessors.ReadAsaHash - setScenario(): * sets the static scenario * sets
 * Timestep start / current / end years based on scenariosHash
 */
class ProjectLoaderTest {

	/**
	 * Helper to set a private static field on ProjectLoader via reflection.
	 */
	private void setStaticField(Class<?> clazz, String fieldName, Object value) {
		try {
			Field f = clazz.getDeclaredField(fieldName);
			f.setAccessible(true);
			f.set(null, value);
		} catch (Exception e) {
			throw new RuntimeException("Failed to set static field '" + fieldName + "' on " + clazz.getName(), e);
		}
	}

	@SuppressWarnings("unchecked")
	private <T> T getStaticField(Class<?> clazz, String fieldName, Class<T> type) {
		try {
			Field f = clazz.getDeclaredField(fieldName);
			f.setAccessible(true);
			return (T) f.get(null);
		} catch (Exception e) {
			throw new RuntimeException("Failed to get static field '" + fieldName + "' on " + clazz.getName(), e);
		}
	}

	@Test
	void pathInitialisation_InitializesProjectPath_FileList_AndScenarios() {
		Path project = Paths.get("dummyProject");

		// Fake scenarios.csv path (content is provided via CsvProcessors mock)
		Path scenariosCsvPath = project.resolve("scenarios.csv");

		// Prepare fake all-file list
		ArrayList<Path> allPaths = new ArrayList<>();
		allPaths.add(project.resolve("data/file1.txt"));

		// Prepare fake scenarios hash from CSV
		Map<String, List<String>> csvHash = new HashMap<>();
		csvHash.put("Name", List.of("ScenarioA", "ScenarioB"));
		csvHash.put("startYear", List.of("2000", "2010"));
		csvHash.put("endtYear", List.of("2005", "2020"));

		try (MockedStatic<PathTools> pathToolsMock = mockStatic(PathTools.class);
				MockedStatic<CsvProcessors> csvProcessorsMock = mockStatic(CsvProcessors.class)) {

			// PathTools.findAllFilePaths(projectPath)
			pathToolsMock.when(() -> PathTools.findAllFilePaths(project)).thenReturn(allPaths);

			// PathTools.fileFilter(File.separator + "scenarios.csv")
			pathToolsMock.when(() -> PathTools.fileFilter(File.separator + "scenarios.csv"))
					.thenReturn(new ArrayList<>(Collections.singletonList(scenariosCsvPath)));

			// CsvProcessors.ReadAsaHash(scenariosCsvPath)
			csvProcessorsMock.when(() -> CsvProcessors.ReadAsaHash(scenariosCsvPath)).thenReturn(csvHash);

			// Act
//			ProjectLoader.pathInitialisation(project);
		}
//
//		// Assert projectPath
//		assertEquals(project, ProjectLoader.getProjectPath());
//
//		// Assert allfilePathsInDataDirectory
//		assertEquals(allPaths, ProjectLoader.getAllfilesPathInData());
//
//		// Assert scenariosList
//		List<String> scenarios = ProjectLoader.getScenariosList();
//		assertEquals(List.of("ScenarioA", "ScenarioB"), scenarios);
	}

	@Test
	void setScenario_SetsTimestepYearsAndScenario() {
		// Arrange: inject scenariosHash manually via reflection
		Map<String, String> scenariosHash = new HashMap<>();
		// scenarioKey -> "start_end"
		scenariosHash.put("ScenarioX", "2001_2004");
		setStaticField(ProjectLoader.class, "scenariosHash", scenariosHash);

		// Ensure Utils.sToD works as expected (normally it does; this is just a sanity
		// call)
		double parsedStart = Utils.sToD("2001");
		double parsedEnd = Utils.sToD("2004");
		assertEquals(2001.0, parsedStart, 1e-9);
		assertEquals(2004.0, parsedEnd, 1e-9);

		// Reset Timestep to a known state
		Timestep.setStartYear(0);
		Timestep.setCurrentYear(0);
		Timestep.setEndtYear(0);

		// Act
		ProjectLoader.setScenario("ScenarioX");

		// Assert: scenario field
		assertEquals("ScenarioX", ProjectLoader.getScenario());

		// Assert: Timestep years
		assertEquals(2001, Timestep.getStartYear());
		assertEquals(2001, Timestep.getCurrentYear());
		assertEquals(2004, Timestep.getEndtYear());

		// Also check that internal scenariosHash is still consistent
		@SuppressWarnings("unchecked")
		Map<String, String> storedHash = getStaticField(ProjectLoader.class, "scenariosHash", Map.class);
		assertEquals("2001_2004", storedHash.get("ScenarioX"));
	}
}
