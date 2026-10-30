package com.nexus.enchants.config;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable database configuration.
 *
 * @param type           active back end
 * @param sqliteFile     relative file name for the SQLite database
 * @param foreignKeys    enable SQLite foreign-key enforcement
 * @param busyTimeoutMs  SQLite busy timeout in milliseconds
 * @param journalMode    SQLite journal mode, for example {@code WAL}
 * @param poolSize       connection pool size for the embedded database
 * @param mysql          MySQL/MariaDB settings, {@code null} for SQLite
 */
public record DatabaseConfig(
        DatabaseType type,
        String sqliteFile,
        boolean foreignKeys,
        int busyTimeoutMs,
        String journalMode,
        int poolSize,
        MysqlSettings mysql) {

    /**
     * Validates the configuration.
     *
     * @param type          active back end
     * @param sqliteFile    relative file name for the SQLite database
     * @param foreignKeys   enable SQLite foreign-key enforcement
     * @param busyTimeoutMs SQLite busy timeout in milliseconds
     * @param journalMode   SQLite journal mode
     * @param poolSize      connection pool size
     * @param mysql         MySQL settings
     */
    public DatabaseConfig {
        Objects.requireNonNull(type, "type");
        if (busyTimeoutMs < 0) {
            throw new IllegalArgumentException("busyTimeoutMs must not be negative");
        }
        if (poolSize < 1) {
            throw new IllegalArgumentException("poolSize must be at least 1");
        }
        if (type == DatabaseType.MYSQL && mysql == null) {
            throw new IllegalArgumentException("MySQL settings are required when type is MYSQL");
        }
    }

    /**
     * Returns the default SQLite configuration.
     *
     * @return SQLite with WAL, foreign keys and a pool of five connections
     */
    public static DatabaseConfig defaults() {
        return new DatabaseConfig(DatabaseType.SQLITE, "nexusenchants.db", true, 5000, "WAL", 5, null);
    }

    /**
     * MySQL/MariaDB connection settings.
     *
     * @param host              server hostname
     * @param port              server port
     * @param database          database name
     * @param username          database user
     * @param password          database password, never logged
     * @param ssl               whether TLS is required
     * @param maximumPoolSize   maximum connections
     * @param minimumIdle       minimum idle connections
     * @param connectionTimeout connection timeout in milliseconds
     */
    public record MysqlSettings(
            String host,
            int port,
            String database,
            String username,
            String password,
            boolean ssl,
            int maximumPoolSize,
            int minimumIdle,
            int connectionTimeout) {

        /**
         * Validates the settings.
         *
         * @param host              server hostname
         * @param port              server port
         * @param database          database name
         * @param username          database user
         * @param password          database password
         * @param ssl               whether TLS is required
         * @param maximumPoolSize   maximum connections
         * @param minimumIdle       minimum idle connections
         * @param connectionTimeout connection timeout in milliseconds
         */
        public MysqlSettings {
            Objects.requireNonNull(host, "host");
            Objects.requireNonNull(database, "database");
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(password, "password");
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("port must be between 1 and 65535");
            }
            if (maximumPoolSize < 1) {
                throw new IllegalArgumentException("maximumPoolSize must be at least 1");
            }
            if (minimumIdle < 0 || minimumIdle > maximumPoolSize) {
                throw new IllegalArgumentException("minimumIdle must be between 0 and maximumPoolSize");
            }
            if (connectionTimeout < 250) {
                throw new IllegalArgumentException("connectionTimeout must be at least 250 ms");
            }
        }

        /**
         * Returns the JDBC URL for these settings.
         *
         * @return the connection URL without credentials
         */
        public String jdbcUrl() {
            return String.format(Locale.ROOT,
                    "jdbc:mysql://%s:%d/%s?useSSL=%s&allowPublicKeyRetrieval=true"
                            + "&characterEncoding=UTF-8&useUnicode=true&serverTimezone=UTC",
                    host, port, database, ssl);
        }

        /**
         * Returns the settings without the password, safe for logs and diagnostics.
         *
         * @return a redacted description
         */
        @Override
        public String toString() {
            return "MysqlSettings[host=" + host + ", port=" + port + ", database=" + database
                    + ", username=" + username + ", password=***, ssl=" + ssl
                    + ", maximumPoolSize=" + maximumPoolSize + ", minimumIdle=" + minimumIdle
                    + ", connectionTimeout=" + connectionTimeout + "]";
        }
    }
}
