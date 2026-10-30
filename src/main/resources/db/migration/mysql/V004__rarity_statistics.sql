-- NexusEnchants V004 (MySQL/MariaDB): aggregated rarity statistics.
CREATE TABLE IF NOT EXISTS rarity_statistics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source VARCHAR(32) NOT NULL,
    enchantment_key VARCHAR(96) NOT NULL,
    enchantment_level INT NOT NULL,
    selection_count BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_rarity_statistics_key (source, enchantment_key, enchantment_level),
    CONSTRAINT ck_rarity_statistics_count CHECK (selection_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
