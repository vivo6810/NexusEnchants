package com.nexus.enchants.api;

import com.nexus.enchants.sources.AcquisitionSource;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.enchantments.Enchantment;

/**
 * Public read-only view of NexusEnchants, registered as a Bukkit service.
 *
 * <p>Other plugins should look the API up through
 * {@code Bukkit.getServicesManager().load(NexusEnchantsApi.class)} instead of
 * depending on internal classes.</p>
 */
public interface NexusEnchantsApi {

    /**
     * Version of this interface. Bumped when a method is removed or changes meaning.
     */
    String API_VERSION = "1.0.0";

    /**
     * Returns the interface version.
     *
     * @return the version string
     */
    String apiVersion();

    /**
     * Returns the configured base price of an enchantment level.
     *
     * @param enchantment enchantment
     * @param level       enchantment level
     * @return the base price in emeralds
     * @throws IllegalArgumentException when the level is below 1
     */
    int getBasePrice(Enchantment enchantment, int level);

    /**
     * Returns the price a player with the given Hero of the Village amplifier pays.
     *
     * @param enchantment   enchantment
     * @param level         enchantment level
     * @param heroAmplifier Hero of the Village amplifier, {@code 0} when absent
     * @return the effective price in emeralds
     */
    int getEffectivePrice(Enchantment enchantment, int level, int heroAmplifier);

    /**
     * Returns whether an enchantment may be obtained from a source.
     *
     * @param enchantment enchantment
     * @param source      acquisition source
     * @return {@code true} when allowed
     */
    boolean isSourceAllowed(Enchantment enchantment, AcquisitionSource source);

    /**
     * Returns the probability that a librarian offers an enchantment.
     *
     * @param enchantment enchantment
     * @return the probability in the range {@code 0.0 - 1.0}, {@code 0.0} when not eligible
     */
    double getVillagerProbability(Enchantment enchantment);

    /**
     * Returns the probability that an enchanting table picks an enchantment,
     * ignoring item compatibility.
     *
     * @param enchantment enchantment
     * @return the probability in the range {@code 0.0 - 1.0}
     */
    double getEnchantingProbability(Enchantment enchantment);

    /**
     * Returns whether a villager is under plugin control.
     *
     * @param villagerId villager UUID
     * @return {@code true} when managed
     */
    boolean isVillagerManaged(UUID villagerId);

    /**
     * Reloads every YAML file.
     *
     * @return a future completed on the main thread with the reload result
     */
    CompletableFuture<ReloadResult> reload();

    /**
     * Outcome of a reload.
     *
     * @param success  whether the reload completed without configuration errors
     * @param errors   number of configuration errors
     * @param warnings number of configuration warnings
     */
    record ReloadResult(boolean success, int errors, int warnings) {
    }
}
