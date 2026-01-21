package de.cesr.crafty.core.output;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.crafty.Region;
import de.cesr.crafty.core.crafty.Service;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.RegionsModelRunnerUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.general.Utils;

/**
 * Region-level output collector for multi-region CRAFTY runs.
 *
 * While {@link Listener} writes global (world/total) aggregates, this class
 * records the same core indicators per {@link Region} and exports them into a
 * region-specific output subfolder.
 *
 * For each simulated year, it can export: - Regional AFT composition
 * (cells/agents per AFT). - Regional service supply vs. demand time series. -
 * Service calibration factors / demand–supply equilibrium metadata (per
 * service). - Mean utilities per AFT for the region (based on the regional
 * model runner statistics).
 *
 * Files are written under: {output_folder}/region_{RegionName}/
 *
 * Notes: - This class is intended to be used only when the model is running
 * with multiple regions (see {@link CellsLoader#regions}).
 * {@link #exportFiles(int, ConcurrentHashMap)} will do nothing for
 * single-region runs. - Data are stored in lightweight String[][] tables and
 * written as CSV at each export step. -
 * {@link #fillDSEquilibriumListener(ConcurrentHashMap)} should be called once
 * the region services are initialized so calibration factors are captured.
 */

public class ListenerByRegion {
	Region R;
	private String[][] compositionAftListener;
	private String[][] servicedemandListener;
	public String[][] DSEquilibriumListener;
	public String[][] averageUtilities;

	public ListenerByRegion(Region R) {
		this.R = R;
	}

	public void initializeListeners() {
		compositionAftListener = new String[Timestep.getSize() + 1][AFTsLoader.getAftHash().size() + 1];
		servicedemandListener = new String[Timestep.getSize() + 1][ServiceSet.getServicesList().size() * 2 + 1];
		averageUtilities = new String[compositionAftListener.length][compositionAftListener[0].length];

		servicedemandListener[0][0] = "Year";
		for (int i = 1; i < ServiceSet.getServicesList().size() + 1; i++) {
			servicedemandListener[0][i] = "Supply:" + ServiceSet.getServicesList().get(i - 1);
			servicedemandListener[0][i + ServiceSet.getServicesList().size()] = "Demand:"
					+ ServiceSet.getServicesList().get(i - 1);
		}
		compositionAftListener[0][0] = "Year";
		averageUtilities[0][0] = "Year";
		int j = 1;
		for (String label : AFTsLoader.getAftHash().keySet()) {
			compositionAftListener[0][j] = label;
			averageUtilities[0][j++] = label;
		}
		DSEquilibriumListener = new String[ServiceSet.getServicesList().size() + 1][2];
		DSEquilibriumListener[0][0] = "Service";
		DSEquilibriumListener[0][1] = "Calibration_Factor";

	}

	public void fillDSEquilibriumListener(ConcurrentHashMap<String, Service> ServiceHash) {
		for (int i = 0; i < ServiceSet.getServicesList().size(); i++) {
			DSEquilibriumListener[i + 1][0] = ServiceSet.getServicesList().get(i);
			DSEquilibriumListener[i + 1][1] = String
					.valueOf(ServiceHash.get(ServiceSet.getServicesList().get(i)).getCalibration_Factor());
		}
	}

	private void servicedemandListener(ConcurrentHashMap<String, Double> regionalSupply) {
		AtomicInteger m = new AtomicInteger(1);
		int y = Timestep.getTick() + 1;
		servicedemandListener[y][0] = String.valueOf(Timestep.getCurrentYear());
		ServiceSet.getServicesList().forEach(name -> {
			servicedemandListener[y][m.get()] = String.valueOf(regionalSupply.get(name));
			servicedemandListener[y][m.get() + ServiceSet.getServicesList().size()] = String
					.valueOf(R.getServicesHash().get(name).getDemands().get(Timestep.getCurrentYear()));
			m.getAndIncrement();
		});
	}

	private void compositionAFTListener() {
		int y = Timestep.getTick() + 1;
		compositionAftListener[y][0] = String.valueOf(Timestep.getCurrentYear());
		averageUtilities[y][0] = String.valueOf(Timestep.getCurrentYear());
		AFTsLoader.hashAgentNbrRegions.get(R.getName()).forEach((name, value) -> {
			compositionAftListener[y][Utils.indexof(name, compositionAftListener[0])] = String.valueOf(value);
		});
		if (y > 1) {
			AFTsLoader.getAftHash().forEach((name, aft) -> {
				if (RegionsModelRunnerUpdater.regionsModelRunner.get(R.getName()).getDistributionMeanY() != null) {
					averageUtilities[y - 1][Utils.indexof(name, averageUtilities[0])] = String
							.valueOf(RegionsModelRunnerUpdater.regionsModelRunner.get(R.getName()).getDistributionMean()
									.get(Timestep.getCurrentYear() - 1).get(aft));
				} else {
					averageUtilities[y - 1][Utils.indexof(name, averageUtilities[0])] = "null";
				}
			});
		}
	}

	private void CSVFilesWriter() {
		String dir = PathTools.makeDirectory(
				ConfigLoader.config.output_folder_name + File.separator + "region_" + R.getName() + File.separator);
		if (ConfigLoader.config.generate_output_files) {
			Path aggregateAFTComposition = Paths.get(dir + "region_" + R.getName() + "-AggregateAFTComposition.csv");
			CsvTools.writeCSVfile(compositionAftListener, aggregateAFTComposition);
			Path aggregateServiceDemand = Paths.get(dir + "region_" + R.getName() + "-AggregateServiceDemand.csv");
			CsvTools.writeCSVfile(servicedemandListener, aggregateServiceDemand);
			Path DSEquilibriumPath = Paths.get(dir + "region_" + R.getName() + "-DemandServicesEquilibrium.csv");
			CsvTools.writeCSVfile(DSEquilibriumListener, DSEquilibriumPath);
			Path averageUtilitiesPath = Paths.get(dir + "region_" + R.getName() + "-AverageUtilities.csv");
			CsvTools.writeCSVfile(averageUtilities, averageUtilitiesPath);
		}
	}

	public void exportFiles(ConcurrentHashMap<String, Double> regionalSupply) {
		if (ConfigLoader.config.generate_output_files && CellsLoader.regions.size() > 1) {
			servicedemandListener(regionalSupply);
			compositionAFTListener();
			Tracker.trackSupply(R.getName());
			CSVFilesWriter();
		}
	}
}
