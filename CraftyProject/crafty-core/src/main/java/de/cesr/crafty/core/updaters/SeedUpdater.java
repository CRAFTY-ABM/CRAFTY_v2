package de.cesr.crafty.core.updaters;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import de.cesr.crafty.core.cli.ConfigLoader;
import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Cell;
import de.cesr.crafty.core.crafty.RegionalModelRunner;
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
			ConfigLoader.config.longSeedID.set((long) Long.parseLong(ConfigLoader.config.seedID));// ConfigLoader.config.seedID.hashCode();
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

	public static ConcurrentHashMap<String, Cell> selectSeed(RegionalModelRunner r, double percent, boolean byAfts,
			long id) {
		if (ConfigLoader.config.seedID.equalsIgnoreCase("rank")) {
			return seedRanking(r.R.getCells().values(), percent, byAfts);
		} else {
			ConfigLoader.config.longSeedID.getAndIncrement();
			return Selector.randomSeed(r.R.getCells(), percent, id);
		}
	}

	private static ConcurrentHashMap<String, Cell> seedRanking(Collection<Cell> cells, double percent, boolean byAfts) {
		if (!byAfts) {
			return rankCellByUtilities(cells, percent); // your existing fast path
		}

		Map<String, ArrayList<Cell>> groups = groupByOwner(cells);
		ConcurrentHashMap<String, Cell> seed = new ConcurrentHashMap<>();

		groups.values().parallelStream().forEach(group -> {
			List<Cell> worst = bottomPercent(group, percent);
			for (Cell c : worst) {
				seed.put(c.getX() + "," + c.getY(), c);
			}
		});

		return seed;
	}

	private static ConcurrentHashMap<String, ArrayList<Cell>> groupByOwner(Collection<Cell> cells) {
		ConcurrentHashMap<String, ArrayList<Cell>> groups = new ConcurrentHashMap<>();
		for (Cell c : cells) {
			groups.computeIfAbsent(c.getOwnerName(), k -> new ArrayList<>()).add(c);
		}
		return groups;
	}

	private static ConcurrentHashMap<String, Cell> rankCellByUtilities(Collection<Cell> cellsHash, double percent) {
		ConcurrentHashMap<String, Cell> seed = new ConcurrentHashMap<>();
		List<Cell> list = bottomPercent(cellsHash, percent);
		list.forEach(c -> {
			seed.put(c.getX() + "," + c.getY(), c);
		});

		return seed;
	}

	private static double util(Cell c) {
		double u = c.getCurrentUtility();
		return Double.isNaN(u) ? Double.POSITIVE_INFINITY : u;
	}

	public static List<Cell> bottomPercent(Collection<Cell> cells, double percent) {
		if (cells == null || cells.isEmpty())
			return List.of();

		double p = Math.max(0.0, Math.min(1.0, percent));

		// Count n without assuming size() is cheap
		int n = 0;
		for (@SuppressWarnings("unused")
		Cell ignored : cells) {
			n++;
		}

		int k = (int) Math.ceil(n * p);
		if (k <= 0)
			return List.of();
		if (k >= n)
			return new ArrayList<>(cells);

		final long tieSeed = ThreadLocalRandom.current().nextLong();
		Comparator<Cell> asc = Comparator.comparingDouble((Cell c) -> util(c))
				.thenComparingLong(c -> tieKey(c, tieSeed));
		Comparator<Cell> worstFirst = asc.reversed();

		PriorityQueue<Cell> pq = new PriorityQueue<>(k, worstFirst);

		for (Cell c : cells) {
			if (pq.size() < k)
				pq.add(c);
			else if (asc.compare(c, pq.peek()) < 0) {
				pq.poll();
				pq.add(c);
			}
		}

		ArrayList<Cell> out = new ArrayList<>(pq);
		out.sort(asc);
		return out;
	}

	private static long tieKey(Cell c, long seed) {
		long xy = (((long) c.getX()) << 32) ^ (c.getY() & 0xffffffffL);
		return mix64(xy ^ seed);
	}

	private static long mix64(long z) {
		z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
		z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
		return z ^ (z >>> 31);
	}

}
