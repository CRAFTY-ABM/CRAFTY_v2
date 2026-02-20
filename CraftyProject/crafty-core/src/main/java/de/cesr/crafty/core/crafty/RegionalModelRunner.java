package de.cesr.crafty.core.crafty;

import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.afts.AftCategorised;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.output.Listener;
import de.cesr.crafty.core.output.ListenerByRegion;
import de.cesr.crafty.core.updaters.LandMaskUpdater;
import de.cesr.crafty.core.updaters.SeedUpdater;
import de.cesr.crafty.core.updaters.ServicesUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.analysis.StepProfiler;
import de.cesr.crafty.core.utils.general.Utils;

/**
 * Executes the CRAFTY decision cycle for a single region and a single simulation year.
 *
 * {@code RegionalModelRunner} encapsulates all region level calculations required to:
 * - Compute supply (per service) from cell-level productivity.
 * - Derive marginal utilities from demand–supply gaps (optionally averaged per cell).
 * - Apply region-level service taxes/subsidies and AFT-level land taxes/subsidies (cached on each AFT).
 * - Compute cell utilities, distribution statistics (mean utility per AFT), and utility ranges.
 * - Trigger behavioural abandonment (give-up), reallocation of unmanaged land, and competition-driven
 *   land-use change among AFTs.
 * - Export per-region outputs (time series, equilibrium factors, trackers) via {@link ListenerByRegion}.
 *
 * Key state held by this runner:
 * - {@link #regionalSupply}: region-wide total supply per service (aggregated from cells).
 * - {@link #marginal}: marginal utility signal per service derived from demand–supply gaps.
 * - {@link #serviceTax}: calibrated service tax/subsidy signal used in utility calculation.
 * - {@link #distributionMean}: per-year map of mean utility per AFT (computed from cell utilities).
 * - {@link #maxUtility}/{@link #minUtility}: utility range used for normalised-utility behaviour modes.
 *
 * Performance:
 * Several heavy computations are parallelised (e.g., supply aggregation, utility assignment, statistics).
 * A lightweight {@link StepProfiler} can be enabled via configuration to report per-section timings.
 *
 * Typical yearly sequence ({@link #step(int)}):
 * 1) Export current outputs (region time series and diagnostics).
 * 2) Compute marginal utilities (demand–supply gap signal).
 * 3) Update service-level taxes/subsidies and cache land taxes on AFTs.
 * 4) Compute utilities for all cells.
 * 5) Compute distribution means (per AFT) and min/max utilities.
 * 6) Apply give-up (abandonment) to a seeded subset of cells.
 * 7) Attempt takeover of unmanaged cells.
 * 8) Run competition on a seeded subset of cells (optionally in multiple sub-iterations per tick).
 * 9) Update AFT cell counts for the region.
 *
 * Notes:
 * - Seed selection is delegated to {@link SeedUpdater} to support deterministic/reproducible subsets
 *   (ranking or hashed coordinate selection, depending on configuration).
 * - This runner assumes that region cells and services have been initialized before stepping.
 */

/**
 * @author Mohamed Byari
 *
 */

public class RegionalModelRunner {
	private static final CustomLogger LOGGER = new CustomLogger(RegionalModelRunner.class);
	private ConcurrentHashMap<String, Double> regionalSupply;
	private ConcurrentHashMap<String, Double> marginal = new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, Double> serviceTax = new ConcurrentHashMap<>();
	private ConcurrentHashMap<Integer, ConcurrentHashMap<Aft, Double>> distributionMean = new ConcurrentHashMap<>();

	private double maxUtility, minUtility;
	public Region R;

	public ListenerByRegion listner;

	public RegionalModelRunner(String regionName) {
		R = CellsLoader.regions.get(regionName);
		listner = new ListenerByRegion(R);
		listner.initializeListeners();

		for (int i = Timestep.getStartYear(); i < Timestep.getEndtYear() + 1; i++) {
			distributionMean.put(i, new ConcurrentHashMap<>());
		}
	}

	private void computeRegionsSupply() {
		setRegionalSupply(new ConcurrentHashMap<>());
		R.getCells().values().parallelStream()/**/.forEach(c -> {
			for (int i = 0; i < ServiceSet.getServicesList().size(); i++) {
				getRegionalSupply().merge(ServiceSet.getServicesList().get(i), c.getCurrentProd()[i], Double::sum);
			}
		});
	}

	private void servicesTax() {
		serviceTax.clear();
		ServiceSet.getServicesList().forEach(serviceName -> {
			if (Timestep.getStartYear() == Timestep.getCurrentYear()) {
				serviceTax.put(serviceName, 0.);
			} else {
				double gap = ServicesUpdater.getGaps().get(R.getName()).get(serviceName)
						.get(Timestep.getStartYear() + 1);
				double calib = gap != 0 ? Math.abs(gap) : 1;
				double tx = R.getServicesHash().get(serviceName).getTaxes_subsidies().get(Timestep.getCurrentYear());
				serviceTax.put(serviceName, 100 * calib * tx);
			}
		});
	}

	private void landTS() {
		AFTsLoader.getActivateAFTsHash().forEach((aftName, aft) -> {
			if (Timestep.getTick() == 0) {
				aft.setCachedLandTax(0.);
			} else {
				double initialAverageUtility = distributionMean.get(Timestep.getStartYear()).get(aft);
				double calib = initialAverageUtility != 0 ? Math.abs(initialAverageUtility) : 1;
				double tx = aft.getLand_taxes_subsidies().get(Timestep.getCurrentYear() + 1) != null
						? aft.getLand_taxes_subsidies().get(Timestep.getCurrentYear() + 1)
						: 0;
				aft.setCachedLandTax(100 * calib * tx);
			}

		});
	}

	private void utilitytyForAll() {

		// Cache everything locally (cheaper than repeated virtual calls)
		final var cells = R.getCells(); // ConcurrentHashMap<?, Cell>

		// Snapshot services once
		final var servicesList = ServiceSet.getServicesList();
		final String[] services = servicesList.toArray(new String[0]);

		// Precompute coeff[s] = serviceTax + marginal ONCE (per step/year/region)
		final double[] coeff = new double[services.length];
		final var serviceTax = getServiceTax();
		final var marginal = getMarginal();
		for (int i = 0; i < services.length; i++) {
			final String s = services[i];
			coeff[i] = serviceTax.get(s) + marginal.get(s);
		}

		// Faster than values().parallelStream() in many cases for CHM:
		// parallelismThreshold: tune (e.g. 50_000 or 100_000)
		cells.forEach(100_000, (k, c) -> {
			final Aft a = c.owner;
			if (a == null || !a.isInteract()) {
				c.setcCurrentUtility(0.0);
				return;
			}

			double u = a.getCachedLandTax();
			for (int i = 0; i < services.length; i++) {
				u += coeff[i] * c.productivity(a, services[i]);
			}
			c.setcCurrentUtility(u);
		});
	}

	private void computeDistributionMean() {
		// Calculate the mean distribution
		R.getCells().values()/**/.parallelStream().forEach(c -> {
			if (c.getOwner() != null) {
				distributionMean.get(Timestep.getCurrentYear()).merge(c.getOwner(),
						c.getCurrentUtility()
								/ AFTsLoader.hashAgentNbrRegions.get(R.getName()).get(c.getOwner().getLabel()),
						Double::sum);
			}
		});
		AFTsLoader.getActivateAFTsHash().values()
				.forEach(a -> distributionMean.get(Timestep.getCurrentYear()).computeIfAbsent(a, key -> 0.));

		StringJoiner joiner = new StringJoiner(", ", "Region: [" + R.getName() + "]: Distribution Mean: {", "}");
		for (Aft a : distributionMean.get(Timestep.getCurrentYear()).keySet()) {
			joiner.add(a.getLabel() + "= " + distributionMean.get(Timestep.getCurrentYear()).get(a));
		}
		LOGGER.info(joiner.toString());
	}

	private void computeMaxMinUtility() {
		DoubleSummaryStatistics stats = R.getCells().values().parallelStream().mapToDouble(Cell::getCurrentUtility)
				.summaryStatistics();

		if (stats.getCount() == 0) {
			setMinUtility(0.0);
			setMaxUtility(0.0);
			return;
		}

		setMinUtility(stats.getMin());
		setMaxUtility(stats.getMax());
	}

	private void computeMarginal() {
		getRegionalSupply().forEach((serviceName, serviceSupply) -> {
//			Service s = R.getServicesHash().get(serviceName);
			double serviceDemand = ServicesUpdater.getDemandByRegions().get(R.getName()).get(serviceName); // s.getDemands().get(year);
			double serviceWeight = ServicesUpdater.getWeightByRegions().get(R.getName()).get(serviceName);// s.getWeights().get(year)
			double marg = ServiceSet.getPenalise_Oversupply().get(serviceName) ? (serviceDemand - serviceSupply)
					: Math.max((serviceDemand - serviceSupply), 0);

			marg = marg * serviceWeight;
			if (ConfigLoader.config.averaged_residual_demand_per_cell) {
				marg = marg / R.getCells().size();
			}
			if (ConfigLoader.config.use_relative_marginal_utility) {
				marg = serviceDemand != 0 ? marg / serviceDemand : marg;
			}
			getMarginal().put(serviceName, marg);
		});
	}

	private void takeOverUnmanageCells() {
		LOGGER.trace("Region: [" + R.getName() + "] Take over unmanaged cells & Launching the competition process...");
		R.getUnmanageCellsR().parallelStream().forEach(c -> {
			if (Math.random() < ConfigLoader.config.takeOverUnmanageCells_percentage) {
				Competitiveness.competition(c, this);
				if (c.getOwner() != null && !c.getOwner().isAbandoned()) {
					R.getUnmanageCellsR().remove(c);
					c.setOwnerLifeCounter(0);
					Listener.landUseChangeCounter.getAndIncrement();
				}
			}
		});
	}

	private void takeOverMaskedCellByCategories() {
		if (LandMaskUpdater.cellsMasked.keySet().contains(R.getName())) {
			LandMaskUpdater.cellsMasked.get(R.getName()).forEach((categoryName, cells) -> {
				cells.parallelStream().forEach(c -> {
					Aft a = Competitiveness.mostCompetitiveAgent(c, AftCategorised.aftCategories.get(categoryName),
							this);
					c.setOwner(a);
					LandMaskUpdater.cellsForecedToChange.get(c.getMaskType()).put(c.getX() + "," + c.getY(), c);
				});
			});
		}
	}

	public void regionalSupply() {
		productivityForAllExecutor();
		computeRegionsSupply();
		LOGGER.info("Rigion: [" + R.getName() + "] Total Supply = " + getRegionalSupply());
	}

	public void initialDSEquilibriumFactorCalculation() {
		// update the capital first year
		regionalSupply();
		getRegionalSupply().forEach((serviceName, serviceSuplly) -> {
			double factor = 1;
			if (serviceSuplly != 0) {
				if (R.getServicesHash().get(serviceName).getDemands().get(Timestep.getStartYear()) == 0) {
					LOGGER.warn("Demand for " + serviceName + " = 0");
				} else {
					factor = R.getServicesHash().get(serviceName).getDemands().get(Timestep.getStartYear())
							/ (serviceSuplly);
				}
			} else {
				ServiceSet.NoInitialSupplyServices.get(R.getName()).add(serviceName);
				LOGGER.warn("Baseline supply for |" + serviceName + "| in |" + R.getName() + "| is 0"
						+ " - Use Default Calibration_Factor = 1");
			}
			R.getServicesHash().get(serviceName).setCalibration_Factor(factor);
		});

		listner.fillDSEquilibriumListener(R.getServicesHash());
		LOGGER.info(
				"Initial Demand Service Equilibrium Factor= " + R.getName() + ": " + R.getServiceCalibration_Factor());
	}

	private final StepProfiler profiler = new StepProfiler(ConfigLoader.config.printRegionalModelRunnerMeasures);

	public void step() {
		profiler.reset();

		try (var t = profiler.section("exportFiles")) {
			listner.exportFiles(getRegionalSupply());
		}
		try (var t = profiler.section("computeMarginal")) {
			computeMarginal();
		}
		try (var t = profiler.section("servicesTax")) {
			servicesTax();
		}
		try (var t = profiler.section("landTS")) {
			landTS();
		}
		try (var t = profiler.section("utilitytyForAll")) {
			utilitytyForAll();
		}
		try (var t = profiler.section("calculeDistributionMean")) {
			computeDistributionMean();
		}
		try (var t = profiler.section("calculeMaxMinUtility")) {
			computeMaxMinUtility();
		}
		try (var t = profiler.section("takeOverMaskedCellByCategories")) {
			takeOverMaskedCellByCategories();
		}
		try (var t = profiler.section("giveUp")) {
			giveUp();
		}
		try (var t = profiler.section("takeOverUnmanageCells")) {
			takeOverUnmanageCells();
		}
		try (var t = profiler.section("competition")) {
			competition();
		}
		try (var t = profiler.section("hashAgentNbr")) {
			AFTsLoader.hashAgentNbr(R.getName());
		}

//		LOGGER.info(profiler.report("RegionalModelRunner (year=" + year + ", region=" + R.getName() + ")"));
		System.out.print(
				profiler.report("Step timings (year=" + Timestep.getCurrentYear() + ", region=" + R.getName() + ")"));

	}

	private void giveUp() {
		if (ConfigLoader.config.use_abandonment_threshold) {
			ConcurrentHashMap<String, Cell> randomCellsubSetForGiveUp = SeedUpdater.selectSeed(this,
					ConfigLoader.config.land_abandonment_percentage, true, ConfigLoader.config.longSeedID.get());
			if (randomCellsubSetForGiveUp != null) {
				randomCellsubSetForGiveUp.values()/**/ .parallelStream().forEach(c -> {
					c.giveUp(this, distributionMean.get(Timestep.getCurrentYear()));
				});
			}
		}
	}

	private void competition() {
		// Randomly select % of the land available for competition
		ConcurrentHashMap<String, Cell> seed = SeedUpdater.selectSeed(this,
				ConfigLoader.config.participating_cells_percentage, true, ConfigLoader.config.longSeedID.get());

		seed.putAll(cellsWhereOwnerExceededMaxLifeCycle());

//				Selector.randomSeed(R.getCells(),
//				ConfigLoader.config.participating_cells_percentage, ConfigLoader.config.longSeedID);
		if (seed != null) {
			List<ConcurrentHashMap<String, Cell>> subsubsets = Utils.splitIntoSubsets(seed,
					ConfigLoader.config.marginal_utility_calculations_per_tick);
			ConcurrentHashMap<String, Double> servicesBeforeCompetition = new ConcurrentHashMap<>();
			ConcurrentHashMap<String, Double> servicesAfterCompetition = new ConcurrentHashMap<>();
			subsubsets.forEach(subsubset -> {
				if (subsubset != null) {
					subsubset.values().parallelStream().forEach(c -> {
						if (c.getOwner() != null && c.getOwner().isActive()) {
							for (int i = 0; i < ServiceSet.getServicesList().size(); i++) {
								servicesBeforeCompetition.merge(ServiceSet.getServicesList().get(i),
										c.getCurrentProd()[i], Double::sum);
							}
							Competitiveness.competition(c, this);
							c.calculateCurrentProductivity();
							for (int i = 0; i < ServiceSet.getServicesList().size(); i++) {
								servicesAfterCompetition.merge(ServiceSet.getServicesList().get(i),
										c.getCurrentProd()[i], Double::sum);
							}
						}
					});
				}
				servicesBeforeCompetition.forEach((key, value) -> getRegionalSupply().merge(key, -value, Double::sum));
				servicesAfterCompetition.forEach((key, value) -> getRegionalSupply().merge(key, value, Double::sum));
				computeMarginal();
			});
		}
	}

	private ConcurrentHashMap<String, Cell> cellsWhereOwnerExceededMaxLifeCycle() {
		// check if the max used
//		Select cells exite the max 
//		add them to the seed (the cell should be Mask free)
		////
		ConcurrentHashMap<String, Cell> cells = new ConcurrentHashMap<>();
		boolean useMax = false;
		for (Aft a : AFTsLoader.getActivateAFTsHash().values()) {
			if (a.getMax_life_cycle() != Integer.MAX_VALUE) {
				useMax = true;
				break;
			}
		}
		if (useMax) {
			CellsLoader.hashCell.values().parallelStream().forEach(c -> {
				if (c.owner != null && c.getMaskType() == null
						&& c.getOwnerLifeCounter() >= c.owner.getMax_life_cycle()) {
					cells.put(c.getX() + "," + c.getY(), c);
				}
			});
		}
		return cells;

	}

	private void productivityForAllExecutor() {
		final ConcurrentHashMap<String, Cell> cells = R.getCells();
		cells.forEach(150_000, (id, c) -> {
			c.calculateCurrentProductivity();
			if (Timestep.getTick() > 0) {
				c.OwnerLifeCounterIncrement();
			}
		});

	}

	public ConcurrentHashMap<Aft, Double> getDistributionMeanY() {
		return distributionMean.get(Timestep.getCurrentYear());
	}

	public ConcurrentHashMap<Integer, ConcurrentHashMap<Aft, Double>> getDistributionMean() {
		return distributionMean;
	}

	public ConcurrentHashMap<String, Double> getRegionalSupply() {
		return regionalSupply;
	}

	public void setRegionalSupply(ConcurrentHashMap<String, Double> regionalSupply) {
		this.regionalSupply = regionalSupply;
	}

	public double getMaxUtility() {
		return maxUtility;
	}

	public void setMaxUtility(double maxUtility) {
		this.maxUtility = maxUtility;
	}

	public double getMinUtility() {
		return minUtility;
	}

	public void setMinUtility(double minUtility) {
		this.minUtility = minUtility;
	}

	public ConcurrentHashMap<String, Double> getServiceTax() {
		return serviceTax;
	}

	public ConcurrentHashMap<String, Double> getMarginal() {
		return marginal;
	}

}
