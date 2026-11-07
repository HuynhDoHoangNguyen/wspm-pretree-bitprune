package vn.edu.wspm.backend.services.run;

import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import vn.edu.wspm.backend.dto.run.RunAcceptedResponse;
import vn.edu.wspm.backend.dto.run.RunCreateRequest;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.run.RunCreationRepository;

/** No outer transaction: repository commit must finish before activation. Managed admission is opt-in. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RunCreationService {
    private final RunPreparationService preparation;
    private final RunCreationRepository repository;
    private final ObjectProvider<RunExecutionAdmission> admissions;

    public RunAcceptedResponse create(RunCreateRequest request) {
        var prepared = preparation.prepare(request);
        String runId = UUID.randomUUID().toString();
        boolean committed = false;
        try {
            // Do not let @Primary hide ambiguous provider registration.
            var providers = admissions.stream().limit(2).toList();
            if (providers.size() != 1) {
                log.debug("Run admission unavailable: providerCount={}", providers.size());
                throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE);
            }
            RunAcceptedResponse response;
            try (var reservation = Objects.requireNonNull(providers.getFirst().reserve(runId, prepared), "reservation is required")) {
                var ownership = reservation.ownership();
                var entity = ownership.isPresent() ? repository.createOwned(runId, prepared, ownership.orElseThrow())
                        : repository.create(runId, prepared); // Proxy returns only after REQUIRES_NEW commit.
                committed = true;
                log.info("Run queued: runId={}, snapshotId={}, algorithm={}", runId, entity.getSnapshotId(), entity.getAlgorithm());
                response = new RunAcceptedResponse(entity.getRunId(), entity.getSnapshotId(), entity.getAlgorithm(),
                        entity.getStatus(), entity.getCreatedAt());
                reservation.activate();
            }
            log.info("Run admission acknowledged: runId={}", runId);
            return response;
        } catch (ApplicationException expected) {
            if (committed) log.warn("Run admission failed after commit; reconciliation required: runId={}, code={}",
                    runId, expected.code());
            throw expected;
        }
        catch (RuntimeException failure) {
            // Commit/activation may be uncertain. Never delete, retry, or invent a terminal state here.
            var safe = new IllegalStateException("Run creation/admission failed: " + failure.getClass().getSimpleName());
            safe.setStackTrace(failure.getStackTrace());
            log.error("Run creation unavailable; reconciliation may be required: runId={}, snapshotId={}",
                    runId, prepared.binding().publication().snapshotId(), safe);
            throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
