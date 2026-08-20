package de.cesr.crafty.institution.model;

public record PolicyConstraints(NumericRange value) {
	public PolicyConstraints {
		if (value == null) {
			throw new IllegalArgumentException("Policy value constraint is required");
		}
	}
}
