package de.cesr.crafty.core.modelRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.crafty.RegionalModelRunner;
import de.cesr.crafty.core.crafty.Service;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceDemandLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.CapitalUpdater;
import de.cesr.crafty.core.updaters.RegionsModelRunnerUpdater;
import de.cesr.crafty.core.updaters.Timestep;

public class InitialDSEquilibriumManager {

	private static final CustomLogger LOGGER = new CustomLogger(InitialDSEquilibriumManager.class);

	public static void demandEquilibrium() {
		if (ConfigLoader.config.initial_demand_supply_equilibrium) {
//			updateBaselineIfsupplyIsNull();
			RegionalDemandEquilibrium_calculation();
			RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(RegionalRunner -> {
				RegionalRunner.R.getServicesHash().forEach((ns, s) -> {
					s.getDemands().forEach((year, v) -> {
						s.getDemands().put(year, v / s.getCalibration_Factor());
					});
				});
			});
			validateInitialEquilibrium();
			initialTotalDSEquilibriumListrner();
			ServiceDemandLoader.aggregateRegionalToWorldServiceDemand();
		}

	}

	private static void RegionalDemandEquilibrium_calculation() {
		// Calculate EQ
		// store services has 0 supply hashMap<regionName, List<servicesNames>>
		// Compute the average EQ
		// go to 0 supply services and repleas them with the average

		List<RegionalModelRunner> orderedRunners = RegionsModelRunnerUpdater.regionsModelRunner.entrySet().stream()
				.sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();

		orderedRunners.forEach(RegionalRunner -> {
			ServiceSet.NoInitialSupplyServices.put(RegionalRunner.R.getName(), new ArrayList<>());
			RegionalRunner.initialDSEquilibriumFactorCalculation();
		});

		// calculate the average
		HashMap<String, Double> averageEQ = new HashMap<>();
		orderedRunners.forEach(RegionalRunner -> {
			ServiceSet.getServicesList().forEach(serviceName -> {
				double av = RegionalRunner.R.getServicesHash().get(serviceName).getCalibration_Factor()
						/ RegionsModelRunnerUpdater.regionsModelRunner.size();
				averageEQ.merge(serviceName, av, Double::sum);
			});
		});

		// comeback to NoInitialSupplyServices-EQ with the averageEQ
		orderedRunners.forEach(RegionalRunner -> {
			ServiceSet.getServicesList().forEach(serviceName -> {
				if (ServiceSet.NoInitialSupplyServices.get(RegionalRunner.R.getName()).contains(serviceName)) {
					RegionalRunner.R.getServicesHash().get(serviceName)
							.setCalibration_Factor(averageEQ.get(serviceName));
				}
			});

		});

	}

	/**
	 * Fails fast when a calibratable service does not satisfy the initial
	 * demand-supply invariant. Zero demand or zero supply cannot be corrected by
	 * a finite multiplicative factor and retain their existing warning/fallback
	 * behaviour.
	 */
	static void validateInitialEquilibrium() {
		RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(regionalRunner -> {
			String regionName = regionalRunner.R.getName();
			ServiceSet.getServicesList().forEach(serviceName -> {
				Double supply = regionalRunner.getRegionalSupply().get(serviceName);
				Double demand = regionalRunner.R.getServicesHash().get(serviceName).getDemands()
						.get(Timestep.getStartYear());

				if (supply == null || demand == null) {
					throw new IllegalStateException("Missing initial demand or supply: region=" + regionName
							+ ", service=" + serviceName + ", demand=" + demand + ", supply=" + supply);
				}
				if (supply == 0.0 || demand == 0.0) {
					return;
				}

				double tolerance = Math.max(1.0e-9, Math.abs(supply) * 1.0e-10);
				if (!Double.isFinite(supply) || !Double.isFinite(demand)
						|| Math.abs(demand - supply) > tolerance) {
					throw new IllegalStateException("Initial demand-supply equilibrium failed: region=" + regionName
							+ ", service=" + serviceName + ", demand=" + demand + ", supply=" + supply
							+ ", tolerance=" + tolerance);
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
//
//	private static void updateBaselineIfsupplyIsNull() {
//		// Loop for a region
//		// Calculate supply for all services stor 0-supply (exclud non-map)
//		// If the initial demands is not null for a 0-supply
//		// Check list of AFTs could produice that service
//		// Loop for all cells calculate supply for these services
//		// Choose the 0.01% of best ranking cells-aft
//		// Take over of a cell by the winner AFT
//		// Compute the eqilibueme factor again.
//		RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(r -> {
//			Set<String> nullSupply = new HashSet<>();
//			ServiceSet.getServicesList().forEach(serviceName -> {
//				boolean isZero = true;
//				for (Cell c : r.R.getCells().values()) {
//					if (c.productivity(c.getOwner(), serviceName) != 0) {
//						isZero = false;
//						break;
//					}
//				}
//				if (isZero)
//					nullSupply.add(serviceName);
//			});
//			LOGGER.warn("The list of services has supply = 0  in the baseline  for region (" + r.R.getName() + "): "
//					+ nullSupply);
////	Find AFTs that could produce zerosupplyServices
//			Cell perfectCell = new Cell(0, 0);
//			CapitalUpdater.getCapitalsList().forEach(capitalName -> {
//				perfectCell.getCapitals().put(capitalName, 1d);
//			});
//			Set<String> aftProduceZeroService = new HashSet<>();
//			Set<String> corruptedServices = new HashSet<>();
//
//			nullSupply.forEach(serviceName -> {
//				boolean noAFTCouldProduce = true;
//				for (Aft a : AFTsLoader.getActivateAFTsHash().values()) {
//					double d = perfectCell.productivity(a, serviceName);
//					if (d != 0) {
//						aftProduceZeroService.add(a.getLabel());
//						noAFTCouldProduce = false;
//					}
//				}
//				if (noAFTCouldProduce) {
//					corruptedServices.add(serviceName);
//				}
//			});
//			nullSupply.removeAll(corruptedServices);
//			if (!corruptedServices.isEmpty()) {
//				LOGGER.error("Region: (" + r.R.getName() + ") List of services that cannot be provided by any AFT: "
//						+ corruptedServices);
//			}
//
//			int minCells = (int) (r.R.getCells().size() * 0.001);
//			if (minCells > 0 && !aftProduceZeroService.isEmpty() && !nullSupply.isEmpty()) {
//				System.out
//						.println("Crafty will allocated " + minCells + " of cells  to each AFT " + aftProduceZeroService
//								+ " to avoid null supply for services " + nullSupply + " in the baseline year");
//
//				HashMap<String, Integer> counter = new HashMap<>();
//				List<Cell> cells = new ArrayList<>(r.R.getCells().values());
//				Collections.shuffle(cells);
//				for (Cell c : cells) {
//					for (String a : aftProduceZeroService) {
//						Aft aft = AFTsLoader.getActivateAFTsHash().get(a);
//						for (String serviceName : nullSupply) {
//							if (counter.getOrDefault(serviceName, 0) > minCells) {
//								nullSupply.remove(serviceName);
//								break;
//							}
//							double d = perfectCell.productivity(aft, serviceName);
//							if (d != 0) {
//								counter.merge(serviceName, 1, Integer::sum);
//								c.setOwner(aft);
//								break;
//							}
//						}
//					}
//				}
//			}
//		});
//	}
	
	private static final double EPS = 1e-12;
	private static final double BASELINE_FIX_FRACTION = 0.001; 
	// 0.001 = 0.1%
	// use 0.0001 if you really want 0.01%

	private static void updateBaselineIfsupplyIsNull() {

		RegionsModelRunnerUpdater.regionsModelRunner.values().stream()
				.sorted(Comparator.comparing(r -> r.R.getName()))
				.forEach(r -> {

			Set<String> nullSupply = new TreeSet<>();

			List<String> services = new ArrayList<>(ServiceSet.getServicesList());
			Collections.sort(services);

			for (String serviceName : services) {
				boolean isZero = true;

				for (Cell c : r.R.getCells().values()) {
					if (Math.abs(c.productivity(c.getOwner(), serviceName)) > EPS) {
						isZero = false;
						break;
					}
				}

				if (isZero) {
					nullSupply.add(serviceName);
				}
			}

			LOGGER.warn("The list of services with supply = 0 in the baseline for region (" 
					+ r.R.getName() + "): " + nullSupply);

			if (nullSupply.isEmpty()) {
				return;
			}

			Cell perfectCell = new Cell(0, 0);
			CapitalUpdater.getCapitalsList().forEach(capitalName -> {
				perfectCell.getCapitals().put(capitalName, 1d);
			});

			List<Aft> activeAfts = AFTsLoader.getActivateAFTsHash().values().stream()
					.sorted(Comparator.comparing(Aft::getLabel))
					.toList();

			Map<String, List<Aft>> producersByService = new TreeMap<>();
			Set<String> corruptedServices = new TreeSet<>();

			for (String serviceName : nullSupply) {
				List<Aft> producers = new ArrayList<>();

				for (Aft aft : activeAfts) {
					double productivity = perfectCell.productivity(aft, serviceName);

					if (Math.abs(productivity) > EPS) {
						producers.add(aft);
					}
				}

				if (producers.isEmpty()) {
					corruptedServices.add(serviceName);
				} else {
					producersByService.put(serviceName, producers);
				}
			}

			if (!corruptedServices.isEmpty()) {
				LOGGER.error("Region: (" + r.R.getName() 
						+ ") List of services that cannot be provided by any AFT: " 
						+ corruptedServices);
			}

			nullSupply.removeAll(corruptedServices);

			if (nullSupply.isEmpty()) {
				return;
			}

			int cellsToAllocate = Math.max(1, (int) Math.ceil(r.R.getCells().size() * BASELINE_FIX_FRACTION));

			List<Cell> cells = new ArrayList<>(r.R.getCells().values());

			// Use a stable cell order.
			cells.sort(Comparator.comparing(Cell::getID));

			Set<Cell> alreadyChangedCells = Collections.newSetFromMap(new IdentityHashMap<>());

			for (String serviceName : nullSupply) {

				// The service may already be fixed by previous AFT replacements.
				if (!hasZeroSupply(r, serviceName)) {
					continue;
				}

				List<CellAftCandidate> candidates = new ArrayList<>();

				for (Cell cell : cells) {
					if (alreadyChangedCells.contains(cell)) {
						continue;
					}

					for (Aft aft : producersByService.get(serviceName)) {
						double productivity = cell.productivity(aft, serviceName);

						if (Math.abs(productivity) > EPS) {
							candidates.add(new CellAftCandidate(cell, aft, serviceName, productivity));
						}
					}
				}

				candidates.sort(
						Comparator.comparingDouble(CellAftCandidate::productivity).reversed()
								.thenComparing(c -> c.aft().getLabel())
								.thenComparing(c -> c.cell().getId())
				);

				int changed = 0;

				for (CellAftCandidate candidate : candidates) {
					if (changed >= cellsToAllocate) {
						break;
					}

					Cell cell = candidate.cell();

					if (alreadyChangedCells.add(cell)) {
						cell.setOwner(candidate.aft());
						changed++;
					}
				}

				LOGGER.warn("Region (" + r.R.getName() + "): changed " + changed 
						+ " cells to avoid zero baseline supply for service " + serviceName);
			}
		});
	}

	private static boolean hasZeroSupply(RegionalModelRunner r, String serviceName) {
		for (Cell c : r.R.getCells().values()) {
			if (Math.abs(c.productivity(c.getOwner(), serviceName)) > EPS) {
				return false;
			}
		}
		return true;
	}

	private record CellAftCandidate(
			Cell cell,
			Aft aft,
			String serviceName,
			double productivity
	) {}

}
