# Architecture

NexusEnchants separates five concerns that are easy to tangle: **selection** (which enchantment),
**pricing** (what it costs), **trade manipulation** (how a villager offers it), **enchanting
manipulation** (what the table produces), and **persistence** (what survives a restart).
Configuration and commands sit on top of all of them.

## 1. Layering

```
            commands/            api/            diagnostics/
                 \               |                /
                  -------- bootstrap/PluginBootstrap --------
                 /        |             |            \
        villager/     enchanting/    sources/      economy/
                 \        |             |            /
                        config/  <->  storage/
                             \        /
                            database/
                               |
                             util/
```

- **`util/`** has no dependency on any other package. `WeightedTable` is a generic, allocation-free
  weighted picker; `PriceMath` owns every rounding and overflow rule; `EnchantmentResolver` maps
  user input (`MENDING`, `minecraft:mending`, `Mending`) to a registry `Enchantment`.
- **`config/`** parses and validates everything and exposes one immutable `ConfigSnapshot`.
  Nothing outside `config/` reads YAML.
- **`economy/`** turns `(enchantment, level)` into a validated `PricedTrade`:
  `PriceCalculator` → `DiscountManager` → `TradeValidator`.
- **`villager/`**, **`enchanting/`**, **`sources/`** mutate events; they never query JDBC.
- **`database/`** is the only package that touches `java.sql`. It runs on `AsyncDatabaseExecutor`.
- **`storage/`** holds bounded in-memory state (`TradeCache`, `RarityStatisticsStore`) that the
  main thread may read without blocking.
- **`bootstrap/`** owns wiring order and the shutdown sequence.

## 2. Request flows

### Librarian generates a trade

```
VillagerAcquireTradeEvent (HIGHEST)
  → VillagerTradeManager.onAcquireTrade
      → VillagerRarityManager.select()            weighted enchantment + level
      → EnchantmentSourceManager.allows(...)      source gate (villager)
      → TradeGenerationManager.buildRecipe(...)   MerchantRecipe with the split payment
      → BookPricingManager.price(...)             price + discount + validation
      → event.setRecipe(recipe)
      → cache.remember(...) + async TradeRepository.upsert(...) + AuditLogRepository.insert(...)
```

The async writes never touch the event or any Bukkit object; they receive immutable records copied
on the main thread.

### Librarian restocks

`VillagerReplenishTradeEvent` (HIGH) re-applies the configured price to the replenished recipe, so a
restock can never restore a vanilla price. If the enchantment's source is no longer allowed, the
trade is removed instead.

### Player clicks a trade

`PlayerTradeEvent` (HIGH) re-validates the emerald cost **before** payment
(`VillagerTradeManager.onPlayerTrade`): if the recipe would charge less than the configured price,
the event is cancelled and the recipe is repaired on the next restock. This is the anti-exploit
floor; inventory integrity always wins over price enforcement.

### Enchanting table

```
PrepareItemEnchantEvent (HIGH)     → plan per offer slot, cache by player (60 s)
  → CombinationManager.plan(item, cost, random)
      → EnchantmentWeightManager.pick(item)   eligibility: item type, treasure, source, weight
      → EnchantmentLevelManager.pick(...)     level weights over the legal range
      → compatibility check for extra enchantments
  → EnchantingResultManager.applyPreview(offer, plan)   preview == result

EnchantItemEvent (HIGHEST)         → apply the cached plan for the clicked slot
                                     (or enforce sources when no plan exists)
```

XP and lapis costs are never modified; only enchantment identity and level are rewritten.

### Loot and fishing

`SourceRestrictionListener` removes enchantments whose source is disallowed from
`LootGenerateEvent#getLoot()` and from a caught `Item`'s stack. It filters, it never adds.

## 3. Threading model

| Thread | May touch |
|---|---|
| Server/main thread | Bukkit objects, configuration snapshots, in-memory caches, event handlers |
| `NexusEnchants-DB-*` workers (2, bounded queue 512) | JDBC only, plus immutable records |
| Async hand-back | `Bukkit.getScheduler().runTask(...)` before any Bukkit mutation |

Rules enforced in code:

- `DatabaseManager` hands out connections only to the executor.
- Repositories take plain values (`UUID`, `String`, `int`, records), never `Villager` or `ItemStack`.
- `AsyncDatabaseExecutor` counts submitted/completed/failed/rejected tasks and logs queue overflow
  once per hundred rejections; it never blocks the caller.
- Shutdown flushes statistics, drains the queue with a timeout, then closes the pool.

## 4. Configuration snapshots

`ConfigManager` builds a complete `ConfigSnapshot` off-thread, validates it, and swaps the reference
atomically. Handlers read `config.current()` once per event and use that snapshot for the whole
operation, so a reload never produces a half-applied state. A file that fails to parse is reported
as a `ConfigIssue` and the previously valid snapshot keeps serving.

## 5. Error strategy

- Configuration problems → `ConfigIssue` (ERROR/WARNING) → console + `/vbe diagnostics`.
- Missing price → `PriceNotConfiguredException` → the trade is left untouched and the problem is
  reported; the plugin never invents a price.
- Database problems → `DatabaseException` / `DatabaseUnavailableException` → logged, features that
  require persistence degrade, the server keeps running.
- Unexpected runtime failures in tasks → counted, logged, worker survives.

## 6. Extension points

- `NexusEnchantsApi` (services manager) for third-party plugins.
- New acquisition source → add an `AcquisitionSource` constant, a listener, and configuration keys;
  the existing rule lookup already supports it.
- New schema change → add `V005__*.sql` to both migration folders; never edit V001–V004.
