package com.nexus.enchants.database.repositories;

import com.nexus.enchants.database.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;

/**
 * Append-only access to {@code audit_logs}.
 */
public final class AuditLogRepository {

    private final DatabaseManager database;

    /**
     * Creates the repository.
     *
     * @param database database manager
     */
    public AuditLogRepository(DatabaseManager database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /**
     * Inserts an audit entry.
     *
     * @param record entry to write
     * @throws SQLException when the insert fails
     */
    public void insert(AuditRecord record) throws SQLException {
        String sql = "INSERT INTO audit_logs"
                + " (event_type, actor_uuid, villager_uuid, enchantment_key, enchantment_level,"
                + " previous_value, new_value, details, created_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = database.connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.eventType());
            setNullableString(statement, 2, record.actorId() == null ? null : record.actorId().toString());
            setNullableString(statement, 3, record.villagerId() == null ? null : record.villagerId().toString());
            setNullableString(statement, 4, record.enchantmentKey());
            if (record.enchantmentLevel() == null) {
                statement.setNull(5, Types.INTEGER);
            } else {
                statement.setInt(5, record.enchantmentLevel());
            }
            setNullableString(statement, 6, record.previousValue());
            setNullableString(statement, 7, record.newValue());
            setNullableString(statement, 8, record.details());
            statement.setLong(9, record.createdAt());
            statement.executeUpdate();
        }
    }

    private static void setNullableString(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    /**
     * Counts audit entries.
     *
     * @return the row count
     * @throws SQLException when the read fails
     */
    public long count() throws SQLException {
        try (Connection connection = database.connection();
                PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM audit_logs");
                ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getLong(1) : 0L;
        }
    }

    /**
     * Deletes entries older than a timestamp, keeping the table bounded.
     *
     * @param cutoffEpochMillis entries created before this instant are removed
     * @return the number of removed rows
     * @throws SQLException when the delete fails
     */
    public int deleteOlderThan(long cutoffEpochMillis) throws SQLException {
        try (Connection connection = database.connection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM audit_logs WHERE created_at < ?")) {
            statement.setLong(1, cutoffEpochMillis);
            return statement.executeUpdate();
        }
    }
}
