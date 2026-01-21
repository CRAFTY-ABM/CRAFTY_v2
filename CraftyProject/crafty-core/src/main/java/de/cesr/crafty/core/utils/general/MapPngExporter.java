package de.cesr.crafty.core.utils.general;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.output.Listener;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.PathTools;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

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
	public static void exportOwnerMapAsPng() {
		// Headless-safe (recommended for HPC)
		System.setProperty("java.awt.headless", "true");

		// For now, reuse your year-filter logic and just run when years match.
		if (ConfigLoader.config == null)
			return;

		int year = Timestep.getCurrentYear();
		if (!Listener.yearsMapExporting.contains(year))
			return;

		String outDir = PathTools.makeDirectory(ConfigLoader.config.output_folder_name + File.separator + "MapsPlots");

		int width = CellsLoader.maxX;
		int height = CellsLoader.maxY;

		// ARGB pixels (top-left origin, row-major). Default = transparent.
		int[] argb = new int[width * height];

		// Fill pixels from cells
		CellsLoader.hashCell.values().forEach(c -> {
			if (c == null || c.getOwner() == null)
				return;

			int x = c.getX();
			int y = c.getY();
			if (x < 0 || y < 0 || x >= width || y >= height)
				return;

			int rgb = parseRgbOrDefault(c.getOwner().getColor(), 0x000000);
			int a = 0xFF;
			int pixel = (a << 24) | (rgb & 0xFFFFFF);
			argb[y * width + x] = pixel;
		});

		File out = new File(outDir + File.separator + "map_" + year + ".png");
		ensureParentDir(out);

		try {
			PngWriter.writeRgbaPng(out, width, height, argb);
			LOGGER.info("PNG written to: " + out.getAbsolutePath());
		} catch (IOException e) {
			LOGGER.warn("Failed to write PNG: " + e.getMessage());
			e.printStackTrace();
		}
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

	private static void ensureParentDir(File f) {
		File p = f.getParentFile();
		if (p != null && !p.exists()) {
			// noinspection ResultOfMethodCallIgnored
			p.mkdirs();
		}
	}
}
