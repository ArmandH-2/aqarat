package co.syntropyhq.aqarat.valuation;

import co.syntropyhq.aqarat.model.ValuationFlag;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Everything PriceEstimator produces for one property: an estimate, a range
 * that brackets it, and enough detail that an agent can see why the number
 * is what it is instead of just trusting it.
 *
 * Built once by PriceEstimator and handed back whole, so it carries no
 * setters - nothing downstream should be able to edit an estimate after the
 * fact.
 */
public class ValuationResult {

    private final BigDecimal estimatedValue;
    private final BigDecimal lowerBound;
    private final BigDecimal upperBound;
    private final BigDecimal pricePerSqm;
    private final ValuationFlag flag;

    // Factor name -> its dollar or percentage contribution. Kept as a plain
    // map rather than a class per factor: the set of factors is display
    // detail, not a type worth modelling.
    private final Map<String, BigDecimal> factorContributions;

    private final List<ComparableProperty> comparables;

    public ValuationResult(BigDecimal estimatedValue, BigDecimal lowerBound, BigDecimal upperBound,
            BigDecimal pricePerSqm, ValuationFlag flag, Map<String, BigDecimal> factorContributions,
            List<ComparableProperty> comparables) {
        this.estimatedValue = estimatedValue;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.pricePerSqm = pricePerSqm;
        this.flag = flag;
        this.factorContributions = factorContributions;
        this.comparables = comparables;
    }

    public BigDecimal getEstimatedValue() {
        return estimatedValue;
    }

    public BigDecimal getLowerBound() {
        return lowerBound;
    }

    public BigDecimal getUpperBound() {
        return upperBound;
    }

    public BigDecimal getPricePerSqm() {
        return pricePerSqm;
    }

    public ValuationFlag getFlag() {
        return flag;
    }

    public Map<String, BigDecimal> getFactorContributions() {
        return factorContributions;
    }

    public List<ComparableProperty> getComparables() {
        return comparables;
    }
}
