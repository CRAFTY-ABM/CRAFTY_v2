package de.cesr.crafty.institution.decision.fuzzy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.cesr.crafty.institution.orchestration.InstitutionContext;
class FuzzyDecisionEngineTest {
	@Test
	void evaluatesThroughInjectedPortWithoutLoadingFclOrGlobalState() {
		FuzzyPolicyState policy = policy(100);
		FuzzyDecisionEngine engine = new FuzzyDecisionEngine(List.of(policy), (block, inputs) -> 10, 4, false);

		var decision = engine.decide(new InstitutionContext("test", 0, true,
				Map.of("msa", List.of(0.0)), Map.of("BIODIVERSITY", 10.0), 1000, ""));

		assertEquals(0.25, policy.change());
		assertEquals(1.25, policy.modifier());
		assertEquals(0.125, decision.policyValues().get("BIODIVERSITY"), 1.0e-12);
	}

	static FuzzyPolicyState policy(double upperBound) {
		return new FuzzyPolicyState("biodiversity", "BIODIVERSITY", Map.of("msa", 0.4), "BIODIVERSITY",
				-0.25, 0.25, 1, 0.1, 0, upperBound);
	}
}
