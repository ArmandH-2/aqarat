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
        return date.format(DATE_FORMAT);
    }

    // The database stores UTC. This is the one place a stored timestamp
    // becomes the user's local time.
    public static String dateTime(LocalDateTime utcDateTime) {
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
        String lower = value.name().toLowerCase(Locale.ENGLISH).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String wholeNumber(BigDecimal value) {
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.US);
        return format.format(value.setScale(0, RoundingMode.HALF_UP));
    }
}
