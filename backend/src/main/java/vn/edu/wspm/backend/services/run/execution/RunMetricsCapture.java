package vn.edu.wspm.backend.services.run.execution;

import java.util.Objects;
import vn.edu.wspm.backend.services.run.engine.MiningEngineResult;

/** WDM-05.6 worker-compute scope. Times are nanoseconds; heap is the native algorithm JVM-wide sample. */
public record RunMetricsCapture(long datasetIoNanos, long preprocessingNanos, long dfsMiningNanos,
        long completeAlgorithmNanos, long totalExecutionNanos, long peakSampledJvmHeapBytes,
        long patternCount, long candidateCount) {
    public RunMetricsCapture {
        if (datasetIoNanos < 0 || preprocessingNanos < 0 || dfsMiningNanos < 0 || completeAlgorithmNanos < 0
                || totalExecutionNanos < 0 || peakSampledJvmHeapBytes < 0 || patternCount < 0 || candidateCount < 0
                || preprocessingNanos > completeAlgorithmNanos
                || dfsMiningNanos > completeAlgorithmNanos - preprocessingNanos
                || datasetIoNanos > totalExecutionNanos
                || completeAlgorithmNanos > totalExecutionNanos - datasetIoNanos) {
            throw new IllegalArgumentException("Nonnegative, contained worker metrics required");
        }
    }
    public static RunMetricsCapture from(MiningEngineResult result, long datasetIoNanos, long totalExecutionNanos) {
        Objects.requireNonNull(result);
        var metrics = result.metrics();
        return new RunMetricsCapture(datasetIoNanos, metrics.preprocessing().elapsedNanos(), metrics.dfsMining().elapsedNanos(),
                metrics.completeAlgorithmNanos(), totalExecutionNanos, metrics.observedPeakHeapAlgorithmBytes(),
                result.patternCount(), result.statistics().candidateCount());
    }
    /** Independent floor conversion. Null output means no output scope was executed. */
    public Persisted persisted() {
        return new Persisted(patternCount, candidateCount, datasetIoNanos / 1_000_000, preprocessingNanos / 1_000_000,
                dfsMiningNanos / 1_000_000, completeAlgorithmNanos / 1_000_000, null,
                totalExecutionNanos / 1_000_000, peakSampledJvmHeapBytes);
    }
    /** Nullable fields also represent historical/unmeasured rows during exact readback. */
    public record Persisted(Long patternCount, Long candidateCount, Long datasetIoMs, Long preprocessingMs,
            Long dfsMiningMs, Long completeAlgorithmMs, Long outputIoMs, Long totalExecutionMs, Long peakSampledJvmHeapBytes) {}
}
