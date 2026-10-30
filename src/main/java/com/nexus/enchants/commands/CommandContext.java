package com.nexus.enchants.commands;

import com.nexus.enchants.config.ConfigManager;
import com.nexus.enchants.database.DatabaseManager;
import com.nexus.enchants.diagnostics.DiagnosticsService;
import com.nexus.enchants.economy.BookPricingManager;
import com.nexus.enchants.enchanting.CombinationManager;
import com.nexus.enchants.enchanting.EnchantingManager;
import com.nexus.enchants.enchanting.EnchantmentLevelManager;
import com.nexus.enchants.enchanting.EnchantmentWeightManager;
import com.nexus.enchants.storage.RarityStatisticsStore;
import com.nexus.enchants.storage.TradeCache;
import com.nexus.enchants.villager.VillagerRarityManager;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Live services shared by every subcommand.
 *
 * @param plugin            owning plugin
 * @param config            configuration manager
 * @param database          database manager
 * @param cache             managed villager cache
 * @param statistics        aggregated statistics store
 * @param pricing           price engine
 * @param villagerRarity    librarian rarity manager
 * @param enchantingWeights enchanting weight manager
 * @param enchantingLevels  level probability manager
 * @param combinations      enchanting combination builder
 * @param enchanting        enchanting manager, used for cache inspection
 * @param diagnostics       diagnostics service
 * @param reloadSupplier    performs a full reload and reports the outcome
 */
public record CommandContext(
        JavaPlugin plugin,
        ConfigManager config,
        DatabaseManager database,
        TradeCache cache,
        RarityStatisticsStore statistics,
        BookPricingManager pricing,
        VillagerRarityManager villagerRarity,
        EnchantmentWeightManager enchantingWeights,
        EnchantmentLevelManager enchantingLevels,
        CombinationManager combinations,
        EnchantingManager enchanting,
        DiagnosticsService diagnostics,
        Supplier<ReloadReport> reloadSupplier) {

    /**
     * Validates the context.
     *
     * @param plugin            owning plugin
     * @param config            configuration manager
     * @param database          database manager
     * @param cache             managed villager cache
     * @param statistics        statistics store
     * @param pricing           price engine
     * @param villagerRarity    librarian rarity manager
     * @param enchantingWeights enchanting weight manager
     * @param enchantingLevels  level probability manager
     * @param combinations      combination builder
     * @param enchanting        enchanting manager
     * @param diagnostics       diagnostics service
     * @param reloadSupplier    reload action
     */
    public CommandContext {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(statistics, "statistics");
        Objects.requireNonNull(pricing, "pricing");
        Objects.requireNonNull(villagerRarity, "villagerRarity");
        Objects.requireNonNull(enchantingWeights, "enchantingWeights");
        Objects.requireNonNull(enchantingLevels, "enchantingLevels");
        Objects.requireNonNull(combinations, "combinations");
        Objects.requireNonNull(enchanting, "enchanting");
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(reloadSupplier, "reloadSupplier");
    }
}
