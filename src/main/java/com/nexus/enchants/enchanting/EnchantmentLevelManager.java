package com.nexus.enchants.enchanting;

import com.nexus.enchants.config.LevelProbabilityConfig;
import com.nexus.enchants.util.EnchantmentResolver;
import com.nexus.enchants.util.PriceMath;
import com.nexus.enchants.util.WeightedTable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;
import org.bukkit.enchantments.Enchantment;

/**
 * Chooses an enchantment level from configured weights.
 *
 * <p>Weights are filtered to the enchantment's legal range before they are
 * normalised, so an unsupported level can never be produced. When no weight is
 * configured for any legal level the manager falls back to a uniform
 * distribution over that range and says so through
 * {@link #hasConfiguredLevels(Enchantment, Map)}.</p>
 */
public final class EnchantmentLevelManager {

    private final Supplier<LevelProbabilityConfig> configSupplier;

    /**
     * Creates the manager.
     *
     * @param configSupplier supplier of the active level configuration
     */
    public EnchantmentLevelManager(Supplier<LevelProbabilityConfig> configSupplier) {
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
    }

    /**
     * Picks a level for an enchantment.
     *
     * @param enchantment enchantment
     * @param overrides   per-level weight override, may be empty
     * @param random      random source
     * @return the chosen legal level
     */
    public int pick(Enchantment enchantment, Map<Integer, Integer> overrides, Random random) {
        WeightedTable<Integer> table = tableFor(enchantment, overrides);
        return table.pick(random).orElseGet(() -> legalRange(enchantment)[0]);
    }

    /**
     * Builds the level table for an enchantment.
     *
     * @param enchantment enchantment
     * @param overrides   per-level weight override, may be empty
     * @return the normalised level table
     */
    public WeightedTable<Integer> tableFor(Enchantment enchantment, Map<Integer, Integer> overrides) {
        Objects.requireNonNull(enchantment, "enchantment");
        int[] range = legalRange(enchantment);
        Map<Integer, Integer> weights = new LinkedHashMap<>();
        LevelProbabilityConfig config = configSupplier.get();
        boolean useOverrides = overrides != null && !overrides.isEmpty();
        if (useOverrides) {
            weights.putAll(overrides);
        } else if (config.enabled()) {
            weights.putAll(config.weightsFor(EnchantmentResolver.keyOf(enchantment)));
        }
        WeightedTable.Builder<Integer> builder = WeightedTable.builder();
        long total = 0L;
        for (int level = range[0]; level <= range[1]; level++) {
            int weight = weights.getOrDefault(level, 0);
            if (weight < 0) {
                continue;
            }
            builder.add(level, weight);
            total += weight;
        }
        if (total == 0L) {
            // No usable configuration: spread evenly over the legal range so the
            // result is still valid, deterministic and reported by diagnostics.
            for (int level = range[0]; level <= range[1]; level++) {
                builder.add(level, 1);
            }
        }
        return builder.build();
    }

    /**
     * Returns whether configured weights produced at least one legal level.
     *
     * @param enchantment enchantment
     * @param overrides   per-level weight override, may be empty
     * @return {@code true} when the configuration is usable for this enchantment
     */
    public boolean hasConfiguredLevels(Enchantment enchantment, Map<Integer, Integer> overrides) {
        WeightedTable<Integer> table = tableFor(enchantment, overrides);
        if (overrides != null && !overrides.isEmpty()) {
            return table.totalWeight() > 0L && hasPositiveOverride(enchantment, overrides);
        }
        LevelProbabilityConfig config = configSupplier.get();
        if (!config.enabled()) {
            return false;
        }
        Map<Integer, Integer> weights = config.weightsFor(EnchantmentResolver.keyOf(enchantment));
        int[] range = legalRange(enchantment);
        for (Map.Entry<Integer, Integer> entry : weights.entrySet()) {
            if (entry.getValue() > 0 && entry.getKey() >= range[0] && entry.getKey() <= range[1]) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPositiveOverride(Enchantment enchantment, Map<Integer, Integer> overrides) {
        int[] range = legalRange(enchantment);
        for (Map.Entry<Integer, Integer> entry : overrides.entrySet()) {
            if (entry.getValue() > 0 && entry.getKey() >= range[0] && entry.getKey() <= range[1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the legal level range of an enchantment.
     *
     * @param enchantment enchantment
     * @return array of {@code [lowest, highest]}
     */
    public static int[] legalRange(Enchantment enchantment) {
        int lowest = Math.max(1, enchantment.getStartLevel());
        int highest = Math.max(lowest, enchantment.getMaxLevel());
        return new int[] {lowest, highest};
    }

    /**
     * Clamps a level into the legal range of an enchantment.
     *
     * @param enchantment enchantment
     * @param level       requested level
     * @return the clamped level
     */
    public static int clampLevel(Enchantment enchantment, int level) {
        int[] range = legalRange(enchantment);
        return PriceMath.clamp(level, range[0], range[1]);
    }
}
