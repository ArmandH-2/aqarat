package co.syntropyhq.aqarat.ai;

import co.syntropyhq.aqarat.dao.PropertySearch;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.PropertyType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterMapperTest {

    private FilterMapper filterMapper;

    @BeforeEach
    void setUp() {
        District d1 = new District();
        d1.setId(1);
        d1.setName("Achrafieh");

        District d2 = new District();
        d2.setId(2);
        d2.setName("Hamra");

        PropertyType t1 = new PropertyType();
        t1.setId(10);
        t1.setName("Apartment");

        PropertyType t2 = new PropertyType();
        t2.setId(20);
        t2.setName("Villa");

        filterMapper = new FilterMapper(List.of(d1, d2), List.of(t1, t2));
    }

    @Test
    void budgetOnlySentenceLeavesMinPriceNull() {
        String json = "{\"maxPrice\": 300000}";
        PropertySearch result = filterMapper.map(json, null);

        assertEquals(new BigDecimal("300000"), result.getMaxPrice());
        assertNull(result.getMinPrice(), "minPrice must remain null for budget-only constraint");
    }

    @Test
    void unknownDistrictNameLeavesDistrictIdNull() {
        String json = "{\"district\": \"Atlantis\", \"bedrooms\": 2}";
        PropertySearch result = filterMapper.map(json, null);

        assertNull(result.getDistrictId(), "Unknown district should leave districtId null");
        assertEquals(2, result.getBedrooms());
    }

    @Test
    void refinementMergesOntoCarriedFilters() {
        PropertySearch carried = new PropertySearch();
        carried.setDistrictId(1);
        carried.setBedrooms(3);

        String refinementJson = "{\"hasParking\": true, \"maxPrice\": 250000}";
        PropertySearch result = filterMapper.map(refinementJson, carried);

        assertEquals(1, result.getDistrictId(), "District from earlier turn must be preserved");
        assertEquals(3, result.getBedrooms(), "Bedrooms from earlier turn must be preserved");
        assertTrue(result.getHasParking(), "Refinement must add hasParking");
        assertEquals(new BigDecimal("250000"), result.getMaxPrice(), "Refinement must set maxPrice");
    }

    @Test
    void caseInsensitiveAndTrimmedMatching() {
        String json = "{\"district\": \"  achrafieh \", \"propertyType\": \"villa\"}";
        PropertySearch result = filterMapper.map(json, null);

        assertEquals(1, result.getDistrictId());
        assertEquals(20, result.getPropertyTypeId());
    }

    @Test
    void falseAmenityDoesNotReachFilter() {
        String json = "{\"hasParking\": true, \"hasElevator\": false}";
        PropertySearch result = filterMapper.map(json, null);

        assertTrue(result.getHasParking());
        assertNull(result.getHasElevator(), "hasElevator=false must not set a false filter");
    }
}
