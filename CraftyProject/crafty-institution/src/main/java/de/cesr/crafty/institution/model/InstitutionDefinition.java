package de.cesr.crafty.institution.model;

import de.cesr.crafty.institution.config.Identifiers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record InstitutionDefinition(String id, String name, String description, ActivationSchedule schedule,
		SpatialScope scope, BudgetDefinition budget, List<TargetReference> targets,
		Map<String, PolicyDefinition> policies, DecisionEngineDefinition decisionEngine) {
	public InstitutionDefinition {
		id = Identifiers.normalize(id);
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Institution name cannot be blank");
		}
		name = name.trim();
		description = description == null ? "" : description.trim();
		if (schedule == null || scope == null || budget == null || decisionEngine == null) {
			throw new IllegalArgumentException("Institution schedule, scope, budget and decision engine are required");
		}
		targets = List.copyOf(targets);
		Map<String, PolicyDefinition> copy = new LinkedHashMap<>();
		policies.forEach((key, value) -> copy.put(Identifiers.normalize(key), value));
		policies = Map.copyOf(copy);
		if (policies.isEmpty()) {
			throw new IllegalArgumentException("Institution must contain at least one policy");
		}
		for (PolicyDefinition policy : policies.values()) {
			validatePolicyParameters(policy, decisionEngine.type());
		}
	}

	private static void validatePolicyParameters(PolicyDefinition policy, DecisionEngineType engineType) {
		PolicyConstraints constraints = policy.constraints();
		if (engineType == DecisionEngineType.LLM) {
			if (policy.cost() != null || constraints != null) {
				throw new IllegalArgumentException("LLM policy '" + policy.id()
						+ "' must not define cost or constraints");
			}
			return;
		}
		if (policy.cost() == null || constraints == null || constraints.value() == null) {
			throw new IllegalArgumentException(engineType + " policy '" + policy.id()
					+ "' requires cost and a value constraint");
		}
	}
}
