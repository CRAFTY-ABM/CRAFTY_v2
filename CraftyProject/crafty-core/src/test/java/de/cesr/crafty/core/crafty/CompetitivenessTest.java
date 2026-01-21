package de.cesr.crafty.core.crafty;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.afts.AftCategorised;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.output.Listener;
import de.cesr.crafty.core.updaters.CellBehaviourUpdater;
import de.cesr.crafty.core.updaters.LandMaskUpdater;

class CompetitivenessTest {

    private Object originalConfig;
    private boolean originalUseCategorisationGivIn;
    private boolean originalBehaviourUsed;
    private Map<String, Double> meanBackup;
    private Map<String, Double> sdBackup;
    private Map<String, ConcurrentHashMap<String, Boolean>> restrictionsBackup;

    @BeforeEach
    void snapshotAndReset() throws Exception {
        // ensure counter exists + reset
        if (Listener.landUseChangeCounter == null) {
            Listener.landUseChangeCounter = new AtomicInteger(0);
        } else {
            Listener.landUseChangeCounter.set(0);
        }
        
        

        // snapshot config
        Field cfgField = findField(ConfigLoader.class, "config");
        cfgField.setAccessible(true);
        originalConfig = cfgField.get(null);

        // snapshot AftCategorised
        originalUseCategorisationGivIn = AftCategorised.useCategorisationGivIn;
        meanBackup = new HashMap<>(AftCategorised.getMean());
        sdBackup = new HashMap<>(AftCategorised.getSD());

        // snapshot CellBehaviourUpdater
        originalBehaviourUsed = CellBehaviourUpdater.behaviourUsed;

        // snapshot LandMaskUpdater.restrictions
        if (LandMaskUpdater.restrictions == null) {
            LandMaskUpdater.restrictions = new ConcurrentHashMap<>();
        }
        restrictionsBackup = deepCopyRestrictions(LandMaskUpdater.restrictions);
        Listener.newAftsInLandNbr = new ConcurrentHashMap<>();
    }

    @AfterEach
    void restore() throws Exception {
        // restore config
        Field cfgField = findField(ConfigLoader.class, "config");
        cfgField.setAccessible(true);
        cfgField.set(null, originalConfig);

        // restore AftCategorised
        AftCategorised.useCategorisationGivIn = originalUseCategorisationGivIn;
        AftCategorised.getMean().clear();
        AftCategorised.getMean().putAll(meanBackup);
        AftCategorised.getSD().clear();
        AftCategorised.getSD().putAll(sdBackup);

        // restore behaviour flag
        CellBehaviourUpdater.behaviourUsed = originalBehaviourUsed;

        // restore restrictions
        LandMaskUpdater.restrictions.clear();
        LandMaskUpdater.restrictions.putAll(restrictionsBackup);
    }

    // -------------------------------------------------------
    // utility(...)
    // -------------------------------------------------------

    @Test
    void utility_returnsZero_whenAftNullOrNotInteract() throws Throwable {
        withServices(List.of("S1"), () -> {
            Cell c = new Cell(0, 0);
            RegionalModelRunner r = mock(RegionalModelRunner.class);

            assertEquals(0.0, Competitiveness.utility(c, null, r), 1e-12);

            Aft a = mock(Aft.class);
            when(a.isInteract()).thenReturn(false);
            assertEquals(0.0, Competitiveness.utility(c, a, r), 1e-12);
        });
    }

    @Test
    void utility_sumsTaxPlusMarginal_timesProductivity_plusLandTax() throws Throwable {
        withServices(List.of("S1", "S2"), () -> {
            Cell c = Mockito.spy(new Cell(0, 0));

            Aft a = mock(Aft.class);
            when(a.isInteract()).thenReturn(true);
            when(a.getCachedLandTax()).thenReturn(5.0);

            RegionalModelRunner r = mock(RegionalModelRunner.class);
            when(r.getServiceTax()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 1.0, "S2", 10.0)));
            when(r.getMarginal()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 0.5, "S2", -2.0)));

            // S1 productivity=2, S2 productivity=3
            doReturn(2.0).when(c).productivity(a, "S1");
            doReturn(3.0).when(c).productivity(a, "S2");

            // expected: (1+0.5)*2 + (10-2)*3 + 5 = 3 + 24 + 5 = 32
            assertEquals(32.0, Competitiveness.utility(c, a, r), 1e-12);
        });
    }

    @Test
    void associateUtility_setsCellCurrentUtility() throws Throwable {
        withServices(List.of("S1"), () -> {
            Cell c = Mockito.spy(new Cell(0, 0));

            Aft owner = mock(Aft.class);
            when(owner.isInteract()).thenReturn(true);
            when(owner.getCachedLandTax()).thenReturn(1.0);
            setOwner(c, owner);

            RegionalModelRunner r = mock(RegionalModelRunner.class);
            when(r.getServiceTax()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 2.0)));
            when(r.getMarginal()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 0.0)));

            doReturn(10.0).when(c).productivity(owner, "S1"); // => 2*10 + 1 = 21

            Competitiveness.associateUtility(c, r);

            assertEquals(21.0, c.getCurrentUtility(), 1e-12);
        });
    }

    // -------------------------------------------------------
    // mostCompetitiveAgent(...)
    // -------------------------------------------------------

    @Test
    void mostCompetitiveAgent_returnsOwner_whenSetIsEmpty() throws Throwable {
        withServices(List.of("S1"), () -> {
            Cell c = new Cell(0, 0);
            Aft owner = mock(Aft.class);
            setOwner(c, owner);

            RegionalModelRunner r = mock(RegionalModelRunner.class);

            assertSame(owner, Competitiveness.mostCompetitiveAgent(c, List.of(), r));
        });
    }

    @Test
    void mostCompetitiveAgent_picksAgentWithHighestUtility() throws Throwable {
        withServices(List.of("S1"), () -> {
            Cell c = Mockito.spy(new Cell(0, 0));

            Aft a1 = mock(Aft.class);
            when(a1.isInteract()).thenReturn(true);
            when(a1.getCachedLandTax()).thenReturn(0.0);

            Aft a2 = mock(Aft.class);
            when(a2.isInteract()).thenReturn(true);
            when(a2.getCachedLandTax()).thenReturn(0.0);

            RegionalModelRunner r = mock(RegionalModelRunner.class);
            when(r.getServiceTax()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 1.0)));
            when(r.getMarginal()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 0.0)));

            // utility = 1 * productivity
            doReturn(1.0).when(c).productivity(a1, "S1");
            doReturn(10.0).when(c).productivity(a2, "S1");

            Aft best = Competitiveness.mostCompetitiveAgent(c, List.of(a1, a2), r);
            assertSame(a2, best);
        });
    }

    // -------------------------------------------------------
    // competition(...)
    // -------------------------------------------------------

    @Test
    void competition_takesOver_whenOwnerNull_andCompetitorAboveMean_nonNormalisedPath() throws Throwable {
        withServices(List.of("S1"), () -> {
            // config: force "most competitor" branch, no neighbor priority, no mutation
            Object cfg = ensureConfigInstance();
            setConfig(cfg,
                    Map.of(
                            "use_neighbor_priority", false,
                            "MostCompetitorAFTProbability", 1.0,
                            "mutate_on_competition_win", false
                    )
            );

            // categorisation OFF so landUsechange() is used
            AftCategorised.useCategorisationGivIn = false;
            CellBehaviourUpdater.behaviourUsed = false;

            Cell c = Mockito.spy(new Cell(0, 0));
            setOwner(c, null);

            Aft competitor = mock(Aft.class);
            when(competitor.isInteract()).thenReturn(true);
            when(competitor.getCachedLandTax()).thenReturn(0.0);

            RegionalModelRunner r = mock(RegionalModelRunner.class);
            when(r.getServiceTax()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 1.0)));
            when(r.getMarginal()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 0.0)));

            // competitor utility = 1 * 6 = 6
            doReturn(6.0).when(c).productivity(competitor, "S1");

            ConcurrentHashMap<Aft, Double> meanY = new ConcurrentHashMap<>();
            meanY.put(competitor, 5.0);
            when(r.getDistributionMeanY()).thenReturn(meanY);

            try (MockedStatic<AFTsLoader> mocked = Mockito.mockStatic(AFTsLoader.class)) {
            	ConcurrentHashMap<String, Aft> active = new ConcurrentHashMap<>();
                active.put("comp", competitor);
                mocked.when(AFTsLoader::getActivateAFTsHash).thenReturn(active);
                Competitiveness.competition(c, r);

                assertSame(competitor, getOwner(c));
                assertEquals(1, Listener.landUseChangeCounter.get());
            }
        });
    }

    @Test
    void competition_isBlockedByMaskRestriction_whenOwnerNull() throws Throwable {
        withServices(List.of("S1"), () -> {
            Object cfg = ensureConfigInstance();
            setConfig(cfg,
                    Map.of(
                            "use_neighbor_priority", false,
                            "MostCompetitorAFTProbability", 1.0,
                            "mutate_on_competition_win", false
                    )
            );

            AftCategorised.useCategorisationGivIn = false;
            CellBehaviourUpdater.behaviourUsed = false;

            Cell c = Mockito.spy(new Cell(0, 0));
            setOwner(c, null);

            // set mask type
            setField(c, "maskType", "mask1");

            Aft competitor = mock(Aft.class);
            when(competitor.isInteract()).thenReturn(true);
            when(competitor.getCachedLandTax()).thenReturn(0.0);
            when(competitor.getLabel()).thenReturn("FARM");

            // restrictions: for unmanaged -> competitor takeover key is "FARM_FARM"
            LandMaskUpdater.restrictions.put("mask1", new ConcurrentHashMap<>(Map.of("FARM_FARM", false)));

            RegionalModelRunner r = mock(RegionalModelRunner.class);
            when(r.getServiceTax()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 1.0)));
            when(r.getMarginal()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 0.0)));

            doReturn(100.0).when(c).productivity(competitor, "S1");

            ConcurrentHashMap<Aft, Double> meanY = new ConcurrentHashMap<>();
            meanY.put(competitor, 0.0);
            when(r.getDistributionMeanY()).thenReturn(meanY);

            try (MockedStatic<AFTsLoader> mocked = Mockito.mockStatic(AFTsLoader.class)) {
                mocked.when(AFTsLoader::getActivateAFTsHash).thenReturn(new ConcurrentHashMap<>(Map.of("comp", competitor)));

                Competitiveness.competition(c, r);

                assertNull(getOwner(c), "Owner must stay null because mask blocks competition");
                assertEquals(0, Listener.landUseChangeCounter.get());
            }
        });
    }

    @Test
    void competition_takesOver_whenOwnerExists_andUtilityDifferenceExceedsThreshold_nonNormalisedPath() throws Throwable {
        withServices(List.of("S1"), () -> {
            Object cfg = ensureConfigInstance();
            setConfig(cfg,
                    Map.of(
                            "use_neighbor_priority", false,
                            "MostCompetitorAFTProbability", 0.0, // force random-AFT branch
                            "mutate_on_competition_win", false
                    )
            );

            AftCategorised.useCategorisationGivIn = false;
            CellBehaviourUpdater.behaviourUsed = false;

            Cell c = Mockito.spy(new Cell(0, 0));

            Aft owner = mock(Aft.class);
            when(owner.isInteract()).thenReturn(true);
            when(owner.isAbandoned()).thenReturn(false);
            when(owner.getGiveInMean()).thenReturn(1.0);
            when(owner.getGiveInSD()).thenReturn(0.0); // remove randomness
            setOwner(c, owner);

            // set current utility uO=1
            c.setcCurrentUtility(1.0);

            Aft competitor = mock(Aft.class);
            when(competitor.isInteract()).thenReturn(true);
            when(competitor.getCachedLandTax()).thenReturn(0.0);

            RegionalModelRunner r = mock(RegionalModelRunner.class);
            when(r.getServiceTax()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 1.0)));
            when(r.getMarginal()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 0.0)));

            // competitor uC = 10
            doReturn(10.0).when(c).productivity(competitor, "S1");

            // nbr = mean(owner) * giveInThreshold = 2 * 1 = 2
            ConcurrentHashMap<Aft, Double> meanY = new ConcurrentHashMap<>();
            meanY.put(owner, 2.0);
            when(r.getDistributionMeanY()).thenReturn(meanY);

            try (MockedStatic<AFTsLoader> mocked = Mockito.mockStatic(AFTsLoader.class)) {
                mocked.when(AFTsLoader::getActivateAFTsHash).thenReturn(new ConcurrentHashMap<>(Map.of("comp", competitor)));
                mocked.when(() -> AFTsLoader.getRandomAFT(any())).thenReturn(competitor);

                Competitiveness.competition(c, r);

                assertSame(competitor, getOwner(c));
                assertEquals(1, Listener.landUseChangeCounter.get());
            }
        });
    }

    @Test
    void competition_takesOver_inNormalisedUtilityPath_whenOwnerNull_andUCPositive() throws Throwable {
        withServices(List.of("S1"), () -> {
            Object cfg = ensureConfigInstance();
            setConfig(cfg,
                    Map.of(
                            "use_neighbor_priority", false,
                            "MostCompetitorAFTProbability", 0.0, // random
                            "mutate_on_competition_win", false
                    )
            );

            // force normalised path
            AftCategorised.useCategorisationGivIn = true;
            CellBehaviourUpdater.behaviourUsed = true;

            Cell c = Mockito.spy(new Cell(0, 0));
            setOwner(c, null);

            Aft competitor = mock(Aft.class);
            when(competitor.isInteract()).thenReturn(true);
            when(competitor.getCachedLandTax()).thenReturn(0.0);

            RegionalModelRunner r = mock(RegionalModelRunner.class);
            when(r.getServiceTax()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 1.0)));
            when(r.getMarginal()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 0.0)));
            when(r.getMinUtility()).thenReturn(0.0);
            when(r.getMaxUtility()).thenReturn(10.0);

            // raw utility = 5 => normalised 0.5 > 0 => takeover for owner null
            doReturn(5.0).when(c).productivity(competitor, "S1");

            try (MockedStatic<AFTsLoader> mocked = Mockito.mockStatic(AFTsLoader.class)) {
                mocked.when(AFTsLoader::getActivateAFTsHash).thenReturn(new ConcurrentHashMap<>(Map.of("comp", competitor)));
                mocked.when(() -> AFTsLoader.getRandomAFT(any())).thenReturn(competitor);

                Competitiveness.competition(c, r);

                assertSame(competitor, getOwner(c));
                assertEquals(1, Listener.landUseChangeCounter.get());
            }
        });
    }

    // -------------------------------------------------------
    // giveInThreshold(...) (private) via reflection
    // -------------------------------------------------------

    @Test
    void giveInThreshold_usesCategorisedMeanSd_whenPresent() throws Throwable {
        AftCategorised.useCategorisationGivIn = true;

        Aft owner = mock(Aft.class);
        Aft competitor = mock(Aft.class);

        AftCategory ownerCat = mock(AftCategory.class);
        when(ownerCat.getName()).thenReturn("A");
        when(owner.getCategory()).thenReturn(ownerCat);

        AftCategory compCat = mock(AftCategory.class);
        when(compCat.getName()).thenReturn("B");
        when(competitor.getCategory()).thenReturn(compCat);

        // sd=0 removes randomness from ThreadLocalRandom gaussian
        AftCategorised.getMean().put("A|B", 0.25);
        AftCategorised.getSD().put("A|B", 0.0);

        Method m = Competitiveness.class.getDeclaredMethod("giveInThreshold", Aft.class, Aft.class);
        m.setAccessible(true);

        double v = (double) m.invoke(null, owner, competitor);
        assertEquals(0.25, v, 1e-12);
    }

    // =========================================================
    // Helpers
    // =========================================================

    private static void withServices(List<String> services, ExecutableThrowing body) throws Throwable {
        List<String> provided = new ArrayList<>(services);

        try {
            // try mutate real list
            List<String> real = ServiceSet.getServicesList();
            List<String> backup = new ArrayList<>(real);
            try {
                real.clear();
                real.addAll(provided);
                body.run();
            } finally {
                real.clear();
                real.addAll(backup);
            }
        } catch (UnsupportedOperationException ex) {
            // fallback to static mocking
            try (MockedStatic<ServiceSet> mocked = Mockito.mockStatic(ServiceSet.class)) {
                mocked.when(ServiceSet::getServicesList).thenReturn(provided);
                body.run();
            }
        }
    }

    @FunctionalInterface
    private interface ExecutableThrowing {
        void run() throws Throwable;
    }

    private Object ensureConfigInstance() throws Exception {
        Field cfgField = findField(ConfigLoader.class, "config");
        cfgField.setAccessible(true);
        Object cfg = cfgField.get(null);
        if (cfg == null) {
            cfg = cfgField.getType().getDeclaredConstructor().newInstance();
            cfgField.set(null, cfg);
        }
        return cfg;
    }

    private static void setConfig(Object cfg, Map<String, Object> values) {
        values.forEach((k, v) -> setField(cfg, k, v));
    }

    private static void setOwner(Cell c, Aft owner) {
        // Prefer a setter if present, else set field.
        try {
            Method m = findMethod(c.getClass(), "setOwner", Aft.class);
            if (m != null) {
                m.setAccessible(true);
                m.invoke(c, owner);
                return;
            }
        } catch (Exception ignored) {}
        setField(c, "owner", owner);
    }

    private static Aft getOwner(Cell c) {
        try {
            Method m = findMethod(c.getClass(), "getOwner");
            if (m != null) {
                m.setAccessible(true);
                return (Aft) m.invoke(c);
            }
        } catch (Exception ignored) {}
        return (Aft) getField(c, "owner");
    }

    private static Map<String, ConcurrentHashMap<String, Boolean>> deepCopyRestrictions(
            Map<String, ConcurrentHashMap<String, Boolean>> src) {
        Map<String, ConcurrentHashMap<String, Boolean>> copy = new HashMap<>();
        for (var e : src.entrySet()) {
            copy.put(e.getKey(), e.getValue() == null ? null : new ConcurrentHashMap<>(e.getValue()));
        }
        return copy;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        Class<?> c = type;
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
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

    private static Object getField(Object target, String fieldName) {
        try {
            Field f = findField(target.getClass(), fieldName);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read field '" + fieldName + "'", e);
        }
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
}
