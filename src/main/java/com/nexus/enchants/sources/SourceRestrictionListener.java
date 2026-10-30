package com.nexus.enchants.sources;

import com.nexus.enchants.config.ConfigSnapshot;
import com.nexus.enchants.util.EnchantmentResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Enforces acquisition source restrictions for loot and fishing.
 *
 * <p>Loot filtering works on the generated list before it is placed into a
 * container, so no item is ever created and then retracted. A book whose only
 * enchantment is forbidden is removed entirely; an item that keeps at least one
 * allowed enchantment keeps the item and loses only the forbidden ones.</p>
 */
public final class SourceRestrictionListener implements Listener {

    private final Supplier<ConfigSnapshot> snapshotSupplier;
    private final EnchantmentSourceManager sources;

    /**
     * Creates the listener.
     *
     * @param snapshotSupplier supplier of the active configuration
     * @param sources          acquisition source manager
     */
    public SourceRestrictionListener(
            Supplier<ConfigSnapshot> snapshotSupplier,
            EnchantmentSourceManager sources) {
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.sources = Objects.requireNonNull(sources, "sources");
    }

    /**
     * Filters generated loot.
     *
     * @param event loot event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        if (!isActive()) {
            return;
        }
        List<ItemStack> loot = event.getLoot();
        if (loot.isEmpty()) {
            return;
        }
        List<ItemStack> filtered = new ArrayList<>(loot.size());
        boolean changed = false;
        for (ItemStack stack : loot) {
            ItemStack result = filter(stack, AcquisitionSource.LOOT);
            if (result == null) {
                changed = true;
                continue;
            }
            if (result != stack) {
                changed = true;
            }
            filtered.add(result);
        }
        if (changed) {
            event.setLoot(filtered);
        }
    }

    /**
     * Filters a caught fishing treasure.
     *
     * @param event fish event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !isActive()) {
            return;
        }
        if (!(event.getCaught() instanceof Item caught)) {
            return;
        }
        ItemStack original = caught.getItemStack();
        ItemStack filtered = filter(original, AcquisitionSource.FISHING);
        if (filtered == null) {
            // The only catch was a forbidden book: cancel instead of handing out a banned item.
            event.setCancelled(true);
            return;
        }
        if (filtered != original) {
            caught.setItemStack(filtered);
        }
    }

    /**
     * Filters a single item.
     *
     * @param stack  item to filter, may be {@code null}
     * @param source acquisition source
     * @return the original item when unchanged, a modified copy when enchantments were removed,
     *         or {@code null} when the item must be dropped
     */
    public ItemStack filter(ItemStack stack, AcquisitionSource source) {
        if (stack == null || stack.getType() == Material.AIR) {
            return stack;
        }
        Map<Enchantment, Integer> enchantments = stack.getEnchantments();
        if (enchantments.isEmpty()) {
            return stack;
        }
        List<Enchantment> forbidden = new ArrayList<>();
        for (Enchantment enchantment : enchantments.keySet()) {
            if (!sources.allows(EnchantmentResolver.keyOf(enchantment), source)) {
                forbidden.add(enchantment);
            }
        }
        if (forbidden.isEmpty()) {
            return stack;
        }
        if (forbidden.size() == enchantments.size()) {
            return stack.getType() == Material.ENCHANTED_BOOK ? null : stack;
        }
        ItemStack copy = stack.clone();
        for (Enchantment enchantment : forbidden) {
            copy.removeEnchantment(enchantment);
        }
        return copy;
    }

    private boolean isActive() {
        ConfigSnapshot snapshot = snapshotSupplier.get();
        return snapshot.settings().enabled() && snapshot.sources().enabled();
    }
}
