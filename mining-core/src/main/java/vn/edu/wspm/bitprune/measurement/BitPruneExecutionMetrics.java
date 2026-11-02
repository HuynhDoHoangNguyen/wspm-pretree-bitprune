package vn.edu.wspm.bitprune.measurement;

import java.util.Objects;
import java.util.Optional;
import vn.edu.wspm.measurement.PhaseExecutionMetrics;

/**
 * Immutable measurement observations for a complete standalone execution of WSPM_BitPrune.
 * Represents all six standardized time scopes (Dataset I/O, Preprocessing, DFS Mining,
 * Complete Algorithm, Output I/O, Total Execution) and sampled heap peak containment.
 * Total Execution excludes console formatting and console printing.
 */
public record BitPruneExecutionMetrics(
        PhaseExecutionMetrics datasetIo,
        BitPruneAlgorithmMetrics algorithm,
        Optional<PhaseExecutionMetrics> outputIo,
        long totalExecutionNanos,
        long observedPeakHeapTotalBytes,
        long samplingIntervalMillis) {

    public static final double NANOS_PER_MILLISECOND = 1_000_000.0;
    public static final double BYTES_PER_MB = 1_000_000.0;

    public BitPruneExecutionMetrics {
        Objects.requireNonNull(datasetIo, "dataset I/O metrics must not be null");
        Objects.requireNonNull(algorithm, "algorithm metrics must not be null");
        outputIo = Objects.requireNonNull(outputIo, "output I/O optional must not be null");
        outputIo.ifPresent(metrics -> Objects.requireNonNull(metrics, "output I/O metrics must not be null"));
        requireNonNegative(totalExecutionNanos, "total execution nanoseconds");
        requireNonNegative(observedPeakHeapTotalBytes, "observed peak heap total");
        if (samplingIntervalMillis <= 0L) {
            throw new IllegalArgumentException("sampling interval must be positive");
        }

        long requiredNanos = Math.addExact(datasetIo.elapsedNanos(), algorithm.completeAlgorithmNanos());
        if (outputIo.isPresent()) {
            requiredNanos = Math.addExact(requiredNanos, outputIo.get().elapsedNanos());
        }
        if (totalExecutionNanos < requiredNanos) {
            throw new IllegalArgumentException(
                    "total execution time must cover dataset I/O, algorithm, and output I/O");
        }

        long requiredPeak = Math.max(
                datasetIo.observedPeakHeapBytes(),
                algorithm.observedPeakHeapAlgorithmBytes());
        if (outputIo.isPresent()) {
            requiredPeak = Math.max(requiredPeak, outputIo.get().observedPeakHeapBytes());
        }
        if (observedPeakHeapTotalBytes < requiredPeak) {
            throw new IllegalArgumentException(
                    "total peak must cover every measured scope");
        }
    }

    /**
     * Returns output I/O elapsed nanoseconds, or 0 if output export was disabled.
     *
     * @return elapsed nanoseconds for output I/O, or 0 if absent
     */
    public long outputIoNanos() {
        return outputIo.map(PhaseExecutionMetrics::elapsedNanos).orElse(0L);
    }

    /**
     * Converts elapsed nanoseconds to decimal milliseconds using standard factor 1,000,000.0.
     *
     * @param nanoseconds non-negative duration in nanoseconds
     * @return duration in decimal milliseconds
     */
    public static double nanosecondsToMilliseconds(long nanoseconds) {
        requireNonNegative(nanoseconds, "nanoseconds");
        return nanoseconds / NANOS_PER_MILLISECOND;
    }

    /**
     * Converts memory bytes to decimal Megabytes (SI MB) using standard factor 1,000,000.0.
     *
     * @param bytes non-negative memory size in bytes
     * @return memory size in decimal MB
     */
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

