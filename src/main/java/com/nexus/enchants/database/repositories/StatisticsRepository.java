package com.nexus.enchants.database.repositories;

import com.nexus.enchants.database.DatabaseManager;
import com.nexus.enchants.storage.StatisticKey;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;

/**
 * Batched access to {@code rarity_statistics}.
 *
 * <p>Counts arrive already aggregated, so a flush costs one batch instead of one
 * statement per selection.</p>
 */
public final class StatisticsRepository {

    private final DatabaseManager database;

    /**
     * Creates the repository.
     *
     * @param database database manager
     */
    public StatisticsRepository(DatabaseManager database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /**
     * Adds aggregated counts.
     *
     * @param increments key to number of additional selections
     * @param now        epoch milliseconds
     * @return the number of rows written
     * @throws SQLException when the batch fails
     */
    public int addCounts(Map<StatisticKey, Long> increments, long now) throws SQLException {
        if (increments.isEmpty()) {
            return 0;
        }
        String sql = "INSERT INTO rarity_statistics"
                + " (source, enchantment_key, enchantment_level, selection_count, updated_at)"
                + " VALUES (?, ?, ?, ?, ?)"
                + database.dialect().statisticsUpsertClause();
        int rows = 0;
        try (Connection connection = database.connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<StatisticKey, Long> entry : increments.entrySet()) {
                if (entry.getValue() <= 0L) {
                    continue;
                }
                StatisticKey key = entry.getKey();
                statement.setString(1, key.source());
                statement.setString(2, key.enchantmentKey());
                statement.setInt(3, key.enchantmentLevel());
                statement.setLong(4, entry.getValue());
                statement.setLong(5, now);
                statement.addBatch();
                rows++;
            }
            if (rows > 0) {
                statement.executeBatch();
            }
        }
        return rows;
    }

    /**
     * Returns the total number of recorded selections.
     *
     * @return the sum of all counters
     * @throws SQLException when the read fails
     */
    public long totalSelections() throws SQLException {
        try (Connection connection = database.connection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COALESCE(SUM(selection_count), 0) FROM rarity_statistics");
                ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getLong(1) : 0L;
        }
    }

    /**
     * Counts statistic rows.
     *
     * @return the row count
     * @throws SQLException when the read fails
     */
    public long rowCount() throws SQLException {
        try (Connection connection = database.connection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM rarity_statistics");
                ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getLong(1) : 0L;
        }
    }
}
