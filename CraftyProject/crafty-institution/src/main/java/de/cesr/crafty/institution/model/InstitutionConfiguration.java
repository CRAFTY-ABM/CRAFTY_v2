package de.cesr.crafty.institution.model;

import de.cesr.crafty.institution.config.Identifiers;

import java.util.LinkedHashMap;
import java.util.Map;

public record InstitutionConfiguration(int schemaVersion, Map<String, TargetDefinition> targets,
		Map<String, InstitutionDefinition> institutions) {
	public InstitutionConfiguration {
		if (schemaVersion != 1) {
			throw new IllegalArgumentException("Unsupported institution schema version: " + schemaVersion);
		}
		targets = immutableNormalizedCopy(targets);
		institutions = immutableNormalizedCopy(institutions);
	}

	private static <T> Map<String, T> immutableNormalizedCopy(Map<String, T> source) {
		Map<String, T> copy = new LinkedHashMap<>();
		source.forEach((key, value) -> copy.put(Identifiers.normalize(key), value));
		return Map.copyOf(copy);
	}
}
