package com.nexus.enchants.economy;

import com.nexus.enchants.util.PriceMath;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates a planned merchant trade before it reaches the Bukkit API.
 *
 * <p>Every rejection produces a human readable problem so the caller can log or
 * report it instead of silently falling back to vanilla behaviour.</p>
 */
public final class TradeValidator {

    /**
     * Creates the validator.
     */
    public TradeValidator() {
    }

    /**
     * Validates an emerald price.
     *
     * @param price   price in emeralds
     * @param minimum configured minimum
     * @param maximum configured maximum
     * @return the validation result
     */
    public Result validatePrice(int price, int minimum, int maximum) {
        List<String> problems = new ArrayList<>();
        if (price < minimum) {
            problems.add("price " + price + " is below the configured minimum " + minimum);
        }
        if (price > maximum) {
            problems.add("price " + price + " exceeds the configured maximum " + maximum);
        }
        if (price < 0) {
            problems.add("price " + price + " is negative");
        }
        if (!PriceMath.isRepresentable(price)) {
            problems.add("price " + price + " cannot be paid with two merchant input slots");
        }
        if (minimum > maximum) {
            problems.add("minimum " + minimum + " is greater than maximum " + maximum);
        }
        return new Result(problems.isEmpty(), List.copyOf(problems));
    }

    /**
     * Validates a payment plan against the configured limits.
     *
     * @param plan    payment plan
     * @param minimum configured minimum
     * @param maximum configured maximum
     * @return the validation result
     */
    public Result validatePayment(PaymentPlan plan, int minimum, int maximum) {
        return validatePrice(plan.total(), minimum, maximum);
    }

    /**
     * Validation outcome.
     *
     * @param valid    whether the trade may be applied
     * @param problems human readable problems, empty when valid
     */
    public record Result(boolean valid, List<String> problems) {

        /**
         * Validates the result.
         *
         * @param valid    whether the trade may be applied
         * @param problems human readable problems
         */
        public Result {
            problems = List.copyOf(problems);
        }

        /**
         * Returns a single line summary of the problems.
         *
         * @return the joined problems, or an empty string when valid
         */
        public String summary() {
            return String.join("; ", problems);
        }
    }
}
