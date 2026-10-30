package com.nexus.enchants.database;

import java.util.Objects;

/**
 * A versioned SQL migration shipped inside the plugin jar.
 *
 * @param version     schema version, ascending and unique
 * @param description human readable description taken from the file name
 * @param resource    classpath resource path
 * @param checksum    SHA-256 of the file contents
 */
public record Migration(int version, String description, String resource, String checksum) {

    /**
     * Validates the migration.
     *
     * @param version     schema version
     * @param description description
     * @param resource    classpath resource
     * @param checksum    SHA-256 checksum
     */
    public Migration {
        if (version < 1) {
            throw new IllegalArgumentException("Migration version must be positive");
        }
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(checksum, "checksum");
    }
}
