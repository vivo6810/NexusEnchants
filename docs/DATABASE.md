# Database

The database stores **operational state only**. Gameplay values (prices, weights, level
probabilities, source rules, discount policy, settings) live exclusively in YAML — see
`docs/CONFIGURATION.md`. Nothing is stored twice.

## 1. What the database is used for

| Concern | Stored | Notes |
|---|---|---|
| Managed villager registry | `managed_villagers` | one row per villager the plugin controls |
| Trade metadata | `managed_trades` | per villager + trade key, unique |
| Migration history | `schema_migrations` | version, description, timestamp, SHA-256 |
| Audit trail | `audit_logs` | administrative and economic operations |
| Statistics | `rarity_statistics` | aggregated counts, flushed periodically |

If the database is unavailable, trades still work with configuration-derived prices, and the plugin
reports degraded persistence in `/vbe diagnostics`. It never crashes and never falls back to vanilla
prices silently.

## 2. SQLite (default)

```yaml
database:
  type: SQLITE
  sqlite:
    file: nexusenchants.db
    options:
      foreign-keys: true
      busy-timeout: 5000
      journal-mode: WAL
  connection-pool:
    maximum-size: 5
```

- The file is created inside `plugins/NexusEnchants/` on first use.
- `PRAGMA foreign_keys = ON` is applied per connection (driver property **and** init SQL).
- `journal_mode=WAL` improves concurrent reads; `busy_timeout` handles writer contention.
- The pool is created by HikariCP (`HikariDataSource`) with `minimumIdle = 1`.

## 3. MySQL / MariaDB (optional)

```yaml
database:
  type: MYSQL
  mysql:
    host: localhost
    port: 3306
    database: nexusenchants
    username: nexus_user
    password: CHANGE_ME
    ssl: false
    pool:
      maximum-size: 10
      minimum-idle: 2
      connection-timeout: 10000
```

Required grants on the schema:

```sql
GRANT CREATE, ALTER, INDEX, SELECT, INSERT, UPDATE, DELETE ON nexusenchants.* TO 'nexus_user'@'%';
```

Notes:

- MySQL 8.0+ and MariaDB 10.6+ are supported (`utf8mb4`, `InnoDB`).
- The JDBC URL sets `useSSL`, `allowPublicKeyRetrieval`, `characterEncoding=UTF-8` and
  `serverTimezone=UTC`.
- Passwords are never logged; `MysqlSettings#toString()` redacts them.
- Tables must be created by migrations — do not create them by hand.

## 4. Schema (version 4)

`schema_migrations`

| Column | Type | Notes |
|---|---|---|
| `version` | INT / INTEGER | primary key |
| `description` | VARCHAR(255) / TEXT | derived from the file name |
| `installed_at` | BIGINT / INTEGER | epoch millis |
| `checksum` | CHAR(64) / TEXT | SHA-256 of the migration file |

`managed_villagers`

| Column | Notes |
|---|---|
| `id` | surrogate key |
| `villager_uuid` | **unique** |
| `world_uuid`, `world_name`, `profession` | descriptive |
| `managed` | 1 = plugin controls this villager |
| `first_registered`, `last_updated` | epoch millis |
| `schema_version` | row-level schema marker |

`managed_trades`

| Column | Notes |
|---|---|
| `villager_uuid` | FK → `managed_villagers.villager_uuid`, `ON DELETE CASCADE` |
| `trade_key` | stable identifier inside one villager |
| `enchantment_key`, `enchantment_level` | CHECK level ≥ 1 |
| `emerald_price` | CHECK price ≥ 0 |
| `uses`, `max_uses` | restock accounting |
| unique | `(villager_uuid, trade_key)` |

`audit_logs` — `event_type`, `actor_uuid`, `villager_uuid`, `enchantment_key`,
`enchantment_level`, `previous_value`, `new_value`, `details`, `created_at`.

`rarity_statistics` — `source`, `enchantment_key`, `enchantment_level`, `selection_count`,
`updated_at`; unique `(source, enchantment_key, enchantment_level)`, CHECK count ≥ 0.
Selections are aggregated in memory and flushed on an interval, so the table grows with distinct
enchantment/level pairs, never with the number of rolls.

### Indexes

`idx_managed_villagers_world`, `idx_managed_trades_villager`, `idx_audit_logs_created`,
`idx_audit_logs_event`.

## 5. Migrations

Files live in `src/main/resources/db/migration/<dialect>/VNNN__description.sql`; **both** dialect
folders must contain the same versions.

```text
sqlite/V001__initial_schema.sql            mysql/V001__initial_schema.sql
sqlite/V002__villager_trade_tracking.sql   mysql/V002__villager_trade_tracking.sql
sqlite/V003__audit_logs.sql                mysql/V003__audit_logs.sql
sqlite/V004__rarity_statistics.sql         mysql/V004__rarity_statistics.sql
```

Behaviour (`MigrationManager`):

1. `schema_migrations` is created if absent.
2. Discovered files are sorted by version.
3. Every already-applied version is **checksum verified**; a mismatch aborts startup with
   `MigrationException: ... checksum mismatch` and nothing is applied.
4. Pending migrations run in ascending order, each inside a transaction; failure rolls back that
   migration and stops applying further ones.
5. A `.backup` copy of the SQLite file is made before pending migrations run.
6. Scripts are split on `;` after stripping `--` comments; a statement never spans a `;`.

**Never edit a released migration.** Add `V005__…` instead, in both folders, and add a changelog entry.

## 6. Backup

SQLite:

```bash
# stop the server, then:
cp plugins/NexusEnchants/nexusenchants.db plugins/NexusEnchants/nexusenchants.db.backup
# or, while running:
sqlite3 plugins/NexusEnchants/nexusenchants.db ".backup 'nexusenchants-backup.db'"
```

Automatic backups written before migrations are named
`nexusenchants.db.<timestamp>.backup` next to the database file.

MySQL/MariaDB:

```bash
mysqldump --single-transaction nexusenchants > nexusenchants-$(date +%F).sql
```

## 7. Recovery

| Symptom | Action |
|---|---|
| `checksum mismatch` on startup | Restore the original released migration file (preferred), or restore the database from a backup. Do not edit the recorded checksum. |
| Corrupted SQLite file | Stop the server, replace `nexusenchants.db` with a `.backup` file, restart. Missing rows are re-registered as villagers are encountered. |
| Table missing | The database was created by hand; drop the schema and let migrations recreate it, or apply the failing version manually and re-run. |
| Database unreachable | The plugin keeps serving configured prices and marks persistence degraded; fix connectivity and restart. |

## 8. Connection-pool settings

| Setting | SQLite | MySQL |
|---|---|---|
| `maximum-size` | `connection-pool.maximum-size` (default 5) | `mysql.pool.maximum-size` (default 10) |
| `minimum-idle` | 1 | `mysql.pool.minimum-idle` (default 2) |
| `connection-timeout` | HikariCP default | `mysql.pool.connection-timeout` ms |
| validation | `Connection#isValid(2)` at startup | same |
| max lifetime | 10 minutes | 10 minutes |

Every connection is validated once during initialization; a failed validation closes the pool and
the plugin reports that the database is unavailable instead of serving broken trades.

## 9. Failure handling

- Queue overflow is counted and logged (`rejected`), never retried in a loop.
- Failed tasks are counted and logged with their label; the worker thread survives.
- On shutdown the executor drains for up to five seconds, then forces termination.
- Nothing in the database path runs on the server thread, so a slow database cannot stall the tick.
