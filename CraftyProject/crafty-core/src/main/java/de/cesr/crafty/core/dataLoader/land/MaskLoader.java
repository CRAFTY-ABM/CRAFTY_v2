package de.cesr.crafty.core.dataLoader.land;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.utils.file.CsvTools;

/** Discovers metadata-approved land-use masks and their restriction tables. */
public final class MaskLoader {

	private static final CustomLogger LOGGER = new CustomLogger(MaskLoader.class);
	private static final String METADATA_FILE = "LandUseControl-metadata.csv";
	private static final Pattern MASK_YEAR = Pattern.compile("(?i)Year[_-]?(\\d{4})");
	private static final Pattern ANY_YEAR = Pattern.compile("(?<!\\d)(\\d{4})(?!\\d)");

	public static Map<String, TreeMap<Integer, Path>> mask_paths = new LinkedHashMap<>();
	public static Map<String, TreeMap<Integer, Path>> restriction_paths = new LinkedHashMap<>();
	public static Map<String, Path> scenario_restriction_paths = new LinkedHashMap<>();
	public static Map<String, Path> default_restriction_paths = new LinkedHashMap<>();
	public static Map<String, MaskMetadata> mask_metadata = new LinkedHashMap<>();
	private static Path metadataPath;

	private MaskLoader() {
	}

	public static void initialize() {
		mask_paths = new LinkedHashMap<>();
		restriction_paths = new LinkedHashMap<>();
		scenario_restriction_paths = new LinkedHashMap<>();
		default_restriction_paths = new LinkedHashMap<>();
		mask_metadata = new LinkedHashMap<>();

		metadataPath = findMetadataPath();
		if (metadataPath == null || !loadMetadata(metadataPath)) {
			LOGGER.warn("LandUseControl-metadata.csv was not found or is invalid; land-use masks are disabled");
			return;
		}

		List<String> configured = ConfigLoader.config == null ? null : ConfigLoader.config.land_control_directories;
		if (configured != null && !configured.isEmpty()) {
			discoverConfigured(configured);
		} else {
			discoverAutomatically();
		}

		for (MaskMetadata metadata : orderedMetadata()) {
			String name = metadata.name();
			if (!mask_paths.containsKey(name) || mask_paths.get(name).isEmpty()) {
				LOGGER.warn("No mask files found for metadata entry: " + name);
				continue;
			}
			LOGGER.info("Land control " + name + " forced=" + metadata.forced() + " priority="
					+ metadata.priority() + " mask years=" + mask_paths.get(name).keySet() + " restriction years="
					+ restriction_paths.getOrDefault(name, new TreeMap<>()).keySet());
		}
	}

	private static Path findMetadataPath() {
		Path project = ProjectLoader.getProjectPath();
		if (project == null) {
			return null;
		}
		Path csvPath = project.resolve("csv").resolve(METADATA_FILE);
		if (Files.isRegularFile(csvPath)) {
			return csvPath;
		}
		if (ConfigLoader.config != null && ConfigLoader.config.metadata_directory != null
				&& !ConfigLoader.config.metadata_directory.isBlank()) {
			Path configured = Paths.get(ConfigLoader.config.metadata_directory).resolve(METADATA_FILE);
			if (Files.isRegularFile(configured)) {
				return configured;
			}
		}
		return null;
	}

	/** Loads and validates mask metadata. Exposed for focused loader tests. */
	public static boolean loadMetadata(Path path) {
		LinkedHashMap<String, MaskMetadata> loaded = new LinkedHashMap<>();
		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			String headerLine = reader.readLine();
			if (headerLine == null) {
				LOGGER.warn("Empty mask metadata file: " + path);
				return false;
			}
			String[] headers = CsvTools.parseCsvLine(headerLine);
			int nameIndex = headerIndex(headers, "name");
			int forcedIndex = headerIndex(headers, "isForced");
			int priorityIndex = headerIndex(headers, "Priority");
			if (nameIndex < 0 || forcedIndex < 0) {
				LOGGER.warn("Mask metadata must contain name and isForced columns: " + path);
				return false;
			}

			String line;
			int fileOrder = 0;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				String[] values = CsvTools.parseCsvLine(line);
				String name = value(values, nameIndex).trim();
				String forcedText = value(values, forcedIndex).trim();
				if (name.isEmpty()) {
					LOGGER.warn("Ignoring mask metadata row with an empty name at row " + (fileOrder + 2));
					fileOrder++;
					continue;
				}
				Boolean forced = parseBoolean(forcedText);
				if (forced == null) {
					LOGGER.warn("Ignoring mask metadata row with invalid isForced value '" + forcedText + "': " + name);
					fileOrder++;
					continue;
				}
				int priority = parsePriority(value(values, priorityIndex), fileOrder);
				String duplicate = findNameIgnoreCase(loaded, name);
				if (duplicate != null) {
					LOGGER.warn("Ignoring duplicate mask metadata entry: " + name);
					fileOrder++;
					continue;
				}
				loaded.put(name, new MaskMetadata(name, forced, priority, fileOrder));
				fileOrder++;
			}
		} catch (IOException e) {
			LOGGER.warn("Cannot read mask metadata " + path + ": " + e.getMessage());
			return false;
		}
		mask_metadata = loaded;
		metadataPath = path;
		return !loaded.isEmpty();
	}

	private static int headerIndex(String[] headers, String expected) {
		for (int i = 0; i < headers.length; i++) {
			String header = headers[i] == null ? "" : headers[i].replace("\ufeff", "").trim();
			if (header.equalsIgnoreCase(expected)) {
				return i;
			}
		}
		return -1;
	}

	private static String value(String[] values, int index) {
		return index >= 0 && index < values.length && values[index] != null ? values[index] : "";
	}

	private static Boolean parseBoolean(String value) {
		return switch (value.toLowerCase(Locale.ROOT)) {
		case "true", "1", "yes", "y" -> Boolean.TRUE;
		case "false", "0", "no", "n" -> Boolean.FALSE;
		default -> null;
		};
	}

	private static int parsePriority(String text, int fileOrder) {
		if (text == null || text.isBlank()) {
			return fileOrder + 1;
		}
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException e) {
			LOGGER.warn("Invalid mask priority '" + text + "'; using metadata row order " + (fileOrder + 1));
			return fileOrder + 1;
		}
	}

	private static String findNameIgnoreCase(Map<String, ?> values, String name) {
		return values.keySet().stream().filter(existing -> existing.equalsIgnoreCase(name)).findFirst().orElse(null);
	}

	public static List<MaskMetadata> orderedMetadata() {
		return mask_metadata.values().stream().sorted(MaskMetadata.PRIORITY_ORDER).toList();
	}

	public static MaskMetadata metadata(String maskName) {
		String key = findNameIgnoreCase(mask_metadata, maskName);
		return key == null ? null : mask_metadata.get(key);
	}

	private static void discoverConfigured(List<String> configuredDirectories) {
		for (String directoryText : configuredDirectories) {
			if (directoryText == null || directoryText.isBlank()) {
				continue;
			}
			Path directory = Paths.get(directoryText).normalize();
			MaskMetadata metadata = metadataForPath(directory);
			if (metadata == null) {
				LOGGER.warn("Configured land-control path does not match a metadata name and is ignored: " + directory);
				continue;
			}
			discoverMask(metadata.name(), directory);
		}
	}

	private static MaskMetadata metadataForPath(Path path) {
		for (int i = path.getNameCount() - 1; i >= 0; i--) {
			MaskMetadata metadata = metadata(path.getName(i).toString());
			if (metadata != null) {
				return metadata;
			}
		}
		return null;
	}

	private static void discoverAutomatically() {
		Path root = ProjectLoader.getProjectPath().resolve("worlds").resolve("LandUseControl");
		String scenario = ProjectLoader.getScenario();
		for (MaskMetadata metadata : orderedMetadata()) {
			Path maskRoot = root.resolve(metadata.name());
			Path maskFirstScenario = scenario == null ? maskRoot : maskRoot.resolve(scenario);
			Path scenarioFirstMask = scenario == null ? maskRoot : root.resolve(scenario).resolve(metadata.name());
			Path supplied = Files.isDirectory(maskFirstScenario) ? maskFirstScenario
					: Files.isDirectory(scenarioFirstMask) ? scenarioFirstMask : maskRoot;
			discoverMask(metadata.name(), supplied);
		}
	}

	private static void discoverMask(String maskName, Path suppliedPath) {
		Path namedMaskDirectory = findNamedAncestor(suppliedPath, maskName);
		Path landUseControlRoot = findNamedAncestor(suppliedPath, "LandUseControl");
		Path canonicalMaskRoot = landUseControlRoot == null ? null : landUseControlRoot.resolve(maskName);
		Path maskRoot = canonicalMaskRoot != null && Files.isDirectory(canonicalMaskRoot) ? canonicalMaskRoot
				: namedMaskDirectory;
		if (maskRoot == null) {
			maskRoot = suppliedPath;
		}
		Path scenarioDirectory = resolveScenarioDirectory(suppliedPath, maskRoot, landUseControlRoot, maskName);
		TreeMap<Integer, Path> masks = findFiles(scenarioDirectory, false);
		RestrictionFiles restrictions = discoverRestrictionFiles(maskRoot, scenarioDirectory,
				ProjectLoader.getScenario());
		mask_paths.put(maskName, masks);
		restriction_paths.put(maskName, restrictions.yearly());
		if (restrictions.scenario() != null) {
			scenario_restriction_paths.put(maskName, restrictions.scenario());
		}
		if (restrictions.defaultFile() != null) {
			default_restriction_paths.put(maskName, restrictions.defaultFile());
		}
	}

	private static Path findNamedAncestor(Path path, String maskName) {
		Path current = path;
		while (current != null) {
			Path fileName = current.getFileName();
			if (fileName != null && fileName.toString().equalsIgnoreCase(maskName)) {
				return current;
			}
			current = current.getParent();
		}
		return null;
	}

	private static Path resolveScenarioDirectory(Path suppliedPath, Path maskRoot, Path landUseControlRoot,
			String maskName) {
		if (!suppliedPath.equals(maskRoot) && Files.isDirectory(suppliedPath)) {
			return suppliedPath;
		}
		String scenario = ProjectLoader.getScenario();
		if (scenario != null) {
			Path scenarioBelowRoot = maskRoot.resolve(scenario);
			if (Files.isDirectory(scenarioBelowRoot)) {
				return scenarioBelowRoot;
			}
			if (landUseControlRoot != null) {
				Path scenarioAboveMask = landUseControlRoot.resolve(scenario).resolve(maskName);
				if (Files.isDirectory(scenarioAboveMask)) {
					return scenarioAboveMask;
				}
			}
		}
		return suppliedPath;
	}

	private static TreeMap<Integer, Path> findFiles(Path root, boolean restrictions) {
		TreeMap<Integer, Path> result = new TreeMap<>();
		if (root == null || !Files.isDirectory(root)) {
			return result;
		}
		try (Stream<Path> files = Files.walk(root)) {
			files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
					.endsWith(".csv")).forEach(path -> indexFile(result, path, restrictions));
		} catch (IOException e) {
			LOGGER.warn("Cannot scan land-control directory " + root + ": " + e.getMessage());
		}
		return result;
	}

	/**
	 * Finds restriction files without entering sibling scenario directories. Files in
	 * the selected scenario override files from the mask root at the same tier.
	 */
	static RestrictionFiles discoverRestrictionFiles(Path maskRoot, Path scenarioDirectory, String scenarioName) {
		RestrictionFiles result = new RestrictionFiles(new TreeMap<>(), null, null);
		result = scanRestrictionDirectory(maskRoot, false, scenarioName, result);
		if (scenarioDirectory != null && !scenarioDirectory.equals(maskRoot)) {
			result = scanRestrictionDirectory(scenarioDirectory, true, scenarioName, result);
		}
		return result;
	}

	private static RestrictionFiles scanRestrictionDirectory(Path directory, boolean recursive, String scenarioName,
			RestrictionFiles initial) {
		if (directory == null || !Files.isDirectory(directory)) {
			return initial;
		}
		TreeMap<Integer, Path> yearly = new TreeMap<>(initial.yearly());
		Path scenario = initial.scenario();
		Path defaultFile = initial.defaultFile();
		try (Stream<Path> stream = recursive ? Files.walk(directory) : Files.list(directory)) {
			List<Path> files = stream.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
					.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).contains("restriction"))
					.sorted().toList();
			for (Path path : files) {
				String fileName = path.getFileName().toString();
				Matcher yearMatcher = ANY_YEAR.matcher(fileName);
				if (yearMatcher.find()) {
					int year = Integer.parseInt(yearMatcher.group(1));
					warnReplacement("year " + year, yearly.put(year, path), path);
				} else if (fileName.toLowerCase(Locale.ROOT).contains("default")) {
					warnReplacement("default", defaultFile, path);
					defaultFile = path;
				} else if (recursive || containsIgnoreCase(fileName, scenarioName)) {
					warnReplacement("scenario " + scenarioName, scenario, path);
					scenario = path;
				}
			}
		} catch (IOException e) {
			LOGGER.warn("Cannot scan restriction directory " + directory + ": " + e.getMessage());
		}
		return new RestrictionFiles(yearly, scenario, defaultFile);
	}

	private static boolean containsIgnoreCase(String value, String expected) {
		return expected != null && !expected.isBlank()
				&& value.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
	}

	private static void warnReplacement(String tier, Path previous, Path replacement) {
		if (previous != null && !previous.equals(replacement)) {
			LOGGER.warn("Multiple restriction files found for " + tier + "; using " + replacement + " instead of "
					+ previous);
		}
	}

	/** Resolves the documented precedence: exact year, scenario, then default. */
	public static Path resolveRestrictionPath(String maskName, int year) {
		TreeMap<Integer, Path> yearly = restriction_paths.get(maskName);
		Path exact = yearly == null ? null : yearly.get(year);
		if (exact != null) {
			return exact;
		}
		Path scenario = scenario_restriction_paths.get(maskName);
		return scenario != null ? scenario : default_restriction_paths.get(maskName);
	}

	private static void indexFile(TreeMap<Integer, Path> result, Path path, boolean restrictions) {
		String fileName = path.getFileName().toString();
		boolean isRestriction = fileName.toLowerCase(Locale.ROOT).contains("restriction");
		if (restrictions != isRestriction) {
			return;
		}
		if (restrictions && fileName.toLowerCase(Locale.ROOT).contains("default")) {
			result.putIfAbsent(0, path);
			return;
		}
		Matcher matcher = (restrictions ? ANY_YEAR : MASK_YEAR).matcher(fileName);
		if (matcher.find()) {
			result.putIfAbsent(Integer.parseInt(matcher.group(1)), path);
		}
	}

	public static Path getMetadataPath() {
		return metadataPath;
	}

	static record RestrictionFiles(TreeMap<Integer, Path> yearly, Path scenario, Path defaultFile) {
	}
}
