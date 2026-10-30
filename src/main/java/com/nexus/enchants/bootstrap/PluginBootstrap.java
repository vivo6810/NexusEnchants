package com.nexus.enchants.bootstrap;

import com.nexus.enchants.NexusEnchants;
import com.nexus.enchants.api.NexusEnchantsApi;
import com.nexus.enchants.api.NexusEnchantsApiImpl;
import com.nexus.enchants.commands.AdminCommands;
import com.nexus.enchants.commands.CommandContext;
import com.nexus.enchants.commands.CommandDispatcher;
import com.nexus.enchants.commands.DiagnosticsCommands;
import com.nexus.enchants.commands.HelpCommand;
import com.nexus.enchants.commands.PricingCommands;
import com.nexus.enchants.commands.RarityCommands;
import com.nexus.enchants.commands.ReloadReport;
import com.nexus.enchants.commands.SubCommand;
import com.nexus.enchants.config.ConfigIssue;
import com.nexus.enchants.config.ConfigManager;
import com.nexus.enchants.config.ConfigSnapshot;
import com.nexus.enchants.database.DatabaseManager;
import com.nexus.enchants.diagnostics.DiagnosticsService;
import com.nexus.enchants.economy.BookPricingManager;
import com.nexus.enchants.enchanting.CombinationManager;
import com.nexus.enchants.enchanting.EnchantingManager;
import com.nexus.enchants.enchanting.EnchantingResultManager;
import com.nexus.enchants.enchanting.EnchantmentLevelManager;
import com.nexus.enchants.enchanting.EnchantmentWeightManager;
import com.nexus.enchants.sources.EnchantmentSourceManager;
import com.nexus.enchants.sources.SourceRestrictionListener;
import com.nexus.enchants.storage.RarityStatisticsStore;
import com.nexus.enchants.storage.StatisticKey;
import com.nexus.enchants.storage.TradeCache;
import com.nexus.enchants.villager.TradeGenerationManager;
import com.nexus.enchants.villager.VillagerMigrationManager;
import com.nexus.enchants.villager.VillagerRarityManager;
import com.nexus.enchants.villager.VillagerTradeManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitTask;

/**
 * Creates, wires and tears down every NexusEnchants service.
 *
 * <p>Startup order matters and is fixed here: configuration first, then the pure
 * services, then the database (asynchronously), then the tasks that need a live
 * connection. No service is created twice and nothing is re-created on reload:
 * services read the current configuration snapshot through a supplier, so a
 * reload is atomic from their point of view.</p>
 */
public final class PluginBootstrap {

    private final NexusEnchants plugin;
    private final Logger logger;
    private final ServiceRegistry services = new ServiceRegistry();

    private ConfigManager configManager;
    private TradeCache cache;
    private RarityStatisticsStore statistics;
    private EnchantmentSourceManager sources;
    private EnchantmentLevelManager levels;
    private EnchantmentWeightManager enchantingWeights;
    private VillagerRarityManager villagerRarity;
    private TradeGenerationManager generation;
    private BookPricingManager pricing;
    private DatabaseManager database;
    private VillagerTradeManager trades;
    private VillagerMigrationManager migration;
    private CombinationManager combinations;
    private EnchantingManager enchanting;
    private SourceRestrictionListener sourceListener;
    private DiagnosticsService diagnostics;
    private NexusEnchantsApiImpl api;
    private BukkitTask statisticsTask;
    private BukkitTask purgeTask;
    private boolean started;

    /**
     * Creates the bootstrap.
     *
     * @param plugin owning plugin
     */
    public PluginBootstrap(NexusEnchants plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
    }

    /**
     * Starts the plugin.
     *
     * @throws IllegalStateException when a required command or configuration is missing
     */
    public void start() {
        if (started) {
            return;
        }
        configManager = new ConfigManager(plugin, logger);
        ConfigSnapshot initial = configManager.reload();
        Supplier<ConfigSnapshot> snapshot = configManager::current;

        cache = new TradeCache();
        statistics = new RarityStatisticsStore(initial.settings().statisticsEnabled());
        sources = new EnchantmentSourceManager(snapshot);
        levels = new EnchantmentLevelManager(() -> configManager.current().levels());
        enchantingWeights = new EnchantmentWeightManager(snapshot, sources);
        villagerRarity = new VillagerRarityManager(snapshot, sources, levels, new Random());
        generation = new TradeGenerationManager();
        pricing = new BookPricingManager(snapshot);
        combinations = new CombinationManager(snapshot, enchantingWeights, levels);
        enchanting = new EnchantingManager(snapshot, combinations, new EnchantingResultManager(), sources,
                statistics, logger, new Random());
        sourceListener = new SourceRestrictionListener(snapshot, sources);

        database = new DatabaseManager(initial.database(), initial.settings(), plugin.getDataFolder(), logger,
                this::runOnMainThread);
        trades = new VillagerTradeManager(plugin, snapshot, sources, villagerRarity, generation, pricing, cache,
                statistics, database, logger);
        migration = new VillagerMigrationManager(plugin, trades, sources, logger);
        diagnostics = new DiagnosticsService(snapshot, database, cache, statistics, enchanting,
                plugin.pluginVersion());
        api = new NexusEnchantsApiImpl(snapshot, pricing, villagerRarity, combinations, sources, cache,
                this::reload, this::runOnMainThread);

        registerServices();
        registerListeners();
        registerCommands();
        registerApi();

        database.initializeAsync();
        database.whenReady(() -> {
            if (!plugin.isEnabled()) {
                return;
            }
            migration.start();
        });

        scheduleTasks();
        started = true;
        logStartupSummary(initial);
    }

    /**
     * Stops the plugin and releases every resource.
     */
    public void stop() {
        started = false;
        cancel(statisticsTask);
        cancel(purgeTask);
        statisticsTask = null;
        purgeTask = null;
        if (migration != null) {
            migration.stop();
        }
        flushStatistics();
        if (database != null) {
            database.close();
        }
        services.clear();
        logger.info("NexusEnchants stopped.");
    }

    /**
     * Reloads the configuration and re-applies it to the world.
     *
     * @return the reload outcome
     */
    public ReloadReport reload() {
        if (!started) {
            return new ReloadReport(false, 1, 0, List.of("The plugin is not running."));
        }
        ConfigSnapshot snapshot;
        try {
            snapshot = configManager.reload();
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Configuration reload failed", exception);
            return new ReloadReport(false, 1, 0, List.of(exception.getMessage()));
        }
        rescheduleStatistics(snapshot);
        migration.stop();
        migration.start();
        List<String> problems = new ArrayList<>();
        for (ConfigIssue issue : snapshot.errors()) {
            problems.add(issue.format());
        }
        return new ReloadReport(snapshot.errors().isEmpty(), snapshot.errors().size(),
                snapshot.warnings().size(), problems);
    }

    private void registerServices() {
        services.register(ConfigManager.class, configManager);
        services.register(DatabaseManager.class, database);
        services.register(TradeCache.class, cache);
        services.register(RarityStatisticsStore.class, statistics);
        services.register(EnchantmentSourceManager.class, sources);
        services.register(EnchantmentLevelManager.class, levels);
        services.register(EnchantmentWeightManager.class, enchantingWeights);
        services.register(VillagerRarityManager.class, villagerRarity);
        services.register(BookPricingManager.class, pricing);
        services.register(CombinationManager.class, combinations);
        services.register(EnchantingManager.class, enchanting);
        services.register(VillagerTradeManager.class, trades);
        services.register(DiagnosticsService.class, diagnostics);
        services.register(NexusEnchantsApi.class, api);
    }

    private void registerListeners() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(trades, plugin);
        pluginManager.registerEvents(enchanting, plugin);
        pluginManager.registerEvents(sourceListener, plugin);
    }

    private void registerCommands() {
        List<SubCommand> all = new ArrayList<>();
        all.addAll(new AdminCommands().commands());
        all.addAll(new PricingCommands().commands());
        all.addAll(new RarityCommands().commands());
        all.addAll(new DiagnosticsCommands().commands());
        all.add(new HelpCommand(List.copyOf(all)));

        CommandDispatcher dispatcher = new CommandDispatcher(commandContext(), all);
        PluginCommand command = plugin.getCommand("vbe");
        if (command == null) {
            throw new IllegalStateException("plugin.yml does not declare the 'vbe' command");
        }
        command.setExecutor(dispatcher);
        command.setTabCompleter(dispatcher);
    }

    private void registerApi() {
        try {
            plugin.getServer().getServicesManager().register(NexusEnchantsApi.class, api, plugin,
                    ServicePriority.Normal);
        } catch (RuntimeException exception) {
            // A minimal server implementation may not support the service manager.
            logger.log(Level.WARNING, "Could not register the NexusEnchants API service", exception);
        }
    }

    private CommandContext commandContext() {
        return new CommandContext(
                plugin,
                configManager,
                database,
                cache,
                statistics,
                pricing,
                villagerRarity,
                enchantingWeights,
                levels,
                combinations,
                enchanting,
                diagnostics,
                this::reload);
    }

    private void scheduleTasks() {
        statisticsTask = scheduleStatistics(() -> configManager.current().settings().statisticsFlushSeconds());
        try {
            long period = 20L * 60L;
            purgeTask = plugin.getServer().getScheduler()
                    .runTaskTimer(plugin, enchanting::purgeExpired, period, period);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Could not schedule the plan cache purge task", exception);
        }
    }

    private BukkitTask scheduleStatistics(Supplier<Integer> intervalSeconds) {
        if (statisticsTask != null) {
            statisticsTask.cancel();
            statisticsTask = null;
        }
        long period = 20L * Math.max(10, intervalSeconds.get());
        try {
            return plugin.getServer().getScheduler()
                    .runTaskTimerAsynchronously(plugin, this::flushStatistics, period, period);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Could not schedule the statistics flush task", exception);
            return null;
        }
    }

    private void rescheduleStatistics(ConfigSnapshot snapshot) {
        cancel(statisticsTask);
        statisticsTask = scheduleStatistics(() -> snapshot.settings().statisticsFlushSeconds());
    }

    private static void cancel(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Drains the in-memory statistics and writes them with one batch.
     *
     * <p>Runs on a worker thread; the actual JDBC call is handed to the database
     * executor so the queue stays bounded.</p>
     */
    private void flushStatistics() {
        if (!statistics.enabled() || !database.isReady()) {
            return;
        }
        Map<StatisticKey, Long> drained = statistics.drain();
        if (drained.isEmpty()) {
            return;
        }
        boolean accepted = database.executor().submitSql("flush-statistics",
                () -> database.statistics().addCounts(drained, System.currentTimeMillis()));
        if (!accepted) {
            logger.fine("Statistics flush rejected because the database queue is full; counts stay in memory.");
        }
    }

    private void runOnMainThread(Runnable task) {
        if (!plugin.isEnabled()) {
            logger.fine("Dropping a main-thread task because the plugin is disabling.");
            return;
        }
        try {
            plugin.getServer().getScheduler().runTask(plugin, task);
        } catch (RuntimeException exception) {
            logger.log(Level.FINE, "Could not schedule a main-thread task", exception);
        }
    }

    private void logStartupSummary(ConfigSnapshot snapshot) {
        logger.info("NexusEnchants " + plugin.pluginVersion() + " enabled. Control: "
                + (snapshot.settings().enabled() ? "on" : "off")
                + ", pricing: " + snapshot.pricing().prices().size() + " enchantment(s)"
                + ", librarian rarity: " + snapshot.villagerRarity().activationLabel()
                + ", enchanting rarity: " + snapshot.enchanting().activationLabel()
                + ", sources enforced: " + snapshot.sources().enabled()
                + ", discounts: " + snapshot.discounts().policy());
        if (!snapshot.errors().isEmpty()) {
            logger.warning("Configuration has " + snapshot.errors().size()
                    + " error(s). Affected features fall back to documented defaults; run /vbe diagnostics.");
        }
        if (snapshot.settings().verboseStartup()) {
            for (String limitation : DiagnosticsService.knownLimitations()) {
                logger.info("Limitation: " + limitation);
            }
        }
    }

    /**
     * Returns the service registry.
     *
     * @return the registry
     */
    public ServiceRegistry services() {
        return services;
    }

    /**
     * Returns the configuration manager.
     *
     * @return the manager
     */
    public ConfigManager configManager() {
        return configManager;
    }

    /**
     * Returns the database manager.
     *
     * @return the manager
     */
    public DatabaseManager database() {
        return database;
    }

    /**
     * Returns the enchanting manager.
     *
     * @return the manager
     */
    public EnchantingManager enchantingManager() {
        return enchanting;
    }

    /**
     * Returns the trade cache.
     *
     * @return the cache
     */
    public TradeCache tradeCache() {
        return cache;
    }

    /**
     * Returns whether the plugin has finished starting up.
     *
     * @return {@code true} when started
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * Returns the public API implementation.
     *
     * @return the API
     */
    public NexusEnchantsApi api() {
        return api;
    }

    /**
     * Returns a consumer that schedules work on the server thread.
     *
     * @return the scheduler callback
     */
    public Consumer<Runnable> mainThreadScheduler() {
        return this::runOnMainThread;
    }
}
