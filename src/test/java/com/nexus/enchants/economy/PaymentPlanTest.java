package com.nexus.enchants.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Payment planning and pre-flight validation.
 *
 * <p>A merchant recipe has two input slots, so a price above one stack must be
 * split. This is the arithmetic half of M001-M004; whether the client can
 * actually complete the trade is a real-server concern documented in
 * docs/TESTING.md.</p>
 */
class PaymentPlanTest {

    @Test
    @DisplayName("M001: 70 emeralds are split into 64 + 6")
    void seventySplits() {
        PaymentPlan plan = PaymentPlan.of(70);
        assertEquals(70, plan.total());
        assertEquals(64, plan.primaryAmount());
        assertEquals(6, plan.secondaryAmount());
        assertEquals(List.of(64, 6), plan.slotAmounts());
        assertTrue(plan.isSplit());
    }

    @Test
    @DisplayName("A price of one stack or less uses a single slot")
    void singleSlot() {
        PaymentPlan plan = PaymentPlan.of(45);
        assertEquals(List.of(45), plan.slotAmounts());
        assertEquals(0, plan.secondaryAmount());
        assertFalse(plan.isSplit());
        assertEquals(List.of(1), PaymentPlan.of(1).slotAmounts());
        assertEquals(List.of(64), PaymentPlan.of(64).slotAmounts());
    }

    @Test
    @DisplayName("The largest representable price is 64 + 64")
    void maximumPrice() {
        PaymentPlan plan = PaymentPlan.of(128);
        assertEquals(List.of(64, 64), plan.slotAmounts());
        assertEquals(128, plan.total());
    }

    @Test
    @DisplayName("A price that needs a third slot is rejected instead of silently truncated")
    void tooLarge() {
        assertThrows(IllegalArgumentException.class, () -> PaymentPlan.of(129));
        assertThrows(IllegalArgumentException.class, () -> PaymentPlan.of(1000));
        assertThrows(IllegalArgumentException.class, () -> PaymentPlan.of(0));
        assertThrows(IllegalArgumentException.class, () -> PaymentPlan.of(-5));
    }

    @Test
    @DisplayName("Hand-built plans are validated")
    void planValidation() {
        assertThrows(IllegalArgumentException.class, () -> new PaymentPlan(70, List.of(64)));
        assertThrows(IllegalArgumentException.class, () -> new PaymentPlan(20, List.of(10)));
        assertThrows(IllegalArgumentException.class, () -> new PaymentPlan(10, List.of(5, 5, 0)));
        assertThrows(IllegalArgumentException.class, () -> new PaymentPlan(130, List.of(65, 65)));
    }

    @Test
    @DisplayName("M002-M004: validation reports every problem with a price")
    void validation() {
        TradeValidator validator = new TradeValidator();

        TradeValidator.Result ok = validator.validatePrice(70, 1, 128);
        assertTrue(ok.valid());
        assertEquals("", ok.summary());

        TradeValidator.Result tooCheap = validator.validatePrice(0, 1, 128);
        assertFalse(tooCheap.valid());
        assertTrue(tooCheap.summary().contains("below the configured minimum"));

        TradeValidator.Result tooExpensive = validator.validatePrice(500, 1, 128);
        assertFalse(tooExpensive.valid());

        TradeValidator.Result negative = validator.validatePrice(-1, -5, 128);
        assertFalse(negative.valid());
        assertEquals(2, negative.problems().size(), "negative and unrepresentable");

        assertFalse(validator.validatePayment(PaymentPlan.of(128), 1, 100).valid());
        assertTrue(validator.validatePayment(PaymentPlan.of(128), 1, 128).valid());
    }

    @Test
    @DisplayName("An impossible minimum/maximum pair is reported")
    void invertedRange() {
        TradeValidator.Result result = new TradeValidator().validatePrice(10, 50, 5);
        assertFalse(result.valid());
        assertTrue(result.summary().contains("greater than maximum"));
    }
}
