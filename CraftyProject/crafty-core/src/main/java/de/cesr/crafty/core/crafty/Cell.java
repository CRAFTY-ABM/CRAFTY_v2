package de.cesr.crafty.core.crafty;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.output.Listener;

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

			final double capVal = getCapitals().getOrDefault(e.getKey(), 0.0); // assumes present
			if (p == 1.0)
				product *= capVal;
			else
				product *= Math.pow(capVal, p);
		}

		return product * a.getProductivityLevel().get(serviceName);
	}

	public void calculateCurrentProductivity() {
		for (int i = 0; i < ServiceSet.getServicesList().size(); i++) {
			getCurrentProd()[i] = productivity(owner, ServiceSet.getServicesList().get(i));
		}
	}

	public void calculateCurrentProductivity(String[] services) {
		for (int i = 0; i < ServiceSet.getServicesList().size(); i++) {
			getCurrentProd()[i] = productivity(owner, services[i]);
		}
	}

	void giveUp(RegionalModelRunner r, ConcurrentHashMap<Aft, Double> distributionMean) {
		if (getOwner() != null && getOwner().isInteract()) {
			double utility = getCurrentUtility();
			double averageutility = distributionMean.get(getOwner());
			if ((utility < averageutility
					* (getOwner().getGiveUpMean() + getOwner().getGiveUpSD() * new Random().nextGaussian())
					&& getOwner().getGiveUpProbabilty() > Math.random())) {
				setOwner(null);
				r.R.getUnmanageCellsR().add(this);
				Listener.landUseChangeCounter.getAndIncrement();
			}
		}
	}

}
