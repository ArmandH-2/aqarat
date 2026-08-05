package co.syntropyhq.aqarat.dao;

import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.util.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DistrictDao {

    public List<District> findAll(Connection connection) throws SQLException {
        String sql = """
            SELECT id, name, governorate, avg_price_per_sqm
            FROM district
            ORDER BY name
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            List<District> districts = new ArrayList<>();
            while (resultSet.next()) {
                districts.add(mapRow(resultSet));
            }
            return districts;
        }
    }

    public List<District> findAll() throws SQLException {
        try (Connection connection = Db.get()) {
            return findAll(connection);
        }
    }

    public District findById(Connection connection, int id) throws SQLException {
        String sql = """
            SELECT id, name, governorate, avg_price_per_sqm
            FROM district
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

    public District findById(int id) throws SQLException {
        try (Connection connection = Db.get()) {
            return findById(connection, id);
        }
    }

    private District mapRow(ResultSet resultSet) throws SQLException {
        District district = new District();
        district.setId(resultSet.getInt("id"));
        district.setName(resultSet.getString("name"));
        district.setGovernorate(resultSet.getString("governorate"));
        district.setAvgPricePerSqm(resultSet.getBigDecimal("avg_price_per_sqm"));
        return district;
    }
}
