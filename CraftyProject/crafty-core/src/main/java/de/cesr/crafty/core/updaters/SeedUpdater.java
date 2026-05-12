package de.cesr.crafty.core.updaters;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.crafty.RegionalModelRunner;
import de.cesr.crafty.core.utils.general.DeterministicRandom;
import de.cesr.crafty.core.utils.general.Selector;

/**
 * Selects the subset of cells (“seed”) that will participate in  processes (competition and land abandonment).
 *
 * This updater implements two seed-selection strategies, controlled by {@code seedID} in the configuration:
 * - {@code "rank"}: deterministic ranking-based selection that picks the bottom {@code percent} of cells
 *   by current utility (optionally stratified by AFT/owner).
 * - any other non-null identifier (with {@code longSeedID != 0}): pseudo-random selection via
 *   {@link Selector#randomSeed(..)}.
 *
 * Ranking mode supports two behaviours:
 * - {@code byAfts == false}: ranks all cells together and selects the bottom {@code percent}.
 * - {@code byAfts == true}: groups cells by owner (AFT label) and selects the bottom {@code percent} within
 *   each group, then merges the results.
 *
 * The core selection primitive is {@link #bottomPercent(Collection, double)}, which returns the {@code k}
 * lowest utility cells using a bounded priority queue (O(n log k)). To ensure deterministic ordering for ties
 * within a call, it uses a per-call random tie seed and a stable tie-breaker based on cell coordinates.
 *
 * Notes / edge cases:
 * - {@code percent} is clamped to [0, 1].
 * - Utilities that are {@code NaN} are treated as worst possible (mapped to +∞) to avoid polluting selection.
 * - If seed configuration is missing/invalid, the method logs a fatal message and returns {@code null}.
 */
/**
 * @author Mohamed Byari
 *
 */

public class SeedUpdater {
	private static final CustomLogger LOGGER = new CustomLogger(SeedUpdater.class);

	public static void inialize() {
		if (ConfigLoader.config.seedID == null) {
			ConfigLoader.config.seedID = "rank";
		} else if (ConfigLoader.config.seedID.equals("0")) {
			ConfigLoader.config.longSeedID.set(ThreadLocalRandom.current().nextLong());
		} else if (ConfigLoader.config.seedID.matches("[-+]?\\d+")) {
			LOGGER.info("seedID is a number ID--> " + ConfigLoader.config.seedID);
			ConfigLoader.config.longSeedID.set((long) Long.parseLong(ConfigLoader.config.seedID));
		} else if (Paths.get(ConfigLoader.config.seedID).toFile().isDirectory()) {
			// not implemented yet
			LOGGER.fatal("seed for a file is not imlemented yet (use seedID:rank or seedID: nbr)");
		} else if (ConfigLoader.config.seedID.equalsIgnoreCase("rank")) {
			LOGGER.info("Cells Seed flag configuration is rank. CRAFTY will rank cells by utilities");
		} else {
			LOGGER.fatal("SeedID flag configuration error:"
					+ " this is not an identification number, no directory path is specified, or  not the ‘rank’ key.");
		}
	}

	public static List<Cell> selectSeed(RegionalModelRunner r, ConcurrentHashMap<String, Cell> cells, double percent,
			boolean byAfts, int processID) {
		long runSeed = ConfigLoader.config.longSeedID.get();
		int year = Timestep.getCurrentYear();
		
		cells.values().parallelStream().forEach(c -> {
			double score = DeterministicRandom.randomDouble(runSeed, year, processID, c.getCellID(), 0L, 0);
			c.setScore(score);
		});
		
		if (ConfigLoader.config.seedID.equalsIgnoreCase("rank")) {
			// Snapshot once for deterministic ranking over a stable candidate set
			List<Cell> snapshot = new ArrayList<>(cells.values());
			return seedRanking(snapshot, percent, byAfts, runSeed, year, processID);
		} else {
			return Selector.randomSeed(cells, percent);
		}
	}

	private static List<Cell> seedRanking(Collection<Cell> cells, double percent, boolean byAfts, long runSeed,
			int year, int processID) {
		List<Cell> selected = new ArrayList<>();
		if (!byAfts) {
			selected.addAll(bottomPercent(cells, percent, runSeed, year));
		} else {
			Map<String, List<Cell>> groups = new TreeMap<>();
			for (Cell c : cells) {
				groups.computeIfAbsent(c.getOwnerName(), k -> new ArrayList<>()).add(c);
			}
			for (List<Cell> group : groups.values()) {
				selected.addAll(bottomPercent(group, percent, runSeed, year));
			}
		}
		selected.sort(Comparator.comparingLong(c -> DeterministicRandom.randomLong(runSeed, year, processID,
				DeterministicRandom.stableCellId(c.getX(), c.getY()), 0L, 0)));
		return selected;
	}

	private static double util(Cell c) {
		double u = c.getCurrentUtility();
		return Double.isNaN(u) ? Double.POSITIVE_INFINITY : u;
	}

	public static List<Cell> bottomPercent(Collection<Cell> cells, double percent, long runSeed, int year) {
		if (cells == null || cells.isEmpty()) {
			return List.of();
		}

		// Snapshot defensively so selection is over a stable set
		List<Cell> snapshot = new ArrayList<>(cells);

		double p = Math.max(0.0, Math.min(1.0, percent));
		int n = snapshot.size();

		int k = (int) Math.ceil(n * p);
		if (k <= 0) {
			return List.of();
		}
		if (k >= n) {
			snapshot.sort(cellUtilityComparator(runSeed, year));
			return snapshot;
		}

		Comparator<Cell> asc = cellUtilityComparator(runSeed, year);
		Comparator<Cell> worstFirst = asc.reversed();
		PriorityQueue<Cell> pq = new PriorityQueue<>(k, worstFirst);

		for (Cell c : snapshot) {
			if (pq.size() < k) {
				pq.add(c);
			} else if (asc.compare(c, pq.peek()) < 0) {
				pq.poll();
				pq.add(c);
			}
		}

		ArrayList<Cell> out = new ArrayList<>(pq);
		out.sort(asc);
		return out;
	}

	private static long stableCellKey(Cell c) {
		return DeterministicRandom.stableCellId(c.getX(), c.getY());
	}

	private static Comparator<Cell> cellUtilityComparator(long runSeed, int year) {
		return Comparator.comparingDouble((Cell c) -> util(c))
				.thenComparingLong(c -> DeterministicRandom.randomLong(runSeed, year,
						DeterministicRandom.Process.TIE_BREAK, stableCellKey(c), 0L, 0))
				.thenComparingLong(SeedUpdater::stableCellKey);
	}

}
