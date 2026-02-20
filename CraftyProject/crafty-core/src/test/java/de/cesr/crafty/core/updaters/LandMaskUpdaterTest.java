package de.cesr.crafty.core.updaters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.land.MaskLoader;
import de.cesr.crafty.core.output.Tracker;
import de.cesr.crafty.core.utils.file.CsvTools;

class LandMaskUpdaterTest {

    @TempDir Path tmp;

    @BeforeEach
    void resetState() {
        LandMaskUpdater.restrictions.clear();

        // Reset MaskLoader maps
        MaskLoader.mask_paths = new HashMap<>();
        MaskLoader.restriction_paths = new HashMap<>();

        // Reset CellsLoader hash
        if (CellsLoader.hashCell == null) {
            CellsLoader.hashCell = new ConcurrentHashMap<>();
        } else {
            CellsLoader.hashCell.clear();
        }
    }

    @Test
    void cellOneMaskUpdater_appliesMaskAndOwner_andCleansPreviousAssignments() throws Exception {
        String maskType = "Urban_mask"; // contains "Urban"
        int year = 2000;

        // AFT "Urban"
        Aft urban = mock(Aft.class);
        when(urban.getLabel()).thenReturn("Urban");

        Map<String, Aft> afts = new ConcurrentHashMap<>();
        afts.put("Urban", urban);

        // 3 stateful cells; all start already masked + owned -> should be cleaned then re-applied for rows with 1
        CellState c12 = statefulCell(maskType, urban);
        CellState c34 = statefulCell(maskType, urban);
        CellState c56 = statefulCell(maskType, urban);

        CellsLoader.hashCell.put("1,2", c12.cell);
        CellsLoader.hashCell.put("3,4", c34.cell);
        CellsLoader.hashCell.put("5,6", c56.cell);

        // CSV file: apply to (1,2) and (5,6), not (3,4)
        Path csv = tmp.resolve("mask.csv");
        Files.writeString(csv,
                "X,Y,Year_2000\n" +
                "1,2,1\n" +
                "3,4,0\n" +
                "5,6,1\n");

        MaskLoader.mask_paths.put(maskType, new TreeMap<>(Map.of(year, csv)));

        try (MockedStatic<CellsLoader> cellsLoader = Mockito.mockStatic(CellsLoader.class);
             MockedStatic<AFTsLoader> aftsLoader = Mockito.mockStatic(AFTsLoader.class)) {

            cellsLoader.when(() -> CellsLoader.getCell(1, 2)).thenReturn(c12.cell);
            cellsLoader.when(() -> CellsLoader.getCell(3, 4)).thenReturn(c34.cell);
            cellsLoader.when(() -> CellsLoader.getCell(5, 6)).thenReturn(c56.cell);

            aftsLoader.when(AFTsLoader::getAftHash).thenReturn(afts);
            Tracker.sankeydata.put("Abandoned", new ConcurrentHashMap<>());
            Tracker.sankeydata.put("Urban", new ConcurrentHashMap<>());
            Tracker.sankeydata.get("Abandoned").put(Timestep.getCurrentYear(),new ConcurrentHashMap<>() );
            Tracker.sankeydata.get("Urban").put(Timestep.getCurrentYear(),new ConcurrentHashMap<>() );
            LandMaskUpdater.cellsForecedToChange.put(maskType, new ConcurrentHashMap<>());
            LandMaskUpdater.cellOneMaskUpdater(maskType, year);

            // (1,2) should be masked + owned
            assertEquals(maskType, c12.maskType.get());
            assertSame(urban, c12.owner.get());

            // (3,4) should be cleaned and NOT re-applied
            assertNull(c34.maskType.get());
            assertNull(c34.owner.get());

            // (5,6) should be masked + owned
            assertEquals(maskType, c56.maskType.get());
            assertSame(urban, c56.owner.get());
        }
    }

    @Test
    void cellOneMaskUpdater_missingXYColumns_doesNotCleanOrChangeCells() throws Exception {
        String maskType = "Urban_mask";
        int year = 2000;

        Aft urban = mock(Aft.class);
        when(urban.getLabel()).thenReturn("Urban");

        CellState c12 = statefulCell(maskType, urban);
        CellsLoader.hashCell.put("1,2", c12.cell);

        Path csv = tmp.resolve("bad_header.csv");
        Files.writeString(csv,
                "A,B,Year_2000\n" +
                "1,2,1\n");

        MaskLoader.mask_paths.put(maskType, new TreeMap<>(Map.of(year, csv)));

        try (MockedStatic<CellsLoader> cellsLoader = Mockito.mockStatic(CellsLoader.class)) {
            // even if getCell would return something, code returns early before cleaning
            cellsLoader.when(() -> CellsLoader.getCell(anyInt(), anyInt())).thenReturn(c12.cell);

            LandMaskUpdater.cellOneMaskUpdater(maskType, year);

            // unchanged
            assertEquals(maskType, c12.maskType.get());
            assertSame(urban, c12.owner.get());
        }
    }

    @Test
    void updateRestrections_updatesRestrictionsMap_whenYearPathExists() {
        String maskType = "Urban_mask";
        int year = 2000;

        Path dummy = tmp.resolve("restr.csv");
        MaskLoader.restriction_paths.put(maskType, new TreeMap<>(Map.of(year, dummy)));

        String[][] matrix = new String[][] {
                {"", "AFT1", "AFT2"},
                {"MaskA", "1", "0"},
                {"MaskB", "0", "1"}
        };

        try (MockedStatic<Timestep> timestep = Mockito.mockStatic(Timestep.class);
             MockedStatic<CsvTools> csvTools = Mockito.mockStatic(CsvTools.class)) {

            timestep.when(Timestep::getCurrentYear).thenReturn(year);
            csvTools.when(() -> CsvTools.csvReader(dummy)).thenReturn(matrix);

            LandMaskUpdater.updateRestrections(maskType);

            assertNotNull(LandMaskUpdater.restrictions.get(maskType));
            Map<String, Boolean> r = LandMaskUpdater.restrictions.get(maskType);

            assertEquals(true,  r.get("MaskA_AFT1"));
            assertEquals(false, r.get("MaskA_AFT2"));
            assertEquals(false, r.get("MaskB_AFT1"));
            assertEquals(true,  r.get("MaskB_AFT2"));
        }
    }

    @Test
    void updateRestrections_missingMaskTypeKey_throwsNpe_documentingRisk() {
        assertThrows(NullPointerException.class, () -> LandMaskUpdater.updateRestrections("NoSuchMask"));
    }

    // ---------------- helpers ----------------

    private static final class CellState {
        final Cell cell;
        final AtomicReference<String> maskType;
        final AtomicReference<Aft> owner;

        CellState(Cell cell, AtomicReference<String> maskType, AtomicReference<Aft> owner) {
            this.cell = cell;
            this.maskType = maskType;
            this.owner = owner;
        }
    }

    private static CellState statefulCell(String initialMaskType, Aft initialOwner) {
        AtomicReference<String> maskRef = new AtomicReference<>(initialMaskType);
        AtomicReference<Aft> ownerRef = new AtomicReference<>(initialOwner);

        Answer<Object> ans = inv -> {
            String m = inv.getMethod().getName();
            switch (m) {
                case "getMaskType": return maskRef.get();
                case "setMaskType": maskRef.set((String) inv.getArgument(0)); return null;
                case "getOwner": return ownerRef.get();
                case "setOwner": ownerRef.set((Aft) inv.getArgument(0)); return null;
                default: return Mockito.RETURNS_DEFAULTS.answer(inv);
            }
        };

        Cell cell = mock(Cell.class, withSettings().defaultAnswer(ans));
        return new CellState(cell, maskRef, ownerRef);
    }
}
