package de.cesr.crafty.institution.runtime;

import de.cesr.crafty.core.crafty.Cell;

@FunctionalInterface
public interface CellServiceValueProvider {
	double value(Cell cell, String serviceName);
}
