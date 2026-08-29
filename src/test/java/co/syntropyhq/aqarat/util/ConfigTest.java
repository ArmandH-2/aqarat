package co.syntropyhq.aqarat.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigTest {

    @Test
    void loadsExistingProperty() {
        assertNotNull(Config.get("db.url"));
        assertNotNull(Config.require("db.url"));
    }

    @Test
    void returnsFallbackWhenMissing() {
        assertEquals("default-val", Config.get("non_existent_key_xyz", "default-val"));
        assertNull(Config.get("non_existent_key_xyz"));
    }

    @Test
    void throwsWhenRequiredPropertyMissing() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> Config.require("non_existent_key_xyz"));
        assertEquals("config/local.properties is missing the required key 'non_existent_key_xyz'.",
            ex.getMessage());
    }
}
