package com.nexus.enchants.enchanting;

import com.nexus.enchants.config.ConfigSnapshot;
import com.nexus.enchants.util.EnchantmentResolver;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

/**
 * Builds a complete enchanting result for one item.
 *
 * <p>Combinations never break vanilla rules: an enchantment must be able to
 * apply to the item, must not conflict with an already chosen enchantment and is
 * never added twice.</p>
 */
public final class CombinationManager {

    private final Supplier<ConfigSnapshot> snapshotSupplier;
    private final EnchantmentWeightManager weights;
    private final EnchantmentLevelManager levels;

    /**
     * Creates the manager.
     *
     * @param snapshotSupplier supplier of the active configuration
     * @param weights          enchantment weight manager
     * @param levels           level selection manager
     */
    public CombinationManager(
            Supplier<ConfigSnapshot> snapshotSupplier,
            EnchantmentWeightManager weights,
            EnchantmentLevelManager levels) {
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.weights = Objects.requireNonNull(weights, "weights");
        this.levels = Objects.requireNonNull(levels, "levels");
    }

    /**
     * Builds a plan for an item.
     *
     * @param item           item being enchanted
     * @param experienceCost vanilla XP cost of the clicked slot
     * @param random         random source
     * @return the plan, empty when no enchantment is eligible
     */
    public EnchantingPlan plan(ItemStack item, int experienceCost, Random random) {
        ConfigSnapshot snapshot = snapshotSupplier.get();
        if (!snapshot.enchanting().active()) {
            return EnchantingPlan.empty(experienceCost);
        }
        Optional<Enchantment> primary = weights.pick(item, random);
        if (primary.isEmpty()) {
            return EnchantingPlan.empty(experienceCost);
        }
        Map<Enchantment, Integer> chosen = new LinkedHashMap<>();
        Enchantment primaryEnchantment = primary.get();
        chosen.put(primaryEnchantment, levels.pick(primaryEnchantment, Map.of(), random));

        if (!snapshot.enchanting().multipleEnchantmentsEnabled()) {
            return new EnchantingPlan(chosen, experienceCost);
        }
        int maximum = Math.max(1, snapshot.enchanting().maximumEnchantments());
        while (chosen.size() < maximum && random.nextDouble() * 100.0D < snapshot.enchanting()
                .multipleEnchantmentsChance()) {
            Optional<Enchantment> next = pickCompatible(item, chosen, random);
            if (next.isEmpty()) {
                break;
            }
            Enchantment enchantment = next.get();
            int[] range = EnchantmentLevelManager.legalRange(enchantment);
            int low = Math.max(range[0], snapshot.enchanting().additionalMinimumLevel());
            int high = Math.min(range[1], snapshot.enchanting().additionalMaximumLevel());
            if (low > high) {
                low = range[0];
                high = range[1];
            }
            int level = low + random.nextInt(high - low + 1);
            chosen.put(enchantment, level);
        }
        return new EnchantingPlan(chosen, experienceCost);
    }

    private Optional<Enchantment> pickCompatible(ItemStack item, Map<Enchantment, Integer> chosen, Random random) {
        Enchantment candidate = weights.pick(item, random).orElse(null);
        if (candidate == null) {
            return Optional.empty();
        }
        if (chosen.containsKey(candidate) || conflictsWithAny(candidate, chosen)) {
            return Optional.empty();
        }
        return Optional.of(candidate);
    }

    private boolean conflictsWithAny(Enchantment candidate, Map<Enchantment, Integer> chosen) {
        for (Enchantment existing : chosen.keySet()) {
            if (candidate.conflictsWith(existing) || existing.conflictsWith(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the probability of an enchantment being the primary result.
     *
     * @param enchantmentKey canonical enchantment key
     * @param item           item being enchanted
     * @return the probability in the range {@code 0.0 - 1.0}
     */
    public double primaryProbability(String enchantmentKey, ItemStack item) {
        for (var entry : weights.tableFor(item).entries()) {
            if (EnchantmentResolver.keyOf(entry.value()).equals(enchantmentKey)) {
                return weights.tableFor(item).probabilityOf(entry.value());
            }
        }
        return 0.0D;
    }
}
