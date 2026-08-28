package de.cesr.crafty.core.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.ThresholdFilter;
import org.apache.logging.log4j.core.layout.PatternLayout;

public class CustomLogger {

	private static final String INFO_APPENDER_NAME = "CRAFTY_INFO_FILE_APPENDER";
	private static final String ERROR_APPENDER_NAME = "CRAFTY_ERROR_FILE_APPENDER";

	private final Logger logger;

	public CustomLogger(Class<?> c) {
		this.logger = LogManager.getLogger(c);
	}

	private boolean isConfigAvailable() {
		return ConfigLoader.config != null;
	}

	public void info(String message) {
		if (isConfigAvailable() && ConfigLoader.config.logger_info) {
			logger.info(message);
		}
	}

	public void warn(String message) {
		if (isConfigAvailable() && ConfigLoader.config.logger_warn) {
			logger.warn(message);
		}
	}

	public void error(String message) {
		if (isConfigAvailable())
			logger.error(message);
	}

	public void error(String message, Throwable throwable) {
		if (isConfigAvailable())
			logger.error(message, throwable);
	}

	public void debug(String message) {
		// For now, debug follows the same flag as trace.
		// If you later add LOGGER_debug, you can separate them.
		if (isConfigAvailable() && ConfigLoader.config.logger_trace) {
			logger.debug(message);
		}
	}

	public void trace(String message) {
		if (isConfigAvailable() && ConfigLoader.config.logger_trace) {
			logger.trace(message);
		}
	}

	public void fatal(String message) {
		if (isConfigAvailable()) {
			logger.fatal(message);
			LogManager.shutdown();
			System.exit(1);
		}
	}

	public void fatal(String message, Throwable throwable) {
		logger.fatal(message, throwable);
		LogManager.shutdown();
		System.exit(1);
	}

	/**
	 * Creates two log files in the given output directory: - LOG_INFO.txt : trace,
	 * debug, info - LOG_ERRORS.txt : warn, error, fatal
	 */
	public static synchronized void configureLoggers(Path outputDirectory) {
		Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");

		ensureDirectoryExists(outputDirectory);

		Path infoLogPath = outputDirectory.resolve("LOG_INFO.txt");
		Path errorLogPath = outputDirectory.resolve("LOG_ERRORS.txt");

		LoggerContext context = (LoggerContext) LogManager.getContext(false);
		Configuration configuration = context.getConfiguration();

		LoggerConfig rootLogger = configuration.getRootLogger();

		removeAppenderIfExists(configuration, rootLogger, INFO_APPENDER_NAME);
		removeAppenderIfExists(configuration, rootLogger, ERROR_APPENDER_NAME);

		LoggerConfig craftyLoggerConfig = configuration.getLoggerConfig("de.cesr.crafty");

		removeAppenderIfExists(configuration, craftyLoggerConfig, INFO_APPENDER_NAME);
		removeAppenderIfExists(configuration, craftyLoggerConfig, ERROR_APPENDER_NAME);

		PatternLayout infoLayout = PatternLayout.newBuilder().withConfiguration(configuration)
				.withPattern("%-5level: [%logger{1}] - %msg%n").build();

		PatternLayout errorLayout = PatternLayout.newBuilder().withConfiguration(configuration)
				.withPattern("[%-5level]: [%logger{1}] - %msg%n").build();

		Filter infoFilter = ThresholdFilter.createFilter(Level.WARN, Filter.Result.DENY, Filter.Result.ACCEPT);

		FileAppender infoAppender = FileAppender.newBuilder().setName(INFO_APPENDER_NAME)
				.withFileName(infoLogPath.toAbsolutePath().toString()).withAppend(false).setImmediateFlush(true)
				.setLayout(infoLayout).setFilter(infoFilter).setConfiguration(configuration).build();

		infoAppender.start();

		FileAppender errorAppender = FileAppender.newBuilder().setName(ERROR_APPENDER_NAME)
				.withFileName(errorLogPath.toAbsolutePath().toString()).withAppend(false).setImmediateFlush(true)
				.setLayout(errorLayout).setConfiguration(configuration).build();

		errorAppender.start();

		configuration.addAppender(infoAppender);
		configuration.addAppender(errorAppender);

		/*
		 * Attach directly to CRAFTY logger config, not only root. This is safer on HPC
		 * if root propagation behaves differently.
		 */
		craftyLoggerConfig.addAppender(infoAppender, Level.TRACE, null);
		craftyLoggerConfig.addAppender(errorAppender, Level.WARN, null);

		craftyLoggerConfig.setLevel(Level.TRACE);
		rootLogger.setLevel(Level.TRACE);

		context.updateLoggers();
	}

	private static void removeAppenderIfExists(Configuration configuration, LoggerConfig loggerConfig,
			String appenderName) {
		Appender oldAppender = configuration.getAppender(appenderName);

		loggerConfig.removeAppender(appenderName);

		if (oldAppender != null) {
			oldAppender.stop();
			configuration.getAppenders().remove(appenderName);
		}
	}

	private static void ensureDirectoryExists(Path outputDirectory) {
		try {
			Files.createDirectories(outputDirectory);
		} catch (IOException e) {
			throw new RuntimeException("Could not create log directory: " + outputDirectory, e);
		}
	}

	public static void shutdownLogger() {
		LogManager.shutdown();
	}

	public static synchronized void shutdownRunFileLoggers() {
		LoggerContext context = (LoggerContext) LogManager.getContext(false);
		Configuration configuration = context.getConfiguration();
		LoggerConfig rootLogger = configuration.getRootLogger();

		stopAndRemoveAppender(configuration, rootLogger, INFO_APPENDER_NAME);
		stopAndRemoveAppender(configuration, rootLogger, ERROR_APPENDER_NAME);

		context.updateLoggers();
	}

	private static void stopAndRemoveAppender(Configuration configuration, LoggerConfig rootLogger,
			String appenderName) {
		Appender appender = configuration.getAppender(appenderName);

		if (appender != null) {
			rootLogger.removeAppender(appenderName);
			appender.stop();

			// Remove it from the configuration map as well
			configuration.getAppenders().remove(appenderName);
		}
	}
}
