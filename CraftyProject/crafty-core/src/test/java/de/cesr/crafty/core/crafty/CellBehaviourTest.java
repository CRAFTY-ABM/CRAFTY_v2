package de.cesr.crafty.core.crafty;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.cesr.crafty.core.dataLoader.afts.AftCategorised;
import de.cesr.crafty.core.updaters.CellBehaviourUpdater;

class CellBehaviourTest {

	private Aft owner;
	private Aft competitorSameCategory;
	private Aft competitorHigherIntensity;
	private Cell cell;
	private CellBehaviour behaviour;

	@BeforeEach
	void setUp() {
		AftCategory lowIntensity = new AftCategory("CAT", "low", 0);
		AftCategory highIntensity = new AftCategory("CAT", "high", 2);

		// --- Owner AFT ---
		owner = new Aft("Owner");
		owner.category = lowIntensity; 

		// --- Competitor with same category (same intensity level) ---
		competitorSameCategory = new Aft("CompetitorSame");
		competitorSameCategory.category = lowIntensity;

		// --- Competitor with higher intensity in same category name ---
		competitorHigherIntensity = new Aft("CompetitorHigh");
		competitorHigherIntensity.category = highIntensity;

		// --- Cell owned by 'owner' 
		cell = new Cell(0, 0);
		cell.owner = owner; 

		// --- CellBehaviour with some parameter values ---
		behaviour = new CellBehaviour(cell);
		behaviour.setAttitude_intensification(0.6); // arbitrary positive value
		behaviour.setWeight_inertia(0.3);
		behaviour.setWeight_social(0.5);
		behaviour.setCritical_mass(0.4);
		behaviour.setNeighborhood_size(0); // expect no neighbours -> empty set
		behaviour.setMaxGive_in(1.0);

		// Enable behaviour / categorisation flags (so social_influence branch is
		// active)
		AftCategorised.useCategorisationGivIn = true;
		CellBehaviourUpdater.behaviourUsed = true;
	}

	@Test
	void giveIn_sameCategoryAndIntensity_returnsHalfOfMaxGiveIn() {
		// When owner and competitor share the same category name and intensity:
		// - intensificationGap = 0
		// - Attitude_influence = 0
		// - social_influence = 0 (early return in social_influence)
		//
		// => exponent = 0, so give_In = maxGive_in / (1 + exp(0)) = maxGive_in / 2

		double result = behaviour.give_In(competitorSameCategory);

		assertEquals(behaviour.getMaxGive_in() / 2.0, result, 1e-12,
				"For same category & intensity, give_In should be exactly maxGive_in / 2");
	}

	@Test
	void giveIn_usesLogisticFormulaWithExpectedComponents() {
		// For a competitor with higher intensity in the same category name, and with
		// neighborhood_size = 0, we expect:
		// - intensificationGap > 0
		// - fractionOfNeighbors(...) = 0 (empty neighbours)
		// - social_influence = max(min(2*0 - Critical_mass, 1), -1)
		// = -Critical_mass (since Critical_mass in (0,1))
		//
		// Then:
		// attitudeInfluence = clamp(signum(gap) * Attitude_intensification, -1, 1)
		// socialInfluence = -Critical_mass
		// exponent = steepness * ((1 - w_social)*attitudeInfluence +
		// w_social*socialInfluence)
		// + w_inertia * |gap|
		// give_In = maxGive_in / (1 + exp(exponent))

		// 1) Compute expected value using the same formula
		int gap = competitorHigherIntensity.getCategory().getIntensityLevel() - owner.getCategory().getIntensityLevel();

		double attitudeInfluence = Math.signum(gap) * behaviour.getAttitude_intensification();
		attitudeInfluence = Math.max(Math.min(attitudeInfluence, 1.0), -1.0);

		double socialInfluence = -behaviour.getCritical_mass(); // neighbours empty

		double combinedInfluence = (1.0 - behaviour.getWeight_social()) * attitudeInfluence
				+ behaviour.getWeight_social() * socialInfluence;

		double exponent = behaviour.steepness_logistic_eq * combinedInfluence
				+ behaviour.getWeight_inertia() * Math.abs(gap);

		double expected = behaviour.getMaxGive_in() / (1.0 + Math.exp(exponent));

		// 2) Call the actual method
		double actual = behaviour.give_In(competitorHigherIntensity);

		// 3) Compare with tight tolerance
		assertEquals(expected, actual, 1e-12, "give_In should follow the logistic formula defined in CellBehaviour");
	}

	@Test
	void giveIn_isAlwaysBetweenZeroAndMaxGiveIn() {
		// A generic sanity check for bounds
		double resultSame = behaviour.give_In(competitorSameCategory);
		double resultHigh = behaviour.give_In(competitorHigherIntensity);

		assertTrue(resultSame >= 0.0 && resultSame <= behaviour.getMaxGive_in(),
				"give_In should be between 0 and maxGive_in for same-category competitor");
		assertTrue(resultHigh >= 0.0 && resultHigh <= behaviour.getMaxGive_in(),
				"give_In should be between 0 and maxGive_in for higher-intensity competitor");
	}

	@Test
	void toString_containsKeyBehaviourParameters() {
		String s = behaviour.toString();

		assertTrue(s.contains("Attitude_intensification=" + behaviour.getAttitude_intensification()));
		assertTrue(s.contains("Weight_inertia=" + behaviour.getWeight_inertia()));
		assertTrue(s.contains("weight_social=" + behaviour.getWeight_social()));
		assertTrue(s.contains("Critical_mass=" + behaviour.getCritical_mass()));
		assertTrue(s.contains("neighborhood_size=" + behaviour.getNeighborhood_size()));
		assertTrue(s.contains("maxGive_in=" + behaviour.getMaxGive_in()));
	}
}
