package de.cesr.crafty.core.crafty;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.output.Listener;
import de.cesr.crafty.core.output.Tracker;
import de.cesr.crafty.core.updaters.CapitalUpdater;
import de.cesr.crafty.core.updaters.Timestep;

class CellTest {

	@BeforeEach
	void resetCounters() {
		// make sure we start clean for giveUp() tests
		if (Listener.landUseChangeCounter != null) {
			Listener.landUseChangeCounter.set(0);
		}
	}

	// -----------------------------
	// productivity(...)
	// -----------------------------

	@Test
	void productivity_returnsZero_whenAftNull() {
		Cell c = new Cell(0, 0);
		assertEquals(0.0, c.productivity(null, "S1"), 1e-12);
	}

	@Test
	void productivity_returnsZero_whenAftNotInteract() {
		Cell c = new Cell(0, 0);

		Aft a = mock(Aft.class);
		when(a.isInteract()).thenReturn(false);

		assertEquals(0.0, c.productivity(a, "S1"), 1e-12);
	}

	@Test
	void productivity_returnsZero_whenSensitivitiesMissingOrEmpty() throws Throwable {
		withServices(List.of("S1"), () -> {
			Cell c = new Cell(0, 0);

			Aft a = mock(Aft.class);
			when(a.isInteract()).thenReturn(true);
			when(a.getSensByService()).thenReturn(new ConcurrentHashMap<>());

			assertEquals(0.0, c.productivity(a, "S1"), 1e-12);

			Map<String, Map<String, Double>> sens = new ConcurrentHashMap<>();
			sens.put("S1", new HashMap<>()); // empty
			when(a.getSensByService()).thenReturn(sens);

			assertEquals(0.0, c.productivity(a, "S1"), 1e-12);
		});
	}

	@Test
	void productivity_computesExpectedProduct() throws Throwable {
		withServices(List.of("S1"), () -> {
			Cell c = new Cell(1, 2);

			// capitals: capA=2, capB=9, capC=100
			Map<String, Double> caps = new ConcurrentHashMap<>();
			caps.put("capA", 2.0);
			caps.put("capB", 9.0);
			caps.put("capC", 100.0);
			setField(c, "capitals", caps);

			// sensitivities for S1: capA^1 * capB^0.5 * capC^0 (ignored)
			Map<String, Double> exps = new LinkedHashMap<>();
			exps.put("capA", 1.0);
			exps.put("capB", 0.5);
			exps.put("capC", 0.0);

			Map<String, Map<String, Double>> sensByService = new ConcurrentHashMap<>();
			sensByService.put("S1", exps);

			ConcurrentHashMap<String, Double> levels = new ConcurrentHashMap<>();
			levels.put("S1", 10.0);

			Aft a = mock(Aft.class);
			when(a.isInteract()).thenReturn(true);
			when(a.getSensByService()).thenReturn(sensByService);
			when(a.getProductivityLevel()).thenReturn(levels);

			// expected: (2 * sqrt(9)) * 10 = (2 * 3) * 10 = 60
			assertEquals(60.0, c.productivity(a, "S1"), 1e-12);
		});
	}

	// -----------------------------
	// competitiveness(...)
	// -----------------------------

	@Test
	void competitiveness_computesExpectedProduct() throws Throwable {
		withServices(List.of("S1"), () -> {
			Cell c = new Cell(1, 2);

			Map<String, Double> caps = new ConcurrentHashMap<>();
			caps.put("capA", 2.0);
			caps.put("capB", 9.0);
			caps.put("capC", 100.0);
			setField(c, "capitals", caps);

			Map<String, Double> exps = new LinkedHashMap<>();
			exps.put("capA", 1.0);
			exps.put("capB", 0.5);
			exps.put("capC", 0.0);

			Map<String, Map<String, Double>> sensByService = new ConcurrentHashMap<>();
			sensByService.put("S1", exps);

			ConcurrentHashMap<String, Double> levels = new ConcurrentHashMap<>();
			levels.put("S1", 10.0);

			Aft a = mock(Aft.class);
			when(a.isInteract()).thenReturn(true);
			when(a.getSensByService()).thenReturn(sensByService);
			when(a.getProductivityLevel()).thenReturn(levels);

			assertEquals(60.0, c.competitiveness(a, "S1"), 1e-12);
		});
	}

	@Test
	void competitiveness_isLessThanOrEqualToProductivity_whenCapitalsInUnitRange() throws Throwable {
		withServices(List.of("S1"), () -> {
			Object cfg = ensureConfigInstance();
			setField(cfg, "separate_production_competitiveness", true);

			try {
				CapitalUpdater.getCapitalTypes().clear();
				CapitalUpdater.getCapitalTypes().put("capA", "Suitability");
				CapitalUpdater.getCapitalTypes().put("capB", "Capital");

				Cell c = new Cell(3, 4);

				Map<String, Double> caps = new ConcurrentHashMap<>();
				caps.put("capA", 0.5);
				caps.put("capB", 0.8);
				setField(c, "capitals", caps);

				Map<String, Double> exps = new LinkedHashMap<>();
				exps.put("capA", 1.0);
				exps.put("capB", 2.0);

				Map<String, Map<String, Double>> sensByService = new ConcurrentHashMap<>();
				sensByService.put("S1", exps);

				ConcurrentHashMap<String, Double> levels = new ConcurrentHashMap<>();
				levels.put("S1", 10.0);

				Aft a = mock(Aft.class);
				when(a.isInteract()).thenReturn(true);
				when(a.getSensByService()).thenReturn(sensByService);
				when(a.getProductivityLevel()).thenReturn(levels);

				double prod = c.productivity(a, "S1");
				double comp = c.competitiveness(a, "S1");

				// prod uses only capA (Suitability): 0.5^1 * 10 = 5.0
				// comp uses both: 0.5^1 * 0.8^2 * 10 = 3.2
				assertEquals(5.0, prod, 1e-12);
				assertEquals(3.2, comp, 1e-12);
				assertTrue(comp <= prod);
			} finally {
				setField(cfg, "separate_production_competitiveness", false);
				CapitalUpdater.getCapitalTypes().clear();
			}
		});
	}

	@Test
	void competitiveness_invertedByCapitalExponent_recoversProductivity() throws Throwable {
		withServices(List.of("S1"), () -> {
			Object cfg = ensureConfigInstance();
			setField(cfg, "separate_production_competitiveness", true);

			try {
				CapitalUpdater.getCapitalTypes().clear();
				CapitalUpdater.getCapitalTypes().put("capA", "Suitability");

				Cell c = new Cell(5, 6);

				Map<String, Double> caps = new ConcurrentHashMap<>();
				caps.put("capA", 4.0);
				setField(c, "capitals", caps);

				Map<String, Double> exps = new LinkedHashMap<>();
				exps.put("capA", 2.0);

				Map<String, Map<String, Double>> sensByService = new ConcurrentHashMap<>();
				sensByService.put("S1", exps);

				ConcurrentHashMap<String, Double> levels = new ConcurrentHashMap<>();
				levels.put("S1", 5.0);

				Aft a = mock(Aft.class);
				when(a.isInteract()).thenReturn(true);
				when(a.getSensByService()).thenReturn(sensByService);
				when(a.getProductivityLevel()).thenReturn(levels);

				double prod = c.productivity(a, "S1");
				double comp = c.competitiveness(a, "S1");

				// capA is Suitability, so both methods include it — prod == comp
				assertEquals(prod, comp, 1e-9);
			} finally {
				setField(cfg, "separate_production_competitiveness", false);
				CapitalUpdater.getCapitalTypes().clear();
			}
		});
	}

	@Test
	void productivity_includesSuitabilities_excludesCapitals() throws Throwable {
		withServices(List.of("S1"), () -> {
			Object cfg = ensureConfigInstance();
			setField(cfg, "separate_production_competitiveness", true);

			try {
				CapitalUpdater.getCapitalTypes().clear();
				CapitalUpdater.getCapitalTypes().put("yield_potential", "Suitability");
				CapitalUpdater.getCapitalTypes().put("infrastructure", "Capital");

				Cell c = new Cell(7, 8);

				Map<String, Double> caps = new ConcurrentHashMap<>();
				caps.put("yield_potential", 0.6);
				caps.put("infrastructure", 0.9);
				setField(c, "capitals", caps);

				Map<String, Double> exps = new LinkedHashMap<>();
				exps.put("yield_potential", 1.0);
				exps.put("infrastructure", 0.5);

				Map<String, Map<String, Double>> sensByService = new ConcurrentHashMap<>();
				sensByService.put("S1", exps);

				ConcurrentHashMap<String, Double> levels = new ConcurrentHashMap<>();
				levels.put("S1", 10.0);

				Aft a = mock(Aft.class);
				when(a.isInteract()).thenReturn(true);
				when(a.getSensByService()).thenReturn(sensByService);
				when(a.getProductivityLevel()).thenReturn(levels);

				double prod = c.productivity(a, "S1");
				double comp = c.competitiveness(a, "S1");

				// prod: only yield_potential (Suitability): 0.6^1 * 10 = 6.0
				assertEquals(6.0, prod, 1e-12);

				// comp: both capitals: 0.6^1 * 0.9^0.5 * 10
				assertEquals(0.6 * Math.pow(0.9, 0.5) * 10.0, comp, 1e-9);

				assertTrue(comp < prod,
						"Competitiveness should be less than production when a true Capital < 1");
			} finally {
				setField(cfg, "separate_production_competitiveness", false);
				CapitalUpdater.getCapitalTypes().clear();
			}
		});
	}

	// -----------------------------
	// calculateCurrentProductivity(...)
	// -----------------------------

	@Test
	void calculateCurrentProductivity_fillsCurrentProd_usingServiceSetList() throws Throwable {
		withServices(List.of("S1", "S2"), () -> {
			Cell c = new Cell(0, 0);

			Map<String, Double> caps = new ConcurrentHashMap<>();
			caps.put("capA", 2.0);
			setField(c, "capitals", caps);

			// S1: capA^1 * level=1 => 2
			// S2: capA^2 * level=5 => 4*5=20
			Map<String, Map<String, Double>> sensByService = new ConcurrentHashMap<>();
			sensByService.put("S1", Map.of("capA", 1.0));
			sensByService.put("S2", Map.of("capA", 2.0));

			ConcurrentHashMap<String, Double> levels = new ConcurrentHashMap<>();
			levels.put("S1", 1.0);
			levels.put("S2", 5.0);

			Aft owner = mock(Aft.class);
			when(owner.isInteract()).thenReturn(true);
			when(owner.getSensByService()).thenReturn(sensByService);
			when(owner.getProductivityLevel()).thenReturn(levels);

			setOwner(c, owner);

			c.calculateCurrentProductivity();

			assertArrayEquals(new double[] { 2.0, 20.0 }, c.getCurrentProd(), 1e-12);
		});
	}

	@Test
	void calculateCurrentProductivity_usesProvidedServicesArray() throws Throwable {
		// ServiceSet list is only used for the loop length in this overload
		withServices(List.of("X", "Y"), () -> {
			Cell c = new Cell(0, 0);

			Map<String, Double> caps = new ConcurrentHashMap<>();
			caps.put("capA", 3.0);
			setField(c, "capitals", caps);

			Map<String, Map<String, Double>> sensByService = new ConcurrentHashMap<>();
			sensByService.put("S1", Map.of("capA", 1.0)); // => 3
			sensByService.put("S2", Map.of("capA", 2.0)); // => 9

			ConcurrentHashMap<String, Double> levels = new ConcurrentHashMap<>();
			levels.put("S1", 2.0); // => 6
			levels.put("S2", 10.0); // => 90

			Aft owner = mock(Aft.class);
			when(owner.isInteract()).thenReturn(true);
			when(owner.getSensByService()).thenReturn(sensByService);
			when(owner.getProductivityLevel()).thenReturn(levels);

			setOwner(c, owner);

			c.calculateCurrentProductivity(new String[] { "S1", "S2" });

			assertArrayEquals(new double[] { 6.0, 90.0 }, c.getCurrentProd(), 1e-12);
		});
	}

	@Test
	void calculateCurrentProductivity_throwsIfServicesArrayTooShort() throws Throwable {
		withServices(List.of("A", "B"), () -> {
			Cell c = new Cell(0, 0);
			assertThrows(ArrayIndexOutOfBoundsException.class,
					() -> c.calculateCurrentProductivity(new String[] { "onlyOne" }));
		});
	}

	// -----------------------------
	// giveUp(...)
	// -----------------------------

	@Test
	void giveUp_doesNothing_whenOwnerNull() throws Throwable {
		withServices(List.of("S1"), () -> {
			Cell c = new Cell(0, 0);
			setOwner(c, null);

			Region region = mock(Region.class);
			Set<Cell> unmanaged = ConcurrentHashMap.newKeySet();
			when(region.getUnmanageCellsR()).thenReturn(unmanaged);

			RegionalModelRunner runner = mock(RegionalModelRunner.class);
			setField(runner, "R", region);

			c.giveUp(runner, new ConcurrentHashMap<>());

			assertTrue(unmanaged.isEmpty());
			assertEquals(0, Listener.landUseChangeCounter.get());
		});
	}

//	@Test
//	void giveUp_givesUpDeterministically_whenBelowThresholdAndProbabilityOne() throws Throwable {
//		withServices(List.of("S1"), () -> {
//			Cell c = new Cell(0, 0);
//
//			Aft owner = mock(Aft.class);
//			when(owner.isInteract()).thenReturn(true);
//			when(owner.getGiveUpMean()).thenReturn(1.0); // threshold factor
//			when(owner.getGiveUpSD()).thenReturn(0.0); // remove gaussian randomness
//			when(owner.getGiveUpProbabilty()).thenReturn(1.0); // Math.random always < 1 => true
//
//			setOwner(c, owner);
//
//			// utility < averageUtility * 1.0 => triggers
//			setCurrentUtility(c, 5.0);
//
//			ConcurrentHashMap<Aft, Double> mean = new ConcurrentHashMap<>();
//			mean.put(owner, 10.0);
//
//			Region region = mock(Region.class);
//			Set<Cell> unmanaged = ConcurrentHashMap.newKeySet();
//			when(region.getUnmanageCellsR()).thenReturn(unmanaged);
//
//			RegionalModelRunner runner = mock(RegionalModelRunner.class);
//			setField(runner, "R", region);
//			when(owner.getLabel()).thenReturn(""); // Math.random always < 1 => true
//			Tracker.sankeydata.put("Abandoned", new ConcurrentHashMap<>() );
//			Tracker.sankeydata.get("Abandoned").put(Timestep.getCurrentYear(),new ConcurrentHashMap<>() );
//			c.giveUp(runner, mean);
//
//			assertNull(getOwner(c), "Owner should be cleared after giveUp");
//			assertTrue(unmanaged.contains(c), "Cell should be added to unmanaged set");
//			assertEquals(1, Listener.landUseChangeCounter.get(), "Counter should increment");
//		});
//	}

	// =========================================================
	// Helpers
	// =========================================================

	private Object ensureConfigInstance() {
		try {
			Field cfgField = findField(ConfigLoader.class, "config");
			cfgField.setAccessible(true);
			Object cfg = cfgField.get(null);
			if (cfg == null) {
				cfg = cfgField.getType().getDeclaredConstructor().newInstance();
				cfgField.set(null, cfg);
			}
			return cfg;
		} catch (Exception e) {
			throw new RuntimeException("Failed to ensure config instance", e);
		}
	}

	/**
	 * Runs a test body with ServiceSet.getServicesList() returning the provided
	 * list.
	 *
	 * First tries to mutate the real list (if it's mutable). If not possible, falls
	 * back to Mockito static mocking (needs mockito-inline).
	 */
	private static void withServices(List<String> services, Executable body) throws Throwable {
		List<String> provided = new ArrayList<>(services);

		try {
			List<String> real = ServiceSet.getServicesList();
			List<String> backup = new ArrayList<>(real);
			try {
				real.clear();
				real.addAll(provided);
				body.execute();
			} finally {
				real.clear();
				real.addAll(backup);
			}
		} catch (UnsupportedOperationException ex) {
			try (MockedStatic<ServiceSet> mocked = Mockito.mockStatic(ServiceSet.class)) {
				mocked.when(ServiceSet::getServicesList).thenReturn(provided);
				body.execute();
			}
		}
	}

	private static void setOwner(Cell c, Aft owner) {
		// prefer setter if present
		try {
			Method m = findMethod(c.getClass(), "setOwner", Aft.class);
			if (m != null) {
				m.setAccessible(true);
				m.invoke(c, owner);
				return;
			}
		} catch (Exception ignored) {
		}
		setField(c, "owner", owner);
	}

	private static Aft getOwner(Cell c) {
		try {
			Method m = findMethod(c.getClass(), "getOwner");
			if (m != null) {
				m.setAccessible(true);
				return (Aft) m.invoke(c);
			}
		} catch (Exception ignored) {
		}
		return (Aft) getField(c, "owner");
	}

	private static void setCurrentUtility(Cell c, double util) {
		// prefer setter if present
		try {
			Method m = findMethod(c.getClass(), "setCurrentUtility", double.class);
			if (m != null) {
				m.setAccessible(true);
				m.invoke(c, util);
				return;
			}
		} catch (Exception ignored) {
		}

		// fallback to field if present
		try {
			setField(c, "currentUtility", util);
		} catch (Exception e) {
			// If your AbstractCell uses a different name, update here.
			throw new AssertionError("Could not set current utility. Add a setter or adjust field name.", e);
		}
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
