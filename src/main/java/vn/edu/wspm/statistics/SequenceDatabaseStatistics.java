package vn.edu.wspm.statistics;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable item statistics and basic metadata for one sequence database. */
public final class SequenceDatabaseStatistics {

    private final int numberOfSequences;
    private final long totalDatabaseLength;
    private final NavigableMap<Integer, ItemStatistics> itemStatistics;

    public SequenceDatabaseStatistics(
            int numberOfSequences,
            long totalDatabaseLength,
            Map<Integer, ItemStatistics> itemStatistics) {
        if (numberOfSequences < 0) {
            throw new IllegalArgumentException("number of sequences must not be negative");
        }
        if (totalDatabaseLength < 0) {
            throw new IllegalArgumentException("total database length must not be negative");
        }
        Objects.requireNonNull(itemStatistics, "item statistics must not be null");

        var orderedStatistics = new TreeMap<Integer, ItemStatistics>();
        for (var entry : itemStatistics.entrySet()) {
            var itemId = Objects.requireNonNull(entry.getKey(),
                    "item statistics must not contain a null item id");
            var statistics = Objects.requireNonNull(entry.getValue(),
                    "item statistics must not contain a null value");
            if (itemId != statistics.itemId()) {
                throw new IllegalArgumentException(
                        "item statistics key does not match value item id: " + itemId);
            }
            orderedStatistics.put(itemId, statistics);
        }

        this.numberOfSequences = numberOfSequences;
        this.totalDatabaseLength = totalDatabaseLength;
        this.itemStatistics = Collections.unmodifiableNavigableMap(orderedStatistics);
    }

    public int numberOfSequences() {
        return numberOfSequences;
    }

    public int numberOfDistinctItems() {
        return itemStatistics.size();
    }

    public long totalDatabaseLength() {
        return totalDatabaseLength;
    }

    /** Returns all present items in ascending numeric identifier order. */
    public NavigableMap<Integer, ItemStatistics> itemStatistics() {
        return itemStatistics;
    }

    /** Returns an empty optional when a valid item identifier is absent from the database. */
    public Optional<ItemStatistics> statisticsFor(int itemId) {
        if (itemId < 0) {
            throw new IllegalArgumentException("item id must not be negative: " + itemId);
        }
        return Optional.ofNullable(itemStatistics.get(itemId));
    }
}
