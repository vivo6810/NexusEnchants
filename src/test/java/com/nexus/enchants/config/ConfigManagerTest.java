package com.nexus.enchants.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexus.enchants.NexusEnchants;
import com.nexus.enchants.economy.DiscountPolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Configuration loading, validation and reloading against a real plugin
 * instance running inside MockBukkit.
 *
 * <p>Covers the configuration half of P011 and the configuration test matrix from
 * the specification: missing files, invalid values, reload behaviour and
 * unknown keys.</p>
 */
class ConfigManagerTest {

    private NexusEnchants plugin;
    private ConfigManager config;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(NexusEnchants.class);
        config = plugin.bootstrap().configManager();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("The bundled defaults load without a single validation error")
    void defaultsLoadCleanly() {
        ConfigSnapshot snapshot = config.current();
        assertTrue(snapshot.errors().isEmpty(), "unexpected errors: " + snapshot.errors());
        assertTrue(snapshot.settings().enabled());
        assertEquals(70, snapshot.pricing().explicitPrice("minecraft:mending", 1).orElse(-1));
        assertEquals(45, snapshot.pricing().explicitPrice("minecraft:unbreaking", 3).orElse(-1));
        assertEquals(2, snapshot.villagerRarity().weightOf("minecraft:mending").weight());
        assertEquals(12, snapshot.villagerRarity().weightOf("minecraft:unbreaking").weight());
        assertFalse(snapshot.enchanting().weightOf("minecraft:mending").enabled());
        assertEquals(DiscountPolicy.CAP, snapshot.discounts().policy());
        assertEquals(20, snapshot.discounts().maximumDiscountPercentage());
        assertEquals(DatabaseType.SQLITE, snapshot.database().type());
    }

    @Test
    @DisplayName("Every configuration file is written to the data folder on first start")
    void filesAreCreated() {
        Path folder = config.dataFolder().toPath();
        for (String file : config.managedFiles()) {
            assertTrue(Files.isRegularFile(folder.resolve(file)), "missing " + file);
        }
        assertEquals(List.of("config.yml", "pricing.yml", "villager-rarity.yml", "enchanting-rarity.yml",
                "enchantment-levels.yml", "enchantment-sources.yml", "discounts.yml"),
                config.managedFiles());
    }

    @Test
    @DisplayName("P011: a price written by a command is applied immediately and survives a reload")
    void writePriceIsApplied() throws IOException {
        ConfigSnapshot updated = config.writePrice("minecraft:mending", 1, 99);
        assertEquals(99, updated.pricing().explicitPrice("minecraft:mending", 1).orElse(-1));

        assertTrue(Files.readString(config.dataFolder().toPath().resolve("pricing.yml"),
                StandardCharsets.UTF_8).contains("99"));

        ConfigSnapshot reloaded = config.reload();
        assertEquals(99, reloaded.pricing().explicitPrice("minecraft:mending", 1).orElse(-1));
        assertTrue(reloaded.errors().isEmpty(), reloaded.errors().toString());
    }

    @Test
    @DisplayName("Rarity weights can be written and reloaded")
    void writeWeights() throws IOException {
        config.writeVillagerWeight("minecraft:mending", 9, true);
        assertTrue(plugin.bootstrap().configManager().current()
                .villagerRarity().weightOf("minecraft:mending").enabled());
        assertEquals(9, config.reload().villagerRarity().weightOf("minecraft:mending").weight());

        config.writeEnchantingWeight("minecraft:mending", 4, true);
        ConfigSnapshot after = config.reload();
        assertTrue(after.enchanting().weightOf("minecraft:mending").enabled());
        assertEquals(4, after.enchanting().weightOf("minecraft:mending").weight());
    }

    @Test
    @DisplayName("An invalid price is reported and cannot silently become the live price")
    void invalidPriceIsReported() throws IOException {
        Path pricing = config.dataFolder().toPath().resolve("pricing.yml");
        String content = Files.readString(pricing, StandardCharsets.UTF_8)
                .replace("    1: 70", "    1: -5");
        Files.writeString(pricing, content, StandardCharsets.UTF_8);

        ConfigSnapshot snapshot = config.reload();
        assertFalse(snapshot.errors().isEmpty(), "a negative price must be reported");

        OptionalInt resolved = snapshot.pricing().explicitPrice("minecraft:mending", 1);
        assertTrue(resolved.isEmpty() || resolved.getAsInt() >= 1,
                "an invalid price must never reach the trade");
    }

    @Test
    @DisplayName("A missing configuration file is recreated from the bundled default")
    void missingFileIsRecreated() throws IOException {
        Path placeholder = config.dataFolder().toPath().resolve("discounts.yml");
        assertTrue(Files.deleteIfExists(placeholder));

        ConfigSnapshot snapshot = config.reload();
        assertTrue(Files.isRegularFile(placeholder), "the file is recreated");
        assertEquals(DiscountPolicy.CAP, snapshot.discounts().policy());
    }

    @Test
    @DisplayName("Broken YAML is reported instead of crashing the reload")
    void brokenYamlIsReported() throws IOException {
        Path pricing = config.dataFolder().toPath().resolve("pricing.yml");
        Files.writeString(pricing, "pricing:\n  MENDING: [unclosed\n", StandardCharsets.UTF_8);

        ConfigSnapshot snapshot = config.reload();
        assertNotNull(snapshot);
        assertFalse(snapshot.errors().isEmpty(), "a YAML syntax error must be reported");
        // The plugin keeps running: it either keeps the previous good snapshot or
        // starts from validated defaults, but it never exposes an unset state.
        assertNotNull(config.current());
        assertNotNull(config.current().pricing());
        assertTrue(config.current().pricing().explicitPrice("minecraft:mending", 1).orElse(0) >= 0);
    }

    @Test
    @DisplayName("Unknown enchantment keys are reported as configuration errors")
    void unknownEnchantmentKey() throws IOException {
        Path pricing = config.dataFolder().toPath().resolve("pricing.yml");
        Files.writeString(pricing, "pricing:\n  NOT_A_REAL_ENCHANTMENT:\n    1: 10\n",
                StandardCharsets.UTF_8);

        ConfigSnapshot snapshot = config.reload();
        assertFalse(snapshot.errors().isEmpty(), "an unknown enchantment must be reported");
    }

    @Test
    @DisplayName("Reloading twice produces a fresh snapshot each time")
    void repeatedReload() {
        ConfigSnapshot first = config.reload();
        ConfigSnapshot second = config.reload();
        assertTrue(second.loadedAtMillis() >= first.loadedAtMillis());
        assertEquals(first.pricing().prices(), second.pricing().prices());
    }
}
