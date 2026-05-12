package de.cesr.crafty.core.dataLoader.serivces;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Region;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;

/**
 * Loads and initializes time-series taxes/subsidies applied to ecosystem
 * services.
 *
 * This loader reads per-service tax/subsidy trajectories (one value per year)
 * from CSV inputs and assigns them to each
 * {@link de.cesr.crafty.core.crafty.Region}'s
 * {@link de.cesr.crafty.core.crafty.Service} object via
 * {@link de.cesr.crafty.core.crafty.Service#getTaxes_subsidies()}.
 *
 * These taxes/subsidies are later used in the regional model runner to compute
 * the service-level incentive term (together with demand-supply gaps and
 * calibration factors) that influences the utility and competitiveness of AFTs.
 *
 * Input discovery: - If
 * {@code ConfigLoader.config.services_taxes_subsidies_path} is set and
 * non-empty: - If it points to a file, that file is used. - If it points to a
 * directory, the loader searches within it for a region-specific file
 * containing the token "_;RegionName;_;". - Otherwise, it falls back to the
 * scenario structure and searches under {@code services_taxes_subsidies/} for a
 * file matching the region token.
 *
 * Initialization behaviour: - {@link #initializeTaxes_subsidies()} iterates
 * over all regions and calls {@link #initializeTS(Region)}. - For a given
 * region, the CSV file is parsed using {@link CsvProcessors#ReadAsaHash(Path)}
 * where keys correspond to service names and values to the yearly vector. -
 * Only services present in {@link ServiceSet#getServicesList()} are applied;
 * unknown columns are ignored. - If no taxes/subsidies file is found for a
 * region, {@link #useDefaultTS(Region)} assigns a constant value of {@code 0.0}
 * for all services and all years, ensuring the model remains runnable without
 * optional policy inputs.
 */

/*
 * @author Mohamed Byari
 *
 */

public class TaxesSubsidiesLoader {
	private static final CustomLogger LOGGER = new CustomLogger(TaxesSubsidiesLoader.class);

	public static void initializeTaxes_subsidies() {
		CellsLoader.regions.values().forEach(r -> {
			initializeTS(r);
		});
	}

	private static Path tsPath(Region R) {
		Path path;
		String ts = ConfigLoader.config.services_taxes_subsidies_path;
		if (ts != null && !ts.isEmpty()) {
			if (Paths.get(ts).toFile().isFile()) {
				path = Paths.get(ts);
			} else {
				ArrayList<Path> directory = PathTools.findAllFilePaths(Paths.get(ts));
				path = PathTools.fileFilter(directory, "_" + R.getName()).get(0);
			}
		} else {
			try {
				path = PathTools.fileFilter(ProjectLoader.getScenario(), PathTools.asFolder("services_taxes_subsidies"),
						"_" + R.getName()).get(0);
			} catch (NullPointerException e) {
				LOGGER.warn("No services_taxes_subsidies file fund for region: |" + R.getName() + "|");
				return null;
			}
		}
		return path;
	}

	private static void initializeTS(Region R) {
		Path path = tsPath(R);
		if (path != null) {
			Map<String, List<String>> hashTS = CsvProcessors.ReadAsaHash(path);
			LOGGER.info("Update taxes_subsidies for [" + R.getName() + "]: " + path);
			hashTS.forEach((serviceName, vect) -> {
				if (ServiceSet.getServicesList().contains(serviceName)) {
					ConcurrentHashMap<Integer, Double> dv = new ConcurrentHashMap<>();
					for (int i = 0; i < Timestep.getSize(); i++) {
						if (i < vect.size()) {
							dv.put(Timestep.getStartYear() + i, Utils.sToD(vect.get(i)));
						}
					}
					R.getServicesHash().get(serviceName).getTaxes_subsidies().clear();
					R.getServicesHash().get(serviceName).setTaxes_subsidies(dv);
					LOGGER.trace("taxes_subsidies for [" + serviceName + "]: "
							+ R.getServicesHash().get(serviceName).getTaxes_subsidies());
				}
			});
		} else {
			ConfigLoader.config.consider_subsidies_taxes = false;
			LOGGER.info("Taxes_subsidies file not fund for |" + R.getName()
					+ "| default value will use: Taxes_subsidies = 0 for all services");
			useDefaultTS(R);
		}
		R.getServicesHash().values().forEach(s -> {
			LOGGER.trace("Taxes_subsidies: " + s.getName() + "= " + s.getTaxes_subsidies());
		});
	}

	private static void useDefaultTS(Region R) {
		ServiceSet.getServicesList().forEach((serviceName) -> {
			ConcurrentHashMap<Integer, Double> dv = new ConcurrentHashMap<>();
			for (int i = 0; i < Timestep.getSize(); i++) {
				dv.put(i + Timestep.getStartYear(), 0.);
			}
			R.getServicesHash().get(serviceName).setTaxes_subsidies(dv);
		});
	}

}
