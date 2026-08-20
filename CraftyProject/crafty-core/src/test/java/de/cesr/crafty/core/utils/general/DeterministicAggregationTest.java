package de.cesr.crafty.core.utils.general;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.cesr.crafty.core.crafty.Cell;

class DeterministicAggregationTest {

	@Test
	void cellsAreOrderedByCoordinates() {
		Cell first = new Cell(-1, 5);
		Cell second = new Cell(2, 3);
		Cell third = new Cell(2, 4);

		assertEquals(List.of(first, second, third),
				DeterministicAggregation.cellsInStableOrder(List.of(third, first, second)));
	}

	@Test
	void floatingPointSumDoesNotDependOnInputOrder() {
		Cell largePositive = new Cell(0, 0);
		Cell largeNegative = new Cell(1, 0);
		Cell one = new Cell(2, 0);
		Map<Cell, Double> values = Map.of(largePositive, 1.0e16, largeNegative, -1.0e16, one, 1.0);

		double forward = DeterministicAggregation.sumCells(List.of(largePositive, largeNegative, one), values::get);
		double shuffled = DeterministicAggregation.sumCells(List.of(largePositive, one, largeNegative), values::get);

		assertEquals(1.0, forward);
		assertEquals(forward, shuffled);
	}
}
