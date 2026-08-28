package de.cesr.crafty.institution.runtime;

import java.util.Collection;

import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.institution.model.SpatialScope;

@FunctionalInterface
public interface CellScopeResolver {
	Collection<Cell> resolve(SpatialScope scope);
}
