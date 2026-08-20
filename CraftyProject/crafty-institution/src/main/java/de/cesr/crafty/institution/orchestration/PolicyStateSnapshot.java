package de.cesr.crafty.institution.orchestration;

import java.util.List;

public record PolicyStateSnapshot(double currentDecision, double effectiveValue,
		List<Double> decisionHistory, List<Double> effectiveHistory) {
	public PolicyStateSnapshot {
		decisionHistory = List.copyOf(decisionHistory);
		effectiveHistory = List.copyOf(effectiveHistory);
	}
}
