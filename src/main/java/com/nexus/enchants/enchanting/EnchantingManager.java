package com.nexus.enchants.enchanting;

import com.nexus.enchants.config.ConfigSnapshot;
import com.nexus.enchants.sources.AcquisitionSource;
import com.nexus.enchants.sources.EnchantmentSourceManager;
import com.nexus.enchants.storage.RarityStatisticsStore;
import com.nexus.enchants.storage.StatisticKey;
import com.nexus.enchants.util.EnchantmentResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Controls enchanting-table results.
 *
 * <p>The plan is decided when the player opens the table
 * ({@link PrepareItemEnchantEvent}) and applied unchanged when they click
 * ({@link EnchantItemEvent}). That two-step design is what keeps the shown offer
 * and the produced enchantment identical, and it is why the plan cache is keyed
 * by player UUID and expires after a minute.</p>
 *
 * <p>Paper's offer structure exposes exactly one enchantment per slot, so extra
 * enchantments from a combination cannot be previewed. They are applied from the
 * same recorded plan; this limitation is documented in
 * docs/API_COMPATIBILITY.md.</p>
 */
public final class EnchantingManager implements Listener {

    private static final long PLAN_MAX_AGE_MILLIS = 60_000L;

    private final Supplier<ConfigSnapshot> snapshotSupplier;
    private final CombinationManager combinations;
    private final EnchantingResultManager results;
    private final EnchantmentSourceManager sources;
    private final RarityStatisticsStore statistics;
    private final Logger logger;
    private final Random random;
    private final Map<UUID, PendingPlans> pending = new ConcurrentHashMap<>();
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    /**
     * Creates the manager.
     *
     * @param snapshotSupplier supplier of the active configuration
     * @param combinations     combination builder
     * @param results          event writer
     * @param sources          acquisition source manager
     * @param statistics       aggregated statistics store
     * @param logger           logger
     * @param random           random source
     */
    public EnchantingManager(
            Supplier<ConfigSnapshot> snapshotSupplier,
            CombinationManager combinations,
            EnchantingResultManager results,
            EnchantmentSourceManager sources,
            RarityStatisticsStore statistics,
            Logger logger,
            Random random) {
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.combinations = Objects.requireNonNull(combinations, "combinations");
        this.results = Objects.requireNonNull(results, "results");
        this.sources = Objects.requireNonNull(sources, "sources");
        this.statistics = Objects.requireNonNull(statistics, "statistics");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Rewrites the three preview offers with weighted results.
     *
     * @param event prepare event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepare(PrepareItemEnchantEvent event) {
        ConfigSnapshot snapshot = snapshotSupplier.get();
        if (!snapshot.settings().enabled() || !snapshot.enchanting().active()) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        EnchantmentOffer[] offers = event.getOffers();
        EnchantingPlan[] plans = new EnchantingPlan[offers.length];
        boolean any = false;
        for (int index = 0; index < offers.length; index++) {
            EnchantmentOffer offer = offers[index];
            if (offer == null) {
                continue;
            }
            EnchantingPlan plan = combinations.plan(item, offer.getCost(), random);
            if (plan.isEmpty() || !results.applyPreview(offer, plan)) {
                continue;
            }
            plans[index] = plan;
            any = true;
        }
        if (!any) {
            reportOnce("empty-table", () -> logger.severe("The enchanting table could not produce a result for "
                    + "any offered slot. Check enchanting-rarity.yml, enchantment-sources.yml and the item type."));
            return;
        }
        pending.put(event.getEnchanter().getUniqueId(), new PendingPlans(plans, System.currentTimeMillis()));
    }

    /**
     * Applies the recorded plan or, when no plan exists, only enforces sources.
     *
     * @param event enchant event
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        ConfigSnapshot snapshot = snapshotSupplier.get();
        if (!snapshot.settings().enabled()) {
            return;
        }
        Player player = event.getEnchanter();
        PendingPlans saved = pending.remove(player.getUniqueId());
        if (saved == null || saved.isExpired()) {
            enforceSources(event, snapshot);
            return;
        }
        int slot = event.whichButton();
        if (slot < 0 || slot >= saved.plans.length) {
            enforceSources(event, snapshot);
            return;
        }
        EnchantingPlan plan = saved.plans[slot];
        if (plan == null || plan.isEmpty()) {
            enforceSources(event, snapshot);
            return;
        }
        results.apply(event, plan);
        Enchantment primary = plan.primary();
        if (primary != null) {
            statistics.record(new StatisticKey("enchanting", EnchantmentResolver.keyOf(primary),
                    plan.primaryLevel()));
        }
    }

    /**
     * Removes enchantments that are not allowed from the table.
     *
     * @param event    enchant event
     * @param snapshot active configuration
     */
    void enforceSources(EnchantItemEvent event, ConfigSnapshot snapshot) {
        Map<Enchantment, Integer> map = event.getEnchantsToAdd();
        if (map.isEmpty()) {
            return;
        }
        List<Enchantment> toRemove = new ArrayList<>();
        for (Enchantment enchantment : map.keySet()) {
            String key = EnchantmentResolver.keyOf(enchantment);
            boolean sourceAllowed = sources.allows(key, AcquisitionSource.ENCHANTING_TABLE);
            boolean treasureAllowed = snapshot.enchanting().treasureEnabled() || !enchantment.isTreasure();
            boolean excluded = snapshot.enchanting().excludedEnchantments().contains(key);
            if (!sourceAllowed || !treasureAllowed || excluded) {
                toRemove.add(enchantment);
            }
        }
        for (Enchantment enchantment : toRemove) {
            map.remove(enchantment);
        }
    }

    /**
     * Drops the cached plan of a player who disconnects.
     *
     * @param event quit event
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Returns how many players currently have a recorded plan.
     *
     * @return the cache size
     */
    public int pendingPlans() {
        return pending.size();
    }

    /**
     * Removes expired plans.
     */
    public void purgeExpired() {
        pending.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private void reportOnce(String key, Runnable action) {
        if (reported.add(key)) {
            action.run();
        }
    }

    /**
     * Plans recorded for one player, one per offered slot.
     *
     * @param plans     plans indexed by offer slot
     * @param createdAt creation timestamp
     */
    private record PendingPlans(EnchantingPlan[] plans, long createdAt) {

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > PLAN_MAX_AGE_MILLIS;
        }
    }

    /**
     * Returns whether an item can be enchanted at all.
     *
     * @param item item to inspect
     * @return {@code true} when the item is not empty
     */
    public static boolean isEnchantable(ItemStack item) {
        return item != null && item.getType() != Material.AIR;
    }
}
