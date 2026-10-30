package com.nexus.enchants.database.repositories;

import java.util.UUID;

/**
 * One audit trail entry.
 *
 * @param eventType        event identifier, for example {@code PRICE_CHANGED}
 * @param actorId          player who caused the change, {@code null} for the console or the plugin
 * @param villagerId       affected villager, may be {@code null}
 * @param enchantmentKey   affected enchantment, may be {@code null}
 * @param enchantmentLevel affected level, may be {@code null}
 * @param previousValue    value before the change, may be {@code null}
 * @param newValue         value after the change, may be {@code null}
 * @param details          free-form context, may be {@code null}
 * @param createdAt        epoch milliseconds
 */
public record AuditRecord(
        String eventType,
        UUID actorId,
        UUID villagerId,
        String enchantmentKey,
        Integer enchantmentLevel,
        String previousValue,
        String newValue,
        String details,
        long createdAt) {

    /**
     * Creates an administrative change entry.
     *
     * @param eventType        event identifier
     * @param actorId          acting player
     * @param enchantmentKey   affected enchantment
     * @param enchantmentLevel affected level
     * @param previousValue    previous value
     * @param newValue         new value
     * @param details          context
     * @param createdAt        epoch milliseconds
     * @return the record
     */
    public static AuditRecord ofChange(
            String eventType,
            UUID actorId,
            String enchantmentKey,
            Integer enchantmentLevel,
            String previousValue,
            String newValue,
            String details,
            long createdAt) {
        return new AuditRecord(eventType, actorId, null, enchantmentKey, enchantmentLevel,
                previousValue, newValue, details, createdAt);
    }
}
