package de.cesr.crafty.core.utils.analysis;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.main.MainHeadless;
import de.cesr.crafty.core.utils.file.CsvTools;

/**
 * Creates a lower-resolution copy of a CRAFTY data project.
 *
 * CSV files containing X and Y columns are treated as maps. Continuous values
 * are averaged, while categorical values use a local mode adjusted to retain
 * their global percentage distribution. Other files are copied unchanged.
 */
public class CraftyDataUpscaler {
	static double scale = 15;
	private static final int TYPE_INFERENCE_ROWS = 100;

	private enum ColumnType {
		COORDINATE, IDENTIFIER, CONTINUOUS, CATEGORICAL
	}

	public static void main(String[] args) {
		System.out.println("-- Starting CRAFTY data upscaler --");
		MainHeadless.initializeConfig(args);
		ProjectLoader.pathInitialisation(Paths.get(ConfigLoader.config.project_path));

		Path source = ProjectLoader.getProjectPath().toAbsolutePath().normalize();
		Path destination = source.resolveSibling(source.getFileName() + "_upscaled_" + formatNumber(scale));
		try {
			UpscaleSummary summary = upscaleProject(source, destination, scale);
			System.out.println("Upscaled project: " + destination);
			System.out.println("Maps upscaled: " + summary.mapsUpscaled() + ", files copied: "
					+ summary.filesCopied() + ", output folders skipped: " + summary.outputFoldersSkipped());
		} catch (IOException e) {
			throw new IllegalStateException("Unable to upscale project " + source, e);
		}
	}

	/**
	 * Mirrors a project into {@code destination}, excluding output directories.
	 */
	public static UpscaleSummary upscaleProject(Path source, Path destination, double factor) throws IOException {
		validatePaths(source, destination, factor);
		Files.createDirectories(destination);
		MutableSummary summary = new MutableSummary();

		Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				if (!dir.equals(source) && isOutputDirectory(dir)) {
					summary.outputFoldersSkipped++;
					return FileVisitResult.SKIP_SUBTREE;
				}
				Files.createDirectories(destination.resolve(source.relativize(dir)));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Path target = destination.resolve(source.relativize(file));
				if (isCsv(file) && isMapCsv(file)) {
					upscaleCsvMap(file, target, factor);
					summary.mapsUpscaled++;
				} else {
//					System.out.println("copy file: " +file);
					Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING,
							StandardCopyOption.COPY_ATTRIBUTES);
					summary.filesCopied++;
				}
				return FileVisitResult.CONTINUE;
			}
		});

		return new UpscaleSummary(summary.mapsUpscaled, summary.filesCopied, summary.outputFoldersSkipped);
	}

	/* Package-level compatibility helpers used by SplitByRegions. */
	static void createDataTemplate(String destinationPath) {
		Path source = ProjectLoader.getProjectPath();
		Path destination = Paths.get(destinationPath);
		for (String directory : List.of("AFTs", "csv", "services")) {
			Path child = source.resolve(directory);
			if (Files.isDirectory(child)) {
				try {
					copyDirectory(child, destination.resolve(directory));
				} catch (IOException e) {
					throw new IllegalStateException("Unable to copy " + child, e);
				}
			}
		}
	}

	static String switchPaths(String path, String outputFolderPath) {
		return path.replace(ProjectLoader.getProjectPath().toString(), outputFolderPath);
	}

	private static void copyDirectory(Path source, Path destination) throws IOException {
		Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
				Files.createDirectories(destination.resolve(source.relativize(directory)));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.copy(file, destination.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.COPY_ATTRIBUTES);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	static boolean isMapCsv(Path csv) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
			String headerLine = reader.readLine();
			if (headerLine == null) {
				return false;
			}
			String[] headers = CsvTools.parseCsvLine(stripBom(headerLine));
			return findColumn(headers, "X") >= 0 && findColumn(headers, "Y") >= 0;
		}
	}

	static void upscaleCsvMap(Path input, Path output, double factor) throws IOException {
		System.out.print("upscal csv map:"+ input+"...");
		validateFactor(factor);
		String[] headers;
		List<String[]> sampleRows = new ArrayList<>();
		try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
			String headerLine = reader.readLine();
			if (headerLine == null) {
				Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
				return;
			}
			headers = CsvTools.parseCsvLine(stripBom(headerLine));
			String line;
			while (sampleRows.size() < TYPE_INFERENCE_ROWS && (line = reader.readLine()) != null) {
				if (!line.isBlank()) {
					sampleRows.add(normalizeRow(CsvTools.parseCsvLine(line), headers.length));
				}
			}
		}

		int xColumn = findColumn(headers, "X");
		int yColumn = findColumn(headers, "Y");
		if (xColumn < 0 || yColumn < 0) {
			throw new IllegalArgumentException("Map CSV must contain X and Y columns: " + input);
		}

		ColumnType[] columnTypes = inferColumnTypes(headers, sampleRows, xColumn, yColumn);
		Map<GridCoordinate, CellAggregate> cells = new HashMap<>();
		List<Map<String, Long>> globalCategoryCounts = categoryCountMaps(columnTypes);

		try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
			reader.readLine();
			String line;
			long lineNumber = 1;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				if (line.isBlank()) {
					continue;
				}
				String[] row = normalizeRow(CsvTools.parseCsvLine(line), headers.length);
				double sourceX = parseCoordinate(row[xColumn], input, lineNumber, headers[xColumn]);
				double sourceY = parseCoordinate(row[yColumn], input, lineNumber, headers[yColumn]);
				int targetX = scaledCoordinate(sourceX, factor, input, lineNumber);
				int targetY = scaledCoordinate(sourceY, factor, input, lineNumber);
				GridCoordinate coordinate = new GridCoordinate(targetX, targetY);
				CellAggregate cell = cells.computeIfAbsent(coordinate,
						ignored -> new CellAggregate(coordinate, headers.length, columnTypes));
				cell.add(row, sourceX, sourceY, factor, columnTypes, globalCategoryCounts);
			}
		}

		for (int column = 0; column < headers.length; column++) {
			if (columnTypes[column] == ColumnType.CATEGORICAL) {
				assignCategoricalValues(cells.values(), column, globalCategoryCounts.get(column));
			}
		}

		Files.createDirectories(output.toAbsolutePath().normalize().getParent());
		List<CellAggregate> sortedCells = new ArrayList<>(cells.values());
		sortedCells.sort(Comparator.comparingInt((CellAggregate cell) -> cell.coordinate.x())
				.thenComparingInt(cell -> cell.coordinate.y()));
		writeMap(output, headers, columnTypes, xColumn, yColumn, sortedCells);
		System.out.println("done");
	}

	private static void assignCategoricalValues(Iterable<CellAggregate> cells, int column,
			Map<String, Long> sourceCounts) {
		List<CellAggregate> populated = new ArrayList<>();
		for (CellAggregate cell : cells) {
			if (!cell.categoryCounts[column].isEmpty()) {
				cell.assignedCategories[column] = cell.localMode(column);
				populated.add(cell);
			}
		}
		if (populated.isEmpty()) {
			return;
		}

		Map<String, Integer> targets = distributionTargets(sourceCounts, populated.size());
		Map<String, Integer> assignedCounts = new HashMap<>();
		for (CellAggregate cell : populated) {
			assignedCounts.merge(cell.assignedCategories[column], 1, Integer::sum);
		}

		for (Map.Entry<String, Integer> target : targets.entrySet()) {
			String neededCategory = target.getKey();
			int deficit = target.getValue() - assignedCounts.getOrDefault(neededCategory, 0);
			if (deficit <= 0) {
				continue;
			}

			List<CellAggregate> candidates = new ArrayList<>();
			for (CellAggregate cell : populated) {
				String current = cell.assignedCategories[column];
				if (!neededCategory.equals(current)
						&& assignedCounts.getOrDefault(current, 0) > targets.getOrDefault(current, 0)) {
					candidates.add(cell);
				}
			}
			candidates.sort(Comparator
					.comparingInt((CellAggregate cell) -> cell.categoryCount(column, neededCategory) > 0 ? -1 : 0)
					.thenComparingInt(cell -> -cell.categoryAdvantage(column, neededCategory))
					.thenComparingInt(cell -> cell.coordinate.x())
					.thenComparingInt(cell -> cell.coordinate.y()));

			for (CellAggregate cell : candidates) {
				if (deficit == 0) {
					break;
				}
				String current = cell.assignedCategories[column];
				if (assignedCounts.getOrDefault(current, 0) <= targets.getOrDefault(current, 0)) {
					continue;
				}
				cell.assignedCategories[column] = neededCategory;
				assignedCounts.merge(current, -1, Integer::sum);
				assignedCounts.merge(neededCategory, 1, Integer::sum);
				deficit--;
			}
		}
	}

	private static Map<String, Integer> distributionTargets(Map<String, Long> sourceCounts, int targetSize) {
		List<String> categories = new ArrayList<>(sourceCounts.keySet());
		categories.sort(String::compareTo);
		long sourceTotal = sourceCounts.values().stream().mapToLong(Long::longValue).sum();
		Map<String, Double> exact = new LinkedHashMap<>();
		Map<String, Integer> targets = new LinkedHashMap<>();

		for (String category : categories) {
			double expected = sourceTotal == 0 ? 0 : sourceCounts.get(category) * (double) targetSize / sourceTotal;
			exact.put(category, expected);
			targets.put(category, (int) Math.floor(expected));
		}
		if (targetSize >= categories.size()) {
			for (String category : categories) {
				if (targets.get(category) == 0 && sourceCounts.get(category) > 0) {
					targets.put(category, 1);
				}
			}
		}

		while (sum(targets) < targetSize) {
			String category = categories.stream()
					.max(Comparator.comparingDouble(c -> exact.get(c) - targets.get(c)))
					.orElseThrow();
			targets.merge(category, 1, Integer::sum);
		}
		while (sum(targets) > targetSize) {
			String category = categories.stream().filter(c -> targets.get(c) > 1)
					.max(Comparator.comparingDouble(c -> targets.get(c) - exact.get(c)))
					.orElseGet(() -> categories.stream().filter(c -> targets.get(c) > 0)
							.max(Comparator.comparingInt(targets::get)).orElseThrow());
			targets.merge(category, -1, Integer::sum);
		}
		return targets;
	}

	private static int sum(Map<String, Integer> values) {
		return values.values().stream().mapToInt(Integer::intValue).sum();
	}

	private static void writeMap(Path output, String[] headers, ColumnType[] columnTypes, int xColumn,
			int yColumn, List<CellAggregate> cells) throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
			writeCsvRow(writer, headers);
			for (CellAggregate cell : cells) {
				String[] row = new String[headers.length];
				for (int column = 0; column < headers.length; column++) {
					if (column == xColumn) {
						row[column] = Integer.toString(cell.coordinate.x());
					} else if (column == yColumn) {
						row[column] = Integer.toString(cell.coordinate.y());
					} else if (columnTypes[column] == ColumnType.IDENTIFIER) {
						row[column] = cell.representativeRow == null ? "" : cell.representativeRow[column];
					} else if (columnTypes[column] == ColumnType.CONTINUOUS) {
						row[column] = cell.numericCounts[column] == 0 ? ""
								: formatNumber(cell.numericSums[column] / cell.numericCounts[column]);
					} else if (columnTypes[column] == ColumnType.CATEGORICAL) {
						row[column] = cell.assignedCategories[column] == null ? ""
								: cell.assignedCategories[column];
					} else {
						row[column] = "";
					}
				}
				writeCsvRow(writer, row);
			}
		}
	}

	private static void writeCsvRow(BufferedWriter writer, String[] row) throws IOException {
		for (int column = 0; column < row.length; column++) {
			if (column > 0) {
				writer.write(',');
			}
			writer.write(escapeCsv(row[column]));
		}
		writer.newLine();
	}

	private static String escapeCsv(String value) {
		String safe = value == null ? "" : value;
		if (safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0
				|| safe.indexOf('\r') >= 0) {
			return '"' + safe.replace("\"", "\"\"") + '"';
		}
		return safe;
	}

	private static ColumnType[] inferColumnTypes(String[] headers, List<String[]> rows, int xColumn, int yColumn) {
		ColumnType[] types = new ColumnType[headers.length];
		for (int column = 0; column < headers.length; column++) {
			if (column == xColumn || column == yColumn) {
				types[column] = ColumnType.COORDINATE;
			} else if (isIdentifierColumn(headers[column])) {
				types[column] = ColumnType.IDENTIFIER;
			} else if (isCategoricalColumn(headers[column]) || containsNonNumericValue(rows, column)) {
				types[column] = ColumnType.CATEGORICAL;
			} else {
				types[column] = ColumnType.CONTINUOUS;
			}
		}
		return types;
	}

	private static boolean containsNonNumericValue(List<String[]> rows, int column) {
		for (String[] row : rows) {
			String value = row[column] == null ? "" : row[column].trim();
			if (!value.isEmpty() && tryParseDouble(value) == null) {
				return true;
			}
		}
		return false;
	}

	private static boolean isIdentifierColumn(String header) {
		String normalized = normalizeHeader(header);
		return normalized.equals("id") || normalized.equals("cellid") || normalized.equals("c0")
				|| normalized.equals("v1");
	}

	private static boolean isCategoricalColumn(String header) {
		String normalized = normalizeHeader(header);
		return normalized.equals("owner") || normalized.equals("agent") || normalized.equals("agents")
				|| normalized.equals("aft") || normalized.equals("fr") || normalized.equals("regioncode")
				|| normalized.startsWith("year");
	}

	private static String normalizeHeader(String header) {
		return stripBom(header).trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
	}

	private static List<Map<String, Long>> categoryCountMaps(ColumnType[] types) {
		List<Map<String, Long>> counts = new ArrayList<>(types.length);
		for (ColumnType type : types) {
			counts.add(type == ColumnType.CATEGORICAL ? new HashMap<>() : null);
		}
		return counts;
	}

	private static int findColumn(String[] headers, String expected) {
		for (int column = 0; column < headers.length; column++) {
			if (stripBom(headers[column]).trim().equalsIgnoreCase(expected)) {
				return column;
			}
		}
		return -1;
	}

	private static String[] normalizeRow(String[] row, int width) {
		return row.length == width ? row : Arrays.copyOf(row, width);
	}

	private static double parseCoordinate(String value, Path input, long line, String column) throws IOException {
		Double coordinate = tryParseDouble(value);
		if (coordinate == null || !Double.isFinite(coordinate)) {
			throw new IOException("Invalid " + column + " coordinate at " + input + ":" + line + " -> " + value);
		}
		return coordinate;
	}

	private static Double tryParseDouble(String value) {
		try {
			return Double.valueOf(value.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static int scaledCoordinate(double coordinate, double factor, Path input, long line) throws IOException {
		double scaled = Math.floor(coordinate / factor);
		if (scaled < Integer.MIN_VALUE || scaled > Integer.MAX_VALUE) {
			throw new IOException("Scaled coordinate is outside the integer range at " + input + ":" + line);
		}
		return (int) scaled;
	}

	private static boolean isCsv(Path file) {
		return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv");
	}

	private static boolean isOutputDirectory(Path directory) {
		return directory.getFileName() != null && directory.getFileName().toString().equalsIgnoreCase("output");
	}

	private static void validatePaths(Path source, Path destination, double factor) throws IOException {
		validateFactor(factor);
		Path normalizedSource = source.toAbsolutePath().normalize();
		Path normalizedDestination = destination.toAbsolutePath().normalize();
		if (!Files.isDirectory(normalizedSource)) {
			throw new IllegalArgumentException("Source project does not exist or is not a directory: " + source);
		}
		if (normalizedDestination.startsWith(normalizedSource)) {
			throw new IllegalArgumentException("Destination must not be inside the source project: " + destination);
		}
	}

	private static void validateFactor(double factor) {
		if (!Double.isFinite(factor) || factor <= 1.0) {
			throw new IllegalArgumentException("Upscaling factor must be finite and greater than 1: " + factor);
		}
	}

	private static String stripBom(String value) {
		return value != null && !value.isEmpty() && value.charAt(0) == '\ufeff' ? value.substring(1) : value;
	}

	private static String formatNumber(double value) {
		return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
	}

	public record UpscaleSummary(int mapsUpscaled, int filesCopied, int outputFoldersSkipped) {
	}

	private record GridCoordinate(int x, int y) {
	}

	private static final class MutableSummary {
		private int mapsUpscaled;
		private int filesCopied;
		private int outputFoldersSkipped;
	}

	private static final class CellAggregate {
		private final GridCoordinate coordinate;
		private final double[] numericSums;
		private final int[] numericCounts;
		private final Map<String, Integer>[] categoryCounts;
		private final String[] assignedCategories;
		private String[] representativeRow;
		private double representativeDistance = Double.POSITIVE_INFINITY;
		private double representativeX;
		private double representativeY;

		@SuppressWarnings("unchecked")
		private CellAggregate(GridCoordinate coordinate, int columns, ColumnType[] types) {
			this.coordinate = coordinate;
			this.numericSums = new double[columns];
			this.numericCounts = new int[columns];
			this.categoryCounts = (Map<String, Integer>[]) new Map<?, ?>[columns];
			this.assignedCategories = new String[columns];
			for (int column = 0; column < columns; column++) {
				if (types[column] == ColumnType.CATEGORICAL) {
					categoryCounts[column] = new HashMap<>();
				}
			}
		}

		private void add(String[] row, double sourceX, double sourceY, double factor, ColumnType[] types,
				List<Map<String, Long>> globalCategoryCounts) throws IOException {
			selectRepresentative(row, sourceX, sourceY, factor);
			for (int column = 0; column < row.length; column++) {
				String value = row[column] == null ? "" : row[column].trim();
				if (value.isEmpty()) {
					continue;
				}
				if (types[column] == ColumnType.CONTINUOUS) {
					Double number = tryParseDouble(value);
					if (number == null || !Double.isFinite(number)) {
						throw new IOException("Non-numeric value in continuous column: " + value);
					}
					numericSums[column] += number;
					numericCounts[column]++;
				} else if (types[column] == ColumnType.CATEGORICAL) {
					categoryCounts[column].merge(value, 1, Integer::sum);
					globalCategoryCounts.get(column).merge(value, 1L, Long::sum);
				}
			}
		}

		private void selectRepresentative(String[] row, double sourceX, double sourceY, double factor) {
			double centreX = (coordinate.x() + 0.5) * factor;
			double centreY = (coordinate.y() + 0.5) * factor;
			double distance = Math.pow(sourceX - centreX, 2) + Math.pow(sourceY - centreY, 2);
			if (representativeRow == null || distance < representativeDistance
					|| (distance == representativeDistance && (sourceX < representativeX
							|| (sourceX == representativeX && sourceY < representativeY)))) {
				representativeRow = row.clone();
				representativeDistance = distance;
				representativeX = sourceX;
				representativeY = sourceY;
			}
		}

		private String localMode(int column) {
			Map<String, Integer> counts = categoryCounts[column];
			int maximum = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
			String representative = representativeRow == null ? null : representativeRow[column];
			if (representative != null && counts.getOrDefault(representative.trim(), 0) == maximum) {
				return representative.trim();
			}
			return counts.entrySet().stream().filter(entry -> entry.getValue() == maximum).map(Map.Entry::getKey)
					.min(String::compareTo).orElse("");
		}

		private int categoryCount(int column, String category) {
			return categoryCounts[column].getOrDefault(category, 0);
		}

		private int categoryAdvantage(int column, String category) {
			return categoryCount(column, category) - categoryCount(column, assignedCategories[column]);
		}
	}
}
