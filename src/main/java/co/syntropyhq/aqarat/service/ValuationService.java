package co.syntropyhq.aqarat.service;

import co.syntropyhq.aqarat.dao.DistrictDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertySearch;
import co.syntropyhq.aqarat.dao.SystemSettingDao;
import co.syntropyhq.aqarat.dao.ValuationDao;
import co.syntropyhq.aqarat.model.DealType;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.model.SystemSetting;
import co.syntropyhq.aqarat.model.Valuation;
import co.syntropyhq.aqarat.util.Db;
import co.syntropyhq.aqarat.valuation.ComparableProperty;
import co.syntropyhq.aqarat.valuation.PriceEstimator;
import co.syntropyhq.aqarat.valuation.ValuationResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gathers what PriceEstimator needs, calls it, and saves what it returns.
 * CONVENTIONS.md layer rule 6: PriceEstimator does the maths and touches no
 * database; this class is what queries for the comparables and what saves
 * the result. There is no calculation in here - if a number is being
 * computed rather than looked up or read off a ValuationResult, it belongs
 * in PriceEstimator instead.
 */
public class ValuationService {

    // A slice with too few CLOSED rows cannot support a stable regression
    // (PriceEstimator wants three times its ten features), so both the
    // comparable search and the regression dataset ask for far more rows
    // than the ~385 CLOSED properties the seed currently has.
    private static final int DATASET_PAGE_SIZE = 1000;

    // Recorded on every row so a future model change is visible in the
    // history rather than silently reinterpreting old estimates. Matches
    // the column's own default in db/schema.sql.
    private static final String MODEL_VERSION = "v1";

    private final PropertyDao propertyDao;
    private final ValuationDao valuationDao;
    private final SystemSettingDao systemSettingDao;
    private final DistrictDao districtDao;
    private final PriceEstimator priceEstimator = new PriceEstimator();

    public ValuationService(PropertyDao propertyDao, ValuationDao valuationDao,
            SystemSettingDao systemSettingDao, DistrictDao districtDao) {
        this.propertyDao = propertyDao;
        this.valuationDao = valuationDao;
        this.systemSettingDao = systemSettingDao;
        this.districtDao = districtDao;
    }

    /**
     * Values a property and saves the result. Comparables are properties in
     * the same district, of the same property type and deal type, within
     * the area tolerance from system_setting, that have CLOSED (DESIGN.md
     * section 7) - the subject itself is under review, never CLOSED, so it
     * cannot appear in its own comparable set. The regression side uses every
     * CLOSED property of the same deal type. The flag PriceEstimator computes is advisory
     * only: this method does not block or alter anything based on it, an
     * agent's decision to publish anyway is theirs to make and record.
     */
    public ValuationResult valueProperty(int propertyId) throws SQLException, CannotValueException {
        try (Connection connection = Db.get()) {
            Property subject = propertyDao.findById(connection, propertyId);
            if (subject == null) {
                throw new IllegalArgumentException("No property with id " + propertyId + ".");
            }

            BigDecimal areaTolerance = requireDecimalSetting(connection, "comparable_area_tolerance_percent");
            int minComparableCount = requireIntSetting(connection, "comparable_min_count");
            BigDecimal aboveMarketThreshold = requireDecimalSetting(connection, "above_market_threshold_percent");
            BigDecimal implausibleThreshold = requireDecimalSetting(connection, "implausible_threshold_percent");

            List<Property> comparables = findComparables(connection, subject, areaTolerance);
            List<Property> regressionDataset = findRegressionDataset(connection, subject.getDealType());
            Map<Integer, BigDecimal> districtAverages = loadDistrictAverages(connection);

            ValuationResult result = estimate(subject, propertyId, comparables, regressionDataset,
                districtAverages, minComparableCount, aboveMarketThreshold, implausibleThreshold);
            save(connection, propertyId, result);
            return result;
        }
    }

    /**
     * Values a property that has not been saved yet, without recording anything.
     *
     * <p>An owner filling in the submission form should see what the system
     * thinks their property is worth while they are still deciding what to ask
     * for it — that is the point of publishing the reasoning rather than a
     * number. The draft has no id, so nothing is written to the valuation table
     * and nothing is excluded from the comparable set as "itself"; the estimate
     * that counts is the one taken when the submission is actually made.
     *
     * @param draft a property carrying at least area, district, type and deal
     *              type; it need not exist in the database
     */
    public ValuationResult previewValue(Property draft) throws SQLException, CannotValueException {
        try (Connection connection = Db.get()) {
            BigDecimal areaTolerance = requireDecimalSetting(connection, "comparable_area_tolerance_percent");
            int minComparableCount = requireIntSetting(connection, "comparable_min_count");
            BigDecimal aboveMarketThreshold = requireDecimalSetting(connection, "above_market_threshold_percent");
            BigDecimal implausibleThreshold = requireDecimalSetting(connection, "implausible_threshold_percent");

            List<Property> comparables = findComparables(connection, draft, areaTolerance);
            List<Property> regressionDataset = findRegressionDataset(connection, draft.getDealType());
            Map<Integer, BigDecimal> districtAverages = loadDistrictAverages(connection);

            // Id 0 matches no stored property, so nothing is filtered out of the
            // comparable set and no row is written.
            return estimate(draft, 0, comparables, regressionDataset, districtAverages,
                minComparableCount, aboveMarketThreshold, implausibleThreshold);
        }
    }

    /** What the review screen shows on open - the most recent estimate, or null if none yet. */
    public Valuation findLatest(int propertyId) throws SQLException {
        return valuationDao.findLatestByProperty(propertyId);
    }

    /**
     * The most recent saved estimate, rebuilt into the shape a fresh valuation
     * returns. The breakdown and comparables columns hold text this class
     * wrote, so this class is what reads it back - a screen should render one
     * type whether the estimate was just computed or loaded from last week.
     * Null when the property has never been valued.
     */
    public ValuationResult findLatestResult(int propertyId) throws SQLException {
        try (Connection connection = Db.get()) {
            Valuation saved = valuationDao.findLatestByProperty(connection, propertyId);
            if (saved == null) {
                return null;
            }
            return new ValuationResult(saved.getEstimatedValue(), saved.getLowerBound(),
                saved.getUpperBound(), saved.getPricePerSqm(), saved.getFlag(),
                parseBreakdown(saved.getBreakdown()),
                parseComparables(connection, saved.getComparables()));
        }
    }

    // This method writes one entry per line, but the seeded rows separate
    // theirs with semicolons and record factor names this version never
    // produces. Both are read, and an entry whose value is not a number is
    // skipped rather than failing the whole screen - a valuation saved by an
    // older version is still worth showing the parts of that make sense.
    private Map<String, BigDecimal> parseBreakdown(String text) {
        Map<String, BigDecimal> factors = new LinkedHashMap<>();
        if (text == null) {
            return factors;
        }
        for (String entry : text.split("[\n;]")) {
            String trimmed = entry.trim();
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String value = trimmed.substring(separator + 1).trim();
            if (value.matches("-?\\d+(\\.\\d+)?")) {
                factors.put(trimmed.substring(0, separator), new BigDecimal(value));
            }
        }
        return factors;
    }

    private List<ComparableProperty> parseComparables(Connection connection, String text)
            throws SQLException {
        List<ComparableProperty> comparables = new ArrayList<>();
        if (text == null) {
            return comparables;
        }
        for (String line : text.split("\n")) {
            String[] fields = line.trim().split(" ");
            // The seeded rows hold the single word "seeded" rather than a list,
            // so anything not shaped like one of ours is passed over.
            if (fields.length < 3 || !fields[0].startsWith("id=")) {
                continue;
            }
            Property property = propertyDao.findById(connection, valueOf(fields[0]).intValue());
            if (property != null) {
                comparables.add(new ComparableProperty(property, valueOf(fields[2])));
            }
        }
        return comparables;
    }

    // Each field is written as name=value, so the value is what follows the =.
    private BigDecimal valueOf(String field) {
        return new BigDecimal(field.substring(field.indexOf('=') + 1));
    }

    public List<Valuation> findHistory(int propertyId) throws SQLException {
        return valuationDao.findAllByProperty(propertyId);
    }

    // A rural property with no comparables and a thin CLOSED dataset is a
    // normal outcome, not a bug, so PriceEstimator's IllegalStateException
    // is turned into a checked type the review screen can catch and show a
    // message for, the same way PropertyService reports a refused request.
    private ValuationResult estimate(Property subject, int propertyId, List<Property> comparables,
            List<Property> regressionDataset, Map<Integer, BigDecimal> districtAverages,
            int minComparableCount, BigDecimal aboveMarketThreshold, BigDecimal implausibleThreshold)
            throws CannotValueException {
        try {
            return priceEstimator.estimate(subject, comparables, regressionDataset, districtAverages,
                minComparableCount, aboveMarketThreshold, implausibleThreshold);
        } catch (IllegalStateException notEnoughData) {
            throw new CannotValueException(
                "Not enough comparable or historical data to value property " + propertyId + ".",
                notEnoughData);
        }
    }

    private List<Property> findComparables(Connection connection, Property subject, BigDecimal areaTolerancePercent)
            throws SQLException {
        BigDecimal toleranceFraction = areaTolerancePercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        BigDecimal minArea = subject.getAreaSqm().multiply(BigDecimal.ONE.subtract(toleranceFraction))
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal maxArea = subject.getAreaSqm().multiply(BigDecimal.ONE.add(toleranceFraction))
            .setScale(2, RoundingMode.HALF_UP);

        PropertySearch filters = new PropertySearch();
        filters.setDistrictId(subject.getDistrictId());
        filters.setPropertyTypeId(subject.getPropertyTypeId());
        filters.setDealType(subject.getDealType());
        filters.setMinArea(minArea);
        filters.setMaxArea(maxArea);
        return propertyDao.search(connection, List.of(PropertyStatus.CLOSED), filters, 0, DATASET_PAGE_SIZE);
    }

    // Restricted to the subject's deal type for the same reason the
    // comparables are: asking_price is a sale price for SALE and a monthly
    // rent for RENT, so one regression fitted over both learns a number that
    // is wrong for either. Measured against the closed properties, mixing
    // them put the rent estimates out by four orders of magnitude.
    private List<Property> findRegressionDataset(Connection connection, DealType dealType)
            throws SQLException {
        PropertySearch filters = new PropertySearch();
        filters.setDealType(dealType);
        return propertyDao.search(
            connection, List.of(PropertyStatus.CLOSED), filters, 0, DATASET_PAGE_SIZE);
    }

    private Map<Integer, BigDecimal> loadDistrictAverages(Connection connection) throws SQLException {
        Map<Integer, BigDecimal> averages = new LinkedHashMap<>();
        for (District district : districtDao.findAll(connection)) {
            averages.put(district.getId(), district.getAvgPricePerSqm());
        }
        return averages;
    }

    // system_setting deliberately stores text and leaves parsing to the
    // caller (SystemSettingDao's own comment). A missing key is a
    // configuration bug, not a value this class is allowed to guess at -
    // DESIGN.md section 7 is explicit that the threshold lives in the
    // table, not in code, so there is no fallback to fall back to.
    private SystemSetting requireSetting(Connection connection, String key) throws SQLException {
        SystemSetting setting = systemSettingDao.findByKey(connection, key);
        if (setting == null) {
            throw new IllegalStateException("system_setting is missing required key: " + key);
        }
        return setting;
    }

    private BigDecimal requireDecimalSetting(Connection connection, String key) throws SQLException {
        return new BigDecimal(requireSetting(connection, key).getValue());
    }

    private int requireIntSetting(Connection connection, String key) throws SQLException {
        return Integer.parseInt(requireSetting(connection, key).getValue());
    }

    private void save(Connection connection, int propertyId, ValuationResult result) throws SQLException {
        Valuation valuation = new Valuation();
        valuation.setPropertyId(propertyId);
        valuation.setEstimatedValue(result.getEstimatedValue());
        valuation.setLowerBound(result.getLowerBound());
        valuation.setUpperBound(result.getUpperBound());
        valuation.setPricePerSqm(result.getPricePerSqm());
        valuation.setFlag(result.getFlag());
        valuation.setBreakdown(formatBreakdown(result.getFactorContributions()));
        valuation.setComparables(formatComparables(result.getComparables()));
        valuation.setModelVersion(MODEL_VERSION);
        valuationDao.insert(connection, valuation);
    }

    private String formatBreakdown(Map<String, BigDecimal> factors) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, BigDecimal> factor : factors.entrySet()) {
            text.append(factor.getKey()).append('=').append(factor.getValue()).append('\n');
        }
        return text.toString();
    }

    private String formatComparables(List<ComparableProperty> comparables) {
        StringBuilder text = new StringBuilder();
        for (ComparableProperty comparable : comparables) {
            Property property = comparable.getProperty();
            BigDecimal pricePerSqm = property.getAskingPrice()
                .divide(property.getAreaSqm(), 2, RoundingMode.HALF_UP);
            text.append("id=").append(property.getId())
                .append(" pricePerSqm=").append(pricePerSqm)
                .append(" similarity=").append(comparable.getSimilarityScore())
                .append('\n');
        }
        return text.toString();
    }

    // A distinct, checked type so a controller can tell "this property
    // genuinely cannot be valued yet" apart from a database failure, and
    // show the right message for each (CONVENTIONS.md, Errors).
    public static class CannotValueException extends Exception {

        public CannotValueException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
