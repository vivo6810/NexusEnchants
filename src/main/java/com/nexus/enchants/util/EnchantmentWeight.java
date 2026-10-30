package com.nexus.enchants.util;

/**
 * A configurable rarity weight.
 *
 * @param enabled whether the entry may be selected at all
 * @param weight  relative weight, ignored when disabled or zero
 */
public record EnchantmentWeight(boolean enabled, int weight) {

    /**
     * Effective weight used by selection tables.
     *
     * @return the configured weight when enabled, otherwise {@code 0}
     */
    public int effectiveWeight() {
        return enabled ? weight : 0;
    }

    /**
     * Returns whether this entry can ever be selected.
     *
     * @return {@code true} when enabled with a positive weight
     */
    public boolean selectable() {
        return enabled && weight > 0;
    }
}
