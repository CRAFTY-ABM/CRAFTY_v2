package de.cesr.crafty.institution.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.cesr.crafty.core.crafty.Cell;

class CellPolicyStateTest {
	@Test
	void clearsEveryCellLevelPolicyMapAtThePeriodBoundary() {
		Cell cell = new Cell(1, 2);
		cell.getLandTax().put("AF", 1.0);
		cell.getServicesTax().put("Food", 2.0);
		cell.getCapitalsAdjusment().put("human", 3.0);

		CellPolicyState.clear(List.of(cell));

		assertTrue(cell.getLandTax().isEmpty());
		assertTrue(cell.getServicesTax().isEmpty());
		assertTrue(cell.getCapitalsAdjusment().isEmpty());
	}
}
