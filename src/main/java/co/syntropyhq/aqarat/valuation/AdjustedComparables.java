package co.syntropyhq.aqarat.valuation;

import co.syntropyhq.aqarat.model.Property;

import java.util.ArrayList;
import java.util.List;

// The comparables that survived PriceEstimator's data checks, in three
// lists kept parallel by index: the adjusted price per m2, the property it
// came from (for recency weighting), and the ComparableProperty shown to
// the caller. Package-private and field-only, same pattern as
// ComparablesResult.
final class AdjustedComparables {

    final List<Double> prices = new ArrayList<>();
    final List<Property> properties = new ArrayList<>();
    final List<ComparableProperty> used = new ArrayList<>();
}
