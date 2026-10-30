package com.nexus.enchants.config;

import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable level probability configuration.
 *
 * <p>Weights are normalised at selection time across the levels that are both
 * configured and legal for the enchantment, so an illegal level can never be
 * produced.</p>
 */
public final class LevelProbabilityConfig {

    private final boolean enabled;
    private final Map<Integer, Integer> defaultWeights;
    private final Map<String, Map<Integer, Integer>> overrides;

    /**
     * Creates the configuration.
     *
     * @param enabled        whether level weights are used
     * @param defaultWeights level to weight used when no override exists
     * @param overrides      canonical enchantment key to level to weight
     */
    public LevelProbabilityConfig(
            boolean enabled,
            Map<Integer, Integer> defaultWeights,
            Map<String, Map<Integer, Integer>> overrides) {
        this.enabled = enabled;
        this.defaultWeights = Map.copyOf(new TreeMap<>(defaultWeights));
        Map<String, Map<Integer, Integer>> copy = new TreeMap<>();
        overrides.forEach((key, levels) -> copy.put(key, Map.copyOf(new TreeMap<>(levels))));
        this.overrides = Map.copyOf(copy);
    }

    /**
     * Returns a configuration that produces no weighted levels.
     *
     * @return a disabled configuration
     */
    public static LevelProbabilityConfig disabled() {
        return new LevelProbabilityConfig(false, Map.of(), Map.of());
    }

    /**
     * Returns the weight table for an enchantment.
     *
     * @param enchantmentKey canonical enchantment key
     * @return level to weight map, the override when present and the default otherwise
     */
    public Map<Integer, Integer> weightsFor(String enchantmentKey) {
        Map<Integer, Integer> override = overrides.get(enchantmentKey);
        return override != null ? override : defaultWeights;
    }

    /**
     * Returns whether an override exists for the enchantment.
     *
     * @param enchantmentKey canonical enchantment key
     * @return {@code true} when an override is configured
     */
    public boolean hasOverride(String enchantmentKey) {
        return overrides.containsKey(enchantmentKey);
    }

    /**
     * Returns whether level weights are enabled.
     *
     * @return the configured enabled flag
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Returns the default weights.
     *
     * @return level to weight map
     */
    public Map<Integer, Integer> defaultWeights() {
        return defaultWeights;
    }

    /**
     * Returns every override.
     *
     * @return immutable canonical key to level to weight map
     */
    public Map<String, Map<Integer, Integer>> overrides() {
        return overrides;
    }
}
