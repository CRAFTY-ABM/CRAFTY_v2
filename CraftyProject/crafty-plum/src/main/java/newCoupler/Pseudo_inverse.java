package newCoupler;

import java.util.*;

public class Pseudo_inverse {

	public enum GroupingMode {
		EXACT, PROPORTIONAL
	}

	// ---- Public API ----

	/**
	 * Build a back-transfer mapper using REGULARIZED least squares pseudo-inverse.
	 * Also collapses ambiguous groups (identical/proportional columns) into a
	 * single group-variable, then expands back using shares from commodityTotals.
	 *
	 * @param serviceToCommodity <service, <commodity, w>> forward matrix
	 * @param commodityTotals    <commodity, ref> used to compute shares within
	 *                           ambiguous groups
	 * @param mode               EXACT or PROPORTIONAL grouping for ambiguity
	 *                           detection
	 * @param tol                signature quantization (e.g. 1e-9)
	 * @param eps                near-zero cutoff (e.g. 1e-12)
	 * @param lambda             regularization strength (e.g. 1e-6 .. 1e-2; tune to
	 *                           reduce error)
	 */
	public static Map<String, Map<String, Double>> buildCorrectedBackTransferMapper(
			Map<String, Map<String, Double>> serviceToCommodity, Map<String, Double> commodityTotals) {
		return buildCorrectedBackTransferMapper(serviceToCommodity, commodityTotals, GroupingMode.EXACT, 1e-9, 1e-12, 1e-3);
	}

	public static Map<String, Map<String, Double>> buildCorrectedBackTransferMapper(
			Map<String, Map<String, Double>> serviceToCommodity, Map<String, Double> commodityTotals, GroupingMode mode,
			double tol, double eps, double lambda) {
		// 1) transpose forward -> <commodity, <service,w>> (needed for ambiguity
		// detection)
		Map<String, Map<String, Double>> commodityToService = transposeServiceToCommodity(serviceToCommodity,
				commodityTotals, false, eps); 

		// 2) detect ambiguous groups + shares
		AmbiguityInfo amb = detectAmbiguityAndComputeShares(commodityToService, commodityTotals, mode, tol, eps);

		// 3) build "variables": unique commodities + ambiguous group totals
		// varName = commodity for unique, or "GROUP::<key>" for ambiguous group total
		Map<String, String> commodityToVar = new LinkedHashMap<>();
		Set<String> ambiguousCommodities = amb.commodityToAmbiguousGroup.keySet();

		for (String c : commodityToService.keySet()) {
			if (ambiguousCommodities.contains(c)) {
				String g = amb.commodityToAmbiguousGroup.get(c);
				commodityToVar.put(c, "GROUP::" + g);
			} else {
				commodityToVar.put(c, c);
			}
		}

		// unique var list
		LinkedHashSet<String> varSet = new LinkedHashSet<>(commodityToVar.values());
		List<String> vars = new ArrayList<>(varSet);

		// services list
		List<String> services = new ArrayList<>(serviceToCommodity.keySet());

		int S = services.size();
		int V = vars.size();

		// 4) Build collapsed forward matrix Wc (S x V)
		// For unique commodity vars: use its column
		// For group vars: use representative column of any member (columns are
		// proportional/identical by grouping definition)
		double[][] Wc = new double[S][V];
		Map<String, Map<String, Double>> repColumnForGroup = pickRepresentativeColumns(amb, commodityToService);

		for (int si = 0; si < S; si++) {
			String s = services.get(si);

			for (int vi = 0; vi < V; vi++) {
				String var = vars.get(vi);

				double w = 0.0;
				if (var.startsWith("GROUP::")) {
					String gKey = var.substring("GROUP::".length());
					Map<String, Double> rep = repColumnForGroup.get(gKey); // <service, w>
					if (rep != null)
						w = rep.getOrDefault(s, 0.0);
				} else {
					Map<String, Double> col = commodityToService.get(var); // because var == commodity
					if (col != null)
						w = col.getOrDefault(s, 0.0);
				}

				if (Math.abs(w) <= eps)
					w = 0.0;
				Wc[si][vi] = w;
			}
		}

		// 5) Compute pseudo-inverse P = (W^T W + lambda I)^-1 W^T => size (V x S)
		double[][] P = regularizedPseudoInverse(Wc, lambda, eps);

		// 6) Convert to var->service mapper, then expand group vars back to commodities
		// via shares
		Map<String, Map<String, Double>> varToService = new LinkedHashMap<>();
		for (int vi = 0; vi < V; vi++) {
			String var = vars.get(vi);
			Map<String, Double> m = new LinkedHashMap<>();
			for (int si = 0; si < S; si++) {
				double w = P[vi][si];
				if (Math.abs(w) > eps)
					m.put(services.get(si), w);
			}
			varToService.put(var, m);
		}

		// 7) Expand to commodity mapper
		Map<String, Map<String, Double>> commodityBack = new LinkedHashMap<>();
		for (String c : commodityToService.keySet()) {
			String var = commodityToVar.get(c);

			if (var.startsWith("GROUP::")) {
				String gKey = var.substring("GROUP::".length());
				double share = amb.ambiguousGroupShares.getOrDefault(gKey, Collections.emptyMap()).getOrDefault(c, 0.0);

				Map<String, Double> base = varToService.getOrDefault(var, Collections.emptyMap());
				Map<String, Double> scaled = new LinkedHashMap<>();
				for (Map.Entry<String, Double> e : base.entrySet()) {
					double w = e.getValue() * share;
					if (Math.abs(w) > eps)
						scaled.put(e.getKey(), w);
				}
				commodityBack.put(c, scaled);

			} else {
				commodityBack.put(c, new LinkedHashMap<>(varToService.getOrDefault(var, Collections.emptyMap())));
			}
		}

		// 8) Optional but highly recommended: override pure-partition commodities
		// exactly
		// (fixes RoundwoodDemand case: Softwood/Hardwood)
		applyPurePartitionOverride(serviceToCommodity, commodityBack, eps);

		return commodityBack;
	}

	// ---- Helpers you already had / need ----
	// transposeServiceToCommodity(...)
	// detectAmbiguityAndComputeShares(...) + AmbiguityInfo
	// and add the matrix + override helpers below.

	// Pick one representative column per ambiguous group (any member works)
	private static Map<String, Map<String, Double>> pickRepresentativeColumns(AmbiguityInfo amb,
			Map<String, Map<String, Double>> commodityToService) {
		Map<String, Map<String, Double>> rep = new LinkedHashMap<>();
		for (Map.Entry<String, List<String>> g : amb.ambiguousGroups.entrySet()) {
			String gKey = g.getKey();
			String first = g.getValue().get(0);
			rep.put(gKey, commodityToService.getOrDefault(first, Collections.emptyMap()));
		}
		return rep;
	}

	// Regularized pseudo-inverse: P = (W^T W + lambda I)^-1 W^T
	private static double[][] regularizedPseudoInverse(double[][] W, double lambda, double eps) {
		int S = W.length;
		int V = W[0].length;

		// A = W^T W + lambda I (V x V)
		double[][] A = new double[V][V];
		for (int i = 0; i < V; i++) {
			for (int j = 0; j < V; j++) {
				double sum = 0.0;
				for (int k = 0; k < S; k++)
					sum += W[k][i] * W[k][j];
				A[i][j] = sum;
			}
			A[i][i] += lambda;
		}

		// B = W^T (V x S)
		double[][] B = new double[V][S];
		for (int i = 0; i < V; i++) {
			for (int k = 0; k < S; k++)
				B[i][k] = W[k][i];
		}

		// Solve A * X = B for X (V x S)
		return solveLinearSystem(A, B, eps);
	}

	// Gaussian elimination with partial pivoting for multiple RHS
	private static double[][] solveLinearSystem(double[][] A, double[][] B, double eps) {
		int n = A.length;
		int m = B[0].length;

		// Augment A with B
		double[][] M = new double[n][n + m];
		for (int i = 0; i < n; i++) {
			System.arraycopy(A[i], 0, M[i], 0, n);
			System.arraycopy(B[i], 0, M[i], n, m);
		}

		// Elimination
		for (int p = 0; p < n; p++) {
			// pivot
			int max = p;
			double maxVal = Math.abs(M[p][p]);
			for (int i = p + 1; i < n; i++) {
				double v = Math.abs(M[i][p]);
				if (v > maxVal) {
					maxVal = v;
					max = i;
				}
			}
			if (maxVal <= eps) {
				// matrix is near-singular: increase regularization (caller should do this),
				// but we still avoid crash by leaving row as-is
				continue;
			}
			if (max != p) {
				double[] tmp = M[p];
				M[p] = M[max];
				M[max] = tmp;
			}

			// normalize pivot row
			double piv = M[p][p];
			for (int j = p; j < n + m; j++)
				M[p][j] /= piv;

			// eliminate below/above
			for (int i = 0; i < n; i++) {
				if (i == p)
					continue;
				double factor = M[i][p];
				if (Math.abs(factor) <= eps)
					continue;
				for (int j = p; j < n + m; j++) {
					M[i][j] -= factor * M[p][j];
				}
			}
		}

		// Extract X
		double[][] X = new double[n][m];
		for (int i = 0; i < n; i++) {
			System.arraycopy(M[i], n, X[i], 0, m);
		}
		return X;
	}

	// Override commodities reconstructible from pure service rows:
	// commodity = sum(serviceValues)/sum(weights) -> so mapper weight = 1/sumW for
	// those services
	private static void applyPurePartitionOverride(Map<String, Map<String, Double>> serviceToCommodity,
			Map<String, Map<String, Double>> commodityBack, double eps) {
		Map<String, Map<String, Double>> pureByCommodity = new LinkedHashMap<>();

		for (Map.Entry<String, Map<String, Double>> sEntry : serviceToCommodity.entrySet()) {
			String service = sEntry.getKey();
			Map<String, Double> row = sEntry.getValue();
			if (row == null)
				continue;

			String onlyC = null;
			int nz = 0;
			double w = 0.0;

			for (Map.Entry<String, Double> e : row.entrySet()) {
				if (e.getKey() == null || e.getValue() == null)
					continue;
				double val = e.getValue();
				if (Math.abs(val) <= eps)
					continue;
				nz++;
				onlyC = e.getKey();
				w = val;
				if (nz > 1)
					break;
			}

			if (nz == 1 && onlyC != null) {
				pureByCommodity.computeIfAbsent(onlyC, k -> new LinkedHashMap<>()).put(service, w);
			}
		}

		for (Map.Entry<String, Map<String, Double>> e : pureByCommodity.entrySet()) {
			String commodity = e.getKey();
			Map<String, Double> pure = e.getValue();

			double sumW = 0.0;
			for (double ww : pure.values())
				sumW += ww;
			if (Math.abs(sumW) <= eps)
				continue;

			double inv = 1.0 / sumW;
			Map<String, Double> invMap = new LinkedHashMap<>();
			for (String service : pure.keySet())
				invMap.put(service, inv);

			commodityBack.put(commodity, invMap);
		}
	}

	// -----------------------------
	// 1) TRANSPOSE: service->commodity ==> commodity->service
	// -----------------------------
	
	
	
	public static Map<String, Map<String, Double>> transposeServiceToCommodity(
			Map<String, Map<String, Double>> serviceToCommodity, // <service, <commodity, weight>>
			Map<String, Double> commodityTotals, // <commodity, totalDemand> (can be empty)
			boolean normalizePerCommodity, double eps) {
		Objects.requireNonNull(serviceToCommodity, "serviceToCommodity");
		Objects.requireNonNull(commodityTotals, "commodityTotals");

		Map<String, Map<String, Double>> commodityToService = new LinkedHashMap<>();

		// Pre-seed commodities from totals (so unmapped commodities still exist in
		// output)
		for (String c : commodityTotals.keySet()) {
			commodityToService.put(c, new LinkedHashMap<>());
		}

		// Transpose
		for (Map.Entry<String, Map<String, Double>> sEntry : serviceToCommodity.entrySet()) {
			String service = sEntry.getKey();
			Map<String, Double> inner = sEntry.getValue();
			if (inner == null)
				continue;

			for (Map.Entry<String, Double> cEntry : inner.entrySet()) {
				String commodity = cEntry.getKey();
				Double wObj = cEntry.getValue();
				if (commodity == null || wObj == null)
					continue;

				double w = wObj;
				if (Math.abs(w) <= eps)
					continue;

				commodityToService.computeIfAbsent(commodity, k -> new LinkedHashMap<>()).put(service, w);
			}
		}

		// Optional: normalize per commodity so sum_service weights = 1 for each
		// commodity
		if (normalizePerCommodity) {
			for (Map.Entry<String, Map<String, Double>> e : commodityToService.entrySet()) {
				Map<String, Double> sw = e.getValue();
				if (sw == null || sw.isEmpty())
					continue;

				double sum = 0.0;
				for (double v : sw.values())
					sum += v;

				if (Math.abs(sum) > eps) {
					for (Map.Entry<String, Double> x : sw.entrySet()) {
						x.setValue(x.getValue() / sum);
					}
				}
			}
		}

		return commodityToService;
	}

	public static AmbiguityInfo detectAmbiguityAndComputeShares(Map<String, Map<String, Double>> commodityToService,
			Map<String, Double> commodityTotals, GroupingMode mode, double tol, double eps) {
		Objects.requireNonNull(commodityToService, "commodityToService");
		Objects.requireNonNull(commodityTotals, "commodityTotals");
		Objects.requireNonNull(mode, "mode");

		// Collect all services (for consistent signatures)
		SortedSet<String> allServices = new TreeSet<>();
		for (Map<String, Double> sw : commodityToService.values()) {
			if (sw != null)
				allServices.addAll(sw.keySet());
		}
		List<String> services = new ArrayList<>(allServices);

		// signatureKey -> list of commodities
		Map<String, List<String>> groups = new LinkedHashMap<>();

		for (Map.Entry<String, Map<String, Double>> e : commodityToService.entrySet()) {
			String commodity = e.getKey();
			Map<String, Double> sw = e.getValue();

			String sig = buildSignature(commodity, sw, services, mode, tol, eps);
			groups.computeIfAbsent(sig, k -> new ArrayList<>()).add(commodity);
		}

		// Extract ambiguous groups (size > 1), ignore "ZERO" group (unmapped)
		Map<String, List<String>> ambiguousGroups = new LinkedHashMap<>();
		for (Map.Entry<String, List<String>> g : groups.entrySet()) {
			if ("ZERO".equals(g.getKey()))
				continue;
			if (g.getValue().size() > 1) {
				ambiguousGroups.put(g.getKey(), Collections.unmodifiableList(g.getValue()));
			}
		}

		// Compute shares inside ambiguous groups from commodityTotals
		Map<String, Map<String, Double>> groupShares = new LinkedHashMap<>();
		Map<String, String> commodityToGroup = new LinkedHashMap<>();

		for (Map.Entry<String, List<String>> g : ambiguousGroups.entrySet()) {
			String groupKey = g.getKey();
			List<String> coms = g.getValue();

			double total = 0.0;
			for (String c : coms)
				total += Math.max(0.0, commodityTotals.getOrDefault(c, 0.0));

			Map<String, Double> shares = new LinkedHashMap<>();
			if (total > eps) {
				for (String c : coms) {
					double v = Math.max(0.0, commodityTotals.getOrDefault(c, 0.0));
					shares.put(c, v / total);
					commodityToGroup.put(c, groupKey);
				}
			} else {
				// fallback: equal split if no reference info
				double eq = 1.0 / coms.size();
				for (String c : coms) {
					shares.put(c, eq);
					commodityToGroup.put(c, groupKey);
				}
			}

			groupShares.put(groupKey, Collections.unmodifiableMap(shares));
		}

		return new AmbiguityInfo(Collections.unmodifiableMap(groups), Collections.unmodifiableMap(ambiguousGroups),
				Collections.unmodifiableMap(groupShares), Collections.unmodifiableMap(commodityToGroup));
	}

	/**
	 * Result: ambiguous groups and their split shares derived from commodityTotals.
	 */
	public static final class AmbiguityInfo {
		/** GroupKey -> commodities in that group (includes unique groups too). */
		public final Map<String, List<String>> groupToCommodities;
		/** Only groups with size>1 (ambiguous). */
		public final Map<String, List<String>> ambiguousGroups;
		/**
		 * Only ambiguous groups: GroupKey -> (commodity -> share). Shares sum to 1
		 * within group.
		 */
		public final Map<String, Map<String, Double>> ambiguousGroupShares;
		/** Commodity -> groupKey (only for ambiguous commodities). */
		public final Map<String, String> commodityToAmbiguousGroup;

		private AmbiguityInfo(Map<String, List<String>> groupToCommodities, Map<String, List<String>> ambiguousGroups,
				Map<String, Map<String, Double>> ambiguousGroupShares, Map<String, String> commodityToAmbiguousGroup) {
			this.groupToCommodities = groupToCommodities;
			this.ambiguousGroups = ambiguousGroups;
			this.ambiguousGroupShares = ambiguousGroupShares;
			this.commodityToAmbiguousGroup = commodityToAmbiguousGroup;
		}

		/**
		 * Convenience: returns share for commodity if ambiguous, otherwise 1.0 (i.e.,
		 * no split needed).
		 */
		public double shareOrOne(String commodity) {
			String g = commodityToAmbiguousGroup.get(commodity);
			if (g == null)
				return 1.0;
			Map<String, Double> shares = ambiguousGroupShares.get(g);
			if (shares == null)
				return 1.0;
			return shares.getOrDefault(commodity, 1.0);
		}
	}

	// -----------------------------
	// Signature builder (core of grouping)
	// -----------------------------
	private static String buildSignature(String commodity, Map<String, Double> serviceWeights, List<String> allServices,
			GroupingMode mode, double tol, double eps) {
		// Build dense vector in stable service order
		double[] v = new double[allServices.size()];
		boolean any = false;
		for (int i = 0; i < allServices.size(); i++) {
			String s = allServices.get(i);
			double w = 0.0;
			if (serviceWeights != null) {
				Double wObj = serviceWeights.get(s);
				if (wObj != null)
					w = wObj;
			}
			if (Math.abs(w) <= eps)
				w = 0.0;
			v[i] = w;
			if (w != 0.0)
				any = true;
		}

		if (!any)
			return "ZERO";

		// If PROPORTIONAL: normalize by first non-zero element so collinear vectors
		// match
		if (mode == GroupingMode.PROPORTIONAL) {
			double scale = 0.0;
			for (double x : v) {
				if (x != 0.0) {
					scale = x;
					break;
				}
			}
			if (scale == 0.0)
				return "ZERO";
			// make first non-zero positive
			if (scale < 0)
				scale = -scale;

			for (int i = 0; i < v.length; i++) {
				v[i] = v[i] / scale;
				if (Math.abs(v[i]) <= eps)
					v[i] = 0.0;
			}
		}

		// Quantize into integer bins to make stable keys under tol
		// (tol is your comparison “resolution”; bigger tol => more aggressive grouping)
		StringBuilder sb = new StringBuilder();
		sb.append(mode.name()).append('|');
		for (int i = 0; i < v.length; i++) {
			long q = quantize(v[i], tol);
			// include service name for debugging transparency (safe even if order changes)
			sb.append(allServices.get(i)).append('=').append(q).append(';');
		}
		return sb.toString();
	}

	private static long quantize(double x, double tol) {
		if (tol <= 0) {
			// fall back: exact-ish (not recommended)
			return Double.doubleToLongBits(x);
		}
		return Math.round(x / tol);
	}
}