package de.cesr.crafty.institution.model;

public record BudgetDefinition(double amount) {
	public BudgetDefinition {
		if (!Double.isFinite(amount) || amount < 0) {
			throw new IllegalArgumentException("Budget amount must be finite and non-negative");
		}
	}
}
