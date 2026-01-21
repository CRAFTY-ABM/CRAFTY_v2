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
import de.cesr.crafty.core.utils.general.Utils;

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

		hashAFTs.forEach((l, a) -> {
			LOGGER.info("AFT Lifecycle: " + l + ": max= " + a.getMax_life_cycle() + ", min= " + a.getMin_life_cycle());
		});
	}

	void initializeAFTs() {
		initializeAftList();
		AftCategorised.CategoriesLoader();
		AftCategorised.initializeBehevoirByCategories();
		aft_production_paths.clear();
		aft_behevoir_paths.clear();
		aft_production_paths = production_paths();
		aft_behevoir_paths = behavior_paths();
		LOGGER.trace("production, files: " + aft_production_paths);
		LOGGER.trace("behevoir, files: " + aft_behevoir_paths);

		hashAFTs.forEach((aftName, a) -> {
			LOGGER.trace("Import Production and behaviour for AFT: " + aftName);
			if (a.isInteract()) {
				Path pFile = getInitailPaths("production", aftName);
				AftsUpdater.updateAFTProduction(hashAFTs.get(pFile.toFile().getName().replace(".csv", "")), pFile);
				LOGGER.info("production file for (" + aftName + "): " + pFile);
				Path bFile = getInitailPaths("behevoir", aftName);
				initializeAFTBehevoir(bFile);
				LOGGER.trace("giveIn-giveUp file for (" + aftName + "): " + bFile);
			}
		});
	}

	public static Path getPath(String BorP, String aftName) {
		Path configPath = BorP == "production" ? Paths.get(ConfigLoader.config.aft_production_directory)
				: Paths.get(ConfigLoader.config.aft_behevoir_directory);
		Map<String, Path> hash = BorP == "production" ? aft_production_paths.get(aftName)
				: aft_behevoir_paths.get(aftName);
		if (configPath.toFile().isDirectory()) {
			if (hash.keySet().contains(String.valueOf(Timestep.getCurrentYear()))) {
				return hash.get(String.valueOf(Timestep.getCurrentYear()));
			}
		} else {
			if (hash.keySet().contains(ProjectLoader.getScenario() + "|" + Timestep.getCurrentYear())) {
				return hash.get(ProjectLoader.getScenario() + "|" + Timestep.getCurrentYear());
			}
		}
		return null;
	}

	private static Path getInitailPaths(String BorP, String aftName) {
		Path configPath = BorP == "production" ? Paths.get(ConfigLoader.config.aft_production_directory)
				: Paths.get(ConfigLoader.config.aft_behevoir_directory);
		Map<String, Path> hash = BorP == "production" ? aft_production_paths.get(aftName)
				: aft_behevoir_paths.get(aftName);
		if (configPath.toFile().isDirectory()) {
			if (hash.keySet().contains("default_")) {
				return hash.get("default_");
			} else {
				LOGGER.fatal("Unable to find  default or " + Timestep.getStartYear() + " " + BorP
						+ " parameters file for AFT: " + aftName + "; " + configPath);
			}
		} else {
			if (hash.keySet().contains(ProjectLoader.getScenario())) {
				return hash.get(ProjectLoader.getScenario());
			} else if (hash.keySet().contains("default_")) {
				return hash.get("default_");
			} else {
				LOGGER.fatal("Unable to find  default or " + Timestep.getStartYear() + " " + BorP
						+ " parameters file for AFT: " + aftName + "; scenario  " + ProjectLoader.getScenario());
			}
		}
		return null;
	}

	Map<String, Map<String, Path>> production_paths() {
		Map<String, Map<String, Path>> data = new HashMap<>(); // <aftName,default_/Year/scenario,path>

		Path configPath = Paths.get(ConfigLoader.config.aft_production_directory);
		if (configPath.toFile().isDirectory()) {
			ArrayList<Path> folder = PathTools.findAllFilePaths(configPath);
			hashAFTs.keySet().forEach(aftName -> {
				data.put(aftName, new HashMap<>());
				for (int i = Timestep.getStartYear(); i <= Timestep.getEndtYear(); i++) {
					ArrayList<Path> p = PathTools.fileFilter(folder, String.valueOf(i),
							File.separator + aftName + ".csv");
					if (p != null && !p.isEmpty()) {
						data.get(aftName).put(String.valueOf(i), p.get(0));
					}
				}
				ArrayList<Path> list2 = new ArrayList<>(data.get(aftName).values());
				ArrayList<Path> tmp = PathTools.fileFilter(folder, File.separator + aftName + ".csv");
				if (tmp != null) {
					ArrayList<Path> list3 = new ArrayList<>(tmp);
					list3.removeAll(new HashSet<>(list2));
					list3.forEach(p -> {
						data.get(aftName).put("default_", p);
					});
				}
			});

		} else {
			ArrayList<Path> folder = PathTools.fileFilter(PathTools.asFolder("production"));
			hashAFTs.keySet().forEach(aftName -> {
				data.put(aftName, new HashMap<>());
				// if Name contain year and scenarios
				ProjectLoader.getScenariosList().forEach(scenarioName -> {
					for (int i = Timestep.getStartYear(); i < Timestep.getEndtYear(); i++) {
						ArrayList<Path> p = PathTools.fileFilter(folder, String.valueOf(i),
								File.separator + aftName + ".csv", scenarioName);
						if (p != null && !p.isEmpty()) {
							data.get(aftName).put(scenarioName + "|" + i, p.get(0));
						}
					}
				});
				// if Name contain only scenarios -> go to rest and find scenarios
				ArrayList<Path> exist1 = new ArrayList<>(data.get(aftName).values());

				ProjectLoader.getScenariosList().forEach(scenarioName -> {
					ArrayList<Path> tmp = PathTools.fileFilter(folder, File.separator + aftName + ".csv", scenarioName);
					if (tmp != null) {
						ArrayList<Path> rest1 = new ArrayList<>(tmp);
						rest1.removeAll(new HashSet<>(exist1));
						rest1.forEach(p -> {
							data.get(aftName).put(scenarioName, p);
						});
					}
				});
				// if Name contain only default_-> go to rest and define file as default_
				ArrayList<Path> exist2 = new ArrayList<>(data.get(aftName).values());
				ArrayList<Path> tmp = PathTools.fileFilter(folder, aftName + ".csv", "default_");
				if (tmp != null) {
					ArrayList<Path> rest2 = new ArrayList<>(tmp);
					rest2.removeAll(new HashSet<>(exist2));
					rest2.forEach(p -> {
						data.get(aftName).put("default_", p);
					});
				}
			});
		}
//		data.forEach((a, v) -> {
//			System.out.println("@@   " + a + ": " + v);
//		});
		return data;
	}

	Map<String, Map<String, Path>> behavior_paths() {
		Map<String, Map<String, Path>> data = new HashMap<>(); // <aftName,default_/Year/scenario,path>
		Path configPath = Paths.get(ConfigLoader.config.aft_behevoir_directory);
		if (configPath.toFile().isDirectory()) {
			ArrayList<Path> folder = PathTools.findAllFilePaths(configPath);
			hashAFTs.keySet().forEach(aftName -> {
				data.put(aftName, new HashMap<>());
				for (int i = Timestep.getStartYear(); i < Timestep.getEndtYear(); i++) {
					ArrayList<Path> p = PathTools.fileFilter(folder, String.valueOf(i),
							"AftParams_" + aftName + ".csv");
					if (p != null && !p.isEmpty()) {
						data.get(aftName).put(String.valueOf(i), p.get(0));
					}
				}
				ArrayList<Path> list2 = new ArrayList<>(data.get(aftName).values());
				ArrayList<Path> list3 = new ArrayList<>(PathTools.fileFilter(folder, "AftParams_" + aftName + ".csv"));
				list3.removeAll(new HashSet<>(list2));
				list3.forEach(p -> {
					data.get(aftName).put("default_", p);
				});
			});

		} else {
			ArrayList<Path> folder = PathTools.fileFilter(PathTools.asFolder("agents"));
			hashAFTs.keySet().forEach(aftName -> {
				data.put(aftName, new HashMap<>());
				// if Name contain year and scenarios
				ProjectLoader.getScenariosList().forEach(scenarioName -> {
					for (int i = Timestep.getStartYear(); i < Timestep.getEndtYear(); i++) {
						ArrayList<Path> p = PathTools.fileFilter(folder, String.valueOf(i),
								"AftParams_" + aftName + ".csv", scenarioName);
						if (p != null && !p.isEmpty()) {
							data.get(aftName).put(scenarioName + "|" + i, p.get(0));
						}
					}
				});
				// if Name contain only scenarios -> go to rest and find scenarios
				ArrayList<Path> exist1 = new ArrayList<>(data.get(aftName).values());

				ProjectLoader.getScenariosList().forEach(scenarioName -> {
					ArrayList<Path> tmp = PathTools.fileFilter(folder, "AftParams_" + aftName + ".csv", scenarioName);
					if (tmp != null) {
						ArrayList<Path> rest1 = new ArrayList<>(tmp);
						rest1.removeAll(new HashSet<>(exist1));
						rest1.forEach(p -> {
							data.get(aftName).put(scenarioName, p);
						});
					}
				});
				// if Name contain only default_-> go to rest and define file as default_
				ArrayList<Path> exist2 = new ArrayList<>(data.get(aftName).values());
				ArrayList<Path> tmp = PathTools.fileFilter(folder, "AftParams_" + aftName + ".csv", "default_");
				if (tmp != null) {
					ArrayList<Path> rest2 = new ArrayList<>(tmp);
					rest2.removeAll(new HashSet<>(exist2));
					rest2.forEach(p -> {
						data.get(aftName).put("default_", p);
					});
				}
			});
		}
		return data;
	}

	private void initializeAFTBehevoir(Path aftPath) {
		Aft a = hashAFTs.get(aftPath.toFile().getName().replace(".csv", "").replace("AftParams_", ""));
		AftsUpdater.updateAFTBehevoir(a, aftPath);
	}

	private void initializeAftList() {// create AFts list, //
		hashAFTs.clear();
		Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(ProjectLoader.getAftMetaData());
		if (csv.get("Type") != null) {
			for (int i = 0; i < csv.get("Label").size(); i++) {
				String label = csv.get("Label").get(i);
				Aft a = new Aft(label);
				a.setColor(csv.get("Color").get(i));
				if (csv.keySet().contains("Name")) {
					a.setCompleteName(csv.get("Name").get(i));
				} else {
					a.setCompleteName("-");
				}
				hashAFTs.put(label, a);
				switch (csv.get("Type").get(i)) {
				case "Mask":
					a.setType(ManagerTypes.MASK);
					break;
				case "Abandoned":
					a.setType(ManagerTypes.Abandoned);
					break;
				default:
					a.setType(ManagerTypes.AFT);
				}
				if (csv.get("min_life_cycle") != null) {
					a.setMin_life_cycle(Utils.sToI(csv.get("min_life_cycle").get(i)));
				}
				if (csv.get("max_life_cycle") != null) {
					int max = Utils.sToI(csv.get("max_life_cycle").get(i));
					a.setMax_life_cycle(max > 0 ? max : Integer.MAX_VALUE);
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
