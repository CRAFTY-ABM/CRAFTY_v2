package de.cesr.crafty.core.utils.general;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;

class CellsSubSetsTest {

	/**
	 * Helper: create a mocked cell with fixed coordinates.
	 */
	private Cell mockCell(int x, int y) {
		Cell cell = mock(Cell.class);
		when(cell.getX()).thenReturn(x);
		when(cell.getY()).thenReturn(y);
		return cell;
	}

	@Test
	void getMooreNeighborhoodShouldReturnEightNeighborsWhenAllPresent() {
		// Center cell
		Cell center = mockCell(5, 5);

		// Create 3x3 grid of cells around center
		Map<String, Cell> grid = new HashMap<>();
		for (int x = 4; x <= 6; x++) {
			for (int y = 4; y <= 6; y++) {
				Cell c = mockCell(x, y);
				grid.put(x + "," + y, c);
			}
		}

		try (MockedStatic<CellsLoader> mocked = Mockito.mockStatic(CellsLoader.class)) {
			mocked.when(() -> CellsLoader.getCell(anyInt(), anyInt())).thenAnswer(invocation -> {
				int x = invocation.getArgument(0);
				int y = invocation.getArgument(1);
				return grid.get(x + "," + y);
			});

			// Act
			Set<Cell> neighbors = CellsSubSets.getMooreNeighborhood(center);

			// Assert: 8 neighbors, center not included
			assertEquals(8, neighbors.size(), "Moore neighborhood should have 8 cells");
			assertFalse(neighbors.contains(grid.get("5,5")), "Center cell must not be included");
		}
	}

	@Test
	void getMooreNeighborhoodShouldIgnoreNullCells() {
		Cell center = mockCell(0, 0);

		// Only two neighbors exist in our "world"
		Map<String, Cell> grid = new HashMap<>();
		grid.put("0,-1", mockCell(0, -1));
		grid.put("1,0", mockCell(1, 0));

		try (MockedStatic<CellsLoader> mocked = Mockito.mockStatic(CellsLoader.class)) {
			mocked.when(() -> CellsLoader.getCell(anyInt(), anyInt())).thenAnswer(invocation -> {
				int x = invocation.getArgument(0);
				int y = invocation.getArgument(1);
				return grid.get(x + "," + y); // returns null otherwise
			});

			Set<Cell> neighbors = CellsSubSets.getMooreNeighborhood(center);
			assertEquals(2, neighbors.size(), "Only the two defined neighbors should be returned");
		}
	}

	@Test
	void detectNeighboringAFTsShouldReturnInteractiveOwnersOnly() {
		Cell center = mockCell(10, 10);

		// Mock AFTs
		Aft interactiveA = mock(Aft.class);
		Aft interactiveB = mock(Aft.class);
		Aft nonInteractive = mock(Aft.class);
		when(interactiveA.isInteract()).thenReturn(true);
		when(interactiveB.isInteract()).thenReturn(true);
		when(nonInteractive.isInteract()).thenReturn(false);

		// Create neighbor cells
		Cell cell1 = mockCell(9, 10);
		when(cell1.getOwner()).thenReturn(interactiveA);

		Cell cell2 = mockCell(10, 9);
		when(cell2.getOwner()).thenReturn(nonInteractive);

		Cell cell3 = mockCell(11, 11);
		when(cell3.getOwner()).thenReturn(interactiveB);

		// This cell has null owner and should be ignored
		Cell cell4 = mockCell(9, 9);
		when(cell4.getOwner()).thenReturn(null);

		Map<String, Cell> grid = new HashMap<>();
		grid.put("9,10", cell1);
		grid.put("10,9", cell2);
		grid.put("11,11", cell3);
		grid.put("9,9", cell4);
		grid.put("10,10", center);

		try (MockedStatic<CellsLoader> mocked = Mockito.mockStatic(CellsLoader.class)) {
			mocked.when(() -> CellsLoader.getCell(anyInt(), anyInt())).thenAnswer(invocation -> {
				int x = invocation.getArgument(0);
				int y = invocation.getArgument(1);
				return grid.get(x + "," + y);
			});

			// Act
			var neighboringAFTs = CellsSubSets.detectNeighboringAFTs(center);

			// Assert: only interactive owners, no duplicates
			assertEquals(2, neighboringAFTs.size());
			assertTrue(neighboringAFTs.contains(interactiveA));
			assertTrue(neighboringAFTs.contains(interactiveB));
			assertFalse(neighboringAFTs.contains(nonInteractive));
		}
	}

	@Test
	void getExtendedMooreNeighborhoodShouldRespectRadius() {
		Cell center = mockCell(5, 5);
		int radius = 2;

		// Fill a (2r+1)x(2r+1) grid around center except one missing cell
		Map<String, Cell> grid = new HashMap<>();
		for (int x = 5 - radius; x <= 5 + radius; x++) {
			for (int y = 5 - radius; y <= 5 + radius; y++) {
				if (x == 5 && y == 5) {
					continue; // center
				}
				// skip one neighbor to confirm nulls are ignored
				if (x == 4 && y == 4) {
					continue;
				}
				grid.put(x + "," + y, mockCell(x, y));
			}
		}

		try (MockedStatic<CellsLoader> mocked = Mockito.mockStatic(CellsLoader.class)) {
			mocked.when(() -> CellsLoader.getCell(anyInt(), anyInt())).thenAnswer(invocation -> {
				int x = invocation.getArgument(0);
				int y = invocation.getArgument(1);
				return grid.get(x + "," + y);
			});

			Set<Cell> neighbors = CellsSubSets.getExtendedMooreNeighborhood(center, radius);

			// Expected total positions in square minus center = (2r+1)^2 - 1 = 24
			// We deliberately left out one (4,4) => expect 23 cells
			assertEquals(23, neighbors.size(), "Extended neighborhood should include all existing cells except center");
		}
	}

    @Test
    void detectExtendedNeighboringAFTsShouldDeduplicateAFTs() {
        Cell center = mockCell(0, 0);
        int radius = 1;

        Aft aft1 = mock(Aft.class);
        when(aft1.isInteract()).thenReturn(true);

        // Two cells with the same interactive AFT
        Cell c1 = mockCell(-1, 0);
        when(c1.getOwner()).thenReturn(aft1);

        Cell c2 = mockCell(1, 0);
        when(c2.getOwner()).thenReturn(aft1);

        Map<String, Cell> grid = new HashMap<>();
        grid.put("-1,0", c1);
        grid.put("1,0", c2);
        grid.put("0,0", center);

        try (MockedStatic<CellsLoader> mocked = Mockito.mockStatic(CellsLoader.class)) {
            mocked.when(() -> CellsLoader.getCell(anyInt(), anyInt()))
                    .thenAnswer(invocation -> {
                        int x = invocation.getArgument(0);
                        int y = invocation.getArgument(1);
                        return grid.get(x + "," + y);
                    });

            var afts = CellsSubSets.detectExtendedNeighboringAFTs(center, radius);

            assertEquals(1, afts.size(),
                    "Same AFT appearing in multiple neighbor cells should only appear once in the result");
            assertTrue(afts.contains(aft1));
        }
    }
}
