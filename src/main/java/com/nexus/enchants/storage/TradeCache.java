package com.nexus.enchants.storage;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory view of the villagers the plugin currently controls.
 *
 * <p>The cache lets the plugin answer "is this villager managed?" without a
 * query and lets a reload find every villager that needs repricing. It is only
 * a cache: SQL remains the durable record and YAML remains the price source, so
 * losing the cache can never change a price.</p>
 *
 * <p>All access must happen on the server thread.</p>
 */
public final class TradeCache {

    private final Map<UUID, VillagerRecord> villagers = new ConcurrentHashMap<>();

    /**
     * Records a managed villager.
     *
     * @param record villager metadata
     */
    public void markManaged(VillagerRecord record) {
        villagers.put(record.villagerId(), record);
    }

    /**
     * Returns whether a villager is managed.
     *
     * @param villagerId villager UUID
     * @return {@code true} when the villager is managed
     */
    public boolean isManaged(UUID villagerId) {
        return villagers.containsKey(villagerId);
    }

    /**
     * Returns the cached record of a villager.
     *
     * @param villagerId villager UUID
     * @return the record, if cached
     */
    public Optional<VillagerRecord> get(UUID villagerId) {
        return Optional.ofNullable(villagers.get(villagerId));
    }

    /**
     * Removes a villager from the cache.
     *
     * @param villagerId villager UUID
     */
    public void remove(UUID villagerId) {
        villagers.remove(villagerId);
    }

    /**
     * Returns a snapshot of every managed villager.
     *
     * @return immutable collection
     */
    public Collection<VillagerRecord> snapshot() {
        return Map.copyOf(villagers).values();
    }

    /**
     * Returns the number of managed villagers.
     *
     * @return the cache size
     */
    public int size() {
        return villagers.size();
    }

    /**
     * Empties the cache.
     */
    public void clear() {
        villagers.clear();
    }
}
