package de.cesr.crafty.core.dataLoader.afts;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.ManagerTypes;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.updaters.AftsUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.PathTools;

/**
 * Loads, initializes, and provides global access to the set of Agent Functional Types (AFTs) used in a run.
 *
 * Responsibilities:
 * - Read AFT definitions (labels, colors, names, and types) from the AFT metadata CSV
 *   (see {@link ProjectLoader#getAftMetaData()}).
 * - Create and register all {@link Aft} instances, including a built-in "Abandoned" manager.
 * - Maintain global lookup maps:
 *   - {@link #hashAFTs}: all AFTs by label.
 *   - {@link #activateAFTsHash}: only active managers (interacting AFTs + abandoned).
 * - Resolve per-AFT parameter files (production and behaviour) from the configured directories and load
 *   the appropriate file for the selected scenario and start year, with sensible fallbacks to defaults.
 * - Provide helper utilities such as counting the number of cells owned by each AFT (globally and per region),
 *   and selecting a random active AFT.
 *
 * Parameter file resolution:
 * For each AFT, the loader searches for files under the configured production/behaviour directories and
 * supports multiple levels of specificity:
 * - scenario + year (e.g., "{scenario}|{year}")
 * - scenario-wide defaults
 * - global defaults (default_production / default_agents) optionally also varying by year
 *
 * These files are then applied via {@link AftsUpdater} to populate productivity, sensitivities, and
 * behavioural parameters (give-in/give-up, etc.).
 *
 * Notes:
 * - This class extends {@link HashSet} mainly to allow legacy code patterns like {@code addAll(...)}; the
 *   authoritative storage is the static maps.
 * - Counting methods ({@link #hashAgentNbr()} and region variants) depend on {@link CellsLoader} being initialized.
 */

/**
 * @author Mohamed Byari
 *
 */

public class AFTsLoader extends HashSet<Aft> {

	private static final CustomLogger LOGGER = new CustomLogger(AFTsLoader.class);
	private static final long serialVersionUID = 1L;
	private static ConcurrentHashMap<String, Aft> hashAFTs = new ConcurrentHashMap<>();
	private static ConcurrentHashMap<String, Aft> activateAFTsHash = new ConcurrentHashMap<>();
	public static ConcurrentHashMap<String, Integer> hashAgentNbr = new ConcurrentHashMap<>();
	public static ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> hashAgentNbrRegions = new ConcurrentHashMap<>();

	public static Map<String, Map<String, Path>> aft_production_paths = new HashMap<>();// <aftName,default/year,path>
	public static Map<String, Map<String, Path>> aft_behevoir_paths = new HashMap<>();;

	public AFTsLoader() {
		initializeAFTs();
		hashAFTs.put("Abandoned", new Aft("Abandoned"));
		addAll(hashAFTs.values());
		activateAFTsHash.clear();
		hashAFTs.entrySet().stream().filter(entry -> entry.getValue().isActive())
				.forEach(entry -> activateAFTsHash.put(entry.getKey(), entry.getValue()));
		LOGGER.info(" AFTs: " + hashAFTs.keySet());
		LOGGER.info("Active AFTs: " + activateAFTsHash.keySet());
	}

	void initializeAFTs() {
		initializeAftTypes();
		AftCategorised.CategoriesLoader();
		AftCategorised.initializeBehevoirByCategories();
		aft_production_paths = aftParametersPaths("production");
		aft_behevoir_paths = aftParametersPaths("agents");

		hashAFTs.forEach((Label, a) -> {
			LOGGER.trace("Import Production and behaviour for AFT: " + Label);
			if (a.isInteract()) {
				Path pFile = Optional.ofNullable(aft_production_paths.get(Label)).map(paths -> {
					String scenarioKey = ProjectLoader.getScenario() + "|" + Timestep.getStartYear();
					if (paths.containsKey(scenarioKey))
						return paths.get(scenarioKey);
					if (paths.containsKey(ProjectLoader.getScenario()))
						return paths.get(ProjectLoader.getScenario());
					return paths.get("default_production");
				}).orElse(null);

				if (pFile != null) {
					initializeAFTProduction(pFile);
					LOGGER.trace("production file for (" + Label + "): " + pFile);
				} else {
					LOGGER.fatal("Could NOT found AFT Production file for initialisation: " + Label);
				}

				Path bFile = Optional.ofNullable(aft_behevoir_paths.get(Label)).map(paths -> {
					String scenarioKey = ProjectLoader.getScenario() + "|" + Timestep.getStartYear();
					if (paths.containsKey(scenarioKey))
						return paths.get(scenarioKey);
					if (paths.containsKey(ProjectLoader.getScenario()))
						return paths.get(ProjectLoader.getScenario());
					return paths.get("default_agents");
				}).orElse(null);

				if (pFile != null) {
					initializeAFTBehevoir(bFile);
					LOGGER.trace("giveIn-giveUp file for (" + Label + "): " + bFile);
				} else {
					LOGGER.fatal("Could NOT found AFT Behevoir file for initialisation: " + Label);
				}
			}
		});
	}

	Map<String, Map<String, Path>> aftParametersPaths(String productioOrBehevoir) {
		Map<String, Map<String, Path>> data = new HashMap<>();
		String aftparams = productioOrBehevoir.equals("agents") ? "AftParams_" : "";
		String cofigPorB = productioOrBehevoir.equals("agents") ? ConfigLoader.config.aft_behevoir_directory
				: ConfigLoader.config.aft_production_directory;
		String scenrioParameters = Paths.get(cofigPorB).getFileName().toString();
		if (!scenrioParameters.isBlank()) {
			hashAFTs.keySet().forEach(aftLable -> {
				ArrayList<Path> pfiles = PathTools.fileFilter(PathTools.findAllFilePaths(Paths.get(cofigPorB)),
						PathTools.asFolder(productioOrBehevoir), aftparams + aftLable + ".csv");
				if (pfiles != null) {
					Map<String, Path> temp = new HashMap<>();
					pfiles.forEach(p -> {
						if (p.toString().contains(scenrioParameters + File.separator + aftparams + aftLable + ".csv")) {
							temp.put(ProjectLoader.getScenario(), p);
						} else if (p.toString().contains(scenrioParameters)) {
							for (int i = Timestep.getStartYear(); i < Timestep.getEndtYear(); i++) {
								if (p.toString().contains(PathTools.asFolder(String.valueOf(i)))) {
									temp.put(ProjectLoader.getScenario() + "|" + i, p);
								}
							}
						} else if (p.toString().contains(
								"default_" + productioOrBehevoir + File.separator + aftparams + aftLable + ".csv")) {
							temp.put("default_" + productioOrBehevoir, p);
						} else if (p.toString().contains("default_" + productioOrBehevoir)) {
							for (int i = Timestep.getStartYear(); i < Timestep.getEndtYear(); i++) {
								if (p.toString().contains(PathTools.asFolder(String.valueOf(i)))
										&& p.toString().contains("default_" + productioOrBehevoir)) {
									temp.put("default_" + productioOrBehevoir + "|" + i, p);
								}
							}
						}
					});
					data.put(aftLable, temp);
				}
			});
		} else {
			ArrayList<Path> directory =PathTools.fileFilter(PathTools.asFolder(productioOrBehevoir) );
			hashAFTs.keySet().forEach(label -> {
				Map<String, Path> yPath = new HashMap<>();
				directory.forEach(p -> {
					if (p.toString().contains(File.separator + aftparams + label + ".csv")) {
						if (p.toString().contains(cofigPorB + File.separator + aftparams + label + ".csv")) {
							yPath.put("default_" + productioOrBehevoir, p);
						}
						for (int i = Timestep.getStartYear(); i < Timestep.getEndtYear(); i++) {
							if (p.toString().contains(String.valueOf(i))) {
								yPath.put(ProjectLoader.getScenario() + "|" + i, p);
							}
						}
					}
				});
				data.put(label, yPath);
			});
		}
		return data;
	}

	private void initializeAFTBehevoir(Path aftPath) {
		File file = aftPath.toFile();
		Aft a = hashAFTs.get(file.getName().replace(".csv", "").replace("AftParams_", ""));
		AftsUpdater.updateAFTBehevoir(a, file);
	}

	private void initializeAFTProduction(Path aftPath) {
		File file = aftPath.toFile();
		AftsUpdater.updateAFTProduction(hashAFTs.get(file.getName().replace(".csv", "")), file);
	}

	void initializeAftTypes() {// mask, AFT, or unmanaged //
		hashAFTs.clear();
		Map<String, List<String>> matrix = CsvProcessors.ReadAsaHash(ProjectLoader.getAftMetaData());
		if (matrix.get("Type") != null) {
			for (int i = 0; i < matrix.get("Label").size(); i++) {
				String label = matrix.get("Label").get(i);
				Aft a = new Aft(label);
				a.setColor(matrix.get("Color").get(i));
				if (matrix.keySet().contains("Name")) {
					a.setCompleteName(matrix.get("Name").get(i));
				} else {
					a.setCompleteName("-");
				}
				hashAFTs.put(label, a);
				switch (matrix.get("Type").get(i)) {
				case "Mask":
					a.setType(ManagerTypes.MASK);
					break;
				case "Abandoned":
					a.setType(ManagerTypes.Abandoned);
					break;
				default:
					a.setType(ManagerTypes.AFT);
				}
			}
		}
	}

	public static void hashAgentNbr() {
		hashAgentNbr.clear();
		CellsLoader.hashCell.values().forEach(c -> {
			if (c.getOwner() != null)
				hashAgentNbr.merge(c.getOwner().getLabel(), 1, Integer::sum);
			else {
				hashAgentNbr.merge("Abandoned", 1, Integer::sum);
			}
		});
		if (!hashAgentNbr.containsKey("Abandoned") || !hashAgentNbr.containsKey("Abandoned")) {
			hashAgentNbr.put("Abandoned", 0);
		}
		LOGGER.info("Number of cells for each AFT: " + hashAgentNbr);
	}

	public static void hashAgentNbrRegions() {
		CellsLoader.regions.keySet().forEach(r -> {
			hashAgentNbr(r);
		});
	}

	public static void hashAgentNbr(String regionName) {
		ConcurrentHashMap<String, Integer> hashAgentNbr = new ConcurrentHashMap<>();
		CellsLoader.regions.get(regionName).getCells().values().forEach(c -> {
			if (c.getOwner() != null)
				hashAgentNbr.merge(c.getOwner().getLabel(), 1, Integer::sum);
			else {
				hashAgentNbr.merge("Abandoned", 1, Integer::sum);
			}
			if (!hashAgentNbr.containsKey("Abandoned") || !hashAgentNbr.containsKey("Abandoned")) {
				hashAgentNbr.put("Abandoned", 0);
			}
		});
		hashAgentNbrRegions.put(regionName, hashAgentNbr);
		getAftHash().values().forEach(a -> hashAgentNbrRegions.get(regionName).computeIfAbsent(a.getLabel(), key -> 0));

		LOGGER.trace("Rigion: [" + regionName + "] NBR of AFTs: " + hashAgentNbrRegions.get(regionName));
	}

	public static ConcurrentHashMap<String, Aft> getAftHash() {
		return hashAFTs;
	}

	public static ConcurrentHashMap<String, Aft> getActivateAFTsHash() {
		return activateAFTsHash;
	}

	public static Aft getRandomAFT() {
		return getRandomAFT(activateAFTsHash.values());
	}

	public static Aft getRandomAFT(Collection<Aft> afts) {
		if (afts.size() != 0) {
			int index = ThreadLocalRandom.current().nextInt(afts.size());
			Aft aft = afts.stream().skip(index).findFirst().orElse(null);
			return aft;
		}
		return null;
	}

}
