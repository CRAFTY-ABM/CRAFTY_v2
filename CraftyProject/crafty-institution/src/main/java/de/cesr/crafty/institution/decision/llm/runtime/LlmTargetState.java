package de.cesr.crafty.institution.decision.llm.runtime;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.non_java_code_controller.External_variables_Manager_manager;
import de.cesr.crafty.institution.model.EffectType;
import de.cesr.crafty.institution.model.SpatialScope;
import de.cesr.crafty.institution.model.TargetDefinition;
import de.cesr.crafty.institution.runtime.CraftyCellServiceValueProvider;
import de.cesr.crafty.institution.runtime.ObservationBaselines;
import de.cesr.crafty.institution.runtime.TargetObservation;
import de.cesr.crafty.institution.runtime.TargetObserver;

public final class LlmTargetState {

	private final String name;
	private final String type;
	ParadigmRuntime paradigm;
	private final Map<String, Double> craftyElem;// <ElmName,weight>;
	private double annualValue;

	private Map<Integer, Double> history = new LinkedHashMap<>();

	private Map<Integer, Double> optimist_goals = new LinkedHashMap<>();
	private Map<Integer, Double> realist_goals = new LinkedHashMap<>();
	private Map<Integer, Double> pessimist_goals = new LinkedHashMap<>();

	private double annualValueInParadigm;
	private Map<Integer, Double> historyInParadigm = new LinkedHashMap<>();
	private final ObservationBaselines observationBaselines = new ObservationBaselines();
	private final TargetObserver targetObserver = new TargetObserver(this::resolveCells,
			new CraftyCellServiceValueProvider(), External_variables_Manager_manager::getExternal_variables,
			observationBaselines);
	private final TargetDefinition commonDefinition;

	public LlmTargetState(TargetDefinition definition) {
		this.commonDefinition = java.util.Objects.requireNonNull(definition, "definition");
		this.name = definition.name();
		this.type = display(definition.observations().get(0).type());
		this.craftyElem = new LinkedHashMap<>();
		definition.observations().forEach(element -> craftyElem.put(element.name(), element.weight()));
		optimist_goals = new LinkedHashMap<>(definition.goalTrajectories().getOrDefault("optimist", Map.of()));
		realist_goals = new LinkedHashMap<>(definition.goalTrajectories().getOrDefault("realist", Map.of()));
		pessimist_goals = new LinkedHashMap<>(definition.goalTrajectories().getOrDefault("pessimist", Map.of()));
		fillGoals();
	}

	public void setParadigm(ParadigmRuntime paradigm) {
		this.paradigm = paradigm;

	}

	public void prepareModelOutput() {
		TargetDefinition definition = definition();
		TargetObservation global = targetObserver.observe(definition,
				new SpatialScope(SpatialScope.Type.ALL_CELLS, ""));
		TargetObservation inParadigm = targetObserver.observe(definition,
				new SpatialScope(SpatialScope.Type.PARADIGM, paradigm.getName()));
		annualValue = global.normalizedValue();
		annualValueInParadigm = inParadigm.normalizedValue();
		history.put(Timestep.getCurrentYear() - 1, annualValue);
		historyInParadigm.put(Timestep.getCurrentYear() - 1, annualValueInParadigm);
	}

	public void initializeObservationBaselines() {
		if (commonDefinition.observations().stream().noneMatch(element -> element.type() == EffectType.SERVICE)) {
			return;
		}
		TargetDefinition definition = definition();
		targetObserver.observe(definition, new SpatialScope(SpatialScope.Type.ALL_CELLS, ""));
		targetObserver.observe(definition, new SpatialScope(SpatialScope.Type.PARADIGM, paradigm.getName()));
	}

	private TargetDefinition definition() {
		return commonDefinition;
	}

	private static String display(de.cesr.crafty.institution.model.EffectType type) {
		return type == de.cesr.crafty.institution.model.EffectType.AFT ? "AFT"
				: type.name().charAt(0) + type.name().substring(1).toLowerCase();
	}

	private Collection<de.cesr.crafty.core.crafty.Cell> resolveCells(SpatialScope scope) {
		if (scope.type() == SpatialScope.Type.PARADIGM) {
			return paradigm == null ? List.of() : paradigm.getCells();
		}
		return CellsLoader.hashCell.values();
	}

	public void fillGoals() {
		optimist_goals = buildYearlyGoals(optimist_goals, Timestep.getStartYear(), Timestep.getEndtYear());
		realist_goals = buildYearlyGoals(realist_goals, Timestep.getStartYear(), Timestep.getEndtYear());
		pessimist_goals = buildYearlyGoals(pessimist_goals, Timestep.getStartYear(), Timestep.getEndtYear());
	}

	public String getName() {
		return name;
	}

	public String getType() {
		return type;
	}

	public Map<String, Double> getCraftyElem() {
		return craftyElem;
	}


	public Map<Integer, Double> getHistory() {
		return history;
	}

	public double getAnnualValue() {
		return annualValue;
	}

	public Map<Integer, Double> getOptimist_goals() {
		return optimist_goals;
	}

	public Map<Integer, Double> getRealist_goals() {
		return realist_goals;
	}

	public Map<Integer, Double> getPessimist_goals() {
		return pessimist_goals;
	}

	public Map<Integer, Double> getHistoryInParadigm() {
		return historyInParadigm;
	}

	@Override
	public String toString() {
		final int maxLen = 3;
		return "Target [name=" + name + ", type=" + type + ", craftyElem="
				+ (craftyElem != null ? toString(craftyElem.entrySet(), maxLen) : null) + ", annualValue=" + annualValue
				+ ", history=" + (history != null ? toString(history.entrySet(), maxLen) : null) + "]";
	}

	private String toString(Collection<?> collection, int maxLen) {
		StringBuilder builder = new StringBuilder();
		builder.append("[");
		int i = 0;
		for (Iterator<?> iterator = collection.iterator(); iterator.hasNext() && i < maxLen; i++) {
			if (i > 0) {
				builder.append(", ");
			}
			builder.append(iterator.next());
		}
		builder.append("]");
		return builder.toString();
	}

	private LinkedHashMap<Integer, Double> buildYearlyGoals(Map<Integer, Double> sparseGoals, int startYear,
			int endYear) {

		if (endYear < startYear) {
			throw new IllegalArgumentException("endYear must be >= startYear.");
		}

		final double INITIAL_VALUE = 1.0;

		// Sort goal years, because LinkedHashMap order may not always be guaranteed
		// from YAML logic.
		Map<Integer, Double> sortedGoals = new java.util.TreeMap<>();

		if (sparseGoals != null) {
			if (sparseGoals.isEmpty()) {
				return new LinkedHashMap<>();
			}

			for (Map.Entry<Integer, Double> entry : sparseGoals.entrySet()) {
				Integer year = entry.getKey();
				Double value = entry.getValue();

				if (year == null) {
					throw new IllegalArgumentException("Goal year cannot be null.");
				}

				if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
					throw new IllegalArgumentException("Invalid goal value for year " + year + ": " + value);
				}

				if (year < startYear) {
					throw new IllegalArgumentException("Goal year " + year + " is before startYear " + startYear + ".");
				}

				if (year == startYear && Math.abs(value - INITIAL_VALUE) > 1e-9) {
					throw new IllegalArgumentException("Goal year " + year + " conflicts with the initial value. "
							+ "The start year is always fixed to " + INITIAL_VALUE + ".");
				}

				if (year > startYear) {
					sortedGoals.put(year, value);
				}
			}
		}

		LinkedHashMap<Integer, Double> yearlyGoals = new LinkedHashMap<>();

		int previousYear = startYear;
		double previousValue = INITIAL_VALUE;

		yearlyGoals.put(startYear, INITIAL_VALUE);

		for (Map.Entry<Integer, Double> goal : sortedGoals.entrySet()) {
			int nextYear = goal.getKey();
			double nextValue = goal.getValue();

			int toYear = Math.min(nextYear, endYear);

			for (int year = previousYear + 1; year <= toYear; year++) {
				double value = interpolateLinear(year, previousYear, previousValue, nextYear, nextValue);
				yearlyGoals.put(year, value);
			}

			if (endYear <= nextYear) {
				return yearlyGoals;
			}

			previousYear = nextYear;
			previousValue = nextValue;
		}

		// After the last goal year, keep the last value constant.
		for (int year = previousYear + 1; year <= endYear; year++) {
			yearlyGoals.put(year, previousValue);
		}

		return yearlyGoals;
	}

	private static double interpolateLinear(int year, int startYear, double startValue, int endYear, double endValue) {

		if (endYear == startYear) {
			return endValue;
		}

		double ratio = (double) (year - startYear) / (double) (endYear - startYear);
		return startValue + ratio * (endValue - startValue);
	}

}
