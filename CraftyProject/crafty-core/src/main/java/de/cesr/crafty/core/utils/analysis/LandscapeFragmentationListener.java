package de.cesr.crafty.core.utils.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.land.CellsLoader;
import de.cesr.crafty.core.updaters.AbstractUpdater;
import de.cesr.crafty.core.updaters.Timestep;

/**
 * Optional annual listener for landscape fragmentation and AFT clustering.
 *
 * <p>
 * Cells sharing an edge or a corner are neighbours (eight-neighbour, or Moore,
 * connectivity), matching the neighbourhood used by CRAFTY. The listener
 * records adjacency- and connected-patch measures in one CSV row per simulation
 * year. Off-map neighbours are ignored, and cells without an owner are treated
 * as one unmanaged class.
 * </p>
 *
 * <p>
 * Higher {@code same_aft_adjacency}, {@code adjacency_clustering_index},
 * {@code largest_patch_share}, and {@code normalized_effective_mesh_size}
 * indicate a more aggregated landscape. Higher {@code boundary_edge_density},
 * {@code patch_count}, and {@code patch_density} indicate greater fragmentation.
 * </p>
 */
public class LandscapeFragmentationListener extends AbstractUpdater {
	private static final CustomLogger LOGGER = new CustomLogger(LandscapeFragmentationListener.class);
	private static final String UNMANAGED = "<unmanaged>";
	private static final int[][] MOORE_DIRECTIONS = { { -1, -1 }, { 0, -1 }, { 1, -1 }, { -1, 0 },
			{ 1, 0 }, { -1, 1 }, { 0, 1 }, { 1, 1 } };
	/** Half of the Moore directions, so each undirected neighbour pair is counted once. */
	private static final int[][] UNIQUE_MOORE_DIRECTIONS = { { 1, 0 }, { 0, 1 }, { 1, 1 }, { 1, -1 } };
	private static final String CSV_HEADER = String.join(",", "year", "total_cells", "aft_classes",
			"adjacent_pairs", "same_aft_adjacent_pairs", "different_aft_adjacent_pairs", "same_aft_adjacency",
			"adjacency_clustering_index", "boundary_edge_density", "patch_count", "patch_density",
			"mean_patch_size_cells", "largest_patch_size_cells", "largest_patch_share",
			"effective_mesh_size_cells", "normalized_effective_mesh_size", "shannon_diversity");

	private boolean outputInitialized;

	@Override
	public void toSchedule() {
		modelRunner.scheduleRepeating(this);
	}

	@Override
	public void step() {
		if (!ConfigLoader.config.generate_output_files
				|| !ConfigLoader.config.generate_land_fragmentation_output) {
			return;
		}

		FragmentationMetrics metrics = calculate(CellsLoader.hashCell.values());
		Path output = Path.of(ConfigLoader.config.output_folder_name,
				ProjectLoader.getScenario() + "-land-fragmentation.csv");
		try {
			writeMetrics(output, Timestep.getCurrentYear(), metrics, outputInitialized);
			outputInitialized = true;
		} catch (IOException exception) {
			LOGGER.error("Could not write landscape fragmentation output: " + output, exception);
		}
	}

	/**
	 * Calculates fragmentation metrics without depending on simulation time or
	 * output state.
	 */
	public static FragmentationMetrics calculate(Collection<Cell> cells) {
		Map<Coordinate, Cell> grid = new HashMap<>();
		for (Cell cell : cells) {
			grid.put(new Coordinate(cell.getX(), cell.getY()), cell);
		}

		int totalCells = grid.size();
		if (totalCells == 0) {
			return FragmentationMetrics.empty();
		}

		Map<String, Integer> classCounts = new HashMap<>();
		grid.values().forEach(cell -> classCounts.merge(ownerLabel(cell), 1, Integer::sum));

		long adjacentPairs = 0;
		long sameAftPairs = 0;
		for (Map.Entry<Coordinate, Cell> entry : grid.entrySet()) {
			Coordinate coordinate = entry.getKey();
			String label = ownerLabel(entry.getValue());
			for (int[] direction : UNIQUE_MOORE_DIRECTIONS) {
				Cell neighbour = grid.get(new Coordinate(coordinate.x + direction[0], coordinate.y + direction[1]));
				if (neighbour == null) {
					continue;
				}
				adjacentPairs++;
				if (label.equals(ownerLabel(neighbour))) {
					sameAftPairs++;
				}
			}
		}

		Set<Coordinate> visited = new HashSet<>();
		int patchCount = 0;
		int largestPatchSize = 0;
		long squaredPatchSizes = 0;
		for (Map.Entry<Coordinate, Cell> entry : grid.entrySet()) {
			if (visited.contains(entry.getKey())) {
				continue;
			}
			patchCount++;
			int patchSize = floodFillPatch(entry.getKey(), ownerLabel(entry.getValue()), grid, visited);
			largestPatchSize = Math.max(largestPatchSize, patchSize);
			squaredPatchSizes += (long) patchSize * patchSize;
		}

		long differentAftPairs = adjacentPairs - sameAftPairs;
		double sameAftAdjacency = adjacentPairs == 0 ? 0.0 : (double) sameAftPairs / adjacentPairs;
		double expectedSameAdjacency = classCounts.values().stream().mapToDouble(count -> {
			double share = (double) count / totalCells;
			return share * share;
		}).sum();
		double clusteringIndex = expectedSameAdjacency >= 1.0
				? (sameAftAdjacency >= 1.0 ? 1.0 : 0.0)
				: (sameAftAdjacency - expectedSameAdjacency) / (1.0 - expectedSameAdjacency);
		double shannonDiversity = classCounts.values().stream().mapToDouble(count -> {
			double share = (double) count / totalCells;
			return -share * Math.log(share);
		}).sum();

		return new FragmentationMetrics(totalCells, classCounts.size(), adjacentPairs, sameAftPairs,
				differentAftPairs, sameAftAdjacency, clusteringIndex, (double) differentAftPairs / totalCells,
				patchCount, (double) patchCount / totalCells, (double) totalCells / patchCount, largestPatchSize,
				(double) largestPatchSize / totalCells, (double) squaredPatchSizes / totalCells,
				(double) squaredPatchSizes / ((double) totalCells * totalCells), shannonDiversity);
	}

	private static int floodFillPatch(Coordinate start, String label, Map<Coordinate, Cell> grid,
			Set<Coordinate> visited) {
		ArrayDeque<Coordinate> pending = new ArrayDeque<>();
		pending.add(start);
		visited.add(start);
		int patchSize = 0;

		while (!pending.isEmpty()) {
			Coordinate coordinate = pending.removeFirst();
			patchSize++;
			for (int[] direction : MOORE_DIRECTIONS) {
				Coordinate neighbourCoordinate = new Coordinate(coordinate.x + direction[0],
						coordinate.y + direction[1]);
				Cell neighbour = grid.get(neighbourCoordinate);
				if (neighbour != null && !visited.contains(neighbourCoordinate)
						&& label.equals(ownerLabel(neighbour))) {
					visited.add(neighbourCoordinate);
					pending.addLast(neighbourCoordinate);
				}
			}
		}
		return patchSize;
	}

	private static String ownerLabel(Cell cell) {
		return cell.getOwner() == null ? UNMANAGED : cell.getOwner().getLabel();
	}

	static void writeMetrics(Path output, int year, FragmentationMetrics metrics, boolean append) throws IOException {
		if (output.getParent() != null) {
			Files.createDirectories(output.getParent());
		}
		String content = (append ? "" : CSV_HEADER + System.lineSeparator()) + metrics.toCsvRow(year)
				+ System.lineSeparator();
		if (append) {
			Files.writeString(output, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
					StandardOpenOption.APPEND);
		} else {
			Files.writeString(output, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING);
		}
	}

	private record Coordinate(int x, int y) {
	}

	/** Annual landscape metrics written by this listener. */
	public record FragmentationMetrics(int totalCells, int aftClasses, long adjacentPairs,
			long sameAftAdjacentPairs, long differentAftAdjacentPairs, double sameAftAdjacency,
			double adjacencyClusteringIndex, double boundaryEdgeDensity, int patchCount, double patchDensity,
			double meanPatchSizeCells, int largestPatchSizeCells, double largestPatchShare,
			double effectiveMeshSizeCells, double normalizedEffectiveMeshSize, double shannonDiversity) {

		static FragmentationMetrics empty() {
			return new FragmentationMetrics(0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0, 0.0, 0.0, 0, 0.0, 0.0,
					0.0, 0.0);
		}

		String toCsvRow(int year) {
			return String.join(",", Integer.toString(year), Integer.toString(totalCells), Integer.toString(aftClasses),
					Long.toString(adjacentPairs), Long.toString(sameAftAdjacentPairs),
					Long.toString(differentAftAdjacentPairs), Double.toString(sameAftAdjacency),
					Double.toString(adjacencyClusteringIndex), Double.toString(boundaryEdgeDensity),
					Integer.toString(patchCount), Double.toString(patchDensity), Double.toString(meanPatchSizeCells),
					Integer.toString(largestPatchSizeCells), Double.toString(largestPatchShare),
					Double.toString(effectiveMeshSizeCells), Double.toString(normalizedEffectiveMeshSize),
					Double.toString(shannonDiversity));
		}
	}
}
