package com.nexus.enchants.database;

import com.nexus.enchants.config.DatabaseConfig;
import com.nexus.enchants.config.DatabaseType;
import com.zaxxer.hikari.HikariConfig;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * MySQL and MariaDB provider backed by HikariCP.
 *
 * <p>The password is only handed to the driver. It never appears in a log line,
 * an exception message or a diagnostic report.</p>
 */
public final class MySQLProvider extends AbstractHikariProvider {

    private final DatabaseConfig.MysqlSettings settings;

    /**
     * Creates the provider.
     *
     * @param settings MySQL settings
     * @param logger   logger
     */
    public MySQLProvider(DatabaseConfig.MysqlSettings settings, Logger logger) {
        super(DatabaseType.MYSQL, logger, "NexusEnchants-MySQL");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    protected void configure(HikariConfig hikari) {
        hikari.setJdbcUrl(settings.jdbcUrl());
        hikari.setUsername(settings.username());
        hikari.setPassword(settings.password());
        hikari.setMaximumPoolSize(settings.maximumPoolSize());
        hikari.setMinimumIdle(settings.minimumIdle());
        hikari.setConnectionTimeout(settings.connectionTimeout());
        hikari.setKeepaliveTime(60_000L);
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "64");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
    }

    @Override
    protected void verify(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT 1")) {
            if (!result.next()) {
                throw new SQLException("MySQL validation query returned no row");
            }
        }
    }

    @Override
    public String describe() {
        return "MySQL[" + settings.host() + ":" + settings.port() + "/" + settings.database()
                + ", pool=" + settings.maximumPoolSize() + ", ssl=" + settings.ssl() + "]";
    }
}
