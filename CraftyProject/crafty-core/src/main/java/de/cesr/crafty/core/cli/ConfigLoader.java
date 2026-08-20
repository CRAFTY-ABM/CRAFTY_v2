package de.cesr.crafty.core.cli;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import de.cesr.crafty.core.utils.non_java_code_controller.RScriptRunnerConfig;

import org.yaml.snakeyaml.LoaderOptions;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
		if (config.use_twinned_cost && !config.use_twinned_AFTs) {
			LOGGER.fatal("Cannot use twinned cost when use_twinned_AFTs is false");
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
		if (config.use_price_explicit_givingUp) {
			LOGGER.warn("Using price explicit givingUp");
			if (config.use_abandonment_threshold) {
				LOGGER.info("use_price_explicit_givingUp overrides use_abandonment_threshold");
			}
			if (!config.use_explicit_price_utility && !config.use_price_only_utility) {
				LOGGER.warn("use_price_explicit_givingUp is designed for price-based utility modes; "
						+ "current utility mode may produce unexpected results");
			}
		}
	}

	public static boolean isUseTwinnedAFTs() {
		return config != null && config.use_twinned_AFTs;
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
		return config != null && config.use_price_explicit_givingUp;
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

			Constructor constructor = new Constructor(Config.class, new LoaderOptions());
			Yaml yaml = new Yaml(constructor);
			Config loadedConfig = yaml.load(inputStream);
			System.out.println("loadedConfig: " + loadedConfig);
			if (loadedConfig == null) {
				System.out.println("Config file is empty or invalid. Using default config values.");
				return new Config();
			}
			return loadedConfig;
		} catch (Exception e) {
			System.out.println("Failed to load config. Using default config values.");
			e.printStackTrace();
			return new Config();
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
