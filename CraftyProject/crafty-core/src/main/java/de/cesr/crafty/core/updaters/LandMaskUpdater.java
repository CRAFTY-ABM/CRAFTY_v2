package de.cesr.crafty.core.updaters;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.land.MaskLoader;
import de.cesr.crafty.core.output.Tracker;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.general.Utils;

/**
 * Updater responsible for applying land-use control masks and their associated restriction rules.
 *
 * This component manages two related concepts:
 * - Masks: spatial (cell-level) flags that assign a {@code maskType} to cells for a given year, and can
 *   optionally force ownership to a specific {@link Aft} (via {@link #maskToOwner()}).
 * - Restrictions: a boolean compatibility matrix per mask type controlling which owner to competitor
 *   transitions are permitted under that mask (used by {@link Competitiveness}).
 *
 * Initialisation:
 * - Calls {@link MaskLoader#initialize()} to resolve available mask and restriction files from config and/or
 *   scenario structure.
 * - Loads the initial restriction matrix for each mask (prefer the start-year file; otherwise fall back to
 *   the default restriction file keyed by year {@code 0}) into {@link #restrictions}.
 *
 * Per-tick/year behavior ({@link #step()}):
 * - For every configured {@code maskType}:
 *   1) Applies the mask map for the current year via {@link #cellOneMaskUpdater()}.
 *      This reads the mask CSV in a streaming manner (BufferedReader) to avoid loading the full file into
 *      memory, detects {@code X}/{@code Y} columns plus any {@code Year_*} columns, clears any previously
 *      assigned occurrences of this mask (via {@link #cleanMaskType(String)}), and assigns the mask to cells
 *      whose row indicates activity (a {@code Year_*} column containing "1").
 *   2) Updates the restriction matrix for that mask type (if a year-specific restriction file exists).
 *   
 * Restriction matrix format:
 * - Read as a CSV table where the first row provides competitor headers and the first column provides
 *   current-owner headers. Each cell is interpreted as {@code true} if it contains "1".
 * - Stored as a flat key {@code "<owner>_<competitor>"} to {@code Boolean} in {@link #restrictions}.
 *
 * Notes / assumptions:
 * - Masks are re-applied each year; cleaning resets the mask flag and may clear ownership if the mask name
 *   implies ownership.
 * - Mask-to-owner assignment is currently based on string containment of an AFT label in {@code maskType}
 *   (see {@link #maskToOwner(Cell, String)}), and should be revisited if mask naming conventions change.
 */

/**
 * @author Mohamed Byari
 *
 */
public class LandMaskUpdater extends AbstractUpdater {

	private static final CustomLogger LOGGER = new CustomLogger(LandMaskUpdater.class);

	public static ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>> restrictions = new ConcurrentHashMap<>();

	public LandMaskUpdater() {
		MaskLoader.initialize();

		if (MaskLoader.restriction_paths.size() > 0) {
			MaskLoader.restriction_paths.keySet().forEach(maskName -> {
				Path initialRestection = MaskLoader.restriction_paths.get(maskName).get(Timestep.getStartYear());
				Path defRestriction = MaskLoader.restriction_paths.get(maskName).get(0);
				if (initialRestection != null) {
					restrictions.put(maskName, importResrection(initialRestection));
				} else if (defRestriction != null) {
					restrictions.put(maskName, importResrection(defRestriction));
				} else {
					LOGGER.error("Restriction file not found : " + maskName);
				}
			});
		}
	}

	@Override
	public void toSchedule() {
		modelRunner.scheduleRepeating(this);
	}

	@Override
	public void step() {
		MaskLoader.mask_paths.keySet().forEach(maskType -> {
			cellOneMaskUpdater(maskType, Timestep.getCurrentYear());
			updateRestrections(maskType);
		});
	}

	public static void updateRestrections(String maskType) {
		// check if the restriction is not null or 0 size (else error)
		if (MaskLoader.restriction_paths.get(maskType).size() == 0) {
			LOGGER.fatal("No restrection file fund: " + maskType);
			return;
		}
		// if there is a corespondanet year use it
		Path updatedRestection = MaskLoader.restriction_paths.get(maskType).get(Timestep.getCurrentYear());
		if (updatedRestection != null) {
			restrictions.put(maskType, importResrection(updatedRestection));
			LOGGER.info("restrection updated for  (" + maskType + ") : " + updatedRestection);

		} else {
			LOGGER.info("No Resrection Update for: " + maskType);
		}

	}

	private static ConcurrentHashMap<String, Boolean> importResrection(Path path) {
		LOGGER.info("Import Resrection: " + path);
		ConcurrentHashMap<String, Boolean> restric = new ConcurrentHashMap<>();
		String[][] matrix = CsvTools.csvReader(path);
		if (matrix != null) {
			for (int i = 1; i < matrix.length; i++) {
				for (int j = 1; j < matrix[0].length; j++) {
					restric.put(matrix[i][0] + "_" + matrix[0][j], matrix[i][j].contains("1"));
				}
			}
			return restric;
		} else
			return null;
	}

	public static void cellOneMaskUpdater(String maskType, int year) {
		Path path = MaskLoader.mask_paths.get(maskType).get(year);

		if (path == null) {
			LOGGER.info("Mask file not found for  [" + maskType + " for the year:" + year
					+ "]  use the latest year available");
			return;
		}

		if (!Files.exists(path)) {
			LOGGER.warn("Cannot find the mask files..." + path);
			return;
		}

		LOGGER.info("Reading mask (streaming): " + path);

		try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {

			String headerLine = br.readLine();
			if (headerLine == null) {
				LOGGER.warn("Empty mask file: " + path);
				return;
			}

			String[] headers = CsvTools.parseCsvLine(headerLine);

			int xIdx = -1;
			int yIdx = -1;
			List<Integer> yearIdxs = new ArrayList<>();

			for (int i = 0; i < headers.length; i++) {
				String h = headers[i];
				if ("X".equalsIgnoreCase(h))
					xIdx = i;
				else if ("Y".equalsIgnoreCase(h))
					yIdx = i;
				else if (h != null && h.contains("Year_"))
					yearIdxs.add(i);
			}

			if (xIdx < 0 || yIdx < 0) {
				LOGGER.error("Mask CSV missing X or Y columns: " + path);
				return;
			}
			// Match old behavior: only clean after we confirm file is readable
			cleanMaskType(maskType);

			String line;
			while ((line = br.readLine()) != null) {
				if (line.isEmpty())
					continue;
				String[] values = CsvTools.parseCsvLine(line);

				if (xIdx >= values.length || yIdx >= values.length) {
					// Row malformed; skip
					continue;
				}
				int x;
				int y;
				try {
					x = (int) Utils.sToD(values[xIdx]);
					y = (int) Utils.sToD(values[yIdx]);
				} catch (Exception ex) {
					// Bad coordinate row; skip
					continue;
				}
				Cell c = CellsLoader.getCell(x, y);
				if (c == null)
					continue;
				boolean shouldApply = false;
				for (int idx : yearIdxs) {
					if (idx < values.length) {
						String v = values[idx];
						if (v != null && v.contains("1")) {
							shouldApply = true;
							break;
							// The original code might apply the same mask repeatedly
							// if multiple Year_ columns contain "1".
							// Breaking avoids redundant calls while preserving intent.
						}
					}
				}
				if (shouldApply) {
					c.setMaskType(maskType);
					maskToOwner(c, maskType);
				}
			}
			LOGGER.info("Update Mask: " + maskType + "[" + path + "]");
		} catch (IOException e) {
			LOGGER.error("Error reading mask file: " + path + " :: " + e.getMessage());
		}
	}

	public static void cleanMaskType(String maskType) {
		CellsLoader.hashCell.values()/**/ .parallelStream().forEach(c -> {
			if (c.getMaskType() != null && c.getMaskType().equals(maskType)) {
				c.setMaskType(null);
				if (c.getOwner() != null && maskType.contains(c.getOwner().getLabel())) {
					c.setOwner(null);
				}
			}
		});
	}

	private static void maskToOwner(Cell c, String maskType) {// need to be revised
		for (Aft a : AFTsLoader.getAftHash().values()) {
			if (maskType.contains(a.getLabel())) {
				if (c.getOwner() != null && !a.getLabel().equals(c.getOwner().getLabel())) {
					Tracker.sankeydata.get(a.getLabel()).get(Timestep.getCurrentYear())
							.merge((c.getOwner() != null ? c.getOwner().getLabel() : "Abandoned"), 1, Integer::sum);
				}
				c.setOwner(a);
				break;
			}
		}
	}

}
