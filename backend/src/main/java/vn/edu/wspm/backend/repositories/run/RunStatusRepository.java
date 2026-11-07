package vn.edu.wspm.backend.repositories.run;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** One coherent row, no lifecycle writes, joins, artifact access or live entity escaping the read transaction. */
@Repository
public class RunStatusRepository {
    @PersistenceContext private EntityManager em;
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public Snapshot read(String id) {
        var rows = em.createNativeQuery("SELECT run_id,snapshot_id,algorithm,status,minsup_mode,minsup_configured,"
                + "minsup_relative_fraction,effective_threshold,max_w,min_w,mean_w,"
                + "DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s.%fZ'),DATE_FORMAT(started_at,'%Y-%m-%dT%H:%i:%s.%fZ'),"
                + "DATE_FORMAT(finished_at,'%Y-%m-%dT%H:%i:%s.%fZ'),"
                + "CAST(pattern_count AS CHAR),CAST(candidate_count AS CHAR),CAST(dataset_io_ms AS CHAR),"
                + "CAST(preprocessing_ms AS CHAR),CAST(dfs_mining_ms AS CHAR),CAST(complete_algorithm_ms AS CHAR),"
                + "CAST(output_io_ms AS CHAR),CAST(total_execution_ms AS CHAR),CAST(peak_sampled_jvm_heap_bytes AS CHAR),error_message,"
                + "(execution_owner_id IS NOT NULL AND execution_attempt_id IS NOT NULL AND execution_lease_until IS NOT NULL)"
                + " FROM MINING_RUN WHERE run_id=:id").setParameter("id", id).getResultList();
        if (rows.isEmpty()) return null;
        var r = (Object[]) rows.getFirst();
        return new Snapshot((String) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4],
                decimal(r[5]), decimal(r[6]), decimal(r[7]), decimal(r[8]), decimal(r[9]), decimal(r[10]),
                instant(r[11]), instant(r[12]), instant(r[13]), integer(r[14]), integer(r[15]), integer(r[16]),
                integer(r[17]), integer(r[18]), integer(r[19]), integer(r[20]), integer(r[21]), integer(r[22]),
                (String) r[23], Boolean.TRUE.equals(r[24]) || r[24] instanceof Number n && n.intValue() == 1);
    }
    private static Double decimal(Object value) { return value == null ? null : ((Number) value).doubleValue(); }
    private static String integer(Object value) { return value == null ? null : new BigDecimal(value.toString()).toBigIntegerExact().toString(); }
    private static Instant instant(Object value) {
        if (value == null) return null;
        // DATETIME stores UTC wall values. Format in SQL to avoid JDBC/JVM default-zone reinterpretation.
        return Instant.parse((String) value);
    }
    public record Snapshot(String runId, String snapshotId, String algorithm, String status, String minsupMode,
            Double configuredValue, Double relativeFraction, Double effectiveThreshold, Double maxW, Double minW, Double meanW,
            Instant createdAt, Instant startedAt, Instant finishedAt, String patternCount, String candidateCount,
            String datasetIoMs, String preprocessingMs, String dfsMiningMs, String completeAlgorithmMs, String outputIoMs,
            String totalExecutionMs, String peakSampledJvmHeapBytes, String diagnostic, boolean owned) {
        @Override public String toString() { return "RunStatusSnapshot[redacted]"; }
    }
}
