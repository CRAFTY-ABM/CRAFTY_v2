package de.cesr.crafty.gui.institutes;

/**
 * Counts successful policy applications and their cumulative cost.
 */
public final class PolicyCostCounter {

	private long applicationCount;
	private double totalCost;

	public void recordApplication(double appliedValue, double unitCost) {
		if (appliedValue == 0) {
			return;
		}

		applicationCount++;
		totalCost += unitCost;
	}

	public long getApplicationCount() {
		return applicationCount;
	}

	public double getTotalCost() {
		return totalCost;
	}

	public void reset() {
		applicationCount = 0;
		totalCost = 0;
	}
}
