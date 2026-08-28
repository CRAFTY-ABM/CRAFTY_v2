package de.cesr.crafty.core.updaters;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.crafty.Competitiveness;
import de.cesr.crafty.core.crafty.RegionalModelRunner;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.afts.AftCategorised;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.land.MaskLoader;
import de.cesr.crafty.core.dataLoader.land.MaskMetadata;
import de.cesr.crafty.core.output.Tracker;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.general.DeterministicRandom;
import de.cesr.crafty.core.utils.general.Utils;

/** Applies metadata-approved land-use masks, priorities, and restrictions. */
public class LandMaskUpdater extends AbstractUpdater {

	private static final CustomLogger LOGGER = new CustomLogger(LandMaskUpdater.class);

	public static ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>> restrictions = new ConcurrentHashMap<>();
	public static ConcurrentHashMap<String, ConcurrentHashMap<String, Set<Cell>>> cellsMasked = new ConcurrentHashMap<>();
	public static ConcurrentHashMap<String, ConcurrentHashMap<String, Cell>> cellsForecedToChange = new ConcurrentHashMap<>();
	static ConcurrentHashMap<String, List<String>> forcedAllowedAftLabels = new ConcurrentHashMap<>();
	private static final Set<String> warnedForcedMasks = ConcurrentHashMap.newKeySet();
	private static final ConcurrentHashMap<String, Path> loadedRestrictionPaths = new ConcurrentHashMap<>();

	private void initializeCellsMasked() {
		cellsMasked.clear();
		CellsLoader.regions.keySet().forEach(region -> {
			cellsMasked.put(region, new ConcurrentHashMap<>());
			AftCategorised.aftCategories.forEach((categoryName, afts) -> cellsMasked.get(region).put(categoryName,
					Collections.synchronizedSet(new HashSet<>())));
		});
	}

	public LandMaskUpdater() {
		MaskLoader.initialize();
		initializeCellsMasked();
		restrictions.clear();
		forcedAllowedAftLabels.clear();
		cellsForecedToChange.clear();
		warnedForcedMasks.clear();
		loadedRestrictionPaths.clear();

		for (MaskMetadata metadata : MaskLoader.orderedMetadata()) {
			String maskName = metadata.name();
			cellsForecedToChange.put(maskName, new ConcurrentHashMap<>());
			Path initial = MaskLoader.resolveRestrictionPath(maskName, Timestep.getStartYear());
			if (initial != null) {
				loadRestriction(maskName, initial);
			} else {
				LOGGER.warn("No year, scenario, or default restriction file found for: " + maskName);
			}
		}
	}

	@Override
	public void toSchedule() {
		modelRunner.scheduleRepeating(this);
	}

	@Override
	public void step() {
		initializeCellsMasked();
		for (MaskMetadata metadata : MaskLoader.orderedMetadata()) {
			updateRestrections(metadata.name());
		}
		applyMasks(Timestep.getCurrentYear());
	}

	public static void updateRestrections(String maskType) {
		Path updated = MaskLoader.resolveRestrictionPath(maskType, Timestep.getCurrentYear());
		if (updated == null) {
			LOGGER.warn("No restriction file found: " + maskType);
			return;
		}
		if (!updated.equals(loadedRestrictionPaths.get(maskType))) {
			loadRestriction(maskType, updated);
			LOGGER.info("Restriction updated for " + maskType + ": " + updated);
		}
	}

	private static void loadRestriction(String maskType, Path path) {
		RestrictionImport imported = importRestriction(path);
		if (imported == null) {
			return;
		}
		restrictions.put(maskType, imported.values());
		loadedRestrictionPaths.put(maskType, path);
		MaskMetadata metadata = MaskLoader.metadata(maskType);
		if (metadata == null || !metadata.forced()) {
			forcedAllowedAftLabels.remove(maskType);
			return;
		}
		if (!imported.allowedTargets().isEmpty()) {
			forcedAllowedAftLabels.put(maskType, imported.allowedTargets().stream().sorted().toList());
			warnedForcedMasks.remove(maskType);
		} else {
			forcedAllowedAftLabels.remove(maskType);
			LOGGER.warn("Forced mask " + maskType + " has no allowed target AFT in its restriction map");
		}
	}

	private static RestrictionImport importRestriction(Path path) {
		LOGGER.info("Import restriction: " + path);
		String[][] matrix = CsvTools.csvReader(path);
		if (matrix == null || matrix.length < 2 || matrix[0].length < 2) {
			LOGGER.warn("Invalid restriction matrix: " + path);
			return null;
		}
		ConcurrentHashMap<String, Boolean> values = new ConcurrentHashMap<>();
		Set<String> allowedTargets = new LinkedHashSet<>();
		for (int row = 1; row < matrix.length; row++) {
			if (matrix[row] == null || matrix[row].length == 0) {
				continue;
			}
			for (int column = 1; column < matrix[0].length; column++) {
				if (column >= matrix[row].length) {
					continue;
				}
				boolean allowed = isActive(matrix[row][column]);
				values.put(matrix[row][0] + "_" + matrix[0][column], allowed);
				if (allowed) {
					allowedTargets.add(matrix[0][column]);
				}
			}
		}
		return new RestrictionImport(values, allowedTargets);
	}

	/** Resolves all active masks together so overlap priority is deterministic. */
	public static void applyMasks(int year) {
		List<MaskMetadata> ordered = MaskLoader.orderedMetadata();
		if (ordered.isEmpty()) {
			return;
		}

		Map<Cell, MaskMetadata> winners = new LinkedHashMap<>();
		for (MaskMetadata metadata : ordered) {
			Path path = maskPath(metadata.name(), year);
			if (path == null) {
				LOGGER.info("No mask file available for " + metadata.name() + " at year " + year);
				continue;
			}
			for (Cell cell : readActiveCells(path)) {
				winners.putIfAbsent(cell, metadata);
			}
		}

		Set<String> managedNames = new HashSet<>(MaskLoader.mask_metadata.keySet());
		for (Cell cell : CellsLoader.hashCell.values()) {
			if (containsIgnoreCase(managedNames, cell.getMaskType())) {
				cell.setMaskType(null);
			}
		}

		for (Map.Entry<Cell, MaskMetadata> winner : winners.entrySet()) {
			Cell cell = winner.getKey();
			MaskMetadata metadata = winner.getValue();
			cell.setMaskType(metadata.name());
			if (metadata.forced()) {
				forceSingleAllowedOwner(cell, metadata.name());
			}
		}
		LOGGER.info("Applied " + winners.size() + " prioritized mask assignments for year " + year);
	}

	private static Path maskPath(String maskType, int year) {
		TreeMap<Integer, Path> paths = MaskLoader.mask_paths.get(maskType);
		if (paths == null || paths.isEmpty()) {
			return null;
		}
		Path exact = paths.get(year);
		if (exact != null) {
			return exact;
		}
		Map.Entry<Integer, Path> latest = paths.floorEntry(year);
		return latest == null ? null : latest.getValue();
	}

	private static Set<Cell> readActiveCells(Path path) {
		Set<Cell> activeCells = new LinkedHashSet<>();
		if (!Files.isRegularFile(path)) {
			LOGGER.warn("Cannot find mask file: " + path);
			return activeCells;
		}
		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			String headerLine = reader.readLine();
			if (headerLine == null) {
				LOGGER.warn("Empty mask file: " + path);
				return activeCells;
			}
			String[] headers = CsvTools.parseCsvLine(headerLine);
			int xIndex = -1;
			int yIndex = -1;
			List<Integer> yearIndexes = new ArrayList<>();
			for (int i = 0; i < headers.length; i++) {
				String header = headers[i] == null ? "" : headers[i].trim();
				if ("X".equalsIgnoreCase(header)) {
					xIndex = i;
				} else if ("Y".equalsIgnoreCase(header)) {
					yIndex = i;
				} else if (header.regionMatches(true, 0, "Year_", 0, 5)) {
					yearIndexes.add(i);
				}
			}
			if (xIndex < 0 || yIndex < 0) {
				LOGGER.error("Mask CSV missing X or Y columns: " + path);
				return activeCells;
			}
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				String[] values = CsvTools.parseCsvLine(line);
				if (xIndex >= values.length || yIndex >= values.length || !active(values, yearIndexes)) {
					continue;
				}
				int x = (int) Utils.sToD(values[xIndex]);
				int y = (int) Utils.sToD(values[yIndex]);
				Cell cell = CellsLoader.getCell(x, y);
				if (cell != null) {
					activeCells.add(cell);
				}
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Error reading mask file " + path + ": " + e.getMessage());
		}
		return activeCells;
	}

	private static boolean active(String[] values, List<Integer> indexes) {
		for (int index : indexes) {
			if (index < values.length && isActive(values[index])) {
				return true;
			}
		}
		return false;
	}

	private static boolean isActive(String value) {
		if (value == null) {
			return false;
		}
		String normalized = value.trim();
		return normalized.equals("1") || normalized.equals("1.0") || normalized.equalsIgnoreCase("true");
	}

	/** Applies one mask for compatibility with existing callers and focused tests. */
	public static void cellOneMaskUpdater(String maskType, int year) {
		MaskMetadata metadata = MaskLoader.metadata(maskType);
		if (metadata == null) {
			LOGGER.warn("Mask is absent from LandUseControl metadata and is ignored: " + maskType);
			return;
		}
		Path path = maskPath(metadata.name(), year);
		if (path == null) {
			return;
		}
		cleanMaskType(metadata.name());
		for (Cell cell : readActiveCells(path)) {
			MaskMetadata current = MaskLoader.metadata(cell.getMaskType());
			if (current != null && MaskMetadata.PRIORITY_ORDER.compare(current, metadata) < 0) {
				continue;
			}
			cell.setMaskType(metadata.name());
			if (metadata.forced()) {
				forceSingleAllowedOwner(cell, metadata.name());
			}
		}
	}

	public static void cleanMaskType(String maskType) {
		for (Cell cell : CellsLoader.hashCell.values()) {
			if (cell.getMaskType() != null && cell.getMaskType().equalsIgnoreCase(maskType)) {
				cell.setMaskType(null);
			}
		}
	}

	private static void forceSingleAllowedOwner(Cell cell, String maskType) {
		List<String> allowedLabels = forcedAllowedAftLabels.get(maskType);
		if (allowedLabels == null || allowedLabels.isEmpty()) {
			if (warnedForcedMasks.add(maskType)) {
				LOGGER.warn("Forced mask has no allowed target AFT from its restriction map: " + maskType);
			}
			return;
		}
		if (allowedLabels.size() != 1) {
			return; // resolved after regional utilities are available
		}
		Aft target = findAft(allowedLabels.get(0));
		if (target == null) {
			if (warnedForcedMasks.add(maskType)) {
				LOGGER.warn("Forced target AFT '" + allowedLabels.get(0) + "' is not loaded for mask " + maskType);
			}
			return;
		}
		applyForcedOwner(cell, maskType, target);
	}

	/**
	 * Resolves multi-target forced masks using normal competitor-selection settings,
	 * but deliberately without neighbor filtering.
	 *
	 * @return number of ownership changes
	 */
	public static int applyForcedMasks(RegionalModelRunner regionalRunner) {
		int changes = 0;
		for (Cell cell : regionalRunner.R.getCells().values()) {
			MaskMetadata metadata = MaskLoader.metadata(cell.getMaskType());
			if (metadata == null || !metadata.forced()) {
				continue;
			}
			List<String> labels = forcedAllowedAftLabels.get(metadata.name());
			if (labels == null || labels.size() <= 1) {
				continue;
			}
			Aft selected = selectForcedOwner(cell, metadata.name(), regionalRunner);
			if (selected != null && applyForcedOwner(cell, metadata.name(), selected)) {
				changes++;
			}
		}
		return changes;
	}

	static Aft selectForcedOwner(Cell cell, String maskType, RegionalModelRunner regionalRunner) {
		List<String> labels = forcedAllowedAftLabels.get(maskType);
		if (labels == null || labels.isEmpty()) {
			return null;
		}
		List<Aft> candidates = labels.stream().map(LandMaskUpdater::findAft)
				.filter(aft -> aft != null && aft.isInteract()).toList();
		if (candidates.isEmpty()) {
			return null;
		}

		long cellId = DeterministicRandom.stableCellKey(cell);
		long maskId = DeterministicRandom.hashString64(maskType);
		boolean chooseMostCompetitive = DeterministicRandom.randomBoolean(ConfigLoader.config.random_seed,
				Timestep.getCurrentYear(),
				DeterministicRandom.Process.FORCED_MASK_COMPETITOR_PICK, cellId, maskId, 0,
				ConfigLoader.config.most_competitive_aft_probability);
		if (chooseMostCompetitive) {
			return Competitiveness.mostCompetitiveAgent(cell, candidates, regionalRunner);
		}
		return AFTsLoader.getDeterministicRandomAFT(candidates,
				ConfigLoader.config.random_seed, Timestep.getCurrentYear(), cellId, maskId, 0);
	}

	private static Aft findAft(String label) {
		return AFTsLoader.getAftHash().values().stream().filter(aft -> label.equalsIgnoreCase(aft.getLabel())).findFirst()
				.orElse(null);
	}

	private static boolean applyForcedOwner(Cell cell, String maskType, Aft target) {
		Aft oldOwner = cell.getOwner();
		if (oldOwner == target || oldOwner != null && target.getLabel().equals(oldOwner.getLabel())) {
			return false;
		}
		trackForcedChange(maskType, target, oldOwner, cell);
		cell.setOwner(target);
		return true;
	}

	private static void trackForcedChange(String maskType, Aft target, Aft oldOwner, Cell cell) {
		Map<Integer, Map<String, Integer>> byYear = Tracker.sankeydata.get(target.getLabel());
		if (byYear != null) {
			Map<String, Integer> changes = byYear.get(Timestep.getCurrentYear());
			if (changes != null) {
				changes.merge(oldOwner == null ? "Abandoned" : oldOwner.getLabel(), 1, Integer::sum);
			}
		}
		cellsForecedToChange.computeIfAbsent(maskType, ignored -> new ConcurrentHashMap<>())
				.put(cell.getX() + "," + cell.getY(), cell);
	}

	private static boolean containsIgnoreCase(Set<String> values, String candidate) {
		return candidate != null && values.stream().anyMatch(value -> value.equalsIgnoreCase(candidate));
	}

	private record RestrictionImport(ConcurrentHashMap<String, Boolean> values, Set<String> allowedTargets) {
	}
}
