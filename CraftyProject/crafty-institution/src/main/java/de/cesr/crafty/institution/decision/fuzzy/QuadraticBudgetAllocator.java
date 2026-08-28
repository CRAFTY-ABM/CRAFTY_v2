package de.cesr.crafty.institution.decision.fuzzy;

import java.util.LinkedHashMap;
import java.util.Map;

/** Allocates a limited budget while minimizing weighted squared deviation. */
final class QuadraticBudgetAllocator {
	private QuadraticBudgetAllocator() {
	}

	static Map<String, Double> allocate(Map<String, Double> weights, Map<String, Double> targets, double budget) {
		double targetTotal = targets.values().stream().mapToDouble(Double::doubleValue).sum();
		if (targetTotal <= budget) {
			return new LinkedHashMap<>(targets);
		}
		double low = 0;
		double high = weights.keySet().stream()
				.mapToDouble(key -> 2 * Math.max(weights.get(key), 1.0e-12) * targets.get(key)).max().orElse(0);
		for (int iteration = 0; iteration < 200; iteration++) {
			double lambda = (low + high) / 2;
			double allocated = 0;
			for (String key : weights.keySet()) {
				allocated += Math.max(0, targets.get(key) - lambda / (2 * Math.max(weights.get(key), 1.0e-12)));
			}
			if (allocated > budget) {
				low = lambda;
			} else {
				high = lambda;
			}
		}
		double lambda = high;
		Map<String, Double> result = new LinkedHashMap<>();
		weights.forEach((key, weight) -> result.put(key,
				Math.max(0, targets.get(key) - lambda / (2 * Math.max(weight, 1.0e-12)))));
		return result;
	}
}
