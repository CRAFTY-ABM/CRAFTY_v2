package de.cesr.crafty.core.utils.graphics;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;

import javax.imageio.ImageIO;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.output.Listener;
import de.cesr.crafty.core.updaters.Timestep;

public class Triple_value_PNG {

	public static void exportRgbMapAsPng(File out, ConcurrentHashMap<String, Cell> cells, String name,
			ToDoubleFunction<Cell> redExtractor, ToDoubleFunction<Cell> greenExtractor,
			ToDoubleFunction<Cell> blueExtractor, String redLabel, String greenLabel, String blueLabel) {

		System.setProperty("java.awt.headless", "true");

		if (ConfigLoader.config == null)
			return;

		int year = Timestep.getCurrentYear();
		if (!Listener.yearsMapExporting.contains(year))
			return;

		// If maxX/maxY are already dimensions, remove the +1
		int mapWidth = CellsLoader.maxX + 1;
		int mapHeight = CellsLoader.maxY + 1;

		// -------------------------------------------------
		// 1) Auto min/max for each channel
		// -------------------------------------------------
		double[] rRange = findMinMax(cells, redExtractor);
		double[] gRange = findMinMax(cells, greenExtractor);
		double[] bRange = findMinMax(cells, blueExtractor);

		double rMin = rRange[0];
		double rMax = rRange[1];

		double gMin = gRange[0];
		double gMax = gRange[1];

		double bMin = bRange[0];
		double bMax = bRange[1];

		// -------------------------------------------------
		// 2) Create pixel array
		// -------------------------------------------------
		int[] argb = new int[mapWidth * mapHeight];
		Arrays.fill(argb, 0x00000000); // transparent background

		cells.values().forEach(c -> {
			if (c == null)
				return;

			int x = c.getX();
			int y = c.getY();

			if (x < 0 || y < 0 || x >= mapWidth || y >= mapHeight)
				return;

			double rv = redExtractor.applyAsDouble(c);
			double gv = greenExtractor.applyAsDouble(c);
			double bv = blueExtractor.applyAsDouble(c);

			if (Double.isNaN(rv) || Double.isNaN(gv) || Double.isNaN(bv))
				return;

			int color = getRgbColorForValues(rv, gv, bv, rMin, rMax, gMin, gMax, bMin, bMax);

			int idx = y * mapWidth + x;
			argb[idx] = color;
		});

		BufferedImage mapImage = new BufferedImage(mapWidth, mapHeight, BufferedImage.TYPE_INT_ARGB);
		mapImage.setRGB(0, 0, mapWidth, mapHeight, argb, 0, mapWidth);

		// -------------------------------------------------
		// 3) Final image with legend
		// -------------------------------------------------
		// -------------------------------------------------
		// 3) Final image with automatically scaled legend
		// -------------------------------------------------

		int baseSize = Math.min(mapWidth, mapHeight);

		// Legend side is relative to map size.
		// Tune 0.22 if you want larger/smaller legend.
		int legendSide = clampInt((int) Math.round(baseSize * 0.22), 180, 1800);
		double legendScale = legendSide / 180.0;

		// Margins also scale, but stay reasonable
		int marginTop = clampInt((int) Math.round(baseSize * 0.025), 20, 140);
		int marginLeft = marginTop;
		int marginBottom = marginTop;
		int gapMapLegend = clampInt((int) Math.round(baseSize * 0.025), 30, 180);

		// Give enough horizontal space for large fonts and labels
		int legendWidth = Math.max(
				(int) Math.round(430 * legendScale),
				legendSide + (int) Math.round(170 * legendScale)
		);

		int legendHeight = (int) Math.round(
				(Math.sqrt(3) * legendSide / 2.0) + 120 * legendScale
		);

		int canvasWidth = marginLeft + mapWidth + gapMapLegend + legendWidth + marginLeft;
		int canvasHeight = Math.max(
				marginTop + mapHeight + marginBottom,
				marginTop + legendHeight + marginBottom
		);

		BufferedImage finalImage = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = finalImage.createGraphics();

		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			g.setColor(new Color(245, 245, 245));
			g.fillRect(0, 0, canvasWidth, canvasHeight);

			int mapX = marginLeft;
			int mapY = marginTop;
			g.drawImage(mapImage, mapX, mapY, null);

			int legendX = mapX + mapWidth + gapMapLegend;
			int legendY = mapY + clampInt((int) Math.round(baseSize * 0.02), 20, 120);

			drawRgbTriangleLegend(
					g,
					legendX,
					legendY,
					legendSide,
					legendScale,
					redLabel, rMin, rMax,
					greenLabel, gMin, gMax,
					blueLabel, bMin, bMax
			);

		} finally {
			g.dispose();
		}

		File outFile = new File(out + File.separator + name + "_" + Timestep.getCurrentYear() + ".png");

		try {
			ImageIO.write(finalImage, "png", outFile);
		} catch (IOException e) {
			throw new RuntimeException("Failed to write PNG: " + outFile, e);
		}
	}

	private static int getRgbColorForValues(double redValue, double greenValue, double blueValue, double redMin,
			double redMax, double greenMin, double greenMax, double blueMin, double blueMax) {

		double rn = normalize(redValue, redMin, redMax);
		double gn = normalize(greenValue, greenMin, greenMax);
		double bn = normalize(blueValue, blueMin, blueMax);

		double sum = rn + gn + bn;

		// All very small -> white
		if (sum <= 1e-12) {
			return 0xFFFFFFFF;
		}

		// -------------------------------------------------
		// 1) hue/composition from RGB balance
		// -------------------------------------------------
		double max = Math.max(rn, Math.max(gn, bn));

		double hr = rn / max;
		double hg = gn / max;
		double hb = bn / max;

		int hueR = clamp255((int) Math.round(255.0 * hr));
		int hueG = clamp255((int) Math.round(255.0 * hg));
		int hueB = clamp255((int) Math.round(255.0 * hb));

		// -------------------------------------------------
		// 2) overall intensity
		// low -> white
		// mid -> vivid hue
		// high -> black
		// -------------------------------------------------
		double intensity = (rn + gn + bn) / 3.0; // 0..1

		int r, g, b;

		if (intensity <= 0.5) {
			// white -> hue
			double t = intensity / 0.5; // 0..1
			r = lerp255(255, hueR, t);
			g = lerp255(255, hueG, t);
			b = lerp255(255, hueB, t);
		} else {
			// hue -> black
			double t = (intensity - 0.5) / 0.5; // 0..1
			r = lerp255(hueR, 0, t);
			g = lerp255(hueG, 0, t);
			b = lerp255(hueB, 0, t);
		}

		return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
	}

	private static int lerp255(int a, int b, double t) {
		return clamp255((int) Math.round(a + t * (b - a)));
	}

	private static double normalize(double value, double min, double max) {
		if (Double.isNaN(value) || Double.isNaN(min) || Double.isNaN(max)) {
			return 0.0;
		}

		if (max <= min) {
			return 0.5; // all values identical
		}

		double r = (value - min) / (max - min);
		return Math.max(0.0, Math.min(1.0, r));
	}

	private static double[] findMinMax(ConcurrentHashMap<String, Cell> cells, ToDoubleFunction<Cell> extractor) {

		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;

		for (Cell c : cells.values()) {
			if (c == null)
				continue;

			double v = extractor.applyAsDouble(c);
			if (Double.isNaN(v))
				continue;

			if (v < min)
				min = v;
			if (v > max)
				max = v;
		}

		if (min == Double.POSITIVE_INFINITY || max == Double.NEGATIVE_INFINITY) {
			return new double[] { 0.0, 1.0 };
		}

		return new double[] { min, max };
	}
	private static String formatDouble(double v) {
		return String.format("%.2f", v);
	}

	private static void drawRgbTriangleLegend(Graphics2D g, int x, int y, int side, double scale,
			String redLabel, double rMin, double rMax,
			String greenLabel, double gMin, double gMax,
			String blueLabel, double bMin, double bMax) {

		int h = (int) Math.round(Math.sqrt(3) * side / 2.0);

		double ax = x + side / 2.0;
		double ay = y;

		double bx = x;
		double by = y + h;

		double cx = x + side;
		double cy = y + h;

		int titleFontSize = scaledFontSize(13, scale);
		int cornerFontSize = scaledFontSize(12, scale);
		int labelFontSize = scaledFontSize(11, scale);

		int dotRadius = scaled(5, scale);
		int labelGap = scaled(24, scale);

		// -------------------------------------------------
		// Title
		// -------------------------------------------------
		g.setColor(Color.DARK_GRAY);
		g.setFont(new Font("SansSerif", Font.BOLD, titleFontSize));
		g.drawString(Timestep.getCurrentYear() + "", x, y - scaled(20, scale));

		// -------------------------------------------------
		// Triangle image
		// -------------------------------------------------
		BufferedImage triangleImg = new BufferedImage(side + 1, h + 1, BufferedImage.TYPE_INT_ARGB);

		for (int py = 0; py <= h; py++) {
			for (int px = 0; px <= side; px++) {

				double gx = x + px + 0.5;
				double gy = y + py + 0.5;

				double[] w = barycentric(gx, gy, ax, ay, bx, by, cx, cy);

				double wr = w[0];
				double wg = w[1];
				double wb = w[2];

				if (wr >= 0 && wg >= 0 && wb >= 0) {

					double max = Math.max(wr, Math.max(wg, wb));

					int r = 0;
					int gg = 0;
					int b = 0;

					if (max > 0) {
						r = clamp255((int) Math.round(255.0 * wr / max));
						gg = clamp255((int) Math.round(255.0 * wg / max));
						b = clamp255((int) Math.round(255.0 * wb / max));
					}

					int argb = 0xFF000000 | ((r & 0xFF) << 16) | ((gg & 0xFF) << 8) | (b & 0xFF);
					triangleImg.setRGB(px, py, argb);

				} else {
					triangleImg.setRGB(px, py, 0x00000000);
				}
			}
		}

		g.drawImage(triangleImg, x, y, null);

		// Border
		g.setColor(new Color(90, 90, 90));
		g.drawLine((int) ax, (int) ay, (int) bx, (int) by);
		g.drawLine((int) bx, (int) by, (int) cx, (int) cy);
		g.drawLine((int) cx, (int) cy, (int) ax, (int) ay);

		// Corner dots
		g.setColor(Color.RED);
		g.fillOval((int) ax - dotRadius, (int) ay - dotRadius, dotRadius * 2, dotRadius * 2);

		g.setColor(Color.GREEN);
		g.fillOval((int) bx - dotRadius, (int) by - dotRadius, dotRadius * 2, dotRadius * 2);

		g.setColor(Color.BLUE);
		g.fillOval((int) cx - dotRadius, (int) cy - dotRadius, dotRadius * 2, dotRadius * 2);

		// Corner labels
		g.setFont(new Font("SansSerif", Font.BOLD, cornerFontSize));

		g.setColor(Color.RED);
		g.drawString("R", (int) ax - scaled(4, scale), (int) ay - scaled(10, scale));

		g.setColor(Color.GREEN.darker());
		g.drawString("G", (int) bx - scaled(16, scale), (int) by + scaled(4, scale));

		g.setColor(Color.BLUE);
		g.drawString("B", (int) cx + scaled(8, scale), (int) cy + scaled(4, scale));

		// -------------------------------------------------
		// Full label list below the triangle
		// -------------------------------------------------
		int labelY = y + h + scaled(35, scale);

		g.setFont(new Font("SansSerif", Font.PLAIN, labelFontSize));

		drawLegendLine(g, x, labelY, Color.RED,
				"R = " + redLabel + " [" + formatDouble(rMin) + " – " + formatDouble(rMax) + "]",
				scale);

		drawLegendLine(g, x, labelY + labelGap, Color.GREEN.darker(),
				"G = " + greenLabel + " [" + formatDouble(gMin) + " – " + formatDouble(gMax) + "]",
				scale);

		drawLegendLine(g, x, labelY + 2 * labelGap, Color.BLUE,
				"B = " + blueLabel + " [" + formatDouble(bMin) + " – " + formatDouble(bMax) + "]",
				scale);

		// -------------------------------------------------
		// Brightness bar
		// -------------------------------------------------
		int barX = x + side + scaled(35, scale);
		int barY = y + scaled(10, scale);
		int barW = scaled(18, scale);
		int barH = h - scaled(20, scale);

		drawBrightnessBar(g, barX, barY, barW, barH, scale);
	}

	private static void drawLegendLine(Graphics2D g, int x, int y, Color color, String text, double scale) {

		int barW = scaled(85, scale);
		int barH = scaled(12, scale);

		int barX = x;
		int barY = y - barH + scaled(2, scale);

		// Gradient from low contribution to high contribution
		drawHorizontalGradientBar(
				g,
				barX,
				barY,
				barW,
				barH,
				Color.WHITE,
				color
		);

		g.setColor(Color.DARK_GRAY);
		g.drawString(text, barX + barW + scaled(10, scale), y);
	}
	
	private static void drawHorizontalGradientBar(
			Graphics2D g,
			int x,
			int y,
			int width,
			int height,
			Color from,
			Color to) {

		for (int i = 0; i < width; i++) {
			double t = (double) i / Math.max(1, width - 1);

			int r = lerp255(from.getRed(), to.getRed(), t);
			int gg = lerp255(from.getGreen(), to.getGreen(), t);
			int b = lerp255(from.getBlue(), to.getBlue(), t);

			g.setColor(new Color(r, gg, b));
			g.drawLine(x + i, y, x + i, y + height);
		}

		g.setColor(Color.GRAY);
		g.drawRect(x, y, width, height);
	}

	private static double[] barycentric(double px, double py, double ax, double ay, double bx, double by, double cx,
			double cy) {

		double denom = ((by - cy) * (ax - cx) + (cx - bx) * (ay - cy));

		if (Math.abs(denom) < 1e-12) {
			return new double[] { -1, -1, -1 };
		}

		double w1 = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / denom;
		double w2 = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / denom;
		double w3 = 1.0 - w1 - w2;

		return new double[] { w1, w2, w3 };
	}

	private static void drawBrightnessBar(Graphics2D g, int x, int y, int width, int height, double scale) {
		for (int i = 0; i < height; i++) {
			double t = (double) i / Math.max(1, height - 1);
			int v = clamp255((int) Math.round(255 * (1.0 - t)));
			g.setColor(new Color(v, v, v));
			g.drawLine(x, y + i, x + width, y + i);
		}

		g.setColor(Color.GRAY);
		g.drawRect(x, y, width, height);

		g.setColor(Color.DARK_GRAY);
		g.setFont(new Font("SansSerif", Font.PLAIN, scaledFontSize(11, scale)));

		g.drawString("Intensity", x - scaled(5, scale), y - scaled(6, scale));
		g.drawString("min", x + width + scaled(6, scale), y + scaled(10, scale));
		g.drawString("max", x + width + scaled(6, scale), y + height);
	}

	private static int clamp255(int v) {
		return Math.max(0, Math.min(255, v));
	}
	
	private static int scaled(int value, double scale) {
		return Math.max(1, (int) Math.round(value * scale));
	}

	private static int scaledFontSize(int baseSize, double scale) {
		return clampInt((int) Math.round(baseSize * scale), baseSize, 120);
	}

	private static int clampInt(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
