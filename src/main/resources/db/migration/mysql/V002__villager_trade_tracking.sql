-- NexusEnchants V002 (MySQL/MariaDB): managed trade metadata.
CREATE TABLE IF NOT EXISTS managed_trades (
    id BIGINT NOT NULL AUTO_INCREMENT,
    villager_uuid CHAR(36) NOT NULL,
    trade_key VARCHAR(96) NOT NULL,
    enchantment_key VARCHAR(96) NOT NULL,
    enchantment_level INT NOT NULL,
    emerald_price INT NOT NULL,
    uses INT NOT NULL DEFAULT 0,
    max_uses INT NOT NULL DEFAULT 12,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_managed_trades_key (villager_uuid, trade_key),
    KEY idx_managed_trades_villager (villager_uuid),
    CONSTRAINT fk_managed_trades_villager FOREIGN KEY (villager_uuid)
        REFERENCES managed_villagers (villager_uuid) ON DELETE CASCADE,
    CONSTRAINT ck_managed_trades_price CHECK (emerald_price >= 0),
    CONSTRAINT ck_managed_trades_level CHECK (enchantment_level >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
