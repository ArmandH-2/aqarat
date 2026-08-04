package co.syntropyhq.aqarat.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import javax.sql.DataSource;

public final class Db {

    private static DataSource dataSource;

    private Db() {
    }

    public static synchronized Connection get() throws SQLException {
        if (dataSource == null) {
            dataSource = buildDataSource();
        }
        return dataSource.getConnection();
    }

    private static DataSource buildDataSource() {
        Properties props = loadProperties();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(requireProperty(props, "db.url"));
        config.setUsername(requireProperty(props, "db.user"));
        config.setPassword(requireProperty(props, "db.password"));
        config.setMaximumPoolSize(Integer.parseInt(requireProperty(props, "db.pool.size")));
        config.setConnectionTimeout(Long.parseLong(requireProperty(props, "db.pool.timeoutMs")));
        return new HikariDataSource(config);
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

    private static String requireProperty(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "config/local.properties is missing the required key '" + key + "'.");
        }
        return value;
    }
}
