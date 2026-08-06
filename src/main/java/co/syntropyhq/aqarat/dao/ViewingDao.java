package co.syntropyhq.aqarat.dao;

import co.syntropyhq.aqarat.model.Viewing;
import co.syntropyhq.aqarat.model.ViewingStatus;
import co.syntropyhq.aqarat.util.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ViewingDao {

    private static final String COLUMNS = """
        id, property_id, client_id, agent_id, scheduled_at, status, outcome_note, created_at""";

    public int insert(Connection connection, Viewing viewing) throws SQLException {
        String sql = """
            INSERT INTO viewing (property_id, client_id, agent_id, scheduled_at, status, outcome_note)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement statement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, viewing.getPropertyId());
            statement.setInt(2, viewing.getClientId());
            statement.setObject(3, viewing.getAgentId());
            statement.setObject(4, viewing.getScheduledAt());
            statement.setString(5, viewing.getStatus().name());
            statement.setString(6, viewing.getOutcomeNote());
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                generatedKeys.next();
                return generatedKeys.getInt(1);
            }
        }
    }

    public Viewing findById(Connection connection, int id) throws SQLException {
        String sql = ("""
            SELECT %s
            FROM viewing
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

    public Viewing findById(int id) throws SQLException {
        try (Connection connection = Db.get()) {
            return findById(connection, id);
        }
    }

    public List<Viewing> findByAgent(Connection connection, int agentId) throws SQLException {
        String sql = ("""
            SELECT %s
            FROM viewing
            WHERE agent_id = ?
            ORDER BY scheduled_at
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, agentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapRows(resultSet);
            }
        }
    }

    public List<Viewing> findByAgent(int agentId) throws SQLException {
        try (Connection connection = Db.get()) {
            return findByAgent(connection, agentId);
        }
    }

    // A client's own requests plus everything an agent has since scheduled for
    // them - filtered here, in the query, so a customer's screen can never
    // fetch a row that belongs to someone else (DESIGN.md section 8).
    public List<Viewing> findByClient(Connection connection, int clientId) throws SQLException {
        String sql = ("""
            SELECT %s
            FROM viewing
            WHERE client_id = ?
            ORDER BY scheduled_at
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, clientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapRows(resultSet);
            }
        }
    }

    public List<Viewing> findByClient(int clientId) throws SQLException {
        try (Connection connection = Db.get()) {
            return findByClient(connection, clientId);
        }
    }

    public List<Viewing> findByProperty(Connection connection, int propertyId) throws SQLException {
        String sql = ("""
            SELECT %s
            FROM viewing
            WHERE property_id = ?
            ORDER BY scheduled_at
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, propertyId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapRows(resultSet);
            }
        }
    }

    public List<Viewing> findByProperty(int propertyId) throws SQLException {
        try (Connection connection = Db.get()) {
            return findByProperty(connection, propertyId);
        }
    }

    // The confirm-or-decline queue: every REQUESTED viewing has agent_id
    // null, so it cannot be reached through findByAgent - any agent may pick
    // one up, the same way an unclaimed property waits for whoever claims it.
    public List<Viewing> findByStatus(Connection connection, ViewingStatus status)
            throws SQLException {
        String sql = ("""
            SELECT %s
            FROM viewing
            WHERE status = ?
            ORDER BY scheduled_at
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapRows(resultSet);
            }
        }
    }

    public List<Viewing> findByStatus(ViewingStatus status) throws SQLException {
        try (Connection connection = Db.get()) {
            return findByStatus(connection, status);
        }
    }

    public List<Viewing> findByAgentInRange(Connection connection, int agentId,
            LocalDateTime from, LocalDateTime to) throws SQLException {
        String sql = ("""
            SELECT %s
            FROM viewing
            WHERE agent_id = ? AND scheduled_at BETWEEN ? AND ?
            ORDER BY scheduled_at
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, agentId);
            statement.setObject(2, from);
            statement.setObject(3, to);
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapRows(resultSet);
            }
        }
    }

    public List<Viewing> findByAgentInRange(int agentId, LocalDateTime from, LocalDateTime to)
            throws SQLException {
        try (Connection connection = Db.get()) {
            return findByAgentInRange(connection, agentId, from, to);
        }
    }

    // The service-layer half of the double-booking check: ux_viewing_agent_slot
    // is the backstop, this is what lets it produce a readable message before
    // the write is even attempted.
    public boolean hasConfirmedSlot(Connection connection, int agentId, LocalDateTime scheduledAt)
            throws SQLException {
        String sql = """
            SELECT COUNT(*)
            FROM viewing
            WHERE agent_id = ? AND scheduled_at = ? AND status = 'CONFIRMED'
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, agentId);
            statement.setObject(2, scheduledAt);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    // agent_id moves alongside status because confirming is the one
    // transition that also assigns the agent to the slot - every other
    // transition passes back the agent_id the row already had.
    public void updateStatus(Connection connection, int viewingId, ViewingStatus status,
            Integer agentId) throws SQLException {
        String sql = "UPDATE viewing SET status = ?, agent_id = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setObject(2, agentId);
            statement.setInt(3, viewingId);
            statement.executeUpdate();
        }
    }

    public void updateOutcomeNote(Connection connection, int viewingId, String outcomeNote)
            throws SQLException {
        String sql = "UPDATE viewing SET outcome_note = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, outcomeNote);
            statement.setInt(2, viewingId);
            statement.executeUpdate();
        }
    }

    private List<Viewing> mapRows(ResultSet resultSet) throws SQLException {
        List<Viewing> results = new ArrayList<>();
        while (resultSet.next()) {
            results.add(mapRow(resultSet));
        }
        return results;
    }

    private Viewing mapRow(ResultSet resultSet) throws SQLException {
        Viewing viewing = new Viewing();
        viewing.setId(resultSet.getInt("id"));
        viewing.setPropertyId(resultSet.getInt("property_id"));
        viewing.setClientId(resultSet.getInt("client_id"));
        viewing.setAgentId(resultSet.getObject("agent_id", Integer.class));
        viewing.setScheduledAt(resultSet.getObject("scheduled_at", LocalDateTime.class));
        viewing.setStatus(ViewingStatus.valueOf(resultSet.getString("status")));
        viewing.setOutcomeNote(resultSet.getString("outcome_note"));
        viewing.setCreatedAt(resultSet.getObject("created_at", LocalDateTime.class));
        return viewing;
    }
}
