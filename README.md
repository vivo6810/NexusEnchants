# NexusEnchants

A Paper plugin that puts you in control of **how enchantments are obtained, priced and distributed**:
librarian enchanted-book prices and rarity, enchanting-table rarity, level probabilities,
acquisition-source restrictions and discount policy.

> **Build status:** the source, tests, documentation and CI workflows in this repository have been
> written but **never compiled or executed**. GitHub Actions performs the first build; until that run
> passes, treat every module as *implemented, not verified*. See `docs/DEVELOPMENT_STATUS.md`.

---

## Features

| System | What it does |
|---|---|
| **Villager pricing** | Replaces vanilla enchanted-book prices with per-enchantment, per-level prices (Mending I = 70 emeralds by default). |
| **Villager rarity** | Weighted random selection decides which book a librarian offers (Mending weight 2 vs Unbreaking 12). |
| **Enchanting-table rarity** | Weighted selection decides what the table produces, for both the preview and the result. |
| **Level probabilities** | Independent weights per level, normalised over the levels that are legal for the enchantment. |
| **Acquisition sources** | Allow or forbid each enchantment per source: librarian, enchanting table, loot, fishing. |
| **Discount policy** | `PRESERVE`, `DISABLE` or `CAP` (default: cap at 20%). |
| **Persistence** | SQLite by default, MySQL/MariaDB optional; versioned, checksum-protected migrations. |
| **Operations** | `/vbe` command tree, permissions, tab completion, diagnostics, structured audit records. |

## Supported versions

| Component | Version |
|---|---|
| Minecraft / Paper | 26.3 (`api-version: '26.3'`) |
| Java | 25 (required by Paper 26.3) |
| Gradle | 9.8.2 (wrapper included) |
| Databases | SQLite (embedded), MySQL 8.0+/MariaDB 10.6+ |

## Installation

1. Build the plugin jar (see [Building](#building)) or download it from a GitHub Release.
2. Copy `NexusEnchants-<version>.jar` into your server's `plugins/` directory.
3. Start the server once. The plugin writes its configuration to
   `plugins/NexusEnchants/` and creates the SQLite database on first use.
4. Edit the configuration files, then run `/vbe reload`.

## Requirements

- Paper 26.3 server with Java 25.
- No other plugin is required. LuckPerms is recommended for permission management.
- Optional: a MySQL/MariaDB server if you prefer an external database.

## Default gameplay policy

| Setting | Default |
|---|---:|
| Mending price | 70 emeralds |
| Mending villager weight | 2 |
| Unbreaking villager weight | 12 |
| Fortune villager weight | 5 |
| Silk Touch villager weight | 4 |
| Mending via enchanting table | disabled |
| Treasure enchantments via table | disabled |
| Multiple enchantments chance | 15% |
| Maximum discount | 20% (`CAP`) |
| Rarity algorithm | weighted random |
| Database | SQLite |

These are deliberate custom defaults, not vanilla probabilities. Every value is configurable, and
YAML is the only source of truth for gameplay values.

## Configuration

| File | Purpose |
|---|---|
| `config.yml` | Master switch, book trades, statistics, audit, async pool, database |
| `pricing.yml` | Per-enchantment, per-level prices, fallback and limits |
| `villager-rarity.yml` | Librarian weights per enchantment and per level |
| `enchanting-rarity.yml` | Table weights, treasure rule, multiple-enchantment chance |
| `enchantment-levels.yml` | Level weights (default + per-enchantment overrides) |
| `enchantment-sources.yml` | Per-enchantment source permissions |
| `discounts.yml` | Discount policy, cap, hero amplification |

Full reference: [docs/CONFIGURATION.md](docs/CONFIGURATION.md).

## Database setup

SQLite is used automatically — no configuration is needed. For MySQL/MariaDB, edit `config.yml`:

```yaml
database:
  type: MYSQL
  mysql:
    host: localhost
    port: 3306
    database: nexusenchants
    username: nexus_user
    password: CHANGE_ME
```

The account needs `CREATE, ALTER, INDEX, SELECT, INSERT, UPDATE, DELETE` on that schema. Migrations
run automatically on startup, each in its own transaction; a backup of the SQLite file is written
before pending migrations. Database settings require a **restart**, not `/vbe reload`.
Details: [docs/DATABASE.md](docs/DATABASE.md).

## Commands

| Command | Description |
|---|---|
| `/vbe help` | List every subcommand |
| `/vbe info` | Plugin and configuration summary |
| `/vbe reload` | Reload all configuration files |
| `/vbe price <enchantment> <level>` | Show a configured price |
| `/vbe setprice <enchantment> <level> <price>` | Change a price and persist it to `pricing.yml` |
| `/vbe rarity list` | List every rarity weight |
| `/vbe rarity villager <enchantment>` | Librarian details for one enchantment |
| `/vbe rarity enchanting <enchantment>` | Enchanting-table details for one enchantment |
| `/vbe rarity set villager <enchantment> <weight>` | Change a librarian weight |
| `/vbe rarity set enchanting <enchantment> <weight>` | Change a table weight |
| `/vbe rarity test <enchantment>` | Simulate 20,000 librarian rolls and compare with the expectation |
| `/vbe database status` | Connection and pool status |
| `/vbe database migrations` | Applied and pending migrations |
| `/vbe diagnostics` | Configuration problems, degraded features, timings |

Alias: `/nexusenchants`. Tab completion is available for every argument.

## Permissions

| Permission | Default | Grants |
|---|---|---|
| `nexusenchants.admin` | op | Everything below (child permissions) |
| `nexusenchants.reload` | op | `/vbe reload` |
| `nexusenchants.prices` | op | `/vbe price`, `/vbe setprice` |
| `nexusenchants.rarity` | op | `/vbe rarity …` |
| `nexusenchants.diagnostics` | op | `/vbe diagnostics`, `/vbe info` |
| `nexusenchants.database` | op | `/vbe database …` |

## Compatibility and API

A stable Java API (`NexusEnchantsApi` 1.0.0) is exposed through Bukkit's `ServicesManager` for
other plugins. Verified Paper APIs and documented limitations are listed in
[docs/API_COMPATIBILITY.md](docs/API_COMPATIBILITY.md).

## Known limitations

1. **Vanilla discounts cannot be capped per player.** Paper 26.3 has no per-player merchant price
   hook. `CAP`/`DISABLE` suppress vanilla discount providers on the recipe; the plugin re-applies
   only Hero of the Village. Cured-villager and reputation discounts are not recomputed.
   Use `PRESERVE` if you want untouched vanilla behaviour.
2. **128 emeralds per trade is the hard ceiling** (two merchant input slots of 64). Prices above it
   are rejected at load time rather than silently truncated.
3. **Additional enchantments cannot be previewed.** The offer API exposes one enchantment per slot;
   extra enchantments from a combination are applied from the recorded plan.
4. **Anvil, grindstone and chest transfers are not intercepted** — only librarian trades, enchanting
   tables, loot generation and fishing.
5. **Existing librarians are repriced, not re-rolled.** Enchantments already sold are never changed;
   trades whose source became disallowed are dropped.
6. **Database settings need a restart.**

## Building

The build is performed by GitHub Actions; the workflows are the supported build path:

```bash
# what CI runs
./gradlew --no-daemon clean check build
```

The shaded plugin jar is written to `build/libs/NexusEnchants-<version>.jar`.

## Troubleshooting

See [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md), or run `/vbe diagnostics`.

## Project layout

```
INIT.md                     project memory — read this first
AGENTS.md                   instructions for coding agents
README.md                   this file
docs/                       architecture, database, API, testing, configuration, status, ADRs
src/main/java/              plugin source
src/main/resources/         plugin.yml, YAML configuration, SQL migrations
src/test/java/              unit, database, MockBukkit and statistical tests
.github/workflows/          CI and release pipelines
```

## Licence

MIT — see [LICENSE](LICENSE). Third-party attributions: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
