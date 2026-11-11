package vn.edu.wspm.backend.services.workspace;

import java.nio.file.Path;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;

/** Metadata-only workspace queries. No artifact reads, integrity claims or lifecycle writes. */
@Service
@Slf4j
public class MiningWorkspaceReadService {
    private final NamedParameterJdbcTemplate jdbc;
    private final Path root;
    public MiningWorkspaceReadService(NamedParameterJdbcTemplate jdbc, @Value("${wdm.snapshot-storage.root}") String root) {
        this.jdbc = jdbc; this.root = Path.of(root).toAbsolutePath().normalize();
    }
    public record Page(List<Map<String, Object>> items, int page, int size, long total) {}
    public record FileLocation(String kind, String filename, String reference, String absolutePath, String sha256) {}
    public record Storage(String snapshotId, String sourceName, String root, String binding,
            String verification, List<FileLocation> files) {}
    public static String id(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw ApplicationException.invalid("id", "Supply a canonical UUID.");
        return value;
    }
    public static int integer(String value, int fallback, int min, int max) {
        if (value == null) return fallback;
        try { if (!value.matches("[0-9]{1,7}")) throw new NumberFormatException(); int n = Integer.parseInt(value); if (n >= min && n <= max) return n; }
        catch (NumberFormatException ignored) { /* fixed public validation below */ }
        throw ApplicationException.invalid("query", "Pagination is outside the supported range.");
    }
    private static String allowed(String value, Set<String> options) {
        if (value == null || value.isEmpty()) return null;
        if (!options.contains(value)) throw ApplicationException.invalid("query", "Unsupported filter value.");
        return value;
    }
    @Transactional(readOnly = true, timeout = 5, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page list(boolean runs, Map<String, String> query) {
        int page = integer(query.get("page"), 1, 1, 100000), size = integer(query.get("size"), 20, 1, 100);
        var params = new HashMap<String, Object>(); var where = new StringBuilder(" WHERE 1=1");
        String search = query.getOrDefault("search", "").trim();
        if (search.length() > 255) throw ApplicationException.invalid("search", "Search is too long.");
        if (!search.isEmpty()) {
            // LOCATE treats %, _ and backslash literally; all values remain bound parameters.
            where.append(runs ? " AND LOCATE(:search,r.run_id)>0" : " AND (LOCATE(:search,s.source_name)>0 OR LOCATE(:search,s.snapshot_id)>0)");
            params.put("search", search);
        }
        if (runs) {
            filter(where, params, "r.status", "status", allowed(query.get("status"), Set.of("QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "LEGACY_UNVERIFIED")));
            filter(where, params, "r.algorithm", "algorithm", allowed(query.get("algorithm"), Set.of("WSPM_PRETREE", "WSPM_BITPRUNE")));
            if (query.containsKey("snapshotId") && !query.get("snapshotId").isEmpty()) filter(where, params, "r.snapshot_id", "snapshotId", id(query.get("snapshotId")));
        } else {
            filter(where, params, "s.validation_status", "validationStatus", allowed(query.get("validationStatus"), Set.of("PENDING", "VALID", "INVALID", "LEGACY_UNVERIFIED")));
            filter(where, params, "JSON_UNQUOTE(JSON_EXTRACT(s.conversion_report_json,'$.conversionStatus'))", "conversionStatus", allowed(query.get("conversionStatus"), Set.of("NOT_STARTED", "RUNNING", "SUCCEEDED", "FAILED")));
        }
        String from = runs ? " FROM MINING_RUN r JOIN DATASET_SNAPSHOT s ON s.snapshot_id=r.snapshot_id" : " FROM DATASET_SNAPSHOT s";
        long total = Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*)" + from + where, params, Long.class));
        String columns = runs ? "r.run_id AS runId,r.snapshot_id AS snapshotId,s.source_name AS sourceName,r.algorithm,r.status,r.minsup_mode AS minsupMode,r.minsup_configured AS configuredValue,CAST(r.pattern_count AS CHAR) AS patternCount," + utc("r.created_at", "createdAt") + "," + utc("r.started_at", "startedAt") + "," + utc("r.finished_at", "finishedAt")
            : "s.snapshot_id AS snapshotId,s.source_name AS sourceName,s.source_type AS sourceType,s.validation_status AS validationStatus,JSON_UNQUOTE(JSON_EXTRACT(s.conversion_report_json,'$.conversionStatus')) AS conversionStatus,CASE WHEN JSON_UNQUOTE(JSON_EXTRACT(s.conversion_report_json,'$.statisticsStatus'))='COMPUTED' THEN CAST(s.sequence_count AS CHAR) ELSE NULL END AS sequenceCount,CAST(s.distinct_item_count AS CHAR) AS distinctItemCount," + utc("s.created_at", "createdAt");
        params.put("limit", size); params.put("offset", (page - 1) * size);
        String identity = runs ? "r.run_id" : "s.snapshot_id", key = runs ? "runId" : "snapshotId";
        // MySQL can carry the entire JSON report into filesort when sorting its projections.
        // The inner LIMIT prevents derived-table merging even when JSON is used in a filter.
        // Materialize only ID/date, sort those narrow rows, then project one page without sorting JSON.
        String created = runs ? "r.created_at" : "s.created_at";
        String narrow = "SELECT " + identity + " AS id," + created + " AS createdAt" + from + where + " LIMIT 18446744073709551615";
        var ids = jdbc.queryForList("SELECT p.id FROM (" + narrow + ") p ORDER BY p.createdAt DESC,p.id DESC LIMIT :limit OFFSET :offset", params, String.class);
        List<Map<String, Object>> items = new ArrayList<>();
        if (!ids.isEmpty()) {
            items = jdbc.queryForList("SELECT " + columns + from + " WHERE " + identity + " IN (:ids)", Map.of("ids", ids));
            var positions = new HashMap<String, Integer>();
            for (int i = 0; i < ids.size(); i++) positions.put(ids.get(i), i);
            items.sort(Comparator.comparingInt(row -> positions.get((String) row.get(key))));
        }
        log.debug("Mining workspace list read: kind={}, page={}, rows={}", runs ? "runs" : "snapshots", page, items.size());
        return new Page(items, page, size, total);
    }
    private static String utc(String column, String alias) { return "DATE_FORMAT(" + column + ",'%Y-%m-%dT%H:%i:%s.%fZ') AS " + alias; }
    private static void filter(StringBuilder sql, Map<String, Object> params, String column, String key, String value) {
        if (value != null) { sql.append(" AND ").append(column).append("=:").append(key); params.put(key, value); }
    }
    @Transactional(readOnly = true, timeout = 5)
    public Storage storage(String snapshotId) {
        id(snapshotId);
        var rows = jdbc.queryForList("SELECT source_name,raw_file_path,raw_sha256,sequence_file_path,sequence_sha256,mapping_file_path,mapping_sha256 FROM DATASET_SNAPSHOT WHERE snapshot_id=:id", Map.of("id", snapshotId));
        if (rows.isEmpty()) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        var row = rows.getFirst(); var files = new ArrayList<FileLocation>();
        add(files, snapshotId, row, "raw", "raw_file_path", "raw_sha256", "raw.csv");
        add(files, snapshotId, row, "sequence", "sequence_file_path", "sequence_sha256", "sequences.spmf");
        add(files, snapshotId, row, "mapping", "mapping_file_path", "mapping_sha256", "mapping.json");
        return new Storage(snapshotId, Objects.toString(row.get("source_name"), ""), root.toString(), "CURRENT_SNAPSHOT_PUBLICATION", "METADATA_ONLY_NOT_INTEGRITY_VERIFIED", List.copyOf(files));
    }
    private void add(List<FileLocation> files, String id, Map<String, Object> row, String kind, String key, String hash, String filename) {
        Object value = row.get(key); if (!(value instanceof String reference)) return;
        String uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
        String expected = kind.equals("raw") ? java.util.regex.Pattern.quote(id + "/raw.csv")
                : java.util.regex.Pattern.quote(id + "/conversions/") + uuid + "/" + java.util.regex.Pattern.quote(filename);
        if (!reference.matches(expected)) return; // Legacy/untrusted references are not presented as usable paths.
        Path absolute = root.resolve(reference).normalize(); if (!absolute.startsWith(root)) return;
        files.add(new FileLocation(kind, filename, reference, absolute.toString(), Objects.toString(row.get(hash), null)));
    }
}
