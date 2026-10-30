package com.nexus.enchants.villager;

import com.nexus.enchants.economy.BookPricingManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

/**
 * Builds the Bukkit objects for a managed librarian trade.
 *
 * <p>The result item, the emerald payment and the recipe flags are produced in
 * one place so the acquire, replenish and migration paths cannot drift apart.</p>
 */
public final class TradeGenerationManager {

    /**
     * Creates the manager.
     */
    public TradeGenerationManager() {
    }

    /**
     * Creates an enchanted book carrying exactly one enchantment.
     *
     * @param enchantment enchantment to store
     * @param level       stored level
     * @return the book
     * @throws IllegalArgumentException when the level is below 1
     */
    public ItemStack createEnchantedBook(Enchantment enchantment, int level) {
        Objects.requireNonNull(enchantment, "enchantment");
        if (level < 1) {
            throw new IllegalArgumentException("Enchantment level must be at least 1, got " + level);
        }
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        book.addUnsafeEnchantment(enchantment, level);
        return book;
    }

    /**
     * Creates an emerald stack of a payment slot.
     *
     * @param amount emerald amount, 1 to 64
     * @return the stack
     * @throws IllegalArgumentException when the amount is out of range
     */
    public ItemStack createEmeralds(int amount) {
        if (amount < 1 || amount > 64) {
            throw new IllegalArgumentException("Emerald slot amount must be between 1 and 64, got " + amount);
        }
        return new ItemStack(Material.EMERALD, amount);
    }

    /**
     * Rebuilds a merchant recipe with a new result, payment and discount flags.
     *
     * <p>The merchant uses-stock, experience reward and price multiplier of the
     * original recipe are preserved; only the parts the plugin owns are replaced.</p>
     *
     * @param original         original recipe supplied by the server
     * @param result           replacement result item
     * @param priced           priced trade providing the emerald payment
     * @return the replacement recipe
     */
    public MerchantRecipe buildRecipe(
            MerchantRecipe original,
            ItemStack result,
            BookPricingManager.PricedTrade priced) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(priced, "priced");

        MerchantRecipe recipe = new MerchantRecipe(result, Math.max(1, original.getMaxUses()));
        recipe.setUses(original.getUses());
        recipe.setExperienceReward(original.hasExperienceReward());
        recipe.setVillagerExperience(original.getVillagerExperience());
        recipe.setPriceMultiplier(original.getPriceMultiplier());
        // Vanilla demand/special-price drift must not silently change the configured price.
        recipe.setDemand(0);
        recipe.setSpecialPrice(0);
        recipe.setIgnoreDiscounts(priced.suppressDiscounts());

        List<ItemStack> ingredients = new ArrayList<>(2);
        for (Integer amount : priced.plan().slotAmounts()) {
            ingredients.add(createEmeralds(amount));
        }
        recipe.setIngredients(ingredients);
        return recipe;
    }

    /**
     * Returns the enchantment stored on a book, if any.
     *
     * @param item item to inspect
     * @return the first stored enchantment, or empty
     */
    public static java.util.Optional<Enchantment> storedEnchantment(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) {
            return java.util.Optional.empty();
        }
        java.util.Map<Enchantment, Integer> enchantments = item.getEnchantments();
        if (enchantments.isEmpty()) {
            return java.util.Optional.empty();
        }
        return enchantments.keySet().stream().findFirst();
    }

    /**
     * Returns the level of a stored enchantment.
     *
     * @param item        book to inspect
     * @param enchantment enchantment to look up
     * @return the stored level, {@code 0} when absent
     */
    public static int storedLevel(ItemStack item, Enchantment enchantment) {
        if (item == null || enchantment == null) {
            return 0;
        }
        return item.getEnchantmentLevel(enchantment);
    }

    /**
     * Returns a stable key identifying a book trade inside one villager.
     *
     * @param slotIndex      recipe index inside the villager
     * @param enchantmentKey canonical enchantment key
     * @param level          enchantment level
     * @return the trade key
     */
    public static String tradeKey(int slotIndex, String enchantmentKey, int level) {
        return "book:" + slotIndex + ":" + enchantmentKey + ":" + level;
    }
}
