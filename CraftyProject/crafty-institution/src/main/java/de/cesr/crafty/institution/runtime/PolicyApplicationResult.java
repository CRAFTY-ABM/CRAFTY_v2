package de.cesr.crafty.institution.runtime;

import java.util.Map;

import de.cesr.crafty.institution.model.EffectType;

public record PolicyApplicationResult(long selectedCellCount, long applicationCount,
		Map<EffectType, Long> applicationsByType) {
	public PolicyApplicationResult {
		applicationsByType = Map.copyOf(applicationsByType);
	}
}
