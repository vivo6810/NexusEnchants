package com.nexus.enchants.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexus.enchants.config.PricingConfig;
import com.nexus.enchants.util.PriceMath;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pricing engine tests P001 through P012.
 *
 * <p>The configuration mirrors the documented defaults from {@code pricing.yml}
 * so a change to the shipped defaults is caught here as well as in
 * {@code ResourceIntegrityTest}.</p>
 */
class PriceCalculatorTest {

    private static PricingConfig defaults() {
        Map<String, Map<Integer, Integer>> prices = new LinkedHashMap<>();
        prices.put("MENDING", Map.of(1, 70));
        prices.put("UNBREAKING", Map.of(1, 20, 2, 30, 3, 45));
        prices.put("EFFICIENCY", Map.of(1, 10, 2, 18, 3, 27, 4, 40, 5, 55));
        prices.put("SHARPNESS", Map.of(1, 10, 2, 18, 3, 27, 4, 38, 5, 50));
        prices.put("PROTECTION", Map.of(1, 10, 2, 18, 3, 28, 4, 45));
        prices.put("FORTUNE", Map.of(1, 20, 2, 35, 3, 50));
        prices.put("SILK_TOUCH", Map.of(1, 40));
        prices.put("LOOTING", Map.of(1, 15, 2, 30, 3, 45));
        prices.put("FEATHER_FALLING", Map.of(1, 10, 2, 18, 3, 27, 4, 35));
        prices.put("THORNS", Map.of(1, 15, 2, 25, 3, 40));
        return new PricingConfig(prices, false, 0, 1, PriceMath.MAX_REPRESENTABLE_PRICE, false);
    }

    private static PriceCalculator calculator() {
        return new PriceCalculator(defaults());
    }

    @Test
    @DisplayName("P001: Mending I costs 70 emeralds")
    void mendingPrice() {
        assertEquals(70, calculator().basePrice("MENDING", 1));
    }

    @Test
    @DisplayName("P002: Unbreaking III costs 45 emeralds")
    void unbreakingThree() {
        assertEquals(45, calculator().basePrice("UNBREAKING", 3));
    }

    @Test
    @DisplayName("P003: Efficiency V costs 55 emeralds")
    void efficiencyFive() {
        assertEquals(55, calculator().basePrice("EFFICIENCY", 5));
    }

    @Test
    @DisplayName("P004: a missing price falls back to the configured fallback")
    void fallbackPrice() {
        PricingConfig fallback = new PricingConfig(Map.of(), true, 25, 1, 128, false);
        assertEquals(25, new PriceCalculator(fallback).basePrice("UNKNOWN_ENCHANT", 1));
        assertFalse(new PriceCalculator(fallback).hasExplicitPrice("UNKNOWN_ENCHANT", 1));
    }

    @Test
    @DisplayName("P005: an enchantment without a price and without fallback is rejected")
    void missingPriceWithoutFallback() {
        PriceCalculator calculator = calculator();
        PriceNotConfiguredException exception = assertThrows(PriceNotConfiguredException.class,
                () -> calculator.basePrice("MENDING", 2));
        assertTrue(exception.getMessage().contains("MENDING"));
    }

    @Test
    @DisplayName("P006: a negative price is clamped to the minimum, never offered")
    void negativePriceClamped() {
        PricingConfig config = new PricingConfig(Map.of("MENDING", Map.of(1, -40)), false, 0, 1, 128, false);
        assertEquals(1, new PriceCalculator(config).basePrice("MENDING", 1));
    }

    @Test
    @DisplayName("P007: zero is preserved only when explicitly allowed")
    void zeroPrice() {
        PricingConfig free = new PricingConfig(Map.of("MENDING", Map.of(1, 0)), false, 0, 1, 128, true);
        assertEquals(0, new PriceCalculator(free).basePrice("MENDING", 1));

        PricingConfig paid = new PricingConfig(Map.of("MENDING", Map.of(1, 0)), false, 0, 1, 128, false);
        assertEquals(1, new PriceCalculator(paid).basePrice("MENDING", 1));
    }

    @Test
    @DisplayName("P008: a price above the configured maximum is rejected by clamping")
    void priceAboveMaximum() {
        PricingConfig capped = new PricingConfig(Map.of("MENDING", Map.of(1, 500)), false, 0, 1, 128, false);
        assertEquals(128, new PriceCalculator(capped).basePrice("MENDING", 1));

        PricingConfig tight = new PricingConfig(Map.of("MENDING", Map.of(1, 500)), false, 0, 1, 64, false);
        assertEquals(64, new PriceCalculator(tight).basePrice("MENDING", 1));
    }

    @Test
    @DisplayName("P009: a price above one stack becomes a two-slot payment plan")
    void priceAboveStackSize() {
        PriceCalculator calculator = calculator();
        assertEquals(70, calculator.basePrice("MENDING", 1));
        PaymentPlan plan = calculator.basePayment("MENDING", 1);
        assertEquals(70, plan.total());
        assertEquals(64, plan.primaryAmount());
        assertEquals(6, plan.secondaryAmount());
        assertTrue(plan.isSplit());
    }

    @Test
    @DisplayName("P010: an overflowing price is clamped instead of wrapping negative")
    void integerOverflow() {
        PricingConfig huge = new PricingConfig(Map.of("MENDING", Map.of(1, Integer.MAX_VALUE)),
                false, 0, 1, 128, false);
        assertEquals(128, new PriceCalculator(huge).basePrice("MENDING", 1));
    }

    @Test
    @DisplayName("P011: changed configuration produces new prices without mutating the old snapshot")
    void reloadIsImmutable() {
        PricingConfig before = defaults();
        PriceCalculator calculator = new PriceCalculator(before);
        assertEquals(70, calculator.basePrice("MENDING", 1));

        PricingConfig after = before.withPrice("MENDING", 1, 99);
        assertEquals(99, new PriceCalculator(after).basePrice("MENDING", 1));
        assertEquals(70, calculator.basePrice("MENDING", 1), "the old calculator must not change");
        assertEquals(OptionalInt.of(70), before.explicitPrice("MENDING", 1));
    }

    @Test
    @DisplayName("P012: each level has its own price")
    void perLevelPrices() {
        PriceCalculator calculator = calculator();
        assertEquals(20, calculator.basePrice("UNBREAKING", 1));
        assertEquals(30, calculator.basePrice("UNBREAKING", 2));
        assertEquals(45, calculator.basePrice("UNBREAKING", 3));
        assertEquals(10, calculator.basePrice("EFFICIENCY", 1));
        assertEquals(55, calculator.basePrice("EFFICIENCY", 5));
    }

    @Test
    @DisplayName("P012: an invalid level is rejected before it can be priced")
    void invalidLevel() {
        PriceCalculator calculator = calculator();
        assertThrows(IllegalArgumentException.class, () -> calculator.basePrice("MENDING", 0));
        assertThrows(IllegalArgumentException.class, () -> calculator.basePrice("MENDING", -1));
        assertThrows(NullPointerException.class, () -> calculator.basePrice(null, 1));
    }
}
