package vn.edu.wspm.bitprune.measurement;

import java.util.Objects;
import vn.edu.wspm.measurement.PhaseExecutionMetrics;

/**
 * Immutable measurement observations for the complete WSPM_BitPrune algorithm execution.
 * Enforces containment invariants: complete algorithm time covers preprocessing and DFS mining,
 * and observed peak heap covers preprocessing peak, DFS peak, and post-mining heap.
 */
public record BitPruneAlgorithmMetrics(
        PhaseExecutionMetrics preprocessing,
        PhaseExecutionMetrics dfsMining,
        long completeAlgorithmNanos,
        long heapAfterMiningBytes,
        long observedPeakHeapAlgorithmBytes) {

    public BitPruneAlgorithmMetrics {
        Objects.requireNonNull(preprocessing, "preprocessing metrics must not be null");
        Objects.requireNonNull(dfsMining, "DFS mining metrics must not be null");
        requireNonNegative(completeAlgorithmNanos, "complete algorithm nanoseconds");
        requireNonNegative(heapAfterMiningBytes, "heap after mining");
        requireNonNegative(observedPeakHeapAlgorithmBytes, "observed peak heap algorithm");

        long subtotalNanos = Math.addExact(preprocessing.elapsedNanos(), dfsMining.elapsedNanos());
        if (completeAlgorithmNanos < subtotalNanos) {
            throw new IllegalArgumentException(
                    "complete algorithm time must cover preprocessing and DFS mining");
        }

        long requiredPeak = Math.max(
                heapAfterMiningBytes,
                Math.max(
                        preprocessing.observedPeakHeapBytes(),
                        dfsMining.observedPeakHeapBytes()));
        if (observedPeakHeapAlgorithmBytes < requiredPeak) {
            throw new IllegalArgumentException(
                    "algorithm peak must cover preprocessing, DFS, and final result construction");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}

