package com.nexus.enchants.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregates rarity selections in memory before they are written to SQL.
 *
 * <p>A selection is a hot path during villager generation and enchanting, so it
 * only touches a counter. The database is written periodically with one batch
 * per flush.</p>
 */
public final class RarityStatisticsStore {

    private final boolean enabled;
    private final Map<StatisticKey, Long> counts = new HashMap<>();
    private long recordedSinceFlush;

    /**
     * Creates the store.
     *
     * @param enabled whether selections are recorded at all
     */
    public RarityStatisticsStore(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Records one selection.
     *
     * @param key statistic identity
     */
    public synchronized void record(StatisticKey key) {
        record(key, 1L);
    }

    /**
     * Records several selections.
     *
     * @param key    statistic identity
     * @param amount number of selections
     */
    public synchronized void record(StatisticKey key, long amount) {
        if (!enabled || amount <= 0L) {
            return;
        }
        Objects.requireNonNull(key, "key");
        counts.merge(key, amount, Long::sum);
        recordedSinceFlush += amount;
    }

    /**
     * Returns and clears the pending counts.
     *
     * @return immutable snapshot of the counters
     */
    public synchronized Map<StatisticKey, Long> drain() {
        if (counts.isEmpty()) {
            return Map.of();
        }
        Map<StatisticKey, Long> drained = Map.copyOf(counts);
        counts.clear();
        recordedSinceFlush = 0L;
        return drained;
    }

    /**
     * Returns how many counters are pending.
     *
     * @return the number of distinct statistic keys
     */
    public synchronized int pendingKeys() {
        return counts.size();
    }

    /**
     * Returns how many selections were recorded since the last drain.
     *
     * @return the selection count
     */
    public synchronized long recordedSinceFlush() {
        return recordedSinceFlush;
    }

    /**
     * Returns whether recording is enabled.
     *
     * @return the enabled flag
     */
    public boolean enabled() {
        return enabled;
    }
}
