package vn.edu.wspm.bitprune.mining;

import java.util.Objects;
import vn.edu.wspm.bitprune.measurement.BitPruneAlgorithmMetrics;
import vn.edu.wspm.bitprune.result.BitPruneMiningResult;

/**
 * Immutable carrier holding the completed mining result, algorithm execution metrics,
 * and scientific run metadata for a successful WSPM_BitPrune execution (C13.3, C15.0).
 *
 * <p>Represents a completed successful algorithm run without exposing mutable mining
 * state, sampler references, I/O metrics, or runner configuration.</p>
 */
public record MeasuredBitPruneRun(
        BitPruneMiningResult result,
        BitPruneAlgorithmMetrics metrics,
        BitPruneRunMetadata metadata) {

    public MeasuredBitPruneRun {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");

        if (metadata.minimumSupport().numberOfSequences() != result.statistics().sequenceCount()) {
            throw new IllegalArgumentException(
                    "metadata sequence count (" + metadata.minimumSupport().numberOfSequences()
                    + ") must equal result statistics sequence count ("
                    + result.statistics().sequenceCount() + ")");
        }
    }
}
