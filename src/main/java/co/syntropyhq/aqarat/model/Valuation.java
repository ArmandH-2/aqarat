package co.syntropyhq.aqarat.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Valuation {

    private int id;
    private int propertyId;
    private BigDecimal estimatedValue;
    private BigDecimal lowerBound;
    private BigDecimal upperBound;
    private BigDecimal pricePerSqm;
    private ValuationFlag flag;
    private String breakdown;
    private String comparables;
    private String modelVersion;
    private LocalDateTime createdAt;

    public Valuation() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(int propertyId) {
        this.propertyId = propertyId;
    }

    public BigDecimal getEstimatedValue() {
        return estimatedValue;
    }

    public void setEstimatedValue(BigDecimal estimatedValue) {
        this.estimatedValue = estimatedValue;
    }

    public BigDecimal getLowerBound() {
        return lowerBound;
    }

    public void setLowerBound(BigDecimal lowerBound) {
        this.lowerBound = lowerBound;
    }

    public BigDecimal getUpperBound() {
        return upperBound;
    }

    public void setUpperBound(BigDecimal upperBound) {
        this.upperBound = upperBound;
    }

    public BigDecimal getPricePerSqm() {
        return pricePerSqm;
    }

    public void setPricePerSqm(BigDecimal pricePerSqm) {
        this.pricePerSqm = pricePerSqm;
    }

    public ValuationFlag getFlag() {
        return flag;
    }

    public void setFlag(ValuationFlag flag) {
        this.flag = flag;
    }

    public String getBreakdown() {
        return breakdown;
    }

    public void setBreakdown(String breakdown) {
        this.breakdown = breakdown;
    }

    public String getComparables() {
        return comparables;
    }

    public void setComparables(String comparables) {
        this.comparables = comparables;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
