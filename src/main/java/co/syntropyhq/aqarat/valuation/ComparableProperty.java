package co.syntropyhq.aqarat.valuation;

import co.syntropyhq.aqarat.model.Property;

import java.math.BigDecimal;

/**
 * One property PriceEstimator judged comparable to the subject, paired with
 * how similar it judged it to be.
 */
public class ComparableProperty {

    private final Property property;

    // 0 (nothing alike) to 1 (same area as the subject). See
    // PriceEstimator.similarityScore for how it is derived.
    private final BigDecimal similarityScore;

    public ComparableProperty(Property property, BigDecimal similarityScore) {
        this.property = property;
        this.similarityScore = similarityScore;
    }

    public Property getProperty() {
        return property;
    }

    public BigDecimal getSimilarityScore() {
        return similarityScore;
    }
}
