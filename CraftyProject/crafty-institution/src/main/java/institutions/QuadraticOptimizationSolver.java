package institutions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/*
 * @author yongchao Zeng
 *
 */

/**
 * Solver for the quadratic optimization problem:
 * min sum(w_j * (r_j - R_j)^2)
 * subject to:
 * sum(r_j) <= C
 * r_j >= 0
 */

/**
 * see the optimization_solver_python_cross_verification.py in the resources folder for Python cross-verification.
 */
public class QuadraticOptimizationSolver {

    /**
     * Analytical solution approach using KKT conditions.
     * Returns a HashMap with the same keys as the input maps.
     *
     * @param weights HashMap with keys as item names and values as weights
     * @param targets HashMap with keys as item names and values as target values (must have same keys as weights)
     * @param constraintValue The constraint value C
     * @return HashMap with the same keys and optimal r_j values
     */
    public static Map<String, Double> solveUsingKKT(
            Map<String, Double> weights,
            Map<String, Double> targets,
            double constraintValue) {

        // Validate input
        if (!weights.keySet().equals(targets.keySet())) {
            throw new IllegalArgumentException("Weight and target maps must have identical keys");
        }

        List<String> keys = new ArrayList<>(weights.keySet());

        // First, check if the unconstrained optimum (r_j = R_j) satisfies the constraint
        double sumTargets = targets.values().stream().mapToDouble(Double::doubleValue).sum();

        Map<String, Double> solution = new HashMap<>();

        if (sumTargets <= constraintValue) {
            // If unconstrained solution is viable
            // Solution is just the target values
            for (String key : keys) {
                solution.put(key, targets.get(key));
            }

            return solution;
        }

        // If we're here, the constraint is active
        // For the case where the constraint is tight, use Lagrange multiplier
        // The solution is: r_j = max(0, R_j - λ/(2*w_j))

        // Find the Lagrange multiplier (lambda) using bisection method
        double lambda = findLambda(weights, targets, constraintValue, keys);

        // Calculate the solution values
        for (String key : keys) {
            double w = weights.get(key);
            double R = targets.get(key);
            double r = Math.max(0, R - lambda / (2 * w));
            solution.put(key, r);
        }

        return solution;
    }

    /**
     * Find the Lagrange multiplier (lambda) such that the sum of r_j equals C
     */
    private static double findLambda(
            Map<String, Double> weights,
            Map<String, Double> targets,
            double constraintValue,
            List<String> keys) {

        // Use bisection method to find lambda
        double lambdaMin = 0.0;

        // Calculate a reasonable upper bound for lambda
        double lambdaMax = 0.0;
        for (String key : keys) {
            double w = weights.get(key);
            double R = targets.get(key);
            lambdaMax = Math.max(lambdaMax, 2 * w * R);
        }
        lambdaMax *= 2; // Ensure it's large enough

        double tolerance = 1e-10; // High precision
        int maxIterations = 200;  // More iterations for better convergence
        double lambda = 0.0;

        for (int iter = 0; iter < maxIterations; iter++) {
            lambda = (lambdaMin + lambdaMax) / 2.0;

            // Calculate sum of r_j with this lambda
            double sum = 0.0;
            for (String key : keys) {
                double w = weights.get(key);
                double R = targets.get(key);
                double r = Math.max(0.0, R - lambda / (2 * w));
                sum += r;
            }

            if (Math.abs(sum - constraintValue) < tolerance) {
                // We've found a good lambda
                break;
            }

            if (sum > constraintValue) {
                // Lambda is too small, increase it
                lambdaMin = lambda;
            } else {
                // Lambda is too large, decrease it
                lambdaMax = lambda;
            }
        }

        // Fine-tune lambda with a few more iterations of numerical approximation
        // This helps get even closer to the constraint
        for (int i = 0; i < 5; i++) {
            double sum = 0.0;
            for (String key : keys) {
                double w = weights.get(key);
                double R = targets.get(key);
                double r = Math.max(0.0, R - lambda / (2 * w));
                sum += r;
            }

            // Adjust lambda based on the difference
            double adjustment = (sum - constraintValue) * 0.01;
            lambda += adjustment;
        }

        return lambda;
    }

    /**
     * Calculate the objective function value for a given solution
     * This is useful for validation and comparison
     */
    public static double calculateObjectiveValue(
            Map<String, Double> weights,
            Map<String, Double> targets,
            Map<String, Double> solution) {

        double objValue = 0.0;
        for (String key : weights.keySet()) {
            double w = weights.get(key);
            double R = targets.get(key);
            double r = solution.get(key);
            objValue += w * Math.pow(r - R, 2);
        }

        return objValue;
    }
}