package de.cesr.crafty.core.utils.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cesr.crafty.core.cli.Config;
import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.crafty.Aft;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.utils.analysis.LandscapeFragmentationListener.FragmentationMetrics;

class LandscapeFragmentationListenerTest {
	private final Aft aftA = new Aft("A");
	private final Aft aftB = new Aft("B");

	@TempDir
	Path tempDirectory;

	@BeforeEach
	void setUp() {
		ConfigLoader.config = new Config();
		ConfigLoader.config.logger_info = false;
		ConfigLoader.config.logger_warn = false;
	}

	@Test
	void singlePatchHasMaximumAggregation() {
		FragmentationMetrics metrics = LandscapeFragmentationListener.calculate(singleOwnerRectangle(2, 2));

		assertEquals(4, metrics.totalCells());
		assertEquals(6, metrics.adjacentPairs());
		assertEquals(1.0, metrics.sameAftAdjacency(), 1e-12);
		assertEquals(1.0, metrics.adjacencyClusteringIndex(), 1e-12);
		assertEquals(1, metrics.patchCount());
		assertEquals(1.0, metrics.largestPatchShare(), 1e-12);
		assertEquals(1.0, metrics.normalizedEffectiveMeshSize(), 1e-12);
	}

	@Test
	void diagonalSameAftCellsFormOneMoorePatch() {
		Cell first = new Cell(0, 0);
		first.setOwner(aftA);
		Cell diagonal = new Cell(1, 1);
		diagonal.setOwner(aftA);

		FragmentationMetrics metrics = LandscapeFragmentationListener.calculate(List.of(first, diagonal));

		assertEquals(1, metrics.adjacentPairs());
		assertEquals(1, metrics.sameAftAdjacentPairs());
		assertEquals(1, metrics.patchCount());
	}

	@Test
	void clusteredPatternScoresHigherThanDispersedPattern() {
		FragmentationMetrics clustered = LandscapeFragmentationListener.calculate(clusteredNineInFiveByFive());
		FragmentationMetrics dispersed = LandscapeFragmentationListener.calculate(dispersedNineInFiveByFive());

		assertEquals(2, clustered.patchCount());
		assertEquals(10, dispersed.patchCount());
		assertTrue(clustered.sameAftAdjacency() > dispersed.sameAftAdjacency());
		assertTrue(clustered.adjacencyClusteringIndex() > dispersed.adjacencyClusteringIndex());
		assertTrue(clustered.boundaryEdgeDensity() < dispersed.boundaryEdgeDensity());
		assertTrue(clustered.normalizedEffectiveMeshSize() > dispersed.normalizedEffectiveMeshSize());
	}

	@Test
	void csvWriterWritesOneHeaderAndOneRowPerYear() throws Exception {
		Path output = tempDirectory.resolve("fragmentation.csv");
		FragmentationMetrics metrics = LandscapeFragmentationListener.calculate(singleOwnerRectangle(2, 2));

		LandscapeFragmentationListener.writeMetrics(output, 2020, metrics, false);
		LandscapeFragmentationListener.writeMetrics(output, 2021, metrics, true);

		List<String> lines = Files.readAllLines(output);
		assertEquals(3, lines.size());
		assertTrue(lines.get(0).startsWith("year,total_cells,aft_classes,adjacent_pairs"));
		assertTrue(lines.get(1).startsWith("2020,"));
		assertTrue(lines.get(2).startsWith("2021,"));
	}

	@Test
	void disabledFlagDoesNotCreateOutput() throws Exception {
		ConfigLoader.config.generate_output_files = true;
		ConfigLoader.config.generate_land_fragmentation_output = false;
		ConfigLoader.config.output_folder_name = tempDirectory.toString();

		new LandscapeFragmentationListener().step();

		try (var paths = Files.list(tempDirectory)) {
			assertFalse(paths.findAny().isPresent());
		}
	}

	private List<Cell> singleOwnerRectangle(int width, int height) {
		List<Cell> cells = new ArrayList<>();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				Cell cell = new Cell(x, y);
				cell.setOwner(aftA);
				cells.add(cell);
			}
		}
		return cells;
	}

	private List<Cell> clusteredNineInFiveByFive() {
		List<Cell> cells = new ArrayList<>();
		for (int y = 0; y < 5; y++) {
			for (int x = 0; x < 5; x++) {
				Cell cell = new Cell(x, y);
				cell.setOwner(x < 3 && y < 3 ? aftA : aftB);
				cells.add(cell);
			}
		}
		return cells;
	}

	private List<Cell> dispersedNineInFiveByFive() {
		List<Cell> cells = new ArrayList<>();
		for (int y = 0; y < 5; y++) {
			for (int x = 0; x < 5; x++) {
				Cell cell = new Cell(x, y);
				cell.setOwner(x % 2 == 0 && y % 2 == 0 ? aftA : aftB);
				cells.add(cell);
			}
		}
		return cells;
	}
}
