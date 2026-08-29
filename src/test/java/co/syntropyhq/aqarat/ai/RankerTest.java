package co.syntropyhq.aqarat.ai;

import co.syntropyhq.aqarat.dao.PropertySearch;
import co.syntropyhq.aqarat.model.Property;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankerTest {

    @Test
    void propertyClosestToStatedBudgetRanksFirst() {
        Property p1 = new Property();
        p1.setId(1);
        p1.setTitle("Far from budget");
        p1.setAskingPrice(new BigDecimal("150000"));

        Property p2 = new Property();
        p2.setId(2);
        p2.setTitle("Closer to budget");
        p2.setAskingPrice(new BigDecimal("220000"));

        Property p3 = new Property();
        p3.setId(3);
        p3.setTitle("Closest to budget");
        p3.setAskingPrice(new BigDecimal("290000"));

        PropertySearch filters = new PropertySearch();
        filters.setMaxPrice(new BigDecimal("300000"));

        List<Property> ranked = Ranker.rank(List.of(p1, p2, p3), filters);

        assertEquals(3, ranked.size());
        assertEquals(3, ranked.get(0).getId(), "Property closest to stated budget ($290k) must rank first");
        assertEquals(2, ranked.get(1).getId());
        assertEquals(1, ranked.get(2).getId());
    }

    @Test
    void missingRequestedAmenityRanksBelowOneThatHasIt() {
        Property withParking = new Property();
        withParking.setId(1);
        withParking.setTitle("With Parking");
        withParking.setAskingPrice(new BigDecimal("200000"));
        withParking.setHasParking(true);

        Property withoutParking = new Property();
        withoutParking.setId(2);
        withoutParking.setTitle("Without Parking");
        withoutParking.setAskingPrice(new BigDecimal("290000"));
        withoutParking.setHasParking(false);

        PropertySearch filters = new PropertySearch();
        filters.setMaxPrice(new BigDecimal("300000"));
        filters.setHasParking(true);

        List<Property> ranked = Ranker.rank(List.of(withoutParking, withParking), filters);

        assertEquals(2, ranked.size());
        assertEquals(1, ranked.get(0).getId(), "Property meeting requested amenity must rank above one without it");
        assertEquals(2, ranked.get(1).getId());
    }

    @Test
    void handlesEmptyAndLimitGracefully() {
        assertTrue(Ranker.rank(null, new PropertySearch()).isEmpty());
        assertTrue(Ranker.rank(List.of(), new PropertySearch()).isEmpty());

        Property p = new Property();
        p.setId(1);
        p.setAskingPrice(new BigDecimal("100000"));
        List<Property> list = List.of(p);
        assertEquals(1, Ranker.rank(list, new PropertySearch(), 5).size());
    }
}
