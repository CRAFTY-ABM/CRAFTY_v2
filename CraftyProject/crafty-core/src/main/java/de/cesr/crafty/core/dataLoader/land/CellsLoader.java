package de.cesr.crafty.core.dataLoader.land;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.crafty.Region;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.CsvKind;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.utils.file.PathTools;

/**
 * Loads the spatial cell grid (baseline land use) and prepares region partitions for the simulation.
 *
 * {@code CellsLoader} is responsible for building the in-memory representation of the model world:
 * - Reads the baseline map CSV and creates {@link Cell} instances (via {@link CsvProcessors} / {@link CsvKind#BASELINE}).
 * - Loads auxiliary GIS attributes (via {@link GisLoader}) that assign region identifiers and other spatial fields.
 * - Builds the region container(s) ({@link #regions}) either as:
 *   - multiple regions when {@code config.regionalization} is enabled, or
 *   - a single “world” region containing all cells otherwise.
 * - Initializes the global service registry and regional service state via {@link ServiceSet}.
 * - Computes basic grid extents ({@link #minX}, {@link #maxX}, {@link #minY}, {@link #maxY}) from loaded cells.
 *
 * Key outputs:
 * - {@link #hashCell}: all cells indexed by "x,y".
 * - {@link #regions}: mapping of region name -> {@link Region} with its cell subset.
 * - {@link #regionsNamesSet}: set of region names discovered during GIS loading (used when regionalising).
 *
 * Notes:
 * - If regionalisation is enabled but required regional service inputs are missing,
 *   the loader automatically falls back to a single-region configuration.
 */

/**
 * @author Mohamed Byari
 *
 */

public class CellsLoader {
	private static final CustomLogger LOGGER = new CustomLogger(CellsLoader.class);
	public static Set<String> regionsNamesSet = new HashSet<>();
	public static ConcurrentHashMap<String, Cell> hashCell = new ConcurrentHashMap<>();
	public static ConcurrentHashMap<String, Region> regions = new ConcurrentHashMap<>();
	public static int maxX, maxY, minX, minY;

	public static boolean regionalization = false;

	private static int nbrOfCells = 0;

	public CellsLoader() {
		regionalization = ConfigLoader.config.regionalization;
		hashCell.clear();
		if (ConfigLoader.config.BASELINE_path == null || ConfigLoader.config.BASELINE_path.isEmpty()) {
			ConfigLoader.config.BASELINE_path = PathTools.fileFilter(PathTools.asFolder("worlds"), "Baseline_map")
					.iterator().next().toString();
		}
		LOGGER.info("BASELINE_path  = " + ConfigLoader.config.BASELINE_path);
		CsvProcessors.processCSV(Paths.get(ConfigLoader.config.BASELINE_path), CsvKind.BASELINE);
		AFTsLoader.hashAgentNbr();

		new GisLoader().loadGisData();
		regionsInitialize();
		AFTsLoader.hashAgentNbrRegions();
		AFTsLoader.hashAgentNbr();
		nbrOfCells = hashCell.size();
		System.out.println("hashCell.size() = " + nbrOfCells);
		initialMaxMinXY();
	}
	
	
	private static void initialMaxMinXY() {
		ArrayList<Integer> X = new ArrayList<>();
		ArrayList<Integer> Y = new ArrayList<>();

		CellsLoader.hashCell.values().forEach(c -> {
			X.add(c.getX());
			Y.add(c.getY());
		});
		maxX = Collections.max(X) + 1;
		maxY = Collections.max(Y) + 1;
		minX = Collections.min(X);
		minY = Collections.min(Y);
	}


	public static Cell getCell(int i, int j) {
		return hashCell.get(i + "," + j);
	}

	public static int getNbrOfCells() {
		return nbrOfCells;
	}

	public static void regionsInitialize() {
		regions = new ConcurrentHashMap<>();
		if (regionalization) {
			CellsLoader.regionsNamesSet.forEach(regionName -> {
				regions.put(regionName, new Region(regionName));
			});
			CellsLoader.hashCell.values()/**/.parallelStream().forEach(c -> {
				if (c.getCurrentRegion() != null) {
					regions.get(c.getCurrentRegion()).getCells().put(c.getX() + "," + c.getY(), c);
				}
			});
			boolean isRegionalPossible = ServiceSet.isRegionalServicesExisting();
			if (!isRegionalPossible) {
				regionalization = false;
				regionsInitialize();
			}
		} else {
			String name = ProjectLoader.WorldName;
			regions.put(name, new Region(name));
			regions.get(name).setCells(CellsLoader.hashCell);
		}

		ServiceSet.initialseServices();
		ServiceSet.serviceupdater();

		LOGGER.info("Regions: " + regions.keySet());
	}

}
