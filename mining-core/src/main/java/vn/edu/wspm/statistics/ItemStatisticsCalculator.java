package vn.edu.wspm.statistics;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.TreeMap;
import vn.edu.wspm.model.SequenceDatabase;

/** Calculates raw item statistics with one conceptual traversal over all item occurrences. */
public final class ItemStatisticsCalculator {

    public SequenceDatabaseStatistics calculate(SequenceDatabase database) {
        Objects.requireNonNull(database, "sequence database must not be null");

        var accumulators = new HashMap<Integer, MutableItemStatistics>();
        long totalDatabaseLength = 0;

        for (var record : database.records()) {
            var itemIdsInSequence = new HashSet<Integer>();
            long sequenceLength = 0;

            for (var itemset : record.sequence().itemsets()) {
                sequenceLength = Math.addExact(sequenceLength, itemset.size());
                for (var item : itemset.items()) {
                    var accumulator = accumulators.computeIfAbsent(
                            item.id(), ignored -> new MutableItemStatistics());
                    accumulator.occurrenceCount = Math.incrementExact(accumulator.occurrenceCount);
                    itemIdsInSequence.add(item.id());
                }
            }

            totalDatabaseLength = Math.addExact(totalDatabaseLength, sequenceLength);
            for (var itemId : itemIdsInSequence) {
                var accumulator = accumulators.get(itemId);
                accumulator.sequenceSupport = Math.incrementExact(accumulator.sequenceSupport);
                accumulator.sumOfContainingSequenceLengths = Math.addExact(
                        accumulator.sumOfContainingSequenceLengths, sequenceLength);
            }
        }

        var resultByItem = new TreeMap<Integer, ItemStatistics>();
        for (var entry : accumulators.entrySet()) {
            var value = entry.getValue();
            resultByItem.put(entry.getKey(), new ItemStatistics(
                    entry.getKey(),
                    value.occurrenceCount,
                    value.sequenceSupport,
                    value.sumOfContainingSequenceLengths));
        }

        return new SequenceDatabaseStatistics(
                database.size(), totalDatabaseLength, resultByItem);
    }

    private static final class MutableItemStatistics {
        private long occurrenceCount;
        private int sequenceSupport;
        private long sumOfContainingSequenceLengths;
    }
}
