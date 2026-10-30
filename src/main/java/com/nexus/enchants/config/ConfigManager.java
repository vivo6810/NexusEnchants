package com.nexus.enchants.config;

import com.nexus.enchants.economy.DiscountPolicy;
import com.nexus.enchants.sources.AcquisitionSource;
import com.nexus.enchants.util.EnchantmentResolver;
import com.nexus.enchants.util.EnchantmentWeight;
import com.nexus.enchants.util.PriceMath;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads, validates and writes every NexusEnchants configuration file.
 *
 * <p>YAML remains the single source of truth for prices, weights and policies;
 * nothing here reads or writes SQL. Files are loaded into an immutable
 * {@link ConfigSnapshot} so services never observe a partially applied reload.
 * A file that cannot be parsed produces an ERROR issue and falls back to the
 * bundled defaults for that file only.</p>
 */
public final class ConfigManager {

    /** General settings file. */
    public static final String CONFIG_FILE = "config.yml";
    /** Pricing file. */
    public static final String PRICING_FILE = "pricing.yml";
    /** Librarian rarity file. */
    public static final String VILLAGER_RARITY_FILE = "villager-rarity.yml";
    /** Enchanting table rarity file. */
    public static final String ENCHANTING_RARITY_FILE = "enchanting-rarity.yml";
    /** Level probability file. */
    public static final String LEVELS_FILE = "enchantment-levels.yml";
    /** Acquisition source file. */
    public static final String SOURCES_FILE = "enchantment-sources.yml";
    /** Discount policy file. */
    public static final String DISCOUNTS_FILE = "discounts.yml";

    private static final List<String> ALL_FILES = List.of(
            CONFIG_FILE,
            PRICING_FILE,
            VILLAGER_RARITY_FILE,
            ENCHANTING_RARITY_FILE,
            LEVELS_FILE,
            SOURCES_FILE,
            DISCOUNTS_FILE);

    private final JavaPlugin plugin;
    private final Logger logger;
    private final ConfigValidator validator;
    private ConfigSnapshot snapshot;

    /**
     * Creates the manager.
     *
     * @param plugin owning plugin, used for the data folder and bundled resources
     * @param logger logger for load reporting
     */
    public ConfigManager(JavaPlugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
        this.validator = new ConfigValidator();
    }

    /**
     * Returns the current snapshot, loading it on first use.
     *
     * @return the active snapshot
     */
    public ConfigSnapshot current() {
        if (snapshot == null) {
            return reload();
        }
        return snapshot;
    }

    /**
     * Loads every configuration file and replaces the active snapshot.
     *
     * <p>This method performs blocking file I/O and must be called from the
     * server thread during startup or from a command that already documented
     * the cost.</p>
     *
     * @return the new snapshot
     */
    public ConfigSnapshot reload() {
        List<ConfigIssue> issues = new ArrayList<>();
        Map<String, YamlConfiguration> files = new LinkedHashMap<>();
        for (String name : ALL_FILES) {
            files.put(name, loadOrCreate(name, issues));
        }

        PluginSettings settings = parseSettings(files.get(CONFIG_FILE), issues);
        PricingConfig pricing = parsePricing(files.get(PRICING_FILE), issues);
        VillagerRarityConfig villagerRarity = parseVillagerRarity(files.get(VILLAGER_RARITY_FILE), issues);
        EnchantingConfig enchanting = parseEnchanting(files.get(ENCHANTING_RARITY_FILE), issues);
        LevelProbabilityConfig levels = parseLevels(files.get(LEVELS_FILE), issues);
        SourceConfig sources = parseSources(files.get(SOURCES_FILE), issues);
        DiscountConfig discounts = parseDiscounts(files.get(DISCOUNTS_FILE), issues);
        DatabaseConfig database = parseDatabase(files.get(CONFIG_FILE), issues);

        ConfigSnapshot candidate = new ConfigSnapshot(
                settings,
                pricing,
                villagerRarity,
                enchanting,
                levels,
                sources,
                discounts,
                database,
                issues,
                System.currentTimeMillis());

        List<ConfigIssue> crossFile = validator.validateSnapshot(candidate);
        if (!crossFile.isEmpty()) {
            List<ConfigIssue> merged = new ArrayList<>(candidate.issues());
            merged.addAll(crossFile);
            candidate = new ConfigSnapshot(
                    settings, pricing, villagerRarity, enchanting, levels, sources, discounts, database,
                    merged, candidate.loadedAtMillis());
        }

        report(candidate);
        this.snapshot = candidate;
        return candidate;
    }

    private void report(ConfigSnapshot candidate) {
        for (ConfigIssue issue : candidate.issues()) {
            if (issue.severity() == ConfigIssue.Severity.ERROR) {
                logger.severe(issue.format());
            } else {
                logger.warning(issue.format());
            }
        }
        if (candidate.issues().isEmpty()) {
            logger.info("Configuration loaded without findings.");
        } else {
            logger.info("Configuration loaded with " + candidate.errors().size() + " error(s) and "
                    + candidate.warnings().size() + " warning(s).");
        }
    }

    private YamlConfiguration loadOrCreate(String name, List<ConfigIssue> issues) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            try {
                plugin.saveResource(name, false);
            } catch (IllegalArgumentException exception) {
                issues.add(ConfigValidator.error(name, "<file>", "bundled default is missing: "
                        + exception.getMessage()));
                return new YamlConfiguration();
            }
        }
        try {
            return readYaml(file);
        } catch (IOException | InvalidConfigurationException exception) {
            issues.add(ConfigValidator.error(name, "<file>", "could not be parsed (" + exception.getMessage()
                    + "); the bundled defaults are used for this file until it is repaired"));
            logger.log(Level.SEVERE, "Failed to read " + name, exception);
            return bundledDefaults(name, issues);
        }
    }

    /**
     * Parses a YAML file and reports malformed content to the caller.
     *
     * <p>{@code YamlConfiguration.loadConfiguration(File)} swallows parse
     * failures and returns an empty configuration, which would hide a broken
     * file behind default gameplay values. Reading the file and parsing it with
     * {@code loadFromString} keeps the failure visible.</p>
     *
     * @param file file to read
     * @return the parsed configuration
     * @throws IOException                   when the file cannot be read
     * @throws InvalidConfigurationException when the content is not valid YAML
     */
    private YamlConfiguration readYaml(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        return configuration;
    }

    private YamlConfiguration bundledDefaults(String name, List<ConfigIssue> issues) {
        try (InputStream stream = plugin.getResource(name)) {
            if (stream == null) {
                issues.add(ConfigValidator.error(name, "<file>", "no bundled default available"));
                return new YamlConfiguration();
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (IOException exception) {
            issues.add(ConfigValidator.error(name, "<file>", "bundled default unreadable: " + exception.getMessage()));
            return new YamlConfiguration();
        }
    }

    private PluginSettings parseSettings(YamlConfiguration file, List<ConfigIssue> issues) {
        PluginSettings defaults = PluginSettings.defaults();
        try {
            return new PluginSettings(
                    file.getBoolean("settings.enabled", defaults.enabled()),
                    file.getBoolean("settings.verbose-startup", defaults.verboseStartup()),
                    file.getInt("settings.librarian-book-trades", defaults.librarianBookTrades()),
                    file.getBoolean("settings.statistics.enabled", defaults.statisticsEnabled()),
                    file.getInt("settings.statistics.flush-interval-seconds", defaults.statisticsFlushSeconds()),
                    file.getBoolean("settings.audit.enabled", defaults.auditEnabled()),
                    file.getInt("settings.async.worker-threads", defaults.asyncWorkers()),
                    file.getInt("settings.async.queue-capacity", defaults.asyncQueueCapacity()));
        } catch (IllegalArgumentException exception) {
            issues.add(ConfigValidator.error(CONFIG_FILE, "settings", exception.getMessage()
                    + "; the documented defaults are used"));
            return defaults;
        }
    }

    private PricingConfig parsePricing(YamlConfiguration file, List<ConfigIssue> issues) {
        Map<String, Map<Integer, Integer>> prices = new LinkedHashMap<>();
        ConfigurationSection root = file.getConfigurationSection("pricing");
        if (root == null) {
            issues.add(ConfigValidator.error(PRICING_FILE, "pricing", "expected a section of enchantment prices"));
        } else {
            for (String rawKey : root.getKeys(false)) {
                ConfigurationSection levels = root.getConfigurationSection(rawKey);
                String path = "pricing." + rawKey;
                if (levels == null) {
                    issues.add(ConfigValidator.error(PRICING_FILE, path,
                            "expected a section of level: price entries"));
                    continue;
                }
                Optional<String> canonical = ConfigValidator.canonicalKey(rawKey, PRICING_FILE, path, issues);
                if (canonical.isEmpty()) {
                    continue;
                }
                Map<Integer, Integer> levelPrices = new TreeMap<>();
                for (String levelKey : levels.getKeys(false)) {
                    int level;
                    try {
                        level = Integer.parseInt(levelKey);
                    } catch (NumberFormatException exception) {
                        issues.add(ConfigValidator.error(PRICING_FILE, path + "." + levelKey,
                                "level must be a number"));
                        continue;
                    }
                    if (level < 1) {
                        issues.add(ConfigValidator.error(PRICING_FILE, path + "." + levelKey,
                                "level must be at least 1"));
                        continue;
                    }
                    int price = levels.getInt(levelKey, -1);
                    if (price < 0) {
                        issues.add(ConfigValidator.error(PRICING_FILE, path + "." + levelKey,
                                "price must not be negative, got " + price));
                        continue;
                    }
                    if (price == 0 && !file.getBoolean("limits.allow-zero", false)) {
                        issues.add(ConfigValidator.error(PRICING_FILE, path + "." + levelKey,
                                "price 0 is only allowed with limits.allow-zero: true"));
                        continue;
                    }
                    if (price > PriceMath.MAX_REPRESENTABLE_PRICE) {
                        issues.add(ConfigValidator.error(PRICING_FILE, path + "." + levelKey,
                                "price " + price + " cannot be paid in one trade (maximum "
                                        + PriceMath.MAX_REPRESENTABLE_PRICE + ")"));
                        continue;
                    }
                    levelPrices.put(level, price);
                }
                if (!levelPrices.isEmpty()) {
                    prices.put(canonical.get(), levelPrices);
                }
            }
        }
        int minimum = file.getInt("limits.minimum", 1);
        int maximum = file.getInt("limits.maximum", PriceMath.MAX_REPRESENTABLE_PRICE);
        if (maximum > PriceMath.MAX_REPRESENTABLE_PRICE) {
            // The trade cannot represent the value; clamp so the engine never builds an invalid recipe.
            maximum = PriceMath.MAX_REPRESENTABLE_PRICE;
        }
        if (minimum < 0) {
            issues.add(ConfigValidator.error(PRICING_FILE, "limits.minimum", "minimum must not be negative"));
            minimum = 0;
        }
        if (minimum > maximum) {
            issues.add(ConfigValidator.error(PRICING_FILE, "limits.minimum",
                    "minimum " + minimum + " is greater than maximum " + maximum + "; using 1"));
            minimum = Math.min(1, maximum);
        }
        boolean fallbackEnabled = file.getBoolean("fallback.enabled", true);
        int fallbackPrice = file.getInt("fallback.price", 40);
        if (fallbackPrice < 0) {
            issues.add(ConfigValidator.error(PRICING_FILE, "fallback.price", "fallback price must not be negative"));
            fallbackPrice = 0;
        }
        return new PricingConfig(prices, fallbackEnabled, PriceMath.clamp(fallbackPrice, 0, maximum),
                minimum, maximum, file.getBoolean("limits.allow-zero", false));
    }

    private VillagerRarityConfig parseVillagerRarity(YamlConfiguration file, List<ConfigIssue> issues) {
        SelectionMode mode = SelectionMode.parse(file.getString("villager-rarity.selection-mode", "WEIGHTED"))
                .orElseGet(() -> {
                    issues.add(ConfigValidator.error(VILLAGER_RARITY_FILE, "villager-rarity.selection-mode",
                            "expected WEIGHTED or DISABLED; using WEIGHTED"));
                    return SelectionMode.WEIGHTED;
                });
        Map<String, EnchantmentWeight> weights = ConfigValidator.readWeights(
                file.getConfigurationSection("villager-rarity.enchantments"),
                VILLAGER_RARITY_FILE, "villager-rarity.enchantments", issues);
        Map<String, Map<Integer, Integer>> levelWeights = ConfigValidator.readLevelOverrides(
                file.getConfigurationSection("villager-rarity.level-weights"),
                VILLAGER_RARITY_FILE, "villager-rarity.level-weights", issues);
        Set<String> disabled = ConfigValidator.readKeys(
                file.getStringList("villager-rarity.disabled-enchantments"),
                VILLAGER_RARITY_FILE, "villager-rarity.disabled-enchantments", issues);
        return new VillagerRarityConfig(
                file.getBoolean("villager-rarity.enabled", true),
                mode,
                weights,
                levelWeights,
                file.getBoolean("villager-rarity.treasure-enchantments.enabled", true),
                disabled);
    }

    private EnchantingConfig parseEnchanting(YamlConfiguration file, List<ConfigIssue> issues) {
        SelectionMode mode = SelectionMode.parse(file.getString("enchanting-table.rarity-mode", "WEIGHTED"))
                .orElseGet(() -> {
                    issues.add(ConfigValidator.error(ENCHANTING_RARITY_FILE, "enchanting-table.rarity-mode",
                            "expected WEIGHTED or DISABLED; using WEIGHTED"));
                    return SelectionMode.WEIGHTED;
                });
        Map<String, EnchantmentWeight> weights = ConfigValidator.readWeights(
                file.getConfigurationSection("enchanting-table.enchantment-weights"),
                ENCHANTING_RARITY_FILE, "enchanting-table.enchantment-weights", issues);
        Set<String> excluded = ConfigValidator.readKeys(
                file.getStringList("enchanting-table.excluded-enchantments"),
                ENCHANTING_RARITY_FILE, "enchanting-table.excluded-enchantments", issues);
        int chance = ConfigValidator.percentage(
                file.getInt("enchanting-table.multiple-enchantments.chance", 15),
                ENCHANTING_RARITY_FILE, "enchanting-table.multiple-enchantments.chance", issues);
        int maximum = Math.max(1, file.getInt("enchanting-table.multiple-enchantments.maximum", 3));
        int minimumLevel = Math.max(1, file.getInt("enchanting-table.multiple-enchantments.minimum-level", 1));
        int maximumLevel = Math.max(minimumLevel,
                file.getInt("enchanting-table.multiple-enchantments.maximum-level", 3));
        return new EnchantingConfig(
                file.getBoolean("enchanting-table.enabled", true),
                mode,
                file.getBoolean("enchanting-table.treasure-enchantments.enabled", false),
                file.getBoolean("enchanting-table.multiple-enchantments.enabled", true),
                chance,
                maximum,
                minimumLevel,
                maximumLevel,
                excluded,
                weights);
    }

    private LevelProbabilityConfig parseLevels(YamlConfiguration file, List<ConfigIssue> issues) {
        Map<Integer, Integer> defaults = ConfigValidator.readLevelWeights(
                file.getConfigurationSection("enchantment-levels.default"),
                LEVELS_FILE, "enchantment-levels.default", issues);
        Map<String, Map<Integer, Integer>> overrides = ConfigValidator.readLevelOverrides(
                file.getConfigurationSection("enchantment-levels.overrides"),
                LEVELS_FILE, "enchantment-levels.overrides", issues);
        return new LevelProbabilityConfig(
                file.getBoolean("enchantment-levels.enabled", true),
                defaults,
                overrides);
    }

    private SourceConfig parseSources(YamlConfiguration file, List<ConfigIssue> issues) {
        Map<AcquisitionSource, Boolean> defaults = ConfigValidator.readSourceDefaults(
                file.getConfigurationSection("enchantment-sources.default"),
                SOURCES_FILE, "enchantment-sources.default", issues);
        Map<String, Map<AcquisitionSource, Boolean>> rules = new LinkedHashMap<>();
        ConfigurationSection root = file.getConfigurationSection("enchantment-sources");
        if (root != null) {
            for (String enchantmentKey : root.getKeys(false)) {
                if ("enabled".equals(enchantmentKey) || "default".equals(enchantmentKey)) {
                    continue;
                }
                ConfigurationSection entry = root.getConfigurationSection(enchantmentKey);
                String path = "enchantment-sources." + enchantmentKey;
                if (entry == null) {
                    issues.add(ConfigValidator.error(SOURCES_FILE, path,
                            "expected a section of source: true/false entries"));
                    continue;
                }
                Optional<String> canonical = ConfigValidator.canonicalKey(enchantmentKey, SOURCES_FILE, path, issues);
                if (canonical.isEmpty()) {
                    continue;
                }
                Map<AcquisitionSource, Boolean> rulesForEnchantment = new EnumMap<>(AcquisitionSource.class);
                for (String sourceKey : entry.getKeys(false)) {
                    Optional<AcquisitionSource> source = AcquisitionSource.parse(sourceKey);
                    if (source.isEmpty()) {
                        issues.add(ConfigValidator.error(SOURCES_FILE, path + "." + sourceKey,
                                "unknown source; expected villager, enchanting-table, loot or fishing"));
                        continue;
                    }
                    rulesForEnchantment.put(source.get(), entry.getBoolean(sourceKey, true));
                }
                if (!rulesForEnchantment.isEmpty()) {
                    rules.put(canonical.get(), rulesForEnchantment);
                }
            }
        }
        return new SourceConfig(file.getBoolean("enchantment-sources.enabled", true), defaults, rules);
    }

    private DiscountConfig parseDiscounts(YamlConfiguration file, List<ConfigIssue> issues) {
        DiscountPolicy policy = DiscountPolicy.parse(file.getString("discounts.policy", "CAP"))
                .orElseGet(() -> {
                    issues.add(ConfigValidator.error(DISCOUNTS_FILE, "discounts.policy",
                            "expected PRESERVE, DISABLE or CAP; using CAP"));
                    return DiscountPolicy.CAP;
                });
        int maximum = ConfigValidator.percentage(
                file.getInt("discounts.maximum-discount-percentage", 20),
                DISCOUNTS_FILE, "discounts.maximum-discount-percentage", issues);
        int heroPerLevel = ConfigValidator.percentage(
                file.getInt("discounts.hero-of-the-village-percentage-per-level", 5),
                DISCOUNTS_FILE, "discounts.hero-of-the-village-percentage-per-level", issues);
        try {
            return new DiscountConfig(
                    policy,
                    maximum,
                    file.getBoolean("discounts.hero-of-the-village", true),
                    heroPerLevel,
                    file.getBoolean("discounts.curing-discounts", true),
                    file.getBoolean("discounts.reputation-discounts", true));
        } catch (IllegalArgumentException exception) {
            issues.add(ConfigValidator.error(DISCOUNTS_FILE, "discounts", exception.getMessage()
                    + "; the documented defaults are used"));
            return DiscountConfig.defaults();
        }
    }

    private DatabaseConfig parseDatabase(YamlConfiguration file, List<ConfigIssue> issues) {
        DatabaseType type = DatabaseType.parse(file.getString("database.type", "SQLITE"))
                .orElseGet(() -> {
                    issues.add(ConfigValidator.error(CONFIG_FILE, "database.type",
                            "expected SQLITE or MYSQL; using SQLITE"));
                    return DatabaseType.SQLITE;
                });
        int poolSize = file.getInt("database.connection-pool.maximum-size", 5);
        if (poolSize < 1) {
            issues.add(ConfigValidator.error(CONFIG_FILE, "database.connection-pool.maximum-size",
                    "must be at least 1; using 5"));
            poolSize = 5;
        }
        String journalMode = file.getString("database.sqlite.options.journal-mode", "WAL");
        DatabaseConfig.MysqlSettings mysql = null;
        if (type == DatabaseType.MYSQL) {
            try {
                mysql = new DatabaseConfig.MysqlSettings(
                        file.getString("database.mysql.host", "localhost"),
                        file.getInt("database.mysql.port", 3306),
                        file.getString("database.mysql.database", "nexusenchants"),
                        file.getString("database.mysql.username", "nexus_user"),
                        file.getString("database.mysql.password", "CHANGE_ME"),
                        file.getBoolean("database.mysql.ssl", false),
                        file.getInt("database.mysql.pool.maximum-size", 10),
                        file.getInt("database.mysql.pool.minimum-idle", 2),
                        file.getInt("database.mysql.pool.connection-timeout", 10_000));
            } catch (IllegalArgumentException exception) {
                issues.add(ConfigValidator.error(CONFIG_FILE, "database.mysql", exception.getMessage()
                        + "; falling back to SQLITE"));
                type = DatabaseType.SQLITE;
            }
        }
        try {
            return new DatabaseConfig(
                    type,
                    file.getString("database.sqlite.file", "nexusenchants.db"),
                    file.getBoolean("database.sqlite.options.foreign-keys", true),
                    file.getInt("database.sqlite.options.busy-timeout", 5000),
                    journalMode,
                    poolSize,
                    mysql);
        } catch (IllegalArgumentException exception) {
            issues.add(ConfigValidator.error(CONFIG_FILE, "database", exception.getMessage()
                    + "; the documented defaults are used"));
            return DatabaseConfig.defaults();
        }
    }

    /**
     * Writes a single price to pricing.yml and reloads the snapshot.
     *
     * <p>Blocking file I/O; call from the server thread only.</p>
     *
     * @param enchantmentKey canonical enchantment key
     * @param level          enchantment level
     * @param price          new price in emeralds
     * @return the reloaded snapshot
     * @throws IOException when the file cannot be written
     */
    public ConfigSnapshot writePrice(String enchantmentKey, int level, int price) throws IOException {
        YamlConfiguration file = loadForWrite(PRICING_FILE);
        String sectionName = findSectionName(file.getConfigurationSection("pricing"), enchantmentKey)
                .orElseGet(() -> enchantmentKey.substring(enchantmentKey.indexOf(':') + 1)
                        .toUpperCase(java.util.Locale.ROOT));
        file.set("pricing." + sectionName + "." + level, price);
        file.save(new File(plugin.getDataFolder(), PRICING_FILE));
        return reload();
    }

    /**
     * Writes a villager rarity weight and reloads the snapshot.
     *
     * @param enchantmentKey canonical enchantment key
     * @param weight         new weight
     * @param enabled        whether the entry is enabled
     * @return the reloaded snapshot
     * @throws IOException when the file cannot be written
     */
    public ConfigSnapshot writeVillagerWeight(String enchantmentKey, int weight, boolean enabled) throws IOException {
        YamlConfiguration file = loadForWrite(VILLAGER_RARITY_FILE);
        ConfigurationSection root = file.getConfigurationSection("villager-rarity.enchantments");
        String sectionName = findSectionName(root, enchantmentKey)
                .orElseGet(() -> enchantmentKey.substring(enchantmentKey.indexOf(':') + 1)
                        .toUpperCase(java.util.Locale.ROOT));
        String path = "villager-rarity.enchantments." + sectionName;
        file.set(path + ".enabled", enabled);
        file.set(path + ".weight", weight);
        file.save(new File(plugin.getDataFolder(), VILLAGER_RARITY_FILE));
        return reload();
    }

    /**
     * Writes an enchanting weight and reloads the snapshot.
     *
     * @param enchantmentKey canonical enchantment key
     * @param weight         new weight
     * @param enabled        whether the entry is enabled
     * @return the reloaded snapshot
     * @throws IOException when the file cannot be written
     */
    public ConfigSnapshot writeEnchantingWeight(String enchantmentKey, int weight, boolean enabled)
            throws IOException {
        YamlConfiguration file = loadForWrite(ENCHANTING_RARITY_FILE);
        ConfigurationSection root = file.getConfigurationSection("enchanting-table.enchantment-weights");
        String sectionName = findSectionName(root, enchantmentKey)
                .orElseGet(() -> enchantmentKey.substring(enchantmentKey.indexOf(':') + 1)
                        .toUpperCase(java.util.Locale.ROOT));
        String path = "enchanting-table.enchantment-weights." + sectionName;
        file.set(path + ".enabled", enabled);
        file.set(path + ".weight", weight);
        file.save(new File(plugin.getDataFolder(), ENCHANTING_RARITY_FILE));
        return reload();
    }

    private YamlConfiguration loadForWrite(String name) throws IOException {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        try {
            return readYaml(file);
        } catch (InvalidConfigurationException exception) {
            throw new IOException("Cannot update " + name + " because it is not valid YAML", exception);
        }
    }

    private Optional<String> findSectionName(ConfigurationSection section, String canonicalKey) {
        if (section == null) {
            return Optional.empty();
        }
        for (String key : section.getKeys(false)) {
            Optional<org.bukkit.enchantments.Enchantment> resolved = EnchantmentResolver.resolve(key);
            if (resolved.isPresent() && EnchantmentResolver.keyOf(resolved.get()).equals(canonicalKey)) {
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the configuration folder.
     *
     * @return the plugin data folder
     */
    public File dataFolder() {
        return plugin.getDataFolder();
    }

    /**
     * Returns the files this manager owns.
     *
     * @return immutable list of file names
     */
    public List<String> managedFiles() {
        return ALL_FILES;
    }

    /**
     * Returns all acquireable source keys, used by tab completion.
     *
     * @return the source keys
     */
    public List<String> sourceKeys() {
        List<String> keys = new ArrayList<>();
        for (AcquisitionSource source : AcquisitionSource.values()) {
            keys.add(source.configKey());
        }
        return List.copyOf(keys);
    }

    /**
     * Returns every configured enchantment key across all weight tables.
     *
     * @return sorted canonical keys
     */
    public Set<String> configuredEnchantmentKeys() {
        ConfigSnapshot currentSnapshot = current();
        Set<String> keys = new LinkedHashSet<>(currentSnapshot.pricing().prices().keySet());
        currentSnapshot.villagerRarity().weights().forEach((key, weight) -> keys.add(key));
        currentSnapshot.enchanting().weights().forEach((key, weight) -> keys.add(key));
        return keys;
    }
}
