package de.cesr.crafty.core.cli;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import org.yaml.snakeyaml.LoaderOptions;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

	/**
	 * Loads the CRAFTY configuration from a YAML file and exposes it as a global {@link Config}.
	 *
	 * The loader first tries to read a user-provided YAML path (typically set by the CLI flag:
	 * --config-file "C:\\path\\to\\config.yaml"). If the path is missing or the file does not exist,
	 * it falls back to a bundled classpath resource at "/config.yaml".
	 *
	 * If the file cannot be found, is empty, or cannot be parsed, the loader returns a default
	 * {@code new Config()} to keep the application runnable.
	 *
	 * Typical usage: set {@link #configPath}  and call {@link #init()} once at startup.
	 * {@link #init()} loads the config and then calls {@link Config#inialize()} to perform any
	 * post-load initialization of derived/default values.
	 *
	 * Note: this class uses SnakeYAML with a {@link org.yaml.snakeyaml.constructor.Constructor}
	 * bound to {@link Config} to map YAML keys to Java fields.
	 */
/*
 * @author Mohamed Byari
 *
 */
public class ConfigLoader {
	public static String configPath;
	public static Config config;

	public static void init() {
		config = loadConfig();
	}

//--config-file "C:\Users\byari-m\Desktop\config.yaml"
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
			System.out.println("loadedConfig: "+loadedConfig);
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

}
