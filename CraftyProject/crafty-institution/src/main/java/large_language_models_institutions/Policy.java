package large_language_models_institutions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.updaters.CapitalUpdater;

public class Policy {
	private static final CustomLogger LOGGER = new CustomLogger(Policy.class);
	private String name;
	private double value;
	private double sensitivity = 1;
	private Map<String, Map<String, Double>> craftyElem = new ConcurrentHashMap<>();// <type,<craftyElem,weight>>

	private Paradigm paradigm;
	private double accumulatedValue;
	private List<Double> desicions_history = new ArrayList<>();

	private List<Double> recorder = new ArrayList<>();

	public Policy(String name, Paradigm paradigm) {
		this.name = name;
		this.paradigm = paradigm;
	}

	public void step() {
		applyPolicy();
	}

	private void applyPolicy() {
		craftyElem.forEach((type, hash) -> {
			hash.forEach((element, weight) -> {
				LOGGER.trace(type + " .. " + element + " = " + weight);
				switch (type.toLowerCase()) {
				case "aft":
					applyAftAdjustment(element, weight);
					break;
				case "service":
					applyServiceAdjustment(element, weight);
					break;
				case "capital":
					applyCapitalAdjustment(element, weight);
					break;
				default:
					break;
				}
			});
		});
	}

	private void applyAftAdjustment(String element, double weight) {
		paradigm.getSubRegions().forEach((subRegion_name, cells) -> {
			cells.forEach(c -> {
				if (c.getOwner() != null && c.getOwnerName().equals(element)) {
					c.getLandTax().merge(element, sensitivity*weight * findpolicyValue(c), Double::sum);
				}
			});
		});
	}

	private void applyServiceAdjustment(String element, double weight) {

		paradigm.getSubRegions().forEach((subRegion_name, cells) -> {
			cells.forEach(c -> {
				c.getServicesTax().put(element, sensitivity*weight * findpolicyValue(c));
			});
		});
	}

	private void applyCapitalAdjustment(String element, double weight) {
		String[] parts = element.split(":");
		if (parts.length != 2) {
			return;
		}

		String aftName = parts[0];
		String capital = parts[1];

		if (AFTsLoader.getActivateAFTsHash().containsKey(aftName)
				&& CapitalUpdater.getCapitalsList().contains(capital)) {
			paradigm.getSubRegions().forEach((subRegion_name, cells) -> {
				cells.forEach(c -> {
					if (c.getOwnerName().equals(aftName)) {
						c.getCapitalsAdjusment().merge(capital, sensitivity*weight * findpolicyValue(c) / 100, Double::sum);
					}
				});
			});
		}
	}

	private double findpolicyValue(Cell c) {
		double v = 0;
		if (paradigm.getDelay().getOrDefault(c.getCurrentRegion(), 0) > recorder.size() - 1) {
			v = 0;
		} else {
			v = recorder.get(recorder.size() - 1 - paradigm.getDelay().getOrDefault(c.getCurrentRegion(), 0));
		}
		return v;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public double getValue() {
		return value;
	}

	public void setValue(double value) {
		this.value = value;
	}

	public double getAccumulatedValue() {
		return accumulatedValue;
	}

	public void setAccumulatedValue(double accumulatedValue) {
		this.accumulatedValue = this.accumulatedValue + accumulatedValue;
	}

	public Map<String, Map<String, Double>> getCraftyElem() {
		return craftyElem;
	}

	public void setCraftyElem(Map<String, Map<String, Double>> craftyElem) {
		this.craftyElem = craftyElem;
	}

	public List<Double> getDesicions_history() {
		return desicions_history;
	}

	public List<Double> getRecorder() {
		return recorder;
	}

}
