package vn.edu.wspm.backend.repositories.run;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.wspm.backend.entities.RunEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.services.run.PreparedRun;
import vn.edu.wspm.backend.services.run.execution.ExecutionOwnership;

/** Fresh, short read transaction only. No mining or filesystem access; DB lifecycle belongs to WDM-05.5. */
@Repository
public class RunExecutionVerificationRepository {
    @PersistenceContext private EntityManager em;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void requireCommitted(String runId, PreparedRun input) {
        require(runId, input, "QUEUED", null);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void requireOwned(String runId, PreparedRun input, String state, ExecutionOwnership owner) {
        require(runId, input, state, owner);
    }

    private void require(String runId, PreparedRun input, String state, ExecutionOwnership owner) {
        var run = em.find(RunEntity.class, runId);
        var resolved = input.minimumSupport(); var weights = input.weights();
        if (run == null || !state.equals(run.getStatus())
                || !Objects.equals(run.getSnapshotId(), input.binding().publication().snapshotId())
                || !Objects.equals(run.getAlgorithm(), input.engineRequest().algorithm().name())
                || !Objects.equals(run.getDatasetSource(), input.binding().sourceName())
                || !Objects.equals(run.getMinsupMode(), resolved.mode().name())
                || !bits(run.getMinsupConfigured(), resolved.configuredValue())
                || !bits(run.getMinsupAbs(), resolved.effectiveThreshold())
                || (resolved.relativeMinimumSupport().isPresent()
                    ? !bits(run.getMinsupRatio(), resolved.relativeMinimumSupport().getAsDouble()) : run.getMinsupRatio() != null)
                || !bits(run.getMaxW(), weights.maxWeight().orElseThrow())
                || !bits(run.getMinW(), weights.minWeight().orElseThrow())
                || !bits(run.getMeanW(), weights.meanWeight().orElseThrow())) {
            throw new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE, "Committed run does not match its execution ticket.");
        }
        if (owner != null) {
            var count = (Number) em.createNativeQuery("SELECT COUNT(*) FROM MINING_RUN WHERE run_id=:id AND status=:state"
                    + " AND execution_owner_id=:owner AND execution_attempt_id=:attempt AND execution_lease_until>UTC_TIMESTAMP(6)")
                    .setParameter("id", runId).setParameter("state", state).setParameter("owner", owner.ownerId())
                    .setParameter("attempt", owner.attemptId()).getSingleResult();
            if (count.intValue() != 1) throw new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE, "Execution ownership is not valid.");
        }
    }

    private static boolean bits(Double actual, double expected) {
        return actual != null && Double.doubleToRawLongBits(actual) == Double.doubleToRawLongBits(expected);
    }
}
