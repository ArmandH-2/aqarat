package co.syntropyhq.aqarat.valuation;

import java.util.Arrays;
import java.util.List;

// Small, boring statistics helpers shared by PriceEstimator. Package-private:
// nothing outside valuation needs plain-array weighted median and spread.
final class Stats {

    private Stats() {
    }

    // Weighted median: sort by value, walk the cumulative weight, and take
    // the value where it crosses half the total weight. With equal weights
    // this lands on the same value an unweighted median would pick.
    static double weightedMedian(double[] values, double[] weights) {
        Integer[] order = ascendingIndices(values);
        double half = sum(weights) / 2.0;

        double cumulative = 0.0;
        for (int index : order) {
            cumulative += weights[index];
            if (cumulative >= half) {
                return values[index];
            }
        }
        return values[order[order.length - 1]];
    }

    private static Integer[] ascendingIndices(double[] values) {
        Integer[] order = new Integer[values.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(values[a], values[b]));
        return order;
    }

    private static double sum(double[] values) {
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        return total;
    }

    // Sample standard deviation. A single value has no spread to measure,
    // so it returns 0 rather than dividing by zero.
    static double standardDeviation(double[] values) {
        if (values.length < 2) {
            return 0.0;
        }
        double mean = 0.0;
        for (double value : values) {
            mean += value;
        }
        mean /= values.length;

        double sumSquares = 0.0;
        for (double value : values) {
            sumSquares += (value - mean) * (value - mean);
        }
        return Math.sqrt(sumSquares / (values.length - 1));
    }

    static double[] toArray(List<Double> values) {
        double[] array = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            array[i] = values.get(i);
        }
        return array;
    }
}
