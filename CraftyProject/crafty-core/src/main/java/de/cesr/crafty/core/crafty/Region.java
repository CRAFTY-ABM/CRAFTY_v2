package de.cesr.crafty.core.crafty;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Represents a simulation region (a subset of the full grid) and holds region-specific state.
 *
 * A {@code Region} groups cells and services to support regionalised model runs. It stores:
 * - A name/identifier used for lookup and output folder naming.
 * - The set of cells belonging to the region ({@link #cells}).
 * - A thread-safe set of unmanaged (unowned) cells ({@link #unmanageCellsR}), used by abandonment and
 *   reallocation mechanisms.
 * - A region-specific service registry ({@link #servicesHash}) containing demand, calibration, and other
 *   service-level parameters for that region.
 *
 * Convenience method:
 * - {@link #getServiceCalibration_Factor()} returns a concurrent map of service name -> calibration factor,
 *   extracted from the underlying service objects,  to ensure an initial supply-demand equilibrium.
 */

/**
 * @author Mohamed Byari
 *
 */

public class Region {
	private String name;
	private ConcurrentHashMap<String, Cell> cells = new ConcurrentHashMap<>();
	private Set<Cell> unmanageCellsR = ConcurrentHashMap.newKeySet();
	private ConcurrentHashMap<String, Service> servicesHash = new ConcurrentHashMap<>();;

	public Region(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public ConcurrentHashMap<String, Cell> getCells() {
		return cells;
	}

	public void setCells(ConcurrentHashMap<String, Cell> cells) {
		this.cells = cells;
	}

	public Set<Cell> getUnmanageCellsR() {
		return unmanageCellsR;
	}

	public ConcurrentHashMap<String, Service> getServicesHash() {
		return servicesHash;
	}
	

	public ConcurrentMap<String, Double> getServiceCalibration_Factor() {
		return getServicesHash().entrySet().stream().collect(
				Collectors.toConcurrentMap(Map.Entry::getKey, entry -> entry.getValue().getCalibration_Factor()));
	}
}
