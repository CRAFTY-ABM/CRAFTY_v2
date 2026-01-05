package de.cesr.crafty.gui.utils.graphical.helpres;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * NestedVoronoiDiskChart --------------------- A JavaFX Region that renders a
 * circular "Voronoi pie" (top-level groups like a pie chart, but with
 * Voronoi-like island boundaries), and each top-level region is further
 * subdivided into nested Voronoi sub-cells.
 *
 * Rendering is raster-based (computed on a grid) to avoid heavy computational
 * geometry libs.
 *
 * Usage: NestedVoronoiDiskChart chart = new NestedVoronoiDiskChart();
 * chart.setData(data, colors); chart.setPreferredResolution(520);
 */
public class NestedVoronoiDiskChart extends Region {

	// ===== Public API =====
	/**
	 * @param data   parent -> (child -> value)
	 * @param colors parent -> (child -> color) (optionally include "__PARENT__" for
	 *               parent color)
	 */
	public void setData(Map<String, Map<String, Double>> data, Map<String, Map<String, Color>> colors) {
		this.data = (data == null) ? Map.of() : deepCopy(data);
		this.colors = (colors == null) ? Map.of() : deepCopyColors(colors);
		this.colors.put("__PARENT__", colors.values().iterator().next());
		requestRedraw();
	}

	/** Grid resolution (higher = nicer boundaries, slower). Typical: 420..700 */
	public void setPreferredResolution(int res) {
		this.preferredResolution = clampInt(res, 240, 1200);
		requestRedraw();
	}

	/** Set boundary thickness in pixels on the internal grid. Typical: 1..3 */
	public void setBoundaryThickness(int thickness) {
		this.boundaryThickness = clampInt(thickness, 0, 8);
		requestRedraw();
	}

	/** Optional: call when your app closes to stop background thread. */
	public void dispose() {
		executor.shutdownNow();
	}

	// ===== Internals =====
	private final Canvas canvas = new Canvas();
	private final Tooltip tooltip = new Tooltip();

	private Map<String, Map<String, Double>> data = Map.of();
//	private Map<String, Color> parentColors = Map.of();
	private Map<String, Map<String, Color>> colors = Map.of();

	private int preferredResolution = 520;
	private int parentIterations = 32;
	private int childIterations = 26;
	private int boundaryThickness = 2;

	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "NestedVoronoiDiskChart-Renderer");
		t.setDaemon(true);
		return t;
	});

	private volatile Future<?> currentJob;
	private volatile RenderResult lastResult;

	public NestedVoronoiDiskChart() {
		getChildren().add(canvas);
		Tooltip.install(canvas, tooltip);
		tooltip.setAutoHide(true);

		// Redraw on resize (debounced by cancelling previous render job)
		widthProperty().addListener((_, _, _) -> requestRedraw());
		heightProperty().addListener((_, _, _) -> requestRedraw());

		canvas.setOnMouseMoved(e -> {
			RenderResult rr = lastResult;
			if (rr == null)
				return;

			double w = canvas.getWidth();
			double h = canvas.getHeight();
			double size = Math.min(w, h);
			double ox = (w - size) * 0.5;
			double oy = (h - size) * 0.5;

			double nx = ((e.getX() - ox) / size) * 2.0 - 1.0;
			double ny = ((e.getY() - oy) / size) * 2.0 - 1.0;

			String text = rr.pick(nx, ny);
			tooltip.setText(text == null ? "" : text);
		});
	}

	// --- Convergence controls ---
	private double parentTol = 0.010;
	private double childTol = 0.020;
	private int stableNeeded = 3;
	private int minTargetPx = 40;

	public void setConvergence(double parentTol, double childTol, int stableNeeded, int minTargetPx) {
		this.parentTol = clamp(parentTol, 0.001, 0.20);
		this.childTol = clamp(childTol, 0.001, 0.30);
		this.stableNeeded = clampInt(stableNeeded, 1, 20);
		this.minTargetPx = clampInt(minTargetPx, 1, 2000);
		requestRedraw();
	}

	private static double maxRelErr(int[] count, double[] target, int minTargetPx) {
		double max = 0.0;
		for (int i = 0; i < count.length; i++) {
			double ta = Math.max(target[i], minTargetPx); // avoid tiny targets dominating
			double err = (count[i] - target[i]) / ta;
			max = Math.max(max, Math.abs(err));
		}
		return max;
	}

	@Override
	protected void layoutChildren() {
		canvas.setWidth(getWidth());
		canvas.setHeight(getHeight());
		// Redraw is handled by listeners too, but keep it safe:
		requestRedraw();
	}

	private void requestRedraw() {
		// Avoid work when not visible / too small
		if (getWidth() <= 10 || getHeight() <= 10)
			return;

		// cancel previous job
		Future<?> job = currentJob;
		if (job != null)
			job.cancel(true);

		final int res = preferredResolution;
		final int pIters = parentIterations;
		final int cIters = childIterations;
		final int bThick = boundaryThickness;

		final Map<String, Map<String, Double>> dataSnap = deepCopy(this.data);
		final Map<String, Map<String, Color>> colorsSnap = deepCopyColors(this.colors);

		currentJob = executor.submit(() -> {
			RenderResult rr = render(dataSnap, colorsSnap, res, pIters, cIters, bThick);

			if (Thread.currentThread().isInterrupted())
				return;

			Platform.runLater(() -> {
				lastResult = rr;
				drawToCanvas(rr);
			});
		});
	}

	private double outerPaddingFrac = 0.08; // 10% of size (tune 0.08..0.15)

	public void setOuterPaddingFrac(double f) {
		outerPaddingFrac = Math.max(0, Math.min(0.25, f));
		requestRedraw();
	}

	private void drawToCanvas(RenderResult rr) {
		GraphicsContext g = canvas.getGraphicsContext2D();
		double w = canvas.getWidth();
		double h = canvas.getHeight();
		g.clearRect(0, 0, w, h);

		double base = Math.min(w, h);
		double pad = base * outerPaddingFrac; // reserved margin for arcs/labels
		double size = base - 2 * pad; // actual chart diameter
		double ox = (w - size) * 0.5;
		double oy = (h - size) * 0.5;

		// draw image scaled
		g.drawImage(rr.image, ox, oy, size, size);

		// circle outline
		g.setStroke(Color.rgb(255, 255, 255, 0.9));
		g.setLineWidth(Math.max(1.0, size * 0.004));
		g.strokeOval(ox, oy, size, size);

		// --- child labels inside islands ---
		g.setFill(Color.rgb(0, 0, 0, 0.70));
		double baseFont = Math.max(10, size * 0.018);
		g.setFont(Font.font(baseFont));

		int minPixelsForLabel = Math.max(120, (int) (rr.N * rr.N * 0.0012)); // tune (0.12% of pixels)

		for (int p = 0; p < rr.children.size(); p++) {
			List<ChildMeta> kids = rr.children.get(p);
			if (kids == null || kids.isEmpty())
				continue;

			for (ChildMeta cm : kids) {
				if (cm.count() < minPixelsForLabel)
					continue;

				double x = ox + ((cm.cx() + 1) * 0.5) * size;
				double y = oy + ((cm.cy() + 1) * 0.5) * size;

				// Simple centering hack: shift a bit left depending on name length
				String label = cm.name();
				double approxWidth = label.length() * baseFont * 0.55;
				g.fillText(label, x - approxWidth * 0.5, y + baseFont * 0.35);
			}
		}
		double m = size * 0.01;
//		arcs
		arc(rr, g, size, ox, oy, m);
		// --- parent labels outside the disk ---
		parentLabels(rr, g, size, ox, oy, m);

	}

	private void parentLabels(RenderResult rr, GraphicsContext g, double size, double ox, double oy, double m) {
		g.setFill(Color.BLACK);
		double font = Math.max(11, size * 0.020);
		g.setFont(Font.font(font));
		// (optional but nicer) center alignment:
		g.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
		g.setTextBaseline(javafx.geometry.VPos.CENTER);

		double radius = size * 0.5;
		double lineW = Math.max(3.0, size * 0.02);
		double arcOuterRadius = radius + m + lineW * 0.5;
		double labelRadiusPx = arcOuterRadius + Math.max(10, size * 0.03);

		double cx = ox + radius;
		double cy = oy + radius;

		for (ParentMeta pm : rr.parents) {
			double ang = pm.midAng();

			double x = cx + Math.cos(ang) * labelRadiusPx;
			double y = cy + Math.sin(ang) * labelRadiusPx;

			g.fillText(pm.name(), x, y);
		}
	}

	private void arc(RenderResult rr, GraphicsContext g, double size, double ox, double oy, double m) {
		/// arc

		double arcX = ox - m, arcY = oy - m, arcW = size + 2 * m, arcH = size + 2 * m;
		g.setLineWidth(Math.max(3.0, size * 0.02));

		for (ArcSeg s : rr.arcs) {
			g.setStroke(s.color.darker());

			double a0 = s.a0;
			double a1 = s.a1;

			// If the segment wraps past 2π -> split into [a0..2π] and [0..a1]
			if (a1 < a0) {
				strokeArcRad(g, arcX, arcY, arcW, arcH, a0, 2 * Math.PI);
				strokeArcRad(g, arcX, arcY, arcW, arcH, 0, a1);
			} else {
				strokeArcRad(g, arcX, arcY, arcW, arcH, a0, a1);
			}
		}

	}

	private static void strokeArcRad(GraphicsContext g, double x, double y, double w, double h, double a0, double a1) {
// JavaFX strokeArc uses degrees, 0° at 3 o'clock, and positive extends CCW.
		double startDeg = -Math.toDegrees(a0);
		double extentDeg = -Math.toDegrees(a1 - a0);

// guard against tiny arcs
		if (Math.abs(extentDeg) < 0.05)
			return;

		g.strokeArc(x, y, w, h, startDeg, extentDeg, ArcType.OPEN);
	}

	// ===== Rendering core =====

	private RenderResult render(Map<String, Map<String, Double>> rawData, Map<String, Map<String, Color>> colors, int N,
			int parentIters, int childIters, int boundaryThickness) {

		// Sanitize & flatten
		List<ParentGroup> parents = buildParents(rawData);

		// Edge case: nothing to draw
		if (parents.isEmpty()) {
			return RenderResult.empty(N);
		}

		// Precompute inside-disk mask and normalized coords
		boolean[] inside = new boolean[N * N];
		double[] px = new double[N * N];
		double[] py = new double[N * N];
		int insideCount = 0;

		for (int y = 0; y < N; y++) {
			for (int x = 0; x < N; x++) {
				int idx = y * N + x;
				double nx = ((x + 0.5) / N) * 2.0 - 1.0;
				double ny = ((y + 0.5) / N) * 2.0 - 1.0;
				px[idx] = nx;
				py[idx] = ny;
				if (nx * nx + ny * ny <= 1.0) {
					inside[idx] = true;
					insideCount++;
				}
			}
		}

		double grandTotal = parents.stream().mapToDouble(p -> p.total).sum();
		if (grandTotal <= 0) {
			return RenderResult.empty(N);
		}

		// Build parent seeds around the circle in "pie order" (sorted by total desc for
		// stability)
		// If you want a specific order, pass a LinkedHashMap as input (and remove the
		// sort below).
		parents.sort(Comparator.comparingDouble((ParentGroup p) -> p.total).reversed().thenComparing(p -> p.name));

		double startAngle = -Math.PI / 2.0;
		double acc = 0.0;

		Random rnd = new Random(12345);

		List<Seed> parentSeeds = new ArrayList<>();
		List<Double> parentStartAng = new ArrayList<>();
		List<Double> parentEndAng = new ArrayList<>();
		List<Double> parentMidAng = new ArrayList<>();
		for (ParentGroup pg : parents) {
			double frac = pg.total / grandTotal;
			double span = frac * 2.0 * Math.PI;

			double a0 = startAngle + acc; // start
			double a1 = a0 + span; // end
			double am = a0 + span * 0.5; // mid
			acc += span;

			double r = 0.62 + (rnd.nextDouble() - 0.5) * 0.06;
			double sx = r * Math.cos(am);
			double sy = r * Math.sin(am);

			Color base = resolveParentColor(pg.name, colors);
			parentSeeds.add(new Seed(pg.name, sx, sy, am, 0.0, pg.total, base));

			// store angles somewhere to use later
			// easiest: keep arrays aligned with seeds
			parentStartAng.add(a0);
			parentEndAng.add(a1);
			parentMidAng.add(am);
		}

		// Parent assignment
		int[] parentId = new int[N * N];
		Arrays.fill(parentId, -1);

		// Target areas proportional to totals
		double[] targetArea = new double[parentSeeds.size()];
		for (int i = 0; i < parentSeeds.size(); i++) {
			targetArea[i] = insideCount * (parentSeeds.get(i).value / grandTotal);
		}

		// Weight + radial relaxation (power diagram) to match target areas
		final double weightLR = 0.25; // lower than 0.85 (much more stable)
		final double radialMoveLR = 0.12;
		final double parentWeightClamp = 2.6; // keep weight comparable to dist^2

		int stable = 0;

		for (int it = 0; it < parentIters; it++) {

			// --- ASSIGN (as you already do) ---
			int[] count = new int[parentSeeds.size()];
			double[] sumX = new double[parentSeeds.size()];
			double[] sumY = new double[parentSeeds.size()];

			for (int idx = 0; idx < N * N; idx++) {
				if (!inside[idx])
					continue;
				double x = px[idx], y = py[idx];

				int best = -1;
				double bestScore = Double.POSITIVE_INFINITY;
				for (int s = 0; s < parentSeeds.size(); s++) {
					Seed seed = parentSeeds.get(s);
					double dx = x - seed.x, dy = y - seed.y;
					double score = (dx * dx + dy * dy) - seed.weight;
					if (score < bestScore) {
						bestScore = score;
						best = s;
					}
				}
				parentId[idx] = best;
				count[best]++;
				sumX[best] += x;
				sumY[best] += y;
			}

			// --- CHECK ---
			double maxAbs = maxRelErr(count, targetArea, minTargetPx);
			if (maxAbs < parentTol)
				stable++;
			else
				stable = 0;

			// IMPORTANT: if stable, STOP NOW (don’t update away from the good state)
			if (stable >= stableNeeded)
				break;

			// --- UPDATE ---
			for (int s = 0; s < parentSeeds.size(); s++) {
				Seed seed = parentSeeds.get(s);

				double ta = Math.max(1.0, targetArea[s]);
				double err = (ta - count[s]) / ta;
				seed.weight += weightLR * err;
				seed.weight = clamp(seed.weight, -parentWeightClamp, parentWeightClamp);

				if (count[s] > 0) {
					double cx = sumX[s] / count[s];
					double cy = sumY[s] / count[s];
					double rc = Math.hypot(cx, cy);
					double rOld = Math.hypot(seed.x, seed.y);
					double rNew = lerp(rOld, clamp(rc, 0.18, 0.90), radialMoveLR);
					seed.x = rNew * Math.cos(seed.fixedAngle);
					seed.y = rNew * Math.sin(seed.fixedAngle);
				}
			}
		}

		// If we stopped due to max iterations (not stable), do ONE FINAL ASSIGN using
		// final seeds:
		{
			int[] dummyCount = new int[parentSeeds.size()];
			for (int idx = 0; idx < N * N; idx++) {
				if (!inside[idx])
					continue;
				double x = px[idx], y = py[idx];

				int best = -1;
				double bestScore = Double.POSITIVE_INFINITY;
				for (int s = 0; s < parentSeeds.size(); s++) {
					Seed seed = parentSeeds.get(s);
					double dx = x - seed.x, dy = y - seed.y;
					double score = (dx * dx + dy * dy) - seed.weight;
					if (score < bestScore) {
						bestScore = score;
						best = s;
					}
				}
				parentId[idx] = best;
				dummyCount[best]++;
			}

			if (true) {
				int[] cnt = new int[data.size()];
				for (int idx = 0; idx < N * N; idx++)
					if (inside[idx])
						cnt[parentId[idx]]++;

				System.out.println("PARENT AREAS:");
				for (int p = 0; p < data.size(); p++) {
					double frac = cnt[p] / (double) insideCount;
					double target = targetArea[p] / (double) insideCount;
					System.out.printf(Locale.US, "  %-14s  frac=%.4f  target=%.4f%n", parentSeeds.get(p).name, frac,
							target);
				}
			}

		}

		List<ArcSeg> arcs = buildPerimeterArcs(N, inside, parentId, parentSeeds, colors);
		double[] midFromPerim = midAnglesFromPerimeter(arcs, data.size());

		// Build pools of pixels per parent for quick sampling of child seed positions
		// (reservoir sampling)
		int P = parentSeeds.size();
		int poolSize = 3500;
		int[][] pools = new int[P][poolSize];
		int[] poolFill = new int[P];
		long[] seen = new long[P];

		for (int idx = 0; idx < N * N; idx++) {
			int p = parentId[idx];
			if (p < 0)
				continue;
			long k = ++seen[p];
			int fill = poolFill[p];
			if (fill < poolSize) {
				pools[p][fill] = idx;
				poolFill[p] = fill + 1;
			} else {
				// reservoir replace with probability poolSize/k
				long r = (long) (rnd.nextDouble() * k);
				if (r < poolSize)
					pools[p][(int) r] = idx;
			}
		}

		// Nested children
		int[] childId = new int[N * N];
		Arrays.fill(childId, -1);

		List<ParentMeta> parentMetas = new ArrayList<>();
		List<List<ChildMeta>> childMetasAll = new ArrayList<>();

		// compute centroid for parent labels & keep counts
		int[] parentCountFinal = new int[P];
		double[] parentSumXFinal = new double[P];
		double[] parentSumYFinal = new double[P];

		for (int idx = 0; idx < N * N; idx++) {
			int p = parentId[idx];
			if (p < 0)
				continue;
			parentCountFinal[p]++;
			parentSumXFinal[p] += px[idx];
			parentSumYFinal[p] += py[idx];
		}

		for (int p = 0; p < P; p++) {
			Seed ps = parentSeeds.get(p);
			double cx = parentCountFinal[p] > 0 ? parentSumXFinal[p] / parentCountFinal[p] : ps.x;
			double cy = parentCountFinal[p] > 0 ? parentSumYFinal[p] / parentCountFinal[p] : ps.y;
			Color arcColor = resolveParentColor(ps.name, colors); // uses __PARENT__ if present
//			parentMetas.add(new ParentMeta(ps.name, ps.value, parentCountFinal[p], cx, cy, parentStartAng.get(p),
//					parentEndAng.get(p), parentMidAng.get(p), arcColor));
			double midAng = midFromPerim[p]; // <-- real perimeter mid
			parentMetas.add(new ParentMeta(ps.name, ps.value, parentCountFinal[p], cx, cy, parentStartAng.get(p),
					parentEndAng.get(p), midAng, arcColor));

		}

		// For each parent, run local weighted power diagram among its children
		for (int p = 0; p < P; p++) {
			ParentGroup pg = parents.get(p); // because we sorted parents same as seeds
			Map<String, Double> kidsMap = pg.children;

			if (kidsMap.isEmpty() || parentCountFinal[p] <= 0) {
				childMetasAll.add(List.of());
				continue;
			}

			double parentTotal = Math.max(1e-12, pg.total);

			List<String> childNames = new ArrayList<>(kidsMap.keySet());
			// stable order: largest first
			childNames.sort(Comparator.comparingDouble((String k) -> kidsMap.getOrDefault(k, 0.0)).reversed());
			// childNames.sort(Comparator.comparingDouble((String k) ->
			// kidsMap.getOrDefault(k, 0.0)).reversed().thenComparing(k -> k));
			int childK = childNames.size();
			int[] seedIdx = pickFarthestSeeds(pools[p], poolFill[p], childK, rnd, px, py);

			List<Seed> childSeeds = new ArrayList<>();
			for (int i = 0; i < childK; i++) {
				String cn = childNames.get(i);

				int idx = seedIdx[i];
				double sx = (idx >= 0) ? px[idx] : (rnd.nextDouble() * 2 - 1);
				double sy = (idx >= 0) ? py[idx] : (rnd.nextDouble() * 2 - 1);

				Color cc = resolveChildColor(pg.name, cn, colors, parentSeeds.get(p).baseColor);
				childSeeds.add(new Seed(cn, sx, sy, 0.0, 0.0, kidsMap.getOrDefault(cn, 0.0), cc));
			}

			// target area per child inside this parent
			double[] childTarget = new double[childSeeds.size()];
			for (int i = 0; i < childSeeds.size(); i++) {
				childTarget[i] = parentCountFinal[p] * (childSeeds.get(i).value / parentTotal);
			}

			final double childWeightLR = 0.06; // <<<<<< MUCH smaller
			final double childMoveLR = 0.32;
			final double childWeightClamp = 1.6; // keep comparable to dist^2 (~0..4)

			int stableC = 0;

			for (int it = 0; it < childIters; it++) {
				if (Thread.currentThread().isInterrupted())
					break;

				int[] cCount = new int[childSeeds.size()];
				double[] cSumX = new double[childSeeds.size()];
				double[] cSumY = new double[childSeeds.size()];

				// assignment (unchanged)
				for (int idx = 0; idx < N * N; idx++) {
					if (parentId[idx] != p)
						continue;

					double x = px[idx], y = py[idx];
					int best = -1;
					double bestScore = Double.POSITIVE_INFINITY;

					for (int s = 0; s < childSeeds.size(); s++) {
						Seed seed = childSeeds.get(s);
						double dx = x - seed.x;
						double dy = y - seed.y;
						double score = (dx * dx + dy * dy) - seed.weight;
						if (score < bestScore) {
							bestScore = score;
							best = s;
						}
					}

					childId[idx] = best;
					cCount[best]++;
					cSumX[best] += x;
					cSumY[best] += y;
				}

				// convergence check (NEW)
				double maxAbs = maxRelErr(cCount, childTarget, minTargetPx);
				if (maxAbs < childTol)
					stableC++;
				else
					stableC = 0;

				// update (unchanged)
				for (int s = 0; s < childSeeds.size(); s++) {
					Seed seed = childSeeds.get(s);
					double ta = Math.max(1.0, childTarget[s]);
					double err = (ta - cCount[s]) / ta;
					seed.weight += childWeightLR * err;
					seed.weight = clamp(seed.weight, -childWeightClamp, childWeightClamp);

					if (cCount[s] > 0) {
						double cx = cSumX[s] / cCount[s];
						double cy = cSumY[s] / cCount[s];
						double nx = lerp(seed.x, cx, childMoveLR);
						double ny = lerp(seed.y, cy, childMoveLR);

						int g = normToIndex(nx, ny, N);
						if (g >= 0 && parentId[g] == p) {
							seed.x = nx;
							seed.y = ny;
						} else {
							int sIdx = sampleFromPool(pools[p], poolFill[p], rnd);
							if (sIdx >= 0) {
								seed.x = px[sIdx];
								seed.y = py[sIdx];
							}
						}
					}
				}

				if (stableC >= stableNeeded)
					break;
			}

			// Store child centroids for optional labels / tooltips
			int C = childSeeds.size();
			int[] finalCount = new int[C];
			double[] finalSumX = new double[C];
			double[] finalSumY = new double[C];

			for (int idx = 0; idx < N * N; idx++) {
				if (parentId[idx] != p)
					continue;
				int c = childId[idx];
				if (c < 0)
					continue;
				finalCount[c]++;
				finalSumX[c] += px[idx];
				finalSumY[c] += py[idx];
			}

			List<ChildMeta> cm = new ArrayList<>();
			for (int c = 0; c < C; c++) {
				Seed s = childSeeds.get(c);
				double cx = finalCount[c] > 0 ? finalSumX[c] / finalCount[c] : s.x;
				double cy = finalCount[c] > 0 ? finalSumY[c] / finalCount[c] : s.y;
				cm.add(new ChildMeta(s.name, s.value, finalCount[c], cx, cy));
			}
			childMetasAll.add(cm);

		}

// ===== Gradient preparation (linear gradient per child cell) =====
		final boolean ENABLE_GRADIENT = true; // toggle
		final double LIGHT_AMT = 0.35; // 0..1
		final double DARK_AMT = 0.2; // 0..1

// direction per parent: outward (from center towards the parent seed)
		double[] gdx = new double[P];
		double[] gdy = new double[P];
		for (int p = 0; p < P; p++) {
			double vx = parentSeeds.get(p).x;
			double vy = parentSeeds.get(p).y;
			double inv = invSqrt(vx * vx + vy * vy);
			gdx[p] = vx * inv;
			gdy[p] = vy * inv;
		}

// For each parent, arrays per child: minProj/maxProj and gradient endpoint colors
		List<double[]> minProj = new ArrayList<>(P);
		List<double[]> maxProj = new ArrayList<>(P);
		List<int[]> lightArgb = new ArrayList<>(P);
		List<int[]> darkArgb = new ArrayList<>(P);

		for (int p = 0; p < P; p++) {
			int C = childMetasAll.get(p).size();
			if (C <= 0) {
				minProj.add(new double[0]);
				maxProj.add(new double[0]);
				lightArgb.add(new int[0]);
				darkArgb.add(new int[0]);
				continue;
			}

			double[] mn = new double[C];
			double[] mx = new double[C];
			Arrays.fill(mn, Double.POSITIVE_INFINITY);
			Arrays.fill(mx, Double.NEGATIVE_INFINITY);

			int[] la = new int[C];
			int[] da = new int[C];

			String parentName = parentSeeds.get(p).name;
			Color parentBase = parentSeeds.get(p).baseColor;

			for (int c = 0; c < C; c++) {
				String childName = childMetasAll.get(p).get(c).name();
				Color base = resolveChildColor(parentName, childName, colors, parentBase);
				int[] ends = gradientEndpoints(base, LIGHT_AMT, DARK_AMT);
				la[c] = ends[0];
				da[c] = ends[1];
			}

// compute min/max projection per child region
			for (int idx = 0; idx < N * N; idx++) {
				if (!inside[idx])
					continue;
				if (parentId[idx] != p)
					continue;

				int c = childId[idx];
				if (c < 0 || c >= C)
					continue;

				double proj = px[idx] * gdx[p] + py[idx] * gdy[p];
				if (proj < mn[c])
					mn[c] = proj;
				if (proj > mx[c])
					mx[c] = proj;
			}

// fallback if a child got no pixels
			for (int c = 0; c < C; c++) {
				if (!Double.isFinite(mn[c]) || !Double.isFinite(mx[c]) || mx[c] <= mn[c]) {
					mn[c] = -1;
					mx[c] = 1;
				}
			}

			minProj.add(mn);
			maxProj.add(mx);
			lightArgb.add(la);
			darkArgb.add(da);
		}

		// Compose final ARGB image buffer
		int[] argb = new int[N * N];
		Arrays.fill(argb, 0x00000000);

		// Paint region colors (with per-child linear gradient)
		for (int idx = 0; idx < N * N; idx++) {
			if (!inside[idx])
				continue;

			int p = parentId[idx];
			if (p < 0)
				continue;

			int c = childId[idx];

			if (c >= 0 && p < childMetasAll.size() && c < childMetasAll.get(p).size()) {
				if (ENABLE_GRADIENT) {
					double proj = px[idx] * gdx[p] + py[idx] * gdy[p];
					double mn = minProj.get(p)[c];
					double mx = maxProj.get(p)[c];
					double t = (proj - mn) / (mx - mn + 1e-12);

					// optional: bias to make highlight smaller/stronger
					// t = Math.pow(clamp(t,0,1), 0.85);

					argb[idx] = lerpArgb(lightArgb.get(p)[c], darkArgb.get(p)[c], t);
				} else {
					String parentName = parentSeeds.get(p).name;
					String childName = childMetasAll.get(p).get(c).name();
					Color base = resolveChildColor(parentName, childName, colors, parentSeeds.get(p).baseColor);
					argb[idx] = toArgbPre(base);
				}
			} else {
				// Parent-only (no child) fill
				argb[idx] = toArgbPre(parentSeeds.get(p).baseColor);
			}
		}

		// Draw boundaries
		if (boundaryThickness > 0) {
			int[] parentEdge = new int[N * N];
			int[] childEdge = new int[N * N];

			for (int idx = 0; idx < N * N; idx++) {
				if (!inside[idx])
					continue;

				int p0 = parentId[idx];
				int c0 = childId[idx];

				int x = idx % N;
				int y = idx / N;

				int[] nbs = new int[] { (x > 0) ? idx - 1 : -1, (x + 1 < N) ? idx + 1 : -1, (y > 0) ? idx - N : -1,
						(y + 1 < N) ? idx + N : -1 };

				for (int nb : nbs) {
					if (nb < 0 || !inside[nb])
						continue;
					int p1 = parentId[nb];
					int c1 = childId[nb];

					if (p1 != p0) {
						parentEdge[idx] = 1;
						break;
					} else if (c1 != c0) {
						childEdge[idx] = 1;
					}
				}
			}

			int[] parentThick = thicken4(parentEdge, N, boundaryThickness);
			int[] childThick = thicken4(childEdge, N, Math.max(1, boundaryThickness - 1));

			int parentColor = toArgb(Color.PINK);
			int childColor = toArgb(Color.WHITE);

			for (int idx = 0; idx < N * N; idx++) {
				if (!inside[idx])
					continue;
				if (childThick[idx] == 1)
					argb[idx] = childColor;
				if (parentThick[idx] == 1)
					argb[idx] = parentColor; // overwrite: parent edges win
			}
		}

		// Anti-alias outer edge (simple alpha fade near radius=1)
		for (int idx = 0; idx < N * N; idx++) {
			double x = px[idx], y = py[idx];
			double r2 = x * x + y * y;
			if (r2 <= 1.0) {
				double r = Math.sqrt(r2);
				// Fade in last ~2 pixels worth in normalized units
				double pxSize = 2.0 / N;
				double edge = 1.0 - r;
				if (edge < 2.2 * pxSize) {
					double a = clamp(edge / (2.2 * pxSize), 0, 1);
					argb[idx] = applyAlpha(argb[idx], a);
				}
			} else {
				argb[idx] = 0x00000000;
			}
		}

		// Create WritableImage
		javafx.scene.image.WritableImage img = new javafx.scene.image.WritableImage(N, N);
		img.getPixelWriter().setPixels(0, 0, N, N, javafx.scene.image.PixelFormat.getIntArgbPreInstance(), argb, 0, N);

		return new RenderResult(N, img, inside, parentId, childId, parentMetas, childMetasAll, arcs);
	}

	// ===== Helper structures =====

	private static Color resolveChildColor(String parent, String child, Map<String, Map<String, Color>> colors,
			Color parentFallback) {
		Map<String, Color> m = colors.get(parent);
		if (m != null) {
			Color c = m.get(child);
			if (c != null)
				return c;
		}
// fallback: slight variant of parent, but deterministic
		return varyChildColor(parentFallback, child);
	}

	private static final String PARENT_KEY = "__PARENT__";

	private static Color resolveParentColor(String parent, Map<String, Map<String, Color>> colors) {
		Map<String, Color> m = colors.get(parent);
		if (m != null) {
			Color c = m.get(PARENT_KEY);
			if (c != null)
				return c;

			// fallback: use first child color if present
			if (!m.isEmpty())
				return m.values().iterator().next();
		}
		return autoColor(parent);
	}

	private static Map<String, Map<String, Color>> deepCopyColors(Map<String, Map<String, Color>> src) {
		if (src == null)
			return Map.of();
		Map<String, Map<String, Color>> out = new LinkedHashMap<>();
		for (var e : src.entrySet()) {
			String parent = e.getKey();
			if (parent == null)
				continue;
			Map<String, Color> m = (e.getValue() == null) ? Map.of() : e.getValue();
			Map<String, Color> mm = new LinkedHashMap<>();
			for (var kv : m.entrySet()) {
				if (kv.getKey() != null && kv.getValue() != null) {
					mm.put(kv.getKey(), kv.getValue());
				}
			}
			out.put(parent, mm);
		}
		return out;
	}

	private static int[] thicken4(int[] mask, int N, int steps) {
		int[] cur = Arrays.copyOf(mask, mask.length);
		for (int s = 1; s < steps; s++) {
			int[] next = Arrays.copyOf(cur, cur.length);
			for (int idx = 0; idx < cur.length; idx++) {
				if (cur[idx] == 0)
					continue;
				int x = idx % N;
				int y = idx / N;
				if (x > 0)
					next[idx - 1] = 1;
				if (x + 1 < N)
					next[idx + 1] = 1;
				if (y > 0)
					next[idx - N] = 1;
				if (y + 1 < N)
					next[idx + N] = 1;
			}
			cur = next;
		}
		return cur;
	}

	private static int[] pickFarthestSeeds(int[] pool, int fill, int k, Random rnd, double[] px, double[] py) {
		int[] out = new int[k];
		Arrays.fill(out, -1);
		if (k <= 0)
			return out;
		if (fill <= 0)
			return out;

// if very few pixels, just sample (still better than duplicates)
		if (fill <= k) {
			for (int i = 0; i < k && i < fill; i++)
				out[i] = pool[i];
			for (int i = fill; i < k; i++)
				out[i] = pool[rnd.nextInt(fill)];
			return out;
		}

// 1st seed random
		out[0] = pool[rnd.nextInt(fill)];

// remaining: farthest-point sampling using random candidate trials
		int trials = Math.min(320, fill); // candidates per seed
		for (int i = 1; i < k; i++) {
			int bestIdx = -1;
			double bestMinD2 = -1;

			for (int t = 0; t < trials; t++) {
				int cand = pool[rnd.nextInt(fill)];
				double cx = px[cand], cy = py[cand];

				double minD2 = Double.POSITIVE_INFINITY;
				for (int j = 0; j < i; j++) {
					int s = out[j];
					double dx = cx - px[s];
					double dy = cy - py[s];
					double d2 = dx * dx + dy * dy;
					if (d2 < minD2)
						minD2 = d2;
				}

				if (minD2 > bestMinD2) {
					bestMinD2 = minD2;
					bestIdx = cand;
				}
			}

			out[i] = (bestIdx >= 0) ? bestIdx : pool[rnd.nextInt(fill)];
		}

		return out;
	}

	private static final class ParentGroup {
		final String name;
		final Map<String, Double> children;
		final double total;

		ParentGroup(String name, Map<String, Double> children) {
			this.name = name;
			this.children = children;
			this.total = children.values().stream().mapToDouble(v -> Math.max(0, v)).sum();
		}
	}

	private static final class Seed {
		final String name;
		double x, y;
		final double fixedAngle; // used mainly for parents to keep order
		double weight;
		final double value;
		final Color baseColor;

		Seed(String name, double x, double y, double fixedAngle, double weight, double value, Color baseColor) {
			this.name = name;
			this.x = x;
			this.y = y;
			this.fixedAngle = fixedAngle;
			this.weight = weight;
			this.value = value;
			this.baseColor = baseColor;
		}
	}

	private record ParentMeta(String name, double value, int count, double cx, double cy, double startAng,
			double endAng, double midAng, Color arcColor) {
	}

	private record ArcSeg(int parentIndex, double a0, double a1, Color color) {
	}

	private record ChildMeta(String name, double value, int count, double cx, double cy) {
	}

	private static final class RenderResult {
		final int N;
		final javafx.scene.image.WritableImage image;
		final boolean[] inside;
		final int[] parentId;
		final int[] childId;
		final List<ParentMeta> parents;
		final List<List<ChildMeta>> children; // indexed by parent
		final List<ArcSeg> arcs;

		RenderResult(int n, javafx.scene.image.WritableImage img, boolean[] inside, int[] parentId, int[] childId,
				List<ParentMeta> parents, List<List<ChildMeta>> children, List<ArcSeg> arcs) {
			this.N = n;
			this.image = img;
			this.inside = inside;
			this.parentId = parentId;
			this.childId = childId;
			this.parents = parents;
			this.children = children;
			this.arcs = arcs;
		}

		static RenderResult empty(int N) {
			javafx.scene.image.WritableImage img = new javafx.scene.image.WritableImage(N, N);
			return new RenderResult(N, img, new boolean[N * N], new int[N * N], new int[N * N], List.of(), List.of(),
					List.of());

		}

		String pick(double nx, double ny) {
			int idx = normToIndex(nx, ny, N);
			if (idx < 0)
				return null;
			if (!inside[idx])
				return null;

			int p = parentId[idx];
			if (p < 0 || p >= parents.size())
				return null;

			ParentMeta pm = parents.get(p);
			int c = childId[idx];

			if (c >= 0 && p < children.size() && c < children.get(p).size()) {
				ChildMeta cm = children.get(p).get(c);
				return pm.name + "  →  " + cm.name + "\n" + "value: " + nice(cm.value) + "   (parent: " + nice(pm.value)
						+ ")";
			} else {
				return pm.name + "\nvalue: " + nice(pm.value);
			}
		}

		private static String nice(double v) {
			if (v >= 1_000_000_000)
				return String.format(Locale.US, "%.2fB", v / 1_000_000_000.0);
			if (v >= 1_000_000)
				return String.format(Locale.US, "%.2fM", v / 1_000_000.0);
			if (v >= 1_000)
				return String.format(Locale.US, "%.2fK", v / 1_000.0);
			return String.format(Locale.US, "%.2f", v);
		}
	}

	// ===== Utilities =====

	private static List<ParentGroup> buildParents(Map<String, Map<String, Double>> raw) {
		if (raw == null || raw.isEmpty())
			return new ArrayList<>();

		// Filter nulls, coerce negatives to zero for totals
		List<ParentGroup> parents = new ArrayList<>();
		for (var e : raw.entrySet()) {
			String parent = e.getKey();
			if (parent == null)
				continue;
			Map<String, Double> kids = (e.getValue() == null) ? Map.of() : e.getValue();

			// remove null keys/values
			Map<String, Double> cleaned = kids.entrySet().stream()
					.filter(kv -> kv.getKey() != null && kv.getValue() != null).collect(Collectors.toMap(
							Map.Entry::getKey, kv -> Math.max(0.0, kv.getValue()), (a, _) -> a, LinkedHashMap::new));

			ParentGroup pg = new ParentGroup(parent, cleaned);
			if (pg.total > 0)
				parents.add(pg);
		}
		return parents;
	}

	private static int sampleFromPool(int[] pool, int fill, Random rnd) {
		if (fill <= 0)
			return -1;
		return pool[rnd.nextInt(fill)];
	}

	private static int normToIndex(double nx, double ny, int N) {
		if (nx < -1 || nx > 1 || ny < -1 || ny > 1)
			return -1;
		int x = (int) Math.floor(((nx + 1) * 0.5) * N);
		int y = (int) Math.floor(((ny + 1) * 0.5) * N);
		x = clampInt(x, 0, N - 1);
		y = clampInt(y, 0, N - 1);
		return y * N + x;
	}

	private static Color autoColor(String key) {
		int h = (key == null) ? 0 : key.hashCode();
		double hue = ((h & 0x7fffffff) % 360);
		return Color.hsb(hue, 0.45, 0.92);
	}

	private static Color varyChildColor(Color base, String key) {
		int h = (key == null) ? 0 : key.hashCode();
		double hueShift = ((h & 0x7fffffff) % 80) - 40; // -40..+39
		double hue = (base.getHue() + hueShift + 360.0) % 360.0;

		double sat = clamp(0.55 + 0.35 * frac01(h * 1103515245 + 12345), 0, 1);
		double bri = clamp(0.75 + 0.20 * frac01(h * 214013 + 2531011), 0, 1);

		return Color.hsb(hue, sat, bri, 1.0);
	}

	private static double frac01(int x) {
		long v = (x & 0xffffffffL);
		return (v / (double) 0xffffffffL); // 0..1
	}

	private static int toArgb(Color c) {
		int a = (int) Math.round(c.getOpacity() * 255);
		int r = (int) Math.round(c.getRed() * 255);
		int g = (int) Math.round(c.getGreen() * 255);
		int b = (int) Math.round(c.getBlue() * 255);
		// ARGB pre-multiplied not strictly needed because we use IntArgbPre, but alpha
		// is mostly 1 here.
		return (a << 24) | (r << 16) | (g << 8) | (b);
	}

	private static int applyAlpha(int argb, double alphaMul) {
		int a = (argb >>> 24) & 0xff;
		int r = (argb >>> 16) & 0xff;
		int g = (argb >>> 8) & 0xff;
		int b = (argb) & 0xff;
		int na = (int) Math.round(a * alphaMul);
		return (na << 24) | (r << 16) | (g << 8) | b;
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	private static int clampInt(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	private static Map<String, Map<String, Double>> deepCopy(Map<String, Map<String, Double>> src) {
		if (src == null)
			return Map.of();
		Map<String, Map<String, Double>> out = new LinkedHashMap<>();
		for (var e : src.entrySet()) {
			String k = e.getKey();
			Map<String, Double> v = e.getValue();
			if (k == null)
				continue;
			Map<String, Double> vv = (v == null) ? Map.of() : new LinkedHashMap<>(v);
			out.put(k, vv);
		}
		return out;
	}

	private static int toArgbPre(Color c) {
		double a = clamp(c.getOpacity(), 0, 1);
		int ai = (int) Math.round(a * 255);
		int ri = (int) Math.round(c.getRed() * 255 * a);
		int gi = (int) Math.round(c.getGreen() * 255 * a);
		int bi = (int) Math.round(c.getBlue() * 255 * a);
		return (ai << 24) | (ri << 16) | (gi << 8) | bi;
	}

	private static int lerpArgb(int a, int b, double t) {
		t = clamp(t, 0, 1);
		int aa = (a >>> 24) & 0xff, ar = (a >>> 16) & 0xff, ag = (a >>> 8) & 0xff, ab = a & 0xff;
		int ba = (b >>> 24) & 0xff, br = (b >>> 16) & 0xff, bg = (b >>> 8) & 0xff, bb = b & 0xff;
		int ca = (int) Math.round(aa + (ba - aa) * t);
		int cr = (int) Math.round(ar + (br - ar) * t);
		int cg = (int) Math.round(ag + (bg - ag) * t);
		int cb = (int) Math.round(ab + (bb - ab) * t);
		return (ca << 24) | (cr << 16) | (cg << 8) | cb;
	}

	/** create endpoints for a subtle linear gradient from a base color */
	private static int[] gradientEndpoints(Color base, double lightAmt, double darkAmt) {
		// light endpoint: blend towards white; dark endpoint: towards black
		Color light = base.interpolate(Color.WHITE, clamp(lightAmt, 0, 1));
		Color dark = base.interpolate(Color.BLACK, clamp(darkAmt, 0, 1));
		return new int[] { toArgbPre(light), toArgbPre(dark) };
	}

	private static double invSqrt(double x) {
		return 1.0 / Math.sqrt(Math.max(1e-12, x));
	}

	private static List<ArcSeg> buildPerimeterArcs(int N, boolean[] inside, int[] parentId, List<Seed> parentSeeds,
			Map<String, Map<String, Color>> colors) {
		final int P = parentSeeds.size();
		final int S = 2048; // samples around circle (increase for smoother)
		final double r = 0.995; // sample slightly inside the circle

		int[] pid = new int[S];

		for (int i = 0; i < S; i++) {
			double a = 2.0 * Math.PI * i / S; // 0 at 3 o'clock, CCW
			double nx = r * Math.cos(a);
			double ny = r * Math.sin(a);

			int idx = normToIndex(nx, ny, N);
			int p = (idx >= 0 && inside[idx]) ? parentId[idx] : -1;
			pid[i] = p;
		}

		boolean any = false;
		for (int i = 0; i < S; i++) {
			if (pid[i] >= 0) {
				any = true;
				break;
			}
		}
		if (!any)
			return List.of();

		// fill any -1 gaps safely
		for (int i = 0; i < S; i++) {
			if (pid[i] >= 0)
				continue;

			int j = i;
			int tries = 0;
			while (pid[j] < 0 && tries < S) {
				j = (j + 1) % S;
				tries++;
			}
			if (tries >= S)
				return List.of(); // safety
			pid[i] = pid[j];
		}

// run-length encode
		List<ArcSeg> segs = new ArrayList<>();
		int start = 0;
		int cur = pid[0];

		for (int i = 1; i <= S; i++) {
			int v = pid[i % S];
			if (v != cur || i == S) {
				int end = i;

				if (cur >= 0 && cur < P) {
					double a0 = 2.0 * Math.PI * start / S;
					double a1 = 2.0 * Math.PI * end / S;

					String parentName = parentSeeds.get(cur).name;
					Color arcColor = resolveParentColor(parentName, colors); // uses __PARENT__
					segs.add(new ArcSeg(cur, a0, a1, arcColor));
				}

				start = i;
				cur = v;
			}
		}

// merge wrap-around if first/last are same parent
		if (segs.size() >= 2) {
			ArcSeg first = segs.get(0);
			ArcSeg last = segs.get(segs.size() - 1);
			if (first.parentIndex == last.parentIndex) {
				ArcSeg merged = new ArcSeg(first.parentIndex, last.a0, first.a1, first.color);
				segs.set(0, merged);
				segs.remove(segs.size() - 1);
			}
		}

		return segs;
	}

	private static double arcLen(ArcSeg s) {
		return (s.a1 >= s.a0) ? (s.a1 - s.a0) : (2 * Math.PI - s.a0 + s.a1);
	}

	private static double arcMid(ArcSeg s) {
		double len = arcLen(s);
		double mid = s.a0 + len * 0.5;
		if (mid >= 2 * Math.PI)
			mid -= 2 * Math.PI;
		return mid;
	}

	private static double[] midAnglesFromPerimeter(List<ArcSeg> arcs, int P) {
		double[] bestLen = new double[P];
		double[] bestMid = new double[P];
		Arrays.fill(bestLen, -1);

		for (ArcSeg s : arcs) {
			double len = arcLen(s);
			int p = s.parentIndex;
			if (p >= 0 && p < P && len > bestLen[p]) {
				bestLen[p] = len;
				bestMid[p] = arcMid(s);
			}
		}
		return bestMid;
	}

}
