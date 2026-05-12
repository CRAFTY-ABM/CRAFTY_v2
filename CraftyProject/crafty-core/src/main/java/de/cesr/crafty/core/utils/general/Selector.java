package de.cesr.crafty.core.utils.general;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Cell;

/**
 * Deterministic subset selector based on precomputed cell scores.
 *
 * Expected workflow: - each iteration, every cell gets a deterministic score
 * (e.g. via DeterministicRandom) - this class selects the N cells with the
 * smallest score
 *
 * This keeps selection reproducible and independent from traversal order.
 *
 * @author Mohamed Byari
 */
public class Selector {

	private static final CustomLogger LOGGER = new CustomLogger(Selector.class);

	static final class EntryScore {
		final String key;
		final Cell cell;
		final double score;
		final long cellId;

		EntryScore(String key, Cell cell, double score, long cellId) {
			this.key = key;
			this.cell = cell;
			this.score = score;
			this.cellId = cellId;
		}
	}

	public static List<Cell> randomSeed(ConcurrentHashMap<String, Cell> cellsHash,
			double percentage) {

		int size = cellsHash.size();
		if (size == 0) {
			return new ArrayList<>();
		}

		double p = Math.max(0.0, Math.min(1.0, percentage));
		int n = (int) Math.round(size * p);

		if (n <= 0) {
			return new ArrayList<>();
		}
		if (n >= size) {
			return new ArrayList<>(cellsHash.values());
		}

		List<Map.Entry<String, Cell>> entries = new ArrayList<>(cellsHash.entrySet());

		// Keep N smallest scores in a max-heap:
		// worst currently kept item is on top and gets evicted first.
		PriorityQueue<EntryScore> heap = new PriorityQueue<>(n, (a, b) -> {
			int cmp = Double.compare(b.score, a.score); // reverse score: larger first
			if (cmp != 0) {
				return cmp;
			}

			cmp = Long.compare(b.cellId, a.cellId); // reverse tie-break for eviction
			if (cmp != 0) {
				return cmp;
			}

			return b.key.compareTo(a.key); // final deterministic tie-break
		});

		for (Map.Entry<String, Cell> e : entries) {
			Cell c = e.getValue();
			if (c == null) {
				continue;
			}

			double s = c.getScore();
			long cellId = c.getCellID();

			EntryScore candidate = new EntryScore(e.getKey(), c, s, cellId);

			if (heap.size() < n) {
				heap.offer(candidate);
			} else {
				EntryScore top = heap.peek();

				boolean better = s < top.score || (Double.compare(s, top.score) == 0 && cellId < top.cellId)
						|| (Double.compare(s, top.score) == 0 && cellId == top.cellId
								&& e.getKey().compareTo(top.key) < 0);

				if (better) {
					heap.poll();
					heap.offer(candidate);
				}
			}
		}

		ConcurrentHashMap<String, Cell> subset = new ConcurrentHashMap<>(Math.max(16, n * 2));
		for (EntryScore es : heap) {
			subset.put(es.key, es.cell);
		}
		 
		LOGGER.info("Random score-based seed percentage=" + (percentage * 100.0) + "% seed size=" + subset.size());
		return new ArrayList<>(subset.values());
	}
}