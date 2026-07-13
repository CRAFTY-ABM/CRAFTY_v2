package de.cesr.crafty.core.crafty;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base representation of a spatial cell in the CRAFTY grid.
 *
 * A cell is the core spatial unit of the model. It stores:
 * - Spatial position ({@link #x}, {@link #y}) and an string identifier ({@link #id}).
 * - The current owning manager ({@link Aft}) and region assignment.
 * - Capital used by production/utility calculations.
 * - Current service production vector ({@link #currentProd}) and the last computed utility value.
 * - mask type used for masks and protected areas
 * - Optional visual attributes used for outputs and plotting.
 *
 * This class is intentionally lightweight and focused on state storage; higher-level behaviour
 * (e.g., utility calculation, transitions) is implemented in child class {@link Cell}
 *
 * Concurrency note:
 * Capitals are stored in a {@link ConcurrentHashMap} because capital values may be read frequently during
 * a step and, in some configurations, accessed from parallel computations. Callers should still ensure
 * updates are performed in a controlled way (e.g., by step/updater logic) to avoid inconsistent states.
 */

/**
 * @author Mohamed Byari
 *
 */
public abstract class AbstractCell {
//	static int size = 1;
	private String id;
	int x, y;
	private ConcurrentHashMap<String, Double> capitals = new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, Double> ServicesTax = new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, Double> landTax = new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, Double> capitalsAdjusment = new ConcurrentHashMap<>();

	// Production cost storage (AFT label -> $/ha)
	private ConcurrentHashMap<String, Double> nfertCosts = new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, Double> irrigationCosts = new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, Double> intensityCosts = new ConcurrentHashMap<>();

	private double[] currentProd;
	private double currentUtility;
	String CurrentRegion;
	private Aft owner;
	protected String color = "#848484";
	private String maskType;
	private int OwnerLifeCounter = 1;

	private long sID;
	private double score;

	public long getCellID() {
		return sID;
	}

	public void setCellID(long sID) {
		this.sID = sID;
	}

	public double getScore() {
		return score;
	}

	public void setScore(double score) {
		this.score = score;
	}

	public int getOwnerLifeCounter() {
		return OwnerLifeCounter;
	}

	public void setOwnerLifeCounter(int ownerLifeCounter) {
		OwnerLifeCounter = ownerLifeCounter;
	}

	public void OwnerLifeCounterIncrement() {
		OwnerLifeCounter++;
	}

	public double getCurrentUtility() {
		return currentUtility;
	}

	public void setCurrentUtility(double currentUtility) {
		this.currentUtility = currentUtility;
	}

	public String getCurrentRegion() {
		return CurrentRegion;
	}

	public void setCurrentRegion(String currentRegion) {
		CurrentRegion = currentRegion;
	}

	public String getColor() {
		return color;
	}

	public void setColor(String color) {
		this.color = color;
	}

	public String getMaskType() {
		return maskType;
	}

	public void setMaskType(String maskType) {
		this.maskType = maskType;
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = y;
	}

	public Aft getOwner() {
		return owner;
	}

	public String getOwnerName() {
		if (owner == null)
			return "null";
		return owner.getLabel();
	}

	public void setOwner(Aft owner) {
		this.owner = owner;
	}

	public ConcurrentHashMap<String, Double> getCapitals() {
		return capitals ;
	}

	public double getOneCapitals(String capitalName) {
		return getCapitals().get(capitalName);
	}

	public void setOneCapitals(String capitalName, double value) {
		getCapitals().put(capitalName, value);
	}

//	public static int getSize() {
//		return size;
//	}

//	public static void setSize(int size) {
//		AbstractCell.size = size;
//	}

	public String getID() {
		return getId();
	}

	public void setID(String id) {
		this.setId(id);
	}

	@Override
	public String toString() {
		return "\n Cell [index=" + getId() + ", x=" + x + ", y=" + y + ", CurrentRegion=" + CurrentRegion + "\n, Mask="
				+ getMaskType() + ", getOwner()=" + (getOwner() != null ? getOwner().getLabel() : " null")
				+ " category: " + (getOwner() != null ? getOwner().category : "null") + ", Color: " + color
				+ ", getCapitals()=" + getCapitals() + ", getCurrentProductivity()=" + Arrays.toString(getCurrentProd())
				+ "] \n "
		/* + CellBehaviourLoader.cellsBehevoir.get(this) */;
	}

	public double[] getCurrentProd() {
		return currentProd;
	}

	public void setCurrentProd(double[] currentProd) {
		this.currentProd = currentProd;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setCapitals(ConcurrentHashMap<String, Double> capitals) {
		this.capitals = capitals;
	}

	public ConcurrentHashMap<String, Double> getServicesTax() {
		return ServicesTax;
	}

	public ConcurrentHashMap<String, Double> getLandTax() {
		return landTax;
	}

	public ConcurrentHashMap<String, Double> getCapitalsAdjusment() {
		return capitalsAdjusment;
	}

	public ConcurrentHashMap<String, Double> getNfertCosts() {
		return nfertCosts;
	}

	public ConcurrentHashMap<String, Double> getIrrigationCosts() {
		return irrigationCosts;
	}

	public ConcurrentHashMap<String, Double> getIntensityCosts() {
		return intensityCosts;
	}

}
