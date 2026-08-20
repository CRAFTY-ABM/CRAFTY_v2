package de.cesr.crafty.institution.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.institution.model.CraftyElementRef;
import de.cesr.crafty.institution.model.EffectType;
import de.cesr.crafty.institution.model.NormalizationType;
import de.cesr.crafty.institution.model.SpatialScope;
import de.cesr.crafty.institution.model.TargetDefinition;

class TargetObserverTest {
	private static final SpatialScope ALL = new SpatialScope(SpatialScope.Type.ALL_CELLS, "");

	@Test
	void aggregatesAftSharesAcrossSelectedCells() {
		List<Cell> cells = List.of(cell(1, "AF"), cell(2, "AF"), cell(3, "CW"));
		TargetObserver observer = observer(cells, Map.of(), new AtomicReference<>(0.0));
		TargetDefinition target = target("land", NormalizationType.RAW,
				new CraftyElementRef(EffectType.AFT, "AF", 1),
				new CraftyElementRef(EffectType.AFT, "CW", 1));

		TargetObservation observation = observer.observe(target, ALL);

		assertEquals(2.0 / 3.0, observation.components().get("AF"), 1.0e-12);
		assertEquals(1.0 / 3.0, observation.components().get("CW"), 1.0e-12);
		assertEquals(0.5, observation.normalizedValue(), 1.0e-12);
	}

	@Test
	void calculatesExternalBaselineRatioAndHandlesZeroBaseline() {
		AtomicReference<Double> external = new AtomicReference<>(2.0);
		TargetObserver observer = observer(List.of(), Map.of(), external);
		TargetDefinition target = target("external", NormalizationType.BASELINE_RATIO,
				new CraftyElementRef(EffectType.EXTERNAL, "signal", 1));

		assertEquals(1, observer.observe(target, ALL).normalizedValue());
		external.set(3.0);
		assertEquals(1.5, observer.observe(target, ALL).normalizedValue());

		TargetObserver zeroObserver = observer(List.of(), Map.of(), new AtomicReference<>(0.0));
		assertTrue(Double.isNaN(zeroObserver.observe(target, ALL).normalizedValue()));
	}

	@Test
	void keepsIndependentBaselinesForDifferentSpatialScopes() {
		AtomicReference<Double> external = new AtomicReference<>(2.0);
		TargetObserver observer = observer(List.of(), Map.of(), external);
		TargetDefinition target = target("external", NormalizationType.BASELINE_RATIO,
				new CraftyElementRef(EffectType.EXTERNAL, "signal", 1));
		SpatialScope paradigm = new SpatialScope(SpatialScope.Type.PARADIGM, "EM");

		assertEquals(1, observer.observe(target, ALL).normalizedValue());
		external.set(4.0);
		assertEquals(1, observer.observe(target, paradigm).normalizedValue());
		assertEquals(2, observer.observe(target, ALL).normalizedValue());
	}

	private static TargetObserver observer(List<Cell> cells, Map<Cell, Double> services,
			AtomicReference<Double> external) {
		return new TargetObserver(scope -> cells, (cell, service) -> services.getOrDefault(cell, 0.0),
				name -> external.get(), new ObservationBaselines());
	}

	private static TargetDefinition target(String id, NormalizationType normalization,
			CraftyElementRef... elements) {
		return new TargetDefinition(id, id, List.of(elements), normalization, Map.of());
	}

	private static Cell cell(int x, String owner) {
		Cell cell = new Cell(x, 0);
		cell.setOwner(new Aft(owner));
		return cell;
	}
}
