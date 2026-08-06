package co.syntropyhq.aqarat.valuation;

import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.ValuationFlag;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a property and a caller-supplied slice of the closed-property
 * dataset into a ValuationResult. Touches no database - everything it needs
 * arrives as an argument, per DESIGN.md section 7.
 *
 * Two estimates are blended: a median of adjusted comparable prices per m²,
 * and a linear regression over the wider closed-property dataset. The
 * confidence range comes from how much the comparables disagree with each
 * other, floored at the "above market" band so a lucky run of similar
 * comparables never produces a suspiciously narrow range.
 */
public class PriceEstimator {

    // Heuristic nudges applied per comparable, not fitted coefficients - the
    // regression component is where the data does the fitting. These just
    // keep a like-for-like comparison honest: a comparable with two fewer
    // bedrooms should not be treated as equally comparable at face value.
    private static final double BEDROOM_ADJUSTMENT_PER_UNIT = 0.03;
    private static final double FLOOR_ADJUSTMENT_PER_UNIT = 0.005;
    private static final double AGE_ADJUSTMENT_PER_YEAR = 0.004;

    // The regression needs meaningfully more rows than coefficients (10
    // features + intercept = 11) to fit something stable rather than a
    // curve threaded exactly through 11 points. Three times over is the
    // usual rule of thumb.
    private static final int MIN_REGRESSION_ROWS = 33;

    public ValuationResult estimate(
            Property subject,
            List<Property> comparables,
            List<Property> regressionDataset,
            Map<Integer, BigDecimal> avgPricePerSqmByDistrict,
            int comparableMinCount,
            BigDecimal aboveMarketThresholdPercent,
            BigDecimal implausibleThresholdPercent) {

        if (subject.getAreaSqm() == null || subject.getAreaSqm().signum() <= 0) {
            throw new IllegalArgumentException("Cannot value a property with a zero or negative area.");
        }

        ComparablesResult comparablesResult = comparablesEstimate(subject, comparables);
        Double regressionEstimate = regressionEstimate(subject, regressionDataset, avgPricePerSqmByDistrict);

        if (comparablesResult.estimate == null && regressionEstimate == null) {
            throw new IllegalStateException(
                "Cannot value property " + subject.getId()
                    + ": no usable comparables and not enough closed-property data for a regression.");
        }

        double blendedEstimate = blend(comparablesResult.estimate, regressionEstimate);
        if (blendedEstimate <= 0) {
            throw new IllegalStateException("Computed a non-positive estimate for property " + subject.getId());
        }
        double halfWidth = rangeHalfWidth(comparablesResult, comparableMinCount, blendedEstimate,
            aboveMarketThresholdPercent);
        BigDecimal estimatedValue = toMoney(blendedEstimate);
        BigDecimal lowerBound = toMoney(Math.max(0, blendedEstimate - halfWidth));
        BigDecimal upperBound = toMoney(blendedEstimate + halfWidth);
        BigDecimal pricePerSqm = estimatedValue.divide(subject.getAreaSqm(), 2, RoundingMode.HALF_UP);

        ValuationFlag flag = computeFlag(subject.getAskingPrice(), lowerBound, upperBound,
            blendedEstimate, implausibleThresholdPercent);

        return new ValuationResult(estimatedValue, lowerBound, upperBound, pricePerSqm, flag,
            buildFactorContributions(comparablesResult, regressionEstimate), comparablesResult.comparableProperties);
    }

    // Median rather than mean: one over- or under-priced comparable should
    // not swing the estimate the way it would swing an average.
    private ComparablesResult comparablesEstimate(Property subject, List<Property> comparables) {
        List<Double> adjustedPrices = new ArrayList<>();
        List<ComparableProperty> used = new ArrayList<>();

        for (Property comp : comparables) {
            if (comp.getAreaSqm() == null || comp.getAreaSqm().signum() <= 0 || comp.getAskingPrice() == null) {
                continue; // bad data - skip rather than divide by zero
            }
            double pricePerSqm = comp.getAskingPrice().doubleValue() / comp.getAreaSqm().doubleValue();
            double adjusted = pricePerSqm * adjustmentFactor(subject, comp);
            if (adjusted <= 0) {
                continue; // an extreme diff pushed the adjustment negative - not usable
            }
            adjustedPrices.add(adjusted);
            used.add(new ComparableProperty(comp, similarityScore(subject, comp)));
        }

        if (adjustedPrices.isEmpty()) {
            return new ComparablesResult(null, new double[0], used);
        }

        double area = subject.getAreaSqm().doubleValue();
        double medianPricePerSqm = Stats.median(Stats.toArray(adjustedPrices));
        double[] individualEstimates = Stats.toArray(adjustedPrices);
        for (int i = 0; i < individualEstimates.length; i++) {
            individualEstimates[i] *= area;
        }
        return new ComparablesResult(medianPricePerSqm * area, individualEstimates, used);
    }

    // A multiplier nudging comp's price per m² towards what it would be with
    // the subject's bedroom count, floor and year built. Missing floor or
    // year data contributes nothing rather than being treated as zero.
    private double adjustmentFactor(Property subject, Property comp) {
        double factor = 1.0 + BEDROOM_ADJUSTMENT_PER_UNIT * (subject.getBedrooms() - comp.getBedrooms());
        if (subject.getFloorNumber() != null && comp.getFloorNumber() != null) {
            factor += FLOOR_ADJUSTMENT_PER_UNIT * (subject.getFloorNumber() - comp.getFloorNumber());
        }
        if (subject.getYearBuilt() != null && comp.getYearBuilt() != null) {
            factor += AGE_ADJUSTMENT_PER_YEAR * (subject.getYearBuilt() - comp.getYearBuilt());
        }
        return factor;
    }

    // Purely area-based: comparable selection already fixed district, type
    // and a +/-30% area band, so area distance is what is left to rank on.
    private BigDecimal similarityScore(Property subject, Property comp) {
        double subjectArea = subject.getAreaSqm().doubleValue();
        double areaDiff = Math.abs(subjectArea - comp.getAreaSqm().doubleValue());
        double score = Math.max(0.0, Math.min(1.0, 1.0 - areaDiff / subjectArea));
        return BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);
    }

    // Regression uses year built directly rather than an age computed from
    // the current date: age and year differ by a constant offset that a
    // fitted coefficient absorbs anyway, and it keeps this class free of any
    // dependency on the wall clock.
    private Double regressionEstimate(Property subject, List<Property> dataset,
            Map<Integer, BigDecimal> avgPricePerSqmByDistrict) {
        if (!hasCompleteFeatures(subject, avgPricePerSqmByDistrict)) {
            return null;
        }
        List<Property> usable = new ArrayList<>();
        for (Property candidate : dataset) {
            if (hasCompleteFeatures(candidate, avgPricePerSqmByDistrict)) {
                usable.add(candidate);
            }
        }
        if (usable.size() < MIN_REGRESSION_ROWS) {
            return null;
        }

        double[][] features = new double[usable.size()][];
        double[] targets = new double[usable.size()];
        for (int i = 0; i < usable.size(); i++) {
            features[i] = featuresOf(usable.get(i), avgPricePerSqmByDistrict);
            // Fitting price per m2 instead of raw price keeps every row's
            // contribution to the least-squares fit on the same scale, so a
            // $900,000 sale no longer dominates the fit the way it would
            // when area only appears as a feature and not as the target's
            // own denominator.
            targets[i] = usable.get(i).getAskingPrice().doubleValue() / usable.get(i).getAreaSqm().doubleValue();
        }

        LinearRegression regression = new LinearRegression();
        try {
            regression.fit(features, targets);
        } catch (IllegalStateException singularFeatures) {
            // A slice with no variation in some column cannot support a
            // fitted model. Falling back to comparables alone beats showing
            // a number produced from a degenerate fit.
            return null;
        }
        double predictedPricePerSqm = regression.predict(featuresOf(subject, avgPricePerSqmByDistrict));
        double predicted = predictedPricePerSqm * subject.getAreaSqm().doubleValue();
        return predicted > 0 ? predicted : null;
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

    private double blend(Double comparablesEstimate, Double regressionEstimate) {
        if (comparablesEstimate == null) {
            return regressionEstimate;
        }
        if (regressionEstimate == null) {
            return comparablesEstimate;
        }
        return (comparablesEstimate + regressionEstimate) / 2.0;
    }

    // The range is never tighter than the "above market" band from
    // system_setting - that band is the business's own definition of "still
    // looks like the same market", so the range should not claim tighter
    // confidence than that even when the comparables happen to agree. A
    // wider comparable spread, or too few comparables to trust, widens it
    // further.
    private double rangeHalfWidth(ComparablesResult comparablesResult, int comparableMinCount,
            double blendedEstimate, BigDecimal aboveMarketThresholdPercent) {
        double spreadHalfWidth = 0.0;
        int usableCount = comparablesResult.individualEstimates.length;
        if (usableCount >= 2) {
            spreadHalfWidth = Stats.standardDeviation(comparablesResult.individualEstimates);
            if (usableCount < comparableMinCount) {
                spreadHalfWidth *= (double) comparableMinCount / usableCount;
            }
        }
        double marketBandHalfWidth = blendedEstimate * aboveMarketThresholdPercent.doubleValue() / 100.0;
        return Math.max(spreadHalfWidth, marketBandHalfWidth);
    }

    private ValuationFlag computeFlag(BigDecimal askingPrice, BigDecimal lowerBound, BigDecimal upperBound,
            double blendedEstimate, BigDecimal implausibleThresholdPercent) {
        if (askingPrice.compareTo(lowerBound) >= 0 && askingPrice.compareTo(upperBound) <= 0) {
            return ValuationFlag.OK;
        }
        double percentOff = Math.abs(askingPrice.doubleValue() - blendedEstimate) / blendedEstimate * 100.0;
        return percentOff > implausibleThresholdPercent.doubleValue()
            ? ValuationFlag.IMPLAUSIBLE
            : ValuationFlag.ABOVE_MARKET;
    }

    private Map<String, BigDecimal> buildFactorContributions(ComparablesResult comparablesResult,
            Double regressionEstimate) {
        Map<String, BigDecimal> factors = new LinkedHashMap<>();
        if (comparablesResult.estimate != null) {
            factors.put("comparablesEstimate", toMoney(comparablesResult.estimate));
        }
        if (regressionEstimate != null) {
            factors.put("regressionEstimate", toMoney(regressionEstimate));
        }
        return factors;
    }

    // double is correct for every calculation above - this is arithmetic on
    // ratios and statistics, not a stored money value. It becomes BigDecimal,
    // scale 2, only here, at the boundary where a number turns into a price
    // or a displayed percentage.
    private BigDecimal toMoney(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
