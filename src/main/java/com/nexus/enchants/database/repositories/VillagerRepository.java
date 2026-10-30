package com.nexus.enchants.database.repositories;

import com.nexus.enchants.database.DatabaseManager;
import com.nexus.enchants.storage.VillagerRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Read and write access to {@code managed_villagers}.
 *
 * <p>Every method must be called from a database worker thread.</p>
 */
public final class VillagerRepository {

    private final DatabaseManager database;

    /**
     * Creates the repository.
     *
     * @param database database manager
     */
    public VillagerRepository(DatabaseManager database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /**
     * Inserts or updates a villager. The original registration timestamp is preserved.
     *
     * @param record villager metadata
     * @throws SQLException when the write fails
     */
    public void upsert(VillagerRecord record) throws SQLException {
        String sql = "INSERT INTO managed_villagers"
                + " (villager_uuid, world_uuid, world_name, profession, managed,"
                + " first_registered, last_updated, schema_version)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                + database.dialect().villagerUpsertClause();
        try (Connection connection = database.connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.villagerId().toString());
            statement.setString(2, record.worldId().toString());
            statement.setString(3, record.worldName());
            statement.setString(4, record.profession());
            statement.setInt(5, record.managed() ? 1 : 0);
            statement.setLong(6, record.firstRegistered());
            statement.setLong(7, record.lastUpdated());
            statement.setInt(8, record.schemaVersion());
            statement.executeUpdate();
        }
    }

    /**
     * Looks up a villager.
     *
     * @param villagerId villager UUID
     * @return the record when it exists
     * @throws SQLException when the read fails
     */
    public Optional<VillagerRecord> find(UUID villagerId) throws SQLException {
        String sql = "SELECT villager_uuid, world_uuid, world_name, profession, managed,"
                + " first_registered, last_updated, schema_version"
                + " FROM managed_villagers WHERE villager_uuid = ?";
        try (Connection connection = database.connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, villagerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new VillagerRecord(
                        UUID.fromString(result.getString("villager_uuid")),
                        UUID.fromString(result.getString("world_uuid")),
                        result.getString("world_name"),
                        result.getString("profession"),
                        result.getInt("managed") != 0,
                        result.getLong("first_registered"),
                        result.getLong("last_updated"),
                        result.getInt("schema_version")));
            }
        }
    }

    /**
     * Counts managed villagers.
     *
     * @return the row count
     * @throws SQLException when the read fails
     */
    public long count() throws SQLException {
        try (Connection connection = database.connection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM managed_villagers");
                ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getLong(1) : 0L;
        }
    }

    /**
     * Deletes a villager and, through the foreign key, its trades.
     *
     * @param villagerId villager UUID
     * @return {@code true} when a row was removed
     * @throws SQLException when the delete fails
     */
    public boolean delete(UUID villagerId) throws SQLException {
        try (Connection connection = database.connection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM managed_villagers WHERE villager_uuid = ?")) {
            statement.setString(1, villagerId.toString());
            return statement.executeUpdate() > 0;
        }
    }
}
