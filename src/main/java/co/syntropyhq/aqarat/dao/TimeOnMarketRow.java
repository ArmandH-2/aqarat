package co.syntropyhq.aqarat.dao;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// One row of ReportDao.timeOnMarket. ReportDao fills the raw columns;
// ReportService derives daysOnMarket and premiumPercent from them, since
// both are business arithmetic, not a query concern (CLAUDE.md layer rules).
public class TimeOnMarketRow {

    private int propertyId;
    private String title;
    private LocalDateTime publishedAt;
    private LocalDateTime closedAt;
    private BigDecimal askingPrice;
    private BigDecimal estimatedValue;
    private int daysOnMarket;
    private BigDecimal premiumPercent;

    public TimeOnMarketRow() {
    }

    public int getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(int propertyId) {
        this.propertyId = propertyId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public BigDecimal getAskingPrice() {
        return askingPrice;
    }

    public void setAskingPrice(BigDecimal askingPrice) {
        this.askingPrice = askingPrice;
    }

    public BigDecimal getEstimatedValue() {
        return estimatedValue;
    }

    public void setEstimatedValue(BigDecimal estimatedValue) {
        this.estimatedValue = estimatedValue;
    }

    public int getDaysOnMarket() {
        return daysOnMarket;
    }

    public void setDaysOnMarket(int daysOnMarket) {
        this.daysOnMarket = daysOnMarket;
    }

    public BigDecimal getPremiumPercent() {
        return premiumPercent;
    }

    public void setPremiumPercent(BigDecimal premiumPercent) {
        this.premiumPercent = premiumPercent;
    }
}
