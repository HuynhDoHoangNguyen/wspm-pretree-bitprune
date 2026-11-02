package vn.edu.wspm.measurement;

import java.util.Objects;
import java.util.Optional;

/** Immutable resource measurements for one complete standalone execution. */
public record WspmExecutionMetrics(
        PhaseExecutionMetrics datasetIo,
        WspmAlgorithmMetrics algorithm,
        Optional<PhaseExecutionMetrics> outputIo,
        long totalExecutionNanos,
        long observedPeakHeapTotalBytes,
        long samplingIntervalMillis) {

    public static final double BYTES_PER_MB = 1_000_000.0;

    public WspmExecutionMetrics {
        Objects.requireNonNull(datasetIo, "dataset I/O metrics must not be null");
        Objects.requireNonNull(algorithm, "algorithm metrics must not be null");
        outputIo = Objects.requireNonNull(outputIo, "output I/O optional must not be null");
        requireNonNegative(totalExecutionNanos, "total execution nanoseconds");
        requireNonNegative(observedPeakHeapTotalBytes, "observed total peak heap");
        if (samplingIntervalMillis <= 0L) {
            throw new IllegalArgumentException("sampling interval must be positive");
        }
        long requiredPeak = Math.max(
                datasetIo.observedPeakHeapBytes(), algorithm.observedPeakHeapAlgorithmBytes());
        if (outputIo.isPresent()) {
            requiredPeak = Math.max(requiredPeak, outputIo.get().observedPeakHeapBytes());
        }
        if (observedPeakHeapTotalBytes < requiredPeak) {
            throw new IllegalArgumentException("total peak must cover every measured scope");
        }
    }

    /** Output-file I/O is conventionally zero when export is disabled. */
    public long outputIoNanos() {
        return outputIo.map(PhaseExecutionMetrics::elapsedNanos).orElse(0L);
    }

    public static double bytesToMegabytes(long bytes) {
        requireNonNegative(bytes, "bytes");
        return bytes / BYTES_PER_MB;
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
