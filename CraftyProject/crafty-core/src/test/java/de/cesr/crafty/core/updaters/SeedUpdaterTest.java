package de.cesr.crafty.core.updaters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.utils.general.DeterministicRandom;

class SeedUpdaterTest {
//
//    private Cell cell(long cellId, int x, int y, String owner, double utility) {
//        Cell c = mock(Cell.class);
//        when(c.getCellID()).thenReturn(cellId);
//        when(c.getX()).thenReturn(x);
//        when(c.getY()).thenReturn(y);
//        when(c.getOwnerName()).thenReturn(owner);
//        when(c.getCurrentUtility()).thenReturn(utility);
//        return c;
//    }
//
//    private List<Long> ids(List<Cell> cells) {
//        return cells.stream().map(Cell::getCellID).collect(Collectors.toList());
//    }
//
//    @Test
//    void bottomPercent_sameInputSameSeedSameYear_isStable() {
//        long runSeed = 12345L;
//        int year = 2030;
//
//        List<Cell> cells = List.of(
//                cell(10L, 0, 0, "A", 5.0),
//                cell(11L, 1, 0, "A", 2.0),
//                cell(12L, 2, 0, "A", 3.0),
//                cell(13L, 3, 0, "A", 1.0),
//                cell(14L, 4, 0, "A", 4.0)
//        );
//
//        List<Long> expected = ids(SeedUpdater.bottomPercent(cells, 0.4, runSeed, year));
//
//        for (int i = 0; i < 50; i++) {
//            List<Long> actual = ids(SeedUpdater.bottomPercent(cells, 0.4, runSeed, year));
//            assertIterableEquals(expected, actual);
//        }
//    }
//
//    @Test
//    void bottomPercent_isIndependentOfInputOrder() {
//        long runSeed = 98765L;
//        int year = 2040;
//
//        List<Cell> cells = new ArrayList<>(List.of(
//                cell(100L, 0, 0, "A", 5.0),
//                cell(101L, 1, 0, "A", 2.0),
//                cell(102L, 2, 0, "A", 2.0),
//                cell(103L, 3, 0, "A", 1.0),
//                cell(104L, 4, 0, "A", 4.0),
//                cell(105L, 5, 0, "A", 3.0)
//        ));
//
//        List<Long> expected = ids(SeedUpdater.bottomPercent(cells, 0.5, runSeed, year));
//
//        List<Cell> shuffled = new ArrayList<>(cells);
//        Collections.shuffle(shuffled, new Random(42));
//
//        List<Long> actual = ids(SeedUpdater.bottomPercent(shuffled, 0.5, runSeed, year));
//
//        assertIterableEquals(expected, actual);
//    }
//
//    @Test
//    void bottomPercent_equalUtilities_usesDeterministicTieBreak() {
//        long runSeed = 555L;
//        int year = 2035;
//
//        List<Cell> cells = List.of(
//                cell(1L, 0, 0, "A", 7.0),
//                cell(2L, 1, 0, "A", 7.0),
//                cell(3L, 2, 0, "A", 7.0),
//                cell(4L, 3, 0, "A", 7.0),
//                cell(5L, 4, 0, "A", 7.0)
//        );
//
//        List<Long> expected = cells.stream()
//                .sorted(Comparator
//                        .comparingDouble((Cell c) -> 7.0)
//                        .thenComparingLong(c -> DeterministicRandom.randomLong(
//                                runSeed,
//                                year,
//                                DeterministicRandom.Process.TIE_BREAK,
//                                c.getCellID(),
//                                0L,
//                                0))
//                        .thenComparingLong(Cell::getCellID))
//                .limit(2)
//                .map(Cell::getCellID)
//                .collect(Collectors.toList());
//
//        List<Long> actual = ids(SeedUpdater.bottomPercent(cells, 0.4, runSeed, year));
//
//        assertIterableEquals(expected, actual);
//    }
//
//    @Test
//    void bottomPercent_nanUtilitiesAreWorst() {
//        long runSeed = 222L;
//        int year = 2032;
//
//        List<Cell> cells = List.of(
//                cell(1L, 0, 0, "A", 1.0),
//                cell(2L, 1, 0, "A", 2.0),
//                cell(3L, 2, 0, "A", Double.NaN),
//                cell(4L, 3, 0, "A", 0.5)
//        );
//
//        List<Long> actual = ids(SeedUpdater.bottomPercent(cells, 0.25, runSeed, year));
//
//        assertEquals(List.of(4L), actual);
//    }
//
//    @Test
//    void bottomPercent_selectsCorrectBottomK() {
//        long runSeed = 444L;
//        int year = 2045;
//
//        List<Cell> cells = List.of(
//                cell(1L, 0, 0, "A", 9.0),
//                cell(2L, 1, 0, "A", 1.0),
//                cell(3L, 2, 0, "A", 7.0),
//                cell(4L, 3, 0, "A", 2.0),
//                cell(5L, 4, 0, "A", 3.0),
//                cell(6L, 5, 0, "A", 8.0)
//        );
//
//        List<Long> actual = ids(SeedUpdater.bottomPercent(cells, 0.5, runSeed, year));
//
//        // bottom 3 utilities are: 1.0, 2.0, 3.0 => cell IDs 2, 4, 5
//        assertIterableEquals(List.of(2L, 4L, 5L), actual);
//    }
}