package co.syntropyhq.aqarat.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.syntropyhq.aqarat.dao.PropertySearch;
import co.syntropyhq.aqarat.model.DealType;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.PropertyType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The router's value is in what it refuses to handle.
 *
 * <p>A phrase it routes wrongly silently narrows someone's search, so the cases
 * that matter most here are the ones that must fall through to the assistant —
 * above all a typo, which is the reason the fail-open rule exists.
 */
class QueryRouterTest {

    private static final List<District> DISTRICTS = List.of(
        district(1, "Achrafieh"), district(2, "Ras Beirut"), district(3, "Jezzine"));
    private static final List<PropertyType> TYPES = List.of(
        propertyType(1, "Apartment"), propertyType(2, "Villa"));

    @Test
    @DisplayName("a bare district name needs no model")
    void routesDistrictAlone() {
        PropertySearch filters = routed("Achrafieh");
        assertEquals(1, filters.getDistrictId());
    }

    @Test
    @DisplayName("district, type, bedrooms and a price ceiling together")
    void routesFullStructuredPhrase() {
        PropertySearch filters = routed("Achrafieh apartment 3 bed under 400k");
        assertEquals(1, filters.getDistrictId());
        assertEquals(1, filters.getPropertyTypeId());
        assertEquals(3, filters.getBedrooms());
        assertEquals(0, new BigDecimal("400000").compareTo(filters.getMaxPrice()));
    }

    @Test
    @DisplayName("a multi-word district is matched whole")
    void routesMultiWordDistrict() {
        assertEquals(2, routed("villa in Ras Beirut").getDistrictId());
    }

    @Test
    @DisplayName("deal type words map to the enum")
    void routesDealType() {
        assertEquals(DealType.RENT, routed("apartment to rent").getDealType());
        assertEquals(DealType.SALE, routed("villa for sale").getDealType());
    }

    @Test
    @DisplayName("money suffixes scale")
    void routesMoneySuffixes() {
        assertEquals(0, new BigDecimal("1200000").compareTo(routed("under 1.2m").getMaxPrice()));
        assertEquals(0, new BigDecimal("250000").compareTo(routed("over 250k").getMinPrice()));
    }

    @Test
    @DisplayName("area is read separately from price")
    void routesArea() {
        PropertySearch filters = routed("over 150sqm");
        assertEquals(0, new BigDecimal("150").compareTo(filters.getMinArea()));
        assertNull(filters.getMaxPrice());
    }

    // ---- the cases that must reach the assistant ---------------------------

    @Test
    @DisplayName("a misspelled district falls through rather than being ignored")
    void typoFallsThrough() {
        assertTrue(QueryRouter.route("Achrafeih 3 bed", DISTRICTS, TYPES).isEmpty());
    }

    @Test
    @DisplayName("a describable quality falls through")
    void qualityFallsThrough() {
        assertTrue(QueryRouter.route("somewhere quiet with morning light", DISTRICTS, TYPES).isEmpty());
        assertTrue(QueryRouter.route("cheap flat in Achrafieh", DISTRICTS, TYPES).isEmpty());
        assertTrue(QueryRouter.route("apartment near a school", DISTRICTS, TYPES).isEmpty());
    }

    @Test
    @DisplayName("a dangling comparison is incomplete, not a filter")
    void danglingComparisonFallsThrough() {
        assertTrue(QueryRouter.route("Achrafieh under", DISTRICTS, TYPES).isEmpty());
    }

    @Test
    @DisplayName("a bare number is not silently taken as a price")
    void bareNumberFallsThrough() {
        assertTrue(QueryRouter.route("Achrafieh 400000", DISTRICTS, TYPES).isEmpty());
    }

    @Test
    @DisplayName("nothing recognisable is not an empty filter set")
    void emptyAndStopwordsOnlyFallThrough() {
        assertTrue(QueryRouter.route("", DISTRICTS, TYPES).isEmpty());
        assertTrue(QueryRouter.route("   ", DISTRICTS, TYPES).isEmpty());
        assertTrue(QueryRouter.route("in the a", DISTRICTS, TYPES).isEmpty());
    }

    @Test
    @DisplayName("a routed phrase explains itself")
    void explains() {
        Optional<QueryRouter.Routed> routed =
            QueryRouter.route("Achrafieh 3 bed", DISTRICTS, TYPES);
        assertTrue(routed.isPresent());
        assertTrue(routed.get().explanation().contains("Achrafieh"));
        assertTrue(routed.get().explanation().contains("3+ bedrooms"));
    }

    // ---- helpers -----------------------------------------------------------

    private static PropertySearch routed(String phrase) {
        Optional<QueryRouter.Routed> result = QueryRouter.route(phrase, DISTRICTS, TYPES);
        assertTrue(result.isPresent(), "expected \"" + phrase + "\" to be routed locally");
        return result.get().filters();
    }

    private static District district(int id, String name) {
        District district = new District();
        district.setId(id);
        district.setName(name);
        return district;
    }

    private static PropertyType propertyType(int id, String name) {
        PropertyType type = new PropertyType();
        type.setId(id);
        type.setName(name);
        return type;
    }
}
