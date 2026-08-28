package de.cesr.crafty.core.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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
    void loadConfigShouldReadSelectionModeAndRandomSeed() throws Exception {
        originalConfigPath = ConfigLoader.configPath;
        originalConfig = ConfigLoader.config;

        Path configFile = tempDir.resolve("seed-config.yaml");
        Files.writeString(configFile, "cell_selection: random\nrandom_seed: 987654321\n");
        ConfigLoader.configPath = configFile.toString();

        Config cfg = invokeLoadConfig();

        assertEquals("random", cfg.cell_selection);
        assertEquals(987654321L, cfg.random_seed);
    }

    @Test
    void legacyRankSeedIdShouldMapToCellSelection() throws Exception {
        originalConfigPath = ConfigLoader.configPath;
        originalConfig = ConfigLoader.config;

        Path configFile = tempDir.resolve("legacy-rank-seed.yaml");
        Files.writeString(configFile, "seedID: rank\n");
        ConfigLoader.configPath = configFile.toString();

        Config cfg = invokeLoadConfig();

        assertEquals("rank", cfg.cell_selection);
        assertEquals(1L, cfg.random_seed);
    }

    @Test
    void legacyNumericSeedIdShouldMapToRandomSelectionAndSeed() throws Exception {
        originalConfigPath = ConfigLoader.configPath;
        originalConfig = ConfigLoader.config;

        Path configFile = tempDir.resolve("legacy-numeric-seed.yaml");
        Files.writeString(configFile, "seedID: 1234\n");
        ConfigLoader.configPath = configFile.toString();

        Config cfg = invokeLoadConfig();

        assertEquals("random", cfg.cell_selection);
        assertEquals(1234L, cfg.random_seed);
    }

    @Test
    void loadConfigShouldReadCanonicalBritishBehaviourKeys() throws Exception {
        originalConfigPath = ConfigLoader.configPath;
        originalConfig = ConfigLoader.config;

        Path configFile = tempDir.resolve("behaviour-config.yaml");
        Files.writeString(configFile, """
                aft_behaviour_parameters_directory: /aft
                category_give_in_distributions_directory: /categories
                cell_behaviour_parameters_directory: /cells
                use_category_based_give_in: false
                use_cell_behaviour_model: false
                cell_behaviour_logistic_steepness: 4.5
                """);
        ConfigLoader.configPath = configFile.toString();

        Config cfg = invokeLoadConfig();

        assertEquals("/aft", cfg.aft_behaviour_parameters_directory);
        assertEquals("/categories", cfg.category_give_in_distributions_directory);
        assertEquals("/cells", cfg.cell_behaviour_parameters_directory);
        assertFalse(cfg.use_category_based_give_in);
        assertFalse(cfg.use_cell_behaviour_model);
        assertEquals(4.5, cfg.cell_behaviour_logistic_steepness);
    }

    @Test
    void loadConfigShouldMapLegacyBehaviourKeysToCanonicalFields() throws Exception {
        originalConfigPath = ConfigLoader.configPath;
        originalConfig = ConfigLoader.config;

        Path configFile = tempDir.resolve("legacy-behaviour-config.yaml");
        Files.writeString(configFile, """
                aft_behevoir_directory: /legacy-aft
                categories_givingInDistribution: /legacy-categories
                Behevoir_Cells_directory: /legacy-cells
                use_AFTs_categories_GiveIn: false
                steepness_logistic_eq: 3.0
                """);
        ConfigLoader.configPath = configFile.toString();

        Config cfg = invokeLoadConfig();

        assertEquals("/legacy-aft", cfg.aft_behaviour_parameters_directory);
        assertEquals("/legacy-categories", cfg.category_give_in_distributions_directory);
        assertEquals("/legacy-cells", cfg.cell_behaviour_parameters_directory);
        assertFalse(cfg.use_category_based_give_in);
        assertEquals(3.0, cfg.cell_behaviour_logistic_steepness);
    }

    @Test
    void canonicalBehaviourKeyShouldWinOverLegacyAlias() throws Exception {
        originalConfigPath = ConfigLoader.configPath;
        originalConfig = ConfigLoader.config;

        Path configFile = tempDir.resolve("mixed-behaviour-config.yaml");
        Files.writeString(configFile, """
                aft_behaviour_parameters_directory: /canonical
                aft_behevoir_directory: /legacy
                """);
        ConfigLoader.configPath = configFile.toString();

        Config cfg = invokeLoadConfig();

        assertEquals("/canonical", cfg.aft_behaviour_parameters_directory);
    }

    @Test
    void removedKeysShouldBeIgnoredWithoutInvalidatingConfig() throws Exception {
        originalConfigPath = ConfigLoader.configPath;
        originalConfig = ConfigLoader.config;

        Path configFile = tempDir.resolve("removed-tax-config.yaml");
        Files.writeString(configFile, """
                service_taxes_and_subsidies_path: /services
                land_taxes_subsidies_path: /land
                consider_taxes_and_subsidies: true
                mutate_on_competition_win: true
                mutation_interval: 5
                generate_chart_plots_pdf: true
                generate_map_plots_tif: true
                random_seed: 42
                """);
        ConfigLoader.configPath = configFile.toString();

        Config cfg = invokeLoadConfig();

        assertEquals(42L, cfg.random_seed);
        assertThrows(NoSuchFieldException.class,
                () -> Config.class.getField("service_taxes_and_subsidies_path"));
        assertThrows(NoSuchFieldException.class,
                () -> Config.class.getField("land_taxes_and_subsidies_path"));
        assertThrows(NoSuchFieldException.class,
                () -> Config.class.getField("consider_taxes_and_subsidies"));
        assertThrows(NoSuchFieldException.class,
                () -> Config.class.getField("mutate_on_competition_win"));
        assertThrows(NoSuchFieldException.class,
                () -> Config.class.getField("mutation_interval"));
        assertThrows(NoSuchFieldException.class,
                () -> Config.class.getField("generate_chart_plots_pdf"));
        assertThrows(NoSuchFieldException.class,
                () -> Config.class.getField("generate_map_plots_tif"));
    }

    @Test
    void allPublicConfigFieldsShouldUseCanonicalSnakeCase() {
        for (Field field : Config.class.getFields()) {
            assertTrue(field.getName().matches("[a-z][a-z0-9_]*"),
                    () -> "Non-canonical Config field: " + field.getName());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyLegacyAliasShouldTargetAnExistingCanonicalField() throws Exception {
        Field aliasesField = ConfigLoader.class.getDeclaredField("LEGACY_KEYS");
        aliasesField.setAccessible(true);
        Map<String, String> aliases = (Map<String, String>) aliasesField.get(null);

        assertFalse(aliases.isEmpty());
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            Field canonicalField = Config.class.getField(alias.getValue());
            assertNotNull(canonicalField,
                    () -> "Alias target does not exist: " + alias.getKey() + " -> " + alias.getValue());
        }
    }

    @Test
    void loadConfigShouldMapRepresentativeLegacyKeysFromEverySection() throws Exception {
        originalConfigPath = ConfigLoader.configPath;
        originalConfig = ConfigLoader.config;

        Path configFile = tempDir.resolve("legacy-full-config.yaml");
        Files.writeString(configFile, """
                metaData_directory: /metadata
                BASELINE_path: /baseline.csv
                regionalization: true
                MostCompetitorAFTProbability: 0.75
                neighbor_radius: 4
                participating_cells_percentage: 0.12
                land_abandonment_percentage: 0.08
                takeOverUnmanageCells_percentage: 0.6
                Output_path: /output
                generate_charts_plots_PNG: true
                LOGGER_trace: true
                LLM_model_name: legacy-model
                COUPLED_WITH_PLUM: true
                plumOutPutPath: /plum
                """);
        ConfigLoader.configPath = configFile.toString();

        Config cfg = invokeLoadConfig();

        assertEquals("/metadata", cfg.metadata_directory);
        assertEquals("/baseline.csv", cfg.baseline_path);
        assertTrue(cfg.regionalisation);
        assertEquals(0.75, cfg.most_competitive_aft_probability);
        assertEquals(4, cfg.neighbour_radius);
        assertEquals(0.12, cfg.participating_cell_fraction);
        assertEquals(0.08, cfg.land_abandonment_fraction);
        assertEquals(0.6, cfg.unmanaged_cell_takeover_fraction);
        assertEquals("/output", cfg.output_path);
        assertTrue(cfg.generate_chart_plots_png);
        assertTrue(cfg.logger_trace);
        assertEquals("legacy-model", cfg.llm_model_name);
        assertTrue(cfg.coupled_with_plum);
        assertEquals("/plum", cfg.plum_output_path);
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
