package de.cesr.crafty.institution.decision.fuzzy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.cesr.crafty.institution.orchestration.DecisionMode;
import de.cesr.crafty.institution.orchestration.DecisionSchedule;
import de.cesr.crafty.institution.orchestration.InstitutionContext;
import de.cesr.crafty.institution.model.InstitutionDefinition;
import de.cesr.crafty.institution.orchestration.InstitutionOrchestrator;
import net.sourceforge.jFuzzyLogic.FIS;

/** Executable fuzzy state for one immutable institution definition. */
public final class FuzzyInstitutionRuntime {
	private final InstitutionDefinition definition;
	private final List<FuzzyPolicyState> policies;
	private final FuzzyDecisionEngine engine;
	private final InstitutionOrchestrator orchestrator;
	private long step;

	public FuzzyInstitutionRuntime(InstitutionDefinition definition) {
		this.definition = java.util.Objects.requireNonNull(definition, "definition");
		this.policies = definition.policies().values().stream()
				.map(policy -> FuzzyPolicyState.from(definition, policy)).toList();
		FIS fis = FIS.load(definition.decisionEngine().fuzzy().fclFile().toString(), true);
		if (fis == null) {
			throw new IllegalArgumentException("Could not load fuzzy FCL file: "
					+ definition.decisionEngine().fuzzy().fclFile());
		}
		this.engine = new FuzzyDecisionEngine(policies, new JFuzzyLogicEvaluator(fis),
				definition.schedule().intervalYears(),
				definition.decisionEngine().fuzzy().optimizeBudget());
		Map<String, Double> initial = new LinkedHashMap<>();
		policies.forEach(policy -> initial.put(policy.name(), policy.value()));
		int firstDecisionOffset = definition.decisionEngine().fuzzy().startAtFirstStep()
				? 0 : definition.schedule().intervalYears();
		this.orchestrator = new InstitutionOrchestrator(engine,
				new DecisionSchedule(definition.schedule(), firstDecisionOffset), DecisionMode.ABSOLUTE, initial);
	}

	public Map<String, Double> step(Map<String, ? extends List<Double>> targetHistories,
			Map<String, Double> estimatedQuantities, double budget) {
		Map<String, List<Double>> histories = new LinkedHashMap<>();
		targetHistories.forEach((key, value) -> histories.put(key, List.copyOf(value)));
		int year = Math.toIntExact((long) definition.schedule().startYear() + step);
		var result = orchestrator.step(new InstitutionContext(definition.id(), year, false, histories,
				estimatedQuantities, budget, ""));
		step++;
		return result.effectiveValues();
	}

	public String id() { return definition.id(); }
	public String name() { return definition.name(); }
	public InstitutionDefinition definition() { return definition; }
	public List<FuzzyPolicyState> policies() { return policies; }
}
