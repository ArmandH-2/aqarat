package co.syntropyhq.aqarat.valuation;

import co.syntropyhq.aqarat.model.DealType;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.model.ValuationFlag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceEstimatorTest {

    // The real system_setting values, per CONVENTIONS.md.
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

    // No comparables, no usable regression data, and no district average
    // either: PriceEstimator truly has nothing to reason from and says so
    // with a clear, documented exception rather than a NaN or a
    // divide-by-zero.
    @Test
    void noComparablesNoRegressionDataAndNoDistrictAverageThrowsAClearException() {
        Property subject = property(100, 200_000, 3, 2, 2015);

        assertThrows(IllegalStateException.class, () -> estimator.estimate(subject,
            Collections.emptyList(), Collections.emptyList(), Map.of(),
            COMPARABLE_MIN_COUNT, ABOVE_MARKET_PERCENT, IMPLAUSIBLE_PERCENT));
    }

    // No comparables and no usable regression data, but the district does
    // have an average price per m² on file: a brand-new district's first
    // submission still gets a usable estimate instead of a dead end.
    @Test
    void districtAverageFallbackProducesAnEstimateInsteadOfThrowing() {
        Property subject = property(100, 200_000, 3, 2, 2015);

        ValuationResult result = estimator.estimate(subject, Collections.emptyList(), Collections.emptyList(),
            districtAverages, COMPARABLE_MIN_COUNT, ABOVE_MARKET_PERCENT, IMPLAUSIBLE_PERCENT);

        // districtAverages maps district 1 to $2000/m², times the subject's 100 m².
        assertEquals(0, new BigDecimal("200000.00").compareTo(result.getEstimatedValue()));
        assertTrue(result.getFactorContributions().containsKey("districtAverageFallback"));
        assertTrue(result.getLowerBound().compareTo(result.getEstimatedValue()) <= 0);
        assertTrue(result.getEstimatedValue().compareTo(result.getUpperBound()) <= 0);
    }

    // district.avg_price_per_sqm (db/schema.sql) is calibrated to sale
    // prices only - there is no rent equivalent column. Using it directly
    // as a monthly rent would be off by roughly two orders of magnitude, so
    // a RENT property still gets the same refusal it always has rather than
    // a fabricated number.
    @Test
    void districtAverageFallbackDoesNotApplyToRentProperties() {
        Property subject = property(100, 1_500, 3, 2, 2015);
        subject.setDealType(DealType.RENT);

        assertThrows(IllegalStateException.class, () -> estimator.estimate(subject,
            Collections.emptyList(), Collections.emptyList(), districtAverages,
            COMPARABLE_MIN_COUNT, ABOVE_MARKET_PERCENT, IMPLAUSIBLE_PERCENT));
    }

    // The range must widen, not narrow, when there is less to check the
    // estimate against. Zero comparables (regression-only) is compared
    // against a full set of comparables at the business's own minimum count.
    @Test
    void zeroComparablesProducesAWiderRangeThanManyComparables() {
        Map<Integer, BigDecimal> districts = Map.of(
            1, BigDecimal.valueOf(1800),
            2, BigDecimal.valueOf(2200),
            3, BigDecimal.valueOf(2600));
        List<Property> dataset = regressionTrainingSet(districts);
        Property subject = regressionSubject();

        ValuationResult zeroComparables = estimator.estimate(subject, Collections.emptyList(), dataset,
            districts, COMPARABLE_MIN_COUNT, ABOVE_MARKET_PERCENT, IMPLAUSIBLE_PERCENT);
        ValuationResult manyComparables = estimator.estimate(subject, fiveComparablesMatchingSubject(210_000),
            dataset, districts, COMPARABLE_MIN_COUNT, ABOVE_MARKET_PERCENT, IMPLAUSIBLE_PERCENT);

        double zeroWidth = width(zeroComparables);
        double manyWidth = width(manyComparables);
        assertTrue(zeroWidth > manyWidth,
            "zero-comparable range (" + zeroWidth + ") should be wider than the many-comparable range (" + manyWidth + ")");
    }

    // Recency weighting: two comparables with the same prices, but which one
    // is "recent" is swapped between scenarios. The recent one should pull
    // the weighted median toward its own price each time.
    @Test
    void recentComparableMovesTheEstimateMoreThanAnOldOne() {
        Property subject = property(100, 200_000, 3, 2, 2015);
        LocalDateTime recent = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime old = recent.minusYears(5);

        Property lowPriced = property(100, 150_000, 3, 2, 2015);
        Property highPriced = property(100, 250_000, 3, 2, 2015);

        lowPriced.setClosedAt(recent);
        highPriced.setClosedAt(old);
        ValuationResult lowIsRecent = estimator.estimate(subject, Arrays.asList(lowPriced, highPriced),
            Collections.emptyList(), districtAverages, 2, ABOVE_MARKET_PERCENT, IMPLAUSIBLE_PERCENT);

        lowPriced.setClosedAt(old);
        highPriced.setClosedAt(recent);
        ValuationResult highIsRecent = estimator.estimate(subject, Arrays.asList(lowPriced, highPriced),
            Collections.emptyList(), districtAverages, 2, ABOVE_MARKET_PERCENT, IMPLAUSIBLE_PERCENT);

        assertTrue(highIsRecent.getEstimatedValue().compareTo(lowIsRecent.getEstimatedValue()) > 0,
            "the estimate should be higher when the higher-priced comparable is the recent one");
    }

    private double width(ValuationResult result) {
        return result.getUpperBound().subtract(result.getLowerBound()).doubleValue();
    }

    private List<Property> fiveComparablesMatchingSubject(long pricePerComparable) {
        return Arrays.asList(
            property(110, pricePerComparable - 10_000, 3, 4, 2005),
            property(110, pricePerComparable - 5_000, 3, 4, 2005),
            property(110, pricePerComparable, 3, 4, 2005),
            property(110, pricePerComparable + 5_000, 3, 4, 2005),
            property(110, pricePerComparable + 10_000, 3, 4, 2005));
    }

    @Test
    void zeroAreaIsRejectedUpFront() {
        Property subject = property(0, 200_000, 3, 2, 2015);
        List<Property> comparables = comparablesAround(200_000);

        assertThrows(IllegalArgumentException.class, () -> estimator.estimate(subject, comparables,
            Collections.emptyList(), districtAverages, comparables.size(), ABOVE_MARKET_PERCENT,
            IMPLAUSIBLE_PERCENT));
    }

    // Fitting price per m2 should make the regression scale-free: multiply
    // every training price and the subject's own asking price by the same
    // constant, and the estimate should scale by exactly that constant.
    // Comparables are left empty so the blended estimate is the regression
    // estimate alone.
    @Test
    void scalingAllTrainingPricesScalesTheRegressionEstimateByTheSameFactor() {
        Map<Integer, BigDecimal> districts = Map.of(
            1, BigDecimal.valueOf(1800),
            2, BigDecimal.valueOf(2200),
            3, BigDecimal.valueOf(2600));
        List<Property> baseDataset = regressionTrainingSet(districts);
        Property baseSubject = regressionSubject();

        ValuationResult base = estimator.estimate(baseSubject, Collections.emptyList(), baseDataset,
            districts, COMPARABLE_MIN_COUNT, ABOVE_MARKET_PERCENT, IMPLAUSIBLE_PERCENT);

        int scale = 3;
        List<Property> scaledDataset = scalePrices(regressionTrainingSet(districts), scale);
        Property scaledSubject = regressionSubject();
        scaledSubject.setAskingPrice(scaledSubject.getAskingPrice().multiply(BigDecimal.valueOf(scale)));

        ValuationResult scaled = estimator.estimate(scaledSubject, Collections.emptyList(), scaledDataset,
            districts, COMPARABLE_MIN_COUNT, ABOVE_MARKET_PERCENT, IMPLAUSIBLE_PERCENT);

        double ratio = scaled.getEstimatedValue().doubleValue() / base.getEstimatedValue().doubleValue();
        assertEquals(scale, ratio, 0.01);
    }

    private List<Property> scalePrices(List<Property> properties, int scale) {
        for (Property property : properties) {
            property.setAskingPrice(property.getAskingPrice().multiply(BigDecimal.valueOf(scale)));
        }
        return properties;
    }

    // 40 rows, three districts, every feature column varied so the design
    // matrix has no constant column - a constant column (e.g. every row the
    // same district, or every row unfurnished) is linearly dependent with
    // the regression's own intercept column and the fit fails.
    private List<Property> regressionTrainingSet(Map<Integer, BigDecimal> districts) {
        int[] districtIds = {1, 2, 3};
        List<Property> rows = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            Property property = new Property();
            property.setDistrictId(districtIds[i % districtIds.length]);
            property.setPropertyTypeId(1);
            property.setAreaSqm(BigDecimal.valueOf(70 + i * 5));
            property.setBedrooms(2 + i % 4);
            property.setBathrooms(1 + i % 5);
            property.setFloorNumber(1 + i % 7);
            property.setYearBuilt(1985 + i % 13);
            property.setDealType(DealType.SALE);
            property.setStatus(PropertyStatus.CLOSED);
            property.setHasParking(i % 2 == 0);
            property.setHasElevator(i % 11 == 0);
            property.setHasBalcony(i % 8 == 0);
            property.setFurnished(i % 9 == 0);
            double pricePerSqm = 1500 + i * 23 + property.getBedrooms() * 40;
            property.setAskingPrice(BigDecimal.valueOf(pricePerSqm * property.getAreaSqm().doubleValue()));
            rows.add(property);
        }
        return rows;
    }

    private Property regressionSubject() {
        Property subject = new Property();
        subject.setDistrictId(1);
        subject.setPropertyTypeId(1);
        subject.setAreaSqm(BigDecimal.valueOf(110));
        subject.setBedrooms(3);
        subject.setBathrooms(2);
        subject.setFloorNumber(4);
        subject.setYearBuilt(2005);
        subject.setDealType(DealType.SALE);
        subject.setStatus(PropertyStatus.CLOSED);
        subject.setAskingPrice(BigDecimal.valueOf(230_000));
        return subject;
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
