package vn.edu.wspm.backend.mappers;

import java.util.Set;
import org.springframework.stereotype.Component;
import vn.edu.wspm.backend.dto.run.RunStatusResponse;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.run.RunStatusRepository.Snapshot;
import vn.edu.wspm.backend.services.run.execution.RunFailureCode;

/** Public allowlist projection. Raw persisted diagnostics and ownership never enter the DTO. */
@Component
public class RunStatusResponseMapper {
    private static final Set<String> STATES = Set.of("QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "LEGACY_UNVERIFIED");
    public RunStatusResponse map(Snapshot row) {
        if (row.status() == null || !STATES.contains(row.status())) throw new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE);
        boolean measured = row.owned() && row.patternCount() != null && row.candidateCount() != null
                && row.datasetIoMs() != null && row.preprocessingMs() != null && row.dfsMiningMs() != null
                && row.completeAlgorithmMs() != null && row.totalExecutionMs() != null && row.peakSampledJvmHeapBytes() != null;
        var descriptor = new RunStatusResponse.Measurement(measured ? "WDM-05.6" : null, "MILLISECONDS", "BYTES",
                measured ? "WORKER_VERIFIED_RAW_SEQUENCE_MAPPING_READS" : null,
                measured ? "POST_CLAIM_REFRESH_THROUGH_ENGINE_RETURN" : null,
                measured ? "ALGORITHM_JVM_WIDE_USED_HEAP_SAMPLING" : null);
        return new RunStatusResponse(row.runId(), row.snapshotId(), row.algorithm(), row.status(),
                new RunStatusResponse.Configuration(row.minsupMode(), row.configuredValue(), row.relativeFraction(),
                        row.effectiveThreshold(), row.maxW(), row.minW(), row.meanW()), row.createdAt(), row.startedAt(), row.finishedAt(),
                row.patternCount(), row.candidateCount(), new RunStatusResponse.Metrics(row.datasetIoMs(), row.preprocessingMs(),
                        row.dfsMiningMs(), row.completeAlgorithmMs(), row.outputIoMs(), row.totalExecutionMs(), row.peakSampledJvmHeapBytes()),
                descriptor, "FAILED".equals(row.status()) ? failure(row.diagnostic()) : null);
    }
    private RunStatusResponse.Failure failure(String diagnostic) {
        RunFailureCode code;
        try { code = RunFailureCode.valueOf(diagnostic); }
        catch (RuntimeException unknown) { return new RunStatusResponse.Failure("RUN_FAILED", "Run failed."); }
        String message = switch (code) {
            case ADMISSION_FAILED -> "Run could not be admitted for execution.";
            case CLAIM_NOT_CONFIRMED -> "Execution ownership could not be confirmed.";
            case INPUT_REVERIFICATION_FAILED -> "Run input could not be verified.";
            case ENGINE_EXECUTION_FAILED -> "Mining execution failed.";
            case EXECUTION_LEASE_EXPIRED -> "Execution lease expired.";
        };
        return new RunStatusResponse.Failure(code.name(), message);
    }
}
