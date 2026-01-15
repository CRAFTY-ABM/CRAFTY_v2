package de.cesr.crafty.gui.main;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.transform.Scale;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.List;

public class GuiScaler {

	public static Screen lastScreen = getScreenForStage(FxMain.primaryStage);
	public static double graphicScaleX;
	public static double graphicScaleY;
//	public static boolean igorInitialScaled = true;

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
		Screen current = getScreenForStage(stage);
		if (current != lastScreen) {
			scaler(current);
			lastScreen = current;
		}
	}

	public static void scaleLogoD(Group logoD) {
		logoD.setTranslateX(GuiScaler.lastScreen.getBounds().getWidth() / 2.5);
		logoD.setTranslateY(GuiScaler.lastScreen.getBounds().getHeight() / 4);
//		scaler(lastScreen);
	
		double graphicScaleX = Screen.getPrimary().getBounds().getWidth() / 2750;
		double graphicScaleY = Screen.getPrimary().getBounds().getHeight() / 1550;
		double scale = Math.min(graphicScaleX * 1.2, graphicScaleY * 1.2);
		Scale scaleTransform = new Scale(scale, scale, 0, 0);
		logoD.getTransforms().clear();
		logoD.getTransforms().add(scaleTransform);
	}

	public static void scaler(Screen current) {
		graphicScaleX = current.getBounds().getWidth() / 2750;
		graphicScaleY = current.getBounds().getHeight() / 1550;
		double scale = Math.min(graphicScaleX, graphicScaleY);
		Scale scaleTransform = new Scale(scale, scale, 0, 0);
		FxMain.anchor.getTransforms().clear();
		FxMain.anchor.getTransforms().add(scaleTransform);
	}

	/**
	 * Returns the Screen that contains the largest portion of the Stage.
	 */
	static private Screen getScreenForStage(Stage stage) {
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
