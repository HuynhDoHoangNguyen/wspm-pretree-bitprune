package vn.edu.wspm.backend.repositories.run;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.services.run.persistence.RunProvenance;

/** Metadata only. No upsert, publication writer, engine calls or filesystem access. */
@Repository
@Slf4j
public class RunMetadataRepository {
    @PersistenceContext private EntityManager em;
    private final ObjectMapper json;
    public RunMetadataRepository(ObjectMapper json) { this.json = json; }

    /** Must join the creation transaction: any failure rolls back all three rows. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(String runId, RunProvenance provenance) {
        int inserted = em.createNativeQuery("INSERT INTO RUN_PROVENANCE(run_id,provenance_version,provenance_json,captured_at)"
                + " SELECT run_id,:version,:document,created_at FROM MINING_RUN WHERE run_id=:id")
                .setParameter("id", runId).setParameter("version", provenance.version())
                .setParameter("document", json.writeValueAsString(provenance)).executeUpdate();
        if (inserted != 1) throw invalid();
        em.createNativeQuery("INSERT INTO RUN_RESULT_PUBLICATION(run_id) VALUES(:id)")
                .setParameter("id", runId).executeUpdate();
        log.debug("Run metadata inserted in creation transaction: runId={}", runId);
    }

    /** Same transaction as the verifier; absence is not permission to execute a legacy ticket. */
    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public void requireMatches(String runId, RunProvenance expected) {
        var stored = query(runId);
        if (stored.isEmpty() || !expected.equals(stored.orElseThrow().provenance())
                || !"NOT_PUBLISHED".equals(stored.orElseThrow().publication().status())) {
            log.warn("Run provenance verification rejected: runId={}", runId);
            throw invalid();
        }
    }

    /** Internal metadata snapshot. Existing RunStatusRepository owns config/lifecycle/metrics reads. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public Optional<Snapshot> read(String runId) { return query(runId); }

    public record Publication(String status, String manifestVersion, String resultSha256, String patternCount,
                              String patternItemCount, String runItemCount, String weightPolicy,
                              String elapsedMs, Instant committedAt) {
        @Override public String toString() { return "RunPublication[status=" + status + "]"; }
    }
    public record Snapshot(String runId, RunProvenance provenance, Instant capturedAt, Publication publication) {
        @Override public String toString() { return "RunMetadataSnapshot[runId=" + runId + "]"; }
    }

    private Optional<Snapshot> query(String runId) {
        var rows = em.createNativeQuery("SELECT r.run_id,p.provenance_version,CAST(p.provenance_json AS CHAR),"
                + "DATE_FORMAT(p.captured_at,'%Y-%m-%dT%H:%i:%s.%fZ'),"
                + "u.publication_status,u.manifest_version,u.result_sha256,CAST(u.pattern_count AS CHAR),"
                + "CAST(u.pattern_item_count AS CHAR),CAST(u.run_item_count AS CHAR),u.pattern_weight_policy,"
                + "CAST(u.publication_elapsed_ms AS CHAR),DATE_FORMAT(u.committed_at,'%Y-%m-%dT%H:%i:%s.%fZ'),r.snapshot_id"
                + " FROM MINING_RUN r LEFT JOIN RUN_PROVENANCE p ON p.run_id=r.run_id"
                + " LEFT JOIN RUN_RESULT_PUBLICATION u ON u.run_id=r.run_id WHERE r.run_id=:id")
                .setParameter("id", runId).getResultList();
        if (rows.isEmpty()) return Optional.empty();
        var row = (Object[]) rows.getFirst();
        RunProvenance provenance = null;
        if (row[1] != null) {
            try { provenance = json.readValue((String) row[2], RunProvenance.class); }
            catch (RuntimeException malformed) { throw invalid(); }
            if (provenance == null || !provenance.version().equals(row[1])
                    || !provenance.binding().publication().snapshotId().equals(row[13])) throw invalid();
        }
        // A partial pair is corruption, not a legacy record.
        if ((row[1] == null) != (row[4] == null)) throw invalid();
        if (row[4] != null && !java.util.Set.of("NOT_PUBLISHED", "COMMITTED").contains(row[4])) throw invalid();
        var publication = new Publication(row[4] == null ? "UNVERIFIED" : (String) row[4],
                (String) row[5], (String) row[6], (String) row[7], (String) row[8], (String) row[9],
                (String) row[10], (String) row[11], instant(row[12]));
        return Optional.of(new Snapshot((String) row[0], provenance, instant(row[3]), publication));
    }
    private static Instant instant(Object value) { return value == null ? null : Instant.parse((String) value); }
    private static ApplicationException invalid() {
        return new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE, "Run metadata is absent or inconsistent.");
    }
}
