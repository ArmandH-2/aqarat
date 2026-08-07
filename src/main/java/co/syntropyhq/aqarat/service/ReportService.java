package co.syntropyhq.aqarat.service;

import co.syntropyhq.aqarat.dao.OverduePayment;
import co.syntropyhq.aqarat.dao.ReportDao;
import co.syntropyhq.aqarat.dao.RevenueByPeriod;
import co.syntropyhq.aqarat.dao.SystemSettingDao;
import co.syntropyhq.aqarat.dao.TimeOnMarketRow;
import co.syntropyhq.aqarat.model.SystemSetting;
import co.syntropyhq.aqarat.util.Db;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

// Reads settings, calls ReportDao, and turns its raw rows into the figures
// an admin actually asked for (DESIGN.md section 5). All SQL lives in
// ReportDao - this class does arithmetic and nothing else, and opens no
// transaction because none of the three reports write anything.
public class ReportService {

    private final ReportDao reportDao;
    private final SystemSettingDao systemSettingDao;

    public ReportService(ReportDao reportDao, SystemSettingDao systemSettingDao) {
        this.reportDao = reportDao;
        this.systemSettingDao = systemSettingDao;
    }

    public List<RevenueByPeriod> revenueByPeriod(LocalDate from, LocalDate to) throws SQLException {
        try (Connection connection = Db.get()) {
            return reportDao.revenueByPeriod(connection, from, to);
        }
    }

    /**
     * DESIGN.md section 6: a schedule row is overdue once due_date plus the
     * grace period in system_setting has passed and it is still short. The
     * grace period is read here, never hardcoded (CLAUDE.md).
     */
    public List<OverduePayment> overduePayments() throws SQLException {
        return overduePayments(null);
    }

    /**
     * The whole agency's overdue rows, or one agent's own - the dashboard's
     * "overdue" figure measures the agent's portfolio, not the company's
     * (ReportsController is where the company-wide view lives). A null
     * agentId means no scoping.
     */
    public List<OverduePayment> overduePayments(Integer agentId) throws SQLException {
        int graceDays = paymentGraceDays();
        LocalDate today = LocalDate.now();
        List<OverduePayment> rows;
        try (Connection connection = Db.get()) {
            rows = reportDao.overduePayments(connection, graceDays, today, agentId);
        }
        for (OverduePayment row : rows) {
            row.setDaysOverdue((int) ChronoUnit.DAYS.between(row.getDueDate(), today));
        }
        return rows;
    }

    private int paymentGraceDays() throws SQLException {
        SystemSetting setting = systemSettingDao.findByKey("payment_grace_days");
        if (setting == null) {
            throw new IllegalStateException("Missing system setting: payment_grace_days");
        }
        return Integer.parseInt(setting.getValue());
    }

    /**
     * DESIGN.md section 7: the premium is how far the asking price sat above
     * the estimate, as a percentage of the estimate - the report the
     * valuation idea is judged by.
     */
    public List<TimeOnMarketRow> timeOnMarket() throws SQLException {
        List<TimeOnMarketRow> rows;
        try (Connection connection = Db.get()) {
            rows = reportDao.timeOnMarket(connection);
        }
        for (TimeOnMarketRow row : rows) {
            row.setDaysOnMarket(
                (int) ChronoUnit.DAYS.between(row.getPublishedAt(), row.getClosedAt()));
            row.setPremiumPercent(premiumPercent(row.getAskingPrice(), row.getEstimatedValue()));
        }
        return rows;
    }

    private BigDecimal premiumPercent(BigDecimal askingPrice, BigDecimal estimatedValue) {
        return askingPrice.subtract(estimatedValue)
            .divide(estimatedValue, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(1, RoundingMode.HALF_UP);
    }
}
