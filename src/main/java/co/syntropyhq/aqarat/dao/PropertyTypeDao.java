package co.syntropyhq.aqarat.dao;

import co.syntropyhq.aqarat.model.PropertyType;
import co.syntropyhq.aqarat.util.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class PropertyTypeDao {

    public int insert(Connection connection, PropertyType propertyType) throws SQLException {
        String sql = "INSERT INTO property_type (name) VALUES (?)";
        try (PreparedStatement statement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, propertyType.getName());
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                generatedKeys.next();
                return generatedKeys.getInt(1);
            }
        }
    }

    public List<PropertyType> findAll(Connection connection) throws SQLException {
        String sql = """
            SELECT id, name
            FROM property_type
            ORDER BY name
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            List<PropertyType> types = new ArrayList<>();
            while (resultSet.next()) {
                types.add(mapRow(resultSet));
            }
            return types;
        }
    }

    public List<PropertyType> findAll() throws SQLException {
        try (Connection connection = Db.get()) {
            return findAll(connection);
        }
    }

    public PropertyType findById(Connection connection, int id) throws SQLException {
        String sql = """
            SELECT id, name
            FROM property_type
            WHERE id = ?
            """;
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

    public PropertyType findById(int id) throws SQLException {
        try (Connection connection = Db.get()) {
            return findById(connection, id);
        }
    }

    private PropertyType mapRow(ResultSet resultSet) throws SQLException {
        PropertyType propertyType = new PropertyType();
        propertyType.setId(resultSet.getInt("id"));
        propertyType.setName(resultSet.getString("name"));
        return propertyType;
    }
}
