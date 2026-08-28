package de.cesr.crafty.institution.runtime;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.institution.model.CraftyElementRef;
import de.cesr.crafty.institution.model.EffectType;
import de.cesr.crafty.institution.model.PolicyDefinition;
import de.cesr.crafty.institution.model.SpatialScope;

public final class PolicyEffectApplier {
	public static final double CAPITAL_PERCENT_SCALE = 0.01;
	private final CellScopeResolver scopeResolver;

	public PolicyEffectApplier(CellScopeResolver scopeResolver) {
		this.scopeResolver = Objects.requireNonNull(scopeResolver, "scopeResolver");
	}

	public PolicyApplicationResult apply(PolicyDefinition policy, double policyValue, SpatialScope scope) {
		return apply(policy.effects(), ignored -> policyValue, scope);
	}

	public PolicyApplicationResult apply(List<CraftyElementRef> effects, ToDoubleFunction<Cell> policyValueProvider,
			SpatialScope scope) {
		List<Cell> cells = new ArrayList<>(scopeResolver.resolve(scope));
		Map<EffectType, Long> counts = new EnumMap<>(EffectType.class);
		long applications = 0;
		for (CraftyElementRef effect : effects) {
			if (effect.type() == EffectType.EXTERNAL) {
				throw new IllegalArgumentException(
						"External policy effect '" + effect.name() + "' is not cell-level and cannot be applied");
			}
			for (Cell cell : cells) {
				double policyValue = policyValueProvider.applyAsDouble(cell);
				if (!Double.isFinite(policyValue)) {
					throw new IllegalArgumentException("Policy value must be finite");
				}
				double adjustment = policyValue * effect.weight();
				if (adjustment == 0 || !appliesToCell(effect, cell)) {
					continue;
				}
				switch (effect.type()) {
				case AFT -> cell.getLandTax().merge(effect.name(), adjustment, Double::sum);
				case SERVICE -> cell.getServicesTax().merge(effect.name(), adjustment, Double::sum);
				case CAPITAL -> cell.getCapitalsAdjusment().merge(capitalName(effect.name()),
						adjustment * CAPITAL_PERCENT_SCALE, Double::sum);
				case EXTERNAL -> throw new IllegalStateException("External effect should have been rejected");
				}
				applications++;
				counts.merge(effect.type(), 1L, Long::sum);
			}
		}
		return new PolicyApplicationResult(cells.size(), applications, counts);
	}

	private static boolean appliesToCell(CraftyElementRef effect, Cell cell) {
		return switch (effect.type()) {
		case AFT -> cell.getOwner() != null && effect.name().equals(cell.getOwnerName());
		case SERVICE -> true;
		case CAPITAL -> cell.getOwner() != null && capitalAft(effect.name()).equals(cell.getOwnerName());
		case EXTERNAL -> false;
		};
	}

	private static String capitalAft(String value) {
		return capitalParts(value)[0];
	}

	private static String capitalName(String value) {
		return capitalParts(value)[1];
	}

	private static String[] capitalParts(String value) {
		String[] parts = value.split(":", -1);
		if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
			throw new IllegalArgumentException("Capital effect must use AFT:capital format: " + value);
		}
		return parts;
	}
}
