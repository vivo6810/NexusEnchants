package com.nexus.enchants.database;

import com.nexus.enchants.config.DatabaseConfig;
import com.nexus.enchants.config.DatabaseType;
import com.nexus.enchants.config.PluginSettings;
import com.nexus.enchants.database.repositories.AuditLogRepository;
import com.nexus.enchants.database.repositories.StatisticsRepository;
import com.nexus.enchants.database.repositories.TradeRepository;
import com.nexus.enchants.database.repositories.VillagerRepository;
import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the database life cycle and hands out pooled connections.
 *
 * <p>Initialization, every query and shutdown run on a worker thread from
 * {@link AsyncDatabaseExecutor}. The main thread is only involved through the
 * {@code mainThread} callback, which is how readiness is reported back to code
 * that needs to touch Bukkit objects.</p>
 *
 * <p>When the database is unavailable the manager never throws into gameplay
 * code: {@link #isReady()} is {@code false}, {@link #connection()} rejects the
 * call and the affected persistence features degrade instead of charging a
 * player a wrong price.</p>
 */
public final class DatabaseManager implements AutoCloseable {

    private final DatabaseConfig config;
    private final File dataFolder;
    private final Logger logger;
    private final Consumer<Runnable> mainThread;
    private final AsyncDatabaseExecutor executor;
    private final CopyOnWriteArrayList<Runnable> readyListeners = new CopyOnWriteArrayList<>();
    private final AtomicLong operationFailures = new AtomicLong();
    private final VillagerRepository villagerRepository = new VillagerRepository(this);
    private final TradeRepository tradeRepository = new TradeRepository(this);
    private final AuditLogRepository auditLogRepository = new AuditLogRepository(this);
    private final StatisticsRepository statisticsRepository = new StatisticsRepository(this);

    private volatile DatabaseProvider provider;
    private volatile MigrationManager migrationManager;
    private volatile SqlDialect dialect = SqlDialect.SQLITE;
    private volatile boolean ready;
    private volatile String lastFailure;
    private volatile List<AppliedMigration> appliedMigrations = List.of();

    /**
     * Creates the manager.
     *
     * @param config     database configuration
     * @param settings   general settings, used for the worker pool bounds
     * @param dataFolder plugin data folder
     * @param logger     logger
     * @param mainThread callback that schedules work on the server thread
     */
    public DatabaseManager(
            DatabaseConfig config,
            PluginSettings settings,
            File dataFolder,
            Logger logger,
            Consumer<Runnable> mainThread) {
        this.config = Objects.requireNonNull(config, "config");
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.executor = new AsyncDatabaseExecutor(settings.asyncWorkers(), settings.asyncQueueCapacity(), logger);
        this.dialect = SqlDialect.of(config.type());
    }

    /**
     * Starts initialization on a worker thread.
     *
     * @return {@code true} when the task was accepted by the executor
     */
    public boolean initializeAsync() {
        return executor.submit("initialize", () -> {
            try {
                initializeBlocking();
            } catch (RuntimeException exception) {
                ready = false;
                lastFailure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
                logger.log(Level.SEVERE, "Database initialization failed; persistence is disabled "
                        + "while gameplay control continues", exception);
            } finally {
                notifyReady();
            }
        });
    }

    /**
     * Opens the pool, applies pending migrations and reads the migration history.
     *
     * <p>Blocking. Call it from a worker thread in production and directly from
     * tests.</p>
     *
     * @throws DatabaseException when the database cannot be opened or migrated
     */
    public void initializeBlocking() {
        DatabaseProvider newProvider = createProvider();
        newProvider.initialize();
        Path sqlitePath = newProvider instanceof SQLiteProvider sqlite ? sqlite.databaseFile() : null;
        MigrationManager manager = new MigrationManager(logger, newProvider.type(), sqlitePath);
        try (Connection connection = newProvider.getConnection()) {
            manager.migrate(connection);
            this.appliedMigrations = List.copyOf(manager.applied(connection));
        } catch (SQLException | DatabaseException exception) {
            newProvider.close();
            throw exception instanceof DatabaseException databaseException
                    ? databaseException
                    : new DatabaseException("Migrations failed", exception);
        }
        this.provider = newProvider;
        this.migrationManager = manager;
        this.dialect = SqlDialect.of(newProvider.type());
        this.ready = true;
        this.lastFailure = null;
        logger.info("Database ready: " + newProvider.describe() + ", schema version "
                + schemaVersion() + ", migrations " + appliedMigrations.size());
    }

    private DatabaseProvider createProvider() {
        if (config.type() == DatabaseType.MYSQL) {
            if (config.mysql() == null) {
                throw new DatabaseException("MYSQL is configured but the mysql block is missing");
            }
            return new MySQLProvider(config.mysql(), logger);
        }
        return new SQLiteProvider(config, dataFolder, logger);
    }

    private void notifyReady() {
        mainThread.accept(() -> {
            for (Runnable listener : readyListeners) {
                try {
                    listener.run();
                } catch (RuntimeException exception) {
                    logger.log(Level.WARNING, "A database readiness listener failed", exception);
                }
            }
        });
    }

    /**
     * Runs an action on the server thread once initialization has finished.
     *
     * <p>The action runs immediately when the database is already ready.</p>
     *
     * @param action action to run; it must check {@link #isReady()} before querying
     */
    public void whenReady(Runnable action) {
        if (ready) {
            mainThread.accept(action);
            return;
        }
        readyListeners.add(action);
    }

    /**
     * Returns the configured database type.
     *
     * @return the database type
     */
    public DatabaseType type() {
        return config.type();
    }

    /**
     * Returns whether the database is open and migrated.
     *
     * @return {@code true} when queries may run
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * Returns the last initialization failure, if any.
     *
     * @return the failure description, or {@code null}
     */
    public String lastFailure() {
        return lastFailure;
    }

    /**
     * Returns the number of failed operations since startup.
     *
     * @return the failure count
     */
    public long operationFailures() {
        return operationFailures.get();
    }

    /**
     * Borrows a pooled connection.
     *
     * @return a pooled connection owned by the caller
     * @throws SQLException                  when no connection is available
     * @throws DatabaseUnavailableException  when the database is not ready
     */
    public Connection connection() throws SQLException {
        DatabaseProvider current = provider;
        if (!ready || current == null) {
            throw new DatabaseUnavailableException(lastFailure == null ? "not initialized yet" : lastFailure);
        }
        try {
            return current.getConnection();
        } catch (SQLException exception) {
            operationFailures.incrementAndGet();
            throw exception;
        }
    }

    /**
     * Returns the dialect used by the active provider.
     *
     * @return the SQL dialect
     */
    public SqlDialect dialect() {
        return dialect;
    }

    /**
     * Returns the active provider, or {@code null} before initialization.
     *
     * @return the provider
     */
    public DatabaseProvider provider() {
        return provider;
    }

    /**
     * Returns the migration history read during initialization.
     *
     * @return immutable list ordered by version
     */
    public List<AppliedMigration> appliedMigrations() {
        return appliedMigrations;
    }

    /**
     * Returns the applied schema version.
     *
     * @return the version, {@code 0} before initialization
     */
    public int schemaVersion() {
        return appliedMigrations.stream().mapToInt(AppliedMigration::version).max().orElse(0);
    }

    /**
     * Returns the migration manager.
     *
     * @return the manager, or {@code null} before initialization
     */
    public MigrationManager migrationManager() {
        return migrationManager;
    }

    /**
     * Returns the bounded task executor used for all persistence work.
     *
     * @return the executor
     */
    public AsyncDatabaseExecutor executor() {
        return executor;
    }

    /**
     * Returns the villager repository.
     *
     * @return the repository
     */
    public VillagerRepository villagers() {
        return villagerRepository;
    }

    /**
     * Returns the trade repository.
     *
     * @return the repository
     */
    public TradeRepository trades() {
        return tradeRepository;
    }

    /**
     * Returns the audit repository.
     *
     * @return the repository
     */
    public AuditLogRepository auditLogs() {
        return auditLogRepository;
    }

    /**
     * Returns the statistics repository.
     *
     * @return the repository
     */
    public StatisticsRepository statistics() {
        return statisticsRepository;
    }

    /**
     * Returns a short status line for diagnostics.
     *
     * @return the status summary
     */
    public String statusSummary() {
        DatabaseProvider current = provider;
        String description = current == null ? config.type() + " (not connected)" : current.describe();
        return description + ", ready=" + ready + ", schema=" + schemaVersion()
                + ", queued=" + executor.queued() + ", failed=" + executor.failedCount()
                + ", rejected=" + executor.rejectedCount();
    }

    @Override
    public void close() {
        ready = false;
        executor.close(5_000L);
        DatabaseProvider current = provider;
        provider = null;
        if (current != null) {
            current.close();
            logger.info("Database connection closed.");
        }
    }
}
