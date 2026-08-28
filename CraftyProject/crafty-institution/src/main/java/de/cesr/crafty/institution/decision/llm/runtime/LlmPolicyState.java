package de.cesr.crafty.institution.decision.llm.runtime;

import java.util.ArrayList;
import java.util.List;

import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.institution.model.CraftyElementRef;
import de.cesr.crafty.institution.model.PolicyDefinition;
import de.cesr.crafty.institution.model.SpatialScope;
import de.cesr.crafty.institution.runtime.PolicyEffectApplier;

public final class LlmPolicyState {
	private static final CustomLogger LOGGER = new CustomLogger(LlmPolicyState.class);
	private final String name;
	private double value;
	private double sensitivity = 1;
	private final List<CraftyElementRef> effects;
	private final ParadigmRuntime paradigm;
	private final SpatialScope scope;
	private double accumulatedValue;
	private final List<Double> decisionHistory = new ArrayList<>();
	private final List<Double> effectiveHistory = new ArrayList<>();
	private final PolicyEffectApplier effectApplier;

	public LlmPolicyState(String name, ParadigmRuntime paradigm) {
		this(name, List.of(), paradigm, new SpatialScope(SpatialScope.Type.PARADIGM, paradigm.getName()));
	}

	public LlmPolicyState(PolicyDefinition definition, ParadigmRuntime paradigm, SpatialScope scope) {
		this(definition.id(), definition.effects(), paradigm, scope);
	}

	private LlmPolicyState(String name, List<CraftyElementRef> effects, ParadigmRuntime paradigm, SpatialScope scope) {
		this.name = name;
		this.effects = List.copyOf(effects);
		this.paradigm = paradigm;
		this.scope = java.util.Objects.requireNonNull(scope, "scope");
		this.effectApplier = new PolicyEffectApplier(ignoredScope -> paradigm.getCells());
	}

	public void step() {
		applyPolicy();
	}

	private void applyPolicy() {
		effects.forEach(effect -> LOGGER.trace(effect.type() + " .. " + effect.name() + " = " + effect.weight()));
		effectApplier.apply(effects, cell -> sensitivity * findPolicyValue(cell), scope);
	}

	private double findPolicyValue(Cell c) {
		double v = 0;
		if (c.getCurrentRegion() == null) {
			return 0;
		}
		if (paradigm.getDelay().getOrDefault(c.getCurrentRegion(), 0) > effectiveHistory.size() - 1) {
			v = 0;
		} else {
			v = effectiveHistory
					.get(effectiveHistory.size() - 1 - paradigm.getDelay().getOrDefault(c.getCurrentRegion(), 0));
		}
		return v;
	}

	public String getName() {
		return name;
	}

	public double getValue() {
		return value;
	}

	public void setValue(double value) {
		this.value = value;
	}

	public double getAccumulatedValue() {
		return accumulatedValue;
	}

	public void replaceAccumulatedValue(double accumulatedValue) {
		this.accumulatedValue = accumulatedValue;
	}

	public List<Double> decisionHistory() {
		return decisionHistory;
	}

	public List<Double> effectiveHistory() {
		return effectiveHistory;
	}

}
