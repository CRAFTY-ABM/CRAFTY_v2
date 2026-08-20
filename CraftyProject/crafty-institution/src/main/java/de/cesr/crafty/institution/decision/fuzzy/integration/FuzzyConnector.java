package de.cesr.crafty.institution.decision.fuzzy.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.main.MainHeadless;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.institution.model.CraftyElementRef;
import de.cesr.crafty.institution.model.ActivationSchedule;
import de.cesr.crafty.institution.model.InstitutionDefinition;
import de.cesr.crafty.institution.model.NormalizationType;
import de.cesr.crafty.institution.model.SpatialScope;
import de.cesr.crafty.institution.model.TargetDefinition;
import de.cesr.crafty.institution.runtime.CellPolicyState;
import de.cesr.crafty.institution.runtime.CraftyCellServiceValueProvider;
import de.cesr.crafty.institution.runtime.ObservationBaselines;
import de.cesr.crafty.institution.runtime.PolicyEffectApplier;
import de.cesr.crafty.institution.runtime.TargetObservation;
import de.cesr.crafty.institution.runtime.TargetObserver;
import de.cesr.crafty.institution.model.InstitutionConfiguration;

/*
 * @author Mohamed Byari
 *
 */

public class FuzzyConnector {
	private static final CustomLogger LOGGER = new CustomLogger(FuzzyConnector.class);

	private HashMap<String, ArrayList<Double>> prepModelOutput = new HashMap<>();// <target, List_time_stateObservation>
	Map<String, Double> policyListener = new HashMap<>();// <instutition@policy,policyValue>
	Map<String, Double> policyEffectsListner = new HashMap<>();// <instutition@policy@service,policyValue>
	private final Map<String, List<CraftyElementRef>> targetObservations = new HashMap<>();
	private final Map<String, TargetDefinition> targetDefinitions = new HashMap<>();
	private final Map<String, List<CraftyElementRef>> cellPolicyEffects = new HashMap<>();
	private final Map<String, SpatialScope> policyScopes = new HashMap<>();
	private final Map<String, ActivationSchedule> institutionSchedules = new HashMap<>();
	private final InstitutionConfiguration configuration;
	private final PolicyEffectApplier policyEffectApplier =
			new PolicyEffectApplier(scope -> CellsLoader.hashCell.values());
	private final TargetObserver targetObserver = new TargetObserver(scope -> CellsLoader.hashCell.values(),
			new CraftyCellServiceValueProvider(), FuzzyExternalValues::value,
			new ObservationBaselines());
	Map<String, Double> targetToValue = new HashMap<>();

	public FuzzyConnector(InstitutionConfiguration configuration) {
		this.configuration = java.util.Objects.requireNonNull(configuration, "configuration");
		initializeCommonConfiguration();
		DataCollector.init(this);
	}

	private void initializeCommonConfiguration() {
		for (InstitutionDefinition institution : configuration.institutions().values()) {
			institutionSchedules.put(institution.name(), institution.schedule());
			institution.policies().values().forEach(policy -> {
				String policyKey = institution.name() + "@" + policy.name();
				policyListener.put(policyKey, 0.0);
				cellPolicyEffects.put(policyKey, policy.effects());
				policyScopes.put(policyKey, institution.scope());
				policy.effects().forEach(effect ->
						policyEffectsListner.put(policyKey + "@" + effect.name(), 0.0));
			});
			institution.targets().forEach(reference -> {
				String targetId = reference.targetId();
				prepModelOutput.putIfAbsent(targetId, new ArrayList<>());
				targetToValue.merge(targetId, institution.decisionEngine().fuzzy().targets().get(targetId).desiredValue(),
						Double::sum);
			});
		}
		configuration.targets().forEach((targetId, definition) -> {
			targetDefinitions.put(targetId, definition);
			targetObservations.put(targetId, definition.observations());
		});
	}

	private void prepareModelOutput() {
		targetObservations.forEach((target, observations) -> {
			TargetDefinition definition = targetDefinitions.getOrDefault(target,
					new TargetDefinition(target, target, observations, NormalizationType.RAW, Map.of()));
			TargetObservation observation = targetObserver.observe(definition,
					new SpatialScope(SpatialScope.Type.ALL_CELLS, ""));
			LOGGER.info(target + "; " + observation.normalizedValue());
			prepModelOutput.get(target).add(observation.normalizedValue());
		});
	}

	public TargetModelOutput step(InstitutionOutput institutionOutput) {
		MainHeadless.runner.step();
		FuzzyExternalValues.injectValues();
		CellPolicyState.clear(CellsLoader.hashCell.values());
		// runner.step() advances the clock before institution output is recorded, so
		// the effects being applied belong to the preceding (reported) model year.
		applyPolicyEffects(institutionOutput, Timestep.getCurrentYear() - 1);
		prepareModelOutput();
		DataCollector.outputFiles();
		return output();
	}

	private TargetModelOutput output() {
		return new TargetModelOutput(prepModelOutput(), prepEstimatedQuantities(), prepBudget());
	}

	private void applyPolicyEffects(InstitutionOutput institutionOutput, int effectYear) {
		// Apply each policy effect
		for (Map.Entry<String, HashMap<String, Double>> entryInstitution : institutionOutput.output().entrySet()) {
			String institutionName = entryInstitution.getKey();
			ActivationSchedule schedule = institutionSchedules.get(institutionName);
			if (schedule == null) {
				throw new IllegalArgumentException("Unknown institution schedule: " + institutionName);
			}
			boolean active = schedule.includes(effectYear);
			HashMap<String, Double> policyValues = entryInstitution.getValue();
			for (Map.Entry<String, Double> entryPolicies : policyValues.entrySet()) {
				String policyName = entryPolicies.getKey();
				double policyValue = entryPolicies.getValue();
				double appliedValue = active ? policyValue : 0.0;
				LOGGER.info(institutionName + "@ " + policyName + "-> " + appliedValue);
				// Update current policy values map
				policyListener.put(institutionName + "@" + policyName, appliedValue);
				if (active) {
					applyOnePolicy(institutionName, policyName, policyValue);
				} else {
					resetPolicyEffectListener(institutionName, policyName);
				}
			}
		}
	}

	private void resetPolicyEffectListener(String institutionName, String policyName) {
		String policyId = institutionName + "@" + policyName;
		cellPolicyEffects.getOrDefault(policyId, List.of()).forEach(effect ->
				policyEffectsListner.put(policyId + "@" + effect.name(), 0.0));
	}

	private void applyOnePolicy(String instuteName, String policyName, double policyValue) {
		String policyId = instuteName + "@" + policyName;
		if (!cellPolicyEffects.containsKey(policyId)) {
			LOGGER.warn("Unknown policy or instute: " + policyName + ", " + instuteName);
			return;
		}
		LOGGER.info("==> " + instuteName + "@" + policyName + "@" + policyValue);
		policyEffectApplier.apply(cellPolicyEffects.get(policyId), cell -> policyValue,
				policyScopes.getOrDefault(policyId, new SpatialScope(SpatialScope.Type.ALL_CELLS, "")));
		cellPolicyEffects.get(policyId).forEach(effect -> policyEffectsListner.put(
				instuteName + "@" + policyName + "@" + effect.name(), effect.weight() * policyValue));
	}

	HashMap<String, ArrayList<Double>> prepModelOutput() {
		return prepModelOutput;
	}

	private HashMap<String, HashMap<String, Double>> prepEstimatedQuantities() {
		HashMap<String, HashMap<String, Double>> estimatedQuantities = new HashMap<>();
		configuration.institutions().values().forEach(institution -> {
			HashMap<String, Double> values = new HashMap<>();
			institution.policies().values().forEach(policy ->
					values.put(policy.name(), policy.cost().estimatedQuantity()));
			estimatedQuantities.put(institution.name(), values);
		});
		return estimatedQuantities;

	}

	private HashMap<String, Double> prepBudget() {
		HashMap<String, Double> budget = new HashMap<>();
		configuration.institutions().values()
				.forEach(institution -> budget.put(institution.name(), institution.budget().amount()));
		return budget;
	}

}
