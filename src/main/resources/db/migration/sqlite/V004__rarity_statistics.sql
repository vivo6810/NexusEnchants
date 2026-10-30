-- NexusEnchants V004 (SQLite): aggregated rarity statistics.
--
-- Selections are aggregated in memory and flushed periodically, so this table
-- grows with the number of distinct enchantment/level pairs and never with the
-- number of rolls.
CREATE TABLE IF NOT EXISTS rarity_statistics (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source TEXT NOT NULL,
    enchantment_key TEXT NOT NULL,
    enchantment_level INTEGER NOT NULL,
    selection_count INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL,
    CONSTRAINT uq_rarity_statistics_key UNIQUE (source, enchantment_key, enchantment_level),
    CONSTRAINT ck_rarity_statistics_count CHECK (selection_count >= 0)
);
