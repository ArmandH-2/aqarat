package co.syntropyhq.aqarat.valuation;

import co.syntropyhq.aqarat.model.ValuationFlag;

import java.math.BigDecimal;
import java.util.Collections;
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

    // Feature name -> its fitted weight, so a saved estimate can be
    // explained later without re-running the regression. Named rather than
    // a plain list for the same reason factorContributions is a map: an
    // agent reading this wants "yearBuilt", not "coefficient 4". Empty
    // when no regression ran (comparables-only or district-average
    // estimates).
    private final Map<String, BigDecimal> regressionCoefficients;

    // Convenience overload for callers rebuilding a result from a saved
    // valuation row that predates this field (ValuationService reads old
    // rows back through this constructor).
    public ValuationResult(BigDecimal estimatedValue, BigDecimal lowerBound, BigDecimal upperBound,
            BigDecimal pricePerSqm, ValuationFlag flag, Map<String, BigDecimal> factorContributions,
            List<ComparableProperty> comparables) {
        this(estimatedValue, lowerBound, upperBound, pricePerSqm, flag, factorContributions, comparables,
            Collections.emptyMap());
    }

    public ValuationResult(BigDecimal estimatedValue, BigDecimal lowerBound, BigDecimal upperBound,
            BigDecimal pricePerSqm, ValuationFlag flag, Map<String, BigDecimal> factorContributions,
            List<ComparableProperty> comparables, Map<String, BigDecimal> regressionCoefficients) {
        this.estimatedValue = estimatedValue;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.pricePerSqm = pricePerSqm;
        this.flag = flag;
        this.factorContributions = factorContributions;
        this.comparables = comparables;
        this.regressionCoefficients = regressionCoefficients;
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

    public Map<String, BigDecimal> getRegressionCoefficients() {
        return regressionCoefficients;
    }
}
