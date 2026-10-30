package com.nexus.enchants.database;

import com.nexus.enchants.config.DatabaseType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Applies the SQL migrations that ship with the plugin.
 *
 * <p>Guarantees:</p>
 * <ul>
 *     <li>Migrations run in ascending version order, once, inside a transaction.</li>
 *     <li>A checksum is stored per version; a later change to an already applied
 *     file is reported as an error instead of being re-run.</li>
 *     <li>SQLite databases are copied before any pending migration is applied.</li>
 *     <li>A failure rolls the migration back and stops the sequence.</li>
 * </ul>
 */
public final class MigrationManager {

    /** Bootstrap table created by the runner itself. */
    public static final String MIGRATION_TABLE = "schema_migrations";

    private static final Pattern FILE_PATTERN = Pattern.compile("V(\\d+)__(.+)\\.sql");
    private static final String FOLDER = "db/migration/";
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Logger logger;
    private final DatabaseType type;
    private final Path sqliteFile;
    private final String resourceFolder;

    /**
     * Creates the manager.
     *
     * @param logger     logger for migration reporting
     * @param type       database type, which selects the migration folder
     * @param sqliteFile database file to back up before destructive work, {@code null} for MySQL
     */
    public MigrationManager(Logger logger, DatabaseType type, Path sqliteFile) {
        this.logger = logger;
        this.type = type;
        this.sqliteFile = sqliteFile;
        this.resourceFolder = FOLDER + type.migrationFolder();
    }

    /**
     * Returns the migration folder of this dialect.
     *
     * @return the classpath folder
     */
    public String resourceFolder() {
        return resourceFolder;
    }

    /**
     * Discovers every migration shipped for this dialect.
     *
     * @return migrations sorted by version
     * @throws DatabaseException when a resource cannot be read
     */
    public List<Migration> discover() {
        List<Migration> migrations = new ArrayList<>();
        for (String resource : listMigrationResources()) {
            String fileName = resource.substring(resource.lastIndexOf('/') + 1);
            Matcher matcher = FILE_PATTERN.matcher(fileName);
            if (!matcher.matches()) {
                logger.warning("Ignoring migration resource with an unexpected name: " + resource);
                continue;
            }
            int version;
            try {
                version = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException exception) {
                logger.warning("Ignoring migration with a non numeric version: " + resource);
                continue;
            }
            String description = matcher.group(2).replace('_', ' ');
            migrations.add(new Migration(version, description, resource, sha256(readResource(resource))));
        }
        migrations.sort(Comparator.comparingInt(Migration::version));
        return List.copyOf(migrations);
    }

    private List<String> listMigrationResources() {
        URL url = MigrationManager.class.getClassLoader().getResource(resourceFolder);
        if (url == null) {
            return List.of();
        }
        try {
            if ("file".equals(url.getProtocol())) {
                Path directory = Path.of(url.toURI());
                try (Stream<Path> stream = Files.list(directory)) {
                    return stream
                            .filter(path -> path.getFileName().toString().endsWith(".sql"))
                            .map(path -> resourceFolder + "/" + path.getFileName())
                            .sorted()
                            .toList();
                }
            }
            if ("jar".equals(url.getProtocol())) {
                JarURLConnection connection = (JarURLConnection) url.openConnection();
                connection.setUseCaches(false);
                try (JarFile jar = connection.getJarFile()) {
                    List<String> names = new ArrayList<>();
                    for (JarEntry entry : jar.stream().toList()) {
                        String name = entry.getName();
                        if (entry.isDirectory() || !name.startsWith(resourceFolder + "/") || !name.endsWith(".sql")) {
                            continue;
                        }
                        if (name.substring(resourceFolder.length() + 1).indexOf('/') >= 0) {
                            continue;
                        }
                        names.add(name);
                    }
                    names.sort(String::compareTo);
                    return names;
                }
            }
            return List.of();
        } catch (IOException | URISyntaxException exception) {
            throw new DatabaseException("Could not list migrations in " + resourceFolder, exception);
        }
    }

    private byte[] readResource(String resource) {
        try (InputStream stream = MigrationManager.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new DatabaseException("Missing migration resource " + resource);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = stream.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new DatabaseException("Could not read migration " + resource, exception);
        }
    }

    /**
     * Creates the migration bookkeeping table when it does not exist yet.
     *
     * @param connection open connection
     * @throws SQLException when the table cannot be created
     */
    public void ensureMigrationTable(Connection connection) throws SQLException {
        String sql = type == DatabaseType.MYSQL
                ? "CREATE TABLE IF NOT EXISTS " + MIGRATION_TABLE + " ("
                        + "version INT NOT NULL,"
                        + "description VARCHAR(255) NOT NULL,"
                        + "installed_at BIGINT NOT NULL,"
                        + "checksum CHAR(64) NOT NULL,"
                        + "PRIMARY KEY (version)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
                : "CREATE TABLE IF NOT EXISTS " + MIGRATION_TABLE + " ("
                        + "version INTEGER PRIMARY KEY,"
                        + "description TEXT NOT NULL,"
                        + "installed_at INTEGER NOT NULL,"
                        + "checksum TEXT NOT NULL"
                        + ")";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /**
     * Returns the migrations recorded in the database.
     *
     * @param connection open connection
     * @return applied migrations ordered by version
     * @throws SQLException when the table cannot be read
     */
    public List<AppliedMigration> applied(Connection connection) throws SQLException {
        String sql = "SELECT version, description, installed_at, checksum FROM " + MIGRATION_TABLE
                + " ORDER BY version ASC";
        List<AppliedMigration> applied = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                applied.add(new AppliedMigration(
                        result.getInt("version"),
                        result.getString("description"),
                        result.getLong("installed_at"),
                        result.getString("checksum")));
            }
        }
        return applied;
    }

    /**
     * Applies every pending migration.
     *
     * @param connection open connection
     * @return the number of applied migrations
     * @throws SQLException when a migration fails
     */
    public int migrate(Connection connection) throws SQLException {
        ensureMigrationTable(connection);
        List<Migration> discovered = discover();
        Map<Integer, AppliedMigration> applied = new LinkedHashMap<>();
        for (AppliedMigration migration : applied(connection)) {
            applied.put(migration.version(), migration);
        }

        for (Migration migration : discovered) {
            AppliedMigration record = applied.get(migration.version());
            if (record != null && !record.checksum().equals(migration.checksum())) {
                throw new DatabaseException("Migration V" + versionLabel(migration.version())
                        + " was modified after it was applied (checksum mismatch). "
                        + "Restore the released file and add a new migration instead.");
            }
        }

        List<Migration> pending = discovered.stream()
                .filter(migration -> !applied.containsKey(migration.version()))
                .toList();
        if (pending.isEmpty()) {
            logger.info("Database schema is up to date at version " + highestVersion(applied));
            return 0;
        }

        backupIfNeeded(pending);
        int appliedCount = 0;
        for (Migration migration : pending) {
            applyOne(connection, migration);
            appliedCount++;
        }
        logger.info("Applied " + appliedCount + " database migration(s); schema version is now "
                + pending.get(pending.size() - 1).version() + ".");
        return appliedCount;
    }

    private static int highestVersion(Map<Integer, AppliedMigration> applied) {
        int highest = 0;
        for (Integer version : applied.keySet()) {
            highest = Math.max(highest, version);
        }
        return highest;
    }

    /**
     * Returns the migrations that have not been applied yet.
     *
     * @param connection open connection
     * @return pending migrations ordered by version
     * @throws SQLException when the bookkeeping table cannot be read
     */
    public List<Migration> pending(Connection connection) throws SQLException {
        ensureMigrationTable(connection);
        List<AppliedMigration> applied = applied(connection);
        List<Migration> pending = new ArrayList<>();
        for (Migration migration : discover()) {
            boolean isApplied = applied.stream().anyMatch(record -> record.version() == migration.version());
            if (!isApplied) {
                pending.add(migration);
            }
        }
        return List.copyOf(pending);
    }

    private void applyOne(Connection connection, Migration migration) throws SQLException {
        String script;
        try (InputStream stream = MigrationManager.class.getClassLoader()
                .getResourceAsStream(migration.resource())) {
            if (stream == null) {
                throw new DatabaseException("Missing migration resource " + migration.resource());
            }
            script = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new DatabaseException("Could not read migration " + migration.resource(), exception);
        }

        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (Statement statement = connection.createStatement()) {
                for (String sql : splitStatements(script)) {
                    statement.execute(sql);
                }
            }
            String insert = "INSERT INTO " + MIGRATION_TABLE
                    + " (version, description, installed_at, checksum) VALUES (?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                statement.setInt(1, migration.version());
                statement.setString(2, migration.description());
                statement.setLong(3, System.currentTimeMillis());
                statement.setString(4, migration.checksum());
                statement.executeUpdate();
            }
            connection.commit();
            logger.info("Applied migration V" + versionLabel(migration.version())
                    + " (" + migration.description() + ")");
        } catch (SQLException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                logger.log(Level.SEVERE, "Rollback failed for migration " + migration.resource(), rollbackFailure);
            }
            throw exception;
        } finally {
            if (previousAutoCommit) {
                connection.setAutoCommit(true);
            }
        }
    }

    private void backupIfNeeded(List<Migration> pending) {
        if (type != DatabaseType.SQLITE || sqliteFile == null || !Files.isRegularFile(sqliteFile)) {
            return;
        }
        String stamp = BACKUP_STAMP.format(LocalDateTime.now());
        Path target = sqliteFile.resolveSibling(sqliteFile.getFileName() + "." + stamp + ".backup");
        try {
            Files.copy(sqliteFile, target, StandardCopyOption.COPY_ATTRIBUTES);
            logger.info("Backed up the SQLite database to " + target.getFileName()
                    + " before applying " + pending.size() + " migration(s).");
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Could not back up the SQLite database before migrating; "
                    + "continuing because each migration is transactional", exception);
        }
    }

    /**
     * Splits a SQL script into individual statements.
     *
     * <p>Line comments starting with {@code --} are removed, blank statements are
     * skipped and semicolons terminate a statement.</p>
     *
     * @param script full SQL script
     * @return executable statements
     */
    public static List<String> splitStatements(String script) {
        StringBuilder current = new StringBuilder();
        for (String rawLine : script.split("\\R")) {
            String line = rawLine.trim();
            if (line.startsWith("--") || line.isEmpty()) {
                continue;
            }
            current.append(line).append(' ');
        }
        List<String> statements = new ArrayList<>();
        for (String chunk : current.toString().split(";")) {
            String statement = chunk.trim();
            if (!statement.isEmpty()) {
                statements.add(statement);
            }
        }
        return statements;
    }

    /**
     * Computes the SHA-256 checksum of a migration file.
     *
     * @param content file contents
     * @return lower case hex checksum
     */
    public static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable on this runtime", exception);
        }
    }

    /**
     * Returns the highest applied schema version.
     *
     * @param connection open connection
     * @return the version, {@code 0} when no migration has been applied
     * @throws SQLException when the table cannot be read
     */
    public int schemaVersion(Connection connection) throws SQLException {
        return applied(connection).stream().mapToInt(AppliedMigration::version).max().orElse(0);
    }

    /**
     * Returns the migration recorded for a version.
     *
     * @param connection open connection
     * @param version    schema version
     * @return the applied migration, if present
     * @throws SQLException when the table cannot be read
     */
    public Optional<AppliedMigration> findApplied(Connection connection, int version) throws SQLException {
        return applied(connection).stream().filter(record -> record.version() == version).findFirst();
    }

    private static String versionLabel(int version) {
        return String.format("%03d", version);
    }
}
