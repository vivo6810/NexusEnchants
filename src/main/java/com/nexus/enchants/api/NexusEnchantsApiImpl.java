package com.nexus.enchants.api;

import com.nexus.enchants.commands.ReloadReport;
import com.nexus.enchants.config.ConfigSnapshot;
import com.nexus.enchants.economy.BookPricingManager;
import com.nexus.enchants.economy.PriceNotConfiguredException;
import com.nexus.enchants.enchanting.CombinationManager;
import com.nexus.enchants.sources.AcquisitionSource;
import com.nexus.enchants.sources.EnchantmentSourceManager;
import com.nexus.enchants.storage.TradeCache;
import com.nexus.enchants.util.EnchantmentResolver;
import com.nexus.enchants.villager.VillagerRarityManager;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.enchantments.Enchantment;

/**
 * Default API implementation backed by the live services.
 */
public final class NexusEnchantsApiImpl implements NexusEnchantsApi {

    private final Supplier<ConfigSnapshot> snapshotSupplier;
    private final BookPricingManager pricing;
    private final VillagerRarityManager villagerRarity;
    private final CombinationManager combinations;
    private final EnchantmentSourceManager sources;
    private final TradeCache cache;
    private final Supplier<ReloadReport> reload;
    private final Consumer<Runnable> mainThread;

    /**
     * Creates the implementation.
     *
     * @param snapshotSupplier supplier of the active configuration
     * @param pricing          price engine
     * @param villagerRarity   librarian rarity manager
     * @param combinations     enchanting combination builder
     * @param sources          acquisition source manager
     * @param cache            managed villager cache
     * @param reload           reload action
     * @param mainThread       scheduler for main-thread callbacks
     */
    public NexusEnchantsApiImpl(
            Supplier<ConfigSnapshot> snapshotSupplier,
            BookPricingManager pricing,
            VillagerRarityManager villagerRarity,
            CombinationManager combinations,
            EnchantmentSourceManager sources,
            TradeCache cache,
            Supplier<ReloadReport> reload,
            Consumer<Runnable> mainThread) {
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.pricing = Objects.requireNonNull(pricing, "pricing");
        this.villagerRarity = Objects.requireNonNull(villagerRarity, "villagerRarity");
        this.combinations = Objects.requireNonNull(combinations, "combinations");
        this.sources = Objects.requireNonNull(sources, "sources");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.reload = Objects.requireNonNull(reload, "reload");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
    }

    @Override
    public String apiVersion() {
        return API_VERSION;
    }

    @Override
    public int getBasePrice(Enchantment enchantment, int level) {
        return pricing.basePrice(EnchantmentResolver.keyOf(enchantment), level);
    }

    @Override
    public int getEffectivePrice(Enchantment enchantment, int level, int heroAmplifier) {
        try {
            return pricing.price(EnchantmentResolver.keyOf(enchantment), level, Math.max(0, heroAmplifier))
                    .finalPrice();
        } catch (PriceNotConfiguredException | IllegalStateException exception) {
            return pricing.basePrice(EnchantmentResolver.keyOf(enchantment), level);
        }
    }

    @Override
    public boolean isSourceAllowed(Enchantment enchantment, AcquisitionSource source) {
        return sources.allows(enchantment, source);
    }

    @Override
    public double getVillagerProbability(Enchantment enchantment) {
        return villagerRarity.probabilityOf(EnchantmentResolver.keyOf(enchantment));
    }

    @Override
    public double getEnchantingProbability(Enchantment enchantment) {
        return combinations.primaryProbability(EnchantmentResolver.keyOf(enchantment), null);
    }

    @Override
    public boolean isVillagerManaged(UUID villagerId) {
        return cache.isManaged(villagerId);
    }

    @Override
    public CompletableFuture<ReloadResult> reload() {
        CompletableFuture<ReloadResult> future = new CompletableFuture<>();
        mainThread.accept(() -> {
            try {
                ReloadReport report = reload.get();
                future.complete(new ReloadResult(report.success(), report.errors(), report.warnings()));
            } catch (RuntimeException exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    /**
     * Returns the snapshot supplier used by this implementation.
     *
     * @return the supplier
     */
    public Supplier<ConfigSnapshot> snapshotSupplier() {
        return snapshotSupplier;
    }
}
