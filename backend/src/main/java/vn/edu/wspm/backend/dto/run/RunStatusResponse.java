package vn.edu.wspm.backend.dto.run;

import java.time.Instant;

/** Persisted read snapshot; numeric BIGINT values use exact decimal strings for browser consumers. */
public record RunStatusResponse(String runId, String snapshotId, String algorithm, String status,
        Configuration configuration, Instant createdAt, Instant startedAt, Instant finishedAt,
        String patternCount, String candidateCount, Metrics metrics, Measurement measurement, Failure failure) {
    public record Configuration(String minsupMode, Double configuredValue, Double relativeFraction,
            Double effectiveThreshold, Double maxW, Double minW, Double meanW) {}
    public record Metrics(String datasetIoMs, String preprocessingMs, String dfsMiningMs, String completeAlgorithmMs,
            String outputIoMs, String totalExecutionMs, String peakSampledJvmHeapBytes) {}
    public record Measurement(String contractVersion, String timeUnit, String memoryUnit, String datasetIoScope,
            String totalExecutionScope, String heapScope) {}
    public record Failure(String code, String message) {}
    @Override public String toString() { return "RunStatusResponse[runId=" + runId + ", status=" + status + "]"; }
}
