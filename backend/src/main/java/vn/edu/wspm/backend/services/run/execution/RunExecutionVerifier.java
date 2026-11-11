package vn.edu.wspm.backend.services.run.execution;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.edu.wspm.backend.dto.run.RunCreateRequest;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.run.RunExecutionVerificationRepository;
import vn.edu.wspm.backend.services.run.PreparedRun;
import vn.edu.wspm.backend.services.run.RunPreparationService;
import vn.edu.wspm.backend.services.run.MeasuredRunPreparation;
import vn.edu.wspm.config.ResolvedMinimumSupport;

/** Queue delay is not an integrity proof. Reverify actual publication bytes immediately before mining. */
@Service
@RequiredArgsConstructor
public class RunExecutionVerifier {
    private final RunExecutionVerificationRepository repository;
    private final RunPreparationService preparation;

    public void requireCommitted(String runId, PreparedRun input) { repository.requireCommitted(runId, input); }
    public void requireQueuedOwned(String runId, PreparedRun input, ExecutionOwnership owner) {
        repository.requireOwned(runId, input, "QUEUED", owner);
    }

    public PreparedRun refresh(String runId, PreparedRun original) {
        return refresh(runId, original, null);
    }
    public PreparedRun refreshOwned(String runId, PreparedRun original, ExecutionOwnership owner) {
        return refresh(runId, original, java.util.Objects.requireNonNull(owner));
    }
    private PreparedRun refresh(String runId, PreparedRun original, ExecutionOwnership owner) {
        return refresh(runId, original, owner, null).input();
    }
    public MeasuredRunPreparation refreshMeasuredOwned(String runId, PreparedRun original, ExecutionOwnership owner,
            java.util.function.LongSupplier ticker) {
        return refresh(runId, original, java.util.Objects.requireNonNull(owner), java.util.Objects.requireNonNull(ticker));
    }
    private MeasuredRunPreparation refresh(String runId, PreparedRun original, ExecutionOwnership owner,
            java.util.function.LongSupplier ticker) {
        if (owner == null) requireCommitted(runId, original); else repository.requireOwned(runId, original, "RUNNING", owner);
        var config = original.engineRequest().minimumSupport();
        var request = new RunCreateRequest(original.binding().publication().snapshotId(),
                original.engineRequest().algorithm().name(), config.mode().name(), config.configuredValue());
        var measured = ticker == null ? new MeasuredRunPreparation(preparation.prepare(request), 0) : preparation.prepareMeasured(request, ticker);
        var fresh = measured.input();
        if (!fresh.binding().equals(original.binding()) || !sameResolution(fresh.minimumSupport(), original.minimumSupport())
                || !fresh.weights().itemWeights().equals(original.weights().itemWeights())
                || !fresh.weights().meanWeight().equals(original.weights().meanWeight())
                || !fresh.weights().maxWeight().equals(original.weights().maxWeight())
                || !fresh.weights().minWeight().equals(original.weights().minWeight())) {
            throw new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE, "Execution publication changed after admission.");
        }
        // Recheck DB after potentially slow I/O/preparation; no transaction crosses that work.
        if (owner == null) requireCommitted(runId, fresh); else repository.requireOwned(runId, fresh, "RUNNING", owner);
        return measured;
    }

    private static boolean sameResolution(ResolvedMinimumSupport a, ResolvedMinimumSupport b) {
        return a.mode() == b.mode() && a.numberOfSequences() == b.numberOfSequences()
                && Double.doubleToRawLongBits(a.configuredValue()) == Double.doubleToRawLongBits(b.configuredValue())
                && a.relativeMinimumSupport().equals(b.relativeMinimumSupport())
                && Double.doubleToRawLongBits(a.effectiveThreshold()) == Double.doubleToRawLongBits(b.effectiveThreshold());
    }
}
