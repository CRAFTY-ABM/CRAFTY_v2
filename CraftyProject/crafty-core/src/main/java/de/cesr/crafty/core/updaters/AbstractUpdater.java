package de.cesr.crafty.core.updaters;

import de.cesr.crafty.core.modelRunner.AbstractModelRunner;
import de.cesr.crafty.core.modelRunner.ModelState;

/**
 * Base class for all CRAFTY “updater” components that participate in the model schedule.
 *
 * An updater is a {@link ModelState}: it can be registered with the {@link AbstractModelRunner} scheduler
 * (via {@link #toSchedule()}) and is executed once per tick/year (via {@link #step()}).
 *
 * This abstract class provides a shared back-reference to the active {@link AbstractModelRunner}.
 * The runner calls {@link #setup(AbstractModelRunner)} during initialization so each updater can
 * access scheduling utilities and shared runtime context.
 *
 * Design notes:
 * - The {@link #modelRunner} field is {@code protected} so subclasses can access it, but it is not exposed
 *   publicly to keep the external API minimal.
 * - Subclasses are expected to implement at least {@link #toSchedule()} and {@link #step()}.
 */
/**
 * @author Mohamed Byari
 *
 */
public abstract class AbstractUpdater implements ModelState{
    /** Back-reference to the active model runner (injected during runner setup). */
    protected AbstractModelRunner modelRunner;

    @Override
    public void setup(AbstractModelRunner modelRunner) {
        this.modelRunner = modelRunner;          
    }
}
