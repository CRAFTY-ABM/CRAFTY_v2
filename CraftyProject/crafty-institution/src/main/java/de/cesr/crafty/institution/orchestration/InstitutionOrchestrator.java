package de.cesr.crafty.institution.orchestration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class InstitutionOrchestrator {
	private final InstitutionDecisionEngine engine;
	private final DecisionSchedule schedule;
	private final DecisionMode mode;
	private final Map<String, MutablePolicyState> states = new LinkedHashMap<>();

	public InstitutionOrchestrator(InstitutionDecisionEngine engine, DecisionSchedule schedule, DecisionMode mode,
			Map<String, Double> initialEffectiveValues) {
		this.engine = Objects.requireNonNull(engine, "engine");
		this.schedule = Objects.requireNonNull(schedule, "schedule");
		this.mode = Objects.requireNonNull(mode, "mode");
		if (initialEffectiveValues.isEmpty()) {
			throw new IllegalArgumentException("At least one policy state is required");
		}
		initialEffectiveValues.forEach((id, value) -> {
			if (id == null || id.isBlank() || value == null || !Double.isFinite(value)) {
				throw new IllegalArgumentException("Initial policy ids and values must be valid");
			}
			states.put(id, new MutablePolicyState(value));
		});
	}

	public synchronized OrchestrationResult step(InstitutionContext baseContext) {
		boolean due = schedule.isDue(baseContext.year());
		InstitutionContext context = baseContext.withDecisionDue(due);
		InstitutionDecision decision = Objects.requireNonNull(engine.decide(context), "Engine decision");
		if (decision.mode() != mode) {
			throw new IllegalStateException("Engine returned " + decision.mode() + " but orchestrator expects " + mode);
		}
		validateDecision(decision, due);

		boolean record = due && decision.accepted();
		Map<String, Double> current = new LinkedHashMap<>();
		Map<String, Double> effective = new LinkedHashMap<>();
		states.forEach((id, state) -> {
			Double value = decision.policyValues().get(id);
			if (mode == DecisionMode.ABSOLUTE && value != null) {
				state.currentDecision = value;
				state.effectiveValue = value;
			} else if (mode == DecisionMode.CHANGE && record) {
				state.currentDecision = value;
				state.effectiveValue += value;
			} else if (mode == DecisionMode.CHANGE) {
				state.currentDecision = 0;
			}
			if (record) {
				state.decisionHistory.add(state.currentDecision);
			}
			state.effectiveHistory.add(state.effectiveValue);
			current.put(id, state.currentDecision);
			effective.put(id, state.effectiveValue);
		});
		return new OrchestrationResult(record, current, effective, decision.rawOutput());
	}

	public Map<String, PolicyStateSnapshot> snapshots() {
		Map<String, PolicyStateSnapshot> result = new LinkedHashMap<>();
		states.forEach((id, state) -> result.put(id, state.snapshot()));
		return Map.copyOf(result);
	}

	private void validateDecision(InstitutionDecision decision, boolean due) {
		if (decision.accepted() && !due) {
			throw new IllegalStateException("Engine cannot accept a decision outside the configured schedule");
		}
		if (!decision.policyValues().isEmpty() && !decision.policyValues().keySet().equals(states.keySet())) {
			throw new IllegalStateException("Engine policy keys do not match configured policies");
		}
		if (decision.accepted() && !decision.policyValues().keySet().equals(states.keySet())) {
			throw new IllegalStateException("Accepted decision must contain every configured policy");
		}
		decision.policyValues().forEach((id, value) -> {
			if (value == null || !Double.isFinite(value)) {
				throw new IllegalStateException("Engine returned a non-finite value for policy " + id);
			}
		});
	}

	private static final class MutablePolicyState {
		private double currentDecision;
		private double effectiveValue;
		private final ArrayList<Double> decisionHistory = new ArrayList<>();
		private final ArrayList<Double> effectiveHistory = new ArrayList<>();

		private MutablePolicyState(double effectiveValue) {
			this.effectiveValue = effectiveValue;
		}

		private PolicyStateSnapshot snapshot() {
			return new PolicyStateSnapshot(currentDecision, effectiveValue, decisionHistory, effectiveHistory);
		}
	}
}
