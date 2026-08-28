package de.cesr.crafty.core.utils.general;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

import de.cesr.crafty.core.crafty.Cell;

/** Stable-order aggregation helpers for reproducible floating-point results. */
public final class DeterministicAggregation {

	private static final Comparator<Cell> CELL_ORDER = Comparator.comparingInt(Cell::getX)
			.thenComparingInt(Cell::getY);

	private DeterministicAggregation() {
	}

	public static List<Cell> cellsInStableOrder(Collection<Cell> cells) {
		List<Cell> ordered = new ArrayList<>(cells);
		ordered.sort(CELL_ORDER);
		return ordered;
	}

	public static double sumCells(Collection<Cell> cells, ToDoubleFunction<Cell> valueFunction) {
		double sum = 0.0;
		for (Cell cell : cellsInStableOrder(cells)) {
			sum += valueFunction.applyAsDouble(cell);
		}
		return sum;
	}
}
