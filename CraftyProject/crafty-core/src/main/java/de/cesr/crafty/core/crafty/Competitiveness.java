package de.cesr.crafty.core.crafty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

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
import de.cesr.crafty.core.utils.general.DeterministicRandom;

/**
 * Implements the land-use competition mechanism that reallocates cell ownership among AFTs.
 *
 * This class evaluates the utility of candidate AFT on a cell and performs ownership change
 * when a competitor sufficiently outperforms the current owner. It supports both a standard utility
 * comparison and an optional behaviour/categorisation-aware "give-in" mechanism {@link CellBehaviour}.
 *
 * Core concepts:
 * - Utility calculation ({@link #utility(Cell, Aft, RegionalModelRunner)}):
 *   Utility is computed from marginal utility and productivity. When cell-level policy effects are enabled,
 *   cell service and land taxes/subsidies are included as additional terms.
 *
 * - Candidate set selection:
 *   Candidates are either all active AFTs or a neighbourhood-derived subset (extended Moore neighbourhood),
 *   depending on configuration ({@code use_neighbour_priority}, {@code neighbour_radius}).
 *
 * - Competitor choice:
 *   With probability {@code most_competitive_aft_probability}, the best-performing candidate (higher  utility) is chosen;
 *   otherwise a random AFT is tested. This provides a mix of deterministic pressure and stochastic exploration.
 *
 * - Constraints (masks/restrictions):
 *   {@link LandMaskUpdater#restrictions} can forbid specific transitions based on a cell mask type
 *   (e.g., "owner -> competitor" pairs), preventing competition where policy or protection rules apply.
 *
 * Ownership change rules:
 * - If a cell is unmanaged/abandoned, a competitor may take over when its utility meets the acceptance
 *   criterion (mean distribution threshold or positive normalised utility, depending on mode).
 * - If the cell has an interacting owner, the competitor must exceed the current owner by a threshold:
 *   either a simple give-in threshold (drawn from owner parameters or category-pair distributions) or,
 *   when enabled, a behaviour-based give-in value computed by {@link CellBehaviour}.
 *
 * Every successful land-use change increments {@link Listener#landUseChangeCounter}.
 */

/**
 * @author Mohamed Byari
 *
 */

public class Competitiveness {
    /** Immutable ownership change calculated without mutating model state. */
    static record CompetitionDecision(Cell cell, Aft newOwner) {
    }

    // ── Utility calculation ─────────────────────────────────────────────

    static double utility(Cell c, Aft a, RegionalModelRunner r) {
        if (ConfigLoader.config.use_explicit_price_utility) {
            return utilityUseExplicitPrice(c, a, r);
        }
        if (ConfigLoader.config.use_price_only_utility) {
            return utilityUseOnlyPrice(c, a, r);
        }
        if (ConfigLoader.config.use_cell_level_taxes) {
            return utilityUseMarginalWithTaxes(c, a, r);
        }
        return utilityUseMarginal(c, a, r);
    }

    private static double utilityUseMarginal(Cell c, Aft a, RegionalModelRunner r) {
        if (a == null || !a.isInteract()) {
            return 0;
        }
        // u= sum_s[ms*ps]
        return ServiceSet.getServicesList().stream()
                .mapToDouble(serviceName -> r.getMarginal().get(serviceName) * c.competitiveness(a, serviceName))
                .sum();
    }

    private static double utilityUseMarginalWithTaxes(Cell c, Aft a, RegionalModelRunner r) {
        if (a == null || !a.isInteract()) {
            return 0;
        }
        // u= sum_s[ (ms+ts*d0)ps]+ land_ts*abs(u1)
        return ServiceSet.getServicesList().stream()
                .mapToDouble(serviceName -> (c.getServicesTax().getOrDefault(serviceName, 1d)
                        * (r.initial_service_gaps.get(serviceName)) + r.getMarginal().get(serviceName))
                        * c.competitiveness(a, serviceName))
                .sum()
                + c.getLandTax().getOrDefault(a.getLabel(), 0d)
                * (r.initialutilityAverage.getOrDefault(a.getLabel(), 1d));
    }

    private static double priceBasedUtility(Cell c, Aft a, RegionalModelRunner r,
                                            boolean normaliseToBaseline, boolean subtractCosts) {
        if (a == null || !a.isInteract()) {
            return 0;
        }
        double revenue = ServiceSet.getServicesList().stream().mapToDouble(serviceName -> {
            Service service = r.R.getServicesHash().get(serviceName);
            double weight = service.getWeights().get(Timestep.getCurrentYear());
            if (normaliseToBaseline) {
                weight = weight / service.getWeights().get(Timestep.getStartYear());
            }
            double result = weight * c.competitiveness(a, serviceName);
            if (Double.isNaN(result) || Double.isInfinite(result)) {
                return 0.0;
            }
            return result;
        }).sum();
        return subtractCosts ? revenue - c.productionCost(a) : revenue;
    }

    private static double utilityUseExplicitPrice(Cell c, Aft a, RegionalModelRunner r) {
        return priceBasedUtility(c, a, r, false, true);
    }

    private static double utilityUseOnlyPrice(Cell c, Aft a, RegionalModelRunner r) {
        return priceBasedUtility(c, a, r, true, false);
    }

    // ── Land-use change decision logic ──────────────────────────────────

    private static CompetitionDecision evaluateCandidate(Cell c, Aft owner, Aft competitor, RegionalModelRunner r) {
        if (competitor == null || !competitor.isInteract()) {
            return null;
        }
        if (!makeCompetition(c, owner, competitor)) {
            return null;
        }

        boolean changesOwner;
        if (ConfigLoader.config.use_normalised_price_competition) {
            changesOwner = landUsechangeNormalisedPriceUtility(c, owner, competitor, r);
        } else if (AftCategorised.useCategorisationGivIn && CellBehaviourUpdater.behaviourUsed) {
            changesOwner = landUsechangeNormalisedUtility(c, owner, competitor, r);
        } else {
            changesOwner = landUsechange(c, owner, competitor, r);
        }
        return changesOwner ? new CompetitionDecision(c, competitor) : null;
    }

    private static boolean makeCompetition(Cell c, Aft owner, Aft competitor) {
        if (owner == competitor) {
            return false;
        }

        boolean makeCompetition = true;
        if (c.getMaskType() != null) {
            ConcurrentHashMap<String, Boolean> mask = LandMaskUpdater.restrictions.get(c.getMaskType());
            if (mask != null) {
                if (owner == null) {
                    if (mask.get(competitor.getLabel() + "_" + competitor.getLabel()) != null)
                        makeCompetition = mask.get(competitor.getLabel() + "_" + competitor.getLabel());
                } else {
                    if (mask.get(owner.getLabel() + "_" + competitor.getLabel()) != null)
                        makeCompetition = mask.get(owner.getLabel() + "_" + competitor.getLabel());
                }
            }
        } else if (owner != null && c.getOwnerLifeCounter() < owner.getMin_life_cycle()) {
            makeCompetition = false;
        }
        return makeCompetition;
    }

    private static boolean landUsechange(Cell c, Aft owner, Aft competitor, RegionalModelRunner r) {
        double uC = utility(c, competitor, r);
        if (owner == competitor) {
            return false;
        }
        if (owner == null || owner.isAbandoned()) {
            return uC >= r.getDistributionMeanY().get(competitor.getLabel());
        }
        double uO = c.getCurrentUtility();

        double nbr = r.getDistributionMeanY() != null
                ? (r.getDistributionMeanY().get(owner.getLabel()) * (giveInThreshold(c, owner, competitor)))
                : 0;

        return (uC - uO > nbr) && uC > 0;
    }

    private static boolean landUsechangeNormalisedUtility(Cell c, Aft owner, Aft competitor, RegionalModelRunner r) {
        if (r.getMaxUtility() == r.getMinUtility()) {
            return false;
        }
        if (owner == competitor) {
            return false;
        }
        double uC = (utility(c, competitor, r) - r.getMinUtility()) / (r.getMaxUtility() - r.getMinUtility());

        if (owner == null || owner.isAbandoned()) {
            return uC > 0;
        }

        double uO = (c.getCurrentUtility() - r.getMinUtility()) / (r.getMaxUtility() - r.getMinUtility());
        double giveIn = effectiveGiveIn(c, owner, competitor);
        return (uC > uO + giveIn) && uC > 0;
    }

    private static boolean landUsechangeNormalisedPriceUtility(Cell c, Aft owner, Aft competitor,
                                                               RegionalModelRunner r) {
        if (owner == competitor) {
            return false;
        }
        double uC = utility(c, competitor, r);
        if (uC <= 0) {
            return false;
        }
        if (owner == null || owner.isAbandoned()) {
            return true;
        }
        double uO = c.getCurrentUtility();
        double normDiff = (uC - uO) / (Math.abs(uC) + Math.abs(uO));

        double giveIn;
        if (AftCategorised.useCategorisationGivIn && CellBehaviourUpdater.behaviourUsed) {
            giveIn = effectiveGiveIn(c, owner, competitor);
        } else {
            giveIn = giveInThreshold(c, owner, competitor);
        }
        return normDiff > giveIn;
    }

    private static double effectiveGiveIn(Cell c, Aft owner, Aft competitor) {
        boolean sameCategories = owner.category.getName().equals(competitor.category.getName());
        boolean sameIntensity = owner.category.getIntensityLevel() == competitor.category.getIntensityLevel();

        if (!sameCategories || sameIntensity) {
            return giveInThreshold(c, owner, competitor);
        }
        CellBehaviour behaviour = CellBehaviourUpdater.cellBehaviours.get(c);
        if (behaviour != null) {
            return behaviour.give_In(competitor);
        }
        return 0;
    }

    // ── Twin competition ────────────────────────────────────────────────

    static void twinCompetition(Cell c, RegionalModelRunner r) {
        Aft owner = c.getOwner();
        if (owner == null || !owner.isInteract() || !owner.hasTwin()) return;
        Aft twin = AFTsLoader.getAftHash().get(owner.getTwinLabel());
        if (twin == null || !twin.isInteract() || !makeCompetition(c, owner, twin)) return;

        if (ConfigLoader.config.use_twinned_cost) {
            double uTwin = utility(c, twin, r);
            double uOwner = c.getCurrentUtility();
            if (uTwin > 0 && uTwin > uOwner + twin.getTwinCost()) {
                takeOverAcell(c, twin, r);
            }
        } else {
            boolean changesOwner;
            if (ConfigLoader.config.use_normalised_price_competition) {
                changesOwner = landUsechangeNormalisedPriceUtility(c, owner, twin, r);
            } else if (AftCategorised.useCategorisationGivIn && CellBehaviourUpdater.behaviourUsed) {
                changesOwner = landUsechangeNormalisedUtility(c, owner, twin, r);
            } else {
                changesOwner = landUsechange(c, owner, twin, r);
            }
            if (changesOwner) {
                takeOverAcell(c, twin, r);
            }
        }
    }

    // ── Cell takeover ───────────────────────────────────────────────────

    private static void takeOverAcell(Cell c, Aft newOwner, RegionalModelRunner r) {
        String oldOwner = c.getOwner() != null ? c.getOwner().getLabel() : "Abandoned";

        c.setOwner(newOwner);
        // Later same-tick phases compare against currentUtility, so it must
        // reflect the new owner immediately, not at the next global refresh.
        c.setCurrentUtility(utility(c, newOwner, r));
//		CellsUpdater.decesionsNewOwner.put(c, newOwner);
        c.setOwnerLifeCounter(1);
        Listener.landUseChangeCounter.getAndIncrement();
        if (newOwner.getLabel() != null) {
            Listener.newAftsInLandNbr.merge(newOwner.getLabel(), 1, Integer::sum);
            Tracker.sankeydata.get(newOwner.getLabel()).get(Timestep.getCurrentYear()).merge(oldOwner, 1, Integer::sum);
        }
    }

    // ── Give-in threshold ───────────────────────────────────────────────

    private static double giveInThreshold(Cell cell, Aft owner, Aft competitor) {
        long runSeed = ConfigLoader.config.random_seed;
        int year = Timestep.getCurrentYear();

        long cellId = DeterministicRandom.stableCellKey(cell);
        long pairId = DeterministicRandom.hashString64(owner.getLabel() + "|" + competitor.getLabel());

        double gaussian = DeterministicRandom.randomGaussian(runSeed, year,
                DeterministicRandom.Process.GIVE_IN_THRESHOLD, cellId, pairId, 0);

        if (AftCategorised.useCategorisationGivIn) {
            String key = owner.getCategory().getName() + "|" + competitor.getCategory().getName();
            Double mean = AftCategorised.getMean().get(key);
            Double sd = AftCategorised.getSD().get(key);

            if (mean != null && sd != null) {
                return mean + sd * gaussian;
            }
        }
        return owner.getGiveInMean() + owner.getGiveInSD() * gaussian;
    }

    // ── Competition entry points ────────────────────────────────────────

    static void competition(Cell c, RegionalModelRunner r) {
        competition(c, r, DeterministicRandom.Process.CELL_SELECTION_COMPETITION);
    }

    static void competition(Cell c, RegionalModelRunner r, int decisionContext) {
        applyCompetitionDecision(evaluateCompetition(c, r, decisionContext), r);
    }

    static CompetitionDecision evaluateCompetition(Cell c, RegionalModelRunner r, int decisionContext) {
        Aft owner = c.getOwner();
        long runSeed = ConfigLoader.config.random_seed;
        int year = Timestep.getCurrentYear();
        long cellId = DeterministicRandom.stableCellKey(c);

        boolean useNeighbors = ConfigLoader.config.use_neighbour_priority
                && DeterministicRandom.randomBoolean(runSeed, year, DeterministicRandom.Process.NEIGHBOR_PICK, cellId,
                decisionContext, 0, ConfigLoader.config.neighbour_priority_probability);
        Collection<Aft> afts = useNeighbors
                ? CellsSubSets.detectExtendedNeighboringAFTs(c, ConfigLoader.config.neighbour_radius)
                : AFTsLoader.getActivateAFTsHash().values();

        boolean chooseMostCompetitive = DeterministicRandom.randomBoolean(runSeed, year,
                DeterministicRandom.Process.COMPETITOR_PICK, cellId, decisionContext, 0,
                ConfigLoader.config.most_competitive_aft_probability);
        if (chooseMostCompetitive) {
            return evaluateCandidate(c, owner, mostCompetitiveAgent(c, afts, r), r);
        } else {
            return evaluateCandidate(c, owner,
                    AFTsLoader.getDeterministicRandomAFT(afts, runSeed, year, cellId, decisionContext, 0), r);
        }
    }

    static void applyCompetitionDecision(CompetitionDecision decision, RegionalModelRunner r) {
        if (decision != null) {
            takeOverAcell(decision.cell(), decision.newOwner(), r);
        }
    }

    public static Aft mostCompetitiveAgent(Cell c, Collection<Aft> setAfts, RegionalModelRunner r) {
        if (setAfts == null || setAfts.isEmpty()) {
            return c.getOwner();
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
        winners.sort(Comparator.comparing(Aft::getLabel));
        return winners.get(0);
    }

}
