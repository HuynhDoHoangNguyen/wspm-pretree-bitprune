package vn.edu.wspm.backend.repositories.run;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.entities.RunEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec;
import vn.edu.wspm.backend.services.run.PreparedRun;
import vn.edu.wspm.backend.services.run.persistence.RunProvenance;
import vn.edu.wspm.backend.repositories.dataset.SnapshotConversionRepository;
import vn.edu.wspm.backend.services.run.execution.ExecutionOwnership;

/** Commit boundary only: snapshot rebinding and INSERT. No files, admission or mining inside the transaction. */
@Repository
public class RunCreationRepository {
    @PersistenceContext private EntityManager em;
    private final SnapshotConversionRepository publications;
    private final Clock clock;
    private final RunMetadataRepository metadata;
    public RunCreationRepository(SnapshotConversionRepository publications, Clock clock, RunMetadataRepository metadata) {
        this.publications = publications; this.clock = clock; this.metadata = metadata;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public RunEntity create(String runId, PreparedRun input) {
        return insert(runId, input, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public RunEntity createOwned(String runId, PreparedRun input, ExecutionOwnership ownership) {
        return insert(runId, input, Objects.requireNonNull(ownership));
    }

    private RunEntity insert(String runId, PreparedRun input, ExecutionOwnership ownership) {
        var binding = input.binding(); var expected = binding.publication();
        var snapshot = em.find(DatasetSnapshotEntity.class, expected.snapshotId(), LockModeType.PESSIMISTIC_WRITE,
                Map.of("jakarta.persistence.lock.timeout", 0));
        if (snapshot == null) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!expected.equals(publications.completed(snapshot)) || !Objects.equals(binding.sourceType(), snapshot.getSourceType())
                || !Objects.equals(binding.sourceName(), snapshot.getSourceName())
                || !Objects.equals(binding.rawReference(), snapshot.getRawFilePath())
                || !Objects.equals(binding.rawSha256(), snapshot.getRawSha256())) throw ConversionArtifactCodec.invalid();
        var resolved = input.minimumSupport(); var weights = input.weights();
        var entity = RunEntity.queued(runId, expected.snapshotId(), input.engineRequest().algorithm().name(),
                snapshot.getSourceName(), resolved.mode().name(), resolved.configuredValue(),
                resolved.relativeMinimumSupport().isPresent() ? resolved.relativeMinimumSupport().getAsDouble() : null,
                resolved.effectiveThreshold(), weights.maxWeight().orElseThrow(), weights.minWeight().orElseThrow(),
                weights.meanWeight().orElseThrow(), clock.instant().truncatedTo(ChronoUnit.MICROS));
        em.persist(entity); em.flush();
        metadata.insert(runId, RunProvenance.from(input));
        if (ownership != null) {
            em.createNativeQuery("UPDATE MINING_RUN SET execution_owner_id=:owner, execution_attempt_id=:attempt, "
                    + "execution_lease_until=TIMESTAMPADD(MICROSECOND,:lease,UTC_TIMESTAMP(6)) WHERE run_id=:id")
                    .setParameter("owner", ownership.ownerId()).setParameter("attempt", ownership.attemptId())
                    .setParameter("lease", ownership.leaseMillis() * 1000).setParameter("id", runId).executeUpdate();
            em.refresh(entity);
        }
        return entity;
    }

}
