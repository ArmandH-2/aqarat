package co.syntropyhq.aqarat.dao;

import co.syntropyhq.aqarat.model.Reservation;
import co.syntropyhq.aqarat.model.ReservationStatus;
import co.syntropyhq.aqarat.util.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ReservationDao {

    private static final String COLUMNS = """
        id, property_id, client_id, agent_id, deposit_amount, reserved_at, expires_at, status""";

    public int insert(Connection connection, Reservation reservation) throws SQLException {
        String sql = """
            INSERT INTO reservation (property_id, client_id, agent_id, deposit_amount,
                reserved_at, expires_at, status)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement statement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, reservation.getPropertyId());
            statement.setInt(2, reservation.getClientId());
            statement.setObject(3, reservation.getAgentId());
            statement.setBigDecimal(4, reservation.getDepositAmount());
            statement.setObject(5, reservation.getReservedAt());
            statement.setObject(6, reservation.getExpiresAt());
            statement.setString(7, reservation.getStatus().name());
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                generatedKeys.next();
                return generatedKeys.getInt(1);
            }
        }
    }

    public Reservation findById(Connection connection, int id) throws SQLException {
        String sql = ("""
            SELECT %s
            FROM reservation
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

    public Reservation findById(int id) throws SQLException {
        try (Connection connection = Db.get()) {
            return findById(connection, id);
        }
    }

    public List<Reservation> findByProperty(Connection connection, int propertyId)
            throws SQLException {
        String sql = ("""
            SELECT %s
            FROM reservation
            WHERE property_id = ?
            ORDER BY reserved_at DESC
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, propertyId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Reservation> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
                return results;
            }
        }
    }

    public List<Reservation> findByProperty(int propertyId) throws SQLException {
        try (Connection connection = Db.get()) {
            return findByProperty(connection, propertyId);
        }
    }

    // A client sees only their own reservations - client_id is bound into the
    // WHERE clause here, not applied by filtering a full result set afterwards
    // (DESIGN.md section 8).
    public List<Reservation> findByClient(Connection connection, int clientId)
            throws SQLException {
        String sql = ("""
            SELECT %s
            FROM reservation
            WHERE client_id = ?
            ORDER BY reserved_at DESC
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, clientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Reservation> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
                return results;
            }
        }
    }

    public List<Reservation> findByClient(int clientId) throws SQLException {
        try (Connection connection = Db.get()) {
            return findByClient(connection, clientId);
        }
    }

    // Matches the filtered unique index ux_reservation_active: at most one row
    // can ever come back.
    public Reservation findActiveForProperty(Connection connection, int propertyId)
            throws SQLException {
        String sql = ("""
            SELECT %s
            FROM reservation
            WHERE property_id = ? AND status = 'ACTIVE'
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, propertyId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapRow(resultSet);
                }
                return null;
            }
        }
    }

    public void updateStatus(Connection connection, int id, ReservationStatus status)
            throws SQLException {
        String sql = "UPDATE reservation SET status = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setInt(2, id);
            statement.executeUpdate();
        }
    }

    private Reservation mapRow(ResultSet resultSet) throws SQLException {
        Reservation reservation = new Reservation();
        reservation.setId(resultSet.getInt("id"));
        reservation.setPropertyId(resultSet.getInt("property_id"));
        reservation.setClientId(resultSet.getInt("client_id"));
        reservation.setAgentId(resultSet.getObject("agent_id", Integer.class));
        reservation.setDepositAmount(resultSet.getBigDecimal("deposit_amount"));
        reservation.setReservedAt(resultSet.getObject("reserved_at", LocalDateTime.class));
        reservation.setExpiresAt(resultSet.getObject("expires_at", LocalDateTime.class));
        reservation.setStatus(ReservationStatus.valueOf(resultSet.getString("status")));
        return reservation;
    }
}
