package com.nexus.enchants.config;

import java.util.Locale;
import java.util.Optional;

/**
 * Supported database back ends.
 */
public enum DatabaseType {

    /**
     * Embedded SQLite database, created next to the plugin jar.
     */
    SQLITE("sqlite", "org.sqlite.JDBC"),

    /**
     * External MySQL or MariaDB server.
     */
    MYSQL("mysql", "com.mysql.cj.jdbc.Driver");

    private final String configKey;
    private final String driverClassName;

    DatabaseType(String configKey, String driverClassName) {
        this.configKey = configKey;
        this.driverClassName = driverClassName;
    }

    /**
     * Returns the key used in config.yml.
     *
     * @return the configuration key
     */
    public String configKey() {
        return configKey;
    }

    /**
     * Returns the JDBC driver class name.
     *
     * @return the fully qualified driver class
     */
    public String driverClassName() {
        return driverClassName;
    }

    /**
     * Returns the directory below {@code db/migration} that holds this dialect's migrations.
     *
     * @return the resource folder name
     */
    public String migrationFolder() {
        return configKey;
    }

    /**
     * Parses a database type.
     *
     * @param raw raw configuration value
     * @return the parsed type, or empty when unknown
     */
    public static Optional<DatabaseType> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String cleaned = raw.trim().toUpperCase(Locale.ROOT);
        for (DatabaseType type : values()) {
            if (type.name().equals(cleaned) || type.configKey.equals(cleaned.toLowerCase(Locale.ROOT))) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
