package de.cesr.crafty.institution.config;

import java.util.List;

public class ConfigurationException extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;
	private final List<String> errors;

	public ConfigurationException(List<String> errors) {
		super("Invalid institution configuration:\n - " + String.join("\n - ", errors));
		this.errors = List.copyOf(errors);
	}

	public List<String> errors() {
		return errors;
	}
}
