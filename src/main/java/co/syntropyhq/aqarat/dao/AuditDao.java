package co.syntropyhq.aqarat.dao;

import co.syntropyhq.aqarat.model.AuditLog;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AuditDao {

    private static final String COLUMNS =
        "id, user_id, entity_type, entity_id, action, old_value, new_value, created_at";

    // Every filter is optional, so each is guarded with "? IS NULL OR ..."
    // and bound twice, the same idiom PropertyDao.search uses - one
    // PreparedStatement covers any combination of filters, with nothing
    // concatenated into the SQL text. search and count share this clause.
    private static final String FILTER_CLAUSE = """
        FROM audit_log
        WHERE (? IS NULL OR entity_type = ?)
          AND (? IS NULL OR action = ?)
          AND (? IS NULL OR user_id = ?)
          AND (? IS NULL OR created_at >= ?)
          AND (? IS NULL OR created_at <= ?)
        """;

    public List<AuditLog> search(Connection connection, AuditSearch filters, int offset, int pageSize)
            throws SQLException {
        String sql = "SELECT " + COLUMNS + "\n" + FILTER_CLAUSE
            + "ORDER BY created_at DESC\nOFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bindFilters(statement, filters);
            statement.setInt(index++, offset);
            statement.setInt(index, pageSize);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<AuditLog> entries = new ArrayList<>();
                while (resultSet.next()) {
                    entries.add(mapRow(resultSet));
                }
                return entries;
            }
        }
    }

    public int count(Connection connection, AuditSearch filters) throws SQLException {
        String sql = "SELECT COUNT(*)\n" + FILTER_CLAUSE;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindFilters(statement, filters);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private int bindFilters(PreparedStatement statement, AuditSearch filters) throws SQLException {
        int index = 1;
        statement.setString(index++, filters.getEntityType());
        statement.setString(index++, filters.getEntityType());
        statement.setString(index++, filters.getAction());
        statement.setString(index++, filters.getAction());
        statement.setObject(index++, filters.getUserId());
        statement.setObject(index++, filters.getUserId());
        statement.setObject(index++, filters.getCreatedFrom());
        statement.setObject(index++, filters.getCreatedFrom());
        statement.setObject(index++, filters.getCreatedTo());
        statement.setObject(index++, filters.getCreatedTo());
        return index;
    }

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

    /**
     * The trail for many rows of one entity type in a single query.
     *
     * <p>The dossier needs the history of a property and of everything hanging
     * off it. Asking per row would be one query per viewing, reservation,
     * contract and document on the page; this is one per type. The IN list is
     * built from generated placeholders with the ids bound to them, never from
     * the values themselves.
     */
    public List<AuditLog> findByEntities(Connection connection, String entityType,
            List<Integer> entityIds) throws SQLException {
        if (entityIds.isEmpty()) {
            return new ArrayList<>();
        }
        String placeholders = String.join(",", Collections.nCopies(entityIds.size(), "?"));
        String sql = """
            SELECT id, user_id, entity_type, entity_id, action, old_value, new_value, created_at
            FROM audit_log
            WHERE entity_type = ? AND entity_id IN (%s)
            ORDER BY created_at DESC
            """.formatted(placeholders);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entityType);
            int parameter = 2;
            for (Integer entityId : entityIds) {
                statement.setInt(parameter++, entityId);
            }
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
