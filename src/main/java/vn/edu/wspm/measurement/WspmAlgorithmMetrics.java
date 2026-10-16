package vn.edu.wspm.measurement;

import java.util.Objects;

/** Immutable preprocessing, DFS, and complete-algorithm measurements for one mining run. */
public record WspmAlgorithmMetrics(
        PhaseExecutionMetrics preprocessing,
        PhaseExecutionMetrics dfsMining,
        long algorithmNanos,
        long heapAfterMiningBytes,
        long observedPeakHeapAlgorithmBytes) {

    public WspmAlgorithmMetrics {
        Objects.requireNonNull(preprocessing, "preprocessing metrics must not be null");
        Objects.requireNonNull(dfsMining, "DFS mining metrics must not be null");
        requireNonNegative(algorithmNanos, "algorithm nanoseconds");
        requireNonNegative(heapAfterMiningBytes, "heap after mining");
        requireNonNegative(observedPeakHeapAlgorithmBytes, "observed algorithm peak heap");
        long requiredPeak = Math.max(
                heapAfterMiningBytes,
                Math.max(
                        preprocessing.observedPeakHeapBytes(),
                        dfsMining.observedPeakHeapBytes()));
        if (observedPeakHeapAlgorithmBytes < requiredPeak) {
            throw new IllegalArgumentException(
                    "algorithm peak must cover preprocessing, DFS, and final result construction");
        }
        if (algorithmNanos < preprocessing.elapsedNanos()
                || algorithmNanos < dfsMining.elapsedNanos()) {
            throw new IllegalArgumentException("algorithm time must cover each measured phase");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
