-- NexusEnchants V001 (SQLite): managed villager registry.
--
-- Stores one row per villager the plugin controls. Prices themselves always
-- come from YAML; this table only records that a villager was taken over, so a
-- restart or a plugin reload can find and re-apply the managed state.
CREATE TABLE IF NOT EXISTS managed_villagers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    villager_uuid TEXT NOT NULL,
    world_uuid TEXT NOT NULL,
    world_name TEXT NOT NULL,
    profession TEXT NOT NULL,
    managed INTEGER NOT NULL DEFAULT 1,
    first_registered INTEGER NOT NULL,
    last_updated INTEGER NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT uq_managed_villagers_uuid UNIQUE (villager_uuid)
);

CREATE INDEX IF NOT EXISTS idx_managed_villagers_world ON managed_villagers (world_uuid);
