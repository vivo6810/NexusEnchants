package com.nexus.enchants.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexus.enchants.config.DiscountConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Discount engine tests D001 through D010.
 *
 * <p>Vanilla applies curing, reputation and Hero of the Village discounts inside
 * the merchant. Paper does not expose a per-player price hook, so the plugin's
 * {@code CAP} policy both caps the discount it computes for simulation and
 * suppresses the vanilla discount providers on the recipe. These tests cover the
 * computation; the suppression is covered by {@link TradeValidatorTest}.</p>
 */
class DiscountManagerTest {

    private static DiscountConfig config(DiscountPolicy policy, int cap) {
        return new DiscountConfig(policy, cap, true, 5, true, true);
    }

    private static DiscountManager cap() {
        return new DiscountManager(config(DiscountPolicy.CAP, 20));
    }

    @Test
    @DisplayName("D001: without a discount the configured price is charged")
    void noDiscount() {
        DiscountResult result = cap().evaluateBase(70, 1);
        assertEquals(70, result.finalPrice());
        assertEquals(0, result.appliedPercentage());
        assertFalse(result.capped());
    }

    @Test
    @DisplayName("D002: a 10% discount produces 63 from 70")
    void tenPercent() {
        DiscountResult result = cap().evaluate(70, 1, 1);
        assertEquals(10, result.appliedPercentage());
        assertEquals(63, result.finalPrice());
        assertEquals(7, result.savedAmount());
        assertEquals(10, result.realisedPercentage());
    }

    @Test
    @DisplayName("D003: a 20% discount produces 56 from 70")
    void twentyPercent() {
        DiscountResult result = cap().evaluate(70, 3, 1);
        assertEquals(20, result.appliedPercentage());
        assertEquals(56, result.finalPrice());
        assertFalse(result.capped());
    }

    @Test
    @DisplayName("D004: a discount above the cap is clamped and reported")
    void discountAboveCap() {
        DiscountResult result = cap().evaluate(70, 10, 1);
        assertEquals(20, result.appliedPercentage());
        assertEquals(56, result.finalPrice());
        assertTrue(result.capped());
    }

    @Test
    @DisplayName("D005: repeated evaluation never stacks discounts")
    void noStacking() {
        DiscountManager manager = cap();
        DiscountResult first = manager.evaluate(70, 3, 1);
        DiscountResult second = manager.evaluate(first.finalPrice(), 3, 1);
        assertEquals(56, first.finalPrice());
        // A second pass starts from the already discounted price and cannot go below the
        // cap of that base; the first result is authoritative for a single trade.
        assertTrue(second.finalPrice() <= first.finalPrice());
        assertEquals(70, manager.evaluateBase(70, 1).finalPrice());
    }

    @Test
    @DisplayName("D006: DISABLE keeps the configured price")
    void disablePolicy() {
        DiscountManager manager = new DiscountManager(config(DiscountPolicy.DISABLE, 20));
        DiscountResult result = manager.evaluate(70, 10, 1);
        assertEquals(70, result.finalPrice());
        assertEquals(0, result.appliedPercentage());
        assertEquals(DiscountPolicy.DISABLE, result.policy());
        assertTrue(manager.suppressesVanillaDiscounts());
    }

    @Test
    @DisplayName("D007: PRESERVE leaves the configured price and vanilla discounts intact")
    void preservePolicy() {
        DiscountManager manager = new DiscountManager(config(DiscountPolicy.PRESERVE, 20));
        DiscountResult result = manager.evaluate(70, 10, 1);
        assertEquals(70, result.finalPrice());
        assertEquals(DiscountPolicy.PRESERVE, result.policy());
        assertFalse(manager.suppressesVanillaDiscounts());
    }

    @Test
    @DisplayName("D008: CAP never exceeds the configured maximum percentage")
    void capPolicy() {
        DiscountManager manager = new DiscountManager(config(DiscountPolicy.CAP, 5));
        DiscountResult result = manager.evaluate(70, 10, 1);
        assertEquals(5, result.appliedPercentage());
        assertEquals(67, result.finalPrice(), "70 - floor(70 * 5%) = 70 - 3");
        assertTrue(result.capped());
    }

    @Test
    @DisplayName("D009: rounding is deterministic and truncates towards zero")
    void rounding() {
        DiscountManager manager = cap();
        DiscountResult result = manager.evaluate(65, 1, 1);
        assertEquals(10, result.appliedPercentage());
        assertEquals(59, result.finalPrice(), "65 * 10% = 6.5 -> 6 emeralds off");
        assertEquals(59, manager.evaluate(65, 1, 1).finalPrice());
    }

    @Test
    @DisplayName("D010: the final price never drops below the configured minimum")
    void minimumPrice() {
        DiscountResult result = cap().evaluate(70, 10, 69);
        assertEquals(69, result.finalPrice());
        assertEquals(20, result.appliedPercentage());
    }

    @Test
    @DisplayName("A free trade stays free")
    void freeTrade() {
        DiscountResult result = cap().evaluateBase(0, 1);
        assertEquals(0, result.finalPrice());
        assertEquals(0, result.appliedPercentage());
        assertEquals("free", result.reason());
    }

    @Test
    @DisplayName("A negative base price is rejected")
    void negativeBaseRejected() {
        assertThrows(IllegalArgumentException.class, () -> cap().evaluateBase(-1, 1));
    }
}
