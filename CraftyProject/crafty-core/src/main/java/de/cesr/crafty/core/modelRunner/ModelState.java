package de.cesr.crafty.core.modelRunner;

/**
 * Minimal lifecycle contract for components executed by the model scheduler.
 *
 * Implementations represent model states / modules that participate in the simulation loop.
 * The runner calls these methods in a standard order:
 * - {@link #setup(AbstractModelRunner)} provides a back-pointer to the active runner so the component
 *   can access shared state and scheduling utilities.
 * - {@link #toSchedule()} registers the component with the runner’s scheduler (typically as a repeating task).
 * - {@link #step()} is invoked once per tick (usually per simulated year) to execute the component logic.
 *
 * This interface keeps scheduler-aware modules decoupled from concrete runner implementations while
 * enforcing a consistent initialization and execution pattern.
 */

/**
 * @author Mohamed Byari
 *
 */

public interface ModelState {
	/** give the component a back-pointer to the runner */
	void setup(AbstractModelRunner modelRunner);

	/** register myself with the scheduler */
	void toSchedule();

	/** called every tick */
	void step();
}
