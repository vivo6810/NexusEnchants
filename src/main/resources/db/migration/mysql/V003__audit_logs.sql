-- NexusEnchants V003 (MySQL/MariaDB): administrative and economic audit trail.
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(64) NOT NULL,
    actor_uuid CHAR(36) NULL,
    villager_uuid CHAR(36) NULL,
    enchantment_key VARCHAR(96) NULL,
    enchantment_level INT NULL,
    previous_value VARCHAR(255) NULL,
    new_value VARCHAR(255) NULL,
    details TEXT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_logs_created (created_at),
    KEY idx_audit_logs_event (event_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
