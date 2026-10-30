package com.nexus.enchants.enchanting;

import java.util.Map;
import java.util.Objects;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.enchantment.EnchantItemEvent;

/**
 * Writes a computed {@link EnchantingPlan} into the Bukkit events.
 *
 * <p>XP cost and lapis are never touched: the plan only replaces the
 * enchantment selection, so the player pays exactly the vanilla price of the
 * slot they clicked.</p>
 */
public final class EnchantingResultManager {

    /**
     * Creates the manager.
     */
    public EnchantingResultManager() {
    }

    /**
     * Applies a plan to the preview offers.
     *
     * <p>The offer object is mutated in place, which is how the server builds the
     * packet, and its cost is preserved.</p>
     *
     * @param offer offer to rewrite
     * @param plan  plan to show
     * @return {@code true} when the offer was rewritten
     */
    public boolean applyPreview(EnchantmentOffer offer, EnchantingPlan plan) {
        Objects.requireNonNull(offer, "offer");
        Objects.requireNonNull(plan, "plan");
        Enchantment primary = plan.primary();
        if (primary == null) {
            return false;
        }
        int cost = offer.getCost();
        offer.setEnchantment(primary);
        offer.setEnchantmentLevel(plan.primaryLevel());
        offer.setCost(cost);
        return true;
    }

    /**
     * Applies a plan to a completed enchant.
     *
     * @param event enchant event
     * @param plan  plan decided when the menu was opened
     */
    public void apply(EnchantItemEvent event, EnchantingPlan plan) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(plan, "plan");
        if (plan.isEmpty()) {
            return;
        }
        Map<Enchantment, Integer> map = event.getEnchantsToAdd();
        map.clear();
        map.putAll(plan.enchantments());
    }
}
