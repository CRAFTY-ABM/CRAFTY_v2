package large_language_models_institutions;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import de.cesr.crafty.core.crafty.RegionalModelRunner;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.updaters.Timestep;
import utils.External_variables_Manager;

public class Target {

	private String name;
	private String type;
	private Map<String, Double> craftyElem = new HashMap<>();// <ElmName,weight>;
	private double annualValue;
	private Map<Integer, Double> history = new LinkedHashMap<>();

	private Map<Integer, Double> optimist_goals = new LinkedHashMap<>();
	private Map<Integer, Double> realist_goals = new LinkedHashMap<>();
	private Map<Integer, Double> pessimist_goals = new LinkedHashMap<>();

	public Target(String name) {
		this.name = name;
	}

	public void PreparModelOutput(RegionalModelRunner r) {
		annualValue = 0;
		double w = 0;
		for (Map.Entry<String, Double> entry : craftyElem.entrySet()) {
			String elemName = entry.getKey();
			Double weight = entry.getValue();

			if (type.equals("Service")) {
				double initial_supply = Targets_Set.initial_supplys.get(r.R.getName()).get(elemName);
				initial_supply = initial_supply != 0 ? initial_supply : 1;
				annualValue += weight * (r.getRegionalSupply().get(elemName) / initial_supply);
			} else if (type.equals("AFT")) {
				int nmbr0 = AFTsLoader.hashAgentNbr_initialYear.get(elemName);
				nmbr0 = nmbr0 != 0 ? nmbr0 : 1;
				annualValue += weight * AFTsLoader.hashAgentNbr.get(elemName) / nmbr0;
			} else if (type.equals("external")) {
				annualValue += External_variables_Manager.getExternal_variables(elemName);
			}
			w += weight;
		}
		annualValue = annualValue / w;
		history.put(Timestep.getCurrentYear() - 1, annualValue);

//		System.out.println(name+"=> "+annualValue+":  history "+ history);
	}

	public void fillGoals() {
		optimist_goals = buildYearlyGoals(optimist_goals, Timestep.getStartYear(), Timestep.getEndtYear());
		realist_goals = buildYearlyGoals(realist_goals, Timestep.getStartYear(), Timestep.getEndtYear());
		pessimist_goals = buildYearlyGoals(pessimist_goals, Timestep.getStartYear(), Timestep.getEndtYear());
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Map<String, Double> getCraftyElem() {
		return craftyElem;
	}

	public void setCraftyElem(Map<String, Double> craftyElem) {
		this.craftyElem = craftyElem;
	}

	public Map<Integer, Double> getHistory() {
		return history;
	}

	public double getAnnualValue() {
		return annualValue;
	}

	public void setAnnualValue(double annualValue) {
		this.annualValue = annualValue;
	}

	public Map<Integer, Double> getOptimist_goals() {
		return optimist_goals;
	}

	public void setOptimist_goals(Map<Integer, Double> optimist_goals) {
		this.optimist_goals = optimist_goals;
	}

	public Map<Integer, Double> getRealist_goals() {
		return realist_goals;
	}

	public void setOptimistGoals(Map<Integer, Double> optimistGoals) {
		this.optimist_goals = new LinkedHashMap<>(optimistGoals);
	}

	public void setRealistGoals(Map<Integer, Double> realistGoals) {
		this.realist_goals = new LinkedHashMap<>(realistGoals);
	}

	public void setPessimistGoals(Map<Integer, Double> pessimistGoals) {
		this.pessimist_goals = new LinkedHashMap<>(pessimistGoals);
	}

	public void setRealist_goals(Map<Integer, Double> realist_goals) {
		this.realist_goals = realist_goals;
	}

	public Map<Integer, Double> getPessimist_goals() {
		return pessimist_goals;
	}

	public void setPessimist_goals(Map<Integer, Double> pessimist_goals) {
		this.pessimist_goals = pessimist_goals;
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
