package de.cesr.crafty.core.crafty;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base definition for an Agent Functional Type (AFT) used in CRAFTY.
 *
 * This abstract class stores the core parameters that describe how a manager behaves and competes for
 * land, independent of any specific decision model implementation. Concrete AFT classes typically extend
 * this type (directly or indirectly) and use these fields during utility calculation and land-use change.
 *
 * Main parameter groups:
 * - Identity and categorisation: label, completeName, {@link ManagerTypes}, {@link AftCategory}, and display color.
 * - Production behaviour:
 *   - {@link #productivityLevel}: per-service productivity modifiers.
 *   - {@link #sensitivity}: service -> (capital -> exponent) sensitivity matrix used in production/utility functions.
 * - Behavioural thresholds and stochasticity: give-in / give-up means and standard deviations, noise bounds, and
 *   give-up probability.
 * - Policy instruments: {@link #land_taxes_subsidies} stores a time series (year -> tax/subsidy) and
 *   {@link #cachedLandTax} provides a cached value for fast repeated access during a step.
 *
 * Convenience methods:
 * - {@link #isActive()}, {@link #isInteract()}, {@link #isAbandoned()} provide quick checks based on {@link #type}.
 *
 * Concurrency note:
 * Several collections are {@link ConcurrentHashMap}s because they may be accessed frequently during the run and,
 * in some configurations, from parallel code paths. Callers should still treat configuration maps as read-mostly
 * after initialization.
 */
/**
 * @author Mohamed Byari
 *
 */
public abstract class AbstractAft {
	private String label;
	private long id;
	private String completeName;
	ManagerTypes type;
	ConcurrentHashMap<String, Double> productivityLevel = new ConcurrentHashMap<>();
	double giveInMean = 0, giveInSD = 0, giveUpMean = 0, giveUpSD = 0, serviceLevelNoiseMin = 0,
			serviceLevelNoiseMax = 0, giveUpProbabilty = 0;
	AftCategory category;
	String color;
	private ConcurrentHashMap<Integer, Double> land_taxes_subsidies = new ConcurrentHashMap<>();// <year,TS>
	private ConcurrentHashMap<String, Double> capital_adjustments = new ConcurrentHashMap<>(); // <capital_Name,

	private double cachedLandTax;

	protected Map<String, Map<String, Double>> sensitivity = new ConcurrentHashMap<>(); // <service,capital,exponent>
	private int min_life_cycle = 0, max_life_cycle = Integer.MAX_VALUE;

	// Production cost fields
	private double nfertRate = 0.0;
	private boolean irrigated = false;
	private double nfertCostPerHa = 0.0;
	private double intensityCostPerHa = 0.0;

	// Twinned AFT fields
	private String twinLabel = null;
	private double twinCost = 0.0;

	// Subsidy eligibility (0.0 = none, 1.0 = full, fractional = partial)
	private double receivesSubsidy = 0.0;

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public int getMin_life_cycle() {
		return min_life_cycle;
	}

	public void setMin_life_cycle(int min_life_cycle) {
		this.min_life_cycle = min_life_cycle;
	}

	public int getMax_life_cycle() {
		return max_life_cycle;
	}

	public void setMax_life_cycle(int max_life_cycle) {
		this.max_life_cycle = max_life_cycle;
	}

	public Map<String, Map<String, Double>> getSensByService() {
		return sensitivity;
	}

//	public Map<String, Double> getSensitivity(String service) { 
//		return sensitivity.get(service);
//	}

	public void setSensByService(Map<String, Map<String, Double>> sensByService) {
		this.sensitivity = sensByService;
	}

	public double getCachedLandTax() {
		return cachedLandTax;
	}

	public void setCachedLandTax(double v) {
		cachedLandTax = v;
	}

	public AftCategory getCategory() {
		return category;
	}

	public void setCategory(AftCategory category) {
		this.category = category;
	}

	public ManagerTypes getType() {
		return type;
	}

	public void setType(ManagerTypes type) {
		this.type = type;
	}

	public boolean isActive() {
		return type == ManagerTypes.AFT || type == ManagerTypes.Abandoned;
	}

	public boolean isInteract() {
		return type == ManagerTypes.AFT;
	}

	public boolean isAbandoned() {
		return type == ManagerTypes.Abandoned;
	}

	public double getServiceLevelNoiseMin() {
		return serviceLevelNoiseMin;
	}

	public void setServiceLevelNoiseMin(double serviceLevelNoiseMin) {
		this.serviceLevelNoiseMin = serviceLevelNoiseMin;
	}

	public double getServiceLevelNoiseMax() {
		return serviceLevelNoiseMax;
	}

	public void setServiceLevelNoiseMax(double serviceLevelNoiseMax) {
		this.serviceLevelNoiseMax = serviceLevelNoiseMax;
	}

	public double getGiveInMean() {
		return giveInMean;
	}

	public void setGiveInMean(double giveInMean) {
		this.giveInMean = giveInMean;
	}

	public double getGiveInSD() {
		return giveInSD;
	}

	public void setGiveInSD(double giveInSD) {
		this.giveInSD = giveInSD;
	}

	public double getGiveUpMean() {
		return giveUpMean;
	}

	public void setGiveUpMean(double giveUpMean) {
		this.giveUpMean = giveUpMean;
	}

	public double getGiveUpSD() {
		return giveUpSD;
	}

	public void setGiveUpSD(double giveUpSD) {
		this.giveUpSD = giveUpSD;
	}

	public double getGiveUpProbabilty() {
		return giveUpProbabilty;
	}

	public void setGiveUpProbabilty(double giveUpProbabilty) {
		this.giveUpProbabilty = giveUpProbabilty;
	}

	public String getColor() {
		return color;
	}

	public void setColor(String color) {
		this.color = color;
	}

	public ConcurrentHashMap<String, Double> getProductivityLevel() {
		return productivityLevel;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getCompleteName() {
		return completeName;
	}

	public void setCompleteName(String name) {
		this.completeName = name;
	}

	public ConcurrentHashMap<Integer, Double> getLand_taxes_subsidies() {
		return land_taxes_subsidies;
	}

	public void setLand_taxes_subsidies(ConcurrentHashMap<Integer, Double> land_taxes_subsidies) {
		this.land_taxes_subsidies = land_taxes_subsidies;
	}

	public ConcurrentHashMap<String, Double> getCapital_adjustments() {
		return capital_adjustments;
	}

	public double getNfertRate() {
		return nfertRate;
	}

	public void setNfertRate(double nfertRate) {
		this.nfertRate = nfertRate;
	}

	public boolean isIrrigated() {
		return irrigated;
	}

	public void setIrrigated(boolean irrigated) {
		this.irrigated = irrigated;
	}

	public double getNfertCostPerHa() {
		return nfertCostPerHa;
	}

	public void setNfertCostPerHa(double nfertCostPerHa) {
		this.nfertCostPerHa = nfertCostPerHa;
	}

	public double getIntensityCostPerHa() {
		return intensityCostPerHa;
	}

	public void setIntensityCostPerHa(double intensityCostPerHa) {
		this.intensityCostPerHa = intensityCostPerHa;
	}

	public String getTwinLabel() {
		return twinLabel;
	}

	public void setTwinLabel(String twinLabel) {
		this.twinLabel = twinLabel;
	}

	public double getTwinCost() {
		return twinCost;
	}

	public void setTwinCost(double twinCost) {
		this.twinCost = twinCost;
	}

	public boolean hasTwin() {
		return twinLabel != null && !twinLabel.isBlank();
	}

	public double getReceivesSubsidy() {
		return receivesSubsidy;
	}

	public void setReceivesSubsidy(double receivesSubsidy) {
		this.receivesSubsidy = receivesSubsidy;
	}

}
