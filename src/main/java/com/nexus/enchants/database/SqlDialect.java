package com.nexus.enchants.database;

import com.nexus.enchants.config.DatabaseType;

/**
 * The small set of SQL differences between the supported back ends.
 *
 * <p>Repositories ask the dialect for their upsert clause instead of embedding
 * one vendor's syntax in shared code.</p>
 */
public enum SqlDialect {

    /**
     * Embedded SQLite dialect.
     */
    SQLITE(DatabaseType.SQLITE),

    /**
     * MySQL and MariaDB dialect.
     */
    MYSQL(DatabaseType.MYSQL);

    private final DatabaseType type;

    SqlDialect(DatabaseType type) {
        this.type = type;
    }

    /**
     * Returns the dialect for a database type.
     *
     * @param type configured database type
     * @return the matching dialect
     */
    public static SqlDialect of(DatabaseType type) {
        return type == DatabaseType.MYSQL ? MYSQL : SQLITE;
    }

    /**
     * Returns the database type of this dialect.
     *
     * @return the database type
     */
    public DatabaseType type() {
        return type;
    }

    /**
     * Returns the upsert suffix for the managed villager table.
     *
     * @return the SQL fragment that follows the {@code VALUES (...)} list
     */
    public String villagerUpsertClause() {
        return this == SQLITE
                ? " ON CONFLICT(villager_uuid) DO UPDATE SET"
                        + " world_uuid = excluded.world_uuid,"
                        + " world_name = excluded.world_name,"
                        + " profession = excluded.profession,"
                        + " managed = excluded.managed,"
                        + " last_updated = excluded.last_updated,"
                        + " schema_version = excluded.schema_version"
                : " ON DUPLICATE KEY UPDATE"
                        + " world_uuid = VALUES(world_uuid),"
                        + " world_name = VALUES(world_name),"
                        + " profession = VALUES(profession),"
                        + " managed = VALUES(managed),"
                        + " last_updated = VALUES(last_updated),"
                        + " schema_version = VALUES(schema_version)";
    }

    /**
     * Returns the upsert suffix for the managed trade table.
     *
     * @return the SQL fragment that follows the {@code VALUES (...)} list
     */
    public String tradeUpsertClause() {
        return this == SQLITE
                ? " ON CONFLICT(villager_uuid, trade_key) DO UPDATE SET"
                        + " enchantment_key = excluded.enchantment_key,"
                        + " enchantment_level = excluded.enchantment_level,"
                        + " emerald_price = excluded.emerald_price,"
                        + " uses = excluded.uses,"
                        + " max_uses = excluded.max_uses,"
                        + " updated_at = excluded.updated_at"
                : " ON DUPLICATE KEY UPDATE"
                        + " enchantment_key = VALUES(enchantment_key),"
                        + " enchantment_level = VALUES(enchantment_level),"
                        + " emerald_price = VALUES(emerald_price),"
                        + " uses = VALUES(uses),"
                        + " max_uses = VALUES(max_uses),"
                        + " updated_at = VALUES(updated_at)";
    }

    /**
     * Returns the upsert suffix for the rarity statistics table.
     *
     * <p>Both dialects accept {@code ?} for the inserted count and for the
     * timestamp, so the clause stays fully parameterised.</p>
     *
     * @return the SQL fragment that follows the {@code VALUES (...)} list
     */
    public String statisticsUpsertClause() {
        return this == SQLITE
                ? " ON CONFLICT(source, enchantment_key, enchantment_level) DO UPDATE SET"
                        + " selection_count = selection_count + excluded.selection_count,"
                        + " updated_at = excluded.updated_at"
                : " ON DUPLICATE KEY UPDATE"
                        + " selection_count = selection_count + VALUES(selection_count),"
                        + " updated_at = VALUES(updated_at)";
    }
}
