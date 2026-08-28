package de.cesr.crafty.institution.orchestration;

import de.cesr.crafty.institution.model.ActivationSchedule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class InstitutionOrchestratorTest {
	@Test
	void schedulesAndAccumulatesAcceptedPolicyChanges() {
		InstitutionDecisionEngine engine = context -> context.decisionDue()
				? new InstitutionDecision(Map.of("policy", 2.0), DecisionMode.CHANGE, true, "raw")
				: InstitutionDecision.noDecision(DecisionMode.CHANGE);
		InstitutionOrchestrator orchestrator = new InstitutionOrchestrator(engine,
				new DecisionSchedule(new ActivationSchedule(2020, 2030, 4), 1), DecisionMode.CHANGE,
				Map.of("policy", 0.0));

		OrchestrationResult first = orchestrator.step(context(2021));
		OrchestrationResult carry = orchestrator.step(context(2022));
		OrchestrationResult second = orchestrator.step(context(2025));

		assertEquals(2, first.effectiveValues().get("policy"));
		assertEquals(0, carry.decisionValues().get("policy"));
		assertEquals(2, carry.effectiveValues().get("policy"));
		assertEquals(4, second.effectiveValues().get("policy"));
		assertEquals(List.of(2.0, 2.0), orchestrator.snapshots().get("policy").decisionHistory());
		assertEquals(List.of(2.0, 2.0, 4.0), orchestrator.snapshots().get("policy").effectiveHistory());
	}

	@Test
	void appliesAbsoluteEngineOutputEveryStepButRecordsOnlyScheduledDecisions() {
		InstitutionDecisionEngine engine = context -> new InstitutionDecision(
				Map.of("policy", context.decisionDue() ? 3.0 : 1.0), DecisionMode.ABSOLUTE,
				context.decisionDue(), "");
		InstitutionOrchestrator orchestrator = new InstitutionOrchestrator(engine,
				new DecisionSchedule(new ActivationSchedule(0, 20, 2), 2), DecisionMode.ABSOLUTE,
				Map.of("policy", 1.0));

		assertEquals(1, orchestrator.step(context(0)).effectiveValues().get("policy"));
		assertEquals(3, orchestrator.step(context(2)).effectiveValues().get("policy"));
		assertEquals(List.of(3.0), orchestrator.snapshots().get("policy").decisionHistory());
	}

	private static InstitutionContext context(int year) {
		return new InstitutionContext("institution", year, false, Map.of(), Map.of(), 100, "");
	}
}
