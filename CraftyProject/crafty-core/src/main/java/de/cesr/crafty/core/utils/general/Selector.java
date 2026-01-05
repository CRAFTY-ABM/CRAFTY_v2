package de.cesr.crafty.core.utils.general;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import de.cesr.crafty.core.cli.CustomLogger;
import de.cesr.crafty.core.crafty.Cell;

/**
 * Deterministic subset selector for choosing a percentage of cells from a larger set.
 *
 * This utility is used to build a "seed" subset (e.g., candidate cells for abandonment/competition)
 * in a way that is reproducible across runs and safe to call on concurrent data structures.
 *
 * Selection approach:
 * - Each cell is assigned a stable 64-bit score computed from its (x,y) coordinates and a user-provided seed.
 * - The method then keeps the N cells with the smallest scores (where N = round(size * percentage)).
 * - A max-heap is used so the selection runs in O(M log N) time (M = number of cells), without sorting all cells.
 *
 * Why hashing coordinates?
 * - Hashing (x,y,seed) avoids spatial artifacts (e.g., selecting whole rows/columns) and produces an even spread.
 * - Using a deterministic tie-break (the entry key) guarantees stable output when scores collide.
 *
 * Concurrency note:
 * - The input map is a {@link ConcurrentHashMap}. The method snapshots {@code entrySet()} into a list once to
 *   avoid weakly-consistent iteration during selection.
 */

/**
 * @author Mohamed Byari
 *
 */

public class Selector {


	private static final CustomLogger LOGGER = new CustomLogger(Selector.class);

	// 64-bit avalanche (SplitMix64 finalizer)
	static long mix64(long z) {
		z ^= (z >>> 30);
		z *= 0xbf58476d1ce4e5b9L;
		z ^= (z >>> 27);
		z *= 0x94d049bb133111ebL;
		z ^= (z >>> 31);
		return z;
	}

	// Seeded 2-D hash from coords (kills row/column artifacts)
	static long hash2D(int x, int y, long seed) {
		long sx = Integer.toUnsignedLong(x) * 0x9E3779B97F4A7C15L; // golden ratio
		long sy = Integer.toUnsignedLong(y) * 0xC2B2AE3D27D4EB4FL; // xxHash prime
		return mix64(seed ^ sx ^ Long.rotateLeft(sy, 32));
	}

	static final class EntryScore {
		final String key;
		final Cell cell;
		final long score;

		EntryScore(String k, Cell c, long s) {
			key = k;
			cell = c;
			score = s;
		}
	}

	public static ConcurrentHashMap<String, Cell> randomSeed(ConcurrentHashMap<String, Cell> cellsHash,
			double percentage, long seedID) {

		int size = cellsHash.size();
		if (size == 0)
			return new ConcurrentHashMap<>();

		double p = Math.max(0.0, Math.min(1.0, percentage));
		int n = (int) Math.round(size * p);
		if (n <= 0)
			return new ConcurrentHashMap<>();
		if (n >= size)
			return new ConcurrentHashMap<>(cellsHash);

		// Snapshot once to avoid weakly-consistent traversal
		List<Map.Entry<String, Cell>> entries = new ArrayList<>(cellsHash.entrySet());
		// Keep N smallest scores in a max-heap (largest on top for eviction)
		PriorityQueue<EntryScore> heap = new PriorityQueue<>(n, (a, b) -> {
			int c = Long.compare(b.score, a.score); // reverse
			return (c != 0) ? c : b.key.compareTo(a.key); // deterministic tie-break
		});
		for (Map.Entry<String, Cell> e : entries) {
			Cell c = e.getValue();
			if (c == null)
				continue;
			long s = hash2D(c.getX(), c.getY(), seedID);
			if (heap.size() < n) {
				heap.offer(new EntryScore(e.getKey(), c, s));
			} else {
				EntryScore top = heap.peek();
				if (s < top.score || (s == top.score && e.getKey().compareTo(top.key) < 0)) {
					heap.poll();
					heap.offer(new EntryScore(e.getKey(), c, s));
				}
			}
		}
		ConcurrentHashMap<String, Cell> subset = new ConcurrentHashMap<>(Math.max(16, n * 2));
		for (EntryScore es : heap)
			subset.put(es.key, es.cell);
		LOGGER.info("Random seedID =" + seedID + " percentage= " + percentage * 100 + "%  seed size " + subset.size());
		return subset;
	}

}
