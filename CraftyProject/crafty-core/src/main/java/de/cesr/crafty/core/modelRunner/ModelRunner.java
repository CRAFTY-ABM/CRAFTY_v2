package de.cesr.crafty.core.modelRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Service;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceDemandLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.output.Listener;
import de.cesr.crafty.core.output.Tracker;
import de.cesr.crafty.core.updaters.AftsUpdater;
import de.cesr.crafty.core.updaters.CapitalUpdater;
import de.cesr.crafty.core.updaters.Capital_Degradation_Updater;
import de.cesr.crafty.core.updaters.CellBehaviourUpdater;
import de.cesr.crafty.core.updaters.FlagUpdater;
import de.cesr.crafty.core.updaters.LandMaskUpdater;
import de.cesr.crafty.core.updaters.ProductionCostUpdater;
import de.cesr.crafty.core.updaters.RegionsModelRunnerUpdater;
import de.cesr.crafty.core.updaters.ServicesUpdater;
import de.cesr.crafty.core.updaters.SubsidyUpdater;
import de.cesr.crafty.core.updaters.SupplyUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.PathTools;
import de.cesr.crafty.core.utils.graphics.ChartExporter;

/**
 * Main CRAFTY model runner.
 *
 * This class assembles and executes the full simulation workflow by:
 * - Initializing scenario context and core datasets (services, capitals, AFTs, cells).
 * - Building the ordered schedule of {@link de.cesr.crafty.core.modelRunner.ModelState} components
 *   that run at every simulation step/year.
 * - Running the step loop from {@link Timestep#getStartYear()} to {@link Timestep#getEndtYear()}.
 * - Optionally performing an initial demand–supply equilibrium calibration and exporting outputs.
 *
 * High-level lifecycle:
 * 1) {@link #start()}:
 *    - Sets scenario and loads service metadata.
 *    - Instantiates the main updaters/loaders (capitals, AFTs, cells) and performs a first capital update.
 *    - Builds the schedule in a fixed order that defines the model execution pipeline per year.
 *
 * 2) {@link #initialzeRun()}:
 *    - Creates/derives the output directory structure.
 *    - Optionally redirects logs to a file in the output folder.
 *    - Exports the resolved configuration snapshot to disk.
 *    - Optionally performs initial demand–supply equilibrium calibration.
 *
 3) {@link #run()}:
 *   - Executes {@link AbstractModelRunner#step()} once per year.
 *   - Exports post-run charts/plots (PNG/PDF depending on configuration).
 *
 * Scheduling / execution order:
 * The order in {@link #start()} is intentional. Each component may depend on outputs of earlier components,
 * e.g.:
 * - {@link ServicesUpdater} updates demands/weights/taxes (inputs to utility and competitiveness).
 * - {@link CapitalUpdater} and {@link Capital_Degradation_Updater} affect capitals used in productivity and competitiveness.
 * - {@link AftsUpdater} updates AFT production/behaviour and land taxes/subsidies.
 * - {@link SupplyUpdater} and {@link Listener} compute and persist outputs derived from current state.
 * - {@link RegionsModelRunnerUpdater} executes regional model logic for the current year.
 * - {@link Timestep} advances the simulation year counter.
 *
 * Initial demand–supply equilibrium calibration:
 * If enabled, {@link #demandEquilibrium()} computes a calibration factor per service (and per region),
 * based on baseline supply and initial demands, and then rescales demands by dividing by that factor.
 * Services with zero initial supply are tracked, and their calibration factors are replaced with the
 * across-region average for that service.
 *
 * Outputs:
 * - The model writes CSV outputs through {@link Listener} / {@link ListenerByRegion}
 *   and optionally supply tracking via {@link Tracker}.
 * - At the end of the run, charts may be exported to a "plots" folder using {@link ChartExporter}.
 *
 * Notes / assumptions:
 * - This runner keeps references to core components as static fields to allow access from other parts
 *   of the code base. This design assumes a single active run at a time.
 * - {@link #start()} clears and rebuilds the schedule explicitly via {@link #getScheduled()} to enforce
 *   deterministic execution order.
 */
/**
 * @author Mohamed Byari
 *
 */

public class ModelRunner extends AbstractModelRunner {

	public static CellsLoader cellsSet;
	public static CapitalUpdater capitalUpdater;
	public static AftsUpdater aftsUpdater;
	public static ProductionCostUpdater productionCostUpdater;
	public static SubsidyUpdater subsidyUpdater;
	private static Capital_Degradation_Updater capital_Degradation_Updater;
	public RegionsModelRunnerUpdater regionsModelRunnerUpdater;

	public void start() {
		

		ServiceSet.loadServiceList();
		capitalUpdater = new CapitalUpdater();
		aftsUpdater = new AftsUpdater();
		cellsSet = new CellsLoader();
		capitalUpdater.step();
		productionCostUpdater = new ProductionCostUpdater();
		capital_Degradation_Updater = new Capital_Degradation_Updater();
		subsidyUpdater = new SubsidyUpdater();
		regionsModelRunnerUpdater=new RegionsModelRunnerUpdater();
		getScheduled().clear();
		getScheduled().add(new FlagUpdater());
		getScheduled().add(new ServicesUpdater());
		getScheduled().add(capitalUpdater);
		getScheduled().add(productionCostUpdater);
		getScheduled().add(subsidyUpdater);
		getScheduled().add(aftsUpdater);
		getScheduled().add(new CellBehaviourUpdater());
		getScheduled().add(new LandMaskUpdater());
		getScheduled().add(capital_Degradation_Updater);
		getScheduled().add(new SupplyUpdater());
		getScheduled().add(new Listener());
		getScheduled().add(new Tracker());
		getScheduled().add(regionsModelRunnerUpdater);
		getScheduled().add(new Timestep());
	} 

	public void initialzeRun() {
		
		PathTools.writeFile(ConfigLoader.config.output_folder_name + File.separator + "config.txt",
				Listener.exportConfigurationFile(), false);
		demandEquilibrium();
	}

	public void run() {
		for (int i = Timestep.getStartYear(); i <= Timestep.getEndtYear(); i++) {
			step();
		}
		exportChartsPlots();
		CustomLogger.shutdownLogger();
	}

	public static void demandEquilibrium() {
		if (ConfigLoader.config.initial_demand_supply_equilibrium) {
			RegionalDemandEquilibrium_calculation();
			RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(RegionalRunner -> {
				RegionalRunner.R.getServicesHash().forEach((ns, s) -> {
					s.getDemands().forEach((year, v) -> {
						s.getDemands().put(year, v / s.getCalibration_Factor());
					});
				});
			});
			initialTotalDSEquilibriumListrner();
			ServiceDemandLoader.aggregateRegionalToWorldServiceDemand();
		}
	}

	private static void RegionalDemandEquilibrium_calculation() {
		ModelRunner.capitalUpdater.step();
		// Initial capital degradation
		capital_Degradation_Updater.step();
		// Calculate EQ
		// Remumber the service has 0 supply hashMap<regionName, List<servicesNames>>
		// Calculate the average EQ
		// go to 0 supply services and repleas them with the average

		RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(RegionalRunner -> {
			ServiceSet.NoInitialSupplyServices.put(RegionalRunner.R.getName(), new ArrayList<>());
			RegionalRunner.initialDSEquilibriumFactorCalculation();
		});

		// calculate the average
		HashMap<String, Double> averageEQ = new HashMap<>();
		RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(RegionalRunner -> {
			ServiceSet.getServicesList().forEach(serviceName -> {
				double av = RegionalRunner.R.getServicesHash().get(serviceName).getCalibration_Factor()
						/ RegionsModelRunnerUpdater.regionsModelRunner.size();
				averageEQ.merge(serviceName, av, Double::sum);
			});
		});

		// comeback to NoInitialSupplyServices-EQ with the averageEQ
		RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(RegionalRunner -> {
			ServiceSet.getServicesList().forEach(serviceName -> {
				if (ServiceSet.NoInitialSupplyServices.get(RegionalRunner.R.getName()).contains(serviceName)) {
					RegionalRunner.R.getServicesHash().get(serviceName)
							.setCalibration_Factor(averageEQ.get(serviceName));
				}
			});

		});

	}

	private static void initialTotalDSEquilibriumListrner() {
		ServiceSet.worldService.forEach((serviceName, service) -> {
			RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(RegionalRunner -> {
				Service s = RegionalRunner.R.getServicesHash().get(serviceName);
				int i = ServiceSet.getServicesList().indexOf(serviceName);
				RegionalRunner.listner.DSEquilibriumListener[i + 1][0] = serviceName;
				RegionalRunner.listner.DSEquilibriumListener[i + 1][1] = String.valueOf(s.getCalibration_Factor());
			});
		});
	}

	public static void exportChartsPlots() {
		if (ConfigLoader.config.generate_charts_plots_PNG || ConfigLoader.config.generate_charts_plots_PDF) {
			String path = PathTools.makeDirectory(ConfigLoader.config.output_folder_name + File.separator + "plots");
			Listener.servicedemandHash.forEach((serviceName, serviceHash) -> {
				ChartExporter.createAndSaveChartAsPNG(serviceHash, Timestep.getStartYear(), serviceName,
						path + File.separator + serviceName);
			});
			Map<String, String> hashColors = new HashMap<>();
			AFTsLoader.getAftHash().forEach((name, aft) -> {
				hashColors.put(name, aft.getColor());
			});

			ChartExporter.createAndSaveChartAsPNG(Listener.compositionAftHash, hashColors, Timestep.getStartYear(),
					"LandUseTrends", path + File.separator + "Land_use_trends");
		}
	}

}
