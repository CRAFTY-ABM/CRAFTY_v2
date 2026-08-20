package de.cesr.crafty.institution.orchestration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record InstitutionContext(String institutionId, int year, boolean decisionDue,
		Map<String, List<Double>> targetHistories, Map<String, Double> estimatedQuantities,
		double availableBudget, String engineInput) {
	public InstitutionContext {
		if (institutionId == null || institutionId.isBlank()) {
			throw new IllegalArgumentException("Institution id cannot be blank");
		}
		Map<String, List<Double>> histories = new LinkedHashMap<>();
		targetHistories.forEach((key, value) -> histories.put(key, List.copyOf(value)));
		targetHistories = Map.copyOf(histories);
		estimatedQuantities = Map.copyOf(estimatedQuantities);
		if (!Double.isFinite(availableBudget)) {
			throw new IllegalArgumentException("Available budget must be finite");
		}
		engineInput = engineInput == null ? "" : engineInput;
	}

	public InstitutionContext withDecisionDue(boolean due) {
		return new InstitutionContext(institutionId, year, due, targetHistories, estimatedQuantities, availableBudget,
				engineInput);
	}
}
