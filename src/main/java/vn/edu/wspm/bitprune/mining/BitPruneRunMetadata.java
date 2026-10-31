package vn.edu.wspm.bitprune.mining;

import java.util.Objects;
import java.util.OptionalDouble;
import vn.edu.wspm.config.ResolvedMinimumSupport;

/**
 * Immutable metadata carrier for a completed WSPM_BitPrune execution (C15.0).
 *
 * <p>Encapsulates database-level scientific parameters computed during Scope 2 Preprocessing:
 * <ul>
 *   <li>The number of distinct items present in the database;</li>
 *   <li>The global MeanW constant (present for non-empty databases, empty for zero-sequence databases);</li>
 *   <li>The resolved minimum support configuration.</li>
 * </ul>
 * </p>
 *
 * <p>This record does not retain any transient bitwise, positional, or index structures.</p>
 */
public record BitPruneRunMetadata(
        int distinctItemCount,
        OptionalDouble globalMeanWeight,
        ResolvedMinimumSupport minimumSupport) {

    public BitPruneRunMetadata {
        Objects.requireNonNull(globalMeanWeight, "globalMeanWeight must not be null");
        Objects.requireNonNull(minimumSupport, "minimumSupport must not be null");

        if (distinctItemCount < 0) {
            throw new IllegalArgumentException("distinctItemCount must not be negative: " + distinctItemCount);
        }

        if (globalMeanWeight.isPresent()) {
            double val = globalMeanWeight.getAsDouble();
            if (!Double.isFinite(val) || val < 0.0) {
                throw new IllegalArgumentException(
                        "present globalMeanWeight must be finite and non-negative; got: " + val);
            }
        }

        if (minimumSupport.numberOfSequences() == 0) {
            if (distinctItemCount != 0) {
                throw new IllegalArgumentException(
                        "zero-sequence metadata must have distinctItemCount == 0; got: " + distinctItemCount);
            }
            if (globalMeanWeight.isPresent()) {
                throw new IllegalArgumentException(
                        "zero-sequence metadata must have empty globalMeanWeight");
            }
        } else {
            if (distinctItemCount <= 0) {
                throw new IllegalArgumentException(
                        "positive-sequence metadata must have positive distinctItemCount; got: " + distinctItemCount);
            }
            if (globalMeanWeight.isEmpty()) {
                throw new IllegalArgumentException(
                        "positive-sequence metadata must have present globalMeanWeight");
            }
        }
    }
}

