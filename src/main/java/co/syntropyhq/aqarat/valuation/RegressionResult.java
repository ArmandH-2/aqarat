package co.syntropyhq.aqarat.valuation;

import java.math.BigDecimal;
import java.util.Map;

// Carries the pieces of the regression-based estimate between the private
// PriceEstimator methods that build it and the ones that need its parts.
// Package-private and field-only: nothing outside PriceEstimator sees this.
final class RegressionResult {

    final Double estimate;
    final Map<String, BigDecimal> coefficients;

    RegressionResult(Double estimate, Map<String, BigDecimal> coefficients) {
        this.estimate = estimate;
        this.coefficients = coefficients;
    }
}
