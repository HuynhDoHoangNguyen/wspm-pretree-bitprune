package vn.edu.wspm.backend.services.run.persistence;

import java.util.LinkedHashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import vn.edu.wspm.backend.dto.run.RunCreateRequest;
import vn.edu.wspm.backend.repositories.run.PatternPersistenceRepository;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionReadService;
import vn.edu.wspm.backend.services.dataset.conversion.ProductItemDictionary;
import vn.edu.wspm.backend.services.run.RunPreparationService;
import vn.edu.wspm.backend.services.run.execution.ManagedRunExecutionAdmission.Outcome;
import vn.edu.wspm.backend.services.run.persistence.PatternPersistenceProjector.Batch;
import vn.edu.wspm.backend.services.run.persistence.PatternPersistenceProjector.Row;

/** Explicit internal seam only; no scheduler, HTTP route, publication transition or registry acknowledgement. */
@Service
@Slf4j
public class PatternPersistenceService {
    private final RunPreparationService preparation;
    private final ConversionReadService reads;
    private final ObjectMapper json;
    private final PatternPersistenceRepository repository;
    private final PlatformTransactionManager transactions;

    public PatternPersistenceService(RunPreparationService preparation, ConversionReadService reads, ObjectMapper json,
            PatternPersistenceRepository repository, PlatformTransactionManager transactions) {
        this.preparation = preparation; this.reads = reads; this.json = json;
        this.repository = repository; this.transactions = transactions;
    }

    /** Artifact I/O and projection complete before opening a database transaction. */
    public Batch prepare(Outcome outcome) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Artifact preparation must run outside a database transaction");
        }
        PatternPersistenceContext.requireOutcome(outcome);
        var result = outcome.result(); var identity = outcome.persistenceContext(); var config = result.minimumSupport();
        var fresh = preparation.prepare(new RunCreateRequest(identity.provenance().binding().publication().snapshotId(),
                result.algorithm().name(), config.mode().name(), config.configuredValue()));
        if (!identity.provenance().equals(RunProvenance.from(fresh))) throw ConversionArtifactCodec.invalid();
        // Reuse the canonical verified mapping, not global mapping allocation or current catalog labels.
        var bundle = reads.verified(fresh.binding().publication().snapshotId());
        if (!bundle.result().equals(fresh.binding().publication())) throw ConversionArtifactCodec.invalid();
        var doc = json.readValue(bundle.mappingBytes(), ConversionArtifactCodec.DictionaryDocument.class);
        var products = new LinkedHashMap<String, Integer>();
        for (var entry : doc.items()) products.put(entry.productId(), entry.itemId());
        return PatternPersistenceProjector.project(PatternPersistenceContext.verified(outcome, fresh, new ProductItemDictionary(products)));
    }

    public PatternPersistenceRepository.Result persist(Outcome outcome) {
        String id = outcome == null ? null : outcome.runId();
        try {
            var batch = prepare(outcome);
            var result = transaction().execute(tx -> repository.persist(batch));
            log.info("Pattern rows persisted/verified: runId={}, count={}, result={}", id, batch.rows().size(), result);
            return result;
        } catch (vn.edu.wspm.backend.exception.ApplicationException expected) {
            log.warn("Pattern persistence rejected: runId={}, code={}", id, expected.code());
            throw expected;
        } catch (IllegalArgumentException expected) {
            log.warn("Pattern persistence rejected invalid projection/context: runId={}", id);
            throw expected;
        } catch (RuntimeException failure) {
            // Safe diagnostic: dataset paths/payloads and driver SQL may appear in the original exception.
            var safe = new IllegalStateException("Pattern persistence failed: " + failure.getClass().getSimpleName());
            safe.setStackTrace(failure.getStackTrace());
            log.error("Pattern persistence failed; outcome retained by caller: runId={}", id, safe);
            throw failure;
        }
    }

    /** Explicit readback after uncertain commit; no blind replay or in-memory eviction. */
    public List<Row> verify(Outcome outcome) {
        var batch = prepare(outcome);
        return transaction().execute(tx -> repository.verify(batch));
    }

    private TransactionTemplate transaction() {
        var tx = new TransactionTemplate(transactions);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(30);
        return tx;
    }
}
