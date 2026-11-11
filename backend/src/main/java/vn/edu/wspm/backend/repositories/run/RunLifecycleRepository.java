package vn.edu.wspm.backend.repositories.run;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.wspm.backend.services.run.execution.ExecutionOwnership;
import vn.edu.wspm.backend.services.run.execution.RunFailureCode;
import vn.edu.wspm.backend.services.run.execution.RunMetricsCapture;

/** All lifecycle writes are short conditional transactions; DB UTC is the lease authority. No engine work here. */
@Repository
@Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
public class RunLifecycleRepository {
    @PersistenceContext private EntityManager em;
    private static final String OWNED = "run_id=:id AND execution_owner_id=:owner AND execution_attempt_id=:attempt";
    private static final String LIVE = " AND execution_lease_until>UTC_TIMESTAMP(6)";
    private static final String FINISHED = "finished_at=GREATEST(created_at,COALESCE(started_at,created_at),UTC_TIMESTAMP(6))";

    public int claim(String id, ExecutionOwnership owner) {
        return ownedUpdate("UPDATE MINING_RUN SET status='RUNNING',started_at=GREATEST(created_at,UTC_TIMESTAMP(6)),"
                + "execution_lease_until=TIMESTAMPADD(MICROSECOND,:lease,UTC_TIMESTAMP(6)) WHERE " + OWNED
                + LIVE + " AND status='QUEUED'", id, owner).setParameter("lease", owner.leaseMillis() * 1000).executeUpdate();
    }
    public int succeed(String id, ExecutionOwnership owner, RunMetricsCapture capture) {
        var metrics = java.util.Objects.requireNonNull(capture).persisted();
        return ownedUpdate("UPDATE MINING_RUN SET status='SUCCEEDED',error_message=NULL," + FINISHED
                + ",pattern_count=:patterns,candidate_count=:candidates,dataset_io_ms=:io,preprocessing_ms=:pre,"
                + "dfs_mining_ms=:dfs,complete_algorithm_ms=:algorithm,output_io_ms=NULL,total_execution_ms=:total,"
                + "peak_sampled_jvm_heap_bytes=:heap WHERE " + OWNED + LIVE + " AND status='RUNNING'", id, owner)
                .setParameter("patterns", metrics.patternCount()).setParameter("candidates", metrics.candidateCount())
                .setParameter("io", metrics.datasetIoMs()).setParameter("pre", metrics.preprocessingMs())
                .setParameter("dfs", metrics.dfsMiningMs()).setParameter("algorithm", metrics.completeAlgorithmMs())
                .setParameter("total", metrics.totalExecutionMs()).setParameter("heap", metrics.peakSampledJvmHeapBytes()).executeUpdate();
    }
    public int fail(String id, ExecutionOwnership owner, String expected, RunFailureCode code) {
        if (!List.of("QUEUED", "RUNNING").contains(expected)) throw new IllegalArgumentException("Expected active state required");
        return ownedUpdate("UPDATE MINING_RUN SET status='FAILED',error_message=:code," + FINISHED
                + " WHERE " + OWNED + LIVE + " AND status=:expected", id, owner)
                .setParameter("expected", expected).setParameter("code", code.name()).executeUpdate();
    }
    public int renew(String id, ExecutionOwnership owner) {
        return ownedUpdate("UPDATE MINING_RUN SET execution_lease_until=TIMESTAMPADD(MICROSECOND,:lease,UTC_TIMESTAMP(6)) WHERE "
                + OWNED + LIVE + " AND status IN ('QUEUED','RUNNING')", id, owner)
                .setParameter("lease", owner.leaseMillis() * 1000).executeUpdate();
    }
    public record Observation(String status, String ownerId, String attemptId, boolean leaseValid, String failureCode,
            RunMetricsCapture.Persisted metrics) {
        public boolean matches(ExecutionOwnership owner) {
            return owner.ownerId().equals(ownerId) && owner.attemptId().equals(attemptId);
        }
    }
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public Observation read(String id) {
        var rows = em.createNativeQuery("SELECT status,execution_owner_id,execution_attempt_id,"
                + "execution_lease_until>UTC_TIMESTAMP(6),error_message,pattern_count,candidate_count,dataset_io_ms,preprocessing_ms,"
                + "dfs_mining_ms,complete_algorithm_ms,output_io_ms,total_execution_ms,peak_sampled_jvm_heap_bytes FROM MINING_RUN WHERE run_id=:id")
                .setParameter("id", id).getResultList();
        if (rows.isEmpty()) return null;
        var row = (Object[]) rows.getFirst(); Object live = row[3];
        return new Observation((String) row[0], (String) row[1], (String) row[2],
                Boolean.TRUE.equals(live) || live instanceof Number n && n.intValue() == 1, (String) row[4],
                new RunMetricsCapture.Persisted(number(row[5]), number(row[6]), number(row[7]), number(row[8]), number(row[9]),
                        number(row[10]), number(row[11]), number(row[12]), number(row[13])));
    }
    private static Long number(Object value) { return value == null ? null : new java.math.BigDecimal(value.toString()).longValueExact(); }
    public int recover(int batch) {
        if (batch <= 0) throw new IllegalArgumentException("Positive recovery batch required");
        var ids = em.createNativeQuery("SELECT run_id FROM MINING_RUN WHERE status IN ('QUEUED','RUNNING')"
                + " AND execution_owner_id IS NOT NULL AND execution_attempt_id IS NOT NULL"
                + " AND execution_lease_until<=UTC_TIMESTAMP(6) ORDER BY execution_lease_until,run_id LIMIT :batch FOR UPDATE SKIP LOCKED")
                .setParameter("batch", batch).getResultList();
        int count = 0;
        for (Object id : ids) count += em.createNativeQuery("UPDATE MINING_RUN SET status='FAILED',error_message=:code," + FINISHED
                + " WHERE run_id=:id AND status IN ('QUEUED','RUNNING') AND execution_owner_id IS NOT NULL"
                + " AND execution_attempt_id IS NOT NULL AND execution_lease_until<=UTC_TIMESTAMP(6)")
                .setParameter("id", id).setParameter("code", RunFailureCode.EXECUTION_LEASE_EXPIRED.name()).executeUpdate();
        return count;
    }
    private jakarta.persistence.Query ownedUpdate(String sql, String id, ExecutionOwnership owner) {
        return em.createNativeQuery(sql).setParameter("id", id).setParameter("owner", owner.ownerId()).setParameter("attempt", owner.attemptId());
    }
}
