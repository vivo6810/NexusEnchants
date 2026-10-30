package com.nexus.enchants.config;

import com.nexus.enchants.economy.DiscountPolicy;
import java.util.Objects;

/**
 * Immutable discount configuration.
 *
 * @param policy                       active policy
 * @param maximumDiscountPercentage    highest discount the plugin will grant
 * @param heroOfTheVillageEnabled      whether Hero of the Village is re-applied under CAP
 * @param heroPercentagePerLevel       percentage granted per Hero of the Village level
 * @param curingDiscountsReported      whether cured-villager discounts are reported as suppressed
 * @param reputationDiscountsReported  whether gossip reputation discounts are reported as suppressed
 */
public record DiscountConfig(
        DiscountPolicy policy,
        int maximumDiscountPercentage,
        boolean heroOfTheVillageEnabled,
        int heroPercentagePerLevel,
        boolean curingDiscountsReported,
        boolean reputationDiscountsReported) {

    /**
     * Validates the configuration.
     *
     * @param policy                       active policy
     * @param maximumDiscountPercentage    highest discount the plugin will grant
     * @param heroOfTheVillageEnabled      whether Hero of the Village is re-applied under CAP
     * @param heroPercentagePerLevel       percentage granted per Hero of the Village level
     * @param curingDiscountsReported      whether cured-villager discounts are reported
     * @param reputationDiscountsReported  whether reputation discounts are reported
     */
    public DiscountConfig {
        Objects.requireNonNull(policy, "policy");
        if (maximumDiscountPercentage < 0 || maximumDiscountPercentage > 100) {
            throw new IllegalArgumentException("maximumDiscountPercentage must be between 0 and 100");
        }
        if (heroPercentagePerLevel < 0 || heroPercentagePerLevel > 100) {
            throw new IllegalArgumentException("heroPercentagePerLevel must be between 0 and 100");
        }
    }

    /**
     * Returns the default configuration.
     *
     * @return CAP at 20 percent with Hero of the Village re-applied
     */
    public static DiscountConfig defaults() {
        return new DiscountConfig(DiscountPolicy.CAP, 20, true, 5, true, true);
    }
}
