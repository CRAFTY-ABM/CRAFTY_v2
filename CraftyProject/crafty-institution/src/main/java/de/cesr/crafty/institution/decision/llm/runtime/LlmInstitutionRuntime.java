package de.cesr.crafty.institution.decision.llm.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.institution.model.ActivationSchedule;
import de.cesr.crafty.institution.orchestration.DecisionMode;
import de.cesr.crafty.institution.orchestration.DecisionSchedule;
import de.cesr.crafty.institution.orchestration.InstitutionContext;
import de.cesr.crafty.institution.orchestration.InstitutionOrchestrator;
import de.cesr.crafty.institution.model.InstitutionDefinition;
import de.cesr.crafty.institution.model.PolicyDefinition;
import de.cesr.crafty.institution.model.TargetDefinition;
import de.cesr.crafty.institution.decision.llm.CsvLlmOutputWriter;
import de.cesr.crafty.institution.decision.llm.LlmClient;
import de.cesr.crafty.institution.decision.llm.LlmClientFactory;
import de.cesr.crafty.institution.decision.llm.LlmDecisionEngine;
import de.cesr.crafty.institution.decision.llm.LlmPromptBuilder;
import de.cesr.crafty.institution.decision.llm.LlmOutputSnapshot;
import de.cesr.crafty.institution.decision.llm.LlmOutputWriter;

/** Executable LLM state for one institution and paradigm. */
public final class LlmInstitutionRuntime {
	private static final CustomLogger LOGGER = new CustomLogger(LlmInstitutionRuntime.class);
	private final String name;
	private final ParadigmRuntime paradigm;
	private final int timeLag;
	private final int startYear;
	private final int endYear;
	private final Map<String, LlmPolicyState> policies;
	private final Map<String, LlmTargetState> targets;
	private final String basePrompt;
	private String completePrompt;
	private String llmOutput = "";
	private final Supplier<LlmClient> llmClientSupplier;
	private final LlmOutputWriter outputWriter;
	private final LlmPromptBuilder promptBuilder = new LlmPromptBuilder();
	private InstitutionOrchestrator orchestrator;
	private final int retryCount;

	public LlmInstitutionRuntime(InstitutionDefinition definition,
			Map<String, TargetDefinition> targets, ParadigmRuntime paradigm) {
		this(definition.id(), paradigm, LlmClientFactory::createFromConfig, new CsvLlmOutputWriter(),
				definition.schedule().intervalYears(), definition.schedule().startYear(),
				definition.schedule().endYear(), definition.decisionEngine().llm().retryCount(),
				de.cesr.crafty.core.utils.file.PathTools
						.readFileToString(definition.decisionEngine().llm().promptFile()),
				policyViews(definition, paradigm), targetViews(definition, targets, paradigm));
	}

	private static Map<String, LlmPolicyState> policyViews(InstitutionDefinition definition,
			ParadigmRuntime paradigm) {
		Map<String, LlmPolicyState> policyViews = new LinkedHashMap<>();
		definition.policies().values().forEach(
				policy -> policyViews.put(policy.id(), policyView(policy, paradigm, definition.scope())));
		return policyViews;
	}

	private static Map<String, LlmTargetState> targetViews(InstitutionDefinition definition,
			Map<String, TargetDefinition> targets, ParadigmRuntime paradigm) {
		Map<String, LlmTargetState> targetViews = new LinkedHashMap<>();
		definition.targets().forEach(reference -> {
			var targetDefinition = targets.get(reference.targetId());
			LlmTargetState target = new LlmTargetState(targetDefinition);
			target.setParadigm(paradigm);
			targetViews.put(reference.targetId(), target);
			LlmTargetRuntimeSet.register(definition.id() + "|" + paradigm.getName() + "|" + reference.targetId(),
					target);
		});
		return targetViews;
	}

	private static LlmPolicyState policyView(PolicyDefinition definition, ParadigmRuntime paradigm,
			de.cesr.crafty.institution.model.SpatialScope scope) {
		return new LlmPolicyState(definition, paradigm, scope);
	}

	LlmInstitutionRuntime(String name, ParadigmRuntime paradigm, Supplier<LlmClient> llmClientSupplier,
			LlmOutputWriter outputWriter, int timeLag, int startYear, int endYear, int retryCount,
			String basePrompt, Map<String, LlmPolicyState> policies, Map<String, LlmTargetState> targets) {
		this.name = java.util.Objects.requireNonNull(name, "name");
		this.paradigm = java.util.Objects.requireNonNull(paradigm, "paradigm");
		this.llmClientSupplier = java.util.Objects.requireNonNull(llmClientSupplier, "llmClientSupplier");
		this.outputWriter = java.util.Objects.requireNonNull(outputWriter, "outputWriter");
		this.timeLag = timeLag;
		this.startYear = startYear;
		this.endYear = endYear;
		this.retryCount = retryCount;
		this.basePrompt = java.util.Objects.requireNonNull(basePrompt, "basePrompt");
		this.policies = new LinkedHashMap<>(policies);
		this.targets = new LinkedHashMap<>(targets);
	}

	public void preparePrompt() {
		llmOutput = "";
		completePrompt = promptBuilder.build(basePrompt, policies, targets, timeLag, startYear,
				Timestep.getStartYear());
	}

	public void connectLlm() {
		if (orchestrator == null) {
			initializeDecisionRuntime();
		}
		Map<String, List<Double>> targetHistories = new LinkedHashMap<>();
		targets.forEach(
				(targetName, target) -> targetHistories.put(targetName, new ArrayList<>(target.getHistory().values())));
		var result = orchestrator.step(
				new InstitutionContext(name, Timestep.getCurrentYear(), false, targetHistories, Map.of(), 0,
						completePrompt));
		llmOutput = result.rawOutput();
		policies.forEach((policyName, policy) -> {
			policy.setValue(result.decisionValues().get(policyName));
			policy.replaceAccumulatedValue(result.effectiveValues().get(policyName));
			if (result.decisionRecorded()) {
				policy.decisionHistory().add(result.decisionValues().get(policyName));
			}
			policy.effectiveHistory().add(result.effectiveValues().get(policyName));
		});
	}

	public void applyPolicies() {
		LOGGER.info("Institute (applied policies)= " + name);
		policies.values().forEach(LlmPolicyState::step);
		Map<String, Double> decisions = new LinkedHashMap<>();
		Map<String, Double> effective = new LinkedHashMap<>();
		policies.forEach((policyName, policy) -> {
			decisions.put(policyName, policy.getValue());
			effective.put(policyName, policy.getAccumulatedValue());
		});
		outputWriter.write(new LlmOutputSnapshot(name, paradigm.getName(), Timestep.getCurrentYear(), completePrompt,
				llmOutput, decisions, effective));
	}

	private void initializeDecisionRuntime() {
		Map<String, Double> initialValues = new LinkedHashMap<>();
		policies.forEach((policyName, policy) -> initialValues.put(policyName, policy.getAccumulatedValue()));
		var engine = new LlmDecisionEngine(llmClientSupplier, policies.keySet(), retryCount);
		orchestrator = new InstitutionOrchestrator(engine,
				new DecisionSchedule(new ActivationSchedule(startYear, endYear, timeLag), 1), DecisionMode.CHANGE,
				initialValues);
	}

	Map<String, LlmPolicyState> policies() {
		return policies;
	}
}
