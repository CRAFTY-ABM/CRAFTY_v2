package de.cesr.crafty.institution.orchestration;

@FunctionalInterface
public interface InstitutionDecisionEngine {
	InstitutionDecision decide(InstitutionContext context);
}
