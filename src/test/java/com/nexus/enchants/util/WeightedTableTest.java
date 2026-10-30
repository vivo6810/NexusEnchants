package com.nexus.enchants.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Weighted selection tests W001 through W010.
 */
class WeightedTableTest {

    @Test
    @DisplayName("W001: weight 2 against 12 yields the expected ratio")
    void relativeDistribution() {
        WeightedTable<String> table = WeightedTable.<String>builder()
                .add("MENDING", 2)
                .add("UNBREAKING", 12)
                .build();
        assertEquals(14L, table.totalWeight());
        assertEquals(2.0D / 14.0D, table.probabilityOf("MENDING"), 1.0e-12);
        assertEquals(12.0D / 14.0D, table.probabilityOf("UNBREAKING"), 1.0e-12);
    }

    @Test
    @DisplayName("W002: a zero weight is never selected")
    void zeroWeightNeverSelected() {
        WeightedTable<String> table = WeightedTable.<String>builder()
                .add("ZERO", 0)
                .add("ALWAYS", 5)
                .build();
        Random random = new Random(4242L);
        for (int index = 0; index < 5000; index++) {
            assertEquals(Optional.of("ALWAYS"), table.pick(random));
        }
        assertEquals(0.0D, table.probabilityOf("ZERO"));
        assertEquals(0L, table.weightOf("ZERO"));
    }

    @Test
    @DisplayName("W003: a disabled enchantment has no selectable weight")
    void disabledEnchantment() {
        EnchantmentWeight disabled = new EnchantmentWeight(false, 40);
        assertEquals(0, disabled.effectiveWeight());
        assertFalse(disabled.selectable());

        WeightedTable<String> table = WeightedTable.<String>builder()
                .add("DISABLED", disabled.effectiveWeight())
                .add("ENABLED", 3)
                .build();
        assertEquals(3L, table.totalWeight());
        assertEquals(0.0D, table.probabilityOf("DISABLED"));
    }

    @Test
    @DisplayName("W004: a table whose weights are all zero is empty and reports it")
    void allWeightsZero() {
        WeightedTable<String> table = WeightedTable.<String>builder()
                .add("A", 0)
                .add("B", 0)
                .build();
        assertTrue(table.isEmpty());
        assertEquals(Optional.empty(), table.pick(new Random(1L)));
        assertEquals(0.0D, table.probabilityOf("A"));
    }

    @Test
    @DisplayName("W005: a negative weight is rejected")
    void negativeWeightRejected() {
        assertThrows(IllegalArgumentException.class, () -> WeightedTable.<String>builder().add("A", -1));
        assertThrows(IllegalArgumentException.class, () -> new WeightedTable.Entry<>("A", -5L));
    }

    @Test
    @DisplayName("W006: a single eligible enchantment is always selected")
    void singleEntry() {
        WeightedTable<String> table = WeightedTable.of(Map.of("ONLY", 1));
        Random random = new Random(7L);
        for (int index = 0; index < 1000; index++) {
            assertEquals(Optional.of("ONLY"), table.pick(random));
        }
    }

    @Test
    @DisplayName("W007: very large weights do not overflow")
    void largeWeights() {
        WeightedTable<String> table = WeightedTable.<String>builder()
                .add("BIG", Integer.MAX_VALUE)
                .add("ALSO_BIG", Integer.MAX_VALUE)
                .build();
        assertEquals(2L * Integer.MAX_VALUE, table.totalWeight());
        Random random = new Random(11L);
        for (int index = 0; index < 1000; index++) {
            assertTrue(table.pick(random).isPresent());
        }
        assertThrows(IllegalArgumentException.class, () -> WeightedTable.<String>builder()
                .add("A", Long.MAX_VALUE)
                .add("B", Long.MAX_VALUE)
                .build());
    }

    @Test
    @DisplayName("W008: a missing entry reports a zero weight instead of failing")
    void missingWeight() {
        WeightedTable<String> table = WeightedTable.of(Map.of("A", 1));
        assertEquals(0L, table.weightOf("MISSING"));
        assertEquals(0.0D, table.probabilityOf("MISSING"));
        assertEquals(1, table.size());
    }

    @Test
    @DisplayName("W009: duplicate additions accumulate instead of discarding weights")
    void duplicateAdditionsAccumulate() {
        WeightedTable<String> table = WeightedTable.<String>builder()
                .add("A", 2)
                .add("A", 3)
                .build();
        assertEquals(5L, table.weightOf("A"));
        assertEquals(1, table.size());
    }

    @Test
    @DisplayName("W010: the same seed produces the same sequence")
    void seededReproducibility() {
        WeightedTable<String> table = WeightedTable.<String>builder()
                .add("A", 1)
                .add("B", 3)
                .add("C", 7)
                .build();
        List<String> first = draw(table, 42L);
        List<String> second = draw(table, 42L);
        assertEquals(first, second);
        assertFalse(first.isEmpty());
    }

    @Test
    @DisplayName("An immutable copy is returned by entries()")
    void entriesAreImmutable() {
        WeightedTable<String> table = WeightedTable.of(Map.of("A", 1));
        List<WeightedTable.Entry<String>> entries = table.entries();
        assertThrows(UnsupportedOperationException.class,
                () -> entries.add(new WeightedTable.Entry<>("B", 1L)));
    }

    @Test
    @DisplayName("Order is preserved for reproducibility")
    void orderIsStable() {
        Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("FIRST", 1);
        weights.put("SECOND", 1);
        weights.put("THIRD", 1);
        WeightedTable<String> table = WeightedTable.of(weights);
        List<String> names = new ArrayList<>();
        table.entries().forEach(entry -> names.add(entry.value()));
        assertEquals(List.of("FIRST", "SECOND", "THIRD"), names);
    }

    private static List<String> draw(WeightedTable<String> table, long seed) {
        Random random = new Random(seed);
        List<String> drawn = new ArrayList<>();
        for (int index = 0; index < 500; index++) {
            table.pick(random).ifPresent(drawn::add);
        }
        return drawn;
    }
}
