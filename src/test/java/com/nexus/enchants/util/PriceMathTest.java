package com.nexus.enchants.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Price arithmetic, including the overflow and rounding cases required by
 * P009, P010 and D009.
 */
class PriceMathTest {

    @Test
    @DisplayName("P009: a price above 64 is preserved as an integer")
    void priceAboveStackSizeIsPreserved() {
        assertEquals(70, PriceMath.clamp(70, 1, PriceMath.MAX_REPRESENTABLE_PRICE));
        assertTrue(PriceMath.isRepresentable(70));
        assertEquals(70, PriceMath.applyDiscount(70, 0, 1));
    }

    @Test
    @DisplayName("P010: an overflowing configured price is clamped, never wrapped")
    void overflowIsClamped() {
        assertEquals(PriceMath.MAX_REPRESENTABLE_PRICE,
                PriceMath.clamp(Integer.MAX_VALUE, 1, PriceMath.MAX_REPRESENTABLE_PRICE));
        assertEquals(1, PriceMath.clamp(Integer.MIN_VALUE, 1, PriceMath.MAX_REPRESENTABLE_PRICE));
        // 2_147_483_647 * 100 must not overflow the int multiplication.
        assertEquals(Integer.MAX_VALUE, PriceMath.discountAmount(Integer.MAX_VALUE, 100));
    }

    @Test
    @DisplayName("D009: discounts round down deterministically")
    void discountRoundingIsDown() {
        assertEquals(6, PriceMath.discountAmount(65, 10));
        assertEquals(7, PriceMath.discountAmount(65, 10, PriceMath.Rounding.UP));
        assertEquals(0, PriceMath.discountAmount(9, 10));
        assertEquals(1, PriceMath.discountAmount(9, 10, PriceMath.Rounding.UP));
        assertEquals(14, PriceMath.discountAmount(70, 20));
    }

    @Test
    @DisplayName("D010: the discounted price never drops below the minimum")
    void discountRespectsMinimum() {
        assertEquals(69, PriceMath.applyDiscount(70, 20, 69));
        assertEquals(56, PriceMath.applyDiscount(70, 20, 1));
        assertEquals(70, PriceMath.applyDiscount(70, 20, 70));
    }

    @Test
    @DisplayName("A discount can never exceed the base price")
    void discountNeverExceedsBase() {
        // A 1000% discount is capped at the whole price, which leaves a price of 0.
        assertEquals(5, PriceMath.discountAmount(5, 1000));
        assertEquals(0, PriceMath.applyDiscount(5, 1000, 0));
        assertEquals(0, PriceMath.discountAmount(0, 50));
        assertEquals(0, PriceMath.discountAmount(-10, 50));
    }

    @Test
    @DisplayName("Only 0..128 emeralds can be expressed by one merchant trade")
    void representableRange() {
        assertTrue(PriceMath.isRepresentable(0));
        assertTrue(PriceMath.isRepresentable(128));
        assertFalse(PriceMath.isRepresentable(129));
        assertFalse(PriceMath.isRepresentable(-1));
    }

    @Test
    @DisplayName("clamp rejects an inverted range")
    void clampRejectsInvertedRange() {
        assertThrows(IllegalArgumentException.class, () -> PriceMath.clamp(5, 10, 1));
    }
}
