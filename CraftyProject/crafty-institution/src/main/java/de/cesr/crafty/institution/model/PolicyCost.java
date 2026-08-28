package de.cesr.crafty.institution.model;

public record PolicyCost(double unitCost, double estimatedQuantity) {
	public PolicyCost {
		if (!Double.isFinite(unitCost) || unitCost < 0) {
			throw new IllegalArgumentException("Unit cost must be finite and non-negative");
		}
		if (!Double.isFinite(estimatedQuantity) || estimatedQuantity < 0) {
			throw new IllegalArgumentException("Estimated quantity must be finite and non-negative");
		}
	}

	public double estimatedTotalCost() {
		return unitCost * estimatedQuantity;
	}
}
