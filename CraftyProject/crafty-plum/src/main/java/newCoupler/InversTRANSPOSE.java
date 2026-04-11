package newCoupler;
import java.util.*;

/**
 * Utilities for mapping between services and commodities, and detecting
 * non-identifiable (ambiguous) commodity groups to derive split ratios
 * from reference commodity totals.
 */
public class InversTRANSPOSE {

    // -----------------------------
    // 1) TRANSPOSE: service->commodity  ==>  commodity->service
    // -----------------------------
    public static Map<String, Map<String, Double>> transposeServiceToCommodity(
            Map<String, Map<String, Double>> serviceToCommodity,  // <service, <commodity, weight>>
            Map<String, Double> commodityTotals,                  // <commodity, totalDemand> (can be empty)
            boolean normalizePerCommodity,
            double eps
    ) {
        Objects.requireNonNull(serviceToCommodity, "serviceToCommodity");
        Objects.requireNonNull(commodityTotals, "commodityTotals");

        Map<String, Map<String, Double>> commodityToService = new LinkedHashMap<>();

        // Pre-seed commodities from totals (so unmapped commodities still exist in output)
        for (String c : commodityTotals.keySet()) {
            commodityToService.put(c, new LinkedHashMap<>());
        }

        // Transpose
        for (Map.Entry<String, Map<String, Double>> sEntry : serviceToCommodity.entrySet()) {
            String service = sEntry.getKey();
            Map<String, Double> inner = sEntry.getValue();
            if (inner == null) continue;

            for (Map.Entry<String, Double> cEntry : inner.entrySet()) {
                String commodity = cEntry.getKey();
                Double wObj = cEntry.getValue();
                if (commodity == null || wObj == null) continue;

                double w = wObj;
                if (Math.abs(w) <= eps) continue;

                commodityToService
                        .computeIfAbsent(commodity, k -> new LinkedHashMap<>())
                        .put(service, w);
            }
        }

        // Optional: normalize per commodity so sum_service weights = 1 for each commodity
        if (normalizePerCommodity) {
            for (Map.Entry<String, Map<String, Double>> e : commodityToService.entrySet()) {
                Map<String, Double> sw = e.getValue();
                if (sw == null || sw.isEmpty()) continue;

                double sum = 0.0;
                for (double v : sw.values()) sum += v;

                if (Math.abs(sum) > eps) {
                    for (Map.Entry<String, Double> x : sw.entrySet()) {
                        x.setValue(x.getValue() / sum);
                    }
                }
            }
        }

        return commodityToService;
    }

    public static Map<String, Map<String, Double>> transposeServiceToCommodity(
            Map<String, Map<String, Double>> serviceToCommodity,
            Map<String, Double> commodityTotals
    ) {
        return transposeServiceToCommodity(serviceToCommodity, commodityTotals, false, 1e-12);
    }

    // -----------------------------
    // 2) AMBIGUITY DETECTION + RATIO ESTIMATION
    // -----------------------------

    /** How to group commodities by their “column signature”. */
    public enum GroupingMode {
        /** Columns must match exactly (after tolerance-quantization). */
        EXACT,
        /** Columns can be proportional: colB = k * colA (after tolerance). */
        PROPORTIONAL
    }

    /** Result: ambiguous groups and their split shares derived from commodityTotals. */
    public static final class AmbiguityInfo {
        /** GroupKey -> commodities in that group (includes unique groups too). */
        public final Map<String, List<String>> groupToCommodities;
        /** Only groups with size>1 (ambiguous). */
        public final Map<String, List<String>> ambiguousGroups;
        /** Only ambiguous groups: GroupKey -> (commodity -> share). Shares sum to 1 within group. */
        public final Map<String, Map<String, Double>> ambiguousGroupShares;
        /** Commodity -> groupKey (only for ambiguous commodities). */
        public final Map<String, String> commodityToAmbiguousGroup;

        private AmbiguityInfo(
                Map<String, List<String>> groupToCommodities,
                Map<String, List<String>> ambiguousGroups,
                Map<String, Map<String, Double>> ambiguousGroupShares,
                Map<String, String> commodityToAmbiguousGroup
        ) {
            this.groupToCommodities = groupToCommodities;
            this.ambiguousGroups = ambiguousGroups;
            this.ambiguousGroupShares = ambiguousGroupShares;
            this.commodityToAmbiguousGroup = commodityToAmbiguousGroup;
        }

        /** Convenience: returns share for commodity if ambiguous, otherwise 1.0 (i.e., no split needed). */
        public double shareOrOne(String commodity) {
            String g = commodityToAmbiguousGroup.get(commodity);
            if (g == null) return 1.0;
            Map<String, Double> shares = ambiguousGroupShares.get(g);
            if (shares == null) return 1.0;
            return shares.getOrDefault(commodity, 1.0);
        }
    }

    /**
     * Detect ambiguous commodity groups (identical/proportional columns) based on commodity->service weights,
     * and compute split ratios using commodityTotals.
     *
     * @param commodityToService <commodity, <service, weight>>
     * @param commodityTotals    <commodity, reference totalDemand> used to compute ratios inside each ambiguous group
     * @param mode               EXACT or PROPORTIONAL grouping
     * @param tol                tolerance/quantization step for comparing doubles (e.g., 1e-9 or 1e-6)
     * @param eps                near-zero cutoff (e.g., 1e-12)
     */
    public static AmbiguityInfo detectAmbiguityAndComputeShares(
            Map<String, Map<String, Double>> commodityToService,
            Map<String, Double> commodityTotals,
            GroupingMode mode,
            double tol,
            double eps
    ) {
        Objects.requireNonNull(commodityToService, "commodityToService");
        Objects.requireNonNull(commodityTotals, "commodityTotals");
        Objects.requireNonNull(mode, "mode");

        // Collect all services (for consistent signatures)
        SortedSet<String> allServices = new TreeSet<>();
        for (Map<String, Double> sw : commodityToService.values()) {
            if (sw != null) allServices.addAll(sw.keySet());
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
            if ("ZERO".equals(g.getKey())) continue;
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
            for (String c : coms) total += Math.max(0.0, commodityTotals.getOrDefault(c, 0.0));

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

        return new AmbiguityInfo(
                Collections.unmodifiableMap(groups),
                Collections.unmodifiableMap(ambiguousGroups),
                Collections.unmodifiableMap(groupShares),
                Collections.unmodifiableMap(commodityToGroup)
        );
    }

    // -----------------------------
    // Signature builder (core of grouping)
    // -----------------------------
    private static String buildSignature(
            String commodity,
            Map<String, Double> serviceWeights,
            List<String> allServices,
            GroupingMode mode,
            double tol,
            double eps
    ) {
        // Build dense vector in stable service order
        double[] v = new double[allServices.size()];
        boolean any = false;
        for (int i = 0; i < allServices.size(); i++) {
            String s = allServices.get(i);
            double w = 0.0;
            if (serviceWeights != null) {
                Double wObj = serviceWeights.get(s);
                if (wObj != null) w = wObj;
            }
            if (Math.abs(w) <= eps) w = 0.0;
            v[i] = w;
            if (w != 0.0) any = true;
        }

        if (!any) return "ZERO";

        // If PROPORTIONAL: normalize by first non-zero element so collinear vectors match
        if (mode == GroupingMode.PROPORTIONAL) {
            double scale = 0.0;
            for (double x : v) {
                if (x != 0.0) { scale = x; break; }
            }
            if (scale == 0.0) return "ZERO";
            // make first non-zero positive
            if (scale < 0) scale = -scale;

            for (int i = 0; i < v.length; i++) {
                v[i] = v[i] / scale;
                if (Math.abs(v[i]) <= eps) v[i] = 0.0;
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
    
    
    /**
     * Builds a corrected mapper for back-transfer: <commodity, <service, weight>>
     * that resolves ambiguous commodities by splitting using ratios from commodityTotals.
     *
     * IMPORTANT: this does NOT compute a true inverse. It only fixes the ambiguity problem
     * (commodities with identical/proportional columns would otherwise be indistinguishable).
     */
    public static Map<String, Map<String, Double>> buildCorrectedBackTransferMapper(
            Map<String, Map<String, Double>> serviceToCommodity, // <service, <commodity, w>>
            Map<String, Double> commodityTotals,                 // reference totals -> shares
            GroupingMode mode,
            double tol,
            double eps
    ) {
        // A) start from your previous "corrected transpose" approach
        Map<String, Map<String, Double>> raw =
                transposeServiceToCommodity(serviceToCommodity, commodityTotals, false, eps);

        AmbiguityInfo info = detectAmbiguityAndComputeShares(raw, commodityTotals, mode, tol, eps);

        Map<String, Map<String, Double>> corrected = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Double>> e : raw.entrySet()) {
            corrected.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
        }

        // Split ambiguous groups by shares (same as before)
        for (Map.Entry<String, List<String>> g : info.ambiguousGroups.entrySet()) {
            String groupKey = g.getKey();
            List<String> commodities = g.getValue();
            if (commodities == null || commodities.isEmpty()) continue;

            Map<String, Double> shares = info.ambiguousGroupShares.get(groupKey);
            if (shares == null || shares.isEmpty()) continue;

            String rep = commodities.get(0);
            Map<String, Double> repWeights = raw.get(rep);
            if (repWeights == null || repWeights.isEmpty()) continue;

            for (String c : commodities) {
                double share = shares.getOrDefault(c, 0.0);
                Map<String, Double> newWeights = new LinkedHashMap<>();
                if (share > eps) {
                    for (Map.Entry<String, Double> sw : repWeights.entrySet()) {
                        double w = sw.getValue() * share;
                        if (Math.abs(w) > eps) newWeights.put(sw.getKey(), w);
                    }
                }
                corrected.put(c, newWeights);
            }
        }

        // B) NEW: override commodities reconstructible from "pure" service rows
        // pure row = a service depends on exactly one commodity
        Map<String, Map<String, Double>> pureServicesByCommodity = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Double>> sEntry : serviceToCommodity.entrySet()) {
            String service = sEntry.getKey();
            Map<String, Double> row = sEntry.getValue();
            if (row == null) continue;

            String onlyCommodity = null;
            double onlyW = 0.0;
            int nz = 0;

            for (Map.Entry<String, Double> cw : row.entrySet()) {
                if (cw.getKey() == null || cw.getValue() == null) continue;
                double w = cw.getValue();
                if (Math.abs(w) <= eps) continue;
                nz++;
                onlyCommodity = cw.getKey();
                onlyW = w;
                if (nz > 1) break;
            }

            if (nz == 1 && onlyCommodity != null) {
                pureServicesByCommodity
                        .computeIfAbsent(onlyCommodity, k -> new LinkedHashMap<>())
                        .put(service, onlyW);
            }
        }

        // If a commodity has pure services, reconstruct it ONLY from them:
        // commodity = sum(serviceValues) / sum(weights)  -> so each service gets weight 1/sumW
        for (Map.Entry<String, Map<String, Double>> e : pureServicesByCommodity.entrySet()) {
            String commodity = e.getKey();
            Map<String, Double> pure = e.getValue();
            if (pure == null || pure.isEmpty()) continue;

            double sumW = 0.0;
            for (double w : pure.values()) sumW += w;
            if (Math.abs(sumW) <= eps) continue;

            double inv = 1.0 / sumW;

            Map<String, Double> invWeights = new LinkedHashMap<>();
            for (String service : pure.keySet()) {
                invWeights.put(service, inv);
            }

            // Override corrected mapping for this commodity
            corrected.put(commodity, invWeights);
        }

        return corrected;
    }

    public static Map<String, Map<String, Double>> buildCorrectedBackTransferMapper(
            Map<String, Map<String, Double>> serviceToCommodity,
            Map<String, Double> commodityTotals
    ) {
        return buildCorrectedBackTransferMapper(serviceToCommodity, commodityTotals,
                GroupingMode.PROPORTIONAL, 1e-9, 1e-12);
    }

}