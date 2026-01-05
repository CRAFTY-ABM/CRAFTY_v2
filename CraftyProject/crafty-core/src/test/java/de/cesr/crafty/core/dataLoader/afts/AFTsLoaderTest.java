package de.cesr.crafty.core.dataLoader.afts;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cesr.crafty.core.ToyData;
import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.ManagerTypes;

class AFTsLoaderTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Prepare fake project + metadata in a fresh temp directory
        ToyData toyData = new ToyData();
        toyData.resetStaticState(tempDir);
    }

    @Test
    void constructor_initializesAftsFromMetaData_andAddsAbandoned() {

        Map<String, Aft> afts = AFTsLoader.getAftHash();

        // 3 from ToyData + 1 auto-added Abandoned = 4
        assertEquals(4, afts.size(), "Expected 3 metadata AFTs + auto 'Abandoned' AFT");

        // All labels from ToyData
        assertTrue(afts.containsKey("AFT1"));
        assertTrue(afts.containsKey("AFT2"));
        assertTrue(afts.containsKey("AFT3"));

        // Auto-added abandoned manager is always present
        assertTrue(afts.containsKey("Abandoned"),
                "Abandoned AFT should always be present");

        // Check some basic properties from CSV
        Aft aft1 = afts.get("AFT1");
        assertEquals(ManagerTypes.AFT, aft1.getType());

        Aft aft3 = afts.get("AFT3");
        assertEquals(ManagerTypes.AFT, aft3.getType());

        Aft abandoned = afts.get("Abandoned");
        assertEquals(ManagerTypes.Abandoned, abandoned.getType());
    }

    @Test
    void constructor_populatesActiveAftsHash() {
        // Act
        Map<String, Aft> active = AFTsLoader.getActivateAFTsHash();

        // Should not be empty, because default AFTs are interactive/active
        assertFalse(active.isEmpty(), "Active AFTs map should not be empty");
        assertTrue(active.keySet().contains("AFT1") ||
                   active.keySet().contains("AFT2") ||
                   active.keySet().contains("AFT3"),
                   "At least the normal AFTs should be active");
    }

    @Test
    void getRandomAFT_returnsOneOfActiveAfts() {
        // Act
        Aft random = AFTsLoader.getRandomAFT();

        // Assert
        assertNotNull(random, "Random AFT should not be null when active AFTs exist");
        assertTrue(
            AFTsLoader.getActivateAFTsHash().containsValue(random),
            "Random AFT must be one of the active AFTs"
        );
    }

    @Test
    void getRandomAFT_fromEmptyCollection_returnsNull() {
        // Act
        Aft random = AFTsLoader.getRandomAFT(new ArrayList<>());

        // Assert
        assertNull(random, "Random AFT from an empty collection should be null");
    }
}
