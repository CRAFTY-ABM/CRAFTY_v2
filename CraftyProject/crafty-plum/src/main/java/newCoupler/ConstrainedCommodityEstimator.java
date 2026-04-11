package newCoupler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.HashMap;

/**
 * Solve, per country:
 *
 *   min_x ||M x - y||^2 + alpha ||x - prior||^2 + beta ||x - baseline||^2
 *   s.t. x >= 0
 *
 * where:
 *   - M is service -> commodity matrix
 *   - y is observed service supply
 *   - x is commodity supply to estimate
 *
 * No external libraries required.
 * Uses projected gradient descent with a conservative step size.
 */
public final class ConstrainedCommodityEstimator {

    private static final double EPS = 1e-12;

    private ConstrainedCommodityEstimator() {
    }

    public static Map<String, Double> solveCountry(
            Map<String, Map<String, Double>> serviceToCommodity,
            Map<String, Double> observedServices,
            Map<String, Double> priorCommodity,
            Map<String, Double> baselineCommodity,
            double alpha,
            double beta,
            int maxIter,
            double tol) {

        Map<String, Double> prior = priorCommodity == null ? Collections.emptyMap() : priorCommodity;
        Map<String, Double> baseline = baselineCommodity == null ? Collections.emptyMap() : baselineCommodity;
        Map<String, Double> yMap = observedServices == null ? Collections.emptyMap() : observedServices;
        Map<String, Map<String, Double>> MMap = serviceToCommodity == null ? Collections.emptyMap() : serviceToCommodity;

        List<String> serviceKeys = collectServiceKeys(MMap, yMap);
        List<String> commodityKeys = collectCommodityKeys(MMap, prior, baseline);

        Map<String, Double> out = new HashMap<>();
        if (commodityKeys.isEmpty()) {
            return out;
        }

        // If there is no usable matrix, fall back to prior/baseline clipped to >= 0
        if (serviceKeys.isEmpty() || MMap.isEmpty()) {
            for (String c : commodityKeys) {
                double v = prior.containsKey(c) ? prior.get(c) : baseline.getOrDefault(c, 0.0);
                out.put(c, Math.max(0.0, v));
            }
            return out;
        }

        final int n = serviceKeys.size();
        final int m = commodityKeys.size();

        double[][] M = new double[n][m];
        double[] y = new double[n];
        double[] x = new double[m];
        double[] xPrior = new double[m];
        double[] xBase = new double[m];

        // Build dense arrays
        for (int i = 0; i < n; i++) {
            String s = serviceKeys.get(i);
            y[i] = yMap.getOrDefault(s, 0.0);

            Map<String, Double> row = MMap.get(s);
            if (row != null) {
                for (int j = 0; j < m; j++) {
                    String c = commodityKeys.get(j);
                    M[i][j] = row.getOrDefault(c, 0.0);
                }
            }
        }

        for (int j = 0; j < m; j++) {
            String c = commodityKeys.get(j);
            xPrior[j] = Math.max(0.0, prior.getOrDefault(c, baseline.getOrDefault(c, 0.0)));
            xBase[j] = Math.max(0.0, baseline.getOrDefault(c, 0.0));
            x[j] = xPrior[j]; // warm start from previous estimate if available
        }

        double step = 1.0 / estimateLipschitz(M, alpha, beta);

        double[] residual = new double[n];
        double[] grad = new double[m];
        double[] xNew = new double[m];

        for (int iter = 0; iter < maxIter; iter++) {

            // residual = Mx - y
            for (int i = 0; i < n; i++) {
                double sum = 0.0;
                for (int j = 0; j < m; j++) {
                    sum += M[i][j] * x[j];
                }
                residual[i] = sum - y[i];
            }

            // grad = 2 M^T (Mx - y) + 2 alpha (x - prior) + 2 beta (x - baseline)
            for (int j = 0; j < m; j++) {
                double g = 0.0;
                for (int i = 0; i < n; i++) {
                    g += 2.0 * M[i][j] * residual[i];
                }
                g += 2.0 * alpha * (x[j] - xPrior[j]);
                g += 2.0 * beta * (x[j] - xBase[j]);
                grad[j] = g;
            }

            // projected gradient step
            double maxAbsChange = 0.0;
            double maxAbsX = 0.0;

            for (int j = 0; j < m; j++) {
                xNew[j] = Math.max(0.0, x[j] - step * grad[j]);
                double ch = Math.abs(xNew[j] - x[j]);
                if (ch > maxAbsChange) {
                    maxAbsChange = ch;
                }
                if (Math.abs(x[j]) > maxAbsX) {
                    maxAbsX = Math.abs(x[j]);
                }
            }

            System.arraycopy(xNew, 0, x, 0, m);

            if (maxAbsChange <= tol * (1.0 + maxAbsX)) {
                break;
            }
        }

        for (int j = 0; j < m; j++) {
            out.put(commodityKeys.get(j), Math.max(0.0, x[j]));
        }

        return out;
    }

    private static List<String> collectServiceKeys(
            Map<String, Map<String, Double>> M,
            Map<String, Double> y) {

        TreeSet<String> keys = new TreeSet<>();
        if (M != null) {
            keys.addAll(M.keySet());
        }
        if (y != null) {
            keys.addAll(y.keySet());
        }
        return new ArrayList<>(keys);
    }

    private static List<String> collectCommodityKeys(
            Map<String, Map<String, Double>> M,
            Map<String, Double> prior,
            Map<String, Double> baseline) {

        TreeSet<String> keys = new TreeSet<>();
        if (prior != null) {
            keys.addAll(prior.keySet());
        }
        if (baseline != null) {
            keys.addAll(baseline.keySet());
        }
        if (M != null) {
            for (Map<String, Double> row : M.values()) {
                if (row != null) {
                    keys.addAll(row.keySet());
                }
            }
        }
        return new ArrayList<>(keys);
    }

    /**
     * Conservative Lipschitz estimate for the gradient:
     * grad = 2(M^T M)x + 2(alpha + beta)x + ...
     *
     * We bound ||H|| using max absolute row sum of H.
     */
    private static double estimateLipschitz(double[][] M, double alpha, double beta) {
        int n = M.length;
        int m = (n == 0) ? 0 : M[0].length;

        if (m == 0) {
            return 1.0;
        }

        double maxRowSum = 2.0 * (alpha + beta);

        for (int j = 0; j < m; j++) {
            double rowSum = 2.0 * (alpha + beta);
            for (int k = 0; k < m; k++) {
                double mtm = 0.0;
                for (int i = 0; i < n; i++) {
                    mtm += M[i][j] * M[i][k];
                }
                rowSum += 2.0 * Math.abs(mtm);
            }
            if (rowSum > maxRowSum) {
                maxRowSum = rowSum;
            }
        }

        return Math.max(maxRowSum, EPS);
    }
}