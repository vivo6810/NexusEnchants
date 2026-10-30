package com.nexus.enchants.villager;

import com.nexus.enchants.sources.AcquisitionSource;
import com.nexus.enchants.sources.EnchantmentSourceManager;
import com.nexus.enchants.util.EnchantmentResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Brings librarians that already exist in the world under plugin control.
 *
 * <p>Migration is deliberately conservative: an existing enchanted book keeps
 * its enchantment and level (players may already have seen the offer) and only
 * the price is rewritten. A trade whose enchantment is no longer allowed from
 * villagers is removed, because keeping it would silently defeat the source
 * configuration.</p>
 *
 * <p>The scan is spread across server ticks so a world with thousands of
 * villagers never causes a lag spike.</p>
 */
public final class VillagerMigrationManager {

    private static final int VILLAGERS_PER_TICK = 200;

    private final JavaPlugin plugin;
    private final VillagerTradeManager trades;
    private final EnchantmentSourceManager sources;
    private final Logger logger;
    private BukkitTask task;

    /**
     * Creates the manager.
     *
     * @param plugin  owning plugin
     * @param trades  trade manager used to reprice
     * @param sources acquisition source manager
     * @param logger  logger
     */
    public VillagerMigrationManager(
            JavaPlugin plugin,
            VillagerTradeManager trades,
            EnchantmentSourceManager sources,
            Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.trades = Objects.requireNonNull(trades, "trades");
        this.sources = Objects.requireNonNull(sources, "sources");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Starts a budgeted scan over every loaded villager.
     *
     * <p>Must be called from the server thread.</p>
     */
    public void start() {
        stop();
        List<Villager> pending = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            pending.addAll(world.getEntitiesByClass(Villager.class));
        }
        if (pending.isEmpty()) {
            logger.info("No loaded villagers to migrate.");
            return;
        }
        int[] cursor = {0};
        int[] changed = {0};
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int processed = 0;
            while (cursor[0] < pending.size() && processed < VILLAGERS_PER_TICK) {
                Villager villager = pending.get(cursor[0]);
                if (villager.isValid()) {
                    changed[0] += migrate(villager);
                }
                cursor[0]++;
                processed++;
            }
            if (cursor[0] >= pending.size()) {
                logger.info("Villager migration finished: " + changed[0] + " trade(s) repriced across "
                        + pending.size() + " loaded villager(s).");
                stop();
            }
        }, 1L, 1L);
    }

    /**
     * Stops a running scan.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Migrates a single villager.
     *
     * @param villager villager to migrate
     * @return the number of trades that were rewritten or removed
     */
    public int migrate(Villager villager) {
        if (!VillagerTradeManager.isLibrarian(villager)) {
            return 0;
        }
        List<MerchantRecipe> recipes = new ArrayList<>(villager.getRecipes());
        List<MerchantRecipe> updated = new ArrayList<>(recipes.size());
        int changed = 0;
        for (MerchantRecipe recipe : recipes) {
            if (!VillagerTradeManager.isBookTrade(recipe)) {
                updated.add(recipe);
                continue;
            }
            Optional<Enchantment> stored = TradeGenerationManager.storedEnchantment(recipe.getResult());
            if (stored.isEmpty()) {
                updated.add(recipe);
                continue;
            }
            String key = EnchantmentResolver.keyOf(stored.get());
            if (!sources.allows(key, AcquisitionSource.VILLAGER)) {
                changed++;
                continue;
            }
            Optional<MerchantRecipe> repriced = trades.repriceBookTrade(recipe);
            if (repriced.isPresent()) {
                updated.add(repriced.get());
                changed++;
            } else {
                updated.add(recipe);
            }
        }
        if (changed > 0) {
            villager.setRecipes(updated);
        }
        trades.registerManaged(villager);
        return changed;
    }
}
