package co.syntropyhq.aqarat.dao;

import co.syntropyhq.aqarat.model.SystemSetting;
import co.syntropyhq.aqarat.util.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

// setting_key, not key - KEY is a reserved word in SQL Server.
public class SystemSettingDao {

    private static final String COLUMNS = "setting_key, value, description";

    public SystemSetting findByKey(Connection connection, String settingKey) throws SQLException {
        String sql = ("""
            SELECT %s
            FROM system_setting
            WHERE setting_key = ?
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, settingKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapRow(resultSet);
                }
                return null;
            }
        }
    }

    public SystemSetting findByKey(String settingKey) throws SQLException {
        try (Connection connection = Db.get()) {
            return findByKey(connection, settingKey);
        }
    }

    public List<SystemSetting> findAll(Connection connection) throws SQLException {
        String sql = ("""
            SELECT %s
            FROM system_setting
            ORDER BY setting_key
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            List<SystemSetting> results = new ArrayList<>();
            while (resultSet.next()) {
                results.add(mapRow(resultSet));
            }
            return results;
        }
    }

    public List<SystemSetting> findAll() throws SQLException {
        try (Connection connection = Db.get()) {
            return findAll(connection);
        }
    }

    private SystemSetting mapRow(ResultSet resultSet) throws SQLException {
        SystemSetting setting = new SystemSetting();
        setting.setSettingKey(resultSet.getString("setting_key"));
        setting.setValue(resultSet.getString("value"));
        setting.setDescription(resultSet.getString("description"));
        return setting;
    }
}
