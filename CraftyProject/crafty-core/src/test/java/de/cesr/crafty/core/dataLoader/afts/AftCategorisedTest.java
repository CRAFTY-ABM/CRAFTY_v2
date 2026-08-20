package de.cesr.crafty.core.dataLoader.afts;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.AftCategory;
import de.cesr.crafty.core.ToyData;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.cli.ConfigLoader;

public class AftCategorisedTest {

	@TempDir
	Path projectDir;

	@BeforeEach
	void resetStaticState() {
		ToyData toy = new ToyData();
		toy.resetStaticState(projectDir);
	}
    @AfterEach
    void tearDown() {
    	CustomLogger.shutdownRunFileLoggers();
    }

	@Test
	void categoriesLoader_populatesCategoriesFromMetadata() throws IOException {

		Aft aft1 = AFTsLoader.getAftHash().get("AFT1");
		Aft aft2 = AFTsLoader.getAftHash().get("AFT2");
		Aft aft3 = AFTsLoader.getAftHash().get("AFT3");

		// categories have been created and populated
		Map<String, Set<Aft>> categories = AftCategorised.aftCategories;
		Map<String, Set<String>> intensities = AftCategorised.CategoriesIntestisy;
		assertEquals(3, categories.size(), "Expected 3 categories (Pasture, Forest,Crop)");
		assertTrue(categories.containsKey("Forest"));
		assertTrue(categories.containsKey("Pasture"));
		// Forest should contain AFT1 and AFT2
		Set<Aft> forestAfts = categories.get("Forest");
		assertEquals(2, forestAfts.size());
		assertTrue(forestAfts.contains(aft1));
		assertTrue(forestAfts.contains(aft2));

		// Agriculture should contain AFT3
		Set<Aft> agriAfts = categories.get("Pasture");
		assertEquals(1, agriAfts.size());
		assertTrue(agriAfts.contains(aft3));

		// Check that categories and intensities are set on the Aft objects
		AftCategory c1 = aft1.getCategory();
		assertNotNull(c1);
		assertEquals("Forest", c1.getName());
		assertEquals("Low", c1.getIntensity());
		assertEquals(1, c1.getIntensityLevel());

		AftCategory c2 = aft2.getCategory();
		assertEquals("Medium", c2.getIntensity());
		assertEquals(2, c2.getIntensityLevel());

		AftCategory c3 = aft3.getCategory();
		assertEquals("Pasture", c3.getName());
		assertEquals("High", c3.getIntensity());
		assertEquals(3, c3.getIntensityLevel());

		// CategoriesIntestisy should contain intensity names per category
		assertTrue(intensities.get("Forest").contains("Low"));
		assertTrue(intensities.get("Forest").contains("Medium"));
		assertTrue(intensities.get("Pasture").contains("High"));
	}

	@Test
	void categoriesLoader_whenSwitchIsOff_clearsAndDisablesCategories() {
		ConfigLoader.config.use_category_based_give_in = false;

		AftCategorised.CategoriesLoader();
		AftCategorised.initializeBehaviourByCategories();

		assertTrue(AftCategorised.aftCategories.isEmpty());
		assertTrue(AftCategorised.CategoriesIntestisy.isEmpty());
		assertTrue(AftCategorised.categoriesColor.isEmpty());
		assertFalse(AftCategorised.useCategorisationGivIn);
		assertTrue(AftCategorised.getMean().isEmpty());
		assertTrue(AftCategorised.getSD().isEmpty());
	}

	@Test
	void categoryBehaviour_whenStandardDeviationMatrixIsMissing_staysDisabled() throws IOException {
		Path distributions = projectDir.resolve("category-distributions");
		Files.createDirectories(distributions);
		Files.writeString(distributions.resolve("categories_givingInDistributionMean_Default.csv"), "A,B\nA,0.1\n");
		ConfigLoader.config.category_give_in_distributions_directory = distributions.toString();
		AftCategorised.useCategorisationGivIn = true;

		AftCategorised.initializeBehaviourByCategories();

		assertFalse(AftCategorised.useCategorisationGivIn);
		assertTrue(AftCategorised.getMean().isEmpty());
		assertTrue(AftCategorised.getSD().isEmpty());
	}

 	@Test
	void concurrentAccessToCategoryMapsIsSafe() throws InterruptedException {
		// This method doesn't start threads itself, but we want to check that
		// the public static ConcurrentHashMaps behave correctly when used
		// from multiple threads.

		int threads = 10;
		int perThreadCategories = 50;
		CountDownLatch latch = new CountDownLatch(threads);
		AftCategorised.aftCategories.clear();
		AftCategorised.CategoriesIntestisy.clear();
		AftCategorised.categoriesColor.clear();
		Runnable task = () -> {
			for (int i = 0; i < perThreadCategories; i++) {
				String categoryName = "Cat-" + Thread.currentThread().getId() + "-" + i;
				AftCategorised.aftCategories.putIfAbsent(categoryName, ConcurrentHashMap.newKeySet());
				AftCategorised.CategoriesIntestisy.putIfAbsent(categoryName, ConcurrentHashMap.newKeySet());
				AftCategorised.categoriesColor.putIfAbsent(categoryName, "#000000");
			}
			latch.countDown();
		};

		// Start several threads that mutate the maps
		for (int t = 0; t < threads; t++) {
			new Thread(task).start();
		}

		latch.await(); // wait for all threads to finish

		// If the maps were not concurrent, we may see exceptions or a size < expected.
		int expected = threads * perThreadCategories;
		assertEquals(expected, AftCategorised.aftCategories.size(),
				"All categories from all threads should be present in aftCategories");
		assertEquals(expected, AftCategorised.CategoriesIntestisy.size(),
				"All categories from all threads should be present in CategoriesIntestisy");
		assertEquals(expected, AftCategorised.categoriesColor.size(),
				"All categories from all threads should be present in categoriesColor");
	}
}
