package de.cesr.crafty.institution.runtime;

import java.util.Map;

public record TargetObservation(String targetId, double rawValue, double normalizedValue, double baselineValue,
		Map<String, Double> components) {
	public TargetObservation {
		components = Map.copyOf(components);
	}
}
