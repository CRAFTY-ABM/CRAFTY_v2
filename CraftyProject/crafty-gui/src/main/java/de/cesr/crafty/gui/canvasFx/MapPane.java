package de.cesr.crafty.gui.canvasFx;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Affine;

public class MapPane extends Pane {
	private static final double MAP_MARGIN = 24;
	private static final double MIN_SCALE = 0.02;
	private static final double MAX_SCALE = 1000;

	static double scale = 1;
	static double offsetX;
	static double offsetY;

	private final Rectangle marquee = new Rectangle();
	private double dragStartX;
	private double dragStartY;
	private boolean fitOnFirstLayout = true;

	public enum MouseMode {
		SELECT, PAN, ZOOM
	}

	public static MouseMode mouseMode = MouseMode.PAN;

	public MapPane() {
		getChildren().add(CellsCanvas.getCanvas());

		setOnScroll(event -> {
			double factor = event.getDeltaY() > 0 ? 1.1 : 1 / 1.1;
			zoomAt(factor, event.getX(), event.getY());
			event.consume();
		});

		wireMouseHandlers(new Delta());
		initMarquee();

		widthProperty().addListener(_ -> resizeCanvas());
		heightProperty().addListener(_ -> resizeCanvas());
	}

	private void initMarquee() {
		marquee.setFill(Color.web("#4A90E4", 0.2));
		marquee.setStroke(Color.web("#4A90E4"));
		marquee.getStrokeDashArray().setAll(6.0, 6.0);
		marquee.setVisible(false);
		getChildren().add(marquee);
	}

	private void resizeCanvas() {
		if (getWidth() <= 1 || getHeight() <= 1) {
			return;
		}

		CellsCanvas.getCanvas().setWidth(getWidth());
		CellsCanvas.getCanvas().setHeight(getHeight());
		if (fitOnFirstLayout) {
			fitOnFirstLayout = false;
			fitMapInWindow();
		} else {
			redraw();
		}
	}

	private static void redraw() {
		if (CellsCanvas.getCanvas() == null) {
			return;
		}
		GraphicsContext graphics = CellsCanvas.getCanvas().getGraphicsContext2D();
		graphics.setTransform(new Affine());
		graphics.clearRect(0, 0, CellsCanvas.getCanvas().getWidth(), CellsCanvas.getCanvas().getHeight());
		graphics.translate(offsetX, offsetY);
		graphics.scale(scale, scale);
		CellsCanvas.colorMap();
	}

	public static void fitMapInWindow() {
		if (CellsCanvas.getCanvas() == null) {
			return;
		}

		double mapWidth = CellsCanvas.maxX - CellsCanvas.minX;
		double mapHeight = CellsCanvas.maxY - CellsCanvas.minY;
		double canvasWidth = CellsCanvas.getCanvas().getWidth();
		double canvasHeight = CellsCanvas.getCanvas().getHeight();
		if (mapWidth <= 0 || mapHeight <= 0 || canvasWidth <= MAP_MARGIN || canvasHeight <= MAP_MARGIN) {
			return;
		}

		double horizontalScale = (canvasWidth - MAP_MARGIN * 2) / mapWidth;
		double verticalScale = (canvasHeight - MAP_MARGIN * 2) / mapHeight;
		// Fractional scales let a large map fit completely on a laptop screen.
		scale = clampScale(Math.min(horizontalScale, verticalScale));
		offsetX = (canvasWidth - mapWidth * scale) / 2.0;
		offsetY = (canvasHeight - mapHeight * scale) / 2.0;
		redraw();
	}

	public static void zoom(int direction) {
		double factor = direction > 0 ? 1.2 : 1 / 1.2;
		zoomAt(factor, CellsCanvas.getCanvas().getWidth() / 2, CellsCanvas.getCanvas().getHeight() / 2);
	}

	private static void zoomAt(double factor, double pivotX, double pivotY) {
		double worldX = (pivotX - offsetX) / scale;
		double worldY = (pivotY - offsetY) / scale;
		double newScale = clampScale(scale * factor);
		offsetX = pivotX - worldX * newScale;
		offsetY = pivotY - worldY * newScale;
		scale = newScale;
		redraw();
	}

	private static double clampScale(double value) {
		return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
	}

	private void wireMouseHandlers(Delta drag) {
		setOnMousePressed(event -> {
			if (mouseMode == MouseMode.PAN && event.getButton() == MouseButton.PRIMARY) {
				drag.x = event.getX();
				drag.y = event.getY();
			} else if (mouseMode == MouseMode.ZOOM && event.getButton() == MouseButton.PRIMARY) {
				dragStartX = event.getX();
				dragStartY = event.getY();
				marquee.setX(dragStartX);
				marquee.setY(dragStartY);
				marquee.setWidth(0);
				marquee.setHeight(0);
				marquee.setVisible(true);
			}
		});

		setOnMouseDragged(event -> {
			if (mouseMode == MouseMode.PAN && event.isPrimaryButtonDown()) {
				offsetX += event.getX() - drag.x;
				offsetY += event.getY() - drag.y;
				drag.x = event.getX();
				drag.y = event.getY();
				redraw();
			} else if (mouseMode == MouseMode.ZOOM && marquee.isVisible()) {
				double width = event.getX() - dragStartX;
				double height = event.getY() - dragStartY;
				marquee.setX(width >= 0 ? dragStartX : event.getX());
				marquee.setY(height >= 0 ? dragStartY : event.getY());
				marquee.setWidth(Math.abs(width));
				marquee.setHeight(Math.abs(height));
			}
		});

		setOnMouseReleased(_ -> {
			if (mouseMode != MouseMode.ZOOM || !marquee.isVisible()) {
				return;
			}
			marquee.setVisible(false);
			if (marquee.getWidth() >= 4 && marquee.getHeight() >= 4) {
				zoomToMarquee();
			}
		});
	}

	private void zoomToMarquee() {
		double screenMinX = marquee.getX();
		double screenMinY = marquee.getY();
		double screenMaxX = screenMinX + marquee.getWidth();
		double screenMaxY = screenMinY + marquee.getHeight();

		double worldMinX = (screenMinX - offsetX) / scale;
		double worldMinY = (screenMinY - offsetY) / scale;
		double worldMaxX = (screenMaxX - offsetX) / scale;
		double worldMaxY = (screenMaxY - offsetY) / scale;
		double worldWidth = worldMaxX - worldMinX;
		double worldHeight = worldMaxY - worldMinY;

		double newScale = clampScale(Math.min((getWidth() - MAP_MARGIN * 2) / worldWidth,
				(getHeight() - MAP_MARGIN * 2) / worldHeight));
		offsetX = (getWidth() - worldWidth * newScale) / 2.0 - worldMinX * newScale;
		offsetY = (getHeight() - worldHeight * newScale) / 2.0 - worldMinY * newScale;
		scale = newScale;
		redraw();
	}

	private static final class Delta {
		private double x;
		private double y;
	}
}
