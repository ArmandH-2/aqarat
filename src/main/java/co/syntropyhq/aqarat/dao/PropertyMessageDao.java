package co.syntropyhq.aqarat.dao;

import co.syntropyhq.aqarat.model.PropertyMessage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PropertyMessageDao {

    public int insert(Connection connection, PropertyMessage message) throws SQLException {
        String sql = """
            INSERT INTO property_message (property_id, author_id, message)
            VALUES (?, ?, ?)
            """;
        try (PreparedStatement statement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, message.getPropertyId());
            statement.setInt(2, message.getAuthorId());
            statement.setString(3, message.getMessage());
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                generatedKeys.next();
                return generatedKeys.getInt(1);
            }
        }
    }

    // Oldest first - a discussion is read top to bottom the way it happened.
    // author_name is joined in because every screen that shows a message
    // shows who wrote it; nobody ever displays a bare message.
    public List<PropertyMessage> findByProperty(Connection connection, int propertyId)
            throws SQLException {
        String sql = """
            SELECT m.id, m.property_id, m.author_id, u.full_name AS author_name,
                   m.message, m.created_at
            FROM property_message m
            JOIN app_user u ON u.id = m.author_id
            WHERE m.property_id = ?
            ORDER BY m.created_at, m.id
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, propertyId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapRows(resultSet);
            }
        }
    }

    private List<PropertyMessage> mapRows(ResultSet resultSet) throws SQLException {
        List<PropertyMessage> results = new ArrayList<>();
        while (resultSet.next()) {
            results.add(mapRow(resultSet));
        }
        return results;
    }

    private PropertyMessage mapRow(ResultSet resultSet) throws SQLException {
        PropertyMessage message = new PropertyMessage();
        message.setId(resultSet.getInt("id"));
        message.setPropertyId(resultSet.getInt("property_id"));
        message.setAuthorId(resultSet.getInt("author_id"));
        message.setAuthorName(resultSet.getString("author_name"));
        message.setMessage(resultSet.getString("message"));
        message.setCreatedAt(resultSet.getObject("created_at", LocalDateTime.class));
        return message;
    }
}
