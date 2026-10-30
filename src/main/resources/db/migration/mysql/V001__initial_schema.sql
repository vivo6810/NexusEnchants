-- NexusEnchants V001 (MySQL/MariaDB): managed villager registry.
CREATE TABLE IF NOT EXISTS managed_villagers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    villager_uuid CHAR(36) NOT NULL,
    world_uuid CHAR(36) NOT NULL,
    world_name VARCHAR(64) NOT NULL,
    profession VARCHAR(64) NOT NULL,
    managed TINYINT(1) NOT NULL DEFAULT 1,
    first_registered BIGINT NOT NULL,
    last_updated BIGINT NOT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_managed_villagers_uuid (villager_uuid),
    KEY idx_managed_villagers_world (world_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
