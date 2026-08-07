package co.syntropyhq.aqarat.valuation;

import co.syntropyhq.aqarat.model.DealType;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.ValuationFlag;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a property and a caller-supplied slice of the closed-property
 * dataset into a ValuationResult. Touches no database - everything it needs
 * arrives as an argument, per DESIGN.md section 7.
 *
 * Two estimates are blended: a weighted median of adjusted comparable
 * prices per m², and a weighted linear regression over the wider
 * closed-property dataset. Both weight more recent closings more heavily.
 * When neither has enough to work with, a district-average fallback keeps
 * a brand-new district from valuing at nothing. The confidence range comes
 * from how much the comparables disagree with each other, and widens as
 * the evidence behind the estimate thins out.
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

    // A model hyperparameter, not a business threshold like the market-band
    // percentages in system_setting - it says how fast the estimator
    // forgets a sale, not what counts as "the same market". Twelve months:
    // roughly a full cycle of seasonal listing activity, so one sale does
    // not lose most of its weight before the market has had a chance to move.
    private static final double RECENCY_HALF_LIFE_MONTHS = 12.0;

    // How far the range widens when there is nothing to measure a spread
    // from. 3x the market band at zero comparables; the district-average
    // fallback has even less to go on than that, so it widens further still.
    private static final double ZERO_COMPARABLES_WIDTH_MULTIPLIER = 3.0;
    private static final double DISTRICT_FALLBACK_WIDTH_MULTIPLIER = 5.0;

    // Order matches featuresOf() exactly - this is what turns
    // LinearRegression's plain coefficient array into something an agent
    // can read later: "yearBuilt", not "coefficient 4".
    private static final List<String> FEATURE_NAMES = List.of(
        "areaSqm", "bedrooms", "bathrooms", "floorNumber", "yearBuilt",
        "districtAvgPricePerSqm", "hasParking", "hasElevator", "hasBalcony", "furnished");

    private static final RegressionResult NO_REGRESSION = new RegressionResult(null, Collections.emptyMap());

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
        RegressionResult regressionResult = regressionEstimate(subject, regressionDataset, avgPricePerSqmByDistrict);
        boolean usedFallback = comparablesResult.estimate == null && regressionResult.estimate == null;
        Double districtFallbackEstimate = usedFallback
            ? requireDistrictAverageEstimate(subject, avgPricePerSqmByDistrict) : null;

        double blendedEstimate = usedFallback
            ? districtFallbackEstimate
            : blend(comparablesResult.estimate, regressionResult.estimate);
        if (blendedEstimate <= 0) {
            throw new IllegalStateException("Computed a non-positive estimate for property " + subject.getId());
        }

        double halfWidth = rangeHalfWidth(comparablesResult, comparableMinCount, blendedEstimate,
            aboveMarketThresholdPercent, usedFallback);
        BigDecimal estimatedValue = toMoney(blendedEstimate);
        BigDecimal lowerBound = toMoney(Math.max(0, blendedEstimate - halfWidth));
        BigDecimal upperBound = toMoney(blendedEstimate + halfWidth);
        BigDecimal pricePerSqm = estimatedValue.divide(subject.getAreaSqm(), 2, RoundingMode.HALF_UP);

        ValuationFlag flag = computeFlag(subject.getAskingPrice(), lowerBound, upperBound,
            blendedEstimate, implausibleThresholdPercent);

        return new ValuationResult(estimatedValue, lowerBound, upperBound, pricePerSqm, flag,
            buildFactorContributions(comparablesResult, regressionResult, districtFallbackEstimate),
            comparablesResult.comparableProperties, regressionResult.coefficients);
    }

    // Median rather than mean: one over- or under-priced comparable should
    // not swing the estimate the way it would swing an average. Weighted by
    // recency so a sale from last month outweighs one from three years ago.
    private ComparablesResult comparablesEstimate(Property subject, List<Property> comparables) {
        AdjustedComparables adjusted = collectAdjustedComparables(subject, comparables);
        if (adjusted.prices.isEmpty()) {
            return new ComparablesResult(null, new double[0], adjusted.used);
        }

        double area = subject.getAreaSqm().doubleValue();
        double[] prices = Stats.toArray(adjusted.prices);
        double[] weights = RecencyWeighting.weightsFor(adjusted.properties, RECENCY_HALF_LIFE_MONTHS);
        double medianPricePerSqm = Stats.weightedMedian(prices, weights);

        double[] individualEstimates = prices.clone();
        for (int i = 0; i < individualEstimates.length; i++) {
            individualEstimates[i] *= area;
        }
        return new ComparablesResult(medianPricePerSqm * area, individualEstimates, adjusted.used);
    }

    private AdjustedComparables collectAdjustedComparables(Property subject, List<Property> comparables) {
        AdjustedComparables result = new AdjustedComparables();
        for (Property comp : comparables) {
            if (comp.getAreaSqm() == null || comp.getAreaSqm().signum() <= 0 || comp.getAskingPrice() == null) {
                continue; // bad data - skip rather than divide by zero
            }
            double pricePerSqm = comp.getAskingPrice().doubleValue() / comp.getAreaSqm().doubleValue();
            double adjusted = pricePerSqm * adjustmentFactor(subject, comp);
            if (adjusted <= 0) {
                continue; // an extreme diff pushed the adjustment negative - not usable
            }
            result.prices.add(adjusted);
            result.properties.add(comp);
            result.used.add(new ComparableProperty(comp, similarityScore(subject, comp)));
        }
        return result;
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
    private RegressionResult regressionEstimate(Property subject, List<Property> dataset,
            Map<Integer, BigDecimal> avgPricePerSqmByDistrict) {
        if (!hasCompleteFeatures(subject, avgPricePerSqmByDistrict)) {
            return NO_REGRESSION;
        }
        List<Property> usable = usableForRegression(dataset, avgPricePerSqmByDistrict);
        if (usable.size() < MIN_REGRESSION_ROWS) {
            return NO_REGRESSION;
        }
        return fitAndPredict(subject, usable, avgPricePerSqmByDistrict);
    }

    private List<Property> usableForRegression(List<Property> dataset,
            Map<Integer, BigDecimal> avgPricePerSqmByDistrict) {
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
            Map<Integer, BigDecimal> avgPricePerSqmByDistrict) {
        double[][] features = new double[usable.size()][];
        double[] targets = new double[usable.size()];
        for (int i = 0; i < usable.size(); i++) {
            features[i] = featuresOf(usable.get(i), avgPricePerSqmByDistrict);
            targets[i] = usable.get(i).getAskingPrice().doubleValue() / usable.get(i).getAreaSqm().doubleValue();
        }
        double[] weights = RecencyWeighting.weightsFor(usable, RECENCY_HALF_LIFE_MONTHS);

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

    private double blend(Double comparablesEstimate, Double regressionEstimate) {
        if (comparablesEstimate == null) {
            return regressionEstimate;
        }
        if (regressionEstimate == null) {
            return comparablesEstimate;
        }
        return (comparablesEstimate + regressionEstimate) / 2.0;
    }

    // The last resort before refusing to value the property at all - a
    // brand-new district's first submission otherwise has nothing else to
    // go on. Unlike a comparable, there is no specific property to adjust
    // the average against: the bedroom, floor and age nudges each need a
    // baseline to diff the subject against, and the only input here is one
    // aggregate per district, so the average passes through as-is.
    //
    // SALE only. district.avg_price_per_sqm is a single, sale-calibrated
    // figure (db/schema.sql has no rent equivalent, and adding one is a
    // schema change, not something this class can decide on its own). Used
    // directly as a monthly rent it would be off by roughly two orders of
    // magnitude, so a RENT subject with no comparables and no regression
    // still gets the same "cannot value" refusal it always has.
    private double requireDistrictAverageEstimate(Property subject, Map<Integer, BigDecimal> avgPricePerSqmByDistrict) {
        BigDecimal districtAvg = avgPricePerSqmByDistrict.get(subject.getDistrictId());
        if (districtAvg == null || subject.getDealType() != DealType.SALE) {
            throw new IllegalStateException(
                "Cannot value property " + subject.getId()
                    + ": no usable comparables, not enough closed-property data for a regression, "
                    + "and no district average fallback available for a " + subject.getDealType() + " property.");
        }
        return districtAvg.doubleValue() * subject.getAreaSqm().doubleValue();
    }

    // The range must say when the estimator is guessing. It never claims a
    // tighter band than the "above market" threshold - the business's own
    // definition of "still looks like the same market" - and widens further
    // as comparable evidence thins out, reaching its widest at zero
    // comparables. A district-average fallback has no comparable evidence
    // at all, so it is wider still.
    private double rangeHalfWidth(ComparablesResult comparablesResult, int comparableMinCount,
            double blendedEstimate, BigDecimal aboveMarketThresholdPercent, boolean isDistrictFallback) {
        double marketBandHalfWidth = blendedEstimate * aboveMarketThresholdPercent.doubleValue() / 100.0;
        if (isDistrictFallback) {
            return marketBandHalfWidth * DISTRICT_FALLBACK_WIDTH_MULTIPLIER;
        }

        int usableCount = comparablesResult.individualEstimates.length;
        double spreadHalfWidth = usableCount >= 2 ? Stats.standardDeviation(comparablesResult.individualEstimates) : 0.0;
        double baseHalfWidth = Math.max(spreadHalfWidth, marketBandHalfWidth);
        return baseHalfWidth * thinEvidenceMultiplier(usableCount, comparableMinCount);
    }

    // 1.0 once there are as many comparables as the business trusts
    // (comparable_min_count), climbing linearly as they fall short, and
    // reaching ZERO_COMPARABLES_WIDTH_MULTIPLIER at zero. This replaces the
    // old behaviour where zero comparables produced the narrowest range,
    // because there was no spread left to measure.
    private double thinEvidenceMultiplier(int usableCount, int comparableMinCount) {
        if (comparableMinCount <= 0 || usableCount >= comparableMinCount) {
            return 1.0;
        }
        double shortfall = (comparableMinCount - usableCount) / (double) comparableMinCount;
        return 1.0 + shortfall * (ZERO_COMPARABLES_WIDTH_MULTIPLIER - 1.0);
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
            RegressionResult regressionResult, Double districtFallbackEstimate) {
        Map<String, BigDecimal> factors = new LinkedHashMap<>();
        if (comparablesResult.estimate != null) {
            factors.put("comparablesEstimate", toMoney(comparablesResult.estimate));
        }
        if (regressionResult.estimate != null) {
            factors.put("regressionEstimate", toMoney(regressionResult.estimate));
        }
        if (districtFallbackEstimate != null) {
            factors.put("districtAverageFallback", toMoney(districtFallbackEstimate));
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

    // Coefficients keep more precision than a displayed price: the area
    // coefficient in particular is a $/m2 change per additional m2 and is
    // often a small fraction that scale-2 would round away to nothing.
    private BigDecimal toCoefficientValue(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }
}
