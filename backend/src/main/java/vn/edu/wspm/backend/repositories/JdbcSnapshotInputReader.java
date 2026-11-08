package vn.edu.wspm.backend.repositories;

import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.wspm.backend.services.mining.provider.SnapshotInputReader;

/** Explicit internal adapter; not auto-wired into any endpoint or pipeline. Read-only existing V2 fields. */
public final class JdbcSnapshotInputReader implements SnapshotInputReader {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    public JdbcSnapshotInputReader(DataSource source) {
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setReadOnly(true);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @Override public Read read(String id, Set<String> productIds) {
        return transaction.execute(tx -> {
            var metadata = jdbc.query("""
                    SELECT snapshot_id, source_type, validation_status, raw_file_path, raw_sha256,
                      JSON_UNQUOTE(JSON_EXTRACT(conversion_report_json,'$.provenance.rawSizeBytes')),
                      JSON_UNQUOTE(JSON_EXTRACT(conversion_report_json,'$.contractVersion')),
                      JSON_UNQUOTE(JSON_EXTRACT(conversion_report_json,'$.provenance.acquisition')),
                      JSON_UNQUOTE(JSON_EXTRACT(conversion_report_json,'$.provenance.sourceTimeZone')),
                      JSON_UNQUOTE(JSON_EXTRACT(conversion_report_json,'$.provenance.timeField')),
                      JSON_UNQUOTE(JSON_EXTRACT(conversion_report_json,'$.provenance.statusPolicy')),
                      from_time, to_time, created_at, source_name, CAST(conversion_report_json AS CHAR)
                    FROM DATASET_SNAPSHOT WHERE CAST(snapshot_id AS BINARY)=CAST(? AS BINARY)
                    """, (rs, row) -> new Metadata(rs.getString(1),rs.getString(2),rs.getString(3),
                    rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),
                    nullableJson(rs.getString(9)),rs.getString(10),rs.getString(11),
                    rs.getObject(12,LocalDateTime.class),rs.getObject(13,LocalDateTime.class),
                    rs.getObject(14,LocalDateTime.class),rs.getString(15),rs.getString(16)), id);
            var matches = new HashMap<String,List<String>>();
            if (!metadata.isEmpty()) {
                for (String product : new TreeSet<>(productIds)) {
                    // Intentionally use column collation: binary-only lookup would hide alias collisions.
                    matches.put(product, jdbc.query("SELECT product_id FROM PRODUCT WHERE product_id=?",
                            (rs, row) -> rs.getString(1), product));
                }
            }
            return new Read(metadata.stream().findFirst(), matches);
        });
    }
    private static String nullableJson(String value) { return "null".equals(value) ? null : value; }
}
