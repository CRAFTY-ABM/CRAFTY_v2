package de.cesr.crafty.core.updaters;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.ManagerTypes;
import de.cesr.crafty.core.crafty.Region;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.csv.CsvReadOptions;

/**
 * Scheduled updater responsible for time-varying AFT parameters (production, behaviour, and land policy inputs).
 *
 * This updater is executed once per model step (typically per simulated year) and applies any parameter
 * changes defined in the input directories. It supports:
 * - Updating AFT production parameters (productivity levels and sensitivity matrix).
 * - Updating AFT behavioural parameters (give-in/give-up distributions, probabilities).
 * - Loading land taxes/subsidies per AFT and region at the start of the simulation.
 *
 * Update logic:
 * - Parameter file paths are pre-resolved by {@link AFTsLoader} into maps keyed by AFT label and
 *   scenario/year selectors (e.g., "{scenario}|{year}" or "default_production|{year}").
 * - At each step, {@link #updateProduction(String)} checks whether a year-specific file exists for the
 *   current year and applies it to the active AFTs only.
 *
 * Input formats:
 * - Production files are read as a simple table; rows are services, and the "Production" column defines
 *   the productivity level for each service. The sensitivity matrix is loaded from the same file and
 *   populates {@code service -> (capital -> exponent)} in {@link Aft#getSensByService()}.
 * - Behaviour files are read as a key/value style CSV and populate giving-in/up distribution parameters
 *   and noise ranges.
 * - Land taxes/subsidies are loaded per region and stored as a time series (year -> value) in each AFT.
 *   If no file is found, a default value of 0 is applied for all years.
 *
 * Notes:
 * - Production and sensitivity updates use the global service and capital lists ({@link ServiceSet} and
 *   {@link CapitalUpdater}).
 * - This updater expects model time to be configured via {@link Timestep} and scenario via {@link ProjectLoader}.
 */
/**
 * @author Mohamed Byari
 *
 */
public class AftsUpdater extends AbstractUpdater {
	private static final CustomLogger LOGGER = new CustomLogger(AftsUpdater.class);
	public AFTsLoader AFtsSet;

	public AftsUpdater() {
		AFtsSet = new AFTsLoader();
	}

	@Override
	public void toSchedule() {
		modelRunner.scheduleRepeating(this);
	}

	@Override
	public void step() {
		updatePorB("production");
		updatePorB("agents");
		if (Timestep.getTick() == 0) {
			CellsLoader.regions.values().forEach(R -> {
				load_land_taxes_subsidies(R);
			});
		}
		update_AFT_capital_adjustments_values();

	}

	private void update_AFT_capital_adjustments_values() {
		// Clear AFT_capital_adjustments for each AFT
		// Find if there is any file for capital adjusments in YAML
		// if No,check the default
		// if No, ignore -> for all afts -> capital_adjustments=null
		// if yes, read the path and assosiete each AFt with the capitals adjsument
		AFTsLoader.getActivateAFTsHash().values().forEach(a -> {
			a.getCapital_adjustments().clear();
		});

		Path path = null;
		ArrayList<Path> directory;
		if (Paths.get(ConfigLoader.config.AFT_capital_adjustments).toFile().isDirectory()) {
			directory = PathTools.findAllFilePaths(Paths.get(ConfigLoader.config.AFT_capital_adjustments));
		} else {
			directory = PathTools.fileFilter("AFT_capital_adjustments", ProjectLoader.getScenario(),
					Timestep.getCurrentYear() + ".csv");
		}
		if (directory != null) {
			ArrayList<Path> ps = PathTools.fileFilter(directory, Timestep.getCurrentYear() + ".csv");
			if (ps != null && !ps.isEmpty()) {
				path = ps.get(0);
			}
		}
		if (path != null) {
			Map<String, Map<String, String>> csv = CsvTools.csvToColumnRowValue(path);
			csv.forEach((aftName, capitals) -> {
				capitals.forEach((capitalName, value) -> {
					if (AFTsLoader.getActivateAFTsHash().keySet().contains(aftName)
							&& CapitalUpdater.getCapitalsList().contains(capitalName)) {
					}
					AFTsLoader.getActivateAFTsHash().get(aftName).getCapital_adjustments().put(capitalName,
							Utils.sToD(value));
				});
			});
			adjust_cell_capitals();
		}
	}

	public static void adjust_cell_capitals() {
		LOGGER.info("adjust_cell_capitals");
		CellsLoader.hashCell.values().parallelStream().forEach(c -> {
			Aft a = c.getOwner();
			if (a != null) {
				if (!a.getCapital_adjustments().isEmpty()) {
					CapitalUpdater.getCapitalsList().forEach(cn -> {
						if (a.getCapital_adjustments().get(cn) != null) {
							c.getCapitals().put(cn,
									Math.max(c.getCapitals().get(cn) * (1 + a.getCapital_adjustments().get(cn)), 0));
						}
					});
				}
			}
		});
	}

	private void updatePorB(String pORb) {
		AFTsLoader.getActivateAFTsHash().forEach((aftName, aft) -> {
			if (aft.getType() != ManagerTypes.Abandoned) {
				Path p = AFTsLoader.getPath(pORb, aftName);
				if (p != null) {
					if (pORb.equals("production")) {
						updateAFTProduction(aft, p);
					} else {
						updateAFTBehevoir(aft, p);
					}
					LOGGER.info("Update " + pORb + " parametres for AFT: " + aftName + " using: " + p);
				} else {
					LOGGER.trace("AFT " + pORb + " parameters not updated (no folder found) for: " + aftName);
				}
			}
		});
	}

	public static void updateAFTProduction(Aft a, Path path) {
		String[][] m = CsvTools.csvReader(path);
		for (int i = 0; i < m.length; i++) {
			if (!m[i][0].isBlank()) {
				if (ServiceSet.getServicesList().contains(m[i][0])) {
					a.getProductivityLevel().put(m[i][0], Utils.sToD(m[i][Utils.indexof("Production", m[0])]));
				} else {
					LOGGER.warn("(" + m[i][0] + ")  is not existe in Services List, will be ignored");
				}
			}
		}
		updateSensitivty(a, path);
	}

	private static void updateSensitivty(Aft a, Path path) {
//		LOGGER.info("updateSensitivty for: " + a.getLabel() + " using : " + path);
		try {
			CsvReadOptions options = CsvReadOptions.builder(path.toFile()).separator(',').build();
			Table T = Table.read().usingOptions(options);
			// Optional: assume first column contains service names
			var serviceCol = T.stringColumn(0);

			ServiceSet.getServicesList().forEach(Sn -> {
				int row = serviceCol.indexOf(Sn);
				if (row < 0) {
					System.out.println("Service not found in CSV: " + Sn);
					return;
				}

				a.getSensByService().putIfAbsent(Sn, new ConcurrentHashMap<>());

				CapitalUpdater.getCapitalsList().forEach(Cn -> {
					Object s = T.column(Cn).get(row);

					if (s instanceof Number n) {
						a.getSensByService().get(Sn).put(Cn, n.doubleValue());
					} else {
						System.out.println("ERROR reading Sensitivity value for service=" + Sn + " capital=" + Cn
								+ " (value=" + s + ", type=" + (s == null ? "null" : s.getClass().getName()) + ")");
					}
				});
			});

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void updateAFTBehevoir(Aft a, Path path) {
		Map<String, List<String>> reder = CsvProcessors.ReadAsaHash(path);
		a.setGiveInMean(Utils.sToD(reder.get("givingInDistributionMean").get(0)));
		a.setGiveUpMean(Utils.sToD(reder.get("givingUpDistributionMean").get(0)));
		a.setGiveInSD(Utils.sToD(reder.get("givingInDistributionSD").get(0)));
		a.setGiveUpSD(Utils.sToD(reder.get("givingUpDistributionSD").get(0)));
//		a.setServiceLevelNoiseMin(Utils.sToD(reder.get("serviceLevelNoiseMin").get(0)));
//		a.setServiceLevelNoiseMax(Utils.sToD(reder.get("serviceLevelNoiseMax").get(0)));
		a.setGiveUpProbabilty(Utils.sToD(reder.get("givingUpProb").get(0)));
	}

	private void load_land_taxes_subsidies(Region R) {
		Path path = landTSPath(R);
		if (path == null) {
			LOGGER.info("AFT lan_taxes_subsidies file not fund for |" + R.getName()
					+ "| default value will use: land_taxes_subsidies = 0 for all AFts");
			ConfigLoader.config.consider_subsidies_taxes = false;
			return;
		}
		Map<String, List<String>> hashTS = CsvProcessors.ReadAsaHash(path);
		LOGGER.info("Update land_taxes_subsidies for [" + R.getName() + "]: " + path);
		hashTS.forEach((AFTName, vect) -> {
			if (AFTsLoader.getActivateAFTsHash().keySet().contains(AFTName)) {
				ConcurrentHashMap<Integer, Double> dv = new ConcurrentHashMap<>();
				for (int i = 0; i < Timestep.getSize(); i++) {
					if (i < vect.size()) {
						dv.put(Timestep.getStartYear() + i, Utils.sToD(vect.get(i)));
					}
				}
				AFTsLoader.getActivateAFTsHash().get(AFTName).getLand_taxes_subsidies().clear();
				AFTsLoader.getActivateAFTsHash().get(AFTName).setLand_taxes_subsidies(dv);

				LOGGER.trace("land_taxes_subsidies for [" + AFTName + "]: "
						+ AFTsLoader.getActivateAFTsHash().get(AFTName).getLand_taxes_subsidies());
			}
		});

	}

	private static Path landTSPath(Region R) {
		Path path;
		String ts = ConfigLoader.config.land_taxes_subsidies_path;
		if (ts != null && !ts.isEmpty()) {
			if (Paths.get(ts).toFile().isFile()) {
				path = Paths.get(ts);
			} else {// if is folder
				ArrayList<Path> directory = PathTools.findAllFilePaths(Paths.get(ts));
				path = PathTools.fileFilter(directory, "_" + R.getName()).get(0);
			}
		} else {
			try {
				path = PathTools.fileFilter(ProjectLoader.getScenario(), PathTools.asFolder("land_taxes_subsidies"),
						"_" + R.getName()).get(0);
			} catch (NullPointerException e) {
				LOGGER.warn("No Land_taxes_subsidies file fund for region: |" + R.getName() + "|");
				return null;
			}
		}
		return path;
	}

}
