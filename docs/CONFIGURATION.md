# Configuration

All gameplay values live in YAML inside `plugins/NexusEnchants/`. YAML is the **single source of
truth**: SQL never stores a price, weight or policy. Files are validated on load and on
`/vbe reload`; anything invalid is reported in the console and by `/vbe diagnostics`, and the
affected feature keeps its last valid value instead of guessing.

Seven files are managed:

```
config.yml
pricing.yml
villager-rarity.yml
enchanting-rarity.yml
enchantment-levels.yml
enchantment-sources.yml
discounts.yml
```

Missing files are recreated from the bundled defaults on the next start or reload.

## Enchantment keys

Every enchantment key accepts three spellings:

| Form | Example |
|---|---|
| Canonical | `MENDING` |
| Namespaced | `minecraft:mending` |
| Display/legacy | `Mending`, `mending` |

Keys are normalised through `EnchantmentResolver`; unknown keys are reported as configuration errors.
Datapack enchantments work as long as their registry key is used.

## `config.yml`

```yaml
settings:
  language: en                 # only "en" ships
  enabled: true                # master switch; false = vanilla behaviour everywhere
  verbose-startup: false       # log resolved prices/weights at startup
  librarian-book-trades: 2     # enchantment-book trades per new librarian (1-6)
  statistics:
    enabled: true
    flush-interval-seconds: 300  # minimum 10
  audit:
    enabled: true
  async:
    worker-threads: 2          # 1-16
    queue-capacity: 512        # minimum 16
```

`database` is documented in `docs/DATABASE.md`.

## `pricing.yml`

```yaml
pricing:
  MENDING:
    1: 70
  UNBREAKING:
    1: 20
    2: 30
    3: 45

fallback:
  enabled: true
  price: 40          # used for enchantment/level pairs without an explicit price

limits:
  minimum: 1         # clamped up
  maximum: 128       # rejected above (two merchant slots × 64)
  allow-zero: false  # permit a configured price of 0
```

- A missing pair uses `fallback.price` when `fallback.enabled` is true, otherwise the trade is left
  untouched and the problem is reported.
- Prices above `limits.maximum` are a configuration error — never silently truncated.
- Prices above 64 are paid as `64 + remainder` across the two merchant input slots.

## `villager-rarity.yml`

```yaml
villager-rarity:
  enabled: true
  selection-mode: WEIGHTED      # WEIGHTED | DISABLED
  treasure-enabled: false       # allow treasure enchantments from librarians
  enchantments:
    MENDING:
      enabled: true
      weight: 2
```

- Weights are relative. With Mending 2 and Unbreaking 12, a Mending book is 2/14 ≈ 14.29% likely
  among those two offers.
- `enabled: false` or `weight: 0` removes the enchantment entirely.
- If every weight is zero the system reports a configuration error instead of generating arbitrary
  trades.
- Per-level weights can be supplied with a `levels:` block under an enchantment; omitted levels use
  `enchantment-levels.yml`.

## `enchanting-rarity.yml`

```yaml
enchanting-table:
  enabled: true
  rarity-mode: WEIGHTED              # WEIGHTED | DISABLED
  treasure-enchantments:
    enabled: false                   # treasure enchantments never come from a table
  multiple-enchantments:
    enabled: true
    chance: 15                       # percent
    maximum: 3                       # total enchantments per result
    additional-minimum-level: 1
    additional-maximum-level: 3
  enchantment-weights:
    MENDING:
      enabled: false
      weight: 0
```

- Eligibility respects item type (`Enchantment#canEnchantItem`), maximum level, treasure policy,
  acquisition-source rules and conflicts (`Enchantment#conflictsWith`).
- XP and lapis costs are never modified.
- The preview and the applied result come from the same plan; extra enchantments cannot be shown in
  the GUI (API limitation — see `docs/API_COMPATIBILITY.md` §2.3).

## `enchantment-levels.yml`

```yaml
enchantment-levels:
  enabled: true
  default:
    level-1: 45
    level-2: 30
    level-3: 15
    level-4: 7
    level-5: 3
  overrides:
    MENDING:
      level-1: 100
```

Weights are normalised across the levels that are both configured **and** legal for the enchantment
(`getStartLevel`..`getMaxLevel`), so an illegal level can never be produced. An override replaces the
default table for that enchantment only.

## `enchantment-sources.yml`

```yaml
enchantment-sources:
  enabled: true
  defaults:              # used when an enchantment has no explicit rule
    villager: true
    enchanting-table: true
    loot: true
    fishing: true
  MENDING:
    villager: true
    enchanting-table: false
    loot: true
    fishing: true
```

Sources: `villager` (librarian trades), `enchanting-table`, `loot` (world loot generation),
`fishing`. Enforced by `SourceRestrictionListener` and by the trade/enchanting managers.
Anvil, grindstone, creative menus and plugin-granted items are **not** intercepted.

## `discounts.yml`

```yaml
discounts:
  policy: CAP                          # PRESERVE | DISABLE | CAP
  maximum-discount-percentage: 20      # CAP ceiling, 0-100
  hero-of-the-village: true            # re-apply hero under CAP
  hero-of-the-village-percentage-per-level: 5
  curing-discounts: true               # recorded; reported by /vbe diagnostics
  reputation-discounts: true           # recorded; reported by /vbe diagnostics
```

| Policy | Effect |
|---|---|
| `PRESERVE` | Vanilla decides the final price on top of the configured base. |
| `DISABLE` | Vanilla discount providers suppressed; the player pays the configured price. |
| `CAP` | Providers suppressed; the plugin re-applies Hero of the Village clamped to the cap. |

Paper 26.3 has no per-player merchant price hook, so curing/reputation discounts cannot be capped
in place — they can only be suppressed. See `docs/API_COMPATIBILITY.md` §2.1.

## Validation rules

| Case | Result |
|---|---|
| Missing file | Recreated from bundled defaults |
| YAML syntax error | Reported as an ERROR; previous valid snapshot keeps serving |
| Unknown enchantment key | ERROR with file and path |
| Negative price / weight | ERROR |
| Price above `limits.maximum` | ERROR |
| Weight above 1000 (commands) | Rejected by the command |
| All weights zero | ERROR, feature disabled |
| Invalid discount policy | ERROR, `CAP` default retained |
| `type: MYSQL` without settings | ERROR, SQLite retained |
| Unknown keys | Reported so typos are visible |

## Reload vs restart

`/vbe reload` re-reads all seven YAML files and swaps in a new immutable snapshot. **Database
settings require a restart**, because the pool is built once during startup.
