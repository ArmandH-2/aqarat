package co.syntropyhq.aqarat.ai;

import co.syntropyhq.aqarat.dao.PropertySearch;
import co.syntropyhq.aqarat.model.DealType;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.PropertyType;
import co.syntropyhq.aqarat.util.Format;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Answers one question about a search phrase: can it be satisfied without the
 * model?
 *
 * <p>Most searches in a property application are not sentences. "Achrafieh",
 * "3 bed apartment", "villa under 500k" are filters someone has typed in a
 * hurry, and sending each one through an agentic loop spends money and a second
 * of the visitor's time to rediscover what a regular expression already knows.
 *
 * <p><strong>The rule that makes this safe is fail-open: a phrase is routed only
 * when every single token has been recognised.</strong> One unrecognised word
 * and the whole phrase goes to the assistant untouched. That is what handles
 * typos — "Achrafeih" matches no district, so it falls through and the model
 * deals with it exactly as it does today — and it is what keeps a misreading
 * from silently narrowing someone's search.
 *
 * <p>Do not weaken that into a best-effort parse that guesses at leftovers and
 * stops calling the model. The value here is in what it declines to handle.
 */
public final class QueryRouter {

    /** Words that carry no filter and no meaning the assistant could add. */
    private static final Set<String> STOPWORDS = Set.of(
        "a", "an", "the", "in", "at", "on", "of", "for", "and", "to", "with", "is", "are");

    private static final Pattern BEDROOMS =
        Pattern.compile("^(\\d{1,2})\\+?(?:bed|beds|bedroom|bedrooms|br)$");
    private static final Pattern BATHROOMS =
        Pattern.compile("^(\\d{1,2})\\+?(?:bath|baths|bathroom|bathrooms)$");
    private static final Pattern AREA =
        Pattern.compile("^(\\d{2,5})(?:m2|sqm|m²)$");
    private static final Pattern MONEY =
        Pattern.compile("^\\$?(\\d+(?:\\.\\d+)?)([km])?$");

    private QueryRouter() {
    }

    /** A phrase the router fully understood, with the filters it produced. */
    public record Routed(PropertySearch filters, String explanation) {
    }

    /**
     * @return the filters when every token was recognised, otherwise empty,
     *         meaning the phrase belongs to the assistant
     */
    public static Optional<Routed> route(String phrase, List<District> districts,
            List<PropertyType> propertyTypes) {
        if (phrase == null || phrase.isBlank()) {
            return Optional.empty();
        }

        List<String> tokens = tokenise(normalise(phrase));
        if (tokens.isEmpty()) {
            return Optional.empty();
        }

        PropertySearch filters = new PropertySearch();
        List<String> understood = new ArrayList<>();

        // District and property type names can be several words ("Ras Beirut"),
        // so they are matched against the phrase before it is broken up.
        String remaining = " " + String.join(" ", tokens) + " ";
        remaining = takeNamed(remaining, districts, District::getName, name -> {
            filters.setDistrictId(idOf(districts, name));
            understood.add(name);
        });
        remaining = takeNamed(remaining, propertyTypes, PropertyType::getName, name -> {
            filters.setPropertyTypeId(typeIdOf(propertyTypes, name));
            understood.add(name);
        });

        PendingRange price = new PendingRange();
        PendingRange area = new PendingRange();
        Comparison comparison = Comparison.NONE;

        for (String token : tokenise(remaining)) {
            if (STOPWORDS.contains(token)) {
                continue;
            }

            Comparison next = Comparison.of(token);
            if (next != Comparison.NONE) {
                comparison = next;
                continue;
            }

            if (token.equals("sale") || token.equals("buy") || token.equals("sell")) {
                filters.setDealType(DealType.SALE);
                understood.add("for sale");
                continue;
            }
            if (token.equals("rent") || token.equals("lease") || token.equals("rental")) {
                filters.setDealType(DealType.RENT);
                understood.add("to rent");
                continue;
            }

            Matcher bedrooms = BEDROOMS.matcher(token);
            if (bedrooms.matches()) {
                int count = Integer.parseInt(bedrooms.group(1));
                filters.setBedrooms(count);
                understood.add(count + "+ bedrooms");
                continue;
            }

            Matcher bathrooms = BATHROOMS.matcher(token);
            if (bathrooms.matches()) {
                int count = Integer.parseInt(bathrooms.group(1));
                filters.setBathrooms(count);
                understood.add(count + "+ bathrooms");
                continue;
            }

            Matcher areaMatch = AREA.matcher(token);
            if (areaMatch.matches()) {
                area.apply(comparison, new BigDecimal(areaMatch.group(1)));
                understood.add(comparison.describe(areaMatch.group(1) + " m²"));
                comparison = Comparison.NONE;
                continue;
            }

            // A bare number is only meaningful next to a comparison word, and
            // even then only as money — "under 400k", never "3".
            Matcher money = MONEY.matcher(token);
            if (money.matches() && comparison != Comparison.NONE) {
                BigDecimal amount = scaled(money.group(1), money.group(2));
                price.apply(comparison, amount);
                understood.add(comparison.describe(Format.salePrice(amount)));
                comparison = Comparison.NONE;
                continue;
            }

            // Anything left is something only the assistant can interpret.
            return Optional.empty();
        }

        // A trailing comparison with nothing after it ("under") is incomplete.
        if (comparison != Comparison.NONE || understood.isEmpty()) {
            return Optional.empty();
        }

        filters.setMinPrice(price.min);
        filters.setMaxPrice(price.max);
        filters.setMinArea(area.min);
        filters.setMaxArea(area.max);
        return Optional.of(new Routed(filters, "Filtered by " + String.join(", ", understood) + "."));
    }

    // ------------------------------------------------------------- internals

    /**
     * Joins a count to the unit it belongs to, so "3 bed" and "150 sqm" read as
     * one token. People type the space; without this every such phrase would
     * fall through to the model for no reason.
     */
    private static String normalise(String phrase) {
        return phrase.toLowerCase(Locale.ROOT).replaceAll(
            "(\\d)\\s+(beds?|bedrooms?|br|baths?|bathrooms?|m2|sqm|m²)\\b", "$1$2");
    }

    private static List<String> tokenise(String phrase) {
        List<String> tokens = new ArrayList<>();
        for (String raw : phrase.toLowerCase(Locale.ROOT).split("[\\s,;]+")) {
            // Commas and spaces separate; a stray full stop or quote does not
            // make a word unrecognisable.
            String token = raw.replaceAll("^[\"'(]+|[\"').!?]+$", "").replace(",", "");
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static <T> String takeNamed(String haystack, List<T> candidates,
            java.util.function.Function<T, String> naming, java.util.function.Consumer<String> onFound) {
        String best = null;
        for (T candidate : candidates) {
            String name = naming.apply(candidate);
            if (name == null || name.isBlank()) {
                continue;
            }
            String needle = " " + name.toLowerCase(Locale.ROOT) + " ";
            if (haystack.contains(needle) && (best == null || name.length() > best.length())) {
                best = name;
            }
        }
        if (best == null) {
            return haystack;
        }
        onFound.accept(best);
        return haystack.replace(" " + best.toLowerCase(Locale.ROOT) + " ", " ");
    }

    private static Integer idOf(List<District> districts, String name) {
        return districts.stream()
            .filter(district -> name.equals(district.getName()))
            .map(District::getId)
            .findFirst()
            .orElse(null);
    }

    private static Integer typeIdOf(List<PropertyType> types, String name) {
        return types.stream()
            .filter(type -> name.equals(type.getName()))
            .map(PropertyType::getId)
            .findFirst()
            .orElse(null);
    }

    private static BigDecimal scaled(String digits, String suffix) {
        BigDecimal value = new BigDecimal(digits);
        if ("k".equals(suffix)) {
            return value.multiply(BigDecimal.valueOf(1_000));
        }
        if ("m".equals(suffix)) {
            return value.multiply(BigDecimal.valueOf(1_000_000));
        }
        return value;
    }

    private enum Comparison {
        NONE, UNDER, OVER;

        static Comparison of(String token) {
            return switch (token) {
                case "under", "below", "max", "maximum", "upto", "cheaper" -> UNDER;
                case "over", "above", "min", "minimum", "from" -> OVER;
                default -> NONE;
            };
        }

        String describe(String amount) {
            return this == UNDER ? "up to " + amount : "from " + amount;
        }
    }

    /** Collects one side of a range as the tokens are read. */
    private static final class PendingRange {
        private BigDecimal min;
        private BigDecimal max;

        void apply(Comparison comparison, BigDecimal value) {
            if (comparison == Comparison.OVER) {
                min = value;
            } else {
                max = value;
            }
        }
    }
}
