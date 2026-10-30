package com.nexus.enchants.storage;

import java.util.UUID;

/**
 * Persistent metadata of a managed librarian trade.
 *
 * @param villagerId       owning villager UUID
 * @param tradeKey         stable key of the trade inside the villager
 * @param enchantmentKey   canonical enchantment key
 * @param enchantmentLevel enchantment level
 * @param emeraldPrice     base price in emeralds, before discounts
 * @param uses             uses consumed at the time of the write
 * @param maxUses          maximum uses of the recipe
 * @param createdAt        epoch milliseconds of the first write
 * @param updatedAt        epoch milliseconds of the last write
 */
public record TradeRecord(
        UUID villagerId,
        String tradeKey,
        String enchantmentKey,
        int enchantmentLevel,
        int emeraldPrice,
        int uses,
        int maxUses,
        long createdAt,
        long updatedAt) {

    /**
     * Creates a new trade record.
     *
     * @param villagerId       owning villager
     * @param tradeKey         stable trade key
     * @param enchantmentKey   canonical enchantment key
     * @param enchantmentLevel enchantment level
     * @param emeraldPrice     base price
     * @param uses             current uses
     * @param maxUses          maximum uses
     * @param now              current epoch milliseconds
     * @return the record
     */
    public static TradeRecord create(
            UUID villagerId,
            String tradeKey,
            String enchantmentKey,
            int enchantmentLevel,
            int emeraldPrice,
            int uses,
            int maxUses,
            long now) {
        return new TradeRecord(villagerId, tradeKey, enchantmentKey, enchantmentLevel, emeraldPrice, uses,
                maxUses, now, now);
    }
}
