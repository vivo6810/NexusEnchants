-- NexusEnchants V003 (SQLite): administrative and economic audit trail.
CREATE TABLE IF NOT EXISTS audit_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type TEXT NOT NULL,
    actor_uuid TEXT,
    villager_uuid TEXT,
    enchantment_key TEXT,
    enchantment_level INTEGER,
    previous_value TEXT,
    new_value TEXT,
    details TEXT,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON audit_logs (created_at);
CREATE INDEX IF NOT EXISTS idx_audit_logs_event ON audit_logs (event_type, created_at);
