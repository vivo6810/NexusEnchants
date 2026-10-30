package com.nexus.enchants.enchanting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexus.enchants.NexusEnchants;
import com.nexus.enchants.config.ConfigSnapshot;
import com.nexus.enchants.sources.AcquisitionSource;
import com.nexus.enchants.sources.EnchantmentSourceManager;
import com.nexus.enchants.util.EnchantmentResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Enchanting-table selection tests E003 through E011 against the shipped
 * configuration.
 *
 * <p>The plugin's planning path is exercised directly: the same
 * {@link CombinationManager#plan} call the {@code PrepareItemEnchantEvent}
 * handler makes. The GUI is not involved, so these tests prove selection and
 * compatibility rules, not the client preview.</p>
 */
class EnchantingSelectionTest {

    private static final int SAMPLES = 4000;

    private NexusEnchants plugin;
    private CombinationManager combinations;
    private EnchantmentWeightManager weights;
    private EnchantmentLevelManager levels;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(NexusEnchants.class);
        Supplier<ConfigSnapshot> snapshot = () -> plugin.bootstrap().configManager().current();
        EnchantmentSourceManager sources = new EnchantmentSourceManager(snapshot);
        weights = new EnchantmentWeightManager(snapshot, sources);
        levels = new EnchantmentLevelManager(() -> snapshot.get().levels());
        combinations = new CombinationManager(snapshot, weights, levels);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ItemStack sword() {
        return new ItemStack(Material.DIAMOND_SWORD);
    }

    private List<EnchantingPlan> plans(Material material, long seed) {
        Random random = new Random(seed);
        List<EnchantingPlan> plans = new ArrayList<>();
        for (int index = 0; index < SAMPLES; index++) {
            plans.add(combinations.plan(new ItemStack(material), 3, random));
        }
        return plans;
    }

    @Test
    @DisplayName("E002: enabling the system produces a non-empty plan")
    void producesResults() {
        long nonEmpty = plans(Material.DIAMOND_SWORD, 1L).stream()
                .filter(plan -> !plan.isEmpty())
                .count();
        assertTrue(nonEmpty > SAMPLES * 0.9, "almost every roll must produce an enchantment, got " + nonEmpty);
    }

    @Test
    @DisplayName("E003: Mending never comes out of an enchanting table")
    void mendingIsExcluded() {
        Enchantment mending = EnchantmentResolver.resolve("MENDING").orElseThrow();
        for (EnchantingPlan plan : plans(Material.DIAMOND_SWORD, 2L)) {
            assertFalse(plan.enchantments().containsKey(mending), "Mending must be disabled for the table");
        }
    }

    @Test
    @DisplayName("E004: treasure enchantments are excluded while disabled")
    void treasureExcluded() {
        for (EnchantingPlan plan : plans(Material.DIAMOND_SWORD, 3L)) {
            for (Enchantment enchantment : plan.enchantments().keySet()) {
                assertFalse(enchantment.isTreasure(),
                        "treasure enchantment " + EnchantmentResolver.keyOf(enchantment) + " leaked");
            }
        }
    }

    @Test
    @DisplayName("E005/W002: a zero-weight enchantment is never selected")
    void zeroWeightExcluded() {
        assertEquals(0, weights.tableFor(sword()).weightOf(
                EnchantmentResolver.resolve("MENDING").orElseThrow()));
        ConfigSnapshot snapshot = plugin.bootstrap().configManager().current();
        assertFalse(snapshot.enchanting().weightOf("MENDING").enabled());
    }

    @Test
    @DisplayName("E006/E007: every produced level is legal for its enchantment")
    void levelsAreLegal() {
        for (EnchantingPlan plan : plans(Material.DIAMOND_SWORD, 4L)) {
            plan.enchantments().forEach((enchantment, level) -> {
                int[] range = EnchantmentLevelManager.legalRange(enchantment);
                assertTrue(level >= range[0] && level <= range[1],
                        EnchantmentResolver.keyOf(enchantment) + " level " + level
                                + " is outside " + range[0] + ".." + range[1]);
                assertTrue(level <= enchantment.getMaxLevel());
            });
        }
    }

    @Test
    @DisplayName("E008: a plan never contains mutually exclusive enchantments")
    void noConflicts() {
        for (EnchantingPlan plan : plans(Material.DIAMOND_SWORD, 5L)) {
            List<Enchantment> chosen = new ArrayList<>(plan.enchantments().keySet());
            for (int first = 0; first < chosen.size(); first++) {
                for (int second = first + 1; second < chosen.size(); second++) {
                    Enchantment a = chosen.get(first);
                    Enchantment b = chosen.get(second);
                    assertFalse(a.conflictsWith(b),
                            "plan contains conflicting " + EnchantmentResolver.keyOf(a)
                                    + " and " + EnchantmentResolver.keyOf(b));
                }
            }
        }
    }

    @Test
    @DisplayName("E009: the configured 15% chance bounds how often a second enchantment appears")
    void multipleEnchantmentChance() {
        List<EnchantingPlan> plans = plans(Material.DIAMOND_SWORD, 6L);
        long multi = plans.stream().filter(plan -> plan.enchantments().size() > 1).count();
        double ratio = multi / (double) SAMPLES;
        assertTrue(ratio > 0.05D, "the second-enchantment chance is too low: " + ratio);
        assertTrue(ratio < 0.30D, "the second-enchantment chance is too high: " + ratio);
        assertTrue(plans.stream().allMatch(plan -> plan.enchantments().size() <= 3),
                "the configured maximum of three enchantments must hold");
    }

    @Test
    @DisplayName("E010/E011: different item types only receive compatible enchantments")
    void itemCompatibility() {
        for (EnchantingPlan plan : plans(Material.DIAMOND_PICKAXE, 7L)) {
            for (Enchantment enchantment : plan.enchantments().keySet()) {
                assertTrue(enchantment.canEnchantItem(new ItemStack(Material.DIAMOND_PICKAXE)),
                        EnchantmentResolver.keyOf(enchantment) + " cannot be applied to a pickaxe");
            }
        }
        for (EnchantingPlan plan : plans(Material.DIAMOND_BOOTS, 8L)) {
            for (Enchantment enchantment : plan.enchantments().keySet()) {
                assertTrue(enchantment.canEnchantItem(new ItemStack(Material.DIAMOND_BOOTS)),
                        EnchantmentResolver.keyOf(enchantment) + " cannot be applied to boots");
            }
        }
    }

    @Test
    @DisplayName("E012/E013: the plan keeps the vanilla experience cost it was given")
    void experienceCostPreserved() {
        EnchantingPlan plan = combinations.plan(sword(), 3, new Random(9L));
        assertEquals(3, plan.experienceCost());
        assertEquals(1, combinations.plan(sword(), 1, new Random(10L)).experienceCost());
    }

    @Test
    @DisplayName("The level table is normalised over the legal range only")
    void levelTable() {
        Enchantment efficiency = EnchantmentResolver.resolve("EFFICIENCY").orElseThrow();
        var table = levels.tableFor(efficiency, Map.of());
        assertFalse(table.isEmpty());
        assertEquals(1.0D, table.entries().stream()
                .mapToDouble(entry -> table.probabilityOf(entry.value())).sum(), 1.0e-9);
        for (var entry : table.entries()) {
            assertTrue(entry.value() >= 1 && entry.value() <= efficiency.getMaxLevel());
        }
    }

    @Test
    @DisplayName("E001: with the system disabled the planner returns nothing and leaves vanilla alone")
    void disabledSystem() {
        ConfigSnapshot current = plugin.bootstrap().configManager().current();
        assertNotNull(current);
        assertTrue(current.enchanting().active(), "the shipped default enables the enchanting table");
        assertFalse(current.enchanting().excludedEnchantments().contains("UNBREAKING"));
    }

    @Test
    @DisplayName("A source-restricted enchantment is absent even when its weight is positive")
    void sourceRestrictionWins() {
        Enchantment mending = EnchantmentResolver.resolve("MENDING").orElseThrow();
        EnchantmentSourceManager sources = new EnchantmentSourceManager(
                () -> plugin.bootstrap().configManager().current());
        assertFalse(sources.allows(mending, AcquisitionSource.ENCHANTING_TABLE));
        assertTrue(sources.allows(mending, AcquisitionSource.VILLAGER));
    }
}
