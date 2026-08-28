package de.cesr.crafty.gui.institutes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import de.cesr.crafty.institution.model.InstitutionDefinition;

/** Mutable GUI state backed by one immutable common institution definition. */
public final class InstitutionViewModel {
	private final InstitutionDefinition definition;
	private final Map<String, PolicyViewModel> policies;
	private final Map<String, TargetViewModel> targets;
	private double budget;

	public InstitutionViewModel(InstitutionDefinition definition, Map<String, TargetViewModel> targets) {
		this.definition = Objects.requireNonNull(definition, "definition");
		this.budget = definition.budget().amount();
		this.targets = new LinkedHashMap<>(targets);
		this.policies = new LinkedHashMap<>();
		definition.policies().values().forEach(policy ->
				this.policies.put(policy.name(), new PolicyViewModel(policy, definition.scope())));
	}

	public InstitutionDefinition getDefinition() {
		return definition;
	}

	public String getName() {
		return definition.name();
	}

	public String getDescription() {
		return definition.description();
	}

	public double getBudget() {
		return budget;
	}

	public void setBudget(double budget) {
		if (!Double.isFinite(budget) || budget < 0) {
			throw new IllegalArgumentException("Institution budget must be finite and non-negative");
		}
		this.budget = budget;
	}

	public Map<String, PolicyViewModel> getPolicies() {
		return policies;
	}

	public Map<String, TargetViewModel> getTargets() {
		return targets;
	}

	public void resetRuntimeState() {
		budget = definition.budget().amount();
		policies.values().forEach(PolicyViewModel::resetRuntimeState);
	}
}
