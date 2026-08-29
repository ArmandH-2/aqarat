package co.syntropyhq.aqarat.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.util.Db;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

// Covers the filters added for the search assistant. These read the seeded
// database rather than mocking it, because the thing worth checking is that
// FILTER_CLAUSE and bindFilters still agree about parameter order - a
// disagreement there returns wrong rows silently, with no exception to catch.
class PropertyDaoTest {

    private static final List<PropertyStatus> AVAILABLE = List.of(PropertyStatus.AVAILABLE);

    private final PropertyDao dao = new PropertyDao();

    @Test
    void searchAndCountAgreeWithNoFilters() throws SQLException {
        try (Connection connection = Db.get()) {
            PropertySearch filters = new PropertySearch();
            int counted = dao.count(connection, AVAILABLE, filters);
            List<Property> page = dao.search(connection, AVAILABLE, filters, 0, counted);
            assertEquals(counted, page.size(), "count and search must match on the same filters");
        }
    }

    @Test
    void hasParkingFiltersStrictlyFewerRows() throws SQLException {
        try (Connection connection = Db.get()) {
            int unfiltered = dao.count(connection, AVAILABLE, new PropertySearch());

            PropertySearch filters = new PropertySearch();
            filters.setHasParking(true);
            int withParking = dao.count(connection, AVAILABLE, filters);

            assertTrue(withParking > 0, "the seed should contain properties with parking");
            assertTrue(withParking < unfiltered, "asking for parking must narrow the result");
        }
    }

    @Test
    void amenityFiltersWorkCorrectly() throws SQLException {
        try (Connection connection = Db.get()) {
            int unfiltered = dao.count(connection, AVAILABLE, new PropertySearch());

            // false means "the user did not ask for this", not "must not have it".
            PropertySearch notAsked = new PropertySearch();
            notAsked.setHasParking(false);
            notAsked.setIsFurnished(false);
            assertEquals(unfiltered, dao.count(connection, AVAILABLE, notAsked),
                "a false amenity must not filter anything out");

            PropertySearch asked = new PropertySearch();
            asked.setHasElevator(true);
            asked.setIsFurnished(true);
            for (Property property : dao.search(connection, AVAILABLE, asked, 0, 20)) {
                assertTrue(property.isHasElevator(), "every row must have an elevator");
                assertTrue(property.isFurnished(), "every row must be furnished");
            }
        }
    }

    @Test
    void bathroomsFilterWorksCorrectly() throws SQLException {
        try (Connection connection = Db.get()) {
            PropertySearch filters = new PropertySearch();
            filters.setBathrooms(2);
            List<Property> results = dao.search(connection, AVAILABLE, filters, 0, 20);
            assertTrue(results.size() > 0, "the seed should contain two-bathroom properties");
            for (Property property : results) {
                assertEquals(2, property.getBathrooms());
            }
        }
    }

    @Test
    void governorateFilterThroughDistrict() throws SQLException {
        try (Connection connection = Db.get()) {
            int unfiltered = dao.count(connection, AVAILABLE, new PropertySearch());

            PropertySearch filters = new PropertySearch();
            filters.setGovernorate("Beirut");
            int inBeirut = dao.count(connection, AVAILABLE, filters);

            assertTrue(inBeirut > 0, "the seed should contain Beirut properties");
            assertTrue(inBeirut < unfiltered, "a governorate must narrow the result");

            // The districts the rows came back from must all belong to that governorate.
            DistrictDao districtDao = new DistrictDao();
            for (Property property : dao.search(connection, AVAILABLE, filters, 0, 20)) {
                assertEquals("Beirut",
                    districtDao.findById(connection, property.getDistrictId()).getGovernorate());
            }
        }
    }
}
