package de.cesr.crafty.institution.model;

public record ActivationSchedule(int startYear, int endYear, int intervalYears) {
	public ActivationSchedule {
		if (endYear < startYear) {
			throw new IllegalArgumentException("Schedule end year must be greater than or equal to start year");
		}
		if (intervalYears <= 0) {
			throw new IllegalArgumentException("Schedule interval must be positive");
		}
	}

	/** Returns whether policy effects are active during the supplied model year. */
	public boolean includes(int year) {
		return year >= startYear && year <= endYear;
	}
}
