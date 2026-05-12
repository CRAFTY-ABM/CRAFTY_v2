package de.cesr.crafty.core.updaters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import de.cesr.crafty.core.cli.Config;
import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.crafty.CellBehaviour;
import de.cesr.crafty.core.dataLoader.CsvProcessors;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.afts.AftCategorised;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.utils.file.PathTools;

class CellBehaviourUpdaterTest {

    @BeforeEach
    void resetStaticState() {
        // reset updater static state
        CellBehaviourUpdater.behaviourUsed = false;
        CellBehaviourUpdater.cellsBehevoir.clear();

        // ensure CellsLoader.hashCell exists & is empty
        if (CellsLoader.hashCell == null) {
            CellsLoader.hashCell = new ConcurrentHashMap<>();
        } else {
            CellsLoader.hashCell.clear();
        }

        // default: categorisation off
        AftCategorised.useCategorisationGivIn = false;
        if (ConfigLoader.config == null) {
			ConfigLoader.config = new Config();
		}
    }

    @Test
    void constructor_whenCategorisationOff_doesNothing() {
        AftCategorised.useCategorisationGivIn = false;

        new CellBehaviourUpdater();

        assertFalse(CellBehaviourUpdater.behaviourUsed, "behaviourUsed should stay false");
        assertTrue(CellBehaviourUpdater.cellsBehevoir.isEmpty(), "cellsBehevoir should stay empty");
    }

    @Test
    void constructor_whenCategorisationOn_andFileExists_populatesMap() {
        AftCategorised.useCategorisationGivIn = true;

        // Prepare 2 rows CSV
        Map<String, List<String>> csv = new HashMap<>();
        csv.put("X", List.of("1", "3"));
        csv.put("Y", List.of("2", "4"));
        csv.put("Attitude_intensification", List.of("0.1", "0.9"));
        csv.put("Weight_inertia", List.of("0.2", "0.8"));
        csv.put("Weight-social", List.of("0.3", "0.7"));
        csv.put("Critical_mass", List.of("0.4", "0.6"));
        csv.put("Neighborhood_size", List.of("5", "9"));
        csv.put("MaxGive_in", List.of("0.5", "0.55"));

        // Put cells in CellsLoader map for coords "1,2" and "3,4"
        Cell c12 = mock(Cell.class);
        Cell c34 = mock(Cell.class);
        CellsLoader.hashCell.put("1,2", c12);
        CellsLoader.hashCell.put("3,4", c34);

        Path dummyFolder = Path.of("dummy/behaviour");
        Path dummyFile = Path.of("dummy/behaviour/Cell_behaviour_parameters2000.csv");
        ArrayList<Path> dummyFiles = new ArrayList<>(List.of(dummyFile));

        try (MockedStatic<ProjectLoader> projectLoader = Mockito.mockStatic(ProjectLoader.class);
             MockedStatic<Timestep> timestep = Mockito.mockStatic(Timestep.class);
             MockedStatic<PathTools> pathTools = Mockito.mockStatic(PathTools.class);
             MockedStatic<CsvProcessors> csvProcessors = Mockito.mockStatic(CsvProcessors.class)) {

            projectLoader.when(ProjectLoader::getScenario).thenReturn("ssp126");

            timestep.when(Timestep::getStartYear).thenReturn(2000);
            timestep.when(Timestep::getCurrentYear).thenReturn(2000);

            pathTools.when(() -> PathTools.asFolder("behaviour")).thenReturn(dummyFolder.toString());

            // constructor uses startYear, step uses currentYear (same here)
            pathTools.when(() -> PathTools.fileFilter(any(String.class), anyString(),
                    eq("Cell_behaviour_parameters"), eq("2000.csv")))
                    .thenReturn(dummyFiles);

            csvProcessors.when(() -> CsvProcessors.ReadAsaHash(dummyFile)).thenReturn(csv);

            new CellBehaviourUpdater();

            assertTrue(CellBehaviourUpdater.behaviourUsed, "behaviourUsed should be true when file is found");
            assertEquals(2, CellBehaviourUpdater.cellsBehevoir.size(), "Should create behaviour for two rows");

            CellBehaviour b12 = CellBehaviourUpdater.cellsBehevoir.get(c12);
            CellBehaviour b34 = CellBehaviourUpdater.cellsBehevoir.get(c34);

            assertNotNull(b12);
            assertNotNull(b34);

            // Robust assertions via reflection (works even if getters differ)
            assertDoubleProperty(b12, 0.1, "attitude_intensification", "attitudeIntensification");
            assertDoubleProperty(b12, 0.2, "weight_inertia", "weightInertia");
            assertDoubleProperty(b12, 0.3, "weight_social", "weightSocial");
            assertDoubleProperty(b12, 0.4, "critical_mass", "criticalMass");
            assertIntProperty(b12, 5, "neighborhood_size", "neighborhoodSize");
            assertDoubleProperty(b12, 0.5, "maxGive_in", "maxGiveIn");
        }
    }

    @Test
    void step_whenBehaviourUsedFalse_doesNothing() {
        CellBehaviourUpdater.behaviourUsed = false;

        new CellBehaviourUpdater().step();

        assertTrue(CellBehaviourUpdater.cellsBehevoir.isEmpty());
    }

    @Test
    void step_whenFileFilterReturnsNull_doesNothing() {
        CellBehaviourUpdater.behaviourUsed = true;
        AftCategorised.useCategorisationGivIn = true;

        try (MockedStatic<ProjectLoader> projectLoader = Mockito.mockStatic(ProjectLoader.class);
             MockedStatic<Timestep> timestep = Mockito.mockStatic(Timestep.class);
             MockedStatic<PathTools> pathTools = Mockito.mockStatic(PathTools.class)) {

            projectLoader.when(ProjectLoader::getScenario).thenReturn("ssp126");
            timestep.when(Timestep::getCurrentYear).thenReturn(2000);
            pathTools.when(() -> PathTools.asFolder("behaviour")).thenReturn("dummy/behaviour");

            pathTools.when(() -> PathTools.fileFilter(any(String.class), anyString(),
                    eq("Cell_behaviour_parameters"), eq("2000.csv")))
                    .thenReturn(null);

            new CellBehaviourUpdater().step();

            assertTrue(CellBehaviourUpdater.cellsBehevoir.isEmpty());
        }
    }


    // ---------------- helpers: tolerant reflection-based property checks ----------------

    private static void assertDoubleProperty(Object obj, double expected, String... baseNames) {
        Double val = readDouble(obj, baseNames);
        assertNotNull(val, "Could not read double property for: " + Arrays.toString(baseNames));
        assertEquals(expected, val, 1e-9);
    }

    private static void assertIntProperty(Object obj, int expected, String... baseNames) {
        Integer val = readInt(obj, baseNames);
        assertNotNull(val, "Could not read int property for: " + Arrays.toString(baseNames));
        assertEquals(expected, val);
    }

    private static Double readDouble(Object obj, String... baseNames) {
        Object v = readProperty(obj, baseNames);
        if (v instanceof Number n) return n.doubleValue();
        return null;
    }

    private static Integer readInt(Object obj, String... baseNames) {
        Object v = readProperty(obj, baseNames);
        if (v instanceof Number n) return n.intValue();
        return null;
    }

    private static Object readProperty(Object obj, String... baseNames) {
        // try fields first
        for (String base : baseNames) {
            for (String candidate : fieldCandidates(base)) {
                try {
                    Field f = obj.getClass().getDeclaredField(candidate);
                    f.setAccessible(true);
                    return f.get(obj);
                } catch (NoSuchFieldException ignored) {
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        // then try getters
        for (String base : baseNames) {
            for (String candidate : getterCandidates(base)) {
                try {
                    Method m = obj.getClass().getMethod(candidate);
                    return m.invoke(obj);
                } catch (NoSuchMethodException ignored) {
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return null;
    }

    private static List<String> fieldCandidates(String base) {
        // allow snake_case and camelCase variants
        List<String> out = new ArrayList<>();
        out.add(base);
        out.add(toCamel(base));
        return out;
    }

    private static List<String> getterCandidates(String base) {
        List<String> out = new ArrayList<>();
        String camel = toCamel(base);
        out.add("get" + capitalize(base));
        out.add("get" + capitalize(camel));
        // sometimes boolean-style or direct method name exists
        out.add(base);
        out.add(camel);
        return out;
    }

    private static String toCamel(String s) {
        StringBuilder b = new StringBuilder();
        boolean up = false;
        for (char c : s.toCharArray()) {
            if (c == '_' || c == '-') { up = true; continue; }
            b.append(up ? Character.toUpperCase(c) : c);
            up = false;
        }
        return b.toString();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
