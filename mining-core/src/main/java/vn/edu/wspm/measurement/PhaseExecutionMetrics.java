package vn.edu.wspm.measurement;

/** Immutable time and simultaneous used-heap observations for one execution phase. */
public record PhaseExecutionMetrics(
        long elapsedNanos,
        long heapBeforeBytes,
        long heapAfterBytes,
        long observedPeakHeapBytes) {

    public PhaseExecutionMetrics {
        requireNonNegative(elapsedNanos, "elapsed nanoseconds");
        requireNonNegative(heapBeforeBytes, "heap before");
        requireNonNegative(heapAfterBytes, "heap after");
        requireNonNegative(observedPeakHeapBytes, "observed peak heap");
        if (observedPeakHeapBytes < Math.max(heapBeforeBytes, heapAfterBytes)) {
            throw new IllegalArgumentException(
                    "observed peak heap must include both boundary snapshots");
        }
    }

    public static PhaseExecutionMetrics between(
            ExecutionMemorySampler sampler,
            ExecutionMemorySampler.HeapCheckpoint start,
            ExecutionMemorySampler.HeapCheckpoint end) {
        return new PhaseExecutionMetrics(
                Math.max(0L, end.nanoTime() - start.nanoTime()),
                start.heapUsedBytes(),
                end.heapUsedBytes(),
                sampler.observedPeakBytes(start, end));
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
