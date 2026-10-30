package com.nexus.enchants.database.repositories;

import com.nexus.enchants.database.DatabaseManager;
import com.nexus.enchants.storage.TradeRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Read and write access to {@code managed_trades}.
 *
 * <p>The unique key {@code (villager_uuid, trade_key)} makes a duplicate trade
 * row impossible even when the same trade is rewritten repeatedly.</p>
 */
public final class TradeRepository {

    private final DatabaseManager database;

    /**
     * Creates the repository.
     *
     * @param database database manager
     */
    public TradeRepository(DatabaseManager database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /**
     * Inserts or updates a trade.
     *
     * @param record trade metadata
     * @throws SQLException when the write fails
     */
    public void upsert(TradeRecord record) throws SQLException {
        String sql = "INSERT INTO managed_trades"
                + " (villager_uuid, trade_key, enchantment_key, enchantment_level, emerald_price,"
                + " uses, max_uses, created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                + database.dialect().tradeUpsertClause();
        try (Connection connection = database.connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.villagerId().toString());
            statement.setString(2, record.tradeKey());
            statement.setString(3, record.enchantmentKey());
            statement.setInt(4, record.enchantmentLevel());
            statement.setInt(5, record.emeraldPrice());
            statement.setInt(6, record.uses());
            statement.setInt(7, record.maxUses());
            statement.setLong(8, record.createdAt());
            statement.setLong(9, record.updatedAt());
            statement.executeUpdate();
        }
    }

    /**
     * Returns every trade of a villager.
     *
     * @param villagerId villager UUID
     * @return trades ordered by trade key
     * @throws SQLException when the read fails
     */
    public List<TradeRecord> findByVillager(UUID villagerId) throws SQLException {
        String sql = "SELECT villager_uuid, trade_key, enchantment_key, enchantment_level, emerald_price,"
                + " uses, max_uses, created_at, updated_at"
                + " FROM managed_trades WHERE villager_uuid = ? ORDER BY trade_key";
        List<TradeRecord> records = new ArrayList<>();
        try (Connection connection = database.connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, villagerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    records.add(new TradeRecord(
                            UUID.fromString(result.getString("villager_uuid")),
                            result.getString("trade_key"),
                            result.getString("enchantment_key"),
                            result.getInt("enchantment_level"),
                            result.getInt("emerald_price"),
                            result.getInt("uses"),
                            result.getInt("max_uses"),
                            result.getLong("created_at"),
                            result.getLong("updated_at")));
                }
            }
        }
        return records;
    }

    /**
     * Counts tracked trades.
     *
     * @return the row count
     * @throws SQLException when the read fails
     */
    public long count() throws SQLException {
        try (Connection connection = database.connection();
                PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM managed_trades");
                ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getLong(1) : 0L;
        }
    }

    /**
     * Deletes every trade of a villager.
     *
     * @param villagerId villager UUID
     * @return the number of removed rows
     * @throws SQLException when the delete fails
     */
    public int deleteByVillager(UUID villagerId) throws SQLException {
        try (Connection connection = database.connection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM managed_trades WHERE villager_uuid = ?")) {
            statement.setString(1, villagerId.toString());
            return statement.executeUpdate();
        }
    }
}
