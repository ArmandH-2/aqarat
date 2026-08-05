package co.syntropyhq.aqarat.dao;

import co.syntropyhq.aqarat.model.DealType;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.util.Db;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PropertyDao {

    private static final String COLUMNS = """
        id, owner_id, agent_id, district_id, property_type_id, title, description,
        address_line, area_sqm, bedrooms, bathrooms, floor_number, total_floors,
        year_built, has_parking, has_elevator, has_balcony, is_furnished, deal_type,
        asking_price, min_term_months, max_term_months, status, review_note,
        submitted_at, published_at, closed_at, created_at""";

    // Every filter is optional, so each one is guarded with "? IS NULL OR ..."
    // and bound twice: once for the null check, once for the comparison. This
    // keeps the statement a single PreparedStatement for any combination of
    // filters, with nothing concatenated into the SQL text.
    public List<Property> search(Connection connection, List<PropertyStatus> statuses,
            PropertySearch filters, int offset, int pageSize) throws SQLException {
        String statusPlaceholders = String.join(", ", Collections.nCopies(statuses.size(), "?"));
        String sql = ("""
            SELECT %s
            FROM property
            WHERE status IN (%s)
              AND (? IS NULL OR district_id = ?)
              AND (? IS NULL OR property_type_id = ?)
              AND (? IS NULL OR deal_type = ?)
              AND (? IS NULL OR asking_price >= ?)
              AND (? IS NULL OR asking_price <= ?)
              AND (? IS NULL OR bedrooms = ?)
              AND (? IS NULL OR area_sqm >= ?)
              AND (? IS NULL OR area_sqm <= ?)
              AND (? IS NULL OR title LIKE ?)
            ORDER BY created_at DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """).formatted(COLUMNS, statusPlaceholders);

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (PropertyStatus status : statuses) {
                statement.setString(index++, status.name());
            }
            index = bindFilters(statement, filters, index);
            statement.setInt(index++, offset);
            statement.setInt(index, pageSize);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<Property> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
                return results;
            }
        }
    }

    private int bindFilters(PreparedStatement statement, PropertySearch filters, int index)
            throws SQLException {
        statement.setObject(index++, filters.getDistrictId());
        statement.setObject(index++, filters.getDistrictId());
        statement.setObject(index++, filters.getPropertyTypeId());
        statement.setObject(index++, filters.getPropertyTypeId());
        String dealType = filters.getDealType() == null ? null : filters.getDealType().name();
        statement.setString(index++, dealType);
        statement.setString(index++, dealType);
        statement.setBigDecimal(index++, filters.getMinPrice());
        statement.setBigDecimal(index++, filters.getMinPrice());
        statement.setBigDecimal(index++, filters.getMaxPrice());
        statement.setBigDecimal(index++, filters.getMaxPrice());
        statement.setObject(index++, filters.getBedrooms());
        statement.setObject(index++, filters.getBedrooms());
        statement.setBigDecimal(index++, filters.getMinArea());
        statement.setBigDecimal(index++, filters.getMinArea());
        statement.setBigDecimal(index++, filters.getMaxArea());
        statement.setBigDecimal(index++, filters.getMaxArea());
        String titlePattern =
            filters.getTitleContains() == null ? null : "%" + filters.getTitleContains() + "%";
        statement.setString(index++, filters.getTitleContains());
        statement.setString(index++, titlePattern);
        return index;
    }

    public Property findById(Connection connection, int id) throws SQLException {
        String sql = ("""
            SELECT %s
            FROM property
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

    public Property findById(int id) throws SQLException {
        try (Connection connection = Db.get()) {
            return findById(connection, id);
        }
    }

    public List<Property> findByOwner(Connection connection, int ownerId) throws SQLException {
        String sql = ("""
            SELECT %s
            FROM property
            WHERE owner_id = ?
            ORDER BY created_at DESC
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ownerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Property> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
                return results;
            }
        }
    }

    public List<Property> findByOwner(int ownerId) throws SQLException {
        try (Connection connection = Db.get()) {
            return findByOwner(connection, ownerId);
        }
    }

    public List<Property> findByAgent(Connection connection, int agentId) throws SQLException {
        String sql = ("""
            SELECT %s
            FROM property
            WHERE agent_id = ?
            ORDER BY created_at DESC
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, agentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Property> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
                return results;
            }
        }
    }

    public List<Property> findByAgent(int agentId) throws SQLException {
        try (Connection connection = Db.get()) {
            return findByAgent(connection, agentId);
        }
    }

    public int insert(Connection connection, Property property) throws SQLException {
        String sql = """
            INSERT INTO property (owner_id, agent_id, district_id, property_type_id, title,
                description, address_line, area_sqm, bedrooms, bathrooms, floor_number,
                total_floors, year_built, has_parking, has_elevator, has_balcony, is_furnished,
                deal_type, asking_price, min_term_months, max_term_months, status, review_note,
                submitted_at, published_at, closed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement statement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindPropertyColumns(statement, property);
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                generatedKeys.next();
                return generatedKeys.getInt(1);
            }
        }
    }

    private void bindPropertyColumns(PreparedStatement statement, Property property)
            throws SQLException {
        statement.setInt(1, property.getOwnerId());
        statement.setObject(2, property.getAgentId());
        statement.setInt(3, property.getDistrictId());
        statement.setInt(4, property.getPropertyTypeId());
        statement.setString(5, property.getTitle());
        statement.setString(6, property.getDescription());
        statement.setString(7, property.getAddressLine());
        statement.setBigDecimal(8, property.getAreaSqm());
        statement.setInt(9, property.getBedrooms());
        statement.setInt(10, property.getBathrooms());
        statement.setObject(11, property.getFloorNumber());
        statement.setObject(12, property.getTotalFloors());
        statement.setObject(13, property.getYearBuilt());
        statement.setBoolean(14, property.isHasParking());
        statement.setBoolean(15, property.isHasElevator());
        statement.setBoolean(16, property.isHasBalcony());
        statement.setBoolean(17, property.isFurnished());
        statement.setString(18, property.getDealType().name());
        statement.setBigDecimal(19, property.getAskingPrice());
        statement.setObject(20, property.getMinTermMonths());
        statement.setObject(21, property.getMaxTermMonths());
        statement.setString(22, property.getStatus().name());
        statement.setString(23, property.getReviewNote());
        statement.setObject(24, property.getSubmittedAt());
        statement.setObject(25, property.getPublishedAt());
        statement.setObject(26, property.getClosedAt());
    }

    // The three lifecycle timestamps are written alongside the status because
    // they only ever change when the status does. Taking the whole Property
    // means the caller has already decided which of them this move stamps.
    public void updateStatus(Connection connection, Property property) throws SQLException {
        String sql = """
            UPDATE property
            SET status = ?, submitted_at = ?, published_at = ?, closed_at = ?
            WHERE id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, property.getStatus().name());
            statement.setObject(2, property.getSubmittedAt());
            statement.setObject(3, property.getPublishedAt());
            statement.setObject(4, property.getClosedAt());
            statement.setInt(5, property.getId());
            statement.executeUpdate();
        }
    }

    public void updateReviewNote(Connection connection, int propertyId, String reviewNote)
            throws SQLException {
        String sql = "UPDATE property SET review_note = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reviewNote);
            statement.setInt(2, propertyId);
            statement.executeUpdate();
        }
    }

    private Property mapRow(ResultSet resultSet) throws SQLException {
        Property property = new Property();
        property.setId(resultSet.getInt("id"));
        property.setOwnerId(resultSet.getInt("owner_id"));
        property.setAgentId(resultSet.getObject("agent_id", Integer.class));
        property.setDistrictId(resultSet.getInt("district_id"));
        property.setPropertyTypeId(resultSet.getInt("property_type_id"));
        property.setTitle(resultSet.getString("title"));
        property.setDescription(resultSet.getString("description"));
        property.setAddressLine(resultSet.getString("address_line"));
        property.setAreaSqm(resultSet.getBigDecimal("area_sqm"));
        property.setBedrooms(resultSet.getInt("bedrooms"));
        property.setBathrooms(resultSet.getInt("bathrooms"));
        property.setFloorNumber(resultSet.getObject("floor_number", Integer.class));
        property.setTotalFloors(resultSet.getObject("total_floors", Integer.class));
        property.setYearBuilt(resultSet.getObject("year_built", Integer.class));
        property.setHasParking(resultSet.getBoolean("has_parking"));
        property.setHasElevator(resultSet.getBoolean("has_elevator"));
        property.setHasBalcony(resultSet.getBoolean("has_balcony"));
        property.setFurnished(resultSet.getBoolean("is_furnished"));
        property.setDealType(DealType.valueOf(resultSet.getString("deal_type")));
        property.setAskingPrice(resultSet.getBigDecimal("asking_price"));
        property.setMinTermMonths(resultSet.getObject("min_term_months", Integer.class));
        property.setMaxTermMonths(resultSet.getObject("max_term_months", Integer.class));
        property.setStatus(PropertyStatus.valueOf(resultSet.getString("status")));
        property.setReviewNote(resultSet.getString("review_note"));
        property.setSubmittedAt(resultSet.getObject("submitted_at", LocalDateTime.class));
        property.setPublishedAt(resultSet.getObject("published_at", LocalDateTime.class));
        property.setClosedAt(resultSet.getObject("closed_at", LocalDateTime.class));
        property.setCreatedAt(resultSet.getObject("created_at", LocalDateTime.class));
        return property;
    }
}
