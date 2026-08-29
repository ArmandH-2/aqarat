package co.syntropyhq.aqarat.ai;

import co.syntropyhq.aqarat.dao.PropertySearch;
import co.syntropyhq.aqarat.model.Property;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Ranker {

    private Ranker() {
    }

    public static List<Property> rank(List<Property> properties, PropertySearch filters, int limit) {
        if (properties == null || properties.isEmpty() || limit <= 0) {
            return List.of();
        }

        List<Property> sorted = new ArrayList<>(properties);
        sorted.sort(Comparator
            .comparingDouble((Property p) -> calculatePenalty(p, filters))
            .thenComparingInt(Property::getId));

        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    public static List<Property> rank(List<Property> properties, PropertySearch filters) {
        return rank(properties, filters, 5);
    }

    private static double calculatePenalty(Property property, PropertySearch filters) {
        if (property == null) {
            return Double.MAX_VALUE;
        }
        if (filters == null) {
            return 0.0;
        }

        double penalty = 0.0;

        // 1. Budget fit: distance from stated budget
        BigDecimal targetBudget = getTargetBudget(filters);
        if (targetBudget != null && targetBudget.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal price = property.getAskingPrice();
            if (price != null) {
                double diff = Math.abs(price.subtract(targetBudget).doubleValue());
                double relativeDiff = diff / targetBudget.doubleValue();
                penalty += relativeDiff * 100.0;
            } else {
                penalty += 100.0;
            }
        }

        // 2. Amenity penalties for requested amenities not met
        if (Boolean.TRUE.equals(filters.getHasParking()) && !property.isHasParking()) {
            penalty += 1000.0;
        }
        if (Boolean.TRUE.equals(filters.getHasElevator()) && !property.isHasElevator()) {
            penalty += 1000.0;
        }
        if (Boolean.TRUE.equals(filters.getHasBalcony()) && !property.isHasBalcony()) {
            penalty += 1000.0;
        }
        if (Boolean.TRUE.equals(filters.getIsFurnished()) && !property.isFurnished()) {
            penalty += 1000.0;
        }

        // 3. Bedrooms / Bathrooms / Area differences
        if (filters.getBedrooms() != null) {
            penalty += Math.abs(property.getBedrooms() - filters.getBedrooms()) * 50.0;
        }
        if (filters.getBathrooms() != null) {
            penalty += Math.abs(property.getBathrooms() - filters.getBathrooms()) * 30.0;
        }
        if (filters.getMinArea() != null && property.getAreaSqm() != null
                && filters.getMinArea().compareTo(BigDecimal.ZERO) > 0) {
            if (property.getAreaSqm().compareTo(filters.getMinArea()) < 0) {
                double deficit = filters.getMinArea().subtract(property.getAreaSqm()).doubleValue();
                penalty += (deficit / filters.getMinArea().doubleValue()) * 100.0;
            }
        }

        return penalty;
    }

    private static BigDecimal getTargetBudget(PropertySearch filters) {
        if (filters.getMaxPrice() != null && filters.getMinPrice() != null) {
            return filters.getMinPrice().add(filters.getMaxPrice())
                .divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);
        }
        if (filters.getMaxPrice() != null) {
            return filters.getMaxPrice();
        }
        if (filters.getMinPrice() != null) {
            return filters.getMinPrice();
        }
        return null;
    }
}
