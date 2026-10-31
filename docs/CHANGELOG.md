# Changelog

All notable changes to NexusEnchants are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> Entries below describe released functionality. The build, the full test suite and the shaded jar
> are verified by GitHub Actions (run
> [41083616077](https://github.com/vivo6810/NexusEnchants/actions/runs/41083616077));
> live-server verification is tracked in `docs/DEVELOPMENT_STATUS.md`.

## [Unreleased]

### Changed (2026-10-31 compatibility and distribution pass)

- **`api-version` lowered to `26.2`** so the same jar loads on Purpur/Paper 26.2 and later 26.x
  servers; the API the code compiles against is unchanged (ADR-014).
- **Single shaded jar** — the internal `-plain` jar is no longer produced, the CI artifact contains
  exactly the shaded plugin jar, and the release workflow can no longer pick a dependency-less jar
  by accident (the release's `find` previously tended to select `-plain.jar` first).
- **Jar size reduced from ~17 MB to ~4–5 MB** — unused sqlite-jdbc native platforms (FreeBSD,
  musl, 32-bit ARM/Windows, riscv64, ppc64) and the unused MySQL X-DevAPI/protobuf payload are
  excluded; CI now fails above 7 MB (ADR-013).
- GitHub Actions updated off the Node.js 20 runtime deprecation: `checkout@v7`, `setup-java@v6`,
  `setup-gradle@v6` (which validates wrapper checksums automatically, replacing the separate
  wrapper-validation action), `upload-artifact@v7`, `action-gh-release@v3`, runner pinned to
  `ubuntu-24.04`.

### Added (2026-10-31 review pass)

- New tests raising direct coverage to 18 classes: `RepositoriesTest` (real temporary SQLite —
  villager upsert semantics incl. registration-timestamp preservation, trade key-unique rewrites,
  audit retention, statistics accumulation), `TradeCacheTest`, `RarityStatisticsStoreTest` and
  `ConfigValidatorTest` (registry-free helper contract).
- Locale-independent journal-mode normalisation (`ConfigValidator`).


### Added

- **Villager book pricing** — per-enchantment, per-level prices with configurable fallback,
  minimum/maximum limits and zero-price opt-in. Prices above one stack are paid as `64 + remainder`
  across the two merchant input slots, hard-capped at 128 emeralds.
- **Villager book rarity** — weighted enchantment selection for librarians, per-enchantment and
  per-level weights, treasure-enchantment policy, enable/disable per enchantment.
- **Trade integration** — acquire/replenish/player-trade handling, restock re-pricing, duplicate
  recipe prevention, one-time migration of existing librarians (prices only), experience preservation.
- **Enchanting-table rarity** — weighted enchantment and level selection, preview and result written
  from the same cached plan, treasure exclusion, configurable multiple-enchantment chance with
  compatibility checks.
- **Level probabilities** — independent level weights with per-enchantment overrides, normalised over
  the levels that are legal for the enchantment.
- **Acquisition sources** — per-enchantment rules for librarian trades, enchanting table, loot
  generation and fishing, plus configurable defaults.
- **Discount policy** — `PRESERVE`, `DISABLE` and `CAP` (default cap 20%), Hero of the Village
  amplification, minimum-price floor, deterministic rounding.
- **Persistence** — SQLite (default) and MySQL/MariaDB behind HikariCP, bounded asynchronous executor,
  repositories with prepared statements, audit trail and aggregated statistics.
- **Migrations** — V001–V004 for both dialects with SHA-256 checksums, transactional application and an
  automatic SQLite backup before pending migrations.
- **Commands** — `/vbe help|info|reload|price|setprice|rarity …|database …|diagnostics` with argument
  validation, consistent formatting and tab completion.
- **Permissions** — `nexusenchants.admin` plus five granular permissions, registered in `plugin.yml`.
- **Public API** — `NexusEnchantsApi` 1.0.0 registered in Bukkit's services manager.
- **Diagnostics** — configuration issues, degraded services, executor counters, unsupported sources.
- **Testing** — 127 test methods: unit, database (real SQLite), MockBukkit integration, resource
  validation and a 1,000,000-iteration statistical suite.

### Fixed

- A librarian rarity system that is set to `selection-mode: DISABLED` no longer exposes a weighted
  table; probability queries and command output report no selectable enchantments.
- **CI/CD** — `ci.yml` (wrapper validation, Java 25, `clean check build`, artifact upload) and
  `release.yml` (tag-driven release with the shaded jar attached).
- **Documentation** — `README.md`, `AGENTS.md`, `INIT.md`, `docs/ARCHITECTURE.md`,
  `docs/DATABASE.md`, `docs/API_COMPATIBILITY.md`, `docs/TESTING.md`, `docs/CONFIGURATION.md`,
  `docs/DEVELOPMENT_STATUS.md`, `docs/DECISIONS.md`, `docs/TROUBLESHOOTING.md`, `docs/SECURITY.md`,
  `THIRD_PARTY_NOTICES.md`, MIT `LICENSE`.

### Known limitations

- Vanilla curing/reputation discounts cannot be capped per player; under `CAP`/`DISABLE` they are
  suppressed and only Hero of the Village is re-applied.
- A single trade cannot exceed 128 emeralds.
- Additional enchantments from a combination cannot be previewed in the table GUI.
- Anvil, grindstone and plugin-granted items are not intercepted.
- Database settings require a restart rather than `/vbe reload`.

## [0.1.0] — unreleased

Initial development version; equivalent to the Unreleased section above. No release has been published.
