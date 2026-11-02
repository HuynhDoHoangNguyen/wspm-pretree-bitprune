package vn.edu.wspm.bitprune.experiments;

import java.util.Objects;
import vn.edu.wspm.bitprune.measurement.BitPruneExecutionMetrics;
import vn.edu.wspm.bitprune.mining.BitPruneRunMetadata;
import vn.edu.wspm.bitprune.result.BitPruneMiningResult;
import vn.edu.wspm.bitprune.result.BitPruneOutputProjection;

/**
 * Immutable carrier holding the completed mining result, full execution metrics,
 * scientific run metadata, and deterministic output projection for a complete
 * standalone execution of WSPM_BitPrune (C15.2).
 *
 * <p>Represents a completed successful standalone execution without retaining
 * parser, writer, database, sampler, or mutable mining state references.</p>
 */
public record BitPruneExecutionRun(
        BitPruneMiningResult result,
        BitPruneExecutionMetrics metrics,
        BitPruneRunMetadata metadata,
        BitPruneOutputProjection projection) {

    public BitPruneExecutionRun {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(projection, "projection must not be null");

        if (metadata.minimumSupport().numberOfSequences() != result.statistics().sequenceCount()) {
            throw new IllegalArgumentException(
                    "metadata sequence count (" + metadata.minimumSupport().numberOfSequences()
                    + ") must equal result statistics sequence count ("
                    + result.statistics().sequenceCount() + ")");
        }
        if (projection.entries().size() != result.acceptedPatterns().size()) {
            throw new IllegalArgumentException(
                    "projection entries count (" + projection.entries().size()
                    + ") must equal accepted patterns count ("
                    + result.acceptedPatterns().size() + ")");
        }
    }
}

