package com.nexus.enchants.util;

/**
 * Integer price arithmetic shared by the pricing and discount engines.
 *
 * <p>All operations are overflow safe: the intermediate product is computed in
 * a {@code long} and clamped before it is narrowed back to an {@code int}.</p>
 */
public final class PriceMath {

    /**
     * Hard ceiling for a single merchant trade: two input slots of 64 items.
     */
    public static final int MAX_REPRESENTABLE_PRICE = 128;

    /**
     * Maximum stack size of an emerald, which is also the size of one payment slot.
     */
    public static final int EMERALD_STACK_SIZE = 64;

    private PriceMath() {
    }

    /**
     * Clamps a value into an inclusive range.
     *
     * @param value value to clamp
     * @param minimum lower bound
     * @param maximum upper bound
     * @return the clamped value
     * @throws IllegalArgumentException when {@code minimum} is greater than {@code maximum}
     */
    public static int clamp(int value, int minimum, int maximum) {
        if (minimum > maximum) {
            throw new IllegalArgumentException("minimum " + minimum + " is greater than maximum " + maximum);
        }
        return Math.min(maximum, Math.max(minimum, value));
    }

    /**
     * Computes the discount amount for a percentage of a base price.
     *
     * <p>The result is rounded down so the server never loses emeralds to
     * rounding. Negative percentages are treated as zero.</p>
     *
     * @param basePrice base price
     * @param percent   discount percentage
     * @return the emerald amount to subtract
     */
    public static int discountAmount(int basePrice, int percent) {
        return discountAmount(basePrice, percent, Rounding.DOWN);
    }

    /**
     * Computes the discount amount for a percentage of a base price.
     *
     * @param basePrice base price, negative values are treated as zero
     * @param percent   discount percentage, negative values are treated as zero
     * @param rounding  rounding direction
     * @return the emerald amount to subtract, never negative
     */
    public static int discountAmount(int basePrice, int percent, Rounding rounding) {
        if (basePrice <= 0 || percent <= 0) {
            return 0;
        }
        long product = (long) basePrice * (long) percent;
        long amount = rounding == Rounding.UP
                ? Math.floorDiv(product + 99L, 100L)
                : Math.floorDiv(product, 100L);
        return (int) Math.min(basePrice, amount);
    }

    /**
     * Applies a discount percentage to a base price.
     *
     * @param basePrice base price
     * @param percent   discount percentage
     * @param minimum   lowest acceptable result
     * @return the discounted price, never below {@code minimum}
     */
    public static int applyDiscount(int basePrice, int percent, int minimum) {
        int discounted = basePrice - discountAmount(basePrice, percent);
        return Math.max(minimum, discounted);
    }

    /**
     * Validates that an amount can be paid in a single merchant trade.
     *
     * @param amount total emerald amount
     * @return {@code true} when the amount fits into the two merchant input slots
     */
    public static boolean isRepresentable(int amount) {
        return amount >= 0 && amount <= MAX_REPRESENTABLE_PRICE;
    }

    /**
     * Rounding direction for percentage calculations.
     */
    public enum Rounding {
        /**
         * Round the discount down, favouring the server.
         */
        DOWN,
        /**
         * Round the discount up, favouring the player.
         */
        UP
    }
}
