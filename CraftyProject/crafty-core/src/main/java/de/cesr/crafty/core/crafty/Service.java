package de.cesr.crafty.core.crafty;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an ecosystem service tracked by CRAFTY, including its time-varying inputs and calibration state.
 *
 * A {@code Service} holds the region-specific exogenous drivers used in the utility and allocation process:
 * - Demand time series (year -> demand).
 * - Utility weights time series (year -> weight) used to scale marginal utility signals.
 *
 * The service also stores a {@link #calibration_Factor} used to scale initial supply–demand equilibrium
 * (computed during the baseline calibration step). This factor allows the model to reconcile baseline
 * supply with initial demand while keeping subsequent dynamics comparable across services and regions.
 *
 * Notes:
 * - All time series are stored in {@link ConcurrentHashMap}s to support parallel reads during regional
 *   calculations and output generation.
 * - The optional {@link #category} field can be used to group services for reporting or/and plotting, but
 *   does not affect core calculations yet.
 */
/**
 * @author Mohamed Byari
 *
 */

public class Service {

	private String name;
	private String category;
	
	private ConcurrentHashMap<Integer, Double> demands;
	private ConcurrentHashMap<Integer, Double> weights = new ConcurrentHashMap<>();
	
	
	private double calibration_Factor = 1;

	public Service(String name) {
		this.name = name;
		demands = new ConcurrentHashMap<>();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public ConcurrentHashMap<Integer, Double> getDemands() {
		return demands;
	}

	public void setDemands(ConcurrentHashMap<Integer, Double> demands) {
		this.demands = demands;
	}

	public ConcurrentHashMap<Integer, Double> getWeights() {
		return weights;
	}

	public void setWeights(ConcurrentHashMap<Integer, Double> weights) {
		this.weights = weights;
	}
	
	

	public double getCalibration_Factor() {
		return calibration_Factor;
	}

	public void setCalibration_Factor(double calibration_Factor) {
		this.calibration_Factor = calibration_Factor;
	}
	
	


	@Override
	public String toString() {
		return "Service [name=" + name + ", demands=" + demands + ", weights=" + weights
				+ ", calibration_Factor=" + calibration_Factor + "]";
	}




}
