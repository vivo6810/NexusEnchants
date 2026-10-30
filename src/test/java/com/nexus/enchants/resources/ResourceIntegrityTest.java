package com.nexus.enchants.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Validates the shipped resources without starting a server.
 *
 * <p>These checks are what make "configuration validation fails the build" true
 * in CI: a YAML syntax error, a missing migration or a broken plugin descriptor
 * fails a test instead of only failing at runtime.</p>
 */
class ResourceIntegrityTest {

    private static final List<String> CONFIG_FILES = List.of(
            "config.yml",
            "pricing.yml",
            "villager-rarity.yml",
            "enchanting-rarity.yml",
            "enchantment-levels.yml",
            "enchantment-sources.yml",
            "discounts.yml");

    private static final Set<String> PERMISSIONS = Set.of(
            "nexusenchants.admin",
            "nexusenchants.reload",
            "nexusenchants.prices",
            "nexusenchants.rarity",
            "nexusenchants.diagnostics",
            "nexusenchants.database");

    private static InputStream resource(String name) {
        InputStream stream = ResourceIntegrityTest.class.getClassLoader().getResourceAsStream(name);
        assertNotNull(stream, "missing resource " + name);
        return stream;
    }

    private static YamlConfiguration yaml(String name) throws IOException {
        try (InputStream stream = resource(name);
                InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    private static String text(String name) throws IOException {
        try (InputStream stream = resource(name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("plugin.yml describes a loadable plugin")
    void pluginDescriptor() throws IOException {
        YamlConfiguration yml = yaml("plugin.yml");
        assertEquals("NexusEnchants", yml.getString("name"));
        assertEquals("com.nexus.enchants.NexusEnchants", yml.getString("main"));
        assertEquals("26.3", yml.getString("api-version"));
        assertTrue(yml.isConfigurationSection("commands"), "the vbe command must be declared");
        assertTrue(yml.isConfigurationSection("commands.vbe"));
        assertEquals("/vbe help", yml.getString("commands.vbe.usage"));
        assertEquals(List.of("nexusenchants"), yml.getStringList("commands.vbe.aliases"));

        String version = yml.getString("version");
        assertNotNull(version);
        assertFalse(version.contains("${"), "processResources must expand the version placeholder: " + version);
    }

    @Test
    @DisplayName("plugin.yml declares every documented permission")
    @SuppressWarnings("unchecked")
    void permissions() throws IOException {
        // The raw YAML is read instead of YamlConfiguration: Bukkit treats '.' as its
        // path separator, so dotted permission names would collapse into a nested tree
        // and the literal keys the server actually registers would be invisible.
        Map<String, Object> descriptor = new Yaml().load(text("plugin.yml"));
        Map<String, Object> permissions = (Map<String, Object>) descriptor.get("permissions");
        assertNotNull(permissions, "plugin.yml must declare permissions");
        assertEquals(PERMISSIONS, permissions.keySet(), "every documented permission is declared");
        for (String permission : PERMISSIONS) {
            Map<String, Object> node = (Map<String, Object>) permissions.get(permission);
            assertNotNull(node, permission);
            assertEquals("op", node.get("default"), permission);
        }

        Map<String, Object> admin = (Map<String, Object>) permissions.get("nexusenchants.admin");
        Map<String, Object> children = (Map<String, Object>) admin.get("children");
        assertNotNull(children, "nexusenchants.admin must inherit the other permissions");
        assertEquals(Set.of("nexusenchants.reload", "nexusenchants.prices", "nexusenchants.rarity",
                        "nexusenchants.diagnostics", "nexusenchants.database"),
                children.keySet());
        children.forEach((child, granted) ->
                assertEquals(Boolean.TRUE, granted, child + " must be granted by nexusenchants.admin"));
    }

    @Test
    @DisplayName("Every configuration file parses and is not empty")
    void configurationFilesParse() throws IOException {
        for (String file : CONFIG_FILES) {
            YamlConfiguration yml = yaml(file);
            assertFalse(yml.getKeys(false).isEmpty(), file + " must not be empty");
        }
    }

    @Test
    @DisplayName("The documented default prices are shipped")
    void defaultPrices() throws IOException {
        ConfigurationSection pricing = yaml("pricing.yml").getConfigurationSection("pricing");
        assertNotNull(pricing);
        assertEquals(70, pricing.getInt("MENDING.1"));
        assertEquals(45, pricing.getInt("UNBREAKING.3"));
        assertEquals(55, pricing.getInt("EFFICIENCY.5"));
        assertEquals(50, pricing.getInt("SHARPNESS.5"));
        assertEquals(45, pricing.getInt("PROTECTION.4"));
        assertEquals(50, pricing.getInt("FORTUNE.3"));
        assertEquals(40, pricing.getInt("SILK_TOUCH.1"));
        assertEquals(45, pricing.getInt("LOOTING.3"));
        assertEquals(35, pricing.getInt("FEATHER_FALLING.4"));
        assertEquals(40, pricing.getInt("THORNS.3"));

        YamlConfiguration file = yaml("pricing.yml");
        assertTrue(file.getBoolean("fallback.enabled"), "the shipped default enables the fallback");
        assertEquals(40, file.getInt("fallback.price"));
        assertEquals(1, file.getInt("limits.minimum"));
        assertEquals(128, file.getInt("limits.maximum"));
        assertFalse(file.getBoolean("limits.allow-zero"));
    }

    @Test
    @DisplayName("The documented villager rarity defaults are shipped")
    void villagerRarityDefaults() throws IOException {
        ConfigurationSection section = yaml("villager-rarity.yml")
                .getConfigurationSection("villager-rarity.enchantments");
        assertNotNull(section);
        assertEquals(2, section.getInt("MENDING.weight"));
        assertEquals(12, section.getInt("UNBREAKING.weight"));
        assertEquals(10, section.getInt("EFFICIENCY.weight"));
        assertEquals(5, section.getInt("FORTUNE.weight"));
        assertEquals(4, section.getInt("SILK_TOUCH.weight"));
        assertEquals(12, section.getInt("SHARPNESS.weight"));
        assertEquals(10, section.getInt("PROTECTION.weight"));
        assertEquals(5, section.getInt("LOOTING.weight"));
        assertEquals(7, section.getInt("FEATHER_FALLING.weight"));
        assertEquals(3, section.getInt("THORNS.weight"));
    }

    @Test
    @DisplayName("Mending is disabled for the enchanting table by default")
    void enchantingDefaults() throws IOException {
        YamlConfiguration yml = yaml("enchanting-rarity.yml");
        ConfigurationSection table = yml.getConfigurationSection("enchanting-table");
        assertNotNull(table);
        assertTrue(table.getBoolean("enabled"));
        assertFalse(table.getBoolean("treasure-enchantments.enabled"));
        assertTrue(table.getBoolean("multiple-enchantments.enabled"));
        assertEquals(15, table.getInt("multiple-enchantments.chance"));
        ConfigurationSection weights = table.getConfigurationSection("enchantment-weights");
        assertNotNull(weights);
        assertFalse(weights.getBoolean("MENDING.enabled"));
        assertEquals(0, weights.getInt("MENDING.weight"));
        assertEquals(35, weights.getInt("UNBREAKING.weight"));
        assertEquals(40, weights.getInt("EFFICIENCY.weight"));
        assertEquals(8, weights.getInt("FORTUNE.weight"));
        assertEquals(5, weights.getInt("SILK_TOUCH.weight"));
    }

    @Test
    @DisplayName("Level weights and discount defaults match the specification")
    void levelAndDiscountDefaults() throws IOException {
        YamlConfiguration levels = yaml("enchantment-levels.yml");
        ConfigurationSection defaults = levels.getConfigurationSection("enchantment-levels.default");
        assertNotNull(defaults);
        assertEquals(45, defaults.getInt("level-1"));
        assertEquals(30, defaults.getInt("level-2"));
        assertEquals(15, defaults.getInt("level-3"));
        assertEquals(7, defaults.getInt("level-4"));
        assertEquals(3, defaults.getInt("level-5"));
        assertEquals(100, levels.getInt("enchantment-levels.overrides.MENDING.level-1"));

        YamlConfiguration discounts = yaml("discounts.yml");
        assertEquals("CAP", discounts.getString("discounts.policy"));
        assertEquals(20, discounts.getInt("discounts.maximum-discount-percentage"));
        assertTrue(discounts.getBoolean("discounts.curing-discounts"));
        assertTrue(discounts.getBoolean("discounts.reputation-discounts"));
        assertTrue(discounts.getBoolean("discounts.hero-of-the-village"));
    }

    @Test
    @DisplayName("Mending is blocked from the enchanting table and allowed elsewhere")
    void sourceDefaults() throws IOException {
        ConfigurationSection sources = yaml("enchantment-sources.yml")
                .getConfigurationSection("enchantment-sources");
        assertNotNull(sources);
        assertTrue(sources.getBoolean("MENDING.villager"));
        assertFalse(sources.getBoolean("MENDING.enchanting-table"));
        assertTrue(sources.getBoolean("MENDING.loot"));
        assertTrue(sources.getBoolean("MENDING.fishing"));
        assertTrue(sources.getBoolean("SILK_TOUCH.enchanting-table"));
    }

    @Test
    @DisplayName("SQLite is the shipped default database")
    void databaseDefaults() throws IOException {
        ConfigurationSection database = yaml("config.yml").getConfigurationSection("database");
        assertNotNull(database);
        assertEquals("SQLITE", database.getString("type"));
        assertEquals("nexusenchants.db", database.getString("sqlite.file"));
        assertTrue(database.getBoolean("sqlite.options.foreign-keys"));
        assertEquals(5000, database.getInt("sqlite.options.busy-timeout"));
        assertEquals("WAL", database.getString("sqlite.options.journal-mode"));
    }

    @Test
    @DisplayName("Both migration dialects ship V001-V004 with matching versions")
    void migrations() throws IOException {
        List<String> sqlite = migrationFiles("db/migration/sqlite");
        List<String> mysql = migrationFiles("db/migration/mysql");
        assertEquals(List.of("V001__initial_schema.sql", "V002__villager_trade_tracking.sql",
                "V003__audit_logs.sql", "V004__rarity_statistics.sql"), sqlite);
        assertEquals(sqlite, mysql);
    }

    @Test
    @DisplayName("The MySQL migrations use MySQL syntax, not SQLite syntax")
    void mysqlDialect() throws IOException {
        for (String file : migrationFiles("db/migration/mysql")) {
            String script = read("db/migration/mysql/" + file);
            assertFalse(script.contains("AUTOINCREMENT"),
                    file + " must not use SQLite-only AUTOINCREMENT");
            assertTrue(script.contains("InnoDB"), file + " should declare an InnoDB engine");
            assertTrue(script.contains("utf8mb4"), file + " should declare a utf8mb4 charset");
        }
    }

    private static List<String> migrationFiles(String folder) throws IOException {
        // The test runtime sees resources as a directory, so list it through the classpath.
        java.net.URL url = ResourceIntegrityTest.class.getClassLoader().getResource(folder);
        assertNotNull(url, "missing migration folder " + folder);
        List<String> names = new ArrayList<>();
        try {
            java.nio.file.Path directory = java.nio.file.Paths.get(url.toURI());
            try (var stream = java.nio.file.Files.list(directory)) {
                stream.map(path -> path.getFileName().toString())
                        .filter(name -> name.endsWith(".sql"))
                        .sorted()
                        .forEach(names::add);
            }
        } catch (java.net.URISyntaxException exception) {
            throw new IOException(exception);
        }
        return names;
    }

    private static String read(String resource) throws IOException {
        try (InputStream stream = resource(resource);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        }
    }

    @Test
    @DisplayName("Migration files contain only forward-only DDL")
    void migrationsAreForwardOnly() throws IOException {
        Pattern destructive = Pattern.compile("(?i)\\b(DROP\\s+TABLE|TRUNCATE|DELETE\\s+FROM)\\b");
        for (String dialect : List.of("sqlite", "mysql")) {
            for (String file : migrationFiles("db/migration/" + dialect)) {
                Matcher matcher = destructive.matcher(read("db/migration/" + dialect + "/" + file));
                assertFalse(matcher.find(), "migration " + dialect + "/" + file + " contains destructive SQL");
            }
        }
    }
}
