package de.cesr.crafty.institution.decision.fuzzy;

import de.cesr.crafty.institution.config.Identifiers;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Configuration used exclusively by the fuzzy decision engine. */
public record FuzzyEngineConfig(Path fclFile, boolean startAtFirstStep, boolean optimizeBudget,
		Map<String, FuzzyTargetSettings> targets, Map<String, FuzzyPolicySettings> policies) {
	public FuzzyEngineConfig {
		if (fclFile == null) {
			throw new IllegalArgumentException("FCL file cannot be null");
		}
		fclFile = fclFile.normalize();
		Map<String, FuzzyTargetSettings> targetCopy = new LinkedHashMap<>();
		targets.forEach((key, value) -> targetCopy.put(Identifiers.normalize(key), value));
		targets = Map.copyOf(targetCopy);
		Map<String, FuzzyPolicySettings> copy = new LinkedHashMap<>();
		policies.forEach((key, value) -> copy.put(Identifiers.normalize(key), value));
		policies = Map.copyOf(copy);
	}
}
