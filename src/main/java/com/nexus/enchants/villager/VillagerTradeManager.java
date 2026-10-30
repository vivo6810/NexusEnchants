package com.nexus.enchants.villager;

import com.nexus.enchants.config.ConfigSnapshot;
import com.nexus.enchants.database.DatabaseManager;
import com.nexus.enchants.database.repositories.AuditRecord;
import com.nexus.enchants.economy.BookPricingManager;
import com.nexus.enchants.economy.PriceNotConfiguredException;
import com.nexus.enchants.sources.AcquisitionSource;
import com.nexus.enchants.sources.EnchantmentSourceManager;
import com.nexus.enchants.storage.RarityStatisticsStore;
import com.nexus.enchants.storage.StatisticKey;
import com.nexus.enchants.storage.TradeCache;
import com.nexus.enchants.storage.TradeRecord;
import com.nexus.enchants.storage.VillagerRecord;
import com.nexus.enchants.util.EnchantmentResolver;
import io.papermc.paper.event.player.PlayerTradeEvent;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Villager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.entity.VillagerReplenishTradeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Owns every librarian enchanted-book trade the plugin manages.
 *
 * <p>Three events matter:</p>
 * <ul>
 *     <li>{@link VillagerAcquireTradeEvent} - a new trade is generated. The
 *     enchantment, level, source permission and price are all decided here.</li>
 *     <li>{@link VillagerReplenishTradeEvent} - the villager restocked. The price
 *     is re-applied, which is what stops vanilla from restoring its own price.</li>
 *     <li>{@link PlayerTradeEvent} - a trade is about to complete. The effective
 *     payment is verified against the configured floor.</li>
 * </ul>
 *
 * <p>Everything runs on the server thread. Database writes are submitted to the
 * bounded worker pool and never block the trade.</p>
 */
public final class VillagerTradeManager implements Listener {

    private final JavaPlugin plugin;
    private final Supplier<ConfigSnapshot> snapshotSupplier;
    private final EnchantmentSourceManager sources;
    private final VillagerRarityManager rarity;
    private final TradeGenerationManager generation;
    private final BookPricingManager pricing;
    private final TradeCache cache;
    private final RarityStatisticsStore statistics;
    private final DatabaseManager database;
    private final Logger logger;
    private final Set<String> reportedProblems = ConcurrentHashMap.newKeySet();

    /**
     * Creates the manager.
     *
     * @param plugin           owning plugin
     * @param snapshotSupplier supplier of the active configuration
     * @param sources          acquisition source manager
     * @param rarity           librarian rarity manager
     * @param generation       trade object factory
     * @param pricing          price engine
     * @param cache            in-memory managed villager cache
     * @param statistics       aggregated statistics store
     * @param database         database manager
     * @param logger           logger
     */
    public VillagerTradeManager(
            JavaPlugin plugin,
            Supplier<ConfigSnapshot> snapshotSupplier,
            EnchantmentSourceManager sources,
            VillagerRarityManager rarity,
            TradeGenerationManager generation,
            BookPricingManager pricing,
            TradeCache cache,
            RarityStatisticsStore statistics,
            DatabaseManager database,
            Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.sources = Objects.requireNonNull(sources, "sources");
        this.rarity = Objects.requireNonNull(rarity, "rarity");
        this.generation = Objects.requireNonNull(generation, "generation");
        this.pricing = Objects.requireNonNull(pricing, "pricing");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.statistics = Objects.requireNonNull(statistics, "statistics");
        this.database = Objects.requireNonNull(database, "database");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Replaces the price and (when rarity is active) the contents of a new book trade.
     *
     * @param event trade generation event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAcquireTrade(VillagerAcquireTradeEvent event) {
        ConfigSnapshot snapshot = snapshotSupplier.get();
        if (!snapshot.settings().enabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Villager villager) || !isLibrarian(villager)) {
            return;
        }
        MerchantRecipe recipe = event.getRecipe();
        if (!isBookTrade(recipe)) {
            return;
        }

        Optional<Selection> selection = resolveSelection(snapshot, recipe);
        if (selection.isEmpty()) {
            // The weighted table cannot produce anything: refuse to invent a trade.
            event.setCancelled(true);
            return;
        }
        Selection chosen = selection.get();

        if (!sources.allows(chosen.enchantmentKey(), AcquisitionSource.VILLAGER)) {
            event.setCancelled(true);
            return;
        }
        if (bookTradeCount(villager) >= snapshot.settings().librarianBookTrades()) {
            // Keep the configured number of book offers per librarian.
            event.setCancelled(true);
            return;
        }

        BookPricingManager.PricedTrade priced;
        try {
            priced = pricing.price(chosen.enchantmentKey(), chosen.level(), 0);
        } catch (PriceNotConfiguredException | IllegalStateException exception) {
            reportOnce("price:" + chosen.enchantmentKey() + ":" + chosen.level(), () ->
                    logger.warning("Skipping librarian trade for " + chosen.enchantmentKey() + " level "
                            + chosen.level() + ": " + exception.getMessage()));
            event.setCancelled(true);
            return;
        }

        ItemStack book = generation.createEnchantedBook(chosen.enchantment(), chosen.level());
        MerchantRecipe rebuilt = generation.buildRecipe(recipe, book, priced);
        event.setRecipe(rebuilt);

        int slotIndex = villager.getRecipeCount();
        register(villager, chosen, priced, slotIndex, rebuilt.getUses(), rebuilt.getMaxUses());
        statistics.record(new StatisticKey("villager", chosen.enchantmentKey(), chosen.level()));
        if (snapshot.settings().verboseStartup()) {
            logger.info("Librarian " + shortId(villager.getUniqueId()) + " now offers "
                    + chosen.enchantmentKey() + " " + roman(chosen.level()) + " for " + priced.finalPrice()
                    + " emerald(s)");
        }
    }

    /**
     * Re-applies the configured price whenever a villager restocks.
     *
     * @param event replenish event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onReplenishTrade(VillagerReplenishTradeEvent event) {
        ConfigSnapshot snapshot = snapshotSupplier.get();
        if (!snapshot.settings().enabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Villager villager) || !isLibrarian(villager)) {
            return;
        }
        MerchantRecipe recipe = event.getRecipe();
        if (!isBookTrade(recipe)) {
            return;
        }
        if (!cache.isManaged(villager.getUniqueId())) {
            // A villager the plugin never touched keeps its vanilla behaviour.
            return;
        }
        Optional<Enchantment> stored = TradeGenerationManager.storedEnchantment(recipe.getResult());
        if (stored.isEmpty()) {
            return;
        }
        Enchantment enchantment = stored.get();
        String key = EnchantmentResolver.keyOf(enchantment);
        int level = TradeGenerationManager.storedLevel(recipe.getResult(), enchantment);
        if (level < 1) {
            return;
        }
        if (!sources.allows(key, AcquisitionSource.VILLAGER)) {
            event.setCancelled(true);
            return;
        }
        BookPricingManager.PricedTrade priced;
        try {
            priced = pricing.price(key, level, 0);
        } catch (PriceNotConfiguredException | IllegalStateException exception) {
            reportOnce("restock:" + key + ":" + level, () ->
                    logger.warning("Could not reprice restocked trade " + key + " level " + level + ": "
                            + exception.getMessage()));
            event.setCancelled(true);
            return;
        }
        ItemStack book = generation.createEnchantedBook(enchantment, level);
        event.setRecipe(generation.buildRecipe(recipe, book, priced));
    }

    /**
     * Verifies that a completing trade cannot undercut the configured minimum.
     *
     * <p>Under CAP and DISABLE the plugin already blocks vanilla price
     * adjustments. This guard protects the configured floor if another plugin
     * re-enables them, and it never touches the player's inventory: a trade that
     * would be too cheap is refused before it is processed.</p>
     *
     * @param event pre-trade event
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTrade(PlayerTradeEvent event) {
        ConfigSnapshot snapshot = snapshotSupplier.get();
        if (!snapshot.settings().enabled()) {
            return;
        }
        if (!snapshot.discounts().policy().suppressesVanilla()) {
            // PRESERVE hands pricing to vanilla on purpose.
            return;
        }
        AbstractVillager merchant = event.getVillager();
        if (!cache.isManaged(merchant.getUniqueId())) {
            return;
        }
        MerchantRecipe trade = event.getTrade();
        if (trade == null) {
            return;
        }
        Optional<Enchantment> stored = TradeGenerationManager.storedEnchantment(trade.getResult());
        if (stored.isEmpty()) {
            return;
        }
        Enchantment enchantment = stored.get();
        String key = EnchantmentResolver.keyOf(enchantment);
        int level = TradeGenerationManager.storedLevel(trade.getResult(), enchantment);
        if (!sources.allows(key, AcquisitionSource.VILLAGER)) {
            event.setCancelled(true);
            audit("TRADE_BLOCKED_SOURCE", key, level, "source disabled for villager trades");
            return;
        }

        int floor;
        try {
            floor = pricing.price(key, level, 0).finalPrice();
        } catch (PriceNotConfiguredException | IllegalStateException exception) {
            return;
        }
        int effective = effectivePayment(trade);
        if (effective > 0 && effective < floor) {
            event.setCancelled(true);
            audit("TRADE_BLOCKED_UNDERCUT", key, level,
                    "effective payment " + effective + " is below the configured floor " + floor);
            logger.warning("Blocked an underpriced trade for " + key + " level " + level
                    + ": effective " + effective + " < floor " + floor);
        }
    }

    private int effectivePayment(MerchantRecipe trade) {
        List<ItemStack> ingredients = trade.getIngredients();
        if (ingredients.isEmpty()) {
            return 0;
        }
        int total = 0;
        ItemStack adjusted = trade.getAdjustedIngredient1();
        for (int index = 0; index < ingredients.size(); index++) {
            ItemStack ingredient = ingredients.get(index);
            if (index == 0 && adjusted != null) {
                total += adjusted.getAmount();
            } else {
                total += ingredient.getAmount();
            }
        }
        return total;
    }

    private Optional<Selection> resolveSelection(ConfigSnapshot snapshot, MerchantRecipe recipe) {
        if (snapshot.villagerRarity().active()) {
            Optional<VillagerRarityManager.Selection> picked = rarity.select();
            if (picked.isEmpty()) {
                reportOnce("empty-table", () -> logger.severe("The librarian rarity table produced no "
                        + "enchantment. Every weight is zero or every entry is disabled; see "
                        + "villager-rarity.yml and /vbe diagnostics."));
                return Optional.empty();
            }
            VillagerRarityManager.Selection selection = picked.get();
            return Optional.of(new Selection(selection.enchantmentKey(), selection.enchantment(),
                    selection.level()));
        }
        Optional<Enchantment> stored = TradeGenerationManager.storedEnchantment(recipe.getResult());
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        Enchantment enchantment = stored.get();
        int level = TradeGenerationManager.storedLevel(recipe.getResult(), enchantment);
        if (level < 1) {
            return Optional.empty();
        }
        return Optional.of(new Selection(EnchantmentResolver.keyOf(enchantment), enchantment, level));
    }

    private void register(
            Villager villager,
            Selection selection,
            BookPricingManager.PricedTrade priced,
            int slotIndex,
            int uses,
            int maxUses) {
        long now = System.currentTimeMillis();
        VillagerRecord record = VillagerRecord.create(
                villager.getUniqueId(),
                villager.getWorld().getUID(),
                villager.getWorld().getName(),
                professionKey(villager),
                now);
        cache.markManaged(record);
        if (!database.isReady()) {
            return;
        }
        String tradeKey = TradeGenerationManager.tradeKey(slotIndex, selection.enchantmentKey(),
                selection.level());
        TradeRecord trade = TradeRecord.create(villager.getUniqueId(), tradeKey, selection.enchantmentKey(),
                selection.level(), priced.finalPrice(), uses, maxUses, now);
        database.executor().submitSql("register-villager", () -> {
            database.villagers().upsert(record);
            database.trades().upsert(trade);
        });
    }

    private void audit(String eventType, String enchantmentKey, int level, String details) {
        ConfigSnapshot snapshot = snapshotSupplier.get();
        if (!snapshot.settings().auditEnabled() || !database.isReady()) {
            return;
        }
        AuditRecord record = AuditRecord.ofChange(eventType, null, enchantmentKey, level, null, null,
                details, System.currentTimeMillis());
        database.executor().submit("audit-" + eventType, () -> {
            try {
                database.auditLogs().insert(record);
            } catch (SQLException exception) {
                reportOnce("audit-failure", () -> logger.warning("Could not write an audit record: "
                        + exception.getMessage()));
            }
        });
    }

    /**
     * Reprices one exportable book trade, used by the migration manager.
     *
     * @param recipe original recipe
     * @return the replacement recipe, or empty when the trade must be removed
     */
    public Optional<MerchantRecipe> repriceBookTrade(MerchantRecipe recipe) {
        Optional<Enchantment> stored = TradeGenerationManager.storedEnchantment(recipe.getResult());
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        Enchantment enchantment = stored.get();
        String key = EnchantmentResolver.keyOf(enchantment);
        int level = TradeGenerationManager.storedLevel(recipe.getResult(), enchantment);
        if (level < 1) {
            return Optional.empty();
        }
        if (!sources.allows(key, AcquisitionSource.VILLAGER)) {
            return Optional.empty();
        }
        try {
            BookPricingManager.PricedTrade priced = pricing.price(key, level, 0);
            ItemStack book = generation.createEnchantedBook(enchantment, level);
            return Optional.of(generation.buildRecipe(recipe, book, priced));
        } catch (PriceNotConfiguredException | IllegalStateException exception) {
            reportOnce("migration:" + key + ":" + level, () ->
                    logger.warning("Leaving existing trade for " + key + " level " + level
                            + " untouched: " + exception.getMessage()));
            return Optional.empty();
        }
    }

    /**
     * Registers a villager as managed without changing its trades.
     *
     * @param villager villager to register
     */
    public void registerManaged(Villager villager) {
        long now = System.currentTimeMillis();
        VillagerRecord record = VillagerRecord.create(
                villager.getUniqueId(),
                villager.getWorld().getUID(),
                villager.getWorld().getName(),
                professionKey(villager),
                now);
        cache.markManaged(record);
        if (database.isReady()) {
            database.executor().submitSql("register-existing", () -> database.villagers().upsert(record));
        }
    }

    private void reportOnce(String key, Runnable action) {
        if (reportedProblems.add(key)) {
            action.run();
        }
    }

    /**
     * Returns whether a villager is a librarian.
     *
     * @param villager villager to inspect
     * @return {@code true} for librarians
     */
    public static boolean isLibrarian(Villager villager) {
        return villager.getProfession() != null
                && villager.getProfession().equals(Villager.Profession.LIBRARIAN);
    }

    /**
     * Returns whether a recipe sells an enchanted book.
     *
     * @param recipe recipe to inspect
     * @return {@code true} for enchanted-book trades
     */
    public static boolean isBookTrade(MerchantRecipe recipe) {
        return recipe != null && recipe.getResult().getType() == Material.ENCHANTED_BOOK;
    }

    private static int bookTradeCount(Villager villager) {
        int count = 0;
        for (MerchantRecipe recipe : villager.getRecipes()) {
            if (isBookTrade(recipe)) {
                count++;
            }
        }
        return count;
    }

    private static String professionKey(Villager villager) {
        Villager.Profession profession = villager.getProfession();
        if (profession == null) {
            return "none";
        }
        if (profession instanceof org.bukkit.Keyed keyed) {
            return keyed.getKey().toString();
        }
        return profession.toString();
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(level);
        };
    }

    /**
     * A resolved enchantment and level for one trade.
     *
     * @param enchantmentKey canonical key
     * @param enchantment    server enchantment
     * @param level          enchantment level
     */
    private record Selection(String enchantmentKey, Enchantment enchantment, int level) {
    }

    /**
     * Returns the plugin that owns this listener.
     *
     * @return the plugin
     */
    public JavaPlugin plugin() {
        return plugin;
    }
}
