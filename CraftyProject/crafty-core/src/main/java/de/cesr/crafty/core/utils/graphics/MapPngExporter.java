package de.cesr.crafty.core.utils.graphics;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.output.Listener;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.PathTools;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import net.mahdilamb.colormap.Colormap;
import net.mahdilamb.colormap.Colormaps;

/**
 * Pure-Java PNG map exporter (no GeoTools, no ImageIO). Writes a simple RGBA
 * PNG where each cell is one pixel.
 */
public final class MapPngExporter {
	private static final CustomLogger LOGGER = new CustomLogger(MapPngExporter.class);

	private MapPngExporter() {
	}

	/**
	 * Exports the current owner map as PNG into: <output>/MapsPlots/map_<year>.png
	 */
	public static void exportOwnerMapAsPng(File out, ConcurrentHashMap<String, Cell> cells, String name) {

		// Stable ordering for reproducibility
		Map<String, Integer> ownerColors = new TreeMap<>();

		for (Cell c : cells.values()) {
			if (c == null || c.getOwner() == null)
				continue;

			String ownerName = c.getOwner().getLabel();
			int rgb = parseRgbOrDefault(c.getOwner().getColor(), 0x000000);
			int argb = 0xFF000000 | (rgb & 0xFFFFFF);

			ownerColors.putIfAbsent(ownerName, argb);
		}

		List<LegendEntry> entries = new ArrayList<>();
		for (Map.Entry<String, Integer> e : ownerColors.entrySet()) {
			entries.add(new LegendEntry(e.getKey(), e.getValue()));
		}

		DiscreteLegendOptions legend = new DiscreteLegendOptions(
				name + " - " + Timestep.getCurrentYear(),
				entries
		);

		exportCellMapAsPng(out, cells, name, c -> {
			if (c.getOwner() == null)
				return null; // transparent

			int rgb = parseRgbOrDefault(c.getOwner().getColor(), 0x000000);
			return 0xFF000000 | (rgb & 0xFFFFFF);
		}, legend);
	}

	public static void exportNumericCellMapAsPng(
			File path,
			ConcurrentHashMap<String, Cell> cells,
			String name,
			ToDoubleFunction<Cell> valueExtractor) {

		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;

		for (Cell c : cells.values()) {
			if (c == null)
				continue;

			double v = valueExtractor.applyAsDouble(c);

			if (Double.isNaN(v) || Double.isInfinite(v))
				continue;

			if (v < min) min = v;
			if (v > max) max = v;
		}

		if (min == Double.POSITIVE_INFINITY || max == Double.NEGATIVE_INFINITY) {
			LOGGER.warn("No valid numeric values found for map: " + name);
			return;
		}

		final double finalMin = min;
		final double finalMax = max;
		final String palette = "Viridis";

		exportCellMapAsPng(
				path,
				cells,
				name,
				c -> {
					double v = valueExtractor.applyAsDouble(c);
					if (Double.isNaN(v) || Double.isInfinite(v))
						return null;
					return getColorForValue(palette, v, finalMin, finalMax);
				},
				new ContinuousLegendOptions(
						name + " - " + Timestep.getCurrentYear(),
						name,
						palette,
						finalMin,
						finalMax
				)
		);
	}

//	public static void exportCellMapAsPng(
//			File path,
//			ConcurrentHashMap<String, Cell> cells,
//			String name,
//			Function<Cell, Integer> cellToArgb) {
//
//		exportCellMapAsPng(path, cells, name, cellToArgb, null);
//	}

	public static void exportCellMapAsPng(
			File path,
			ConcurrentHashMap<String, Cell> cells,
			String name,
			Function<Cell, Integer> cellToArgb,
			LegendOptions legendOptions) {

		System.setProperty("java.awt.headless", "true");

		if (ConfigLoader.config == null)
			return;

		int year = Timestep.getCurrentYear();
		if (!Listener.yearsMapExporting.contains(year))
			return;

		int width = CellsLoader.maxX;
		int height = CellsLoader.maxY;

		int[] argb = new int[width * height];

		cells.values().forEach(c -> {
			if (c == null)
				return;

			int x = c.getX();
			int y = c.getY();

			if (x < 0 || y < 0 || x >= width || y >= height)
				return;

			Integer pixel = cellToArgb.apply(c);
			if (pixel == null)
				return;

			argb[y * width + x] = pixel;
		});

		RenderedPng rendered;

		if (legendOptions == null) {
			rendered = new RenderedPng(width, height, argb);
		} else if (legendOptions instanceof ContinuousLegendOptions continuous) {
			rendered = addTitleAndContinuousLegend(width, height, argb, continuous);
		} else if (legendOptions instanceof DiscreteLegendOptions discrete) {
			rendered = addTitleAndDiscreteLegend(width, height, argb, discrete);
		} else {
			throw new IllegalArgumentException("Unsupported legend type: " + legendOptions.getClass().getName());
		}

		String outDir = PathTools.makeDirectory(path.getPath() + File.separator + name);
		File outFile = new File(outDir, name + "_" + Timestep.getCurrentYear() + ".png");

		try {
			PngWriter.writeRgbaPng(outFile, rendered.width, rendered.height, rendered.argb);
		} catch (IOException e) {
			LOGGER.warn("Failed to write PNG: " + e.getMessage());
			e.printStackTrace();
		}
	}



	private static int ensureOpaque(int color) {
		if ((color & 0xFF000000) == 0) {
			return color | 0xFF000000;
		}
		return color;
	}

	private static String formatLegendNumber(double value) {
		double abs = Math.abs(value);

		if (value != 0.0 && (abs >= 10_000 || abs < 0.001)) {
			return String.format("%.2e", value);
		}

		return String.format("%.3f", value);
	}

	// ----------------------------- PNG writer -----------------------------

	private static final class PngWriter {

		private static final byte[] SIGNATURE = new byte[] { (byte) 137, 80, 78, 71, 13, 10, 26, 10 };

		// IHDR: bitDepth=8, colorType=6 (RGBA), compression=0, filter=0, interlace=0
		static void writeRgbaPng(File file, int width, int height, int[] argbPixels) throws IOException {
			if (width <= 0 || height <= 0)
				throw new IllegalArgumentException("Invalid image size");
			if (argbPixels == null || argbPixels.length < width * height)
				throw new IllegalArgumentException("argbPixels is null or too small");

			try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
				out.write(SIGNATURE);

				// IHDR
				byte[] ihdr = new byte[13];
				writeIntBE(ihdr, 0, width);
				writeIntBE(ihdr, 4, height);
				ihdr[8] = 8; // bit depth
				ihdr[9] = 6; // color type RGBA
				ihdr[10] = 0; // compression
				ihdr[11] = 0; // filter
				ihdr[12] = 0; // interlace
				writeChunk(out, "IHDR", ihdr, 0, ihdr.length);

				// IDAT (streamed as multiple chunks)
				Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION);
				byte[] defBuf = new byte[64 * 1024];

				// one scanline: filter(1 byte) + RGBA(4*width)
				byte[] row = new byte[1 + 4 * width];
				row[0] = 0; // filter type 0

				for (int y = 0; y < height; y++) {
					int base = y * width;
					int j = 1;
					for (int x = 0; x < width; x++) {
						int argb = argbPixels[base + x];
						int a = (argb >>> 24) & 0xFF;
						int r = (argb >>> 16) & 0xFF;
						int g = (argb >>> 8) & 0xFF;
						int b = (argb) & 0xFF;

						row[j++] = (byte) r;
						row[j++] = (byte) g;
						row[j++] = (byte) b;
						row[j++] = (byte) a;
					}

					def.setInput(row);
					while (!def.needsInput()) {
						int n = def.deflate(defBuf);
						if (n > 0)
							writeChunk(out, "IDAT", defBuf, 0, n);
					}
				}

				def.finish();
				while (!def.finished()) {
					int n = def.deflate(defBuf);
					if (n > 0)
						writeChunk(out, "IDAT", defBuf, 0, n);
				}
				def.end();

				// IEND
				writeChunk(out, "IEND", new byte[0], 0, 0);
				out.flush();
			}
		}

		private static void writeChunk(OutputStream out, String type, byte[] data, int off, int len)
				throws IOException {

			byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
			if (typeBytes.length != 4)
				throw new IllegalArgumentException("Chunk type must be 4 chars");

			// length
			writeIntBE(out, len);

			// type + data
			out.write(typeBytes);
			if (len > 0)
				out.write(data, off, len);

			// crc(type + data)
			CRC32 crc = new CRC32();
			crc.update(typeBytes);
			if (len > 0)
				crc.update(data, off, len);
			writeIntBE(out, (int) crc.getValue());
		}

		private static void writeIntBE(OutputStream out, int v) throws IOException {
			out.write((v >>> 24) & 0xFF);
			out.write((v >>> 16) & 0xFF);
			out.write((v >>> 8) & 0xFF);
			out.write((v) & 0xFF);
		}

		private static void writeIntBE(byte[] arr, int pos, int v) {
			arr[pos] = (byte) ((v >>> 24) & 0xFF);
			arr[pos + 1] = (byte) ((v >>> 16) & 0xFF);
			arr[pos + 2] = (byte) ((v >>> 8) & 0xFF);
			arr[pos + 3] = (byte) ((v) & 0xFF);
		}
	}

	// ----------------------------- Helpers -----------------------------

	private static int parseRgbOrDefault(String s, int defaultRgb) {
		if (s == null)
			return defaultRgb;
		String t = s.trim();
		if (t.isEmpty())
			return defaultRgb;

		if (t.startsWith("#"))
			t = t.substring(1);
		if (t.startsWith("0x") || t.startsWith("0X"))
			t = t.substring(2);

		if (t.length() != 6)
			return defaultRgb;
		try {
			return Integer.parseInt(t, 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return defaultRgb;
		}
	}

//	==== colors

	static int getColorForValue(String colortyp, double value, double MIN, double MAX) {
		Colormap colormap = Colormaps.get(colortyp);
		if (colormap == null) {
			colormap = Colormaps.get("Viridis");
		}

		double ratio = 0.0;

		if (!Double.isNaN(value) && !Double.isNaN(MIN) && !Double.isNaN(MAX) && MAX > MIN) {
			ratio = (value - MIN) / (MAX - MIN);
		}

		ratio = Math.max(0.0, Math.min(1.0, ratio));

		int red = colormap.get(ratio).getRed();
		int green = colormap.get(ratio).getGreen();
		int blue = colormap.get(ratio).getBlue();

		int rgb = ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);

		// Add full opacity alpha channel
		return 0xFF000000 | rgb;
	}



	private static final class RenderedPng {
		final int width;
		final int height;
		final int[] argb;

		RenderedPng(int width, int height, int[] argb) {
			this.width = width;
			this.height = height;
			this.argb = argb;
		}
	}
	

	
	private interface LegendOptions {
		String title();
	}

	private static final class ContinuousLegendOptions implements LegendOptions {
		final String title;
		final String legendTitle;
		final String palette;
		final double min;
		final double max;

		ContinuousLegendOptions(String title, String legendTitle, String palette, double min, double max) {
			this.title = title;
			this.legendTitle = legendTitle;
			this.palette = palette;
			this.min = min;
			this.max = max;
		}

		@Override
		public String title() {
			return title;
		}
	}

	private static final class DiscreteLegendOptions implements LegendOptions {
		final String title;
		final java.util.List<LegendEntry> entries;

		DiscreteLegendOptions(String title, java.util.List<LegendEntry> entries) {
			this.title = title;
			this.entries = entries;
		}

		@Override
		public String title() {
			return title;
		}
	}

	private static final class LegendEntry {
		final String label;
		final int argb;

		LegendEntry(String label, int argb) {
			this.label = label;
			this.argb = argb;
		}
	}


	private static RenderedPng addTitleAndContinuousLegend(
			int mapWidth,
			int mapHeight,
			int[] mapArgb,
			ContinuousLegendOptions options) {

		double scale = legendScale(mapWidth, mapHeight);

		int padding = scaled(20, scale);
		int titleHeight = scaled(45, scale);
		int bottomPadding = padding;
		int gapMapLegend = scaled(35, scale);

		int titleFontSize = scaledFontSize(18, scale);
		int legendTitleFontSize = scaledFontSize(13, scale);
		int legendLabelFontSize = scaledFontSize(12, scale);

		int legendBarWidth = scaled(22, scale);
		int legendBarHeight = clampInt(
				(int) Math.round(mapHeight * 0.45),
				scaled(120, scale),
				scaled(300, scale)
		);

		String maxLabel = formatLegendNumber(options.max);
		String minLabel = formatLegendNumber(options.min);
		String midLabel = options.min != options.max
				? formatLegendNumber((options.min + options.max) / 2.0)
				: "";

		Font legendTitleFont = new Font("SansSerif", Font.BOLD, legendTitleFontSize);
		Font legendLabelFont = new Font("SansSerif", Font.PLAIN, legendLabelFontSize);

		int maxNumberWidth = Math.max(
				measureTextWidth(legendLabelFont, maxLabel),
				Math.max(
						measureTextWidth(legendLabelFont, minLabel),
						measureTextWidth(legendLabelFont, midLabel)
				)
		);

		int legendTitleWidth = measureTextWidth(legendTitleFont, options.legendTitle);

		int legendAreaWidth = Math.max(
				scaled(150, scale),
				Math.max(
						legendTitleWidth,
						legendBarWidth + scaled(12, scale) + maxNumberWidth
				) + scaled(30, scale)
		);

		int outputWidth = padding + mapWidth + gapMapLegend + legendAreaWidth + padding;
		int outputHeight = titleHeight + mapHeight + bottomPadding;

		BufferedImage image = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		g.setColor(Color.WHITE);
		g.fillRect(0, 0, outputWidth, outputHeight);

		int mapX = padding;
		int mapY = titleHeight;

		image.setRGB(mapX, mapY, mapWidth, mapHeight, mapArgb, 0, mapWidth);

		g.setColor(new Color(180, 180, 180));
		g.drawRect(mapX, mapY, mapWidth, mapHeight);

		// -------------------------------------------------
		// Title
		// -------------------------------------------------
		g.setFont(new Font("SansSerif", Font.BOLD, titleFontSize));
		g.setColor(Color.BLACK);

		FontMetrics titleMetrics = g.getFontMetrics();
		int titleX = Math.max(padding, (outputWidth - titleMetrics.stringWidth(options.title)) / 2);
		int titleY = (titleHeight + titleMetrics.getAscent() - titleMetrics.getDescent()) / 2;

		g.drawString(options.title, titleX, titleY);

		// -------------------------------------------------
		// Legend
		// -------------------------------------------------
		int legendX = mapX + mapWidth + gapMapLegend;
		int legendY = mapY + (mapHeight - legendBarHeight) / 2;

		g.setFont(legendTitleFont);
		g.setColor(Color.BLACK);
		g.drawString(options.legendTitle, legendX, legendY - scaled(14, scale));

		for (int i = 0; i < legendBarHeight; i++) {
			double ratio = 1.0 - ((double) i / Math.max(1, legendBarHeight - 1));
			double value = options.min == options.max
					? options.min
					: options.min + ratio * (options.max - options.min);

			int color = getColorForValue(options.palette, value, options.min, options.max);
			g.setColor(new Color(ensureOpaque(color), true));
			g.fillRect(legendX, legendY + i, legendBarWidth, 1);
		}

		g.setColor(Color.BLACK);
		g.drawRect(legendX, legendY, legendBarWidth, legendBarHeight);

		g.setFont(legendLabelFont);
		FontMetrics fm = g.getFontMetrics();

		int labelX = legendX + legendBarWidth + scaled(8, scale);

		g.drawString(maxLabel, labelX, legendY + fm.getAscent());
		g.drawString(minLabel, labelX, legendY + legendBarHeight);

		if (options.min != options.max) {
			int midY = legendY + legendBarHeight / 2 + fm.getAscent() / 2;
			g.drawString(midLabel, labelX, midY);
		}

		g.dispose();

		int[] finalArgb = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		return new RenderedPng(outputWidth, outputHeight, finalArgb);
	}
	

	
	private static RenderedPng addTitleAndDiscreteLegend(
			int mapWidth,
			int mapHeight,
			int[] mapArgb,
			DiscreteLegendOptions options) {

		double scale = legendScale(mapWidth, mapHeight);

		int padding = scaled(20, scale);
		int titleHeight = scaled(45, scale);
		int bottomPadding = padding;
		int gapMapLegend = scaled(35, scale);

		int titleFontSize = scaledFontSize(18, scale);
		int legendTitleFontSize = scaledFontSize(13, scale);
		int legendLabelFontSize = scaledFontSize(12, scale);

		int rowHeight = scaled(22, scale);
		int swatchSize = scaled(14, scale);

		Font legendLabelFont = new Font("SansSerif", Font.PLAIN, legendLabelFontSize);

		int maxLabelWidth = 0;
		for (LegendEntry entry : options.entries) {
			maxLabelWidth = Math.max(maxLabelWidth, measureTextWidth(legendLabelFont, entry.label));
		}

		int colWidth = Math.max(
				scaled(180, scale),
				swatchSize + scaled(10, scale) + maxLabelWidth + scaled(30, scale)
		);

		int maxRowsPerColumn = Math.max(1, (mapHeight - scaled(40, scale)) / rowHeight);
		int columnCount = Math.max(1, (int) Math.ceil(options.entries.size() / (double) maxRowsPerColumn));

		int legendAreaWidth = Math.max(
				scaled(180, scale),
				columnCount * colWidth + scaled(20, scale)
		);

		int outputWidth = padding + mapWidth + gapMapLegend + legendAreaWidth + padding;
		int outputHeight = titleHeight + mapHeight + bottomPadding;

		BufferedImage image = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		// -------------------------------------------------
		// Background
		// -------------------------------------------------
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, outputWidth, outputHeight);

		// -------------------------------------------------
		// Map
		// -------------------------------------------------
		int mapX = padding;
		int mapY = titleHeight;

		image.setRGB(mapX, mapY, mapWidth, mapHeight, mapArgb, 0, mapWidth);

		g.setColor(new Color(180, 180, 180));
		g.drawRect(mapX, mapY, mapWidth, mapHeight);

		// -------------------------------------------------
		// Title
		// -------------------------------------------------
		g.setFont(new Font("SansSerif", Font.BOLD, titleFontSize));
		g.setColor(Color.BLACK);

		FontMetrics titleMetrics = g.getFontMetrics();
		int titleX = Math.max(padding, (outputWidth - titleMetrics.stringWidth(options.title)) / 2);
		int titleY = (titleHeight + titleMetrics.getAscent() - titleMetrics.getDescent()) / 2;

		g.drawString(options.title, titleX, titleY);

		// -------------------------------------------------
		// Legend
		// -------------------------------------------------
		int legendX = mapX + mapWidth + gapMapLegend;
		int legendY = mapY + scaled(12, scale);

		g.setFont(new Font("SansSerif", Font.BOLD, legendTitleFontSize));
		g.setColor(Color.BLACK);
		g.drawString("Legend", legendX, legendY);

		g.setFont(legendLabelFont);

		for (int i = 0; i < options.entries.size(); i++) {
			LegendEntry entry = options.entries.get(i);

			int col = i / maxRowsPerColumn;
			int row = i % maxRowsPerColumn;

			int x = legendX + col * colWidth;
			int y = legendY + scaled(18, scale) + row * rowHeight;

			// color box
			g.setColor(new Color(ensureOpaque(entry.argb), true));
			g.fillRect(x, y, swatchSize, swatchSize);

			g.setColor(Color.BLACK);
			g.drawRect(x, y, swatchSize, swatchSize);

			// label
			g.drawString(
					entry.label,
					x + swatchSize + scaled(8, scale),
					y + swatchSize - scaled(2, scale)
			);
		}

		g.dispose();

		int[] finalArgb = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		return new RenderedPng(outputWidth, outputHeight, finalArgb);
	}
	
	
	private static double legendScale(int mapWidth, int mapHeight) {
		int baseSize = Math.max(1, Math.min(mapWidth, mapHeight));

		// 1200 means: maps smaller than ~1200 px keep the old size.
		// Larger maps scale the legend/title/margins.
		return Math.max(1.0, Math.min(8.0, baseSize / 1200.0));
	}

	private static int scaled(int value, double scale) {
		return Math.max(1, (int) Math.round(value * scale));
	}

	private static int scaledFontSize(int baseSize, double scale) {
		return clampInt((int) Math.round(baseSize * scale), baseSize, 160);
	}

	private static int clampInt(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static int measureTextWidth(Font font, String text) {
		if (text == null || text.isEmpty()) {
			return 0;
		}

		BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = tmp.createGraphics();

		try {
			g.setFont(font);
			return g.getFontMetrics().stringWidth(text);
		} finally {
			g.dispose();
		}
	}

}
