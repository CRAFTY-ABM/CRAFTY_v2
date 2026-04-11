package de.cesr.crafty.core.crafty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.afts.AftCategorised;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.output.Listener;
import de.cesr.crafty.core.output.Tracker;
import de.cesr.crafty.core.updaters.CellBehaviourUpdater;
import de.cesr.crafty.core.updaters.LandMaskUpdater;
import de.cesr.crafty.core.updaters.Timestep;
import de.cesr.crafty.core.utils.general.CellsSubSets;

/**
 * Implements the land-use competition mechanism that reallocates cell ownership among AFTs.
 *
 * This class evaluates the utility of candidate AFT on a cell and performs ownership change
 * when a competitor sufficiently outperforms the current owner. It supports both a standard utility
 * comparison and an optional behaviour/categorisation-aware “give-in” mechanism {@link CellBehaviour}.
 *
 * Core concepts:
 * - Utility calculation ({@link #utility(Cell, Aft, RegionalModelRunner)}):
 *   Utility is computed as the sum over services of (marginal utility + service tax/subsidy) multiplied
 *   by the cell’s productivity for the candidate AFT, plus an AFT-specific land tax/subsidy term.
 *
 * - Candidate set selection:
 *   Candidates are either all active AFTs or a neighbourhood-derived subset (extended Moore neighbourhood),
 *   depending on configuration ({@code use_neighbor_priority}, {@code neighbor_radius}).
 *
 * - Competitor choice:
 *   With probability {@code MostCompetitorAFTProbability}, the best-performing candidate (higher  utility) is chosen;
 *   otherwise a random AFT is tested. This provides a mix of deterministic pressure and stochastic exploration.
 *
 * - Constraints (masks/restrictions):
 *   {@link LandMaskUpdater#restrictions} can forbid specific transitions based on a cell mask type
 *   (e.g., “owner -> competitor” pairs), preventing competition where policy or protection rules apply.
 *
 * Ownership change rules:
 * - If a cell is unmanaged/abandoned, a competitor may take over when its utility meets the acceptance
 *   criterion (mean distribution threshold or positive normalised utility, depending on mode).
 * - If the cell has an interacting owner, the competitor must exceed the current owner by a threshold:
 *   either a simple give-in threshold (drawn from owner parameters or category-pair distributions) or,
 *   when enabled, a behaviour-based give-in value computed by {@link CellBehaviour}.
 *
 * Mutation on win:
 * If {@code mutate_on_competition_win} is enabled, the new owner is cloned via {@link Aft#Aft(Aft)} to
 * introduce small random parameter variation; otherwise the competitor instance is reused.
 *
 * Every successful land-use change increments {@link Listener#landUseChangeCounter}.
 */

/**
 * @author Mohamed Byari
 *
 */

public class Competitiveness {

	private static final boolean COUPLED_WITH_PLUM = ConfigLoader.config.COUPLED_WITH_PLUM;

	private static double utility(Cell c, Aft a, RegionalModelRunner r) {
		if (COUPLED_WITH_PLUM) {
			return utilityUseOnlyPrice(c, a, r);
		}
		return utilityUseMarginal(c, a, r);
	}

	private static double utilityUseMarginal(Cell c, Aft a, RegionalModelRunner r) {
		if (a == null || !a.isInteract()) {
			return 0;
		}
		// u= sum_s[ (ms+ts*d0)ps]+ land_ts*abs(u1)
		return ServiceSet.getServicesList().stream()
				.mapToDouble(serviceName -> (r.getServiceTax().get(serviceName) + r.getMarginal().get(serviceName))
						* c.productivity(a, serviceName))
				.sum() + a.getCachedLandTax();
	}

//	## only in coupling withPlum
	private static double utilityUseOnlyPrice(Cell c, Aft a, RegionalModelRunner r) {
		if (a == null || !a.isInteract()) {
			return 0;
		}

		double sum = ServiceSet.getServicesList().stream().mapToDouble(serviceName -> {
			Service service = r.R.getServicesHash().get(serviceName);

			double currentWeight = service.getWeights().get(Timestep.getCurrentYear());
			double initialWeight = service.getWeights().get(Timestep.getStartYear());

			double result = (currentWeight / initialWeight) * c.productivity(a, serviceName);
			if (Double.isNaN(result) || Double.isInfinite(result)) {
				return 0.0;
			}
			return result;
		}).sum();

		return sum;
	}
//	####

	static void associateUtility(Cell c, RegionalModelRunner r) {
		c.setCurrentUtility(utility(c, c.owner, r));
	}

//	public static Aft mostCompetitiveAgent(Cell c, Collection<Aft> setAfts, RegionalModelRunner r) {
//		if (setAfts.size() == 0) {
//			return c.owner;
//		}
//		double uti = Double.NEGATIVE_INFINITY;
//		Aft theBestAFT = setAfts.iterator().next();
//		for (Aft agent : setAfts) {
//			double u = utility(c, agent, r);
//			if (u > uti) {
//				uti = u;
//				theBestAFT = agent;
//			}
//		}
//		return theBestAFT;
//	}

	private static void Competition(Cell c, Aft competitor, RegionalModelRunner r) {
		if (competitor == null || !competitor.isInteract()) {
			return;
		}
		if (makeCompetition(c, competitor)) {

//			if (AftCategorised.useCategorisationGivIn && CellBehaviourUpdater.behaviourUsed) {
//				landUsechangeNormalisedUtility(c, competitor, r);
//			} else {
			landUsechange(c, competitor, r);

		}
	}

	private static boolean makeCompetition(Cell c, Aft competitor) {

//####### tmp for special rules
//########### no deforestation ##############
//		if (c.getOwner() != null && c.getOwner().getCategory() != null && competitor != null
//				&& competitor.getCategory() != null) {
//			boolean tmp = (c.getOwner().getCategory().getName().equals("forest"))
//					&& (!competitor.getCategory().getName().equals("forest") || competitor.getLabel().equals("AF"));
//			if (tmp) {
//				return false;
//			}
//		}
//		################### protecte CW ###########
//		if (c.getOwner() != null && c.getOwner().getLabel() != null && c.getOwner().getLabel().equals("CW")) {
//			return false;
//		}
//############ CW can't compite for land ##########
//		if (competitor != null && competitor.getLabel() != null && competitor.getLabel().equals("CW")) {
//		return false;
//	}
//		###############
		if (c.owner == competitor) {
			return false;
		}

		boolean makeCompetition = true;
		if (c.getMaskType() != null) {
			ConcurrentHashMap<String, Boolean> mask = LandMaskUpdater.restrictions.get(c.getMaskType());
			if (mask != null) {
				if (c.owner == null) {
					if (mask.get(competitor.getLabel() + "_" + competitor.getLabel()) != null)
						makeCompetition = mask.get(competitor.getLabel() + "_" + competitor.getLabel());
				} else {
					if (mask.get(c.owner.getLabel() + "_" + competitor.getLabel()) != null)
						makeCompetition = mask.get(c.owner.getLabel() + "_" + competitor.getLabel());
				}
			}
		} else if (c.owner != null && c.getOwnerLifeCounter() < c.owner.getMin_life_cycle()) {
			makeCompetition = false;
		}
		return makeCompetition;

	}

	private static void landUsechange(Cell c, Aft competitor, RegionalModelRunner r) {
		double uC = utility(c, competitor, r);
		if (c.owner == competitor) {
			return;
		}
		if (c.owner == null || c.owner.isAbandoned()) {
			if (uC >= r.getDistributionMeanY().get(competitor)) {
				takeOverAcell(c, competitor);
			}
			return;
		}
		double uO = c.getCurrentUtility();
		double nbr = r.getDistributionMeanY() != null
				? (r.getDistributionMeanY().get(c.owner) * (giveInThreshold(c.owner, competitor)))
				: 0;
		if ((uC - uO > nbr) && uC > 0) {
			takeOverAcell(c, competitor);
		}
	}

	private static void landUsechangeNormalisedUtility(Cell c, Aft competitor, RegionalModelRunner r) {
		if (r.getMaxUtility() == r.getMinUtility()) {
			return;
		}
		if (c.owner == competitor) {
			return;
		}
		double uC = (utility(c, competitor, r) - r.getMinUtility()) / (r.getMaxUtility() - r.getMinUtility());
		if (c.owner == null || c.owner.isAbandoned()) {
			if (uC > 0) {
				takeOverAcell(c, competitor);
			}
			return;
		}

		double uO = (c.getCurrentUtility() - r.getMinUtility()) / (r.getMaxUtility() - r.getMinUtility());

		double giveIn = 0;
		boolean sameCategories = c.owner.category.getName().equals(competitor.category.getName());
		boolean sameIntesity = c.owner.category.getIntensityLevel() == (competitor.category.getIntensityLevel());

		if (!sameCategories || (sameCategories && sameIntesity)) {
			giveIn = giveInThreshold(c.owner, competitor);
		} else {
			giveIn = CellBehaviourUpdater.cellsBehevoir.get(c).give_In(competitor);
		}

		if ((uC > uO + giveIn)) {
			takeOverAcell(c, competitor);
		}
	}

	private static void takeOverAcell(Cell c, Aft newOwner) {
		String oldOwner = c.owner != null ? c.owner.getLabel() : "Abandoned";

		c.owner = ConfigLoader.config.mutate_on_competition_win ? new Aft(newOwner) : newOwner;
		c.setOwnerLifeCounter(1);
		Listener.landUseChangeCounter.getAndIncrement();
		if (newOwner.getLabel() != null) {
			Listener.newAftsInLandNbr.merge(newOwner.getLabel(), 1, Integer::sum);
			Tracker.sankeydata.get(newOwner.getLabel()).get(Timestep.getCurrentYear()).merge(oldOwner, 1, Integer::sum);
		}
	}

	private static double giveInThreshold(Aft owner, Aft competitor) {
		if (AftCategorised.useCategorisationGivIn) {
			String key = owner.getCategory().getName() + "|" + competitor.getCategory().getName();
			Double mean = AftCategorised.getMean().get(key);
			Double sd = AftCategorised.getSD().get(key);
			// Only use the BehaviorLoader-based mean & sd if BOTH are present AND the
			// categories differ.
			if (mean != null && sd != null) {
				return mean + sd * ThreadLocalRandom.current().nextGaussian();
			}
		}
		// else Fallback to the default owner's giveInMean
		return owner.getGiveInMean() + owner.getGiveInSD() * new Random().nextGaussian();
	}

	static void competition(Cell c, RegionalModelRunner r) {
		boolean Neighboor = ConfigLoader.config.use_neighbor_priority
				&& ConfigLoader.config.neighbor_priority_probability > Math.random();
		Collection<Aft> afts = Neighboor
				? CellsSubSets.detectExtendedNeighboringAFTs(c, ConfigLoader.config.neighbor_radius)
				: AFTsLoader.getActivateAFTsHash().values();

		if (Math.random() < ConfigLoader.config.MostCompetitorAFTProbability) {
//			mostCompetitiveAgent(c, afts, r);
			Competition(c, mostCompetitiveAgent(c, afts, r), r);
		} else {
			Competition(c, AFTsLoader.getRandomAFT(afts), r);
		}
	}

	public static Aft mostCompetitiveAgent(Cell c, Collection<Aft> setAfts, RegionalModelRunner r) {
		if (setAfts == null || setAfts.isEmpty()) {
			return c.owner;
		}

		final double EPS = 1e-12;
		double bestUtility = Double.NEGATIVE_INFINITY;

		List<Aft> winners = new ArrayList<>();

		for (Aft agent : setAfts) {
			double u = utility(c, agent, r);

			if (u > bestUtility + EPS) {
				bestUtility = u;
				winners.clear();
				winners.add(agent);
			} else if (Math.abs(u - bestUtility) <= EPS) {
				winners.add(agent);
			}
		}

		if (winners.size() == 1) {
			return winners.get(0);
		}

		// deterministic pseudo-random tie-break
		Aft bestAft = winners.get(0);
		long bestScore = tieBreakScore(c.getX(), c.getY(), getStableAftId(bestAft), r);

		for (int i = 1; i < winners.size(); i++) {
			Aft aft = winners.get(i);
			long score = tieBreakScore(c.getX(), c.getY(), getStableAftId(aft), r);

			if (Long.compareUnsigned(score, bestScore) < 0) {
				bestScore = score;
				bestAft = aft;
			}
		}

		return bestAft;
	}

	private static int getStableAftId(Aft aft) {
		return aft.getLabel().hashCode();
	}

	private static long tieBreakScore(int x, int y, int aftId, RegionalModelRunner r) {
		long seed = 0x9E3779B97F4A7C15L;
		seed ^= mix64(x);
		seed ^= mix64(y);
		seed ^= mix64(aftId);

		return mix64(seed);
	}

	private static long mix64(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		z = z ^ (z >>> 31);
		return z;
	}

}
