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
    // filters, with nothing concatenated into the SQL text. search and count
    // share this clause so the two can never disagree about what matches.
    private static final String FILTER_CLAUSE = """
        FROM property
        WHERE status IN (%s)
          AND (? IS NULL OR district_id = ?)
          AND (? IS NULL OR property_type_id = ?)
          AND (? IS NULL OR deal_type = ?)
          AND (? IS NULL OR asking_price >= ?)
          AND (? IS NULL OR asking_price <= ?)
          AND (? IS NULL OR bedrooms = ?)
          AND (? IS NULL OR bathrooms = ?)
          AND (? IS NULL OR area_sqm >= ?)
          AND (? IS NULL OR area_sqm <= ?)
          AND (? IS NULL OR has_parking = ?)
          AND (? IS NULL OR has_elevator = ?)
          AND (? IS NULL OR has_balcony = ?)
          AND (? IS NULL OR is_furnished = ?)
          AND (? IS NULL OR district_id IN (SELECT id FROM district WHERE governorate = ?))
          AND (? IS NULL OR title LIKE ?)
          AND (? IS NULL OR agent_id = ?)
          AND (? IS NULL OR agent_id IS NULL)
        """;

    public List<Property> search(Connection connection, List<PropertyStatus> statuses,
            PropertySearch filters, int offset, int pageSize) throws SQLException {
        String sql = "SELECT " + COLUMNS + "\n" + filterClause(statuses)
            + "ORDER BY created_at DESC\nOFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bindStatuses(statement, statuses);
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

    // A dashboard wants the size of a queue, not the queue itself. Counting in
    // SQL keeps a four-figure tile from reading four thousand rows.
    public int count(Connection connection, List<PropertyStatus> statuses, PropertySearch filters)
            throws SQLException {
        String sql = "SELECT COUNT(*)\n" + filterClause(statuses);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bindStatuses(statement, statuses);
            bindFilters(statement, filters, index);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private String filterClause(List<PropertyStatus> statuses) {
        return FILTER_CLAUSE.formatted(
            String.join(", ", Collections.nCopies(statuses.size(), "?")));
    }

    private int bindStatuses(PreparedStatement statement, List<PropertyStatus> statuses)
            throws SQLException {
        int index = 1;
        for (PropertyStatus status : statuses) {
            statement.setString(index++, status.name());
        }
        return index;
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
        statement.setObject(index++, filters.getBathrooms());
        statement.setObject(index++, filters.getBathrooms());
        statement.setBigDecimal(index++, filters.getMinArea());
        statement.setBigDecimal(index++, filters.getMinArea());
        statement.setBigDecimal(index++, filters.getMaxArea());
        statement.setBigDecimal(index++, filters.getMaxArea());
        Boolean hasParking = Boolean.TRUE.equals(filters.getHasParking()) ? Boolean.TRUE : null;
        statement.setObject(index++, hasParking);
        statement.setObject(index++, hasParking);
        Boolean hasElevator = Boolean.TRUE.equals(filters.getHasElevator()) ? Boolean.TRUE : null;
        statement.setObject(index++, hasElevator);
        statement.setObject(index++, hasElevator);
        Boolean hasBalcony = Boolean.TRUE.equals(filters.getHasBalcony()) ? Boolean.TRUE : null;
        statement.setObject(index++, hasBalcony);
        statement.setObject(index++, hasBalcony);
        Boolean isFurnished = Boolean.TRUE.equals(filters.getIsFurnished()) ? Boolean.TRUE : null;
        statement.setObject(index++, isFurnished);
        statement.setObject(index++, isFurnished);
        statement.setString(index++, filters.getGovernorate());
        statement.setString(index++, filters.getGovernorate());
        String titlePattern =
            filters.getTitleContains() == null ? null : "%" + filters.getTitleContains() + "%";
        statement.setString(index++, filters.getTitleContains());
        statement.setString(index++, titlePattern);
        statement.setObject(index++, filters.getAgentId());
        statement.setObject(index++, filters.getAgentId());
        // Only the null check is bound here: the condition it guards needs no
        // value of its own, since "unassigned" is agent_id IS NULL.
        statement.setObject(index++, filters.getUnassignedOnly());
        return index;
    }

    public void updateAgent(Connection connection, int propertyId, Integer agentId)
            throws SQLException {
        String sql = "UPDATE property SET agent_id = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, agentId);
            statement.setInt(2, propertyId);
            statement.executeUpdate();
        }
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

    public void updateCopy(Connection connection, int propertyId, String title, String description)
            throws SQLException {
        String sql = "UPDATE property SET title = ?, description = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, title);
            statement.setString(2, description);
            statement.setInt(3, propertyId);
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
