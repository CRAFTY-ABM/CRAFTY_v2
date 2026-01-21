package de.cesr.crafty.core.updaters;

import java.util.concurrent.ConcurrentHashMap;
/**
 * Computes and maintains the aggregated (world-level) supply of each ecosystem service for the current model year.
 *
 * On each tick, this updater:
 * - clears the global {@link #totalSupply} map;
 * - triggers each {@link de.cesr.crafty.core.crafty.RegionalModelRunner} to recompute its regional supply
 *   for the current year via {@code regionalSupply()};
 * - sums all regional supplies into {@link #totalSupply} using {@code merge(..., Double::sum)} so that:
 *   {@code totalSupply[service] = Σ_regions regionalSupply[service]}.
 *
 * The resulting totals provide a fast, consistent, per-year snapshot that can be used by output writers,
 * diagnostics, or any component requiring world-level service supply.
 */

/**
 * @author Mohamed Byari
 *
 */
public class SupplyUpdater extends AbstractUpdater {
	public static ConcurrentHashMap<String, Double> totalSupply = new ConcurrentHashMap<>();

	@Override
	public void toSchedule() {
		modelRunner.scheduleRepeating(this);
	}

	@Override
	public void step() {
		
		totalSupply.clear();
		RegionsModelRunnerUpdater.regionsModelRunner.values().forEach(RegionalRunner -> {
			RegionalRunner.regionalSupply();
			RegionalRunner.getRegionalSupply().forEach((key, value) -> totalSupply.merge(key, value, Double::sum));
		});
	}
}
