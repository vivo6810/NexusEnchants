package com.nexus.enchants.database;

/**
 * A migration recorded in {@code schema_migrations}.
 *
 * @param version     applied schema version
 * @param description recorded description
 * @param installedAt epoch milliseconds of the application
 * @param checksum    checksum recorded when the migration was applied
 */
public record AppliedMigration(int version, String description, long installedAt, String checksum) {
}
