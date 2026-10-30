package com.nexus.enchants.economy;

/**
 * Outcome of a discount evaluation.
 *
 * @param policy            policy that produced the result
 * @param basePrice         configured base price
 * @param finalPrice        price the player pays
 * @param appliedPercentage discount percentage actually granted by the plugin
 * @param capped            whether the requested discount was reduced to the cap
 * @param reason            short human readable explanation used by diagnostics
 */
public record DiscountResult(
        DiscountPolicy policy,
        int basePrice,
        int finalPrice,
        int appliedPercentage,
        boolean capped,
        String reason) {

    /**
     * Validates the result.
     *
     * @param policy            policy that produced the result
     * @param basePrice         configured base price
     * @param finalPrice        price the player pays
     * @param appliedPercentage discount percentage actually granted by the plugin
     * @param capped            whether the requested discount was reduced to the cap
     * @param reason            short human readable explanation
     */
    public DiscountResult {
        if (basePrice < 0) {
            throw new IllegalArgumentException("basePrice must not be negative");
        }
        if (finalPrice < 0) {
            throw new IllegalArgumentException("finalPrice must not be negative");
        }
    }

    /**
     * Returns the effective discount percentage relative to the base price.
     *
     * @return the realised percentage, rounded down
     */
    public int realisedPercentage() {
        if (basePrice <= 0 || finalPrice >= basePrice) {
            return 0;
        }
        return (int) (((long) (basePrice - finalPrice) * 100L) / (long) basePrice);
    }

    /**
     * Returns the emerald amount saved by the payer.
     *
     * @return the saved amount
     */
    public int savedAmount() {
        return Math.max(0, basePrice - finalPrice);
    }
}
