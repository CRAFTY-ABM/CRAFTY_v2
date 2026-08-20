package de.cesr.crafty.institution.decision.fuzzy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.cesr.crafty.institution.model.DecisionEngineType;
import de.cesr.crafty.institution.model.InstitutionConfiguration;
import de.cesr.crafty.institution.decision.fuzzy.integration.InstitutionOutput;
import de.cesr.crafty.institution.decision.fuzzy.integration.TargetModelOutput;

/** Collection runtime used by the headless fuzzy connector. */
public final class FuzzyInstitutionRuntimeSet {
	private final List<FuzzyInstitutionRuntime> institutions;
	private final Map<String, FuzzyInstitutionRuntime> byName;

	private FuzzyInstitutionRuntimeSet(List<FuzzyInstitutionRuntime> institutions) {
		this.institutions = List.copyOf(institutions);
		Map<String, FuzzyInstitutionRuntime> index = new LinkedHashMap<>();
		institutions.forEach(institution -> index.put(institution.name(), institution));
		this.byName = Map.copyOf(index);
	}

	public static FuzzyInstitutionRuntimeSet from(InstitutionConfiguration configuration) {
		List<FuzzyInstitutionRuntime> runtimes = new ArrayList<>();
		configuration.institutions().values().forEach(definition -> {
			if (definition.decisionEngine().type() != DecisionEngineType.FUZZY) {
				throw new IllegalArgumentException("Fuzzy runtime cannot load " + definition.decisionEngine().type()
						+ " institution '" + definition.id() + "'");
			}
			runtimes.add(new FuzzyInstitutionRuntime(definition));
		});
		return new FuzzyInstitutionRuntimeSet(runtimes);
	}

	public InstitutionOutput step(TargetModelOutput output) {
		HashMap<String, HashMap<String, Double>> values = new HashMap<>();
		for (FuzzyInstitutionRuntime institution : institutions) {
			Map<String, Double> quantities = required(output.estimatedQuantities(), institution.name(),
					"estimated quantities");
			Double budget = output.budgets().get(institution.name());
			if (budget == null) {
				throw new IllegalArgumentException("Missing budget for institution '" + institution.name() + "'");
			}
			values.put(institution.name(), new HashMap<>(institution.step(output.modelOutput(), quantities, budget)));
		}
		return new InstitutionOutput(values);
	}

	public InstitutionOutput initialPolicies() {
		HashMap<String, HashMap<String, Double>> values = new HashMap<>();
		institutions.forEach(institution -> {
			HashMap<String, Double> policies = new HashMap<>();
			institution.policies().forEach(policy -> policies.put(policy.name(), policy.stepSize()));
			values.put(institution.name(), policies);
		});
		return new InstitutionOutput(values);
	}

	public List<FuzzyInstitutionRuntime> institutions() { return institutions; }
	public FuzzyInstitutionRuntime institution(String name) { return byName.get(name); }

	private static <T> T required(Map<String, T> values, String key, String label) {
		T value = values.get(key);
		if (value == null) {
			throw new IllegalArgumentException("Missing " + label + " for institution '" + key + "'");
		}
		return value;
	}
}
