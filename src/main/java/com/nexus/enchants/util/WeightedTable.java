package com.nexus.enchants.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * Immutable weighted random selection table.
 *
 * <p>Weights are relative and never have to sum to 100. Selection uses
 * {@link Random#nextLong()} together with {@link Math#floorMod(long, long)}, so
 * very large weights cannot overflow or bias the result.</p>
 *
 * @param <T> type of the selectable values
 */
public final class WeightedTable<T> {

    private final List<Entry<T>> entries;
    private final long totalWeight;

    private WeightedTable(List<Entry<T>> entries, long totalWeight) {
        this.entries = List.copyOf(entries);
        this.totalWeight = totalWeight;
    }

    /**
     * Creates an empty table. Selecting from it always yields an empty result.
     *
     * @param <T> value type
     * @return an empty table
     */
    public static <T> WeightedTable<T> empty() {
        return new WeightedTable<>(List.of(), 0L);
    }

    /**
     * Builds a table from an ordered map of value to weight.
     *
     * @param weights weights to add, may be empty
     * @param <T>     value type
     * @return the built table
     * @throws IllegalArgumentException when a weight is negative or the total overflows
     */
    public static <T> WeightedTable<T> of(Map<T, Integer> weights) {
        Builder<T> builder = builder();
        weights.forEach(builder::add);
        return builder.build();
    }

    /**
     * Creates a builder.
     *
     * @param <T> value type
     * @return a new builder
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Selects one value using the supplied random source.
     *
     * @param random random source
     * @return the selected value, or empty when the table has no positive weight
     */
    public Optional<T> pick(Random random) {
        Objects.requireNonNull(random, "random");
        if (totalWeight <= 0L) {
            return Optional.empty();
        }
        long roll = Math.floorMod(random.nextLong(), totalWeight);
        for (Entry<T> entry : entries) {
            roll -= entry.weight();
            if (roll < 0L) {
                return Optional.of(entry.value());
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the probability of a value being selected.
     *
     * @param value value to look up
     * @return the probability in the range {@code 0.0 - 1.0}
     */
    public double probabilityOf(T value) {
        if (totalWeight <= 0L) {
            return 0.0D;
        }
        for (Entry<T> entry : entries) {
            if (entry.value().equals(value)) {
                return (double) entry.weight() / (double) totalWeight;
            }
        }
        return 0.0D;
    }

    /**
     * Returns the configured weight of a value.
     *
     * @param value value to look up
     * @return the weight, or {@code 0} when absent
     */
    public long weightOf(T value) {
        for (Entry<T> entry : entries) {
            if (entry.value().equals(value)) {
                return entry.weight();
            }
        }
        return 0L;
    }

    /**
     * Returns every entry in insertion order.
     *
     * @return immutable entry list
     */
    public List<Entry<T>> entries() {
        return entries;
    }

    /**
     * Returns the sum of all weights.
     *
     * @return the total weight, {@code 0} for an empty table
     */
    public long totalWeight() {
        return totalWeight;
    }

    /**
     * Returns whether the table can select anything.
     *
     * @return {@code true} when at least one entry has a positive weight
     */
    public boolean isEmpty() {
        return totalWeight <= 0L;
    }

    /**
     * Returns the number of entries, including zero-weight entries.
     *
     * @return the entry count
     */
    public int size() {
        return entries.size();
    }

    /**
     * A single weighted value.
     *
     * @param value  the value
     * @param weight the relative weight
     * @param <T>    value type
     */
    public record Entry<T>(T value, long weight) {

        /**
         * Validates the entry.
         *
         * @param value  the value
         * @param weight the relative weight
         */
        public Entry {
            Objects.requireNonNull(value, "value");
            if (weight < 0L) {
                throw new IllegalArgumentException("Weight must not be negative: " + weight);
            }
        }
    }

    /**
     * Mutable builder for {@link WeightedTable}.
     *
     * @param <T> value type
     */
    public static final class Builder<T> {

        private final Map<T, Long> weights = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Adds a weight. A zero weight is stored so it can be reported, but it
         * can never be selected.
         *
         * @param value  the value
         * @param weight the relative weight
         * @return this builder
         * @throws IllegalArgumentException when the weight is negative
         */
        public Builder<T> add(T value, int weight) {
            return add(value, (long) weight);
        }

        /**
         * Adds a weight.
         *
         * @param value  the value
         * @param weight the relative weight
         * @return this builder
         * @throws IllegalArgumentException when the weight is negative
         */
        public Builder<T> add(T value, long weight) {
            Objects.requireNonNull(value, "value");
            if (weight < 0L) {
                throw new IllegalArgumentException("Weight must not be negative: " + weight);
            }
            weights.merge(value, weight, Long::sum);
            return this;
        }

        /**
         * Builds the table.
         *
         * @return the immutable table
         * @throws IllegalArgumentException when the total weight overflows a {@code long}
         */
        public WeightedTable<T> build() {
            List<Entry<T>> entries = new ArrayList<>(weights.size());
            long total = 0L;
            for (Map.Entry<T, Long> entry : weights.entrySet()) {
                long weight = entry.getValue();
                if (total > Long.MAX_VALUE - weight) {
                    throw new IllegalArgumentException("Total weight overflows a long");
                }
                total += weight;
                entries.add(new Entry<>(entry.getKey(), weight));
            }
            return new WeightedTable<>(Collections.unmodifiableList(entries), total);
        }
    }
}
