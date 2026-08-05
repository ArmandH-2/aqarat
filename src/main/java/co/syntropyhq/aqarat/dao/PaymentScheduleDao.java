package co.syntropyhq.aqarat.dao;

import co.syntropyhq.aqarat.model.PaymentSchedule;
import co.syntropyhq.aqarat.model.ScheduleStatus;
import co.syntropyhq.aqarat.util.Db;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PaymentScheduleDao {

    private static final String COLUMNS =
        "id, contract_id, installment_no, due_date, amount_due, amount_paid, status";

    private static final String INSERT_SQL = """
        INSERT INTO payment_schedule (contract_id, installment_no, due_date, amount_due,
            amount_paid, status)
        VALUES (?, ?, ?, ?, ?, ?)
        """;

    public int insert(Connection connection, PaymentSchedule schedule) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            bindRow(statement, schedule);
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                generatedKeys.next();
                return generatedKeys.getInt(1);
            }
        }
    }

    // Activation writes a whole schedule in one go (CLAUDE.md scope): one
    // PreparedStatement, re-bound per row, batched on the caller's connection.
    public void insertAll(Connection connection, List<PaymentSchedule> schedule) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            for (PaymentSchedule row : schedule) {
                bindRow(statement, row);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void bindRow(PreparedStatement statement, PaymentSchedule schedule) throws SQLException {
        statement.setInt(1, schedule.getContractId());
        statement.setInt(2, schedule.getInstallmentNo());
        statement.setObject(3, schedule.getDueDate());
        statement.setBigDecimal(4, schedule.getAmountDue());
        statement.setBigDecimal(5, schedule.getAmountPaid());
        statement.setString(6, schedule.getStatus().name());
    }

    public List<PaymentSchedule> findByContract(Connection connection, int contractId)
            throws SQLException {
        String sql = ("""
            SELECT %s
            FROM payment_schedule
            WHERE contract_id = ?
            ORDER BY installment_no
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, contractId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PaymentSchedule> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
                return results;
            }
        }
    }

    public PaymentSchedule findById(Connection connection, int id) throws SQLException {
        String sql = ("""
            SELECT %s
            FROM payment_schedule
            WHERE id = ?
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapRow(resultSet);
                }
                return null;
            }
        }
    }

    public PaymentSchedule findById(int id) throws SQLException {
        try (Connection connection = Db.get()) {
            return findById(connection, id);
        }
    }

    public void updateAmountPaid(Connection connection, int id, BigDecimal amountPaid)
            throws SQLException {
        String sql = "UPDATE payment_schedule SET amount_paid = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, amountPaid);
            statement.setInt(2, id);
            statement.executeUpdate();
        }
    }

    public void updateStatus(Connection connection, int id, ScheduleStatus status)
            throws SQLException {
        String sql = "UPDATE payment_schedule SET status = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setInt(2, id);
            statement.executeUpdate();
        }
    }

    private PaymentSchedule mapRow(ResultSet resultSet) throws SQLException {
        PaymentSchedule schedule = new PaymentSchedule();
        schedule.setId(resultSet.getInt("id"));
        schedule.setContractId(resultSet.getInt("contract_id"));
        schedule.setInstallmentNo(resultSet.getInt("installment_no"));
        schedule.setDueDate(resultSet.getObject("due_date", LocalDate.class));
        schedule.setAmountDue(resultSet.getBigDecimal("amount_due"));
        schedule.setAmountPaid(resultSet.getBigDecimal("amount_paid"));
        schedule.setStatus(ScheduleStatus.valueOf(resultSet.getString("status")));
        return schedule;
    }
}
