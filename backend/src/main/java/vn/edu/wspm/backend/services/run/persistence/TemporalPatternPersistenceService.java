package vn.edu.wspm.backend.services.run.persistence;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.repositories.run.PatternPersistenceRepository;
import vn.edu.wspm.backend.repositories.run.TemporalPatternPersistenceRepository;
import vn.edu.wspm.backend.services.run.execution.ManagedRunExecutionAdmission.Outcome;

/** Explicit composition only. No automatic consumer, publication transition or acknowledgement. */
@Service
@Slf4j
public class TemporalPatternPersistenceService {
    private final PatternPersistenceService preparation;
    private final PatternPersistenceRepository parents;
    private final TemporalPatternPersistenceRepository temporal;
    private final PlatformTransactionManager transactions;
    public TemporalPatternPersistenceService(PatternPersistenceService preparation, PatternPersistenceRepository parents,
            TemporalPatternPersistenceRepository temporal, PlatformTransactionManager transactions) {
        this.preparation = preparation; this.parents = parents; this.temporal = temporal; this.transactions = transactions;
    }

    public TemporalPatternProjector.Batch prepare(Outcome outcome) {
        return TemporalPatternProjector.project(preparation.prepare(outcome));
    }

    public TemporalPatternPersistenceRepository.Result persist(Outcome outcome) {
        String id = outcome == null ? null : outcome.runId();
        try {
            var batch = prepare(outcome);
            var result = transaction().execute(tx -> {
                parents.persist(batch.parents());
                return temporal.persist(batch);
            });
            log.info("Temporal rows persisted/verified: runId={}, count={}, result={}", id, batch.rowCount(), result);
            return result;
        } catch (ApplicationException expected) {
            log.warn("Temporal persistence rejected: runId={}, code={}", id, expected.code()); throw expected;
        } catch (IllegalArgumentException expected) {
            log.warn("Temporal persistence rejected invalid projection/context: runId={}", id); throw expected;
        } catch (RuntimeException failure) {
            var safe = new IllegalStateException("Temporal persistence failed: " + failure.getClass().getSimpleName());
            safe.setStackTrace(failure.getStackTrace());
            log.error("Temporal persistence failed; outcome retained by caller: runId={}", id, safe); throw failure;
        }
    }

    public Map<String, Long> verify(Outcome outcome) {
        var batch = prepare(outcome);
        return transaction().execute(tx -> temporal.verify(batch));
    }

    private TransactionTemplate transaction() {
        var tx = new TransactionTemplate(transactions);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW); tx.setTimeout(30);
        return tx;
    }
}
