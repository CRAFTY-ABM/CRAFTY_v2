package de.cesr.crafty.gui.institutes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.institution.model.CraftyElementRef;
import de.cesr.crafty.institution.model.EffectType;
import de.cesr.crafty.institution.model.PolicyDefinition;
import de.cesr.crafty.institution.orchestration.PolicyStateSnapshot;
import de.cesr.crafty.institution.model.SpatialScope;
import de.cesr.crafty.institution.runtime.PolicyApplicationResult;
import de.cesr.crafty.institution.runtime.PolicyEffectApplier;

/** GUI policy state; all structural data comes from {@link PolicyDefinition}. */
public final class PolicyViewModel {
	private static final CustomLogger LOGGER = new CustomLogger(PolicyViewModel.class);

	private final PolicyDefinition definition;
	private final SpatialScope scope;
	private final PolicyCostCounter costCounter = new PolicyCostCounter();
	private final PolicyEffectApplier effectApplier = new PolicyEffectApplier(ignored -> CellsLoader.hashCell.values());
	private final List<Double> decisionsHistory = new ArrayList<>();
	private final List<Double> recorder = new ArrayList<>();
	private final Map<String, Map<String, Double>> effectsByType;
	private double value;
	private double accumulatedValue;
	private double lastPolicyCost;
	private double totalPolicyCost;

	public PolicyViewModel(PolicyDefinition definition, SpatialScope scope) {
		this.definition = Objects.requireNonNull(definition, "definition");
		this.scope = Objects.requireNonNull(scope, "scope");
		this.effectsByType = effectsByType(definition.effects());
	}

	public void step() {
		recorder.add(value);
		costCounter.reset();
		definition.effects().forEach(effect -> LOGGER.trace(effect.type() + " .. " + effect.name() + " = "
				+ effect.weight()));
		PolicyApplicationResult result = effectApplier.apply(definition.effects(), cell -> value, scope);
		for (long i = 0; i < result.applicationCount(); i++) {
			costCounter.recordApplication(1, getCost());
		}
		lastPolicyCost = calibratedPolicyCost();
		totalPolicyCost += lastPolicyCost;
	}

	private double calibratedPolicyCost() {
		double intensity = Math.abs(value);
		double expected = getInitialExpectedPolicyCost();
		return expected > 0 ? costCounter.getTotalCost() / expected * intensity
				: costCounter.getTotalCost() * intensity;
	}

	public PolicyDefinition getDefinition() {
		return definition;
	}

	public PolicyStateSnapshot snapshot() {
		return new PolicyStateSnapshot(value, value, decisionsHistory, recorder);
	}

	public String getName() {
		return definition.name();
	}

	public double getValue() {
		return value;
	}

	public void setValue(double value) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException("Policy value must be finite");
		}
		this.value = definition.constraints().value().clamp(value);
		decisionsHistory.add(this.value);
	}

	public double getCost() {
		return definition.cost().unitCost();
	}

	public double getInitialExpectedPolicyCost() {
		return definition.cost().estimatedTotalCost();
	}

	public long getPolicyApplicationCount() {
		return costCounter.getApplicationCount();
	}

	public double getTotalPolicyCost() {
		return totalPolicyCost;
	}

	public double getLastPolicyCost() {
		return lastPolicyCost;
	}

	public void resetPolicyCostCounter() {
		costCounter.reset();
		lastPolicyCost = 0;
		totalPolicyCost = 0;
	}

	public void resetRuntimeState() {
		value = 0;
		accumulatedValue = 0;
		decisionsHistory.clear();
		recorder.clear();
		resetPolicyCostCounter();
	}

	public double getAccumulatedValue() {
		return accumulatedValue;
	}

	public void setAccumulatedValue(double additionalValue) {
		accumulatedValue += additionalValue;
	}

	public Map<String, Map<String, Double>> getCraftyElem() {
		return effectsByType;
	}

	public List<Double> getDecisionHistory() {
		return decisionsHistory;
	}

	public List<Double> getEffectiveHistory() {
		return recorder;
	}

	private static Map<String, Map<String, Double>> effectsByType(List<CraftyElementRef> effects) {
		Map<String, Map<String, Double>> result = new LinkedHashMap<>();
		for (CraftyElementRef effect : effects) {
			result.computeIfAbsent(display(effect.type()), ignored -> new LinkedHashMap<>())
					.put(effect.name(), effect.weight());
		}
		result.replaceAll((ignored, values) -> Map.copyOf(values));
		return Map.copyOf(result);
	}

	private static String display(EffectType type) {
		return switch (type) {
		case AFT -> "AFT";
		case SERVICE -> "Service";
		case CAPITAL -> "Capital";
		case EXTERNAL -> "External";
		};
	}

	@Override
	public String toString() {
		return "Policy [name=" + getName() + ", value=" + value + ", cost=" + getCost() + ", applicationCount="
				+ getPolicyApplicationCount() + ", lastPolicyCost=" + lastPolicyCost + ", totalPolicyCost="
				+ totalPolicyCost + ", craftyElem=" + effectsByType + "]";
	}
}
