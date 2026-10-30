package com.nexus.enchants.economy;

import java.util.Locale;
import java.util.Optional;

/**
 * How vanilla merchant discounts interact with the configured price.
 */
public enum DiscountPolicy {

    /**
     * Vanilla decides the final price. The configured price is only the base
     * value the vanilla rules are applied to.
     */
    PRESERVE,

    /**
     * Vanilla discounts are switched off. The player pays the configured price.
     */
    DISABLE,

    /**
     * Vanilla discounts are switched off and the plugin re-applies the
     * discounts it can observe, never exceeding the configured cap.
     */
    CAP;

    /**
     * Parses a policy name case insensitively.
     *
     * @param raw raw configuration value
     * @return the parsed policy, or empty when the value is unknown
     */
    public static Optional<DiscountPolicy> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String cleaned = raw.trim().toUpperCase(Locale.ROOT);
        for (DiscountPolicy policy : values()) {
            if (policy.name().equals(cleaned)) {
                return Optional.of(policy);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns whether vanilla price adjustments must be suppressed for this policy.
     *
     * @return {@code true} for {@link #CAP} and {@link #DISABLE}
     */
    public boolean suppressesVanilla() {
        return this != PRESERVE;
    }
}
