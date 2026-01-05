package de.cesr.crafty.core.utils.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SplitByRegionsTest {

    @BeforeEach
    void resetCountryToGroupMap() {
        // Ensure a clean state before each test
        SplitByRegions.countryToG.clear();
    }

    @Test
    void groupsShouldInitializeCountryMappingCorrectly()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        // Call the private static method groups() using reflection
        Method groupsMethod = SplitByRegions.class.getDeclaredMethod("groups");
        groupsMethod.setAccessible(true);
        groupsMethod.invoke(null); // static method → no instance

        // Basic sanity: map should not be empty
        assertFalse(SplitByRegions.countryToG.isEmpty(), "countryToG should not be empty after groups()");

        // Check a few representative examples for each group
        assertEquals("Northern", SplitByRegions.countryToG.get("NO"));
        assertEquals("Northern", SplitByRegions.countryToG.get("SE"));
        assertEquals("Northern", SplitByRegions.countryToG.get("UK"));
        assertEquals("Western",  SplitByRegions.countryToG.get("FR"));
        assertEquals("Western",  SplitByRegions.countryToG.get("DE"));
        assertEquals("Southern", SplitByRegions.countryToG.get("ES"));
        assertEquals("Southern", SplitByRegions.countryToG.get("PT"));
        assertEquals("Estern",   SplitByRegions.countryToG.get("PL"));
        assertEquals("Estern",   SplitByRegions.countryToG.get("CZ"));

        // Country not in any group → null
        assertNull(SplitByRegions.countryToG.get("EL"),
                "EL is currently commented out in the Estern group, should not be mapped");
        assertNull(SplitByRegions.countryToG.get("XX"),
                "Unknown country codes should not be mapped");
    }

    @Test
    void groupArrayShouldContainExpectedGroupNamesInOrder() {
        String[] expected = { "Northern", "Western", "Southern", "Estern" };
        assertArrayEquals(expected, SplitByRegions.G,
                "Group names or order changed unexpectedly");
    }

    // --- Placeholders for future tests (once environment is easier to mock) ---

    // @Test
    // void mapsSpliterShouldCreateOutputFoldersAndCopyRestrictionFiles() {
    // }

    // @Test
    // void cutByRegionShouldSplitCsvRowsByGroup() {
    // }
}
