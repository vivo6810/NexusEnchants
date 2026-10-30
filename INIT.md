# INIT.md — NexusEnchants Project Memory

> **Read this file before changing anything.** It is the authoritative handover
> document for every AI agent and every human developer working on NexusEnchants.
> If this file disagrees with the source code, inspect the source, then fix this file.

---

## A. Project identity

| Item | Value |
|---|---|
| Project name | NexusEnchants |
| Purpose | Full control over how enchantments are obtained, priced and distributed |
| Type | Minecraft **Paper** plugin (Java) |
| Current development phase | Phase 8 — final verification preparation (implementation complete, **not yet CI verified**) |
| Target Minecraft version | 26.3 (Paper 26.3 line) |
| Target Paper API | `io.papermc.paper:paper-api:26.3.build.200-stable` |
| Java version | **25** (Paper 26.3 class major 69) |
| Gradle version | **9.8.2** (wrapper committed, `distributionSha256Sum` pinned) |
| Database architecture | SQLite (default, embedded) or MySQL/MariaDB (optional), behind HikariCP, all access asynchronous |
| Configuration format | YAML — the only source of truth for gameplay values |
| Build/verification | **GitHub Actions only** (`.github/workflows/ci.yml`, `release.yml`) |

### Five gameplay systems

1. **Villager book pricing** — `economy/`
2. **Villager book rarity** — `villager/`
3. **Enchanting-table rarity** — `enchanting/`
4. **Enchantment level probabilities** — `enchanting/EnchantmentLevelManager`
5. **Acquisition source restrictions** — `sources/`

Plus: discounts (`economy/DiscountManager`), persistence + migrations (`database/`), commands
(`commands/`), permissions (`permissions/`), diagnostics (`diagnostics/`), public API (`api/`).

---

## B. Permanent development rules

1. **Do not compile or run any build task locally.** No `./gradlew build`, `assemble`,
   `compileJava`, `test`, `check`, and no equivalent IDE or Gradle invocation.
   GitHub Actions performs compilation, testing and packaging.
2. **Never claim compilation or test success** without a real GitHub Actions run.
3. **No fake implementations.** No empty methods presented as complete, no placeholder
   classes, no always-passing tests, no silent fallback to vanilla behaviour.
4. **No unverified API assumptions.** Every Bukkit/Paper call used here was checked
   against the Paper 26.3 API or the `paper-api` class file. Additions must be verified
   the same way and recorded in `docs/API_COMPATIBILITY.md`.
5. **YAML is authoritative** for prices, weights, level probabilities, source rules,
   discount policy and settings. SQL only stores *operational state*.
6. **No JDBC on the server thread.** Capture immutable data on the main thread, run SQL on
   `AsyncDatabaseExecutor`, return to the main thread before touching Bukkit objects.
7. **No unnecessary architectural rewrites.** Extend the existing modules; never silently
   reverse a decision from section D.
8. **Update this file at the end of every session** (see section F/H/J).
9. Migrations are forward-only: never edit a released migration, always add a new `Vnnn__*.sql`.
10. Never commit credentials, database files or generated logs.

---

## C. Architecture

### Package structure (`src/main/java/com/nexus/enchants/`, 78 files)

```text
NexusEnchants.java                 JavaPlugin entry point; delegates to PluginBootstrap
bootstrap/
  PluginBootstrap.java             Wiring, registration, reload, start/stop ordering
  ServiceRegistry.java             Typed service lookup container
config/
  ConfigManager.java               Loads 7 YAML files, writes changes, produces snapshots
  ConfigSnapshot.java              Immutable validated view of all configuration
  ConfigValidator.java             Validation helpers and canonical key resolution
  ConfigIssue.java                 Severity/file/path/message diagnostic record
  PricingConfig / VillagerRarityConfig / EnchantingConfig / LevelProbabilityConfig
  SourceConfig / DiscountConfig / DatabaseConfig / PluginSettings / SelectionMode / DatabaseType
economy/
  PriceCalculator.java             key+level -> price (fallback, clamp, limits)
  PaymentPlan.java                 Splits a price into <=2 merchant input slots (max 128)
  DiscountManager.java             PRESERVE / DISABLE / CAP policy, hero amplifier math
  DiscountResult.java, DiscountPolicy.java, PriceNotConfiguredException.java
  TradeValidator.java              Pre-flight validation of price/payment values
  BookPricingManager.java          Orchestrates price + discount + validation
villager/
  VillagerRarityManager.java       Weighted enchantment/level selection for librarians
  TradeGenerationManager.java      Builds MerchantRecipe objects from a selection
  VillagerTradeManager.java        Acquire/replenish/player-trade listeners, restock defence
  VillagerMigrationManager.java    Reprices existing librarians once, never re-rolls
enchanting/
  EnchantmentWeightManager.java    Eligible enchantment table for an item
  EnchantmentLevelManager.java     Level probabilities, legal range clamping
  CombinationManager.java          Builds a plan (primary + extras, compatibility-checked)
  EnchantingResultManager.java     Writes a plan into offers and the enchant event
  EnchantingManager.java           PrepareItemEnchantEvent / EnchantItemEvent listeners
sources/
  AcquisitionSource.java           VILLAGER, ENCHANTING_TABLE, LOOT, FISHING
  EnchantmentSourceManager.java    Rule lookup + defaults
  SourceRestrictionListener.java   Loot generation and fishing interception
database/
  DatabaseProvider.java            initialize/getConnection/close/isConnected contract
  AbstractHikariProvider.java      Shared HikariCP life cycle + verification hooks
  SQLiteProvider.java / MySQLProvider.java
  MigrationManager.java            Discovery, checksums, transactional apply, SQLite backup
  AsyncDatabaseExecutor.java       Bounded ThreadPoolExecutor for every JDBC call
  DatabaseManager.java             Life cycle, readiness, status, repository accessors
  repositories/{Villager,Trade,AuditLog,Statistics}Repository.java
storage/                           In-memory caches: TradeCache, RarityStatisticsStore, records
commands/                          CommandDispatcher, Help/Admin/Pricing/Rarity/Diagnostics
permissions/Permissions.java       Permission constants (must match plugin.yml)
diagnostics/DiagnosticsService.java
api/                               NexusEnchantsApi + implementation (stable third-party surface)
util/                              EnchantmentResolver, WeightedTable, PriceMath, Text, EnchantmentWeight
```

### Service dependencies (wiring order in `PluginBootstrap.start()`)

```text
ConfigManager ─┬─> PriceCalculator ─> DiscountManager ─> BookPricingManager
               ├─> EnchantmentSourceManager ─┬─> VillagerRarityManager
               │                             ├─> EnchantmentWeightManager
               │                             └─> SourceRestrictionListener
               ├─> EnchantmentLevelManager ─> CombinationManager ─> EnchantingManager
               └─> TradeGenerationManager ─> VillagerTradeManager ─> VillagerMigrationManager
DatabaseManager (async init) ─> MigrationManager ─> repositories ─> TradeCache / RarityStatisticsStore
```

### Event handling

| Event | Priority | Purpose |
|---|---|---|
| `VillagerAcquireTradeEvent` | HIGHEST | Replace the generated book trade with a weighted one |
| `VillagerReplenishTradeEvent` | HIGH | Re-apply the configured price after restock |
| `PlayerTradeEvent` | HIGH | Price floor guard before payment, audit record |
| `PrepareItemEnchantEvent` | HIGH | Rewrite the three preview offers from a plan |
| `EnchantItemEvent` | HIGHEST | Apply the cached plan; enforce sources otherwise |
| `LootGenerateEvent` | HIGH | Remove disallowed enchantments from generated loot |
| `PlayerFishEvent` | HIGH | Remove disallowed enchantments from caught items |
| `PlayerQuitEvent` | NORMAL | Drop cached enchanting plans |

---

## D. Technical decisions (ADR log)

```text
ADR-001  SQLite is the default database.
         Reason: zero-setup embedded storage; MySQL/MariaDB available for networks.

ADR-002  YAML is the single source of truth for gameplay values; SQL only stores state.
         Reason: avoids dual-write drift; admins edit YAML, never SQL.

ADR-003  GitHub Actions performs every build, test and package step.
         Reason: reproducible CI and a hard boundary against unverified local builds.

ADR-004  All JDBC work runs on a bounded AsyncDatabaseExecutor (2 workers, 512 queue).
         Reason: protect the tick loop; bounded queue prevents unbounded memory growth.

ADR-005  Java 25 + Paper API 26.3.build.200-stable + Gradle 9.8.2.
         Reason: Paper 26.3 requires Java 25 (class major 69); the API build is pinned to
         the exact build MockBukkit 4.119.0 reports in its manifest.

ADR-006  Shadow 9.6.1 shades HikariCP, sqlite-jdbc and mysql-connector-j; only HikariCP is
         relocated (to com.nexus.enchants.libs.hikari).
         Reason: avoid clashing with other plugins that bundle HikariCP; JDBC drivers need
         their META-INF/services and native resources unrelocated.

ADR-007  Prices above 64 are split across the two merchant input slots (64 + remainder),
         hard-capped at 128 emeralds per trade.
         Reason: a merchant recipe has exactly two input slots; a larger single-trade price
         cannot be represented, so it is refused rather than silently truncated.

ADR-008  Enchanting plans are computed in PrepareItemEnchantEvent, cached per player for 60 s,
         and applied in EnchantItemEvent.
         Reason: the shown offer and the produced result must not disagree.

ADR-009  Discount policy CAP/DISABLE calls MerchantRecipe#setIgnoreDiscounts(true).
         Reason: Paper 26.3 exposes no per-player merchant price hook, so vanilla discount
         providers (curing, reputation, hero) are suppressed; the plugin re-applies only the
         discounts it can read.

ADR-010  Checkstyle with a narrow, objective rule set; SpotBugs intentionally omitted.
         Reason: SpotBugs support for class-file major 69 is unverified; shipping an
         unchecked analyser is worse than documenting the gap (docs/DEVELOPMENT_STATUS.md).

ADR-011  Existing librarians are migrated by rewriting prices only; enchantments are never re-rolled.
         Reason: re-rolling would destroy player-bought trade history; disallowed trades are dropped.

ADR-012  Statistical verification is a separate test category (`statisticalTest`, wired into `check`).
         Reason: 1,000,000-iteration distributions are meaningful in CI but slow for local loops.
```

---

## E. Implementation status

| Module | Status | Notes |
|---|---|---|
| Project bootstrap | IMPLEMENTED | `NexusEnchants`, `PluginBootstrap`, `ServiceRegistry` |
| Configuration engine | IMPLEMENTED | 7 YAML files, validation, atomic snapshot swap |
| Pricing engine | IMPLEMENTED | Per-enchantment/level prices, fallback, clamps, split payment |
| Discount engine | IMPLEMENTED | PRESERVE/DISABLE/CAP, hero amplifier, minimum floor |
| Villager rarity | IMPLEMENTED | Weighted enchantment + level selection, treasure/disabled rules |
| Villager trade integration | IMPLEMENTED | Acquire/replenish/player-trade, restock defence, migration |
| Enchanting-table rarity | IMPLEMENTED | Preview + result, treasure exclusion, multiple enchantments |
| Level probabilities | IMPLEMENTED | Normalised over the legal range, never illegal levels |
| Source restrictions | IMPLEMENTED | Villager, table, loot, fishing |
| SQLite | IMPLEMENTED | HikariCP + sqlite-jdbc, WAL, foreign keys, busy timeout |
| MySQL/MariaDB | IMPLEMENTED | HikariCP + mysql-connector-j, no secrets in logs |
| Migrations | IMPLEMENTED | V001–V004 per dialect, checksums, transactional apply, SQLite backup |
| Repositories | IMPLEMENTED | Villager, Trade, AuditLog, Statistics (prepared statements only) |
| Async database layer | IMPLEMENTED | Bounded executor, counters, graceful shutdown |
| Commands | IMPLEMENTED | `help/info/reload/price/setprice/rarity/database/diagnostics` + tab completion |
| Permissions | IMPLEMENTED | `nexusenchants.*` in plugin.yml and `Permissions` |
| Public API | IMPLEMENTED | `NexusEnchantsApi` 1.0.0 |
| Diagnostics | IMPLEMENTED | `/vbe diagnostics`, degraded-state reporting |
| Unit tests | WRITTEN | 127 test methods across 14 classes; **not executed** |
| Integration tests | WRITTEN | MockBukkit lifecycle/command/config/enchanting; **not executed** |
| Database tests | WRITTEN | Real SQLite file, migrations, constraints, executor; **not executed** |
| Statistical tests | WRITTEN | 1,000,000-iteration chi-square suite; **not executed** |
| GitHub Actions | WRITTEN | `ci.yml`, `release.yml`; **never run** |
| Documentation | IMPLEMENTED | README, AGENTS, INIT, docs/*.md, LICENSE, THIRD_PARTY_NOTICES |

Allowed statuses: NOT STARTED · IN PROGRESS · IMPLEMENTED · CI VERIFIED · BLOCKED · NEEDS REVIEW.
Nothing is `CI VERIFIED` yet — that label requires a linked, successful Actions run.

---

## F. Current development state

- **Last completed phase:** Phase 8 (implementation, tests, docs and workflows written).
- **Current active task:** none in progress; awaiting the first GitHub Actions run.
- **Files created (this session):** the entire repository (see `docs/DEVELOPMENT_STATUS.md` for the
  full inventory): Gradle build + wrapper, `config/checkstyle/checkstyle.xml`, 78 main sources,
  14 test sources, 7 YAML resources, 8 migration scripts, 2 workflows, README/AGENTS/INIT,
  `docs/*.md`, LICENSE, THIRD_PARTY_NOTICES.
- **Files modified after the first pass (static review fixes):**
  - `build.gradle` — Gradle 9 Groovy-DSL hardening (explicit `Action` for `relocate`,
    `mergeServiceFiles('META-INF/services')`, fully-qualified task types).
  - `AsyncDatabaseExecutor` — rejection handler now throws so `submit` can report rejection;
    `submit(String, Runnable)` disambiguated with a `SqlTask` cast.
  - `VillagerRarityManager` — added the missing `EnchantmentLevelManager` import.
  - `VillagerTradeManager` — profession key resolved through `Keyed` instead of assuming `getKey()`.
  - `RarityCommands`, `PricingCommands`, `HelpCommand`, `DiagnosticsCommands`, `DatabaseManager` —
    replaced inline fully-qualified names with imports.
- **Known bugs:** none known; nothing has been compiled, so no defect can be ruled out.
- **Unresolved questions:** none blocking. Open verification items are listed in
  `docs/DEVELOPMENT_STATUS.md` § "Outstanding work".
- **Next recommended task:** push to GitHub, let `ci.yml` run, then fix whatever the compiler and
  tests report and mark modules `CI VERIFIED` with the run URL.

---

## G. Test status

| Metric | Value |
|---|---|
| Test classes | 14 |
| Test methods | 127 |
| Unit tests (pure logic) | 51 (`PriceMath` 7, `PriceCalculator` 13, `DiscountManager` 12, `WeightedTable` 12, `PaymentPlan` 7) |
| Statistical tests | 7 (`StatisticalDistributionTest`, `@Tag("statistical")`, 1,000,000 iterations each) |
| Database tests | 20 (`SQLiteProviderTest` 8, `MigrationManagerTest` 6, `AsyncDatabaseExecutorTest` 6) |
| MockBukkit integration tests | 31 (`PluginLifecycleTest` 10, `ConfigManagerTest` 9, `EnchantingSelectionTest` 12) … plus 12 resource-validation tests |
| Resource validation tests | 12 (`ResourceIntegrityTest`) |
| Tests executed | **0** — no build has been run anywhere yet |
| CI run URL | none |
| Failed tests | unknown |
| Skipped tests | unknown |

Never invent test results. Update this table only from an actual Actions run.

---

## H. Database status

- **Schema version:** 4 (`V001`–`V004`, both dialects).
- **Tables:** `schema_migrations`, `managed_villagers`, `managed_trades`, `audit_logs`,
  `rarity_statistics` (+ indexes `idx_managed_villagers_world`, `idx_managed_trades_villager`,
  `idx_audit_logs_created`, `idx_audit_logs_event`).
- **Constraints:** unique `managed_villagers.villager_uuid`; unique
  `managed_trades (villager_uuid, trade_key)`; FK `managed_trades -> managed_villagers` with
  `ON DELETE CASCADE`; CHECK constraints on price ≥ 0, level ≥ 1 and selection count ≥ 0.
- **Migration history:** none applied outside tests yet; `MigrationManager` records
  version/description/timestamp/SHA-256 on apply and refuses a mutated released file.
- **Defaults:** SQLite file `plugins/NexusEnchants/nexusenchants.db`, WAL, foreign keys on,
  busy timeout 5000 ms, pool 5.
- **Backup:** a `.backup` copy of the SQLite file is written before pending migrations run.
- **Outstanding migration work:** none planned; new schema changes require `V005__*.sql` in **both**
  dialect folders.
- **Compatibility notes:** MySQL 8.0+ / MariaDB 10.6+ with `utf8mb4`; the account needs
  `CREATE, ALTER, INDEX, SELECT, INSERT, UPDATE, DELETE` on the plugin schema. Database settings
  require a server restart (not a `/vbe reload`).

---

## I. API compatibility (verified against Paper 26.3)

| API | Usage | Status |
|---|---|---|
| `MerchantRecipe` (`setIngredients`, `setIgnoreDiscounts`, `setSpecialPrice`, `setPriceMultiplier`, `setDemand`, `setVillagerExperience`) | price + discount control | verified |
| `VillagerAcquireTradeEvent` / `VillagerReplenishTradeEvent` (`getEntity`, `getRecipe`, `setRecipe`) | trade generation + restock | verified |
| `PlayerTradeEvent` (`getVillager`, `getTrade`/`setTrade` inherited from `PlayerPurchaseEvent`) | price floor guard | verified |
| `PrepareItemEnchantEvent#getOffers` → `EnchantmentOffer` (`getCost`, `setEnchantment`, `setEnchantmentLevel`) | preview rewrite | verified |
| `EnchantItemEvent` (`getEnchantsToAdd`, `whichButton`, `getEnchanter`) | result rewrite | verified |
| `LootGenerateEvent` (`getLoot`, `setLoot`) | loot restriction | verified |
| `PlayerFishEvent#getCaught` → `Item#getItemStack/setItemStack` | fishing restriction | verified |
| `AbstractVillager#getRecipes/setRecipes`, `Villager#getProfession/setProfession`, `Profession.LIBRARIAN` | villager control | verified |
| `RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)` | enchantment lookup | verified |
| `Enchantment` (`isTreasure`, `canEnchantItem`, `conflictsWith`, `getMaxLevel`, `getStartLevel`) | eligibility | verified |

**Unsupported / documented limitations** (details in `docs/API_COMPATIBILITY.md`):

1. No per-player merchant price hook — CAP/DISABLE suppress vanilla discounts instead of capping them;
   curing and reputation discounts cannot be recomputed.
2. A single trade cannot exceed 128 emeralds (two 64-item input slots).
3. Extra (non-primary) enchantments from a combination cannot be previewed in the table GUI.
4. Anvil, grindstone, chest transfer and creative-menu sources are not intercepted.
5. `MerchantRecipe` has no `setResult`; replacements are rebuilt from the result item and max uses.

---

## J. Change history

| Date | Work performed | Files | Tests added | Remaining issues |
|---|---|---|---|---|
| 2026-10-30 | Phase 0 — repository inspection, version pinning (Java 25, Paper 26.3.build.200-stable, Gradle 9.8.2, HikariCP 7.1.0, sqlite-jdbc 3.54.1.0, mysql-connector-j 9.7.0, JUnit 6.1.3, MockBukkit 4.119.0, Shadow 9.6.1, Checkstyle 14.3.0) | `settings.gradle`, `build.gradle`, `gradle.properties`, wrapper, `.gitignore`, `.gitattributes`, `.editorconfig`, checkstyle config, `plugin.yml` | — | no build run (by rule) |
| 2026-10-30 | Phase 1–6 — configuration, pricing, discounts, villager rarity + trade integration, enchanting, sources, database layer, migrations, repositories, commands, permissions, diagnostics, public API | 78 main sources, 7 YAML resources, 8 migrations | — | not compiled |
| 2026-10-30 | Phase 7 — anti-exploit guards (restock re-pricing, duplicate-recipe prevention, price floor, inventory-safe payments), thread-safety (bounded async executor, main-thread hand-back), database reliability (bounded queue, graceful degradation) | `VillagerTradeManager`, `AsyncDatabaseExecutor`, `DatabaseManager` | — | not compiled |
| 2026-10-30 | Phase 8 — static consistency audit (imports, cross-package references, nested types), 127 tests written, documentation and workflows written | `src/test/**` (14), `README.md`, `AGENTS.md`, `INIT.md`, `docs/**`, `.github/workflows/*.yml` | 127 | awaiting first CI run |

---

## Handover checklist (do this at the end of every session)

- [ ] Update this file: phase, status table, test table, change history.
- [ ] Record every file created or modified.
- [ ] Record anything incomplete, any known bug, any new limitation.
- [ ] Confirm that no build/compile command was executed locally.
- [ ] State the next recommended task.
- [ ] Update `docs/DEVELOPMENT_STATUS.md` and `docs/CHANGELOG.md` in the same pass.
