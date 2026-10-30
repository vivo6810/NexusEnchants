package com.nexus.enchants.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexus.enchants.util.WeightedTable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Statistical verification of weighted selection.
 *
 * <p>Every test in this class performs at least one million selections and is
 * tagged {@code statistical}, so it runs in the dedicated {@code statisticalTest}
 * Gradle task (wired into {@code check}) instead of the fast unit-test task.</p>
 *
 * <p>Goodness of fit is measured with Pearson's chi-square statistic and compared
 * against the critical value for a 0.1% significance level, which keeps the suite
 * from failing on ordinary random variation while still catching a genuinely
 * wrong distribution.</p>
 */
@Tag("statistical")
class StatisticalDistributionTest {

    private static final int ITERATIONS = 1_000_000;

    /** Two categories, df = 1, p = 0.001. */
    private static final double CRITICAL_DF1 = 10.828D;

    /** Three categories, df = 2, p = 0.001. */
    private static final double CRITICAL_DF2 = 13.816D;

    /** Five categories, df = 4, p = 0.001. */
    private static final double CRITICAL_DF4 = 18.467D;

    @Test
    @DisplayName("Mending weight 2 versus Unbreaking weight 12 matches 14.2857% / 85.7143%")
    void mendingVersusUnbreaking() {
        WeightedTable<String> table = WeightedTable.<String>builder()
                .add("MENDING", 2)
                .add("UNBREAKING", 12)
                .build();
        Map<String, Integer> counts = count(table, new Random(20240101L), ITERATIONS);

        double mending = counts.getOrDefault("MENDING", 0) / (double) ITERATIONS;
        double unbreaking = counts.getOrDefault("UNBREAKING", 0) / (double) ITERATIONS;

        // Deterministic probability model.
        assertEquals(2.0D / 14.0D, table.probabilityOf("MENDING"), 1.0e-12);
        assertEquals(12.0D / 14.0D, table.probabilityOf("UNBREAKING"), 1.0e-12);

        // Empirical frequency with a 0.1% two-sided normal approximation band.
        assertEquals(0.142857D, mending, 0.0012D);
        assertEquals(0.857143D, unbreaking, 0.0012D);

        double chiSquare = chiSquare(counts, table, ITERATIONS);
        assertTrue(chiSquare < CRITICAL_DF1,
                "chi-square " + chiSquare + " exceeds " + CRITICAL_DF1);
    }

    @Test
    @DisplayName("Equal weights produce an approximately uniform distribution")
    void equalWeights() {
        WeightedTable<String> table = WeightedTable.<String>builder()
                .add("A", 10)
                .add("B", 10)
                .add("C", 10)
                .build();
        Map<String, Integer> counts = count(table, new Random(777L), ITERATIONS);
        double chiSquare = chiSquare(counts, table, ITERATIONS);
        assertTrue(chiSquare < CRITICAL_DF2, "chi-square " + chiSquare + " exceeds " + CRITICAL_DF2);
    }

    @Test
    @DisplayName("A zero-weight entry is never selected in a million rolls")
    void zeroWeightExcluded() {
        WeightedTable<String> table = WeightedTable.<String>builder()
                .add("NEVER", 0)
                .add("ALWAYS", 1)
                .build();
        Map<String, Integer> counts = count(table, new Random(99L), ITERATIONS);
        assertEquals(0, counts.getOrDefault("NEVER", 0));
        assertEquals(ITERATIONS, counts.getOrDefault("ALWAYS", 0));
    }

    @Test
    @DisplayName("A disabled enchantment is never selected in a million rolls")
    void disabledExcluded() {
        WeightedTable<String> table = WeightedTable.<String>builder()
                .add("DISABLED_TREASURE", 0)
                .add("UNBREAKING", 35)
                .add("EFFICIENCY", 40)
                .build();
        Map<String, Integer> counts = count(table, new Random(1234L), ITERATIONS);
        assertEquals(0, counts.getOrDefault("DISABLED_TREASURE", 0));
        assertTrue(counts.getOrDefault("UNBREAKING", 0) > 0);
        assertTrue(counts.getOrDefault("EFFICIENCY", 0) > 0);
        assertTrue(chiSquare(counts, table, ITERATIONS) < CRITICAL_DF2);
    }

    @Test
    @DisplayName("Very large weights keep their ratio")
    void largeWeightsNormalise() {
        WeightedTable<String> table = WeightedTable.<String>builder()
                .add("SMALL", 1_000_000)
                .add("LARGE", 3_000_000)
                .build();
        Map<String, Integer> counts = count(table, new Random(555L), ITERATIONS);
        double small = counts.getOrDefault("SMALL", 0) / (double) ITERATIONS;
        assertEquals(0.25D, small, 0.002D);
        assertTrue(chiSquare(counts, table, ITERATIONS) < CRITICAL_DF1);
    }

    @Test
    @DisplayName("The level weights 45/30/15/7/3 match the configured distribution")
    void levelWeights() {
        WeightedTable<Integer> table = WeightedTable.<Integer>builder()
                .add(1, 45)
                .add(2, 30)
                .add(3, 15)
                .add(4, 7)
                .add(5, 3)
                .build();
        Map<String, Integer> counts = countLevels(table, new Random(31337L), ITERATIONS);

        assertEquals(0.45D, counts.getOrDefault("1", 0) / (double) ITERATIONS, 0.002D);
        assertEquals(0.03D, counts.getOrDefault("5", 0) / (double) ITERATIONS, 0.001D);

        double chiSquare = 0.0D;
        for (WeightedTable.Entry<Integer> entry : table.entries()) {
            double expected = table.probabilityOf(entry.value()) * ITERATIONS;
            double observed = counts.getOrDefault(String.valueOf(entry.value()), 0);
            chiSquare += squaredDifference(observed, expected) / expected;
        }
        assertTrue(chiSquare < CRITICAL_DF4, "chi-square " + chiSquare + " exceeds " + CRITICAL_DF4);
    }

    @Test
    @DisplayName("A seeded run is reproducible over a million rolls")
    void deterministicSeed() {
        WeightedTable<String> table = WeightedTable.<String>builder()
                .add("A", 2)
                .add("B", 12)
                .build();
        Map<String, Integer> first = count(table, new Random(424242L), 1_000_000);
        Map<String, Integer> second = count(table, new Random(424242L), 1_000_000);
        assertEquals(first, second);
    }

    private static Map<String, Integer> count(WeightedTable<String> table, Random random, int iterations) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int index = 0; index < iterations; index++) {
            String value = table.pick(random).orElseThrow(
                    () -> new IllegalStateException("the table produced no result"));
            counts.merge(value, 1, Integer::sum);
        }
        return counts;
    }

    private static Map<String, Integer> countLevels(WeightedTable<Integer> table, Random random, int iterations) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int index = 0; index < iterations; index++) {
            Integer value = table.pick(random).orElseThrow(
                    () -> new IllegalStateException("the table produced no result"));
            counts.merge(String.valueOf(value), 1, Integer::sum);
        }
        return counts;
    }

    private static double chiSquare(Map<String, Integer> counts, WeightedTable<String> table, int iterations) {
        double chiSquare = 0.0D;
        for (WeightedTable.Entry<String> entry : table.entries()) {
            double expected = table.probabilityOf(entry.value()) * iterations;
            if (expected <= 0.0D) {
                continue;
            }
            double observed = counts.getOrDefault(entry.value(), 0);
            chiSquare += squaredDifference(observed, expected) / expected;
        }
        return chiSquare;
    }

    private static double squaredDifference(double observed, double expected) {
        double difference = observed - expected;
        return difference * difference;
    }
}
