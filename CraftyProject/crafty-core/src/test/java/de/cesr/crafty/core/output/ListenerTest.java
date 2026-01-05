package de.cesr.crafty.core.output;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.utils.file.PathTools;

/**
 * Unit tests for {@link Listener}.
 *
 * Notes: - Uses reflection to interact with ConfigLoader.config so the test
 * does not depend on the concrete config class type. - Uses Mockito static
 * mocking for ProjectLoader and PathTools to avoid touching the real filesystem
 * and project configuration.
 */
class ListenerTest {

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


	private Object getConfigField(String fieldName) {
		Object cfg = ensureConfigNotNull();
		try {
			Field f = cfg.getClass().getField(fieldName);
			f.setAccessible(true);
			return f.get(cfg);
		} catch (Exception e) {
			throw new RuntimeException("Failed to get config field '" + fieldName + "'", e);
		}
	}

	@Test
	void outputfolderPath_UsesDefaultNameWhenOutputNameIsDefaultAndOutputPathIsNull() {
		ensureConfigNotNull();

		try (MockedStatic<ProjectLoader> projectLoaderMock = mockStatic(ProjectLoader.class);
				MockedStatic<PathTools> pathToolsMock = mockStatic(PathTools.class)) {

			// Arrange
			Path projectPath = Paths.get(tempDir.toString());
			String scenario = "MyScenario";
			
            projectLoaderMock.when(ProjectLoader::getProjectPath).thenReturn(projectPath);
			projectLoaderMock.when(ProjectLoader::getScenario).thenReturn(scenario);

			// PathTools.makeDirectory just echos back the path argument
			pathToolsMock.when(() -> PathTools.makeDirectory(anyString()))
					.thenAnswer(invocation -> invocation.getArgument(0));

			// Act
			Listener.outputfolderPath(null, "Default");

			// Assert
			String outputFolderName = (String) getConfigField("output_folder_name");

			// Expected prefix: <tempDir>/output/<scenario>/Default_Run_Output_
			String expectedPrefix = projectPath + File.separator + "output" + File.separator + scenario + File.separator
					+ "Default_Run_Output_";

			assertTrue(outputFolderName.startsWith(expectedPrefix), () -> "output_folder_name should start with '"
					+ expectedPrefix + "', but was '" + outputFolderName + "'");
		}
	}

    @Test
    void outputfolderPath_UsesProvidedNameWhenNotDefaultAndOutputPathNotNull() {
        ensureConfigNotNull();

        try (MockedStatic<ProjectLoader> projectLoaderMock = mockStatic(ProjectLoader.class);
             MockedStatic<PathTools> pathToolsMock = mockStatic(PathTools.class)) {

            // Arrange
        	Path basePath = Paths.get(tempDir.toString());
            String scenario = "MyScenario";
            String runName = "MyRun";

            projectLoaderMock.when(ProjectLoader::getProjectPath).thenReturn(basePath);
            projectLoaderMock.when(ProjectLoader::getScenario).thenReturn(scenario);

            // PathTools.makeDirectory just returns its argument
            pathToolsMock.when(() -> PathTools.makeDirectory(anyString()))
                         .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Listener.outputfolderPath(basePath.toString(), runName);

            // Assert
            String outputFolderName = (String) getConfigField("output_folder_name");
            String expected = basePath
                    + File.separator + scenario
                    + File.separator + runName;

            assertEquals(expected, outputFolderName);
        }
    }

	@Test
	void exportConfigurationFile_DelegatesToConfigToString() {
		Object cfg = ensureConfigNotNull();

		String expected = cfg.toString();
		String actual = Listener.exportConfigurationFile();

		assertEquals(expected, actual, "exportConfigurationFile() should simply return config.toString()");
	}
}
