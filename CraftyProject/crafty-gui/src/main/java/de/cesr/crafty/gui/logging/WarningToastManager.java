package de.cesr.crafty.gui.logging;

import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Window;

/**
 * Displays log notifications in the bottom-right corner of the screen
 * containing the owner window. Warnings close automatically; errors remain
 * visible until the user closes them.
 */
final class WarningToastManager {

	private static final double SCREEN_MARGIN = 20;
	private static final double TOAST_SPACING = 10;
	private static final double TOAST_WIDTH = 390;
	private static final Duration DISPLAY_TIME = Duration.ofSeconds(8);
	private static final javafx.util.Duration FADE_TIME = javafx.util.Duration.millis(220);

	private final Window owner;
	private final List<WarningToast> visibleToasts = new ArrayList<>();

	WarningToastManager(Window owner) {
		this.owner = owner;
	}

	void showWarning(String message) {
		show(message, ToastType.WARNING);
	}

	void showError(String message) {
		show(message, ToastType.ERROR);
	}

	private void show(String message, ToastType type) {
		if (message != null && !message.isBlank()) {
			runOnFxThread(() -> createToast(message, type));
		}
	}

	void closeAll() {
		runOnFxThread(() -> {
			for (WarningToast toast : List.copyOf(visibleToasts)) {
				toast.popup.hide();
			}
			visibleToasts.clear();
		});
	}

	private void createToast(String message, ToastType type) {
		if (owner == null || !owner.isShowing()) {
			return;
		}

		Label icon = new Label("!");
		icon.getStyleClass().add(type.iconStyleClass);

		Label title = new Label(type.title);
		title.getStyleClass().add(type.titleStyleClass);

		Label body = new Label(message);
		body.setWrapText(true);
		body.setMaxWidth(Double.MAX_VALUE);
		body.getStyleClass().add(type.messageStyleClass);

		VBox text = new VBox(3, title, body);
		HBox.setHgrow(text, Priority.ALWAYS);

		Button closeButton = new Button("\u00d7");
		closeButton.setFocusTraversable(false);
		closeButton.getStyleClass().add("warning-toast-close");

		HBox content = new HBox(12, icon, text, closeButton);
		content.setAlignment(Pos.TOP_LEFT);
		content.setPadding(new Insets(14));
		content.setPrefWidth(TOAST_WIDTH);
		content.setMaxWidth(TOAST_WIDTH);
		content.getStyleClass().add(type.containerStyleClass);

		URL stylesheet = WarningToastManager.class.getResource("/styles.css");
		if (stylesheet != null) {
			content.getStylesheets().add(stylesheet.toExternalForm());
		}

		Popup popup = new Popup();
		popup.setAutoFix(true);
		popup.setAutoHide(false);
		popup.setHideOnEscape(false);
		popup.getContent().add(content);

		WarningToast toast = new WarningToast(popup, content);
		visibleToasts.add(toast);
		closeButton.setOnAction(_ -> dismiss(toast));

		popup.show(owner);
		content.applyCss();
		content.autosize();
		repositionToasts();

		if (type.autoClose) {
			PauseTransition delay = new PauseTransition(javafx.util.Duration.millis(DISPLAY_TIME.toMillis()));
			delay.setOnFinished(_ -> dismiss(toast));
			toast.delay = delay;
			delay.play();
		}
	}

	private void dismiss(WarningToast toast) {
		if (!visibleToasts.contains(toast)) {
			return;
		}

		if (toast.delay != null) {
			toast.delay.stop();
		}

		FadeTransition fade = new FadeTransition(FADE_TIME, toast.content);
		fade.setFromValue(toast.content.getOpacity());
		fade.setToValue(0);
		fade.setOnFinished(_ -> {
			toast.popup.hide();
			visibleToasts.remove(toast);
			repositionToasts();
		});
		fade.play();
	}

	private void repositionToasts() {
		Rectangle2D screen = getOwnerScreen().getVisualBounds();
		double nextY = screen.getMaxY() - SCREEN_MARGIN;

		for (int index = visibleToasts.size() - 1; index >= 0; index--) {
			WarningToast toast = visibleToasts.get(index);
			Bounds bounds = toast.content.getLayoutBounds();
			double width = Math.max(bounds.getWidth(), TOAST_WIDTH);
			double height = Math.max(bounds.getHeight(), toast.content.prefHeight(width));

			nextY -= height;
			toast.popup.setX(screen.getMaxX() - width - SCREEN_MARGIN);
			toast.popup.setY(nextY);
			nextY -= TOAST_SPACING;
		}
	}

	private Screen getOwnerScreen() {
		List<Screen> screens = Screen.getScreensForRectangle(owner.getX(), owner.getY(), owner.getWidth(),
				owner.getHeight());
		return screens.isEmpty() ? Screen.getPrimary() : screens.getFirst();
	}

	private static void runOnFxThread(Runnable action) {
		if (Platform.isFxApplicationThread()) {
			action.run();
		} else {
			Platform.runLater(action);
		}
	}

	private static final class WarningToast {
		private final Popup popup;
		private final Region content;
		private PauseTransition delay;

		private WarningToast(Popup popup, Region content) {
			this.popup = popup;
			this.content = content;
		}
	}

	private enum ToastType {
		WARNING("Warning", true, "warning-toast", "warning-toast-icon", "warning-toast-title",
				"warning-toast-message"),
		ERROR("Error", false, "error-toast", "error-toast-icon", "error-toast-title", "error-toast-message");

		private final String title;
		private final boolean autoClose;
		private final String containerStyleClass;
		private final String iconStyleClass;
		private final String titleStyleClass;
		private final String messageStyleClass;

		ToastType(String title, boolean autoClose, String containerStyleClass, String iconStyleClass,
				String titleStyleClass, String messageStyleClass) {
			this.title = title;
			this.autoClose = autoClose;
			this.containerStyleClass = containerStyleClass;
			this.iconStyleClass = iconStyleClass;
			this.titleStyleClass = titleStyleClass;
			this.messageStyleClass = messageStyleClass;
		}
	}
}
