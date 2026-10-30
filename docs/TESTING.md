# Testing

> **Nothing in this repository has been executed yet.** The suite below is written and reviewed
> statically; the first real execution happens in GitHub Actions. Treat every statement about
> expected behaviour as a specification until CI proves it.

## 1. Test categories

| Category | Classes | Count | Needs | Runs on every CI run |
|---|---|---|---|---|
| Unit (pure logic) | `PriceMathTest`, `PriceCalculatorTest`, `DiscountManagerTest`, `WeightedTableTest`, `PaymentPlanTest` | 51 | nothing | yes |
| Database (real SQLite file) | `SQLiteProviderTest`, `MigrationManagerTest`, `AsyncDatabaseExecutorTest` | 20 | `@TempDir` filesystem | yes |
| Integration (MockBukkit) | `PluginLifecycleTest`, `ConfigManagerTest`, `EnchantingSelectionTest`, `VillagerRarityManagerTest` | 37 | MockBukkit 4.119.0 | yes |
| Resource validation | `ResourceIntegrityTest` | 12 | classpath resources | yes |
| Statistical | `StatisticalDistributionTest` (`@Tag("statistical")`) | 7 | 1,000,000 iterations each | yes (`statisticalTest`) |

Total: **127 test methods across 14 classes.**

## 2. How to run

```bash
# everything CI runs (unit + database + integration + resources + statistical + checkstyle)
./gradlew --no-daemon clean check build

# fast loop: everything except the statistical suite (the `test` task excludes the tag)
./gradlew test

# only the 1,000,000-iteration distribution tests
./gradlew statisticalTest

# single class
./gradlew test --tests 'com.nexus.enchants.economy.PriceCalculatorTest'
```

`check` depends on `statisticalTest`, so CI cannot pass while a distribution test fails.

## 3. Coverage map (specification IDs → tests)

| Spec ID | Covered by |
|---|---|
| P001–P012 | `PriceCalculatorTest`, `PriceMathTest`, `ConfigManagerTest` (P011) |
| D001–D010 | `DiscountManagerTest`, `PriceMathTest` (D009/D010) |
| W001–W010 | `WeightedTableTest`, `EnchantingSelectionTest` (W002) |
| 1M-iteration statistics | `StatisticalDistributionTest` (chi-square, 2/3/5 categories, zero weight, disabled, large weights, seeded, level weights) |
| V003 (selection) | `VillagerRarityManagerTest` |
| V005/V011/V012/V014 | partly `VillagerRarityManagerTest` + `TradeGenerationManager` logic exercised through `EnchantingSelectionTest`-style construction; full villager entity integration requires a real server (see §6) |
| DB001, DB003, DB005–DB012, DB015–DB017, DB020 | `SQLiteProviderTest` |
| DB013/DB014 (unavailable / recovery) | `AsyncDatabaseExecutorTest` (bounded queue and rejection) plus documented manual procedure |
| M001–M010 (merchant interface) | arithmetic covered by `PaymentPlanTest`; **client completion requires a real Paper server** |
| E003–E013 | `EnchantingSelectionTest` |
| Source restrictions | `EnchantingSelectionTest#sourceRestrictionWins`, `VillagerRarityManagerTest` |
| Commands/permissions | `PluginLifecycleTest` |
| Lifecycle | `PluginLifecycleTest` (enable, disable, re-enable, repeated reload) |
| Configuration matrix | `ConfigManagerTest` (defaults, missing file, invalid value, broken YAML, unknown key, repeated reload) |
| Plugin descriptor, YAML parsing, migration integrity | `ResourceIntegrityTest` |

## 4. Statistical methodology

- Sample size: **1,000,000 selections** per distribution test.
- Goodness of fit: Pearson's chi-square against the configured probabilities, compared with the
  critical value at the **0.1% significance level** (df 1 → 10.828, df 2 → 13.816, df 4 → 18.467).
- Frequency assertions use a normal-approximation tolerance of roughly 3–5 standard deviations, so a
  correct distribution effectively never fails and a wrong one (for example `MENDING` and
  `UNBREAKING` swapped) fails immediately.
- All randomness is seeded (`new Random(fixedSeed)`), so a failure is reproducible.
- Reference expectations: Mending weight 2 vs Unbreaking weight 12 → **14.2857%** and **85.7143%**;
  level weights 45/30/15/7/3 → 45%/30%/15%/7%/3%.

## 5. What MockBukkit does and does not prove

MockBukkit provides a real implementation of the Bukkit API surface: registry access, items,
players, commands, the event system and the scheduler. It therefore proves:

- the plugin enables, registers its command, listeners and services and shuts down cleanly;
- configuration files load, validate, reload and persist;
- selection, weighting, level clamping, source rules and compatibility logic behave as specified;
- commands honour permissions and never touch JDBC on the server thread.

It does **not** prove:

- the vanilla trade screen can complete a 70-emerald, two-slot payment (M001–M010);
- the enchanting GUI shows exactly the preview the plugin computed;
- loot-table and fishing behaviour in a real world;
- chunk unloading, world saves or restarts with a live server.

Those require a real Paper server; see §6.

## 6. Integration testing on a real server (required before a production release)

Mockbukkit cannot substitute for these checks. Procedure for a maintainer:

1. Start a Paper 26.3 test server with Java 25 and this jar in `plugins/`.
2. Spawn a librarian, place an enchanting table, `/vbe reload`.
3. **Merchant payment:** verify a 70-emerald Mending book is exactly `64 + 6`, that 69 emeralds cannot
   complete it, and that 71 emeralds leaves no duplicated items.
4. **Restock:** trade until the librarian restocks; confirm the price does not revert.
5. **Inventory:** trade with a nearly full inventory, close the interface mid-trade, disconnect
   mid-trade; confirm no item duplication or loss.
6. **Enchanting:** confirm preview equals result for all three slots; verify XP/lapis costs are
   unchanged; verify Mending and treasure enchantments never appear.
7. **Sources:** disable `FORTUNE.loot`, generate loot from a chest, confirm Fortune no longer
   appears; fish with `MENDING.fishing: false`, confirm the caught item is unaffected beyond that.
8. **Restart:** register a librarian, restart the server, confirm pricing and weights persist.
9. **Database:** run `/vbe database migrations`, confirm version 4 and no pending migrations; then
   test the MySQL path against a disposable schema.

Record the outcome in `docs/DEVELOPMENT_STATUS.md`.

## 7. Performance checks

The suite avoids synchronous JDBC by construction; verify it with:

- `/vbe diagnostics` after several hundred trades (executor queue depth, completed/failed counters).
- `AsyncDatabaseExecutorTest` for bounded-queue behaviour under saturation.
- A profiler or `timings` report during the merchant/enchanting steps above; look for main-thread
  JDBC or a growing `NexusEnchants-DB-*` queue.

## 8. Known testing limitations

1. No automated test can certify Minecraft client behaviour; the merchant and enchanting GUI paths
   are specified in §6 and must be performed on a real server.
2. MySQL/MariaDB tests are not part of CI (no service container was added). The MySQL provider is
   covered by static review and by the SQLite-verified repository code paths; running DB001–DB020
   against MySQL requires a maintainer-provided server.
3. SpotBugs/PMD are not enabled (see `docs/DECISIONS.md` ADR-010).
4. Statistical tests are sensitive to the configured defaults by design: changing
   `villager-rarity.yml` requires updating the expected values in `StatisticalDistributionTest` and
   `ResourceIntegrityTest` in the same commit.
