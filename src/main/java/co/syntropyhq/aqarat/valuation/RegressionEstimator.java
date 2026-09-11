package co.syntropyhq.aqarat.valuation;

import co.syntropyhq.aqarat.model.Property;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The regression half of PriceEstimator's blend, split into its own class
// so PriceEstimator stays under CONVENTIONS.md's 300-line limit. Package-private:
// PriceEstimator is the only caller, the same way it is the only caller of
// its own private methods before this split.
final class RegressionEstimator {

    // The regression needs meaningfully more rows than coefficients (10
    // features + intercept = 11) to fit something stable rather than a
    // curve threaded exactly through 11 points. Three times over is the
    // usual rule of thumb.
    private static final int MIN_REGRESSION_ROWS = 33;

    // Order matches featuresOf() exactly - this is what turns
    // LinearRegression's plain coefficient array into something an agent
    // can read later: "yearBuilt", not "coefficient 4".
    private static final List<String> FEATURE_NAMES = List.of(
        "areaSqm", "bedrooms", "bathrooms", "floorNumber", "yearBuilt",
        "districtAvgPricePerSqm", "hasParking", "hasElevator", "hasBalcony", "furnished");

    private static final RegressionResult NO_REGRESSION = new RegressionResult(null, Collections.emptyMap());

    // Regression uses year built directly rather than an age computed from
    // the current date: age and year differ by a constant offset that a
    // fitted coefficient absorbs anyway, and it keeps this class free of any
    // dependency on the wall clock.
    RegressionResult estimate(Property subject, List<Property> dataset,
            Map<Integer, BigDecimal> avgPricePerSqmByDistrict, double recencyHalfLifeMonths) {
        if (!hasCompleteFeatures(subject, avgPricePerSqmByDistrict)) {
            return NO_REGRESSION;
        }
        List<Property> usable = usableRows(dataset, avgPricePerSqmByDistrict);
        if (usable.size() < MIN_REGRESSION_ROWS) {
            return NO_REGRESSION;
        }
        return fitAndPredict(subject, usable, avgPricePerSqmByDistrict, recencyHalfLifeMonths);
    }

    private List<Property> usableRows(List<Property> dataset, Map<Integer, BigDecimal> avgPricePerSqmByDistrict) {
        List<Property> usable = new ArrayList<>();
        for (Property candidate : dataset) {
            if (hasCompleteFeatures(candidate, avgPricePerSqmByDistrict)) {
                usable.add(candidate);
            }
        }
        return usable;
    }

    // Fitting price per m2 instead of raw price keeps every row's
    // contribution to the least-squares fit on the same scale, so a
    // $900,000 sale no longer dominates the fit the way it would when area
    // only appears as a feature and not as the target's own denominator.
    // Weighted so a recent closing counts for more than an old one.
    private RegressionResult fitAndPredict(Property subject, List<Property> usable,
            Map<Integer, BigDecimal> avgPricePerSqmByDistrict, double recencyHalfLifeMonths) {
        double[][] features = new double[usable.size()][];
        double[] targets = new double[usable.size()];
        for (int i = 0; i < usable.size(); i++) {
            features[i] = featuresOf(usable.get(i), avgPricePerSqmByDistrict);
            targets[i] = usable.get(i).getAskingPrice().doubleValue() / usable.get(i).getAreaSqm().doubleValue();
        }
        double[] weights = RecencyWeighting.weightsFor(usable, recencyHalfLifeMonths);

        LinearRegression regression = new LinearRegression();
        try {
            regression.fit(features, targets, weights);
        } catch (IllegalStateException singularFeatures) {
            // A slice with no variation in some column cannot support a
            // fitted model. Falling back to comparables alone beats showing
            // a number produced from a degenerate fit.
            return NO_REGRESSION;
        }
        double predictedPricePerSqm = regression.predict(featuresOf(subject, avgPricePerSqmByDistrict));
        double predicted = predictedPricePerSqm * subject.getAreaSqm().doubleValue();
        return predicted > 0
            ? new RegressionResult(predicted, namedCoefficients(regression.getCoefficients()))
            : NO_REGRESSION;
    }

    private boolean hasCompleteFeatures(Property property, Map<Integer, BigDecimal> avgPricePerSqmByDistrict) {
        return property.getAreaSqm() != null && property.getAreaSqm().signum() > 0
            && property.getFloorNumber() != null
            && property.getYearBuilt() != null
            && property.getAskingPrice() != null
            && avgPricePerSqmByDistrict.get(property.getDistrictId()) != null;
    }

    private double[] featuresOf(Property property, Map<Integer, BigDecimal> avgPricePerSqmByDistrict) {
        BigDecimal districtAvg = avgPricePerSqmByDistrict.get(property.getDistrictId());
        return new double[] {
            property.getAreaSqm().doubleValue(),
            property.getBedrooms(),
            property.getBathrooms(),
            property.getFloorNumber(),
            property.getYearBuilt(),
            districtAvg.doubleValue(),
            property.isHasParking() ? 1.0 : 0.0,
            property.isHasElevator() ? 1.0 : 0.0,
            property.isHasBalcony() ? 1.0 : 0.0,
            property.isFurnished() ? 1.0 : 0.0
        };
    }

    // Named so a saved estimate can be explained later (DESIGN.md section
    // 1): index 0 from LinearRegression is the intercept, and the rest line
    // up with FEATURE_NAMES in the order featuresOf() builds them.
    private Map<String, BigDecimal> namedCoefficients(double[] coefficients) {
        Map<String, BigDecimal> named = new LinkedHashMap<>();
        named.put("intercept", toCoefficientValue(coefficients[0]));
        for (int i = 0; i < FEATURE_NAMES.size(); i++) {
            named.put(FEATURE_NAMES.get(i), toCoefficientValue(coefficients[i + 1]));
        }
        return named;
    }

    // Coefficients keep more precision than a displayed price: the area
    // coefficient in particular is a $/m2 change per additional m2 and is
    // often a small fraction that scale-2 would round away to nothing.
    private BigDecimal toCoefficientValue(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }
}
