package com.nexus.enchants.storage;

import java.util.UUID;

/**
 * Persistent metadata of a villager controlled by the plugin.
 *
 * <p>Prices are never stored here; they always come from YAML. This record only
 * remembers that the villager is managed, so a restart can find it again.</p>
 *
 * @param villagerId    villager UUID
 * @param worldId       world UUID
 * @param worldName     world name at registration
 * @param profession    profession key
 * @param managed       whether the plugin still controls the villager
 * @param firstRegistered epoch milliseconds of the first registration
 * @param lastUpdated   epoch milliseconds of the last write
 * @param schemaVersion schema version the record was written with
 */
public record VillagerRecord(
        UUID villagerId,
        UUID worldId,
        String worldName,
        String profession,
        boolean managed,
        long firstRegistered,
        long lastUpdated,
        int schemaVersion) {

    /**
     * Creates a new record for a villager that was just taken over.
     *
     * @param villagerId villager UUID
     * @param worldId    world UUID
     * @param worldName  world name
     * @param profession profession key
     * @param now        current epoch milliseconds
     * @return the record
     */
    public static VillagerRecord create(UUID villagerId, UUID worldId, String worldName, String profession, long now) {
        return new VillagerRecord(villagerId, worldId, worldName, profession, true, now, now, 1);
    }

    /**
     * Returns a copy with a new update timestamp.
     *
     * @param now current epoch milliseconds
     * @return the updated record
     */
    public VillagerRecord touch(long now) {
        return new VillagerRecord(villagerId, worldId, worldName, profession, managed, firstRegistered, now,
                schemaVersion);
    }
}
