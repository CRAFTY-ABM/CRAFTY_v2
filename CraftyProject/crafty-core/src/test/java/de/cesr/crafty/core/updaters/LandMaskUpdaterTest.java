package de.cesr.crafty.core.updaters;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.Config;
import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.crafty.Competitiveness;
import de.cesr.crafty.core.crafty.RegionalModelRunner;
import de.cesr.crafty.core.dataLoader.afts.AFTsLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.dataLoader.land.MaskLoader;
import de.cesr.crafty.core.dataLoader.land.MaskMetadata;
import de.cesr.crafty.core.utils.file.CsvTools;

class LandMaskUpdaterTest {

	@TempDir
	Path tmp;
	private double originalMostCompetitiveProbability;
	private long originalRandomSeed;

	@BeforeEach
	void resetState() {
		if (ConfigLoader.config == null) {
			ConfigLoader.config = new Config();
		}
		LandMaskUpdater.restrictions.clear();
		LandMaskUpdater.forcedAllowedAftLabels.clear();
		LandMaskUpdater.cellsForecedToChange.clear();
		MaskLoader.mask_paths = new LinkedHashMap<>();
		MaskLoader.restriction_paths = new LinkedHashMap<>();
		MaskLoader.scenario_restriction_paths = new LinkedHashMap<>();
		MaskLoader.default_restriction_paths = new LinkedHashMap<>();
		MaskLoader.mask_metadata = new LinkedHashMap<>();
		if (CellsLoader.hashCell == null) {
			CellsLoader.hashCell = new ConcurrentHashMap<>();
		} else {
			CellsLoader.hashCell.clear();
		}
		originalMostCompetitiveProbability = ConfigLoader.config.most_competitive_aft_probability;
		originalRandomSeed = ConfigLoader.config.random_seed;
	}

	@AfterEach
	void restoreConfig() {
		ConfigLoader.config.most_competitive_aft_probability = originalMostCompetitiveProbability;
		ConfigLoader.config.random_seed = originalRandomSeed;
	}

	@Test
	void nonForcedMaskChangesMaskButNeverChangesOwner() throws Exception {
		String maskType = "Protected";
		int year = 2000;
		MaskLoader.mask_metadata.put(maskType, new MaskMetadata(maskType, false, 1, 0));

		Aft farm = mock(Aft.class);
		when(farm.getLabel()).thenReturn("Farm");
		CellState active = statefulCell(maskType, farm);
		CellState inactive = statefulCell(maskType, farm);
		CellsLoader.hashCell.put("1,2", active.cell);
		CellsLoader.hashCell.put("3,4", inactive.cell);

		Path csv = maskCsv("protected.csv", "1,2,1\n3,4,0\n");
		MaskLoader.mask_paths.put(maskType, new TreeMap<>(Map.of(year, csv)));

		try (MockedStatic<CellsLoader> cells = Mockito.mockStatic(CellsLoader.class)) {
			cells.when(() -> CellsLoader.getCell(1, 2)).thenReturn(active.cell);
			cells.when(() -> CellsLoader.getCell(3, 4)).thenReturn(inactive.cell);
			LandMaskUpdater.cellOneMaskUpdater(maskType, year);
		}

		assertEquals(maskType, active.maskType.get());
		assertSame(farm, active.owner.get());
		assertNull(inactive.maskType.get());
		assertSame(farm, inactive.owner.get(), "removing a mask must not erase land ownership");
	}

	@Test
	void forcedMaskUsesUniqueAllowedTargetFromRestrictionMap() throws Exception {
		String maskType = "UrbanControl";
		int year = 2000;
		MaskLoader.mask_metadata.put(maskType, new MaskMetadata(maskType, true, 1, 0));

		Path restriction = tmp.resolve("restriction.csv");
		MaskLoader.restriction_paths.put(maskType, new TreeMap<>(Map.of(year, restriction)));
		String[][] matrix = { { "", "Farm", "Urban" }, { "Farm", "0", "0" }, { "Urban", "0", "1" } };

		Aft farm = mock(Aft.class);
		when(farm.getLabel()).thenReturn("Farm");
		Aft urban = mock(Aft.class);
		when(urban.getLabel()).thenReturn("Urban");
		Map<String, Aft> afts = new ConcurrentHashMap<>(Map.of("Farm", farm, "Urban", urban));
		CellState cell = statefulCell(null, farm);
		CellsLoader.hashCell.put("1,2", cell.cell);
		Path csv = maskCsv("urban.csv", "1,2,1\n");
		MaskLoader.mask_paths.put(maskType, new TreeMap<>(Map.of(year, csv)));

		try (MockedStatic<Timestep> timestep = Mockito.mockStatic(Timestep.class);
				MockedStatic<CsvTools> csvTools = Mockito.mockStatic(CsvTools.class)) {
			timestep.when(Timestep::getCurrentYear).thenReturn(year);
			csvTools.when(() -> CsvTools.csvReader(restriction)).thenReturn(matrix);
			LandMaskUpdater.updateRestrections(maskType);
		}
		try (MockedStatic<Timestep> timestep = Mockito.mockStatic(Timestep.class);
				MockedStatic<CellsLoader> cells = Mockito.mockStatic(CellsLoader.class);
				MockedStatic<AFTsLoader> aftsLoader = Mockito.mockStatic(AFTsLoader.class)) {
			timestep.when(Timestep::getCurrentYear).thenReturn(year);
			cells.when(() -> CellsLoader.getCell(1, 2)).thenReturn(cell.cell);
			aftsLoader.when(AFTsLoader::getAftHash).thenReturn(afts);
			LandMaskUpdater.cellOneMaskUpdater(maskType, year);
		}

		assertEquals(List.of("Urban"), LandMaskUpdater.forcedAllowedAftLabels.get(maskType));
		assertEquals(maskType, cell.maskType.get());
		assertSame(urban, cell.owner.get());
	}

	@Test
	void multipleForcedTargetsAreRetainedForConfiguredSelection() {
		String maskType = "Ambiguous";
		int year = 2000;
		MaskLoader.mask_metadata.put(maskType, new MaskMetadata(maskType, true, 1, 0));
		Path restriction = tmp.resolve("ambiguous.csv");
		MaskLoader.restriction_paths.put(maskType, new TreeMap<>(Map.of(year, restriction)));
		String[][] matrix = { { "", "A", "B" }, { "A", "1", "0" }, { "B", "0", "1" } };

		try (MockedStatic<Timestep> timestep = Mockito.mockStatic(Timestep.class);
				MockedStatic<CsvTools> csvTools = Mockito.mockStatic(CsvTools.class)) {
			timestep.when(Timestep::getCurrentYear).thenReturn(year);
			csvTools.when(() -> CsvTools.csvReader(restriction)).thenReturn(matrix);
			LandMaskUpdater.updateRestrections(maskType);
		}

		assertEquals(List.of("A", "B"), LandMaskUpdater.forcedAllowedAftLabels.get(maskType));
	}

	@Test
	void multipleForcedTargetsUseReproducibleRandomSelectionWithoutNeighbors() {
		String maskType = "Multi";
		LandMaskUpdater.forcedAllowedAftLabels.put(maskType, List.of("A", "B"));
		ConfigLoader.config.most_competitive_aft_probability = 0.0;
		ConfigLoader.config.random_seed = 12345L;

		Aft a = mock(Aft.class);
		when(a.getLabel()).thenReturn("A");
		when(a.isInteract()).thenReturn(true);
		Aft b = mock(Aft.class);
		when(b.getLabel()).thenReturn("B");
		when(b.isInteract()).thenReturn(true);
		Map<String, Aft> afts = new ConcurrentHashMap<>(Map.of("A", a, "B", b));
		Cell cell = mock(Cell.class);

		try (MockedStatic<Timestep> timestep = Mockito.mockStatic(Timestep.class);
				MockedStatic<AFTsLoader> aftsLoader = Mockito.mockStatic(AFTsLoader.class, Mockito.CALLS_REAL_METHODS)) {
			timestep.when(Timestep::getCurrentYear).thenReturn(2000);
			aftsLoader.when(AFTsLoader::getAftHash).thenReturn(afts);
			Aft first = LandMaskUpdater.selectForcedOwner(cell, maskType, null);
			Aft second = LandMaskUpdater.selectForcedOwner(cell, maskType, null);
			assertSame(first, second);
			assertTrue(first == a || first == b);
		}
	}

	@Test
	void multipleForcedTargetsUseMostCompetitiveSelectionWhenConfigured() {
		String maskType = "Multi";
		LandMaskUpdater.forcedAllowedAftLabels.put(maskType, List.of("A", "B"));
		ConfigLoader.config.most_competitive_aft_probability = 1.0;

		Aft a = mock(Aft.class);
		when(a.getLabel()).thenReturn("A");
		when(a.isInteract()).thenReturn(true);
		Aft b = mock(Aft.class);
		when(b.getLabel()).thenReturn("B");
		when(b.isInteract()).thenReturn(true);
		Map<String, Aft> afts = new ConcurrentHashMap<>(Map.of("A", a, "B", b));
		Cell cell = mock(Cell.class);
		RegionalModelRunner runner = mock(RegionalModelRunner.class);

		try (MockedStatic<Timestep> timestep = Mockito.mockStatic(Timestep.class);
				MockedStatic<AFTsLoader> aftsLoader = Mockito.mockStatic(AFTsLoader.class);
				MockedStatic<Competitiveness> competitiveness = Mockito.mockStatic(Competitiveness.class)) {
			timestep.when(Timestep::getCurrentYear).thenReturn(2000);
			aftsLoader.when(AFTsLoader::getAftHash).thenReturn(afts);
			competitiveness.when(() -> Competitiveness.mostCompetitiveAgent(cell, List.of(a, b), runner)).thenReturn(b);

			assertSame(b, LandMaskUpdater.selectForcedOwner(cell, maskType, runner));
			competitiveness.verify(() -> Competitiveness.mostCompetitiveAgent(cell, List.of(a, b), runner));
		}
	}

	@Test
	void lowerPriorityNumberWinsWhenMasksOverlap() throws Exception {
		int year = 2000;
		MaskMetadata weak = new MaskMetadata("Weak", false, 5, 0);
		MaskMetadata strong = new MaskMetadata("Strong", false, 1, 1);
		MaskLoader.mask_metadata.put(weak.name(), weak);
		MaskLoader.mask_metadata.put(strong.name(), strong);
		MaskLoader.mask_paths.put(weak.name(), new TreeMap<>(Map.of(year, maskCsv("weak.csv", "1,2,1\n"))));
		MaskLoader.mask_paths.put(strong.name(), new TreeMap<>(Map.of(year, maskCsv("strong.csv", "1,2,1\n"))));

		CellState cell = statefulCell(null, null);
		CellsLoader.hashCell.put("1,2", cell.cell);
		try (MockedStatic<CellsLoader> cells = Mockito.mockStatic(CellsLoader.class)) {
			cells.when(() -> CellsLoader.getCell(anyInt(), anyInt())).thenReturn(cell.cell);
			LandMaskUpdater.applyMasks(year);
		}

		assertEquals("Strong", cell.maskType.get());
	}

	@Test
	void maskAbsentFromMetadataIsIgnored() throws Exception {
		String maskType = "Unregistered";
		int year = 2000;
		CellState cell = statefulCell(null, null);
		Path csv = maskCsv("unregistered.csv", "1,2,1\n");
		MaskLoader.mask_paths.put(maskType, new TreeMap<>(Map.of(year, csv)));

		LandMaskUpdater.cellOneMaskUpdater(maskType, year);

		assertNull(cell.maskType.get());
	}

	@Test
	void missingRestrictionEntryDoesNotThrow() {
		assertDoesNotThrow(() -> LandMaskUpdater.updateRestrections("NoSuchMask"));
	}

	private Path maskCsv(String name, String rows) throws Exception {
		Path path = tmp.resolve(name);
		Files.writeString(path, "X,Y,Year_2000\n" + rows);
		return path;
	}

	private static final class CellState {
		final Cell cell;
		final AtomicReference<String> maskType;
		final AtomicReference<Aft> owner;

		CellState(Cell cell, AtomicReference<String> maskType, AtomicReference<Aft> owner) {
			this.cell = cell;
			this.maskType = maskType;
			this.owner = owner;
		}
	}

	private static CellState statefulCell(String initialMaskType, Aft initialOwner) {
		AtomicReference<String> maskRef = new AtomicReference<>(initialMaskType);
		AtomicReference<Aft> ownerRef = new AtomicReference<>(initialOwner);
		Answer<Object> answer = invocation -> switch (invocation.getMethod().getName()) {
		case "getMaskType" -> maskRef.get();
		case "setMaskType" -> {
			maskRef.set(invocation.getArgument(0));
			yield null;
		}
		case "getOwner" -> ownerRef.get();
		case "setOwner" -> {
			ownerRef.set(invocation.getArgument(0));
			yield null;
		}
		default -> Mockito.RETURNS_DEFAULTS.answer(invocation);
		};
		Cell cell = mock(Cell.class, withSettings().defaultAnswer(answer));
		return new CellState(cell, maskRef, ownerRef);
	}
}
