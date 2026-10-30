package com.nexus.enchants.villager;

import com.nexus.enchants.config.ConfigSnapshot;
import com.nexus.enchants.enchanting.EnchantmentLevelManager;
import com.nexus.enchants.sources.AcquisitionSource;
import com.nexus.enchants.sources.EnchantmentSourceManager;
import com.nexus.enchants.util.EnchantmentResolver;
import com.nexus.enchants.util.EnchantmentWeight;
import com.nexus.enchants.util.WeightedTable;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import org.bukkit.enchantments.Enchantment;

/**
 * Selects the enchantment and level a librarian offers.
 *
 * <p>The eligible pool is built from the live registry on every roll, so a
 * datapack enchantment that is added to the configuration is immediately
 * available and an enchantment that disappears from the registry is never
 * selected.</p>
 */
public final class VillagerRarityManager {

    private final Supplier<ConfigSnapshot> snapshotSupplier;
    private final EnchantmentSourceManager sources;
    private final EnchantmentLevelManager levels;
    private final Random random;

    /**
     * Creates the manager.
     *
     * @param snapshotSupplier supplier of the active configuration
     * @param sources          acquisition source manager
     * @param levels           level selection manager
     * @param random           random source
     */
    public VillagerRarityManager(
            Supplier<ConfigSnapshot> snapshotSupplier,
            EnchantmentSourceManager sources,
            EnchantmentLevelManager levels,
            Random random) {
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.sources = Objects.requireNonNull(sources, "sources");
        this.levels = Objects.requireNonNull(levels, "levels");
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Selects an enchantment and level for a librarian trade.
     *
     * @return the selection, or empty when the weighted table cannot produce a value
     */
    public Optional<Selection> select() {
        ConfigSnapshot snapshot = snapshotSupplier.get();
        if (!snapshot.villagerRarity().active()) {
            return Optional.empty();
        }
        WeightedTable<Enchantment> table = buildTable(snapshot);
        Optional<Enchantment> picked = table.pick(random);
        if (picked.isEmpty()) {
            return Optional.empty();
        }
        Enchantment enchantment = picked.get();
        String key = EnchantmentResolver.keyOf(enchantment);
        int level = levels.pick(enchantment, snapshot.villagerRarity().levelWeightsFor(key), random);
        return Optional.of(new Selection(key, enchantment, level));
    }

    /**
     * Builds the weighted table that is currently valid for librarian trades.
     *
     * @param snapshot configuration snapshot
     * @return the eligible table
     */
    public WeightedTable<Enchantment> buildTable(ConfigSnapshot snapshot) {
        if (!snapshot.villagerRarity().active()) {
            return WeightedTable.empty();
        }
        WeightedTable.Builder<Enchantment> builder = WeightedTable.builder();
        for (Enchantment enchantment : EnchantmentResolver.registry()) {
            String key = EnchantmentResolver.keyOf(enchantment);
            EnchantmentWeight weight = snapshot.villagerRarity().weightOf(key);
            if (!weight.selectable()) {
                continue;
            }
            if (enchantment.isTreasure() && !snapshot.villagerRarity().treasureEnabled()) {
                continue;
            }
            if (!sources.allows(key, AcquisitionSource.VILLAGER)) {
                continue;
            }
            builder.add(enchantment, weight.effectiveWeight());
        }
        return builder.build();
    }

    /**
     * Returns the probability of an enchantment appearing.
     *
     * @param enchantmentKey canonical enchantment key
     * @return the probability in the range {@code 0.0 - 1.0}
     */
    public double probabilityOf(String enchantmentKey) {
        ConfigSnapshot snapshot = snapshotSupplier.get();
        WeightedTable<Enchantment> table = buildTable(snapshot);
        for (WeightedTable.Entry<Enchantment> entry : table.entries()) {
            if (EnchantmentResolver.keyOf(entry.value()).equals(enchantmentKey)) {
                return table.probabilityOf(entry.value());
            }
        }
        return 0.0D;
    }

    /**
     * Returns the configured per-level weights of an enchantment.
     *
     * @param enchantmentKey canonical enchantment key
     * @return level to weight map, empty when no override exists
     */
    public Map<Integer, Integer> levelWeightsFor(String enchantmentKey) {
        return snapshotSupplier.get().villagerRarity().levelWeightsFor(enchantmentKey);
    }

    /**
     * A chosen enchantment and level.
     *
     * @param enchantmentKey canonical key
     * @param enchantment    server enchantment
     * @param level          chosen level
     */
    public record Selection(String enchantmentKey, Enchantment enchantment, int level) {
    }
}
