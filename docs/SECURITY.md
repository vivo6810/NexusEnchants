# Security

## 1. Supported versions

| Version | Supported |
|---|---|
| `main` (latest release) | yes |
| Older tags | no — upgrade instead |

## 2. Reporting a vulnerability

Report privately through GitHub's *Security → Report a vulnerability* on the repository. Do not open a
public issue for an economy exploit or an inventory-duplication bug.

Please include: plugin version, Paper version, reproduction steps, expected vs actual behaviour, and
whether the issue is exploitable by a normal player (inventory duplication, free items, price bypass)
or only by an operator.

## 3. Threat model

Players are untrusted. Operators are trusted. The plugin defends the **economy** and **inventory
integrity**; it does not attempt to sandbox other plugins.

### Defences implemented

| Threat | Defence |
|---|---|
| Restocking restores vanilla prices | `VillagerReplenishTradeEvent` re-applies the configured price on every restock |
| Re-opening the enchanting table re-rolls a result | Plans are cached per player (60 s) and reused by `EnchantItemEvent` |
| Paying less than the configured price via discounts | Recipes are marked `setIgnoreDiscounts(true)` under `CAP`/`DISABLE`; `PlayerTradeEvent` cancels a trade whose effective cost is below the configured price |
| Duplicate merchant recipes | Replacement recipes are deduplicated by trade key; the unique `(villager_uuid, trade_key)` constraint is the final layer |
| Inventory duplication across a multi-stack payment | The recipe always requires exactly `64 + remainder`; the plugin never inserts items into a player inventory itself |
| SQL injection | Every statement is a `PreparedStatement` with bound parameters; no user value is concatenated into SQL |
| Credential leakage | Passwords are never logged; `MysqlSettings#toString()` redacts them; `/vbe database status` shows host/database only |
| Unbounded memory growth under load | Bounded executor queue, bounded caches, aggregated statistics |
| Silent economy drift after a failed load | An invalid configuration keeps the last valid snapshot and reports an error; the plugin never invents a price |

### Explicit non-goals

- Protecting against a compromised server or a malicious operator.
- Preventing players from obtaining items through creative mode, other plugins, or commands.
- Intercepting anvil, grindstone, chest or plugin-granted items (documented in
  `docs/API_COMPATIBILITY.md` §2.4).

## 4. Deployment hardening

1. Run the server as a dedicated, unprivileged user.
2. Keep `plugins/NexusEnchants/` writable only by that user; it contains the SQLite database and the
   YAML configuration.
3. Grant only `nexusenchants.reload`, `nexusenchants.prices` and `nexusenchants.rarity` to staff who
   need them; keep `nexusenchants.database` restricted.
4. Use MySQL with `ssl: true` when the database is not on the same host, and grant only the privileges
   listed in `docs/DATABASE.md` §3.
5. Back up the SQLite file (or dump MySQL) before upgrading the plugin; migrations are forward-only.
6. Never commit `config.yml` containing a real database password. Use `CHANGE_ME` in version control.

## 5. Logging and privacy

- No player identifiers are written to logs.
- Audit records contain actor UUID, villager UUID, enchantment key/level, previous and new values and a
  timestamp; they contain no chat, IP addresses or inventory contents.
- Statistics store aggregated counts only (source, enchantment, level, count).
- Verbose startup logging (`settings.verbose-startup`) prints resolved prices and weights, never
  credentials.

## 6. Data retention

`audit_logs` and `rarity_statistics` grow over time. Operators can truncate them at any time:

```sql
DELETE FROM audit_logs WHERE created_at < :cutoff;
```

Statistics persist as aggregates, so truncation only loses historical distribution data.
