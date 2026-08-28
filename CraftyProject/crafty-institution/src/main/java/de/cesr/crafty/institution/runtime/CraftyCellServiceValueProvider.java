package de.cesr.crafty.institution.runtime;

import java.util.List;

import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;

public final class CraftyCellServiceValueProvider implements CellServiceValueProvider {
	@Override
	public double value(Cell cell, String serviceName) {
		List<String> services = ServiceSet.getServicesList();
		int index = services.indexOf(serviceName);
		if (index < 0) {
			throw new IllegalArgumentException("Unknown CRAFTY service '" + serviceName + "'");
		}
		double[] production = cell.getCurrentProd();
		if (production == null || index >= production.length) {
			return 0;
		}
		return production[index];
	}
}
