package com.nexus.enchants.config;

/**
 * General plugin settings from config.yml.
 *
 * @param enabled              whether villager and enchanting control is active
 * @param verboseStartup       whether resolved tables are logged on startup
 * @param librarianBookTrades  number of enchanted-book trades a new librarian offers
 * @param statisticsEnabled    whether rarity statistics are aggregated
 * @param statisticsFlushSeconds interval between statistics flushes
 * @param auditEnabled         whether audit records are written
 * @param asyncWorkers         database worker threads
 * @param asyncQueueCapacity   bound of the database task queue
 */
public record PluginSettings(
        boolean enabled,
        boolean verboseStartup,
        int librarianBookTrades,
        boolean statisticsEnabled,
        int statisticsFlushSeconds,
        boolean auditEnabled,
        int asyncWorkers,
        int asyncQueueCapacity) {

    /**
     * Validates the settings.
     *
     * @param enabled               whether control is active
     * @param verboseStartup        whether tables are logged
     * @param librarianBookTrades   number of book trades
     * @param statisticsEnabled     whether statistics are aggregated
     * @param statisticsFlushSeconds flush interval
     * @param auditEnabled          whether audit records are written
     * @param asyncWorkers          worker thread count
     * @param asyncQueueCapacity    queue capacity
     */
    public PluginSettings {
        if (librarianBookTrades < 1 || librarianBookTrades > 6) {
            throw new IllegalArgumentException("librarianBookTrades must be between 1 and 6");
        }
        if (statisticsFlushSeconds < 10) {
            throw new IllegalArgumentException("statisticsFlushSeconds must be at least 10");
        }
        if (asyncWorkers < 1 || asyncWorkers > 16) {
            throw new IllegalArgumentException("asyncWorkers must be between 1 and 16");
        }
        if (asyncQueueCapacity < 16) {
            throw new IllegalArgumentException("asyncQueueCapacity must be at least 16");
        }
    }

    /**
     * Returns the documented defaults.
     *
     * @return the default settings
     */
    public static PluginSettings defaults() {
        return new PluginSettings(true, false, 2, true, 300, true, 2, 512);
    }
}
