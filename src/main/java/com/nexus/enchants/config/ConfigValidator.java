package com.nexus.enchants.config;

import com.nexus.enchants.economy.DiscountPolicy;
import com.nexus.enchants.sources.AcquisitionSource;
import com.nexus.enchants.util.EnchantmentResolver;
import com.nexus.enchants.util.EnchantmentWeight;
import com.nexus.enchants.util.PriceMath;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Cross-file configuration validation.
 *
 * <p>Parsing lives in {@link ConfigManager}; this class only answers "is the
 * resulting snapshot internally consistent?". Every finding names the file and
 * YAML path so an administrator can act on it without reading the source.</p>
 */
public final class ConfigValidator {

    /**
     * Creates the validator.
     */
    public ConfigValidator() {
    }

    /**
     * Validates a fully parsed snapshot.
     *
     * @param snapshot snapshot to validate
     * @return additional issues, never {@code null}
     */
    public List<ConfigIssue> validateSnapshot(ConfigSnapshot snapshot) {
        List<ConfigIssue> issues = new ArrayList<>();
        validatePricing(snapshot, issues);
        validateVillagerRarity(snapshot, issues);
        validateEnchanting(snapshot, issues);
        validateLevels(snapshot, issues);
        validateDiscounts(snapshot, issues);
        validateDatabase(snapshot, issues);
        return issues;
    }

    private void validatePricing(ConfigSnapshot snapshot, List<ConfigIssue> issues) {
        PricingConfig pricing = snapshot.pricing();
        if (pricing.minimumPrice() > pricing.maximumPrice()) {
            issues.add(error("pricing.yml", "limits",
                    "minimum " + pricing.minimumPrice() + " is greater than maximum " + pricing.maximumPrice()));
        }
        if (pricing.maximumPrice() > PriceMath.MAX_REPRESENTABLE_PRICE) {
            issues.add(error("pricing.yml", "limits.maximum",
                    "a single merchant trade can require at most " + PriceMath.MAX_REPRESENTABLE_PRICE
                            + " emeralds (two slots of " + PriceMath.EMERALD_STACK_SIZE + ")"));
        }
        if (pricing.minimumPrice() < 0) {
            issues.add(error("pricing.yml", "limits.minimum", "minimum price must not be negative"));
        }
        if (pricing.fallbackEnabled() && pricing.fallbackPrice() < 0) {
            issues.add(error("pricing.yml", "fallback.price", "fallback price must not be negative"));
        }
        if (pricing.prices().isEmpty() && !pricing.fallbackEnabled()) {
            issues.add(error("pricing.yml", "pricing",
                    "no prices are configured and the fallback price is disabled, no book can be sold"));
        }
    }

    private void validateVillagerRarity(ConfigSnapshot snapshot, List<ConfigIssue> issues) {
        VillagerRarityConfig rarity = snapshot.villagerRarity();
        if (rarity.enabled() && rarity.selectionMode() == SelectionMode.WEIGHTED
                && !rarity.hasSelectableEnchantments()) {
            issues.add(error("villager-rarity.yml", "villager-rarity.enchantments",
                    "every enchantment is disabled or has weight 0, no librarian book can be generated"));
        }
        for (Map.Entry<String, EnchantmentWeight> entry : rarity.weights().entrySet()) {
            if (entry.getValue().weight() < 0) {
                issues.add(error("villager-rarity.yml", "villager-rarity.enchantments." + entry.getKey(),
                        "weight must not be negative"));
            }
        }
    }

    private void validateEnchanting(ConfigSnapshot snapshot, List<ConfigIssue> issues) {
        EnchantingConfig enchanting = snapshot.enchanting();
        if (enchanting.enabled() && enchanting.rarityMode() == SelectionMode.WEIGHTED
                && !enchanting.hasSelectableEnchantments()) {
            issues.add(error("enchanting-rarity.yml", "enchanting-table.enchantment-weights",
                    "every enchantment is disabled or excluded, the table could not produce a result"));
        }
        if (enchanting.multipleEnchantmentsChance() < 0 || enchanting.multipleEnchantmentsChance() > 100) {
            issues.add(error("enchanting-rarity.yml", "enchanting-table.multiple-enchantments.chance",
                    "chance must be between 0 and 100"));
        }
        if (enchanting.maximumEnchantments() < 1) {
            issues.add(error("enchanting-rarity.yml", "enchanting-table.multiple-enchantments.maximum",
                    "maximum must be at least 1"));
        }
        if (enchanting.additionalMinimumLevel() > enchanting.additionalMaximumLevel()) {
            issues.add(error("enchanting-rarity.yml", "enchanting-table.multiple-enchantments.minimum-level",
                    "minimum-level is greater than maximum-level"));
        }
        if (enchanting.enabled() && !enchanting.treasureEnabled()
                && enchanting.excludedEnchantments().isEmpty()) {
            issues.add(warning("enchanting-rarity.yml", "enchanting-table.treasure-enchantments.enabled",
                    "treasure enchantments are blocked globally; list any exception in excluded-enchantments"));
        }
    }

    private void validateLevels(ConfigSnapshot snapshot, List<ConfigIssue> issues) {
        LevelProbabilityConfig levels = snapshot.levels();
        if (!levels.enabled()) {
            return;
        }
        if (!hasPositiveWeight(levels.defaultWeights()) && levels.overrides().isEmpty()) {
            issues.add(error("enchantment-levels.yml", "enchantment-levels.default",
                    "every level weight is zero, no enchantment level could be selected"));
        }
    }

    private void validateDiscounts(ConfigSnapshot snapshot, List<ConfigIssue> issues) {
        DiscountConfig discounts = snapshot.discounts();
        if (discounts.policy() == DiscountPolicy.CAP && discounts.maximumDiscountPercentage() == 0) {
            issues.add(warning("discounts.yml", "discounts.maximum-discount-percentage",
                    "policy CAP with a cap of 0 behaves like DISABLE"));
        }
        if (discounts.policy().suppressesVanilla()
                && (discounts.curingDiscountsReported() || discounts.reputationDiscountsReported())) {
            issues.add(warning("discounts.yml", "discounts.curing-discounts",
                    "cured-villager and reputation discounts cannot be read through the Paper API; under policy "
                            + discounts.policy() + " they are suppressed instead of capped. Use PRESERVE to keep "
                            + "the vanilla behaviour."));
        }
    }

    private void validateDatabase(ConfigSnapshot snapshot, List<ConfigIssue> issues) {
        DatabaseConfig database = snapshot.database();
        if (database.type() == DatabaseType.MYSQL) {
            DatabaseConfig.MysqlSettings mysql = database.mysql();
            if (mysql == null) {
                issues.add(error("config.yml", "database.mysql", "MYSQL requires the mysql block to be present"));
                return;
            }
            if ("CHANGE_ME".equals(mysql.password()) || mysql.password().isBlank()) {
                issues.add(error("config.yml", "database.mysql.password",
                        "set a real database password before using MYSQL"));
            }
            if ("nexus_user".equals(mysql.username())) {
                issues.add(warning("config.yml", "database.mysql.username",
                        "the example username is still configured"));
            }
        }
        String journalMode = database.journalMode() == null ? "" : database.journalMode().toUpperCase();
        if (!Set.of("WAL", "DELETE", "TRUNCATE", "PERSIST", "MEMORY", "OFF").contains(journalMode)) {
            issues.add(error("config.yml", "database.sqlite.options.journal-mode",
                    "expected one of WAL, DELETE, TRUNCATE, PERSIST, MEMORY, OFF"));
        }
    }

    private static boolean hasPositiveWeight(Map<Integer, Integer> weights) {
        for (int weight : weights.values()) {
            if (weight > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves a configured enchantment name to its canonical key.
     *
     * @param raw    raw configuration value
     * @param file   file the value came from
     * @param path   YAML path of the value
     * @param issues issue collector
     * @return the canonical key, or empty when the enchantment is unknown
     */
    public static Optional<String> canonicalKey(String raw, String file, String path, List<ConfigIssue> issues) {
        Optional<org.bukkit.enchantments.Enchantment> resolved = EnchantmentResolver.resolve(raw);
        if (resolved.isEmpty()) {
            issues.add(error(file, path, "unknown enchantment '" + raw
                    + "'. Use the vanilla name (MENDING) or the namespaced key (minecraft:mending)"));
            return Optional.empty();
        }
        return Optional.of(EnchantmentResolver.keyOf(resolved.get()));
    }

    /**
     * Parses a percentage value, adding an issue when it is out of range.
     *
     * @param value  raw value
     * @param file   file the value came from
     * @param path   YAML path of the value
     * @param issues issue collector
     * @return the value clamped into {@code 0..100}
     */
    public static int percentage(int value, String file, String path, List<ConfigIssue> issues) {
        if (value < 0 || value > 100) {
            issues.add(error(file, path, "expected a percentage between 0 and 100, got " + value));
            return PriceMath.clamp(value, 0, 100);
        }
        return value;
    }

    /**
     * Parses a non-negative weight.
     *
     * @param value  raw value
     * @param file   file the value came from
     * @param path   YAML path of the value
     * @param issues issue collector
     * @return the weight, {@code 0} when negative
     */
    public static int weight(int value, String file, String path, List<ConfigIssue> issues) {
        if (value < 0) {
            issues.add(error(file, path, "weight must not be negative, got " + value));
            return 0;
        }
        return value;
    }

    /**
     * Collects the weight table of an enchantment section.
     *
     * @param section enchantment section
     * @param file    file the section came from
     * @param path    YAML path of the section
     * @param issues  issue collector
     * @return canonical key to weight
     */
    public static Map<String, EnchantmentWeight> readWeights(
            org.bukkit.configuration.ConfigurationSection section,
            String file,
            String path,
            List<ConfigIssue> issues) {
        Map<String, EnchantmentWeight> weights = new LinkedHashMap<>();
        if (section == null) {
            return weights;
        }
        for (String rawKey : section.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection entry = section.getConfigurationSection(rawKey);
            String entryPath = path + "." + rawKey;
            if (entry == null) {
                issues.add(error(file, entryPath, "expected a section with 'enabled' and 'weight'"));
                continue;
            }
            Optional<String> canonical = canonicalKey(rawKey, file, entryPath, issues);
            if (canonical.isEmpty()) {
                continue;
            }
            boolean enabled = entry.getBoolean("enabled", true);
            int weight = weight(entry.getInt("weight", 0), file, entryPath + ".weight", issues);
            weights.put(canonical.get(), new EnchantmentWeight(enabled, weight));
        }
        return weights;
    }

    /**
     * Collects a set of enchantment keys.
     *
     * @param values raw keys
     * @param file   file the values came from
     * @param path   YAML path of the list
     * @param issues issue collector
     * @return canonical keys
     */
    public static Set<String> readKeys(List<String> values, String file, String path, List<ConfigIssue> issues) {
        Set<String> keys = new LinkedHashSet<>();
        for (String value : values) {
            canonicalKey(value, file, path, issues).ifPresent(keys::add);
        }
        return keys;
    }

    /**
     * Reads level weight entries such as {@code level-1: 45}.
     *
     * @param section section holding the entries
     * @param file    file the section came from
     * @param path    YAML path of the section
     * @param issues  issue collector
     * @return level to weight map, sorted by level
     */
    public static Map<Integer, Integer> readLevelWeights(
            org.bukkit.configuration.ConfigurationSection section,
            String file,
            String path,
            List<ConfigIssue> issues) {
        Map<Integer, Integer> weights = new TreeMap<>();
        if (section == null) {
            return weights;
        }
        for (String key : section.getKeys(false)) {
            if (!key.startsWith("level-")) {
                issues.add(error(file, path + "." + key, "expected a key of the form level-1 .. level-N"));
                continue;
            }
            int level;
            try {
                level = Integer.parseInt(key.substring("level-".length()));
            } catch (NumberFormatException exception) {
                issues.add(error(file, path + "." + key, "level must be a number"));
                continue;
            }
            if (level < 1) {
                issues.add(error(file, path + "." + key, "level must be at least 1"));
                continue;
            }
            weights.put(level, weight(section.getInt(key, 0), file, path + "." + key, issues));
        }
        return weights;
    }

    /**
     * Reads a per-enchantment level weight table.
     *
     * @param section section holding the entries
     * @param file    file the section came from
     * @param path    YAML path of the section
     * @param issues  issue collector
     * @return canonical key to level to weight
     */
    public static Map<String, Map<Integer, Integer>> readLevelOverrides(
            org.bukkit.configuration.ConfigurationSection section,
            String file,
            String path,
            List<ConfigIssue> issues) {
        Map<String, Map<Integer, Integer>> overrides = new LinkedHashMap<>();
        if (section == null) {
            return overrides;
        }
        for (String rawKey : section.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection levels = section.getConfigurationSection(rawKey);
            String entryPath = path + "." + rawKey;
            if (levels == null) {
                issues.add(error(file, entryPath, "expected a section of level-<n>: weight entries"));
                continue;
            }
            Optional<String> canonical = canonicalKey(rawKey, file, entryPath, issues);
            if (canonical.isEmpty()) {
                continue;
            }
            Map<Integer, Integer> weights = readLevelWeights(levels, file, entryPath, issues);
            if (!weights.isEmpty()) {
                overrides.put(canonical.get(), weights);
            }
        }
        return overrides;
    }

    /**
     * Reads the default source rules.
     *
     * @param section default section
     * @param file    file the section came from
     * @param path    YAML path of the section
     * @param issues  issue collector
     * @return source to allowed map
     */
    public static Map<AcquisitionSource, Boolean> readSourceDefaults(
            org.bukkit.configuration.ConfigurationSection section,
            String file,
            String path,
            List<ConfigIssue> issues) {
        Map<AcquisitionSource, Boolean> defaults = new EnumMap<>(AcquisitionSource.class);
        for (AcquisitionSource source : AcquisitionSource.values()) {
            defaults.put(source, true);
        }
        if (section == null) {
            return defaults;
        }
        for (String key : section.getKeys(false)) {
            Optional<AcquisitionSource> source = AcquisitionSource.parse(key);
            if (source.isEmpty()) {
                issues.add(error(file, path + "." + key, "unknown source; expected villager, enchanting-table, loot or fishing"));
                continue;
            }
            defaults.put(source.get(), section.getBoolean(key, true));
        }
        return defaults;
    }

    /**
     * Creates an error issue.
     *
     * @param file    file name
     * @param path    YAML path
     * @param message explanation
     * @return the issue
     */
    public static ConfigIssue error(String file, String path, String message) {
        return new ConfigIssue(ConfigIssue.Severity.ERROR, file, path, message);
    }

    /**
     * Creates a warning issue.
     *
     * @param file    file name
     * @param path    YAML path
     * @param message explanation
     * @return the issue
     */
    public static ConfigIssue warning(String file, String path, String message) {
        return new ConfigIssue(ConfigIssue.Severity.WARNING, file, path, message);
    }
}
