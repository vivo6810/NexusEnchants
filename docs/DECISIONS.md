# Architecture decision record

Every significant decision is recorded here and referenced from `INIT.md` § D.
Never reverse a decision silently: add a new ADR that supersedes the old one and say why.

---

## ADR-001 — SQLite is the default database

**Decision.** Plugin state is stored in an embedded SQLite database by default; MySQL/MariaDB is an
opt-in alternative.

**Reason.** A survival server should not need an external database to install a plugin, and SQLite
removes an entire class of setup mistakes. Both back ends sit behind `DatabaseProvider`, so switching
is a configuration change.

**Consequences.** Off-server backups must copy one file. Concurrent writers are serialized by SQLite;
WAL plus a busy timeout keeps that invisible at plugin scale. MySQL/MariaDB is required only for
multi-server setups.

---

## ADR-002 — YAML is the single source of truth for gameplay values

**Decision.** Prices, weights, level probabilities, source rules, discount policy and settings exist
only in YAML. SQL stores operational state (managed villagers, trades, audit, statistics).

**Reason.** Two writers for one value always drift. Keeping configuration in files means an admin can
diff, review and roll back gameplay changes in version control.

**Consequences.** Commands that change a value must rewrite YAML (`ConfigManager#writePrice` etc.)
and then reload, rather than updating a database row. SQL is never consulted for a price.

---

## ADR-003 — Builds happen only in GitHub Actions

**Decision.** The development agent writes code, tests, configuration and workflows but never runs
Gradle. Compilation, testing and packaging happen in CI.

**Reason.** A build that is claimed but not run is worse than no build. Centralizing it also means one
reproducible definition of "verified".

**Consequences.** Local IDE feedback is not available to agents; static review and class-file
inspection substitute for it. A module may only be called `CI VERIFIED` with an Actions run URL.

---

## ADR-004 — All JDBC work runs on a bounded executor

**Decision.** `AsyncDatabaseExecutor` (2 workers, 512-entry queue) owns every database call. Handlers
capture immutable data on the main thread and receive results back through the server scheduler.

**Reason.** A single slow query on the tick thread causes visible lag and can trigger the watchdog.
An unbounded queue would convert a slow database into an out-of-memory crash.

**Consequences.** Repository methods take values and records, never entities. Rejections are counted
and logged; the plugin degrades rather than queueing indefinitely. Diagnostics expose the counters.

---

## ADR-005 — Java 25, Paper API 26.3.build.200-stable, Gradle 9.8.2

**Decision.** Target Java 25 and pin the Paper API to `26.3.build.200-stable`; use the Gradle 9.8.2
wrapper with a pinned distribution checksum.

**Reason.** Paper 26.3 ships class files with major version 69, which requires Java 25. MockBukkit
4.119.0 declares `Paper-Version: 26.3.build.200-stable` in its manifest and throws
`IncompatiblePaperVersionException` on a mismatch, so the API build is pinned to that exact value.

**Consequences.** No Java 17/21 support. Upgrading Paper requires upgrading MockBukkit, Java and the
API version together.

---

## ADR-006 — Shadow relocation limited to HikariCP

**Decision.** `com.gradleup.shadow` 9.6.1 shades HikariCP, sqlite-jdbc and mysql-connector-j. Only
HikariCP is relocated, to `com.nexus.enchants.libs.hikari`. Service files are merged and signature
files excluded.

**Reason.** Another plugin may bundle a different HikariCP version; relocating removes the conflict.
The JDBC drivers rely on `META-INF/services/java.sql.Driver` and native resources, so relocating them
would break driver discovery.

**Consequences.** The shaded jar is the distributable; the plain jar is internal and must not be
deployed.

---

## ADR-007 — Multi-stack payment is capped at 128 emeralds

**Decision.** `PaymentPlan` splits a price into `64 + remainder` across the two merchant input slots
and refuses anything above 128. Configuration values above the cap are rejected at load time.

**Reason.** A merchant recipe has exactly two input slots holding at most 64 items each. A larger
single-trade price cannot be represented, and silently charging less would be an economy exploit.

**Consequences.** The popular "Mending costs 70" default works exactly (64 + 6). An operator wanting a
500-emerald book must model it as a different trade or accept that the plugin refuses the value.

---

## ADR-008 — Enchanting plans are decided at preview time

**Decision.** `PrepareItemEnchantEvent` builds the plan (enchantment, level, extras) and caches it per
player for 60 seconds; `EnchantItemEvent` applies the cached plan for the clicked slot.

**Reason.** Computing independent results in the two events allows the displayed offer to differ from
what the player receives, which players perceive as cheating. Caching also prevents reroll-by-reopen
abuse within the window.

**Consequences.** Plans expire after a minute or on quit. Extra enchantments beyond the primary cannot
be previewed because `EnchantmentOffer` holds only one enchantment.

---

## ADR-009 — Discount policy suppresses rather than caps vanilla providers

**Decision.** Under `CAP` and `DISABLE`, recipes are marked `setIgnoreDiscounts(true)` and the plugin
re-applies only the discounts it can read (Hero of the Village), clamped to the configured maximum.
`PRESERVE` leaves vanilla untouched.

**Reason.** Paper 26.3 exposes no per-player merchant price hook, so curing and reputation discounts
cannot be read or capped in place. Claiming otherwise would be a fake implementation.

**Consequences.** The documented behaviour is "suppressed", not "capped". `/vbe diagnostics` says
which providers were suppressed. Operators who want vanilla economics choose `PRESERVE`.

---

## ADR-010 — Checkstyle only; SpotBugs deferred

**Decision.** Checkstyle 14.3.0 with a narrow, objective rule set (imports, braces, switch defaults,
banned `System.out`/`printStackTrace`, trailing whitespace) and `maxWarnings = 0`. SpotBugs and PMD
are not enabled.

**Reason.** SpotBugs support for class-file major 69 was not verified; shipping an analyser that
might crash or silently skip classes would create false confidence. Checkstyle rules here cannot
produce false positives.

**Consequences.** Some defect classes (null dereference, resource leaks) are not machine-checked.
This is recorded as outstanding work in `docs/DEVELOPMENT_STATUS.md`.

---

## ADR-011 — Existing librarians are migrated by price only

**Decision.** `VillagerMigrationManager` reprices managed librarians once and never re-rolls existing
enchantments. Trades whose enchantment source became disallowed are removed.

**Reason.** Re-rolling would destroy trades players already paid for, and could not be undone.
Repricing is idempotent and safe on every restart.

**Consequences.** A librarian created before the plugin existed may keep an enchantment that is now
disabled, unless its source is disallowed; operators who need a clean slate must re-roll the villager
themselves.

---

## ADR-012 — Statistical verification is its own test category

**Decision.** Distribution tests use `@Tag("statistical")`, run in a dedicated `statisticalTest` task
wired into `check`, with 1,000,000 iterations and a 0.1% chi-square bound.

**Reason.** A million iterations is slow for an inner development loop but is exactly what makes a
weighted-random system trustworthy; separating the tag keeps `./gradlew test` fast without removing
the verification from CI.

**Consequences.** Changing a shipped weight requires updating the expected values in the statistical
suite and the resource integrity test in the same commit.
