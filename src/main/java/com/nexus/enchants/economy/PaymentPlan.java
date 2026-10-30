package com.nexus.enchants.economy;

import com.nexus.enchants.util.PriceMath;
import java.util.ArrayList;
import java.util.List;

/**
 * A validated emerald payment split across the merchant input slots.
 *
 * <p>A vanilla merchant screen accepts two input stacks of at most 64 items, so
 * a price above 64 must be paid as {@code 64 + remainder}. Plans are pure data:
 * {@link #of(int)} never touches the Bukkit API, which keeps the arithmetic
 * unit testable.</p>
 *
 * @param total       total emerald amount
 * @param slotAmounts amounts per input slot, in slot order
 */
public record PaymentPlan(int total, List<Integer> slotAmounts) {

    /**
     * Validates the plan.
     *
     * @param total       total emerald amount
     * @param slotAmounts amounts per input slot
     */
    public PaymentPlan {
        if (total < 1) {
            throw new IllegalArgumentException("A payment must be at least 1 emerald, got " + total);
        }
        if (!PriceMath.isRepresentable(total)) {
            throw new IllegalArgumentException("A single trade cannot require more than "
                    + PriceMath.MAX_REPRESENTABLE_PRICE + " emeralds, got " + total);
        }
        slotAmounts = List.copyOf(slotAmounts);
        int sum = 0;
        for (int amount : slotAmounts) {
            if (amount < 1 || amount > PriceMath.EMERALD_STACK_SIZE) {
                throw new IllegalArgumentException("Slot amount out of range: " + amount);
            }
            sum += amount;
        }
        if (sum != total) {
            throw new IllegalArgumentException("Slot amounts " + slotAmounts + " do not sum to " + total);
        }
        if (slotAmounts.size() > 2) {
            throw new IllegalArgumentException("A merchant trade has at most two input slots");
        }
    }

    /**
     * Splits a total price into merchant input slots.
     *
     * @param total total emerald amount
     * @return the payment plan
     * @throws IllegalArgumentException when the amount cannot be paid in one trade
     */
    public static PaymentPlan of(int total) {
        if (total < 1) {
            throw new IllegalArgumentException("A payment must be at least 1 emerald, got " + total);
        }
        if (!PriceMath.isRepresentable(total)) {
            throw new IllegalArgumentException("A single trade cannot require more than "
                    + PriceMath.MAX_REPRESENTABLE_PRICE + " emeralds, got " + total);
        }
        List<Integer> slots = new ArrayList<>(2);
        int remaining = total;
        while (remaining > 0) {
            int chunk = Math.min(remaining, PriceMath.EMERALD_STACK_SIZE);
            slots.add(chunk);
            remaining -= chunk;
        }
        return new PaymentPlan(total, slots);
    }

    /**
     * Returns whether more than one input slot is used.
     *
     * @return {@code true} when the payment is split
     */
    public boolean isSplit() {
        return slotAmounts.size() > 1;
    }

    /**
     * Returns the amount required in the first input slot.
     *
     * @return the primary amount
     */
    public int primaryAmount() {
        return slotAmounts.get(0);
    }

    /**
     * Returns the amount required in the second input slot.
     *
     * @return the secondary amount, {@code 0} when the payment is not split
     */
    public int secondaryAmount() {
        return slotAmounts.size() > 1 ? slotAmounts.get(1) : 0;
    }
}
