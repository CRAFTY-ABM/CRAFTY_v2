package de.cesr.crafty.core.crafty;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.dataLoader.afts.AftCategorised;
import de.cesr.crafty.core.updaters.CellBehaviourUpdater;
import de.cesr.crafty.core.utils.general.CellsSubSets;

/**
 * Behavioural “give-in” model for land-use change at the cell level.
 *
 * This class computes the probability/propensity that a cell’s current owner will give in to a competing
 * {@link Aft}, based on a logistic decision rule combining:
 * - Attitude influence (a directional preference towards intensification or extensification).
 * - Social influence (pressure from neighbouring cells / peer effects).
 * - Inertia (resistance to change, increasing with the intensity gap between the current owner and competitor).
 *
 * The main entry point is {@link #give_In(Aft)}, which returns a value bounded by {@link #maxGive_in}
	 * using a logistic function (steepness controlled by the configured cell behaviour logistic steepness).
 *
 * Social influence is only active when categorisation-based give-in is enabled
 * ({@link AftCategorised#useCategorisationGivIn}) and the behaviour module is switched on
 * ({@link CellBehaviourUpdater#behaviourUsed}). Neighbour influence is derived from the extended Moore
 * neighbourhood around the focal cell via {@link CellsSubSets#detectExtendedNeighboringAFTs(Cell, int)}.
 *
 * Parameters:
 * - {@link #Attitude_intensification}: strength of attitude towards intensity change direction.
 * - {@link #weight_social}: weight of social vs. attitude components in the combined signal.
 * - {@link #Critical_mass}: threshold fraction of neighbours required to produce net social pressure.
 * - {@link #neighborhood_size}: radius used to define the neighbourhood.
 * - {@link #Weight_inertia}: penalty applied to large intensity gaps (reduces give-in).
 * - {@link #maxGive_in}: upper bound for the give-in outcome.
 */

/**
 * @author Mohamed Byari
 *
 */
public class CellBehaviour {
	private static final List<String> PARAMETER_NAMES = List.of(
			"Attitude_intensification",
			"Weight_inertia",
			"Weight-social",
			"Critical_mass",
			"MaxGive_in",
			"Neighborhood_size");

	double Attitude_intensification;
	double Weight_inertia;//
	double weight_social;
	double Critical_mass;//
	int neighborhood_size;
	double maxGive_in;
	private Cell c;

	public CellBehaviour(Cell c) {
		this.c = c;
	}

	public static List<String> parameterNames() {
		return PARAMETER_NAMES;
	}

	public double parameterValue(String parameterName) {
		if (parameterName == null) {
			throw unknownParameter(null);
		}
		String normalised = parameterName.trim().toLowerCase(Locale.ROOT).replace('-', '_');
		return switch (normalised) {
		case "attitude_intensification" -> getAttitude_intensification();
		case "weight_inertia" -> getWeight_inertia();
		case "weight_social" -> getWeight_social();
		case "critical_mass" -> getCritical_mass();
		case "maxgive_in", "max_give_in" -> getMaxGive_in();
		case "neighborhood_size", "neighbourhood_size" -> getNeighborhood_size();
		default -> throw unknownParameter(parameterName);
		};
	}

	private static IllegalArgumentException unknownParameter(String parameterName) {
		return new IllegalArgumentException("Unknown cell behaviour parameter '" + parameterName
				+ "'. Supported parameters: " + PARAMETER_NAMES);
	}

	public double give_In(Aft competitor) {
		int intesificationGap = competitor.getCategory().getIntensityLevel()
				- c.getOwner().getCategory().getIntensityLevel();
		return maxGive_in / (1 + Math.exp(ConfigLoader.config.cell_behaviour_logistic_steepness
				* ((1 - weight_social) * Attitude_influence(competitor) + weight_social * social_influence(competitor)
						- Weight_inertia * Math.abs(intesificationGap))));
	}

	private double social_influence(Aft competitor) {
		if (AftCategorised.useCategorisationGivIn && CellBehaviourUpdater.behaviourUsed) {
			if (c.getOwner().category.getName().equals(competitor.category.getName())) {
				if (c.getOwner().category.getIntensity().equals(competitor.category.getIntensity())) {
					return 0;
				}
				if (competitor.category.getIntensityLevel() > c.getOwner().category.getIntensityLevel()) {
					return Math.max(Math.min(2 * fractionOfNeighbors(
							neighbor -> neighbor.category.getIntensityLevel() > competitor.category.getIntensityLevel())
							- Critical_mass, 1), -1);
				} else {
					return Math.max(Math.min(2 * fractionOfNeighbors(
							neighbor -> neighbor.category.getIntensityLevel() < competitor.category.getIntensityLevel())
							- Critical_mass, 1), -1);
				}
			}
		}
		return 0;
	}

	private double fractionOfNeighbors(Predicate<Aft> neighborCondition) {
		Collection<Aft> neighbors = CellsSubSets.detectExtendedNeighboringAFTs(c, neighborhood_size);
		int count = 0;
		for (Aft neighbor : neighbors) {
			if (neighbor.getCategory().getName().equals(c.getOwner().getCategory().getName())
					&& neighborCondition.test(neighbor)) {
				count++;
			}
		}
		return neighbors.isEmpty() ? 0.0 : (double) count / neighbors.size();
	}

	private double Attitude_influence(Aft competitor) {
		int intesificationGap = competitor.getCategory().getIntensityLevel()
				- c.getOwner().getCategory().getIntensityLevel();
		return Math.max(Math.min(Math.signum(intesificationGap) * Attitude_intensification, 1), -1);
	}

	public double getAttitude_intensification() {
		return Attitude_intensification;
	}

	public void setAttitude_intensification(double attitude_intensification) {
		Attitude_intensification = attitude_intensification;
	}

	public double getWeight_inertia() {
		return Weight_inertia;
	}

	public void setWeight_inertia(double weight_inertia) {
		Weight_inertia = weight_inertia;
	}

	public double getWeight_social() {
		return weight_social;
	}

	public void setWeight_social(double weight_social) {
		this.weight_social = weight_social;
	}

	public double getCritical_mass() {
		return Critical_mass;
	}

	public void setCritical_mass(double critical_mass) {
		Critical_mass = critical_mass;
	}

	public int getNeighborhood_size() {
		return neighborhood_size;
	}

	public void setNeighborhood_size(int neighborhood_size) {
		this.neighborhood_size = neighborhood_size;
	}

	public double getMaxGive_in() {
		return maxGive_in;
	}

	public void setMaxGive_in(double maxGive_in) {
		this.maxGive_in = maxGive_in;
	}

	@Override
	public String toString() {
		return "CellBehaviour [Attitude_intensification=" + Attitude_intensification + ", Weight_inertia="
				+ Weight_inertia + ", weight_social=" + weight_social + ", Critical_mass=" + Critical_mass
				+ ", neighborhood_size=" + neighborhood_size + ", cell_behaviour_logistic_steepness="
				+ ConfigLoader.config.cell_behaviour_logistic_steepness + ", maxGive_in=" + maxGive_in + "]";
	}

}
