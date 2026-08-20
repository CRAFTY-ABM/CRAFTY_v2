package de.cesr.crafty.institution.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.institution.model.SpatialScope;

public final class ObservationBaselines {
	private final Map<Key, Double> values = new ConcurrentHashMap<>();

	public double getOrRecord(String targetId, SpatialScope scope, double rawValue) {
		return values.computeIfAbsent(new Key(targetId, scope.type(), scope.name()), ignored -> rawValue);
	}

	public void clear() {
		values.clear();
	}

	private record Key(String targetId, SpatialScope.Type scopeType, String scopeName) {
	}
}
