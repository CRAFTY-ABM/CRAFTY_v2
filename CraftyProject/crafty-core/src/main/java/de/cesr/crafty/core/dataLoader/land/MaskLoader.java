package de.cesr.crafty.core.dataLoader.land;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.PathTools;

/**
 * Discovers and indexes land-use control masks and their associated transition restriction tables.
 *
 * {@code MaskLoader} builds a time-indexed lookup for:
 * - Mask CSV files that assign a {@code maskType} (e.g., protected area, zoning layer) to cells.
 * - Restriction CSV files that define allowed / forbidden transitions between current owner and competitor
 *   for a given mask type (used by {@code LandMaskUpdater} / competition logic).
 *
 * The loader supports two input modes:
 * 1) Configuration-driven mode ({@link #byConfigItializer()}):
 *    Uses {@code config.landControle_directories} to point to one or more mask folders and then searches
 *    for per-year files between {@link Timestep#getStartYear()} and {@link Timestep#getEndtYear()}.
 *    A default restriction table can also be provided as {@code default_<maskName>_Restrictions.csv}.
 *
 * 2) Scenario-driven fallback ({@link #byScenarioinitializer()}):
 *    If configuration does not provide usable paths, the loader scans the project structure under
 *    {@code worlds/LandUseControl/<scenario>/<maskType>/...} and discovers per-year mask files and restrictions.
 *
 * Outputs:
 * - {@link #mask_paths}: {@code maskType -> (year -> mask CSV path)}
 * - {@link #restriction_paths}: {@code maskType -> (year -> restriction CSV path)} where year {@code 0}
 *   is reserved for a default restriction file.
 *
 * Notes:
 * - Both maps are stored as {@link TreeMap}s to preserve temporal ordering and support efficient
 *   “latest available year” lookups by updaters.
 * - If no masks/restrictions are found, the model proceeds without land-use controls.
 */

/**
 * @author Mohamed Byari
 *
 */

public class MaskLoader {

	private static final CustomLogger LOGGER = new CustomLogger(MaskLoader.class);
	public static Map<String, TreeMap<Integer, Path>> mask_paths = new HashMap<>();
	public static Map<String, TreeMap<Integer, Path>> restriction_paths = new HashMap<>();

	public static void initialize() {
		byConfigItializer();
		if (mask_paths.size() == 0 || restriction_paths.size() == 0) {
			byScenarioinitializer();
		}
		if (mask_paths.size() > 0) {
			LOGGER.info("Land Controls " + mask_paths.keySet());
			mask_paths.forEach((name, v) -> {
				LOGGER.info("The mask files will be updated for (" + name + "): " + v.keySet());
				LOGGER.info("The Restection file will be updated in: " + name + ": "
						+ restriction_paths.get(name).keySet());

			});

		}
	}

	private static void byScenarioinitializer() {
		List<File> LandUseControlFolder = PathTools
				.detectFolders(ProjectLoader.getProjectPath() + PathTools.asFolder("worlds") + "LandUseControl");
		if (LandUseControlFolder != null) {
			for (File folder : LandUseControlFolder) {
				maskByScenario(folder.getName());
				restrictionsByScenario(folder.getName());
			}
		} else {
			LOGGER.info("No land use control \"Mask\" considered ");
		}
	}

	public static void byConfigItializer() {
		if (ConfigLoader.config.landControle_directories != null
				&& ConfigLoader.config.landControle_directories instanceof List<String>) {
			for (String landControle_directories : ConfigLoader.config.landControle_directories) {
				String maskName = Paths.get(landControle_directories).getParent().getFileName().toString();
				TreeMap<Integer, Path> maskFinder = new TreeMap<>();
				TreeMap<Integer, Path> restrectionFinder = new TreeMap<>();
				ArrayList<Path> directory = PathTools.findAllFilePaths(Paths.get(landControle_directories));
				for (int i = Timestep.getStartYear(); i <= Timestep.getEndtYear(); i++) {
					ArrayList<Path> listMasks = PathTools.fileFilter(directory, "Year_" + i, maskName, ".csv", "Mask");
					if (listMasks != null) {
						Path p = listMasks.get(0);
						if (!p.toString().contains("Restrictions")) {
							maskFinder.put(i, p);
						}
					}
					ArrayList<Path> listrRestrections = PathTools.fileFilter(directory, "" + i, maskName,
							"_Restrictions", ".csv");
					if (listrRestrections != null) {
						Path p = listrRestrections.get(0);
						restrectionFinder.put(i, p);
					}
				}
				ArrayList<Path> parent = PathTools.findAllFilePaths(Paths.get(landControle_directories).getParent());
				ArrayList<Path> listp = PathTools.fileFilter(parent, "default_", maskName, "_Restrictions.csv");
				if (listp != null) {
					restrectionFinder.put(0, listp.get(0));
				}
				mask_paths.put(maskName, maskFinder);
				restriction_paths.put(maskName, restrectionFinder);

			}
		} else if (ConfigLoader.config.landControle_directories != null
				&& ConfigLoader.config.landControle_directories.size() == 0) {
			LOGGER.warn("No update of landControl from the configuration file");
		} else if (ConfigLoader.config.landControle_directories != null) {
			LOGGER.error(
					"The landControle_directories flag (configuration file) is not in the correct format. It must be [mask1,mask2,...] or empty [].");
		}
	}

	public static void maskByScenario(String maskType) {
		ArrayList<Path> MasksFilesBySce = PathTools.fileFilter(true,
				ProjectLoader.getProjectPath() + PathTools.asFolder("worlds") + "LandUseControl",
				ProjectLoader.getScenario(), PathTools.asFolder(maskType));
		TreeMap<Integer, Path> maskFinder = new TreeMap<>();

		MasksFilesBySce.forEach(path -> {
			for (int i = Timestep.getStartYear(); i < Timestep.getEndtYear(); i++) {
				if (path.getFileName().toString().contains(i + "")) {
					maskFinder.put(i, path);
				}
			}
		});
		mask_paths.put(maskType, maskFinder);
	}

	public static void restrictionsByScenario(String maskType) {
		String[] def = { "LandUseControl", "Restrictions", maskType, ".csv" };
		ArrayList<Path> restrictionsFile = PathTools.fileFilter(def);
		if (restrictionsFile != null && !restrictionsFile.isEmpty()) {
			restrictionsFile = PathTools.fileFilter(def);
			TreeMap<Integer, Path> restrectionFinder = new TreeMap<>();
			restrictionsFile.forEach(path -> {
				for (int i = Timestep.getStartYear(); i < Timestep.getEndtYear(); i++) {
					if (path.getFileName().toString().contains(i + "")
							&& path.toString().contains(ProjectLoader.getScenario())) {
						restrectionFinder.put(i, path);
					} else if (path.getFileName().toString().contains("default_")) {
						restrectionFinder.put(0, path);
					}
				}
			});
			restriction_paths.put(maskType, restrectionFinder);
		}
	}

}
