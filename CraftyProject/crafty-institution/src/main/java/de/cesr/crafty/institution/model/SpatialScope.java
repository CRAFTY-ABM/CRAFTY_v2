package de.cesr.crafty.institution.model;

import java.util.Objects;
import java.util.List;

public record SpatialScope(Type type, String name) {
	public enum Type {
		ALL_CELLS,
		REGIONS,
		PARADIGM
	}

	public SpatialScope {
		Objects.requireNonNull(type, "type");
		name = name == null ? "" : name.trim();
		if (type != Type.ALL_CELLS && name.isEmpty()) {
			throw new IllegalArgumentException("Named spatial scope requires a name");
		}
	}

	public List<String> regions() {
		if (type != Type.REGIONS) {
			return List.of();
		}
		return java.util.Arrays.stream(name.split(","))
				.map(String::trim).filter(region -> !region.isEmpty()).distinct().toList();
	}
}
