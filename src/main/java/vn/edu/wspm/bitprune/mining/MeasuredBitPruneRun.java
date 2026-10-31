package vn.edu.wspm.bitprune.mining;

import java.util.Objects;
import vn.edu.wspm.bitprune.measurement.BitPruneAlgorithmMetrics;
import vn.edu.wspm.bitprune.result.BitPruneMiningResult;

/**
 * Immutable carrier holding the completed mining result and algorithm execution metrics
 * for a successful WSPM_BitPrune execution (C13.3).
 *
 * <p>Represents a completed successful algorithm run without exposing mutable mining
 * state, sampler references, I/O metrics, or runner configuration.</p>
 */
public record MeasuredBitPruneRun(
        BitPruneMiningResult result,
        BitPruneAlgorithmMetrics metrics) {

    public MeasuredBitPruneRun {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
    }
}

