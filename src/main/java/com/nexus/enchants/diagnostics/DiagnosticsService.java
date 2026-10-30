package com.nexus.enchants.diagnostics;

import com.nexus.enchants.config.ConfigIssue;
import com.nexus.enchants.config.ConfigSnapshot;
import com.nexus.enchants.database.DatabaseManager;
import com.nexus.enchants.enchanting.EnchantingManager;
import com.nexus.enchants.storage.RarityStatisticsStore;
import com.nexus.enchants.storage.TradeCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Builds the diagnostic report printed by {@code /vbe diagnostics}.
 *
 * <p>The report only reads cached state and immutable configuration, so it can
 * run on the server thread without touching the database. It also states the
 * known API limitations, because an administrator seeing "discounts: CAP" should
 * also see what CAP cannot do.</p>
 */
public final class DiagnosticsService {

    private final Supplier<ConfigSnapshot> snapshotSupplier;
    private final DatabaseManager database;
    private final TradeCache cache;
    private final RarityStatisticsStore statistics;
    private final EnchantingManager enchanting;
    private final String version;

    /**
     * Creates the service.
     *
     * @param snapshotSupplier supplier of the active configuration
     * @param database         database manager
     * @param cache            managed villager cache
     * @param statistics       statistics store
     * @param enchanting       enchanting manager
     * @param version          plugin version
     */
    public DiagnosticsService(
            Supplier<ConfigSnapshot> snapshotSupplier,
            DatabaseManager database,
            TradeCache cache,
            RarityStatisticsStore statistics,
            EnchantingManager enchanting,
            String version) {
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.database = Objects.requireNonNull(database, "database");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.statistics = Objects.requireNonNull(statistics, "statistics");
        this.enchanting = Objects.requireNonNull(enchanting, "enchanting");
        this.version = Objects.requireNonNull(version, "version");
    }

    /**
     * Builds the report.
     *
     * @return coloured report lines
     */
    public List<String> report() {
        ConfigSnapshot snapshot = snapshotSupplier.get();
        List<String> lines = new ArrayList<>();
        lines.add("&8&m                                                        ");
        lines.add("&bNexusEnchants &7diagnostics &8(v" + version + ")");
        lines.add("&7Control enabled: &f" + snapshot.settings().enabled()
                + "&7, reloaded at &f" + snapshot.loadedAtMillis());
        lines.add("&7Configuration findings: &c" + snapshot.errors().size()
                + "&7 error(s), &e" + snapshot.warnings().size() + "&7 warning(s)");
        int shown = 0;
        for (ConfigIssue issue : snapshot.issues()) {
            if (shown++ >= 10) {
                lines.add("&8  ... " + (snapshot.issues().size() - 10) + " more");
                break;
            }
            lines.add("&8  " + (issue.severity() == ConfigIssue.Severity.ERROR ? "&c" : "&e") + issue.format());
        }
        lines.add("&7Pricing: &f" + countedPairs(snapshot) + "&7 pair(s), limits &f"
                + snapshot.pricing().minimumPrice() + "-" + snapshot.pricing().maximumPrice()
                + "&7, fallback " + snapshot.pricing().fallbackEnabled());
        lines.add("&7Librarian rarity: " + yesNo(snapshot.villagerRarity().active())
                + "&7, mode &f" + snapshot.villagerRarity().selectionMode()
                + "&7, entries &f" + snapshot.villagerRarity().weights().size());
        lines.add("&7Enchanting rarity: " + yesNo(snapshot.enchanting().active())
                + "&7, treasure " + yesNo(snapshot.enchanting().treasureEnabled())
                + "&7, multiple " + yesNo(snapshot.enchanting().multipleEnchantmentsEnabled())
                + "&7 at &f" + snapshot.enchanting().multipleEnchantmentsChance() + "%");
        lines.add("&7Sources enforced: " + yesNo(snapshot.sources().enabled())
                + "&7, defaults &f" + snapshot.sources().defaults());
        lines.add("&7Discounts: &f" + snapshot.discounts().policy()
                + "&7, cap &f" + snapshot.discounts().maximumDiscountPercentage() + "%"
                + "&7, hero re-apply " + yesNo(snapshot.discounts().heroOfTheVillageEnabled()));
        lines.add("&7Database: &f" + database.type() + "&7, ready " + yesNo(database.isReady())
                + "&7, schema &f" + database.schemaVersion());
        lines.add("&8  " + database.statusSummary());
        lines.add("&7Managed villagers cached: &f" + cache.size()
                + "&7, statistics pending: &f" + statistics.pendingKeys() + "&7 key(s), &f"
                + statistics.recordedSinceFlush() + "&7 roll(s)");
        lines.add("&7Cached enchanting plans: &f" + enchanting.pendingPlans());
        lines.add("&cKnown limitations:");
        for (String limitation : knownLimitations()) {
            lines.add("&8  - &7" + limitation);
        }
        lines.add("&8&m                                                        ");
        return List.copyOf(lines);
    }

    /**
     * Returns the documented limitations of the current Paper API surface.
     *
     * @return short limitation descriptions
     */
    public static List<String> knownLimitations() {
        return List.of(
                "Per-player merchant prices are not exposed, so under CAP/DISABLE every player pays the "
                        + "configured price and cured/reputation discounts are suppressed rather than capped.",
                "A merchant trade holds at most 2 x 64 items, so a price above 128 cannot be represented.",
                "Extra enchanting-table enchantments cannot be previewed; only the primary one is shown.",
                "Anvil, grindstone and chest-transfer sources are not intercepted.",
                "Changing database settings requires a server restart.");
    }

    private int countedPairs(ConfigSnapshot snapshot) {
        int total = 0;
        for (var levels : snapshot.pricing().prices().values()) {
            total += levels.size();
        }
        return total;
    }

    private static String yesNo(boolean value) {
        return value ? "&ayes" : "&cno";
    }
}
