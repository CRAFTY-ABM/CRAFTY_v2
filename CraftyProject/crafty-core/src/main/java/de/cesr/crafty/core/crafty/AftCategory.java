package de.cesr.crafty.core.crafty;

/**
 * Lightweight descriptor for grouping AFTs into categories and intensity levels.
 *
 * Categories are used by optional behavioural mechanisms (e.g., social influence and give-in rules)
 * to compare managers that share a common category but differ in management intensity in a competition process.
 *
 * Fields:
 * - {@link #name}: category name (e.g., "Cropd", "pastur", "Forest", ...).
 * - {@link #intensity}: a human-readable intensity label (default "-").
 * - {@link #intensityLevel}: numeric ordering of intensity, used for computing intensity gaps and
 *   direction of change (higher/lower intensity).
 */
/**
 * @author Mohamed Byari
 *
 */

public class AftCategory {

	private String name;
	private String intensity = "-";
	private int intensityLevel = 0;

	public AftCategory(String name) {
		this.name = name;
	}

	public AftCategory(String name, String intensity, int intensityLevel) {
		this.name = name;
		this.intensity = intensity;
		this.intensityLevel = intensityLevel;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getIntensity() {
		return intensity;
	}

	public void setIntensity(String intensity) {
		this.intensity = intensity;
	}

	public int getIntensityLevel() {
		return intensityLevel;
	}

	public void setIntensityLevel(int intensityLevel) {
		this.intensityLevel = intensityLevel;
	}

	@Override
	public String toString() {
		return "AftCategory [name=" + name + ", intensity=" + intensity + ", intensityLevel=" + intensityLevel + "]";
	}

}
