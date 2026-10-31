package com.nexus.enchants.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexus.enchants.config.ConfigIssue.Severity;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the registry-free ConfigValidator helpers.
 *
 * <p>Server-dependent paths (enchantment resolution) run inside the MockBukkit
 * suites; these tests pin the pure parsing and issue-reporting contract.</p>
 */
class ConfigValidatorTest {

    @Test
    @DisplayName("A percentage outside 0..100 is reported and clamped")
    void percentageRange() {
        List<ConfigIssue> issues = new ArrayList<>();
        assertEquals(50, ConfigValidator.percentage(50, "f.yml", "p", issues));
        assertEquals(100, ConfigValidator.percentage(140, "f.yml", "p.high", issues));
        assertEquals(0, ConfigValidator.percentage(-7, "f.yml", "p.low", issues));

        assertEquals(2, issues.size());
        assertEquals(Severity.ERROR, issues.get(0).severity());
        assertEquals("f.yml", issues.get(0).file());
        assertEquals("p.high", issues.get(0).path());
        assertTrue(issues.get(0).message().contains("between 0 and 100"));
    }

    @Test
    @DisplayName("A negative weight is reported and clamped to zero")
    void weightRange() {
        List<ConfigIssue> issues = new ArrayList<>();
        assertEquals(5, ConfigValidator.weight(5, "f.yml", "w", issues));
        assertEquals(0, ConfigValidator.weight(-1, "f.yml", "w.negative", issues));

        assertEquals(1, issues.size());
        assertEquals("f.yml: w.negative", issues.get(0).file() + ": " + issues.get(0).path());
        assertTrue(issues.get(0).message().contains("must not be negative"));
    }

    @Test
    @DisplayName("Level weight tables accept level-N keys and reject everything else")
    void levelWeights() throws InvalidConfigurationException {
        YamlConfiguration yml = new YamlConfiguration();
        yml.loadFromString("""
                weights:
                  level-1: 45
                  level-2: 30
                  level-5: 3
                  level-x: 9
                  level-0: 9
                  bonus: 1
                """);
        MemorySection weights = (MemorySection) yml.getConfigurationSection("weights");
        List<ConfigIssue> issues = new ArrayList<>();
        var result = ConfigValidator.readLevelWeights(weights, "enchantment-levels.yml",
                "enchantment-levels.default", issues);

        assertEquals(45, result.get(1));
        assertEquals(30, result.get(2));
        assertEquals(3, result.get(5));
        // level-x, level-0 and bonus are each rejected with one focused issue.
        assertEquals(3, issues.size());
        assertTrue(issues.stream().anyMatch(i -> i.message().contains("must be a number")));
        assertTrue(issues.stream().anyMatch(i -> i.message().contains("at least 1")));
        assertTrue(issues.stream().allMatch(i -> i.path().startsWith("enchantment-levels.default.")));
    }

    @Test
    @DisplayName("A null section yields an empty map without issues")
    void nullSection() {
        List<ConfigIssue> issues = new ArrayList<>();
        var result = ConfigValidator.readLevelWeights(null, "f.yml", "p", issues);
        assertTrue(result.isEmpty());
        assertTrue(issues.isEmpty());
    }

    @Test
    @DisplayName("Source defaults start all-true and follow the section overrides")
    void sourceDefaults() throws InvalidConfigurationException {
        List<ConfigIssue> issues = new ArrayList<>();
        var defaults = ConfigValidator.readSourceDefaults(null, "f.yml", "p", issues);
        assertEquals(4, defaults.size());
        assertTrue(defaults.values().stream().allMatch(Boolean::booleanValue));

        YamlConfiguration yml = new YamlConfiguration();
        yml.loadFromString("""
                defaults:
                  villager: true
                  enchanting-table: false
                  loot: true
                  fishing: true
                  anvil: false
                """);
        var parsed = ConfigValidator.readSourceDefaults(
                yml.getConfigurationSection("defaults"), "enchantment-sources.yml",
                "enchantment-sources.defaults", issues);
        assertEquals(Boolean.TRUE, parsed.get(com.nexus.enchants.sources.AcquisitionSource.VILLAGER));
        assertEquals(Boolean.FALSE, parsed.get(com.nexus.enchants.sources.AcquisitionSource.ENCHANTING_TABLE));
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).message().contains("unknown source"));
    }

    @Test
    @DisplayName("Issues carry file, path and severity")
    void issueFactories() {
        ConfigIssue error = ConfigValidator.error("f.yml", "p", "m");
        ConfigIssue warning = ConfigValidator.warning("g.yml", "q", "n");
        assertEquals(Severity.ERROR, error.severity());
        assertEquals(Severity.WARNING, warning.severity());
        assertEquals("g.yml", warning.file());
        assertEquals("q", warning.path());
        assertEquals("m", error.message());
        // Issue severity parsing round-trips for diagnostics output.
        assertThrows(NullPointerException.class, () -> ConfigValidator.error(null, "p", "m"));
    }
}
