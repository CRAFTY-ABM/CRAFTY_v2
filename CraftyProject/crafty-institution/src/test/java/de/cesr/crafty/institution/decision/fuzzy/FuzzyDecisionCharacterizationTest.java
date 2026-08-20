package de.cesr.crafty.institution.decision.fuzzy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.cesr.crafty.institution.model.ActivationSchedule;
import de.cesr.crafty.institution.orchestration.DecisionMode;
import de.cesr.crafty.institution.orchestration.DecisionSchedule;
import de.cesr.crafty.institution.orchestration.InstitutionContext;
import de.cesr.crafty.institution.orchestration.InstitutionOrchestrator;
import de.cesr.crafty.institution.decision.fuzzy.FuzzyPolicyState;

class FuzzyDecisionCharacterizationTest {

	@Test
	void evaluatesAtStepZeroWhenConfiguredAndAppliesInertiaToAbsolutePolicyValue() throws Exception {
		FuzzyPolicyState policy = biodiversityPolicy(1.0E9);
		TestRuntime institution = institution(policy, 4, true);

		HashMap<String, Double> output = institution.step(inputs(0.0), quantities(100.0), 10_000.0);

		assertEquals(0.25, policy.change(), 1.0e-9);
		assertEquals(1.25, policy.modifier(), 1.0e-9);
		assertEquals(0.125, output.get("BIODIVERSITY"), 1.0e-9);
	}

	@Test
	void waitsUntilConfiguredLagWhenStepZeroEvaluationIsDisabled() throws Exception {
		FuzzyPolicyState policy = biodiversityPolicy(1.0E9);
		TestRuntime institution = institution(policy, 2, false);

		assertEquals(0.1, institution.step(inputs(0.0), quantities(100.0), 10_000.0)
				.get("BIODIVERSITY"), 1.0e-9);
		assertEquals(0.1, institution.step(inputs(0.0), quantities(100.0), 10_000.0)
				.get("BIODIVERSITY"), 1.0e-9);
		assertEquals(0.125, institution.step(inputs(0.0), quantities(100.0), 10_000.0)
				.get("BIODIVERSITY"), 1.0e-9);
	}

	@Test
	void clampsResolvedAbsolutePolicyValueToConfiguredBounds() throws Exception {
		FuzzyPolicyState policy = biodiversityPolicy(0.11);
		TestRuntime institution = institution(policy, 4, true);

		HashMap<String, Double> output = institution.step(inputs(0.0), quantities(100.0), 10_000.0);

		assertEquals(0.11, output.get("BIODIVERSITY"), 1.0e-9);
	}

	@Test
	void emitsZeroForPolicyWithZeroEstimatedQuantityButStillUpdatesDecisionState() throws Exception {
		FuzzyPolicyState policy = biodiversityPolicy(1.0E9);
		TestRuntime institution = institution(policy, 4, true);

		HashMap<String, Double> output = institution.step(inputs(0.0), quantities(0.0), 10_000.0);

		assertEquals(0.0, output.get("BIODIVERSITY"), 1.0e-9);
		assertEquals(0.25, policy.change(), 1.0e-9);
		assertEquals(1.25, policy.modifier(), 1.0e-9);
	}

	@Test
	void rejectsMissingTargetInputAndEstimatedQuantity() throws Exception {
		TestRuntime institution = institution(biodiversityPolicy(1.0E9), 4, true);

		assertThrows(IllegalArgumentException.class,
				() -> institution.step(new HashMap<>(), quantities(100.0), 10_000.0));
		assertThrows(IllegalArgumentException.class,
				() -> institution.step(inputs(0.0), new HashMap<>(), 10_000.0));
	}

	private static TestRuntime institution(FuzzyPolicyState policy, int timeLag, boolean startZero) {
		FuzzyDecisionEngine engine = new FuzzyDecisionEngine(List.of(policy), (block, inputs) -> 10, timeLag, false);
		return new TestRuntime(engine, policy, timeLag, startZero);
	}

	private static FuzzyPolicyState biodiversityPolicy(double upperBound) {
		return FuzzyDecisionEngineTest.policy(upperBound);
	}

	private static HashMap<String, ArrayList<Double>> inputs(double msa) {
		return new HashMap<>(java.util.Map.of("msa", new ArrayList<>(List.of(msa))));
	}

	private static HashMap<String, Double> quantities(double quantity) {
		return new HashMap<>(java.util.Map.of("BIODIVERSITY", quantity));
	}

	private static final class TestRuntime {
		private final InstitutionOrchestrator orchestrator;
		private int step;

		private TestRuntime(FuzzyDecisionEngine engine, FuzzyPolicyState policy, int interval,
				boolean startAtFirstStep) {
			Map<String, Double> initial = new LinkedHashMap<>();
			initial.put(policy.name(), policy.value());
			orchestrator = new InstitutionOrchestrator(engine,
					new DecisionSchedule(new ActivationSchedule(0, 100, interval),
							startAtFirstStep ? 0 : interval), DecisionMode.ABSOLUTE, initial);
		}

		private HashMap<String, Double> step(HashMap<String, ArrayList<Double>> inputs,
				HashMap<String, Double> quantities, double budget) {
			Map<String, List<Double>> histories = new LinkedHashMap<>();
			inputs.forEach((key, values) -> histories.put(key, values));
			var result = orchestrator.step(new InstitutionContext("test", step, false, histories, quantities, budget,
					""));
			step++;
			return new HashMap<>(result.effectiveValues());
		}
	}
}
