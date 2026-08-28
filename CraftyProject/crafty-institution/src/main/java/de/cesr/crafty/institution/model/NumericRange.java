package de.cesr.crafty.institution.model;

public record NumericRange(double min, double max) {
	public NumericRange {
		if (!Double.isFinite(min) || !Double.isFinite(max)) {
			throw new IllegalArgumentException("Range values must be finite");
		}
		if (min > max) {
			throw new IllegalArgumentException("Range minimum cannot exceed maximum");
		}
	}

	public double clamp(double value) {
		return Math.max(min, Math.min(max, value));
	}
}
