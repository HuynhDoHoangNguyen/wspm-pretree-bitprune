package vn.edu.wspm.mining;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import vn.edu.wspm.config.ResolvedMinimumSupport;
import vn.edu.wspm.statistics.ItemWeightResult;
import vn.edu.wspm.statistics.SequenceDatabaseStatistics;

/** Builds the weighted sequential 1-pattern set used to seed WSPM_PreTree. */
public final class F1Builder {

    /**
     * Selects present items using sequenceSupport(item) * global MeanW >= effectiveMinSup.
     * The returned immutable list follows ascending numeric item order.
     */
    public List<WeightedOnePattern> build(
            SequenceDatabaseStatistics statistics,
            ItemWeightResult weights,
            ResolvedMinimumSupport minimumSupport) {
        Objects.requireNonNull(statistics, "sequence database statistics must not be null");
        Objects.requireNonNull(weights, "item weight result must not be null");
        Objects.requireNonNull(minimumSupport, "resolved minimum support must not be null");

        validateConsistentInputs(statistics, weights, minimumSupport);
        if (statistics.itemStatistics().isEmpty()) {
            return List.of();
        }

        double meanWeight = weights.meanWeight().orElseThrow(
                () -> new IllegalArgumentException(
                        "non-empty item statistics require a global MeanW"));
        var acceptedPatterns = new ArrayList<WeightedOnePattern>();

        for (var itemStatistics : statistics.itemStatistics().values()) {
            int support = itemStatistics.sequenceSupport();
            if (minimumSupport.meetsThreshold(support, meanWeight)) {
                acceptedPatterns.add(new WeightedOnePattern(
                        itemStatistics.itemId(), support));
            }
        }

        return List.copyOf(acceptedPatterns);
    }

    private static void validateConsistentInputs(
            SequenceDatabaseStatistics statistics,
            ItemWeightResult weights,
            ResolvedMinimumSupport minimumSupport) {
        if (statistics.numberOfSequences() != minimumSupport.numberOfSequences()) {
            throw new IllegalArgumentException(
                    "statistics and minimum support must use the same sequence count");
        }
        if (!statistics.itemStatistics().keySet().equals(weights.itemWeights().keySet())) {
            throw new IllegalArgumentException(
                    "statistics and item weights must contain the same item identifiers");
        }
        if (statistics.itemStatistics().isEmpty() && weights.meanWeight().isPresent()) {
            throw new IllegalArgumentException(
                    "empty item statistics must not have a fabricated MeanW");
        }
    }
}
