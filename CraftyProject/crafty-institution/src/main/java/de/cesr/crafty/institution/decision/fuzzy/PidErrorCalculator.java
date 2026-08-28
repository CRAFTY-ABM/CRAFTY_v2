package de.cesr.crafty.institution.decision.fuzzy;

import java.util.List;

/** Weighted PID error calculation used by fuzzy decisions. */
final class PidErrorCalculator {
	private PidErrorCalculator() {
	}

	static double calculateWeightedError(List<Double> values, double target, double kp, double ki, double kd,
			int timeLag) {
		if (values == null || values.isEmpty()) {
			throw new IllegalArgumentException("Values cannot be null or empty");
		}
		if (Math.abs(kp + ki + kd - 1.0) > 0.0001) {
			throw new IllegalArgumentException("Weights must sum to 1.0");
		}
		if (timeLag <= 0) {
			throw new IllegalArgumentException("Time interval must be positive");
		}
		double current = values.get(values.size() - 1);
		double proportional = target == 0 ? -current : (target - current) / Math.abs(target);
		int count = Math.min(values.size(), timeLag);
		double integral = 0;
		for (int offset = 0; offset < count; offset++) {
			double value = values.get(values.size() - 1 - offset);
			integral += target == 0 ? -value : (target - value) / Math.abs(target);
		}
		integral /= count;
		double derivative = 0;
		if (values.size() > timeLag) {
			double past = values.get(values.size() - 1 - timeLag);
			derivative = target == 0 ? (past - current) / timeLag
					: (past - current) / (timeLag * Math.abs(target));
		}
		return kp * proportional + ki * integral + kd * derivative;
	}
}
