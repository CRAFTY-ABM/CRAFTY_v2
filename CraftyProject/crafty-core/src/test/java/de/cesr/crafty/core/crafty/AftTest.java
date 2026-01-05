package de.cesr.crafty.core.crafty;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.core.updaters.CapitalUpdater;

/**
 * Unit tests for {@link Aft}.
 */
class AftTest {

	@BeforeEach
	void setUpStaticLists() {
		// Prepare a tiny, synthetic universe of capitals & services
		ServiceSet.getServicesList().clear();
		ServiceSet.getServicesList().addAll(List.of("ServiceA", "ServiceB"));

		CapitalUpdater.getCapitalsList().clear();
		CapitalUpdater.getCapitalsList().addAll(List.of("Capital1", "Capital2"));
	}

	@Test
	void stringConstructor_setsLabelOnly() {
		Aft aft = new Aft("TestAFT");

		assertEquals("TestAFT", aft.getLabel(), "Label constructor should set the label");
		// We *do not* make assumptions about maps here because this constructor
		// intentionally does not touch sensitivity/productivity.
	}

	@Test
	void toString_containsLabelTypeAndCategory() {
		Aft aft = new Aft("MyAft");
		AftCategory category = mock(AftCategory.class);
		aft.type = ManagerTypes.AFT;
		aft.category = category;
		when(category.getName()).thenReturn("SomeCategory");

		String asString = aft.toString();

		assertTrue(asString.contains("MyAft"));
		assertTrue(asString.contains("AFT"));
		assertTrue(asString.contains("AftCategory"));
	}
}
