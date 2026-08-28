package de.cesr.crafty.institution.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.institution.model.CraftyElementRef;
import de.cesr.crafty.institution.model.EffectType;
import de.cesr.crafty.institution.model.SpatialScope;

class PolicyEffectApplierTest {
	private static final SpatialScope ALL = new SpatialScope(SpatialScope.Type.ALL_CELLS, "");

	@Test
	void appliesAftServiceAndCapitalEffectsOnlyToSelectedCells() {
		Cell af1 = cell(1, "AF");
		Cell af2 = cell(2, "AF");
		Cell woodland = cell(3, "CW");
		PolicyEffectApplier applier = new PolicyEffectApplier(scope -> List.of(af1, af2, woodland));

		PolicyApplicationResult result = applier.apply(List.of(
				new CraftyElementRef(EffectType.AFT, "AF", 2),
				new CraftyElementRef(EffectType.SERVICE, "Food", 0.5),
				new CraftyElementRef(EffectType.CAPITAL, "AF:human", 3)), ignored -> 10, ALL);

		assertEquals(3, result.selectedCellCount());
		assertEquals(7, result.applicationCount());
		assertEquals(2, result.applicationsByType().get(EffectType.AFT));
		assertEquals(3, result.applicationsByType().get(EffectType.SERVICE));
		assertEquals(2, result.applicationsByType().get(EffectType.CAPITAL));
		assertEquals(20, af1.getLandTax().get("AF"));
		assertFalse(woodland.getLandTax().containsKey("AF"));
		assertEquals(5, woodland.getServicesTax().get("Food"));
		assertEquals(0.3, af2.getCapitalsAdjusment().get("human"), 1.0e-12);
		assertFalse(woodland.getCapitalsAdjusment().containsKey("human"));
	}

	@Test
	void supportsPerCellPolicyValuesForRegionalDelay() {
		Cell first = cell(1, "AF");
		Cell second = cell(2, "AF");
		PolicyEffectApplier applier = new PolicyEffectApplier(scope -> List.of(first, second));

		applier.apply(List.of(new CraftyElementRef(EffectType.SERVICE, "Food", 2)),
				cell -> cell.getX() == 1 ? 3 : 4, ALL);

		assertEquals(6, first.getServicesTax().get("Food"));
		assertEquals(8, second.getServicesTax().get("Food"));
	}

	@Test
	void rejectsNonCellExternalEffects() {
		PolicyEffectApplier applier = new PolicyEffectApplier(scope -> List.of(cell(1, "AF")));

		assertThrows(IllegalArgumentException.class, () -> applier.apply(
				List.of(new CraftyElementRef(EffectType.EXTERNAL, "signal", 1)), ignored -> 1, ALL));
	}

	@Test
	void accumulatesSignedAdjustmentsAndDoesNotCountZeroWeightEffects() {
		Cell cell = cell(1, "AF");
		PolicyEffectApplier applier = new PolicyEffectApplier(scope -> List.of(cell));
		List<CraftyElementRef> effects = List.of(
				new CraftyElementRef(EffectType.SERVICE, "Food", 1),
				new CraftyElementRef(EffectType.SERVICE, "Zero", 0));

		applier.apply(effects, ignored -> 4, ALL);
		PolicyApplicationResult result = applier.apply(effects, ignored -> -1.5, ALL);

		assertEquals(2.5, cell.getServicesTax().get("Food"));
		assertFalse(cell.getServicesTax().containsKey("Zero"));
		assertEquals(1, result.applicationCount());
	}

	private static Cell cell(int x, String owner) {
		Cell cell = new Cell(x, 0);
		cell.setOwner(new Aft(owner));
		return cell;
	}
}
