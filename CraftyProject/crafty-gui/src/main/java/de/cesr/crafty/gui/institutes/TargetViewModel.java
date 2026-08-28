package de.cesr.crafty.gui.institutes;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.utils.non_java_code_controller.External_variables_Manager_manager;
import de.cesr.crafty.institution.model.CraftyElementRef;
import de.cesr.crafty.institution.model.EffectType;
import de.cesr.crafty.institution.config.Identifiers;
import de.cesr.crafty.institution.model.SpatialScope;
import de.cesr.crafty.institution.model.TargetDefinition;
import de.cesr.crafty.institution.orchestration.TargetStateSnapshot;
import de.cesr.crafty.institution.runtime.CraftyCellServiceValueProvider;
import de.cesr.crafty.institution.runtime.ObservationBaselines;
import de.cesr.crafty.institution.runtime.TargetObservation;
import de.cesr.crafty.institution.runtime.TargetObserver;

/** GUI target history backed by one immutable common target definition. */
public final class TargetViewModel {
	private final TargetDefinition definition;
	private final Map<String, Double> craftyElements;
	private final Map<String, Double> recorder = new HashMap<>();
	private final Map<Integer, Double> history = new LinkedHashMap<>();
	private final Map<String, Map<Integer, Double>> craftyElemHistory = new LinkedHashMap<>();
	private final ObservationBaselines observationBaselines = new ObservationBaselines();
	private final TargetObserver targetObserver = new TargetObserver(ignored -> CellsLoader.hashCell.values(),
			new CraftyCellServiceValueProvider(), TargetViewModel::externalValue, observationBaselines);
	private Map<Integer, Double> referenceHistory = new LinkedHashMap<>();
	private Map<Integer, Double> goalHistory = new LinkedHashMap<>();
	private double annualValue;

	public TargetViewModel(TargetDefinition definition) {
		this.definition = Objects.requireNonNull(definition, "definition");
		Map<String, Double> elements = new LinkedHashMap<>();
		definition.observations().forEach(element -> elements.put(element.name(), element.weight()));
		this.craftyElements = Map.copyOf(elements);
	}

	public void recordCraftyElementValues(int year) {
		TargetObservation observation = targetObserver.observe(definition,
				new SpatialScope(SpatialScope.Type.ALL_CELLS, ""));
		observation.components().forEach((craftyElement, value) -> {
			recorder.put(craftyElement, value);
			craftyElemHistory.computeIfAbsent(craftyElement, ignored -> new LinkedHashMap<>()).put(year, value);
		});
		annualValue = observation.normalizedValue();
		history.put(year, annualValue);
	}

	public void resetRecordedValues() {
		recorder.clear();
		history.clear();
		craftyElemHistory.clear();
		observationBaselines.clear();
		annualValue = 0;
	}

	public Map<Integer, Double> configuredGoal(String scenario) {
		if (definition.goalTrajectories().isEmpty()) {
			return Map.of();
		}
		if (scenario != null && !scenario.isBlank()) {
			Map<Integer, Double> selected = definition.goalTrajectories().get(Identifiers.normalize(scenario));
			if (selected != null) {
				return selected;
			}
		}
		return definition.goalTrajectories().values().iterator().next();
	}

	private static double externalValue(String name) {
		double value = External_variables_Manager_manager.getExternal_variables(name);
		return Double.isFinite(value) ? value : 0;
	}

	public TargetDefinition getDefinition() {
		return definition;
	}

	public TargetStateSnapshot snapshot() {
		return new TargetStateSnapshot(annualValue, history, referenceHistory, goalHistory);
	}

	public String getName() {
		return definition.name();
	}

	public String getType() {
		return display(definition.observations().getFirst().type());
	}

	public Map<String, Double> getCraftyElem() {
		return craftyElements;
	}

	public Map<Integer, Double> getHistory() {
		return history;
	}

	public Map<Integer, Double> getReferenceHistory() {
		return referenceHistory;
	}

	public void setReferenceHistory(Map<Integer, Double> referenceHistory) {
		this.referenceHistory = new LinkedHashMap<>(referenceHistory);
	}

	public Map<Integer, Double> getGoalHistory() {
		return goalHistory;
	}

	public void setGoalHistory(Map<Integer, Double> goalHistory) {
		this.goalHistory = new LinkedHashMap<>(goalHistory);
	}

	public Map<String, Double> getLatestComponents() {
		return recorder;
	}

	public Map<String, Map<Integer, Double>> getCraftyElemHistory() {
		return craftyElemHistory;
	}

	public double getAnnualValue() {
		return annualValue;
	}

	private static String display(EffectType type) {
		return type == EffectType.AFT ? "AFT"
				: type.name().charAt(0) + type.name().substring(1).toLowerCase();
	}
}
