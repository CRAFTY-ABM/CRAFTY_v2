package de.cesr.crafty.institution.decision.fuzzy;

import java.util.LinkedHashMap;
import java.util.Map;

import de.cesr.crafty.institution.model.InstitutionDefinition;
import de.cesr.crafty.institution.model.PolicyDefinition;

/** Mutable decision state derived from one immutable common policy definition. */
public final class FuzzyPolicyState {
	private final String id;
	private final String name;
	private final Map<String, Double> goals;
	private final Map<String, Double> gaps = new LinkedHashMap<>();
	private final String functionBlock;
	private final double inertiaMin;
	private final double inertiaMax;
	private final double unitCost;
	private final double stepSize;
	private final double valueMin;
	private final double valueMax;
	private double estimatedQuantity;
	private double modifier = 1.0;
	private double change;
	private double value;
	private double totalCost;

	public FuzzyPolicyState(String id, String name, Map<String, Double> goals, String functionBlock,
			double inertiaMin, double inertiaMax, double unitCost, double stepSize,
			double valueMin, double valueMax) {
		this.id = id;
		this.name = name;
		this.goals = Map.copyOf(goals);
		this.functionBlock = functionBlock;
		this.inertiaMin = inertiaMin;
		this.inertiaMax = inertiaMax;
		this.unitCost = unitCost;
		this.stepSize = stepSize;
		this.valueMin = valueMin;
		this.valueMax = valueMax;
		this.value = stepSize;
	}

	public static FuzzyPolicyState from(InstitutionDefinition institution, PolicyDefinition definition) {
		FuzzyPolicySettings settings = institution.decisionEngine().fuzzy().policies().get(definition.id());
		if (settings == null) {
			throw new IllegalArgumentException("Missing fuzzy settings for policy '" + definition.id() + "'");
		}
		Map<String, Double> goals = new LinkedHashMap<>();
		institution.targets().forEach(reference -> goals.put(reference.targetId(), institution.decisionEngine().fuzzy()
				.targets().get(reference.targetId()).desiredValue()));
		return new FuzzyPolicyState(definition.id(), definition.name(), goals, settings.functionBlock(),
				settings.change().min(), settings.change().max(),
				definition.cost().unitCost(), settings.stepSize(), definition.constraints().value().min(),
				definition.constraints().value().max());
	}

	public String id() { return id; }
	public String name() { return name; }
	public Map<String, Double> goals() { return goals; }
	public Map<String, Double> gaps() { return gaps; }
	public String functionBlock() { return functionBlock; }
	public double inertiaMin() { return inertiaMin; }
	public double inertiaMax() { return inertiaMax; }
	public double unitCost() { return unitCost; }
	public double stepSize() { return stepSize; }
	public double valueMin() { return valueMin; }
	public double valueMax() { return valueMax; }
	public double estimatedQuantity() { return estimatedQuantity; }
	public void estimatedQuantity(double quantity) { this.estimatedQuantity = quantity; }
	public double modifier() { return modifier; }
	public void modifier(double modifier) { this.modifier = modifier; }
	public double change() { return change; }
	public void change(double change) { this.change = change; }
	public double value() { return value; }
	public void value(double value) { this.value = value; }
	public double totalCost() { return totalCost; }
	public void totalCost(double totalCost) { this.totalCost = totalCost; }
}
