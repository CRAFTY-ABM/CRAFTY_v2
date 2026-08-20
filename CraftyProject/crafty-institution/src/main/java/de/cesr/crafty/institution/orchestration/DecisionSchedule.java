package de.cesr.crafty.institution.orchestration;

import de.cesr.crafty.institution.model.ActivationSchedule;

import java.util.Objects;

public record DecisionSchedule(ActivationSchedule activation, int firstDecisionOffsetYears) {
	public DecisionSchedule {
		Objects.requireNonNull(activation, "activation");
		if (firstDecisionOffsetYears < 0) {
			throw new IllegalArgumentException("First decision offset cannot be negative");
		}
	}

	public boolean isDue(int year) {
		long firstDecisionYear = (long) activation.startYear() + firstDecisionOffsetYears;
		return activation.includes(year) && year >= firstDecisionYear
				&& (year - firstDecisionYear) % activation.intervalYears() == 0;
	}
}
