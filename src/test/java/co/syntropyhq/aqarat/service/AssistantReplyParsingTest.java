package co.syntropyhq.aqarat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

// The reason under each card is matched to its property by position. It used to
// be matched by title, which gave two warehouses both called "Warehouse in
// Saida" the same reason - the bug these cover.
class AssistantReplyParsingTest {

    private final AssistantService service = new AssistantService(null, null);

    @Test
    void readsOneReasonPerNumberedItem() {
        List<String> reasons = service.numberedLines("""
            I found a couple of warehouses in Saida:

            1. Good storage space with parking available.
            2. A larger option, and it has a balcony.

            Let me know if you want more detail.""");

        assertEquals(2, reasons.size());
        assertEquals("Good storage space with parking available.", reasons.get(0));
        assertEquals("A larger option, and it has a balcony.", reasons.get(1));
    }

    @Test
    void identicalTitlesStillGetDifferentReasons() {
        List<String> reasons = service.numberedLines(
            "1. Warehouse in Saida - closest to your budget.\n"
                + "2. Warehouse in Saida - much larger.");

        assertEquals(2, reasons.size());
        assertTrue(!reasons.get(0).equals(reasons.get(1)), "positions must not collapse");
    }

    @Test
    void stripsEmphasisTheModelSlipsIn() {
        assertEquals("Warehouse in Saida", service.stripEmphasis("**Warehouse in Saida**"));
        assertEquals(List.of("Warehouse in Saida, good size."),
            service.numberedLines("1. **Warehouse in Saida**, good size."));
    }

    @Test
    void proseWithoutNumberingYieldsNothing() {
        assertTrue(service.numberedLines("Which district are you interested in?").isEmpty());
    }
}
