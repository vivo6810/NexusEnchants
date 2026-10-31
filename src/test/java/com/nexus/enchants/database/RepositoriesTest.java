package com.nexus.enchants.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexus.enchants.config.PluginSettings;
import com.nexus.enchants.database.repositories.AuditRecord;
import com.nexus.enchants.storage.StatisticKey;
import com.nexus.enchants.storage.TradeRecord;
import com.nexus.enchants.storage.VillagerRecord;
import java.io.File;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Repository round-trips against a real, migrated temporary SQLite database.
 *
 * <p>These tests are the first direct coverage of villager, trade, audit and
 * statistics persistence; previously only migrations and the provider itself
 * were exercised.</p>
 */
class RepositoriesTest {

    private static final Logger LOGGER = Logger.getLogger("NexusEnchantsTest");
    private static final long T0 = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();

    @TempDir
    File dataFolder;

    private DatabaseManager manager;

    @AfterEach
    void closeManager() {
        if (manager != null) {
            manager.close();
            manager = null;
        }
    }

    private DatabaseManager open() {
        manager = new DatabaseManager(
                DatabaseConfig.defaults(),
                PluginSettings.defaults(),
                dataFolder,
                LOGGER,
                Runnable::run);
        manager.initializeBlocking();
        return manager;
    }

    private VillagerRecord villager(UUID id) {
        return VillagerRecord.create(id, UUID.randomUUID(), "world", "minecraft:librarian", T0);
    }

    @Test
    @DisplayName("A registered villager is found again and preserved on re-upsert")
    void villagerRoundTrip() throws SQLException {
        DatabaseManager db = open();
        UUID id = UUID.randomUUID();

        db.villagers().upsert(villager(id));
        VillagerRecord stored = db.villagers().find(id).orElseThrow();
        assertEquals("minecraft:librarian", stored.profession());
        assertTrue(stored.managed());
        assertEquals(T0, stored.firstRegistered());

        // Re-registration must keep the original registration timestamp.
        db.villagers().upsert(villager(id).touch(T0 + 60_000L)
                .touch(T0 + 130_000L));
        VillagerRecord again = db.villagers().find(id).orElseThrow();
        assertEquals(T0, again.firstRegistered());
        assertEquals(T0 + 130_000L, again.lastUpdated());
        assertEquals(1L, db.villagers().count());

        assertTrue(db.villagers().delete(id));
        assertFalse(db.villagers().delete(id));
        assertTrue(db.villagers().find(id).isEmpty());
        assertEquals(0L, db.villagers().count());
    }

    @Test
    @DisplayName("An unknown villager is not found")
    void unknownVillager() throws SQLException {
        DatabaseManager db = open();
        assertTrue(db.villagers().find(UUID.randomUUID()).isEmpty());
        assertEquals(0L, db.villagers().count());
    }

    @Test
    @DisplayName("Trades are stored, updated by trade key and listed per villager")
    void tradeRoundTrip() throws SQLException {
        DatabaseManager db = open();
        UUID villager = UUID.randomUUID();
        db.villagers().upsert(villager(villager));

        TradeRecord mending = TradeRecord.create(
                villager, "trades/mending", "minecraft:mending", 1, 70, 0, 10, T0);
        db.trades().upsert(mending);
        db.trades().upsert(TradeRecord.create(
                villager, "trades/unbreaking", "minecraft:unbreaking", 3, 45, 0, 10, T0));
        assertEquals(2L, db.trades().count());

        // Rewriting the same trade key must not create a second row.
        db.trades().upsert(new TradeRecord(
                villager, "trades/mending", "minecraft:mending", 1, 72, 1, 10, T0, T0 + 10_000L));
        assertEquals(2L, db.trades().count());

        List<TradeRecord> stored = db.trades().findByVillager(villager);
        assertEquals(2, stored.size());
        assertEquals(1, stored.stream().filter(r -> r.tradeKey().equals("trades/mending")).count());
        assertEquals(72, stored.stream().filter(r -> r.tradeKey().equals("trades/mending"))
                .findFirst().orElseThrow().emeraldPrice());

        assertEquals(2, db.trades().deleteByVillager(villager));
        assertEquals(0L, db.trades().count());
    }

    @Test
    @DisplayName("Audit records are inserted, counted and purged by age")
    void auditRoundTrip() throws SQLException {
        DatabaseManager db = open();
        AuditRecord admin = new AuditRecord(
                "price.set", UUID.randomUUID(), null, "minecraft:mending", 1, null, "70",
                "via /vbe setprice", T0);
        db.auditLogs().insert(admin);
        db.auditLogs().insert(new AuditRecord(
                "reload", UUID.randomUUID(), null, null, null, null, null, "config reload", T0 + 1000L));

        assertEquals(2L, db.auditLogs().count());

        // Strictly-older-than semantics: only the T0 record is under the cutoff.
        db.auditLogs().deleteOlderThan(T0 + 500L);
        assertEquals(1L, db.auditLogs().count());

        db.auditLogs().deleteOlderThan(T0 + 500_000L);
        assertEquals(0L, db.auditLogs().count());
    }

    @Test
    @DisplayName("Statistics counters accumulate per key and sum up")
    void statisticsRoundTrip() throws SQLException {
        DatabaseManager db = open();
        StatisticKey mending = new StatisticKey("villager", "minecraft:mending", 1);

        assertEquals(1, db.statistics().addCounts(Map.of(mending, 42L), T0));
        assertEquals(1, db.statistics().addCounts(Map.of(mending, 8L), T0 + 1000L));

        assertEquals(50L, db.statistics().totalSelections());
        assertEquals(1L, db.statistics().rowCount());

        // A second key adds a second row with its own counter.
        assertEquals(1, db.statistics().addCounts(
                Map.of(new StatisticKey("villager", "minecraft:unbreaking", 3), 3L), T0 + 2000L));
        assertEquals(53L, db.statistics().totalSelections());
        assertEquals(2L, db.statistics().rowCount());
        assertEquals(0, db.statistics().addCounts(Map.of(), T0));
    }

    @Test
    @DisplayName("The schema version and migration history are read back")
    void migrationHistory() {
        DatabaseManager db = open();
        assertTrue(db.isReady());
        assertEquals(4, db.schemaVersion());
        assertEquals(4, db.appliedMigrations().size());
        assertEquals(DatabaseType.SQLITE, db.type());
        assertTrue(db.appliedMigrations().stream().noneMatch(m -> m.version() > 4));
    }
}
