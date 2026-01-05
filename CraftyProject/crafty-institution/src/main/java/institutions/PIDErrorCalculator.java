package institutions;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for calculating weighted PID errors based on time series data.
 * This implements the error calculation described in the paper, where errors are
 * calculated relative to a goal value and weighted according to user preferences.
 */
public class PIDErrorCalculator {

    /**
     * Calculates the weighted sum of Proportional, Integral, and Derivative errors
     * from a time series of values relative to a goal value.
     *
     * @param timeSeries List of historical values (most recent at the end)
     * @param goalValue The target value the series should reach
     * @param proportionalWeight Weight for the proportional error term (current error)
     * @param integralWeight Weight for the integral error term (accumulated past errors)
     * @param derivativeWeight Weight for the derivative error term (rate of change of errors)
     * @param timeInterval The time interval to use for integral and derivative calculations
     * @return The weighted sum of PID errors
     */
	
	/*
	 * @author yongchao Zeng
	 *
	 */
    public static double calculateWeightedError(List<Double> timeSeries, double goalValue,
                                                double proportionalWeight, double integralWeight,
                                                double derivativeWeight, int timeInterval) {
        // Validate inputs
        if (timeSeries == null || timeSeries.isEmpty()) {
            throw new IllegalArgumentException("Time series cannot be null or empty");
        }

        if (Math.abs(proportionalWeight + integralWeight + derivativeWeight - 1.0) > 0.0001) {
            throw new IllegalArgumentException("Weights must sum to 1.0");
        }

        if (timeInterval <= 0) {
            throw new IllegalArgumentException("Time interval must be positive");
        }

        // Get the most recent value
        double currentValue = timeSeries.get(timeSeries.size() - 1);

        // Calculate proportional error: (goalValue - currentValue) / |goalValue|
        double proportionalError = 0;
        if (goalValue != 0) {
            proportionalError = (goalValue - currentValue) / Math.abs(goalValue);
        } else {
            // If goal is zero, use absolute error
            proportionalError = -currentValue;
        }

        // Calculate integral error: average of past errors over timeInterval
        double integralError = calculateIntegralError(timeSeries, goalValue, timeInterval);

        // Calculate derivative error: difference between current error and past error
        double derivativeError = calculateDerivativeError(timeSeries, goalValue, timeInterval);

        // Return weighted sum
        return proportionalWeight * proportionalError +
                integralWeight * integralError +
                derivativeWeight * derivativeError;
    }

    /**
     * Calculates the integral error term (accumulated errors over time)
     */
    private static double calculateIntegralError(List<Double> timeSeries, double goalValue, int timeInterval) {
        int n = timeSeries.size();
        int k = Math.min(n, timeInterval);

        if (k == 0) return 0.0;

        double sum = 0.0;
        for (int i = 0; i < k; i++) {
            double value = timeSeries.get(n - 1 - i);
            if (goalValue != 0) {
                sum += (goalValue - value) / Math.abs(goalValue);
            } else {
                sum += -value;
            }
        }

        return sum / k;
    }

    /**
     * Calculates the derivative error term (rate of change of error)
     */
    private static double calculateDerivativeError(List<Double> timeSeries, double goalValue, int timeInterval) {
        int n = timeSeries.size();

        if (n <= timeInterval) return 0.0;

        double currentValue = timeSeries.get(n - 1);
        double pastValue = timeSeries.get(n - 1 - timeInterval);

        double currentError, pastError;

        if (goalValue != 0) {
            currentError = (goalValue - currentValue);
            pastError = (goalValue - pastValue);
            return (currentError - pastError) / (timeInterval * Math.abs(goalValue));
        } else {
            currentError = -currentValue;
            pastError = -pastValue;
            return (currentError - pastError) / timeInterval;
        }
    }

    /**
     * Alternative method that uses a custom error normalization function
     *
     * @param timeSeries List of historical values (most recent at the end)
     * @param goalValue The target value the series should reach
     * @param proportionalWeight Weight for proportional error
     * @param integralWeight Weight for integral error
     * @param derivativeWeight Weight for derivative error
     * @param timeInterval Time interval for integral and derivative calculations
     * @param normalizationFunction Function to normalize errors
     * @return The weighted sum of PID errors
     */
    public static double calculateWeightedError(List<Double> timeSeries, double goalValue,
                                                double proportionalWeight, double integralWeight,
                                                double derivativeWeight, int timeInterval,
                                                ErrorNormalizationFunction normalizationFunction) {
        if (timeSeries == null || timeSeries.isEmpty()) {
            throw new IllegalArgumentException("Time series cannot be null or empty");
        }

        double currentValue = timeSeries.get(timeSeries.size() - 1);

        // Calculate proportional error using custom normalization
        double proportionalError = normalizationFunction.normalize(goalValue - currentValue, goalValue);

        // Calculate integral error with custom normalization
        double integralError = calculateIntegralError(timeSeries, goalValue, timeInterval, normalizationFunction);

        // Calculate derivative error with custom normalization
        double derivativeError = calculateDerivativeError(timeSeries, goalValue, timeInterval, normalizationFunction);

        // Return weighted sum
        return proportionalWeight * proportionalError +
                integralWeight * integralError +
                derivativeWeight * derivativeError;
    }

    /**
     * Calculates integral error with custom normalization
     */
    private static double calculateIntegralError(List<Double> timeSeries, double goalValue,
                                                 int timeInterval, ErrorNormalizationFunction normFunction) {
        int n = timeSeries.size();
        int k = Math.min(n, timeInterval);

        if (k == 0) return 0.0;

        double sum = 0.0;
        for (int i = 0; i < k; i++) {
            double value = timeSeries.get(n - 1 - i);
            sum += normFunction.normalize(goalValue - value, goalValue);
        }

        return sum / k;
    }

    /**
     * Calculates derivative error with custom normalization
     */
    private static double calculateDerivativeError(List<Double> timeSeries, double goalValue,
                                                   int timeInterval, ErrorNormalizationFunction normFunction) {
        int n = timeSeries.size();

        if (n <= timeInterval) return 0.0;

        double currentValue = timeSeries.get(n - 1);
        double pastValue = timeSeries.get(n - 1 - timeInterval);

        double currentError = goalValue - currentValue;
        double pastError = goalValue - pastValue;

        return normFunction.normalize(currentError - pastError, timeInterval * goalValue);
    }

    /**
     * Functional interface for custom error normalization
     */
    @FunctionalInterface
    public interface ErrorNormalizationFunction {
        /**
         * Normalizes an error value
         *
         * @param error The raw error value
         * @param referenceValue A reference value for normalization (e.g., goalValue)
         * @return The normalized error
         */
        double normalize(double error, double referenceValue);
    }

    /**
     * Example usage of the PID error calculator
     */
    public static void main(String[] args) {
        // Create a sample time series
        List<Double> timeSeries = new ArrayList<>();
        timeSeries.add(1.0);  // oldest value
        timeSeries.add(1.2);
        timeSeries.add(1.4);
        timeSeries.add(1.6);
        timeSeries.add(1.8);  // newest value

        // Calculate weighted PID error
        double goalValue = 2.0;
        double proportionalWeight = 0.5;
        double integralWeight = 0.3;
        double derivativeWeight = 0.2;
        int timeInterval = 3;

        double weightedError = calculateWeightedError(
                timeSeries, goalValue, proportionalWeight, integralWeight, derivativeWeight, timeInterval);

        System.out.println("Weighted PID Error: " + weightedError);

        // Example with custom normalization function
        ErrorNormalizationFunction customNorm = (error, reference) -> {
            // Example: sigmoid normalization
            return 2.0 / (1.0 + Math.exp(-error)) - 1.0;
        };

        double customWeightedError = calculateWeightedError(
                timeSeries, goalValue, proportionalWeight, integralWeight, derivativeWeight,
                timeInterval, customNorm);

        System.out.println("Custom Weighted PID Error: " + customWeightedError);
    }
}