package co.syntropyhq.aqarat.valuation;

import java.util.List;

// Carries the pieces of the comparables-based estimate between the private
// PriceEstimator methods that build it and the ones that need its parts.
// Package-private and field-only: nothing outside PriceEstimator sees this.
final class ComparablesResult {

    final Double estimate;
    final double[] individualEstimates;
    final List<ComparableProperty> comparableProperties;

    ComparablesResult(Double estimate, double[] individualEstimates,
            List<ComparableProperty> comparableProperties) {
        this.estimate = estimate;
        this.individualEstimates = individualEstimates;
        this.comparableProperties = comparableProperties;
    }
}
