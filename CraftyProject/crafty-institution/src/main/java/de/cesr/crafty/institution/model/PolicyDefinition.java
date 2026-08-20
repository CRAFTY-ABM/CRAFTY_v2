package de.cesr.crafty.institution.model;

import de.cesr.crafty.institution.config.Identifiers;

import java.util.List;

public record PolicyDefinition(String id, String name, List<CraftyElementRef> effects, PolicyCost cost,
		PolicyConstraints constraints) {
	public PolicyDefinition {
		id = Identifiers.normalize(id);
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Policy name cannot be blank");
		}
		name = name.trim();
		effects = List.copyOf(effects);
		if (effects.isEmpty()) {
			throw new IllegalArgumentException("Policy must contain at least one effect");
		}
		if (effects.stream().anyMatch(effect -> effect.type() == EffectType.EXTERNAL)) {
			throw new IllegalArgumentException(
					"Policy effects must be cell-level; EXTERNAL is only valid for target observations");
		}

	}
}
