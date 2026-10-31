# Development status

Last updated: 2026-10-30 — phase 9 (CI verified).

**Compiled, tested and packaged by GitHub Actions run
[41083616077](https://github.com/vivo6810/NexusEnchants/actions/runs/41083616077) on commit
`b98b668`: 127 tests passed, checkstyle clean, plugin jar uploaded.** Nothing was compiled locally
(project rule). Live-server behaviour is still unverified — see § 5.

## 1. Status summary

| Area | Status |
|---|---|
| Source code | CI VERIFIED (78 files compiled) |
| Automated tests | CI VERIFIED (127 methods, 14 classes, 0 failures) |
| Configuration resources | CI VERIFIED (7 files, parsed by `ResourceIntegrityTest`) |
| Migrations | CI VERIFIED (V001–V004 × 2 dialects, applied in tests) |
| Documentation | IMPLEMENTED |
| CI/CD workflows | `ci.yml` CI VERIFIED; `release.yml` not yet exercised (no tag pushed) |

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

| | Written | Executed | Passing |
|---|---|---|---|
| Unit tests | 51 | 51 | 51 |
| Database tests | 20 | 20 | 20 |
| MockBukkit integration tests | 37 | 37 | 37 |
| Resource validation tests | 12 | 12 | 12 |
| Statistical tests | 7 | 7 | 7 |
| **Total** | **127** | **127** | **127** |

All 127 ran in run 41083616077; the 120 non-statistical tests in `:test`, the 7 statistical tests in
`:statisticalTest`, 0 failures and 0 skipped.

## 5. Outstanding work

Verification completed by CI:

1. ~~Compile the project and fix compiler errors.~~ Done across runs 41074773260, 41079476259 and
   41083616077 (stale overloads, `submitSql` call sites, final plugin class).
2. ~~Run the full suite; address failures.~~ Done — 39 then 8 then 0 failing tests.
3. ~~Confirm checkstyle passes with `maxWarnings=0`.~~ Passing.
4. ~~Confirm the shaded jar and upload it.~~ `NexusEnchants` jar (16.9 MB) uploaded by the run.

Verification that only a real server can provide:

5. **Live-server verification** of merchant payment, previews, loot/fishing and restart persistence
   (procedure in `docs/TESTING.md` §6) — cannot be automated here.
6. **Inspect the shaded jar** once on a Paper 26.3 server: relocated HikariCP, `plugin.yml` version,
   migration application on a fresh data folder.

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

Run the plugin on a Paper 26.3 test server: check `/vbe diagnostics` on a fresh install, buy a 70-
emerald Mending book (split payment), confirm a restart keeps prices, and push a `v0.1.0` tag so
`release.yml` produces its first release.

---

## Outstanding work

- **Verified on a live server (2026-10-31 report):** the first shaded jar could not load on
  Purpur 26.2 because `plugin.yml` said `26.3`; fixed by ADR-014. Replace the installed jar with the
  new shaded build and retry (`api-version: '26.2'`, single jar, ~4 MB, ADR-013).
