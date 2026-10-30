package com.nexus.enchants.economy;

import com.nexus.enchants.config.DiscountConfig;
import com.nexus.enchants.util.PriceMath;
import java.util.Objects;

/**
 * Applies the configured discount policy to a base price.
 *
 * <p>The manager is pure so that rounding, capping and policy behaviour can be
 * unit tested without a server. The caller supplies the only discount input the
 * Paper API exposes reliably: the player's Hero of the Village amplifier.</p>
 *
 * <p>Cured-villager and gossip reputation discounts are not observable through
 * the Paper 26.3 API. Under {@link DiscountPolicy#CAP} and
 * {@link DiscountPolicy#DISABLE} every vanilla price adjustment is suppressed
 * via {@code MerchantRecipe#setIgnoreDiscounts(true)}, which also suppresses
 * those two sources. They are therefore reported, not approximated. See
 * docs/API_COMPATIBILITY.md.</p>
 */
public final class DiscountManager {

    private final DiscountConfig config;

    /**
     * Creates the manager.
     *
     * @param config immutable discount configuration
     */
    public DiscountManager(DiscountConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Evaluates the final price for a player.
     *
     * @param basePrice  configured base price
     * @param heroLevel  Hero of the Village amplifier, {@code 0} when absent
     * @param minimumPrice lowest acceptable price
     * @return the discount result
     * @throws IllegalArgumentException when the base price is negative
     */
    public DiscountResult evaluate(int basePrice, int heroLevel, int minimumPrice) {
        if (basePrice < 0) {
            throw new IllegalArgumentException("basePrice must not be negative");
        }
        int floor = Math.max(0, minimumPrice);
        if (basePrice == 0) {
            return new DiscountResult(config.policy(), 0, 0, 0, false, "free");
        }
        return switch (config.policy()) {
            case DISABLE -> new DiscountResult(DiscountPolicy.DISABLE, basePrice, basePrice,
                    0, false, "vanilla discounts disabled");
            case PRESERVE -> new DiscountResult(DiscountPolicy.PRESERVE, basePrice, basePrice,
                    0, false, "vanilla discounts preserved");
            case CAP -> evaluateCap(basePrice, heroLevel, floor);
        };
    }

    /**
     * Evaluates a price without any player context.
     *
     * @param basePrice configured base price
     * @param minimumPrice lowest acceptable price
     * @return the discount result with no discount applied
     */
    public DiscountResult evaluateBase(int basePrice, int minimumPrice) {
        return evaluate(basePrice, 0, minimumPrice);
    }

    private DiscountResult evaluateCap(int basePrice, int heroLevel, int floor) {
        int requestedPercent = 0;
        boolean capped = false;
        if (config.heroOfTheVillageEnabled() && heroLevel > 0) {
            long raw = (long) (heroLevel + 1) * (long) config.heroPercentagePerLevel();
            if (raw > config.maximumDiscountPercentage()) {
                requestedPercent = config.maximumDiscountPercentage();
                capped = true;
            } else {
                requestedPercent = (int) raw;
            }
        }
        int grantedPercent = PriceMath.clamp(requestedPercent, 0, config.maximumDiscountPercentage());
        int finalPrice = PriceMath.applyDiscount(basePrice, grantedPercent, Math.max(floor, 1));
        finalPrice = Math.min(basePrice, finalPrice);
        String reason = grantedPercent > 0
                ? "plugin discount " + grantedPercent + "% (cap " + config.maximumDiscountPercentage() + "%)"
                : "no plugin discount";
        return new DiscountResult(DiscountPolicy.CAP, basePrice, finalPrice, grantedPercent, capped, reason);
    }

    /**
     * Returns whether vanilla price adjustments must be blocked for the
     * recipe produced under the current policy.
     *
     * @return {@code true} for the CAP and DISABLE policies
     */
    public boolean suppressesVanillaDiscounts() {
        return config.policy().suppressesVanilla();
    }

    /**
     * Returns the configuration used by this manager.
     *
     * @return the immutable discount configuration
     */
    public DiscountConfig config() {
        return config;
    }
}
