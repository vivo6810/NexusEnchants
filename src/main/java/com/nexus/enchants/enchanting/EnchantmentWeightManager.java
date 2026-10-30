package com.nexus.enchants.enchanting;

import com.nexus.enchants.config.ConfigSnapshot;
import com.nexus.enchants.sources.AcquisitionSource;
import com.nexus.enchants.sources.EnchantmentSourceManager;
import com.nexus.enchants.util.EnchantmentResolver;
import com.nexus.enchants.util.EnchantmentWeight;
import com.nexus.enchants.util.WeightedTable;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

/**
 * Builds the weighted enchantment pool that is legal for a specific item.
 */
public final class EnchantmentWeightManager {

    private final Supplier<ConfigSnapshot> snapshotSupplier;
    private final EnchantmentSourceManager sources;

    /**
     * Creates the manager.
     *
     * @param snapshotSupplier supplier of the active configuration
     * @param sources          acquisition source manager
     */
    public EnchantmentWeightManager(
            Supplier<ConfigSnapshot> snapshotSupplier,
            EnchantmentSourceManager sources) {
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.sources = Objects.requireNonNull(sources, "sources");
    }

    /**
     * Returns whether an enchantment may be applied to an item by the table.
     *
     * @param enchantment enchantment
     * @param item        item being enchanted
     * @return {@code true} when the enchantment is eligible
     */
    public boolean eligible(Enchantment enchantment, ItemStack item) {
        ConfigSnapshot snapshot = snapshotSupplier.get();
        String key = EnchantmentResolver.keyOf(enchantment);
        EnchantmentWeight weight = snapshot.enchanting().weightOf(key);
        if (!weight.selectable()) {
            return false;
        }
        if (enchantment.isTreasure() && !snapshot.enchanting().treasureEnabled()) {
            return false;
        }
        if (!sources.allows(key, AcquisitionSource.ENCHANTING_TABLE)) {
            return false;
        }
        return item == null || enchantment.canEnchantItem(item);
    }

    /**
     * Builds the eligible table for an item.
     *
     * @param item item being enchanted, may be {@code null} to ignore item compatibility
     * @return the eligible table
     */
    public WeightedTable<Enchantment> tableFor(ItemStack item) {
        ConfigSnapshot snapshot = snapshotSupplier.get();
        WeightedTable.Builder<Enchantment> builder = WeightedTable.builder();
        for (Enchantment enchantment : EnchantmentResolver.registry()) {
            String key = EnchantmentResolver.keyOf(enchantment);
            EnchantmentWeight weight = snapshot.enchanting().weightOf(key);
            if (!weight.selectable()) {
                continue;
            }
            if (enchantment.isTreasure() && !snapshot.enchanting().treasureEnabled()) {
                continue;
            }
            if (!sources.allows(key, AcquisitionSource.ENCHANTING_TABLE)) {
                continue;
            }
            if (item != null && !enchantment.canEnchantItem(item)) {
                continue;
            }
            builder.add(enchantment, weight.effectiveWeight());
        }
        return builder.build();
    }

    /**
     * Picks an eligible enchantment.
     *
     * @param item   item being enchanted
     * @param random random source
     * @return the chosen enchantment, or empty when nothing is eligible
     */
    public Optional<Enchantment> pick(ItemStack item, Random random) {
        return tableFor(item).pick(random);
    }
}
