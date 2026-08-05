package co.syntropyhq.aqarat.dao;

import java.math.BigDecimal;

// One row of ReportDao.revenueByPeriod: a calendar month and the contracts
// activated in it. A plain holder, same shape as PropertySearch - it lives
// here rather than in model/ because it is a projection ReportDao produces,
// not a table row.
public class RevenueByPeriod {

    private String period;
    private int contractCount;
    private BigDecimal totalValue;
    private BigDecimal totalCommission;

    public RevenueByPeriod() {
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public int getContractCount() {
        return contractCount;
    }

    public void setContractCount(int contractCount) {
        this.contractCount = contractCount;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    public BigDecimal getTotalCommission() {
        return totalCommission;
    }

    public void setTotalCommission(BigDecimal totalCommission) {
        this.totalCommission = totalCommission;
    }
}
