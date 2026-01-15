package de.cesr.crafty.core.crafty;

import java.util.Collection;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.afts.AftCategorised;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.output.Listener;
import de.cesr.crafty.core.updaters.CellBehaviourUpdater;
import de.cesr.crafty.core.updaters.LandMaskUpdater;
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

	static double utility(Cell c, Aft a, RegionalModelRunner r) {
		if (a == null || !a.isInteract()) {
			return 0;
		}
		// u= sum_s[ (ms+ts*d0)ps]+ land_ts*abs(u1)
		return ServiceSet.getServicesList().stream()
				.mapToDouble(serviceName -> (r.getServiceTax().get(serviceName) + r.getMarginal().get(serviceName))
						* c.productivity(a, serviceName))
				.sum() + a.getCachedLandTax();
	}

	static void associateUtility(Cell c, RegionalModelRunner r) {
		c.setcCurrentUtility(utility(c, c.owner, r));
	}

	static Aft mostCompetitiveAgent(Cell c, Collection<Aft> setAfts, RegionalModelRunner r) {
		if (setAfts.size() == 0) {
			return c.owner;
		}
		double uti = 0;
		Aft theBestAFT = setAfts.iterator().next();
		for (Aft agent : setAfts) {
			double u = utility(c, agent, r);
			if (u > uti) {
				uti = u;
				theBestAFT = agent;
			}
		}
		return theBestAFT;
	}

	private static void Competition(Cell c, Aft competitor, RegionalModelRunner r) {
		if (competitor == null || !competitor.isInteract()) {
			return;
		}
		if (makeCompetition(c, competitor)) {
			if (AftCategorised.useCategorisationGivIn && CellBehaviourUpdater.behaviourUsed) {
				landUsechangeNormalisedUtility(c, competitor, r);
			} else {
				landUsechange(c, competitor, r);
			}

		}
	}

	private static boolean makeCompetition(Cell c, Aft competitor) {
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
		c.owner = ConfigLoader.config.mutate_on_competition_win ? new Aft(newOwner) : newOwner;
		c.setOwnerLifeCounter(1);
		Listener.landUseChangeCounter.getAndIncrement();
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
			mostCompetitiveAgent(c, afts, r);
			Competition(c, mostCompetitiveAgent(c, afts, r), r);
		} else {
			Competition(c, AFTsLoader.getRandomAFT(afts), r);
		}
	}

}
