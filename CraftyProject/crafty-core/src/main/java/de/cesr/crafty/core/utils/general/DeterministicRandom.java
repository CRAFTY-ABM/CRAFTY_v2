package de.cesr.crafty.core.utils.general;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToLongFunction;

import de.cesr.crafty.core.crafty.Cell;

/**
 * Deterministic, stateless pseudo-random utilities for reproducible model decisions.
 *
 * Core idea:
 * Every "random" value is derived from a stable key:
 *   (runSeed, year, processCode, cellId, aftId, drawIndex)
 *
 * This avoids dependence on:
 * - thread scheduling
 * - iteration order
 * - number of prior RNG calls
 * - shared Random instances
 *
 * Notes:
 * - cellId should be a stable identifier for the cell.
 * - aftId should be a stable identifier for the AFT derived from label.
 * - drawIndex is useful when you need multiple independent draws for the same context.
 */
public final class DeterministicRandom {

    private static final long GOLDEN_GAMMA = 0x9e3779b97f4a7c15L;
    private static final long NULL_STRING_SEED = 0x6a09e667f3bcc909L;

    /**
     * Process codes to separate independent random mechanisms.
     * Add more if needed, but keep them stable once used in experiments.
     */
    public static final class Process {
        public static final int CELL_SELECTION_COMPETITION = 1;
        public static final int CELL_SELECTION_ABANDONMENT = 2;
        public static final int CELL_SELECTION_UNMANAGED_TAKEOVER = 3;
        public static final int COMPETITOR_PICK = 4;
        public static final int NEIGHBOR_PICK = 5;
        public static final int NON_NEIGHBOR_PICK =6;
        public static final int ABANDONMENT_TAKEOVER = 7;
        public static final int TIE_BREAK = 8;
        public static final int GIVE_UP_GAUSSIAN = 9;
        public static final int GIVE_UP_PROBABILITY = 10;
        public static final int GIVE_IN_THRESHOLD = 11;
        public static final int CELL_SELECTION_TWIN_COMPETITION = 12;

        private Process() {
        }
    }

    /**
     * SplitMix64 finalizer / 64-bit mixing function.
     * Good quality, very fast, deterministic.
     */
    public static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    /**
     * Stable 64-bit hash for strings.
     * Safe for persistent identifiers such as AFT labels.
     */
    public static long hashString64(String s) {
        if (s == null) {
            return mix64(NULL_STRING_SEED);
        }
        // FNV-1a 64-bit, then final mix
        long h = 0xcbf29ce484222325L;
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            h ^= (b & 0xffL);
            h *= 0x100000001b3L;
        }
        h ^= bytes.length;
        return mix64(h);
    }


    /**
     * Stable cell ID from x/y coordinates.
     */
    public static long stableCellId(int x, int y) {
    	long z = GOLDEN_GAMMA;
        z = mix64(z ^ (long) x);
        z = mix64(z ^ (long) y);
        return z;
    }
    public static long stableCellKey(Cell c) {
        return (((long) c.getX()) << 32) ^ (c.getY() & 0xffffffffL);
    }

    /**
     * Stable AFT ID from a persistent label/name.
     */
    public static long stableAftId(String aftLabel) {
        return hashString64(aftLabel);
    }

    /**
     * Builds a deterministic random key from the full decision context.
     */
	public static long randomKey(long runSeed, int year, int processCode, long cellId, long aftId, int drawIndex) {

		long z = mix64(runSeed + GOLDEN_GAMMA);
		z = mix64(z ^ year);
		z = mix64(z ^ processCode);
		z = mix64(z ^ cellId);
		z = mix64(z ^ aftId);
		z = mix64(z ^ drawIndex);
		return z;
	}

    /**
     * Deterministic pseudo-random long for a specific decision context.
     */
    public static long randomLong(long runSeed,
                                  int year,
                                  int processCode,
                                  long cellId,
                                  long aftId,
                                  int drawIndex) {

        return randomKey(runSeed, year, processCode, cellId, aftId, drawIndex);
    }

    /**
     * Deterministic pseudo-random double in [0,1).
     */
    public static double randomDouble(long runSeed,
                                      int year,
                                      int processCode,
                                      long cellId,
                                      long aftId,
                                      int drawIndex) {

        return toUnitDouble(randomKey(runSeed, year, processCode, cellId, aftId, drawIndex));
    }

    /**
     * Deterministic pseudo-random int in [0, bound).
     * Suitable for reproducible indexing / selection.
     */
    public static int randomInt(long runSeed,
                                int year,
                                int processCode,
                                long cellId,
                                long aftId,
                                int drawIndex,
                                int bound) {

        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be > 0");
        }
        return (int) Long.remainderUnsigned(
                randomKey(runSeed, year, processCode, cellId, aftId, drawIndex),
                bound
        );
    }

    /**
     * Deterministic pseudo-random boolean with given probability of true.
     */
    public static boolean randomBoolean(long runSeed,
                                        int year,
                                        int processCode,
                                        long cellId,
                                        long aftId,
                                        int drawIndex,
                                        double probabilityTrue) {

        if (probabilityTrue < 0.0 || probabilityTrue > 1.0) {
            throw new IllegalArgumentException("probabilityTrue must be in [0,1]");
        }
        return randomDouble(runSeed, year, processCode, cellId, aftId, drawIndex) < probabilityTrue;
    }

    /**
     * Converts a 64-bit value to a uniform double in [0,1).
     */
    public static double toUnitDouble(long x) {
        return (x >>> 11) * 0x1.0p-53;
    }

    /**
     * Comparator that gives a deterministic random ordering for items based on their stable IDs.
     * Useful for random subset selection without replacement:
     * - sort by this comparator
     * - take first k
     */
    public static <T> Comparator<T> randomComparator(ToLongFunction<T> stableIdFunction,
                                                     long runSeed,
                                                     int year,
                                                     int processCode,
                                                     long cellId,
                                                     int drawIndex) {

        return Comparator
                .<T>comparingLong(item -> randomKey(
                        runSeed,
                        year,
                        processCode,
                        cellId,
                        stableIdFunction.applyAsLong(item),
                        drawIndex))
                .thenComparingLong(stableIdFunction);
    }

    /**
     * Sorts a list into a deterministic random order based on stable item IDs.
     */
    public static <T> void sortByRandomKey(List<T> items,
                                           ToLongFunction<T> stableIdFunction,
                                           long runSeed,
                                           int year,
                                           int processCode,
                                           long cellId,
                                           int drawIndex) {

        items.sort(randomComparator(stableIdFunction, runSeed, year, processCode, cellId, drawIndex));
    }

    /**
     * Deterministic tie-break key for a cell-vs-AFT decision.
     * Lower key can be treated as "wins tie" if desired.
     */
    public static long tieBreakKey(long runSeed,
                                   int year,
                                   long cellId,
                                   long aftId) {

        return randomKey(runSeed, year, Process.TIE_BREAK, cellId, aftId, 0);
    }
    
    /**
     * Deterministic pseudo-random Gaussian ~ N(0,1).
     * Uses Box-Muller with two deterministic uniform draws.
     */
    public static double randomGaussian(long runSeed,
                                        int year,
                                        int processCode,
                                        long cellId,
                                        long aftId,
                                        int drawIndex) {

        // Two independent uniforms in (0,1]
        double u1 = 1.0 - randomDouble(runSeed, year, processCode, cellId, aftId, drawIndex * 2);
        double u2 = 1.0 - randomDouble(runSeed, year, processCode, cellId, aftId, drawIndex * 2 + 1);

        return StrictMath.sqrt(-2.0 * StrictMath.log(u1))
                * StrictMath.cos(2.0 * StrictMath.PI * u2);
    }
}