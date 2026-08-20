package de.cesr.crafty.institution.orchestration;

import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

public record TargetStateSnapshot(double currentValue, Map<Integer, Double> observedHistory,
		Map<Integer, Double> referenceHistory, Map<Integer, Double> goalHistory) {
	public TargetStateSnapshot {
		observedHistory = immutableOrdered(observedHistory);
		referenceHistory = immutableOrdered(referenceHistory);
		goalHistory = immutableOrdered(goalHistory);
	}

	private static Map<Integer, Double> immutableOrdered(Map<Integer, Double> values) {
		return Collections.unmodifiableMap(new LinkedHashMap<>(values));
	}
}
