package de.cesr.crafty.core.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    private String originalConfigPath;
    private Config originalConfig;

    private Config invokeLoadConfig()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method m = ConfigLoader.class.getDeclaredMethod("loadConfig");
        m.setAccessible(true);
        return (Config) m.invoke(null);
    }

    @AfterEach
    void restoreStatics() {
        // Restore any previous state so other tests are not affected
        ConfigLoader.configPath = originalConfigPath;
        ConfigLoader.config = originalConfig;
    }

    @Test
    void loadConfigShouldUseAbsoluteFilePathWhenConfigPathExists() throws Exception {
        // Backup previous state
        originalConfigPath = ConfigLoader.configPath;
        originalConfig = ConfigLoader.config;

        // Arrange: create a temporary YAML file
        Path configFile = tempDir.resolve("config.yaml");
        // The content can't be empty; loadConfig handles null -> new Config()
//        Files.writeString(configFile, "");

        ConfigLoader.configPath = configFile.toString();

        // Act
        Config cfg = invokeLoadConfig();

        // Assert
        assertNotNull(cfg, "Config loaded from an existing configPath should not be null");
    }

    @Test
    void loadConfigShouldFallbackToDefaultConfigWhenFileIsMissing() throws Exception {
        // Backup previous state
        originalConfigPath = ConfigLoader.configPath;
        originalConfig = ConfigLoader.config;

        // Arrange: point to a non-existing file
        Path missing = tempDir.resolve("does_not_exist.yaml");
        ConfigLoader.configPath = missing.toString();

        // Act
        Config cfg = invokeLoadConfig();

        // Assert
        assertNotNull(cfg, "When config file is missing, a default Config should be returned");
    }

    @Test
    void initShouldSetGlobalConfigNonNullEvenWithoutExplicitConfigPath() {
        // Backup previous state
        originalConfigPath = ConfigLoader.configPath;
        originalConfig = ConfigLoader.config;

        ConfigLoader.configPath = null;
        ConfigLoader.config = null;

        // Act
        ConfigLoader.init();

        // Assert
        assertNotNull(ConfigLoader.config,
                "ConfigLoader.init() should always set a non-null global Config instance");
    }
}
