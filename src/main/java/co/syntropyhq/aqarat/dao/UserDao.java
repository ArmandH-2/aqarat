package co.syntropyhq.aqarat.dao;

import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.model.UserStatus;
import co.syntropyhq.aqarat.util.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

public class UserDao {

    // No row found is a normal outcome here, not an error, so this returns
    // null rather than throwing.
    public AppUser findByEmail(Connection connection, String email) throws SQLException {
        String sql = """
            SELECT id, email, password_hash, full_name, phone, role, status, created_at
            FROM app_user
            WHERE email = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapRow(resultSet);
                }
                return null;
            }
        }
    }

    public AppUser findByEmail(String email) throws SQLException {
        try (Connection connection = Db.get()) {
            return findByEmail(connection, email);
        }
    }

    public AppUser findById(Connection connection, int id) throws SQLException {
        String sql = """
            SELECT id, email, password_hash, full_name, phone, role, status, created_at
            FROM app_user
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

    public AppUser findById(int id) throws SQLException {
        try (Connection connection = Db.get()) {
            return findById(connection, id);
        }
    }

    public boolean existsByEmail(Connection connection, String email) throws SQLException {
        String sql = "SELECT id FROM app_user WHERE email = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public int insert(Connection connection, AppUser user) throws SQLException {
        String sql = """
            INSERT INTO app_user (email, password_hash, full_name, phone, role, status)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement statement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, user.getEmail());
            statement.setString(2, user.getPasswordHash());
            statement.setString(3, user.getFullName());
            statement.setString(4, user.getPhone());
            statement.setString(5, user.getRole().name());
            statement.setString(6, user.getStatus().name());
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                generatedKeys.next();
                return generatedKeys.getInt(1);
            }
        }
    }

    private AppUser mapRow(ResultSet resultSet) throws SQLException {
        AppUser user = new AppUser();
        user.setId(resultSet.getInt("id"));
        user.setEmail(resultSet.getString("email"));
        user.setPasswordHash(resultSet.getString("password_hash"));
        user.setFullName(resultSet.getString("full_name"));
        user.setPhone(resultSet.getString("phone"));
        user.setRole(Role.valueOf(resultSet.getString("role")));
        user.setStatus(UserStatus.valueOf(resultSet.getString("status")));
        user.setCreatedAt(resultSet.getObject("created_at", LocalDateTime.class));
        return user;
    }
}
