# API compatibility

Target: **Paper API 26.3** (`io.papermc.paper:paper-api:26.3.build.200-stable`), Java 25,
`api-version: '26.3'`.

Every Bukkit/Paper member used by this plugin was checked against the Paper 26.3 API documentation
or the `paper-api` class file before use. Nothing is assumed from older versions.

## 1. Verified Paper API

### Merchant trades

| Member | Usage |
|---|---|
| `MerchantRecipe(ItemStack, int maxUses)` | building a replacement trade (there is no `setResult`) |
| `MerchantRecipe#setIngredients(ItemStack, ItemStack)` | split emerald payment (slot 1, optional slot 2) |
| `MerchantRecipe#getAdjustedIngredient1()` | reading the effective first cost for the floor check |
| `MerchantRecipe#setSpecialPrice(int)` / `setPriceMultiplier(float)` / `setDemand(int)` | normalising the vanilla price modifiers |
| `MerchantRecipe#setIgnoreDiscounts(boolean)` | suppressing vanilla discounts under `CAP`/`DISABLE` |
| `MerchantRecipe#setVillagerExperience(int)` / `setRewardExp(boolean)` | preserving villager experience |
| `AbstractVillager#getRecipes()/setRecipes(List)` , `getRecipeCount()` | enumerating and replacing trades |
| `Villager#getProfession()/setProfession(Profession)` , `Profession.LIBRARIAN` | librarian detection |
| `Villager#getVillagerExperience()/setVillagerExperience(int)` | preserving experience |
| `VillagerAcquireTradeEvent#getEntity()/getRecipe()/setRecipe(...)` | trade generation |
| `VillagerReplenishTradeEvent#getEntity()/getRecipe()/setRecipe(...)` | restock defence |
| `PlayerTradeEvent#getVillager()` , `PlayerPurchaseEvent#getTrade()/setTrade(...)` | pre-payment validation |

### Enchanting

| Member | Usage |
|---|---|
| `PrepareItemEnchantEvent#getOffers()` | the three `EnchantmentOffer` slots |
| `EnchantmentOffer#getCost()/setEnchantment(...)/setEnchantmentLevel(int)` | preview rewrite |
| `EnchantItemEvent#getEnchantsToAdd()/getEnchanter()/whichButton()` | result rewrite |
| `Enchantment#isTreasure()/canEnchantItem(ItemStack)/conflictsWith(Enchantment)` | eligibility |
| `Enchantment#getMaxLevel()/getStartLevel()` | legal level range |
| `RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)` | registry lookup |

### World sources

| Member | Usage |
|---|---|
| `LootGenerateEvent#getLoot()/setLoot(Collection)` | loot filtering |
| `PlayerFishEvent#getCaught()` → `Item#getItemStack()/setItemStack(...)` | fishing filtering |

Item metadata is preserved by editing the enchantment map on the existing `ItemStack`; the plugin
never replaces the stack identity for loot or fishing results.

## 2. Unsupported mechanics and documented alternatives

### 2.1 Per-player merchant pricing

**What is missing.** Paper 26.3 exposes the *recipe*, not the per-player price the merchant computes
from curing, gossip reputation and Hero of the Village. There is no event or method that lets a
plugin cap the final price a specific player pays.

**What the plugin does.**

| Policy | Behaviour |
|---|---|
| `PRESERVE` | Vanilla applies its discounts on top of the configured base price. |
| `DISABLE` | `setIgnoreDiscounts(true)`; vanilla discount providers are suppressed. |
| `CAP` (default) | `setIgnoreDiscounts(true)`; the plugin re-applies only Hero of the Village, clamped to `maximum-discount-percentage`. |

Curing and reputation discounts cannot be read or recomputed, so they are never claimed as applied.
`/vbe diagnostics` reports which discount providers were suppressed.

### 2.2 Prices above 128 emeralds

A merchant recipe has exactly two input slots, each holding at most 64 items. `PaymentPlan` splits a
price into `64 + remainder` and refuses anything above 128
(`PriceMath.MAX_REPRESENTABLE_PRICE`). A larger configured price is rejected at load time with a
configuration error instead of being truncated, because a truncated price is an economy exploit.

### 2.3 Previewing additional enchantments

`EnchantmentOffer` carries one enchantment and one level. When the multiple-enchantment chance
triggers, the primary enchantment is previewed exactly; the extra enchantments are applied from the
same cached plan, so they are *not* shown in the GUI beforehand. This is an API limitation, not a
configuration option.

### 2.4 Unintercepted acquisition sources

Intercepted: librarian trades, enchanting table, loot generation, fishing.

Not intercepted: anvil combination and repair, grindstone, enchanting via commands or other plugins,
creative-menu items, chest/minecart transfers, villager trade from a custom plugin that sets
`ignoreDiscounts` itself. `/vbe diagnostics` lists these as unsupported so operators are not misled.

### 2.5 Villager profession changes

Only `Profession.LIBRARIAN` trades are priced and weighted. A villager that changes profession keeps
its managed registration but its non-librarian trades are left untouched.

## 3. Version-specific notes

- **Java 25 is mandatory** for Paper 26.3 (class file major 69).
- **`api-version: '26.3'`** — Paper rejects older API declarations for this server line.
- `MerchantRecipe` has no `setResult`; a replacement recipe must be constructed from
  `getResult()` and `getMaxUses()`, then have its properties copied.
- `Enchantment` is registry-backed; the plugin always resolves through `RegistryAccess` and falls
  back to `Enchantment#getByKey` only when the registry view is unavailable.
- `PlayerTradeEvent` itself declares only `getMerchant()`/`getVillager()`; `getTrade()`/`setTrade()`
  and `setCancelled()` come from `PlayerPurchaseEvent`. The handler deliberately does not call
  `getPlayer()`.

## 4. Public plugin API

`NexusEnchantsApi` (version `1.0.0`) is registered in Bukkit's `ServicesManager`:

```java
NexusEnchantsApi api = Bukkit.getServicesManager().load(NexusEnchantsApi.class);
int price = api.getBasePrice(Enchantment.MENDING, 1);
boolean allowed = api.isSourceAllowed(Enchantment.MENDING, AcquisitionSource.VILLAGER);
double chance = api.getVillagerProbability(Enchantment.MENDING);
```

| Method | Returns |
|---|---|
| `apiVersion()` | `"1.0.0"` |
| `getBasePrice(Enchantment, int)` | configured price, or the fallback |
| `getEffectivePrice(Enchantment, int, int heroAmplifier)` | price after the discount policy |
| `isSourceAllowed(Enchantment, AcquisitionSource)` | source rule |
| `getVillagerProbability(Enchantment)` / `getEnchantingProbability(Enchantment)` | selection probability |
| `isVillagerManaged(UUID)` | cache lookup, main-thread safe |
| `reload()` | `CompletableFuture<ReloadResult>` |

All methods must be called from the server thread; the interface documents this. Breaking changes
require a new `API_VERSION`.
