package de.cesr.crafty.core.updaters;

import java.util.HashMap;
import java.util.Map;

import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
/**
 * Aggregates per-region service drivers (demand and utility weight) and tracks derived
 * time series (supply and demand–supply gaps) for every service.
 *
 * At each model tick/year, this updater:
 * - snapshots, for each region and each service, the current-year:
 *   demand ({@code Service.demands}),
 *   utility weight ({@code Service.weights});
 * - records the region’s realised supply from the corresponding {@link de.cesr.crafty.core.crafty.RegionalModelRunner}
 *   ({@code r.getRegionalSupply()}), and stores it in {@code supplys[region][service][year]};
 * - computes and stores a normalised gap in {@code gaps[region][service][year]} as:
 *   {@code gap = (demand - supply) / demand}, with a safe fallback to {@code 0} when {@code demand == 0}.
 *
 * The resulting maps ({@code demandByRegions}, {@code weightByRegions}, {@code gaps})
 * provide fast lookup for other components (e.g., marginal utility in the regional runner)
 * and support post-run diagnostics.
 *
 * Implementation notes:
 * - {@code supplys} and {@code gaps} are initialised once in the constructor with region/service keys to avoid
 *   repeated structure allocation during stepping.
 * - The per-year snapshots ({@code demandByRegions}, {@code weightByRegions}) are rebuilt
 *   each tick to reflect updates from other loaders/updaters.
 */


/**
 * @author Mohamed Byari
 *
 */
public class ServicesUpdater extends AbstractUpdater {
	private static final CustomLogger LOGGER = new CustomLogger(ServicesUpdater.class);

	private static Map<String, Map<String, Double>> demandByRegions = new HashMap<>(); // <regionName,serviceName,dmValue>
	private static Map<String, Map<String, Double>> weightByRegions = new HashMap<>();
	private static Map<String, Map<String, Map<Integer, Double>>> supplys = new HashMap<>();//<regionName,serviceName,year,value>
	private static Map<String, Map<String, Map<Integer, Double>>> gaps = new HashMap<>();

	@Override
	public void toSchedule() {
		modelRunner.scheduleRepeating(this);
	}

	public ServicesUpdater() {
		CellsLoader.regions.values().forEach(R -> {
			supplys.put(R.getName(), new HashMap<String, Map<Integer, Double>>());
			gaps.put(R.getName(), new HashMap<String, Map<Integer, Double>>());

			ServiceSet.getServicesList().forEach(serviceName -> {
				supplys.get(R.getName()).put(serviceName, new HashMap<Integer, Double>());
				gaps.get(R.getName()).put(serviceName, new HashMap<Integer, Double>());
			});
		});
	}

	@Override
	public void step() {
		// Update service demand and weight by region.
		demandByRegions.clear();
		weightByRegions.clear();
		RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(r -> {
			Map<String, Double> demand = new HashMap<>();
			Map<String, Double> weight = new HashMap<>();

			r.R.getServicesHash().forEach((serviceName, service) -> {
				int y = Timestep.getCurrentYear();
				double d = service.getDemands().get(Timestep.getCurrentYear());
				double su = r.getRegionalSupply().get(serviceName);
				demand.put(serviceName, d);
				weight.put(serviceName, service.getWeights().get(y));
				supplys.get(r.R.getName()).get(serviceName).put(y, su);
				gaps.get(r.R.getName()).get(serviceName).put(y, d != 0 ? (d - su) / d : 0);
			});

			demandByRegions.put(r.R.getName(), demand);
			weightByRegions.put(r.R.getName(), weight);
		});

		LOGGER.trace("Update_demandByRegions: " + demandByRegions + "\n" + "Update_weightByRegions: "
				+ weightByRegions);
	}

	public static Map<String, Map<String, Double>> getDemandByRegions() {
		return demandByRegions;
	}

	public static Map<String, Map<String, Double>> getWeightByRegions() {
		return weightByRegions;
	}

	public static Map<String, Map<String, Map<Integer, Double>>> getGaps() {
		return gaps;
	}

	public static Map<String, Map<String, Map<Integer, Double>>> getSupplys() {
		return supplys;
	}
	

}
