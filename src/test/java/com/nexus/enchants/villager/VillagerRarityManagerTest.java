package com.nexus.enchants.villager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexus.enchants.NexusEnchants;
import com.nexus.enchants.config.ConfigSnapshot;
import com.nexus.enchants.config.VillagerRarityConfig;
import com.nexus.enchants.enchanting.EnchantmentLevelManager;
import com.nexus.enchants.sources.EnchantmentSourceManager;
import com.nexus.enchants.util.EnchantmentResolver;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import org.bukkit.enchantments.Enchantment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Librarian rarity tests: V003 and the exclusion rules from W002/W003.
 *
 * <p>The weights under test are the shipped defaults, so a change to
 * {@code villager-rarity.yml} that breaks the documented 2:12 Mending-to-Unbreaking
 * ratio fails here as well as in the statistical suite.</p>
 */
class VillagerRarityManagerTest {

    private static final int SAMPLES = 200_000;

    private NexusEnchants plugin;
    private VillagerRarityManager manager;
    private Supplier<ConfigSnapshot> snapshot;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(NexusEnchants.class);
        snapshot = () -> plugin.bootstrap().configManager().current();
        manager = new VillagerRarityManager(
                snapshot,
                new EnchantmentSourceManager(snapshot),
                new EnchantmentLevelManager(() -> snapshot.get().levels()),
                new Random(20250101L));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("V003: Mending is selectable and matches its 2 of 14 share")
    void mendingShare() {
        Map<String, Integer> counts = new HashMap<>();
        int empty = 0;
        for (int index = 0; index < SAMPLES; index++) {
            Optional<VillagerRarityManager.Selection> selection = manager.select();
            if (selection.isEmpty()) {
                empty++;
                continue;
            }
            counts.merge(selection.get().enchantmentKey(), 1, Integer::sum);
        }
        assertEquals(0, empty, "the shipped weights always produce a selection");

        // The shipped weights sum to 70 (2+12+10+5+4+12+10+5+7+3).
        double mending = counts.getOrDefault("minecraft:mending", 0) / (double) SAMPLES;
        double unbreaking = counts.getOrDefault("minecraft:unbreaking", 0) / (double) SAMPLES;
        assertEquals(2.0D / 70.0D, mending, 0.004D);
        assertEquals(12.0D / 70.0D, unbreaking, 0.004D);
        assertEquals(2.0D / 70.0D, manager.probabilityOf("minecraft:mending"), 1.0e-9);
    }

    @Test
    @DisplayName("A selection always carries an enchantment and a legal level")
    void selectionIsConsistent() {
        for (int index = 0; index < 2000; index++) {
            VillagerRarityManager.Selection selection = manager.select().orElseThrow();
            assertNotNull(selection.enchantment());
            assertEquals(EnchantmentResolver.keyOf(selection.enchantment()), selection.enchantmentKey());
            int[] range = EnchantmentLevelManager.legalRange(selection.enchantment());
            assertTrue(selection.level() >= range[0] && selection.level() <= range[1],
                    selection.enchantmentKey() + " level " + selection.level());
        }
    }

    @Test
    @DisplayName("The weighted table honours both weight maps")
    void tableContents() {
        var table = manager.buildTable(snapshot.get());
        assertFalse(table.isEmpty());
        Enchantment mending = EnchantmentResolver.resolve("MENDING").orElseThrow();
        assertEquals(2L, table.weightOf(mending));
        assertEquals(70L, table.totalWeight(), "the ten configured weights sum to 70");
        assertEquals(2.0D / 70.0D, table.probabilityOf(mending), 1.0e-9);
        // Only the documented ten enchantments are configured.
        assertEquals(10, table.size());
    }

    @Test
    @DisplayName("A disabled rarity system yields no selection at all")
    void disabledSystem() {
        VillagerRarityManager disabled = new VillagerRarityManager(
                () -> withVillagerRarity(VillagerRarityConfig.disabled()),
                new EnchantmentSourceManager(snapshot),
                new EnchantmentLevelManager(() -> snapshot.get().levels()),
                new Random(1L));
        assertTrue(disabled.select().isEmpty());
        assertTrue(disabled.buildTable(snapshot.get()).isEmpty());
        assertEquals(0.0D, disabled.probabilityOf("minecraft:mending"));
    }

    @Test
    @DisplayName("An enchantment without a configured weight can never be selected")
    void unconfiguredEnchantment() {
        var table = manager.buildTable(snapshot.get());
        Enchantment unconfigured = EnchantmentResolver.resolve("FROST_WALKER").orElseThrow();
        assertEquals(0L, table.weightOf(unconfigured));
        assertEquals(0.0D, table.probabilityOf(unconfigured));
        assertEquals(0.0D, manager.probabilityOf("minecraft:frost_walker"));
    }

    @Test
    @DisplayName("Villager level weights fall back to the global level table")
    void levelWeights() {
        // The shipped villager file leaves per-enchantment level weights empty, so the
        // global table decides. It fixes Mending at level I with a weight of 100.
        Map<Integer, Integer> mending = manager.levelWeightsFor("minecraft:mending");
        assertTrue(mending.isEmpty(), "no villager-specific override is shipped: " + mending);
        assertTrue(snapshot.get().levels().hasOverride("minecraft:mending"));
        assertEquals(Map.of(1, 100),
                snapshot.get().levels().weightsFor("minecraft:mending"));
        for (int index = 0; index < 4000; index++) {
            VillagerRarityManager.Selection selection = manager.select().orElseThrow();
            if ("minecraft:mending".equals(selection.enchantmentKey())) {
                assertEquals(1, selection.level(), "the global override forces level I");
            }
        }
    }

    private ConfigSnapshot withVillagerRarity(VillagerRarityConfig rarity) {
        ConfigSnapshot base = snapshot.get();
        return new ConfigSnapshot(base.settings(), base.pricing(), rarity, base.enchanting(),
                base.levels(), base.sources(), base.discounts(), base.database(), base.issues(),
                base.loadedAtMillis());
    }
}
