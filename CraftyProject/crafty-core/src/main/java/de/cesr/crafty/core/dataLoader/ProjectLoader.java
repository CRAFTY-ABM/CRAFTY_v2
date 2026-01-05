package de.cesr.crafty.core.dataLoader;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves and caches project-level paths and scenario metadata for a CRAFTY run.
 *
 * {@code ProjectLoader} is responsible for:
 * - Storing the root project directory ({@link #projectPath}) and building a full list of files under it.
 * - Locating the required metadata CSV files (AFTs, Services, Capitals, Scenarios) based on
 *   {@link ConfigLoader#config} and the expected project folder structure.
 * - Reading the scenarios metadata table and building a lookup of scenario -> (startYear, endYear).
 * - Applying the selected scenario via {@link #setScenario(String)}, which also initializes the global
 *   simulation time window in {@link Timestep}.
 *
 * Metadata discovery:
 * The loader first uses {@code config.metaData_directory}. If that path is not a directory, it attempts
 * to auto-detect a suitable folder under the project directory (e.g., "csv" or "metaData"). If metadata
 * files cannot be found, it terminates the run via {@link CustomLogger#fatal(String)}.
 *
 * Typical lifecycle:
 * 1) Call {@link #pathInitialisation(Path)} once after configuration is loaded.
 * 2) Choose a scenario and call {@link #setScenario(String)} to configure {@link Timestep}.
 *
 * Note: this class is static and acts as a global cache of resolved project resources for the duration
 * of a run.
 */

/**
 * @author Mohamed Byari
 *
 */

public final class ProjectLoader {
	private static final CustomLogger LOGGER = new CustomLogger(ProjectLoader.class);

	private static Path projectPath;
	private static Path aftMetaData;
	private static Path serviceMetadata;
	private static Path capitalsMetadata;
	private static Path scenarioMetaData;

	private static ArrayList<String> scenariosList = new ArrayList<>();
	private static HashMap<String, String> scenariosHash = new HashMap<>();
	private static ArrayList<Path> allfilePathsInDataDirectory;
	private static String scenario;
	public static String WorldName = "";

	private static void fillefAllMetaDataFilesAreFound() {
		String d = ConfigLoader.config.metaData_directory;
		if (!Paths.get(d).toFile().isDirectory()) {
			Optional<File> p = PathTools.detectFolders(projectPath.toString()).stream().filter(f -> {
				return f.getName().equalsIgnoreCase("csv") || f.getName().equalsIgnoreCase("metaData");
			}).findAny();
			if (!p.isEmpty() && p.get().isDirectory()) {
				d = p.get().toString();
			} else {
				LOGGER.fatal("metaData_directory not found, check config.yaml or/and input structure " + d);
			}
		}
		// Go to the folder and fill CSVs as path or retun fatal if any one missing
		ArrayList<Path> list = PathTools.findAllFilePaths(Paths.get(d));
		try {
			aftMetaData = PathTools.fileFilter(list, "AFTsMetaData").get(0);
			capitalsMetadata = PathTools.fileFilter(list, "Capitals").get(0);
			scenarioMetaData = PathTools.fileFilter(list, "scenarios").get(0);
			serviceMetadata = PathTools.fileFilter(list, "Services").get(0);
		} catch (NullPointerException e) {
			LOGGER.fatal("One or more metadata files are missing in" + " where we expect to find"
					+ "(scenarios, Services, AFTsMetaData, and Capitals).");
		}
	}

	public static void pathInitialisation(Path p) {
		projectPath = p;
		fillefAllMetaDataFilesAreFound();
		allfilePathsInDataDirectory = PathTools.findAllFilePaths(projectPath);
		initialSenarios();
	}

	static void initialSenarios() {

		Map<String, List<String>> hash = CsvProcessors.ReadAsaHash(scenarioMetaData);
		setScenariosList(hash.get("Name"));
		for (String scenario : scenariosList) {
			try {
				scenariosHash.put(scenario, hash.get("startYear").get(hash.get("Name").indexOf(scenario)) + "_"
						+ hash.get("endtYear").get(hash.get("Name").indexOf(scenario)));
			} catch (NullPointerException e) {
				LOGGER.fatal("cannot find \"Name\", \"startYear\" and/or \"endtYear\" in the head of the file :"
						+ scenarioMetaData);
				break;
			}
		}
	}

	public static ArrayList<Path> getAllfilesPathInData() {
		return allfilePathsInDataDirectory;
	}

	public static Path getProjectPath() {
		return projectPath;
	}

	public static ArrayList<String> getScenariosList() {
		return scenariosList;
	}

	public static void setScenariosList(List<String> list) {
		scenariosList = new ArrayList<>(list);
	}

	public static void setAllfilesPathInData(ArrayList<Path> allfilesPathInData) {
		ProjectLoader.allfilePathsInDataDirectory = allfilesPathInData;
	}

	public static String getScenario() {
		return scenario;
	}

	public static void setScenario(String scenario) {
		ProjectLoader.scenario = scenario;
		String[] temp = scenariosHash.get(scenario).split("_");

		Timestep.setStartYear((int) Utils.sToD(temp[0]));
		Timestep.setCurrentYear(Timestep.getStartYear());
		Timestep.setEndtYear((int) Utils.sToD(temp[1]));
		Timestep.setSize(Timestep.getEndtYear() - Timestep.getStartYear()+1);
		LOGGER.info(scenario + "--> startYear= " + Timestep.getStartYear() + ", endtYear " + Timestep.getEndtYear());
	}

	public static Path getAftMetaData() {
		return aftMetaData;
	}

	public static Path getServiceMetadata() {
		return serviceMetadata;
	}

	public static Path getCapitalsMetadata() {
		return capitalsMetadata;
	}

	public static Path getScenarioMetaData() {
		return scenarioMetaData;
	}

}
