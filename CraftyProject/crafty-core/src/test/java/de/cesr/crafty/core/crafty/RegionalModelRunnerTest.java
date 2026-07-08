package de.cesr.crafty.core.crafty;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import de.cesr.crafty.core.ToyData;
import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.output.ListenerByRegion;
import de.cesr.crafty.core.updaters.ServicesUpdater;
import de.cesr.crafty.core.updaters.Timestep;

class RegionalModelRunnerTest {

	private Object configBackup;
	private Object cellsLoaderRegionsBackup;
	private boolean cellsLoaderRegionalizationBackup;
	private Object hashAgentNbrRegionsBackup;
	@TempDir
	private Path tempDir;
	@BeforeEach
	void backupStatics() throws Exception {
		
		new ToyData().resetStaticState(tempDir);
		// ConfigLoader.config backup
		Field cfgField = findField(ConfigLoader.class, "config");
		cfgField.setAccessible(true);
		configBackup = cfgField.get(null);

		// CellsLoader.regions backup
		Field regionsField = findField(CellsLoader.class, "regions");
		regionsField.setAccessible(true);
		cellsLoaderRegionsBackup = regionsField.get(null);

		// CellsLoader.regionalization backup
		Field regField = findField(CellsLoader.class, "regionalization");
		regField.setAccessible(true);
		cellsLoaderRegionalizationBackup = (boolean) regField.get(null);

		// AFTsLoader.hashAgentNbrRegions backup (used by calculeDistributionMean)
		Field hField = findField(AFTsLoader.class, "hashAgentNbrRegions");
		hField.setAccessible(true);
		hashAgentNbrRegionsBackup = hField.get(null);
	}

	@AfterEach
	void restoreStatics() throws Exception {
		// restore ConfigLoader.config
		Field cfgField = findField(ConfigLoader.class, "config");
		cfgField.setAccessible(true);
		cfgField.set(null, configBackup);

		// restore CellsLoader.regions
		Field regionsField = findField(CellsLoader.class, "regions");
		regionsField.setAccessible(true);
		regionsField.set(null, cellsLoaderRegionsBackup);

		// restore CellsLoader.regionalization
		Field regField = findField(CellsLoader.class, "regionalization");
		regField.setAccessible(true);
		regField.set(null, cellsLoaderRegionalizationBackup);

		// restore AFTsLoader.hashAgentNbrRegions
		Field hField = findField(AFTsLoader.class, "hashAgentNbrRegions");
		hField.setAccessible(true);
		hField.set(null, hashAgentNbrRegionsBackup);
	}
	
    @AfterEach
    void tearDown() {
    	CustomLogger.shutdownRunFileLoggers();
    }

	@Test
	void constructor_initialisesDistributionMeanForAllYears() throws Exception {
		try (MockedStatic<Timestep> ts = Mockito.mockStatic(Timestep.class);
				MockedConstruction<ListenerByRegion> lb = Mockito.mockConstruction(ListenerByRegion.class)) {

			ts.when(Timestep::getStartYear).thenReturn(2000);
			ts.when(Timestep::getEndtYear).thenReturn(2002);

			Region region = mockRegion("R1", new ConcurrentHashMap<>(), 0);

			installRegion("R1", region);

			RegionalModelRunner r = new RegionalModelRunner("R1");

			assertNotNull(r.getDistributionMean().get(2000));
			assertNotNull(r.getDistributionMean().get(2001));
			assertNotNull(r.getDistributionMean().get(2002));
		}
	}

	@Test
	void regionalSupply_sumsCurrentProdAcrossCells_andCallsProductivityCalculation() throws Exception {
		try (MockedStatic<Timestep> ts = Mockito.mockStatic(Timestep.class);
				MockedStatic<ServiceSet> ss = Mockito.mockStatic(ServiceSet.class);
				MockedConstruction<ListenerByRegion> lb = Mockito.mockConstruction(ListenerByRegion.class)) {

			ts.when(Timestep::getStartYear).thenReturn(2000);
			ts.when(Timestep::getEndtYear).thenReturn(2000);

			ss.when(ServiceSet::getServicesList).thenReturn(java.util.List.of("S1", "S2"));

			// force the executor path (doesn't matter much; both call
			// calculateCurrentProductivity)
			setStaticBoolean(CellsLoader.class, "regionalization", false);

			// Cells
			Cell c1 = mock(Cell.class);
			double[] currentProd= {1,2};
			when(c1.getCurrentProd()).thenReturn(currentProd);
			double[] currentProd2= {3,4};
			Cell c2 = mock(Cell.class);
			when(c2.getCurrentProd()).thenReturn(currentProd2);

			ConcurrentHashMap<String, Cell> cells = new ConcurrentHashMap<>();
			cells.put("0,0", c1);
			cells.put("1,1", c2);

			Region region = new Region("R1");
			region.setCells(cells);
			CellsLoader.regions.put("R1",region);
			RegionalModelRunner r = new RegionalModelRunner("R1");
			r.regionalSupply();

			assertEquals(4.0, r.getRegionalSupply().get("S1"), 1e-12);
			assertEquals(6.0, r.getRegionalSupply().get("S2"), 1e-12);

			verify(c1, times(1)).calculateCurrentProductivity();
			verify(c2, times(1)).calculateCurrentProductivity();
		}
	}

	@Test
	void utilitytyForAll_setsUtilities_usingServiceTaxAndMarginalAndLandTax() throws Exception {
		try (MockedStatic<Timestep> ts = Mockito.mockStatic(Timestep.class);
				MockedStatic<ServiceSet> ss = Mockito.mockStatic(ServiceSet.class);
				MockedConstruction<ListenerByRegion> lb = Mockito.mockConstruction(ListenerByRegion.class)) {

			ts.when(Timestep::getStartYear).thenReturn(2000);
			ts.when(Timestep::getEndtYear).thenReturn(2000);

			ss.when(ServiceSet::getServicesList).thenReturn(java.util.List.of("S1"));

			Cell c = spy(new Cell(0, 0));
			Cell cNull = new Cell(1, 1);

			Aft owner = mock(Aft.class);
			when(owner.isInteract()).thenReturn(true);
			when(owner.getCachedLandTax()).thenReturn(5.0);

			setField(c, "owner", owner);
			setField(cNull, "owner", null);

			// competitiveness(owner,"S1") = 10
			doReturn(10.0).when(c).competitiveness(owner, "S1");

			ConcurrentHashMap<String, Cell> cells = new ConcurrentHashMap<>();
			cells.put("0,0", c);
			cells.put("1,1", cNull);

			Region region = mockRegion("R1", cells, cells.size());
			installRegion("R1", region);

			RegionalModelRunner r = new RegionalModelRunner("R1");
			r.getServiceTax().put("S1", 1.0);
			r.getMarginal().put("S1", 0.5);

			invokePrivate(r, "utilitytyForAll");

			// u = landTax + (tax+marg)*prod = 5 + 1.5*10 = 20
			assertEquals(20.0, c.getCurrentUtility(), 1e-12);
			assertEquals(0.0, cNull.getCurrentUtility(), 1e-12);
		}
	}

	@Test
	void calculeMaxMinUtility_setsMinAndMax() throws Exception {
		try (MockedStatic<Timestep> ts = Mockito.mockStatic(Timestep.class);
				MockedStatic<ServiceSet> ss = Mockito.mockStatic(ServiceSet.class);
				MockedConstruction<ListenerByRegion> lb = Mockito.mockConstruction(ListenerByRegion.class)) {

			ts.when(Timestep::getStartYear).thenReturn(2000);
			ts.when(Timestep::getEndtYear).thenReturn(2000);

			ss.when(ServiceSet::getServicesList).thenReturn(java.util.List.of("S1"));

			Cell c1 = new Cell(0, 0);
			Cell c2 = new Cell(1, 1);
			c1.setCurrentUtility(3.0);
			c2.setCurrentUtility(-1.0);

			ConcurrentHashMap<String, Cell> cells = new ConcurrentHashMap<>();
			cells.put("0,0", c1);
			cells.put("1,1", c2);

			Region region = mockRegion("R1", cells, cells.size());
			installRegion("R1", region);

			RegionalModelRunner r = new RegionalModelRunner("R1");
			invokePrivate(r, "computeMaxMinUtility");

			assertEquals(-1.0, r.getMinUtility(), 1e-12);
			assertEquals(3.0, r.getMaxUtility(), 1e-12);
		}
	}

	@Test
	void calculeMarginal_computesExpectedMarginal_withAveraging() throws Exception {
		try (MockedStatic<Timestep> ts = Mockito.mockStatic(Timestep.class);
				MockedStatic<ServiceSet> ss = Mockito.mockStatic(ServiceSet.class);
				MockedStatic<ServicesUpdater> su = Mockito.mockStatic(ServicesUpdater.class);
				MockedConstruction<ListenerByRegion> lb = Mockito.mockConstruction(ListenerByRegion.class)) {

			ts.when(Timestep::getStartYear).thenReturn(2000);
			ts.when(Timestep::getEndtYear).thenReturn(2000);

			ss.when(ServiceSet::getServicesList).thenReturn(java.util.List.of("S1"));
			ss.when(ServiceSet::getPenalise_Oversupply).thenReturn(new ConcurrentHashMap<>(Map.of("S1", false)));

			// demand & weight by region
			Map<String, Map<String, Double>> demandByRegions = Map.of("R1", Map.of("S1", 100.0));
			Map<String, Map<String, Double>> weightByRegions = Map.of("R1", Map.of("S1", 0.5));
			su.when(ServicesUpdater::getDemandByRegions).thenReturn(demandByRegions);
			su.when(ServicesUpdater::getWeightByRegions).thenReturn(weightByRegions);

			// config flag
			Object cfg = ensureConfigInstance();
			setField(cfg, "averaged_residual_demand_per_cell", true);

			ConcurrentHashMap<String, Cell> cells = new ConcurrentHashMap<>();
			// Only need size
			Region region = mockRegion("R1", cells, 10);
			installRegion("R1", region);

			RegionalModelRunner r = new RegionalModelRunner("R1");
			r.setRegionalSupply(new ConcurrentHashMap<>(Map.of("S1", 80.0))); // supply=80

			invokePrivate(r, "computeMarginal");

			// residual = 100-80=20
			// averaged per cell (10): 2
			// * weight 0.5 => 1
			// / demand 100 => 0.01
			assertEquals(0.01, r.getMarginal().get("S1"), 1e-12);
		}
	}

	@Test
	void calculeDistributionMean_sumsUtilityDividedByAgentCount_andAddsZerosForMissingAfts() throws Exception {
		try (MockedStatic<Timestep> ts = Mockito.mockStatic(Timestep.class);
				MockedStatic<ServiceSet> ss = Mockito.mockStatic(ServiceSet.class);
				MockedStatic<AFTsLoader> al = Mockito.mockStatic(AFTsLoader.class);
				MockedConstruction<ListenerByRegion> lb = Mockito.mockConstruction(ListenerByRegion.class)) {

			ts.when(Timestep::getStartYear).thenReturn(2000);
			ts.when(Timestep::getEndtYear).thenReturn(2001);
			ts.when(Timestep::getCurrentYear).thenReturn(2001);

			ss.when(ServiceSet::getServicesList).thenReturn(java.util.List.of("S1"));

			// owner AFT
			Aft owner = mock(Aft.class);
			when(owner.getLabel()).thenReturn("A1");

			// other active AFT (to check computeIfAbsent -> 0)
			Aft other = mock(Aft.class);
			when(other.getLabel()).thenReturn("A2");

			// region cells
			Cell c1 = new Cell(0, 0);
			Cell c2 = new Cell(1, 1);
			setField(c1, "owner", owner);
			setField(c2, "owner", owner);
			c1.setCurrentUtility(10.0);
			c2.setCurrentUtility(6.0);

			ConcurrentHashMap<String, Cell> cells = new ConcurrentHashMap<>();
			cells.put("0,0", c1);
			cells.put("1,1", c2);

			Region region = mockRegion("R1", cells, cells.size());
			installRegion("R1", region);

			// hashAgentNbrRegions: for region R1, label A1 -> 2 (so each utility divided by
			// 2)
			ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> hashNbr = new ConcurrentHashMap<>();
			hashNbr.put("R1", new ConcurrentHashMap<>(Map.of("A1", 2)));
			setStaticField(AFTsLoader.class, "hashAgentNbrRegions", hashNbr);

			// active AFTs
			ConcurrentHashMap<String, Aft> active = new ConcurrentHashMap<>();
			active.put("A1", owner);
			active.put("A2", other);
			al.when(AFTsLoader::getActivateAFTsHash).thenReturn(active);

			RegionalModelRunner r = new RegionalModelRunner("R1");

			invokePrivate(r, "computeDistributionMean");

			double meanOwner = r.getDistributionMean().get(2001).get(owner.getLabel());
			double meanOther = r.getDistributionMean().get(2001).get(other.getLabel());

			// 10/2 + 6/2 = 8
			assertEquals(8.0, meanOwner, 1e-12);
			assertEquals(0.0, meanOther, 1e-12);
		}
	}

	// =========================================================
	// Helpers
	// =========================================================

	private static Region mockRegion(String name, ConcurrentHashMap<String, Cell> cells, int sizeOverride) {
		Region r = mock(Region.class);

		ConcurrentHashMap<String, Cell> cellsSpy = spy(cells);
		doReturn(sizeOverride).when(cellsSpy).size(); // <-- stub size on the map, not on Region

		when(r.getName()).thenReturn(name);
		when(r.getCells()).thenReturn(cellsSpy);

		return r;
	}

	@SuppressWarnings("unchecked")
	private static void installRegion(String regionName, Region region) throws Exception {
		Field regionsField = findField(CellsLoader.class, "regions");
		regionsField.setAccessible(true);

		Object regionsObj = regionsField.get(null);
		ConcurrentHashMap<String, Region> regions;
		if (regionsObj == null) {
			regions = new ConcurrentHashMap<>();
			regionsField.set(null, regions);
		} else {
			regions = (ConcurrentHashMap<String, Region>) regionsObj;
		}
		regions.put(regionName, region);
	}

	private static Object ensureConfigInstance() throws Exception {
		Field cfgField = findField(ConfigLoader.class, "config");
		cfgField.setAccessible(true);
		Object cfg = cfgField.get(null);
		if (cfg == null) {
			cfg = cfgField.getType().getDeclaredConstructor().newInstance();
			cfgField.set(null, cfg);
		}
		return cfg;
	}

	private static void invokePrivate(Object target, String methodName) throws Exception {
		Method m = target.getClass().getDeclaredMethod(methodName);
		m.setAccessible(true);
		m.invoke(target);
	}

	private static void setStaticBoolean(Class<?> cls, String fieldName, boolean value) throws Exception {
		Field f = findField(cls, fieldName);
		f.setAccessible(true);
		f.set(null, value);
	}

	private static void setStaticField(Class<?> cls, String fieldName, Object value) throws Exception {
		Field f = findField(cls, fieldName);
		f.setAccessible(true);
		f.set(null, value);
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
}
