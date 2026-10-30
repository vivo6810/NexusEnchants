package com.nexus.enchants.config;

import java.util.List;
import java.util.Objects;

/**
 * Immutable, consistently loaded view of every configuration file.
 *
 * <p>A snapshot is built once per load/reload and then handed to the services,
 * so no service ever observes a half-applied configuration.</p>
 *
 * @param settings      general settings
 * @param pricing       enchanted-book pricing
 * @param villagerRarity librarian rarity
 * @param enchanting    enchanting-table rarity
 * @param levels        level probabilities
 * @param sources       acquisition source restrictions
 * @param discounts     discount policy
 * @param database      database configuration
 * @param issues        findings collected while loading
 * @param loadedAtMillis epoch milliseconds when the snapshot was built
 */
public record ConfigSnapshot(
        PluginSettings settings,
        PricingConfig pricing,
        VillagerRarityConfig villagerRarity,
        EnchantingConfig enchanting,
        LevelProbabilityConfig levels,
        SourceConfig sources,
        DiscountConfig discounts,
        DatabaseConfig database,
        List<ConfigIssue> issues,
        long loadedAtMillis) {

    /**
     * Validates the snapshot.
     *
     * @param settings       general settings
     * @param pricing        pricing
     * @param villagerRarity librarian rarity
     * @param enchanting     enchanting rarity
     * @param levels         level probabilities
     * @param sources        source restrictions
     * @param discounts      discount policy
     * @param database       database configuration
     * @param issues         findings
     * @param loadedAtMillis load timestamp
     */
    public ConfigSnapshot {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(pricing, "pricing");
        Objects.requireNonNull(villagerRarity, "villagerRarity");
        Objects.requireNonNull(enchanting, "enchanting");
        Objects.requireNonNull(levels, "levels");
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(discounts, "discounts");
        Objects.requireNonNull(database, "database");
        issues = List.copyOf(issues);
    }

    /**
     * Returns the issues with {@link ConfigIssue.Severity#ERROR}.
     *
     * @return the error issues
     */
    public List<ConfigIssue> errors() {
        return issues.stream().filter(issue -> issue.severity() == ConfigIssue.Severity.ERROR).toList();
    }

    /**
     * Returns the issues with {@link ConfigIssue.Severity#WARNING}.
     *
     * @return the warning issues
     */
    public List<ConfigIssue> warnings() {
        return issues.stream().filter(issue -> issue.severity() == ConfigIssue.Severity.WARNING).toList();
    }
}
