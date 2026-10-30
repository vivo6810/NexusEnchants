package com.nexus.enchants.database;

import com.nexus.enchants.config.DatabaseConfig;
import com.nexus.enchants.config.DatabaseType;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Embedded SQLite provider.
 *
 * <p>Foreign keys and the busy timeout are set per connection through driver
 * properties, and the journal mode is verified after the pool is built. The
 * database file is only created when the plugin needs it, which is during
 * startup, never during class loading.</p>
 */
public final class SQLiteProvider extends AbstractHikariProvider {

    private final DatabaseConfig config;
    private final File dataFolder;
    private final Logger logger;
    private Path databaseFile;

    /**
     * Creates the provider.
     *
     * @param config     database configuration
     * @param dataFolder plugin data folder
     * @param logger     logger
     */
    public SQLiteProvider(DatabaseConfig config, File dataFolder, Logger logger) {
        super(DatabaseType.SQLITE, logger, "NexusEnchants-SQLite");
        this.config = Objects.requireNonNull(config, "config");
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    protected void configure(HikariConfig hikari) {
        File folder = dataFolder;
        if (!folder.exists() && !folder.mkdirs()) {
            throw new DatabaseException("Could not create the plugin data folder " + folder.getAbsolutePath());
        }
        this.databaseFile = new File(folder, config.sqliteFile()).toPath().toAbsolutePath();
        Path parent = databaseFile.getParent();
        if (parent != null && !Files.exists(parent)) {
            try {
                Files.createDirectories(parent);
            } catch (IOException exception) {
                throw new DatabaseException("Could not create the directory " + parent, exception);
            }
        }
        hikari.setJdbcUrl("jdbc:sqlite:" + databaseFile);
        hikari.setMaximumPoolSize(Math.max(1, config.poolSize()));
        hikari.setMinimumIdle(1);
        hikari.addDataSourceProperty("foreign_keys", Boolean.toString(config.foreignKeys()));
        hikari.addDataSourceProperty("busy_timeout", Integer.toString(config.busyTimeoutMs()));
        hikari.addDataSourceProperty("journal_mode", config.journalMode().toUpperCase(Locale.ROOT));
        if (config.foreignKeys()) {
            // Drivers that ignore the property still get enforcement through this pragma.
            hikari.setConnectionInitSql("PRAGMA foreign_keys = ON");
        }
    }

    @Override
    protected void verify(Connection connection) throws SQLException {
        if (!config.foreignKeys()) {
            return;
        }
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA foreign_keys")) {
            if (result.next() && result.getInt(1) == 0) {
                logger.warning("SQLite foreign-key enforcement is not active on this connection.");
            }
        }
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA journal_mode")) {
            if (result.next()) {
                String mode = result.getString(1);
                if (mode != null && !mode.equalsIgnoreCase(config.journalMode())) {
                    logger.warning("SQLite journal mode is " + mode + " instead of the configured "
                            + config.journalMode() + ".");
                }
            }
        }
    }

    @Override
    protected void afterPoolCreated(HikariDataSource source) {
        logger.fine("SQLite pool ready for " + databaseFile);
    }

    @Override
    public String describe() {
        return "SQLite[" + (databaseFile == null ? config.sqliteFile() : databaseFile.toString())
                + ", pool=" + config.poolSize() + ", journal=" + config.journalMode() + "]";
    }

    /**
     * Returns the absolute database file path, or {@code null} before initialization.
     *
     * @return the database file path
     */
    public Path databaseFile() {
        return databaseFile;
    }
}
