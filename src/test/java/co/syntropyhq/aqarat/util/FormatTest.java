package co.syntropyhq.aqarat.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * Half the timestamps in the schema are absent by design rather than by
 * accident, so formatting one has to be a thing a screen can do rather than a
 * thing that takes the screen down. {@code published_at} is null on every
 * property still in review, and the property file renders it.
 */
class FormatTest {

    private static final String ABSENT = "—";

    @Test
    void anAbsentTimestampFormatsAsADashRatherThanThrowing() {
        assertEquals(ABSENT, Format.dateTime(null));
    }

    @Test
    void anAbsentDateFormatsAsADashRatherThanThrowing() {
        assertEquals(ABSENT, Format.date(null));
    }

    @Test
    void aPresentDateStillFormats() {
        assertEquals("1 Oct 2026", Format.date(LocalDate.of(2026, 10, 1)));
    }

    @Test
    void aPresentTimestampStillFormats() {
        // Stored UTC, displayed local - so this asserts the date part only,
        // which no sane offset can move off 1 October.
        String formatted = Format.dateTime(LocalDateTime.of(2026, 10, 1, 12, 0));
        assertEquals("1 Oct 2026", formatted.substring(0, formatted.indexOf(',')));
    }
}
