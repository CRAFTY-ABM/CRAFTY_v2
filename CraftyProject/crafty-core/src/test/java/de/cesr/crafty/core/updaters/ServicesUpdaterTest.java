package de.cesr.crafty.core.updaters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import de.cesr.crafty.core.cli.Config;
import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.crafty.Region;
import de.cesr.crafty.core.crafty.RegionalModelRunner;
import de.cesr.crafty.core.crafty.Service;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;

class ServicesUpdaterTest {

	@BeforeEach
	void resetStaticState() throws Exception {

		if (ConfigLoader.config == null) {
			ConfigLoader.config = new Config();
		}
		// Clear ServicesUpdater private static maps via reflection (to avoid test
		// pollution)
		clearStaticMap(ServicesUpdater.class, "demandByRegions");
		clearStaticMap(ServicesUpdater.class, "weightByRegions");
		clearStaticMap(ServicesUpdater.class, "taxesByRegions");
		clearStaticMap(ServicesUpdater.class, "supplys");
		clearStaticMap(ServicesUpdater.class, "gaps");

	}

	@Test
	void constructor_initializesSupplysAndGapsForAllRegionsAndServices() throws Exception {
		// Mock regions in CellsLoader.regions
		Object region1 = mockRegion("R1");
		Object region2 = mockRegion("R2");
		putIntoExternalMap(CellsLoader.class, "regions", "R1", region1);
		putIntoExternalMap(CellsLoader.class, "regions", "R2", region2);

		try (MockedStatic<ServiceSet> serviceSet = Mockito.mockStatic(ServiceSet.class)) {
			serviceSet.when(ServiceSet::getServicesList).thenReturn(List.of("Food", "Timber"));

			new ServicesUpdater();

			Map<?, ?> supplys = getStaticMap(ServicesUpdater.class, "supplys");
			Map<?, ?> gaps = getStaticMap(ServicesUpdater.class, "gaps");

			assertTrue(supplys.containsKey("R1"));
			assertTrue(supplys.containsKey("R2"));
			assertTrue(gaps.containsKey("R1"));
			assertTrue(gaps.containsKey("R2"));

			Map<?, ?> sR1 = (Map<?, ?>) supplys.get("R1");
			Map<?, ?> gR1 = (Map<?, ?>) gaps.get("R1");

			assertTrue(sR1.containsKey("Food"));
			assertTrue(sR1.containsKey("Timber"));
			assertTrue(gR1.containsKey("Food"));
			assertTrue(gR1.containsKey("Timber"));

			// each service map should be empty year->value at init
			assertTrue(((Map<?, ?>) sR1.get("Food")).isEmpty());
			assertTrue(((Map<?, ?>) gR1.get("Food")).isEmpty());
		}
	}

	@Test
	void step_populatesDemandWeightTaxesAndWritesSupplyAndGapByYear() throws Exception {
		// Year
		int year = 2000;

		Region region = new Region("R1");

		putIntoExternalMap(CellsLoader.class, "regions", "R1", region);

		// Service inside region.getServicesHash()
		Service service = mockService("Food", new ConcurrentHashMap<>(Map.of(year, 100.0)), // demands
				new ConcurrentHashMap<>(Map.of(year, 2.5)), // weights
				new ConcurrentHashMap<>(Map.of(year, -0.1)) // taxes/subsidies
		);

		region.getServicesHash().put(service.getName(), service);

		RegionalModelRunner regionRunner = new RegionalModelRunner(region.getName());
		regionRunner.setRegionalSupply(new ConcurrentHashMap<>(Map.of("Food", 80.0)));

		try (MockedStatic<ServiceSet> serviceSet = Mockito.mockStatic(ServiceSet.class);
				MockedStatic<Timestep> timestep = Mockito.mockStatic(Timestep.class)) {

			serviceSet.when(ServiceSet::getServicesList).thenReturn(List.of("Food"));
			timestep.when(Timestep::getCurrentYear).thenReturn(year);

			ServicesUpdater updater = new ServicesUpdater();
			RegionsModelRunnerUpdater.regionsModelRunner = new ConcurrentHashMap<>();
			RegionsModelRunnerUpdater.regionsModelRunner.put(region.getName(), regionRunner);

			updater.step();

			assertEquals(100.0, ServicesUpdater.getDemandByRegions().get("R1").get("Food"), 1e-9);
			assertEquals(2.5, ServicesUpdater.getWeightByRegions().get("R1").get("Food"), 1e-9);
			assertEquals(-0.1, ServicesUpdater.getTaxesByRegions().get("R1").get("Food"), 1e-9);

			Map<?, ?> supplys = getStaticMap(ServicesUpdater.class, "supplys");
			Map<?, ?> gaps = ServicesUpdater.getGaps();

			double su = (double) ((Map<?, ?>) ((Map<?, ?>) supplys.get("R1")).get("Food")).get(year);
			double gap = (double) ((Map<?, ?>) ((Map<?, ?>) gaps.get("R1")).get("Food")).get(year);

			assertEquals(80.0, su, 1e-9);
			assertEquals((100.0 - 80.0) / 100.0, gap, 1e-9);
		}
	}

	@Test
	void step_gapIsZeroWhenDemandIsZero() throws Exception {
		int year = 2000;

		Region region = new Region("R1");
		putIntoExternalMap(CellsLoader.class, "regions", "R1", region);
		RegionalModelRunner regionRunner = new RegionalModelRunner(region.getName());
		
		Service service = mockService("Food", new ConcurrentHashMap<>(Map.of(year, 0.0)), // demand = 0
				new ConcurrentHashMap<>(Map.of(year, 1.0)), new ConcurrentHashMap<>(Map.of(year, 0.0)));
		region.getServicesHash().put(service.getName(), service);

		regionRunner.setRegionalSupply(new ConcurrentHashMap<>(Map.of("Food", 80.0)));
		RegionsModelRunnerUpdater.regionsModelRunner = new ConcurrentHashMap<>();
		RegionsModelRunnerUpdater.regionsModelRunner.put(region.getName(), regionRunner);

		try (MockedStatic<ServiceSet> serviceSet = Mockito.mockStatic(ServiceSet.class);
				MockedStatic<Timestep> timestep = Mockito.mockStatic(Timestep.class)) {

			serviceSet.when(ServiceSet::getServicesList).thenReturn(List.of("Food"));
			timestep.when(Timestep::getCurrentYear).thenReturn(year);

			ServicesUpdater updater = new ServicesUpdater();
			updater.step();

			double gap = (double) ((Map<?, ?>) ((Map<?, ?>) ServicesUpdater.getGaps().get("R1")).get("Food")).get(year);
			assertEquals(0.0, gap, 1e-12);
		}
	}

    @Test
    void step_throwsNpeIfRegionHasServiceNotInitializedInConstructor_documentingBugRisk() throws Exception {
        int year = 2000;

        Region region = new Region("R1");
        putIntoExternalMap(CellsLoader.class, "regions", "R1", region);

        // Region has a service "Biodiversity" but ServiceSet returns only "Food"
        Service service = mockService("Biodiversity",
        		new ConcurrentHashMap<>(Map.of(year, 10.0)),
        		new ConcurrentHashMap<>(Map.of(year, 1.0)),
        		new ConcurrentHashMap<>(Map.of(year, 0.0))
        );
        region.getServicesHash().put(service.getName(), service);
        RegionalModelRunner regionRunner = new RegionalModelRunner(region.getName());
        regionRunner.setRegionalSupply(new ConcurrentHashMap<>(Map.of("Biodiversity", 5.0)));
        putIntoExternalMap(RegionsModelRunnerUpdater.class, "regionsModelRunner", "R1", regionRunner);

        try (MockedStatic<ServiceSet> serviceSet = Mockito.mockStatic(ServiceSet.class);
             MockedStatic<Timestep> timestep = Mockito.mockStatic(Timestep.class)) {

            serviceSet.when(ServiceSet::getServicesList).thenReturn(List.of("Food")); // does NOT include Biodiversity
            timestep.when(Timestep::getCurrentYear).thenReturn(year);

            ServicesUpdater updater = new ServicesUpdater();

            // Current code likely throws NPE here:
            // supplys.get("R1").get("Biodiversity") == null then .put(...)
            assertThrows(NullPointerException.class, updater::step);
        }
    }

    // ------------------------------------------------------------------------
    // Helper methods (reflection-based, to avoid depending on exact field visibility)
    // ------------------------------------------------------------------------

	@SuppressWarnings("unchecked")
	private static Map<Object, Object> getStaticMap(Class<?> cls, String fieldName) throws Exception {
		Field f = cls.getDeclaredField(fieldName);
		f.setAccessible(true);
		return (Map<Object, Object>) f.get(null);
	}

	private static void clearStaticMap(Class<?> cls, String fieldName) throws Exception {
		Map<Object, Object> m = getStaticMap(cls, fieldName);
		m.clear();
	}

	@SuppressWarnings("unchecked")
	private static void putIntoExternalMap(Class<?> cls, String fieldName, Object key, Object value) throws Exception {
		Field f = cls.getDeclaredField(fieldName);
		f.setAccessible(true);
		Map<Object, Object> map = (Map<Object, Object>) f.get(null);
		if (map == null) {
			map = new HashMap<>();
			f.set(null, map);
		}
		map.put(key, value);
	}

	// --------- Project-specific mocks (Region / Service / RegionRunner) ---------

	private static Region mockRegion(String name) throws Exception {
		Region region = mock(Region.class);
		when(Region.class.getMethod("getName").invoke(region)).thenReturn(name);
		return region;
	}

	private static Service mockService(String name, ConcurrentHashMap<Integer, Double> demands,
			ConcurrentHashMap<Integer, Double> weights, ConcurrentHashMap<Integer, Double> taxes) throws Exception {
		Service service = mock(Service.class);
		when(service.getName()).thenReturn(name);
		when(service.getDemands()).thenReturn(demands);
		when(service.getWeights()).thenReturn(weights);
		when(service.getTaxes_subsidies()).thenReturn(taxes);
		return service;
	}

}
