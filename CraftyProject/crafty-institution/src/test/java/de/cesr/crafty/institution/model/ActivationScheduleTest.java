package de.cesr.crafty.institution.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ActivationScheduleTest {
	@Test
	void includesBothBoundaryYears() {
		ActivationSchedule schedule = new ActivationSchedule(2020, 2100, 4);

		assertFalse(schedule.includes(2019));
		assertTrue(schedule.includes(2020));
		assertTrue(schedule.includes(2050));
		assertTrue(schedule.includes(2100));
		assertFalse(schedule.includes(2101));
	}

	@Test
	void supportsMaximumEndYearWithoutArithmeticOverflow() {
		ActivationSchedule schedule = new ActivationSchedule(0, Integer.MAX_VALUE, 1);

		assertTrue(schedule.includes(2020));
		assertTrue(schedule.includes(Integer.MAX_VALUE));
	}
}
