package de.cesr.crafty.core.crafty;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.utils.general.DeterministicRandom;

/**
 * Concrete Agent Functional Type (AFT) implementation used as the land manager/owner for cells.
 *
 * This class mainly provides:
 * - A label-based constructor for creating standard AFT instances (including a built-in "Abandoned" type).
 * - A copy constructor that performs a simple "mutation" of behavioural/production parameters.
 *
 * Mutation behaviour:
 * The copy constructor creates a new AFT by copying parameters from an existing one and perturbing
 * selected values (sensitivity, productivity levels, and key behavioural parameters) by a
 * random factor controlled by {@link ConfigLoader#config} {@code mutation_interval}.
 * This supports evolutionary-style variation when {@code mutate_on_competition_win} are enabled.
 *
 * Special case:
 * If the label is "Abandoned", the instance is configured as a non-interacting abandoned manager with
 * a default grey color and an "Uncategorized" category.
 */

/**
 * @author Mohamed Byari
 *
 */

public class Aft extends AbstractAft {

	public Aft(Aft other) {
		if (other != null) {
			setLabel(other.getLabel());
			this.color = other.color;
			other.sensitivity.forEach((sn, hash) -> {
				sensitivity.put(sn, new ConcurrentHashMap<String, Double>());
				hash.forEach((cn, v) -> {
					sensitivity.get(sn).put(cn,
							v * (1 + ConfigLoader.config.mutation_interval * (2 * new Random().nextDouble() - 1)));
				});
			});
			other.productivityLevel.forEach((n, v) -> {
				this.productivityLevel.put(n,
						v * (1 + ConfigLoader.config.mutation_interval * (2 * new Random().nextDouble() - 1)));
			});
			this.giveInMean = other.giveInMean
					* (1 + ConfigLoader.config.mutation_interval * (2 * new Random().nextDouble() - 1));
			this.giveUpMean = other.giveUpMean
					* (1 + ConfigLoader.config.mutation_interval * (2 * new Random().nextDouble() - 1));
			this.giveUpProbabilty = other.giveUpProbabilty
					* (1 + ConfigLoader.config.mutation_interval * (2 * new Random().nextDouble() - 1));
		}

	}

	public Aft(String label) {
		setLabel(label);
		if (label.equals("Abandoned")) {
			setLabel("Abandoned");
			setType(ManagerTypes.Abandoned);
			setColor("#cccccc");
			setCategory(new AftCategory("Uncategorized"));
		}
		DeterministicRandom.stableAftId(label);
	}

	@Override
	public String toString() {
		return "Aft [label=" + getLabel() + ", type=" + type + ", category=" + category + "]";
	}

}
