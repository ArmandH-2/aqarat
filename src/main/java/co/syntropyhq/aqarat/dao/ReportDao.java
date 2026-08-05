package co.syntropyhq.aqarat.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Three read-only reports (docs/DESIGN.md section 5). Every query is
// aggregate or a projection, so ReportService never opens a transaction -
// it just calls in and reads (CLAUDE.md: read-only aggregates need none).
public class ReportDao {

    // Grouping by month is a reporting concern, not a business rule, so the
    // FORMAT() grouping key lives in the query itself rather than in Java.
    private static final String REVENUE_SQL = """
        SELECT FORMAT(activated_at, 'yyyy-MM') AS period,
               COUNT(*) AS contract_count,
               SUM(total_amount) AS total_value,
               SUM(commission_amount) AS total_commission
        FROM contract
        WHERE activated_at IS NOT NULL
          AND CAST(activated_at AS DATE) BETWEEN ? AND ?
        GROUP BY FORMAT(activated_at, 'yyyy-MM')
        ORDER BY period
        """;

    public List<RevenueByPeriod> revenueByPeriod(Connection connection, LocalDate from, LocalDate to)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(REVENUE_SQL)) {
            statement.setObject(1, from);
            statement.setObject(2, to);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<RevenueByPeriod> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapRevenueRow(resultSet));
                }
                return results;
            }
        }
    }

    private RevenueByPeriod mapRevenueRow(ResultSet resultSet) throws SQLException {
        RevenueByPeriod row = new RevenueByPeriod();
        row.setPeriod(resultSet.getString("period"));
        row.setContractCount(resultSet.getInt("contract_count"));
        row.setTotalValue(resultSet.getBigDecimal("total_value"));
        row.setTotalCommission(resultSet.getBigDecimal("total_commission"));
        return row;
    }

    // "Overdue" per DESIGN.md section 6: due_date plus the grace period has
    // passed, and the row is still short. graceDays and today are passed in
    // rather than computed here - ReportDao does not know system_setting or
    // the clock, it only compares the values it is given.
    private static final String OVERDUE_SQL = """
        SELECT c.id AS contract_id, pr.title AS property_title, u.full_name AS client_name,
               ps.installment_no, ps.due_date, ps.amount_due, ps.amount_paid
        FROM payment_schedule ps
        JOIN contract c ON c.id = ps.contract_id
        JOIN property pr ON pr.id = c.property_id
        JOIN app_user u ON u.id = c.client_id
        WHERE ps.amount_paid < ps.amount_due
          AND DATEADD(day, ?, ps.due_date) < ?
        ORDER BY ps.due_date
        """;

    public List<OverduePayment> overduePayments(Connection connection, int graceDays, LocalDate today)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(OVERDUE_SQL)) {
            statement.setInt(1, graceDays);
            statement.setObject(2, today);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<OverduePayment> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapOverdueRow(resultSet));
                }
                return results;
            }
        }
    }

    private OverduePayment mapOverdueRow(ResultSet resultSet) throws SQLException {
        OverduePayment row = new OverduePayment();
        row.setContractId(resultSet.getInt("contract_id"));
        row.setPropertyTitle(resultSet.getString("property_title"));
        row.setClientName(resultSet.getString("client_name"));
        row.setInstallmentNo(resultSet.getInt("installment_no"));
        row.setDueDate(resultSet.getObject("due_date", LocalDate.class));
        row.setAmountDue(resultSet.getBigDecimal("amount_due"));
        row.setAmountPaid(resultSet.getBigDecimal("amount_paid"));
        return row;
    }

    // Each property joins its most recent valuation only - a property can be
    // revalued more than once, and only the latest estimate is what the
    // owner's asking price should be judged against (matches
    // ValuationDao.findLatestByProperty).
    private static final String TIME_ON_MARKET_SQL = """
        SELECT p.id AS property_id, p.title, p.published_at, p.closed_at,
               p.asking_price, v.estimated_value
        FROM property p
        JOIN valuation v ON v.id = (
            SELECT TOP (1) v2.id
            FROM valuation v2
            WHERE v2.property_id = p.id
            ORDER BY v2.created_at DESC
        )
        WHERE p.status = 'CLOSED'
          AND p.published_at IS NOT NULL
          AND p.closed_at IS NOT NULL
        ORDER BY p.closed_at DESC
        """;

    public List<TimeOnMarketRow> timeOnMarket(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(TIME_ON_MARKET_SQL);
                ResultSet resultSet = statement.executeQuery()) {
            List<TimeOnMarketRow> results = new ArrayList<>();
            while (resultSet.next()) {
                results.add(mapTimeOnMarketRow(resultSet));
            }
            return results;
        }
    }

    private TimeOnMarketRow mapTimeOnMarketRow(ResultSet resultSet) throws SQLException {
        TimeOnMarketRow row = new TimeOnMarketRow();
        row.setPropertyId(resultSet.getInt("property_id"));
        row.setTitle(resultSet.getString("title"));
        row.setPublishedAt(resultSet.getObject("published_at", LocalDateTime.class));
        row.setClosedAt(resultSet.getObject("closed_at", LocalDateTime.class));
        row.setAskingPrice(resultSet.getBigDecimal("asking_price"));
        row.setEstimatedValue(resultSet.getBigDecimal("estimated_value"));
        return row;
    }
}
