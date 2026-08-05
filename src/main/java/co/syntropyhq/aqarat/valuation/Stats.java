package co.syntropyhq.aqarat.valuation;

import java.util.Arrays;
import java.util.List;

// Small, boring statistics helpers shared by PriceEstimator. Package-private:
// nothing outside valuation needs plain-array median and spread.
final class Stats {

    private Stats() {
    }

    static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        if (sorted.length % 2 == 0) {
            return (sorted[mid - 1] + sorted[mid]) / 2.0;
        }
        return sorted[mid];
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
