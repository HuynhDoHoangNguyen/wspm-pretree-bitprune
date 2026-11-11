package vn.edu.wspm.statistics;

/** Raw statistics for one item identifier; no weight is calculated at this stage. */
public record ItemStatistics(
        int itemId,
        long occurrenceCount,
        int sequenceSupport,
        long sumOfContainingSequenceLengths) {

    public ItemStatistics {
        if (itemId < 0) {
            throw new IllegalArgumentException("item id must not be negative: " + itemId);
        }
        if (occurrenceCount < 0) {
            throw new IllegalArgumentException("occurrence count must not be negative");
        }
        if (sequenceSupport < 0) {
            throw new IllegalArgumentException("sequence support must not be negative");
        }
        if (sumOfContainingSequenceLengths < 0) {
            throw new IllegalArgumentException(
                    "sum of containing sequence lengths must not be negative");
        }
    }
}
