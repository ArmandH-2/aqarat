package co.syntropyhq.aqarat.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Config {

    private static final Properties PROPERTIES = loadProperties();

    private Config() {
    }

    public static String get(String key) {
        return PROPERTIES.getProperty(key);
    }

    public static String get(String key, String fallback) {
        String value = PROPERTIES.getProperty(key);
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    public static String require(String key) {
        String value = PROPERTIES.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "config/local.properties is missing the required key '" + key + "'.");
        }
        return value;
    }

    private static Properties loadProperties() {
        Path path = Path.of("config/local.properties");
        if (!Files.exists(path)) {
            throw new IllegalStateException(
                "config/local.properties not found. Copy config/local.properties.example "
                    + "to config/local.properties and fill in your database settings.");
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read config/local.properties.", e);
        }
        return props;
    }
}
