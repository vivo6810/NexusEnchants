package com.nexus.enchants.util;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;

/**
 * Resolves enchantment names from configuration and commands.
 *
 * <p>Accepted spellings are the vanilla constant name ({@code MENDING}), the
 * namespaced key ({@code minecraft:mending}) and the legacy display spelling
 * ({@code Mending}). Resolution is registry based, so datapack and modded
 * enchantments work as long as the server registers them.</p>
 */
public final class EnchantmentResolver {

    private EnchantmentResolver() {
    }

    /**
     * Returns the server enchantment registry.
     *
     * @return the enchantment registry, never {@code null}
     */
    public static Registry<Enchantment> registry() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
    }

    /**
     * Normalises a configuration key to {@code namespace:path} lower case.
     *
     * @param raw raw name, may be {@code null}
     * @return the normalised key, or an empty string when {@code raw} is blank
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isEmpty()) {
            return "";
        }
        return cleaned.indexOf(':') < 0 ? "minecraft:" + cleaned : cleaned;
    }

    /**
     * Resolves a configuration key into a server enchantment.
     *
     * @param raw raw name from configuration or a command
     * @return the resolved enchantment, or empty when nothing matches
     */
    public static Optional<Enchantment> resolve(String raw) {
        String normalized = normalize(raw);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        String path = normalized.substring(normalized.indexOf(':') + 1);

        Enchantment direct = registry().get(NamespacedKey.fromString(normalized));
        if (direct != null) {
            return Optional.of(direct);
        }
        for (Enchantment candidate : registry()) {
            NamespacedKey key = candidate.getKey();
            if (key.getKey().equals(path) && key.getNamespace().equals("minecraft")) {
                return Optional.of(candidate);
            }
        }
        for (Enchantment candidate : registry()) {
            if (candidate.getKey().getKey().equalsIgnoreCase(path)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the canonical {@code namespace:path} key of an enchantment.
     *
     * @param enchantment enchantment to describe
     * @return the canonical key
     */
    public static String keyOf(Enchantment enchantment) {
        return enchantment.getKey().toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns every registered enchantment key, sorted alphabetically.
     *
     * @return an immutable list of canonical keys
     */
    public static List<String> allKeys() {
        List<String> keys = new ArrayList<>();
        for (Enchantment enchantment : registry()) {
            keys.add(keyOf(enchantment));
        }
        keys.sort(String::compareTo);
        return List.copyOf(keys);
    }
}
