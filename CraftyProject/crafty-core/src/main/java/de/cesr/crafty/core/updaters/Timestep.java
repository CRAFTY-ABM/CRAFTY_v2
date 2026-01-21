package de.cesr.crafty.core.updaters;
/**
 * Global simulation clock for CRAFTY.
 *
 * This updater maintains the model’s discrete time state (start year, end year, current year) and a simple
 * tick counter. It is typically scheduled once per model iteration and advanced at the end of each yearly step.
 *
 * Responsibilities:
 * - Stores the configured temporal bounds: {@code startYear} and {@code endtYear} (inclusive).
 * - Tracks the current simulation year ({@code currentYear}) and the number of elapsed iterations ({@code tick}).
 * - Computes {@code timeIntervalSize} on scheduling as {@code (endtYear - startYear + 1)}.
 *
 * Update logic:
 * On each {@link #step()} call, {@code currentYear} and {@code tick} are incremented by one, moving the model
 * to the next simulated year.
 */

public class Timestep extends AbstractUpdater {
	private static int startYear;
	private static int endtYear;
	private static int currentYear;
	private static int tick = 0;
	private static int size;

	public static int getStartYear() {
		return startYear;
	}

	public static void setStartYear(int startYear) {
		Timestep.startYear = startYear;
	}

	public static int getEndtYear() {
		return endtYear;
	}

	public static void setEndtYear(int endtYear) {
		Timestep.endtYear = endtYear;
	}

	public static int getCurrentYear() {
		return currentYear;
	}

	public static void setCurrentYear(int currentYear) {
		Timestep.currentYear = currentYear;
	}

	public static int getTick() {
		return tick;
	}

	public static void setTick(int tick) {
		Timestep.tick = tick;
	}



	public static int getSize() {
		return size;
	}

	public static void setSize(int size) {
		Timestep.size = size;
	}

	@Override
	public void toSchedule() {
		modelRunner.scheduleRepeating(this);
	}

	@Override
	public void step() {
		currentYear++;
		tick++;
	}

	
	public static String tostring() {
		return "Timestep [startYear=" + startYear + ", endtYear()=" + endtYear+ ", currentYear=" + currentYear
				+ ", tick=" + tick + "size= "+size+ "]";
	}

	
	
	
}
