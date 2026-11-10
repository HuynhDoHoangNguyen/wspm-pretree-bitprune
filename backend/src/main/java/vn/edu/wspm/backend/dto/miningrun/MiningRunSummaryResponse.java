package vn.edu.wspm.backend.dto.miningrun;

import java.time.Instant;

public record MiningRunSummaryResponse(
        String runId,
        String algorithm,
        String status,
        String datasetSource,
        String snapshotId,
        String minsupMode,
        Double minsupConfigured,
        Double effectiveThreshold,
        Long patternCount,
        Long candidateCount,
        Long totalExecutionMs,
        Long peakSampledJvmHeapBytes,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
}
