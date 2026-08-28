package de.cesr.crafty.institution.config;

import java.util.Locale;

public final class Identifiers {
	private Identifiers() {
	}

	public static String normalize(String value) {
		if (value == null) {
			throw new IllegalArgumentException("Identifier cannot be null");
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", "_")
				.replaceAll("^_+|_+$", "");
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Identifier cannot be empty");
		}
		return normalized;
	}
}
