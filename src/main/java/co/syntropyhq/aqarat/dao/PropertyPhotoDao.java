package co.syntropyhq.aqarat.dao;

import co.syntropyhq.aqarat.model.PropertyPhoto;
import co.syntropyhq.aqarat.util.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class PropertyPhotoDao {

    public List<PropertyPhoto> findByProperty(Connection connection, int propertyId)
            throws SQLException {
        String sql = """
            SELECT id, property_id, file_path, is_primary, sort_order
            FROM property_photo
            WHERE property_id = ?
            ORDER BY sort_order
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, propertyId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PropertyPhoto> photos = new ArrayList<>();
                while (resultSet.next()) {
                    photos.add(mapRow(resultSet));
                }
                return photos;
            }
        }
    }

    public List<PropertyPhoto> findByProperty(int propertyId) throws SQLException {
        try (Connection connection = Db.get()) {
            return findByProperty(connection, propertyId);
        }
    }

    public int insert(Connection connection, PropertyPhoto photo) throws SQLException {
        String sql = """
            INSERT INTO property_photo (property_id, file_path, is_primary, sort_order)
            VALUES (?, ?, ?, ?)
            """;
        try (PreparedStatement statement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, photo.getPropertyId());
            statement.setString(2, photo.getFilePath());
            statement.setBoolean(3, photo.isPrimary());
            statement.setInt(4, photo.getSortOrder());
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                generatedKeys.next();
                return generatedKeys.getInt(1);
            }
        }
    }

    public void delete(Connection connection, int photoId) throws SQLException {
        String sql = "DELETE FROM property_photo WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, photoId);
            statement.executeUpdate();
        }
    }

    private PropertyPhoto mapRow(ResultSet resultSet) throws SQLException {
        PropertyPhoto photo = new PropertyPhoto();
        photo.setId(resultSet.getInt("id"));
        photo.setPropertyId(resultSet.getInt("property_id"));
        photo.setFilePath(resultSet.getString("file_path"));
        photo.setPrimary(resultSet.getBoolean("is_primary"));
        photo.setSortOrder(resultSet.getInt("sort_order"));
        return photo;
    }
}
