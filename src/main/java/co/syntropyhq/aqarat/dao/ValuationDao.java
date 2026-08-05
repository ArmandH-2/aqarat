package co.syntropyhq.aqarat.dao;

import co.syntropyhq.aqarat.model.Valuation;
import co.syntropyhq.aqarat.model.ValuationFlag;
import co.syntropyhq.aqarat.util.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Valuations are never updated or deleted - see the comment on dbo.valuation
// in db/schema.sql. This DAO only ever inserts and reads.
public class ValuationDao {

    private static final String COLUMNS = """
        id, property_id, estimated_value, lower_bound, upper_bound, price_per_sqm,
        flag, breakdown, comparables, model_version, created_at""";

    public int insert(Connection connection, Valuation valuation) throws SQLException {
        String sql = """
            INSERT INTO valuation (property_id, estimated_value, lower_bound, upper_bound,
                price_per_sqm, flag, breakdown, comparables, model_version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement statement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, valuation.getPropertyId());
            statement.setBigDecimal(2, valuation.getEstimatedValue());
            statement.setBigDecimal(3, valuation.getLowerBound());
            statement.setBigDecimal(4, valuation.getUpperBound());
            statement.setBigDecimal(5, valuation.getPricePerSqm());
            statement.setString(6, valuation.getFlag().name());
            statement.setString(7, valuation.getBreakdown());
            statement.setString(8, valuation.getComparables());
            statement.setString(9, valuation.getModelVersion());
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                generatedKeys.next();
                return generatedKeys.getInt(1);
            }
        }
    }

    // The review screen only ever wants the estimate as it stands right now.
    public Valuation findLatestByProperty(Connection connection, int propertyId)
            throws SQLException {
        String sql = ("""
            SELECT TOP (1) %s
            FROM valuation
            WHERE property_id = ?
            ORDER BY created_at DESC
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

    public Valuation findLatestByProperty(int propertyId) throws SQLException {
        try (Connection connection = Db.get()) {
            return findLatestByProperty(connection, propertyId);
        }
    }

    public List<Valuation> findAllByProperty(Connection connection, int propertyId)
            throws SQLException {
        String sql = ("""
            SELECT %s
            FROM valuation
            WHERE property_id = ?
            ORDER BY created_at DESC
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, propertyId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Valuation> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
                return results;
            }
        }
    }

    public List<Valuation> findAllByProperty(int propertyId) throws SQLException {
        try (Connection connection = Db.get()) {
            return findAllByProperty(connection, propertyId);
        }
    }

    private Valuation mapRow(ResultSet resultSet) throws SQLException {
        Valuation valuation = new Valuation();
        valuation.setId(resultSet.getInt("id"));
        valuation.setPropertyId(resultSet.getInt("property_id"));
        valuation.setEstimatedValue(resultSet.getBigDecimal("estimated_value"));
        valuation.setLowerBound(resultSet.getBigDecimal("lower_bound"));
        valuation.setUpperBound(resultSet.getBigDecimal("upper_bound"));
        valuation.setPricePerSqm(resultSet.getBigDecimal("price_per_sqm"));
        valuation.setFlag(ValuationFlag.valueOf(resultSet.getString("flag")));
        valuation.setBreakdown(resultSet.getString("breakdown"));
        valuation.setComparables(resultSet.getString("comparables"));
        valuation.setModelVersion(resultSet.getString("model_version"));
        valuation.setCreatedAt(resultSet.getObject("created_at", LocalDateTime.class));
        return valuation;
    }
}
