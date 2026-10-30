# Troubleshooting

Start with `/vbe diagnostics`: it lists configuration problems, degraded services, executor counters
and unsupported sources.

## The plugin does not start / disables itself

1. Check the console for `NexusEnchants failed to start and will be disabled` — the stack trace names
   the failing service.
2. Verify Java and server versions: Java 25 and Paper 26.3 are required (class file major 69).
3. Verify the jar is the **shaded** one (`NexusEnchants-<version>.jar`, not `-plain.jar`).
4. If the database cannot be opened, the plugin retries with degraded persistence; a failure to build
   the configuration is fatal and logged as a configuration error.

## Prices are not applied to a librarian

| Cause | Check |
|---|---|
| System disabled | `config.yml`: `settings.enabled: true` |
| No price configured and fallback off | `/vbe price MENDING 1`; `pricing.yml` `fallback.enabled` |
| Price above the cap | `limits.maximum` must be ≥ the configured price (max 128) |
| Villager is not a librarian | Only `Profession.LIBRARIAN` trades are priced |
| Trade was created before installation | Run `/vbe reload`; migration reprices on the next generation |
| Ancient villager with a disallowed enchantment | The trade is dropped instead of repriced |

## A trade charges fewer emeralds than configured

Vanilla discount providers can reduce the price on the client. Under the default `CAP` policy the
plugin suppresses them and marks recipes with `setIgnoreDiscounts(true)`. If you set
`policy: PRESERVE`, discounts are intentional. `/vbe trade` floors are re-validated before payment:
a trade that would charge less than the configured price is cancelled and repaired on restock.

## The Mending book costs 64 + 6 instead of 70

That is by design: a merchant recipe has two input slots of at most 64 items each, so 70 emeralds are
paid as two stacks. The total is exactly 70; the client shows two ingredient slots.

## Mending appears from the enchanting table

1. `enchanting-rarity.yml`: `enchanting-table.enchantment-weights.MENDING.enabled` must be `false`.
2. `enchantment-sources.yml`: `MENDING.enchanting-table` must be `false`.
3. `treasure-enchantments.enabled` should be `false` in most configurations.
4. Reload and re-open the table — an already open GUI keeps its previous offers.

## Extra enchantments do not show in the preview

Expected: `EnchantmentOffer` carries one enchantment per slot, so only the primary is previewed. The
extra enchantments are still applied from the recorded plan. See `docs/API_COMPATIBILITY.md` §2.3.

## A configuration change is ignored

- `/vbe reload` re-reads all YAML files; a syntax error keeps the previous valid snapshot. The error
  is printed and listed by `/vbe diagnostics`.
- **Database settings need a restart** (`database.type`, pool and connection settings).
- Weights of `0` mean "never selected", not "use the default".

## `checksum mismatch` on startup

A released migration file was modified after it was applied. Restore the original file from the
published jar, or restore the database from a `.backup` file. Never edit the recorded checksum.

## Database problems

| Symptom | Action |
|---|---|
| `The database pool is closed` | The plugin is shutting down, or initialization failed; check the earlier stack trace |
| SQLite `database is locked` | Another process has the file open, or a long transaction is running; the busy timeout is `sqlite.options.busy-timeout` |
| MySQL connection refused | Verify host/port/credentials and the grants listed in `docs/DATABASE.md` §3 |
| Rejected database tasks in diagnostics | The queue is saturated; reduce trade volume or increase `settings.async.queue-capacity` |

## Performance concerns

- Heap: statistics are aggregated in memory and flushed on an interval; the map size grows only with
  the number of enchantment/level pairs.
- Tick lag: verify with `/vbe diagnostics` that no main-thread JDBC is happening — the queue depth and
  the completed/failed counters should move while the server is not ticking blocked.
- Large villager populations: the trade cache is keyed by villager UUID and bounded per world; entries
  are dropped when a villager is no longer valid.

## Permissions

Commands answer "you do not have permission" when the sender lacks `nexusenchants.*`. Grant
`nexusenchants.admin` (children grant every sub-permission) or the individual permission from
`README.md`. Console always has permission.

## Reporting a problem

Include: Paper version, Java version, plugin version (`/vbe info`), the output of `/vbe diagnostics`,
the relevant configuration files with secrets removed, and the full stack trace.
