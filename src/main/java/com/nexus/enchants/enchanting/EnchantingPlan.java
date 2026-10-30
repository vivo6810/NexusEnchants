package com.nexus.enchants.enchanting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.enchantments.Enchantment;

/**
 * The enchantments a single enchanting-table click will produce.
 *
 * <p>Plans are computed once in {@code PrepareItemEnchantEvent} and stored until
 * {@code EnchantItemEvent} so the preview and the applied result can never
 * disagree.</p>
 */
public final class EnchantingPlan {

    private final Map<Enchantment, Integer> enchantments;
    private final int experienceCost;

    /**
     * Creates the plan.
     *
     * @param enchantments enchantment to level map, iteration order is preserved
     * @param experienceCost vanilla XP cost of the clicked slot, {@code 0} when unknown
     */
    public EnchantingPlan(Map<Enchantment, Integer> enchantments, int experienceCost) {
        this.enchantments = Collections.unmodifiableMap(new LinkedHashMap<>(enchantments));
        this.experienceCost = experienceCost;
    }

    /**
     * Returns an empty plan.
     *
     * @param experienceCost vanilla XP cost
     * @return a plan with no enchantments
     */
    public static EnchantingPlan empty(int experienceCost) {
        return new EnchantingPlan(Map.of(), experienceCost);
    }

    /**
     * Returns the enchantments.
     *
     * @return immutable enchantment to level map
     */
    public Map<Enchantment, Integer> enchantments() {
        return enchantments;
    }

    /**
     * Returns the primary enchantment shown in the preview.
     *
     * @return the primary enchantment, or {@code null} when the plan is empty
     */
    public Enchantment primary() {
        return enchantments.isEmpty() ? null : enchantments.keySet().iterator().next();
    }

    /**
     * Returns the level of the primary enchantment.
     *
     * @return the primary level, {@code 0} when the plan is empty
     */
    public int primaryLevel() {
        Enchantment primary = primary();
        return primary == null ? 0 : enchantments.getOrDefault(primary, 0);
    }

    /**
     * Returns the vanilla XP cost of the slot.
     *
     * @return the XP cost
     */
    public int experienceCost() {
        return experienceCost;
    }

    /**
     * Returns whether the plan contains no enchantments.
     *
     * @return {@code true} when empty
     */
    public boolean isEmpty() {
        return enchantments.isEmpty();
    }

    /**
     * Returns how many enchantments the plan applies.
     *
     * @return the enchantment count
     */
    public int size() {
        return enchantments.size();
    }
}
