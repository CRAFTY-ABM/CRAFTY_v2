package de.cesr.crafty.institution.runtime;

@FunctionalInterface
public interface ExternalValueProvider {
	double value(String name);
}
