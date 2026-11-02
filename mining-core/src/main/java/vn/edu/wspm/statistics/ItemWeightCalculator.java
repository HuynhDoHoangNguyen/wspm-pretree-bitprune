package vn.edu.wspm.statistics;

import java.util.Objects;
import java.util.TreeMap;

/** Calculates item weights and the MaxW, MinW, and MeanW summary from B5 statistics. */
public final class ItemWeightCalculator {

    /**
     * Calculates {@code W(i) = occurrenceCount(i) / totalDatabaseLength}.
     *
     * <p>B6 source decision: the global denominator reproduces WSPM_PreTree Table 2 and matches
     * the formula in its cited SPMW source. No displayed decimal rounding is applied.</p>
     */
    public ItemWeightResult calculate(SequenceDatabaseStatistics statistics) {
        Objects.requireNonNull(statistics, "sequence database statistics must not be null");

        if (statistics.itemStatistics().isEmpty()) {
            if (statistics.totalDatabaseLength() != 0) {
                throw new IllegalArgumentException(
                        "database without item statistics must have total length zero");
            }
            return ItemWeightResult.empty();
        }
        if (statistics.numberOfSequences() == 0) {
            throw new IllegalArgumentException(
                    "item statistics require at least one sequence");
        }
        if (statistics.totalDatabaseLength() <= 0) {
            throw new IllegalArgumentException(
                    "item weights require a positive total database length");
        }

        var weights = new TreeMap<Integer, Double>();
        double maxWeight = Double.NEGATIVE_INFINITY;
        double minWeight = Double.POSITIVE_INFINITY;

        for (var itemStatistics : statistics.itemStatistics().values()) {
            validatePresentItem(itemStatistics, statistics.totalDatabaseLength());
            double weight = (double) itemStatistics.occurrenceCount()
                    / statistics.totalDatabaseLength();
            if (!Double.isFinite(weight)) {
                throw new IllegalArgumentException(
                        "item weight must be finite for item " + itemStatistics.itemId());
            }

            weights.put(itemStatistics.itemId(), weight);
            maxWeight = Math.max(maxWeight, weight);
            minWeight = Math.min(minWeight, weight);
        }

        double meanWeight = (maxWeight + minWeight) / 2.0;
        return ItemWeightResult.of(weights, maxWeight, minWeight, meanWeight);
    }

    private static void validatePresentItem(
            ItemStatistics statistics, long totalDatabaseLength) {
        if (statistics.occurrenceCount() <= 0) {
            throw new IllegalArgumentException(
                    "present item must have a positive occurrence count: " + statistics.itemId());
        }
        if (statistics.sumOfContainingSequenceLengths() <= 0) {
            throw new IllegalArgumentException(
                    "present item must have a positive containing-length sum: "
                            + statistics.itemId());
        }
        if (statistics.occurrenceCount() > totalDatabaseLength) {
            throw new IllegalArgumentException(
                    "item occurrence count exceeds total database length: "
                            + statistics.itemId());
        }
    }
}
