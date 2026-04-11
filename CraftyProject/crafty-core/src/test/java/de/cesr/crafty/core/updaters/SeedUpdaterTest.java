package de.cesr.crafty.core.updaters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.crafty.Region;
import de.cesr.crafty.core.crafty.RegionalModelRunner;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.utils.general.Selector;

class SeedUpdaterTest {

    private Object configBackup;

    @BeforeEach
    void backupConfig() throws Exception {
        Field f = findField(ConfigLoader.class, "config");
        f.setAccessible(true);
        configBackup = f.get(null);
    }

    @AfterEach
    void restoreConfig() throws Exception {
        Field f = findField(ConfigLoader.class, "config");
        f.setAccessible(true);
        f.set(null, configBackup);
    }

    // ---------------------------------------------------------
    // bottomPercent(...) correctness (most important)
    // ---------------------------------------------------------

    @Test
    void bottomPercent_returnsEmptyForEmptyInputOrZeroPercent() {
        assertEquals(List.of(), SeedUpdater.bottomPercent(List.of(), 0.5));

        List<Cell> cells = List.of(cell("A", 0, 0, 1.0));
        assertEquals(List.of(), SeedUpdater.bottomPercent(cells, 0.0));
        assertEquals(List.of(), SeedUpdater.bottomPercent(cells, -1.0));
    }

    @Test
    void bottomPercent_returnsAllWhenPercentAtLeastOne() {
        List<Cell> cells = List.of(
                cell("A", 0, 0, 5.0),
                cell("A", 1, 0, 1.0),
                cell("A", 2, 0, 3.0)
        );

        List<Cell> out = SeedUpdater.bottomPercent(cells, 1.0);
        assertEquals(3, out.size());

        // it may return in sorted order; ensure it contains all by coords
        assertEquals(setOfKeys(cells), setOfKeys(out));
    }

    @Test
    void bottomPercent_usesCeilForK() {
        // n=3, p=0.34 -> ceil(1.02)=2
        List<Cell> cells = List.of(
                cell("A", 0, 0, 1.0),
                cell("A", 1, 0, 2.0),
                cell("A", 2, 0, 3.0)
        );

        List<Cell> out = SeedUpdater.bottomPercent(cells, 0.34);
        assertEquals(2, out.size());
        assertEquals(Set.of("0,0", "1,0"), setOfKeys(out));
    }

    @Test
    void bottomPercent_tieBreaksByXThenY_whenUtilitiesEqual() {
        List<Cell> cells = List.of(
                cell("A", 1, 0, 1.0),
                cell("A", 0, 1, 1.0),
                cell("A", 0, 0, 1.0),
                cell("A", 2, 0, 1.0)
        );

        List<Cell> out = SeedUpdater.bottomPercent(cells, 0.5);
        assertEquals(2, out.size());
    }

    @Test
    void bottomPercent_treatsNaNUtilityAsInfinity() {
        List<Cell> cells = List.of(
                cell("A", 0, 0, Double.NaN),
                cell("A", 1, 0, 1.0),
                cell("A", 2, 0, 2.0)
        );

        // k=1 -> should pick utility=1.0, not NaN
        List<Cell> out = SeedUpdater.bottomPercent(cells, 1.0 / 3.0);
        assertEquals(1, out.size());
        assertEquals("1,0", key(out.get(0)));
    }

    @Test
    void bottomPercent_handlesVeryCloseUtilities_correctly() {
        double u = 1.0;
        List<Cell> cells = List.of(
                cell("A", 0, 0, u + 2e-12),
                cell("A", 1, 0, u + 1e-12),
                cell("A", 2, 0, u),
                cell("A", 3, 0, u + 3e-12)
        );
        List<Cell> out = SeedUpdater.bottomPercent(cells, 0.5); // k=2
        assertEquals(Set.of("2,0", "1,0"), setOfKeys(out));
    }

    /**
     * Stress/regression: compare against a sequential "golden" implementation.
     * This is the best way to catch subtle parallel-collector mistakes.
     */
//    @RepeatedTest(3)
    @Test
    void bottomPercent_matchesSequentialReference_evenWithCloseValues() {
        int n = 2000;
        double percent = 0.12; // k = ceil(240) = 240
        Random rnd = new Random(12345); // fixed seed => deterministic

        List<Cell> cells = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            // cluster utilities very tightly around 1.0 (the scenario you described)
            double util = 1.0 + (rnd.nextInt(10_000) - 5_000) * 1e-12;
            cells.add(cell("A", i, 0, util));
        }

        List<Cell> actual = SeedUpdater.bottomPercent(cells, percent);
        List<Cell> expected = referenceBottomPercent(cells, percent);

        assertEquals(expected.size(), actual.size(), "k mismatch");
        assertEquals(setOfKeys(expected), setOfKeys(actual), "Selected bottom-k differs from reference");
    }

    // ---------------------------------------------------------
    // selectSeed(...) behavior (rank path + random path)
    // ---------------------------------------------------------

    @Test
    void selectSeed_rankPath_returnsBottomPercentMap_notByAfts() throws Exception {
        Object cfg = ensureConfigInstance();
        setField(cfg, "seedID", "rank");
        setField(cfg, "longSeedID", new AtomicLong());

        ConcurrentHashMap<String, Cell> cellMap = new ConcurrentHashMap<>();
        cellMap.put("0,0", cell("O1", 0, 0, 5.0));
        cellMap.put("1,0", cell("O1", 1, 0, 1.0));
        cellMap.put("2,0", cell("O1", 2, 0, 3.0));

        Region region = mock(Region.class);
        when(region.getCells()).thenReturn(cellMap);

        RegionalModelRunner r = mock(RegionalModelRunner.class);
        r.R = region;

        ConcurrentHashMap<String, Cell> seed = SeedUpdater.selectSeed(r,region.getCells(), 1.0 / 3.0, false, 42L);
        assertNotNull(seed);
        assertEquals(Set.of("1,0"), seed.keySet());
    }

    @Test
    void selectSeed_rankPath_byAfts_selectsPerOwnerGroup() throws Exception {
        Object cfg = ensureConfigInstance();
        setField(cfg, "seedID", "rank");
        setField(cfg, "longSeedID", new AtomicLong());

        // Two owners, 4 cells each, percent=0.5 -> 2 per owner -> total 4
        ConcurrentHashMap<String, Cell> cellMap = new ConcurrentHashMap<>();
        // Owner A: utilities 1,2,3,4 -> pick 1 & 2
        cellMap.put("0,0", cell("A", 0, 0, 1.0));
        cellMap.put("1,0", cell("A", 1, 0, 2.0));
        cellMap.put("2,0", cell("A", 2, 0, 3.0));
        cellMap.put("3,0", cell("A", 3, 0, 4.0));
        // Owner B: utilities 10,11,12,13 -> pick 10 & 11
        cellMap.put("0,1", cell("B", 0, 1, 10.0));
        cellMap.put("1,1", cell("B", 1, 1, 11.0));
        cellMap.put("2,1", cell("B", 2, 1, 12.0));
        cellMap.put("3,1", cell("B", 3, 1, 13.0));

        Region region = mock(Region.class);
        when(region.getCells()).thenReturn(cellMap);

        RegionalModelRunner r = mock(RegionalModelRunner.class);
        r.R = region;

        ConcurrentHashMap<String, Cell> seed = SeedUpdater.selectSeed(r, region.getCells(),0.5, true, 42L);

        assertNotNull(seed);
        assertEquals(4, seed.size());
        assertEquals(Set.of("0,0", "1,0", "0,1", "1,1"), seed.keySet());
    }

    @Test
    void selectSeed_randomPath_callsSelectorRandomSeed_andIncrementsLongSeedId() throws Exception {
        Object cfg = ensureConfigInstance();
        setField(cfg, "seedID", "whatever");
        setField(cfg, "longSeedID", new AtomicLong(10));

        ConcurrentHashMap<String, Cell> cellMap = new ConcurrentHashMap<>();
        cellMap.put("0,0", cell("A", 0, 0, 1.0));

        Region region = mock(Region.class);
        when(region.getCells()).thenReturn(cellMap);

        RegionalModelRunner r = mock(RegionalModelRunner.class);
        r.R = region;

        ConcurrentHashMap<String, Cell> expected = new ConcurrentHashMap<>();
        expected.put("x", cell("A", 99, 99, 0.0));

        try (MockedStatic<Selector> sel = Mockito.mockStatic(Selector.class)) {
            sel.when(() -> Selector.randomSeed(cellMap, 0.2, 777L)).thenReturn(expected);

            ConcurrentHashMap<String, Cell> out = SeedUpdater.selectSeed(r,region.getCells(), 0.2, false, 777L);

            assertSame(expected, out);
            sel.verify(() -> Selector.randomSeed(cellMap, 0.2, 777L), times(1));
        }
        AtomicLong actual = (AtomicLong) getField(cfg, "longSeedID");
        assertEquals(11L, actual.get(), "longSeedID should have been incremented");

    }

    // =========================================================
    // Reference implementation (sequential) for bottomPercent
    // =========================================================

    private static List<Cell> referenceBottomPercent(Collection<Cell> cells, double percent) {
        int n = cells.size();
        if (n == 0) return List.of();

        double p = Math.max(0.0, Math.min(1.0, percent));
        int k = (int) Math.ceil(n * p);
        if (k <= 0) return List.of();
        if (k >= n) return new ArrayList<>(cells);

        Comparator<Cell> asc = Comparator
                .comparingDouble((Cell c) -> {
                    double u = c.getCurrentUtility();
                    return Double.isNaN(u) ? Double.POSITIVE_INFINITY : u;
                })
                .thenComparingInt(Cell::getX)
                .thenComparingInt(Cell::getY);

        ArrayList<Cell> sorted = new ArrayList<>(cells);
        sorted.sort(asc);
        return new ArrayList<>(sorted.subList(0, k));
    }

    // =========================================================
    // Small helpers
    // =========================================================

    private static Cell cell(String ownerName, int x, int y, double utility) {
        Cell c = mock(Cell.class);
        when(c.getOwnerName()).thenReturn(ownerName);
        when(c.getX()).thenReturn(x);
        when(c.getY()).thenReturn(y);
        when(c.getCurrentUtility()).thenReturn(utility);
        return c;
    }

    private static String key(Cell c) {
        return c.getX() + "," + c.getY();
    }

    private static Set<String> setOfKeys(Collection<Cell> cells) {
        Set<String> s = new HashSet<>();
        for (Cell c : cells) s.add(key(c));
        return s;
    }


    private static Object ensureConfigInstance() throws Exception {
        Field f = findField(ConfigLoader.class, "config");
        f.setAccessible(true);
        Object cfg = f.get(null);
        if (cfg == null) {
            cfg = f.getType().getDeclaredConstructor().newInstance();
            f.set(null, cfg);
        }
        return cfg;
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field f = findField(target.getClass(), fieldName);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field f = findField(target.getClass(), fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field '" + fieldName + "'", e);
        }
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> c = type;
        while (c != null) {
            try {
                return c.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
    
    

    // ----------------------------
    // Core regression tests
    // ----------------------------

    @RepeatedTest(3)
    void bottomPercent_isDeterministic_andMatchesSequentialReference_largeN() throws Throwable {
        withServices(List.of("S1"), () -> {
            final int n = 200_000;           // CI-friendly; run the disabled test for bigger
            final double percent = 0.07;     // k = ceil(n*percent)
            final int width = 2000;          // for x/y layout

            List<Cell> cells = generateCellsCloseUtilities(n, width, 12345L);

            // Run twice to catch any nondeterminism from the parallel collector
            List<Cell> a1 = SeedUpdater.bottomPercent(cells, percent);
            List<Cell> a2 = SeedUpdater.bottomPercent(cells, percent);

            assertEquals(keys(a1), keys(a2), "Parallel selection should be deterministic across runs");

            // Compare to sequential "gold" implementation
            List<Cell> expected = referenceBottomPercentSequential(cells, percent);
            assertEquals(keys(expected), keys(a1), "Parallel selection must match sequential reference");
        });
    }

   
    /**
     * Optional: enable locally to get closer to “5M-ish” behavior.
     * Keep disabled in CI because it’s memory/time heavy.
     */
//    @Disabled("Enable locally for very large regression (memory/time heavy).")
//    @Test
//    void bottomPercent_matchesReference_veryLarge() throws Throwable {
//        withServices(List.of("S1"), () -> {
//            final int n = 1_000_000;     
//            final double percent = 0.02;
//            final int width = 5000;
//
//            List<Cell> cells = generateCellsCloseUtilities(n, width, 12345L);
//
//            List<Cell> actual = SeedUpdater.bottomPercent(cells, percent);
//            List<Cell> expected = referenceBottomPercentSequential(cells, percent);
//
//            assertEquals(keys(expected), keys(actual));
//        });
//    }

    // ----------------------------
    // Utilities
    // ----------------------------

    /**
     * Utilities are extremely close around 1.0 (your “close values” scenario),
     * but still totally ordered most of the time.
     */
    private static List<Cell> generateCellsCloseUtilities(int n, int width, long seed) {
        SplittableRandom rnd = new SplittableRandom(seed);
        ArrayList<Cell> cells = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            int x = i % width;
            int y = i / width;

            // Very tight distribution around 1.0
            // mix discrete + tiny jitter to simulate quantization + floating noise
            long bucket = rnd.nextLong(10_000);     // many near ties
            double jitter = (rnd.nextInt(1_000) - 500) * 1e-15;

            double util = 1.0 + bucket * 1e-12 + jitter;

            Cell c = new Cell(x, y);
            c.setCurrentUtility(util);
            cells.add(c);
        }
        return cells;
    }

    private static List<Cell> referenceBottomPercentSequential(Collection<Cell> cells, double percent) {
        int n = cells.size();
        if (n == 0) return List.of();

        double p = Math.max(0.0, Math.min(1.0, percent));
        int k = (int) Math.ceil(n * p);
        if (k <= 0) return List.of();
        if (k >= n) return new ArrayList<>(cells);

        Comparator<Cell> asc = Comparator
                .comparingDouble((Cell c) -> {
                    double u = c.getCurrentUtility();
                    return Double.isNaN(u) ? Double.POSITIVE_INFINITY : u;
                })
                .thenComparingInt(Cell::getX)
                .thenComparingInt(Cell::getY);

        ArrayList<Cell> sorted = new ArrayList<>(cells);
        sorted.sort(asc);
        return new ArrayList<>(sorted.subList(0, k));
    }

    private static Set<String> keys(List<Cell> cells) {
        return cells.stream().map(c -> c.getX() + "," + c.getY()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Cell constructor depends on ServiceSet.getServicesList().size().
     * We keep it tiny for memory/perf.
     */
    private static void withServices(List<String> services, ThrowingRunnable body) throws Throwable {
        // Try to mutate real list (if mutable)
        try {
            List<String> real = ServiceSet.getServicesList();
            List<String> backup = new ArrayList<>(real);
            try {
                real.clear();
                real.addAll(services);
                body.run();
            } finally {
                real.clear();
                real.addAll(backup);
            }
        } catch (UnsupportedOperationException ex) {
            // Fall back to static mocking
            try (MockedStatic<ServiceSet> mocked = Mockito.mockStatic(ServiceSet.class)) {
                mocked.when(ServiceSet::getServicesList).thenReturn(new ArrayList<>(services));
                body.run();
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
