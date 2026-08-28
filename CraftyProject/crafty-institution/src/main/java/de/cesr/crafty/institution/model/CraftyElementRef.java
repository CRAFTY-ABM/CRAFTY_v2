package de.cesr.crafty.institution.model;

import java.util.Objects;

public record CraftyElementRef(EffectType type, String name, double weight) {
	public CraftyElementRef {
		Objects.requireNonNull(type, "type");
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("CRAFTY element name cannot be blank");
		}
		name = name.trim();
		if (!Double.isFinite(weight)) {
			throw new IllegalArgumentException("CRAFTY element weight must be finite");
		}
	}
}
