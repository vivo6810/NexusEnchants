package com.nexus.enchants.database;

import com.nexus.enchants.config.DatabaseType;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared HikariCP wiring for the SQLite and MySQL providers.
 */
abstract class AbstractHikariProvider implements DatabaseProvider {

    private final DatabaseType type;
    private final Logger logger;
    private final String poolName;
    private HikariDataSource dataSource;

    /**
     * Creates the provider.
     *
     * @param type     database type
     * @param logger   logger for connection reporting
     * @param poolName pool name shown in Hikari diagnostics
     */
    AbstractHikariProvider(DatabaseType type, Logger logger, String poolName) {
        this.type = type;
        this.logger = Objects.requireNonNull(logger, "logger");
        this.poolName = poolName;
    }

    /**
     * Configures the driver, pool size and connection validation.
     *
     * @param config configuration to complete
     */
    protected abstract void configure(HikariConfig config);

    /**
     * Runs after the pool is created, before it is verified.
     *
     * @param source created pool
     */
    protected void afterPoolCreated(HikariDataSource source) {
        // Extension point; SQLite verifies its pragmas here.
    }

    /**
     * Verifies that the database answers and that the expected settings are active.
     *
     * @param connection verified connection
     * @throws SQLException when the verification query fails
     */
    protected abstract void verify(Connection connection) throws SQLException;

    @Override
    public final void initialize() {
        if (dataSource != null) {
            return;
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setDriverClassName(type.driverClassName());
        config.setAutoCommit(true);
        config.setMaxLifetime(600_000L);
        config.setValidationTimeout(5_000L);
        configure(config);
        try {
            this.dataSource = new HikariDataSource(config);
            afterPoolCreated(dataSource);
            try (Connection connection = dataSource.getConnection()) {
                verify(connection);
            }
            logger.info("Database connection verified for " + describe());
        } catch (RuntimeException | SQLException exception) {
            close();
            throw new DatabaseException("Could not open the " + type + " database", exception);
        }
    }

    @Override
    public final Connection getConnection() throws SQLException {
        HikariDataSource current = dataSource;
        if (current == null || current.isClosed()) {
            throw new SQLException("The database pool is closed");
        }
        return current.getConnection();
    }

    @Override
    public final void close() {
        HikariDataSource current = dataSource;
        dataSource = null;
        if (current != null) {
            try {
                current.close();
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING, "Failed to close the database pool cleanly", exception);
            }
        }
    }

    @Override
    public final boolean isConnected() {
        HikariDataSource current = dataSource;
        if (current == null || current.isClosed()) {
            return false;
        }
        try (Connection connection = current.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException exception) {
            logger.log(Level.FINE, "Database validation failed", exception);
            return false;
        }
    }

    @Override
    public final DatabaseType type() {
        return type;
    }
}
