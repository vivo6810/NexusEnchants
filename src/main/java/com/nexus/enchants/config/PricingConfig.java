package com.nexus.enchants.config;

import com.nexus.enchants.util.PriceMath;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * Immutable enchanted-book pricing configuration.
 *
 * <p>Keys are canonical enchantment keys ({@code minecraft:mending}). Level
 * maps are sorted so that diagnostics and command output are stable.</p>
 */
public final class PricingConfig {

    private final Map<String, Map<Integer, Integer>> prices;
    private final boolean fallbackEnabled;
    private final int fallbackPrice;
    private final int minimumPrice;
    private final int maximumPrice;
    private final boolean allowZero;

    /**
     * Creates the configuration.
     *
     * @param prices          canonical enchantment key to level to price
     * @param fallbackEnabled whether unlisted levels use the fallback price
     * @param fallbackPrice   fallback price
     * @param minimumPrice    lowest allowed price
     * @param maximumPrice    highest allowed price
     * @param allowZero       whether a price of zero is allowed
     */
    public PricingConfig(
            Map<String, Map<Integer, Integer>> prices,
            boolean fallbackEnabled,
            int fallbackPrice,
            int minimumPrice,
            int maximumPrice,
            boolean allowZero) {
        Objects.requireNonNull(prices, "prices");
        Map<String, Map<Integer, Integer>> copy = new LinkedHashMap<>();
        prices.forEach((key, levels) -> copy.put(key, Map.copyOf(new TreeMap<>(levels))));
        this.prices = Map.copyOf(copy);
        this.fallbackEnabled = fallbackEnabled;
        this.fallbackPrice = fallbackPrice;
        this.minimumPrice = minimumPrice;
        this.maximumPrice = maximumPrice;
        this.allowZero = allowZero;
    }

    /**
     * Returns an empty configuration that rejects every lookup.
     *
     * @return a configuration with no prices and no fallback
     */
    public static PricingConfig empty() {
        return new PricingConfig(Map.of(), false, 0, 1, PriceMath.MAX_REPRESENTABLE_PRICE, false);
    }

    /**
     * Returns the explicit price of an enchantment level.
     *
     * @param enchantmentKey canonical enchantment key
     * @param level          enchantment level
     * @return the configured price, or empty when unlisted
     */
    public OptionalInt explicitPrice(String enchantmentKey, int level) {
        if (enchantmentKey == null) {
            return OptionalInt.empty();
        }
        Map<Integer, Integer> levels = prices.get(enchantmentKey);
        if (levels == null) {
            return OptionalInt.empty();
        }
        Integer price = levels.get(level);
        return price == null ? OptionalInt.empty() : OptionalInt.of(price);
    }

    /**
     * Returns every configured level for an enchantment.
     *
     * @param enchantmentKey canonical enchantment key
     * @return the level to price map, empty when unlisted
     */
    public Map<Integer, Integer> levelsFor(String enchantmentKey) {
        return prices.getOrDefault(enchantmentKey, Map.of());
    }

    /**
     * Returns the full price table.
     *
     * @return immutable enchantment key to level to price map
     */
    public Map<String, Map<Integer, Integer>> prices() {
        return prices;
    }

    /**
     * Returns a copy with one price replaced or added.
     *
     * @param enchantmentKey canonical enchantment key
     * @param level          enchantment level
     * @param price          new price
     * @return a new configuration instance
     */
    public PricingConfig withPrice(String enchantmentKey, int level, int price) {
        Map<String, Map<Integer, Integer>> copy = new LinkedHashMap<>();
        prices.forEach((key, levels) -> copy.put(key, new LinkedHashMap<>(levels)));
        copy.computeIfAbsent(enchantmentKey, key -> new TreeMap<>()).put(level, price);
        return new PricingConfig(copy, fallbackEnabled, fallbackPrice, minimumPrice, maximumPrice, allowZero);
    }

    /**
     * Returns whether the fallback price is enabled.
     *
     * @return {@code true} when enabled
     */
    public boolean fallbackEnabled() {
        return fallbackEnabled;
    }

    /**
     * Returns the fallback price.
     *
     * @return the fallback price in emeralds
     */
    public int fallbackPrice() {
        return fallbackPrice;
    }

    /**
     * Returns the configured minimum price.
     *
     * @return the minimum price
     */
    public int minimumPrice() {
        return minimumPrice;
    }

    /**
     * Returns the configured maximum price.
     *
     * @return the maximum price
     */
    public int maximumPrice() {
        return maximumPrice;
    }

    /**
     * Returns whether a price of zero is allowed.
     *
     * @return {@code true} when zero is allowed
     */
    public boolean allowZero() {
        return allowZero;
    }
}
