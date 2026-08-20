package de.cesr.crafty.core.crafty;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.output.Listener;
import de.cesr.crafty.core.output.Tracker;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.general.DeterministicRandom;

/**
 * Concrete spatial cell implementation used during simulation.
 *
 * In addition to the basic state stored in {@link AbstractCell}, this class implements core per-cell
 * calculations and events that depend on the current owner (AFT):
 *
 * - productivity:
 *   {@link #productivity(Aft, String)} computes service potontial production as a multiplicative function of
 *   capitals raised to AFT-specific sensitivity exponents (service -> capital -> exponent),
 *   multiplied by an AFT-specific productivity level for the service.
 *   {@link #calculateCurrentProductivity()} fills the full production vector for all services.
 *
 * - Behavioural land abandonment (give-up):
 *   {@link #giveUp(RegionalModelRunner, ConcurrentHashMap)} compares the cell utility to the mean utility
 *   of the owning AFT (from the regional distribution statistics) and stochastically abandons the cell
 *   using the AFT’s give-up parameters. When give-up occurs, the owner is cleared, the cell is added to
 *   the region’s unmanaged pool, and the global land-use change counter is incremented
 *   ({@link Listener#landUseChangeCounter}).
 *
 * Notes:
 * - Production assumes required capital values are present in the cell’s capital map.
 * - The production vector is sized using the current global service list ({@link ServiceSet}).
 */

/**
 * @author Mohamed Byari
 *
 */

public class Cell extends AbstractCell {

	public Cell(int x, int y) {
		this.x = x;
		this.y = y;
		this.setCellID(DeterministicRandom.stableCellId(x, y));
		setCurrentProd(new double[ServiceSet.getServicesList().size()]);
	}

	public double productivity(Aft a, String serviceName) {
		if (a == null || !a.isInteract())
			return 0.0;

		final Map<String, Double> exps = a.getSensByService().get(serviceName);
		if (exps == null || exps.isEmpty())
			return 0.0;

		double product = 1.0;

		for (var e : exps.entrySet()) {
			final double p = e.getValue();
			if (p == 0.0)
				continue;
			final double capVal = (getCapitals().getOrDefault(e.getKey(), 0.)
					* (1 + getCapitalsAdjusment().getOrDefault(e.getKey(), 0.))); // assumes present
			if (p == 1.0)
				product *= capVal;
			else
				product *= Math.pow(capVal, p);
		}

		return product * a.getProductivityLevel().get(serviceName);
	}

	public void calculateCurrentProductivity() {
		for (int i = 0; i < ServiceSet.getServicesList().size(); i++) {
			getCurrentProd()[i] = productivity(getOwner(), ServiceSet.getServicesList().get(i));
		}
	}

	public void calculateCurrentProductivity(String[] services) {
		for (int i = 0; i < ServiceSet.getServicesList().size(); i++) {
			getCurrentProd()[i] = productivity(getOwner(), services[i]);
		}
	}

	void giveUp(RegionalModelRunner r, ConcurrentHashMap<String, Double> distributionMean) {

		Aft owner = getOwner();
		if (owner == null || !owner.isInteract()) {
			return;
		}

		Double averageUtilityObj = distributionMean.get(owner.getLabel());
		if (averageUtilityObj == null) {
			return;
		}

		double utility = getCurrentUtility();
		double averageUtility = averageUtilityObj;

		long runSeed = ConfigLoader.config.random_seed;
		int year = Timestep.getCurrentYear();

		long cellId = DeterministicRandom.stableCellKey(this);
		long aftId = DeterministicRandom.stableAftId(owner.getLabel());

		double gaussian = DeterministicRandom.randomGaussian(runSeed, year,
				DeterministicRandom.Process.GIVE_UP_GAUSSIAN, cellId, aftId, 0);

		double thresholdFactor = owner.getGiveUpMean() + owner.getGiveUpSD() * gaussian;

		boolean passesUtilityTest = utility < averageUtility * thresholdFactor;
		boolean passesProbabilityTest = DeterministicRandom.randomBoolean(runSeed, year,
				DeterministicRandom.Process.GIVE_UP_PROBABILITY, cellId, aftId, 0, owner.getGiveUpProbabilty());

		boolean giveUp = passesUtilityTest && passesProbabilityTest;
//		System.out.println("Utility= "+utility +" average Utiulity= "+ averageUtility +" threshold= "+ thresholdFactor+"==> "+giveUp);
		if (giveUp) {
			String oldOwner = getOwner().getLabel();
			setOwner(null);
//			CellsUpdater.decesionsNewOwner.put(this, AFTsLoader.getAftHash().get("Abandoned"));
			r.R.getUnmanageCellsR().add(this);
			setOwnerLifeCounter(0);
			Listener.landUseChangeCounter.getAndIncrement();
			Listener.newAftsInLandNbr.merge("null", 1, Integer::sum);
			Tracker.sankeydata.get("Abandoned").get(Timestep.getCurrentYear()).merge(oldOwner, 1, Integer::sum);
//			RegionalModelRunner.tmp.getAndIncrement();
		}
	}

}
