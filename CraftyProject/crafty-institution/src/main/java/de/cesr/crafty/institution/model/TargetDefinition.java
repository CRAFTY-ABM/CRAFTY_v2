package de.cesr.crafty.institution.model;

import de.cesr.crafty.institution.config.Identifiers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TargetDefinition(String id, String name, List<CraftyElementRef> observations,
		NormalizationType normalization, Map<String, Map<Integer, Double>> goalTrajectories) {
	public TargetDefinition {
		id = Identifiers.normalize(id);
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Target name cannot be blank");
		}
		name = name.trim();
		observations = List.copyOf(observations);
		if (observations.isEmpty()) {
			throw new IllegalArgumentException("Target must contain at least one observation mapping");
		}
		EffectType observationType = observations.get(0).type();
		if (observations.stream().anyMatch(observation -> observation.type() != observationType)) {
			throw new IllegalArgumentException("All target observation mappings must use the same type");
		}
		if (normalization == null) {
			throw new IllegalArgumentException("Target normalization cannot be null");
		}
		Map<String, Map<Integer, Double>> copy = new LinkedHashMap<>();
		goalTrajectories.forEach((key, value) -> copy.put(Identifiers.normalize(key), Map.copyOf(value)));
		goalTrajectories = Map.copyOf(copy);
	}
}
