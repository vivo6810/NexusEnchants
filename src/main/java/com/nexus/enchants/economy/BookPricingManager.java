package com.nexus.enchants.economy;

import com.nexus.enchants.config.ConfigSnapshot;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Turns an enchantment level into the exact emerald payment for a librarian trade.
 *
 * <p>The manager is a thin orchestration layer over {@link PriceCalculator},
 * {@link DiscountManager} and {@link TradeValidator}. It resolves the current
 * configuration on every call so a reload takes effect immediately without
 * rebuilding any service.</p>
 */
public final class BookPricingManager {

    private final Supplier<ConfigSnapshot> snapshotSupplier;

    /**
     * Creates the manager.
     *
     * @param snapshotSupplier supplier of the active configuration snapshot
     */
    public BookPricingManager(Supplier<ConfigSnapshot> snapshotSupplier) {
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
    }

    /**
     * Prices a book.
     *
     * @param enchantmentKey canonical enchantment key
     * @param level          enchantment level
     * @param heroAmplifier  Hero of the Village amplifier of the payer, {@code 0} when unknown
     * @return the priced trade
     * @throws PriceNotConfiguredException when no price can be resolved
     * @throws IllegalStateException       when the resulting payment cannot be represented
     */
    public PricedTrade price(String enchantmentKey, int level, int heroAmplifier) {
        ConfigSnapshot snapshot = snapshotSupplier.get();
        PriceCalculator calculator = new PriceCalculator(snapshot.pricing());
        int basePrice = calculator.basePrice(enchantmentKey, level);
        if (basePrice < 1) {
            throw new IllegalStateException("A merchant trade must require at least one emerald, but "
                    + enchantmentKey + " level " + level + " resolved to " + basePrice
                    + ". Disable limits.allow-zero or raise the price.");
        }

        DiscountManager discountManager = new DiscountManager(snapshot.discounts());
        DiscountResult discount = discountManager.evaluate(basePrice, heroAmplifier,
                snapshot.pricing().minimumPrice());

        PaymentPlan plan;
        try {
            plan = PaymentPlan.of(discount.finalPrice());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot build a payment for " + enchantmentKey + " level " + level
                    + ": " + exception.getMessage(), exception);
        }

        TradeValidator.Result validation = new TradeValidator().validatePayment(
                plan, snapshot.pricing().minimumPrice(), snapshot.pricing().maximumPrice());
        if (!validation.valid()) {
            throw new IllegalStateException("Rejected trade for " + enchantmentKey + " level " + level
                    + ": " + validation.summary());
        }

        return new PricedTrade(enchantmentKey, level, basePrice, plan.total(),
                discount.appliedPercentage(), plan, discountManager.suppressesVanillaDiscounts(),
                discount.reason());
    }

    /**
     * Returns the base price without any discount, used by commands and the public API.
     *
     * @param enchantmentKey canonical enchantment key
     * @param level          enchantment level
     * @return the base price
     * @throws PriceNotConfiguredException when no price can be resolved
     */
    public int basePrice(String enchantmentKey, int level) {
        return new PriceCalculator(snapshotSupplier.get().pricing()).basePrice(enchantmentKey, level);
    }

    /**
     * A priced book trade.
     *
     * @param enchantmentKey     canonical enchantment key
     * @param level              enchantment level
     * @param basePrice          configured price before discounts
     * @param finalPrice         price the player pays
     * @param discountPercentage plugin discount percentage that was applied
     * @param plan               emerald payment split across merchant slots
     * @param suppressDiscounts  whether vanilla price adjustments must be blocked
     * @param reason             explanation used by diagnostics
     */
    public record PricedTrade(
            String enchantmentKey,
            int level,
            int basePrice,
            int finalPrice,
            int discountPercentage,
            PaymentPlan plan,
            boolean suppressDiscounts,
            String reason) {
    }
}
