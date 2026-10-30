package com.nexus.enchants.economy;

/**
 * Thrown when no price and no fallback price exist for an enchantment level.
 */
public class PriceNotConfiguredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String enchantmentKey;
    private final int level;

    /**
     * Creates the exception.
     *
     * @param enchantmentKey canonical enchantment key
     * @param level          enchantment level
     */
    public PriceNotConfiguredException(String enchantmentKey, int level) {
        super("No price configured for " + enchantmentKey + " level " + level + " and no fallback is enabled");
        this.enchantmentKey = enchantmentKey;
        this.level = level;
    }

    /**
     * Returns the enchantment key that had no price.
     *
     * @return the canonical enchantment key
     */
    public String enchantmentKey() {
        return enchantmentKey;
    }

    /**
     * Returns the level that had no price.
     *
     * @return the enchantment level
     */
    public int level() {
        return level;
    }
}
