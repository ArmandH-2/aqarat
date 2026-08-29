package co.syntropyhq.aqarat.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
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
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(Config.require("db.url"));
        config.setUsername(Config.require("db.user"));
        config.setPassword(Config.require("db.password"));
        config.setMaximumPoolSize(Integer.parseInt(Config.require("db.pool.size")));
        config.setConnectionTimeout(Long.parseLong(Config.require("db.pool.timeoutMs")));
        return new HikariDataSource(config);
    }
}
