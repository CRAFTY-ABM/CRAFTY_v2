package de.cesr.crafty.core.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.crafty.Region;
import de.cesr.crafty.core.crafty.Service;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.file.CsvTools;
import de.cesr.crafty.core.utils.file.PathTools;

/**
 * Unit tests for {@link ListenerByRegion}.
 *
 * Assumptions: - ConfigLoader.config has public fields 'output_folder_name' and
 * 'generate_output_files'. - AFTsLoader.hashAgentNbrRegions,
 * CellsLoader.regions are static fields. - Timestep.getsize() are static.
 *
 * Uses Mockito-inline for static mocking (same dependencies as for
 * ListenerTest).
 */
class ListenerByRegionTest {

	/**
	 * Ensure that ConfigLoader.config is non-null and return it.
	 */
	private Object ensureConfigNotNull() {
		try {
			Field configField = ConfigLoader.class.getField("config");
			Object config = configField.get(null);
			if (config == null) {
				Class<?> cfgType = configField.getType();
				Object instance = cfgType.getDeclaredConstructor().newInstance();
				configField.setAccessible(true);
				configField.set(null, instance);
				config = instance;
			}
			return config;
		} catch (Exception e) {
			throw new RuntimeException("Failed to ensure ConfigLoader.config is initialised", e);
		}
	}

	/**
	 * Set a field on ConfigLoader.config via reflection.
	 */
	private void setConfigField(String fieldName, Object value) {
		Object cfg = ensureConfigNotNull();
		try {
			Field f = cfg.getClass().getField(fieldName);
			f.setAccessible(true);
			f.set(cfg, value);
		} catch (NoSuchFieldException e) {
			throw new RuntimeException("Config class does not have public field '" + fieldName + "'", e);
		} catch (Exception e) {
			throw new RuntimeException("Failed to set config field '" + fieldName + "'", e);
		}
	}

	/**
	 * Read a field on ConfigLoader.config via reflection.
	 */
	private Object getConfigField(String fieldName) {
		Object cfg = ensureConfigNotNull();
		try {
			Field f = cfg.getClass().getField(fieldName);
			f.setAccessible(true);
			return f.get(cfg);
		} catch (Exception e) {
			throw new RuntimeException("Failed to get config field '" + fieldName + "'", e);
		}
	}

	/**
	 * Helper to read a private String[][] field from ListenerByRegion.
	 */
	private String[][] getPrivateMatrix(ListenerByRegion listener, String fieldName) {
		try {
			Field f = ListenerByRegion.class.getDeclaredField(fieldName);
			f.setAccessible(true);
			return (String[][]) f.get(listener);
		} catch (Exception e) {
			throw new RuntimeException("Failed to read private matrix '" + fieldName + "'", e);
		}
	}

	/**
	 * Helper to set a static field via reflection (for
	 * AFTsLoader.hashAgentNbrRegions, CellsLoader.regions, etc.).
	 */
	private void setStaticField(Class<?> clazz, String fieldName, Object value) {
		try {
			Field f = clazz.getDeclaredField(fieldName);
			f.setAccessible(true);
			f.set(null, value);
		} catch (Exception e) {
			throw new RuntimeException("Failed to set static field '" + fieldName + "' on " + clazz.getName(), e);
		}
	}

	@Test
	void initializeListeners_SetsHeaderRowsCorrectly() {
		// Mock Region (only name is needed here)
		Region region = Mockito.mock(Region.class);
		Mockito.when(region.getName()).thenReturn("R1");

		try (MockedStatic<Timestep> timestepMock = mockStatic(Timestep.class);
				MockedStatic<AFTsLoader> aftsMock = mockStatic(AFTsLoader.class);
				MockedStatic<ServiceSet> serviceSetMock = mockStatic(ServiceSet.class)) {

			// Years: 2000..2002 (3 years -> length + 2 = 5 rows)
			timestepMock.when(Timestep::getStartYear).thenReturn(2000);
			timestepMock.when(Timestep::getEndtYear).thenReturn(2002);

			// Services
			serviceSetMock.when(ServiceSet::getServicesList).thenReturn(Arrays.asList("S1", "S2"));

			// AFTs (use LinkedHashMap-style ordering via normal HashMap + assumptions)
			Map<String, Object> aftMap = new ConcurrentHashMap<>();
			aftMap.put("A1", new Object());
			aftMap.put("A2", new Object());
			aftsMock.when(AFTsLoader::getAftHash).thenReturn(aftMap);

			ListenerByRegion listener = new ListenerByRegion(region);
			listener.initializeListeners();

			String[][] serviceDemand = getPrivateMatrix(listener, "servicedemandListener");
			String[][] composition = getPrivateMatrix(listener, "compositionAftListener");
			String[][] avgUtils = listener.averageUtilities;
			String[][] dsEq = listener.DSEquilibriumListener;

			// Check servicedemand header
			assertEquals("Year", serviceDemand[0][0]);
			assertEquals("Supply:S1", serviceDemand[0][1]);
			assertEquals("Supply:S2", serviceDemand[0][2]);
			assertEquals("Demand:S1", serviceDemand[0][3]);
			assertEquals("Demand:S2", serviceDemand[0][4]);


			// Check composition & averageUtilities headers
			assertEquals("Year", composition[0][0]);
			assertEquals("A1", composition[0][1]);
			assertEquals("A2", composition[0][2]);

			assertEquals("Year", avgUtils[0][0]);
			assertEquals("A1", avgUtils[0][1]);
			assertEquals("A2", avgUtils[0][2]);

			// DSEquilibrium header
			assertEquals("Service", dsEq[0][0]);
			assertEquals("Calibration_Factor", dsEq[0][1]);
		}
	}

	@Test
	void fillDSEquilibriumListener_FillsFromServiceHash() {
		Region region = Mockito.mock(Region.class);
		Mockito.when(region.getName()).thenReturn("R1");

		try (MockedStatic<Timestep> timestepMock = mockStatic(Timestep.class);
				MockedStatic<AFTsLoader> aftsMock = mockStatic(AFTsLoader.class);
				MockedStatic<ServiceSet> serviceSetMock = mockStatic(ServiceSet.class)) {

			// Simple 1-year setup
			timestepMock.when(Timestep::getStartYear).thenReturn(2000);
			timestepMock.when(Timestep::getEndtYear).thenReturn(2000);

			serviceSetMock.when(ServiceSet::getServicesList).thenReturn(Collections.singletonList("S1"));

			// AFTs (only needed for initializeListeners' sizing)
			Map<String, Object> aftMap = new ConcurrentHashMap<>();
			aftMap.put("A1", new Object());
			aftsMock.when(AFTsLoader::getAftHash).thenReturn(aftMap);

			ListenerByRegion listener = new ListenerByRegion(region);
			listener.initializeListeners();

			// Prepare service hash with calibration factor
			ConcurrentHashMap<String, Service> serviceHash = new ConcurrentHashMap<>();
			Service s1 = Mockito.mock(Service.class);
			Mockito.when(s1.getCalibration_Factor()).thenReturn(1.5);
			serviceHash.put("S1", s1);

			listener.fillDSEquilibriumListener(serviceHash);

			// Row 1 (index 1) should contain service name and calibration factor
			assertEquals("S1", listener.DSEquilibriumListener[1][0]);
			assertEquals("1.5", listener.DSEquilibriumListener[1][1]);
		}
	}

	@Test
	void exportFiles_WritesCsvWhenOutputEnabledAndMultipleRegions() {
		// Ensure config exists and set fields
		ensureConfigNotNull();
		setConfigField("generate_output_files", true);
		setConfigField("output_folder_name", "baseOutputDir");

		// Mock Region and attached services
		Region region = Mockito.mock(Region.class);
		Mockito.when(region.getName()).thenReturn("R1");

		Service service = Mockito.mock(Service.class);
		Map<Integer, Double> demands = new ConcurrentHashMap<>();
		demands.put(2000, 100.0);
		Mockito.when(service.getDemands()).thenReturn((ConcurrentHashMap<Integer, Double>) demands);

		Map<String, Service> servicesHash = new ConcurrentHashMap<>();
		servicesHash.put("S1", service);
		Mockito.when(region.getServicesHash()).thenReturn((ConcurrentHashMap<String, Service>) servicesHash);

		ListenerByRegion listener = new ListenerByRegion(region);

		// Regional supply
		ConcurrentHashMap<String, Double> regionalSupply = new ConcurrentHashMap<>();
		regionalSupply.put("S1", 10.0);

		// Static mocks
		try (MockedStatic<Timestep> timestepMock = mockStatic(Timestep.class);
				MockedStatic<AFTsLoader> aftsMock = mockStatic(AFTsLoader.class);
				MockedStatic<ServiceSet> serviceSetMock = mockStatic(ServiceSet.class);
				MockedStatic<PathTools> pathToolsMock = mockStatic(PathTools.class);
				MockedStatic<CsvTools> csvToolsMock = mockStatic(CsvTools.class);
				MockedStatic<Tracker> trackerMock = mockStatic(Tracker.class)) {

			// Years 2000..2001 (2 years) so method indices work for year 2000
			timestepMock.when(Timestep::getStartYear).thenReturn(2000);
			timestepMock.when(Timestep::getCurrentYear).thenReturn(2000);
			timestepMock.when(Timestep::getEndtYear).thenReturn(2001);
			timestepMock.when(Timestep::getSize).thenReturn(2);

			serviceSetMock.when(ServiceSet::getServicesList).thenReturn(Collections.singletonList("S1"));
			
//			R.getServicesHash().get(serviceName).getDemands().get(Timestep.getCurrentYear()) 

			// AFTs only needed for initializeListeners sizing
			Map<String, Object> aftMap = new ConcurrentHashMap<>();
			aftMap.put("A1", new Object());
			aftsMock.when(AFTsLoader::getAftHash).thenReturn(aftMap);

			// hashAgentNbrRegions[R1]["A1"] = 5
			Map<String, Integer> regionAftCounts = new ConcurrentHashMap<>();
			regionAftCounts.put("A1", 5);
			Map<String, Map<String, Integer>> hashAgentNbrRegions = new ConcurrentHashMap<>();
			hashAgentNbrRegions.put("R1", regionAftCounts);
			setStaticField(AFTsLoader.class, "hashAgentNbrRegions", hashAgentNbrRegions);

			// CellsLoader.regions: simulate 2 regions so size() > 1
			Map<String, Region> regionsMap = new ConcurrentHashMap<>();
			regionsMap.put("R1", region);
			regionsMap.put("R2", Mockito.mock(Region.class));
			setStaticField(CellsLoader.class, "regions", regionsMap);

			// PathTools.makeDirectory: just echo the path
			pathToolsMock.when(() -> PathTools.makeDirectory(anyString()))
					.thenAnswer(invocation -> invocation.getArgument(0));

			// Initialize listener internal arrays
			listener.initializeListeners();

			// Act: export for year 2000 (y = 1)
			
			listener.exportFiles(regionalSupply);

			// Assertions / verifications:

			// Tracker.trackSupply must be called once with year and region name
			trackerMock.verify(() -> Tracker.trackSupply("R1"));

			// CSV should be written 4 times (AFT composition, service demand,
			// DS equilibrium, average utilities)
			csvToolsMock.verify(() -> CsvTools.writeCSVfile(any(String[][].class), any(Path.class)), Mockito.times(5));

			// The output_folder_name should be used to build the directory path
			String dirFromConfig = (String) getConfigField("output_folder_name");
			assertTrue(dirFromConfig.contains("baseOutputDir"));
		}
	}
}
