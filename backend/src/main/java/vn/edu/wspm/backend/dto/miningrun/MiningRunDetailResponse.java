package vn.edu.wspm.backend.dto.miningrun;

import java.time.Instant;

public record MiningRunDetailResponse(
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
        Instant finishedAt,
        Double minsupRelativeFraction,
        Double maxW,
        Double minW,
        Double meanW,
        Long datasetIoMs,
        Long preprocessingMs,
        Long dfsMiningMs,
        Long completeAlgorithmMs,
        Long outputIoMs,
        String errorMessage,
        Long sequenceCount,
        Long distinctItemCount,
        String validationStatus
) {
}
