package com.nexus.enchants.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexus.enchants.config.DatabaseType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Migration metadata tests that do not need a database connection.
 */
class MigrationManagerTest {

    private static final Logger LOGGER = Logger.getLogger("NexusEnchantsTest");

    private static MigrationManager sqlite() {
        return new MigrationManager(LOGGER, DatabaseType.SQLITE, null);
    }

    @Test
    @DisplayName("V001-V004 are discovered in ascending order for both dialects")
    void discovery() {
        List<Migration> sqlite = sqlite().discover();
        assertEquals(4, sqlite.size());
        for (int index = 0; index < sqlite.size(); index++) {
            assertEquals(index + 1, sqlite.get(index).version());
        }
        assertEquals("initial schema", sqlite.get(0).description());
        assertEquals("villager trade tracking", sqlite.get(1).description());
        assertEquals("audit logs", sqlite.get(2).description());
        assertEquals("rarity statistics", sqlite.get(3).description());
        assertTrue(sqlite.get(0).resource().startsWith("db/migration/sqlite/"));

        List<Migration> mysql = new MigrationManager(LOGGER, DatabaseType.MYSQL, null).discover();
        assertEquals(4, mysql.size());
        assertTrue(mysql.get(0).resource().startsWith("db/migration/mysql/"));
        assertEquals("db/migration/sqlite", sqlite().resourceFolder());
        assertEquals("db/migration/mysql",
                new MigrationManager(LOGGER, DatabaseType.MYSQL, null).resourceFolder());
    }

    @Test
    @DisplayName("Every migration has a full SHA-256 checksum")
    void checksums() {
        for (Migration migration : sqlite().discover()) {
            assertEquals(64, migration.checksum().length(), migration.resource());
            assertTrue(migration.checksum().matches("[0-9a-f]{64}"), migration.resource());
        }
        assertNotEquals(sqlite().discover().get(0).checksum(), sqlite().discover().get(1).checksum());
    }

    @Test
    @DisplayName("Checksums are stable and content sensitive")
    void checksumFunction() {
        byte[] content = "CREATE TABLE t (id INTEGER);".getBytes(StandardCharsets.UTF_8);
        String first = MigrationManager.sha256(content);
        String second = MigrationManager.sha256("CREATE TABLE t (id INTEGER);"
                .getBytes(StandardCharsets.UTF_8));
        assertEquals(first, second);
        assertNotEquals(first, MigrationManager.sha256("CREATE TABLE u (id INTEGER);"
                .getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Scripts are split on semicolons and line comments are removed")
    void splitStatements() {
        String script = "-- a comment\n"
                + "CREATE TABLE a (id INTEGER);\n"
                + "CREATE INDEX i ON a (id);\n"
                + "\n"
                + "-- trailing comment\n";
        List<String> statements = MigrationManager.splitStatements(script);
        assertEquals(2, statements.size());
        assertTrue(statements.get(0).startsWith("CREATE TABLE a"));
        assertTrue(statements.get(1).startsWith("CREATE INDEX i"));
        assertFalse(statements.get(0).contains("--"));
    }

    @Test
    @DisplayName("An empty script produces no statements")
    void emptyScript() {
        assertTrue(MigrationManager.splitStatements("").isEmpty());
        assertTrue(MigrationManager.splitStatements("-- only a comment").isEmpty());
        assertTrue(MigrationManager.splitStatements("   \n\n ").isEmpty());
    }

    @Test
    @DisplayName("Every shipped statement ends with a terminator or a body")
    void shippedScriptsSplitCleanly() {
        List<String> statements = new ArrayList<>();
        for (Migration migration : sqlite().discover()) {
            List<String> split = MigrationManager.splitStatements(read(migration));
            assertFalse(split.isEmpty(), migration.resource());
            assertTrue(split.stream().noneMatch(String::isBlank), migration.resource());
            statements.addAll(split);
        }
        assertTrue(statements.size() >= 8, "expected the tables and their indexes");
    }

    private static String read(Migration migration) {
        try (var stream = MigrationManagerTest.class.getClassLoader()
                .getResourceAsStream(migration.resource())) {
            assertTrue(stream != null, "missing resource " + migration.resource());
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
