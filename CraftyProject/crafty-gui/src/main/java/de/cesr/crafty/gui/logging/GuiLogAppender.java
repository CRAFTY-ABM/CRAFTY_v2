package de.cesr.crafty.gui.logging;

import java.io.Serializable;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

import javafx.stage.Window;

/**
 * Sends CRAFTY warning and error log events to non-blocking JavaFX
 * notifications.
 */
public final class GuiLogAppender extends AbstractAppender {

	private static final String APPENDER_NAME = "CRAFTY_GUI_LOG_APPENDER";
	private static final String CRAFTY_LOGGER_NAME = "de.cesr.crafty";

	private static GuiLogAppender instance;
	private static LoggerConfig attachedLoggerConfig;

	private final WarningToastManager toastManager;

	private GuiLogAppender(Window owner) {
		super(APPENDER_NAME, (Filter) null, createLayout(), true, Property.EMPTY_ARRAY);
		toastManager = new WarningToastManager(owner);
	}

	private static Layout<? extends Serializable> createLayout() {
		return PatternLayout.newBuilder().withPattern("%msg").build();
	}

	/**
	 * Installs the appender once the primary JavaFX window is available.
	 */
	public static synchronized void install(Window owner) {
		if (instance != null) {
			return;
		}

		LoggerContext context = (LoggerContext) LogManager.getContext(false);
		Configuration configuration = context.getConfiguration();
		attachedLoggerConfig = configuration.getLoggerConfig(CRAFTY_LOGGER_NAME);

		instance = new GuiLogAppender(owner);
		instance.start();
		configuration.addAppender(instance);
		attachedLoggerConfig.addAppender(instance, Level.WARN, null);
		context.updateLoggers();
	}

	/**
	 * Removes the GUI appender and closes any visible notifications.
	 */
	public static synchronized void uninstall() {
		if (instance == null) {
			return;
		}

		LoggerContext context = (LoggerContext) LogManager.getContext(false);
		Configuration configuration = context.getConfiguration();

		if (attachedLoggerConfig != null) {
			attachedLoggerConfig.removeAppender(APPENDER_NAME);
		}
		configuration.getAppenders().remove(APPENDER_NAME);
		instance.toastManager.closeAll();
		instance.stop();
		context.updateLoggers();

		instance = null;
		attachedLoggerConfig = null;
	}

	@Override
	public void append(LogEvent event) {
		String loggerName = event.getLoggerName();
		if (loggerName == null || !loggerName.startsWith(CRAFTY_LOGGER_NAME)) {
			return;
		}

		String message = event.getMessage().getFormattedMessage();
		if (event.getLevel() == Level.WARN) {
			toastManager.showWarning(message);
		} else if (event.getLevel() == Level.ERROR) {
			toastManager.showError(message);
		}
	}
}
