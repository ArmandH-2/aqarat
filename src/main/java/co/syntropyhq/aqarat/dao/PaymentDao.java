package co.syntropyhq.aqarat.dao;

import co.syntropyhq.aqarat.model.Payment;
import co.syntropyhq.aqarat.model.PaymentMethod;
import co.syntropyhq.aqarat.model.PaymentStatus;
import co.syntropyhq.aqarat.util.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PaymentDao {

    private static final String COLUMNS = """
        id, schedule_id, reservation_id, amount, paid_at, method, reference, proof_path,
        declared_by, confirmed_by, status, created_at""";

    // confirmed_by starts NULL: nobody has confirmed a payment the moment it
    // is declared, agent-recorded or not - updateStatus is what sets it.
    public int insert(Connection connection, Payment payment) throws SQLException {
        String sql = """
            INSERT INTO payment (schedule_id, reservation_id, amount, paid_at, method, reference,
                proof_path, declared_by, confirmed_by, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement statement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setObject(1, payment.getScheduleId());
            statement.setObject(2, payment.getReservationId());
            statement.setBigDecimal(3, payment.getAmount());
            statement.setObject(4, payment.getPaidAt());
            statement.setString(5, payment.getMethod().name());
            statement.setString(6, payment.getReference());
            statement.setString(7, payment.getProofPath());
            statement.setInt(8, payment.getDeclaredBy());
            statement.setObject(9, payment.getConfirmedBy());
            statement.setString(10, payment.getStatus().name());
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                generatedKeys.next();
                return generatedKeys.getInt(1);
            }
        }
    }

    public Payment findById(Connection connection, int id) throws SQLException {
        String sql = ("""
            SELECT %s
            FROM payment
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

    public Payment findById(int id) throws SQLException {
        try (Connection connection = Db.get()) {
            return findById(connection, id);
        }
    }

    public List<Payment> findBySchedule(Connection connection, int scheduleId) throws SQLException {
        String sql = ("""
            SELECT %s
            FROM payment
            WHERE schedule_id = ?
            ORDER BY created_at DESC
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, scheduleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapRows(resultSet);
            }
        }
    }

    // Joins through payment_schedule, since payment itself carries no
    // contract_id. A payment settling a reservation deposit has no schedule
    // row and so never appears here - it belongs to the reservation, not to
    // a contract, and that is by design (ck_payment_target).
    public List<Payment> findByContract(Connection connection, int contractId) throws SQLException {
        String sql = """
            SELECT p.id, p.schedule_id, p.reservation_id, p.amount, p.paid_at, p.method,
                p.reference, p.proof_path, p.declared_by, p.confirmed_by, p.status, p.created_at
            FROM payment p
            JOIN payment_schedule ps ON ps.id = p.schedule_id
            WHERE ps.contract_id = ?
            ORDER BY p.created_at DESC
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, contractId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapRows(resultSet);
            }
        }
    }

    public List<Payment> findDeclared(Connection connection) throws SQLException {
        String sql = ("""
            SELECT %s
            FROM payment
            WHERE status = 'DECLARED'
            ORDER BY created_at
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            return mapRows(resultSet);
        }
    }

    public void updateStatus(Connection connection, int id, PaymentStatus status, Integer confirmedBy)
            throws SQLException {
        String sql = "UPDATE payment SET status = ?, confirmed_by = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setObject(2, confirmedBy);
            statement.setInt(3, id);
            statement.executeUpdate();
        }
    }

    private List<Payment> mapRows(ResultSet resultSet) throws SQLException {
        List<Payment> results = new ArrayList<>();
        while (resultSet.next()) {
            results.add(mapRow(resultSet));
        }
        return results;
    }

    private Payment mapRow(ResultSet resultSet) throws SQLException {
        Payment payment = new Payment();
        payment.setId(resultSet.getInt("id"));
        payment.setScheduleId(resultSet.getObject("schedule_id", Integer.class));
        payment.setReservationId(resultSet.getObject("reservation_id", Integer.class));
        payment.setAmount(resultSet.getBigDecimal("amount"));
        payment.setPaidAt(resultSet.getObject("paid_at", LocalDateTime.class));
        payment.setMethod(PaymentMethod.valueOf(resultSet.getString("method")));
        payment.setReference(resultSet.getString("reference"));
        payment.setProofPath(resultSet.getString("proof_path"));
        payment.setDeclaredBy(resultSet.getInt("declared_by"));
        payment.setConfirmedBy(resultSet.getObject("confirmed_by", Integer.class));
        payment.setStatus(PaymentStatus.valueOf(resultSet.getString("status")));
        payment.setCreatedAt(resultSet.getObject("created_at", LocalDateTime.class));
        return payment;
    }
}
