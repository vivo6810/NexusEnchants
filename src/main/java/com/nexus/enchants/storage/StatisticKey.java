package com.nexus.enchants.storage;

/**
 * Identity of an aggregated rarity statistic row.
 *
 * @param source           producing system, for example {@code villager} or {@code enchanting}
 * @param enchantmentKey   canonical enchantment key
 * @param enchantmentLevel enchantment level
 */
public record StatisticKey(String source, String enchantmentKey, int enchantmentLevel) {
}
