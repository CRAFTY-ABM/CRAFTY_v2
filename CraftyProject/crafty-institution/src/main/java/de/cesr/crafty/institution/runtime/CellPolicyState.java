package de.cesr.crafty.institution.runtime;

import java.util.Collection;

import de.cesr.crafty.core.crafty.Cell;

/** Lifecycle operations for the current period's cell-level policy effects. */
public final class CellPolicyState {
	private CellPolicyState() {
	}

	public static void clear(Collection<Cell> cells) {
		cells.forEach(cell -> {
			cell.getServicesTax().clear();
			cell.getLandTax().clear();
			cell.getCapitalsAdjusment().clear();
		});
	}
}
