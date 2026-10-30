# Development status

Last updated: 2026-10-30 — phase 8 (final verification preparation).

**No compilation, test run or packaging has been performed. Nothing is CI verified.**
The first build is performed by `.github/workflows/ci.yml` after the repository is pushed.

## 1. Status summary

| Area | Status |
|---|---|
| Source code | IMPLEMENTED (78 files), **not compiled** |
| Automated tests | WRITTEN (127 methods, 14 classes), **not executed** |
| Configuration resources | IMPLEMENTED (7 files), parsed by `ResourceIntegrityTest` at CI time |
| Migrations | IMPLEMENTED (V001–V004 × 2 dialects), applied only in tests |
| Documentation | IMPLEMENTED |
| CI/CD workflows | WRITTEN, **never run** |

## 2. Implemented functionality

- **Pricing** — per-enchantment/per-level prices, fallback, minimum/maximum limits, zero-price
  opt-in, split emerald payments up to 128, immutable snapshots, command-driven persistence.
- **Discounts** — `PRESERVE`/`DISABLE`/`CAP`, hero amplification, cap clamping, minimum floor,
  deterministic rounding, `setIgnoreDiscounts` integration.
- **Villager rarity** — weighted enchantment and level selection, treasure and disabled rules,
  source gating, table construction per snapshot.
- **Villager trade integration** — acquire/replenish/player-trade handling, duplicate-recipe
  prevention, restock re-pricing, price floor guard, book-trade cap, experience preservation,
  one-time migration of existing librarians (prices only, never re-rolled).
- **Enchanting table** — plan cache (60 s, per player), preview rewrite, result application,
  treasure exclusion, multiple-enchantment chance with compatibility checks, source enforcement,
  quit-time cleanup.
- **Level probabilities** — normalised over the legal range, per-enchantment overrides.
- **Sources** — villager, enchanting table, loot, fishing; per-enchantment rules with defaults.
- **Persistence** — HikariCP, SQLite (WAL, FK, busy timeout) and MySQL/MariaDB, bounded async
  executor, repositories with prepared statements only, audit trail, aggregated statistics.
- **Migrations** — discovery, ordering, SHA-256 checksums, transactional apply, SQLite backup.
- **Commands** — `help`, `info`, `reload`, `price`, `setprice`, `rarity list|villager|enchanting|
  set|test`, `database status|migrations`, `diagnostics`, with validation and tab completion.
- **Permissions** — `nexusenchants.*` registered in `plugin.yml` and enforced per subcommand.
- **Diagnostics** — configuration issues, degraded services, executor counters, unsupported sources.
- **Public API** — `NexusEnchantsApi` 1.0.0 via the services manager.

## 3. Files

```text
Build        settings.gradle, build.gradle, gradle.properties, gradlew, gradlew.bat,
             gradle/wrapper/gradle-wrapper.properties, gradle/wrapper/gradle-wrapper.jar,
             .gitignore, .gitattributes, .editorconfig, config/checkstyle/checkstyle.xml
Main source  78 files under src/main/java/com/nexus/enchants/
Resources    plugin.yml, config.yml, pricing.yml, villager-rarity.yml, enchanting-rarity.yml,
             enchantment-levels.yml, enchantment-sources.yml, discounts.yml,
             db/migration/sqlite/V001..V004, db/migration/mysql/V001..V004
Tests        14 files under src/test/java/com/nexus/enchants/
CI           .github/workflows/ci.yml, .github/workflows/release.yml
Docs         INIT.md, AGENTS.md, README.md, docs/*.md (10), LICENSE, THIRD_PARTY_NOTICES.md
```

## 4. Tests written vs executed

| | Written | Executed |
|---|---|---|
| Unit tests | 51 | 0 |
| Database tests | 20 | 0 |
| MockBukkit integration tests | 37 | 0 |
| Resource validation tests | 12 | 0 |
| Statistical tests | 7 | 0 |
| **Total** | **127** | **0** |

## 5. Outstanding work

Verification (must be done by GitHub Actions / a real server):

1. **Compile** the project (first CI run) and fix any compiler error.
2. **Run** the full suite; address failures, especially MockBukkit API drift.
3. **Confirm** `checkstyle` passes with `maxWarnings=0`.
4. **Confirm** the shaded jar contains relocated HikariCP, the JDBC drivers and `plugin.yml` with the
   expanded version.
5. **Real-server verification** of merchant payment, previews, loot/fishing and restart persistence
   (procedure in `docs/TESTING.md` §6) — cannot be automated here.

Deliberately deferred:

6. MySQL/MariaDB integration tests in CI (needs a service container; see `docs/TESTING.md` §8).
7. SpotBugs/PMD (class-file 69 support unverified — ADR-010).
8. An autonomy layer for anvil/grindstone sources (not exposed by the API in a safe way).

## 6. Known risks

1. **MockBukkit fidelity.** MockBukkit 4.119.0 targets Paper 26.3.build.200-stable. If any handler
   relies on behaviour the mock does not implement, an integration test may fail for reasons
   unrelated to the plugin's logic; the failure must be investigated, not silenced.
2. **Shadow 9 relocation API.** `build.gradle` passes an explicit `Action` to `relocate(...)` and
   `mergeServiceFiles('META-INF/services')`; both are stable APIs but were selected to avoid Groovy
   default-argument pitfalls and have not been executed yet.
3. **Gradle 9 Groovy DSL strictness.** Property assignment and fully-qualified task types are used
   deliberately; any deprecation warning that fails `--warning-mode all` must be fixed.
4. **Client behaviour** can never be proven by unit tests — see item 5 above.

## 7. Next recommended task

Push the repository to GitHub, let `ci.yml` run, then fix the reported compiler/test failures and
record the run URL in `INIT.md` § G, promoting modules to `CI VERIFIED`.
