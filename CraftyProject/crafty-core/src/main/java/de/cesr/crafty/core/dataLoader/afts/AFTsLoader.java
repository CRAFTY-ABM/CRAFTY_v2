package de.cesr.crafty.core.dataLoader.afts;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
import de.cesr.crafty.core.utils.general.DeterministicRandom;

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
	public static ConcurrentHashMap<String, Integer> hashAgentNbr_initialYear = new ConcurrentHashMap<>();

	public static ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> hashAgentNbrRegions = new ConcurrentHashMap<>();

	public static Map<String, Map<String, Path>> aft_production_paths = new HashMap<>();// <aftName,default/year,path>
	public static Map<String, Map<String, Path>> aft_behaviour_paths = new HashMap<>();;

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
			LOGGER.trace("AFT Lifecycle: " + l + ": max= " + a.getMax_life_cycle() + ", min= " + a.getMin_life_cycle());
		});
	}

	void initializeAFTs() {
		initializeAftList();
		AftCategorised.CategoriesLoader();
		AftCategorised.initializeBehaviourByCategories();
		aft_production_paths.clear();
		aft_behaviour_paths.clear();
		aft_production_paths = production_paths();
		aft_behaviour_paths = behaviourPaths();
		LOGGER.trace("production, files: " + aft_production_paths);
		LOGGER.trace("behaviour, files: " + aft_behaviour_paths);

		hashAFTs.forEach((aftName, a) -> {
			LOGGER.trace("Import Production and behaviour for AFT: " + aftName);
			if (a.isInteract()) {
				Path pFile = getInitailPaths("production", aftName);
				AftsUpdater.updateAFTProduction(hashAFTs.get(pFile.toFile().getName().replace(".csv", "")), pFile);
				LOGGER.info("production file for (" + aftName + "): " + pFile);
				Path bFile = getInitailPaths("behaviour", aftName);
				initializeAFTBehaviour(bFile);
				LOGGER.trace("giveIn-giveUp file for (" + aftName + "): " + bFile);
			}
		});
	}

	public static Path getPath(String BorP, String aftName) {
		boolean production = "production".equals(BorP);
		String configuredDirectory = production ? ConfigLoader.config.aft_production_parameters_directory
				: ConfigLoader.config.aft_behaviour_parameters_directory;
		Path configPath = configuredDirectory == null || configuredDirectory.isBlank() ? null
				: Paths.get(configuredDirectory);
		Map<String, Path> hash = production ? aft_production_paths.get(aftName)
				: aft_behaviour_paths.get(aftName);
		if (configPath != null && configPath.toFile().isDirectory()) {
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
		boolean production = "production".equals(BorP);
		String configuredDirectory = production ? ConfigLoader.config.aft_production_parameters_directory
				: ConfigLoader.config.aft_behaviour_parameters_directory;
		Path configPath = configuredDirectory == null || configuredDirectory.isBlank() ? null
				: Paths.get(configuredDirectory);
		Map<String, Path> hash = production ? aft_production_paths.get(aftName)
				: aft_behaviour_paths.get(aftName);
		if (configPath != null && configPath.toFile().isDirectory()) {
			if (hash.keySet().contains("default_")) {
				return hash.get("default_");
			} else {
				LOGGER.fatal("Unable to find  default or " + Timestep.getStartYear() + " " + BorP
						+ " parameters file for AFT: " + aftName + "; " + configuredDirectory);
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

		Path configPath = Paths.get(ConfigLoader.config.aft_production_parameters_directory);
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
				ArrayList<Path> tmp = PathTools.fileFilter(folder, File.separator + aftName + ".csv", "default_");
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

	Map<String, Map<String, Path>> behaviourPaths() {
		Map<String, Map<String, Path>> data = new HashMap<>(); // <aftName,default_/Year/scenario,path>
		String configuredDirectory = ConfigLoader.config.aft_behaviour_parameters_directory;
		Path configPath = configuredDirectory == null || configuredDirectory.isBlank() ? null
				: Paths.get(configuredDirectory);
		if (configPath != null && configPath.toFile().isDirectory()) {
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

	private void initializeAFTBehaviour(Path aftPath) {
		Aft a = hashAFTs.get(aftPath.toFile().getName().replace(".csv", "").replace("AftParams_", ""));
		AftsUpdater.updateAFTBehaviour(a, aftPath);
	}

	private void initializeAftList() {// create AFts list, //
		hashAFTs.clear();
		Map<String, List<String>> csv = CsvProcessors.ReadAsaHash(ProjectLoader.getAftMetaData());
		int indexBackUp = 0;
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
				if (csv.keySet().contains("ID")) {
					a.setId(Utils.sToI(csv.get("ID").get(i)));
				} else {
					a.setId(indexBackUp++);
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
				if (csv.containsKey("Nfert_rate")) {
					a.setNfertRate(Utils.sToD(csv.get("Nfert_rate").get(i)));
				}
				if (csv.containsKey("Irrigated")) {
					String val = csv.get("Irrigated").get(i).trim();
					try {
						a.setIrrigated(Integer.parseInt(val) == 1);
					} catch (NumberFormatException e) {
						LOGGER.warn("Unrecognised Irrigated value '" + val
								+ "' for AFT " + label + ", defaulting to false (rainfed)");
						a.setIrrigated(false);
					}
				}
			if (csv.containsKey("Twin")) {
				String twin = csv.get("Twin").get(i).trim();
				a.setTwinLabel(twin.isEmpty() ? null : twin);
			}
			if (csv.containsKey("Twin_cost")) {
				a.setTwinCost(Utils.sToD(csv.get("Twin_cost").get(i)));
			}
			if (csv.containsKey("Receives_subsidy")) {
				String val = csv.get("Receives_subsidy").get(i).trim();
				try {
					double parsed = Double.parseDouble(val);
					if (parsed >= 0.0 && parsed <= 1.0) {
						a.setReceivesSubsidy(parsed);
					} else {
						LOGGER.warn("Receives_subsidy value '" + val + "' for AFT " + label
								+ " is outside [0,1], defaulting to 0.0");
						a.setReceivesSubsidy(0.0);
					}
				} catch (NumberFormatException e) {
					LOGGER.warn("Unrecognised Receives_subsidy value '" + val
							+ "' for AFT " + label + ", defaulting to 0.0");
					a.setReceivesSubsidy(0.0);
				}
			}

			}
		}

		if (ConfigLoader.config.use_twinned_afts && !csv.containsKey("Twin")) {
			LOGGER.fatal("use_twinned_afts is true but 'Twin' column is missing from AFTsMetaData");
		}
		if (ConfigLoader.config.use_twinned_cost && !csv.containsKey("Twin_cost")) {
			LOGGER.fatal("use_twinned_cost is true but 'Twin_cost' column is missing from AFTsMetaData");
		}
		if (ConfigLoader.config.use_price_explicit_giving_up && !csv.containsKey("Receives_subsidy")) {
			LOGGER.warn("use_price_explicit_giving_up is true but 'Receives_subsidy' column is missing "
					+ "from AFTsMetaData — all AFTs will default to not receiving subsidies; "
					+ "institutional model may still set them");
		}

		for (Aft a : hashAFTs.values()) {
			if (!a.hasTwin()) continue;
			String label = a.getLabel();
			String twin = a.getTwinLabel();

			if (twin.equals(label)) {
				LOGGER.warn("AFT '" + label + "' names itself as its own twin — clearing twin");
				a.setTwinLabel(null);
				continue;
			}

			Aft resolved = hashAFTs.get(twin);
			if (resolved == null) {
				LOGGER.warn("AFT '" + label + "' names twin '" + twin + "' which does not exist — clearing twin");
				a.setTwinLabel(null);
			} else if (!resolved.isInteract()) {
				LOGGER.warn("AFT '" + label + "' names twin '" + twin
						+ "' which is not an interacting AFT (type=" + resolved.getType() + ") — clearing twin");
				a.setTwinLabel(null);
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
//		loop for all AFT and put 0 if missing 
		hashAFTs.keySet().forEach(aftName -> {
			hashAgentNbr.putIfAbsent(aftName, 0);
		});

		if (Timestep.getTick() == 0) {
			hashAgentNbr.forEach((name, value) -> {
				hashAgentNbr_initialYear.put(name, value);
			});
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

	public static Aft getDeterministicRandomAFT(Collection<Aft> afts, long runSeed, int year, long cellId,
			long decisionContext, int drawIndex) {
		if (afts == null || afts.isEmpty()) {
			return null;
		}

		List<Aft> ordered = new ArrayList<>(afts);
		ordered.sort(Comparator.comparing(Aft::getLabel));
		int index = DeterministicRandom.randomInt(runSeed, year, DeterministicRandom.Process.NON_NEIGHBOR_PICK,
				cellId, decisionContext, drawIndex, ordered.size());
		return ordered.get(index);
	}

}
