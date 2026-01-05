package de.cesr.crafty.core.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.PatternLayout;

	/**
	 * Lightweight wrapper around Log4j2 that respects the runtime configuration flags in {@link Config}.
	 *
	 * This class provides convenience methods (info/warn/trace, etc.) that only emit messages when the
	 * configuration has been loaded and the corresponding logging flag is enabled (e.g.,
	 * {@code LOGGER_info}, {@code LOGGER_warn}, {@code LOGGER_trace}). This keeps logging behaviour
	 * consistent across the codebase without repeating flag checks everywhere.
	 *
	 * It also supports dynamic file logging via {@link #configureLogger(Path)}. This method creates a
	 * Log4j2 {@link FileAppender} at runtime (creating parent directories if needed) and attaches it to
	 * the root logger, so logs can be written into the model output directory defined by the run.
	 *
	 * Notes:
	 * - If configuration is not available yet ({@link ConfigLoader#config} is null), most log methods do nothing.
	 * - {@link #fatal(String)} logs the message and terminates the JVM with exit code 1.
	 */
	
	/**
	 * @author Mohamed Byari
	 *
	 */


public class CustomLogger {

	private final Logger logger;

	public CustomLogger(Class<?> c) {
		this.logger = LogManager.getLogger(c);
	}

	private boolean isConfigAvailable() {
		return de.cesr.crafty.core.cli.ConfigLoader.config != null;
	}

	public void info(String message) {
		if (isConfigAvailable())
			if (ConfigLoader.config.LOGGER_info)
				logger.info(message);
	}

	public void warn(String message) {
		if (isConfigAvailable())
			if (ConfigLoader.config.LOGGER_warn)
				logger.warn(message);
	}

	public void error(String message) {
		if (isConfigAvailable())
			logger.error(message);
	}

	public void debug(String message) {
		if (isConfigAvailable())
			logger.debug(message);
	}

	public void trace(String message) {
		if (isConfigAvailable())
			if (ConfigLoader.config.LOGGER_trace)
				logger.trace(message);
	}

	public void fatal(String message) {
		if (isConfigAvailable()) {
			logger.fatal(message);
			System.exit(1);
		}

	}

	public static void configureLogger(Path logFilePath) {
		ensureDirectoryExists(logFilePath);

		LoggerContext context = (LoggerContext) LogManager.getContext(false);
		Configuration config = context.getConfiguration();
		// Create a layout for the log messages
		PatternLayout layout = PatternLayout.newBuilder().withPattern("%d{HH:mm:ss} - %-5level: [%logger{36}] - %msg%n")
				.build();
		// Create the FileAppender
		FileAppender appender = FileAppender.newBuilder().withFileName(logFilePath.toString())
				.setName("DynamicFileAppender").setLayout(layout).withAppend(false) // Append to the file if it exists
				.setConfiguration(config) // Pass the configuration
				.build();
		// Start the appender
		appender.start();

		// Add the appender to the root logger
		config.addAppender(appender);
		config.getRootLogger().addAppender(appender, null, null);
		// Update the logger context
		context.updateLoggers();
	}

	private static void ensureDirectoryExists(Path logFilePath) {
		Path directory = logFilePath.getParent();
		if (directory != null && !Files.exists(directory)) {
			try {
				Files.createDirectories(directory);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} // Create directories if they don't exist
		}
	}

}
