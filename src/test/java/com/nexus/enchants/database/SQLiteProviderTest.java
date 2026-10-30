package com.nexus.enchants.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexus.enchants.config.DatabaseConfig;
import com.nexus.enchants.config.DatabaseType;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Real SQLite integration tests. No database is mocked: every assertion runs
 * against a temporary database file created by the driver.
 *
 * <p>Covers DB001, DB003, DB005 through DB008, DB010 through DB012, DB015,
 * DB016, DB017 and DB020.</p>
 */
class SQLiteProviderTest {

    private static final Logger LOGGER = Logger.getLogger("NexusEnchantsTest");

    @TempDir
    Path tempDir;

    private SQLiteProvider provider;

    private static DatabaseConfig config() {
        return new DatabaseConfig(DatabaseType.SQLITE, "nexusenchants.db", true, 5000, "WAL", 3, null);
    }

    @AfterEach
    void closeProvider() {
        if (provider != null) {
            provider.close();
            provider = null;
        }
    }

    private SQLiteProvider open() {
        provider = new SQLiteProvider(config(), tempDir.toFile(), LOGGER);
        provider.initialize();
        return provider;
    }

    @Test
    @DisplayName("DB003: the database file is created on initialization")
    void createsDatabaseFile() {
        SQLiteProvider provider = open();
        Path file = provider.databaseFile();
        assertNotNull(file, "the resolved database path is recorded");
        assertEquals(tempDir.toAbsolutePath().resolve("nexusenchants.db"), file);
        assertTrue(Files.isRegularFile(file), "the SQLite driver creates the file on first connection");
        assertTrue(provider.isConnected());
        assertTrue(provider.describe().contains("journal=WAL"));
    }

    @Test
    @DisplayName("DB001/DB016: foreign keys and WAL are active")
    void pragmasAreActive() throws SQLException {
        SQLiteProvider provider = open();
        try (Connection connection = provider.getConnection()) {
            assertEquals(1, queryInt(connection, "PRAGMA foreign_keys"));
            assertEquals("wal", queryString(connection, "PRAGMA journal_mode").toLowerCase());
        }
    }

    @Test
    @DisplayName("DB015: closing the pool invalidates connections instead of leaking them")
    void closeReleasesConnections() {
        SQLiteProvider provider = open();
        assertTrue(provider.isConnected());
        provider.close();
        assertFalse(provider.isConnected());
        assertThrows(SQLException.class, provider::getConnection);
        // Closing twice is safe.
        provider.close();
    }

    @Test
    @DisplayName("DB005-DB008: migrations apply once, in order, and are checksum protected")
    void migrationsApplyOnce() throws SQLException {
        SQLiteProvider provider = open();
        MigrationManager migrations = new MigrationManager(LOGGER, DatabaseType.SQLITE, provider.databaseFile());

        assertEquals(4, migrations.discover().size());
        assertEquals(1, migrations.discover().get(0).version());
        assertEquals("initial schema", migrations.discover().get(0).description());

        try (Connection connection = provider.getConnection()) {
            assertEquals(4, migrations.migrate(connection));
            assertEquals(4, migrations.schemaVersion(connection));
            assertEquals(0, migrations.migrate(connection), "a second run must apply nothing");
            assertTrue(migrations.pending(connection).isEmpty());

            for (String table : new String[] {"managed_villagers", "managed_trades", "audit_logs",
                    "rarity_statistics"}) {
                assertTrue(tableExists(connection, table), "missing table " + table);
            }

            // DB008: a recorded checksum that no longer matches the file is fatal.
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE " + MigrationManager.MIGRATION_TABLE + " SET checksum = ? WHERE version = 1")) {
                statement.setString(1, "0".repeat(64));
                statement.executeUpdate();
            }
            DatabaseException mismatch = assertThrows(DatabaseException.class,
                    () -> migrations.migrate(connection));
            assertTrue(mismatch.getMessage().contains("checksum mismatch"));
        }
    }

    @Test
    @DisplayName("DB020: the SQLite file is backed up before a pending migration runs")
    void backupBeforeMigration() throws SQLException {
        SQLiteProvider provider = open();
        MigrationManager migrations = new MigrationManager(LOGGER, DatabaseType.SQLITE, provider.databaseFile());
        try (Connection connection = provider.getConnection()) {
            migrations.migrate(connection);
        }
        File[] backups = tempDir.toFile().listFiles((dir, name) -> name.endsWith(".backup"));
        assertNotNull(backups);
        assertTrue(backups.length >= 1, "a backup file is written before migrating an existing database");
    }

    @Test
    @DisplayName("DB010-DB012: constraints prevent duplicates and orphans")
    void constraintsAreEnforced() throws SQLException {
        SQLiteProvider provider = open();
        MigrationManager migrations = new MigrationManager(LOGGER, DatabaseType.SQLITE, provider.databaseFile());
        try (Connection connection = provider.getConnection()) {
            migrations.migrate(connection);
            String villager = UUID.randomUUID().toString();
            insertVillager(connection, villager);

            // DB011: duplicate villager UUID.
            assertThrows(SQLException.class, () -> insertVillager(connection, villager));

            // DB010: a trade for an unknown villager violates the foreign key.
            assertThrows(SQLException.class, () -> insertTrade(connection, UUID.randomUUID().toString(), "t1"));

            // DB012: duplicate trade key for the same villager.
            insertTrade(connection, villager, "t1");
            assertThrows(SQLException.class, () -> insertTrade(connection, villager, "t1"));
        }
    }

    @Test
    @DisplayName("DB017: values are bound as parameters, never concatenated")
    void parameterisedQueries() throws SQLException {
        SQLiteProvider provider = open();
        MigrationManager migrations = new MigrationManager(LOGGER, DatabaseType.SQLITE, provider.databaseFile());
        String hostile = "Robert'); DROP TABLE managed_villagers;--";
        try (Connection connection = provider.getConnection()) {
            migrations.migrate(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO managed_villagers (villager_uuid, world_uuid, world_name, profession,"
                            + " managed, first_registered, last_updated, schema_version)"
                            + " VALUES (?, ?, ?, ?, 1, 0, 0, 1)")) {
                statement.setString(1, hostile);
                statement.setString(2, "world");
                statement.setString(3, "world");
                statement.setString(4, "librarian");
                assertEquals(1, statement.executeUpdate());
            }
            assertTrue(tableExists(connection, "managed_villagers"), "the table must still exist");
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM managed_villagers WHERE villager_uuid = ?")) {
                statement.setString(1, hostile);
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(1, result.getInt(1), "the value is stored verbatim, not executed");
                }
            }
        }
    }

    @Test
    @DisplayName("DB009: a failed transaction is rolled back completely")
    void transactionRollback() throws SQLException {
        SQLiteProvider provider = open();
        MigrationManager migrations = new MigrationManager(LOGGER, DatabaseType.SQLITE, provider.databaseFile());
        try (Connection connection = provider.getConnection()) {
            migrations.migrate(connection);
            connection.setAutoCommit(false);
            insertVillager(connection, UUID.randomUUID().toString());
            connection.rollback();
            connection.setAutoCommit(true);
            assertEquals(0, queryInt(connection, "SELECT COUNT(*) FROM managed_villagers"));
        }
    }

    private static void insertVillager(Connection connection, String uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO managed_villagers (villager_uuid, world_uuid, world_name, profession,"
                        + " managed, first_registered, last_updated, schema_version)"
                        + " VALUES (?, 'world', 'world', 'librarian', 1, 0, 0, 1)")) {
            statement.setString(1, uuid);
            statement.executeUpdate();
        }
    }

    private static void insertTrade(Connection connection, String villagerUuid, String tradeKey)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO managed_trades (villager_uuid, trade_key, enchantment_key, enchantment_level,"
                        + " emerald_price, uses, max_uses, created_at, updated_at)"
                        + " VALUES (?, ?, 'MENDING', 1, 70, 0, 12, 0, 0)")) {
            statement.setString(1, villagerUuid);
            statement.setString(2, tradeKey);
            statement.executeUpdate();
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }

    private static int queryInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getInt(1) : -1;
        }
    }

    private static String queryString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getString(1) : "";
        }
    }
}
