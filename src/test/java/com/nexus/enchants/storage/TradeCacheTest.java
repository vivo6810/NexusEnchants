package com.nexus.enchants.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the in-memory managed-villager cache.
 */
class TradeCacheTest {

    private static final long NOW = 1750000000000L;

    private TradeCache cache;

    @BeforeEach
    void setUp() {
        cache = new TradeCache();
    }

    private VillagerRecord villager(UUID id) {
        return VillagerRecord.create(id, UUID.randomUUID(), "world", "minecraft:librarian", NOW);
    }

    @Test
    @DisplayName("A marked villager is managed and retrievable")
    void markAndLookup() {
        UUID id = UUID.randomUUID();
        cache.markManaged(villager(id));

        assertTrue(cache.isManaged(id));
        assertEquals(1, cache.size());
        assertEquals(Optional.of("minecraft:librarian"),
                cache.get(id).map(VillagerRecord::profession));
    }

    @Test
    @DisplayName("An unknown villager is never reported as managed")
    void unknownVillager() {
        assertFalse(cache.isManaged(UUID.randomUUID()));
        assertEquals(Optional.empty(), cache.get(UUID.randomUUID()));
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("Marking the same villager again replaces the record")
    void reMarkReplaces() {
        UUID id = UUID.randomUUID();
        cache.markManaged(villager(id));
        VillagerRecord updated = cache.get(id).orElseThrow().touch(NOW + 5_000L);
        cache.markManaged(updated);

        assertEquals(1, cache.size());
        assertEquals(NOW + 5_000L, cache.get(id).orElseThrow().lastUpdated());
    }

    @Test
    @DisplayName("The snapshot is immutable and reflects the live content")
    void snapshotIsImmutable() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        cache.markManaged(villager(first));
        cache.markManaged(villager(second));

        List<VillagerRecord> snapshot = List.copyOf(cache.snapshot());
        assertEquals(2, snapshot.size());
        assertThrows(UnsupportedOperationException.class,
                () -> cache.snapshot().stream().toList().add(villager(UUID.randomUUID())));
        // Mutating the returned collection cannot affect the cache itself:
        cache.snapshot().stream().toList().clear();
        assertEquals(2, cache.size());

        cache.remove(first);
        assertEquals(1, cache.snapshot().stream().filter(r -> r.villagerId().equals(second)).count());
    }

    @Test
    @DisplayName("Removal and clear empty the cache")
    void removalAndClear() {
        UUID id = UUID.randomUUID();
        cache.markManaged(villager(id));
        cache.remove(id);
        assertFalse(cache.isManaged(id));

        cache.markManaged(villager(UUID.randomUUID()));
        cache.clear();
        assertEquals(0, cache.size());
    }
}
