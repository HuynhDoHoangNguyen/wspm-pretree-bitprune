package vn.edu.wspm.backend.repositories.run;

import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.services.run.persistence.*;

/** Caller owns transaction and lock order. Hashes stored content, never repairs it. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class RunResultPublicationRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    public RunResultPublicationRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public Map<String, Object> marker(String run, boolean lock) {
        var p = Map.of("run", run);
        if (lock && jdbc.queryForList("SELECT run_id FROM MINING_RUN WHERE run_id=:run FOR UPDATE", p).size() != 1) throw conflict();
        var rows = jdbc.queryForList("SELECT publication_status,manifest_version,result_sha256,CAST(pattern_count AS CHAR) AS patterns,"
                + "CAST(pattern_item_count AS CHAR) AS temporal,CAST(run_item_count AS CHAR) AS items,pattern_weight_policy "
                + "FROM RUN_RESULT_PUBLICATION WHERE run_id=:run" + (lock ? " FOR UPDATE" : ""), p);
        if (rows.size() != 1) throw conflict();
        return rows.getFirst();
    }
    public record Snapshot(Map<String, Object> binding, List<Map<String, Object>> patterns,
            List<Map<String, Object>> temporal, List<Map<String, Object>> items, String digest) {}
    public Snapshot snapshot(String run) {
        var p = Map.of("run", run);
        // Bound allocation before fetching TEXT/JSON, including corrupt historical rows.
        var budgets = jdbc.queryForMap("SELECT (SELECT COUNT(*) FROM PATTERN WHERE run_id=:run) AS pc,"
                + "(SELECT COUNT(*) FROM PATTERN_ITEM i JOIN PATTERN p ON p.pattern_id=i.pattern_id WHERE p.run_id=:run) AS tc,"
                + "(SELECT COUNT(*) FROM RUN_ITEM_STATISTIC WHERE run_id=:run) AS ic,"
                + "(SELECT COALESCE(SUM(OCTET_LENGTH(canonical_pattern)+512),0) FROM PATTERN WHERE run_id=:run) AS pb,"
                + "(SELECT COALESCE(SUM(OCTET_LENGTH(product_id)+OCTET_LENGTH(product_name_snapshot)+OCTET_LENGTH(sku_snapshot)+256),0)"
                + " FROM RUN_ITEM_STATISTIC WHERE run_id=:run) AS ib", p);
        if (num(budgets,"pc") > 10000 || num(budgets,"tc") > 100000 || num(budgets,"ic") > 10000
                || num(budgets,"pb") > 12L*1024*1024 || num(budgets,"ib") > 12L*1024*1024) throw conflict();
        var bindings = jdbc.queryForList("SELECT r.run_id,r.snapshot_id,r.status,r.algorithm,r.dataset_source,r.minsup_mode,"
                + "r.minsup_configured,r.minsup_relative_fraction,r.effective_threshold,r.max_w,r.min_w,r.mean_w,"
                + "CAST(r.pattern_count AS CHAR) AS pattern_count,p.provenance_version,"
                + "CASE WHEN OCTET_LENGTH(p.provenance_json)<=65536 THEN CAST(p.provenance_json AS CHAR) END AS document "
                + "FROM MINING_RUN r JOIN RUN_PROVENANCE p ON p.run_id=r.run_id WHERE r.run_id=:run", p);
        if (bindings.size()!=1 || !"SUCCEEDED".equals(bindings.getFirst().get("status"))) throw conflict();
        var binding = new TreeMap<>(bindings.getFirst());
        if (!(binding.get("document") instanceof String document)) throw conflict();
        try {
            var provenance = json.readValue(document, RunProvenance.class);
            if (!Objects.equals(binding.get("snapshot_id"), provenance.binding().publication().snapshotId())
                    || !Objects.equals(binding.get("provenance_version"), provenance.version())) throw conflict();
            binding.put("document", json.readValue(document, Map.class));
        } catch (IllegalArgumentException e) { throw conflict(); }
        var patterns = jdbc.queryForList("SELECT CAST(pattern_id AS CHAR) AS id,canonical_pattern,canonical_pattern_hash,"
                + "CAST(support_abs AS CHAR) AS support,support_ratio,weighted_support,CAST(pattern_item_count AS CHAR) AS items,"
                + "CAST(itemset_count AS CHAR) AS sets,pattern_weight,pattern_weight_policy FROM PATTERN WHERE run_id=:run ORDER BY canonical_pattern_hash", p);
        var temporal = jdbc.queryForList("SELECT p.canonical_pattern_hash AS hash,CAST(i.itemset_order AS CHAR) AS set_order,"
                + "CAST(i.item_order AS CHAR) AS item_order,CAST(i.item_id AS CHAR) AS item_id FROM PATTERN_ITEM i JOIN PATTERN p "
                + "ON p.pattern_id=i.pattern_id WHERE p.run_id=:run ORDER BY p.canonical_pattern_hash,i.itemset_order,i.item_order", p);
        var items = jdbc.queryForList("SELECT CAST(item_id AS CHAR) AS item_id,product_id,CAST(occurrence_count AS CHAR) AS occurrences,"
                // Qualify the numeric column: the unqualified name resolves to the CHAR projection alias.
                + "CAST(sequence_support AS CHAR) AS support,item_weight,product_name_snapshot,sku_snapshot FROM RUN_ITEM_STATISTIC s WHERE run_id=:run ORDER BY s.item_id", p);
        if (!Objects.equals(binding.get("pattern_count"), Integer.toString(patterns.size()))) throw conflict();
        String digest = ResultManifestCodec.digest(List.of(binding, patterns, temporal, items));
        return new Snapshot(Collections.unmodifiableMap(binding), freeze(patterns), freeze(temporal), freeze(items), digest);
    }
    public Snapshot verified(String run) {
        var marker = marker(run, false);
        if (!"COMMITTED".equals(marker.get("publication_status")) || !ResultManifestCodec.VERSION.equals(marker.get("manifest_version"))
                || !PatternWeightPolicy.VERSION.equals(marker.get("pattern_weight_policy"))) throw conflict();
        var s = snapshot(run);
        if (!s.digest().equals(marker.get("result_sha256")) || !Integer.toString(s.patterns().size()).equals(marker.get("patterns"))
                || !Integer.toString(s.temporal().size()).equals(marker.get("temporal")) || !Integer.toString(s.items().size()).equals(marker.get("items"))) throw conflict();
        return s;
    }
    public void commit(String run, Snapshot s, long millis) {
        int changed = jdbc.update("UPDATE RUN_RESULT_PUBLICATION SET publication_status='COMMITTED',manifest_version=:version,"
                + "result_sha256=:hash,pattern_count=:pc,pattern_item_count=:tc,run_item_count=:ic,pattern_weight_policy=:policy,"
                + "publication_elapsed_ms=:ms,committed_at=UTC_TIMESTAMP(6) WHERE run_id=:run AND publication_status='NOT_PUBLISHED'",
                Map.of("run",run,"version",ResultManifestCodec.VERSION,"hash",s.digest(),"pc",s.patterns().size(),"tc",s.temporal().size(),
                        "ic",s.items().size(),"policy",PatternWeightPolicy.VERSION,"ms",millis));
        if (changed != 1) throw conflict();
    }
    private static List<Map<String,Object>> freeze(List<Map<String,Object>> rows) {
        return rows.stream().map(r -> Collections.unmodifiableMap(new TreeMap<>(r))).toList();
    }
    private static long num(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    public static ApplicationException conflict() { return new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE,"Result publication is incomplete, inconsistent or unsupported."); }
}
