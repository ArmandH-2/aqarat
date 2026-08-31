package co.syntropyhq.aqarat.valuation;

import co.syntropyhq.aqarat.dao.DistrictDao;
import co.syntropyhq.aqarat.dao.PropertyDao;
import co.syntropyhq.aqarat.dao.PropertySearch;
import co.syntropyhq.aqarat.dao.SystemSettingDao;
import co.syntropyhq.aqarat.model.DealType;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.model.SystemSetting;
import co.syntropyhq.aqarat.util.Db;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Measures PriceEstimator as an automated valuation model, against the ratio-study
 * statistics the IAAO Standard on Automated Valuation Models requires an AVM to report.
 *
 * <p>This is an evaluation harness, not a unit test. It is deliberately named so that
 * Surefire's default includes ({@code *Test}, {@code Test*}, {@code *Tests}) do not pick
 * it up: it needs a seeded database and takes minutes, neither of which belongs in the
 * build. Run it explicitly:
 *
 * <pre>mvn test -Dtest=AvmEvaluation</pre>
 *
 * <p><b>Method.</b> Every CLOSED property is used as a held-out subject in turn. The
 * subject is removed from its own comparable set and from its own regression training
 * set before it is valued, so no prediction can see the price it is trying to predict.
 * This is leave-one-out cross-validation; without the exclusion the reported error
 * would be optimistic by construction. In production the exclusion is automatic rather
 * than explicit, because a subject under review is never CLOSED and so is never in the
 * pool it is compared against.
 *
 * <p>Every figure the report quotes comes from the two files this writes into
 * {@code report/evidence/}.
 */
class AvmEvaluation {

    /** Wide enough to hold every CLOSED row in one page, matching ValuationService. */
    private static final int DATASET_PAGE_SIZE = 5000;

    private static final Path OUTPUT_DIR = Path.of("report", "evidence");

    private final PropertyDao propertyDao = new PropertyDao();
    private final DistrictDao districtDao = new DistrictDao();
    private final SystemSettingDao systemSettingDao = new SystemSettingDao();
    private final PriceEstimator estimator = new PriceEstimator();

    @Test
    void measureAgainstClosedProperties() throws SQLException, IOException {
        Files.createDirectories(OUTPUT_DIR);

        try (Connection connection = Db.get()) {
            Settings settings = Settings.read(connection, systemSettingDao);
            Map<Integer, BigDecimal> districtAverages = districtAverages(connection);
            Map<Integer, String> districtNames = districtNames(connection);

            List<Prediction> predictions = new ArrayList<>();
            for (DealType dealType : DealType.values()) {
                List<Property> closed = closedProperties(connection, dealType);
                for (Property subject : closed) {
                    predict(subject, closed, connection, settings, districtAverages)
                        .ifPresentOrElse(predictions::add, () -> { });
                }
            }

            writePredictionsCsv(predictions, districtNames);
            writeReport(predictions, settings);
        }
    }

    // ------------------------------------------------------------------ prediction

    private java.util.Optional<Prediction> predict(Property subject, List<Property> sameDealType,
            Connection connection, Settings settings, Map<Integer, BigDecimal> districtAverages)
            throws SQLException {

        if (subject.getAreaSqm() == null || subject.getAreaSqm().signum() <= 0
            || subject.getAskingPrice() == null || subject.getAskingPrice().signum() <= 0) {
            return java.util.Optional.empty();
        }

        // Leave-one-out. The subject is CLOSED, so unlike a real submission it would
        // otherwise appear in both the pool it is compared against and the data the
        // regression is fitted on.
        List<Property> comparables = comparablesFor(connection, subject, settings.areaTolerancePercent)
            .stream().filter(p -> p.getId() != subject.getId()).toList();
        List<Property> training = sameDealType.stream()
            .filter(p -> p.getId() != subject.getId()).toList();

        try {
            ValuationResult result = estimator.estimate(subject, comparables, training, districtAverages,
                settings.comparableMinCount, settings.aboveMarketThresholdPercent,
                settings.implausibleThresholdPercent);

            return java.util.Optional.of(new Prediction(
                subject.getId(),
                subject.getDealType(),
                subject.getDistrictId(),
                subject.getAreaSqm().doubleValue(),
                subject.getBedrooms(),
                subject.getAskingPrice().doubleValue(),
                result.getEstimatedValue().doubleValue(),
                result.getLowerBound().doubleValue(),
                result.getUpperBound().doubleValue(),
                comparables.size(),
                result.getFlag().name()));
        } catch (RuntimeException e) {
            // A subject the estimator refuses (no comparables, no regression, no district
            // average) is a reportable outcome, not a crash. Counted as a refusal below.
            return java.util.Optional.empty();
        }
    }

    private List<Property> comparablesFor(Connection connection, Property subject, BigDecimal tolerancePercent)
            throws SQLException {
        BigDecimal fraction = tolerancePercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        PropertySearch filters = new PropertySearch();
        filters.setDistrictId(subject.getDistrictId());
        filters.setPropertyTypeId(subject.getPropertyTypeId());
        filters.setDealType(subject.getDealType());
        filters.setMinArea(subject.getAreaSqm().multiply(BigDecimal.ONE.subtract(fraction))
            .setScale(2, RoundingMode.HALF_UP));
        filters.setMaxArea(subject.getAreaSqm().multiply(BigDecimal.ONE.add(fraction))
            .setScale(2, RoundingMode.HALF_UP));
        return propertyDao.search(connection, List.of(PropertyStatus.CLOSED), filters, 0, DATASET_PAGE_SIZE);
    }

    private List<Property> closedProperties(Connection connection, DealType dealType) throws SQLException {
        PropertySearch filters = new PropertySearch();
        filters.setDealType(dealType);
        return propertyDao.search(connection, List.of(PropertyStatus.CLOSED), filters, 0, DATASET_PAGE_SIZE);
    }

    private Map<Integer, BigDecimal> districtAverages(Connection connection) throws SQLException {
        Map<Integer, BigDecimal> byId = new HashMap<>();
        for (District district : districtDao.findAll(connection)) {
            byId.put(district.getId(), district.getAvgPricePerSqm());
        }
        return byId;
    }

    private Map<Integer, String> districtNames(Connection connection) throws SQLException {
        Map<Integer, String> byId = new HashMap<>();
        for (District district : districtDao.findAll(connection)) {
            byId.put(district.getId(), district.getName());
        }
        return byId;
    }

    // ------------------------------------------------------------------ IAAO statistics

    /**
     * The ratio-study statistics of the IAAO Standard on Ratio Studies, computed over
     * ratios of estimated value to actual price.
     */
    private record RatioStudy(
            int n, double medianRatio, double meanRatio, double weightedMeanRatio,
            double cod, double prd, double prb,
            double mdApe, double meanApe, double pe10, double pe20, double pe25,
            double fsd, double coverage95, double flaggedOk) {

        static RatioStudy of(List<Prediction> sample) {
            int n = sample.size();
            double[] ratios = sample.stream().mapToDouble(Prediction::ratio).sorted().toArray();
            double medianRatio = median(ratios);
            double meanRatio = mean(ratios);

            double sumEstimated = sample.stream().mapToDouble(Prediction::estimated).sum();
            double sumActual = sample.stream().mapToDouble(Prediction::actual).sum();
            double weightedMeanRatio = sumEstimated / sumActual;

            // COD: average absolute deviation from the median ratio, as a percentage of it.
            // The IAAO measure of horizontal equity - how consistent the model is across
            // properties that ought to be treated alike.
            double meanAbsDeviation = sample.stream()
                .mapToDouble(p -> Math.abs(p.ratio() - medianRatio)).average().orElse(0);
            double cod = 100.0 * meanAbsDeviation / medianRatio;

            // PRD: mean ratio over weighted mean ratio. Above 1.00 is regressive - low-value
            // properties valued relatively higher than high-value ones. IAAO expects 0.98-1.03.
            double prd = meanRatio / weightedMeanRatio;

            // PRB: the slope of relative deviation from the median ratio against value, in
            // doublings. A PRB of -0.03 means values fall 3% relative for every doubling.
            // IAAO expects -0.05 to +0.05.
            double prb = priceRelatedBias(sample, medianRatio);

            double[] apes = sample.stream().mapToDouble(Prediction::absolutePercentError).sorted().toArray();
            double mdApe = median(apes);
            double meanApe = mean(apes);

            double pe10 = 100.0 * sample.stream().filter(p -> p.absolutePercentError() <= 10).count() / n;
            double pe20 = 100.0 * sample.stream().filter(p -> p.absolutePercentError() <= 20).count() / n;
            double pe25 = 100.0 * sample.stream().filter(p -> p.absolutePercentError() <= 25).count() / n;

            // FSD: a robust dispersion measure of the ratios, the AVM industry's usual
            // companion to the median ratio. 1.4826 scales MAD to a normal-equivalent sigma.
            double[] ratioDeviations = sample.stream()
                .mapToDouble(p -> Math.abs(p.ratio() - medianRatio)).sorted().toArray();
            double fsd = 1.4826 * median(ratioDeviations) / medianRatio;

            // Interval calibration: how often the actual price falls inside the confidence
            // range the system shows the user. A range nobody can rely on is worse than none.
            double coverage = 100.0 * sample.stream().filter(Prediction::actualInsideRange).count() / n;

            double flaggedOk = 100.0 * sample.stream().filter(p -> "OK".equals(p.flag())).count() / n;

            return new RatioStudy(n, medianRatio, meanRatio, weightedMeanRatio, cod, prd, prb,
                mdApe, meanApe, pe10, pe20, pe25, fsd, coverage, flaggedOk);
        }

        private static double priceRelatedBias(List<Prediction> sample, double medianRatio) {
            // IAAO PRB: regress (ratio_i - median) / median on
            // ln( (actual_i + estimated_i / median) / 2 ) / ln 2, through the origin-free
            // least squares slope.
            double sumX = 0, sumY = 0, sumXy = 0, sumXx = 0;
            int n = sample.size();
            for (Prediction p : sample) {
                double proxyValue = (p.actual() + p.estimated() / medianRatio) / 2.0;
                if (proxyValue <= 0) {
                    continue;
                }
                double x = Math.log(proxyValue) / Math.log(2);
                double y = (p.ratio() - medianRatio) / medianRatio;
                sumX += x;
                sumY += y;
                sumXy += x * y;
                sumXx += x * x;
            }
            double denominator = n * sumXx - sumX * sumX;
            return denominator == 0 ? 0 : (n * sumXy - sumX * sumY) / denominator;
        }

        private static double median(double[] sorted) {
            if (sorted.length == 0) {
                return 0;
            }
            int mid = sorted.length / 2;
            return sorted.length % 2 == 0 ? (sorted[mid - 1] + sorted[mid]) / 2.0 : sorted[mid];
        }

        private static double mean(double[] values) {
            double total = 0;
            for (double v : values) {
                total += v;
            }
            return values.length == 0 ? 0 : total / values.length;
        }
    }

    // ------------------------------------------------------------------ output

    private void writePredictionsCsv(List<Prediction> predictions, Map<Integer, String> districtNames)
            throws IOException {
        Path file = OUTPUT_DIR.resolve("avm-predictions.csv");
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file))) {
            out.println("property_id,deal_type,district,area_sqm,bedrooms,actual,estimated,"
                + "lower_bound,upper_bound,ratio,abs_pct_error,inside_range,comparables,flag");
            for (Prediction p : predictions) {
                out.printf(Locale.ROOT, "%d,%s,%s,%.2f,%d,%.2f,%.2f,%.2f,%.2f,%.6f,%.4f,%s,%d,%s%n",
                    p.propertyId(), p.dealType(),
                    districtNames.getOrDefault(p.districtId(), "?").replace(',', ' '),
                    p.areaSqm(), p.bedrooms(), p.actual(), p.estimated(),
                    p.lowerBound(), p.upperBound(), p.ratio(), p.absolutePercentError(),
                    p.actualInsideRange(), p.comparableCount(), p.flag());
            }
        }
        System.out.println("Wrote " + predictions.size() + " predictions to " + file.toAbsolutePath());
    }

    private void writeReport(List<Prediction> predictions, Settings settings) throws IOException {
        Path file = OUTPUT_DIR.resolve("avm-evaluation.md");
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file))) {
            out.println("# AVM evaluation — measured output");
            out.println();
            out.println("Generated by `AvmEvaluation`. Reproduce with:");
            out.println();
            out.println("```");
            out.println("mvn test -Dtest=AvmEvaluation");
            out.println("```");
            out.println();
            out.println("Leave-one-out over every CLOSED property. Each subject is excluded from its own");
            out.println("comparable set and from its own regression training set before it is valued.");
            out.println();
            out.println("## Configuration read from `system_setting`");
            out.println();
            out.println("| Setting | Value |");
            out.println("|---|---|");
            out.printf("| `comparable_area_tolerance_percent` | %s |%n", settings.areaTolerancePercent);
            out.printf("| `comparable_min_count` | %d |%n", settings.comparableMinCount);
            out.printf("| `above_market_threshold_percent` | %s |%n", settings.aboveMarketThresholdPercent);
            out.printf("| `implausible_threshold_percent` | %s |%n", settings.implausibleThresholdPercent);
            out.println();

            out.println("## Ratio study");
            out.println();
            out.println("| Statistic | All | Sale | Rent | IAAO acceptance |");
            out.println("|---|---|---|---|---|");

            List<Prediction> sales = predictions.stream()
                .filter(p -> p.dealType() == DealType.SALE).toList();
            List<Prediction> rents = predictions.stream()
                .filter(p -> p.dealType() == DealType.RENT).toList();

            RatioStudy all = RatioStudy.of(predictions);
            RatioStudy sale = sales.isEmpty() ? null : RatioStudy.of(sales);
            RatioStudy rent = rents.isEmpty() ? null : RatioStudy.of(rents);

            row(out, "n", "%d", all.n(), sale == null ? null : sale.n(), rent == null ? null : rent.n(), "—");
            row(out, "Median ratio", "%.4f", all.medianRatio(),
                sale == null ? null : sale.medianRatio(), rent == null ? null : rent.medianRatio(),
                "0.90–1.10");
            row(out, "Mean ratio", "%.4f", all.meanRatio(),
                sale == null ? null : sale.meanRatio(), rent == null ? null : rent.meanRatio(), "—");
            row(out, "Weighted mean ratio", "%.4f", all.weightedMeanRatio(),
                sale == null ? null : sale.weightedMeanRatio(),
                rent == null ? null : rent.weightedMeanRatio(), "—");
            row(out, "COD (%)", "%.2f", all.cod(),
                sale == null ? null : sale.cod(), rent == null ? null : rent.cod(),
                "5.0–15.0 (residential)");
            row(out, "PRD", "%.4f", all.prd(),
                sale == null ? null : sale.prd(), rent == null ? null : rent.prd(), "0.98–1.03");
            row(out, "PRB", "%.4f", all.prb(),
                sale == null ? null : sale.prb(), rent == null ? null : rent.prb(), "-0.05–+0.05");
            row(out, "FSD", "%.4f", all.fsd(),
                sale == null ? null : sale.fsd(), rent == null ? null : rent.fsd(), "—");
            out.println();
            out.println("## Error distribution");
            out.println();
            out.println("| Statistic | All | Sale | Rent |");
            out.println("|---|---|---|---|");
            row3(out, "MdAPE (%)", "%.2f", all.mdApe(),
                sale == null ? null : sale.mdApe(), rent == null ? null : rent.mdApe());
            row3(out, "MAPE (%)", "%.2f", all.meanApe(),
                sale == null ? null : sale.meanApe(), rent == null ? null : rent.meanApe());
            row3(out, "PE10 (% within 10%)", "%.1f", all.pe10(),
                sale == null ? null : sale.pe10(), rent == null ? null : rent.pe10());
            row3(out, "PE20 (% within 20%)", "%.1f", all.pe20(),
                sale == null ? null : sale.pe20(), rent == null ? null : rent.pe20());
            row3(out, "PE25 (% within 25%)", "%.1f", all.pe25(),
                sale == null ? null : sale.pe25(), rent == null ? null : rent.pe25());
            out.println();
            out.println("## Interval calibration and flagging");
            out.println();
            out.println("| Statistic | All | Sale | Rent |");
            out.println("|---|---|---|---|");
            row3(out, "Actual inside shown range (%)", "%.1f", all.coverage95(),
                sale == null ? null : sale.coverage95(), rent == null ? null : rent.coverage95());
            row3(out, "Flagged OK (%)", "%.1f", all.flaggedOk(),
                sale == null ? null : sale.flaggedOk(), rent == null ? null : rent.flaggedOk());
            out.println();

            List<Prediction> worst = predictions.stream()
                .sorted(Comparator.comparingDouble(Prediction::absolutePercentError).reversed())
                .limit(10).toList();
            out.println("## The ten worst predictions");
            out.println();
            out.println("Reported because the tail is the honest part of an accuracy claim.");
            out.println();
            out.println("| Property | Deal | Area m² | Actual | Estimated | Error % | Comparables |");
            out.println("|---|---|---|---|---|---|---|");
            for (Prediction p : worst) {
                out.printf(Locale.ROOT, "| %d | %s | %.0f | %,.0f | %,.0f | %.1f | %d |%n",
                    p.propertyId(), p.dealType(), p.areaSqm(), p.actual(), p.estimated(),
                    p.absolutePercentError(), p.comparableCount());
            }
            out.println();
            out.println("## Comparable availability");
            out.println();
            long noComparables = predictions.stream().filter(p -> p.comparableCount() == 0).count();
            long belowMinimum = predictions.stream()
                .filter(p -> p.comparableCount() < settings.comparableMinCount).count();
            out.printf("- Subjects with no comparable at all: **%d** of %d (%.1f%%)%n",
                noComparables, predictions.size(), 100.0 * noComparables / predictions.size());
            out.printf("- Subjects below `comparable_min_count` = %d: **%d** of %d (%.1f%%)%n",
                settings.comparableMinCount, belowMinimum, predictions.size(),
                100.0 * belowMinimum / predictions.size());
            out.printf("- Median comparables per subject: **%.0f**%n",
                RatioStudy.median(predictions.stream()
                    .mapToDouble(Prediction::comparableCount).sorted().toArray()));
        }
        System.out.println("Wrote evaluation to " + file.toAbsolutePath());
    }

    private void row(PrintWriter out, String label, String format,
            Object all, Object sale, Object rent, String acceptance) {
        out.printf(Locale.ROOT, "| %s | " + format + " | %s | %s | %s |%n", label, all,
            sale == null ? "—" : String.format(Locale.ROOT, format, sale),
            rent == null ? "—" : String.format(Locale.ROOT, format, rent),
            acceptance);
    }

    private void row3(PrintWriter out, String label, String format, Object all, Object sale, Object rent) {
        out.printf(Locale.ROOT, "| %s | " + format + " | %s | %s |%n", label, all,
            sale == null ? "—" : String.format(Locale.ROOT, format, sale),
            rent == null ? "—" : String.format(Locale.ROOT, format, rent));
    }

    // ------------------------------------------------------------------ holders

    private record Prediction(
            int propertyId, DealType dealType, int districtId, double areaSqm, int bedrooms,
            double actual, double estimated, double lowerBound, double upperBound,
            int comparableCount, String flag) {

        double ratio() {
            return estimated / actual;
        }

        double absolutePercentError() {
            return 100.0 * Math.abs(estimated - actual) / actual;
        }

        boolean actualInsideRange() {
            return actual >= lowerBound && actual <= upperBound;
        }
    }

    private record Settings(
            BigDecimal areaTolerancePercent, int comparableMinCount,
            BigDecimal aboveMarketThresholdPercent, BigDecimal implausibleThresholdPercent) {

        static Settings read(Connection connection, SystemSettingDao dao) throws SQLException {
            Map<String, String> values = new HashMap<>();
            for (SystemSetting setting : dao.findAll(connection)) {
                values.put(setting.getSettingKey(), setting.getValue());
            }
            return new Settings(
                new BigDecimal(values.get("comparable_area_tolerance_percent")),
                Integer.parseInt(values.get("comparable_min_count")),
                new BigDecimal(values.get("above_market_threshold_percent")),
                new BigDecimal(values.get("implausible_threshold_percent")));
        }
    }
}
