package com.nexus.enchants.sources;

import com.nexus.enchants.config.ConfigSnapshot;
import com.nexus.enchants.util.EnchantmentResolver;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.enchantments.Enchantment;

/**
 * Answers whether an enchantment may be obtained from a given source.
 */
public final class EnchantmentSourceManager {

    private final Supplier<ConfigSnapshot> snapshotSupplier;

    /**
     * Creates the manager.
     *
     * @param snapshotSupplier supplier of the active configuration
     */
    public EnchantmentSourceManager(Supplier<ConfigSnapshot> snapshotSupplier) {
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
    }

    /**
     * Returns whether an enchantment may come from a source.
     *
     * @param enchantmentKey canonical enchantment key
     * @param source         acquisition source
     * @return {@code true} when allowed
     */
    public boolean allows(String enchantmentKey, AcquisitionSource source) {
        return snapshotSupplier.get().sources().allows(enchantmentKey, source);
    }

    /**
     * Returns whether an enchantment may come from a source.
     *
     * @param enchantment enchantment
     * @param source      acquisition source
     * @return {@code true} when allowed
     */
    public boolean allows(Enchantment enchantment, AcquisitionSource source) {
        return allows(EnchantmentResolver.keyOf(enchantment), source);
    }

    /**
     * Returns whether source restrictions are enforced at all.
     *
     * @return {@code true} when at least one restriction may apply
     */
    public boolean active() {
        return snapshotSupplier.get().sources().enabled();
    }
}
