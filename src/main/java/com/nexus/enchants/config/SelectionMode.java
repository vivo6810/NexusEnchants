package com.nexus.enchants.config;

import java.util.Locale;
import java.util.Optional;

/**
 * Rarity selection strategy for villager trades and enchanting results.
 */
public enum SelectionMode {

    /**
     * Selection is driven by the configured weight table.
     */
    WEIGHTED,

    /**
     * The plugin leaves the source untouched and vanilla decides.
     */
    DISABLED;

    /**
     * Parses a mode name case insensitively.
     *
     * @param raw raw configuration value
     * @return the parsed mode, or empty when the value is unknown
     */
    public static Optional<SelectionMode> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String cleaned = raw.trim().toUpperCase(Locale.ROOT);
        for (SelectionMode mode : values()) {
            if (mode.name().equals(cleaned)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}
