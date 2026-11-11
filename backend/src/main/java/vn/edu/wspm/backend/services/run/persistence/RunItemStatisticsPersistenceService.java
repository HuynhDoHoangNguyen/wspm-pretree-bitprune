package vn.edu.wspm.backend.services.run.persistence;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.repositories.run.*;
import vn.edu.wspm.backend.services.run.execution.ManagedRunExecutionAdmission.Outcome;
import vn.edu.wspm.backend.services.run.persistence.RunItemStatisticsProjector.*;

/** Explicit atomic staging only; no automatic delivery, publication marker or registry acknowledgement. */
@Service
@Slf4j
public class RunItemStatisticsPersistenceService {
    private final TemporalPatternPersistenceService preparation;
    private final PatternPersistenceRepository parents;
    private final TemporalPatternPersistenceRepository temporal;
    private final RunItemStatisticsPersistenceRepository statistics;
    private final PlatformTransactionManager transactions;
    public RunItemStatisticsPersistenceService(TemporalPatternPersistenceService preparation, PatternPersistenceRepository parents,
            TemporalPatternPersistenceRepository temporal, RunItemStatisticsPersistenceRepository statistics,
            PlatformTransactionManager transactions) {
        this.preparation = preparation; this.parents = parents; this.temporal = temporal;
        this.statistics = statistics; this.transactions = transactions;
    }
    public Batch prepare(Outcome outcome) { return RunItemStatisticsProjector.project(preparation.prepare(outcome)); }

    public RunItemStatisticsPersistenceRepository.Result persist(Outcome outcome) {
        String id = outcome == null ? null : outcome.runId();
        try {
            var batch = prepare(outcome);
            var result = transaction().execute(tx -> {
                parents.persist(batch.temporal().parents()); temporal.persist(batch.temporal()); return statistics.persist(batch);
            });
            log.info("Run item statistics persisted/verified: runId={}, count={}, result={}", id, batch.rows().size(), result);
            return result;
        } catch (ApplicationException expected) {
            log.warn("Run item statistics rejected: runId={}, code={}", id, expected.code()); throw expected;
        } catch (IllegalArgumentException expected) {
            log.warn("Run item statistics rejected invalid projection/context: runId={}", id); throw expected;
        } catch (RuntimeException failure) {
            var safe = new IllegalStateException("Run item statistics persistence failed: " + failure.getClass().getSimpleName());
            safe.setStackTrace(failure.getStackTrace());
            log.error("Run item statistics persistence failed; outcome retained by caller: runId={}", id, safe); throw failure;
        }
    }
    public List<StoredRow> verify(Outcome outcome) {
        var batch = prepare(outcome); return transaction().execute(tx -> statistics.verify(batch));
    }
    private TransactionTemplate transaction() {
        var tx = new TransactionTemplate(transactions);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW); tx.setTimeout(30); return tx;
    }
}
