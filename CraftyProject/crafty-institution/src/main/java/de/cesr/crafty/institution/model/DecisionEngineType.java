package de.cesr.crafty.institution.model;

public enum DecisionEngineType {
	FUZZY,
	LLM,
	/** Policy levels are supplied by an external UI or operator. */
	MANUAL
}
