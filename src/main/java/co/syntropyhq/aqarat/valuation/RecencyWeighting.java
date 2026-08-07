package co.syntropyhq.aqarat.valuation;

import co.syntropyhq.aqarat.model.Property;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

// Recency weight for one property in a comparables or regression slice: an
// exponential decay measured against the newest closedAt in that same
// slice, never the wall clock, so PriceEstimator stays pure and testable
// (DESIGN.md section 7 - a test must not depend on today's date). A row
// with no closedAt cannot be assumed recent, so it is given the weight of
// the oldest dated row in the slice rather than being dropped.
final class RecencyWeighting {

    // The average length of a month, used instead of ChronoUnit.MONTHS so
    // age grows smoothly rather than jumping a whole unit at each calendar
    // boundary.
    private static final double AVERAGE_DAYS_PER_MONTH = 30.44;

    private RecencyWeighting() {
    }

    static double[] weightsFor(List<Property> properties, double halfLifeMonths) {
        LocalDateTime mostRecent = mostRecentClosedAt(properties);
        double oldestAgeMonths = mostRecent == null ? 0.0 : oldestAgeMonths(properties, mostRecent);

        double[] weights = new double[properties.size()];
        for (int i = 0; i < properties.size(); i++) {
            weights[i] = weightFor(properties.get(i).getClosedAt(), mostRecent, oldestAgeMonths, halfLifeMonths);
        }
        return weights;
    }

    private static double weightFor(LocalDateTime closedAt, LocalDateTime mostRecent,
            double oldestAgeMonths, double halfLifeMonths) {
        if (mostRecent == null) {
            return 1.0; // nothing in the slice carries a date - no basis to prefer any row
        }
        double ageMonths = closedAt == null ? oldestAgeMonths : ageInMonths(closedAt, mostRecent);
        return Math.pow(0.5, ageMonths / halfLifeMonths);
    }

    private static double oldestAgeMonths(List<Property> properties, LocalDateTime mostRecent) {
        double oldest = 0.0;
        for (Property property : properties) {
            if (property.getClosedAt() != null) {
                oldest = Math.max(oldest, ageInMonths(property.getClosedAt(), mostRecent));
            }
        }
        return oldest;
    }

    private static LocalDateTime mostRecentClosedAt(List<Property> properties) {
        LocalDateTime latest = null;
        for (Property property : properties) {
            LocalDateTime closedAt = property.getClosedAt();
            if (closedAt != null && (latest == null || closedAt.isAfter(latest))) {
                latest = closedAt;
            }
        }
        return latest;
    }

    private static double ageInMonths(LocalDateTime closedAt, LocalDateTime mostRecent) {
        double days = ChronoUnit.DAYS.between(closedAt, mostRecent);
        return Math.max(0.0, days / AVERAGE_DAYS_PER_MONTH);
    }
}
