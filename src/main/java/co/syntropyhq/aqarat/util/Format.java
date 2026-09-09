package co.syntropyhq.aqarat.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Format {

    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME_FORMAT =
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    /** What every screen in this application shows where a value is absent. */
    private static final String ABSENT = "—";

    private Format() {
    }

    public static String salePrice(BigDecimal amount) {
        return "$" + wholeNumber(amount);
    }

    public static String monthlyRent(BigDecimal amount) {
        return "$" + wholeNumber(amount) + "/mo";
    }

    public static String paymentAmount(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "$" + format.format(amount);
    }

    /** A plain grouped count — "1,847". Not money, so it carries no symbol. */
    public static String count(int value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    public static String area(BigDecimal areaSqm) {
        return wholeNumber(areaSqm) + " m²";
    }

    public static String pricePerSqm(BigDecimal amount) {
        return "$" + wholeNumber(amount) + " /m²";
    }

    public static String date(LocalDate date) {
        return date == null ? ABSENT : date.format(DATE_FORMAT);
    }

    /**
     * The database stores UTC. This is the one place a stored timestamp
     * becomes the user's local time.
     *
     * <p>Half of the nullable timestamps in the schema are absent by design
     * rather than by accident - {@code published_at} on anything still in
     * review, {@code closed_at} on a live contract, {@code verified_at} on a
     * document nobody has looked at yet. A screen showing one of those is
     * describing a thing that has not happened, not recovering from a fault,
     * so it renders the same em dash every other absent value in this
     * application renders and carries on. Throwing here took the property
     * file down for all 320 unpublished properties.
     */
    public static String dateTime(LocalDateTime utcDateTime) {
        if (utcDateTime == null) {
            return ABSENT;
        }
        LocalDateTime local = utcDateTime.atZone(ZoneOffset.UTC)
            .withZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime();
        return local.format(DATE_TIME_FORMAT);
    }

    /** The way back in: a time the user picked, ready to store as UTC. */
    public static LocalDateTime toUtc(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneOffset.UTC)
            .toLocalDateTime();
    }

    public static String percentage(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(1);
        format.setMaximumFractionDigits(1);
        return format.format(value) + "%";
    }

    // "PENDING_REVIEW" becomes "Pending review". This is the only place in
    // the project allowed to print an enum name to a user.
    public static String enumLabel(Enum<?> value) {
        return constantLabel(value.name());
    }

    // The same rule for a constant that arrives as text rather than as an
    // enum: audit_log.action is a VARCHAR verb, so it has no enum to pass.
    public static String constantLabel(String constant) {
        if (constant == null || constant.isBlank()) {
            return "";
        }
        String lower = constant.toLowerCase(Locale.ENGLISH).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /**
     * An audit value, which may be a constant or may be a sentence.
     *
     * <p>old_value and new_value carry whatever the service wrote: a status
     * name, a count, or the reason somebody typed. Only something shaped like
     * a constant is reworded; prose is left exactly as it was written, because
     * lower-casing a person's explanation and re-capitalising it would be
     * putting words in their mouth.
     */
    public static String valueLabel(String value) {
        if (value == null) {
            return "";
        }
        return value.matches("[A-Z][A-Z0-9_]*") ? constantLabel(value) : value;
    }

    private static String wholeNumber(BigDecimal value) {
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.US);
        return format.format(value.setScale(0, RoundingMode.HALF_UP));
    }
}
