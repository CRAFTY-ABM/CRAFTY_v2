package de.cesr.crafty.core.utils.general;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;

/**
 * Helper methods for building spatial subsets of cells and owners (AFTs) around a focal cell.
 *
 * This class provides utilities to retrieve Moore neighbourhoods (8-neighbour grid) and extended
 * Moore neighbourhoods (square window with radius r), and to extract the set of interacting AFTs
 * present in those neighbourhoods.
 *
 * Typical use cases:
 * - Neighbourhood / contagion effects in land-use transitions (e.g., prioritising changes near similar owners).
 * - Detecting local competition or influence zones around a cell.
 *
 * Notes:
 * - Neighbourhood lookup relies on {@link CellsLoader#getCell(int, int)} and therefore depends on the
 *   global cell grid being initialized.
 * - Only owners with {@code isInteract() == true} are returned by the AFT-detection methods.
 * - Sets are created as synchronized sets; this helps because neighbourhoods are computed from parallel
 *   contexts, but callers should still avoid modifying returned collections concurrently.
 */
/**
 * @author Mohamed Byari
 *
 */
public class CellsSubSets {

	static Collection<Aft> detectNeighboringAFTs(Cell c) {
		Set<Aft> neighborhoodAFts = Collections.synchronizedSet(new HashSet<>());
		Set<Cell> neighborhoodCells = getMooreNeighborhood(c);
		neighborhoodCells.forEach(vc -> {
			if (vc.getOwner() != null && vc.getOwner().isInteract())
				neighborhoodAFts.add(vc.getOwner());
		});
		return neighborhoodAFts;
	}

	static Set<Cell> getMooreNeighborhood(Cell c) {
		Set<Cell> neighborhood = Collections.synchronizedSet(new HashSet<>());
		for (int i = (c.getX() - 1); i <= c.getX() + 1; i++) {
			for (int j = (c.getY() - 1); j <= (c.getY()) + 1; j++) {
				if (CellsLoader.getCell(i, j) != null) {
					neighborhood.add(CellsLoader.getCell(i, j));
				}
			}
		}
		neighborhood.remove(CellsLoader.getCell(c.getX(), c.getY()));

		return neighborhood;
	}

	public static Collection<Aft> detectExtendedNeighboringAFTs(Cell c, int raduis) {
		Set<Aft> neighborhoodAFts = Collections.synchronizedSet(new HashSet<>());
		Set<Cell> neighborhoodCells = getExtendedMooreNeighborhood(c, raduis);
		neighborhoodCells.forEach(vc -> {
			if (vc.getOwner() != null && vc.getOwner().isInteract())
				neighborhoodAFts.add(vc.getOwner());
		});
		return neighborhoodAFts;
	}

	public static Set<Cell> getExtendedMooreNeighborhood(Cell c, int r) {
		Set<Cell> neighborhood = Collections.synchronizedSet(new HashSet<>());
		for (int i = c.getX() - r; i <= c.getX() + r; i++) {
			for (int j = c.getY() - r; j <= c.getY() + r; j++) {
				if (i == c.getX() && j == c.getY()) {
					continue;
				}
				Cell cell = CellsLoader.getCell(i, j);
				if (cell != null) {
					neighborhood.add(cell);
				}
			}
		}
		return neighborhood;
	}

}
