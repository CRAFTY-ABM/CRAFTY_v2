package de.cesr.crafty.institution.decision.llm.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.institution.decision.llm.LlmClient;
import de.cesr.crafty.institution.decision.llm.LlmPolicyParser;
import de.cesr.crafty.institution.decision.llm.runtime.LlmInstitutionRuntime;
import de.cesr.crafty.institution.decision.llm.runtime.LlmPolicyState;
import de.cesr.crafty.institution.decision.llm.runtime.ParadigmRuntime;
import de.cesr.crafty.institution.decision.llm.LlmOutputSnapshot;
import de.cesr.crafty.institution.decision.llm.LlmOutputWriter;

class LlmDecisionCharacterizationTest {

	private int originalStartYear;
	private int originalEndYear;
	private int originalCurrentYear;

	@BeforeEach
	void setSimulationYears() {
		originalStartYear = Timestep.getStartYear();
		originalEndYear = Timestep.getEndtYear();
		originalCurrentYear = Timestep.getCurrentYear();
		Timestep.setStartYear(2020);
		Timestep.setEndtYear(2100);
	}

	@AfterEach
	void restoreSimulationYears() {
		Timestep.setStartYear(originalStartYear);
		Timestep.setEndtYear(originalEndYear);
		Timestep.setCurrentYear(originalCurrentYear);
	}

	@Test
	void callsLlmAtFirstDecisionYearAndAccumulatesValidPolicyChange() {
		FakeLlmClient client = new FakeLlmClient(validResponse(2.0));
		LlmInstitutionRuntime institute = institute(client, "biodiversity_land_subsidy");
		Timestep.setCurrentYear(2021);

		institute.preparePrompt();
		institute.connectLlm();

		LlmPolicyState policy = institute.policies().get("biodiversity_land_subsidy");
		assertEquals(1, client.callCount());
		assertEquals(2.0, policy.getValue());
		assertEquals(2.0, policy.getAccumulatedValue());
		assertEquals(List.of(2.0), policy.decisionHistory());
		assertEquals(List.of(2.0), policy.effectiveHistory());
	}

	@Test
	void doesNotCallLlmOutsideDecisionIntervalAndCarriesRecordedAbsoluteValueForward() {
		FakeLlmClient client = new FakeLlmClient(validResponse(3.0));
		LlmInstitutionRuntime institute = institute(client, "biodiversity_land_subsidy");

		Timestep.setCurrentYear(2021);
		institute.preparePrompt();
		institute.connectLlm();

		Timestep.setCurrentYear(2022);
		institute.preparePrompt();
		institute.connectLlm();

		LlmPolicyState policy = institute.policies().get("biodiversity_land_subsidy");
		assertEquals(1, client.callCount());
		assertEquals(0.0, policy.getValue());
		assertEquals(3.0, policy.getAccumulatedValue());
		assertEquals(List.of(3.0), policy.decisionHistory());
		assertEquals(List.of(3.0, 3.0), policy.effectiveHistory());
	}

	@Test
	void retriesMalformedResponseAndUsesSecondValidResponse() {
		FakeLlmClient client = new FakeLlmClient("not json", validResponse(-1.0));
		LlmInstitutionRuntime institute = institute(client, "biodiversity_land_subsidy");
		Timestep.setCurrentYear(2021);

		institute.preparePrompt();
		institute.connectLlm();

		LlmPolicyState policy = institute.policies().get("biodiversity_land_subsidy");
		assertEquals(2, client.callCount());
		assertTrue(client.prompts().get(1).contains("AUTOMATIC PARSER ERROR"));
		assertEquals(-1.0, policy.getValue());
		assertEquals(-1.0, policy.getAccumulatedValue());
	}

	@Test
	void retriesClientFailureAndUsesNextValidResponse() {
		FakeLlmClient client = new FakeLlmClient(new IllegalStateException("temporary failure"), validResponse(1.0));
		LlmInstitutionRuntime institute = institute(client, "biodiversity_land_subsidy");
		Timestep.setCurrentYear(2021);

		institute.preparePrompt();
		institute.connectLlm();

		assertEquals(2, client.callCount());
		assertEquals(1.0, institute.policies().get("biodiversity_land_subsidy").getValue());
	}

	@Test
	void fallsBackToZeroChangeAfterFourInvalidResponses() {
		FakeLlmClient client = new FakeLlmClient("bad one", "bad two", "bad three", "bad four");
		LlmInstitutionRuntime institute = institute(client, "biodiversity_land_subsidy");
		Timestep.setCurrentYear(2021);

		institute.preparePrompt();
		institute.connectLlm();

		LlmPolicyState policy = institute.policies().get("biodiversity_land_subsidy");
		assertEquals(4, client.callCount());
		assertEquals(0.0, policy.getValue());
		assertEquals(0.0, policy.getAccumulatedValue());
		assertTrue(policy.decisionHistory().isEmpty());
		assertEquals(List.of(0.0), policy.effectiveHistory());
	}

	@Test
	void treatsResponseWithMissingPolicyAsInvalidAndRetries() {
		FakeLlmClient client = new FakeLlmClient(
				"{\"reasoning\":\"x\",\"policy_decisions\":{\"first_policy\":1}}",
				"{\"reasoning\":\"x\",\"policy_decisions\":{\"first_policy\":1,\"second_policy\":2}}");
		LlmInstitutionRuntime institute = institute(client, "first_policy", "second_policy");
		Timestep.setCurrentYear(2021);

		institute.preparePrompt();
		institute.connectLlm();

		assertEquals(2, client.callCount());
		assertEquals(1.0, institute.policies().get("first_policy").getValue());
		assertEquals(2.0, institute.policies().get("second_policy").getValue());
	}

	@Test
	void parserNormalizesPolicyNamesAndAcceptsNumericStrings() {
		Map<String, Double> decisions = LlmPolicyParser.extractPolicyDecisionsOrNull(
				"{\"reasoning\":\"x\",\"policy_decisions\":{\"Biodiversity Land Subsidy\":\"2.5\"}}");

		assertEquals(Map.of("biodiversity_land_subsidy", 2.5), decisions);
	}

	@Test
	void writesThroughInjectedOutputPort() {
		FakeLlmClient client = new FakeLlmClient(validResponse(2.0));
		AtomicReference<LlmOutputSnapshot> written = new AtomicReference<>();
		ParadigmRuntime paradigm = new ParadigmRuntime("EM");
		LlmInstitutionRuntime institute = new LlmInstitutionRuntime("environment", paradigm, () -> client,
				written::set, 4, 2020, 2100, 4, "Test policy prompt",
				Map.of("biodiversity_land_subsidy",
						new LlmPolicyState("biodiversity_land_subsidy", paradigm)), Map.of());
		Timestep.setCurrentYear(2021);

		institute.preparePrompt();
		institute.connectLlm();
		institute.applyPolicies();

		assertEquals(2.0, written.get().effectiveValues().get("biodiversity_land_subsidy"));
		assertEquals("EM", written.get().scopeName());
	}

	private static LlmInstitutionRuntime institute(LlmClient client, String... policyNames) {
		ParadigmRuntime paradigm = new ParadigmRuntime("EM");
		Map<String, LlmPolicyState> policies = new LinkedHashMap<>();
		for (String policyName : policyNames) {
			policies.put(policyName, new LlmPolicyState(policyName, paradigm));
		}
		return new LlmInstitutionRuntime("environment", paradigm, () -> client, LlmOutputWriter.noOp(), 4, 2020,
				2100, 4, "Test policy prompt", policies, Map.of());
	}

	private static String validResponse(double decision) {
		return "{\"reasoning\":\"test\",\"policy_decisions\":{\"biodiversity_land_subsidy\":" + decision
				+ "}}";
	}

	private static final class FakeLlmClient implements LlmClient {
		private final Queue<Object> responses = new ArrayDeque<>();
		private final List<String> prompts = new ArrayList<>();

		private FakeLlmClient(Object... responses) {
			this.responses.addAll(List.of(responses));
		}

		@Override
		public String askLLM(String prompt) {
			prompts.add(prompt);
			Object response = responses.remove();
			if (response instanceof RuntimeException failure) {
				throw failure;
			}
			return (String) response;
		}

		private int callCount() {
			return prompts.size();
		}

		private List<String> prompts() {
			return prompts;
		}
	}
}
