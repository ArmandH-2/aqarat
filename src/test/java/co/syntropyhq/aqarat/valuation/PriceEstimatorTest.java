package co.syntropyhq.aqarat.valuation;

import co.syntropyhq.aqarat.model.DealType;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.model.ValuationFlag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceEstimatorTest {

    // The real system_setting values, per CLAUDE.md.
    private static final BigDecimal ABOVE_MARKET_PERCENT = BigDecimal.valueOf(20);
    private static final BigDecimal IMPLAUSIBLE_PERCENT = BigDecimal.valueOf(60);
    private static final int COMPARABLE_MIN_COUNT = 5;

    private final PriceEstimator estimator = new PriceEstimator();
    private final Map<Integer, BigDecimal> districtAverages = Map.of(1, BigDecimal.valueOf(2000));

    // Three comparables with the subject's own bedroom count, floor and
    // year built, so the per-comparable adjustment factor is exactly 1 and
    // the arithmetic below can be checked by hand: prices per m² of 1900,
    // 2000 and 2100 median to 2000, times a 100 m² subject is $200,000.
    @Test
    void knownComparablesProduceTheExpectedEstimate() {
        Property subject = property(100, 200_000, 3, 2, 2015);
        List<Property> comparables = Arrays.asList(
            property(100, 190_000, 3, 2, 2015),
            property(100, 200_000, 3, 2, 2015),
            property(100, 210_000, 3, 2, 2015));

        ValuationResult result = estimator.estimate(subject, comparables, Collections.emptyList(),
            districtAverages, comparables.size(), ABOVE_MARKET_PERCENT, IMPLAUSIBLE_PERCENT);

        assertEquals(0, new BigDecimal("200000.00").compareTo(result.getEstimatedValue()));
        assertEquals(0, new BigDecimal("2000.00").compareTo(result.getPricePerSqm()));
        // Half-width is max(spread stddev = 10,000, 20% market band = 40,000).
        assertEquals(0, new BigDecimal("160000.00").compareTo(result.getLowerBound()));
        assertEquals(0, new BigDecimal("240000.00").compareTo(result.getUpperBound()));
    }

    @Test
    void rangeAlwaysBracketsTheEstimate() {
        Property subject = property(100, 205_000, 3, 2, 2015);
        List<Property> comparables = Arrays.asList(
            property(80, 150_000, 2, 1, 2000),
            property(130, 280_000, 4, 5, 2020),
            property(95, 205_000, 3, 3, 2010));

        ValuationResult result = estimator.estimate(subject, comparables, Collections.emptyList(),
            districtAverages, comparables.size(), ABOVE_MARKET_PERCENT, IMPLAUSIBLE_PERCENT);

        assertTrue(result.getLowerBound().compareTo(result.getEstimatedValue()) <= 0);
        assertTrue(result.getEstimatedValue().compareTo(result.getUpperBound()) <= 0);
    }

    @Test
    void askingPriceInsideTheRangeIsOk() {
        Property subject = property(100, 220_000, 3, 2, 2015);
        List<Property> comparables = comparablesAround(200_000);

        ValuationResult result = estimator.estimate(subject, comparables, Collections.emptyList(),
            districtAverages, comparables.size(), ABOVE_MARKET_PERCENT, IMPLAUSIBLE_PERCENT);

        assertEquals(ValuationFlag.OK, result.getFlag());
    }

    @Test
    void villaListedAtOneDollarIsImplausible() {
        Property subject = property(100, BigDecimal.ONE, 3, 2, 2015);
        List<Property> comparables = comparablesAround(200_000);

        ValuationResult result = estimator.estimate(subject, comparables, Collections.emptyList(),
            districtAverages, comparables.size(), ABOVE_MARKET_PERCENT, IMPLAUSIBLE_PERCENT);

        assertEquals(ValuationFlag.IMPLAUSIBLE, result.getFlag());
    }

    @Test
    void askingPriceFiftyTimesTheEstimateIsImplausible() {
        Property subject = property(100, 10_000_000, 3, 2, 2015);
        List<Property> comparables = comparablesAround(200_000);

        ValuationResult result = estimator.estimate(subject, comparables, Collections.emptyList(),
            districtAverages, comparables.size(), ABOVE_MARKET_PERCENT, IMPLAUSIBLE_PERCENT);

        assertEquals(ValuationFlag.IMPLAUSIBLE, result.getFlag());
    }

    // Fewer than comparable_min_count comparables: the estimate still comes
    // back usable (no NaN, no exception) with a range widened to reflect
    // the thinner evidence, rather than refusing to value the property.
    @Test
    void fewerComparablesThanTheMinimumWidensTheRangeInsteadOfFailing() {
        Property subject = property(100, 200_000, 3, 2, 2015);
        List<Property> comparables = Arrays.asList(
            property(100, 190_000, 3, 2, 2015),
            property(100, 210_000, 3, 2, 2015));

        ValuationResult result = estimator.estimate(subject, comparables, Collections.emptyList(),
            districtAverages, COMPARABLE_MIN_COUNT, ABOVE_MARKET_PERCENT, IMPLAUSIBLE_PERCENT);

        assertTrue(result.getEstimatedValue().signum() > 0);
        assertTrue(result.getLowerBound().compareTo(result.getEstimatedValue()) <= 0);
        assertTrue(result.getEstimatedValue().compareTo(result.getUpperBound()) <= 0);
    }

    // No comparables and no usable regression data: PriceEstimator has
    // nothing to reason from and says so with a clear, documented exception
    // rather than a NaN or a divide-by-zero.
    @Test
    void noComparablesAndNoRegressionDataThrowsAClearException() {
        Property subject = property(100, 200_000, 3, 2, 2015);

        assertThrows(IllegalStateException.class, () -> estimator.estimate(subject,
            Collections.emptyList(), Collections.emptyList(), districtAverages,
            COMPARABLE_MIN_COUNT, ABOVE_MARKET_PERCENT, IMPLAUSIBLE_PERCENT));
    }

    @Test
    void zeroAreaIsRejectedUpFront() {
        Property subject = property(0, 200_000, 3, 2, 2015);
        List<Property> comparables = comparablesAround(200_000);

        assertThrows(IllegalArgumentException.class, () -> estimator.estimate(subject, comparables,
            Collections.emptyList(), districtAverages, comparables.size(), ABOVE_MARKET_PERCENT,
            IMPLAUSIBLE_PERCENT));
    }

    private List<Property> comparablesAround(long pricePerComparable) {
        return Arrays.asList(
            property(100, pricePerComparable - 5_000, 3, 2, 2015),
            property(100, pricePerComparable, 3, 2, 2015),
            property(100, pricePerComparable + 5_000, 3, 2, 2015));
    }

    private Property property(int areaSqm, long askingPrice, int bedrooms, int floorNumber, int yearBuilt) {
        return property(areaSqm, BigDecimal.valueOf(askingPrice), bedrooms, floorNumber, yearBuilt);
    }

    private Property property(int areaSqm, BigDecimal askingPrice, int bedrooms, int floorNumber, int yearBuilt) {
        Property property = new Property();
        property.setDistrictId(1);
        property.setPropertyTypeId(1);
        property.setAreaSqm(BigDecimal.valueOf(areaSqm));
        property.setBedrooms(bedrooms);
        property.setBathrooms(2);
        property.setFloorNumber(floorNumber);
        property.setYearBuilt(yearBuilt);
        property.setDealType(DealType.SALE);
        property.setAskingPrice(askingPrice);
        property.setStatus(PropertyStatus.CLOSED);
        return property;
    }
}
