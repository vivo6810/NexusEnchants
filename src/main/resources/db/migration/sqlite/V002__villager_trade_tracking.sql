-- NexusEnchants V002 (SQLite): managed trade metadata.
--
-- trade_key identifies a trade inside one villager, so regenerating the same
-- trade cannot create a second row. The foreign key makes orphan rows
-- impossible once foreign-key enforcement is enabled.
CREATE TABLE IF NOT EXISTS managed_trades (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    villager_uuid TEXT NOT NULL,
    trade_key TEXT NOT NULL,
    enchantment_key TEXT NOT NULL,
    enchantment_level INTEGER NOT NULL,
    emerald_price INTEGER NOT NULL,
    uses INTEGER NOT NULL DEFAULT 0,
    max_uses INTEGER NOT NULL DEFAULT 12,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    CONSTRAINT fk_managed_trades_villager FOREIGN KEY (villager_uuid)
        REFERENCES managed_villagers (villager_uuid) ON DELETE CASCADE,
    CONSTRAINT uq_managed_trades_key UNIQUE (villager_uuid, trade_key),
    CONSTRAINT ck_managed_trades_price CHECK (emerald_price >= 0),
    CONSTRAINT ck_managed_trades_level CHECK (enchantment_level >= 1)
);

CREATE INDEX IF NOT EXISTS idx_managed_trades_villager ON managed_trades (villager_uuid);
