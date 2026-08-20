package de.cesr.crafty.institution.decision.fuzzy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.cesr.crafty.institution.orchestration.DecisionMode;
import de.cesr.crafty.institution.orchestration.InstitutionContext;
import de.cesr.crafty.institution.orchestration.InstitutionDecision;
import de.cesr.crafty.institution.orchestration.InstitutionDecisionEngine;
/** Fuzzy calculations behind the shared decision-engine boundary. */
public final class FuzzyDecisionEngine implements InstitutionDecisionEngine {
	private static final Logger LOGGER = Logger.getLogger(FuzzyDecisionEngine.class.getName());
	private final List<FuzzyPolicyState> policies;
	private final FuzzyEvaluator evaluator;
	private final int timeLag;
	private final Set<String> requiredTargets;
	private final Set<String> policyNames;
	private final boolean optimizeBudget;

	public FuzzyDecisionEngine(List<FuzzyPolicyState> policies, FuzzyEvaluator evaluator, int timeLag,
			boolean optimizeBudget) {
		this.policies = List.copyOf(policies);
		this.evaluator = java.util.Objects.requireNonNull(evaluator, "evaluator");
		if (timeLag <= 0) {
			throw new IllegalArgumentException("Fuzzy time lag must be positive");
		}
		this.timeLag = timeLag;
		this.optimizeBudget = optimizeBudget;
		this.requiredTargets = policies.stream().flatMap(policy -> policy.goals().keySet().stream())
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
		this.policyNames = policies.stream().map(FuzzyPolicyState::name)
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	@Override
	public InstitutionDecision decide(InstitutionContext context) {
		validateInputs(context);
		if (context.decisionDue()) {
			evaluateGaps(context.targetHistories());
			inferPolicyChanges();
			applyInertia();
			updateModifiers();
		}
		resolveAbsoluteValues(context.estimatedQuantities());
		estimateCosts(context.estimatedQuantities());
		if (optimizeBudget) {
			allocateBudget(context.availableBudget(), context.estimatedQuantities());
		}
		Map<String, Double> values = new LinkedHashMap<>();
		policies.forEach(policy -> values.put(policy.name(), policy.value()));
		return new InstitutionDecision(values, DecisionMode.ABSOLUTE, context.decisionDue(), "");
	}

	private void validateInputs(InstitutionContext context) {
		if (!context.targetHistories().keySet().containsAll(requiredTargets)) {
			var missing = new java.util.HashSet<>(requiredTargets);
			missing.removeAll(context.targetHistories().keySet());
			throw new IllegalArgumentException("Missing required inputs: " + missing);
		}
		if (!context.estimatedQuantities().keySet().containsAll(policyNames)) {
			throw new IllegalArgumentException("Missing required policy quantities in estimated values" + policyNames);
		}
	}

	private void evaluateGaps(Map<String, List<Double>> histories) {
		for (FuzzyPolicyState policy : policies) {
			policy.goals().forEach((target, goal) -> {
				List<Double> history = histories.get(target);
				if (history.isEmpty()) {
					throw new IllegalArgumentException("Input history cannot be empty: " + target);
				}
				double error = PidErrorCalculator.calculateWeightedError(new ArrayList<>(history), goal, 0, 1, 0,
						timeLag);
				policy.gaps().put(target + "_gap", error);
			});
		}
	}

	private void inferPolicyChanges() {
		for (FuzzyPolicyState policy : policies) {
			try {
				policy.change(evaluator.evaluate(policy.functionBlock(), Map.copyOf(policy.gaps())));
			} catch (RuntimeException exception) {
				LOGGER.log(Level.SEVERE, "Error evaluating fuzzy block '" + policy.functionBlock() + "'", exception);
				throw exception;
			}
		}
	}

	private void applyInertia() {
		policies.forEach(policy -> policy.change(
				clamp(policy.change(), policy.inertiaMin(), policy.inertiaMax())));
	}

	private void updateModifiers() {
		policies.forEach(policy -> policy.modifier(policy.modifier() + policy.change()));
	}

	private void resolveAbsoluteValues(Map<String, Double> quantities) {
		for (FuzzyPolicyState policy : policies) {
			if (quantities.get(policy.name()) == 0) {
				policy.value(0);
				continue;
			}
			policy.value(clamp(policy.modifier() * policy.stepSize(), policy.valueMin(), policy.valueMax()));
		}
	}

	private void estimateCosts(Map<String, Double> quantities) {
		policies.forEach(policy -> {
			double quantity = quantities.get(policy.name());
			policy.estimatedQuantity(quantity);
			policy.totalCost(policy.unitCost() * quantity);
		});
	}

	private void allocateBudget(double availableBudget, Map<String, Double> quantities) {
		Map<String, Double> weights = new LinkedHashMap<>();
		Map<String, Double> targetBudgets = new LinkedHashMap<>();
		List<FuzzyPolicyState> costed = new ArrayList<>();
		for (FuzzyPolicyState policy : policies) {
			if (quantities.get(policy.name()) != 0 && policy.unitCost() != 0) {
				weights.put(policy.name(), 1.0);
				targetBudgets.put(policy.name(), policy.totalCost());
				costed.add(policy);
			}
		}
		if (costed.isEmpty() || availableBudget <= 0) {
			return;
		}
		Map<String, Double> allocations = QuadraticBudgetAllocator.allocate(weights, targetBudgets, availableBudget);
		costed.forEach(policy -> policy.value(allocations.get(policy.name()) / policy.estimatedQuantity()));
	}

	private static double clamp(double value, double first, double second) {
		return Math.max(Math.min(first, second), Math.min(Math.max(first, second), value));
	}
}
