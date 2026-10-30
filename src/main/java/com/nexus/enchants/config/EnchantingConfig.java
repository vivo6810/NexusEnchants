package com.nexus.enchants.config;

import com.nexus.enchants.util.EnchantmentWeight;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable enchanting-table configuration.
 */
public final class EnchantingConfig {

    private final boolean enabled;
    private final SelectionMode rarityMode;
    private final boolean treasureEnabled;
    private final boolean multipleEnchantmentsEnabled;
    private final int multipleEnchantmentsChance;
    private final int maximumEnchantments;
    private final int additionalMinimumLevel;
    private final int additionalMaximumLevel;
    private final Set<String> excludedEnchantments;
    private final Map<String, EnchantmentWeight> weights;

    /**
     * Creates the configuration.
     *
     * @param enabled                     whether the system is enabled
     * @param rarityMode                  selection strategy
     * @param treasureEnabled             whether treasure enchantments may appear
     * @param multipleEnchantmentsEnabled whether extra enchantments may be added
     * @param multipleEnchantmentsChance  percentage chance for an extra enchantment
     * @param maximumEnchantments         hard cap on enchantments per result
     * @param additionalMinimumLevel      minimum level of additional enchantments
     * @param additionalMaximumLevel      maximum level of additional enchantments
     * @param excludedEnchantments        canonical keys that are never produced
     * @param weights                     canonical key to weight
     */
    public EnchantingConfig(
            boolean enabled,
            SelectionMode rarityMode,
            boolean treasureEnabled,
            boolean multipleEnchantmentsEnabled,
            int multipleEnchantmentsChance,
            int maximumEnchantments,
            int additionalMinimumLevel,
            int additionalMaximumLevel,
            Set<String> excludedEnchantments,
            Map<String, EnchantmentWeight> weights) {
        this.enabled = enabled;
        this.rarityMode = Objects.requireNonNull(rarityMode, "rarityMode");
        this.treasureEnabled = treasureEnabled;
        this.multipleEnchantmentsEnabled = multipleEnchantmentsEnabled;
        this.multipleEnchantmentsChance = multipleEnchantmentsChance;
        this.maximumEnchantments = maximumEnchantments;
        this.additionalMinimumLevel = additionalMinimumLevel;
        this.additionalMaximumLevel = additionalMaximumLevel;
        this.excludedEnchantments = Set.copyOf(excludedEnchantments);
        this.weights = Map.copyOf(weights);
    }

    /**
     * Returns a disabled configuration that leaves vanilla enchanting alone.
     *
     * @return a disabled configuration
     */
    public static EnchantingConfig disabled() {
        return new EnchantingConfig(false, SelectionMode.DISABLED, false, false, 0, 1, 1, 1, Set.of(), Map.of());
    }

    /**
     * Returns whether the weighted table is active.
     *
     * @return {@code true} when enabled and in weighted mode
     */
    public boolean active() {
        return enabled && rarityMode == SelectionMode.WEIGHTED;
    }

    /**
     * Returns the configured weight of an enchantment.
     *
     * @param enchantmentKey canonical enchantment key
     * @return the weight, disabled by default when unlisted or excluded
     */
    public EnchantmentWeight weightOf(String enchantmentKey) {
        if (excludedEnchantments.contains(enchantmentKey)) {
            return new EnchantmentWeight(false, 0);
        }
        EnchantmentWeight weight = weights.get(enchantmentKey);
        return weight == null ? new EnchantmentWeight(false, 0) : weight;
    }

    /**
     * Returns whether any enchantment can be produced.
     *
     * @return {@code true} when at least one weight is selectable
     */
    public boolean hasSelectableEnchantments() {
        for (Map.Entry<String, EnchantmentWeight> entry : weights.entrySet()) {
            if (!excludedEnchantments.contains(entry.getKey()) && entry.getValue().selectable()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether the system is enabled at all.
     *
     * @return the configured enabled flag
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Returns a short label used in the startup log.
     *
     * @return {@code on (weighted)}, {@code off} or {@code on (disabled mode)}
     */
    public String activationLabel() {
        if (!enabled) {
            return "off";
        }
        return rarityMode == SelectionMode.WEIGHTED ? "on (weighted)" : "on (disabled mode)";
    }

    /**
     * Returns the configured selection mode.
     *
     * @return the selection mode
     */
    public SelectionMode rarityMode() {
        return rarityMode;
    }

    /**
     * Returns whether treasure enchantments may be produced.
     *
     * @return {@code true} when treasure enchantments are allowed
     */
    public boolean treasureEnabled() {
        return treasureEnabled;
    }

    /**
     * Returns whether multiple enchantments may be applied.
     *
     * @return {@code true} when extra enchantments are enabled
     */
    public boolean multipleEnchantmentsEnabled() {
        return multipleEnchantmentsEnabled;
    }

    /**
     * Returns the chance for an extra enchantment.
     *
     * @return the percentage chance
     */
    public int multipleEnchantmentsChance() {
        return multipleEnchantmentsChance;
    }

    /**
     * Returns the hard cap on enchantments per result.
     *
     * @return the maximum enchantment count
     */
    public int maximumEnchantments() {
        return maximumEnchantments;
    }

    /**
     * Returns the minimum level of additional enchantments.
     *
     * @return the minimum level
     */
    public int additionalMinimumLevel() {
        return additionalMinimumLevel;
    }

    /**
     * Returns the maximum level of additional enchantments.
     *
     * @return the maximum level
     */
    public int additionalMaximumLevel() {
        return additionalMaximumLevel;
    }

    /**
     * Returns the excluded enchantment keys.
     *
     * @return immutable set of canonical keys
     */
    public Set<String> excludedEnchantments() {
        return excludedEnchantments;
    }

    /**
     * Returns every configured weight.
     *
     * @return immutable canonical key to weight map
     */
    public Map<String, EnchantmentWeight> weights() {
        return weights;
    }
}
