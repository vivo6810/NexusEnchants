package com.nexus.enchants.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the in-memory rarity statistics aggregator.
 */
class RarityStatisticsStoreTest {

    private static final StatisticKey VILLAGER_MENDING = new StatisticKey("villager", "minecraft:mending", 1);
    private static final StatisticKey VILLAGER_UNBREAKING = new StatisticKey("villager", "minecraft:unbreaking", 3);
    private static final StatisticKey TABLE_THORNS = new StatisticKey("enchanting-table", "minecraft:thorns", 3);

    @Test
    @DisplayName("Selections accumulate per key and drain returns the exact totals")
    void accumulateAndDrain() {
        RarityStatisticsStore store = new RarityStatisticsStore(true);

        store.record(VILLAGER_MENDING);
        store.record(VILLAGER_MENDING);
        store.record(VILLAGER_MENDING, 5L);
        store.record(VILLAGER_UNBREAKING, 2L);
        store.record(TABLE_THORNS, 1L);

        assertEquals(3, store.pendingKeys());
        assertEquals(10L, store.recordedSinceFlush());

        Map<StatisticKey, Long> drained = store.drain();
        assertEquals(8L, drained.get(VILLAGER_MENDING));
        assertEquals(2L, drained.get(VILLAGER_UNBREAKING));
        assertEquals(1L, drained.get(TABLE_THORNS));
    }

    @Test
    @DisplayName("Draining clears the counters exactly once")
    void drainClears() {
        RarityStatisticsStore store = new RarityStatisticsStore(true);
        store.record(VILLAGER_MENDING, 4L);

        store.drain();
        assertEquals(0, store.pendingKeys());
        assertEquals(0L, store.recordedSinceFlush());
        assertTrue(store.drain().isEmpty());
    }

    @Test
    @DisplayName("A drained snapshot cannot be mutated")
    void drainedSnapshotIsImmutable() {
        RarityStatisticsStore store = new RarityStatisticsStore(true);
        store.record(VILLAGER_MENDING, 1L);

        assertThrows(UnsupportedOperationException.class, () -> store.drain().clear());
        store.record(VILLAGER_MENDING, 2L);
        assertEquals(2L, store.recordedSinceFlush());
    }

    @Test
    @DisplayName("Zero and negative amounts are ignored")
    void invalidAmountsIgnored() {
        RarityStatisticsStore store = new RarityStatisticsStore(true);
        store.record(VILLAGER_MENDING, 0L);
        store.record(VILLAGER_MENDING, -3L);
        assertEquals(0, store.pendingKeys());
        assertThrows(NullPointerException.class, () -> store.record(null, 1L));
    }

    @Test
    @DisplayName("A disabled store records nothing even for valid keys")
    void disabledStore() {
        RarityStatisticsStore store = new RarityStatisticsStore(false);
        store.record(VILLAGER_MENDING, 10L);
        assertTrue(store.drain().isEmpty());
        assertEquals(0L, store.recordedSinceFlush());
        assertTrue(!store.enabled());
    }
}
