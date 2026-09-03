package de.cesr.crafty.core.cli;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import de.cesr.crafty.core.utils.non_java_code_controller.RScriptRunnerConfig;

import org.yaml.snakeyaml.LoaderOptions;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Loads the CRAFTY configuration from a YAML file and exposes it as a global
 * {@link Config}.
 *
 * The loader first tries to read a user-provided YAML path (typically set by
 * the CLI flag: --config-file "C:\\path\\to\\config.yaml"). If the path is
 * missing or the file does not exist, it falls back to a bundled classpath
 * resource at "/config.yaml".
 *
 * If the file cannot be found, is empty, or cannot be parsed, the loader
 * returns a default {@code new Config()} to keep the application runnable.
 *
 * Typical usage: set {@link #configPath} and call {@link #init()} once at
 * startup. {@link #init()} loads the config and then calls
 * {@link Config#inialize()} to perform any post-load initialization of
 * derived/default values.
 *
 * Note: this class uses SnakeYAML with a
 * {@link org.yaml.snakeyaml.constructor.Constructor} bound to {@link Config} to
 * map YAML keys to Java fields.
 */
/*
 * @author Mohamed Byari
 *
 */
public class ConfigLoader {
	private static final Map<String, String> LEGACY_KEYS = Map.ofEntries(
			Map.entry("start_End_Year", "simulation_year_range"),
			Map.entry("metaData_directory", "metadata_directory"),
			Map.entry("BASELINE_path", "baseline_path"),
			Map.entry("CAPITALS_directory", "capitals_directory"),
			Map.entry("gisPath", "gis_path"),
			Map.entry("GIS_data_path", "gis_path"),
			Map.entry("landControle_directories", "land_control_directories"),
			Map.entry("SHOCKS_Maps_directory", "shock_maps_directory"),
			Map.entry("aft_production_directory", "aft_production_parameters_directory"),
			Map.entry("service_utility_weight_path", "service_utility_weights_path"),
			Map.entry("waitingFlag_directories_path", "waiting_flags_path"),
			Map.entry("AFT_capital_adjustments", "aft_capital_adjustments_directory"),
			Map.entry("regionalization", "regionalisation"),
			Map.entry("MostCompetitorAFTProbability", "most_competitive_aft_probability"),
			Map.entry("use_neighbor_priority", "use_neighbour_priority"),
			Map.entry("neighbor_priority_probability", "neighbour_priority_probability"),
			Map.entry("neighbor_radius", "neighbour_radius"),
			Map.entry("participating_cells_percentage", "participating_cell_fraction"),
			Map.entry("land_abandonment_percentage", "land_abandonment_fraction"),
			Map.entry("takeOverUnmanageCells_percentage", "unmanaged_cell_takeover_fraction"),
			Map.entry("Output_path", "output_path"),
			Map.entry("generate_charts_plots_PNG", "generate_chart_plots_png"),
			Map.entry("generate_map_PAs_forced", "generate_forced_mask_outputs"),
			Map.entry("export_LOGGER", "export_logger"),
			Map.entry("LOGGER_info", "logger_info"),
			Map.entry("LOGGER_warn", "logger_warn"),
			Map.entry("LOGGER_trace", "logger_trace"),
			Map.entry("printRegionalModelRunnerMeasures", "print_regional_model_runner_measures"),
			Map.entry("printAbstractModelRunnerMeasures", "print_abstract_model_runner_measures"),
			Map.entry("chartSynchronisation", "chart_synchronisation"),
			Map.entry("chartSynchronisationGap", "chart_synchronisation_gap"),
			Map.entry("chart_synchronization", "chart_synchronisation"),
			Map.entry("chart_synchronization_gap", "chart_synchronisation_gap"),
			Map.entry("mapSynchronisation", "map_synchronisation"),
			Map.entry("mapSynchronisationGap", "map_synchronisation_gap"),
			Map.entry("map_synchronization", "map_synchronisation"),
			Map.entry("map_synchronization_gap", "map_synchronisation_gap"),
			Map.entry("LLM_model_name", "llm_model_name"),
			Map.entry("LLM_API_KEY", "llm_api_key"),
			Map.entry("LLM_provider", "llm_provider"),
			Map.entry("COUPLED_WITH_PLUM", "coupled_with_plum"),
			Map.entry("plumCalibPath", "plum_calibration_path"),
			Map.entry("plumOutPutPath", "plum_output_path"),
			Map.entry("services_commodities_map", "service_commodity_mapping_path"),
			Map.entry("Behevoir_Cells_directory", "cell_behaviour_parameters_directory"),
			Map.entry("behavior_cells_directory", "cell_behaviour_parameters_directory"),
			Map.entry("behaviour_cells_directory", "cell_behaviour_parameters_directory"),
			Map.entry("cell_behavior_parameters_directory", "cell_behaviour_parameters_directory"),
			Map.entry("aft_behevoir_directory", "aft_behaviour_parameters_directory"),
			Map.entry("aft_behavior_directory", "aft_behaviour_parameters_directory"),
			Map.entry("aft_behaviour_directory", "aft_behaviour_parameters_directory"),
			Map.entry("aft_behavior_parameters_directory", "aft_behaviour_parameters_directory"),
			Map.entry("categories_givingInDistribution", "category_give_in_distributions_directory"),
			Map.entry("categories_giving_in_distribution", "category_give_in_distributions_directory"),
			Map.entry("category_give_in_distribution_directory", "category_give_in_distributions_directory"),
			Map.entry("use_AFTs_categories_GiveIn", "use_category_based_give_in"),
			Map.entry("use_aft_categories_give_in", "use_category_based_give_in"),
			Map.entry("use_cell_behavior_model", "use_cell_behaviour_model"),
			Map.entry("steepness_logistic_eq", "cell_behaviour_logistic_steepness"),
			Map.entry("cell_behavior_logistic_steepness", "cell_behaviour_logistic_steepness"),
			Map.entry("use_price_explicit_givingUp", "use_price_explicit_giving_up"),
			Map.entry("use_twinned_AFTs", "use_twinned_afts"));

	private static final Set<String> REMOVED_KEYS = Set.of(
			"service_taxes_and_subsidies_path",
			"services_taxes_subsidies_path",
			"land_taxes_and_subsidies_path",
			"land_taxes_subsidies_path",
			"consider_taxes_and_subsidies",
			"consider_subsidies_taxes",
			"mutate_on_competition_win",
			"mutation_interval",
			"generate_chart_plots_pdf",
			"generate_charts_plots_PDF",
			"generate_map_plots_tif");

	public static String configPath;
	public static Config config;
	private static final CustomLogger LOGGER = new CustomLogger(ConfigLoader.class);

	public static void init() {
		config = loadConfig();
		validateProductionCostConfig();
		validateGiveUpConfig();
		validateTwinnedAftConfig();
		validatePriceUtilityConfig();
		try {
			loadExternalRScriptRunnerConfig();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static void validateProductionCostConfig() {
		if (config == null) return;
		if (!config.use_production_costs && config.spatial_production_costs) {
			LOGGER.fatal("Cannot use spatial production costs when use_production_costs is false");
		}
	}

	static void validateTwinnedAftConfig() {
		if (config == null) return;
		if (config.use_twinned_cost && !config.use_twinned_afts) {
			LOGGER.fatal("Cannot use twinned cost when use_twinned_afts is false");
		}
		if (config.twinned_competition_rate < 0.0 || config.twinned_competition_rate > 1.0) {
			LOGGER.fatal("twinned_competition_rate must be in [0, 1], got: " + config.twinned_competition_rate);
		}
	}

	static void validatePriceUtilityConfig() {
		if (config == null) return;
		if (config.use_explicit_price_utility && config.use_price_only_utility) {
			LOGGER.fatal("use_explicit_price_utility and use_price_only_utility are mutually exclusive");
		}
	}

	static void validateGiveUpConfig() {
		if (config == null) return;
		if (config.use_price_explicit_giving_up) {
			LOGGER.warn("Using price explicit giving up");
			if (config.use_abandonment_threshold) {
				LOGGER.info("use_price_explicit_giving_up overrides use_abandonment_threshold");
			}
			if (!config.use_explicit_price_utility && !config.use_price_only_utility) {
				LOGGER.warn("use_price_explicit_giving_up is designed for price-based utility modes; "
						+ "current utility mode may produce unexpected results");
			}
		}
	}

	public static boolean isUseTwinnedAFTs() {
		return config != null && config.use_twinned_afts;
	}

	public static boolean isUseTwinnedCost() {
		return config != null && config.use_twinned_cost;
	}

	public static boolean isUseProductionCosts() {
		return config != null && config.use_production_costs;
	}

	public static boolean isSpatialProductionCosts() {
		return config != null && config.spatial_production_costs;
	}

	public static boolean isUsePriceExplicitGivingUp() {
		return config != null && config.use_price_explicit_giving_up;
	}

	private static Config loadConfig() {
		InputStream inputStream = null;
		try {
			if (configPath != null && Files.exists(Paths.get(configPath))) {
				// Load from absolute file path
				inputStream = new FileInputStream(configPath);
			} else {

				// Load from classpath
				configPath = "/config.yaml";
				inputStream = ConfigLoader.class.getResourceAsStream(configPath);
				System.out.println(
						"Config file not found as Arguments \'--config-file \"C:\\path\\to\\config.yaml\"  Crafty will use default config.yam in \'src\\main\\config\'");
			}
			if (inputStream == null) {
				System.out.println("Config file not found. Using default config values.");
				return new Config(); // Return default config
			}

			LoaderOptions rawOptions = new LoaderOptions();
			Yaml rawYaml = new Yaml(new SafeConstructor(rawOptions));
			Object rawConfig = rawYaml.load(inputStream);
			if (rawConfig == null) {
				System.out.println("Config file is empty or invalid. Using default config values.");
				return new Config();
			}
			if (!(rawConfig instanceof Map<?, ?> rawMap)) {
				throw new IllegalArgumentException("Configuration root must be a YAML mapping.");
			}

			Map<Object, Object> normalisedConfig = normaliseLegacyKeys(rawMap);
			bindStaticFieldsAndRejectUnknownKeys(normalisedConfig);
			Constructor constructor = new Constructor(Config.class, new LoaderOptions());
			Yaml typedYaml = new Yaml(constructor);
			Config loadedConfig = typedYaml.load(rawYaml.dump(normalisedConfig));
			System.out.println("loadedConfig: " + loadedConfig);
			if (loadedConfig == null) {
				System.out.println("Config file is empty or invalid. Using default config values.");
				return new Config();
			}
			return loadedConfig;
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (Exception e) {
			// Unreadable file / IO problems keep the old fallback behaviour.
			System.out.println("Failed to load config. Using default config values.");
			e.printStackTrace();
			return new Config();
		} finally {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException e) {
					System.out.println("Failed to close config input stream: " + e.getMessage());
				}
			}
		}
	}

	private static Map<Object, Object> normaliseLegacyKeys(Map<?, ?> rawConfig) {
		Map<Object, Object> normalised = new LinkedHashMap<>();
		rawConfig.forEach(normalised::put);
		normaliseLegacySeedId(normalised);

		for (String removedKey : REMOVED_KEYS) {
			if (normalised.remove(removedKey) != null) {
				System.out.println("[Config] Removed key '" + removedKey
						+ "' ignored; this configuration option is no longer supported.");
			}
		}

		for (Map.Entry<String, String> alias : LEGACY_KEYS.entrySet()) {
			String legacyKey = alias.getKey();
			String canonicalKey = alias.getValue();
			if (!normalised.containsKey(legacyKey)) {
				continue;
			}
			if (normalised.containsKey(canonicalKey)) {
				System.out.println("[Config] Deprecated key '" + legacyKey + "' ignored because canonical key '"
						+ canonicalKey + "' is also present.");
			} else {
				normalised.put(canonicalKey, normalised.get(legacyKey));
				System.out.println("[Config] Deprecated key '" + legacyKey + "'; use '" + canonicalKey + "'.");
			}
			normalised.remove(legacyKey);
		}
		return normalised;
	}

	private static void normaliseLegacySeedId(Map<Object, Object> configValues) {
		if (!configValues.containsKey("seedID")) {
			return;
		}

		Object legacyValue = configValues.remove("seedID");
		String text = legacyValue == null ? "" : legacyValue.toString().trim();
		if (text.equalsIgnoreCase("rank") || text.equalsIgnoreCase("random")) {
			configValues.putIfAbsent("cell_selection", text.toLowerCase());
			System.out.println("[Config] Deprecated key 'seedID'; use 'cell_selection' and 'random_seed'.");
			return;
		}

		try {
			long seed = legacyValue instanceof Number number ? number.longValue() : Long.parseLong(text);
			configValues.putIfAbsent("cell_selection", "random");
			configValues.putIfAbsent("random_seed", seed);
			System.out.println("[Config] Deprecated numeric key 'seedID' migrated to cell_selection=random and random_seed="
					+ seed + ".");
		} catch (NumberFormatException exception) {
			System.out.println("[Config] Deprecated key 'seedID' ignored because value '" + text
					+ "' is neither 'rank', 'random', nor an integer seed.");
		}
	}

	private static void bindStaticFieldsAndRejectUnknownKeys(Map<Object, Object> configValues) {
		Map<String, Field> fieldsByName = new LinkedHashMap<>();
		for (Field field : Config.class.getFields()) {
			fieldsByName.put(field.getName(), field);
		}
		for (Object rawKey : new ArrayList<>(configValues.keySet())) {
			String key = String.valueOf(rawKey);
			Field field = fieldsByName.get(key);
			if (field == null) {
				throw new IllegalArgumentException("Configuration file " + configPath
						+ " contains the unknown key '" + key + "'."
						+ " Check the spelling against the field names in Config.java, then fix or remove it.");
			}
			if (Modifier.isStatic(field.getModifiers())) {
				Object value = configValues.remove(rawKey);
				try {
					field.set(null, value);
				} catch (ReflectiveOperationException | IllegalArgumentException | NullPointerException e) {
					throw new IllegalArgumentException("Configuration key '" + key
							+ "' has an invalid value: " + value, e);
				}
			}
		}
	}

//	for external Yaml
	private static void loadExternalRScriptRunnerConfig() throws IOException {

		if (config == null || config.r_script_runner == null) {
			return;
		}

		String externalPathText = config.r_script_runner.config_path;

		if (externalPathText == null || externalPathText.isBlank()) {
			return;
		}

		externalPathText = resolveBasicConfigTemplateRScript(externalPathText);

		Path externalPath = Path.of(externalPathText);

		if (!externalPath.isAbsolute()) {
			externalPath = Path.of(config.project_path).resolve(externalPath).normalize();
		}

		if (!Files.exists(externalPath)) {
			throw new IOException("R script runner config file does not exist: " + externalPath.toAbsolutePath());
		}

		LoaderOptions loaderOptions = new LoaderOptions();
		Constructor constructor = new Constructor(RScriptRunnerConfig.class, loaderOptions);
		Yaml yaml = new Yaml(constructor);

		RScriptRunnerConfig externalConfig;

		try (InputStream input = Files.newInputStream(externalPath)) {
			externalConfig = yaml.load(input);
		}

		if (externalConfig == null) {
			throw new IOException("R script runner config file is empty: " + externalPath.toAbsolutePath());
		}

		// Keep the path for logging/debugging
		externalConfig.config_path = externalPath.toString();

		config.r_script_runner = externalConfig;

		System.out.println("[Config] Loaded external R script runner config: " + externalPath.toAbsolutePath());
	}

	private static String resolveBasicConfigTemplateRScript(String value) {

		if (value == null) {
			return null;
		}

		return value.replace("{project_path}", safe(config.project_path))
				.replace("{output_folder_name}", safe(config.output_folder_name))
				.replace("{scenario}", safe(config.scenario));
	}

	private static String safe(Object value) {
		return value == null ? "" : String.valueOf(value);
	}
}
