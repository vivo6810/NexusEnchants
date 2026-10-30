package com.nexus.enchants.sources;

import java.util.Locale;
import java.util.Optional;

/**
 * A way for a player to obtain an enchantment.
 *
 * <p>Each constant is documented with the Paper event the plugin uses to
 * enforce it. Sources without a supported event are not listed here; see
 * docs/API_COMPATIBILITY.md.</p>
 */
public enum AcquisitionSource {

    /**
     * Librarian enchanted-book trades, enforced through
     * {@code VillagerAcquireTradeEvent}, {@code VillagerReplenishTradeEvent} and
     * {@code PlayerTradeEvent}.
     */
    VILLAGER("villager", true),

    /**
     * Enchanting table results, enforced through
     * {@code PrepareItemEnchantEvent} and {@code EnchantItemEvent}.
     */
    ENCHANTING_TABLE("enchanting-table", true),

    /**
     * Loot table generation, enforced through {@code LootGenerateEvent}.
     */
    LOOT("loot", true),

    /**
     * Fishing treasure, enforced through {@code PlayerFishEvent} in the
     * {@code CAUGHT_FISH} state.
     */
    FISHING("fishing", true);

    private final String configKey;
    private final boolean enforced;

    AcquisitionSource(String configKey, boolean enforced) {
        this.configKey = configKey;
        this.enforced = enforced;
    }

    /**
     * Returns the key used in enchantment-sources.yml.
     *
     * @return the configuration key
     */
    public String configKey() {
        return configKey;
    }

    /**
     * Returns whether the plugin enforces this source.
     *
     * @return {@code true} when a supported event exists
     */
    public boolean enforced() {
        return enforced;
    }

    /**
     * Parses a configuration key.
     *
     * @param raw raw key, for example {@code enchanting-table}
     * @return the parsed source, or empty when unknown
     */
    public static Optional<AcquisitionSource> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String cleaned = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (AcquisitionSource source : values()) {
            if (source.configKey.equals(cleaned) || source.name().toLowerCase(Locale.ROOT).equals(cleaned)) {
                return Optional.of(source);
            }
        }
        return Optional.empty();
    }
}
