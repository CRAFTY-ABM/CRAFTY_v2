package de.cesr.crafty.core.crafty;

/**
 * Concrete Agent Functional Type (AFT) implementation used as the land manager/owner for cells.
 *
 * This class mainly provides:
 * - A label-based constructor for creating standard AFT instances (including a built-in "Abandoned" type).
 *
 * Special case:
 * If the label is "Abandoned", the instance is configured as a non-interacting abandoned manager with
 * a default grey color and an "Uncategorized" category.
 */

/**
 * @author Mohamed Byari
 *
 */

public class Aft extends AbstractAft {

	public Aft(String label) {
		setLabel(label);
		if (label.equals("Abandoned")) {
			setLabel("Abandoned");
			setType(ManagerTypes.Abandoned);
			setColor("#cccccc");
			setCategory(new AftCategory("Uncategorized"));
		}
	}

	@Override
	public String toString() {
		return "Aft [label=" + getLabel() + ", type=" + type + ", category=" + category + "]";
	}

}
