package vn.edu.wspm.backend.repositories.run;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.services.run.persistence.TemporalPatternProjector;
import vn.edu.wspm.backend.services.run.persistence.TemporalPatternProjector.*;

/** Same transaction/run lock as the parent writer. Never repairs a nonempty partial temporal stage. */
@Repository
public class TemporalPatternPersistenceRepository {
    @PersistenceContext private EntityManager em;
    private final PatternPersistenceRepository parents;
    public TemporalPatternPersistenceRepository(PatternPersistenceRepository parents) { this.parents = parents; }
    public enum Result { INSERTED, VERIFIED_EXISTING, EMPTY }

    @Transactional(propagation = Propagation.MANDATORY)
    public Result persist(Batch batch) {
        var ids = parents.verifyWithIds(batch.parents());
        var existing = read(batch, ids);
        if (!existing.isEmpty()) { requireMatches(batch, existing); return Result.VERIFIED_EXISTING; }
        if (batch.rowCount() == 0) return Result.EMPTY;
        for (var pattern : batch.patterns()) for (var row : pattern.items()) {
            em.createNativeQuery("INSERT INTO PATTERN_ITEM(pattern_id,itemset_order,item_order,item_id) VALUES(:id,:set,:item,:value)")
                    .setParameter("id", ids.get(pattern.parent().hash())).setParameter("set", row.itemsetOrder())
                    .setParameter("item", row.itemOrder()).setParameter("value", row.itemId()).executeUpdate();
        }
        requireMatches(batch, read(batch, ids));
        return Result.INSERTED;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, Long> verify(Batch batch) {
        var ids = parents.verifyWithIds(batch.parents());
        return requireMatches(batch, read(batch, ids));
    }

    private Map<String, List<ItemRow>> read(Batch batch, Map<String, Long> ids) {
        String run = batch.parents().context().identity().runId();
        var count = (Number) em.createNativeQuery("SELECT COUNT(*) FROM PATTERN_ITEM i JOIN PATTERN p ON p.pattern_id=i.pattern_id WHERE p.run_id=:run")
                .setParameter("run", run).getSingleResult();
        if (new java.math.BigDecimal(count.toString()).compareTo(java.math.BigDecimal.valueOf(TemporalPatternProjector.MAX_TEMPORAL_ROWS)) > 0) throw conflict();
        var values = em.createNativeQuery("SELECT p.canonical_pattern_hash,CAST(i.pattern_id AS CHAR),CAST(i.itemset_order AS CHAR),"
                + "CAST(i.item_order AS CHAR),CAST(i.item_id AS CHAR) FROM PATTERN_ITEM i JOIN PATTERN p ON p.pattern_id=i.pattern_id"
                + " WHERE p.run_id=:run ORDER BY p.canonical_pattern_hash,i.itemset_order,i.item_order FOR UPDATE")
                .setParameter("run", run).setMaxResults(TemporalPatternProjector.MAX_TEMPORAL_ROWS + 1).getResultList();
        if (values.size() > TemporalPatternProjector.MAX_TEMPORAL_ROWS) throw conflict();
        var result = new LinkedHashMap<String, List<ItemRow>>();
        try {
            for (Object value : values) {
                var r = (Object[]) value; String hash = (String) r[0];
                long id = Long.parseLong((String) r[1]);
                if (id <= 0 || !Objects.equals(ids.get(hash), id)) throw conflict();
                result.computeIfAbsent(hash, key -> new ArrayList<>()).add(new ItemRow(
                        Integer.parseInt((String) r[2]), Integer.parseInt((String) r[3]), Integer.parseInt((String) r[4])));
            }
        } catch (NumberFormatException overflow) { throw conflict(); }
        return result;
    }

    private Map<String, Long> requireMatches(Batch batch, Map<String, List<ItemRow>> actual) {
        var reconstructed = new LinkedHashMap<String, Long>();
        if (actual.size() != batch.patterns().size()) throw conflict();
        try {
            for (var expected : batch.patterns()) {
                var rows = actual.get(expected.parent().hash());
                if (!expected.items().equals(rows)) throw conflict();
                var pattern = TemporalPatternProjector.reconstruct(expected.parent(), rows, batch.parents().context().products());
                if (reconstructed.put(pattern.serialize(), expected.parent().support()) != null) throw conflict();
            }
        } catch (IllegalArgumentException invalidStructure) { throw conflict(); }
        var engine = new LinkedHashMap<String, Long>();
        batch.parents().context().result().supports().forEach((pattern, support) -> engine.put(pattern.serialize(), support.longValue()));
        if (!engine.equals(reconstructed)) throw conflict();
        return Collections.unmodifiableMap(reconstructed);
    }

    private static ApplicationException conflict() {
        return new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE, "Temporal persistence binding or structure is inconsistent.");
    }
}
