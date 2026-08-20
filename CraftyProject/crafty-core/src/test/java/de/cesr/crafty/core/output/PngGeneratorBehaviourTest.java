package de.cesr.crafty.core.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.crafty.CellBehaviour;
import de.cesr.crafty.core.updaters.CellBehaviourUpdater;

class PngGeneratorBehaviourTest {
	private Cell cell;

	@BeforeEach
	void setUp() {
		cell = new Cell(1, 2);
		CellBehaviour behaviour = new CellBehaviour(cell);
		behaviour.setAttitude_intensification(0.1);
		behaviour.setWeight_inertia(0.2);
		behaviour.setWeight_social(0.3);
		behaviour.setCritical_mass(0.4);
		behaviour.setMaxGive_in(0.5);
		behaviour.setNeighborhood_size(6);
		CellBehaviourUpdater.cellBehaviours.put(cell, behaviour);
	}

	@AfterEach
	void tearDown() {
		CellBehaviourUpdater.cellBehaviours.clear();
	}

	@Test
	void singleBehaviourMapsResolveEverySupportedParameter() {
		Map<String, Double> expected = Map.of(
				"Attitude_intensification", 0.1,
				"Weight_inertia", 0.2,
				"Weight-social", 0.3,
				"Critical_mass", 0.4,
				"MaxGive_in", 0.5,
				"Neighborhood_size", 6.0);

		expected.forEach((name, value) -> {
			var request = PngGenerator.handler.buildSingleRequests(List.of("behaviour:" + name)).get(0);
			assertEquals(value, request.value.doubleExtractor.applyAsDouble(cell), 1e-12);
		});
	}

	@Test
	void behaviourValuesWorkInDualAndTripleRequests() {
		var dual = PngGenerator.handler.buildDualRequests(
				List.of(List.of("behaviour:Attitude_intensification", "behaviour:Weight_inertia"))).get(0);
		assertEquals(0.1, dual.value1.doubleExtractor.applyAsDouble(cell), 1e-12);
		assertEquals(0.2, dual.value2.doubleExtractor.applyAsDouble(cell), 1e-12);

		var triple = PngGenerator.handler.buildTripleRequests(List.of(List.of(
				"behaviour:Weight-social", "behaviour:Critical_mass", "behaviour:MaxGive_in"))).get(0);
		assertEquals(0.3, triple.red.doubleExtractor.applyAsDouble(cell), 1e-12);
		assertEquals(0.4, triple.green.doubleExtractor.applyAsDouble(cell), 1e-12);
		assertEquals(0.5, triple.blue.doubleExtractor.applyAsDouble(cell), 1e-12);
	}

	@Test
	void compatibilitySpellingAndAllExpansionAreSupported() {
		var misspelled = PngGenerator.handler
				.buildSingleRequests(List.of("beheviour:Attitude_intensification")).get(0);
		assertEquals(0.1, misspelled.value.doubleExtractor.applyAsDouble(cell), 1e-12);
		assertEquals(6, PngGenerator.handler.buildSingleRequests(List.of("behaviour:ALL")).size());
	}

	@Test
	void missingCellBehaviourIsNoDataAndUnknownParameterIsRejected() {
		var request = PngGenerator.handler.buildSingleRequests(List.of("behaviour:Critical_mass")).get(0);
		CellBehaviourUpdater.cellBehaviours.clear();
		assertEquals(Double.NaN, request.value.doubleExtractor.applyAsDouble(cell));
		assertThrows(IllegalArgumentException.class,
				() -> PngGenerator.handler.buildSingleRequests(List.of("behaviour:not_a_parameter")));
	}
}
