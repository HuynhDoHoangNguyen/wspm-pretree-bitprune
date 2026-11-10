package vn.edu.wspm.backend.repositories.run;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.services.run.persistence.RunItemStatisticsProjector;
import vn.edu.wspm.backend.services.run.persistence.RunItemStatisticsProjector.*;

/** Run-serialized staging. Existing display snapshots are frozen, not compared with today's catalog. */
@Repository
public class RunItemStatisticsPersistenceRepository {
    @PersistenceContext private EntityManager em;
    private final TemporalPatternPersistenceRepository temporal;
    public RunItemStatisticsPersistenceRepository(TemporalPatternPersistenceRepository temporal) { this.temporal = temporal; }
    public enum Result { INSERTED, VERIFIED_EXISTING, EMPTY }

    @Transactional(propagation = Propagation.MANDATORY)
    public Result persist(Batch batch) {
        temporal.verify(batch.temporal()); // Recheck run, provenance, parents and full structural map under run lock.
        var existing = read(batch.runId());
        if (!existing.isEmpty()) { requireMatches(batch, existing); return Result.VERIFIED_EXISTING; }
        if (batch.rows().isEmpty()) return Result.EMPTY;
        var captured = capture(batch);
        for (var stored : captured) {
            var row = stored.statistics();
            em.createNativeQuery("INSERT INTO RUN_ITEM_STATISTIC(run_id,item_id,product_id,occurrence_count,sequence_support,"
                    + "item_weight,product_name_snapshot,sku_snapshot) VALUES(:run,:item,:product,:occurrences,:support,:weight,:name,:sku)")
                    .setParameter("run", batch.runId()).setParameter("item", row.itemId()).setParameter("product", row.productId())
                    .setParameter("occurrences", row.occurrences()).setParameter("support", row.support()).setParameter("weight", row.weight())
                    .setParameter("name", stored.name()).setParameter("sku", stored.sku()).executeUpdate();
        }
        var actual = read(batch.runId()); requireMatches(batch, actual);
        if (!captured.equals(actual)) throw conflict();
        return Result.INSERTED;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<StoredRow> verify(Batch batch) {
        temporal.verify(batch.temporal());
        var rows = read(batch.runId()); requireMatches(batch, rows); return rows;
    }

    private List<StoredRow> capture(Batch batch) {
        var byProduct = new TreeMap<String, Row>(); batch.rows().forEach(row -> byProduct.put(row.productId(), row));
        var captured = new ArrayList<StoredRow>();
        // Stable product lock order. FOR SHARE prevents rename/deletion until this first capture commits.
        for (var entry : byProduct.entrySet()) {
            var values = em.createNativeQuery("SELECT product_id,name,sku FROM PRODUCT WHERE product_id=:id FOR SHARE")
                    .setParameter("id", entry.getKey()).getResultList();
            if (values.size() != 1) throw conflict();
            var value = (Object[]) values.getFirst();
            if (!entry.getKey().equals(value[0])) throw conflict();
            captured.add(new StoredRow(entry.getValue(), (String) value[1], (String) value[2]));
        }
        captured.sort(Comparator.comparingInt(row -> row.statistics().itemId()));
        try { return RunItemStatisticsProjector.validateStored(captured); }
        catch (IllegalArgumentException invalid) { throw conflict(); }
    }

    private List<StoredRow> read(String runId) {
        var size = (Object[]) em.createNativeQuery("SELECT COUNT(*),COALESCE(SUM(OCTET_LENGTH(product_id)"
                + "+OCTET_LENGTH(product_name_snapshot)+OCTET_LENGTH(sku_snapshot)+192),0) FROM RUN_ITEM_STATISTIC WHERE run_id=:id")
                .setParameter("id", runId).getSingleResult();
        if (new BigDecimal(size[0].toString()).compareTo(BigDecimal.valueOf(RunItemStatisticsProjector.MAX_ITEMS)) > 0
                || new BigDecimal(size[1].toString()).compareTo(BigDecimal.valueOf(RunItemStatisticsProjector.MAX_PROJECTED_BYTES)) > 0) throw conflict();
        var values = em.createNativeQuery("SELECT CAST(item_id AS CHAR),product_id,CAST(occurrence_count AS CHAR),"
                + "CAST(sequence_support AS CHAR),item_weight,product_name_snapshot,sku_snapshot FROM RUN_ITEM_STATISTIC"
                + " WHERE run_id=:id ORDER BY item_id FOR UPDATE")
                .setParameter("id", runId).setMaxResults(RunItemStatisticsProjector.MAX_ITEMS + 1).getResultList();
        var rows = new ArrayList<StoredRow>();
        try {
            for (Object value : values) {
                var r = (Object[]) value;
                if (!(r[4] instanceof Number weight)) throw conflict();
                rows.add(new StoredRow(new Row(Integer.parseInt((String) r[0]), (String) r[1],
                        Long.parseLong((String) r[2]), Long.parseLong((String) r[3]), weight.doubleValue()), (String) r[5], (String) r[6]));
            }
            return RunItemStatisticsProjector.validateStored(rows);
        } catch (IllegalArgumentException invalid) { throw conflict(); }
    }

    private void requireMatches(Batch batch, List<StoredRow> rows) {
        // Record double equality is exact; no epsilon. Snapshots are checked structurally, not refreshed.
        if (!batch.rows().equals(rows.stream().map(StoredRow::statistics).toList())) throw conflict();
    }
    private static ApplicationException conflict() {
        return new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE, "Run item statistics binding or content is inconsistent.");
    }
}
