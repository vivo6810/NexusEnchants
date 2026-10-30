package com.nexus.enchants.economy;

import com.nexus.enchants.config.PricingConfig;
import com.nexus.enchants.util.PriceMath;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Resolves the configured base price of an enchanted book.
 *
 * <p>The calculator is pure: it reads an immutable {@link PricingConfig} and
 * never touches the Bukkit API, the database or the server thread.</p>
 */
public final class PriceCalculator {

    private final PricingConfig config;

    /**
     * Creates the calculator.
     *
     * @param config immutable pricing configuration
     */
    public PriceCalculator(PricingConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Returns the base price for an enchantment level.
     *
     * <p>Explicit prices are used first, then the fallback price when enabled.
     * The result is always clamped into the configured limits.</p>
     *
     * @param enchantmentKey canonical enchantment key
     * @param level          enchantment level, at least 1
     * @return the base price in emeralds
     * @throws IllegalArgumentException     when the level is below 1
     * @throws PriceNotConfiguredException  when no price and no fallback exist
     */
    public int basePrice(String enchantmentKey, int level) {
        if (level < 1) {
            throw new IllegalArgumentException("Enchantment level must be at least 1, got " + level);
        }
        Objects.requireNonNull(enchantmentKey, "enchantmentKey");
        OptionalInt explicit = config.explicitPrice(enchantmentKey, level);
        int raw;
        if (explicit.isPresent()) {
            raw = explicit.getAsInt();
        } else if (config.fallbackEnabled()) {
            raw = config.fallbackPrice();
        } else {
            throw new PriceNotConfiguredException(enchantmentKey, level);
        }
        if (raw == 0 && config.allowZero()) {
            return 0;
        }
        return PriceMath.clamp(raw, config.minimumPrice(), config.maximumPrice());
    }

    /**
     * Returns whether an explicit price exists for the enchantment level.
     *
     * @param enchantmentKey canonical enchantment key
     * @param level          enchantment level
     * @return {@code true} when a price is configured explicitly
     */
    public boolean hasExplicitPrice(String enchantmentKey, int level) {
        return config.explicitPrice(enchantmentKey, level).isPresent();
    }

    /**
     * Returns whether the configured price would be paid without a discount.
     *
     * @param enchantmentKey canonical enchantment key
     * @param level          enchantment level
     * @return the payment plan for the base price
     * @throws PriceNotConfiguredException when no price can be resolved
     */
    public PaymentPlan basePayment(String enchantmentKey, int level) {
        return PaymentPlan.of(basePrice(enchantmentKey, level));
    }

    /**
     * Returns the pricing configuration used by this calculator.
     *
     * @return the immutable pricing configuration
     */
    public PricingConfig config() {
        return config;
    }
}
