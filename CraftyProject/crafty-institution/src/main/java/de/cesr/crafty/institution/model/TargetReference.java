package de.cesr.crafty.institution.model;

import de.cesr.crafty.institution.config.Identifiers;

public record TargetReference(String targetId) {
	public TargetReference {
		targetId = Identifiers.normalize(targetId);
	}
}
