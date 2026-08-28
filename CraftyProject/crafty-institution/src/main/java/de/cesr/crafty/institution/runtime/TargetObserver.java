package de.cesr.crafty.institution.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.institution.model.CraftyElementRef;
import de.cesr.crafty.institution.model.NormalizationType;
import de.cesr.crafty.institution.model.SpatialScope;
import de.cesr.crafty.institution.model.TargetDefinition;

public final class TargetObserver {
	private final CellScopeResolver scopeResolver;
	private final CellServiceValueProvider serviceValues;
	private final ExternalValueProvider externalValues;
	private final ObservationBaselines baselines;

	public TargetObserver(CellScopeResolver scopeResolver, CellServiceValueProvider serviceValues,
			ExternalValueProvider externalValues, ObservationBaselines baselines) {
		this.scopeResolver = Objects.requireNonNull(scopeResolver, "scopeResolver");
		this.serviceValues = Objects.requireNonNull(serviceValues, "serviceValues");
		this.externalValues = Objects.requireNonNull(externalValues, "externalValues");
		this.baselines = Objects.requireNonNull(baselines, "baselines");
	}

	public TargetObservation observe(TargetDefinition target, SpatialScope scope) {
		List<Cell> cells = new ArrayList<>(scopeResolver.resolve(scope));
		Map<String, Double> components = new LinkedHashMap<>();
		double weighted = 0;
		double totalWeight = 0;
		for (CraftyElementRef element : target.observations()) {
			double component = componentValue(element, cells);
			components.put(element.name(), component);
			weighted += component * element.weight();
			totalWeight += Math.abs(element.weight());
		}
		if (totalWeight == 0) {
			throw new IllegalArgumentException("Target '" + target.id() + "' has zero total observation weight");
		}
		double raw = weighted / totalWeight;
		double baseline = baselines.getOrRecord(target.id(), scope, raw);
		double normalized = normalize(raw, baseline, target.normalization());
		return new TargetObservation(target.id(), raw, normalized, baseline, components);
	}

	private double componentValue(CraftyElementRef element, List<Cell> cells) {
		return switch (element.type()) {
		case AFT -> cells.isEmpty() ? 0
				: (double) cells.stream().filter(cell -> cell.getOwner() != null)
						.filter(cell -> element.name().equals(cell.getOwnerName())).count() / cells.size();
		case SERVICE -> cells.stream().mapToDouble(cell -> serviceValues.value(cell, element.name())).sum();
		case EXTERNAL -> externalValues.value(element.name());
		case CAPITAL -> capitalAverage(element.name(), cells);
		};
	}

	private static double capitalAverage(String reference, List<Cell> cells) {
		String[] parts = reference.split(":", -1);
		String aft = parts.length == 2 ? parts[0] : "";
		String capital = parts.length == 2 ? parts[1] : reference;
		List<Cell> selected = aft.isEmpty() ? cells
				: cells.stream().filter(cell -> cell.getOwner() != null && aft.equals(cell.getOwnerName())).toList();
		return selected.isEmpty() ? 0
				: selected.stream().mapToDouble(cell -> cell.getCapitals().getOrDefault(capital, 0.0)).average().orElse(0);
	}

	private static double normalize(double raw, double baseline, NormalizationType type) {
		return switch (type) {
		case RAW -> raw;
		case BASELINE_RATIO -> baseline == 0 ? Double.NaN : raw / baseline;
		};
	}
}
