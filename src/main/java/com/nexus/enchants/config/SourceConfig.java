package com.nexus.enchants.config;

import com.nexus.enchants.sources.AcquisitionSource;
import java.util.Map;

/**
 * Immutable acquisition-source restrictions.
 *
 * <p>An enchantment that is not listed uses the {@code default} block. The
 * lookup never fails: an unknown source falls back to the default of that
 * source.</p>
 */
public final class SourceConfig {

    private final boolean enabled;
    private final Map<AcquisitionSource, Boolean> defaults;
    private final Map<String, Map<AcquisitionSource, Boolean>> rules;

    /**
     * Creates the configuration.
     *
     * @param enabled  whether restrictions are enforced
     * @param defaults source to allowed map for unlisted enchantments
     * @param rules    canonical enchantment key to source to allowed map
     */
    public SourceConfig(boolean enabled, Map<AcquisitionSource, Boolean> defaults,
            Map<String, Map<AcquisitionSource, Boolean>> rules) {
        this.enabled = enabled;
        this.defaults = Map.copyOf(defaults);
        this.rules = Map.copyOf(rules);
    }

    /**
     * Returns a configuration that allows every source.
     *
     * @return an unrestricted configuration
     */
    public static SourceConfig allowAll() {
        return new SourceConfig(false, Map.of(), Map.of());
    }

    /**
     * Returns whether an enchantment may be obtained from a source.
     *
     * @param enchantmentKey canonical enchantment key
     * @param source         acquisition source
     * @return {@code true} when allowed, always {@code true} when restrictions are disabled
     */
    public boolean allows(String enchantmentKey, AcquisitionSource source) {
        if (!enabled) {
            return true;
        }
        Map<AcquisitionSource, Boolean> rulesForEnchantment = rules.get(enchantmentKey);
        if (rulesForEnchantment != null) {
            Boolean allowed = rulesForEnchantment.get(source);
            if (allowed != null) {
                return allowed;
            }
        }
        return defaults.getOrDefault(source, Boolean.TRUE);
    }

    /**
     * Returns whether restrictions are enforced.
     *
     * @return the configured enabled flag
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Returns the default rule set.
     *
     * @return source to allowed map
     */
    public Map<AcquisitionSource, Boolean> defaults() {
        return defaults;
    }

    /**
     * Returns every per-enchantment rule.
     *
     * @return immutable canonical key to source to allowed map
     */
    public Map<String, Map<AcquisitionSource, Boolean>> rules() {
        return rules;
    }
}
