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

	
	
}
