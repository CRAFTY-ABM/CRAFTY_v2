package de.cesr.crafty.gui.main;

import javafx.beans.value.ChangeListener;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.List;

public class GuiScaler {

	public static Screen lastScreen = getScreenForStage(FxMain.primaryStage);
	/**
	 * Kept for source compatibility with older controllers. JavaFX already works in
	 * logical (DPI-aware) pixels, so application-wide screen scale factors must stay
	 * at 1.0.
	 */
	public static double graphicScaleX = 1.0;
	public static double graphicScaleY = 1.0;

	private static final double BASE_FONT_SIZE = 13.0;
	private static final double MIN_FONT_SCALE = 0.9;
	private static final double MAX_FONT_SCALE = 1.5;
	private static final double DEFAULT_MIN_STAGE_WIDTH = 900;
	private static final double DEFAULT_MIN_STAGE_HEIGHT = 650;
	private static double fontScale = 1.0;
	private static boolean adaptingStage;

	public static void reScale(Stage stage) {

		ChangeListener<Number> listener = (_, _, _) -> updateForScreenChange(stage);
		stage.xProperty().addListener(listener);
		stage.yProperty().addListener(listener);
		stage.widthProperty().addListener(listener);
		stage.heightProperty().addListener(listener);

		// Initial paint
		updateForScreenChange(stage);
	}

	private static void updateForScreenChange(Stage stage) {
		if (adaptingStage) {
			return;
		}
		Screen current = getScreenForStage(stage);
		if (!sameScreen(current, lastScreen)) {
			lastScreen = current;
			adaptStageToScreen(stage, current);
		} else {
			updateMinimumStageSize(stage, current.getVisualBounds());
		}
	}

	private static boolean sameScreen(Screen first, Screen second) {
		return first != null && second != null && first.getVisualBounds().equals(second.getVisualBounds());
	}

	private static void adaptStageToScreen(Stage stage, Screen screen) {
		Rectangle2D visualBounds = screen.getVisualBounds();
		updateMinimumStageSize(stage, visualBounds);

		if (stage.isMaximized()) {
			// Windows already moves a maximized stage to the destination monitor. Do not
			// unmaximize it temporarily: that intermediate tiny layout can collapse
			// TitledPane content and chart preferred sizes.
			requestResponsiveLayout(stage);
			return;
		}

		// Keep normal dragging natural. Only shrink a window that cannot fit on the
		// destination monitor.
		if (stage.getWidth() > visualBounds.getWidth() || stage.getHeight() > visualBounds.getHeight()) {
			adaptingStage = true;
			try {
				double width = Math.min(stage.getWidth(), visualBounds.getWidth());
				double height = Math.min(stage.getHeight(), visualBounds.getHeight());
				stage.setWidth(width);
				stage.setHeight(height);
				stage.setX(Math.max(visualBounds.getMinX(),
						Math.min(stage.getX(), visualBounds.getMaxX() - width)));
				stage.setY(Math.max(visualBounds.getMinY(),
						Math.min(stage.getY(), visualBounds.getMaxY() - height)));
			} finally {
				adaptingStage = false;
			}
		}
		requestResponsiveLayout(stage);
	}

	private static void requestResponsiveLayout(Stage stage) {
		Platform.runLater(() -> {
			if (stage.getScene() == null || stage.getScene().getRoot() == null) {
				return;
			}
			stage.getScene().getRoot().applyCss();
			stage.getScene().getRoot().requestLayout();
			stage.getScene().getRoot().layout();
		});
	}

	private static void updateMinimumStageSize(Stage stage, Rectangle2D visualBounds) {
		stage.setMinWidth(Math.min(DEFAULT_MIN_STAGE_WIDTH, visualBounds.getWidth()));
		stage.setMinHeight(Math.min(DEFAULT_MIN_STAGE_HEIGHT, visualBounds.getHeight()));
	}

	public static void scaleLogoD(Group logoD) {
		// The parent layout centers the logo. Do not scale it from physical monitor
		// dimensions; JavaFX and the operating system already apply DPI scaling.
		logoD.setTranslateX(0);
		logoD.setTranslateY(0);
		logoD.getTransforms().clear();
	}

	/**
	 * Legacy entry point. Scaling the complete scene graph makes text and controls
	 * unreadable on laptops, so it now only updates the active-screen reference.
	 */
	public static void scaler(Screen current) {
		lastScreen = current;
		FxMain.anchor.getTransforms().clear();
	}

	public static void increaseTextSize(Scene scene) {
		setFontScale(scene, fontScale + 0.1);
	}

	public static void decreaseTextSize(Scene scene) {
		setFontScale(scene, fontScale - 0.1);
	}

	public static void resetTextSize(Scene scene) {
		setFontScale(scene, 1.0);
	}

	private static void setFontScale(Scene scene, double requestedScale) {
		fontScale = Math.max(MIN_FONT_SCALE, Math.min(MAX_FONT_SCALE, requestedScale));
		if (scene != null && scene.getRoot() != null) {
			scene.getRoot().setStyle("-fx-font-size: " + (BASE_FONT_SIZE * fontScale) + "px;");
		}
	}

	/**
	 * Returns the Screen that contains the largest portion of the Stage.
	 */
	static private Screen getScreenForStage(Stage stage) {
		if (stage == null) {
			return Screen.getPrimary();
		}
		Rectangle2D win = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
		List<Screen> candidates = Screen.getScreensForRectangle(win);
		if (candidates == null || candidates.isEmpty())
			return Screen.getPrimary();

		// Pick the screen with the largest intersection area
		double maxArea = -1;
		Screen best = candidates.get(0);
		for (Screen s : candidates) {
			Rectangle2D vb = s.getVisualBounds();
			double xOverlap = Math.max(0,
					Math.min(win.getMaxX(), vb.getMaxX()) - Math.max(win.getMinX(), vb.getMinX()));
			double yOverlap = Math.max(0,
					Math.min(win.getMaxY(), vb.getMaxY()) - Math.max(win.getMinY(), vb.getMinY()));
			double area = xOverlap * yOverlap;
			if (area > maxArea) {
				maxArea = area;
				best = s;
			}
		}
		return best;
	}

}
