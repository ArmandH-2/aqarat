package co.syntropyhq.aqarat.dao;

import co.syntropyhq.aqarat.model.AuditLog;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AuditDao {

    // No convenience overload here: an audit write is almost always one
    // statement inside a larger transaction, and a caller that opened its
    // own connection could commit the audit row without the change it
    // describes.
    public void insert(Connection connection, AuditLog auditLog) throws SQLException {
        String sql = """
            INSERT INTO audit_log (user_id, entity_type, entity_id, action, old_value, new_value)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement statement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setObject(1, auditLog.getUserId());
            statement.setString(2, auditLog.getEntityType());
            statement.setObject(3, auditLog.getEntityId());
            statement.setString(4, auditLog.getAction());
            statement.setString(5, auditLog.getOldValue());
            statement.setString(6, auditLog.getNewValue());
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                generatedKeys.next();
                auditLog.setId(generatedKeys.getLong(1));
            }
        }
    }

    public List<AuditLog> findByEntity(Connection connection, String entityType, int entityId)
            throws SQLException {
        String sql = """
            SELECT id, user_id, entity_type, entity_id, action, old_value, new_value, created_at
            FROM audit_log
            WHERE entity_type = ? AND entity_id = ?
            ORDER BY created_at DESC
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entityType);
            statement.setInt(2, entityId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<AuditLog> entries = new ArrayList<>();
                while (resultSet.next()) {
                    entries.add(mapRow(resultSet));
                }
                return entries;
            }
        }
    }

    private AuditLog mapRow(ResultSet resultSet) throws SQLException {
        AuditLog auditLog = new AuditLog();
        auditLog.setId(resultSet.getLong("id"));
        auditLog.setUserId(resultSet.getObject("user_id", Integer.class));
        auditLog.setEntityType(resultSet.getString("entity_type"));
        auditLog.setEntityId(resultSet.getObject("entity_id", Integer.class));
        auditLog.setAction(resultSet.getString("action"));
        auditLog.setOldValue(resultSet.getString("old_value"));
        auditLog.setNewValue(resultSet.getString("new_value"));
        auditLog.setCreatedAt(resultSet.getObject("created_at", LocalDateTime.class));
        return auditLog;
    }
}
