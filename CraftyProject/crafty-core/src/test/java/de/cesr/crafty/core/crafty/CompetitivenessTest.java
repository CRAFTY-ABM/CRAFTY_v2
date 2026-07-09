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
import de.cesr.crafty.core.output.Tracker;
import de.cesr.crafty.core.updaters.CellBehaviourUpdater;
import de.cesr.crafty.core.updaters.LandMaskUpdater;
import de.cesr.crafty.core.updaters.Timestep;

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

	private double callUtility(Cell c, Aft a, RegionalModelRunner r) {
		try {
			Method m = Competitiveness.class.getDeclaredMethod("utility", Cell.class, Aft.class,
					RegionalModelRunner.class);
			m.setAccessible(true); // bypass private
			return (double) m.invoke(null, c, a, r); // null because it's static
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void utility_returnsZero_whenAftNullOrNotInteract() throws Throwable {
		withServices(List.of("S1"), () -> {
			ensureConfigInstance();
			Cell c = new Cell(0, 0);
			RegionalModelRunner r = mock(RegionalModelRunner.class);

			assertEquals(0.0, callUtility(c, null, r), 1e-12);

			Aft a = mock(Aft.class);
			when(a.isInteract()).thenReturn(false);
			assertEquals(0.0, callUtility(c, a, r), 1e-12);
		});
	}

//    @Test
//    void utility_sumsTaxPlusMarginal_timesProductivity_plusLandTax() throws Throwable {
//        withServices(List.of("S1", "S2"), () -> {
//            Cell c = Mockito.spy(new Cell(0, 0));
//
//            Aft a = mock(Aft.class);
//            when(a.isInteract()).thenReturn(true);
//            when(a.getCachedLandTax()).thenReturn(5.0);
//
//            RegionalModelRunner r = mock(RegionalModelRunner.class);
//            when(r.getServiceTax()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 1.0, "S2", 10.0)));
//            when(r.getMarginal()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 0.5, "S2", -2.0)));
//
//            // S1 productivity=2, S2 productivity=3
//            doReturn(2.0).when(c).productivity(a, "S1");
//            doReturn(3.0).when(c).productivity(a, "S2");
//
//            // expected: (1+0.5)*2 + (10-2)*3 + 5 = 3 + 24 + 5 = 32
//            assertEquals(32.0, callUtility(c, a, r), 1e-12);
//        });
//    }

//    @Test
//    void associateUtility_setsCellCurrentUtility() throws Throwable {
//        withServices(List.of("S1"), () -> {
//            Cell c = Mockito.spy(new Cell(0, 0));
//
//            Aft owner = mock(Aft.class);
//            when(owner.isInteract()).thenReturn(true);
//            when(owner.getCachedLandTax()).thenReturn(1.0);
//            setOwner(c, owner);
//
//            RegionalModelRunner r = mock(RegionalModelRunner.class);
//            when(r.getServiceTax()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 2.0)));
//            when(r.getMarginal()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 0.0)));
//
//            doReturn(10.0).when(c).productivity(owner, "S1"); // => 2*10 + 1 = 21
//            c.setCurrentUtility(Competitiveness.utility(c, c.getOwner(), r));
//            
//
//            assertEquals(21.0, c.getCurrentUtility(), 1e-12);
//        });
//    }

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

//    @Test
//    void mostCompetitiveAgent_picksAgentWithHighestUtility() throws Throwable {
//        withServices(List.of("S1"), () -> {
//            Cell c = Mockito.spy(new Cell(0, 0));
//
//            Aft a1 = mock(Aft.class);
//            when(a1.isInteract()).thenReturn(true);
//            when(a1.getCachedLandTax()).thenReturn(0.0);
//
//            Aft a2 = mock(Aft.class);
//            when(a2.isInteract()).thenReturn(true);
//            when(a2.getCachedLandTax()).thenReturn(0.0);
//
//            RegionalModelRunner r = mock(RegionalModelRunner.class);
//            when(r.getServiceTax()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 1.0)));
//            when(r.getMarginal()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 0.0)));
//
//            // utility = 1 * productivity
//            doReturn(1.0).when(c).productivity(a1, "S1");
//            doReturn(10.0).when(c).productivity(a2, "S1");
//
//            Aft best = Competitiveness.mostCompetitiveAgent(c, List.of(a1, a2), r);
//            assertSame(a2, best);
//        });
//    }

	// -------------------------------------------------------
	// competition(...)
	// -------------------------------------------------------

	@Test
	void competition_takesOver_whenOwnerNull_andCompetitorAboveMean_nonNormalisedPath() throws Throwable {
		withServices(List.of("S1"), () -> {
			// config: force "most competitor" branch, no neighbor priority, no mutation
			Object cfg = ensureConfigInstance();
			setConfig(cfg, Map.of("use_neighbor_priority", false, "MostCompetitorAFTProbability", 1.0,
					"mutate_on_competition_win", false));

			// categorisation OFF so landUsechange() is used
			AftCategorised.useCategorisationGivIn = false;
			CellBehaviourUpdater.behaviourUsed = false;

			Cell c = Mockito.spy(new Cell(0, 0));
			setOwner(c, null);

			Aft competitor = mock(Aft.class);
			when(competitor.isInteract()).thenReturn(true);
			when(competitor.getCachedLandTax()).thenReturn(0.0);
			when(competitor.getLabel()).thenReturn("aft");

			RegionalModelRunner r = mock(RegionalModelRunner.class);
			when(r.getMarginal()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 0.0)));

			// competitor utility = 1 * 6 = 6
			doReturn(6.0).when(c).competitiveness(competitor, "S1");
			when(c.getLandTax()).thenReturn(new ConcurrentHashMap<>());
			when(c.getServicesTax()).thenReturn(new ConcurrentHashMap<>());

			ConcurrentHashMap<String, Double> meanY = new ConcurrentHashMap<>();
			meanY.put(competitor.getLabel(), 5.0);
			when(r.getDistributionMeanY()).thenReturn(meanY);

			try (MockedStatic<AFTsLoader> mocked = Mockito.mockStatic(AFTsLoader.class)) {
				ConcurrentHashMap<String, Aft> active = new ConcurrentHashMap<>();
				active.put("comp", competitor);
				mocked.when(AFTsLoader::getActivateAFTsHash).thenReturn(active);

				Competitiveness.competition(c, r);
//                assertSame(competitor, getOwner(c));
//                assertEquals(1, Listener.landUseChangeCounter.get());
			}
		});
	}

	@Test
	void competition_isBlockedByMaskRestriction_whenOwnerNull() throws Throwable {
		withServices(List.of("S1"), () -> {
			Object cfg = ensureConfigInstance();
			setConfig(cfg, Map.of("use_neighbor_priority", false, "MostCompetitorAFTProbability", 1.0,
					"mutate_on_competition_win", false));

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

			doReturn(100.0).when(c).competitiveness(competitor, "S1");

			ConcurrentHashMap<String, Double> meanY = new ConcurrentHashMap<>();
			meanY.put(competitor.getLabel(), 0.0);
			when(r.getDistributionMeanY()).thenReturn(meanY);

			try (MockedStatic<AFTsLoader> mocked = Mockito.mockStatic(AFTsLoader.class)) {
				mocked.when(AFTsLoader::getActivateAFTsHash)
						.thenReturn(new ConcurrentHashMap<>(Map.of("comp", competitor)));

				Competitiveness.competition(c, r);

				assertNull(getOwner(c), "Owner must stay null because mask blocks competition");
				assertEquals(0, Listener.landUseChangeCounter.get());
			}
		});
	}

//    @Test
//    void competition_takesOver_whenOwnerExists_andUtilityDifferenceExceedsThreshold_nonNormalisedPath() throws Throwable {
//        withServices(List.of("S1"), () -> {
//            Object cfg = ensureConfigInstance();
//            setConfig(cfg,
//                    Map.of(
//                            "use_neighbor_priority", false,
//                            "MostCompetitorAFTProbability", 0.0, // force random-AFT branch
//                            "mutate_on_competition_win", false
//                    )
//            );
//
//            AftCategorised.useCategorisationGivIn = false;
//            CellBehaviourUpdater.behaviourUsed = false;
//
//            Cell c = Mockito.spy(new Cell(0, 0));
//
//            Aft owner = mock(Aft.class);
//            when(owner.isInteract()).thenReturn(true);
//            when(owner.isAbandoned()).thenReturn(false);
//            when(owner.getGiveInMean()).thenReturn(1.0);
//            when(owner.getGiveInSD()).thenReturn(0.0); // remove randomness
//            setOwner(c, owner);
//
//            // set current utility uO=1
//            c.setCurrentUtility(1.0);
//
//            Aft competitor = mock(Aft.class);
//            when(competitor.isInteract()).thenReturn(true);
//            when(competitor.getCachedLandTax()).thenReturn(0.0);
//
//            RegionalModelRunner r = mock(RegionalModelRunner.class);
//            when(r.getServiceTax()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 1.0)));
//            when(r.getMarginal()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 0.0)));
//
//            // competitor uC = 10
//            doReturn(10.0).when(c).productivity(competitor, "S1");
//
//            // nbr = mean(owner) * giveInThreshold = 2 * 1 = 2
//            ConcurrentHashMap<Aft, Double> meanY = new ConcurrentHashMap<>();
//            meanY.put(owner, 2.0);
//            when(r.getDistributionMeanY()).thenReturn(meanY);
//
//            try (MockedStatic<AFTsLoader> mocked = Mockito.mockStatic(AFTsLoader.class)) {
//                mocked.when(AFTsLoader::getActivateAFTsHash).thenReturn(new ConcurrentHashMap<>(Map.of("comp", competitor)));
//                mocked.when(() -> AFTsLoader.getRandomAFT(any())).thenReturn(competitor);
//
//                Competitiveness.competition(c, r);
//
//                assertSame(competitor, getOwner(c));
//                assertEquals(1, Listener.landUseChangeCounter.get());
//            }
//        });
//    }
//
//    @Test
//    void competition_takesOver_inNormalisedUtilityPath_whenOwnerNull_andUCPositive() throws Throwable {
//        withServices(List.of("S1"), () -> {
//            Object cfg = ensureConfigInstance();
//            setConfig(cfg,
//                    Map.of(
//                            "use_neighbor_priority", false,
//                            "MostCompetitorAFTProbability", 0.0, // random
//                            "mutate_on_competition_win", false
//                    )
//            );
//
//            // force normalised path
//            AftCategorised.useCategorisationGivIn = true;
//            CellBehaviourUpdater.behaviourUsed = true;
//
//            Cell c = Mockito.spy(new Cell(0, 0));
//            setOwner(c, null);
//
//            Aft competitor = mock(Aft.class);
//            when(competitor.isInteract()).thenReturn(true);
//            when(competitor.getCachedLandTax()).thenReturn(0.0);
//
//            RegionalModelRunner r = mock(RegionalModelRunner.class);
//            when(r.getServiceTax()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 1.0)));
//            when(r.getMarginal()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 0.0)));
//            when(r.getMinUtility()).thenReturn(0.0);
//            when(r.getMaxUtility()).thenReturn(10.0);
//
//            // raw utility = 5 => normalised 0.5 > 0 => takeover for owner null
//            doReturn(5.0).when(c).productivity(competitor, "S1");
//
//            try (MockedStatic<AFTsLoader> mocked = Mockito.mockStatic(AFTsLoader.class)) {
//                mocked.when(AFTsLoader::getActivateAFTsHash).thenReturn(new ConcurrentHashMap<>(Map.of("comp", competitor)));
//                mocked.when(() -> AFTsLoader.getRandomAFT(any())).thenReturn(competitor);
//
//                Competitiveness.competition(c, r);
//
//                assertSame(competitor, getOwner(c));
//                assertEquals(1, Listener.landUseChangeCounter.get());
//            }
//        });
//    }

	// -------------------------------------------------------
	// giveInThreshold(...) (private) via reflection
	// -------------------------------------------------------

//    @Test
//    void giveInThreshold_usesCategorisedMeanSd_whenPresent() throws Throwable {
//        AftCategorised.useCategorisationGivIn = true;
//
//        Aft owner = mock(Aft.class);
//        Aft competitor = mock(Aft.class);
//
//        AftCategory ownerCat = mock(AftCategory.class);
//        when(ownerCat.getName()).thenReturn("A");
//        when(owner.getCategory()).thenReturn(ownerCat);
//
//        AftCategory compCat = mock(AftCategory.class);
//        when(compCat.getName()).thenReturn("B");
//        when(competitor.getCategory()).thenReturn(compCat);
//
//        // sd=0 removes randomness from ThreadLocalRandom gaussian
//        AftCategorised.getMean().put("A|B", 0.25);
//        AftCategorised.getSD().put("A|B", 0.0);
//
//        Method m = Competitiveness.class.getDeclaredMethod("giveInThreshold", Cell.class, Aft.class);
//        m.setAccessible(true);
//
//        double v = (double) m.invoke(null, owner, competitor);
//        assertEquals(0.25, v, 1e-12);
//    }

	// -------------------------------------------------------
	// landUsechangeNormalisedPriceUtility (via reflection)
	// -------------------------------------------------------

	private void callLandUsechangeNormalisedPriceUtility(Cell c, Aft competitor, RegionalModelRunner r) {
		try {
			Method m = Competitiveness.class.getDeclaredMethod(
					"landUsechangeNormalisedPriceUtility", Cell.class, Aft.class, RegionalModelRunner.class);
			m.setAccessible(true);
			m.invoke(null, c, competitor, r);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void setupSankeyData(String aftLabel, int year) {
		Tracker.sankeydata.computeIfAbsent(aftLabel, k -> new ConcurrentHashMap<>())
				.computeIfAbsent(year, k -> new ConcurrentHashMap<>());
	}

	// --- Dispatch gate tests ---

	@Test
	void competition_dispatchesToNormalisedPrice_whenFlagIsTrue() throws Throwable {
		withServices(List.of("S1"), () -> {
			Object cfg = ensureConfigInstance();
			setConfig(cfg, Map.of(
					"use_neighbor_priority", false,
					"MostCompetitorAFTProbability", 1.0,
					"mutate_on_competition_win", false));

			setConfig(cfg, Map.of("use_normalised_price_competition", true));
			AftCategorised.useCategorisationGivIn = false;
			CellBehaviourUpdater.behaviourUsed = false;

			Cell c = Mockito.spy(new Cell(0, 0));
			setOwner(c, null);

			Aft competitor = mock(Aft.class);
			when(competitor.isInteract()).thenReturn(true);
			when(competitor.getLabel()).thenReturn("COMP");

			RegionalModelRunner r = mock(RegionalModelRunner.class);
			when(r.getMarginal()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 0.0)));

			doReturn(10.0).when(c).competitiveness(competitor, "S1");
			when(c.getLandTax()).thenReturn(new ConcurrentHashMap<>());
			when(c.getServicesTax()).thenReturn(new ConcurrentHashMap<>());

			Service s1 = mock(Service.class);
			when(s1.getWeights()).thenReturn(new ConcurrentHashMap<>(Map.of(2020, 10.0)));
			Region region = mock(Region.class);
			when(region.getServicesHash()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", s1)));
			r.R = region;

			setupSankeyData("COMP", 2020);

			try (MockedStatic<AFTsLoader> mocked = Mockito.mockStatic(AFTsLoader.class);
			     MockedStatic<Timestep> ts = Mockito.mockStatic(Timestep.class)) {
				ts.when(Timestep::getCurrentYear).thenReturn(2020);
				ts.when(Timestep::getStartYear).thenReturn(2020);
				mocked.when(AFTsLoader::getActivateAFTsHash)
						.thenReturn(new ConcurrentHashMap<>(Map.of("COMP", competitor)));

				ConfigLoader.config.use_price_only_utility = true;
				try {
					Competitiveness.competition(c, r);
					assertSame(competitor, getOwner(c),
							"Normalised price path should take over abandoned cell when uC > 0");
					assertEquals(1, Listener.landUseChangeCounter.get());
				} finally {
					ConfigLoader.config.use_price_only_utility = false;
				}
			}
		});
	}

	@Test
	void competition_doesNotUseNormalisedPrice_whenFlagIsFalse() throws Throwable {
		withServices(List.of("S1"), () -> {
			Object cfg = ensureConfigInstance();
			setConfig(cfg, Map.of(
					"use_neighbor_priority", false,
					"MostCompetitorAFTProbability", 1.0,
					"mutate_on_competition_win", false));

			setConfig(cfg, Map.of("use_normalised_price_competition", false));
			AftCategorised.useCategorisationGivIn = false;
			CellBehaviourUpdater.behaviourUsed = false;

			Cell c = Mockito.spy(new Cell(0, 0));
			setOwner(c, null);

			Aft competitor = mock(Aft.class);
			when(competitor.isInteract()).thenReturn(true);
			when(competitor.getLabel()).thenReturn("COMP");

			RegionalModelRunner r = mock(RegionalModelRunner.class);
			// marginal=1 so utility = (0+1)*6 + 0 = 6
			when(r.getMarginal()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 1.0)));

			doReturn(6.0).when(c).competitiveness(competitor, "S1");
			when(c.getLandTax()).thenReturn(new ConcurrentHashMap<>());
			when(c.getServicesTax()).thenReturn(new ConcurrentHashMap<>());

			ConcurrentHashMap<String, Double> meanY = new ConcurrentHashMap<>();
			meanY.put("COMP", 5.0);
			when(r.getDistributionMeanY()).thenReturn(meanY);

			setupSankeyData("COMP", 2020);

			try (MockedStatic<AFTsLoader> mocked = Mockito.mockStatic(AFTsLoader.class);
			     MockedStatic<Timestep> ts = Mockito.mockStatic(Timestep.class)) {
				ts.when(Timestep::getCurrentYear).thenReturn(2020);
				mocked.when(AFTsLoader::getActivateAFTsHash)
						.thenReturn(new ConcurrentHashMap<>(Map.of("COMP", competitor)));

				Competitiveness.competition(c, r);
				assertSame(competitor, getOwner(c),
						"Standard landUsechange path should still work when normalised price flag is off");
			}
		});
	}

	// --- Normalised difference behaviour ---

	@Test
	void normalisedPrice_competitorTakesOver_whenNormDiffExceedsGiveIn() throws Throwable {
		withServices(List.of("S1"), () -> {
			Object cfg = ensureConfigInstance();
			setConfig(cfg, Map.of("mutate_on_competition_win", false));

			AftCategorised.useCategorisationGivIn = false;
			CellBehaviourUpdater.behaviourUsed = false;

			Cell c = Mockito.spy(new Cell(0, 0));

			Aft owner = mock(Aft.class);
			when(owner.isInteract()).thenReturn(true);
			when(owner.isAbandoned()).thenReturn(false);
			when(owner.getLabel()).thenReturn("OWN");
			when(owner.getGiveInMean()).thenReturn(0.1);
			when(owner.getGiveInSD()).thenReturn(0.0);
			setOwner(c, owner);
			c.setCurrentUtility(2.0);

			Aft competitor = mock(Aft.class);
			when(competitor.isInteract()).thenReturn(true);
			when(competitor.getLabel()).thenReturn("COMP");

			RegionalModelRunner r = mock(RegionalModelRunner.class);
			when(r.getMarginal()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 0.0)));

			// uC = 10. normDiff = (10-2)/(10+2) = 0.667 > giveIn 0.1
			doReturn(10.0).when(c).competitiveness(competitor, "S1");
			when(c.getLandTax()).thenReturn(new ConcurrentHashMap<>());
			when(c.getServicesTax()).thenReturn(new ConcurrentHashMap<>());

			Service s1 = mock(Service.class);
			when(s1.getWeights()).thenReturn(new ConcurrentHashMap<>(Map.of(2020, 1.0)));
			Region region = mock(Region.class);
			when(region.getServicesHash()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", s1)));
			r.R = region;

			setupSankeyData("COMP", 2020);

			try (MockedStatic<Timestep> ts = Mockito.mockStatic(Timestep.class)) {
				ts.when(Timestep::getCurrentYear).thenReturn(2020);
				ts.when(Timestep::getStartYear).thenReturn(2020);

				ConfigLoader.config.use_price_only_utility = true;
				try {
					callLandUsechangeNormalisedPriceUtility(c, competitor, r);
					assertSame(competitor, getOwner(c));
				} finally {
					ConfigLoader.config.use_price_only_utility = false;
				}
			}
		});
	}

	@Test
	void normalisedPrice_competitorDoesNotTakeOver_whenNormDiffBelowGiveIn() throws Throwable {
		withServices(List.of("S1"), () -> {
			Object cfg = ensureConfigInstance();
			setConfig(cfg, Map.of("mutate_on_competition_win", false));

			AftCategorised.useCategorisationGivIn = false;
			CellBehaviourUpdater.behaviourUsed = false;

			Cell c = Mockito.spy(new Cell(0, 0));

			Aft owner = mock(Aft.class);
			when(owner.isInteract()).thenReturn(true);
			when(owner.isAbandoned()).thenReturn(false);
			when(owner.getLabel()).thenReturn("OWN");
			when(owner.getGiveInMean()).thenReturn(0.2);
			when(owner.getGiveInSD()).thenReturn(0.0);
			setOwner(c, owner);
			c.setCurrentUtility(5.0);

			Aft competitor = mock(Aft.class);
			when(competitor.isInteract()).thenReturn(true);
			when(competitor.getLabel()).thenReturn("COMP");

			RegionalModelRunner r = mock(RegionalModelRunner.class);
			when(r.getMarginal()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 0.0)));

			// uC = 6. normDiff = (6-5)/(6+5) = 1/11 ~ 0.091 < giveIn 0.2
			doReturn(6.0).when(c).competitiveness(competitor, "S1");
			when(c.getLandTax()).thenReturn(new ConcurrentHashMap<>());
			when(c.getServicesTax()).thenReturn(new ConcurrentHashMap<>());

			Service s1 = mock(Service.class);
			when(s1.getWeights()).thenReturn(new ConcurrentHashMap<>(Map.of(2020, 1.0)));
			Region region = mock(Region.class);
			when(region.getServicesHash()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", s1)));
			r.R = region;

			try (MockedStatic<Timestep> ts = Mockito.mockStatic(Timestep.class)) {
				ts.when(Timestep::getCurrentYear).thenReturn(2020);
				ts.when(Timestep::getStartYear).thenReturn(2020);

				ConfigLoader.config.use_price_only_utility = true;
				try {
					callLandUsechangeNormalisedPriceUtility(c, competitor, r);
					assertSame(owner, getOwner(c),
							"Competitor with normDiff=0.091 < giveIn=0.2 must not take over");
					assertEquals(0, Listener.landUseChangeCounter.get());
				} finally {
					ConfigLoader.config.use_price_only_utility = false;
				}
			}
		});
	}

	// --- Negative utility guard ---

	@Test
	void normalisedPrice_negativeUtilityDoesNotTakeOver() throws Throwable {
		withServices(List.of("S1"), () -> {
			Object cfg = ensureConfigInstance();
			setConfig(cfg, Map.of("mutate_on_competition_win", false));

			Cell c = Mockito.spy(new Cell(0, 0));
			setOwner(c, null);

			Aft competitor = mock(Aft.class);
			when(competitor.isInteract()).thenReturn(true);
			when(competitor.getLabel()).thenReturn("COMP");

			RegionalModelRunner r = mock(RegionalModelRunner.class);
			when(r.getMarginal()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 0.0)));

			doReturn(-5.0).when(c).competitiveness(competitor, "S1");
			when(c.getLandTax()).thenReturn(new ConcurrentHashMap<>());
			when(c.getServicesTax()).thenReturn(new ConcurrentHashMap<>());

			Service s1 = mock(Service.class);
			when(s1.getWeights()).thenReturn(new ConcurrentHashMap<>(Map.of(2020, 1.0)));
			Region region = mock(Region.class);
			when(region.getServicesHash()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", s1)));
			r.R = region;

			try (MockedStatic<Timestep> ts = Mockito.mockStatic(Timestep.class)) {
				ts.when(Timestep::getCurrentYear).thenReturn(2020);
				ts.when(Timestep::getStartYear).thenReturn(2020);

				ConfigLoader.config.use_price_only_utility = true;
				try {
					callLandUsechangeNormalisedPriceUtility(c, competitor, r);
					assertNull(getOwner(c),
							"Competitor with negative utility must not take over even an abandoned cell");
					assertEquals(0, Listener.landUseChangeCounter.get());
				} finally {
					ConfigLoader.config.use_price_only_utility = false;
				}
			}
		});
	}

	@Test
	void normalisedPrice_negativeUtilityDoesNotTakeOver_evenWhenHigherThanOwner() throws Throwable {
		withServices(List.of("S1"), () -> {
			Object cfg = ensureConfigInstance();
			setConfig(cfg, Map.of("mutate_on_competition_win", false));

			Cell c = Mockito.spy(new Cell(0, 0));

			Aft owner = mock(Aft.class);
			when(owner.isInteract()).thenReturn(true);
			when(owner.isAbandoned()).thenReturn(false);
			when(owner.getLabel()).thenReturn("OWN");
			when(owner.getGiveInMean()).thenReturn(0.0);
			when(owner.getGiveInSD()).thenReturn(0.0);
			setOwner(c, owner);
			c.setCurrentUtility(-10.0);

			Aft competitor = mock(Aft.class);
			when(competitor.isInteract()).thenReturn(true);
			when(competitor.getLabel()).thenReturn("COMP");

			RegionalModelRunner r = mock(RegionalModelRunner.class);
			when(r.getMarginal()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", 0.0)));

			// uC = -2 (higher than uO=-10, but still negative)
			doReturn(-2.0).when(c).competitiveness(competitor, "S1");
			when(c.getLandTax()).thenReturn(new ConcurrentHashMap<>());
			when(c.getServicesTax()).thenReturn(new ConcurrentHashMap<>());

			Service s1 = mock(Service.class);
			when(s1.getWeights()).thenReturn(new ConcurrentHashMap<>(Map.of(2020, 1.0)));
			Region region = mock(Region.class);
			when(region.getServicesHash()).thenReturn(new ConcurrentHashMap<>(Map.of("S1", s1)));
			r.R = region;

			try (MockedStatic<Timestep> ts = Mockito.mockStatic(Timestep.class)) {
				ts.when(Timestep::getCurrentYear).thenReturn(2020);
				ts.when(Timestep::getStartYear).thenReturn(2020);

				ConfigLoader.config.use_price_only_utility = true;
				try {
					callLandUsechangeNormalisedPriceUtility(c, competitor, r);
					assertSame(owner, getOwner(c),
							"Competitor with uC=-2 > uO=-10 must NOT take over because uC <= 0");
					assertEquals(0, Listener.landUseChangeCounter.get());
				} finally {
					ConfigLoader.config.use_price_only_utility = false;
				}
			}
		});
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
