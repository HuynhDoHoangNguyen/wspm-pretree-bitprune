package vn.edu.wspm.backend.repositories.run;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.services.run.persistence.PatternPersistenceProjector;
import vn.edu.wspm.backend.services.run.persistence.PatternPersistenceProjector.Batch;
import vn.edu.wspm.backend.services.run.persistence.PatternPersistenceProjector.Row;

/** Joins the caller's transaction so later temporal/statistics writers can publish atomically. */
@Repository
public class PatternPersistenceRepository {
    @PersistenceContext private EntityManager em;
    private final RunMetadataRepository metadata;
    public PatternPersistenceRepository(RunMetadataRepository metadata) { this.metadata = metadata; }

    public enum Result { INSERTED, VERIFIED_EXISTING, EMPTY }

    @Transactional(propagation = Propagation.MANDATORY)
    public Result persist(Batch batch) {
        requireRun(batch);
        var expected = batch.rows(); var existing = readRows(batch.context().identity().runId());
        if (!existing.isEmpty()) {
            if (!expected.equals(existing)) throw conflict();
            return Result.VERIFIED_EXISTING;
        }
        if (expected.isEmpty()) return Result.EMPTY;
        for (var row : expected) {
            em.createNativeQuery("INSERT INTO PATTERN(run_id,canonical_pattern,canonical_pattern_hash,support_abs,support_ratio,"
                    + "weighted_support,pattern_item_count,itemset_count,pattern_weight,pattern_weight_policy)"
                    + " VALUES(:run,:canonical,:hash,:support,:ratio,:weighted,:items,:itemsets,:weight,:policy)")
                    .setParameter("run", batch.context().identity().runId()).setParameter("canonical", row.canonical())
                    .setParameter("hash", row.hash()).setParameter("support", row.support()).setParameter("ratio", row.ratio())
                    .setParameter("weighted", row.weightedSupport()).setParameter("items", row.itemCount())
                    .setParameter("itemsets", row.itemsetCount()).setParameter("weight", row.weight()).setParameter("policy", row.policy())
                    .executeUpdate();
        }
        if (!expected.equals(readRows(batch.context().identity().runId()))) throw conflict();
        return Result.INSERTED;
    }

    /** Exact bounded reconciliation, including empty outcomes; never inserts or acknowledges. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Row> verify(Batch batch) {
        requireRun(batch);
        var actual = readRows(batch.context().identity().runId());
        if (!batch.rows().equals(actual)) throw conflict();
        return actual;
    }

    /** Full parent verification and exact generated IDs under the same run/parent locks. */
    @Transactional(propagation = Propagation.MANDATORY)
    public java.util.Map<String, Long> verifyWithIds(Batch batch) {
        verify(batch);
        var values = em.createNativeQuery("SELECT canonical_pattern_hash,CAST(pattern_id AS CHAR) FROM PATTERN"
                + " WHERE run_id=:id ORDER BY canonical_pattern_hash FOR UPDATE")
                .setParameter("id", batch.context().identity().runId()).setMaxResults(PatternPersistenceProjector.MAX_PATTERNS + 1)
                .getResultList();
        if (values.size() != batch.rows().size()) throw conflict();
        var ids = new java.util.LinkedHashMap<String, Long>();
        try {
            for (int i = 0; i < values.size(); i++) {
                var row = (Object[]) values.get(i);
                long id = Long.parseLong((String) row[1]);
                if (id <= 0 || !batch.rows().get(i).hash().equals(row[0]) || ids.put((String) row[0], id) != null) throw conflict();
            }
        } catch (NumberFormatException overflow) { throw conflict(); }
        return java.util.Collections.unmodifiableMap(ids);
    }

    private void requireRun(Batch batch) {
        var c = batch.context(); var result = c.result(); var support = result.minimumSupport();
        // Use fresh scalar values, not potentially cached entities. Terminal state has no live RUNNING lease.
        var rows = em.createNativeQuery("SELECT status,snapshot_id,algorithm,dataset_source,minsup_mode,minsup_configured,"
                + "minsup_relative_fraction,effective_threshold,max_w,min_w,mean_w,CAST(pattern_count AS CHAR)"
                + " FROM MINING_RUN WHERE run_id=:id FOR UPDATE")
                .setParameter("id", c.identity().runId()).getResultList();
        if (rows.size() != 1) throw conflict();
        var r = (Object[]) rows.getFirst(); var binding = c.identity().provenance().binding();
        if (!"SUCCEEDED".equals(r[0]) || !binding.publication().snapshotId().equals(r[1])
                || !result.algorithm().name().equals(r[2]) || !binding.sourceName().equals(r[3])
                || !support.mode().name().equals(r[4]) || !bits(r[5], support.configuredValue())
                || (support.relativeMinimumSupport().isPresent()
                    ? !bits(r[6], support.relativeMinimumSupport().getAsDouble()) : r[6] != null)
                || !bits(r[7], support.effectiveThreshold()) || !bits(r[8], c.maxWeight())
                || !bits(r[9], c.minWeight()) || !bits(r[10], result.globalMeanWeight())
                || !Objects.equals(r[11], Long.toString(result.patternCount()))) throw conflict();
        var publication = em.createNativeQuery("SELECT publication_status FROM RUN_RESULT_PUBLICATION WHERE run_id=:id FOR UPDATE")
                .setParameter("id", c.identity().runId()).getResultList();
        if (publication.size() != 1 || !"NOT_PUBLISHED".equals(publication.getFirst())) throw conflict();
        metadata.requireMatches(c.identity().runId(), c.identity().provenance());
    }

    private List<Row> readRows(String runId) {
        // Parent run lock serializes cooperating writers; reject oversized historical data before materializing TEXT.
        var size = (Object[]) em.createNativeQuery("SELECT COUNT(*),COALESCE(SUM(OCTET_LENGTH(canonical_pattern)+256),0)"
                + " FROM PATTERN WHERE run_id=:id").setParameter("id", runId).getSingleResult();
        if (new java.math.BigDecimal(size[0].toString()).compareTo(java.math.BigDecimal.valueOf(PatternPersistenceProjector.MAX_PATTERNS)) > 0
                || new java.math.BigDecimal(size[1].toString()).compareTo(java.math.BigDecimal.valueOf(PatternPersistenceProjector.MAX_PROJECTED_BYTES)) > 0) {
            throw conflict();
        }
        var values = em.createNativeQuery("SELECT canonical_pattern,canonical_pattern_hash,CAST(support_abs AS CHAR),support_ratio,"
                + "weighted_support,CAST(pattern_item_count AS CHAR),CAST(itemset_count AS CHAR),pattern_weight,pattern_weight_policy,CAST(pattern_id AS CHAR)"
                + " FROM PATTERN WHERE run_id=:id ORDER BY canonical_pattern_hash FOR UPDATE")
                .setParameter("id", runId).setMaxResults(PatternPersistenceProjector.MAX_PATTERNS + 1).getResultList();
        if (values.size() > PatternPersistenceProjector.MAX_PATTERNS) throw conflict();
        var rows = new ArrayList<Row>();
        try {
            for (Object value : values) {
                var r = (Object[]) value;
                if (Long.parseLong((String) r[9]) <= 0) throw conflict();
                rows.add(new Row((String) r[0], (String) r[1], Long.parseLong((String) r[2]),
                        ((Number) r[3]).doubleValue(), ((Number) r[4]).doubleValue(), Integer.parseInt((String) r[5]),
                        Integer.parseInt((String) r[6]), ((Number) r[7]).doubleValue(), (String) r[8]));
            }
        } catch (NumberFormatException overflow) { throw conflict(); }
        return List.copyOf(rows);
    }

    private static boolean bits(Object actual, double expected) {
        return actual instanceof Number number && Double.doubleToRawLongBits(number.doubleValue()) == Double.doubleToRawLongBits(expected);
    }
    private static ApplicationException conflict() {
        return new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE, "Pattern persistence binding or content is inconsistent.");
    }
}
