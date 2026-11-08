package vn.edu.wspm.backend.repositories.run;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.wspm.backend.dto.run.result.RunResultResponse.PatternSummary;

/** Read projections only; the caller owns the single repeatable-read transaction. No locking reads. */
@Repository
public class RunResultQueryRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public RunResultQueryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Map<String, Object> metadata(String id) {
        var rows = jdbc.queryForList("SELECT r.run_id,r.snapshot_id,r.status,r.mean_w,"
                + "p.provenance_version,CASE WHEN OCTET_LENGTH(p.provenance_json)<=65536 THEN CAST(p.provenance_json AS CHAR) END AS document,"
                + "u.publication_status,u.manifest_version,u.result_sha256,"
                + "DATE_FORMAT(u.committed_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS committed_at "
                + "FROM MINING_RUN r LEFT JOIN RUN_PROVENANCE p ON p.run_id=r.run_id "
                + "LEFT JOIN RUN_RESULT_PUBLICATION u ON u.run_id=r.run_id WHERE r.run_id=:run", Map.of("run", id));
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public record Counts(String patterns, String temporal, String statistics) {}
    public Counts counts(String run) {
        var p = Map.of("run", run);
        return new Counts(jdbc.queryForObject("SELECT CAST(COUNT(*) AS CHAR) FROM PATTERN WHERE run_id=:run", p, String.class),
                jdbc.queryForObject("SELECT CAST(COUNT(*) AS CHAR) FROM PATTERN_ITEM i JOIN PATTERN p ON p.pattern_id=i.pattern_id WHERE p.run_id=:run", p, String.class),
                jdbc.queryForObject("SELECT CAST(COUNT(*) AS CHAR) FROM RUN_ITEM_STATISTIC WHERE run_id=:run", p, String.class));
    }
    private static final String COLUMNS = "CAST(pattern_id AS CHAR) AS id,run_id,canonical_pattern,canonical_pattern_hash,"
            + "CAST(support_abs AS CHAR) AS support,support_ratio,weighted_support,pattern_weight,pattern_weight_policy,"
            + "CAST(itemset_count AS CHAR) AS sets,CAST(pattern_item_count AS CHAR) AS items";
    private static final String ORDER = " ORDER BY support_abs DESC,pattern_weight DESC,pattern_id DESC";
    public List<PatternSummary> page(String run, int offset, int size) {
        return jdbc.query("SELECT " + COLUMNS + " FROM PATTERN WHERE run_id=:run" + ORDER + " LIMIT :size OFFSET :offset",
                Map.of("run", run, "offset", offset, "size", size), (r, n) -> parent(r));
    }
    public PatternSummary pattern(String run, String id) {
        var rows = jdbc.query("SELECT " + COLUMNS + " FROM PATTERN WHERE run_id=:run AND pattern_id=CAST(:id AS UNSIGNED)",
                Map.of("run", run, "id", id), (r, n) -> parent(r));
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private static PatternSummary parent(ResultSet r) throws SQLException {
        // SQL UNSIGNED does not imply Java signed int support. Overflow is rejected, never narrowed.
        return new PatternSummary(r.getString("id"), r.getString("run_id"), r.getString("canonical_pattern"),
                r.getString("canonical_pattern_hash"), r.getString("support"), r.getDouble("support_ratio"),
                r.getDouble("weighted_support"), r.getDouble("pattern_weight"), r.getString("pattern_weight_policy"),
                supportedInt(r.getString("sets")), supportedInt(r.getString("items")));
    }
    public List<Map<String, Object>> temporal(String run, String id, int limit) {
        return jdbc.queryForList("SELECT CAST(i.itemset_order AS CHAR) AS set_order,CAST(i.item_order AS CHAR) AS item_order,"
                + "CAST(i.item_id AS CHAR) AS item_id,s.product_id,s.product_name_snapshot,s.sku_snapshot,s.item_weight,"
                + "CAST(s.occurrence_count AS CHAR) AS occurrences,CAST(s.sequence_support AS CHAR) AS support "
                + "FROM PATTERN_ITEM i JOIN PATTERN p ON p.pattern_id=i.pattern_id "
                + "LEFT JOIN RUN_ITEM_STATISTIC s ON s.run_id=p.run_id AND s.item_id=i.item_id "
                + "WHERE p.run_id=:run AND p.pattern_id=CAST(:id AS UNSIGNED) "
                + "ORDER BY i.itemset_order,i.item_order LIMIT :limit", Map.of("run", run, "id", id, "limit", limit));
    }
    public static int supportedInt(String value) {
        try { int n = Integer.parseInt(value); if (n > 0) return n; }
        catch (NumberFormatException ignored) { /* explicit unsupported storage value */ }
        throw new vn.edu.wspm.backend.exception.ApplicationException(
                vn.edu.wspm.backend.exception.ErrorCode.INVALID_RESOURCE_STATE, "Stored integer is outside the supported range.");
    }
}
