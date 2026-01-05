package de.cesr.crafty.core.utils.analysis;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small, allocation-light step profiler for measuring wall-clock time of named model sections.
 *
 * The profiler is designed for quick performance diagnostics during a run (e.g., per tick/year):
 * callers wrap code blocks in a try-with-resources section created by {@link #section(String)}.
 * When the section closes, elapsed time is accumulated under the given name.
 *
 * Key characteristics:
 * - Can be fully disabled via the constructor flag; when disabled, {@link #section(String)} returns
 *   a no-op {@link Section} to keep calling code clean and branch-free.
 * - Uses {@link System#nanoTime()} for duration measurement.
 * - Preserves insertion order (LinkedHashMap) so the report lists sections in the order they were first seen.
 * - Supports repeated sections: time is aggregated and call counts are tracked.
 *
 * Typical usage:
 *   StepProfiler prof = new StepProfiler(true);
 *   try (StepProfiler.Section s = prof.section("Update capitals")) {
 *       // code...
 *   }
 *   System.out.println(prof.report("Year 2030"));
 *   prof.reset();
 */

/**
 * @author Mohamed Byari
 *
 */
public final class StepProfiler {

	/** Keeps insertion order so the report matches call order */
	private final Map<String, Stat> stats = new LinkedHashMap<>();
	private final boolean enabled;

	public StepProfiler(boolean enabled) {
		this.enabled = enabled;
	}

	/** Measure a named section using try-with-resources */
	public Section section(String name) {
		if (!enabled)
			return Section.NOOP;
		return new Section(this, name, System.nanoTime());
	}

	/** Reset between steps/years for per-step reporting */
	public void reset() {
		stats.clear();
	}

	/** Pretty report (ms) */
	public String report(String title) {
		if (!enabled)
			return "";

		StringBuilder sb = new StringBuilder();
		sb.append(title).append('\n');

		long totalNs = 0L;
		for (Map.Entry<String, Stat> e : stats.entrySet()) {
			totalNs += e.getValue().totalNs;
		}

		for (Map.Entry<String, Stat> e : stats.entrySet()) {
			Stat s = e.getValue();
			double ms = s.totalNs / 1_000_000.0;
			double pct = totalNs > 0 ? (100.0 * s.totalNs / totalNs) : 0.0;
			sb.append(String.format("  %-28s %9.3f ms  (%5.1f%%) %n", e.getKey(), ms, pct, s.calls));
		}
		sb.append(String.format("  %-28s %9.3f ms%n", "TOTAL", totalNs / 1_000_000.0));
		return sb.toString() + "\n";
	}

	private void add(String name, long durationNs) {
		Stat s = stats.computeIfAbsent(name, k -> new Stat());
		s.calls++;
		s.totalNs += durationNs;
	}

	private static final class Stat {
		long totalNs;
		int calls;
	}

	public static class Section implements AutoCloseable {
		private static final Section NOOP = new Section(null, "", 0L) {
			@Override
			public void close() {
				/* no-op */ }
		};
		private final StepProfiler owner;
		private final String name;
		private final long startNs;

		private Section(StepProfiler owner, String name, long startNs) {
			this.owner = owner;
			this.name = name;
			this.startNs = startNs;
		}

		@Override
		public void close() {
			if (owner == null)
				return;
			owner.add(name, System.nanoTime() - startNs);
		}
	}
}
