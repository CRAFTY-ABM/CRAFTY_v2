package de.cesr.crafty.core.utils.general;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import de.cesr.crafty.core.crafty.Cell;

class SelectorTest {
//
//    @Test
//    void sameInputShouldSelectSameKeysEveryTime() {
//        ConcurrentHashMap<String, Cell> cells = new ConcurrentHashMap<>();
//        cells.put("0,0", mockCell(100L, 0.90));
//        cells.put("0,1", mockCell(101L, 0.10));
//        cells.put("0,2", mockCell(102L, 0.30));
//        cells.put("0,3", mockCell(103L, 0.20));
//        cells.put("0,4", mockCell(104L, 0.80));
//
//        Set<String> first = selectedKeys(Selector.randomSeed(cells, 0.40));   // pick 2 out of 5
//        Set<String> second = selectedKeys(Selector.randomSeed(cells, 0.40));
//
//        assertEquals(first, second);
//        assertEquals(Set.of("0,1", "0,3"), first); // smallest scores: 0.10 and 0.20
//    }
//
//    @Test
//    void sameCellsDifferentInsertionOrderShouldSelectSameKeys() {
//        ConcurrentHashMap<String, Cell> cellsA = new ConcurrentHashMap<>();
//        cellsA.put("0,0", mockCell(100L, 0.90));
//        cellsA.put("0,1", mockCell(101L, 0.10));
//        cellsA.put("0,2", mockCell(102L, 0.30));
//        cellsA.put("0,3", mockCell(103L, 0.20));
//        cellsA.put("0,4", mockCell(104L, 0.80));
//
//        ConcurrentHashMap<String, Cell> cellsB = new ConcurrentHashMap<>();
//        cellsB.put("0,4", mockCell(104L, 0.80));
//        cellsB.put("0,3", mockCell(103L, 0.20));
//        cellsB.put("0,2", mockCell(102L, 0.30));
//        cellsB.put("0,1", mockCell(101L, 0.10));
//        cellsB.put("0,0", mockCell(100L, 0.90));
//
//        Set<String> selectedA = selectedKeys(Selector.randomSeed(cellsA, 0.40));
//        Set<String> selectedB = selectedKeys(Selector.randomSeed(cellsB, 0.40));
//
//        assertEquals(selectedA, selectedB);
//    }
//
//    @Test
//    void equalScoresShouldUseSmallestCellIdAsTieBreak() {
//        ConcurrentHashMap<String, Cell> cells = new ConcurrentHashMap<>();
//        cells.put("a", mockCell(40L, 0.50));
//        cells.put("b", mockCell(10L, 0.50));
//        cells.put("c", mockCell(30L, 0.50));
//        cells.put("d", mockCell(20L, 0.50));
//
//        Set<String> selected = selectedKeys(Selector.randomSeed(cells, 0.50)); // pick 2 out of 4
//
//        // same score, so smallest cell IDs should win: 10 and 20 => keys b and d
//        assertEquals(Set.of("b", "d"), selected);
//    }
//
//    @Test
//    void equalScoresAndEqualCellIdShouldUseKeyAsFinalTieBreak() {
//        ConcurrentHashMap<String, Cell> cells = new ConcurrentHashMap<>();
//        cells.put("b", mockCell(10L, 0.50));
//        cells.put("a", mockCell(10L, 0.50));
//        cells.put("c", mockCell(10L, 0.50));
//
//        Set<String> selected = selectedKeys(Selector.randomSeed(cells, 1.0 / 3.0)); // round(3 * 1/3) = 1
//
//        // same score and same cellId, so lexicographically smallest key should win
//        assertEquals(Set.of("a"), selected);
//    }
//
//    @Test
//    void boundaryCasesShouldBehaveCorrectly() {
//        ConcurrentHashMap<String, Cell> empty = new ConcurrentHashMap<>();
//        assertTrue(Selector.randomSeed(empty, 0.50).isEmpty());
//
//        ConcurrentHashMap<String, Cell> cells = new ConcurrentHashMap<>();
//        cells.put("0,0", mockCell(1L, 0.1));
//        cells.put("0,1", mockCell(2L, 0.2));
//        cells.put("0,2", mockCell(3L, 0.3));
//
//        assertTrue(Selector.randomSeed(cells, 0.0).isEmpty());
//        assertEquals(3, Selector.randomSeed(cells, 1.0).size());
//        assertEquals(3, Selector.randomSeed(cells, 2.0).size()); // clamped to 1.0
//        assertTrue(Selector.randomSeed(cells, -1.0).isEmpty());  // clamped to 0.0
//    }
//
//    @Test
//    void sameSeedFromDeterministicRandomShouldProduceSameSelectedKeys() {
//        long runSeed = 12345L;
//        int year = 2030;
//
//        ConcurrentHashMap<String, Cell> cells1 = buildCellsFromSeed(runSeed, year,
//                Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L));
//
//        ConcurrentHashMap<String, Cell> cells2 = buildCellsFromSeed(runSeed, year,
//                Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L));
//
//        Set<String> selected1 = selectedKeys(Selector.randomSeed(cells1, 0.50));
//        Set<String> selected2 = selectedKeys(Selector.randomSeed(cells2, 0.50));
//
//        assertEquals(selected1, selected2);
//    }
//
//    @Test
//    void differentSeedFromDeterministicRandomUsuallyProducesDifferentSelection() {
//        int year = 2030;
//
//        ConcurrentHashMap<String, Cell> cells1 = buildCellsFromSeed(111L, year,
//                Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L));
//
//        ConcurrentHashMap<String, Cell> cells2 = buildCellsFromSeed(222L, year,
//                Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L));
//
//        Set<String> selected1 = selectedKeys(Selector.randomSeed(cells1, 0.50));
//        Set<String> selected2 = selectedKeys(Selector.randomSeed(cells2, 0.50));
//
//        // Usually different; if this ever fails by coincidence, remove this test.
//        // It is less important than the "same seed => same result" test.
//        assertTrue(!selected1.equals(selected2),
//                "Different seeds produced the same selection by coincidence.");
//    }
//
//    private static ConcurrentHashMap<String, Cell> buildCellsFromSeed(long runSeed, int year, List<Long> cellIds) {
//        ConcurrentHashMap<String, Cell> cells = new ConcurrentHashMap<>();
//
//        for (long cellId : cellIds) {
//            double score = DeterministicRandom.randomDouble(
//                    runSeed,
//                    year,
//                    DeterministicRandom.Process.CELL_SELECTION_COMPETITION,
//                    cellId,
//                    0L,
//                    0
//            );
//            String key = "cell-" + cellId;
//            cells.put(key, mockCell(cellId, score));
//        }
//
//        return cells;
//    }
//
//    private static Cell mockCell(long cellId, double score) {
//        Cell cell = mock(Cell.class);
//        when(cell.getCellID()).thenReturn(cellId);
//        when(cell.getScore()).thenReturn(score);
//        return cell;
//    }
//
//    private static Set<String> selectedKeys(ConcurrentHashMap<String, Cell> map) {
//        return new TreeSet<>(map.keySet());
//    }
}