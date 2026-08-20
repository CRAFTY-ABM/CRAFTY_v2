package de.cesr.crafty.institution.decision.llm;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.cesr.crafty.institution.model.CraftyElementRef;
import de.cesr.crafty.institution.model.EffectType;
import de.cesr.crafty.institution.model.NormalizationType;
import de.cesr.crafty.institution.model.TargetDefinition;
import de.cesr.crafty.institution.decision.llm.runtime.LlmPolicyState;
import de.cesr.crafty.institution.decision.llm.runtime.LlmTargetState;
import de.cesr.crafty.institution.decision.llm.runtime.ParadigmRuntime;

class LlmPromptBuilderTest {
	@Test
	void buildsDeterministicPromptWithStrictKeysTargetsAndPolicyHistory() {
		ParadigmRuntime paradigm = new ParadigmRuntime("EM");
		LlmPolicyState policy = new LlmPolicyState("land_subsidy", paradigm);
		policy.decisionHistory().add(1.5);
		LlmTargetState target = new LlmTargetState(new TargetDefinition("biodiversity", "biodiversity",
				java.util.List.of(new CraftyElementRef(EffectType.EXTERNAL, "msa", 1)),
				NormalizationType.RAW, Map.of()));
		target.getHistory().put(2020, 0.2);
		target.getHistoryInParadigm().put(2020, 0.1);

		String prompt = new LlmPromptBuilder().build("Base", Map.of("land_subsidy", policy),
				new LinkedHashMap<>(Map.of("biodiversity", target)), 4, 2020, 2020);

		assertTrue(prompt.contains("EXACTLY these keys: [land_subsidy]"));
		assertTrue(prompt.contains("EU-historical_indicators"));
		assertTrue(prompt.contains("historical_indicators (REGIONAL)"));
		assertTrue(prompt.contains("\"2020\": 1.50"));
	}
}
