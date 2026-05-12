package de.cesr.crafty.core.utils.graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;

import javax.imageio.ImageIO;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.output.Listener;
import de.cesr.crafty.core.updaters.Timestep;

public class Dual_value_PNG {
	
	
	public static void exportBivariateMapAsPng(File outFile, ConcurrentHashMap<String, Cell> cells, String name,
			ToDoubleFunction<Cell> xExtractor, ToDoubleFunction<Cell> yExtractor, String xLegendLabel,
			String yLegendLabel) {

		System.setProperty("java.awt.headless", "true");
		
		

		if (ConfigLoader.config == null)
			return;

		int year = Timestep.getCurrentYear();
		if (!Listener.yearsMapExporting.contains(year))
			return;

		int mapWidth = CellsLoader.maxX + 1;
		int mapHeight = CellsLoader.maxY + 1;

		// -------------------------------------------------
		// 1) Use zero as the lower bound
		// -------------------------------------------------
		double[] xRange = findZeroToMax(cells, xExtractor);
		double[] yRange = findZeroToMax(cells, yExtractor);

		double xMin = xRange[0];
		double xMax = xRange[1];
		double yMin = yRange[0];
		double yMax = yRange[1];

		// -------------------------------------------------
		// 2) Build map pixels
		// -------------------------------------------------
		int[] mapArgb = new int[mapWidth * mapHeight];
		Arrays.fill(mapArgb, 0x00000000); // transparent

		cells.values().forEach(c -> {
			if (c == null)
				return;

			int x = c.getX();
			int y = c.getY();

			if (x < 0 || y < 0 || x >= mapWidth || y >= mapHeight)
				return;

			double xv = xExtractor.applyAsDouble(c);
			double yv = yExtractor.applyAsDouble(c);

			if (!Double.isFinite(xv) || !Double.isFinite(yv))
				return;

			int color = getBivariateColor3x3(xv, yv, xMin, xMax, yMin, yMax);

			int idx = y * mapWidth + x;
			mapArgb[idx] = color;
		});

		BufferedImage mapImage = new BufferedImage(mapWidth, mapHeight, BufferedImage.TYPE_INT_ARGB);
		mapImage.setRGB(0, 0, mapWidth, mapHeight, mapArgb, 0, mapWidth);

		// -------------------------------------------------
		// 3) Final canvas with legend
		// -------------------------------------------------
		int baseSize = Math.min(mapWidth, mapHeight);

		int legendCellSize = clampInt((int) Math.round(baseSize * 0.035), 22, 600);
		double legendScale = legendCellSize / 22.0;

		int legendGridSize = 3 * legendCellSize;

		int marginTop = clampInt((int) Math.round(baseSize * 0.025), 20, 140);
		int marginLeft = marginTop;
		int marginBottom = marginTop;
		int gapMapLegend = clampInt((int) Math.round(baseSize * 0.025), 40, 220);

		int legendLeftPadding = scaled(90, legendScale);
		int legendRightPadding = scaled(40, legendScale);
		int legendBottomPadding = scaled(80, legendScale);

		int legendAreaWidth = legendLeftPadding + legendGridSize + legendRightPadding;
		int legendAreaHeight = legendGridSize + legendBottomPadding;

		int canvasWidth = marginLeft + mapWidth + gapMapLegend + legendAreaWidth + marginLeft;
		int canvasHeight = Math.max(
				marginTop + mapHeight + marginBottom,
				marginTop + legendAreaHeight + marginBottom
		);

		BufferedImage finalImage = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = finalImage.createGraphics();

		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

			g.setColor(new Color(245, 245, 245));
			g.fillRect(0, 0, canvasWidth, canvasHeight);

			int mapX = marginLeft;
			int mapY = marginTop;
			g.drawImage(mapImage, mapX, mapY, null);

			int legendX = mapX + mapWidth + gapMapLegend + legendLeftPadding;
			int legendY = mapY + clampInt((int) Math.round(baseSize * 0.02), 20, 120);

			drawBivariateLegend3x3(
					g,
					legendX,
					legendY,
					legendCellSize,
					legendScale,
					xLegendLabel,
					yLegendLabel,
					xMin,
					xMax,
					yMin,
					yMax
			);

		} finally {
			g.dispose();
		}

		File targetFile = new File(outFile, name + "_" + Timestep.getCurrentYear() + ".png");

		try {
			ImageIO.write(finalImage, "png", targetFile);
		} catch (IOException e) {
			throw new RuntimeException("Failed to write PNG: " + targetFile, e);
		}
	}

	private static void drawBivariateLegend3x3(Graphics2D g, int x, int y, int cellSize, double scale,
			String xLegendLabel, String yLegendLabel,
			double xMin, double xMax, double yMin, double yMax) {

		int gridSize = 3 * cellSize;

		// -------------------------------------------------
		// Draw 3x3 color squares
		// -------------------------------------------------
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				g.setColor(new Color(BIVARIATE_PALETTE[row][col], true));
				g.fillRect(x + col * cellSize, y + row * cellSize, cellSize, cellSize);

				g.setColor(new Color(255, 255, 255, 100));
				g.setStroke(new BasicStroke(Math.max(1f, (float) scale)));
				g.drawRect(x + col * cellSize, y + row * cellSize, cellSize, cellSize);
			}
		}

		// Outer border
		g.setColor(new Color(140, 140, 140));
		g.setStroke(new BasicStroke(Math.max(1f, (float) scale)));
		g.drawRect(x, y, gridSize, gridSize);

		g.setColor(Color.DARK_GRAY);

		// -------------------------------------------------
		// X axis labels: Low/High markers
		// -------------------------------------------------
		g.setFont(new Font("SansSerif", Font.PLAIN, scaledFontSize(11, scale)));
		g.drawString("0", x, y + gridSize + scaled(14, scale));

		int highWidth = g.getFontMetrics().stringWidth("High");
		g.drawString("High", x + gridSize - highWidth, y + gridSize + scaled(14, scale));

		// -------------------------------------------------
		// X variable label (line 1)
		// -------------------------------------------------
		g.setFont(new Font("SansSerif", Font.PLAIN, scaledFontSize(12, scale)));
		int xLabelY = y + gridSize + scaled(34, scale);

		int xLabelWidth = g.getFontMetrics().stringWidth(xLegendLabel);
		g.drawString(xLegendLabel, x + (gridSize - xLabelWidth) / 2, xLabelY);

		// X interval (line 2)
		String xRangeText = "[" + formatLegendNumber(xMin) + " - " + formatLegendNumber(xMax) + "]";
		g.setFont(new Font("SansSerif", Font.PLAIN, scaledFontSize(11, scale)));
		int xRangeWidth = g.getFontMetrics().stringWidth(xRangeText);
		g.drawString(xRangeText, x + (gridSize - xRangeWidth) / 2, xLabelY + scaled(16, scale));

		// -------------------------------------------------
		// Y axis labels: Low/High markers
		// -------------------------------------------------
		g.setFont(new Font("SansSerif", Font.PLAIN, scaledFontSize(11, scale)));
		drawRotatedString(g, "0", x - scaled(10, scale), y + gridSize);
		drawRotatedString(g, "High", x - scaled(10, scale), y + scaled(28, scale));

		// -------------------------------------------------
		// Y variable label (line 1, rotated)
		// -------------------------------------------------
		g.setFont(new Font("SansSerif", Font.PLAIN, scaledFontSize(12, scale)));
		drawRotatedString(g, yLegendLabel, x - scaled(45, scale), y + gridSize - scaled(4, scale));

		// Y interval (line 2, rotated)
		String yRangeText = "[" + formatLegendNumber(yMin) + " - " + formatLegendNumber(yMax) + "]";
		g.setFont(new Font("SansSerif", Font.PLAIN, scaledFontSize(11, scale)));
		drawRotatedString(g, yRangeText, x - scaled(62, scale), y + gridSize - scaled(4, scale));
	}

	private static void drawRotatedString(Graphics2D g, String text, int x, int y) {
		AffineTransform old = g.getTransform();
		g.rotate(-Math.PI / 2, x, y);
		g.drawString(text, x, y);
		g.setTransform(old);
	}
	
	private static final DecimalFormat LEGEND_NUMBER_FORMAT = new DecimalFormat("0.##");
	private static String formatLegendNumber(double value) {
		if (!Double.isFinite(value))
			return "NA";
		return LEGEND_NUMBER_FORMAT.format(value);
	}

	private static double[] findZeroToMax(ConcurrentHashMap<String, Cell> cells, ToDoubleFunction<Cell> extractor) {

		double max = Double.NEGATIVE_INFINITY;

		for (Cell c : cells.values()) {
			if (c == null)
				continue;

			double v = extractor.applyAsDouble(c);

			if (!Double.isFinite(v))
				continue;

			if (v > max)
				max = v;
		}

		// If everything is missing, zero, or negative, keep a safe default.
		if (!Double.isFinite(max) || max <= 0.0) {
			return new double[] { 0.0, 1.0 };
		}

		return new double[] { 0.0, max };
	}

	private static double normalize(double value, double min, double max) {
		if (!Double.isFinite(value) || !Double.isFinite(min) || !Double.isFinite(max)) {
			return 0.0;
		}

		if (max <= min) {
			return 0.0;
		}

		double r = (value - min) / (max - min);
		return Math.max(0.0, Math.min(1.0, r));
	}

	private static int toBin3(double ratio) {
		if (ratio < 1.0 / 3.0)
			return 0;
		if (ratio < 2.0 / 3.0)
			return 1;
		return 2;
	}

	private static int getBivariateColor3x3(double xValue, double yValue, double xMin, double xMax, double yMin,
			double yMax) {

		double xr = normalize(xValue, xMin, xMax);
		double yr = normalize(yValue, yMin, yMax);

		int xBin = toBin3(xr);
		int yBin = toBin3(yr);

		// yBin = 0 means low Y, which is the bottom row in the legend.
		// Palette row 0 is high Y, so invert the Y index.
		int paletteRow = 2 - yBin;

		return BIVARIATE_PALETTE[paletteRow][xBin];
	}

	/*
	 * Bivariate palette:
	 *
	 *             X low        X mid        X high
	 * Y high      blue/teal    blue-mix     dark blue/purple
	 * Y mid       light blue   grey-purple  purple
	 * Y low       white        light pink   dark pink
	 *
	 * X direction = pink/red
	 * Y direction = blue/teal
	 */
	private static final int[][] BIVARIATE_PALETTE = {
			{ 0xFF5CBCBC, 0xFF5B93AD, 0xFF434F8D },
			{ 0xFFA3D5D5, 0xFF9EA4C3, 0xFF8764A0 },
			{ 0xFFFFFFFF, 0xFFD1A7C6, 0xFFB465A2 }
	};

	private static int scaled(int value, double scale) {
		return Math.max(1, (int) Math.round(value * scale));
	}

	private static int scaledFontSize(int baseSize, double scale) {
		return clampInt((int) Math.round(baseSize * scale), baseSize, 240);
	}

	private static int clampInt(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}