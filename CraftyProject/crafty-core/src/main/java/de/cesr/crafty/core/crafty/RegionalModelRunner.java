package de.cesr.crafty.core.crafty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.output.Listener;
import de.cesr.crafty.core.output.ListenerByRegion;
import de.cesr.crafty.core.updaters.SeedUpdater;
import de.cesr.crafty.core.updaters.LandMaskUpdater;
import de.cesr.crafty.core.updaters.ServicesUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.analysis.StepProfiler;
import de.cesr.crafty.core.utils.general.DeterministicAggregation;
import de.cesr.crafty.core.utils.general.DeterministicRandom;
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
	private ConcurrentHashMap<Integer, ConcurrentHashMap<String, Double>> distributionMean = new ConcurrentHashMap<>();

	Map<String, Double> initial_service_gaps = new ConcurrentHashMap<>();
	Map<String, Double> initialutilityAverage = new ConcurrentHashMap<>();

	double score;
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

	private final StepProfiler profiler = new StepProfiler(ConfigLoader.config.print_regional_model_runner_measures);

	public void step() {
		profiler.reset();

		try (var t = profiler.section("exportFiles")) {
			listner.exportFiles(getRegionalSupply());
		}
		try (var t = profiler.section("compute Marginal")) {
			computeMarginal();
		}
		try (var t = profiler.section("initialise cell-level policy calibration")) {
			if (ConfigLoader.config.use_cell_level_taxes) {
				intializeForTaxesUse();
			}
		}
		try (var t = profiler.section("utilitytyForAll")) {
			utilitytyForAll();
		}
		try (var t = profiler.section("forced masks")) {
			if (LandMaskUpdater.applyForcedMasks(this) > 0) {
				productivityForAllExecutor();
				computeRegionsSupply();
				computeMarginal();
				utilitytyForAll();
			}
		}
		try (var t = profiler.section("compute DistributionMean")) {
			computeDistributionMean();
		}
		try (var t = profiler.section("compute MaxMinUtility")) {
			computeMaxMinUtility();
		}
//		try (var t = profiler.section("takeOverMaskedCellByCategories")) {
//			takeOverMaskedCellByCategories();
//		}
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

		LOGGER.info(profiler
				.report("RegionalModelRunner (year=" + Timestep.getCurrentYear() + ", region=" + R.getName() + ")"));
	}

	private void computeRegionsSupply() {
		setRegionalSupply(new ConcurrentHashMap<>());
		for (Cell c : DeterministicAggregation.cellsInStableOrder(R.getCells().values())) {
			for (int i = 0; i < ServiceSet.getServicesList().size(); i++) {
				getRegionalSupply().merge(ServiceSet.getServicesList().get(i), c.getCurrentProd()[i], Double::sum);
			}
		}
	}

	private void utilitytyForAll() {
		final var cells = R.getCells(); // ConcurrentHashMap<?, Cell>
		if (!ConfigLoader.config.use_cell_level_taxes) {
			// Snapshot services once
			final List<String> servicesList = ServiceSet.getServicesList();
			final String[] services = servicesList.toArray(new String[0]);
			final double[] coeff = buildUtilityCoeff(services);
			cells.forEach(100_000, (k, c) -> c.setCurrentUtility(computeCellUtility(c, services, coeff)));
		} else {
			cells.forEach(100_000, (k, c) -> c.setCurrentUtility(ownerUtility(c)));
		}

	}

	private double ownerUtility(Cell c) {
		return ServiceSet.getServicesList().stream()
				.mapToDouble(serviceName -> (c.getServicesTax().getOrDefault(serviceName, 1d)
						* (initial_service_gaps.get(serviceName)) + getMarginal().get(serviceName))
						* c.productivity(c.getOwner(), serviceName))
				.sum()
				+ c.getLandTax().getOrDefault(c.getOwnerName(), 0d)
						* (initialutilityAverage.getOrDefault(c.getOwnerName(), 1d));
	}

	private double[] buildUtilityCoeff(String[] services) {
		final double[] coeff = new double[services.length];
		final var marginal = getMarginal();

		for (int i = 0; i < services.length; i++) {
			final String s = services[i];
			coeff[i] = marginal.get(s);
		}
		return coeff;
	}

	private double computeCellUtility(Cell c, String[] services, double[] coeff) {
		final Aft owner = c.getOwner();
		if (owner == null || !owner.isInteract()) {
			return 0;
		}

		double u = 0;
		for (int i = 0; i < services.length; i++) {
			u += coeff[i] * c.productivity(owner, services[i]);
		}

		return u;
	}

	private void intializeForTaxesUse() {
		if (Timestep.getCurrentYear() < Timestep.getStartYear() + 2) {
			ServiceSet.getServicesList().forEach(serviceName -> {
				initial_service_gaps.put(serviceName, Math.abs(ServicesUpdater.getGaps().get(R.getName())
						.get(serviceName).getOrDefault(Timestep.getStartYear() + 1, 1d)));
			});

			AFTsLoader.getAftHash().keySet().forEach(aftName -> {
				initialutilityAverage.put(aftName,
						Math.abs(distributionMean.getOrDefault(Timestep.getStartYear() + 1, new ConcurrentHashMap<>())
								.getOrDefault(aftName, 1d)));
			});
		}
	}

	private void computeDistributionMean() {
		ConcurrentHashMap<String, Double> currentMean = distributionMean.get(Timestep.getCurrentYear());
		currentMean.clear();
		for (Cell c : DeterministicAggregation.cellsInStableOrder(R.getCells().values())) {
			if (c.getOwner() != null) {
				currentMean.merge(c.getOwner().getLabel(),
						c.getCurrentUtility()
								/ AFTsLoader.hashAgentNbrRegions.get(R.getName()).get(c.getOwner().getLabel()),
						Double::sum);
			}
		}
		AFTsLoader.getActivateAFTsHash().keySet().stream().sorted().forEach(a -> currentMean.putIfAbsent(a, 0.0));

		StringJoiner joiner = new StringJoiner(", ", "Region: [" + R.getName() + "]: Distribution Mean: {", "}");
		for (String a : currentMean.keySet().stream().sorted().toList()) {
			joiner.add(a + "= " + currentMean.get(a));
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

//		System.out.println(stats+"   min max :: "+minUtility+", "+maxUtility);
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

	public static AtomicInteger tmp = new AtomicInteger();

	private void giveUp() {
//		tmp = new AtomicInteger();
		if (ConfigLoader.config.use_abandonment_threshold) {
			List<Cell> randomCellsubSetForGiveUp = SeedUpdater.selectSeed(this, R.getCells(),
					ConfigLoader.config.land_abandonment_fraction, false,
					DeterministicRandom.Process.CELL_SELECTION_ABANDONMENT);
			if (randomCellsubSetForGiveUp != null) {
				randomCellsubSetForGiveUp.parallelStream().forEach(c -> {
					c.giveUp(this, distributionMean.get(Timestep.getCurrentYear()));
				});
			}
//			System.out.println("number of cells give up: " + tmp);
		}
	}

	private void takeOverUnmanageCells() {
		LOGGER.trace("Region: [" + R.getName() + "] Take over unmanaged cells ...");
		Collection<Cell> cells = R.getUnmanageCellsR();
		ConcurrentHashMap<String, Cell> map = new ConcurrentHashMap<>(cells.size());
		for (Cell cell : cells) {
			map.put(cell.getX() + "," + cell.getY(), cell);
		}
		List<Cell> seed = SeedUpdater.selectSeed(this, map, ConfigLoader.config.land_abandonment_fraction, false,
				DeterministicRandom.Process.CELL_SELECTION_UNMANAGED_TAKEOVER);

		// Phase 1 only reads model state; no cell owner changes until every decision is
		// ready.
		List<Competitiveness.CompetitionDecision> decisions = seed.parallelStream().map(c -> {
			boolean takeOver = DeterministicRandom.randomBoolean(ConfigLoader.config.random_seed,
					Timestep.getCurrentYear(), DeterministicRandom.Process.ABANDONMENT_TAKEOVER,
					DeterministicRandom.stableCellKey(c), 0L, 0, ConfigLoader.config.unmanaged_cell_takeover_fraction);
			return takeOver
					? Competitiveness.evaluateCompetition(c, this,
							DeterministicRandom.Process.CELL_SELECTION_UNMANAGED_TAKEOVER)
					: null;
		}).filter(Objects::nonNull).toList();

		// Phase 2 applies decisions in the deterministic seed encounter order.
		for (Competitiveness.CompetitionDecision decision : decisions) {
			Competitiveness.applyCompetitionDecision(decision);
			Cell c = decision.cell();
			if (c.getOwner() != null && !c.getOwner().isAbandoned()) {
				R.getUnmanageCellsR().remove(c);
				c.setOwnerLifeCounter(0);
			}
		}

	}

	private void competition() {
		List<Cell> seed = SeedUpdater.selectSeed(this, R.getCells(), ConfigLoader.config.participating_cell_fraction,
				true, DeterministicRandom.Process.CELL_SELECTION_COMPETITION);
		if (seed == null || seed.isEmpty())
			seed = new ArrayList<>();

		Map<Long, Cell> uniqueCells = new LinkedHashMap<>();
		seed.forEach(c -> uniqueCells.put(DeterministicRandom.stableCellKey(c), c));
		cellsWhereOwnerExceededMaxLifeCycle().values()
				.forEach(c -> uniqueCells.put(DeterministicRandom.stableCellKey(c), c));
		if (uniqueCells.isEmpty()) {
			return;
		}

		List<List<Cell>> subsubsets = Utils.splitIntoSubsetsDeterministic(uniqueCells.values(),
				ConfigLoader.config.marginal_utility_calculations_per_tick, ConfigLoader.config.random_seed,
				Timestep.getCurrentYear(), DeterministicRandom.Process.COMPETITION_BATCH_ORDER);

		subsubsets.forEach(subsubset -> {
			List<Cell> eligibleCells = subsubset.stream().filter(c -> c.getOwner() != null && c.getOwner().isActive())
					.toList();
			// Evaluate the complete batch against one unchanged ownership state.
			List<Competitiveness.CompetitionDecision> decisions = eligibleCells.parallelStream()
					.map(c -> Competitiveness.evaluateCompetition(c, this,
							DeterministicRandom.Process.CELL_SELECTION_COMPETITION))
					.filter(Objects::nonNull).toList();

			// Apply only after the parallel evaluation barrier, preserving encounter order.
			decisions.forEach(Competitiveness::applyCompetitionDecision);

			Map<String, Double> before = new LinkedHashMap<>();
			Map<String, Double> after = new LinkedHashMap<>();
			for (Cell c : eligibleCells) {
				for (int i = 0; i < ServiceSet.getServicesList().size(); i++) {
					before.merge(ServiceSet.getServicesList().get(i), c.getCurrentProd()[i], Double::sum);
				}
				c.calculateCurrentProductivity();
				for (int i = 0; i < ServiceSet.getServicesList().size(); i++) {
					after.merge(ServiceSet.getServicesList().get(i), c.getCurrentProd()[i], Double::sum);
				}
			}
			after.forEach(
					(key, value) -> getRegionalSupply().merge(key, value - before.getOrDefault(key, 0.0), Double::sum));
			computeMarginal();
		});
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
				if (c.getOwner() != null && c.getMaskType() == null
						&& c.getOwnerLifeCounter() >= c.getOwner().getMax_life_cycle()) {
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

	public ConcurrentHashMap<String, Double> getDistributionMeanY() {
		return distributionMean.get(Timestep.getCurrentYear());
	}

	public ConcurrentHashMap<Integer, ConcurrentHashMap<String, Double>> getDistributionMean() {
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

	public ConcurrentHashMap<String, Double> getMarginal() {
		return marginal;
	}

}
